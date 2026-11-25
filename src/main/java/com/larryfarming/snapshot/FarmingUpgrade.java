package com.larryfarming.snapshot;

import lombok.Value;

/**
 * One line of advice: gear to put on now, or something to work toward between runs.
 *
 * <p>Already reduced to a row: four outfit slots are one line here.
 */
@Value
public class FarmingUpgrade {

    /** What the player should do about this line. */
    public enum Kind {
        /** Owned, not on. The only kind that can be acted on before setting off. */
        EQUIP,
        /** A quest, diary tier, level, house build or item that would improve a run. */
        GOAL
    }

    Kind kind;

    /** The row's item sprite, or minus an interface sprite's id, such as a spell's. */
    int itemId;

    /** The thing itself: "Farming cape", "Farmer's outfit", or a goal's action. */
    String name;

    /**
     * The whole sentence the row shows: "Wear your Explorer's ring 2 to save an inventory slot.",
     * "Finish Plague City, then use Ardougne Teleport on your runs. Opens the Ardougne Monastery
     * bush patch on the herb run."
     */
    String detail;
}
