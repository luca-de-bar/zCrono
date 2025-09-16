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

    public ZCronoPlaceholderExpansion(com.zKraft.zCrono plugin, MapManager mapManager, StatsManager statsManager) {
        this.plugin = plugin;
        this.mapManager = mapManager;
        this.statsManager = statsManager;
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

        if (identifier.startsWith("top_")) {
            return handleTop(identifier.substring("top_".length()));
        }

        if (identifier.startsWith("rank_")) {
            return handleRank(player, identifier.substring("rank_".length()));
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
        int separator = input.lastIndexOf('_');
        if (separator <= 0 || separator >= input.length() - 1) {
            return "";
        }

        String mapName = input.substring(0, separator);
        String positionValue = input.substring(separator + 1);
        int position;
        try {
            position = Integer.parseInt(positionValue);
        } catch (NumberFormatException exception) {
            return "";
        }

        if (position <= 0) {
            return "";
        }

        Map map = mapManager.getMap(mapName);
        if (map == null) {
            return "-";
        }

        Optional<StatsManager.LeaderboardEntry> entry = statsManager.getTopEntry(map.getName(), position);
        if (entry.isEmpty()) {
            return "-";
        }

        StatsManager.LeaderboardEntry value = entry.get();
        return value.name() + " " + TimeFormatter.format(value.timeNanos());
    }
}
