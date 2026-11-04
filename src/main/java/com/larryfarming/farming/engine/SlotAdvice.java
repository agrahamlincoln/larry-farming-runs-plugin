package com.larryfarming.farming.engine;

import com.larryfarming.farming.model.GearPick;
import com.larryfarming.farming.model.LoadoutSlot;
import lombok.Value;
import lombok.With;

/**
 * What one equipment slot should hold, judged against what the player has.
 */
@Value
public class SlotAdvice {

    /** What the slot as a whole says. */
    public enum Verdict {
        /**
         * The best tier the player owns is where the run wants it: worn, or carried when
         * it works from the inventory.
         */
        SET,
        /** They own something better than what is on hand, or nothing is on hand. */
        SWAP,
        /** Nothing in this chain has been earned yet, or the bank has not been seen. */
        NOTHING_YET
    }

    LoadoutSlot slot;

    Verdict verdict;

    /** The tier to wear: the best one owned, in the form held, or null when none is. */
    GearPick best;

    /**
     * The sentence for the upgrade list when the verdict is {@link Verdict#SWAP}, already
     * naming the item, or empty for any other verdict.
     */
    @With
    String tip;
}
