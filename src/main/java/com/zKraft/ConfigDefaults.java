package com.zKraft;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConfigDefaults {

    private ConfigDefaults() {
    }

    public static void ensure(JavaPlugin plugin) {
        if (plugin == null) {
            return;
        }

        FileConfiguration config = plugin.getConfig();
        applyGeneralDefaults(config);
        applyPersistenceDefaults(config);
        config.options().copyDefaults(true);
        plugin.saveConfig();
    }

    private static void applyGeneralDefaults(FileConfiguration config) {
        config.addDefault("countdownSeconds", 3);
        config.addDefault("startMessage", "&#E43A96⏱ &#545EB6La corsa parte tra {seconds}...");
        config.addDefault("goMessage", "🏁 GO!");
        config.addDefault("endMessage", "&aHai completato il percorso in {time}.");
        config.addDefault("endChatMessage", "&aHai completato il percorso in {time}.");
        config.addDefault("leaveSuccessMessage", "Hai interrotto correttamente la corsa.");
        config.addDefault("leaveNoActiveMessage", "Non ci sono corse attive da interrompere.");
    }

    private static void applyPersistenceDefaults(FileConfiguration config) {
        config.addDefault("persistence.use-mysql", false);
        config.addDefault("persistence.mysql.host", "localhost");
        config.addDefault("persistence.mysql.port", 3306);
        config.addDefault("persistence.mysql.database", "zcrono");
        config.addDefault("persistence.mysql.username", "zcrono");
        config.addDefault("persistence.mysql.password", "changeme");
        config.addDefault("persistence.mysql.use-ssl", true);
    }
}
