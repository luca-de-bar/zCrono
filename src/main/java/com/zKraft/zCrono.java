package com.zKraft;

import com.zKraft.map.MapCommand;
import com.zKraft.map.MapManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class zCrono extends JavaPlugin {

    private MapManager mapManager;

    @Override
    public void onEnable() {
        mapManager = new MapManager(this);
        mapManager.load();

        PluginCommand command = getCommand("zcrono");
        if (command != null) {
            MapCommand mapCommand = new MapCommand(mapManager);
            command.setExecutor(mapCommand);
            command.setTabCompleter(mapCommand);
        } else {
            getLogger().severe("Impossibile registrare il comando /zcrono.");
        }
    }

    @Override
    public void onDisable() {
        if (mapManager != null) {
            mapManager.save();
        }
    }

    public MapManager getMapManager() {
        return mapManager;
    }
}
