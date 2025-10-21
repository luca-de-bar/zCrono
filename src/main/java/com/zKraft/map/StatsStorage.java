package com.zKraft.map;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;

public interface StatsStorage {

    void load();

    void save();

    void recordRun(String mapName, UUID playerId, String playerName, long nanos);

    boolean resetPlayer(String mapName, UUID playerId);

    boolean resetMap(String mapName);

    OptionalLong getBestTime(String mapName, UUID playerId);

    OptionalInt getRank(String mapName, UUID playerId);

    Optional<StatsManager.LeaderboardEntry> getTopEntry(String mapName, int position);

    List<StatsManager.LeaderboardEntry> getEntries(String mapName);

    void saveOngoingRun(String mapName, UUID playerId, String playerName, long nanos);

    OptionalLong getOngoingRun(String mapName, UUID playerId);
}
