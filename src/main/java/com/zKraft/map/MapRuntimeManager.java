package com.zKraft.map;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles the runtime behaviour for maps, timing runs between start and end areas.
 */
public class MapRuntimeManager implements Listener {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([0-9a-fA-F]{6})");

    private final JavaPlugin plugin;
    private final MapManager mapManager;
    private final StatsManager statsManager;
    private final java.util.Map<UUID, PlayerSession> sessions = new HashMap<>();
    private final java.util.Map<UUID, PlayerState> playerStates = new HashMap<>();

    private int countdownSeconds;
    private String startMessage;
    private String goMessage;
    private String endMessage;
    private String endChatMessage;
    private String leaveSuccessMessage;
    private String leaveNoActiveMessage;

    private BukkitTask monitorTask;

    public MapRuntimeManager(JavaPlugin plugin, MapManager mapManager, StatsManager statsManager) {
        this.plugin = plugin;
        this.mapManager = mapManager;
        this.statsManager = statsManager;

        loadSettings(plugin.getConfig());
    }

    public void startup() {
        if (monitorTask != null && !monitorTask.isCancelled()) {
            return;
        }

        monitorTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickPlayers, 1L, 1L);
    }

    public void reload(FileConfiguration configuration) {
        loadSettings(configuration);
    }

    private void loadSettings(FileConfiguration configuration) {
        if (configuration == null) {
            return;
        }

        countdownSeconds = Math.max(0, configuration.getInt("countdownSeconds", 0));
        startMessage = configuration.getString("startMessage", "⏱ La corsa inizia tra {seconds}...");
        goMessage = configuration.getString("goMessage", "🏁 GO!");
        endMessage = configuration.getString("endMessage", "&aHai completato il percorso in {time}.");
        endChatMessage = configuration.getString("endChatMessage", "&aHai completato il percorso in {time}.");
        leaveSuccessMessage = configuration.getString("leaveSuccessMessage", "");
        leaveNoActiveMessage = configuration.getString("leaveNoActiveMessage", "");
    }

    private void tickPlayers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            PlayerState state = playerStates.computeIfAbsent(playerId, id -> new PlayerState(player.getLocation()));
            Location previous = state.getLastLocation();
            Location current = player.getLocation();

            processPlayer(player, previous, current);

            state.update(current);
        }
    }

    private void processPlayer(Player player, Location from, Location to) {
        if (to == null) {
            return;
        }

        UUID playerId = player.getUniqueId();
        PlayerSession session = sessions.get(playerId);

        if (session != null && session.isPaused()) {
            return;
        }

        if (session != null && session.isCountingDown()) {
            Map map = session.getMap();
            MapPoint startPoint = map != null ? map.getStart() : null;
            if (map == null || startPoint == null || !startPoint.contains(to)) {
                session.cancelCountdown(true);
                player.resetTitle();
                cleanupIfIdle(playerId, session);
            }
            return;
        }

        if (session != null && session.isRunning()) {
            handleRunningPlayer(player, session, from, to);
            return;
        }

        Map mapAtStart = findMapForStart(from, to);
        if (mapAtStart != null && mapAtStart.isConfigured()) {
            if (session == null) {
                session = new PlayerSession();
                sessions.put(playerId, session);
            }
            startCountdown(player, mapAtStart, session);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        PlayerSession session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null) {
            session.pause();
        }
        playerStates.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        PlayerSession session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null && session.isPaused()) {
            session.resume();
        }
        playerStates.put(event.getPlayer().getUniqueId(), new PlayerState(event.getPlayer().getLocation()));
    }

    public void shutdown() {
        if (monitorTask != null) {
            monitorTask.cancel();
            monitorTask = null;
        }

        for (java.util.Map.Entry<UUID, PlayerSession> entry : sessions.entrySet()) {
            PlayerSession session = entry.getValue();
            session.reset();
            Player online = plugin.getServer().getPlayer(entry.getKey());
            if (online != null) {
                online.resetTitle();
            }
        }
        sessions.clear();
        playerStates.clear();
    }

    public Map leaveTimer(Player player) {
        PlayerSession session = sessions.get(player.getUniqueId());
        if (session == null || !session.hasActivity()) {
            return null;
        }

        Map map = session.getMap();
        session.reset();
        player.resetTitle();
        cleanupIfIdle(player.getUniqueId(), session);
        return map;
    }

    public String renderLeaveSuccessMessage(Player player, Map map) {
        java.util.Map<String, String> values = new java.util.HashMap<>();
        values.put("player", player.getName());
        values.put("map", map != null ? map.getName() : "");
        return formatMessage(leaveSuccessMessage, values);
    }

    public String renderLeaveNoActiveMessage(Player player) {
        java.util.Map<String, String> values = new java.util.HashMap<>();
        values.put("player", player.getName());
        values.put("map", "");
        return formatMessage(leaveNoActiveMessage, values);
    }

    public void resetPlayerSession(UUID playerId) {
        PlayerSession session = sessions.get(playerId);
        if (session == null) {
            return;
        }

        session.reset();
        Player online = plugin.getServer().getPlayer(playerId);
        if (online != null) {
            online.resetTitle();
        }
        cleanupIfIdle(playerId, session);
    }

    public void resetSessionsForMap(String mapName) {
        if (mapName == null) {
            return;
        }

        Iterator<java.util.Map.Entry<UUID, PlayerSession>> iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            java.util.Map.Entry<UUID, PlayerSession> entry = iterator.next();
            PlayerSession session = entry.getValue();
            Map map = session.getMap();
            if (map != null && map.getName().equalsIgnoreCase(mapName)) {
                session.reset();
                Player online = plugin.getServer().getPlayer(entry.getKey());
                if (online != null) {
                    online.resetTitle();
                }
                if (session.isIdle()) {
                    iterator.remove();
                }
            }
        }
    }

    public OptionalLong getLiveTime(UUID playerId, String mapName) {
        if (playerId == null) {
            return OptionalLong.empty();
        }

        PlayerSession session = sessions.get(playerId);
        if (session == null) {
            return OptionalLong.empty();
        }

        Map map = session.getMap();
        if (map == null) {
            return OptionalLong.empty();
        }

        if (mapName != null && !map.getName().equalsIgnoreCase(mapName)) {
            return OptionalLong.empty();
        }

        if (!(session.isRunning() || session.isPaused())) {
            return OptionalLong.empty();
        }

        return OptionalLong.of(session.elapsedNanos());
    }

    private void handleRunningPlayer(Player player, PlayerSession session, Location from, Location to) {
        Map map = session.getMap();
        if (map == null || !map.isConfigured()) {
            session.reset();
            cleanupIfIdle(player.getUniqueId(), session);
            return;
        }

        MapPoint startPoint = map.getStart();
        if (startPoint != null && isEnteringArea(from, to, startPoint)) {
            startCountdown(player, map, session);
            return;
        }

        MapPoint endPoint = map.getEnd();
        if (endPoint != null && isEnteringArea(from, to, endPoint)) {
            finishRun(player, session, map);
        }
    }

    private void startCountdown(Player player, Map map, PlayerSession session) {
        MapPoint startPoint = map.getStart();
        if (startPoint == null) {
            return;
        }

        session.prepareForCountdown(map);

        if (countdownSeconds <= 0) {
            session.startRun();
            sendGoTitle(player, map);
            return;
        }

        CountdownTask task = new CountdownTask(player, session, map, countdownSeconds);
        session.setCountdown(task);
        task.runTaskTimer(plugin, 0L, 20L);
    }

    private void finishRun(Player player, PlayerSession session, Map map) {
        long nanos = session.elapsedNanos();
        session.reset();
        cleanupIfIdle(player.getUniqueId(), session);

        if (nanos <= 0L) {
            return;
        }

        statsManager.recordRun(map, player, Duration.ofNanos(nanos));

        sendFinishMessages(player, map, nanos);
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

    private void sendCountdownTitle(Player player, Map map, int secondsRemaining) {
        String message = formatMessage(startMessage, java.util.Map.of(
                "seconds", Integer.toString(secondsRemaining),
                "map", map.getName(),
                "player", player.getName()
        ));
        sendWrappedTitle(player, message, 0, 20, 0);
    }

    private void sendGoTitle(Player player, Map map) {
        String message = formatMessage(goMessage, java.util.Map.of(
                "map", map.getName(),
                "player", player.getName()
        ));
        sendWrappedTitle(player, message, 0, 20, 10);
    }

    private void sendFinishMessages(Player player, Map map, long nanos) {
        String formattedTime = TimeFormatter.format(nanos);
        String title = formatMessage(endMessage, java.util.Map.of(
                "time", formattedTime,
                "map", map.getName(),
                "player", player.getName()
        ));
        sendWrappedTitle(player, title, 10, 40, 10);

        String chat = formatMessage(endChatMessage, java.util.Map.of(
                "time", formattedTime,
                "map", map.getName(),
                "player", player.getName()
        ));
        if (!chat.isEmpty()) {
            player.sendMessage(chat);
        }
    }

    private String formatMessage(String template, java.util.Map<String, String> values) {
        if (template == null || template.isEmpty()) {
            return "";
        }

        String result = template;
        for (java.util.Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return colorize(result);
    }

    private String colorize(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String color = matcher.group(1);
            net.md_5.bungee.api.ChatColor chatColor;
            try {
                chatColor = net.md_5.bungee.api.ChatColor.of("#" + color);
            } catch (IllegalArgumentException exception) {
                chatColor = net.md_5.bungee.api.ChatColor.WHITE;
            }
            matcher.appendReplacement(buffer, chatColor.toString());
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    private void cleanupIfIdle(UUID playerId, PlayerSession session) {
        if (session.isIdle()) {
            sessions.remove(playerId);
        }
    }

    private void sendWrappedTitle(Player player, String message, int fadeIn, int stay, int fadeOut) {
        if (message == null || message.isEmpty()) {
            return;
        }

        TitleParts parts = splitForTitle(message);
        if (parts.isEmpty()) {
            return;
        }

        player.sendTitle(parts.title(), parts.subtitle(), fadeIn, stay, fadeOut);
    }

    private TitleParts splitForTitle(String message) {
        if (message == null || message.isEmpty()) {
            return TitleParts.empty();
        }

        String normalized = message.replace("\r\n", "\n").replace('\r', '\n');
        int newline = normalized.indexOf('\n');
        if (newline >= 0) {
            String title = normalized.substring(0, newline);
            String subtitle = newline + 1 < normalized.length() ? normalized.substring(newline + 1) : "";
            return TitleParts.of(title, subtitle);
        }

        final int limit = 32;
        int plainChars = 0;
        int lastSpace = -1;
        for (int index = 0; index < normalized.length(); index++) {
            char current = normalized.charAt(index);
            if (current == ChatColor.COLOR_CHAR && index + 1 < normalized.length()) {
                index++;
                continue;
            }

            plainChars++;
            if (Character.isWhitespace(current)) {
                lastSpace = index;
            }

            if (plainChars > limit) {
                int breakPoint = lastSpace >= 0 ? lastSpace : index;
                return splitAt(normalized, breakPoint);
            }
        }

        return TitleParts.of(normalized, "");
    }

    private TitleParts splitAt(String message, int index) {
        if (message.isEmpty()) {
            return TitleParts.empty();
        }

        int safeIndex = Math.max(0, Math.min(index, message.length()));
        int secondIndex = safeIndex;
        if (secondIndex < message.length() && Character.isWhitespace(message.charAt(secondIndex))) {
            secondIndex++;
        }

        String title = message.substring(0, safeIndex);
        String subtitle = secondIndex < message.length() ? message.substring(secondIndex) : "";
        if (!subtitle.isEmpty()) {
            String lastColors = ChatColor.getLastColors(title);
            if (!lastColors.isEmpty()) {
                subtitle = lastColors + subtitle;
            }
        }
        return TitleParts.of(title, subtitle);
    }

    private record TitleParts(String title, String subtitle) {
        static TitleParts empty() {
            return new TitleParts("", "");
        }

        static TitleParts of(String title, String subtitle) {
            String safeTitle = title == null ? "" : title;
            String safeSubtitle = subtitle == null ? "" : subtitle;
            return new TitleParts(safeTitle, safeSubtitle);
        }

        boolean isEmpty() {
            return ChatColor.stripColor(title).isEmpty() && ChatColor.stripColor(subtitle).isEmpty();
        }
    }

    private class CountdownTask extends BukkitRunnable {
        private final Player player;
        private final PlayerSession session;
        private final Map map;
        private int secondsRemaining;
        private boolean stopped;

        CountdownTask(Player player, PlayerSession session, Map map, int seconds) {
            this.player = player;
            this.session = session;
            this.map = map;
            this.secondsRemaining = seconds;
        }

        @Override
        public void run() {
            if (stopped) {
                return;
            }

            if (!player.isOnline()) {
                stop(true);
                return;
            }

            MapPoint startPoint = map.getStart();
            if (startPoint == null || !startPoint.contains(player.getLocation())) {
                player.resetTitle();
                stop(true);
                return;
            }

            if (secondsRemaining <= 0) {
                session.startRun();
                sendGoTitle(player, map);
                stop(false);
                return;
            }

            sendCountdownTitle(player, map, secondsRemaining);
            secondsRemaining--;
        }

        void stop(boolean clearState) {
            if (stopped) {
                return;
            }
            stopped = true;
            super.cancel();
            session.onCountdownStopped(clearState);
        }
    }

    private static class PlayerState {
        private Location lastLocation;

        PlayerState(Location location) {
            update(location);
        }

        Location getLastLocation() {
            return lastLocation;
        }

        void update(Location location) {
            lastLocation = location == null ? null : location.clone();
        }
    }

    private static class PlayerSession {
        private Map map;
        private boolean running;
        private boolean paused;
        private long runStartNanos;
        private long accumulatedNanos;
        private CountdownTask countdown;

        void prepareForCountdown(Map map) {
            if (countdown != null) {
                countdown.stop(true);
            }
            this.map = map;
            this.running = false;
            this.paused = false;
            this.accumulatedNanos = 0L;
            this.runStartNanos = 0L;
            this.countdown = null;
        }

        void setCountdown(CountdownTask countdown) {
            if (this.countdown != null) {
                this.countdown.stop(true);
            }
            this.countdown = countdown;
        }

        void startRun() {
            running = true;
            paused = false;
            accumulatedNanos = 0L;
            runStartNanos = System.nanoTime();
            countdown = null;
        }

        void pause() {
            if (running) {
                accumulatedNanos += Math.max(System.nanoTime() - runStartNanos, 0L);
                running = false;
                paused = map != null;
            } else {
                paused = false;
            }

            if (countdown != null) {
                countdown.stop(true);
                countdown = null;
            }
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
            if (countdown != null) {
                countdown.stop(true);
                countdown = null;
            }
            running = false;
            paused = false;
            accumulatedNanos = 0L;
            runStartNanos = 0L;
            map = null;
        }

        void cancelCountdown(boolean clearState) {
            if (countdown != null) {
                countdown.stop(clearState);
                countdown = null;
            } else if (clearState) {
                running = false;
                paused = false;
                accumulatedNanos = 0L;
                runStartNanos = 0L;
                map = null;
            }
        }

        void onCountdownStopped(boolean clearState) {
            countdown = null;
            if (clearState) {
                running = false;
                paused = false;
                accumulatedNanos = 0L;
                runStartNanos = 0L;
                map = null;
            }
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

        boolean isCountingDown() {
            return countdown != null;
        }

        boolean isIdle() {
            return !running && !paused && countdown == null && map == null;
        }

        boolean hasActivity() {
            return running || paused || countdown != null;
        }
    }
}
