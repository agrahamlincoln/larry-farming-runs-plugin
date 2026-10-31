package com.larryfarming.farming.engine;

import com.larryfarming.farming.catalogue.PatchAttributes;
import com.larryfarming.farming.detection.CapabilityState;
import com.larryfarming.farming.detection.DiaryTiers;
import com.larryfarming.farming.model.PatchRef;
import com.larryfarming.model.PlayerState;
import com.larryfarming.model.QuestStatus;
import java.util.List;

/**
 * How good a home a patch is for a seed, as a score the allocator sorts on.
 *
 * <p>The score knows nothing about travel and does not change with the walk.
 * {@link Itinerary} reads it separately to order the herb rotation's visits.
 *
 * <p><b>Everything here is a function of player state.</b> Hosidius is disease-free only
 * with the Easy Kourend &amp; Kebos diary; Catherby's yield bonus is +5/+10/+15% at
 * Kandarin Medium/Hard/Elite; Trollheim and Weiss are granted theirs by quests. So the
 * score is recomputed rather than declared.
 *
 * <h2>The ranking</h2>
 *
 * <p>Bands, high to low: <b>yield bonus, disease immunity, experience bonus, easy travel,
 * ordinary, reserved.</b> Within a band the bonus is graded, so a Kandarin Elite Catherby
 * outranks a Hard one. The weights encode only that ordering, not an exchange rate between
 * bands.
 *
 * <p>Reserved sits <em>below</em> ordinary. The Farming Guild herb patch has a yield bonus,
 * but the default route holds it back for contracts, so it must not draw a rotation seed.
 */
public final class PatchPriority {

    /**
     * Band weights, spaced so a graded bonus (at most 15) can never lift a patch out of its
     * band.
     */
    private static final int BAND_YIELD = 1000;
    private static final int BAND_DISEASE_FREE = 100;
    private static final int BAND_XP = 50;
    private static final int BAND_EASY_TRAVEL = 10;
    private static final int BAND_ORDINARY = 0;
    private static final int BAND_RESERVED = -1000;

    private PatchPriority() {
    }

    /**
     * This patch's score for this player, higher being a better home for a better seed.
     *
     * <p>Unknown reads as unearned. {@link DiaryTiers#completed} answers UNKNOWN until the
     * flag is reported, and crediting a bonus on that could put the best seed in a patch
     * without the bonus. Under-crediting only costs seed placement.
     *
     * @param patch the patch being scored
     * @param state the player, or null before anything has been reported
     */
    public static int scoreOf(PatchRef patch, PlayerState state) {
        PatchAttributes.Entry entry = PatchAttributes.of(patch);
        if (entry == null) {
            return BAND_ORDINARY;
        }
        if (entry.isReservedForContracts()) {
            return BAND_RESERVED;
        }

        int yield = bestBonus(entry.getYieldTiers(), state);
        if (yield > 0) {
            return BAND_YIELD + yield;
        }
        if (isDiseaseFree(entry, state)) {
            return BAND_DISEASE_FREE;
        }
        int xp = bestBonus(entry.getXpTiers(), state);
        if (xp > 0) {
            return BAND_XP + xp;
        }
        return entry.isEasyTravel() ? BAND_EASY_TRAVEL : BAND_ORDINARY;
    }

    /**
     * Whether this patch is held back for contracts.
     *
     * <p>Asked directly because {@link #scoreOf}'s floor band cannot enforce it alone: the
     * reserved patch is the only patch in its family, so with nothing ranked against it,
     * it would still draw the best herb. Callers use this to exclude it when counting the
     * good seeds a run has to place.
     *
     * <p>Not a function of player state: the reservation is a routing choice, not an
     * earned bonus.
     */
    public static boolean isReservedForContracts(PatchRef patch) {
        PatchAttributes.Entry entry = PatchAttributes.of(patch);
        return entry != null && entry.isReservedForContracts();
    }

    /**
     * The best percentage this player has earned from a graded set of bonuses, or 0.
     *
     * <p>The best rather than the sum: the tiers do not stack. Kandarin Elite <em>is</em>
     * 15%, not 15% on top of Hard's 10%.
     */
    private static int bestBonus(List<PatchAttributes.BonusTier> tiers, PlayerState state) {
        int best = 0;
        for (PatchAttributes.BonusTier tier : tiers) {
            if (DiaryTiers.completed(tier.getDiaryArea(), tier.getDiaryTier(), state)
                    == CapabilityState.HAVE) {
                best = Math.max(best, tier.getPercent());
            }
        }
        return best;
    }

    /** Whether disease cannot touch this patch for this player, by diary or by quest. */
    private static boolean isDiseaseFree(PatchAttributes.Entry entry, PlayerState state) {
        if (entry.isAlwaysDiseaseFree()) {
            return true;
        }
        if (entry.getDiseaseFreeDiaryArea() != null
                && DiaryTiers.completed(entry.getDiseaseFreeDiaryArea(),
                        entry.getDiseaseFreeDiaryTier(), state) == CapabilityState.HAVE) {
            return true;
        }
        return entry.getDiseaseFreeQuest() != null
                && state != null
                && state.getQuests().get(entry.getDiseaseFreeQuest()) == QuestStatus.COMPLETED;
    }
}
