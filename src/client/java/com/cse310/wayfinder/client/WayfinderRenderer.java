package com.cse310.wayfinder.client;

import com.cse310.wayfinder.Waypoint;
import com.cse310.wayfinder.WayfinderMod;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * Draws the surges that reveal the hidden A* path computed by {@link TrailController}.
 *
 * <p>The path is never drawn directly. Instead we render the short-lived sparks of the
 * travelling surge (the cluster at the head plus its fading wake), a bright glow on the
 * surge head while it travels, and a faint marker on the destination so the endpoint
 * stays locatable between surges.</p>
 *
 * <p>Sparks and the surge head respect the depth buffer so they sit on the terrain; the
 * destination marker is drawn through walls.</p>
 */
public final class WayfinderRenderer {

    private static final double FLOAT_HEIGHT = 0.9;
    private static final float MARKER_SIZE = 0.40f;

    private WayfinderRenderer() {
    }

    public static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        if (player == null || world == null) {
            return;
        }

        Waypoint waypoint = WaypointManager.get().getActive();
        if (waypoint == null || !isHoldingCompass(player)) {
            return;
        }
        if (!world.getRegistryKey().getValue().toString().equals(waypoint.getDimension())) {
            return;
        }

        MatrixStack matrices = context.matrixStack();
        if (matrices == null) {
            return;
        }

        Camera camera = context.camera();
        Vec3d cameraPos = camera.getPos();
        float tickDelta = context.tickCounter().getTickDelta(false);
        double time = world.getTime() + tickDelta;

        Quaternionf rotation = camera.getRotation();
        Vector3f right = rotation.transform(new Vector3f(1.0f, 0.0f, 0.0f));
        Vector3f up = rotation.transform(new Vector3f(0.0f, 1.0f, 0.0f));

        int color = waypoint.getColor();
        int cr = (color >> 16) & 0xFF;
        int cg = (color >> 8) & 0xFF;
        int cb = color & 0xFF;

        TrailController controller = TrailController.get();

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);

        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Tessellator tessellator = Tessellator.getInstance();

        // --- Pass 1: surge sparks + head glow, occluded by terrain. ---
        RenderSystem.enableDepthTest();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        drawSparks(buffer, matrix, controller, time, right, up);
        drawSurgeHead(buffer, matrix, controller, time, tickDelta, right, up, cr, cg, cb);

        BuiltBuffer surge = buffer.endNullable();
        if (surge != null) {
            BufferRenderer.drawWithGlobalProgram(surge);
        }

        // --- Pass 2: faint destination marker, drawn through walls. ---
        RenderSystem.disableDepthTest();
        BufferBuilder markerBuffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        double markerY = waypoint.getY() + FLOAT_HEIGHT + 0.1 * Math.sin(time * 0.15);
        Vec3d markerPoint = new Vec3d(waypoint.getX(), markerY, waypoint.getZ());
        float markerPulse = MARKER_SIZE * (0.8f + 0.2f * (float) Math.sin(time * 0.12));
        emitMarker(markerBuffer, matrix, markerPoint, right, up, markerPulse * 1.5f, cr, cg, cb, 35);
        emitMarker(markerBuffer, matrix, markerPoint, right, up, markerPulse, 255, 255, 255, 110);

        BuiltBuffer markerBuilt = markerBuffer.endNullable();
        if (markerBuilt != null) {
            BufferRenderer.drawWithGlobalProgram(markerBuilt);
        }

        matrices.pop();

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /** Draws each spark moving along its velocity and fading in then out over its life. */
    private static void drawSparks(BufferBuilder buffer, Matrix4f matrix, TrailController controller,
                                   double time, Vector3f right, Vector3f up) {
        for (TrailController.Spark spark : List.copyOf(controller.getSparks())) {
            double age = time - spark.spawnTick;
            if (age < 0 || age >= spark.maxAge) {
                continue;
            }
            double ageFraction = age / spark.maxAge;
            double fade = Math.sin(ageFraction * Math.PI); // 0 -> 1 -> 0

            int sr = (spark.color >> 16) & 0xFF;
            int sg = (spark.color >> 8) & 0xFF;
            int sb = spark.color & 0xFF;

            Vec3d point = spark.origin.add(spark.velocity.multiply(age));
            emitMarker(buffer, matrix, point, right, up, 0.12f, sr, sg, sb, clamp((int) (fade * 120)));
            emitMarker(buffer, matrix, point, right, up, 0.05f, 255, 255, 255, clamp((int) (fade * 230)));
        }
    }

    /** Draws the bright comet-like glow at the head of a travelling surge. */
    private static void drawSurgeHead(BufferBuilder buffer, Matrix4f matrix, TrailController controller,
                                      double time, float tickDelta, Vector3f right, Vector3f up,
                                      int cr, int cg, int cb) {
        if (!controller.isSurgeActive()) {
            return;
        }

        // Interpolate the head position between ticks for smooth motion.
        double head = Math.min(controller.getPathLength(),
                controller.getSurgeHead() + controller.getSurgeSpeed() * tickDelta);
        Vec3d base = controller.pointAt(head);
        if (base == null) {
            return;
        }

        Vec3d point = new Vec3d(base.x, base.y + FLOAT_HEIGHT, base.z);
        float pulse = 0.22f + 0.05f * (float) Math.sin(time * 0.6);
        emitMarker(buffer, matrix, point, right, up, pulse * 1.6f, cr, cg, cb, 120);
        emitMarker(buffer, matrix, point, right, up, pulse, 255, 255, 255, 240);
    }

    /** Emits one camera-facing diamond quad centred on {@code point}. */
    private static void emitMarker(BufferBuilder buffer, Matrix4f matrix, Vec3d point,
                                   Vector3f right, Vector3f up, float size,
                                   int r, int g, int b, int a) {
        float cx = (float) point.x;
        float cy = (float) point.y;
        float cz = (float) point.z;

        Vector3f top = new Vector3f(up).mul(size);
        Vector3f side = new Vector3f(right).mul(size);

        buffer.vertex(matrix, cx + top.x, cy + top.y, cz + top.z).color(r, g, b, a);
        buffer.vertex(matrix, cx + side.x, cy + side.y, cz + side.z).color(r, g, b, a);
        buffer.vertex(matrix, cx - top.x, cy - top.y, cz - top.z).color(r, g, b, a);
        buffer.vertex(matrix, cx - side.x, cy - side.y, cz - side.z).color(r, g, b, a);
    }

    private static int clamp(int alpha) {
        return Math.max(0, Math.min(255, alpha));
    }

    private static boolean isHoldingCompass(ClientPlayerEntity player) {
        return player.getMainHandStack().isOf(WayfinderMod.WAYFINDER_COMPASS)
                || player.getOffHandStack().isOf(WayfinderMod.WAYFINDER_COMPASS);
    }
}
