package com.larryfarming.snapshot;

import com.google.gson.annotations.SerializedName;
import lombok.Value;

/**
 * One thing to have in the bag before setting off.
 */
@Value
public class FarmingCarryItem {

    /** Why it is on the list; also how the rows are grouped. */
    public enum Kind {
        /** Spade, dibber, compost — the same every run. */
        TOOL,
        /** Seeds for the patches, in the quantities the rotation worked out. */
        SEED,
        /**
         * Whatever a chosen route depends on being in the bag — a tablet, a piece of
         * jewellery, or the coins a minecart fare is paid in.
         */
        TRAVEL,
        /** Runes for a teleport a chosen route casts. */
        RUNES
    }

    /** How close this line is to being sorted. */
    public enum Availability {
        /** In the bag or worn: nothing to do, this one is ready to go. */
        CARRIED,
        /**
         * In tool-leprechaun storage: no inventory slot and no detour before leaving, but
         * still a withdrawal on arrival. Leprechaun storage is per-account, so anything in
         * it is reachable at every patch that has a leprechaun.
         */
        AT_LEPRECHAUN,
        /**
         * Owned, and the only place it can be is the seed box in the bag, which has not
         * been checked, or seen to take in or give out seeds, this session, so we cannot
         * confirm the seeds are still there.
         *
         * <p>Not {@link #CARRIED}, because the seeds may have been used in an unseen session.
         * Not {@link #BANKED}, because the player is very likely carrying them. Checking the
         * box settles it before the player walks anywhere.
         *
         * <p>Counted as a withdrawal everywhere a count is taken, so the bag is sized for the
         * seeds coming out of a bank.
         */
        IN_SEED_BOX,
        /** Owned, but in a bank or vault — still a withdrawal between here and leaving. */
        BANKED,
        /** Not owned anywhere we can see. */
        MISSING
    }

    @SerializedName("kind") Kind kind;

    @SerializedName("itemId") int itemId;

    @SerializedName("name") String name;

    /** How many, or 0 when the count is not the point (one spade is one spade). */
    @SerializedName("quantity") int quantity;

    /** Where this item is, in the degrees that change what the player does next. */
    @SerializedName("availability") Availability availability;

    /** The short tag drawn over a seed ("TOA", "SNA"), or empty for anything else. */
    @SerializedName("tag") String tag;

    /**
     * A short caveat about this line (a cheaper fare on a condition, say), or empty.
     *
     * <p>Kept out of {@link #name} because the name feeds the hover and the still-to-get
     * line, while the panel draws the note under the grid.
     */
    @SerializedName("note") String note;

    /**
     * How many of this are on the player right now, counting every form that would do.
     *
     * <p>{@link #availability} is a verdict and cannot be spent. A trip that breaks at
     * several banks draws on the same stack more than once, so it needs this count: two
     * guam seeds cover a planting before the bank stop and another after it, where the
     * {@code CARRIED} verdict read twice would cover both from one seed.
     *
     * <p>Counted over the same ids as the verdict, so a line a sapling satisfies counts
     * saplings. Zero when the player holds none, and zero for
     * {@link Availability#IN_SEED_BOX}: that is a deliberate under-claim, so an unconfirmed
     * seed box cannot settle a shortfall.
     *
     * <p><b>True only of a packing-list line.</b> On a bank row's withdrawal the quantity is
     * a shortfall against a simulated future bag, while this still reports the real bag.
     * The two describe different moments and must not be subtracted from one another.
     */
    @SerializedName("carriedCount") int carriedCount;

    /** True when nothing stands between the player and setting off with this. */
    public boolean isCarried() {
        return availability == Availability.CARRIED;
    }

    /** True when the player owns it at all, wherever it happens to be. */
    public boolean isOwned() {
        return availability != Availability.MISSING;
    }
}
