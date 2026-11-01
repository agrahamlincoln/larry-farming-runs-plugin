package com.larryfarming.farming.engine;

import com.larryfarming.engine.*;
import com.larryfarming.engine.farming.*;
import com.larryfarming.farming.catalogue.CropData;
import com.larryfarming.farming.detection.CapabilityResolver;
import com.larryfarming.farming.detection.CapabilityState;
import com.larryfarming.farming.model.*;
import com.larryfarming.model.PlayerState;
import com.larryfarming.snapshot.FarmingCarryItem;
import java.util.*;
import net.runelite.api.gameval.ItemID;

/**
 * Reads a planned trip and a player, and says what each arrival wants out of the bag.
 *
 * <p>{@link InventorySim} is arithmetic that knows nothing about the catalogue or the
 * player. Everything that looks up a crop, reads a patch or checks what the player owns
 * lives here instead.
 *
 * <p>The three rules that decide most of the answer: seeds stack so a crop costs one
 * slot however many patches take it, saplings do not stack so they cost one each, and a
 * farmer accepts a noted payment so protection costs one slot per kind of patch paid for.
 */
final class InventoryDemands {

    private InventoryDemands() {
    }

    /** Both bucket ids, since the resolver treats either as owning one. */
    private static final List<Integer> BOTTOMLESS_COMPOST_BUCKET_IDS = List.of(
            ItemID.BOTTOMLESS_COMPOST_BUCKET, ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED);

    /**
     * What one arrival wants, for every visit the trip is actually making.
     *
     * <p>One demand per visit, in the order given, so the indices in an
     * {@link InventorySim.Leg} point straight into the caller's list.
     *
     * <p>Relies on three {@link Itinerary.Trip} invariants without guarding them: a visit is
     * never empty ({@code groupByArrival} opens each with its first index), every stop in a
     * visit shares one arrival, and {@code cropFor} is as long as {@code plans}
     * ({@code Itinerary.Planned} enforces it).
     *
     * @param visits   the stop indices at each arrival, in circuit order, filtered to the
     *                 arrivals the player is actually reaching. An unreached arrival takes
     *                 nothing from the bag, but left in the list it could be chosen as the
     *                 bank stop
     * @param plans    every planned stop, indexed by the numbers in {@code visits}
     * @param cropFor  what each stop is planting, index-aligned with {@code plans}
     * @param state    the player, whose owned payments decide which farmers are paid
     * @param resolver decides the travel items, as it does for the packing list
     */
    static List<InventorySim.Demand> forTrip(List<List<Integer>> visits, List<StopPlan> plans,
            List<List<Crop>> cropFor, FarmingInputs inputs, PlayerState state, CapabilityResolver resolver) {
        List<InventorySim.Demand> demands = new ArrayList<>(visits.size());
        boolean pouched = state != null && ownsAny(state, RunePouch.POUCH_ITEM_IDS);
        for (List<Integer> visit : visits) {
            List<StopPlan> here = new ArrayList<>(visit.size());
            List<List<Crop>> plantedHere = new ArrayList<>(visit.size());
            for (int index : visit) {
                here.add(plans.get(index));
                plantedHere.add(cropFor.get(index));
            }
            // The packing list's own lines, so the bag charges what it is told to carry.
            // Kept to the end like any tool, coins included: the stack may outlast the fee,
            // and charging a slot too many costs at most a spare bank stop.
            Set<Integer> tools = CarryPlanner.treeToolsFor(here, plantedHere, state, inputs);
            Set<Integer> travel = new HashSet<>();
            Set<Integer> spent = new HashSet<>();
            // With no player read there is nothing to price a route's items against.
            List<FarmingCarryItem> travelLines = state == null ? List.of() : CarryPlanner.travelFor(here, state, resolver);
            for (FarmingCarryItem line : travelLines) {
                if (!CarryPlanner.takesAbagSlot(line, pouched)) {
                    continue;
                }
                travel.add(line.getItemId());
                if (CarryPlanner.spentOnTheWay(line)) {
                    spent.add(line.getItemId());
                }
            }
            Arrival at = null;
            Set<Crop> seeds = EnumSet.noneOf(Crop.class);
            Set<Crop> produce = EnumSet.noneOf(Crop.class);
            Set<PatchType> protections = EnumSet.noneOf(PatchType.class);
            int saplings = 0;
            int bankTiles = TravelOption.UNMEASURED;
            boolean bankUpstairs = false;
            Bank bank = null;
            for (int index : visit) {
                StopPlan plan = plans.get(index);
                if (!plan.isVisit()) {
                    // A skipped stop takes nothing out of the bag. If every stop is skipped,
                    // the arrival falls back to the visit's first stop below.
                    continue;
                }
                RunStop stop = plan.getStop();
                at = stop.getArrival();
                saplings += sort(cropFor.get(index), seeds);
                collectProduce(stop, inputs, produce);
                collectProtection(stop, cropFor.get(index).size(), state, protections);
                // The cheapest bank the player passes while here. A visit works every patch
                // at its arrival, so a bank on the way to any one of them serves the visit.
                // Unmeasured rungs are skipped, which can only overstate the cost.
                // Compared on effective cost, so an upstairs bank pays for its staircase.
                TravelOption route = plan.getRoute();
                if (route != null && route.hasBankFigure() && (bankTiles == TravelOption.UNMEASURED || effectiveCost(route)
                                        < effectiveCost(bankTiles, bankUpstairs))) {
                    bankTiles = route.getBankDetour();
                    bankUpstairs = route.isBankUpstairs();
                    // Always from the same rung as the figure: different rungs at one visit
                    // can reach different bank buildings.
                    bank = route.getBank();
                }
            }
            if (at == null) {
                at = plans.get(visit.get(0)).getStop().getArrival();
            }
            demands.add(new InventorySim.Demand(at, seeds, saplings, produce, protections,
                    bankTiles, bankUpstairs, bank, tools, travel, spent));
        }
        return demands;
    }

    /**
     * What a bank stop costs to reach, in tiles, counting a floor change. Uses the same
     * arithmetic as {@link Arrival.BankProximity#of}, so the bank chosen is judged on the
     * number the player is told.
     */
    private static int effectiveCost(TravelOption route) {
        return effectiveCost(route.getBankDetour(), route.isBankUpstairs());
    }

    private static int effectiveCost(int tiles, boolean upstairs) {
        return upstairs ? tiles + Arrival.BankProximity.FLOOR_CHANGE_TILES : tiles;
    }

    /**
     * Splits what is being planted into stacking seeds (added to {@code seeds}) and
     * non-stacking saplings, and returns the sapling count. A sapling is a plant pot, and
     * two pots never share a slot.
     */
    private static int sort(List<Crop> planting, Set<Crop> seeds) {
        int saplings = 0;
        for (Crop crop : planting) {
            CropData data = CropData.forCrop(crop);
            if (data != null && data.isSaplingPlanted()) {
                saplings++;
            } else {
                seeds.add(crop);
            }
        }
        return saplings;
    }

    /**
     * What is picked here, one entry per kind.
     *
     * <p>Asked of {@link PatchWork}, so the bag and the walkthrough row agree about what is
     * being harvested.
     *
     * <p>Logs must not be added. The tool leprechaun will not note them, so logs go in a
     * log basket (a fixture) or on the floor, and never take a produce slot.
     */
    private static void collectProduce(RunStop stop, FarmingInputs inputs, Set<Crop> produce) {
        for (PatchRef ref : stop.getPatches()) {
            PatchPrediction seen = inputs.patchAt(ref);
            PatchWork.Act act = PatchWork.at(seen, stop, inputs.getNowEpochSec());
            // A check where the stop also picks fills the plant at once: a bush, cactus or
            // fruit tree bears its full load the moment it is checked.
            boolean picked = act == PatchWork.Act.HARVEST || (act == PatchWork.Act.CHECK
                            && stop.getActions().contains(RunStop.StopAction.HARVEST));
            if (!picked) {
                continue;
            }
            if (seen.getCrop() != null && seen.getCrop().isProduce()) {
                produce.add(seen.getCrop());
            }
        }
    }

    /**
     * Which kinds of patch are paid for here.
     *
     * <p>By patch type rather than by patch, because a farmer takes a noted payment: eight
     * allotments across the trip is one slot of noted produce, not eight.
     *
     * <p>Only where something is planted and the player owns a fee for it (see
     * {@link Protection}), since the payment is not worth gathering for the trip.
     *
     * @param planting how many of the stop's patches are planted this visit
     */
    private static void collectProtection(RunStop stop, int planting, PlayerState state, Set<PatchType> protections) {
        if (Protection.paymentAt(stop, planting, state) == null) {
            return;
        }
        for (PatchRef ref : stop.getPatches()) {
            PatchType type = ref.resolve() == null ? null : ref.resolve().getType();
            if (type != null) {
                protections.add(type);
            }
        }
    }

    /**
     * What this player carries the whole way.
     *
     * <p>The spade is always counted: no run happens without one.
     *
     * <p><b>Owned, not held.</b> The trip starts at a bank, so an item in the bank will be
     * carried. {@link CarryPlanner} draws the same line: {@code countCarried} decides whether
     * a withdrawal is needed, {@code countOwned} decides whether the item comes along.
     *
     * <p>Over-counting costs an unneeded bank stop; under-counting leaves the player unable
     * to plant at the last patch. Before the bank has been seen every count reads zero, so
     * fixtures appear once the player opens a bank. A basket in the bag beside a cape whose
     * teleport the route uses is charged as well as the cape, though the player wears one
     * of the two: the same safe over-count as any worn travel item.
     *
     * <p>The bucket and rune pouch cost a slot <em>because</em> the player owns them: each
     * replaces several slots (compost, loose runes) on a run that uses it. The log basket
     * costs one only on a trip that cuts logs, and not when worn in the cape slot.
     *
     * <p>The axe and the travel items are not fixtures. {@link #forTrip} puts them on the
     * arrivals that use them, so each is charged only from the leg that first needs it.
     */
    static Set<InventorySim.Fixture> fixturesFor(PlayerState state, CapabilityResolver resolver, List<StopPlan> plans, FarmingInputs inputs,
            boolean basketInTheBag) {
        Set<InventorySim.Fixture> carried = EnumSet.of(InventorySim.Fixture.SPADE);
        // Barbarian Training allows bare-handed planting. Anything short of a definite HAVE
        // carries the dibber: an unknown costs a slot here, never a skipped patch.
        if (resolver.resolve(Capability.BARE_HAND_PLANTING, state).getState() != CapabilityState.HAVE) {
            carried.add(InventorySim.Fixture.SEED_DIBBER);
        }
        if (basketInTheBag) {
            carried.add(InventorySim.Fixture.LOG_BASKET);
        }
        // CarryPlanner.packsCompost decides the bucket for the packing list too.
        boolean fertile = FertileSoil.inUse((inputs != null ? inputs : FarmingInputs.none()).getPreferences(), state, resolver.getBook());
        if (ownsAny(state, BOTTOMLESS_COMPOST_BUCKET_IDS) && CarryPlanner.packsCompost(plans, fertile)) {
            carried.add(InventorySim.Fixture.BOTTOMLESS_BUCKET);
        }
        if (ownsAny(state, RunePouch.POUCH_ITEM_IDS)) {
            carried.add(InventorySim.Fixture.RUNE_POUCH);
        }
        if (ownsAny(state, SeedBox.BOX_ITEM_IDS)) {
            carried.add(InventorySim.Fixture.SEED_BOX);
        }
        // The ash is an errand, not an owned saving. It is charged on the same condition
        // CarryPlanner uses to list it.
        if (fertile && FertileSoil.treatsAny(plans)) {
            carried.add(InventorySim.Fixture.VOLCANIC_ASH);
        }
        // No underwater breathing gear on purpose: it only works worn, so it costs
        // equipment slots, not inventory. See CarryPlanner.addDivingGear.
        return carried;
    }

    /**
     * Whether the empty pot stays in the bag after a sapling goes in the ground.
     *
     * <p>Always true. Barbarian Training destroys the pot, but the player can turn that
     * setting off and nothing reports it. {@link Capability#BARE_HAND_PLANTING} says the
     * training is done, not that the setting is on. Assuming the pots are gone would plan
     * legs that overflow in the field; assuming they stay costs at most a spare bank stop.
     */
    static final boolean POTS_ARE_KEPT = true;

    private static boolean ownsAny(PlayerState state, Collection<Integer> itemIds) {
        return itemIds.stream().anyMatch(id -> ItemCounts.countOwned(state, id, null) > 0);
    }
}
