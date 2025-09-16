package com.zKraft.parkour;

import org.bukkit.Location;

/**
 * Represents a checkpoint inside a parkour map.
 */
public class ParkourCheckpoint {

    private final Location location;
    private final double radius;

    public ParkourCheckpoint(Location location, double radius) {
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
