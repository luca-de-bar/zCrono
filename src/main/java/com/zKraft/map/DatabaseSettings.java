package com.zKraft.map;

import java.util.Locale;

/**
 * Impostazioni database
 */
public class DatabaseSettings {

    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final boolean useSsl;

    public DatabaseSettings(String host, int port, String database, String username, String password, boolean useSsl) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.useSsl = useSsl;
    }

    public String jdbcUrl() {
        String ssl = Boolean.toString(useSsl).toLowerCase(Locale.ROOT);
        return "jdbc:mysql://" + host + ':' + port + '/' + database
                + "?useSSL=" + ssl
                + "&allowPublicKeyRetrieval=true"
                + "&autoReconnect=true"
                + "&characterEncoding=UTF-8"
                + "&serverTimezone=UTC";
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }
}
