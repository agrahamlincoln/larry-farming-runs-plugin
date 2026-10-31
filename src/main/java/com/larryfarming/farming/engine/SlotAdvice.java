package com.larryfarming.farming.engine;

import com.larryfarming.farming.model.GearPick;
import com.larryfarming.model.EquipmentSlot;
import java.util.List;
import lombok.Value;

/**
 * What one equipment slot should hold, judged against what the player has.
 */
@Value
public class SlotAdvice {

    /** How a single tier stands with this player. */
    public enum Standing {
        /** Worn right now. */
        WORN,
        /** In the inventory, which serves every item that need not be worn. */
        CARRIED,
        /** Owned, and sitting somewhere other than the slot. */
        OWNED,
        /** Not yet earned. */
        UNEARNED
    }

    /** One tier and where the player stands on it. */
    @Value
    public static class Tier {
        GearPick pick;
        Standing standing;
    }

    /** What the slot as a whole says. */
    public enum Verdict {
        /**
         * The best tier the player owns is where the run wants it: worn, or carried when
         * it works from the inventory.
         */
        SET,
        /** They own something better than what is on hand, or nothing is on hand. */
        SWAP,
        /** Nothing in this chain has been earned yet. */
        NOTHING_YET
    }

    EquipmentSlot slot;
    String purpose;
    boolean niceToHave;

    /** What this slot is part of, or null when it stands alone. */
    String group;

    /** True when the item works only while worn; see {@code LoadoutSlot.mustWear}. */
    boolean mustWear;

    /**
     * The sentence for the upgrade list when the verdict is {@link Verdict#SWAP}, already
     * naming the item, or empty for any other verdict.
     */
    String tip;

    /** Every tier, best first, each marked with where the player stands. */
    List<Tier> tiers;

    Verdict verdict;

    /** The tier to wear: the best one owned, or null when none is. */
    GearPick best;

    /** Player-facing sentence: what to do about this slot, or why nothing to do. */
    String because;

    /**
     * False when the answer rests on a container we have not been shown. The bank is
     * reported only while it is open, so before the player opens it "you own none of
     * these" means nobody has looked.
     */
    boolean confident;

    /** The tier the upgrade list asks the player to earn while none is owned. */
    GearPick goal;

    /** The upgrade list's sentence for {@link #goal}. */
    String goalSentence;

    public boolean needsAction() {
        return verdict == Verdict.SWAP;
    }
}
