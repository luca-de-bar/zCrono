package com.zKraft.map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Handles the persistence and lookup of configured maps.
 */
public class MapManager {

    private final JavaPlugin plugin;
    private final java.util.Map<String, Map> maps = new LinkedHashMap<>();
    private final Logger logger;

    public MapManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();

        maps.clear();
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection mapsSection = config.getConfigurationSection("maps");
        if (mapsSection == null) {
            return;
        }

        for (String mapName : mapsSection.getKeys(false)) {
            ConfigurationSection mapSection = mapsSection.getConfigurationSection(mapName);
            if (mapSection == null) {
                continue;
            }

            Map map = new Map(mapName);

            Location start = LocationSerializer.readLocation(mapSection.getConfigurationSection("start"));
            map.setStart(start);

            Location end = LocationSerializer.readLocation(mapSection.getConfigurationSection("end"));
            map.setEnd(end);

            List<java.util.Map<?, ?>> checkpointList = mapSection.getMapList("checkpoints");
            for (java.util.Map<?, ?> rawCheckpoint : checkpointList) {
                String worldName = asString(rawCheckpoint.get("world"));
                World world = worldName != null ? Bukkit.getWorld(worldName) : null;
                double x = asDouble(rawCheckpoint.get("x"));
                double y = asDouble(rawCheckpoint.get("y"));
                double z = asDouble(rawCheckpoint.get("z"));
                Object rawYaw = rawCheckpoint.containsKey("yaw") ? rawCheckpoint.get("yaw") : 0.0;
                Object rawPitch = rawCheckpoint.containsKey("pitch") ? rawCheckpoint.get("pitch") : 0.0;
                float yaw = (float) asDouble(rawYaw);
                float pitch = (float) asDouble(rawPitch);
                double radius = asDouble(rawCheckpoint.get("radius"));

                Location location = new Location(world, x, y, z, yaw, pitch);
                map.addCheckpoint(new MapCheckpoint(location, radius));
            }

            maps.put(normalizeKey(mapName), map);
        }
    }

    public void save() {
        FileConfiguration config = plugin.getConfig();
        config.set("maps", null);
        ConfigurationSection mapsSection = config.createSection("maps");

        for (Map map : maps.values()) {
            ConfigurationSection mapSection = mapsSection.createSection(map.getName());

            if (map.getStart() != null) {
                ConfigurationSection startSection = mapSection.createSection("start");
                LocationSerializer.writeLocation(startSection, map.getStart());
            }

            if (map.getEnd() != null) {
                ConfigurationSection endSection = mapSection.createSection("end");
                LocationSerializer.writeLocation(endSection, map.getEnd());
            }

            if (!map.getCheckpoints().isEmpty()) {
                List<java.util.Map<String, Object>> checkpointList = new ArrayList<>();

                for (MapCheckpoint checkpoint : map.getCheckpoints()) {
                    java.util.Map<String, Object> checkpointSection = new LinkedHashMap<>();
                    Location location = checkpoint.getLocation();
                    World world = location.getWorld();
                    checkpointSection.put("world", world != null ? world.getName() : null);
                    checkpointSection.put("x", location.getX());
                    checkpointSection.put("y", location.getY());
                    checkpointSection.put("z", location.getZ());
                    checkpointSection.put("yaw", location.getYaw());
                    checkpointSection.put("pitch", location.getPitch());
                    checkpointSection.put("radius", checkpoint.getRadius());
                    checkpointList.add(checkpointSection);
                }

                mapSection.set("checkpoints", checkpointList);
            }
        }

        plugin.saveConfig();
    }

    public boolean createMap(String name) {
        String key = normalizeKey(name);
        if (maps.containsKey(key)) {
            return false;
        }

        maps.put(key, new Map(name));
        save();
        return true;
    }

    public boolean deleteMap(String name) {
        String key = normalizeKey(name);
        if (maps.remove(key) != null) {
            save();
            return true;
        }
        return false;
    }

    public Map getMap(String name) {
        return maps.get(normalizeKey(name));
    }

    public Collection<Map> getMaps() {
        return Collections.unmodifiableCollection(maps.values());
    }

    public List<String> getMapNames() {
        List<String> names = new ArrayList<>();
        for (Map map : maps.values()) {
            names.add(map.getName());
        }
        return names;
    }

    public void updateStart(String name, Location location) {
        Map map = getMap(name);
        if (map == null) {
            logger.warning("Attempted to set start for unknown map " + name);
            return;
        }

        map.setStart(location);
        save();
    }

    public void updateEnd(String name, Location location) {
        Map map = getMap(name);
        if (map == null) {
            logger.warning("Attempted to set end for unknown map " + name);
            return;
        }

        map.setEnd(location);
        save();
    }

    public void addCheckpoint(String name, MapCheckpoint checkpoint) {
        Map map = getMap(name);
        if (map == null) {
            logger.warning("Attempted to add checkpoint for unknown map " + name);
            return;
        }

        map.addCheckpoint(checkpoint);
        save();
    }

    private static String normalizeKey(String key) {
        return key.toLowerCase(Locale.ROOT);
    }

    private static String asString(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private static double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return 0.0;
    }
}
