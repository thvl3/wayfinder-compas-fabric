package com.cse310.wayfinder.client;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * A* pathfinder that walks the terrain surface.
 *
 * <p>Each node is a horizontal block column; moving to a neighbour finds the surface
 * you could stand on there, allowing a small step up or a larger drop (like a player
 * walking). Columns that would require climbing a wall are not traversable, so the
 * path naturally routes <em>around</em> obstacles instead of through them.</p>
 *
 * <p>The search is bounded (node and range caps) so it stays cheap; it only runs every
 * ~10 seconds. If the goal can't be reached within those bounds, it returns the best
 * partial path toward it.</p>
 */
public final class SurfacePathfinder {

    private static final int MAX_NODES = 6000;   // search budget
    private static final int MAX_RANGE = 160;    // blocks from start in X or Z
    private static final int MAX_STEP_UP = 1;    // how high you can step up
    private static final int MAX_STEP_DOWN = 4;  // how far you can drop
    private static final int HEAD_ROOM = 2;      // air blocks needed to fit through

    private SurfacePathfinder() {
    }

    /** Cheap holder for the priority queue. */
    private record Node(long key, double f) {
    }

    public static List<Vec3d> findPath(BlockView world, Vec3d startVec, Vec3d targetVec) {
        int startX = floor(startVec.x);
        int startZ = floor(startVec.z);
        int targetX = floor(targetVec.x);
        int targetZ = floor(targetVec.z);

        Integer startFeet = standableFeetY(world, startX, startZ, floor(startVec.y), 3, 10);
        if (startFeet == null) {
            return Collections.emptyList(); // Couldn't find ground under the player.
        }

        long startKey = key(startX, startZ);

        Map<Long, Long> cameFrom = new HashMap<>();
        Map<Long, Double> gScore = new HashMap<>();
        Map<Long, Integer> feetY = new HashMap<>();
        Set<Long> closed = new HashSet<>();
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::f));

        gScore.put(startKey, 0.0);
        feetY.put(startKey, startFeet);
        open.add(new Node(startKey, heuristic(startX, startZ, targetX, targetZ)));

        long bestKey = startKey;
        double bestHeuristic = heuristic(startX, startZ, targetX, targetZ);
        int processed = 0;

        int[] dx = {1, -1, 0, 0};
        int[] dz = {0, 0, 1, -1};

        while (!open.isEmpty() && processed < MAX_NODES) {
            Node current = open.poll();
            long currentKey = current.key();
            if (closed.contains(currentKey)) {
                continue;
            }
            closed.add(currentKey);
            processed++;

            int cx = unpackX(currentKey);
            int cz = unpackZ(currentKey);
            int currentFeet = feetY.get(currentKey);

            double h = heuristic(cx, cz, targetX, targetZ);
            if (h < bestHeuristic) {
                bestHeuristic = h;
                bestKey = currentKey;
            }
            if (cx == targetX && cz == targetZ) {
                bestKey = currentKey;
                break; // Reached the goal column.
            }

            for (int d = 0; d < 4; d++) {
                int nx = cx + dx[d];
                int nz = cz + dz[d];
                if (Math.abs(nx - startX) > MAX_RANGE || Math.abs(nz - startZ) > MAX_RANGE) {
                    continue;
                }

                long neighborKey = key(nx, nz);
                if (closed.contains(neighborKey)) {
                    continue;
                }

                Integer neighborFeet = standableFeetY(world, nx, nz, currentFeet, MAX_STEP_UP, MAX_STEP_DOWN);
                if (neighborFeet == null) {
                    continue; // Wall or gap we can't traverse.
                }

                double stepCost = 1.0 + Math.abs(neighborFeet - currentFeet) * 0.5;
                double tentative = gScore.get(currentKey) + stepCost;
                Double known = gScore.get(neighborKey);
                if (known == null || tentative < known) {
                    cameFrom.put(neighborKey, currentKey);
                    gScore.put(neighborKey, tentative);
                    feetY.put(neighborKey, neighborFeet);
                    open.add(new Node(neighborKey, tentative + heuristic(nx, nz, targetX, targetZ)));
                }
            }
        }

        return reconstruct(cameFrom, feetY, startKey, bestKey);
    }

    /** Builds the path (start -> goal) by walking the cameFrom links backward. */
    private static List<Vec3d> reconstruct(Map<Long, Long> cameFrom, Map<Long, Integer> feetY,
                                           long startKey, long bestKey) {
        List<Vec3d> path = new ArrayList<>();
        long k = bestKey;
        while (true) {
            int x = unpackX(k);
            int z = unpackZ(k);
            path.add(new Vec3d(x + 0.5, feetY.get(k), z + 0.5));
            if (k == startKey) {
                break;
            }
            Long previous = cameFrom.get(k);
            if (previous == null) {
                break;
            }
            k = previous;
        }
        Collections.reverse(path);
        return path;
    }

    /**
     * Finds the feet height of a standable spot in a column, searching from
     * {@code refFeetY + up} down to {@code refFeetY - down}.
     *
     * @return the feet Y (air block resting on solid ground with headroom), or
     *         {@code null} if no valid spot is in range.
     */
    private static Integer standableFeetY(BlockView world, int x, int z, int refFeetY, int up, int down) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int dy = up; dy >= -down; dy--) {
            int feet = refFeetY + dy;

            // The block under our feet must be solid to stand on.
            pos.set(x, feet - 1, z);
            if (world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()) {
                continue;
            }

            // We must fit: HEAD_ROOM blocks of clear space above the floor.
            boolean clear = true;
            for (int head = 0; head < HEAD_ROOM; head++) {
                pos.set(x, feet + head, z);
                if (!world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()) {
                    clear = false;
                    break;
                }
            }
            if (clear) {
                return feet;
            }
        }
        return null;
    }

    private static double heuristic(int x, int z, int targetX, int targetZ) {
        double dx = targetX - x;
        double dz = targetZ - z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static int floor(double v) {
        return (int) Math.floor(v);
    }

    private static long key(int x, int z) {
        return ((long) x & 0xFFFFFFFFL) << 32 | ((long) z & 0xFFFFFFFFL);
    }

    private static int unpackX(long key) {
        return (int) (key >> 32);
    }

    private static int unpackZ(long key) {
        return (int) key;
    }
}
