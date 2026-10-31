package com.larryfarming.farming.engine;

import com.larryfarming.model.PlayerState;
import net.runelite.api.gameval.ItemID;

/**
 * Whether the bag should carry something to put run energy back, and what.
 *
 * <p>A farm run is mostly walking between teleport landings and patches, and an empty bar
 * turns a two-minute leg into a five-minute one. This class holds only the arithmetic and
 * the threshold; it knows nothing about routes.
 */
public final class EnergyTopUp {

    private EnergyTopUp() {
    }

    /**
     * What one dose puts back, as a percentage.
     *
     * <p>From the wiki's {@code Energy potion} page: "An Energy potion restores 10% of
     * the player's run energy."
     */
    public static final int PERCENT_PER_DOSE = 10;

    /**
     * The client's unit for a full bar: {@code Client.getEnergy()} reports run energy in
     * hundredths of a percent, so a full bar is 10,000, not 100.
     */
    public static final int FULL_BAR = 10_000;

    /**
     * Below this percentage, the row appears. A chosen prompt threshold, not a game fact.
     */
    public static final int OFFER_BELOW_PERCENT = 50;

    /**
     * At or above this percentage, an offer made below {@link #OFFER_BELOW_PERCENT} is
     * withdrawn. Running drains and resting refills the bar a percent at a time, so a single
     * line would flicker the row on and off while the player walks.
     */
    public static final int WITHDRAW_AT_PERCENT = 60;

    /**
     * Energy potions, best dose first.
     *
     * <p>Stamina potions are deliberately absent: they suppress drain for a while rather than
     * restore a percentage, so the dose arithmetic here does not apply to them.
     */
    private static final int[] ENERGY_POTIONS = {
            ItemID._4DOSE1ENERGY, ItemID._3DOSE1ENERGY,
            ItemID._2DOSE1ENERGY, ItemID._1DOSE1ENERGY};

    /**
     * Run energy as a whole percentage, 0–100.
     *
     * <p>The raw-reading overloads let a caller ask about a value it has not stored yet; the
     * plugin uses them each tick to decide whether a new reading is worth pushing.
     */
    public static int percentOf(int runEnergy) {
        return Math.max(0, Math.min(100, runEnergy * 100 / FULL_BAR));
    }

    /**
     * Whether a bar at this reading is low, given whether it was low before: below
     * {@link #OFFER_BELOW_PERCENT} it is, from {@link #WITHDRAW_AT_PERCENT} it is not, and in
     * between it stays as it was. Takes a real reading.
     */
    public static boolean lowAfter(boolean wasLow, int runEnergy) {
        int percent = percentOf(runEnergy);
        return percent < OFFER_BELOW_PERCENT || wasLow && percent < WITHDRAW_AT_PERCENT;
    }

    /**
     * Doses that would fill the bar from here, rounded up. Used to word the row, not to size
     * it: {@code CarryPlanner} packs one potion regardless.
     */
    public static int dosesToFill(int runEnergy) {
        int missing = 100 - percentOf(runEnergy);
        return (missing + PERCENT_PER_DOSE - 1) / PERCENT_PER_DOSE;
    }

    /** Whether the orb has been read for this player at all. */
    public static boolean known(PlayerState state) {
        return state != null && state.getRunEnergy() != PlayerState.RUN_ENERGY_UNREAD;
    }

    /** Run energy as a whole percentage for a player, or 100 when we have never read it. */
    public static int percentOf(PlayerState state) {
        return known(state) ? percentOf(state.getRunEnergy()) : 100;
    }

    /**
     * Whether this player's bag should carry a potion. An unread orb reads as full, so no
     * potion is offered before the first reading. Zero is a real reading (an empty bar),
     * distinct from {@link PlayerState#RUN_ENERGY_UNREAD}.
     */
    public static boolean worthCarrying(PlayerState state) {
        return known(state) && percentOf(state) < lineFor(state);
    }

    /**
     * The percentage the bar must reach before nothing is offered: the offer line, or the
     * withdraw line while an offer stands (see {@link #lowAfter}).
     */
    private static int lineFor(PlayerState state) {
        return state.isRunEnergyLow() ? WITHDRAW_AT_PERCENT : OFFER_BELOW_PERCENT;
    }

    /** Doses that would fill this player's bar, or none when the orb is unread. */
    public static int dosesToFill(PlayerState state) {
        return known(state) ? dosesToFill(state.getRunEnergy()) : 0;
    }

    // --- energy the player already has, without spending a slot on it ------------

    /**
     * The bar as it would read after spending all free restores, capped at 100.
     *
     * <p>{@code freePercent} is energy the player can put back without carrying anything,
     * such as today's unspent Explorer's ring restores. It is an uncapped total; this method
     * does the clamping. With zero it equals {@link #percentOf(PlayerState)}.
     */
    public static int percentWithFree(PlayerState state, int freePercent) {
        return Math.min(100, percentOf(state) + Math.max(0, freePercent));
    }

    /** Whether a potion is still worth a slot once the free restores are counted. */
    public static boolean worthCarrying(PlayerState state, int freePercent) {
        return known(state) && percentWithFree(state, freePercent) < lineFor(state);
    }

    /** Doses that would fill the bar after the free restores have gone in. */
    public static int dosesToFill(PlayerState state, int freePercent) {
        if (!known(state)) {
            return 0;
        }
        int missing = 100 - percentWithFree(state, freePercent);
        return (missing + PERCENT_PER_DOSE - 1) / PERCENT_PER_DOSE;
    }

    /** A copy of the energy potion ids, best dose first. */
    public static int[] potionIds() {
        return ENERGY_POTIONS.clone();
    }
}
