package com.zKraft;

import com.zKraft.map.MapCommand;
import com.zKraft.map.MapManager;
import com.zKraft.map.MapRuntimeManager;
import com.zKraft.map.StatsManager;
import com.zKraft.map.ZCronoPlaceholderExpansion;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class zCrono extends JavaPlugin {

    private MapManager mapManager;
    private MapRuntimeManager runtimeManager;
    private StatsManager statsManager;
    private ZCronoPlaceholderExpansion placeholderExpansion;

    @Override
    public void onEnable() {
        mapManager = new MapManager(this);
        mapManager.load();

        statsManager = new StatsManager(this);
        statsManager.load();

        runtimeManager = new MapRuntimeManager(mapManager, statsManager);
        getServer().getPluginManager().registerEvents(runtimeManager, this);

        PluginCommand command = getCommand("zcrono");
        if (command != null) {
            MapCommand mapCommand = new MapCommand(mapManager);
            command.setExecutor(mapCommand);
            command.setTabCompleter(mapCommand);
        } else {
            getLogger().severe("Impossibile registrare il comando /zcrono.");
        }

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderExpansion = new ZCronoPlaceholderExpansion(this, mapManager, statsManager);
            placeholderExpansion.register();
        } else {
            getLogger().info("PlaceholderAPI non trovato, i placeholder di zCrono sono disabilitati.");
        }
    }

    @Override
    public void onDisable() {
        if (mapManager != null) {
            mapManager.save();
        }

        if (runtimeManager != null) {
            runtimeManager.shutdown();
        }

        if (statsManager != null) {
            statsManager.save();
        }

        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
        }
    }

    public MapManager getMapManager() {
        return mapManager;
    }
}
