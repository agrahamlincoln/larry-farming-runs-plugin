package com.larryfarming.snapshot;

/**
 * How long, in words, for everything farming counts down to. The run list and the stop
 * rows share it so the same countdown never rounds two ways on one screen.
 *
 * <p>Lives beside the DTOs so the panel can reach it. The snapshot carries raw epoch
 * seconds and the panel words them here, so a countdown ticks by relabelling in place
 * rather than rebuilding its row and re-measuring the wrapping column.
 */
public final class FarmingDurations {

    private FarmingDurations() {
    }

    /** Rounded to the largest unit that still says something useful. */
    public static String describe(long seconds) {
        if (seconds <= 60) {
            return "under a minute";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "m";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            long spare = minutes % 60;
            return spare == 0 ? hours + "h" : hours + "h " + spare + "m";
        }
        long days = hours / 24;
        long spare = hours % 24;
        return spare == 0 ? days + "d" : days + "d " + spare + "h";
    }

    /**
     * How long until a moment, or empty when it is unknown or already past. An expired
     * prediction means the tracker has not seen the crop since, not that it just finished.
     *
     * @param epochSec when it happens, or 0 when unknown
     * @param nowEpochSec the current time
     */
    public static String until(long epochSec, long nowEpochSec) {
        if (epochSec <= 0 || epochSec <= nowEpochSec) {
            return "";
        }
        return describe(epochSec - nowEpochSec);
    }
}
