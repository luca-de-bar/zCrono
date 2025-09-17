package com.zKraft.map;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ensures the required database tables and indexes exist.
 */
public class DatabasePreparer {

    private static final String CREATE_PLAYERS_TABLE = """
            CREATE TABLE IF NOT EXISTS zcrono_players (
                uuid CHAR(36) NOT NULL,
                name VARCHAR(64) NOT NULL,
                PRIMARY KEY (uuid),
                KEY idx_zcrono_players_name (name)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
            """;

    private static final String CREATE_MAP_TIMES_TABLE = """
            CREATE TABLE IF NOT EXISTS zcrono_map_times (
                map_key VARCHAR(128) NOT NULL,
                player_uuid CHAR(36) NOT NULL,
                best_nanos BIGINT NOT NULL,
                PRIMARY KEY (map_key, player_uuid),
                KEY idx_zcrono_map_times_map (map_key, best_nanos, player_uuid),
                KEY idx_zcrono_map_times_player (player_uuid)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
            """;

    private final Logger logger;

    public DatabasePreparer(Logger logger) {
        this.logger = logger;
    }

    public void prepare(Connection connection) {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(CREATE_PLAYERS_TABLE);
            statement.executeUpdate(CREATE_MAP_TIMES_TABLE);
        } catch (SQLException exception) {
            logger.log(Level.SEVERE, "Impossibile preparare il database di zCrono", exception);
        }
    }
}
