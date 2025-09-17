package com.zKraft.map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

public class MapCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "zcrono.admin";

    private final JavaPlugin plugin;
    private final MapManager manager;
    private final StatsManager statsManager;
    private final MapRuntimeManager runtimeManager;

    public MapCommand(JavaPlugin plugin, MapManager manager, StatsManager statsManager, MapRuntimeManager runtimeManager) {
        this.plugin = plugin;
        this.manager = manager;
        this.statsManager = statsManager;
        this.runtimeManager = runtimeManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        if (subCommand.equals("leave")) {
            handleLeave(sender);
            return true;
        }

        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage("Non hai il permesso per usare questo comando.");
            return true;
        }

        switch (subCommand) {
            case "create":
                handleCreate(sender, label, args);
                break;
            case "delete":
            case "remove":
                handleDelete(sender, label, args);
                break;
            case "setstart":
                handleSetPoint(sender, label, args, true);
                break;
            case "setend":
                handleSetPoint(sender, label, args, false);
                break;
            case "map":
                handleMapSubcommand(sender, label, args);
                break;
            case "info":
                handleInfo(sender, label, args);
                break;
            case "resetplayer":
                handleResetPlayer(sender, label, args);
                break;
            case "resetmap":
                handleResetMap(sender, label, args);
                break;
            case "reload":
                handleReload(sender);
                break;
            default:
                sender.sendMessage("Comando sconosciuto. Usa /" + label + " per la lista completa.");
                break;
        }
        return true;
    }

    private void handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo un giocatore può abbandonare una corsa attiva.");
            return;
        }

        if (runtimeManager.leaveTimer(player)) {
            sender.sendMessage("Cronometro interrotto.");
        } else {
            sender.sendMessage("Non hai nessuna corsa attiva.");
        }
    }

    private void handleMapSubcommand(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Uso corretto: /" + label + " map list");
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("list")) {
            handleList(sender);
            return;
        }

        sender.sendMessage("Sotto-comando sconosciuto. Usa /" + label + " map list.");
    }

    private void handleCreate(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Uso corretto: /" + label + " " + args[0] + " <nome>");
            return;
        }

        String name = args[1];
        if (manager.createMap(name)) {
            sender.sendMessage("Mappa \"" + name + "\" creata.");
        } else {
            sender.sendMessage("Esiste già una mappa con questo nome.");
        }
    }

    private void handleDelete(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Uso corretto: /" + label + " " + args[0] + " <nome>");
            return;
        }

        String name = args[1];
        if (manager.deleteMap(name)) {
            sender.sendMessage("Mappa \"" + name + "\" eliminata.");
        } else {
            sender.sendMessage("Nessuna mappa trovata con questo nome.");
        }
    }

    private void handleSetPoint(CommandSender sender, String label, String[] args, boolean start) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo un giocatore può usare questo comando.");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("Uso corretto: /" + label + " " + args[0] + " <nome> [radius]");
            return;
        }

        String mapName = args[1];
        Map map = manager.getMap(mapName);
        if (map == null) {
            sender.sendMessage("Nessuna mappa trovata con questo nome.");
            return;
        }

        Location location = player.getLocation().clone();
        double radius = 0.0D;
        if (args.length >= 3) {
            try {
                radius = Integer.parseInt(args[2]);
                if (radius < 0) {
                    sender.sendMessage("Il radius deve essere un numero intero positivo.");
                    return;
                }
            } catch (NumberFormatException exception) {
                sender.sendMessage("Il radius deve essere un numero intero.");
                return;
            }
        }

        if (start) {
            manager.updateStart(mapName, location, radius);
            sender.sendMessage("Punto di partenza per \"" + mapName + "\" salvato.");
        } else {
            manager.updateEnd(mapName, location, radius);
            sender.sendMessage("Punto di arrivo per \"" + mapName + "\" salvato.");
        }
    }

    private void handleList(CommandSender sender) {
        List<String> names = manager.getMapNames();
        if (names.isEmpty()) {
            sender.sendMessage("Non ci sono mappe configurate.");
            return;
        }

        sender.sendMessage("Mappe disponibili: " + String.join(", ", names));
    }

    private void handleInfo(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Uso corretto: /" + label + " " + args[0] + " <nome>");
            return;
        }

        String mapName = args[1];
        Map map = manager.getMap(mapName);
        if (map == null) {
            sender.sendMessage("Nessuna mappa trovata con questo nome.");
            return;
        }

        sender.sendMessage("Informazioni per \"" + map.getName() + "\":");
        sender.sendMessage("Start: " + formatPoint(map.getStart()));
        sender.sendMessage("End: " + formatPoint(map.getEnd()));
    }

    private void handleResetPlayer(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("Uso corretto: /" + label + " " + args[0] + " <mappa> <giocatore>");
            return;
        }

        String mapName = args[1];
        Map map = manager.getMap(mapName);
        if (map == null) {
            sender.sendMessage("Nessuna mappa trovata con questo nome.");
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        UUID targetId = target.getUniqueId();

        runtimeManager.resetPlayerSession(targetId);

        if (statsManager.resetPlayer(map.getName(), targetId)) {
            String displayName = target.getName() != null ? target.getName() : targetId.toString();
            sender.sendMessage("Tempi di " + displayName + " su \"" + map.getName() + "\" azzerati.");
        } else {
            sender.sendMessage("Nessun tempo trovato per il giocatore su questa mappa.");
        }
    }

    private void handleResetMap(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Uso corretto: /" + label + " " + args[0] + " <mappa>");
            return;
        }

        String mapName = args[1];
        Map map = manager.getMap(mapName);
        if (map == null) {
            sender.sendMessage("Nessuna mappa trovata con questo nome.");
            return;
        }

        boolean removed = statsManager.resetMap(map.getName());
        runtimeManager.resetSessionsForMap(map.getName());

        if (removed) {
            sender.sendMessage("Tutti i tempi per \"" + map.getName() + "\" sono stati rimossi.");
        } else {
            sender.sendMessage("Non ci sono tempi registrati per questa mappa.");
        }
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage("Comandi zCrono:");
        sender.sendMessage("/" + label + " create <nome> - crea una nuova mappa");
        sender.sendMessage("/" + label + " delete <nome> - rimuove una mappa");
        sender.sendMessage("/" + label + " setstart <nome> [radius] - imposta l'area di partenza");
        sender.sendMessage("/" + label + " setend <nome> [radius] - imposta l'area di arrivo");
        sender.sendMessage("/" + label + " map list - mostra le mappe configurate");
        sender.sendMessage("/" + label + " info <nome> - mostra i dettagli della mappa");
        sender.sendMessage("/" + label + " resetplayer <mappa> <giocatore> - azzera il tempo di un giocatore");
        sender.sendMessage("/" + label + " resetmap <mappa> - rimuove tutti i tempi di una mappa");
        sender.sendMessage("/" + label + " reload - ricarica la configurazione");
        sender.sendMessage("/" + label + " leave - esce dalla corsa attiva");
    }

    private void handleReload(CommandSender sender) {
        runtimeManager.shutdown();
        manager.load();
        runtimeManager.reload(plugin.getConfig());
        sender.sendMessage("Configurazione di zCrono ricaricata.");
    }

    private String formatPoint(MapPoint point) {
        if (point == null) {
            return "non impostato";
        }

        Location location = point.getLocation();
        String worldName = location.getWorld() != null ? location.getWorld().getName() : "mondo?";
        String base = worldName + " "
                + String.format(Locale.ROOT, "(%.2f, %.2f, %.2f)",
                location.getX(), location.getY(), location.getZ());
        double radius = point.getRadius();
        if (radius > 0.0D) {
            return base + " radius=" + String.format(Locale.ROOT, "%.2f", radius);
        }
        return base;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("create", "delete", "remove", "setstart",
                    "setend", "map", "info", "resetplayer", "resetmap", "reload", "leave");
            return subCommands.stream()
                    .filter(sub -> sub.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("map")) {
                List<String> subCommands = Collections.singletonList("list");
                return subCommands.stream()
                        .filter(sub -> sub.toLowerCase(Locale.ROOT)
                                .startsWith(args[1].toLowerCase(Locale.ROOT)))
                        .collect(Collectors.toList());
            }

            List<String> mapNames = manager.getMapNames();
            return mapNames.stream()
                    .filter(name -> name.toLowerCase(Locale.ROOT)
                            .startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }

        if (args.length == 3 && (args[0].equalsIgnoreCase("setstart") || args[0].equalsIgnoreCase("setend"))) {
            List<String> suggestions = Arrays.asList("0", "2", "3", "4", "5");
            return suggestions.stream()
                    .filter(value -> value.startsWith(args[2]))
                    .collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("resetplayer")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
