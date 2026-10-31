package com.larryfarming.engine.farming;

/**
 * Health/lifecycle state of a farming patch, decoded from its transmit varbit.
 *
 * <p>DISEASED and DEAD are only ever set from a direct observation — the game
 * transmits patch varbits only while the player is near the patch, so a patch
 * last seen GROWING is predicted straight to harvest and any disease that
 * struck while unobserved stays invisible until the player next walks by.
 * This mirrors RuneLite's Time Tracking plugin, which never simulates disease.
 */
public enum CropState {
    GROWING,
    HARVESTABLE,
    DISEASED,
    DEAD,
    /** Compost bin being filled — an idle state with no timer, like a weedy patch. */
    FILLING,
    /** Compost bin standing empty — likewise idle. */
    EMPTY;
}
