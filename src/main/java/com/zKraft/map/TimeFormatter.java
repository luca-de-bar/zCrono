package com.zKraft.map;

import java.time.Duration;
import java.util.Locale;

/**
 * Utility helpers to format run durations for display.
 */
public final class TimeFormatter {

    private TimeFormatter() {
    }

    public static String format(Duration duration) {
        if (duration == null) {
            return "-";
        }
        return format(duration.toNanos());
    }

    public static String format(long nanos) {
        if (nanos <= 0L) {
            return "00:00.000";
        }

        long totalMillis = nanos / 1_000_000L;
        long minutes = totalMillis / 60_000L;
        long seconds = (totalMillis % 60_000L) / 1_000L;
        long millis = totalMillis % 1_000L;
        return String.format(Locale.ROOT, "%02d:%02d.%03d", minutes, seconds, millis);
    }
}
