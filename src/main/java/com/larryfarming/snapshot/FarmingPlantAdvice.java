package com.larryfarming.snapshot;

import com.google.gson.annotations.SerializedName;
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
    @SerializedName("name") String name;

    /**
     * The sprite to draw in the stop's margin, one per planting: the crop, not its seed.
     * Herb seed sprites are indistinguishable at sidebar size.
     */
    @SerializedName("produceItemId") int produceItemId;

    /**
     * How many patches here take this crop: plantings, not seeds. An allotment takes three
     * seeds for one planting. The packing list carries the seed count.
     */
    @SerializedName("patches") int patches;

    /** The herb seed this line plants, or 0 for anything that is not a herb. */
    @SerializedName("seedItemId") int seedItemId;

    /**
     * The herb the default ranking would plant here instead ("Ranarr"), or null when this
     * line is the default pick.
     *
     * <p>Set when the seeds the player carries took the run off the default ranking. The run
     * still plants what they carry; this only says it is not the recommended herb.
     */
    @SerializedName("recommendedName") String recommendedName;
}
