package com.zKraft.map;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Query SQL usate nel plugin
 */
public class MySqlStatsStorage implements StatsStorage {

    private static final String UPSERT_PLAYER_SQL = """
            INSERT INTO zcrono_players (uuid, name)
            VALUES (?, ?)
            ON DUPLICATE KEY UPDATE name = VALUES(name)
            """;

    private static final String UPSERT_TIME_SQL = """
            INSERT INTO zcrono_map_times (map_key, player_uuid, best_nanos)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE best_nanos = LEAST(zcrono_map_times.best_nanos, VALUES(best_nanos))
            """;
    private static final String DELETE_PLAYER_TIME_SQL = """
            DELETE FROM zcrono_map_times
            WHERE map_key = ? AND player_uuid = ?
            """;

    private static final String DELETE_MAP_TIMES_SQL = """
            DELETE FROM zcrono_map_times WHERE map_key = ?
            """;

    private static final String SELECT_BEST_TIME_SQL = """
            SELECT best_nanos FROM zcrono_map_times
            WHERE map_key = ? AND player_uuid = ?
            """;

    private static final String SELECT_RANK_SQL = """
            WITH ordered AS (
                SELECT mt.player_uuid,
                       ROW_NUMBER() OVER (ORDER BY mt.best_nanos ASC, COALESCE(p.name, mt.player_uuid) ASC, mt.player_uuid ASC) AS position
                FROM zcrono_map_times mt
                LEFT JOIN zcrono_players p ON p.uuid = mt.player_uuid
            WHERE mt.map_key = ?
            )
            SELECT position FROM ordered WHERE player_uuid = ?
            """;

    private static final String SELECT_TOP_SQL = """
            WITH ordered AS (
                SELECT mt.player_uuid,
                       COALESCE(p.name, mt.player_uuid) AS player_name,
                       mt.best_nanos,
                       ROW_NUMBER() OVER (ORDER BY mt.best_nanos ASC, COALESCE(p.name, mt.player_uuid) ASC, mt.player_uuid ASC) AS position
                FROM zcrono_map_times mt
                LEFT JOIN zcrono_players p ON p.uuid = mt.player_uuid
                WHERE mt.map_key = ?
            )
            SELECT player_uuid, player_name, best_nanos
            FROM ordered
            WHERE position = ?
            """;

    private static final String SELECT_ALL_SQL = """
            SELECT mt.player_uuid, COALESCE(p.name, mt.player_uuid) AS player_name, mt.best_nanos
            FROM zcrono_map_times mt
            LEFT JOIN zcrono_players p ON p.uuid = mt.player_uuid
            WHERE mt.map_key = ?
            ORDER BY mt.best_nanos ASC, player_name ASC, mt.player_uuid ASC
            """;

    private static final String UPSERT_UNFINISHED_TIME_SQL = """
            INSERT INTO zcrono_map_times_uncompleted (map_key, player_uuid, best_nanos)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE best_nanos = VALUES(best_nanos)
            """;

    private static final String DELETE_UNFINISHED_PLAYER_SQL = """
            DELETE FROM zcrono_map_times_uncompleted
            WHERE map_key = ? AND player_uuid = ?
            """;

    private static final String DELETE_UNFINISHED_MAP_SQL = """
            DELETE FROM zcrono_map_times_uncompleted WHERE map_key = ?
            """;

    private static final String MOVE_FINISHED_PLAYER_SQL = """
            INSERT INTO zcrono_deleted_map_times (map_key, player_uuid, best_nanos, is_run_finished)
            SELECT map_key, player_uuid, best_nanos, 1
            FROM zcrono_map_times
            WHERE map_key = ? AND player_uuid = ?
            ON DUPLICATE KEY UPDATE best_nanos = VALUES(best_nanos)
            """;

    private static final String MOVE_UNFINISHED_PLAYER_SQL = """
            INSERT INTO zcrono_deleted_map_times (map_key, player_uuid, best_nanos, is_run_finished)
            SELECT map_key, player_uuid, best_nanos, 0
            FROM zcrono_map_times_uncompleted
            WHERE map_key = ? AND player_uuid = ?
            ON DUPLICATE KEY UPDATE best_nanos = VALUES(best_nanos)
            """;

    private static final String MOVE_FINISHED_MAP_SQL = """
            INSERT INTO zcrono_deleted_map_times (map_key, player_uuid, best_nanos, is_run_finished)
            SELECT map_key, player_uuid, best_nanos, 1
            FROM zcrono_map_times
            WHERE map_key = ?
            ON DUPLICATE KEY UPDATE best_nanos = VALUES(best_nanos)
            """;

    private static final String MOVE_UNFINISHED_MAP_SQL = """
            INSERT INTO zcrono_deleted_map_times (map_key, player_uuid, best_nanos, is_run_finished)
            SELECT map_key, player_uuid, best_nanos, 0
            FROM zcrono_map_times_uncompleted
            WHERE map_key = ?
            ON DUPLICATE KEY UPDATE best_nanos = VALUES(best_nanos)
            """;

    private static final String SELECT_ALL_UNFINISHED_SQL = """
            SELECT uc.map_key, uc.player_uuid, uc.best_nanos, COALESCE(p.name, uc.player_uuid) AS player_name
            FROM zcrono_map_times_uncompleted uc
            LEFT JOIN zcrono_players p ON p.uuid = uc.player_uuid
            """;

    private final Logger logger;
    private final DatabaseSettings settings;
    private final DatabasePreparer preparer;

    public MySqlStatsStorage(JavaPlugin plugin, DatabaseSettings settings) {
        this.logger = plugin.getLogger();
        this.settings = settings;
        this.preparer = new DatabasePreparer(logger);
        loadDriver();
    }

    @Override
    public void load() {
        try (Connection connection = getConnection()) {
            preparer.prepare(connection);
        } catch (SQLException exception) {
            logger.log(Level.SEVERE, "Impossibile connettersi al database di zCrono", exception);
        }
    }

    @Override
    public void save() {
        // Persistence happens immediately on each update; nothing to flush.
    }

    @Override
    public void recordRun(String mapName, UUID playerId, String playerName, long nanos) {
        if (mapName == null || mapName.isEmpty() || playerId == null || nanos <= 0L) {
            return;
        }

        String normalizedMap = normalizeKey(mapName);
        String resolvedName = resolvePlayerName(playerId, playerName);

        try (Connection connection = getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement playerStatement = connection.prepareStatement(UPSERT_PLAYER_SQL);
                 PreparedStatement timeStatement = connection.prepareStatement(UPSERT_TIME_SQL);
                 PreparedStatement deleteUnfinished = connection.prepareStatement(DELETE_UNFINISHED_PLAYER_SQL)) {

                playerStatement.setString(1, playerId.toString());
                playerStatement.setString(2, resolvedName);
                playerStatement.executeUpdate();

                timeStatement.setString(1, normalizedMap);
                timeStatement.setString(2, playerId.toString());
                timeStatement.setLong(3, nanos);
                timeStatement.executeUpdate();

                deleteUnfinished.setString(1, normalizedMap);
                deleteUnfinished.setString(2, playerId.toString());
                deleteUnfinished.executeUpdate();

                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException exception) {
            logger.log(Level.SEVERE, "Impossibile registrare il tempo nel database", exception);
        }
    }

    @Override
    public boolean resetPlayer(String mapName, UUID playerId) {
        if (mapName == null || playerId == null) {
            return false;
        }

        String normalizedMap = normalizeKey(mapName);

        try (Connection connection = getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            int removed = 0;

            try (PreparedStatement moveFinished = connection.prepareStatement(MOVE_FINISHED_PLAYER_SQL);
                 PreparedStatement moveUnfinished = connection.prepareStatement(MOVE_UNFINISHED_PLAYER_SQL);
                 PreparedStatement deleteFinished = connection.prepareStatement(DELETE_PLAYER_TIME_SQL);
                 PreparedStatement deleteUnfinished = connection.prepareStatement(DELETE_UNFINISHED_PLAYER_SQL)) {

                moveFinished.setString(1, normalizedMap);
                moveFinished.setString(2, playerId.toString());
                moveFinished.executeUpdate();

                moveUnfinished.setString(1, normalizedMap);
                moveUnfinished.setString(2, playerId.toString());
                moveUnfinished.executeUpdate();

                deleteFinished.setString(1, normalizedMap);
                deleteFinished.setString(2, playerId.toString());
                removed += deleteFinished.executeUpdate();

                deleteUnfinished.setString(1, normalizedMap);
                deleteUnfinished.setString(2, playerId.toString());
                removed += deleteUnfinished.executeUpdate();

                connection.commit();
                return removed > 0;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }

        } catch (SQLException exception) {
            logger.log(Level.SEVERE, "Impossibile eliminare il tempo dal database", exception);
            return false;
        }
    }

    @Override
    public boolean resetMap(String mapName) {
        if (mapName == null) {
            return false;
        }
        String normalizedMap = normalizeKey(mapName);

        try (Connection connection = getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            int removed = 0;

            try (PreparedStatement moveFinished = connection.prepareStatement(MOVE_FINISHED_MAP_SQL);
                 PreparedStatement moveUnfinished = connection.prepareStatement(MOVE_UNFINISHED_MAP_SQL);
                 PreparedStatement deleteFinished = connection.prepareStatement(DELETE_MAP_TIMES_SQL);
                 PreparedStatement deleteUnfinished = connection.prepareStatement(DELETE_UNFINISHED_MAP_SQL)) {

                moveFinished.setString(1, normalizedMap);
                moveFinished.executeUpdate();

                moveUnfinished.setString(1, normalizedMap);
                moveUnfinished.executeUpdate();

                deleteFinished.setString(1, normalizedMap);
                removed += deleteFinished.executeUpdate();

                deleteUnfinished.setString(1, normalizedMap);
                removed += deleteUnfinished.executeUpdate();

                connection.commit();
                return removed > 0;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException exception) {
            logger.log(Level.SEVERE, "Impossibile eliminare i tempi della mappa dal database", exception);
            return false;
        }
    }

    @Override
    public OptionalLong getBestTime(String mapName, UUID playerId) {
        if (mapName == null || playerId == null) {
            return OptionalLong.empty();
        }

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BEST_TIME_SQL)) {
            statement.setString(1, normalizeKey(mapName));
            statement.setString(2, playerId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return OptionalLong.of(resultSet.getLong(1));
                }
            }
        } catch (SQLException exception) {
            logger.log(Level.SEVERE, "Impossibile leggere il tempo dal database", exception);
        }
        return OptionalLong.empty();
    }

    @Override
    public OptionalInt getRank(String mapName, UUID playerId) {
        if (mapName == null || playerId == null) {
            return OptionalInt.empty();
        }

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_RANK_SQL)) {
            statement.setString(1, normalizeKey(mapName));
            statement.setString(2, playerId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return OptionalInt.of(resultSet.getInt(1));
                }
            }
        } catch (SQLException exception) {
            logger.log(Level.SEVERE, "Impossibile calcolare la posizione nel database", exception);
        }
        return OptionalInt.empty();
    }

    @Override
    public Optional<StatsManager.LeaderboardEntry> getTopEntry(String mapName, int position) {
        if (mapName == null || position <= 0) {
            return Optional.empty();
        }

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_TOP_SQL)) {
            statement.setString(1, normalizeKey(mapName));
            statement.setInt(2, position);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    UUID playerId = safeUuid(resultSet.getString(1));
                    if (playerId == null) {
                        return Optional.empty();
                    }
                    String name = resultSet.getString(2);
                    long nanos = resultSet.getLong(3);
                    return Optional.of(new StatsManager.LeaderboardEntry(playerId, fallbackName(playerId, name), nanos));
                }
            }
        } catch (SQLException exception) {
            logger.log(Level.SEVERE, "Impossibile recuperare il podio dal database", exception);
        }
        return Optional.empty();
    }

    @Override
    public List<StatsManager.LeaderboardEntry> getEntries(String mapName) {
        if (mapName == null) {
            return Collections.emptyList();
        }

        List<StatsManager.LeaderboardEntry> entries = new ArrayList<>();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ALL_SQL)) {
            statement.setString(1, normalizeKey(mapName));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    UUID playerId = safeUuid(resultSet.getString(1));
                    if (playerId == null) {
                        continue;
                    }
                    String name = resultSet.getString(2);
                    long nanos = resultSet.getLong(3);
                    entries.add(new StatsManager.LeaderboardEntry(playerId, fallbackName(playerId, name), nanos));
                }
            }
        } catch (SQLException exception) {
            logger.log(Level.SEVERE, "Impossibile leggere la classifica dal database", exception);
        }

        return Collections.unmodifiableList(entries);
    }

    @Override
    public void saveOngoingRun(String mapName, UUID playerId, String playerName, long nanos) {
        if (mapName == null || mapName.isEmpty() || playerId == null || nanos < 0L) {
            return;
        }

        String normalizedMap = normalizeKey(mapName);
        String resolvedName = resolvePlayerName(playerId, playerName);

        try (Connection connection = getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement playerStatement = connection.prepareStatement(UPSERT_PLAYER_SQL);
                 PreparedStatement timeStatement = connection.prepareStatement(UPSERT_UNFINISHED_TIME_SQL)) {

                playerStatement.setString(1, playerId.toString());
                playerStatement.setString(2, resolvedName);
                playerStatement.executeUpdate();

                timeStatement.setString(1, normalizedMap);
                timeStatement.setString(2, playerId.toString());
                timeStatement.setLong(3, nanos);
                timeStatement.executeUpdate();

                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException exception) {
            logger.log(Level.SEVERE, "Impossibile registrare il tempo nel database", exception);
        }
    }

    @Override
    public List<StatsManager.OngoingRun> getAllOngoingRuns() {
        List<StatsManager.OngoingRun> runs = new ArrayList<>();

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ALL_UNFINISHED_SQL);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                String mapKey = resultSet.getString(1);
                UUID playerId = safeUuid(resultSet.getString(2));
                if (mapKey == null || playerId == null) {
                    continue;
                }
                long nanos = resultSet.getLong(3);
                String name = resultSet.getString(4);
                runs.add(new StatsManager.OngoingRun(mapKey, playerId, fallbackName(playerId, name), nanos));
            }
        } catch (SQLException exception) {
            logger.log(Level.SEVERE, "Impossibile recuperare le corse attive dal database", exception);
        }
        return Collections.unmodifiableList(runs);
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(settings.jdbcUrl(), settings.username(), settings.password());
    }

    private static void loadDriver() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException ignored) {
        }
    }

    private String normalizeKey(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private UUID safeUuid(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String resolvePlayerName(UUID playerId, String provided) {
        if (provided != null && !provided.isEmpty()) {
            return provided;
        }
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
        String name = offlinePlayer.getName();
        if (name != null && !name.isEmpty()) {
            return name;
        }
        return playerId.toString();
    }

    private String fallbackName(UUID playerId, String stored) {
        if (stored != null && !stored.isEmpty()) {
            return stored;
        }
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
        String name = offlinePlayer.getName();
        if (name != null && !name.isEmpty()) {
            return name;
        }
        return playerId.toString();
    }
}
