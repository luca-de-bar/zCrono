package com.zKraft.map;

/**
 * Represents the configuration for a single map.
 */
public class Map {

    private final String name;
    private MapPoint start;
    private MapPoint end;

    public Map(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public MapPoint getStart() {
        return start;
    }

    public void setStart(MapPoint start) {
        this.start = start;
    }

    public MapPoint getEnd() {
        return end;
    }

    public void setEnd(MapPoint end) {
        this.end = end;
    }

    public boolean isConfigured() {
        return start != null && end != null;
    }
}
