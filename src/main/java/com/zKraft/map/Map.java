package com.zKraft.map;

import org.bukkit.Location;

/**
 * Represents the configuration for a single map.
 */
public class Map {

    private final String name;
    private Location start;
    private Location end;

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
}
