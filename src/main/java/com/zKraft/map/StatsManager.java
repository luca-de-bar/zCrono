package com.zKraft.map;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Stores personal bests and leaderboards for configured maps.
 */
public class StatsManager {

    private final JavaPlugin plugin;
    private final File dataFile;
    private final Map<String, Map<UUID, Long>> mapTimes = new HashMap<>();
    private final Map<UUID, String> playerNames = new HashMap<>();
    private boolean dirty;

    public StatsManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        ensureDataFolder();
        mapTimes.clear();
        playerNames.clear();
        dirty = false;

        if (!dataFile.exists()) {
            return;
        }

        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(dataFile);

        ConfigurationSection playersSection = configuration.getConfigurationSection("players");
        if (playersSection != null) {
            for (String key : playersSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    String name = playersSection.getString(key);
                    if (name != null && !name.isEmpty()) {
                        playerNames.put(uuid, name);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        ConfigurationSection mapsSection = configuration.getConfigurationSection("maps");
        if (mapsSection == null) {
            return;
        }

        for (String mapKey : mapsSection.getKeys(false)) {
            ConfigurationSection mapSection = mapsSection.getConfigurationSection(mapKey);
            if (mapSection == null) {
                continue;
            }

            ConfigurationSection timesSection = mapSection.getConfigurationSection("times");
            if (timesSection == null) {
                continue;
            }

            Map<UUID, Long> times = new HashMap<>();
            for (String uuidKey : timesSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidKey);
                    long time = timesSection.getLong(uuidKey);
                    if (time > 0L) {
                        times.put(uuid, time);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }

            if (!times.isEmpty()) {
                mapTimes.put(normalizeKey(mapKey), times);
            }
        }
    }

    public void save() {
        if (!dirty && dataFile.exists()) {
            return;
        }

        ensureDataFolder();

        YamlConfiguration configuration = new YamlConfiguration();

        ConfigurationSection playersSection = configuration.createSection("players");
        for (Map.Entry<UUID, String> entry : playerNames.entrySet()) {
            playersSection.set(entry.getKey().toString(), entry.getValue());
        }

        ConfigurationSection mapsSection = configuration.createSection("maps");
        for (Map.Entry<String, Map<UUID, Long>> entry : mapTimes.entrySet()) {
            ConfigurationSection mapSection = mapsSection.createSection(entry.getKey());
            ConfigurationSection timesSection = mapSection.createSection("times");
            for (Map.Entry<UUID, Long> timeEntry : entry.getValue().entrySet()) {
                timesSection.set(timeEntry.getKey().toString(), timeEntry.getValue());
            }
        }

        try {
            configuration.save(dataFile);
            dirty = false;
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Impossibile salvare data.yml", exception);
        }
    }

    public void recordRun(com.zKraft.map.Map map, Player player, Duration duration) {
        if (map == null || player == null || duration == null || duration.isNegative()) {
            return;
        }

        long nanos = duration.toNanos();
        if (nanos <= 0L) {
            return;
        }

        String mapKey = normalizeKey(map.getName());
        Map<UUID, Long> times = mapTimes.computeIfAbsent(mapKey, unused -> new HashMap<>());

        UUID playerId = player.getUniqueId();
        boolean changed = updatePlayerName(playerId, player.getName());

        Long currentBest = times.get(playerId);
        if (currentBest == null || nanos < currentBest) {
            times.put(playerId, nanos);
            changed = true;
        }

        if (changed) {
            dirty = true;
            save();
        }
    }

    public boolean resetPlayer(String mapName, UUID playerId) {
        if (mapName == null || playerId == null) {
            return false;
        }

        Map<UUID, Long> times = mapTimes.get(normalizeKey(mapName));
        if (times == null) {
            return false;
        }

        if (times.remove(playerId) == null) {
            return false;
        }

        if (times.isEmpty()) {
            mapTimes.remove(normalizeKey(mapName));
        }

        dirty = true;
        save();
        return true;
    }

    public boolean resetMap(String mapName) {
        if (mapName == null) {
            return false;
        }

        Map<UUID, Long> removed = mapTimes.remove(normalizeKey(mapName));
        if (removed == null) {
            return false;
        }

        dirty = true;
        save();
        return true;
    }

    public OptionalLong getBestTime(String mapName, UUID playerId) {
        if (mapName == null || playerId == null) {
            return OptionalLong.empty();
        }

        Map<UUID, Long> times = mapTimes.get(normalizeKey(mapName));
        if (times == null) {
            return OptionalLong.empty();
        }

        Long value = times.get(playerId);
        return value != null ? OptionalLong.of(value) : OptionalLong.empty();
    }

    public OptionalInt getRank(String mapName, UUID playerId) {
        if (mapName == null || playerId == null) {
            return OptionalInt.empty();
        }

        List<LeaderboardEntry> entries = getEntries(mapName);
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).playerId().equals(playerId)) {
                return OptionalInt.of(index + 1);
            }
        }

        return OptionalInt.empty();
    }

    public Optional<LeaderboardEntry> getTopEntry(String mapName, int position) {
        if (mapName == null || position <= 0) {
            return Optional.empty();
        }

        List<LeaderboardEntry> entries = getEntries(mapName);
        if (position > entries.size()) {
            return Optional.empty();
        }

        return Optional.of(entries.get(position - 1));
    }

    public List<LeaderboardEntry> getEntries(String mapName) {
        if (mapName == null) {
            return Collections.emptyList();
        }

        Map<UUID, Long> times = mapTimes.get(normalizeKey(mapName));
        if (times == null || times.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map.Entry<UUID, Long>> entries = new ArrayList<>(times.entrySet());
        entries.sort(leaderboardComparator());

        List<LeaderboardEntry> leaderboard = new ArrayList<>(entries.size());
        for (Map.Entry<UUID, Long> entry : entries) {
            leaderboard.add(new LeaderboardEntry(entry.getKey(), resolveName(entry.getKey()), entry.getValue()));
        }
        return Collections.unmodifiableList(leaderboard);
    }

    private Comparator<Map.Entry<UUID, Long>> leaderboardComparator() {
        return Comparator.<Map.Entry<UUID, Long>>comparingLong(Map.Entry::getValue)
                .thenComparing(entry -> resolveName(entry.getKey()).toLowerCase(Locale.ROOT))
                .thenComparing(entry -> entry.getKey().toString());
    }

    private String resolveName(UUID uuid) {
        String stored = playerNames.get(uuid);
        if (stored != null && !stored.isEmpty()) {
            return stored;
        }

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        String name = offlinePlayer.getName();
        if (name != null && !name.isEmpty()) {
            return name;
        }

        return uuid.toString();
    }

    private boolean updatePlayerName(UUID uuid, String name) {
        if (uuid == null || name == null || name.isEmpty()) {
            return false;
        }

        String previous = playerNames.put(uuid, name);
        return previous == null || !previous.equals(name);
    }

    private void ensureDataFolder() {
        File folder = plugin.getDataFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Impossibile creare la cartella dati per zCrono");
        }
    }

    private String normalizeKey(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    public record LeaderboardEntry(UUID playerId, String name, long timeNanos) {
    }
}
