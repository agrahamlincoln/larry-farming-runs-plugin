package com.larryfarming.farming.detection;

import com.larryfarming.model.PlayerState;
import net.runelite.api.gameval.VarbitID;

/**
 * What the Explorer's ring can still put back into the run-energy bar today.
 *
 * <p>The restore allowance is separate from the cabbage-teleport allowance, which
 * {@link com.larryfarming.farming.engine.DailyTeleportLedger} tracks. This lets the packing list
 * drop a ring whose restores are spent.
 *
 * <p><b>No ledger: the game keeps the count.</b> Varbit 4553 counts restores spent today
 * upward from zero, like its cabbage-teleport sibling 4552 (RuneLite's
 * {@code ItemChargePlugin} reports remaining charges as {@code MAX - value}). The restores
 * replenish only after a logout and login, not at midnight (wiki), so a self-rolled ledger
 * would hand out charges the game has not given back yet.
 *
 * <p>The tier is read from the diary, not from the item, for the reason {@link DiaryRewards}
 * gives. It reads the Lumbridge diary varbits directly rather than through
 * {@link com.larryfarming.farming.model.Capability}, because Explorer's ring 1 has restores but no
 * teleport, and no travel chain asks for it.
 */
public final class RingEnergyCharges {

    private RingEnergyCharges() {
    }

    /** Restores spent today, counting up from zero. Scanned at login; live after that. */
    public static final int SPENT_VARBIT = VarbitID.LUMBRIDGE_ENERGY_RESTORE;

    private static final String DIARY_AREA = "Lumbridge";

    /**
     * Each tier's allowance and what one restore is worth, indexed by tier. From the wiki
     * ({@code Explorer's ring 1..4}). The elite ring gives more per use but fewer uses than
     * the hard ring.
     */
    private static final int[] RESTORES_PER_DAY = {0, 2, 3, 4, 3};
    private static final int[] PERCENT_PER_RESTORE = {0, 50, 50, 50, 100};

    private static final int[] ITEM_ID = {0, 13125, 13126, 13127, 13128};

    /** What the ring can still do for the bar today, or null when there is no ring. */
    public static final class Charges {
        private final int tier;
        private final int restoresLeft;

        private Charges(int tier, int restoresLeft) {
            this.tier = tier;
            this.restoresLeft = restoresLeft;
        }

        /** The tier the player has earned, 1 to 4. */
        public int getTier() {
            return tier;
        }

        public int getItemId() {
            return ITEM_ID[tier];
        }

        /** The game's item name, which the player looks for in a bank. */
        public String getName() {
            return "Explorer's ring " + tier;
        }

        /** Restores still unspent today. */
        public int getRestoresLeft() {
            return restoresLeft;
        }

        /** What one restore puts back, as a percentage of the bar. */
        public int getPercentPerRestore() {
            return PERCENT_PER_RESTORE[tier];
        }

        /**
         * Everything the ring could put back today if the player spent it all. Not capped
         * by the room left in the bar; {@link com.larryfarming.farming.engine.EnergyTopUp} does that.
         */
        public int getFreePercent() {
            return restoresLeft * getPercentPerRestore();
        }
    }

    /** Every id a ring line accepts, so a worn ring of any tier ticks it off. */
    public static int[] ringItemIds() {
        return new int[] {13125, 13126, 13127, 13128};
    }

    /**
     * What today has left on this player's ring, or null when no Lumbridge diary tier is
     * known to be finished (none done, or the flags are not read yet).
     *
     * <p>An unread {@link #SPENT_VARBIT} reads as nothing spent. The login scan reads it, so
     * the window is narrow, and offering a spent ring costs less than hiding a charged one.
     */
    public static Charges of(PlayerState state) {
        if (state == null) {
            return null;
        }
        int tier = earnedTier(state);
        if (tier == 0) {
            return null;
        }
        Integer spent = state.getVarbits().get(SPENT_VARBIT);
        int used = spent == null ? 0 : Math.max(0, spent);
        int left = Math.max(0, RESTORES_PER_DAY[tier] - used);
        return new Charges(tier, left);
    }

    /** The best Lumbridge tier known to be finished, or 0. */
    private static int earnedTier(PlayerState state) {
        int best = 0;
        String[] tiers = {DiaryTiers.EASY, DiaryTiers.MEDIUM, DiaryTiers.HARD,
                DiaryTiers.ELITE};
        for (int i = 0; i < tiers.length; i++) {
            if (DiaryTiers.completed(DIARY_AREA, tiers[i], state) == CapabilityState.HAVE) {
                best = i + 1;
            }
        }
        return best;
    }
}
