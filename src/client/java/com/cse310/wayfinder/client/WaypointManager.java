package com.cse310.wayfinder.client;

import com.cse310.wayfinder.Waypoint;
import com.cse310.wayfinder.WayfinderMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side store for every waypoint the player has set.
 *
 * <p>Demonstrates the Java Collections Framework (a {@link LinkedHashMap}) and the
 * file read/write stretch challenge: the saved waypoints are written to a text
 * file in the game's config directory and reloaded on the next launch.</p>
 */
public final class WaypointManager {

    // Singleton: one shared store for the whole client.
    private static final WaypointManager INSTANCE = new WaypointManager();

    public static WaypointManager get() {
        return INSTANCE;
    }

    // A rotating palette of trail colours (packed 0xRRGGBB), so each new
    // waypoint looks different from the last.
    private static final int[] COLOR_PALETTE = {
            0x55FF55, // green
            0x55FFFF, // aqua
            0xFFAA00, // gold
            0xFF55FF, // magenta
            0xFFFF55  // yellow
    };

    // LinkedHashMap keeps waypoints in insertion order, which makes the
    // "cycle to the next waypoint" feature predictable.
    private final Map<String, Waypoint> saved = new LinkedHashMap<>();

    private final Path saveFile =
            FabricLoader.getInstance().getConfigDir().resolve("wayfinder_waypoints.txt");

    private Waypoint active;
    private int nextNumber = 1;
    private int cycleIndex = -1;

    private WaypointManager() {
    }

    public Waypoint getActive() {
        return active;
    }

    public int savedCount() {
        return saved.size();
    }

    /**
     * Creates a brand-new waypoint at the given world position, makes it active,
     * stores it, and persists everything to disk.
     */
    public Waypoint createAt(String dimension, Vec3d pos) {
        String name = "Waypoint " + nextNumber;
        nextNumber++;

        int color = COLOR_PALETTE[(saved.size()) % COLOR_PALETTE.length];
        Waypoint waypoint = new Waypoint(name, dimension, pos.x, pos.y, pos.z, color);

        saved.put(name, waypoint);
        active = waypoint;
        cycleIndex = saved.size() - 1;

        save();
        return waypoint;
    }

    public void clearActive() {
        active = null;
        cycleIndex = -1;
    }

    /**
     * Switches the active waypoint to the next one in the saved list, wrapping
     * around at the end. Returns the newly-selected waypoint, or {@code null} if
     * nothing has been saved yet.
     */
    public Waypoint cycleActive() {
        if (saved.isEmpty()) {
            return null;
        }

        // A Map has no index, so copy the values into a List to step through them.
        List<Waypoint> waypoints = new ArrayList<>(saved.values());
        cycleIndex = (cycleIndex + 1) % waypoints.size();
        active = waypoints.get(cycleIndex);
        return active;
    }

    /** Writes every saved waypoint to the text file, one per line. */
    public void save() {
        List<String> lines = new ArrayList<>();
        lines.add("# Wayfinder waypoints - name|dimension|x|y|z|color");

        for (Waypoint waypoint : saved.values()) {
            lines.add(waypoint.toFileLine());
        }

        try {
            Files.createDirectories(saveFile.getParent());
            Files.write(saveFile, lines);
        } catch (IOException e) {
            WayfinderMod.LOGGER.error("Could not save waypoints to {}", saveFile, e);
        }
    }

    /** Reads saved waypoints back from the text file when the client starts. */
    public void load() {
        if (!Files.exists(saveFile)) {
            return; // First run; nothing to load.
        }

        try {
            List<String> lines = Files.readAllLines(saveFile);
            int highestNumber = 0;

            for (String line : lines) {
                Waypoint waypoint = Waypoint.fromFileLine(line);
                if (waypoint == null) {
                    continue; // Blank line, comment, or bad data.
                }

                saved.put(waypoint.getName(), waypoint);

                // Recover the auto-numbering so new waypoints don't reuse names.
                if (waypoint.getName().startsWith("Waypoint ")) {
                    try {
                        int n = Integer.parseInt(waypoint.getName().substring("Waypoint ".length()).trim());
                        if (n > highestNumber) {
                            highestNumber = n;
                        }
                    } catch (NumberFormatException ignored) {
                        // Custom name; leave the counter alone.
                    }
                }
            }

            nextNumber = highestNumber + 1;
            WayfinderMod.LOGGER.info("Loaded {} saved waypoint(s).", saved.size());
        } catch (IOException e) {
            WayfinderMod.LOGGER.error("Could not load waypoints from {}", saveFile, e);
        }
    }
}
