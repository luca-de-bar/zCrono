package com.zKraft.map;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashMap;
import java.util.UUID;

/**
 * Handles the runtime behaviour for maps, including countdowns and timing runs.
 */
public class MapRuntimeManager implements Listener {

    private static final int COUNTDOWN_SECONDS = 3;
    private static final double POINT_RADIUS = 1.5D;

    private final JavaPlugin plugin;
    private final MapManager mapManager;
    private final java.util.Map<UUID, PlayerSession> sessions = new HashMap<>();

    public MapRuntimeManager(JavaPlugin plugin, MapManager mapManager) {
        this.plugin = plugin;
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

        Map mapAtStart = findMapAtStart(to);
        boolean insideStart = mapAtStart != null;

        if (session.countdownTask != null) {
            if (!insideStart || session.map != mapAtStart) {
                cancelCountdown(player, session, ChatColor.RED + "Countdown annullato: sei uscito dall'area di partenza.");

                if (insideStart && mapAtStart != null) {
                    startCountdown(player, session, mapAtStart);
                }
            }
            return;
        }

        if (session.running && session.map != null) {
            handleActiveRun(event, player, session);
            return;
        }

        if (!session.running && insideStart) {
            startCountdown(player, session, mapAtStart);
        }
    }

    private void handleActiveRun(PlayerMoveEvent event, Player player, PlayerSession session) {
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        Map map = session.map;
        Location end = map.getEnd();
        if (end != null && isInsideArea(to, end, POINT_RADIUS)) {
            finishRun(player, session);
            return;
        }

        Location from = event.getFrom();
        Location start = map.getStart();
        if (start != null && !isInsideArea(from, start, POINT_RADIUS) && isInsideArea(to, start, POINT_RADIUS)) {
            session.running = false;
            startCountdown(player, session, map);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        PlayerSession session = sessions.remove(event.getPlayer().getUniqueId());
        if (session != null) {
            cancelCountdown(null, session, null);
        }
    }

    public void shutdown() {
        for (PlayerSession session : sessions.values()) {
            cancelCountdown(null, session, null);
        }
        sessions.clear();
    }

    private void startCountdown(Player player, PlayerSession session, Map map) {
        if (map.getStart() == null || map.getEnd() == null) {
            player.sendMessage(ChatColor.RED + "La mappa \"" + map.getName() + "\" non è configurata correttamente.");
            return;
        }

        cancelCountdown(null, session, null);
        session.map = map;
        session.running = false;

        session.countdownTask = new BukkitRunnable() {
            private int secondsRemaining = COUNTDOWN_SECONDS;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancelCountdown(null, session, null);
                    cancel();
                    return;
                }

                if (!isInsideArea(player.getLocation(), map.getStart(), POINT_RADIUS)) {
                    cancelCountdown(player, session,
                            ChatColor.RED + "Countdown annullato: sei uscito dall'area di partenza.");
                    cancel();
                    return;
                }

                if (secondsRemaining <= 0) {
                    beginRun(player, session);
                    cancel();
                    return;
                }

                player.sendTitle(ChatColor.GOLD + "⏱", ChatColor.YELLOW + "La corsa inizia tra "
                        + secondsRemaining + "...", 0, 25, 0);
                secondsRemaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void beginRun(Player player, PlayerSession session) {
        session.countdownTask = null;
        session.running = true;
        session.runStartTime = System.nanoTime();
        player.sendTitle(ChatColor.GREEN + "🏁 GO!", "", 0, 20, 10);
        player.sendMessage(ChatColor.GREEN + "🏁 GO!");
    }

    private void finishRun(Player player, PlayerSession session) {
        session.running = false;
        session.countdownTask = null;
        long elapsedNanos = System.nanoTime() - session.runStartTime;
        Duration duration = Duration.ofNanos(Math.max(elapsedNanos, 0L));
        String formatted = formatDuration(duration);
        player.sendMessage(ChatColor.GREEN + "Hai completato la mappa \"" + session.map.getName()
                + "\" in " + ChatColor.GOLD + formatted + ChatColor.GREEN + ".");
        session.map = null;
        session.runStartTime = 0L;
    }

    private void cancelCountdown(Player player, PlayerSession session, String message) {
        if (session.countdownTask != null) {
            session.countdownTask.cancel();
            session.countdownTask = null;
        }

        if (player != null && message != null && !message.isEmpty()) {
            player.sendMessage(message);
            player.sendTitle("", "", 0, 0, 0);
        }

        if (!session.running) {
            session.map = null;
        }
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

    private String formatDuration(Duration duration) {
        long totalMillis = duration.toMillis();
        long minutes = totalMillis / 60000;
        long seconds = (totalMillis % 60000) / 1000;
        long millis = totalMillis % 1000;
        return String.format(java.util.Locale.ROOT, "%02d:%02d.%03d", minutes, seconds, millis);
    }

    private static class PlayerSession {
        private Map map;
        private BukkitTask countdownTask;
        private boolean running;
        private long runStartTime;
    }
}
