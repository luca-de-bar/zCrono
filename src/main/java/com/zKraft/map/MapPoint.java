package com.zKraft.map;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Rappresenta un punto di start e/o end di una mappa e le sue configurazioni.
 */
public class MapPoint {

    private static final double DEFAULT_POINT_RADIUS = 0.5D;
    private static final double VERTICAL_TOLERANCE = 1.5D;

    private final Location location;
    private final double radius;

    public MapPoint(Location location, double radius) {
        if (location == null) {
            throw new IllegalArgumentException("location");
        }
        this.location = location.clone();
        this.radius = Math.max(radius, 0.0D);
    }

    public Location getLocation() {
        return location.clone();
    }

    public double getRadius() {
        return radius;
    }

    public double getEffectiveRadius() {
        return radius > 0.0D ? radius : DEFAULT_POINT_RADIUS;
    }

    public boolean contains(Location other) {
        if (other == null) {
            return false;
        }

        World world = location.getWorld();
        World otherWorld = other.getWorld();
        if (world == null || otherWorld == null || !world.equals(otherWorld)) {
            return false;
        }

        double dx = other.getX() - location.getX();
        double dz = other.getZ() - location.getZ();
        double limit = getEffectiveRadius();
        if ((dx * dx + dz * dz) > limit * limit) {
            return false;
        }

        double dy = Math.abs(other.getY() - location.getY());
        return dy <= VERTICAL_TOLERANCE;
    }
}
