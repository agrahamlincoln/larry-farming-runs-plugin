package com.larryfarming.snapshot;

import com.google.gson.annotations.SerializedName;
import lombok.Value;

/**
 * One line of the gear list: something to put on now, or something to work toward.
 *
 * <p>Already reduced to a row: four outfit slots the player has none of are one line here.
 */
@Value
public class FarmingUpgrade {

    /** What the player should do about this line. */
    public enum Kind {
        /** Owned, not on. The only kind that can be acted on before setting off. */
        EQUIP,
        /** Not owned yet. Shown so the next upgrade is visible, not as a task. */
        GOAL
    }

    @SerializedName("kind") Kind kind;

    /** The sprite for the row. */
    @SerializedName("itemId") int itemId;

    /** The thing itself: "Farming cape", "Farmer's outfit". */
    @SerializedName("name") String name;

    /**
     * The whole sentence the row shows: "Wear your Explorer's ring 2 to save a slot.",
     * "Earn Ardougne cloak 2 for three teleports a day to the Ardougne patch."
     */
    @SerializedName("detail") String detail;
}
