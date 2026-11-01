package com.larryfarming.snapshot;

/**
 * Why a stop is not being visited, as a comparable value so a stop with several reasons
 * can pick the one its row carries.
 *
 * <p>The ordinal is the priority, most worth saying first. <b>A shortfall the player can act
 * on outranks a wait that resolves itself:</b> a growing patch will be ready whether or not
 * the player is told, but an empty seed stack stays empty until they act.
 */
public enum FarmingSkipReason {

    /** The seed for this patch is not in stock anywhere we can see. */
    NO_SEED("no seed"),

    /**
     * The seed is owned but the player lacks the Farming level to plant it. Kept apart from
     * {@link #NO_SEED} so the player is not told to buy seeds they already have.
     */
    NEEDS_LEVEL("Farming level too low"),

    /**
     * A fairy ring would reach it, but the player has no way to stand at a ring: every item
     * approach is switched off or not owned. Apart from {@link #NO_ROUTE} because a switch
     * in the side panel fixes it.
     */
    NO_FAIRY_RING_ROUTE("no way to a fairy ring"),

    /** Reachable, but the only route left is too slow to be worth it. */
    NOT_WORTH_TRIP("not worth the trip"),

    /** Nothing reaches it at all, and the route table says no alternative exists. */
    NO_ROUTE("can't reach"),

    /**
     * Every listed route has failed and no fallback is listed.
     *
     * <p>Apart from {@link #NO_ROUTE}: the patch may still be reachable on foot, and several
     * of these can simply be walked to.
     */
    NO_ROUTE_LISTED("no known route"),

    /**
     * Something is still growing here, so there is nothing to harvest and no room to
     * plant. The only reason that comes with a time attached.
     */
    STILL_GROWING("growing"),

    /**
     * A crop the default route plants and then leaves alone across several runs, such as a white
     * lily. Last because the patch is doing what it was planted to do.
     */
    STANDING_CROP("keep the crop");

    private final String label;

    FarmingSkipReason(String label) {
        this.label = label;
    }

    /** Two or three words for a row heading, where the full clause will not fit. */
    public String getLabel() {
        return label;
    }

    /**
     * The more worth saying of two reasons for the same stop. Either argument may be null;
     * the other is returned.
     */
    public static FarmingSkipReason moreUrgent(FarmingSkipReason a, FarmingSkipReason b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.ordinal() <= b.ordinal() ? a : b;
    }
}
