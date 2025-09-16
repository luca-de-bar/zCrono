package com.zKraft.map;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents the configuration for a single map.
 */
public class Map {

    private final String name;
    private Location start;
    private Location end;
    private final List<MapCheckpoint> checkpoints = new ArrayList<>();

    public Map(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Location getStart() {
        return start;
    }

    public void setStart(Location start) {
        this.start = start;
    }

    public Location getEnd() {
        return end;
    }

    public void setEnd(Location end) {
        this.end = end;
    }

    public List<MapCheckpoint> getCheckpoints() {
        return Collections.unmodifiableList(checkpoints);
    }

    public void clearCheckpoints() {
        checkpoints.clear();
    }

    public void addCheckpoint(MapCheckpoint checkpoint) {
        checkpoints.add(checkpoint);
    }
}
