package com.zKraft.map;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class MapCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "zcrono.admin";

    private final MapManager manager;

    public MapCommand(MapManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(ChatColor.RED + "Non hai il permesso per usare questo comando.");
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
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
            default:
                sender.sendMessage(ChatColor.RED + "Comando sconosciuto."
                        + ChatColor.GRAY + " Usa /" + label + " per la lista completa.");
                break;
        }
        return true;
    }

    private void handleMapSubcommand(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso corretto: /" + label + " map list");
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("list")) {
            handleList(sender);
            return;
        }

        sender.sendMessage(ChatColor.RED + "Sotto-comando sconosciuto."
                + ChatColor.GRAY + " Usa /" + label + " map list.");
    }

    private void handleCreate(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso corretto: /" + label + " " + args[0] + " <nome>");
            return;
        }

        String name = args[1];
        if (manager.createMap(name)) {
            sender.sendMessage(ChatColor.GREEN + "Mappa \"" + name + "\" creata.");
        } else {
            sender.sendMessage(ChatColor.RED + "Esiste già una mappa con questo nome.");
        }
    }

    private void handleDelete(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso corretto: /" + label + " " + args[0] + " <nome>");
            return;
        }

        String name = args[1];
        if (manager.deleteMap(name)) {
            sender.sendMessage(ChatColor.YELLOW + "Mappa \"" + name + "\" eliminata.");
        } else {
            sender.sendMessage(ChatColor.RED + "Nessuna mappa trovata con questo nome.");
        }
    }

    private void handleSetPoint(CommandSender sender, String label, String[] args, boolean start) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Solo un giocatore può usare questo comando.");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso corretto: /" + label + " " + args[0] + " <nome>");
            return;
        }

        String mapName = args[1];
        Map map = manager.getMap(mapName);
        if (map == null) {
            sender.sendMessage(ChatColor.RED + "Nessuna mappa trovata con questo nome.");
            return;
        }

        Location location = player.getLocation().clone();
        if (start) {
            manager.updateStart(mapName, location);
            sender.sendMessage(ChatColor.GREEN + "Punto di partenza per \"" + mapName + "\" salvato.");
        } else {
            manager.updateEnd(mapName, location);
            sender.sendMessage(ChatColor.GREEN + "Punto di arrivo per \"" + mapName + "\" salvato.");
        }
    }

    private void handleList(CommandSender sender) {
        List<String> names = manager.getMapNames();
        if (names.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Non ci sono mappe configurate.");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "Mappe disponibili: " + ChatColor.WHITE
                + String.join(ChatColor.GRAY + ", " + ChatColor.WHITE, names));
    }

    private void handleInfo(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso corretto: /" + label + " " + args[0] + " <nome>");
            return;
        }

        String mapName = args[1];
        Map map = manager.getMap(mapName);
        if (map == null) {
            sender.sendMessage(ChatColor.RED + "Nessuna mappa trovata con questo nome.");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "Informazioni per \"" + map.getName() + "\":");
        sender.sendMessage(ChatColor.YELLOW + "Start: " + ChatColor.WHITE + formatLocation(map.getStart()));
        sender.sendMessage(ChatColor.YELLOW + "End: " + ChatColor.WHITE + formatLocation(map.getEnd()));
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.GOLD + "Comandi zCrono:");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " create <nome>"
                + ChatColor.GRAY + " - crea una nuova mappa");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " delete <nome>"
                + ChatColor.GRAY + " - rimuove una mappa");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " setstart <nome>"
                + ChatColor.GRAY + " - imposta il punto di partenza");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " setend <nome>"
                + ChatColor.GRAY + " - imposta il punto finale");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " map list"
                + ChatColor.GRAY + " - mostra le mappe configurate");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " info <nome>"
                + ChatColor.GRAY + " - mostra i dettagli della mappa");
    }

    private String formatLocation(Location location) {
        if (location == null) {
            return "non impostato";
        }

        String worldName = location.getWorld() != null ? location.getWorld().getName() : "mondo?";
        return worldName + " "
                + String.format(Locale.ROOT, "(%.2f, %.2f, %.2f)",
                location.getX(), location.getY(), location.getZ());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("create", "delete", "remove", "setstart",
                    "setend", "map", "info");
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
        return Collections.emptyList();
    }
}
