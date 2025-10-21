package com.zKraft.map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Salva i personal bests
 */
public class StatsManager {

    private final JavaPlugin plugin;
    private StatsStorage storage;

    public StatsManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        storage = createStorage();
        storage.load();
    }

    public void save() {
        if (storage != null) {
            storage.save();
        }
    }

    public void recordRun(com.zKraft.map.Map map, Player player, Duration duration) {
        if (storage == null || map == null || player == null || duration == null || duration.isNegative()) {
            return;
        }

        long nanos = duration.toNanos();
        if (nanos <= 0L) {
            return;
        }

        storage.recordRun(map.getName(), player.getUniqueId(), player.getName(), nanos);
    }

    public boolean resetPlayer(String mapName, UUID playerId) {
        if (storage == null || mapName == null || playerId == null) {
            return false;
        }
        return storage.resetPlayer(mapName, playerId);
    }

    public boolean resetMap(String mapName) {
        if (storage == null || mapName == null) {
            return false;
        }
        return storage.resetMap(mapName);
    }

    public OptionalLong getBestTime(String mapName, UUID playerId) {
        if (storage == null || mapName == null || playerId == null) {
            return OptionalLong.empty();
        }
        return storage.getBestTime(mapName, playerId);
    }

    public OptionalInt getRank(String mapName, UUID playerId) {
        if (storage == null || mapName == null || playerId == null) {
            return OptionalInt.empty();
        }
        return storage.getRank(mapName, playerId);
    }

    public Optional<LeaderboardEntry> getTopEntry(String mapName, int position) {
        if (storage == null || mapName == null || position <= 0) {
            return Optional.empty();
        }
        return storage.getTopEntry(mapName, position);
    }

    public List<LeaderboardEntry> getEntries(String mapName) {
        if (storage == null || mapName == null) {
            return Collections.emptyList();
        }
        return storage.getEntries(mapName);
    }

    private StatsStorage createStorage() {
        ConfigurationSection persistenceSection = plugin.getConfig().getConfigurationSection("persistence");
        boolean useMysql = persistenceSection != null && persistenceSection.getBoolean("use-mysql", false);

        if (useMysql) {
            try {
                DatabaseSettings settings = readDatabaseSettings(persistenceSection.getConfigurationSection("mysql"));
                return new MySqlStatsStorage(plugin, settings);
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().log(Level.SEVERE, "Configurazione MySQL non valida, ricado su YAML", exception);
            }
        }

        return new YamlStatsStorage(plugin);
    }

    private DatabaseSettings readDatabaseSettings(ConfigurationSection section) {
        if (section == null) {
            throw new IllegalArgumentException("Sezione mysql mancante");
        }

        String host = section.getString("host");
        int port = section.getInt("port", 3306);
        String database = section.getString("database");
        String username = section.getString("username");
        String password = section.getString("password");
        boolean useSsl = section.getBoolean("use-ssl", true);

        if (isBlank(host) || isBlank(database) || isBlank(username)) {
            throw new IllegalArgumentException("Host, database o username non configurati correttamente");
        }

        if (password == null) {
            password = "";
        }

        return new DatabaseSettings(host, port, database, username, password, useSsl);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public record LeaderboardEntry(UUID playerId, String name, long timeNanos) {
    }
}
