package com.larryfarming.farming.detection;

import java.util.Map;

/**
 * Where the player's house is, read from the game.
 *
 * <p>A rung that needs POH_IN_&lt;town&gt; is a house teleport, which only helps if the house
 * is in that town.
 *
 * <p>Varbit 2187 ({@code POH_HOUSE_LOCATION}) holds the town. Zero means no house: client
 * script 3809 refuses the Construction lamp option with "You must own a house" on zero.
 * The town values agree with the Shortest Path plugin's house teleports, and Quest
 * Helper's {@code KourendEasy} also requires 8 for Hosidius.
 *
 * <p>A house is in exactly one place, so once the varbit has been read every question has
 * an answer: any value but the town's own is a no.
 */
public final class HouseLocation {

    private HouseLocation() {
    }

    public static final int HOUSE_LOCATION_VARBIT = 2187;

    /** What the varbit reads for a player who owns no house. */
    public static final int NO_HOUSE = 0;

    /**
     * Whether the house is in the town the varbit reads as {@code town}.
     *
     * @return true when the house is there, false when it is anywhere else or there is no
     *     house, and null only when the varbit has not been read
     */
    public static Boolean isHouseIn(int town, Map<Integer, Integer> varbits) {
        Integer value = reading(varbits);
        return value == null ? null : value == town;
    }

    /** Whether the player owns a house at all, or null when the varbit has not been read. */
    public static Boolean ownsHouse(Map<Integer, Integer> varbits) {
        Integer value = reading(varbits);
        return value == null ? null : value != NO_HOUSE;
    }

    private static Integer reading(Map<Integer, Integer> varbits) {
        return varbits != null ? varbits.get(HOUSE_LOCATION_VARBIT) : null;
    }
}
