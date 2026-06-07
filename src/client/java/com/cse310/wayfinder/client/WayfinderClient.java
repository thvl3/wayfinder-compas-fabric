package com.cse310.wayfinder.client;

import com.cse310.wayfinder.Waypoint;
import com.cse310.wayfinder.WayfinderMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;

/**
 * Client entry point. Wires up:
 * <ul>
 *   <li>loading saved waypoints from disk,</li>
 *   <li>the right-click behaviour that sets/clears a waypoint,</li>
 *   <li>the "N" key that cycles through saved waypoints,</li>
 *   <li>the world renderer that draws the trail.</li>
 * </ul>
 */
public class WayfinderClient implements ClientModInitializer {

    private static final double AIM_DISTANCE = 128.0;

    private KeyBinding cycleKey;

    @Override
    public void onInitializeClient() {
        // Pull previously-saved waypoints back into memory.
        WaypointManager.get().load();

        registerUseHandler();
        registerCycleKey();
        registerTrailTick();
        WorldRenderEvents.AFTER_TRANSLUCENT.register(WayfinderRenderer::render);

        WayfinderMod.LOGGER.info("Wayfinder client ready.");
    }

    /**
     * Drives the trail every tick: recomputes the A* path periodically and ages/spawns
     * the sparks. When the player isn't actively tracking a waypoint, the trail is reset.
     */
    private void registerTrailTick() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || client.player == null) {
                return;
            }

            Waypoint waypoint = WaypointManager.get().getActive();
            boolean active = waypoint != null
                    && isHoldingCompass(client.player)
                    && client.world.getRegistryKey().getValue().toString().equals(waypoint.getDimension());

            if (active) {
                TrailController.get().tick(client.world, client.player, waypoint);
            } else {
                TrailController.get().reset();
            }
        });
    }

    private static boolean isHoldingCompass(PlayerEntity player) {
        return player.getMainHandStack().isOf(WayfinderMod.WAYFINDER_COMPASS)
                || player.getOffHandStack().isOf(WayfinderMod.WAYFINDER_COMPASS);
    }

    /** Right-click to set a waypoint, sneak + right-click to clear it. */
    private void registerUseHandler() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);

            // Only act on the client logical side, and only for our compass.
            if (!world.isClient() || !stack.isOf(WayfinderMod.WAYFINDER_COMPASS)) {
                return TypedActionResult.pass(stack);
            }

            if (player.isSneaking()) {
                WaypointManager.get().clearActive();
                sendActionBar(player, Text.literal("Waypoint cleared").formatted(Formatting.RED));
            } else {
                Vec3d target = resolveTarget(player, world);
                String dimension = world.getRegistryKey().getValue().toString();
                Waypoint waypoint = WaypointManager.get().createAt(dimension, target);
                sendActionBar(player, Text.literal("Waypoint set: " + waypoint).formatted(Formatting.GREEN));
            }

            return TypedActionResult.success(stack);
        });
    }

    /**
     * Returns the block the player is looking at (within {@link #AIM_DISTANCE}),
     * or the player's own position if they aren't aiming at anything.
     */
    private Vec3d resolveTarget(PlayerEntity player, World world) {
        HitResult hit = player.raycast(AIM_DISTANCE, 1.0f, false);
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hit;
            return blockHit.getBlockPos().toCenterPos();
        }
        return player.getPos();
    }

    /** Registers the "N" key and the per-tick check that fires it. */
    private void registerCycleKey() {
        cycleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.wayfinder.cycle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
                "key.category.wayfinder"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (cycleKey.wasPressed()) {
                if (client.player == null) {
                    continue;
                }
                Waypoint waypoint = WaypointManager.get().cycleActive();
                if (waypoint == null) {
                    sendActionBar(client.player,
                            Text.literal("No saved waypoints yet").formatted(Formatting.GRAY));
                } else {
                    sendActionBar(client.player,
                            Text.literal("Tracking: " + waypoint).formatted(Formatting.AQUA));
                }
            }
        });
    }

    private static void sendActionBar(PlayerEntity player, Text message) {
        player.sendMessage(message, true);
    }
}
