package com.zKraft;

import com.zKraft.parkour.ParkourCommand;
import com.zKraft.parkour.ParkourManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class zCrono extends JavaPlugin {

    private ParkourManager parkourManager;

    @Override
    public void onEnable() {
        parkourManager = new ParkourManager(this);
        parkourManager.load();

        PluginCommand command = getCommand("parkour");
        if (command != null) {
            ParkourCommand parkourCommand = new ParkourCommand(parkourManager);
            command.setExecutor(parkourCommand);
            command.setTabCompleter(parkourCommand);
        } else {
            getLogger().severe("Impossibile registrare il comando /parkour.");
        }
    }

    @Override
    public void onDisable() {
        if (parkourManager != null) {
            parkourManager.save();
        }
    }

    public ParkourManager getParkourManager() {
        return parkourManager;
    }
}
