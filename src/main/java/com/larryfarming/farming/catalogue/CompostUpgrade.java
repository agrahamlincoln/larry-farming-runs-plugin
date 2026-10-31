package com.larryfarming.farming.catalogue;

import com.larryfarming.engine.farming.Crop;
import net.runelite.api.gameval.ItemID;

/**
 * What a bin of finished compost still needs before it is worth emptying.
 *
 * <p>{@link Compostables} answers what goes into a bin. A finished bin of compost or
 * supercompost can be upgraded one tier in place, the whole bin at once, for one dose of
 * compost potion or a fixed pile of volcanic ash.
 *
 * <p>Upgrading buckets afterwards costs a dose or two ash per bucket, so the in-place
 * upgrade is far cheaper. It is only possible before the first bucket comes out, which is
 * why a bank stop must know about it.
 */
public final class CompostUpgrade {

    private CompostUpgrade() {
    }

    /**
     * Volcanic ash, which the game files call {@code FOSSIL_VOLCANIC_ASH}.
     */
    public static final int VOLCANIC_ASH = ItemID.FOSSIL_VOLCANIC_ASH;

    /**
     * Compost potion, every dose it comes in, best first.
     *
     * <p>{@code SUPERCOMPOST_POTION_4} is the item the game calls "Compost potion(4)"; the
     * constants are named for what the potion makes.
     *
     * <p>Best-first by dose, the order {@code bestOwned} wants.
     */
    private static final int[] COMPOST_POTIONS = {
        ItemID.SUPERCOMPOST_POTION_4, ItemID.SUPERCOMPOST_POTION_3,
        ItemID.SUPERCOMPOST_POTION_2, ItemID.SUPERCOMPOST_POTION_1};

    /**
     * The potion ids to tally a holding over, best first.
     *
     * <p>Returns a copy, so a caller sorting it cannot change what {@link #dosesIn} and
     * {@link #potionForDoses} mean.
     */
    public static int[] compostPotions() {
        return COMPOST_POTIONS.clone();
    }

    /**
     * Ash to lift a whole bin from supercompost to ultracompost.
     *
     * <p>The Farming Guild's big bin holds thirty buckets and takes fifty.
     */
    private static final int ASH_PER_BIN = 25;
    private static final int ASH_PER_BIG_BIN = 50;

    /**
     * How many doses are in this vial, or 0 for anything that is not one.
     *
     * <p>Count doses, not vials: one bin spends one dose, so a four-dose vial covers four bins.
     */
    public static int dosesIn(int itemId) {
        for (int index = 0; index < COMPOST_POTIONS.length; index++) {
            if (COMPOST_POTIONS[index] == itemId) {
                return COMPOST_POTIONS.length - index;
            }
        }
        return 0;
    }

    /**
     * The vial that holds this many doses, for drawing a requirement rather than a holding.
     *
     * <p>Drawn by what is wanted, not what the player holds: a sprite of their one-dose would
     * imply they are equipped when they need three.
     *
     * <p>Clamped to 1..4 doses; the line's count shows anything beyond one vial.
     */
    public static int potionForDoses(int doses) {
        int held = Math.max(1, Math.min(doses, COMPOST_POTIONS.length));
        return COMPOST_POTIONS[COMPOST_POTIONS.length - held];
    }

    /**
     * Whether this finished bin needs a dose of compost potion first.
     *
     * <p>True for plain compost: ash only works on supercompost. One dose covers any bin
     * size, including the big compost bin, so this answers yes or no rather than a count.
     *
     * @param finished the crop a finished bin decodes as, which names its grade; anything
     *                 else, including a bin still filling, returns false
     */
    public static boolean wantsPotion(Crop finished) {
        return finished == Crop.COMPOST || finished == Crop.BIG_COMPOST;
    }

    /**
     * How much volcanic ash this finished bin takes, or 0 where none would help.
     *
     * <p>Plain compost counts too, since the potion and the ash are one errand. Rotten
     * tomatoes and ultracompost take none.
     *
     * <p>The count does not scale with what is left in the bin: the wiki says the full pile
     * is consumed "regardless of the fullness level".
     */
    public static int ashFor(Crop finished) {
        if (finished == Crop.COMPOST || finished == Crop.SUPERCOMPOST) {
            return ASH_PER_BIN;
        }
        if (finished == Crop.BIG_COMPOST || finished == Crop.BIG_SUPERCOMPOST) {
            return ASH_PER_BIG_BIN;
        }
        return 0;
    }
}
