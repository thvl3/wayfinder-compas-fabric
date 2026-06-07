package com.cse310.wayfinder;

/**
 * A single saved location the Wayfinder Compass can point toward.
 *
 * <p>This is a plain Java class (the assignment's "Classes" requirement). It also
 * owns the logic for turning a waypoint into a line of text and back again, which
 * is what makes the file read/write stretch challenge possible.</p>
 */
public class Waypoint {

    private final String name;
    private final String dimension;
    private final double x;
    private final double y;
    private final double z;
    private final int color;

    public Waypoint(String name, String dimension, double x, double y, double z, int color) {
        this.name = name;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.color = color;
    }

    public String getName() {
        return name;
    }

    public String getDimension() {
        return dimension;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public int getColor() {
        return color;
    }

    /**
     * Serializes this waypoint to a single CSV-style line for the save file.
     * Example: {@code Home|minecraft:overworld|125.5|68.0|-340.5|65280}
     */
    public String toFileLine() {
        return String.join("|",
                name,
                dimension,
                Double.toString(x),
                Double.toString(y),
                Double.toString(z),
                Integer.toString(color));
    }

    /**
     * Parses one line from the save file back into a Waypoint.
     *
     * @return the parsed Waypoint, or {@code null} if the line is blank or malformed.
     */
    public static Waypoint fromFileLine(String line) {
        // Skip blank lines and comments.
        if (line == null || line.isBlank() || line.startsWith("#")) {
            return null;
        }

        String[] parts = line.split("\\|");
        if (parts.length != 6) {
            return null; // Not the format we wrote; ignore it rather than crash.
        }

        try {
            String name = parts[0];
            String dimension = parts[1];
            double x = Double.parseDouble(parts[2]);
            double y = Double.parseDouble(parts[3]);
            double z = Double.parseDouble(parts[4]);
            int color = Integer.parseInt(parts[5]);
            return new Waypoint(name, dimension, x, y, z, color);
        } catch (NumberFormatException e) {
            return null; // A number was garbled; skip this entry.
        }
    }

    @Override
    public String toString() {
        return String.format("%s (%.0f, %.0f, %.0f)", name, x, y, z);
    }
}
