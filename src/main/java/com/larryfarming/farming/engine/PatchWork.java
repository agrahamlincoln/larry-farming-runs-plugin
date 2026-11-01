package com.larryfarming.farming.engine;

import com.larryfarming.engine.farming.*;
import com.larryfarming.farming.catalogue.CropData;
import com.larryfarming.farming.model.FarmingPreferences;
import com.larryfarming.model.PlayerState;
import com.larryfarming.farming.model.RunStop;
import java.util.List;
import lombok.AllArgsConstructor;

/**
 * What one patch is asking for, read against the stop that works it.
 *
 * <p>The runs list and the walkthrough both ask this class, so they cannot disagree about
 * whether a patch has work.
 *
 * <p>The verb comes from the stop's actions ({@link RunStop.StopAction#defaultsFor}), not
 * the patch type: bushes and cacti want a health check <em>and</em> a harvest.
 *
 * <p>A stop naming both does them as separate acts: the check first, then the produce.
 * Only the lumber trees (regular, hardwood and redwood) name a check and no harvest, since
 * logs are a Woodcutting errand.
 */
final class PatchWork {

    private PatchWork() {
    }

    /**
     * The one thing worth doing to this patch, or null when there is nothing — or nothing
     * we can see.
     *
     * <p>Readiness goes through {@link PatchPrediction#isReadyAt}, never a local copy, so
     * this agrees with every other "is it ready" answer. That keeps the
     * {@code LIKELY_READY} window (up to a full growth tick before a farm tick is witnessed)
     * on the side of saying nothing rather than sending the player early.
     */
    static Act at(PatchPrediction seen, RunStop stop, long nowEpochSec) {
        // Never seen is not empty: say nothing.
        if (seen == null || seen.getCrop() == null || seen.isIdle()) {
            return null;
        }
        if (seen.getState() == CropState.DISEASED) {
            return Act.CURE;
        }
        if (seen.getState() == CropState.DEAD) {
            return Act.CLEAR_DEAD;
        }
        // A standing guard crop, such as a white lily, wants nothing while it lives. Checked
        // after disease and death, the states where a guard does need help.
        if (stop.isStandingCrop() && FixedCrops.grows(stop, seen.getCrop())) {
            return null;
        }
        if (!seen.isReadyAt(nowEpochSec)) {
            return null;
        }
        // An anima plant's growth ends in its death, so one whose time has run out is dead,
        // seen or not.
        if (stop.patchType() == PatchType.ANIMA && seen.getCrop() != Crop.WEEDS) {
            return Act.CLEAR_DEAD;
        }

        List<RunStop.StopAction> actions = stop.getActions();
        if (actions.contains(RunStop.StopAction.KILL_HESPORI)) {
            return Act.KILL;
        }
        // The pre-check state is GROWING at max stage, so an unchecked patch asks for its
        // check whatever else the stop can do there.
        if (actions.contains(RunStop.StopAction.CHECK_HEALTH) && seen.getState() == CropState.GROWING) {
            return Act.CHECK;
        }
        // Everything else is picked, including a patch that has had its check. Lumber trees
        // declare no HARVEST and return null here; their replanting is standsEmpty's job.
        //
        // This does not require CropState.HARVESTABLE, which the tracker sets only from a
        // direct sighting, so a crop that finished unobserved still reads GROWING. The
        // readiness check above makes this safe.
        //
        // Crop#isProduce excludes the scarecrow, which ticks like a crop in a flower patch
        // but has nothing to pick.
        return actions.contains(RunStop.StopAction.HARVEST) && seen.getCrop().isProduce() ? Act.HARVEST : null;
    }

    /**
     * Whether this patch is ground waiting for a seed.
     *
     * <p>Weeds mean empty. So does a spent crop: a checked tree, or its stump, is something
     * to clear before a sapling goes in, not a crop to wait for.
     *
     * <p><b>"Empty" means a seed can be spent on it, not that the ground is bare.</b> A
     * checked tree still rooted answers yes. Whether it is worth felling is asked of
     * {@link com.larryfarming.engine.farming.PatchPrediction#isFellable()} by
     * {@code StopSkipRules.stillStandingIn}.
     *
     * <p>A spent crop counts only where the stop replants, and only where picking is not
     * the whole of what it offers: a bush or allotment between pickings is still working.
     * A stop that also checks health (fruit tree, calquat,
     * celastrus) is judged as spent once its fruit is taken. Bushes and cacti check health as
     * well, but a picked one keeps growing, so it is never spent.
     *
     * <p>{@link #at} has the last word, because state alone cannot separate spent from
     * waiting: a hespori ready to fight decodes HARVESTABLE with no HARVEST action, and a
     * fruit tree with fruit back reports work.
     */
    static boolean standsEmpty(PatchPrediction seen, RunStop stop, long nowEpochSec) {
        // Unseen is not empty; treating it as empty would drop a stop with real work.
        if (seen == null || seen.getCrop() == null) {
            return false;
        }
        if (seen.getCrop() == Crop.WEEDS) {
            return true;
        }
        boolean pickingIsAllItOffers = stop.getActions().contains(RunStop.StopAction.HARVEST)
                        && (!stop.getActions().contains(RunStop.StopAction.CHECK_HEALTH) || growsAbushOrCactus(stop));
        if (!stop.isPlanting() || seen.getState() != CropState.HARVESTABLE || pickingIsAllItOffers) {
            return false;
        }
        return at(seen, stop, nowEpochSec) == null;
    }

    /**
     * Whether this patch holds a grown bush or cactus to dig up this visit, so a fresh one
     * goes in after the picking.
     *
     * <p>Replanted every visit, its own crop included. A fresh plant pays its health check
     * again, which is most of a bush's or cactus's experience; picking a kept one pays only
     * the harvest. A whiteberry bush checks for 437.5 and picks for 29 a berry, and it grows
     * in 160 minutes, as long as the two herb runs between visits. A potato cactus checks
     * for 230 and grows in 70.
     *
     * <p>Grown means growing done, checked or not: a bush still filling out keeps its patch
     * until then, and a diseased one wants curing, not digging up.
     *
     * <p>Only where the next crop can go in: the player has the Farming level and is not
     * known to be out of its seed. Otherwise digging up a bearing bush gains nothing.
     */
    static boolean comesOut(PatchPrediction seen, RunStop stop, FarmingPreferences prefs, PlayerState player) {
        if (seen == null || seen.getCrop() == null || seen.isIdle() || seen.getCrop() == Crop.WEEDS || !growsAbushOrCactus(stop)
                || !stop.isFixedCrop() || !stop.isPlanting()) {
            return false;
        }
        CropState state = seen.getState();
        if (state == CropState.DEAD || state == CropState.DISEASED || StopSkipRules.isStillGrowing(seen)) {
            return false;
        }
        if (FixedCrops.ticked(stop, prefs).isEmpty()) {
            return false;
        }
        Crop next = FixedCrops.cropFor(stop, prefs, player);
        CropData data = next == null ? null : CropData.forCrop(next);
        return data != null && !FixedCrops.belowLevel(player, data) && !SeedSelector.definitelyOutOf(next, player, data.getSeedsPerPatch());
    }

    /**
     * Whether a bush or cactus grows here. Both are checked, then picked again and again,
     * so a picked one is never spent ground; it comes out when it dies or by
     * {@link #comesOut}.
     */
    private static boolean growsAbushOrCactus(RunStop stop) {
        PatchType type = stop.patchType();
        return type == PatchType.BUSH || type == PatchType.CACTUS;
    }

    /**
     * What a patch is waiting for, most urgent first. The order is the ranking readers
     * walk, so a diseased patch outranks a harvest at the same arrival.
     */
    @AllArgsConstructor
    enum Act {
        CURE("Cure"),
        // Not DIG_UP, which StopAction uses for taking a stump out after a check.
        CLEAR_DEAD("Clear dead"),
        KILL("Kill"),
        CHECK("Check"),
        HARVEST("Harvest");

        /** The act as a row's leading verb; the row adds the crop and the patch count. */
        final String verb;
    }
}
