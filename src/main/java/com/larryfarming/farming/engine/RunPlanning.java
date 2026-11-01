package com.larryfarming.farming.engine;

import com.larryfarming.engine.farming.Crop;
import com.larryfarming.farming.catalogue.FarmingCatalogue;
import com.larryfarming.farming.detection.CapabilityResolver;
import com.larryfarming.farming.detection.CapabilityState;
import com.larryfarming.farming.model.*;
import com.larryfarming.model.PlayerState;
import java.util.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

/**
 * One run planned on its own, before anything about how it is shown: the stops worth
 * making, the seeds sized for them, and the crop each patch takes. Then the routes that
 * lean on another stop, re-decided once the stops and the walking order have settled.
 *
 * <p>Routes come from {@link RunPlanner}, which plans travel alone. This class adds the
 * ground and the seeds, through {@link StopSkipRules} and {@link CropAssigner}.
 */
final class RunPlanning {

    private RunPlanning() {
    }

    /**
     * One run planned as far as it can be on its own: which stops are worth making, what
     * each patch takes, and the seeds that answer came out of.
     *
     * <p>Held together because the three only agree with each other: crops are allocated
     * against seeds sized for these stops, then stops the seeds ran out before are dropped.
     *
     * <p><b>{@link #plans} and {@link #cropFor} stay index-aligned only because
     * {@link StopSkipRules#dropStopsTheSeedsRanOutBefore} turns a stop into a SKIP in
     * place</b> rather than removing it. {@link Itinerary.Planned} rejects a mismatch. A drop
     * that removes entries must filter {@code cropFor} alongside.
     */
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    static final class RunPlan {

        private final FarmingRun run;
        final List<StopPlan> plans;
        final List<List<Crop>> cropFor;
        final SeedPlan seeds;
        /** Which patch each seed landed in; {@link #cropFor} is per stop, not per patch. */
        final Map<PatchRef, Crop> plantedIn;

        Itinerary.Planned forTrip() {
            return new Itinerary.Planned(run.getId(), plans, cropFor);
        }
    }

    /**
     * Plan one run, up to but not including anything about how it is shown.
     *
     * <p>Stays per run even on a merged trip: the seed rotation is sized against
     * <em>this</em> run's patches and allocated in the catalogue's patch order, so merging
     * first would hand a patch another's seed. Trip-wide concerns (circuit order, shared
     * visits, the bag) work on the results.
     *
     * @param tripBook the book of the merged trip this run is part of, whose seats its seeds
     *     are dealt down, or null for a run walked on its own
     * @param contractSeedTaken whether {@link #takeTheContractsSeedFirst} has already taken
     *     this run's contract seed out of the stock
     */
    static RunPlan planRun(FarmingRun run, PlayerState state, FarmingInputs inputs,
            CapabilityResolver resolver, SeedSelector.Stock rotationStock, Spellbook tripBook, boolean contractSeedTaken) {
        RunPlanner planner = new RunPlanner(resolver);
        List<StopPlan> plans = plansOf(run, planner, state, inputs);
        // Read once so the quota and the allocation below rank patches the same way.
        int[] walk = CropAssigner.walkPositions(plans, tripBook);

        SeedPolicy policy = FarmingCatalogue.seedPolicy();
        // Counts only patches worth a good seed, which enforces the reserved band: the
        // contract's guild patch is its own whole family, so assignCrops would otherwise
        // give it the top seat and the player's best herb.
        int rotationPatches = countRotationPatches(plans, state, inputs);
        // Taken before the rotation draws, so the guild's seed is not also offered to a
        // herb patch. A trip takes it earlier still, in takeTheContractsSeedFirst.
        if (!contractSeedTaken) {
            takeTheContractsSeed(plans, state, inputs, rotationStock);
        }
        // The good-seed quota is spent, not re-reserved: sized against seats already in the
        // ground, so planting the two best patches does not free the quota to promote the
        // next two.
        //
        // A player who puts the best seeds everywhere has no quota: every patch worth a good
        // seed takes one. The reserved guild patch still is not, so it keeps the filler.
        int quota = policy.getGoodSeedsPerRun();
        int goodPatches = inputs.getPreferences().plantsBestSeedsEverywhere() ? Math.min(countGoodHomes(plans), rotationPatches)
                : Math.min(countGoodHomes(plans), quota - CropAssigner.goodSeatsSpent(plans, state, inputs, quota, walk));
        // A run naming its own seeds (every tree run) gets a ranked ladder, not the herb
        // rotation. Only the rotation draws from the shared stock: a tree run's ladder
        // covers one patch type, so two tree families never compete for a sapling. A patch
        // a diary claims takes its crop in assignCrops, outside the ladder.
        SeedPlan seeds = run.getSeedPatchType() == null ? SeedSelector.select(policy, inputs.getPreferences(), state, goodPatches,
                        rotationPatches - goodPatches, rotationStock) : SeedSelector.forPreference(inputs.getPreferences(),
                        run.getSeedPatchType(), state, countPlanting(plans) - countDiaryClaims(plans, state, inputs),
                        DiaryPlantings.withheld(state, inputs.getPreferences()));

        // Dealt in walk order, or a seed box lands seeds elsewhere. plantedIn records the
        // patch, not just the stop, so planting one teak at Fossil Island does not ask for
        // the trees in the other two patches to come down.
        Map<PatchRef, Crop> plantedIn = new HashMap<>();
        List<List<Crop>> cropFor = CropAssigner.assignCrops(plans, seeds, state, inputs, walk, plantedIn);
        List<StopPlan> settled = replanWhatLeanedOnAstopThatLeft(
                StopSkipRules.dropStopsTheSeedsRanOutBefore(plans, cropFor, inputs), planner, state);
        return new RunPlan(run, settled, cropsWithoutTheStopsThatLeft(settled, cropFor), seeds, plantedIn);
    }

    /**
     * Routes re-decided against the run the player is actually walking, for rungs that lean
     * on another stop.
     *
     * <p>{@link RunPlanner#plan} answers "have we been there" from every stop it routed, but
     * stops have since been dropped for what is in their ground. A rung riding the Grand
     * Tree's spirit tree is wrong once that still-growing patch is no longer a stop.
     *
     * <p>Only visits that {@link #wantsReplanning} are re-asked; a stop skipped for its
     * ground stays skipped. Walked in order with the reached set rebuilt as it goes, so a
     * rung is never satisfied by a later stop. One pass, not to a fixed point: no chain in
     * the catalogue is three deep.
     */
    private static List<StopPlan> replanWhatLeanedOnAstopThatLeft(List<StopPlan> plans, RunPlanner planner, PlayerState state) {
        List<StopPlan> settled = new ArrayList<>(plans);
        Map<PatchRef, CapabilityState> reached = new LinkedHashMap<>();
        RunPlanner.Allowance allowance = new RunPlanner.Allowance();
        for (int index = 0; index < settled.size(); index++) {
            replanOne(settled, index, planner, state, reached, allowance.covers(settled.get(index).getStop()), null);
            allowance.spendOn(settled.get(index));
            RunPlanner.stood(reached, settled.get(index));
        }
        return settled;
    }

    /**
     * The same re-planning over a drawn order: the stops in the order they will be walked,
     * grouped by the arrival they share. The circuit can reverse a run's own order (it walks
     * Taverley before Falador), so a rung like "run north from the Falador tree patch" must
     * be re-decided against it.
     *
     * <p>A whole group is re-planned before any of it is stood at, so no stop is satisfied by
     * a patch at its own arrival. One forward sweep suffices: a lean is only satisfied by an
     * earlier group, and each stop is stood at in its settled form, so a stop that loses its
     * route strands anything leaning on it at any depth.
     *
     * @param groups every index in {@code plans} exactly once, in the order reached. A
     *     partial grouping drops the missing stops from the reached set and withdraws rungs
     *     that were fine. A group is a journey only when its row is a visit
     *     ({@link StopRowWriter#isJourney}); anything else spends no allowance, reaches
     *     nothing and passes the previous journey's start on, as the row walk reads it.
     */
    static List<StopPlan> replanLeaningRungs(List<StopPlan> plans, List<List<Integer>> groups, RunPlanner planner, PlayerState state) {
        List<StopPlan> settled = new ArrayList<>(plans);
        Map<PatchRef, CapabilityState> reached = new LinkedHashMap<>();
        RunPlanner.Allowance allowance = new RunPlanner.Allowance();
        // What the last journey made leaves the next one: a ring beside the Monastery bushes.
        RunPlanner.Start start = null;
        for (List<Integer> group : groups) {
            for (int index : group) {
                replanOne(settled, index, planner, state, reached, allowance.covers(settled.get(index).getStop()), start);
            }
            // Spent after the whole group: the stops at one arrival are one journey, and
            // only when the drawn row is a visit. A visit under a skipped lead is no journey,
            // so it spends nothing and reaches nothing.
            if (!StopRowWriter.isJourney(group, settled)) {
                continue;
            }
            for (int index : group) {
                allowance.spendOn(settled.get(index));
                RunPlanner.stood(reached, settled.get(index));
            }
            start = CloakToTheRings.after(start, group, settled, planner.resolver(), state);
        }
        return settled;
    }

    /**
     * One stop re-asked, if it is being visited and anything about its chain can have
     * changed since it was routed: it leans on a stop or a position, or the journey before
     * it left the player at the start of a route ({@code start} not null).
     */
    private static void replanOne(List<StopPlan> settled, int index, RunPlanner planner,
            PlayerState state, Map<PatchRef, CapabilityState> reached, boolean mayStandHere, RunPlanner.Start start) {
        StopPlan plan = settled.get(index);
        // Visits only. A stop written off for having no route stays written off even if a
        // second look could reach it: the standing-crop gates, seed checks and crop
        // allocation never read a non-visit's ground, so resurrecting one would draw a full
        // visit to a patch nothing has checked. Fixing that means re-running those gates and
        // re-dealing seeds in this pass.
        if (plan.isVisit() && (start != null || wantsReplanning(plan.getStop()))) {
            settled.set(index, planner.planStop(plan.getStop(), state, reached, mayStandHere, start));
        }
    }

    /**
     * The crops, less the ones allocated to a stop that has since lost its route.
     *
     * <p>{@code adviseOnGroup}'s planting tally counts every stop at an arrival whose lead is
     * visited, so a stranded stop beside a visited one would otherwise print "plant a yew"
     * for a patch the row is not working. Both re-planning passes need this.
     */
    static List<List<Crop>> cropsWithoutTheStopsThatLeft(List<StopPlan> plans, List<List<Crop>> cropFor) {
        List<List<Crop>> kept = new ArrayList<>(cropFor);
        for (int index = 0; index < plans.size(); index++) {
            if (!plans.get(index).isVisit()) {
                kept.set(index, List.of());
            }
        }
        return kept;
    }

    /**
     * Whether any rung of this stop is one that leans on another stop.
     *
     * <p>Asked of the whole chain, not the chosen route, because the question runs both ways:
     * a rung that leaned and no longer can must be taken back, and one that could not lean
     * and now can must be offered. The second stop's chosen route names no patch.
     */
    private static boolean leansOnApatch(RunStop stop) {
        for (TravelOption rung : stop.getTravel()) {
            if (!rung.getRequires().leafPatches().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether re-asking this stop can give a different answer from the one it already has.
     *
     * <p>Two ways it can: it leans on another stop and the run has changed underneath it, or
     * it is answerable from where the player stands and this pass may hand the position
     * allowance to a different stop than the first pass did. Asked of the whole chain, for
     * the reason {@link #leansOnApatch} gives.
     */
    private static boolean wantsReplanning(RunStop stop) {
        if (leansOnApatch(stop)) {
            return true;
        }
        for (TravelOption rung : stop.getTravel()) {
            // Covers a zone named outright and a reach(...) leaf to a place whose ground is
            // known. TravelOption.to always builds such a leaf, so the declared destination
            // needs no separate check.
            if (rung.getRequires().readableFromPosition()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The whole trip's leaning rungs, re-decided in circuit order.
     *
     * <p>The per-run pass only knows its own stops. Here a tree run's Grand Tree stop can
     * serve the fruit run's Village stop.
     */
    static Itinerary.Trip replanAcrossTheCircuit(Itinerary.Trip trip, RunPlanner planner, PlayerState state) {
        List<StopPlan> settled = replanLeaningRungs(trip.getPlans(), trip.getVisits(), planner, state);
        return trip.rerouted(settled, cropsWithoutTheStopsThatLeft(settled, trip.getCropFor()));
    }

    /**
     * Patches being planted today, each needing seeds.
     *
     * <p>Patches, not stops: a stop is a <em>visit</em>, and Fossil Island is three hardwood
     * patches reached by one journey.
     */
    private static int countPlanting(List<StopPlan> plans) {
        int count = 0;
        for (StopPlan plan : plans) {
            if (plan.isVisit() && plan.getStop().isPlanting()) {
                count += plan.getStop().getPatches().size();
            }
        }
        return count;
    }

    /** Patches being planted today that a diary claims, out of {@link #countPlanting}. */
    private static int countDiaryClaims(List<StopPlan> plans, PlayerState state, FarmingInputs inputs) {
        return (int) visitedRotation(plans).stream().filter(patch -> DiaryPlantings.claimedBy(patch, state,
                        inputs.getPreferences()) != null).count();
    }

    /** The patches of the visited stops that plant from a seed list, not one fixed crop. */
    private static List<PatchRef> visitedRotation(List<StopPlan> plans) {
        List<PatchRef> patches = new ArrayList<>();
        for (StopPlan plan : plans) {
            if (plan.isVisit() && plan.getStop().isPlanting() && !plan.getStop().isFixedCrop()) {
                patches.addAll(plan.getStop().getPatches());
            }
        }
        return patches;
    }

    /**
     * Patches this run's rotation has to fill: the ones being planted today that do not
     * name their own crop.
     *
     * <p>Counted over visited stops, in patches, as {@link #countPlanting} is. Contract
     * patches are excluded.
     */
    private static int countRotationPatches(List<StopPlan> plans, PlayerState state, FarmingInputs inputs) {
        return (int) visitedRotation(plans).stream().filter(ref -> !CropAssigner.heldForTheNextContract(ref, state)
                        && CropAssigner.contractedInto(ref, state, inputs) == null).count();
    }

    /**
     * Takes the contract's seed out of the stack the rotation is about to draw from.
     *
     * <p>A contracted patch is dealt outside the shared allocation (see
     * {@link SeedSelector#select}'s {@code stock}), so without this the same seed would be
     * promised to the guild and to a herb patch on the round. Drawn one per patch, the
     * stock's unit throughout.
     */
    private static void takeTheContractsSeed(List<StopPlan> plans, PlayerState state, FarmingInputs inputs, SeedSelector.Stock stock) {
        for (PatchRef ref : visitedRotation(plans)) {
            Crop crop = CropAssigner.contractedInto(ref, state, inputs);
            if (crop != null) {
                stock.spendOne(crop);
            }
        }
    }

    /**
     * Takes this run's contract seed out of the stock before any family on the trip draws
     * the rotation, so {@link #drawsOnlyFiller} families can draw last. Plans the run the
     * way {@link #planRun} does, because only a visited stop takes its seed; a run with no
     * reserved patch, or no contract crop to take, is not planned.
     *
     * @return whether the seed was taken here, which {@link #planRun} must then skip
     */
    static boolean takeTheContractsSeedFirst(FarmingRun run, PlayerState state, FarmingInputs inputs, CapabilityResolver resolver,
            SeedSelector.Stock rotationStock) {
        if (inputs.getContractCrop() == null || rotationPatchesOf(run).stream().noneMatch(PatchPriority::isReservedForContracts)) {
            return false;
        }
        List<StopPlan> plans = plansOf(run, new RunPlanner(resolver), state, inputs);
        takeTheContractsSeed(plans, state, inputs, rotationStock);
        return true;
    }

    /**
     * The run's stop plans before any seed is dealt. {@link #planRun} and
     * {@link #takeTheContractsSeedFirst} both read visits off these, so they must agree.
     */
    private static List<StopPlan> plansOf(FarmingRun run, RunPlanner planner, PlayerState state, FarmingInputs inputs) {
        return StopSkipRules.leaveStandingCrops(run, planner.plan(run, state), inputs, state);
    }

    /**
     * Whether this run draws on the herb rotation through reserved patches alone: the
     * contract's guild herb patch. Such a run sizes no good seeds and takes only filler.
     *
     * <p>A trip draws these families last. The filler end takes carried seeds first, so a
     * filler draw ahead of the herb run would plant the toadflax the player has just
     * withdrawn for the herb run's best patches, and the plan would change mid-withdrawal.
     */
    static boolean drawsOnlyFiller(FarmingRun run) {
        List<PatchRef> patches = rotationPatchesOf(run);
        return run.getSeedPatchType() == null && !patches.isEmpty() && patches.stream().allMatch(PatchPriority::isReservedForContracts);
    }

    /** The patches this run plants from a seed list rather than one fixed crop. */
    private static List<PatchRef> rotationPatchesOf(FarmingRun run) {
        List<PatchRef> patches = new ArrayList<>();
        for (RunStop stop : run.getStops()) {
            if (stop.isPlanting() && !stop.isFixedCrop()) {
                patches.addAll(stop.getPatches());
            }
        }
        return patches;
    }

    /**
     * Rotation patches a good seed may go in: every one not reserved for contracts.
     *
     * <p>The good end is sized against this, not the whole rotation. The Farming Guild herb
     * patch is the contract's entire family, so {@link PatchPriority}'s ranking alone would
     * still put it first within that family. Zero here means the contract, which draws from
     * the filler end.
     */
    private static int countGoodHomes(List<StopPlan> plans) {
        return (int) visitedRotation(plans).stream().filter(ref -> !PatchPriority.isReservedForContracts(ref)).count();
    }
}
