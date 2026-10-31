package com.larryfarming.farming.detection;

import com.larryfarming.farming.model.Capability;
import java.util.Map;

/**
 * Where the player's house is, read from the game.
 *
 * <p>Chains that say "redirected" or "POH tab" mean a house teleport, which only helps if
 * the house is in the right town.
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

    /** The varbit's value for each town a chain asks about. */
    private static final Map<Capability, Integer> TOWN_VALUES = Map.of(
            Capability.POH_IN_TAVERLEY, 2,
            Capability.POH_IN_BRIMHAVEN, 5,
            Capability.POH_IN_HOSIDIUS, 8);

    /**
     * Whether the house is in the town this capability names.
     *
     * @return true when the house is there, false when it is anywhere else or there is no
     *     house, and null only when the varbit has not been read
     * @throws IllegalArgumentException when the capability names no house town
     */
    public static Boolean isHouseIn(Capability location, Map<Integer, Integer> varbits) {
        Integer wanted = TOWN_VALUES.get(location);
        if (wanted == null) {
            throw new IllegalArgumentException(location + " is not a house location");
        }
        Integer value = reading(varbits);
        return value == null ? null : value.intValue() == wanted;
    }

    /** Whether the player owns a house at all, or null when the varbit has not been read. */
    public static Boolean ownsHouse(Map<Integer, Integer> varbits) {
        Integer value = reading(varbits);
        return value == null ? null : value != NO_HOUSE;
    }

    /**
     * The town the house is in, or null when it is unread or in a town no chain asks about.
     */
    public static Capability locationOf(Map<Integer, Integer> varbits) {
        Integer value = reading(varbits);
        if (value == null) {
            return null;
        }
        for (Map.Entry<Capability, Integer> town : TOWN_VALUES.entrySet()) {
            if (town.getValue().equals(value)) {
                return town.getKey();
            }
        }
        return null;
    }

    private static Integer reading(Map<Integer, Integer> varbits) {
        return varbits != null ? varbits.get(HOUSE_LOCATION_VARBIT) : null;
    }
}
