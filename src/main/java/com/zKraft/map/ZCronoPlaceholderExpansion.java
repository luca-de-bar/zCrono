package com.zKraft.map;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * PlaceholderAPI expansion exposing zCrono leaderboard data.
 */
public class ZCronoPlaceholderExpansion extends PlaceholderExpansion {

    private final com.zKraft.zCrono plugin;
    private final MapManager mapManager;
    private final StatsManager statsManager;
    private final MapRuntimeManager runtimeManager;

    public ZCronoPlaceholderExpansion(com.zKraft.zCrono plugin, MapManager mapManager, StatsManager statsManager,
                                      MapRuntimeManager runtimeManager) {
        this.plugin = plugin;
        this.mapManager = mapManager;
        this.statsManager = statsManager;
        this.runtimeManager = runtimeManager;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String getIdentifier() {
        return "zcrono";
    }

    @Override
    public String getAuthor() {
        List<String> authors = plugin.getDescription().getAuthors();
        if (authors == null || authors.isEmpty()) {
            return plugin.getDescription().getName();
        }
        return String.join(", ", authors);
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return "";
        }

        if (identifier.startsWith("besttime_")) {
            return handleBestTime(player, identifier.substring("besttime_".length()));
        }

        if (identifier.startsWith("top_player_")) {
            return handleTopComponent(identifier.substring("top_player_".length()), TopComponent.PLAYER);
        }

        if (identifier.startsWith("top_time_")) {
            return handleTopComponent(identifier.substring("top_time_".length()), TopComponent.TIME);
        }

        if (identifier.startsWith("top_")) {
            return handleTop(identifier.substring("top_".length()));
        }

        if (identifier.startsWith("rank_")) {
            return handleRank(player, identifier.substring("rank_".length()));
        }

        if (identifier.startsWith("live_")) {
            return handleLive(player, identifier.substring("live_".length()));
        }

        return "";
    }

    private String handleBestTime(Player player, String mapName) {
        if (player == null || mapName.isEmpty()) {
            return "-";
        }

        Map map = mapManager.getMap(mapName);
        if (map == null) {
            return "-";
        }

        OptionalLong best = statsManager.getBestTime(map.getName(), player.getUniqueId());
        if (best.isEmpty()) {
            return "-";
        }

        return TimeFormatter.format(best.getAsLong());
    }

    private String handleRank(Player player, String mapName) {
        if (player == null || mapName.isEmpty()) {
            return "-";
        }

        Map map = mapManager.getMap(mapName);
        if (map == null) {
            return "-";
        }

        OptionalInt rank = statsManager.getRank(map.getName(), player.getUniqueId());
        return rank.isPresent() ? Integer.toString(rank.getAsInt()) : "-";
    }

    private String handleTop(String input) {
        Optional<TopRequest> request = parseTopRequest(input);
        if (request.isEmpty()) {
            return "";
        }

        Optional<StatsManager.LeaderboardEntry> entry = resolveTopEntry(request.get());
        if (entry.isEmpty()) {
            return "-";
        }

        StatsManager.LeaderboardEntry value = entry.get();
        return value.name() + " " + TimeFormatter.format(value.timeNanos());
    }

    private String handleTopComponent(String input, TopComponent component) {
        Optional<TopRequest> request = parseTopRequest(input);
        if (request.isEmpty()) {
            return "";
        }

        Optional<StatsManager.LeaderboardEntry> entry = resolveTopEntry(request.get());
        if (entry.isEmpty()) {
            return "-";
        }

        StatsManager.LeaderboardEntry value = entry.get();
        if (component == TopComponent.PLAYER) {
            return value.name();
        }
        return TimeFormatter.format(value.timeNanos());
    }

    private String handleLive(Player player, String mapName) {
        if (player == null || mapName.isEmpty()) {
            return "-";
        }

        Map map = mapManager.getMap(mapName);
        if (map == null) {
            return "-";
        }

        OptionalLong nanos = runtimeManager.getLiveTime(player.getUniqueId(), map.getName());
        return nanos.isPresent() ? TimeFormatter.format(nanos.getAsLong()) : "-";
    }

    private Optional<TopRequest> parseTopRequest(String input) {
        int separator = input.lastIndexOf('_');
        if (separator <= 0 || separator >= input.length() - 1) {
            return Optional.empty();
        }

        String mapName = input.substring(0, separator);
        String positionValue = input.substring(separator + 1);
        int position;
        try {
            position = Integer.parseInt(positionValue);
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }

        if (position <= 0) {
            return Optional.empty();
        }

        return Optional.of(new TopRequest(mapName, position));
    }

    private Optional<StatsManager.LeaderboardEntry> resolveTopEntry(TopRequest request) {
        Map map = mapManager.getMap(request.mapName());
        if (map == null) {
            return Optional.empty();
        }

        return statsManager.getTopEntry(map.getName(), request.position());
    }

    private enum TopComponent {
        PLAYER,
        TIME
    }

    private record TopRequest(String mapName, int position) {
    }
}
