package com.zKraft.parkour;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Utility class used to serialize and deserialize Bukkit {@link Location} instances.
 */
public final class LocationSerializer {

    private LocationSerializer() {
    }

    public static void writeLocation(ConfigurationSection section, Location location) {
        if (section == null || location == null) {
            return;
        }

        World world = location.getWorld();
        section.set("world", world != null ? world.getName() : null);
        section.set("x", location.getX());
        section.set("y", location.getY());
        section.set("z", location.getZ());
        section.set("yaw", location.getYaw());
        section.set("pitch", location.getPitch());
    }

    public static Location readLocation(ConfigurationSection section) {
        if (section == null) {
            return null;
        }

        String worldName = section.getString("world");
        if (worldName == null || worldName.isEmpty()) {
            return null;
        }

        World world = Bukkit.getWorld(worldName);
        double x = section.getDouble("x");
        double y = section.getDouble("y");
        double z = section.getDouble("z");
        float yaw = (float) section.getDouble("yaw", 0.0);
        float pitch = (float) section.getDouble("pitch", 0.0);

        return new Location(world, x, y, z, yaw, pitch);
    }
}
