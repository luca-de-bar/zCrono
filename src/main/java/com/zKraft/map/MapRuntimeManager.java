package com.zKraft.map;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;
import java.util.HashMap;
import java.util.UUID;

/**
 * Handles the runtime behaviour for maps, timing runs between start and end areas.
 */
public class MapRuntimeManager implements Listener {

    private final MapManager mapManager;
    private final StatsManager statsManager;
    private final java.util.Map<UUID, PlayerSession> sessions = new HashMap<>();

    public MapRuntimeManager(MapManager mapManager, StatsManager statsManager) {
        this.mapManager = mapManager;
        this.statsManager = statsManager;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        Player player = event.getPlayer();
        PlayerSession session = sessions.computeIfAbsent(player.getUniqueId(), key -> new PlayerSession());

        if (session.isPaused()) {
            return;
        }

        if (session.isRunning()) {
            handleRunningPlayer(event, player, session);
            return;
        }

        Map mapAtStart = findMapForStart(event.getFrom(), to);
        if (mapAtStart != null && mapAtStart.isConfigured()) {
            session.start(mapAtStart);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        PlayerSession session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null) {
            session.pause();
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        PlayerSession session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null && session.isPaused()) {
            session.resume();
        }
    }

    public void shutdown() {
        sessions.clear();
    }

    private void handleRunningPlayer(PlayerMoveEvent event, Player player, PlayerSession session) {
        Map map = session.getMap();
        if (map == null || !map.isConfigured()) {
            session.reset();
            return;
        }

        MapPoint startPoint = map.getStart();
        if (startPoint != null && isEnteringArea(event.getFrom(), event.getTo(), startPoint)) {
            session.restart();
            return;
        }

        MapPoint endPoint = map.getEnd();
        if (endPoint != null && isEnteringArea(event.getFrom(), event.getTo(), endPoint)) {
            finishRun(player, session, map);
        }
    }

    private void finishRun(Player player, PlayerSession session, Map map) {
        long nanos = session.elapsedNanos();
        session.reset();

        if (nanos <= 0L) {
            return;
        }

        statsManager.recordRun(map, player, Duration.ofNanos(nanos));
    }

    private Map findMapForStart(Location from, Location to) {
        for (Map map : mapManager.getMaps()) {
            if (!map.isConfigured()) {
                continue;
            }

            MapPoint startPoint = map.getStart();
            if (startPoint != null && isEnteringArea(from, to, startPoint)) {
                return map;
            }
        }
        return null;
    }

    private boolean isEnteringArea(Location from, Location to, MapPoint point) {
        if (point == null || to == null) {
            return false;
        }

        boolean wasInside = point.contains(from);
        boolean isInside = point.contains(to);
        return !wasInside && isInside;
    }

    private static class PlayerSession {
        private Map map;
        private boolean running;
        private boolean paused;
        private long runStartNanos;
        private long accumulatedNanos;

        void start(Map map) {
            this.map = map;
            this.running = true;
            this.paused = false;
            this.accumulatedNanos = 0L;
            this.runStartNanos = System.nanoTime();
        }

        void restart() {
            if (map == null) {
                return;
            }
            this.running = true;
            this.paused = false;
            this.accumulatedNanos = 0L;
            this.runStartNanos = System.nanoTime();
        }

        void pause() {
            if (running) {
                accumulatedNanos += Math.max(System.nanoTime() - runStartNanos, 0L);
                running = false;
            }
            paused = map != null;
        }

        void resume() {
            if (map != null && paused) {
                running = true;
                runStartNanos = System.nanoTime();
                paused = false;
            }
        }

        long elapsedNanos() {
            long total = accumulatedNanos;
            if (running) {
                total += Math.max(System.nanoTime() - runStartNanos, 0L);
            }
            return total;
        }

        void reset() {
            running = false;
            paused = false;
            accumulatedNanos = 0L;
            runStartNanos = 0L;
            map = null;
        }

        Map getMap() {
            return map;
        }

        boolean isRunning() {
            return running;
        }

        boolean isPaused() {
            return paused;
        }
    }
}
