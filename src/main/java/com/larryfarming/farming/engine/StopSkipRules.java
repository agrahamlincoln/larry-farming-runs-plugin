package com.larryfarming.farming.engine;

import static com.larryfarming.farming.engine.CropNames.inSentence;

import com.larryfarming.engine.farming.*;
import com.larryfarming.farming.catalogue.CropData;
import com.larryfarming.farming.catalogue.FarmingCatalogue;
import com.larryfarming.farming.model.*;
import com.larryfarming.model.PlayerState;
import com.larryfarming.snapshot.FarmingSkipReason;
import java.util.ArrayList;
import java.util.List;

/**
 * The stops a run leaves alone for what is in their ground or in the player's stock: a
 * standing crop still doing its job, a patch still growing, a seed the player does not
 * have.
 *
 * <p>Route refusals are {@link RunPlanner}'s. These rules run after it, on the stops it
 * routed, and turn a visit into a skip or drop it.
 */
final class StopSkipRules {

    private StopSkipRules() {
    }

    /**
     * Drops the stops that today's trip has nothing to do at: a standing crop still doing
     * its job, an every-second-run extra whose patch is not due yet, or a patch simply
     * still occupied.
     *
     * <p>A white lily protects the allotments beside it until it dies, so a patch seen
     * holding a living one has nothing to do at it. The bush and cactus crops are visited
     * every few runs; rather than count runs, the pacing is read off the patch: still
     * growing means not due.
     *
     * <p>Only when the patch has been seen. Patch varbits read true only while the player is
     * in the region, so an unknown patch keeps its stop: a missed lily costs a diseased
     * crop and a skipped ready cactus a whole harvest, while a wasted visit costs a moment.
     */
    static List<StopPlan> leaveStandingCrops(FarmingRun run, List<StopPlan> plans, FarmingInputs inputs, PlayerState state) {
        List<StopPlan> kept = new ArrayList<>(plans.size());
        for (StopPlan plan : plans) {
            RunStop stop = plan.getStop();
            // Dropped, not skipped: a switched-off crop needs no row explaining it.
            if (PlantingGate.silences(run, stop, inputs.getPreferences())) {
                continue;
            }
            if (!plan.isVisit()) {
                kept.add(plan);
                continue;
            }
            // Before the patch rules below. The contract's stop names the guild herb patch
            // only as a place to be; its verdict comes from the runs list, which reads the
            // contract's own varbits.
            if (stop.isErrand()) {
                kept.add(inputs.contractIsStillGrowing() ? skipUntilTheErrandIsDue(stop, inputs) : plan);
                continue;
            }
            PatchPrediction seen = inputs.patchAt(stop.primaryPatch());
            StopPlan skip = null;
            if (stop.isFixedCrop()) {
                Crop crop = FixedCrops.cropFor(stop, inputs.getPreferences(), state);
                if (stop.isStandingCrop()) {
                    skip = skipIfStanding(stop, seen);
                } else if (FarmingCatalogue.everyNthRun(stop, crop) > 1) {
                    skip = skipIfStillGrowing(stop, seen);
                }
                // The standing and pacing rules read only the stop's own crops. Any crop
                // still growing in every patch blocks the seed, as at a rotation stop, so
                // the visit would have nothing to do. A redberry bush growing in the
                // whiteberry patch skips the stop.
                if (skip == null && stop.isPlanting()) {
                    skip = skipIfPatchesStillFull(stop, inputs);
                }
                // A bush or cactus picked bare reads HARVESTABLE while it restocks, so the
                // growth rules above miss it. With nothing to pick and no room for a seed,
                // the visit would have nothing to do.
                if (skip == null && stop.isPlanting() && !anythingToDoAt(stop, inputs)
                        && CropAssigner.openFixedPatches(stop, inputs, state) == 0) {
                    skip = skipWhileRestocking(stop, inputs);
                }
                // A missing seed may take over a growth skip: the wait resolves itself, the
                // empty stack does not. foldIn keeps the growth estimate. Not asked where
                // work is waiting: a ready limpwurt is worth the harvest without a seed to
                // follow, and the runs list counts it ready. A dead anima plant is not such
                // work: it is cleared only to plant the next.
                if (!anythingToDoAt(stop, inputs) || stop.patchType() == PatchType.ANIMA) {
                    skip = foldIn(skip, skipIfNoSeed(stop, crop, state));
                }
            } else if (stop.isPlanting()) {
                skip = skipIfPatchesStillFull(stop, inputs);
                skip = foldIn(skip, skipIfNoRotationSeed(run, stop, inputs, state));
            } else if (nothingLeftHere(stop, inputs)) {
                continue;
            }
            if (skip == null && stop.isPlanting()) {
                skip = skipIfUnseenWithNoKnownSeed(run, stop, inputs, state);
            }
            kept.add(skip != null ? skip : plan);
        }
        return kept;
    }

    /**
     * Whether anything at this stop is worth the trip on its own account — a harvest, a
     * health check, a cure — whatever the seed stock turns out to say.
     *
     * <p>{@link PatchWork} answers it, as it does for the runs list, so the two cannot
     * contradict each other on screen.
     */
    private static boolean anythingToDoAt(RunStop stop, FarmingInputs inputs) {
        for (PatchRef ref : stop.getPatches()) {
            if (PatchWork.at(inputs.patchAt(ref), stop, inputs.getNowEpochSec()) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether every patch at this stop has been looked at and none of them wants anything.
     *
     * <p>Only asked of a stop that puts nothing in the ground. Such a stop is dropped, not
     * skipped: there is no decision to explain. Never on a guess: an unseen patch keeps it.
     */
    private static boolean nothingLeftHere(RunStop stop, FarmingInputs inputs) {
        return everyPatchLookedAt(stop, inputs) && !anythingToDoAt(stop, inputs);
    }

    /**
     * Whether every patch at this stop has been seen.
     */
    private static boolean everyPatchLookedAt(RunStop stop, FarmingInputs inputs) {
        for (PatchRef ref : stop.getPatches()) {
            PatchPrediction seen = inputs.patchAt(ref);
            if (seen == null || seen.getCrop() == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Stops the allocation could not reach, dropped once it is known which those are.
     *
     * <p>{@link #skipIfNoRotationSeed} only catches a player with <em>no</em> eligible seed.
     * One palm sapling and five fruit tree patches passes it, so the stops the sapling
     * never reached must be dropped here.
     *
     * <p>Runs after {@link CropAssigner#assignCrops}, the first point where it is known which
     * stops draw a seed. It never reassigns seeds. Same guards as the run-wide check: only
     * planting stops whose every patch stands empty, since a crop in a patch is a reason to
     * go.
     */
    static List<StopPlan> dropStopsTheSeedsRanOutBefore(List<StopPlan> plans, List<List<Crop>> cropFor, FarmingInputs inputs) {
        List<StopPlan> kept = new ArrayList<>(plans.size());
        for (int i = 0; i < plans.size(); i++) {
            StopPlan plan = plans.get(i);
            if (seedRanOutAt(plan, cropFor.get(i), inputs)) {
                // A tree the run declined to fell is not a seed that ran out; "no seed" would
                // send the player to buy a sapling they hold.
                kept.add(StopPlan.skip(plan.getStop(), "", anythingStandingAt(plan, inputs) ? FarmingSkipReason.STANDING_CROP
                                : FarmingSkipReason.NO_SEED, 0L));
                continue;
            }
            kept.add(leftStandingAt(plan, cropFor.get(i), inputs) ? StopPlan.skip(plan.getStop(), "", FarmingSkipReason.STANDING_CROP, 0L)
                    : plan);
        }
        return kept;
    }

    /**
     * Whether any patch at this stop still holds a living crop the run is keeping.
     *
     * <p>Only names a skip {@link #seedRanOutAt} already decided. Reads patches through
     * {@link #stillStandingIn}, as the planting does, so the row's reason matches why no
     * sapling was seated.
     */
    static boolean anythingStandingAt(StopPlan plan, FarmingInputs inputs) {
        for (PatchRef ref : plan.getStop().getPatches()) {
            if (stillStandingIn(ref, plan.getStop(), inputs) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * What is growing in this patch that picking it would leave behind, or null.
     *
     * <p>Null means there is room: bare ground, or a crop the harvest removes (a herb is
     * uprooted when picked, an allotment pulled). The exceptions are trees. A fruit tree goes
     * on fruiting after picking, and a grown lumber tree stays rooted after its check, so a
     * sapling goes in only once the tree is chopped down. Naming the tree makes that a cost
     * {@code CropAssigner.dealOutUpgrades} must justify.
     *
     * <p>Asks what is alive in the patch, not what it offers today, so it does not use
     * {@link PatchWork#standsEmpty} for trees: a picked tree is still a tree. Growth matters
     * only to tell a grown lumber tree from bare ground; whether a half-grown tree is worth
     * trading is {@link CropAssigner#assignCrops}'s call.
     */
    static Crop stillStandingIn(PatchRef ref, RunStop stop, FarmingInputs inputs) {
        PatchPrediction seen = inputs.patchAt(ref);
        // A checked lumber tree is still rooted, though no stop declares it a standing crop.
        // Asked first because the patch knows: the object offers "Chop down".
        if (seen != null && seen.isFellable()) {
            return seen.getCrop();
        }
        // So does one still owed its check, which the check leaves rooted. If it read as
        // ground, checking it would move it behind every bare patch mid-visit, and the
        // sapling it was dealt would go elsewhere.
        if (owedItsCheckBeforeTheAxe(ref, seen)) {
            return seen.getCrop();
        }
        if (!stop.isStandingCrop()) {
            return null;
        }
        if (seen == null || seen.getCrop() == null || seen.getCrop() == Crop.WEEDS) {
            return null;
        }
        // A dead crop is dug out, leaving bare ground. A diseased one is cured and stays.
        if (seen.getState() == CropState.DEAD) {
            return null;
        }
        // A regrowing crop stands between harvests, fruit on it or not. Fellable trees
        // returned above, so HARVESTABLE here is the stump of a felled tree: bare ground. A
        // tree still filling out counts as standing.
        if (seen.getCrop().getRegrowTickRateMinutes() > 0) {
            return seen.getState() == CropState.HARVESTABLE ? null : seen.getCrop();
        }
        // A standing crop that does not regrow: the Catherby white lily (declared
        // .standingCrop(), no regrow rate). A guard flower stands while it lives, so
        // standsEmpty decides.
        return PatchWork.standsEmpty(seen, stop, inputs.getNowEpochSec()) ? null : seen.getCrop();
    }

    /**
     * Whether this patch holds a grown regular or hardwood tree awaiting its health check,
     * which then has to be chopped down. A redwood is cleared with a spade after its check,
     * so it is ground on both sides of it.
     */
    private static boolean owedItsCheckBeforeTheAxe(PatchRef ref, PatchPrediction seen) {
        if (seen == null || seen.getCrop() == null || seen.isIdle() || seen.getCrop() == Crop.WEEDS || seen.getState() != CropState.GROWING
                || isStillGrowing(seen)) {
            return false;
        }
        FarmingWorldMap.PatchDef def = ref.resolve();
        return def != null && (def.getType() == PatchType.TREE || def.getType() == PatchType.HARDWOOD_TREE);
    }

    /**
     * Whether the only thing left here is fruit on a tree the player would rather keep.
     *
     * <p>Felling a tree you cannot replant turns a renewable patch into an empty one, so
     * with no sapling to plant the advice is to leave it standing.
     *
     * <p>Only where nothing is assigned: {@code CropAssigner.dealOutUpgrades} has already
     * decided whether an upgrade is worth felling for. And only where a harvest is the sole
     * act; a diseased, dead or grown-and-unchecked tree is a reason to go on its own, and
     * {@link PatchWork#at} ranks those above a harvest.
     */
    static boolean leftStandingAt(StopPlan plan, List<Crop> assigned, FarmingInputs inputs) {
        if (!plan.isVisit() || !plan.getStop().isStandingCrop() || !assigned.isEmpty() || inputs.getPreferences().picksFruitInPassing()
                || plan.getStop().isErrand()) {
            return false;
        }
        boolean anythingToPick = false;
        for (PatchRef ref : plan.getStop().getPatches()) {
            PatchWork.Act act = PatchWork.at(inputs.patchAt(ref), plan.getStop(), inputs.getNowEpochSec());
            if (act == null) {
                continue;
            }
            if (act != PatchWork.Act.HARVEST) {
                return false;
            }
            anythingToPick = true;
        }
        // A stop with no work at all is dropped elsewhere; "left standing" would name a tree
        // that is not there.
        return anythingToPick;
    }

    /**
     * Whether this stop was going to plant and the stock ran out before it.
     */
    static boolean seedRanOutAt(StopPlan plan, List<Crop> assigned, FarmingInputs inputs) {
        if (!plan.isVisit() || !plan.getStop().isPlanting() || !assigned.isEmpty()) {
            return false;
        }
        // An errand is the reason to go, so no seed shortage drops it. The contract's held
        // patch is assigned nothing, which would otherwise read as a seed running out.
        return !plan.getStop().isErrand() && everyPatchStandsEmpty(plan.getStop(), inputs);
    }

    private static boolean everyPatchStandsEmpty(RunStop stop, FarmingInputs inputs) {
        for (PatchRef ref : stop.getPatches()) {
            if (!PatchWork.standsEmpty(inputs.patchAt(ref), stop, inputs.getNowEpochSec())) {
                return false;
            }
        }
        return true;
    }

    /**
     * A stop for one particular crop, dropped when the player cannot plant it: below its
     * Farming level, or holding none of its seed.
     *
     * <p>A stop naming its own crop bypasses the rotation's stock allocation, so it needs
     * its own check. Only on a definite answer: an unread level grows anything, and an
     * unseen stack is not an empty one.
     *
     * @param crop what {@link FixedCrops#cropFor} would plant here: the best growable
     *     ticked crop, or the lowest ticked one when none is growable
     */
    private static StopPlan skipIfNoSeed(RunStop stop, Crop crop, PlayerState state) {
        CropData data = crop == null ? null : CropData.forCrop(crop);
        if (data == null) {
            return null;
        }
        // Checked first: below the level, "no seed" is false and would send the player to
        // buy seeds they hold, and holding the seed would not make it plantable.
        if (FixedCrops.belowLevel(state, data)) {
            return StopPlan.skip(stop, inSentence(crop) + " needs " + data.getFarmingLevel() + " Farming",
                    FarmingSkipReason.NEEDS_LEVEL, 0L);
        }
        if (!SeedSelector.definitelyOutOf(crop, state, data.getSeedsPerPatch())) {
            return null;
        }
        // A tree names both forms: potting a held seed is a bank trip, buying a sapling a
        // shop trip. Item names are the game's ("no oak sapling or acorn"). Any ticked
        // anima seed would do, so the line names none of the three.
        String stock = data.isSaplingPlanted() ? data.getSaplingName() + " or " + data.getSeedName()
                : data.getPatchType() == PatchType.ANIMA ? "anima seed" : data.getSeedName();
        return StopPlan.skip(stop, "no " + inSentence(stock), FarmingSkipReason.NO_SEED, 0L);
    }

    /**
     * The rotation's own seed shortfall: patches standing empty and nothing in stock to
     * fill them.
     *
     * <p>What goes in a herb, allotment or tree patch is chosen from stock each run, so the
     * question is asked of the rotation (or ladder) as a whole, matching
     * {@link RunScheduler}'s count of seedless patches.
     *
     * <p>Only when every patch {@link PatchWork#standsEmpty}: a crop in a patch is a reason to
     * go whether or not a seed follows. A tree already checked counts as empty, because
     * replanting is the only thing left to do at it. Unobserved patches never count.
     */
    private static StopPlan skipIfNoRotationSeed(FarmingRun run, RunStop stop, FarmingInputs inputs, PlayerState state) {
        if (!RunScheduler.definitelyOutOfSeeds(run, stop, state, inputs.getPreferences()) || !everyPatchStandsEmpty(stop, inputs)) {
            return null;
        }
        // No clause: the rotation has no one seed to name.
        return StopPlan.skip(stop, "", FarmingSkipReason.NO_SEED, 0L);
    }

    /**
     * A stop whose every patch is unseen, with no seed Larry knows the player holds for it.
     * The patch is assumed empty, so planting is the only thing to do there, and without a
     * known seed the row could name nothing to plant. The scheduler counts such a patch for
     * nothing, by the same {@link RunScheduler#knowsAseedFor}.
     */
    private static StopPlan skipIfUnseenWithNoKnownSeed(FarmingRun run, RunStop stop, FarmingInputs inputs, PlayerState state) {
        for (PatchRef ref : stop.getPatches()) {
            if (inputs.patchAt(ref) != null) {
                return null;
            }
        }
        return RunScheduler.knowsAseedFor(run, stop, state, inputs.getPreferences()) ? null
                : StopPlan.skip(stop, "", FarmingSkipReason.NO_SEED, 0L);
    }

    /**
     * Two reasons for skipping one stop, resolved into the one worth showing.
     *
     * <p>The reason is ranked by {@link FarmingSkipReason}, but the later growth estimate is
     * kept from either, so a patch both growing and short of a seed names the errand and
     * still counts down. A tie goes to {@code current}.
     *
     * <p>A level shortfall takes no estimate. The crop finishing does not let the player
     * plant, so "Farming level too low, ready in 2h" would promise a wait that fixes
     * nothing.
     */
    private static StopPlan foldIn(StopPlan current, StopPlan candidate) {
        if (current == null) {
            return candidate;
        }
        if (candidate == null) {
            return current;
        }
        FarmingSkipReason winner = FarmingSkipReason.moreUrgent(current.getSkipReason(), candidate.getSkipReason());
        StopPlan kept = winner == current.getSkipReason() ? current : candidate;
        if (winner == FarmingSkipReason.NEEDS_LEVEL) {
            return kept;
        }
        long readyAt = Math.max(current.getReadyAtEpochSec(), candidate.getReadyAtEpochSec());
        if (readyAt == kept.getReadyAtEpochSec()) {
            return kept;
        }
        return new StopPlan(kept.getStop(), kept.getOutcome(), kept.getRoute(),
                kept.getConfidence(), kept.getBecause(), kept.getSkipReason(), readyAt, kept.getNeeds(), kept.getTeleport());
    }

    /**
     * The errand skip: an errand whose run is not calling for it yet.
     *
     * <p>A skip, not a drop: the clicked-run path plans whatever was clicked, and clicking
     * "Contract" must show something. The row borrows the runs list's sentence and clock so
     * the two agree and the row counts down.
     */
    private static StopPlan skipUntilTheErrandIsDue(RunStop stop, FarmingInputs inputs) {
        String because = inputs.contractDetail();
        return StopPlan.skip(stop, because.isEmpty() ? "nothing to do for the contract yet" : because,
                FarmingSkipReason.STILL_GROWING, inputs.contractReadyAtEpochSec());
    }

    /**
     * The standing-crop skip: nothing to do at a patch already holding a live one of the
     * crops the stop may grow.
     */
    private static StopPlan skipIfStanding(RunStop stop, PatchPrediction seen) {
        if (seen == null || !FixedCrops.grows(stop, seen.getCrop()) || !isAlive(seen.getState())) {
            return null;
        }
        return StopPlan.skip(stop, "your " + inSentence(seen.getCrop()) + " is still growing here",
                FarmingSkipReason.STANDING_CROP, seen.getDoneEstimateEpochSec());
    }

    /** The paced skip: nothing to harvest yet at a patch still filling out. */
    private static StopPlan skipIfStillGrowing(RunStop stop, PatchPrediction seen) {
        if (seen == null || !FixedCrops.grows(stop, seen.getCrop()) || !isStillGrowing(seen)) {
            return null;
        }
        return StopPlan.skip(stop, "your " + inSentence(seen.getCrop()) + " is still growing",
                FarmingSkipReason.STILL_GROWING, seen.getDoneEstimateEpochSec());
    }

    /**
     * The rotation's own skip: a patch we can see already has a crop growing in it.
     *
     * <p>Used by rotation stops, which have no fixed crop for the other checks to key on, and
     * by fixed-crop stops whose own rules found no skip. Any growing crop counts, chosen by
     * this run or not.
     *
     * <p>Only a crop still filling out. Ready, diseased, dead and empty patches all have work
     * waiting. Weeds read as idle, not as a crop.
     */
    private static StopPlan skipIfPatchesStillFull(RunStop stop, FarmingInputs inputs) {
        // Every patch must be growing, not just the primary: Fossil Island is three hardwood
        // patches on one journey. The stop is ready when its last patch finishes.
        long readyAt = 0L;
        for (PatchRef ref : stop.getPatches()) {
            PatchPrediction seen = inputs.patchAt(ref);
            if (seen == null || seen.getCrop() == null || seen.isIdle() || !isStillGrowing(seen)) {
                return null;
            }
            readyAt = Math.max(readyAt, seen.getDoneEstimateEpochSec());
        }
        // Named from the primary patch, which the row's heading also names.
        PatchPrediction primary = inputs.patchAt(stop.primaryPatch());
        if (primary == null || primary.getCrop() == null) {
            return null;
        }
        return StopPlan.skip(stop, "your " + inSentence(primary.getCrop()) + " is still growing here",
                FarmingSkipReason.STILL_GROWING, readyAt);
    }

    /**
     * A skip for a stop whose every patch holds a plant restocking its produce, dated by
     * the last of them to restock. Called only once no patch is open and nothing waits.
     */
    private static StopPlan skipWhileRestocking(RunStop stop, FarmingInputs inputs) {
        PatchPrediction primary = inputs.patchAt(stop.primaryPatch());
        if (primary == null || primary.getCrop() == null) {
            return null;
        }
        long readyAt = 0L;
        for (PatchRef ref : stop.getPatches()) {
            PatchPrediction seen = inputs.patchAt(ref);
            if (seen != null) {
                readyAt = Math.max(readyAt, seen.getDoneEstimateEpochSec());
            }
        }
        return StopPlan.skip(stop, "your " + inSentence(primary.getCrop()) + " is regrowing here",
                FarmingSkipReason.STILL_GROWING, readyAt);
    }

    /** Whether what is in the patch is still doing its job. */
    private static boolean isAlive(CropState state) {
        return state == CropState.GROWING || state == CropState.HARVESTABLE;
    }

    /**
     * Whether the crop here is still growing rather than ready to be harvested.
     *
     * <p>Narrower than {@link #isAlive}: for an every-second-run stop the harvest is the
     * reason to go, so HARVESTABLE does not count.
     *
     * <p>The tracker sets HARVESTABLE only from a direct observation (see
     * {@link PatchPrediction}), so the state is read as-is. {@link
     * com.larryfarming.engine.farming.FarmingTracker#predict} advances {@code stage} to now, so a
     * stage short of the last is still growing. {@link PatchPrediction#of} carries 0 of 0
     * stages, so the stage comparison is skipped when there is no count.
     */
    static boolean isStillGrowing(PatchPrediction prediction) {
        if (prediction.getState() != CropState.GROWING) {
            return false;
        }
        return prediction.getStages() <= 0 || prediction.getStage() < prediction.getStages() - 1;
    }
}
