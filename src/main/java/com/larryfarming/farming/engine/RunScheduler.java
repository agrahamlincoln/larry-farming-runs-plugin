package com.larryfarming.farming.engine;

import com.larryfarming.engine.farming.*;
import com.larryfarming.farming.catalogue.*;
import com.larryfarming.farming.detection.*;
import com.larryfarming.farming.model.*;
import com.larryfarming.model.PlayerState;
import com.larryfarming.model.Skill;
import com.larryfarming.snapshot.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import lombok.extern.slf4j.Slf4j;

/**
 * Which farming runs are worth doing, and in what order.
 *
 * <p>Ordered by the value of each run rather than by whichever timer
 * expired first: an idle hardwood patch costs more than an idle herb patch, because it is
 * the best farming experience in the game on the longest cadence.
 * {@link FarmingPreferences#priorityOf} holds that ranking, which the player can lead with
 * the herb run.
 *
 * <p>A run is due when any one of its patches needs a visit, or, for a player who waits
 * for every patch, once none is still growing or any is diseased or dead.
 *
 * <p>Pure: everything it needs arrives as arguments, including the current second.
 */
@Slf4j
public final class RunScheduler {

    private RunScheduler() {
    }

    /** A patch observed within this many seconds counts as seen rather than predicted. */
    private static final long OBSERVED_RECENTLY_SECONDS = 120L;

    /**
     * How long a gap between one patch and the next still counts as the same trip.
     *
     * <p>Generous, because real runs have long gaps: a quetzal out to Ortus Farm, a bank
     * detour for a seed. It only needs to notice that the player has <em>stopped</em>, so
     * a run with an extra left undone releases the tab once they have.
     */
    private static final long ONE_TRIP_SECONDS = 20 * 60L;

    /**
     * The longest a trip is taken to last. A trip runs from its first patch while the player
     * keeps working patches, but standing beside one keeps re-reading it, so without a ceiling
     * an afternoon at the Farming Guild would stretch one trip back to the run before.
     */
    private static final long LONGEST_TRIP_SECONDS = 2 * 60 * 60L;

    /**
     * The contract's state: which crop is assigned, and whether the errand is done. Taken
     * from the detection tables so this reader and the login scan cannot drift apart.
     */
    private static final int CONTRACT_PRODUCE_VARBIT = CapabilitySources.CONTRACT_PRODUCE_VARBIT;
    private static final int CONTRACT_COMPLETE_VARBIT = CapabilitySources.CONTRACT_COMPLETE_VARBIT;
    // Whether checking the patch's health completes a contract for it, rather than picking
    // the produce: PatchType.isHealthCheckRequired. A contract for a crop with a health
    // check completes on the check, so a jangerberry bush finishes its contract before its
    // berries are picked. A crop without one completes once the patch is fully harvested. A
    // crop whose health is already checked cannot satisfy a contract handed out afterwards;
    // that contract needs one grown from seed.

    /** Everywhere a contracted crop could be growing. */
    private static final List<PatchRef> CONTRACT_PATCHES = FarmingCatalogue.contractPatches();

    /**
     * The guild patches a given crop can be planted in — usually one, but two for an
     * allotment, and a contract fills whichever of the pair is free.
     */
    private static List<PatchRef> patchesFor(CropData data) {
        List<PatchRef> matching = new ArrayList<>(2);
        for (PatchRef patch : CONTRACT_PATCHES) {
            FarmingWorldMap.PatchDef def = patch.resolve();
            if (def != null && def.getType() == data.getPatchType()) {
                matching.add(patch);
            }
        }
        return matching;
    }


    /**
     * Every run, best first.
     *
     * @param openBook which spellbook the player is on, deciding which variant of a run
     *                 that has two gets scheduled. Null follows neither.
     */
    public static List<FarmingRunStatus> schedule(PlayerState state, FarmingInputs inputs, Spellbook openBook, long nowEpochSec) {
        FarmingInputs given = (inputs != null ? inputs : FarmingInputs.none()).seeing(state);
        // One resolver per book, cached across the schedule: a run written for another book
        // is measured on both, and building a resolver is the expensive half.
        Map<Spellbook, CapabilityResolver> resolvers = RunVariants.newResolverCache();
        // Once for every run: a trip spans whichever runs the player is doing.
        long tripStart = tripStartedAt(given, nowEpochSec);
        List<FarmingRunStatus> statuses = new ArrayList<>();
        for (RunVariants.Choice choice : oneVariantPerRun(state, openBook, given, resolvers)) {
            // A run with every crop switched off is one the player has said they do not do,
            // so it leaves the list.
            if (PlantingGate.silencesWholeRun(choice.getRun(), given.getPreferences())) {
                continue;
            }
            // Scoring the variant already planned it, so reuse those plans, and the resolver
            // they were planned with.
            CapabilityResolver resolver = resolvers.computeIfAbsent(choice.getPlanFor(), given::resolverOn);
            statuses.add(statusOf(choice.getRun(), state, given, choice.getPlans(), resolver, nowEpochSec, tripStart));
        }
        FarmingPreferences prefs = given.getPreferences();
        statuses.sort(Comparator.comparingInt(status -> rankOf(status, prefs)));
        return statuses;
    }

    /**
     * One variant of each run. The herb run is in the catalogue once per spellbook, and
     * {@link RunVariants} picks between them on reachable patches.
     */
    private static List<RunVariants.Choice> oneVariantPerRun(PlayerState state, Spellbook openBook, FarmingInputs inputs,
            Map<Spellbook, CapabilityResolver> resolvers) {
        Map<RunId, List<FarmingRun>> byId = FarmingCatalogue.runsById();
        List<RunVariants.Choice> chosen = new ArrayList<>(byId.size());
        for (List<FarmingRun> variants : byId.values()) {
            chosen.add(RunVariants.best(variants, state, openBook, inputs, resolvers));
        }
        return chosen;
    }

    /**
     * Due runs first in priority order, then everything waiting in the order it will come
     * ready, then the unseen, then the locked, last because the player can do nothing about
     * them on the run today. Within a group, the player's run priority decides.
     */
    private static int rankOf(FarmingRunStatus status, FarmingPreferences prefs) {
        RunStatus state = RunStatus.valueOf(status.getStatus());
        int priority = prefs.priorityOf(RunId.valueOf(status.getRunId()));
        switch (state) {
            case DUE:
                return priority;
            case PENDING:
                return 100 + priority;
            case UNSEEN:
                return 200 + priority;
            default:
                return 300 + priority;
        }
    }

    private static FarmingRunStatus statusOf(FarmingRun run, PlayerState state,
            FarmingInputs inputs, List<StopPlan> plans, CapabilityResolver resolver, long nowEpochSec, long tripStart) {
        UnlockGate gate = RunAvailability.gateOf(run, inputs.getPreferences());
        if (RunAvailability.of(gate, state) == CapabilityState.LACK) {
            return build(run, RunStatus.LOCKED, RunAvailability.shortfall(gate, state), 0L, 0, false);
        }
        if (waitsForTheHerbLevel(run, inputs.getPreferences(), state)) {
            return build(run, RunStatus.PENDING, "from " + Preset.HERB_RUN_LEVEL + " Farming", 0L, 0, false);
        }
        if (run.getId() == RunId.CONTRACT) {
            // The contract answers from the guild, not from a walk of stops. It needs no
            // reachability guard: the guild chain ends in a minecart rung open to everyone.
            return contractStatus(run, state, inputs, nowEpochSec);
        }

        String noRoute = noRouteDetail(run, plans, inputs.getPreferences(), state, resolver);
        if (noRoute != null) {
            return build(run, RunStatus.LOCKED, noRoute, 0L, 0, false);
        }

        Tally tally = walkPatches(run, plans, inputs, state, nowEpochSec, false, tripStart);

        if (tally.seen == 0 && tally.unseen == 0) {
            // Nothing observed, and no unseen patch the player could plant. Nothing seen
            // is also nothing to be halfway round, so the trip is never unfinished, whatever
            // the extras say.
            return build(run, RunStatus.UNSEEN, UNSEEN_DETAIL, 0L, 0, false);
        }

        Tally extras = walkPatches(run, plans, inputs, state, nowEpochSec, true, tripStart);
        // A route proper never seen is nothing to be halfway round, however fresh an extra.
        boolean unfinished = tally.seen > 0 && tripUnfinished(tally, extras, nowEpochSec);

        // Waiting for every patch, one still growing holds the run back. Only a growing
        // crop can: an unseen patch and a seedless empty one have nothing to wait for, so
        // each is left out here as it is from the verdict below. A diseased or dead patch
        // never waits: a diseased crop dies on its next growth tick, and a dead one is a
        // patch growing nothing until it is cleared. A crop planted on this trip holds
        // nothing back either, or the run would leave the walkthrough at its first
        // replanted patch.
        boolean waits = inputs.getPreferences().waitsForEveryPatch();
        boolean urgent = tally.needsHelp + tally.needsClearing > 0;
        boolean holding = tally.growing > tally.plantedThisTrip;
        // Short of a sapling for every patch, the fruit trees wait for more, unless a tree
        // is diseased or dead.
        String saplings = saplingsShort(run, tally, inputs.getPreferences(), state);
        if (saplings != null && !urgent) {
            return build(run, RunStatus.PENDING, saplings, 0L, 0, tally.predicted, unfinished, false);
        }
        if (tally.actionable > 0 && (!waits || !holding || urgent)) {
            return build(run, RunStatus.DUE, dueDetail(tally), 0L, tally.actionable, tally.predicted, unfinished, false);
        }
        // The run comes due when its last patch does. A crop with no estimate leaves that
        // undated, rather than naming a time the run would still be waiting at.
        long readyAt = !waits ? tally.earliestReady : tally.undated > 0 ? 0L : tally.latestReady;
        return build(run, RunStatus.PENDING, pendingDetail(tally, readyAt, waits, nowEpochSec),
                readyAt, 0, tally.predicted, unfinished, false);
    }

    /** Whether the herb run waits for {@link Preset#HERB_RUN_LEVEL} Farming. */
    static boolean waitsForTheHerbLevel(FarmingRun run, FarmingPreferences prefs, PlayerState state) {
        return run.getId() == RunId.HERB && herbRunsWaitForTheLevel(prefs, state);
    }

    /** Whether the player's herb runs wait for {@link Preset#HERB_RUN_LEVEL} Farming. */
    static boolean herbRunsWaitForTheLevel(FarmingPreferences prefs, PlayerState state) {
        return prefs.herbRunsWaitForLevel() && state != null && state.getSkillLevel(Skill.FARMING) < Preset.HERB_RUN_LEVEL;
    }

    /**
     * The fruit tree run's line while the player owns too few saplings for every patch due
     * one on this visit, such as "2 of 5 saplings", or null when they have enough, the
     * switch is off, or nothing proves the count.
     *
     * <p>A patch is due a sapling when it stands bare, holds a dead tree, which is cleared and
     * replanted, or holds a checked tree, when grown trees are replanted. A tree kept for its
     * fruit is checked and picked, and felled only for a better one, so it needs none. Nor
     * does a diseased tree, which is cured, or a growing one. A run with a tree planted on
     * this trip is under way and never held, or planting the first sapling would end the
     * walkthrough.
     *
     * <p>A seed counts, since the player can grow a sapling from it before setting off. So
     * does any ticked sapling, though a standing tree takes only one as good as itself: five
     * checked dragonfruit trees and five saplings, one of them a palm, release the run, which
     * then plants four.
     */
    private static String saplingsShort(FarmingRun run, Tally tally, FarmingPreferences prefs, PlayerState state) {
        if (run.getSeedPatchType() != PatchType.FRUIT_TREE || !prefs.fruitTreesWaitForSaplings() || tally.plantedThisTrip > 0) {
            return null;
        }
        int patches = tally.bare + tally.needsClearing + tally.grownTrees;
        if (patches == 0) {
            return null;
        }
        int owned = SeedSelector.plantableAcross(prefs, PatchType.FRUIT_TREE, state);
        if (owned < 0 || owned >= patches) {
            return null;
        }
        return owned + " of " + patches + (patches == 1 ? " sapling" : " saplings");
    }

    /**
     * Why no stop on the run proper can be reached, or null when one can or might.
     *
     * <p>Reads the plans the variant was scored on, so it costs no second planning pass.
     * Only the route proper counts, as in {@link #walkPatches}: an extra is no reason to
     * go, so reaching one does not make the run possible. A stop the player switched off
     * is left out too, or a run would read as having no route because of the one stop the
     * player chose not to plant.
     *
     * <p>Every counted stop must be a definite refusal. A stop waiting on an unread fact
     * carries {@code UNKNOWN} confidence (see {@link RunPlanner#planStop}), and a guessed
     * route is a visit, so unread state never greys a run.
     *
     * <p>Names the fairy ring when a way to a ring is the one thing a route could fix: at
     * least one stop wants only that, and every other stop is shut by its access gate
     * (Fossil Island before Bone Voyage). Hardwood shows why: the Locus Oasis is reached
     * only by a fairy ring, and Fossil Island is shut to anyone without the barge.
     *
     * <p>Only when the ring rung certainly holds once a way to a ring is granted. Where the
     * network itself is unread (no staff seen, a quest not yet reported), switching an
     * approach on might still not open the run, so the detail says only "no route".
     *
     * @param resolver the one the plans were made with, to tell a stop shut by its access
     *     gate from one refused for want of a route, as {@link RunPlanner} does
     * @return {@link FarmingSkipReason#NO_FAIRY_RING_ROUTE}'s label when the fairy ring is
     *     the shared blocker; otherwise {@link #NO_ROUTE_DETAIL}
     */
    // No catalogue run has a required stop the player can switch off beside others, so no
    // run reaches the switched-off rule.
    static String noRouteDetail(FarmingRun run, List<StopPlan> plans,
            FarmingPreferences prefs, PlayerState state, CapabilityResolver resolver) {
        boolean anyStop = false;
        boolean anyForWantOfAring = false;
        boolean onlyTheRing = true;
        for (StopPlan plan : plans) {
            RunStop stop = plan.getStop();
            if (stop.isOptional() || PlantingGate.silences(run, stop, prefs)) {
                continue;
            }
            if (!definitelyUnrouted(plan)) {
                return null;
            }
            anyStop = true;
            if (plan.getSkipReason() == FarmingSkipReason.NO_FAIRY_RING_ROUTE && aRingWouldOpenIt(stop, state, resolver)) {
                anyForWantOfAring = true;
            } else if (!shutByItsGate(stop, state, resolver)) {
                onlyTheRing = false;
            }
        }
        if (!anyStop) {
            return null;
        }
        return anyForWantOfAring && onlyTheRing ? FarmingSkipReason.NO_FAIRY_RING_ROUTE.getLabel() : NO_ROUTE_DETAIL;
    }

    /**
     * Whether a fairy ring rung would certainly reach this stop if the player had a way to
     * stand at a ring. Stricter than the planner's own test, which also accepts a network
     * it cannot read yet.
     */
    private static boolean aRingWouldOpenIt(RunStop stop, PlayerState state, CapabilityResolver resolver) {
        Requirement approach = FarmingCatalogue.toAfairyRing();
        for (TravelOption option : stop.getTravel()) {
            if (option.isCertainOnly() || !option.getRequires().contains(approach)) {
                continue;
            }
            if (resolver.evaluateGranting(option.getRequires(), approach, state, Collections.emptyMap()) == CapabilityState.HAVE) {
                return true;
            }
        }
        return false;
    }

    /** Whether the stop's access gate definitely keeps the player out, whatever the route. */
    private static boolean shutByItsGate(RunStop stop, PlayerState state, CapabilityResolver resolver) {
        return stop.getAccessGate() != null && resolver.resolve(stop.getAccessGate(), state).getState() == CapabilityState.LACK;
    }

    /**
     * Whether the planner refused this stop for want of a route, with every rung a definite
     * no. "Not worth the trip" counts: the planner reaches it only when every listed route
     * has failed, so the walkthrough never sends the player there either.
     */
    private static boolean definitelyUnrouted(StopPlan plan) {
        if (plan.isVisit() || plan.getConfidence() != CapabilityState.LACK) {
            return false;
        }
        FarmingSkipReason reason = plan.getSkipReason();
        return reason == FarmingSkipReason.NO_ROUTE || reason == FarmingSkipReason.NO_ROUTE_LISTED
                || reason == FarmingSkipReason.NO_FAIRY_RING_ROUTE || reason == FarmingSkipReason.NOT_WORTH_TRIP;
    }

    /**
     * Whether the player is out on this run right now with something still to do at it.
     *
     * <p>Both halves are needed. "Something left" reads the woven-in extras, which stay out
     * of the due verdict but separate a finished trip from an abandoned one. "Out there"
     * needs recency, because a run can have an extra left undone for days (limpwurt seeds
     * never planted). Patches only report while the player stands beside them, so the last
     * observation records where they have actually been.
     */
    private static boolean tripUnfinished(Tally proper, Tally extras, long nowEpochSec) {
        if (extras.actionable == 0) {
            return false;
        }
        return seenThisTrip(Math.max(proper.lastSeen, extras.lastSeen), nowEpochSec);
    }

    /**
     * Whether a patch last seen at {@code observedAtEpochSec} was seen within one trip's gap
     * of now. Zero means never seen.
     *
     * <p>This asks whether the player is out on a trip at all, and is shared with the
     * walkthrough, which reads a fresh sighting of a growing patch as the player being partway
     * round, so the runs list and the walkthrough agree on when a trip ends. Whether a crop
     * went in on this trip is a different question, answered from the whole trip's span by
     * {@link #tripStartedAt}.
     */
    static boolean seenThisTrip(long observedAtEpochSec, long nowEpochSec) {
        return observedAtEpochSec > 0 && nowEpochSec - observedAtEpochSec <= ONE_TRIP_SECONDS;
    }

    /**
     * Whether nobody can yet say what the next contract will ask for.
     *
     * <p>True in the two windows where Jane has not named a crop: no contract taken
     * ({@link #CONTRACT_PRODUCE_VARBIT} zero), and one finished but not handed in
     * ({@link #CONTRACT_COMPLETE_VARBIT} set), after which the next contract is a fresh
     * roll.
     *
     * <p><b>False when the varbits have not arrived.</b> Reading silence as "no contract"
     * would hold the reserved patch empty for a contract that may not exist.
     *
     * <p>Kept beside {@link #contractStatus}, which reads the same two varbits.
     */
    public static boolean nextContractCropUnknown(PlayerState state) {
        if (state == null) {
            return false;
        }
        Integer produce = state.getVarbits().get(CONTRACT_PRODUCE_VARBIT);
        if (produce == null) {
            return false;
        }
        if (produce == 0) {
            return true;
        }
        Integer complete = state.getVarbits().get(CONTRACT_COMPLETE_VARBIT);
        return complete != null && complete != 0;
    }

    /**
     * The contract, answered from three sources because no one of them is enough: the
     * varbits say whether a contract exists and whether it is finished, and the plugin
     * decodes which crop it wants. Without the crop, reading the whole guild
     * would report a contract ready whenever any guild crop is.
     */
    private static FarmingRunStatus contractStatus(FarmingRun run, PlayerState state, FarmingInputs inputs, long nowEpochSec) {
        Integer produce = state == null ? null : state.getVarbits().get(CONTRACT_PRODUCE_VARBIT);
        if (produce == null) {
            return build(run, RunStatus.UNSEEN, "contract not checked yet", 0L, 0, false);
        }
        if (produce == 0) {
            return build(run, RunStatus.DUE, "get a contract from Guildmaster Jane", 0L, 1, false);
        }
        // Set when the contract's finishing act is done, not when the crop matures: it goes
        // 0 to 1 as the last snape grass leaves the ground. Only the walk to Jane is left.
        // The act depends on the crop: see completedByCheck.
        Integer complete = state.getVarbits().get(CONTRACT_COMPLETE_VARBIT);
        if (complete != null && complete != 0) {
            return build(run, RunStatus.DUE, "hand the contract to Guildmaster Jane", 0L, 1, false);
        }

        Crop crop = inputs.getContractCrop();
        if (crop == null) {
            // A contract value the decoding table does not know, so the contract's patch is
            // unknown and nothing the player does here changes that.
            return build(run, RunStatus.UNSEEN, "contract crop not recognised", 0L, 0, false);
        }

        CropData data = CropData.forCrop(crop);
        if (data == null) {
            // A crop CropData does not carry (easy-tier produce no run plants).
            // Nothing here can find its patch, so say only what is certain.
            log.debug("No crop data for farming contract produce {}", crop);
            return build(run, RunStatus.PENDING, CropNames.inSentence(crop) + " growing", 0L, 0, false);
        }
        return contractPatchStatus(run, crop, data, state, inputs, nowEpochSec);
    }

    /**
     * The contract read off the patch its crop actually goes in.
     *
     * <p>A crop with a health check completes the contract on the check, which is the
     * {@link CropState#GROWING} state at the final stage (the "Check-health" reading). Once
     * checked, the patch turns {@link CropState#HARVESTABLE} and its crop can no longer
     * satisfy a contract, so it falls through to "plant one" even though the patch is not
     * empty. For a crop without a health check, HARVESTABLE is the crop waiting to be
     * picked, and picking it completes the contract.
     */
    private static FarmingRunStatus contractPatchStatus(FarmingRun run, Crop crop,
            CropData data, PlayerState state, FarmingInputs inputs, long nowEpochSec) {
        boolean completedByCheck = data.getPatchType().isHealthCheckRequired();
        String name = CropNames.inSentence(crop);
        boolean seen = false;
        boolean predicted = false;
        boolean needsCuring = false;
        boolean needsClearing = false;
        long soonest = 0;

        for (PatchRef ref : patchesFor(data)) {
            PatchPrediction patch = inputs.patchAt(ref);
            if (patch == null) {
                continue;
            }
            seen = true;
            // Weeds, or somebody else's crop in the patch the contract wants. Either way
            // there is no contract crop here, so it reads as one still to plant.
            if (patch.getCrop() != crop) {
                continue;
            }
            // Set only for patches that hold the contract crop, so the marker does not depend
            // on allotment order.
            if (nowEpochSec - patch.getObservedAtEpochSec() > OBSERVED_RECENTLY_SECONDS) {
                predicted = true;
            }
            // Remembered rather than returned on, because the other patch of the same crop
            // may be ready, and harvesting it finishes the contract outright. Diseased and
            // dead are kept apart: a plant cure saves a diseased crop, and a dead one needs
            // a spade.
            if (patch.getState() == CropState.DISEASED) {
                needsCuring = true;
                continue;
            }
            if (patch.getState() == CropState.DEAD) {
                needsClearing = true;
                continue;
            }
            // Already checked, so spent for the contract whatever produce it still carries.
            if (completedByCheck && patch.getState() == CropState.HARVESTABLE) {
                continue;
            }
            if (patch.isReadyAt(nowEpochSec)) {
                // Not possessive: crop names mix singular and plural ("jangerberries's").
                return dueAtThePatch(run, completedByCheck ? "check the health of your " + name : "harvest your " + name, predicted);
            }
            long done = patch.getDoneEstimateEpochSec();
            soonest = earlier(done, soonest);
        }

        // Curing first: a diseased patch dies on the next growth tick, and a dead one
        // will keep.
        if (needsCuring) {
            return dueAtThePatch(run, "your " + name + " is diseased", predicted);
        }
        if (needsClearing) {
            // "And replant" only when they have the seed, as in the empty-patch branch
            // below. Still DUE either way: the patch is not free until it is dug.
            return dueAtThePatch(run, outOfSeedFor(crop, state) ? "dig up your dead " + name
                    : "dig up your dead " + name + " and replant", predicted);
        }
        if (soonest > 0) {
            return build(run, RunStatus.PENDING, "ready in " + FarmingDurations.describe(soonest - nowEpochSec), soonest, 0, predicted);
        }
        // The contract's patch is visible and its crop is not growing in it.
        if (seen) {
            // An empty patch is a reason to go only while the player has the seed, as for
            // every other run. Only a definite nothing counts, so an unopened bank still
            // sends them.
            return outOfSeedFor(crop, state) ? build(run, RunStatus.PENDING, "no " + CropNames.inSentence(data.getSeedName()), 0L, 0,
                            predicted) : dueAtThePatch(run, "plant " + name + " at the guild", predicted);
        }
        // Not been to the guild since the plugin started, so there is nothing to read.
        return build(run, RunStatus.UNSEEN, "guild patches not checked yet", 0L, 0, false);
    }

    /**
     * Whether this one crop is one the player is known to have no seed for: a contract's
     * crop, or a fixed-crop stop's.
     *
     * <p>Asked of the crop itself rather than through {@link #definitelyOutOfSeeds}: a
     * contract accepts only the crop it names, so preferences and the seed ladder have no
     * say.
     */
    static boolean outOfSeedFor(Crop crop, PlayerState state) {
        CropData data = CropData.forCrop(crop);
        return data != null && SeedSelector.definitelyOutOf(crop, state, data.getSeedsPerPatch());
    }

    /** What each of a run's patches is doing, rolled up. */
    private static final class Tally {
        int seen;
        int actionable;
        int ready;
        /** Diseased, and still savable with a plant cure. */
        int needsHelp;
        /** Dead, and waiting on a spade. */
        int needsClearing;
        int empty;
        /** Patches never seen, assumed empty, that the player has a seed for. */
        int unseen;
        /** Empty patches with no tree rooted in them, the ground a sapling goes into. */
        int bare;
        /** Patches standing empty that the player has no seed for. */
        int seedless;
        /** Of the empty patches with a seed: a fruit tree, calquat, bush or cactus picked bare. */
        int picked;
        /** Of the empty patches with a seed: a lumber tree checked and still standing. */
        int checked;
        /** Checked fruit trees the run chops down and replants. */
        int grownTrees;
        /** Crops still growing, with or without an estimate. */
        int growing;
        /** Growing crops with no estimate of when they finish. */
        int undated;
        /** Growing crops the player put in on this trip. */
        int plantedThisTrip;
        long earliestReady;
        /** When the last estimated crop finishes. */
        long latestReady;
        boolean predicted;
        /** The most recent time any of these patches was actually observed. */
        long lastSeen;
        /** When the player's trip began, from {@link #tripStartedAt}; zero for none. */
        long tripStart;
    }

    /**
     * What the run's own patches are doing: the route proper, without the woven-in extras
     * and without stops this player cannot reach.
     *
     * <p>The extras are things to do while already out there, not reasons to go. An
     * unreachable patch is not somewhere the player can act. Both would otherwise make a
     * run report itself due off a patch nothing can clear.
     *
     * <p>Only reachability filters stops here, unlike the walkthrough. The runs list counts
     * down to a crop still growing, so dropping it would replace a due date with silence.
     *
     * @param extras false for the route proper, which makes the verdict; true for the
     *               woven-in stops alone. Two passes keep every {@link Tally} meaning one
     *               side only.
     */
    private static Tally walkPatches(FarmingRun run, List<StopPlan> plans,
            FarmingInputs inputs, PlayerState state, long nowEpochSec, boolean extras, long tripStart) {
        Tally tally = new Tally();
        tally.tripStart = tripStart;
        for (StopPlan plan : plans) {
            if (!plan.isVisit()) {
                continue;
            }
            RunStop stop = plan.getStop();
            if (stop.isOptional() != extras) {
                continue;
            }
            // A silenced stop is a patch the player switched off, not an empty patch.
            if (PlantingGate.silences(run, stop, inputs.getPreferences())) {
                continue;
            }
            boolean seedless = definitelyOutOfSeeds(run, stop, state, inputs.getPreferences());
            walkPatches(stop, inputs, state, nowEpochSec, seedless, () -> knowsAseedFor(run, stop, state, inputs.getPreferences()), tally);
        }
        return tally;
    }

    /**
     * Whether this stop has nothing the player could put in the ground.
     *
     * <p>The choices are exactly the ones the walkthrough allocates from, via
     * {@link SeedSelector#plantableFrom}. A wider list would let a run report itself due
     * and then name no crop at any stop. A stop with a fixed crop accepts only the crop
     * {@link FixedCrops#cropFor} chooses.
     *
     * <p>A player holding only magic saplings with only yew ticked is out of seeds here;
     * ticking magic fixes it.
     *
     * <p>A crop the player has switched off counts as one they cannot plant. Only a
     * definite nothing counts: an unopened bank is silence, and dropping a patch on
     * silence is the expensive mistake.
     */
    // Package-private: the walkthrough asks the same question of a single stop, and one
    // implementation keeps the two from disagreeing on the same screen.
    static boolean definitelyOutOfSeeds(FarmingRun run, RunStop stop, PlayerState state, FarmingPreferences prefs) {
        // A stop that plants nothing, such as the contract errand, cannot be short of seeds.
        if (!stop.isPlanting()) {
            return false;
        }
        if (stop.isFixedCrop()) {
            // The crop the stop would plant, which has already fallen past every ticked
            // crop the player is out of.
            Crop crop = FixedCrops.cropFor(stop, prefs, state);
            return crop == null || outOfSeedFor(crop, state);
        }
        PatchType type = stop.patchType();
        if (type == null) {
            return false;
        }
        return SeedSelector.definitelyOutOfAll(ladderAt(run, stop, type, state, prefs), state, CropData.seedsPerPatch(type));
    }

    /** The crops this stop may take, best first. */
    private static List<Crop> ladderAt(FarmingRun run, RunStop stop, PatchType type, PlayerState state, FarmingPreferences prefs) {
        return DiaryPlantings.ladderAt(SeedSelector.plantableFrom(FarmingCatalogue.seedPolicy(),
                prefs, run.getSeedPatchType(), type), stop, state, prefs);
    }

    /**
     * Whether a store Larry has seen holds enough seed to fill one patch at this stop, over
     * the same choices as {@link #definitelyOutOfSeeds}. With the bank unopened, neither
     * holds.
     */
    static boolean knowsAseedFor(FarmingRun run, RunStop stop, PlayerState state, FarmingPreferences prefs) {
        if (!stop.isPlanting() || state == null) {
            return false;
        }
        if (stop.isFixedCrop()) {
            Crop crop = FixedCrops.cropFor(stop, prefs, state);
            CropData data = crop == null ? null : CropData.forCrop(crop);
            return data != null && SeedSelector.knownToHold(crop, state, data.getSeedsPerPatch());
        }
        PatchType type = stop.patchType();
        if (type == null) {
            return false;
        }
        int needed = CropData.seedsPerPatch(type);
        for (Crop crop : ladderAt(run, stop, type, state, prefs)) {
            if (SeedSelector.knownToHold(crop, state, needed)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param known whether Larry knows of a seed for the stop, which an unseen patch needs;
     *     asked only once a patch proves unseen, since the ladder costs a walk per stop
     */
    private static void walkPatches(RunStop stop, FarmingInputs inputs, PlayerState state,
            long nowEpochSec, boolean seedless, BooleanSupplier known, Tally tally) {
        Boolean knownHere = null;
        for (PatchRef patch : stop.getPatches()) {
            PatchPrediction prediction = inputs.patchAt(patch);
            if (prediction == null) {
                if (knownHere == null) {
                    knownHere = known.getAsBoolean();
                }
                assumeEmpty(stop, knownHere, tally);
                continue;
            }
            tally.seen++;
            tally.lastSeen = Math.max(tally.lastSeen, prediction.getObservedAtEpochSec());
            if (nowEpochSec - prediction.getObservedAtEpochSec() > OBSERVED_RECENTLY_SECONDS) {
                tally.predicted = true;
            }
            classify(prediction, stop, inputs.getPreferences(), state, tally, nowEpochSec, seedless);
        }
    }

    /**
     * A patch never seen, counted as empty ground. Anything planted before Larry was
     * watching finished long ago, so the visit pays either way, and a run that waited to
     * see the patch would never send the player to see it. Counted apart from the patches
     * seen empty, so the detail can say the emptiness is assumed.
     *
     * <p>Only a stop that plants, and only with a seed Larry knows of: planting is all there is to do at ground assumed
     * empty, so without one the patch counts for nothing, neither "nothing to plant" nor
     * ground waiting, and the player is asked to open the seed stores instead.
     *
     * <p>Never bare ground for the sapling count: a tree planted before Larry was watching
     * still stands, so an unseen patch asks for no sapling, and a fruit tree run never waits
     * for saplings to cover patches nobody has seen.
     */
    private static void assumeEmpty(RunStop stop, boolean known, Tally tally) {
        if (stop.isPlanting() && known) {
            tally.unseen++;
            tally.actionable++;
        }
    }

    /**
     * One patch, sorted into the roll-up. Every question about what is waiting at it goes
     * through {@link PatchWork}, which the walkthrough also reads, so the runs list and the
     * walkthrough on screen beside it agree.
     */
    private static void classify(PatchPrediction prediction, RunStop stop,
            FarmingPreferences prefs, PlayerState player, Tally tally, long nowEpochSec, boolean seedless) {
        CropState state = prediction.getState();
        // An anima plant dies at the end of its time, seen or not, and is cleared only to
        // plant the next one: with no seed for that, it is ground with nothing to plant.
        if (stop.patchType() == PatchType.ANIMA && PatchWork.at(prediction, stop, nowEpochSec) == PatchWork.Act.CLEAR_DEAD) {
            if (seedless) {
                tally.empty++;
                tally.seedless++;
            } else {
                tally.needsClearing++;
                tally.actionable++;
            }
            return;
        }
        if (state == CropState.DISEASED || state == CropState.DEAD) {
            // Disease and death come only from a direct observation, never a prediction.
            // Counted apart: a plant cure saves a diseased patch, and a dead one wants a
            // spade.
            if (state == CropState.DEAD) {
                tally.needsClearing++;
            } else {
                tally.needsHelp++;
            }
            tally.actionable++;
            return;
        }
        // A checked fruit tree the run replants wants a sapling as bare ground does. One still
        // waiting on its check is worth the trip for the check alone, and so is ripe fruit the
        // player picks in passing, so neither is counted.
        if (prefs.replantsGrownTrees() && stop.patchType() == PatchType.FRUIT_TREE && prediction.isFellable()
                && !(prefs.picksFruitInPassing() && PatchWork.at(prediction, stop, nowEpochSec) == PatchWork.Act.HARVEST)) {
            tally.grownTrees++;
        }
        // A ripe or unchecked bush counts as ready below, though it comes out after.
        if (PatchWork.standsEmpty(prediction, stop, nowEpochSec) || PatchWork.at(prediction, stop, nowEpochSec) == null
                        && PatchWork.comesOut(prediction, stop, prefs, player)) {
            // An empty patch is as good a reason to go as a finished crop; see
            // RunStatus.DUE. A checked tree lands here rather than under "ready": it
            // decodes identically to its own stump, so it would read ready for ever. A bush
            // picked bare lands here too: with nothing to pick, it is dug up for a fresh one.
            tally.empty++;
            // Bare ground takes a sapling. A tree still rooted here is not bare ground; one the
            // run replants was counted above, in grownTrees.
            if (!prediction.isFellable()) {
                tally.bare++;
            }
            // An empty patch is a reason to go only while the player has a seed for it.
            // Recorded rather than dropped, so the detail can say why the run went quiet.
            if (seedless) {
                tally.seedless++;
            } else {
                tally.actionable++;
                // Named apart from bare ground, which is all "empty" means to the player.
                if (prediction.isFellable()) {
                    // A calquat bears fruit like a fruit tree, so picked bare it is picked.
                    if (stop.patchType() == PatchType.FRUIT_TREE || stop.patchType() == PatchType.CALQUAT) {
                        tally.picked++;
                    } else {
                        tally.checked++;
                    }
                } else if (prediction.getCrop() != Crop.WEEDS && (stop.patchType() == PatchType.BUSH
                                || stop.patchType() == PatchType.CACTUS)) {
                    tally.picked++;
                }
            }
            return;
        }
        if (prediction.isIdle()) {
            return;
        }
        if (PatchWork.at(prediction, stop, nowEpochSec) != null) {
            tally.ready++;
            tally.actionable++;
            return;
        }
        long done = prediction.getDoneEstimateEpochSec();
        tally.growing++;
        if (done <= 0) {
            tally.undated++;
        } else if (plantedThisTrip(prediction, done, tally.tripStart)) {
            tally.plantedThisTrip++;
        }
        tally.earliestReady = earlier(done, tally.earliestReady);
        tally.latestReady = Math.max(tally.latestReady, done);
    }

    /**
     * Whether this growing crop went in on the trip the player is on now: seen this trip,
     * and finishing no sooner than a crop planted a trip's length ago would. A crop merely
     * passed on another run (Catherby's herb patch beside its fruit tree) was planted long
     * before, so it fails the second half.
     */
    private static boolean plantedThisTrip(PatchPrediction prediction, long done, long tripStart) {
        if (tripStart <= 0) {
            return false;
        }
        // Seen going in: exact, so no slack. A fruit tree passed on this trip and planted
        // two hours before on another would pass the estimate below, since its stage is
        // 160 minutes.
        if (prediction.getPlantedAtEpochSec() > 0) {
            return prediction.getPlantedAtEpochSec() >= tripStart;
        }
        // The finish estimate snaps to the crop's growth ticks, so the planting it implies
        // can read up to one stage earlier than the crop went in.
        long stage = prediction.getCrop().getTickRateMinutes() * 60L;
        long plantedAt = done - prediction.getCrop().totalGrowSeconds();
        return prediction.getObservedAtEpochSec() >= tripStart && plantedAt + stage >= tripStart;
    }

    /**
     * When the trip the player is on began, or zero when they are on none: the earliest
     * sighting in the run of patch sightings that reaches back from now with no gap longer
     * than {@link #ONE_TRIP_SECONDS}. A trip lasts as long as the player keeps working
     * patches, however long that is, and ends once they stop.
     */
    static long tripStartedAt(FarmingInputs inputs, long nowEpochSec) {
        // A player standing near patches re-stamps them every few minutes, so the chain alone
        // could reach back to an earlier run hours ago. No farm run lasts longer than this.
        long earliest = nowEpochSec - LONGEST_TRIP_SECONDS;
        List<Long> sightings = new ArrayList<>();
        for (FarmingWorldMap.PatchDef patch : FarmingWorldMap.allPatches()) {
            PatchPrediction seen = inputs.patchAt(PatchRef.of(patch.getRegionId(), patch.getVarbitId(), patch.displayName()));
            if (seen != null && seen.getObservedAtEpochSec() > 0 && seen.getObservedAtEpochSec() <= nowEpochSec) {
                sightings.add(seen.getObservedAtEpochSec());
            }
        }
        sightings.sort(Comparator.reverseOrder());
        long start = 0;
        long previous = nowEpochSec;
        for (long at : sightings) {
            if (previous - at > ONE_TRIP_SECONDS) {
                break;
            }
            start = at;
            previous = at;
        }
        return start > 0 ? Math.max(start, earliest) : 0;
    }

    private static String dueDetail(Tally tally) {
        List<String> parts = new ArrayList<>();
        // Counts without "patches": the row is one short line, and a longer one is cut off.
        if (tally.ready > 0) {
            parts.add(tally.ready + " ready");
        }
        if (tally.needsHelp > 0) {
            parts.add(tally.needsHelp + " diseased");
        }
        // Its own clause: "diseased" over a dead crop reads as something a plant cure fixes.
        if (tally.needsClearing > 0) {
            parts.add(tally.needsClearing + " dead");
        }
        // A seedless patch is also an empty one. Counting it twice cuts off the line, so the
        // count names only the empty patches the player can fill.
        int plantable = tally.empty - tally.seedless - tally.picked - tally.checked;
        if (plantable > 0) {
            parts.add(plantable + " empty");
        }
        if (tally.picked > 0) {
            parts.add(tally.picked + " picked");
        }
        if (tally.checked > 0) {
            parts.add(tally.checked + " checked");
        }
        // Apart from "empty": the emptiness is assumed until the player passes the patch.
        if (tally.unseen > 0) {
            parts.add(tally.unseen + " unseen");
        }
        // Said last: it explains why the run is smaller than it looks, and is no reason to go.
        // "Plant", not "seed": seaweed grows from spores.
        if (tally.seedless > 0) {
            parts.add(NOTHING_TO_PLANT);
        }
        return String.join(", ", parts);
    }

    /** The earlier of two dates, where zero is no date. */
    private static long earlier(long first, long second) {
        if (first <= 0) {
            return second;
        }
        return second <= 0 ? first : Math.min(first, second);
    }

    /**
     * @param readyAt when the run comes due, or zero for no date
     * @param waits whether the run waits for every patch
     */
    private static String pendingDetail(Tally tally, long readyAt, boolean waits, long nowEpochSec) {
        if (readyAt <= 0) {
            // A crop growing with no estimate holds a waiting run back without a date.
            if (waits && tally.growing > 0) {
                return "growing";
            }
            // Nothing is growing, so there is no date. With seedless patches the player
            // needs seeds, and "nothing waiting" would mislead.
            return tally.seedless > 0 ? NOTHING_TO_PLANT : "nothing waiting";
        }
        return "ready in " + FarmingDurations.describe(readyAt - nowEpochSec);
    }

    private static final String NOTHING_TO_PLANT = "nothing to plant";

    /** A run with no route to any of its stops, for any reason but a missing fairy ring. */
    private static final String NO_ROUTE_DETAIL = "no route";

    /** Worded for one patch or many: Hespori is a single patch. */
    private static final String UNSEEN_DETAIL = "not checked yet";

    // Durations are rounded by FarmingDurations, shared with the stop rows so one screen
    // never rounds the same number two ways.

    /**
     * For runs that cannot be a trip in progress: the contract, a locked run, and one with
     * no route.
     */
    private static FarmingRunStatus build(FarmingRun run, RunStatus status, String detail,
            long readyAt, int actionable, boolean predicted) {
        return build(run, status, detail, readyAt, actionable, predicted, false, false);
    }

    /**
     * @param aboutThePatch a contract verdict about the guild patch itself; see
     *                      {@link FarmingRunStatus#isAboutThePatch()}
     */
    private static FarmingRunStatus build(FarmingRun run, RunStatus status, String detail,
            long readyAt, int actionable, boolean predicted, boolean tripUnfinished, boolean aboutThePatch) {
        return new FarmingRunStatus(run.getId().name(), run.getId().getDisplayName(),
                status.name(), detail, readyAt, actionable, RunIcons.of(run.getId()), predicted, tripUnfinished, aboutThePatch);
    }

    /** Due now, one patch to work, with the act named in {@code detail}. */
    private static FarmingRunStatus dueAtThePatch(FarmingRun run, String detail, boolean predicted) {
        return build(run, RunStatus.DUE, detail, 0L, 1, predicted, false, true);
    }
}
