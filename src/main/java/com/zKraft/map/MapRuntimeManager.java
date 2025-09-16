package com.zKraft.map;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;
import java.util.HashMap;
import java.util.UUID;

/**
 * Handles the runtime behaviour for maps, timing runs between start and end areas.
 */
public class MapRuntimeManager implements Listener {

    private static final double POINT_RADIUS = 1.5D;

    private final MapManager mapManager;
    private final java.util.Map<UUID, PlayerSession> sessions = new HashMap<>();

    public MapRuntimeManager(MapManager mapManager) {
        this.mapManager = mapManager;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        Player player = event.getPlayer();
        PlayerSession session = sessions.computeIfAbsent(player.getUniqueId(), key -> new PlayerSession());

        if (session.running) {
            handleRunningPlayer(event, player, session);
            return;
        }

        Map mapAtStart = findMapAtStart(to);
        if (mapAtStart != null && isEnteringArea(event, mapAtStart.getStart())) {
            beginRun(player, session, mapAtStart);
        }
    }

    private void handleRunningPlayer(PlayerMoveEvent event, Player player, PlayerSession session) {
        Map map = session.map;
        if (map == null) {
            session.running = false;
            return;
        }

        Location start = map.getStart();
        if (start != null && isEnteringArea(event, start)) {
            player.sendMessage(ChatColor.YELLOW + "Cronometro azzerato, riparti!");
            beginRun(player, session, map);
            return;
        }

        Location end = map.getEnd();
        if (end != null && isEnteringArea(event, end)) {
            finishRun(player, session);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    public void shutdown() {
        sessions.clear();
    }

    private void beginRun(Player player, PlayerSession session, Map map) {
        if (map.getStart() == null || map.getEnd() == null) {
            player.sendMessage(ChatColor.RED + "La mappa \"" + map.getName() + "\" non è configurata correttamente.");
            session.running = false;
            session.map = null;
            return;
        }

        session.map = map;
        session.running = true;
        session.runStartTime = System.nanoTime();
        player.sendMessage(ChatColor.GREEN + "Cronometro avviato per \"" + map.getName() + "\".");
    }

    private void finishRun(Player player, PlayerSession session) {
        session.running = false;
        long elapsedNanos = System.nanoTime() - session.runStartTime;
        Duration duration = Duration.ofNanos(Math.max(elapsedNanos, 0L));
        String formatted = formatDuration(duration);
        player.sendMessage(ChatColor.GREEN + "Hai completato la mappa \"" + session.map.getName()
                + "\" in " + ChatColor.GOLD + formatted + ChatColor.GREEN + ".");
        session.map = null;
        session.runStartTime = 0L;
    }

    private Map findMapAtStart(Location location) {
        if (location == null) {
            return null;
        }

        for (Map map : mapManager.getMaps()) {
            Location start = map.getStart();
            if (start != null && isInsideArea(location, start, POINT_RADIUS)) {
                return map;
            }
        }
        return null;
    }

    private boolean isInsideArea(Location location, Location center, double radius) {
        if (location == null || center == null) {
            return false;
        }

        if (location.getWorld() == null || center.getWorld() == null) {
            return false;
        }

        if (!location.getWorld().equals(center.getWorld())) {
            return false;
        }

        double dx = location.getX() - center.getX();
        double dy = location.getY() - center.getY();
        double dz = location.getZ() - center.getZ();
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    private boolean isEnteringArea(PlayerMoveEvent event, Location center) {
        if (center == null) {
            return false;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return false;
        }

        return !isInsideArea(from, center, POINT_RADIUS) && isInsideArea(to, center, POINT_RADIUS);
    }

    private String formatDuration(Duration duration) {
        long totalMillis = duration.toMillis();
        long minutes = totalMillis / 60000;
        long seconds = (totalMillis % 60000) / 1000;
        long millis = totalMillis % 1000;
        return String.format(java.util.Locale.ROOT, "%02d:%02d.%03d", minutes, seconds, millis);
    }

    private static class PlayerSession {
        private Map map;
        private boolean running;
        private long runStartTime;
    }
}
