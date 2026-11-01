package com.larryfarming.snapshot;

import lombok.Value;

/**
 * One thing to put in the ground at a stop.
 *
 * <p>A stop is a place, and a place can hold several patches: Catherby is a herb patch, a
 * flower patch and an allotment reached by one teleport. So a stop carries a list of these.
 */
@Value
public class FarmingPlantAdvice {

    /** Named for a player: "Toadflax", "Snape grass". */
    String name;

    /**
     * The sprite to draw in the stop's margin, one per planting: the crop, not its seed.
     * Herb seed sprites are indistinguishable at sidebar size.
     */
    int produceItemId;

    /**
     * How many patches here take this crop: plantings, not seeds. An allotment takes three
     * seeds for one planting. The packing list carries the seed count.
     */
    int patches;

    /** A patch count as every stop row writes it after the crop: " ×3", nothing for one. */
    public static String countOf(int patches) {
        return patches > 1 ? " ×" + patches : "";
    }
}
