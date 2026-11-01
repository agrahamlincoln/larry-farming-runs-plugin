package com.larryfarming.farming.engine;

import com.larryfarming.engine.farming.Crop;
import java.util.List;
import lombok.Value;

/**
 * The seeds for one end of the rotation, one per patch.
 *
 * <p>A list rather than a single crop because the rotation's unit is the seed: two good
 * seeds are two seeds off the top of the tier list, and a player whose best stack has one
 * seed left takes that one and the next one down.
 *
 * <p>Shorter than {@link #getWanted()} when the stock could not fill every patch.
 */
@Value
public class SeedAllocation {

    /**
     * One crop per patch, best seed first. Never null.
     *
     * <p>Best-first rather than in visit order, because the per-player patch ranking
     * decides which patch gets which seed. Catherby is worth the best seed only to a player
     * with the Kandarin diary.
     */
    List<Crop> perPatch;

    /** How many patches this end has to fill. */
    int wanted;

    /**
     * False when the answer rests on containers we have not been shown. Seeds live in the
     * seed vault, which the game reports only while it is open, so before we have seen it
     * an empty allocation means "I have not looked" rather than "you have none".
     */
    boolean confident;

    /** The crop for the nth best patch of this end, or null once the seeds run out. */
    public Crop at(int index) {
        return index >= 0 && index < perPatch.size() ? perPatch.get(index) : null;
    }

    /** One crop and how many patches take it. */
    @Value
    public static class Tally {
        Crop crop;
        int count;
    }
}
