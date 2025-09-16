package com.zKraft.map;

import org.bukkit.Location;

/**
 * Represents a checkpoint inside a configured map.
 */
public class MapCheckpoint {

    private final Location location;
    private final double radius;

    public MapCheckpoint(Location location, double radius) {
        this.location = location;
        this.radius = radius;
    }

    public Location getLocation() {
        return location;
    }

    public double getRadius() {
        return radius;
    }
}
