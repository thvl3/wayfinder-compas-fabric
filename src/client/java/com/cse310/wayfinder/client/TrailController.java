package com.cse310.wayfinder.client;

import com.cse310.wayfinder.Waypoint;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Owns the hidden A* path and the periodic "surges" of light that reveal it.
 *
 * <p>The path itself is never drawn. Every few seconds a surge ignites at the player
 * (the path's source), races along the track toward the waypoint as a cluster of
 * glowing sparks, and leaves a brief fading wake behind it. When it reaches the
 * destination it bursts, and the trail goes dark until the next surge.</p>
 *
 * <p>The path is recomputed roughly every 10 seconds, or sooner if the waypoint
 * changes or the player wanders away from it.</p>
 */
public final class TrailController {

    private static final TrailController INSTANCE = new TrailController();

    public static TrailController get() {
        return INSTANCE;
    }

    // The path is (re)computed only when a new surge ignites, never mid-surge.
    private static final int SMOOTH_ITERATIONS = 3;        // Chaikin passes to round off the A* path
    private static final double VIEW_DOT_THRESHOLD = -0.25; // surge head considered out of view below this

    // Surge behaviour.
    private static final double SURGE_SPEED = 0.35;  // blocks per tick the surge head travels
    private static final int SURGE_COOLDOWN = 50;    // ticks to wait after a surge finishes
    private static final double FLOAT_HEIGHT = 0.9;  // how high sparks sit above the track

    // Spark clusters.
    private static final int WAKE_LIFETIME = 16;     // brief wake (~0.8s)
    private static final int WAKE_PER_STEP = 4;      // wake sparks spawned each tick of travel
    private static final int BURST_LIFETIME = 22;    // ignition / arrival burst life
    private static final int IGNITION_SPARKS = 24;   // burst at the source
    private static final int ARRIVAL_SPARKS = 20;    // burst at the destination

    /** A single floaty mote with its own velocity and lifetime. */
    public static final class Spark {
        public final Vec3d origin;
        public final Vec3d velocity;
        public final long spawnTick;
        public final int maxAge;
        public final int color;

        Spark(Vec3d origin, Vec3d velocity, long spawnTick, int maxAge, int color) {
            this.origin = origin;
            this.velocity = velocity;
            this.spawnTick = spawnTick;
            this.maxAge = maxAge;
            this.color = color;
        }
    }

    private final Random random = new Random();
    private final List<Spark> sparks = new ArrayList<>();

    private List<Vec3d> path = Collections.emptyList();
    private double pathLength = 0.0;
    private Waypoint trackedWaypoint;

    // Surge state.
    private boolean surgeActive = false;
    private double surgeHead = 0.0;
    private long nextSurgeTick = Long.MIN_VALUE;

    public List<Spark> getSparks() {
        return sparks;
    }

    public double getPathLength() {
        return pathLength;
    }

    public boolean isSurgeActive() {
        return surgeActive;
    }

    public double getSurgeHead() {
        return surgeHead;
    }

    public double getSurgeSpeed() {
        return SURGE_SPEED;
    }

    /** Clears everything (called when the player isn't actively tracking a waypoint). */
    public void reset() {
        if (!path.isEmpty()) {
            path = Collections.emptyList();
        }
        pathLength = 0.0;
        trackedWaypoint = null;
        sparks.clear();
        surgeActive = false;
        surgeHead = 0.0;
        nextSurgeTick = Long.MIN_VALUE;
    }

    public void tick(ClientWorld world, PlayerEntity player, Waypoint waypoint) {
        long now = world.getTime();

        // A brand-new destination interrupts everything and re-ignites from the player.
        if (waypoint != trackedWaypoint) {
            trackedWaypoint = waypoint;
            surgeActive = false;
            surgeHead = 0.0;
            sparks.clear();
            nextSurgeTick = now;
        }

        // Age out sparks past their individual lifetimes.
        sparks.removeIf(spark -> now - spark.spawnTick >= spark.maxAge);

        int color = waypoint.getColor();

        if (surgeActive) {
            Vec3d head = pointAt(surgeHead);

            // Stop the surge early only if its head has fallen out of the player's view,
            // so we never yank the path out from under a surge the player can still see.
            if (head == null || !isVisible(player, head)) {
                surgeActive = false;
                nextSurgeTick = now + SURGE_COOLDOWN;
                return;
            }

            // Advance the head and lay a short wake at its position.
            surgeHead += SURGE_SPEED;
            spawnCluster(head, WAKE_PER_STEP, 0.3, 0.012, WAKE_LIFETIME, now, color);

            // Soft chime emanating from the travelling cluster, rising in pitch as it
            // nears the waypoint.
            if (now % 5 == 0) {
                double progress = pathLength > 0 ? surgeHead / pathLength : 0.0;
                playChime(world, head, 0.18f, 0.8f + (float) progress * 0.9f);
            }

            if (surgeHead >= pathLength) {
                // Reached the destination: arrival burst, then go dark for a bit.
                Vec3d end = pointAt(pathLength);
                if (end != null) {
                    spawnCluster(end, ARRIVAL_SPARKS, 0.45, 0.045, BURST_LIFETIME, now, color);
                    playChime(world, end, 0.6f, 1.7f);
                }
                surgeActive = false;
                nextSurgeTick = now + SURGE_COOLDOWN;
            }
        } else if (now >= nextSurgeTick) {
            // Compute a fresh path from the player's CURRENT position and ignite there,
            // so the particles always originate from the player. The path then stays
            // frozen for the whole surge -- no mid-surge recompute.
            recompute(world, player, waypoint);
            if (path.size() >= 2) {
                surgeActive = true;
                surgeHead = 0.0;
                Vec3d source = pointAt(0.0);
                if (source != null) {
                    spawnCluster(source, IGNITION_SPARKS, 0.4, 0.045, BURST_LIFETIME, now, color);
                    playChime(world, source, 0.5f, 1.1f);
                }
            } else {
                nextSurgeTick = now + SURGE_COOLDOWN; // no usable path yet; retry soon
            }
        }
    }

    /** True unless the point is clearly behind the player (out of view). */
    private static boolean isVisible(PlayerEntity player, Vec3d point) {
        Vec3d eye = player.getEyePos();
        Vec3d toPoint = point.subtract(eye);
        double distance = toPoint.length();
        if (distance < 2.0) {
            return true; // right next to the player
        }
        double dot = player.getRotationVector().dotProduct(toPoint.multiply(1.0 / distance));
        return dot > VIEW_DOT_THRESHOLD;
    }

    /** Plays a soft amethyst chime at a world position, locally on the client. */
    private void playChime(ClientWorld world, Vec3d pos, float volume, float pitch) {
        world.playSound(pos.x, pos.y, pos.z,
                SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                SoundCategory.BLOCKS, volume, pitch, false);
    }

    private void recompute(ClientWorld world, PlayerEntity player, Waypoint waypoint) {
        Vec3d start = player.getPos();
        Vec3d target = new Vec3d(waypoint.getX(), waypoint.getY(), waypoint.getZ());
        path = smooth(SurfacePathfinder.findPath(world, start, target));
        pathLength = computeLength(path);
    }

    /**
     * Rounds off the jagged A* grid path using Chaikin corner-cutting, so the surge
     * follows a gentle curve instead of hard 90-degree steps. Endpoints are preserved.
     */
    private static List<Vec3d> smooth(List<Vec3d> raw) {
        if (raw.size() < 3) {
            return raw;
        }
        List<Vec3d> points = raw;
        for (int iteration = 0; iteration < SMOOTH_ITERATIONS; iteration++) {
            List<Vec3d> next = new ArrayList<>(points.size() * 2);
            next.add(points.get(0));
            for (int i = 0; i < points.size() - 1; i++) {
                Vec3d a = points.get(i);
                Vec3d b = points.get(i + 1);
                next.add(a.multiply(0.75).add(b.multiply(0.25))); // quarter point
                next.add(a.multiply(0.25).add(b.multiply(0.75))); // three-quarter point
            }
            next.add(points.get(points.size() - 1));
            points = next;
        }
        return points;
    }

    /** Spawns {@code count} sparks scattered around {@code center}. */
    private void spawnCluster(Vec3d center, int count, double posSpread, double speed,
                              int maxAge, long now, int color) {
        for (int i = 0; i < count; i++) {
            double ox = (random.nextDouble() - 0.5) * posSpread;
            double oy = random.nextDouble() * posSpread * 0.5;
            double oz = (random.nextDouble() - 0.5) * posSpread;
            Vec3d origin = new Vec3d(center.x + ox, center.y + FLOAT_HEIGHT + oy, center.z + oz);

            double vx = (random.nextDouble() - 0.5) * speed;
            double vy = random.nextDouble() * speed * 0.8;
            double vz = (random.nextDouble() - 0.5) * speed;
            Vec3d velocity = new Vec3d(vx, vy, vz);

            sparks.add(new Spark(origin, velocity, now, maxAge, color));
        }
    }

    private static double computeLength(List<Vec3d> path) {
        double total = 0.0;
        for (int i = 0; i < path.size() - 1; i++) {
            total += path.get(i).distanceTo(path.get(i + 1));
        }
        return total;
    }

    /** Returns the world point at {@code distance} along the path (arc length). */
    public Vec3d pointAt(double distance) {
        if (path.isEmpty()) {
            return null;
        }
        if (distance <= 0.0) {
            return path.get(0);
        }

        double accumulated = 0.0;
        for (int i = 0; i < path.size() - 1; i++) {
            Vec3d a = path.get(i);
            Vec3d b = path.get(i + 1);
            double segment = a.distanceTo(b);
            if (accumulated + segment >= distance) {
                double t = segment > 0 ? (distance - accumulated) / segment : 0.0;
                return a.add(b.subtract(a).multiply(t));
            }
            accumulated += segment;
        }
        return path.get(path.size() - 1);
    }
}
