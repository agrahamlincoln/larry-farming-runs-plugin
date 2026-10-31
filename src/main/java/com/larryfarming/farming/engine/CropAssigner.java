package com.larryfarming.farming.engine;

import com.larryfarming.engine.farming.Crop;
import com.larryfarming.engine.farming.CropState;
import com.larryfarming.engine.farming.FarmingWorldMap;
import com.larryfarming.engine.farming.PatchType;
import com.larryfarming.engine.farming.PatchPrediction;
import com.larryfarming.farming.catalogue.Arrivals;
import com.larryfarming.farming.catalogue.CropData;
import com.larryfarming.farming.model.PatchRef;
import com.larryfarming.farming.model.RunStop;
import com.larryfarming.farming.model.Spellbook;
import com.larryfarming.model.PlayerState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which crop goes into which patch: the run's seeds dealt down the patches best home first,
 * a living tree traded only for a better one unless grown trees are replanted, and a
 * contract's crop put where the contract wants it.
 *
 * <p>Also answers the questions the allocation asks of a single patch (is it held for the
 * next contract, is a living crop in the way), so the quota count and the allocation read
 * the ground the same way.
 */
final class CropAssigner {

    private CropAssigner() {
    }

    /**
     * Whether this patch is being kept empty until the next contract has a crop.
     *
     * <p>A reserved patch otherwise takes a filler seed, but not while the next contract's
     * crop is unknown: planting then would occupy the patch Jane is about to ask for. The
     * guild visit hands in the contract and takes the next before planting (see
     * {@link StopRowWriter#adviseOnGroup}), so the hold usually lasts one conversation.
     */
    static boolean heldForTheNextContract(PatchRef patch, PlayerState state) {
        return PatchPriority.isReservedForContracts(patch)
                && RunScheduler.nextContractCropUnknown(state);
    }

    /**
     * The crop an outstanding contract needs put in this patch, or null.
     *
     * <p>The other half of {@link #heldForTheNextContract}: once Jane names a crop, that crop
     * goes into the reserved patch instead of a filler seed. Only where the crop belongs in
     * <em>this</em> patch type.
     *
     * <p>Null once the contract's crop stands there alive (diseased counts: a cure keeps the
     * crop). A dead one does not count, because the player digs it up and replants the
     * contract's crop. A living crop that is not the contract's is left to
     * {@link #assignCrops}, which drops occupied ground before asking.
     *
     * <p>Null too when the player provably has no such seed, the rule {@link #assignCrops}
     * applies to fixed-crop stops: holding the patch empty would also cost a rotation herb.
     */
    static Crop contractedInto(PatchRef patch, PlayerState state,
            FarmingInputs inputs) {
        if (!PatchPriority.isReservedForContracts(patch)
                || heldForTheNextContract(patch, state)) {
            return null;
        }
        Crop crop = inputs.getContractCrop();
        CropData data = crop == null ? null : CropData.forCrop(crop);
        FarmingWorldMap.PatchDef def = patch.resolve();
        if (data == null || def == null || def.getType() != data.getPatchType()) {
            return null;
        }
        PatchPrediction seen = inputs.patchAt(patch);
        if (seen != null && seen.getCrop() == crop && seen.getState() != CropState.DEAD) {
            return null;
        }
        return SeedSelector.definitelyOutOf(crop, state, data.getSeedsPerPatch())
                ? null : crop;
    }

    /**
     * How many of the run's good-seed seats already have a crop in them.
     *
     * <p>Once the quota of good seeds is in the ground, the rest of the run is filler. A
     * spent seat stays spent, so the allocation holds still as patches are planted.
     *
     * <p><b>Ranked over the whole run, not over what is left</b>, or the worst patch would
     * become the best of what remains and draw the player's best herb. Scored through
     * {@link PatchPriority} as {@link #assignCrops} scores its seats.
     *
     * <p>A seat is a patch the run could seed today <em>or</em> already has growing. Leaving
     * out occupied patches would free a seat the moment it was planted. Leaving out
     * unreachable ones would hold a seat nothing can fill. A ready crop is harvested and
     * replanted, so its seat is open (see {@link #occupiedByAlivingCrop}).
     *
     * <p><b>Only as still as the run is.</b> {@link PatchPriority#scoreOf} depends on player
     * state, so an unlock gained mid-run re-ranks the seats and can let the quota over-spend.
     *
     * <p>Read from {@code plans}, so a stop ruled out by a gate the player lacks holds no
     * seat. Skipped stops are kept: a patch skipped because it is growing is exactly a spent
     * seat.
     */
    static int goodSeatsSpent(List<StopPlan> plans, PlayerState state,
            FarmingInputs inputs, int quota, int[] walk) {
        List<Seat> seats = new ArrayList<>();
        Set<PatchRef> filled = new LinkedHashSet<>();
        for (int index = 0; index < plans.size(); index++) {
            StopPlan plan = plans.get(index);
            if (!plan.getStop().isPlanting() || plan.getStop().isFixedCrop()) {
                continue;
            }
            int patchIndex = 0;
            for (PatchRef ref : plan.getStop().getPatches()) {
                if (PatchPriority.isReservedForContracts(ref)
                        || heldForTheNextContract(ref, state)) {
                    patchIndex++;
                    continue;
                }
                boolean occupied = occupiedByAlivingCrop(ref, inputs);
                if (occupied) {
                    filled.add(ref);
                }
                if (occupied || plan.isVisit()) {
                    seats.add(new Seat(index, patchIndex, walk[index],
                            PatchPriority.scoreOf(ref, state), null, ref));
                }
                patchIndex++;
            }
        }
        // The comparator and walk assignCrops deals by. The seat sets differ (this one keeps
        // occupied patches, which assignCrops drops or sends to its upgrade pass), but the
        // shared seats must sort alike, or the quota counts a seat spent that the
        // allocation never offered a good seed to.
        seats.sort(BEST_HOME_FIRST);

        int spent = 0;
        // Bounded by the quota, so the caller's subtraction can never go negative.
        for (int i = 0; i < Math.min(quota, seats.size()); i++) {
            if (filled.contains(seats.get(i).patch)) {
                spent++;
            }
        }
        return spent;
    }

    /**
     * True while a patch holds a living crop, so nothing new can go in it today.
     *
     * <p>Growing and diseased both, as in {@link StopSkipRules#stillStandingIn}: a cure keeps
     * the crop, while a dead one is dug out and leaves the patch bare. Weeds, and a
     * prediction with no crop, are not occupied.
     */
    private static boolean occupiedByAlivingCrop(PatchRef ref, FarmingInputs inputs) {
        PatchPrediction seen = inputs.patchAt(ref);
        if (seen == null || seen.getCrop() == null || seen.isIdle()) {
            return false;
        }
        return seen.getState() == CropState.DISEASED || StopSkipRules.isStillGrowing(seen);
    }

    /**
     * How many of a fixed-crop stop's patches can take a seed this visit: those not holding
     * a living crop, growing or diseased, nor a bush or cactus that stays standing.
     *
     * <p>Any crop, not only the stop's own: a redberry bush growing in the whiteberry patch
     * blocks the whiteberry seed as a growing whiteberry bush would.
     *
     * <p>A crop that regrows after picking, a bush or a cactus, stays in the patch when it is
     * ready too: picking it, or checking its health, leaves the plant standing. Death clears
     * it, and so does digging it up once it is grown ({@link PatchWork#comesOut}).
     *
     * <p>The bag packs the stop's seeds and fees by this count, so it packs what the
     * planting uses.
     */
    static int openFixedPatches(RunStop stop, FarmingInputs inputs, PlayerState state) {
        int open = 0;
        for (PatchRef ref : stop.getPatches()) {
            boolean free = !occupiedByAlivingCrop(ref, inputs) && !staysAfterPicking(ref, inputs);
            if (free || PatchWork.comesOut(inputs.patchAt(ref), stop,
                    inputs.getPreferences(), state)) {
                open++;
            }
        }
        return open;
    }

    /** Whether this patch holds a living crop that regrows, which picking leaves standing. */
    private static boolean staysAfterPicking(PatchRef ref, FarmingInputs inputs) {
        PatchPrediction seen = inputs.patchAt(ref);
        return seen != null && seen.getCrop() != null && !seen.isIdle()
                && seen.getCrop().getRegrowTickRateMinutes() > 0
                && seen.getState() != CropState.DEAD;
    }

    /**
     * Whether this patch holds a crop that has not finished growing.
     *
     * <p>{@link #occupiedByAlivingCrop} without disease. An unfinished crop is not worth
     * trading away; a diseased tree dies unless cured, so felling it for a better one is a
     * reasonable trade.
     */
    private static boolean stillFillingOut(PatchRef ref, FarmingInputs inputs) {
        PatchPrediction seen = inputs.patchAt(ref);
        return seen != null && seen.getCrop() != null && !seen.isIdle()
                && StopSkipRules.isStillGrowing(seen);
    }

    /**
     * What each stop plants, one entry per patch, keyed by position in {@code plans}.
     *
     * <p>Per patch because a stop is a visit: Fossil Island's three hardwood patches take
     * three saplings, possibly of different crops.
     *
     * <p><b>One entry per patch a seed can go into</b>, so a list is shorter than its stop's
     * patches wherever one is held for the next contract or holds a living crop. Readers
     * count and tally entries; they never line them up against patch positions.
     *
     * <p><b>The better seeds go to the better patches, not the earlier ones.</b> Patches are
     * ranked by {@link PatchPriority} (which reflects diaries and other yield and disease
     * bonuses) and dealt down that ranking, both ends best seed first. Ties keep the
     * catalogue's order.
     *
     * <p>{@link Itinerary} lifts the better homes forward from the same ranking, so a seed
     * box lands correctly. This deals to patches, never to positions, so it does not depend
     * on that.
     */
    static List<List<Crop>> assignCrops(List<StopPlan> plans, SeedPlan seeds,
            PlayerState state, FarmingInputs inputs, int[] walk) {
        return assignCrops(plans, seeds, state, inputs, walk, new HashMap<>());
    }

    /**
     * The same allocation, also reporting which patch each seed landed in.
     *
     * <p>The per-stop lists cannot say which of Fossil Island's three patches a teak goes
     * into. {@code StopRowWriter.treesMakingWay} needs the patch to decide which trees to
     * fell.
     *
     * @param plantedIn <b>filled by this method</b>, one entry per seed that found a home
     */
    static List<List<Crop>> assignCrops(List<StopPlan> plans, SeedPlan seeds,
            PlayerState state, FarmingInputs inputs, int[] walk,
            Map<PatchRef, Crop> plantedIn) {
        List<List<Crop>> cropFor = new ArrayList<>(plans.size());
        // Every patch drawing on the run's seeds. Collected on the way past so the
        // fixed-crop stops are filled in the same walk.
        List<Seat> drawing = new ArrayList<>();
        // Grown trees, fruit or lumber, which clearing is a trade for. They are offered only
        // what the ground above did not take, and only as an upgrade. A living crop anywhere
        // else gets no seat.
        List<Seat> replacing = new ArrayList<>();
        // Patches an outstanding contract or a diary has spoken for, which draw no seed. Folded
        // back in below in the stop's own patch order.
        Map<Seat, Crop> claimed = new LinkedHashMap<>();
        for (int index = 0; index < plans.size(); index++) {
            StopPlan plan = plans.get(index);
            List<Crop> atThisStop = new ArrayList<>();
            cropFor.add(atThisStop);
            if (!plan.isVisit()) {
                continue;
            }
            if (plan.getStop().isFixedCrop()) {
                Crop fixed = FixedCrops.cropFor(plan.getStop(), inputs.getPreferences(), state);
                // Only where the player provably holds the seed.
                // {@link StopSkipRules#skipIfNoSeed} drops the stop only when nothing else is
                // waiting; here a harvest can keep the visit while the planting goes.
                CropData data = fixed == null ? null : CropData.forCrop(fixed);
                if (fixed == null || (data != null
                        && SeedSelector.definitelyOutOf(fixed, state, data.getSeedsPerPatch()))) {
                    continue;
                }
                // A patch holding any living crop takes no seed: one seaweed patch growing
                // beside a ready one plants one, and packs one lot of spores and one
                // protection fee.
                for (int open = openFixedPatches(plan.getStop(), inputs, state); open > 0;
                        open--) {
                    atThisStop.add(fixed);
                }
                continue;
            }
            if (!plan.getStop().isPlanting()) {
                continue;
            }
            int patchIndex = 0;
            for (PatchRef ref : plan.getStop().getPatches()) {
                // Held, not skipped: the errand still stands; only the planting waits.
                if (heldForTheNextContract(ref, state)) {
                    patchIndex++;
                    continue;
                }
                Crop standing = StopSkipRules.stillStandingIn(ref, plan.getStop(), inputs);
                // A living crop that felling is not a trade for: a lumber tree still growing
                // (clearing it loses the tree before its check) or a diseased crop (cured, not dug
                // out). Grown trees, checked or not, come through {@code standing} and go to
                // the upgrade pass.
                //
                // No seat at all, or an unfillable seat would hold its rank and starve a
                // plantable patch. This also agrees with {@link #goodSeatsSpent}, which
                // counts this patch's seed as already spent.
                //
                // Ahead of the contract's claim below: a contract cannot plant through a
                // living crop either, and the guild's errand stop never reaches
                // {@link StopSkipRules#skipIfPatchesStillFull}.
                if (standing == null && occupiedByAlivingCrop(ref, inputs)) {
                    patchIndex++;
                    continue;
                }
                // An immature tree is not traded away: felling it wastes the seed and the
                // growth, and waiting costs only the wait. A grown tree awaiting its health
                // check stays a candidate, because the row asks for the felling only after
                // the check turns it HARVESTABLE. It also covers a fruit tree stop with
                // several patches.
                if (standing != null && stillFillingOut(ref, inputs)) {
                    patchIndex++;
                    continue;
                }
                // A contract's named crop claims its patch ahead of the rotation's filler,
                // and so does a crop a diary keeps for this patch (magic at Falador).
                Crop contracted = contractedInto(ref, state, inputs);
                if (contracted == null) {
                    contracted = DiaryPlantings.claimedBy(ref, state, inputs.getPreferences());
                }
                if (contracted != null) {
                    claimed.put(new Seat(index, patchIndex, walk[index],
                            PatchPriority.scoreOf(ref, state), null, ref), contracted);
                    patchIndex++;
                    continue;
                }
                Seat seat = new Seat(index, patchIndex, walk[index],
                        PatchPriority.scoreOf(ref, state), standing, ref);
                if (standing == null) {
                    drawing.add(seat);
                } else {
                    replacing.add(seat);
                }
                patchIndex++;
            }
        }

        // The k-th patch the player stands at takes the k-th seed. See BEST_HOME_FIRST.
        drawing.sort(BEST_HOME_FIRST);
        // Where each seed landed, re-sorted below into patch order because a stop's list is
        // the order its plant lines print in.
        Map<Seat, Crop> landed = new LinkedHashMap<>();
        int dealt = 0;
        for (; dealt < drawing.size(); dealt++) {
            Crop crop = seatCrop(seeds, dealt);
            if (crop != null) {
                landed.put(drawing.get(dealt), crop);
            }
        }
        dealOutUpgrades(replacing, seeds, dealt, landed, inputs);
        landed.putAll(claimed);

        List<Seat> planted = new ArrayList<>(landed.keySet());
        planted.sort(Comparator.comparingInt((Seat seat) -> seat.planIndex)
                .thenComparingInt(seat -> seat.patchIndex));
        for (Seat seat : planted) {
            cropFor.get(seat.planIndex).add(landed.get(seat));
            plantedIn.put(seat.patch, landed.get(seat));
        }
        return cropFor;
    }

    /**
     * Spends what the ground seats left over on patches worth upgrading, worst first.
     *
     * <p>While grown trees are kept, a living fruit tree is felled only for a <em>better</em>
     * one, so a palm never makes way for a palm. Replanting them, a fruit tree is matched as
     * a lumber tree is, except a diseased one. The worst tree goes first, so one sapling
     * upgrades the pineapple rather than the papaya.
     *
     * <p>Always after the empty ground: a seed in bare soil costs only the seed, while here
     * it also costs a producing tree.
     *
     * @param from the seat number the ground ran to, so the two draws share one allocation
     *             rather than each dealing the best seed again
     * @param inputs whose preferences say whether a grown fruit tree need only be matched,
     *             as a lumber tree is, so its own kind replaces it
     */
    private static void dealOutUpgrades(List<Seat> replacing, SeedPlan seeds, int from,
            Map<Seat, Crop> landed, FarmingInputs inputs) {
        boolean replantsGrownTrees = inputs.getPreferences().replantsGrownTrees();
        // Worst standing crop first; ties by the better home, as the ground seats rank.
        replacing.sort(Comparator.comparingInt((Seat seat) -> tierOf(seat.standing, seat.patch))
                .reversed()
                .thenComparing(Comparator.comparingInt((Seat seat) -> seat.score).reversed()));
        // What the ground left over, as a pool rather than in order, bounded by the seats.
        List<Crop> spare = new ArrayList<>();
        for (int seat = from; seat < from + replacing.size(); seat++) {
            Crop crop = seatCrop(seeds, seat);
            if (crop != null) {
                spare.add(crop);
            }
        }

        for (Seat candidate : replacing) {
            int standing = tierOf(candidate.standing, candidate.patch);
            // A tree that goes on giving must be beaten outright. A spent lumber tree (check
            // banked) need only be matched: replanting a yew with a yew is the ordinary tree
            // run. A downgrade is refused even there, on purpose: a yew patch is worth more
            // empty than holding an oak, and seedRanOutAt then drops the stop.
            // Replanting grown trees, a fruit tree is matched too: its fruit is worth less
            // than a fresh tree's health check. Not a diseased one, which a cure keeps.
            boolean kept = keepsGiving(candidate.standing)
                    && !(replantsGrownTrees && isFruitTree(candidate.patch)
                            && !diseased(candidate.patch, inputs));
            int floor = kept ? standing - 1 : standing;
            // The <em>worst</em> sapling that still improves this patch, not the best: a
            // dragonfruit spent on a pineapple leaves nothing to beat the palm, while the
            // palm sapling there and the dragonfruit at the palm upgrades both.
            int pick = -1;
            for (int i = 0; i < spare.size(); i++) {
                int tier = tierOf(spare.get(i), candidate.patch);
                if (tier > floor) {
                    continue;
                }
                if (pick < 0 || tier > tierOf(spare.get(pick), candidate.patch)) {
                    pick = i;
                }
            }
            if (pick < 0) {
                // The tree stays. Seats run worst-standing-first, so later ones are harder
                // still to improve on.
                continue;
            }
            landed.put(candidate, spare.remove(pick));
        }
    }

    /**
     * Whether this crop is still worth something to its owner where it stands.
     *
     * <p>A fruit tree bears again and again, so felling it is a loss, unless the player
     * replants grown trees for the health check. A lumber tree pays out once, at its health
     * check. Regrow tells them apart, as in {@link RunStop.StopAction#defaultsFor}.
     */
    private static boolean keepsGiving(Crop crop) {
        return crop != null && crop.getRegrowTickRateMinutes() > 0;
    }

    private static boolean diseased(PatchRef patch, FarmingInputs inputs) {
        PatchPrediction seen = inputs.patchAt(patch);
        return seen != null && seen.getState() == CropState.DISEASED;
    }

    private static boolean isFruitTree(PatchRef patch) {
        FarmingWorldMap.PatchDef def = patch.resolve();
        return def != null && def.getType() == PatchType.FRUIT_TREE;
    }

    /**
     * Where a crop sits in its family's ranking, best first — so smaller is better.
     *
     * <p>{@link CropData#plantableIn} is ordered by Farming level descending, which for the
     * tree families is also the order of worth. An unknown crop sorts to the bottom, so it
     * is replaced rather than protected.
     */
    private static int tierOf(Crop crop, PatchRef patch) {
        FarmingWorldMap.PatchDef def = patch.resolve();
        if (crop == null || def == null) {
            return Integer.MAX_VALUE;
        }
        int index = CropData.plantableIn(def.getType()).indexOf(crop);
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    /**
     * One patch a seed could go in: where it sits, what it is worth, and what is in the way.
     *
     * <p>{@code standing} is null for bare ground. {@link #goodSeatsSpent} builds seats only
     * to count the quota, reading {@code patch} and the sort keys.
     */
    private static final class Seat {
        private final int planIndex;
        /** Position within the stop's own patch list, which is the order readers expect. */
        private final int patchIndex;
        /** How far along the walk this patch is reached. See {@link #walkPositions}. */
        private final int walkPosition;
        private final int score;
        /** What is growing here that would have to go, or null for bare ground. */
        private final Crop standing;
        private final PatchRef patch;

        Seat(int planIndex, int patchIndex, int walkPosition, int score, Crop standing,
                PatchRef patch) {
            this.planIndex = planIndex;
            this.patchIndex = patchIndex;
            this.walkPosition = walkPosition;
            this.score = score;
            this.standing = standing;
            this.patch = patch;
        }
    }

    /**
     * The order seats are dealt down: best home first, and among equal homes the one the
     * player reaches first.
     *
     * <p><b>A tie is the common case:</b> every reason one herb patch beats another is a
     * diary or a quest, so an account with none has every patch scoring alike. The
     * tie-break must then be walk order, so the k-th patch reached takes the k-th seed out
     * of the seed box.
     *
     * <p>Walk position, not plan-list position: a trip walks its book's {@link Arrivals}
     * seats across every family's plans, so where a plan sits in the list says nothing about
     * when the player gets there. {@link #walkPositions} gives the same order
     * {@link Itinerary#bestHomesFirst} is handed its visits in. Plan and patch index make the
     * order total.
     *
     * <p><b>An optional stop can draw a good seed, and that is intended.</b> Ortus Farm sits
     * second on the circuit, so it takes the second seed out of the box.
     */
    private static final Comparator<Seat> BEST_HOME_FIRST =
            Comparator.comparingInt((Seat seat) -> seat.score).reversed()
                    .thenComparingInt(seat -> seat.walkPosition)
                    .thenComparingInt(seat -> seat.planIndex)
                    .thenComparingInt(seat -> seat.patchIndex);

    /**
     * How far along the walk each stop is reached, indexed by position in {@code plans}.
     *
     * <p>A trip walks the circuit; a single run walks the catalogue's order with woven-in
     * extras beside their stop. The allocation must follow whichever order is drawn.
     * {@code bestHomesFirst} breaks ties on the order it is handed, so it and this must be
     * handed the same one.
     *
     * @param tripBook the book of the merged trip the run is walked on, whose seats the trip
     *     follows, or null for a run walked on its own
     */
    static int[] walkPositions(List<StopPlan> plans, Spellbook tripBook) {
        int[] position = new int[plans.size()];
        if (tripBook != null) {
            for (int i = 0; i < plans.size(); i++) {
                position[i] = Arrivals.seat(tripBook, plans.get(i).getStop().getArrival());
            }
            return position;
        }
        // The drawn order, which lifts woven-in extras beside their stop.
        List<Integer> order = TripOrder.inTripOrder(plans);
        for (int seat = 0; seat < order.size(); seat++) {
            position[order.get(seat)] = seat;
        }
        return position;
    }

    /**
     * The seed for the nth-best patch, or null where the stock ran out.
     *
     * <p>The good end keeps the seats it was sized for even when short; filler never slides
     * up into them.
     */
    private static Crop seatCrop(SeedPlan seeds, int seat) {
        return seat < seeds.getGood().getWanted()
                ? seeds.getGood().at(seat)
                : seeds.getBad().at(seat - seeds.getGood().getWanted());
    }
}
