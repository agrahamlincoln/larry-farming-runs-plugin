package com.larryfarming.farming.engine;

import com.larryfarming.engine.farming.*;
import com.larryfarming.farming.catalogue.FarmingCatalogue;
import com.larryfarming.farming.detection.*;
import com.larryfarming.farming.model.*;
import com.larryfarming.model.PlayerState;
import com.larryfarming.snapshot.*;
import java.util.*;

/**
 * Turns a planned run into the flat, already-decided shape the panel renders.
 *
 * <p>Holds the entry points and the orchestration: which runs make the trip, the one book
 * it is walked on, the arrivals in walking order, and everything the panel shows around the
* rows. The parts live beside it: {@link RunPlanning} plans each run, with
 * {@link StopSkipRules} and {@link CropAssigner} deciding stops and crops;
 * {@link StopRowWriter} writes each arrival's row; {@link BankStopSplicer} adds the bank
 * rows.
 */
public final class FarmingAdviceBuilder {

    private FarmingAdviceBuilder() {
    }

    /**
     * The farming this player should go and do, walked through in full, with every run's
     * standing alongside it.
     *
     * <p>The trip covering every family with work waiting, or otherwise a run to walk through
     * on a quiet day, usually the herb run, so a quiet day still shows what the feature does.
     */
    public static FarmingAdvice forPlayer(PlayerState state, FarmingInputs inputs, long nowEpochSec) {
        // The caller's clock overrides the inputs' clock, so one second is in play. Inputs
        // built without a clock would otherwise never say what any stop was for.
        FarmingInputs given = (inputs != null ? inputs : FarmingInputs.none()).withNowEpochSec(nowEpochSec).seeing(state);
        Spellbook openBook = openBookOf(state);
        List<FarmingRunStatus> runs = RunScheduler.schedule(state, given, openBook, nowEpochSec);
        // Both paths below read the contract's row from the runs list's verdict, so the trip
        // and the quiet-day run cannot disagree about the contract.
        given = given.withContractRun(statusOf(runs, RunId.CONTRACT));

        FarmingAdvice trip = adviseOnTrip(runs, state, given);
        if (trip != null) {
            return withRuns(trip, runs, state, given);
        }

        RunId walk = runForAquietDay(runs, RunScheduler.herbRunsWaitForTheLevel(given.getPreferences(), state));
        return walk == null ? null : withRuns(adviseOnAquietDay(walk, state, given), runs, state, given);
    }

    /**
     * The quiet day's run, walked as a trip of one family so its stops come in the order
     * they will keep once the run falls due and joins a real trip.
     *
     * <p>Once a run falls due, the walkthrough becomes the trip. Walked as a trip here too,
     * the run keeps its order and its routes at that moment.
     *
     * <p>Planned on its own only when the trip draws no row at all.
     */
    private static FarmingAdvice adviseOnAquietDay(RunId id, PlayerState state, FarmingInputs given) {
        RunVariants.Choice pick = variantFor(id, state, given);
        if (pick == null) {
            return null;
        }
        FarmingAdvice trip = walkTrip(List.of(pick), id.getDisplayName(), state, given);
        return trip != null ? trip : adviseOn(pick.getRun(), state, given, pick.getPlanFor());
    }

    /** {@link RunId#DISPLAY_ORDER} as the ids the snapshot carries. */
    private static final List<String> RUN_ORDER = namesOf(RunId.DISPLAY_ORDER);

    /** The walkthrough with every run's standing and the panel's settings added. */
    private static FarmingAdvice withRuns(FarmingAdvice advice, List<FarmingRunStatus> runs, PlayerState state, FarmingInputs inputs) {
        if (advice == null) {
            return null;
        }
        FarmingPreferences prefs = inputs.getPreferences();
        FarmingHouse house = RunSettings.house(prefs, state, inputs.getHouse());
        return advice.toBuilder().lookOnce(LookOnce.sentence(state, house)).runs(runs).runOrder(RUN_ORDER)
                .switchedOff(RunSettings.switchedOff(prefs)).runSettings(RunSettings.forRuns(prefs, state, inputs::patchAt))
                .runFirst(RunSettings.runFirst(prefs)).waitForEveryPatch(RunSettings.waitForEveryPatch(prefs))
                .travelGroups(RunSettings.travelGroups(prefs, state, inputs.getHouse())).house(house)
                .fairyRingBlocked(fairyRingBlocked(advice, state, inputs)).build();
    }

    /**
     * Whether nothing the player uses reaches a fairy ring, while the network itself is
     * open to them. Asked on the book the run is planned for, since the Arceuus spells are
     * approaches too.
     */
    private static boolean fairyRingBlocked(FarmingAdvice advice, PlayerState state, FarmingInputs inputs) {
        Spellbook book = advice.getCarryFor() != null ? Spellbook.valueOf(advice.getCarryFor()) : Spellbook.STANDARD;
        CapabilityResolver asked = inputs.resolverOn(book);
        return asked.evaluate(FarmingCatalogue.toAfairyRing(), state, Map.of()) == CapabilityState.LACK
                && asked.resolve(Capability.FAIRY_RINGS, state).getState()
                        != CapabilityState.LACK;
    }

    /**
     * What to show when there is nothing to walk: the herb run, or else the first round of
     * patches offered, such as the fruit trees, ahead of the errands.
     *
     * <p>Herb, not whatever sorts first, because with nothing due the ranking means little.
     * Herb is the run a player most likely already does, unless
     * the herb run waits for the player's Farming level. A held herb run is still shown when
     * nothing else is offered, rather than nothing.
     *
     * @param herbsWait whether the herb run waits for the level, which passes it over
     */
    private static RunId runForAquietDay(List<FarmingRunStatus> runs, boolean herbsWait) {
        RunId herb = statusOf(runs, RunId.HERB) != null ? RunId.HERB : null;
        if (herb != null && !herbsWait) {
            return herb;
        }
        // A round of patches first: the contract and Hespori are single errands and make
        // a poor quiet-day walkthrough.
        RunId fallback = null;
        for (FarmingRunStatus run : runs) {
            RunId id = RunId.valueOf(run.getRunId());
            if (id == RunId.HERB) {
                continue;
            }
            if (!ERRANDS.contains(id)) {
                return id;
            }
            if (fallback == null) {
                fallback = id;
            }
        }
        return fallback != null ? fallback : herb;
    }

    /** The runs that are not a round of patches, which a quiet day walks only for want of one. */
    private static final Set<RunId> ERRANDS = EnumSet.of(RunId.CONTRACT, RunId.HESPORI, RunId.ANIMA);

    /**
     * The runs list's verdict on one family, or null when the player has switched that
     * family's crops off.
     */
    private static FarmingRunStatus statusOf(List<FarmingRunStatus> runs, RunId wanted) {
        for (FarmingRunStatus run : runs) {
            if (wanted.name().equals(run.getRunId())) {
                return run;
            }
        }
        return null;
    }

    /**
     * @param book the spellbook to plan the routes on, or null to follow the preview book
     *             and then the open one. Callers that already chose a variant pass the
     *             book it was chosen on, so the walkthrough and the runs list plan the
     *             same run on the same book even before the player swaps.
     */
    public static FarmingAdvice adviseOn(FarmingRun run, PlayerState state, FarmingInputs given, Spellbook book) {
        FarmingInputs inputs = given.seeing(state);
        Spellbook openBook = openBookOf(state);
        Spellbook planFor = book != null ? book : inputs.getPreviewBook() != null ? inputs.getPreviewBook() : openBook;
        // Without the ledger every route resolves as though the day's limited teleports
        // were unspent.
        CapabilityResolver resolver = inputs.resolverOn(planFor);
        // Planned in the catalogue's order, not as part of the merged circuit.
        SeedSelector.Stock rotationStock = SeedSelector.rotationStock(FarmingCatalogue.seedPolicy(), inputs.getPreferences(), state);
        RunPlanning.RunPlan planned = RunPlanning.planRun(run, state, inputs, resolver, rotationStock, null, false);
        RunPlanner planner = new RunPlanner(resolver);
        // Routed again against the best-homes-first order, as the circuit is. planRun
        // settled its routes in the catalogue's order, so a rung leaning on another stop or
        // on where the player stands was decided for a position the stop no longer holds.
        Itinerary.Trip trip = RunPlanning.replanAcrossTheCircuit(Itinerary.alone(planned.forTrip(), state), planner, state);
        // A one-element seed list so the bag, the summary and the warning take the same
        // calls as on a trip.
        return drawTrip(trip, false, run.getId().getDisplayName(), run, planned.plantedIn,
                List.of(planned.seeds), state, planner, inputs, planFor);
    }

    /**
     * The advice for a walkthrough whose rows are drawn: the bag simulated over the arrivals
     * made, the bank stops spliced in, and everything the panel shows around the rows.
     *
     * @param made the arrivals the player is making, as groups of indices into {@code plans}
     * @param rowOf where each arrival in {@code made} sits in {@code stops}
     * @param respawnFor the run whose respawn point the suggestion follows
     */
    private static FarmingAdvice walkthrough(String runName, List<String> tripRuns,
            FarmingRun respawnFor, List<FarmingStopAdvice> stops, List<List<Integer>> made,
            List<Integer> rowOf, List<StopPlan> plans, List<List<Crop>> cropFor, List<SeedPlan> seeds, boolean underway, PlayerState state,
            CapabilityResolver resolver, FarmingInputs inputs, Spellbook planFor) {
        Spellbook openBook = openBookOf(state);
        // One answer for the loadout and the bag simulation. The packing list names the
        // basket either way.
        CarryPlanner.BasketPlace basket = CarryPlanner.basketPlace(plans, cropFor, state, inputs);
        InventorySim.Plan bag = InventorySim.plan(InventoryDemands.forTrip(made, plans, cropFor, inputs, state, resolver),
                InventoryDemands.fixturesFor(state, resolver, plans, inputs, basket == CarryPlanner.BasketPlace.BAG),
                InventoryDemands.POTS_ARE_KEPT);
        List<SlotAdvice> loadout = LoadoutPlanner.plan(state, CarryPlanner.staffWielded(plans, state, resolver),
                teleportsByExplorersRing(plans), basket == CarryPlanner.BasketPlace.BACK);
        WithdrawalPlanner.Withdrawals withdrawals = WithdrawalPlanner.forBankStops(bag, made,
                plans, cropFor, seeds, state, resolver, inputs, underway);
        List<FarmingCarryItem> carry = CarryPlanner.plan(plans, seeds, cropFor, state, resolver, inputs);
        return FarmingAdvice.builder().runName(runName).tripRuns(tripRuns)
                // A leg's ends index {@code made}, whose entries index {@code plans}.
                .stops(BankStopSplicer.withBankStops(withCardNotes(stops, bag, made, rowOf,
                        plans, cropFor, carry, underway, state, resolver, inputs), bag, rowOf, withdrawals, underway))
                .carryNow(underway ? List.of() : nowHalf(withdrawals.getFirstStretch(), carry)).underway(underway)
                .upgrades(LoadoutPlanner.upgrades(loadout)).carry(carry).seedSummary(seedSummary(plans, cropFor))
                .seedWarning(seedWarning(seeds)).herbNote(untickedHerbNote(state, inputs, plans))
                .respawnAction(respawnAction(respawnFor, state, resolver)).carryFor(planFor.name()).spellbookOpen(openBook.name()).build();
    }

    // --- Planning a trip -----------------------------------------------------

    /**
     * Every family with work waiting, walked as one circuit.
     *
     * <p>Plans every due family and hands the stops to {@link Itinerary}, which puts them
     * in circuit order so a place is visited once.
     *
     * <p><b>One spellbook for the whole trip</b>, chosen by {@link #bookForTrip}. Planning
     * each family on its own best book would produce a circuit no single player could walk.
     *
     * @return null when nothing is due
     */
    private static FarmingAdvice adviseOnTrip(List<FarmingRunStatus> runs, PlayerState state, FarmingInputs inputs) {
        // Shared across families: building a resolver is the expensive half of scoring a
        // variant, and this scores every due family on every snapshot.
        Map<Spellbook, CapabilityResolver> resolvers = RunVariants.newResolverCache();
        List<RunVariants.Choice> due = new ArrayList<>();
        for (FarmingRunStatus status : runs) {
            // An unfinished trip counts as due. Planting the last herb takes the herb run
            // out of DUE, and a trip built on DUE alone would drop its woven-in stops
            // (Ortus Farm, the Monastery bushes) before the player reached them.
            if (!RunStatus.DUE.name().equals(status.getStatus()) && !status.isTripUnfinished()) {
                continue;
            }
            RunVariants.Choice choice = variantFor(RunId.valueOf(status.getRunId()), state, inputs, resolvers);
            if (choice != null) {
                due.add(choice);
            }
        }
        if (due.isEmpty()) {
            return null;
        }
        return walkTrip(due, "Farm run", state, inputs);
    }

    /**
     * The trip over these families, or null when it draws no row.
     *
     * @param due the families on the trip, in the scheduler's ranking, which picks the
     *     respawn suggestion. The stops are merged in priority order instead, so a family
     *     coming due or going quiet never re-decides which family's stop leads an arrival
     *     or wins a patch two families name.
     * @param runName the walkthrough's title
     */
    private static FarmingAdvice walkTrip(List<RunVariants.Choice> due, String runName, PlayerState state, FarmingInputs inputs) {
        List<RunVariants.Choice> merged = new ArrayList<>(due);
        // The preset's order, even for a player who runs herbs first.
        merged.sort(Comparator.comparingInt(choice -> choice.getRun().getId().getPriority()));

        Spellbook planFor = bookForTrip(merged, openBookOf(state));
        CapabilityResolver resolver = inputs.resolverOn(planFor);

        List<Itinerary.Planned> planned = new ArrayList<>(merged.size());
        List<SeedPlan> seeds = new ArrayList<>(merged.size());
        // One map for the whole trip. Patches are globally unique, and the rows are drawn
        // off the merged circuit, where a family is no longer indexable.
        Map<PatchRef, Crop> plantedIn = new HashMap<>();
        // Counted once and drawn down as the families take from it, so two families
        // drawing the rotation cannot each claim the same stack in full.
        SeedSelector.Stock rotationStock = SeedSelector.rotationStock(FarmingCatalogue.seedPolicy(), inputs.getPreferences(), state);
        // The contract's named seed leaves the stock before anything draws, so no herb patch
        // is offered it. Then the families that draw only filler (the contract's reserved
        // guild patch) draw last: filler takes carried seeds first, and must not take the
        // good seeds the player withdrew for the herb run.
        boolean[] contractSeedTaken = new boolean[merged.size()];
        List<Integer> drawOrder = new ArrayList<>(merged.size());
        for (int i = 0; i < merged.size(); i++) {
            contractSeedTaken[i] = RunPlanning.takeTheContractsSeedFirst(merged.get(i).getRun(), state, inputs, resolver, rotationStock);
            drawOrder.add(i);
        }
        drawOrder.sort(Comparator.comparing((Integer i) -> RunPlanning.drawsOnlyFiller(merged.get(i).getRun())));
        RunPlanning.RunPlan[] runs = new RunPlanning.RunPlan[merged.size()];
        for (int i : drawOrder) {
            runs[i] = RunPlanning.planRun(merged.get(i).getRun(), state, inputs, resolver, rotationStock, planFor, contractSeedTaken[i]);
        }
        for (RunPlanning.RunPlan run : runs) {
            planned.add(run.forTrip());
            seeds.add(run.seeds);
            plantedIn.putAll(run.plantedIn);
        }

        RunPlanner planner = new RunPlanner(resolver);
        // Merged, then routed again against the merged order. Each run chose its routes
        // knowing only its own stops in its own order; the circuit interleaves families
        // and lifts best homes forward, so a rung leaning on a patch can now come before it.
        // The Monastery bushes move first, so the leg they start is routed from them.
        Itinerary.Trip trip = RunPlanning.replanAcrossTheCircuit(
                CloakToTheRings.bushesBeforeTheFirstRing(Itinerary.merge(planned, state, planFor), resolver, state), planner, state);
        // The respawn suggestion follows the family the ranking puts first.
        return drawTrip(trip, true, runName, due.get(0).getRun(), plantedIn, seeds, state, planner, inputs, planFor);
    }

    /**
     * The walkthrough of a planned trip: a row per arrival, then the bag and everything
     * around the rows.
     *
     * @param merged true for a merged trip, which plans a combined route for an arrival
     *     working two patches; false for a single run, which keeps each lead's own route
     * @param respawnFor the run whose respawn point the suggestion follows
     * @return the walkthrough, or null when a merged trip draws no row; a single run is the
     *     trip even with no row
     */
    private static FarmingAdvice drawTrip(Itinerary.Trip trip, boolean merged,
            String runName, FarmingRun respawnFor, Map<PatchRef, Crop> plantedIn,
            List<SeedPlan> seeds, PlayerState state, RunPlanner planner, FarmingInputs inputs, Spellbook planFor) {
        CapabilityResolver resolver = planner.resolver();
        List<FarmingStopAdvice> stops = new ArrayList<>(trip.getVisits().size());
        // Grown as the circuit is walked, for visits that earn an arrival's combined route
        // (planned here, not in replanAcrossTheCircuit). Filled for any combined chain with a
        // rung that leans on a patch.
        Map<PatchRef, CapabilityState> reached = new LinkedHashMap<>();
        // The families the player will see rows for, in circuit order.
        Set<RunId> covered = new LinkedHashSet<>();
        // The visits the player is making, and where each one's row sits in {@code stops}.
        // The bag is simulated over the shorter list, and its leg indices must map back to
        // a row.
        List<List<Integer>> made = new ArrayList<>();
        List<Integer> rowOf = new ArrayList<>();
        // Only the first arrival made may be routed from where the player stands. Cleared on
        // the first VISIT row, the same journey replanLeaningRungs spends it on
        // (StopRowWriter.isJourney).
        boolean mayStandHere = true;
        // What the last journey leaves the next, as replanLeaningRungs tracks it.
        RunPlanner.Start start = null;
        for (List<Integer> visit : trip.getVisits()) {
            StopPlan route = merged ? StopRowWriter.routeForVisit(visit, trip.getPlans(),
                    StopRowWriter.leadOf(visit, trip.getPlans()), planner, state, reached, mayStandHere, start) : null;
            FarmingStopAdvice row = StopRowWriter.adviseOnGroup(visit, trip.getPlans(), trip.getCropFor(), plantedIn, inputs, route, state);
            start = CloakToTheRings.after(start, visit, trip.getPlans(), resolver, state);
            stops.add(row);
            // Only VISIT rows feed the bag and the reached patches. The sim banks at any
            // arrival on a leg that has a bank, so a skipped visit would let it break the
            // trip somewhere the walkthrough is not sending the player. A rung leaning on a
            // patch is free only once the player has stood there.
            if (row.getOutcome() == FarmingStopAdvice.Outcome.VISIT) {
                made.add(visit);
                rowOf.add(stops.size() - 1);
                addPatchesWorked(visit, trip.getPlans(), reached);
                mayStandHere = false;
            }
            for (int index : visit) {
                covered.add(trip.getRunFor().get(index));
            }
        }

        // No arrival drew a row, so there is no trip and the caller falls back to its
        // quiet-day run.
        if (stops.isEmpty() && merged) {
            return null;
        }
        boolean underway = underway(trip.getVisits(), trip.getPlans(), trip.getRunFor(), inputs);
        // A single run is itself the trip, so the panel has one rule for "which families am
        // I looking at?".
        List<String> tripRuns = merged ? namesOf(covered) : List.of(respawnFor.getId().name());
        return walkthrough(runName, tripRuns, respawnFor, stops, made, rowOf, trip.getPlans(),
                trip.getCropFor(), seeds, underway, state, resolver, inputs, planFor);
    }

    /** Run ids as the snapshot speaks them, immutable, in the given (circuit) order. */
    private static List<String> namesOf(Collection<RunId> runs) {
        List<String> names = new ArrayList<>(runs.size());
        for (RunId run : runs) {
            names.add(run.name());
        }
        return List.copyOf(names);
    }

    /**
     * Whether the player is partway round the walkthrough on screen: some stop on it is off
     * the walkthrough with a crop growing that was seen within one trip's span, and the
     * family that stop belongs to still has an arrival to make.
     *
     * <p>The family check keeps a finished run from leaking into the next one. Straight
     * after a herb run every herb patch is freshly growing, and a later trip or the
     * quiet-day herb walkthrough still lists them as skipped. Those sightings belong to a
     * trip that is over unless the herb family has somewhere left to go.
     *
     * @param arrivals the arrivals that drew a row, as indices into {@code plans}
     * @param runFor the family each plan belongs to, index for index with {@code plans}
     */
    static boolean underway(List<List<Integer>> arrivals, List<StopPlan> plans, List<RunId> runFor, FarmingInputs inputs) {
        // Without a clock every sighting would read as moments ago.
        if (inputs.getNowEpochSec() <= 0) {
            return false;
        }
        Set<RunId> stillVisiting = EnumSet.noneOf(RunId.class);
        for (List<Integer> group : arrivals) {
            for (int index : group) {
                if (plans.get(index).isVisit()) {
                    stillVisiting.add(runFor.get(index));
                }
            }
        }
        for (List<Integer> group : arrivals) {
            for (int index : group) {
                if (stillVisiting.contains(runFor.get(index)) && grewThisTrip(plans.get(index), inputs)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether this stop is off the walkthrough with a crop still growing in its patch that
     * was seen within one trip's span.
     *
     * <p>Patches report only while the player stands near them, so a fresh sighting of a
     * growing crop means they planted it or walked past it on this trip. The window is the
     * runs list's ({@link RunScheduler#seenThisTrip}), so the two agree on when a trip ends:
     * one sighting within it is enough, as the latest patch of a running trip always is.
     *
     * <p>Read off the patch, not off the skip reason. Route skips are settled before growth
     * is checked, so a patch the player has just planted can still be skipped as not worth
     * the trip when the planner does not know the way the player came.
     *
     * <p>Standing crops do not count: a bush left standing is passed on every run and says
     * nothing about this one.
     */
    private static boolean grewThisTrip(StopPlan plan, FarmingInputs inputs) {
        if (plan.isVisit() || plan.getSkipReason() == FarmingSkipReason.STANDING_CROP) {
            return false;
        }
        for (PatchRef ref : plan.getStop().getPatches()) {
            PatchPrediction seen = inputs.patchAt(ref);
            if (seen != null && seen.getCrop() != null && !seen.isIdle() && StopSkipRules.isStillGrowing(seen)
                    && RunScheduler.seenThisTrip(seen.getObservedAtEpochSec(), inputs.getNowEpochSec())) {
                return true;
            }
        }
        return false;
    }

    /**
     * The one book the whole trip is walked on. Its rungs route every stop, and its seats
     * order every family's stops on the circuit.
     *
     * <p>The first book any family <em>asked</em> for, otherwise the open one. Only the herb
     * run comes in per-book variants; every other {@link RunVariants.Choice} names the open
     * book. Taking the top-ranked family's book instead would plan a trailing herb run's
     * Arceuus route on Standard and silently drop patches.
     *
     * <p><b>Not measured:</b> what the swap costs the other families. A swap may turn
     * another family's spell into a tablet. That cost is bounded, because a teleport with no
     * spell falls back to a tablet, and the player is told to swap.
     */
    private static Spellbook bookForTrip(List<RunVariants.Choice> due, Spellbook openBook) {
        for (RunVariants.Choice choice : due) {
            if (choice.getPlanFor() != openBook && choice.getPlanFor() != Spellbook.ANY) {
                return choice.getPlanFor();
            }
        }
        return openBook;
    }

    /**
     * The patches a visit works, added once the visit has been routed, each as sure as its
     * own stop's plan (not the arrival plan {@link StopRowWriter#routeForVisit} may have
     * found).
     */
    private static void addPatchesWorked(List<Integer> visit, List<StopPlan> plans, Map<PatchRef, CapabilityState> reached) {
        for (int index : visit) {
            RunPlanner.stood(reached, plans.get(index));
        }
    }

    // --- Seeds ---------------------------------------------------------------

    /**
     * What to carry out of the bank: "2 × Toadflax, 5 × Irit".
     *
     * <p>Tallied by {@link SeedPlan#goingInTheGround}, the same call the bag makes, so the
     * sentence and the list agree. It counts what lands, not what was dealt.
     */
    private static String seedSummary(List<StopPlan> plans, List<List<Crop>> cropFor) {
        StringBuilder summary = new StringBuilder();
        for (SeedAllocation.Tally tally : SeedPlan.goingInTheGround(plans, cropFor)) {
            if (summary.length() > 0) {
                summary.append(", ");
            }
            summary.append(tally.getCount()).append(" × ").append(CropNames.displayNameOf(tally.getCrop()));
        }
        return summary.toString();
    }

    /**
     * The one thing to say about the seeds that is not the plan itself.
     *
     * <p>Ordered by importance: an unread seed vault first, then nothing to plant, then a
     * short stack.
     *
     * <p><b>One ladder over the trip's totals.</b> The sentence sits above every family, so
     * the worst family's own verdict would say "no seed you can plant" over a walkthrough
     * that plants three.
     */
    static String seedWarning(List<SeedPlan> seeds) {
        if (!SeedPlan.confident(seeds)) {
            return "open your seed vault to count your seeds";
        }
        // Counted over both ends and every run. Per-end counts go wrong once the rotation
        // closes onto one crop: one stack of five can cover either end, but not both.
        int wanted = SeedPlan.wanted(seeds);
        int planted = SeedPlan.planted(seeds);
        // Nothing wanted means every patch is still growing, not that the vault is empty.
        // The skipped stops already say so.
        if (wanted == 0) {
            return "";
        }
        if (planted == 0) {
            return "no seed you can plant";
        }
        if (planted >= wanted) {
            return "";
        }
        return "you have seeds for " + planted + " of the " + wanted + " patches";
    }

    /**
     * A note for the player carrying herb seeds the rotation will not plant because they
     * are switched off in the herb run's grid, or empty. The grid stays the rule, so the run
     * says why the seeds in the inventory are passed over instead of quietly planting them.
     *
     * <p>Empty when no visit plants a herb patch: with no herb planting on screen the seeds
     * are not being passed over.
     */
    private static String untickedHerbNote(PlayerState state, FarmingInputs inputs, List<StopPlan> plans) {
        if (!plantsTheHerbRotation(plans)) {
            return "";
        }
        List<Crop> unticked = SeedSelector.carriedButUnticked(FarmingCatalogue.seedPolicy(), inputs.getPreferences(), state);
        if (unticked.isEmpty()) {
            return "";
        }
        List<String> names = new ArrayList<>(unticked.size());
        for (Crop crop : unticked) {
            names.add(CropNames.inSentence(crop));
        }
        return "You carry " + LookOnce.joined(names, "and") + " seeds. Turn them on under "
                + RunId.HERB.getDisplayName() + " to plant them.";
    }

    /** Whether any visit plants a herb patch the rotation fills. */
    private static boolean plantsTheHerbRotation(List<StopPlan> plans) {
        for (StopPlan plan : plans) {
            if (!plan.isVisit() || !plan.getStop().isPlanting() || plan.getStop().isFixedCrop()) {
                continue;
            }
            FarmingWorldMap.PatchDef def = plan.getStop().primaryPatch().resolve();
            if (def != null && def.getType() == PatchType.HERB) {
                return true;
            }
        }
        return false;
    }

    // --- Spellbook -----------------------------------------------------------

    /**
     * Which version of a run to show, decided as the runs list decides it: by which reaches
     * the most patches for this player, not by the book the default route prefers.
     *
     * <p>A preview book picks the variant written for it outright.
     */
    static RunVariants.Choice variantFor(RunId id, PlayerState state, FarmingInputs inputs) {
        return variantFor(id, state, inputs, RunVariants.newResolverCache());
    }

    /**
     * @param resolvers one resolver per book, shared across calls because building them is
     *                  the expensive half of the answer
     */
    private static RunVariants.Choice variantFor(RunId id, PlayerState state,
            FarmingInputs inputs, Map<Spellbook, CapabilityResolver> resolvers) {
        List<FarmingRun> variants = FarmingCatalogue.runsById().getOrDefault(id, List.of());
        if (variants.isEmpty()) {
            return null;
        }
        Spellbook preview = inputs.getPreviewBook();
        if (preview != null) {
            for (FarmingRun run : variants) {
                if (run.getSpellbook() == preview) {
                    return new RunVariants.Choice(run, preview, 0, 0, List.of());
                }
            }
            // A book this run has no version of (Standard, for the herb run). The best
            // variant is still planned on the named book.
            RunVariants.Choice best = RunVariants.best(variants, state, preview, inputs, resolvers);
            return new RunVariants.Choice(best.getRun(), preview, best.getCertainPatches(), best.getReachablePatches(), best.getPlans());
        }
        return RunVariants.best(variants, state, openBookOf(state), inputs, resolvers);
    }

    /** The book the player has open, as the vocabulary the teleport model speaks. */
    static Spellbook openBookOf(PlayerState state) {
        Map<Integer, Integer> varbits = state == null ? null : state.getVarbits();
        Integer book = varbits == null ? null : varbits.get(CapabilitySources.SPELLBOOK_VARBIT);
        switch (book == null ? CapabilitySources.STANDARD_SPELLBOOK : book) {
            case CapabilitySources.LUNAR_SPELLBOOK:
                return Spellbook.LUNAR;
            case CapabilitySources.ARCEUUS_SPELLBOOK:
                return Spellbook.ARCEUUS;
            default:
                // Ancients included: the runs use none of its spells.
                return Spellbook.STANDARD;
        }
    }

    /**
     * The rows with their card notes: the tools to leave with the leprechaun at the last
     * stop that uses each, and, partway round, what the next stop needs that the bag lacks.
     *
     * @param rowOf where each arrival in {@code made} sits in {@code stops}
     */
    private static List<FarmingStopAdvice> withCardNotes(List<FarmingStopAdvice> stops,
            InventorySim.Plan bag, List<List<Integer>> made, List<Integer> rowOf,
            List<StopPlan> plans, List<List<Crop>> cropFor, List<FarmingCarryItem> carry,
            boolean underway, PlayerState state, CapabilityResolver resolver, FarmingInputs inputs) {
        List<FarmingStopAdvice> rows = new ArrayList<>(stops);
        for (Map.Entry<Integer, List<FarmingCarryItem>> leave : LeprechaunPlanner.leaveAt(made,
                plans, cropFor, carry, underway, state).entrySet()) {
            int row = rowOf.get(leave.getKey());
            rows.set(row, rows.get(row).toBuilder().leprechaun(LeprechaunPlanner.sentence(leave.getValue())).build());
        }
        if (underway && !made.isEmpty()) {
            List<StopPlan> here = new ArrayList<>();
            List<List<Crop>> plantedHere = new ArrayList<>();
            for (int index : made.get(0)) {
                here.add(plans.get(index));
                plantedHere.add(cropFor.get(index));
            }
            String missing = MissingItems.at(here, plantedHere, LeprechaunPlanner.hasLeprechaun(made.get(0), plans),
                    bankBefore(bag, here), state, resolver, inputs);
            if (!missing.isEmpty()) {
                int row = rowOf.get(0);
                rows.set(row, rows.get(row).toBuilder().warning(missing).build());
            }
        }
        return rows;
    }

    /**
     * The bank the warning on the next stop names: the bank row drawn before it, when the
     * bag simulation banks there, else the bank on the stop's own route, else none.
     */
    private static Bank bankBefore(InventorySim.Plan bag, List<StopPlan> here) {
        for (InventorySim.Leg leg : bag.getLegs()) {
            if (leg.getFrom() == 0 && leg.getBankAt() != null && leg.getBankPlace() != null) {
                return leg.getBankPlace();
            }
        }
        for (StopPlan plan : here) {
            if (plan.isVisit() && plan.getRoute() != null && plan.getRoute().getBank() != null) {
                return plan.getRoute().getBank();
            }
        }
        return null;
    }

    /**
     * The packing list's "now" half: the first stretch's lines, in the whole list's order so
     * the grid does not reorder when the split comes and goes.
     *
     * <p>A line the player owns nowhere stays in, whichever stretch needs it: no bank hands
     * it over, so no Later band would name it, and the grid is the only place it shows.
     *
     * @return empty when no later bank splits the trip
     */
    static List<FarmingCarryItem> nowHalf(List<FarmingCarryItem> firstStretch, List<FarmingCarryItem> carry) {
        if (firstStretch.isEmpty()) {
            return List.of();
        }
        Map<Integer, FarmingCarryItem> stretch = new LinkedHashMap<>();
        for (FarmingCarryItem line : firstStretch) {
            stretch.put(line.getItemId(), line);
        }
        List<FarmingCarryItem> now = new ArrayList<>(firstStretch.size());
        for (FarmingCarryItem line : carry) {
            FarmingCarryItem mine = stretch.remove(line.getItemId());
            if (mine != null) {
                now.add(mine);
            } else if (line.getAvailability() == FarmingCarryItem.Availability.MISSING) {
                now.add(line);
            }
        }
        // Anything the whole list does not name keeps its own order at the end.
        now.addAll(stretch.values());
        return now;
    }

    /**
     * Whether a visited stop teleports by the Explorer's ring. The planner offers such a rung
     * only while the ring has a teleport left today, so this also says one is left.
     */
    private static boolean teleportsByExplorersRing(List<StopPlan> plans) {
        for (StopPlan plan : plans) {
            if (!plan.isVisit() || plan.getNeeds() == null) {
                continue;
            }
            for (Capability need : plan.getNeeds()) {
                if (need.getTierFamily() == Capability.TierFamily.EXPLORERS_RING) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The respawn point worth setting before this run, or empty when there is nothing
     * useful to say.
     *
     * <p>Silent when the run has no respawn town, when the player already respawns there,
     * when they cannot select the town (its unlock is not definitely held), when they
     * cannot cast Respawn Teleport, or while the setting is unread.
     */
    private static String respawnAction(FarmingRun run, PlayerState state, CapabilityResolver resolver) {
        String wanted = run.getRespawnTown();
        if (wanted == null || wanted.isEmpty()) {
            return "";
        }
        // Respawn Teleport (Arceuus, 34 Magic) is the only thing a respawn point helps.
        if (resolver.resolve(Capability.RESPAWN_TELEPORT, state).getState() == CapabilityState.LACK) {
            return "";
        }
        String selected = CapabilitySources.selectedRespawnTown(state);
        if (selected == null || selected.equals(wanted)) {
            return "";
        }
        // Suggest only on a definite yes; UNKNOWN (including a town with no unlock rule)
        // stays silent.
        if (CapabilitySources.canSelectRespawn(wanted, state) != CapabilityState.HAVE) {
            return "";
        }
        return "Set your respawn point to " + wanted + " to shorten this run. It is " + selected + " now.";
    }
}
