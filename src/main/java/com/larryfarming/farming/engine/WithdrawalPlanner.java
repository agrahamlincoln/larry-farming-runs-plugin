package com.larryfarming.farming.engine;

import com.larryfarming.engine.SeedBox;
import com.larryfarming.engine.farming.Crop;
import com.larryfarming.farming.catalogue.CropData;
import com.larryfarming.farming.detection.CapabilityResolver;
import com.larryfarming.farming.model.Arrival;
import com.larryfarming.model.ItemStack;
import com.larryfarming.model.PlayerState;
import com.larryfarming.snapshot.FarmingCarryItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * What to take out of the bank at each stop, for the stretch to the next one and no further.
 *
 * <p>The packing list answers "what does this trip need" once, before the player leaves, so it
 * fills the bag with things for stops far ahead. Every bank the trip passes is a chance to fetch
 * the next thing instead.
 *
 * <p><b>Every bank row on the way is a chance to fetch</b>, whether the bag simulation
 * prescribed it or only offered it. A trip whose bag never fills (most herb runs) has no prescribed bank after
 * the first, yet still walks past banks. An offered bank counts only while the panel draws
 * it, and only when it is on the way or a short detour.
 *
 * <p>Each stretch between bank rows is planned as its own little trip, and the answers are
 * walked in order against a running picture of the bag. An item wanted early and again later
 * is withdrawn once if it is a tool and twice if it is a seed, because a seed goes in the
 * ground.
 *
 * <p><b>Nothing is assumed to be deposited.</b> The picture only grows by withdrawals and
 * shrinks by plantings. These are recommendations: a player who skips a bank or deposits
 * something gets a fresh answer from the next snapshot, built from the bag they actually have.
 */
public final class WithdrawalPlanner {

    private WithdrawalPlanner() {
    }

    /**
     * What each bank row on the walkthrough should say to take out.
     *
     * <p>Three lookups because the rows are placed by three rules, each with its own key: the
     * starting row has none, a prescribed bank knows which leg it opens, and an offered bank
     * knows which arrival it sits under.
     */
    public static final class Withdrawals {

        private static final Withdrawals NONE =
                new Withdrawals(List.of(), Map.of(), Map.of(), List.of(), List.of(), false);

        private final List<FarmingCarryItem> beforeSettingOff;
        private final Map<Integer, List<FarmingCarryItem>> atLeg;
        private final Map<Integer, List<FarmingCarryItem>> afterArrival;
        private final List<FarmingCarryItem> firstStretch;
        private final List<FarmingCarryItem> deposits;
        private final boolean depositsUrgent;

        /** Package-private so rows can be built without planning a trip. */
        Withdrawals(List<FarmingCarryItem> beforeSettingOff,
                Map<Integer, List<FarmingCarryItem>> atLeg,
                Map<Integer, List<FarmingCarryItem>> afterArrival) {
            this(beforeSettingOff, atLeg, afterArrival, List.of(), List.of(), false);
        }

        /** Package-private so a row with deposits can be built without planning a trip. */
        Withdrawals(List<FarmingCarryItem> beforeSettingOff,
                Map<Integer, List<FarmingCarryItem>> atLeg,
                Map<Integer, List<FarmingCarryItem>> afterArrival,
                List<FarmingCarryItem> firstStretch, List<FarmingCarryItem> deposits,
                boolean depositsUrgent) {
            this.beforeSettingOff = beforeSettingOff;
            this.atLeg = atLeg;
            this.afterArrival = afterArrival;
            this.firstStretch = firstStretch;
            this.deposits = deposits;
            this.depositsUrgent = depositsUrgent;
        }

        /**
         * What to put back next time the player banks, for the starting row, which partway
         * round stands for that bank. Empty before setting off: nothing from this trip is in
         * the bag yet.
         */
        public List<FarmingCarryItem> getDeposits() {
            return deposits;
        }

        /** Whether the bag lacks room without those deposits. */
        public boolean isDepositsUrgent() {
            return depositsUrgent;
        }

        /**
         * Everything the first stretch wants in the bag, carried or not, as packing-list
         * lines: the "now" half of the packing list. Boxed seeds count at the whole trip's
         * quantity, since they all come out up front. Empty when no later bank splits the
         * trip, where the whole packing list is the first stretch, and once underway.
         */
        public List<FarmingCarryItem> getFirstStretch() {
            return firstStretch;
        }

        /** Nothing anywhere, for a caller that has not worked any of this out. */
        public static Withdrawals none() {
            return NONE;
        }

        /** What to set off carrying, or empty where the packing list already says it. */
        public List<FarmingCarryItem> getBeforeSettingOff() {
            return beforeSettingOff;
        }

        /** What to take out at the bank that opens this leg. */
        public List<FarmingCarryItem> atLeg(int leg) {
            return atLeg.getOrDefault(leg, List.of());
        }

        /** What to take out at the bank offered under this arrival. */
        public List<FarmingCarryItem> afterArrival(int arrival) {
            return afterArrival.getOrDefault(arrival, List.of());
        }

    }

    /**
     * Every bank row's list, worked out in the order the player walks them.
     *
     * @param made  the arrivals the bag was planned over, each a list of positions in
     *              {@code plans} — the same list handed to {@code InventoryDemands.forTrip},
     *              since a leg's ends and an offer's index are positions in it
     * @param seeds every run's allocation, read only where {@code cropFor} cannot answer
     * @param underway whether the player is partway round. The starting chance then stands
     *     for the next bank they reach rather than the one they pack at, so it keeps its
     *     list even with no bank after it
     */
    static Withdrawals forBankStops(InventorySim.Plan bag, List<List<Integer>> made,
            List<StopPlan> plans, List<List<Crop>> cropFor, List<SeedPlan> seeds,
            PlayerState state, CapabilityResolver resolver, FarmingInputs inputs,
            boolean underway) {
        List<Chance> chances = bankStopsIn(bag, !inputs.hidesOfferedBanks());
        // With only the starting bank, its list would repeat the packing list. With any later
        // bank, the starting row is drawn even if the later banks want nothing. Partway
        // round, the bag left the bank long ago, and the starting row alone says what the
        // rest of the trip is short of.
        if (chances.size() < 2 && !underway) {
            return Withdrawals.none();
        }
        // The bag as the player fills it, carried across the whole walk so a tool is asked
        // for once.
        Map<Integer, Integer> bagHolds = new HashMap<>();
        // Boxed seeds are recorded in bagHolds before the walk, so no stretch asks for them.
        List<FarmingCarryItem> boxedLines = new ArrayList<>();
        List<FarmingCarryItem> boxed = seedsForTheWholeTripUpFront(made, plans, cropFor,
                seeds, state, resolver, inputs, bagHolds, boxedLines);
        List<FarmingCarryItem> start = List.of();
        List<FarmingCarryItem> firstStretch = List.of();
        Map<Integer, List<FarmingCarryItem>> atLeg = new LinkedHashMap<>();
        Map<Integer, List<FarmingCarryItem>> afterArrival = new LinkedHashMap<>();
        for (int index = 0; index < chances.size(); index++) {
            Chance chance = chances.get(index);
            // Up to the next chance to fetch, which is not always the next leg: a leg with no
            // bank adds no chance, so its arrivals are packed for at the last bank before it.
            Chance next = index + 1 < chances.size() ? chances.get(index + 1) : null;
            int lastArrival = next != null ? next.firstArrival - 1 : made.size() - 1;
            // A prescribed bank stands at the arrival that opens its leg, so the player
            // reaches it carrying the way there from this stretch.
            List<FarmingCarryItem> needs = needsOf(chance.firstArrival, lastArrival,
                    chance.leg >= 0, made, plans, cropFor, seeds, state, resolver, inputs);
            if (next != null && next.leg >= 0) {
                needs = withTravelOn(needs, CarryPlanner.travelFor(
                        stopsAt(next.firstArrival, made, plans), state, resolver));
            }
            List<FarmingCarryItem> take = shortfallOn(needs, bagHolds);
            boolean starting = chance.leg < 0 && chance.arrival < 0;
            // Underway the starting chance stands for the next bank, not the first stretch.
            if (starting && !underway) {
                firstStretch = withBoxedSeeds(boxedLines, needs);
            }
            if (starting && !boxed.isEmpty()) {
                // The trip's seeds lead the starting row, above the routine tools.
                List<FarmingCarryItem> both = new ArrayList<>(boxed);
                both.addAll(take);
                take = both;
            }
            if (take.isEmpty()) {
                continue;
            }
            if (starting) {
                start = take;
            } else if (chance.leg >= 0) {
                atLeg.put(chance.leg, take);
            } else {
                afterArrival.put(chance.arrival, take);
            }
        }
        List<FarmingCarryItem> deposits = List.of();
        boolean urgent = false;
        if (underway) {
            // Which bank comes next is not known, so the whole remaining trip counts as after
            // it: an item a stop before that bank uses stays too, the safe way.
            deposits = DepositPlanner.atNextBank(state, inputs.getPackedThisTrip(),
                    needsOf(0, made.size() - 1, false, made, plans, cropFor, seeds, state,
                            resolver, inputs),
                    chances.size() < 2);
            urgent = !deposits.isEmpty() && DepositPlanner.bagWouldFill(state, start);
        }
        return new Withdrawals(start, atLeg, afterArrival, firstStretch, deposits, urgent);
    }

    /**
     * The first stretch's lines with each boxed seed at the whole trip's count, in place of
     * the stretch's own line for it.
     */
    private static List<FarmingCarryItem> withBoxedSeeds(List<FarmingCarryItem> boxedLines,
            List<FarmingCarryItem> needs) {
        Set<Integer> boxedIds = new HashSet<>();
        for (FarmingCarryItem line : boxedLines) {
            boxedIds.add(line.getItemId());
        }
        List<FarmingCarryItem> lines = new ArrayList<>(boxedLines);
        for (FarmingCarryItem line : needs) {
            if (!boxedIds.contains(line.getItemId())) {
                lines.add(line);
            }
        }
        return lines;
    }

    /** The seed box holds this many seed types in one inventory slot. */
    private static final int SEED_TYPES_A_BOX_HOLDS = 6;

    /**
     * The trip's seeds, taken in one go at the first bank, when a seed box makes that free.
     *
     * <p>Spreading withdrawals across banks saves bag slots. A seed box holds six types in
     * one slot the player already carries, so for seeds the spread saves nothing and costs a
     * bank visit per stop. Saplings do not go in the box; {@link CropData#boxesAsAseed} draws
     * that line, so tree runs keep the per-stretch schedule.
     *
     * <p>The box holds six kinds, counted over what the trip plants plus the types already
     * in it. Past that, {@link #kindsToBox} picks which kinds ride in the box, and the rest
     * keep the per-stretch schedule. Box contents count only when
     * {@link SeedBox#contentsCarried} says the tally was read; being wrong costs one
     * inventory slot, not a lost seed.
     *
     * <p>Availability stays as {@code CarryPlanner} judged it, including
     * {@code IN_SEED_BOX}, for the reason {@link #shortfallOn} gives.
     *
     * @param bagHolds <b>mutated</b>: each boxed seed is recorded at the whole trip's
     *     count, so no later stretch asks for it again
     * @param boxedLines <b>mutated</b>: each boxed kind's packing-list line at the whole
     *     trip's count, for the first stretch's half of the packing list
     * @return the lines to withdraw, empty when the box needs nothing taken out.
     *     {@code bagHolds} gets an entry for every boxed kind either way
     */
    private static List<FarmingCarryItem> seedsForTheWholeTripUpFront(List<List<Integer>> made,
            List<StopPlan> plans, List<List<Crop>> cropFor, List<SeedPlan> seeds,
            PlayerState state, CapabilityResolver resolver, FarmingInputs inputs,
            Map<Integer, Integer> bagHolds, List<FarmingCarryItem> boxedLines) {
        if (!SeedBox.carried(state)) {
            return List.of();
        }
        List<FarmingCarryItem> wholeTrip = needsOf(0, made.size() - 1, false, made, plans,
                cropFor, seeds, state, resolver, inputs);

        List<FarmingCarryItem> boxable = new ArrayList<>();
        Set<Integer> types = new LinkedHashSet<>();
        for (FarmingCarryItem line : wholeTrip) {
            if (line.getKind() != FarmingCarryItem.Kind.SEED
                    || !CropData.boxesAsAseed(line.getItemId())
                    // Skipped as in shortfallOn: nothing to fetch for either.
                    || line.getAvailability() == FarmingCarryItem.Availability.AT_LEPRECHAUN
                    || line.getAvailability() == FarmingCarryItem.Availability.MISSING) {
                continue;
            }
            boxable.add(line);
            types.add(line.getItemId());
        }
        if (boxable.isEmpty()) {
            return List.of();
        }
        Set<Integer> held = new LinkedHashSet<>();
        if (SeedBox.contentsCarried(state)) {
            for (ItemStack stack : state.getSeedBox()) {
                if (CropData.boxesAsAseed(stack.getItemId())) {
                    held.add(stack.getItemId());
                }
            }
        }
        Set<Integer> boxed = kindsToBox(types, held, made, plans, cropFor, seeds, state,
                resolver, inputs);

        List<FarmingCarryItem> withdraw = new ArrayList<>();
        for (FarmingCarryItem line : boxable) {
            if (!boxed.contains(line.getItemId())) {
                // Left to the per-stretch schedule, like any loose seed.
                continue;
            }
            boxedLines.add(line);
            int wants = line.getQuantity();
            int has = line.getCarriedCount();
            if (has < wants) {
                withdraw.add(forShortfall(line, wants - has));
            }
            // Nothing is deposited, so the bag holds the larger of the two.
            bagHolds.put(line.getItemId(), Math.max(has, wants));
        }
        return withdraw;
    }

    /**
     * Which of the trip's seed kinds ride in the box: all of them while they fit, and past
     * its room, a choice.
     *
     * <p>Kinds already in the box stay there. The rest of its room goes to the kinds the trip
     * plants latest: a loose kind for an early patch comes out at the first bank and is in
     * the ground soon after, while a boxed late kind spares every later bank a withdrawal.
     * Ties keep the trip's own order. The last planting stands in for what a loose kind
     * costs, which is the number of later banks that fetch it; two kinds with no bank
     * between their last patches cost the same, and the earlier still loses.
     *
     * @param types the trip's boxable kinds, in the order the packing list names them
     * @param held  the boxable kinds the box was read to hold; each takes room whether or
     *     not the trip plants it
     */
    private static Set<Integer> kindsToBox(Set<Integer> types, Set<Integer> held,
            List<List<Integer>> made, List<StopPlan> plans, List<List<Crop>> cropFor,
            List<SeedPlan> seeds, PlayerState state, CapabilityResolver resolver,
            FarmingInputs inputs) {
        Set<Integer> boxed = new LinkedHashSet<>();
        List<Integer> loose = new ArrayList<>();
        for (int type : types) {
            if (held.contains(type)) {
                boxed.add(type);
            } else {
                loose.add(type);
            }
        }
        int room = SEED_TYPES_A_BOX_HOLDS - held.size();
        if (loose.size() <= room) {
            boxed.addAll(loose);
            return boxed;
        }
        if (room <= 0) {
            return boxed;
        }
        // Read per arrival only on this path: a trip within the box's room never needs it.
        Map<Integer, Integer> lastPlanted = new HashMap<>();
        for (int arrival = 0; arrival < made.size(); arrival++) {
            for (FarmingCarryItem line : needsOf(arrival, arrival, false, made, plans,
                    cropFor, seeds, state, resolver, inputs)) {
                if (line.getKind() == FarmingCarryItem.Kind.SEED) {
                    lastPlanted.put(line.getItemId(), arrival);
                }
            }
        }
        // Stable, so equal arrivals keep the trip's order.
        loose.sort((a, b) -> Integer.compare(lastPlanted.getOrDefault(b, -1),
                lastPlanted.getOrDefault(a, -1)));
        boxed.addAll(loose.subList(0, room));
        return boxed;
    }

    /**
     * Somewhere the player can open a bank, and the first arrival it packs for. Prescribed
     * and offered banks share this one type so the walk sees them in one sequence.
     */
    private static final class Chance {

        private final int firstArrival;
        private final int leg;
        private final int arrival;

        private Chance(int firstArrival, int leg, int arrival) {
            this.firstArrival = firstArrival;
            this.leg = leg;
            this.arrival = arrival;
        }
    }

    /**
     * Every bank row the walkthrough draws that is worth fetching at, in the order it draws
     * them: the prescribed banks, and the offered ones unless a long detour.
     *
     * <p>A prescribed bank is spliced <em>before</em> the arrival whose leg it opens, and an
     * offered one <em>after</em> the arrival it sits at. So an offer at arrival five and a
     * prescribed stop opening at arrival six both begin packing at six, offer first. The offer
     * then covers an empty stretch and says nothing, and the prescribed bank carries the list.
     *
     * @param offered whether the walkthrough draws the banks the route only passes. A row
     *     the player never sees is no chance to fetch, so its stretch joins the one before.
     *     A long-detour offer is skipped either way
     */
    private static List<Chance> bankStopsIn(InventorySim.Plan bag, boolean offered) {
        List<Chance> chances = new ArrayList<>();
        for (int index = 0; index < bag.getLegs().size(); index++) {
            InventorySim.Leg leg = bag.getLegs().get(index);
            if (index == 0) {
                // The trip starts at a bank whatever else it does — the player packs there.
                chances.add(new Chance(0, -1, -1));
            } else if (leg.getBankAt() != null) {
                chances.add(new Chance(leg.getFrom(), index, -1));
            }
            if (!offered) {
                continue;
            }
            for (InventorySim.Opportunity passed : leg.getPassableBanks()) {
                // A long detour is no place to send the player for something they could
                // have packed: its items come out of the bank before it instead. The row
                // still stands as an offer.
                if (passed.getCost() == Arrival.BankProximity.FAR) {
                    continue;
                }
                chances.add(new Chance(passed.getIndex() + 1, -1, passed.getIndex()));
            }
        }
        // By where each starts packing, offers ahead of prescribed stops on a tie. The sort
        // is stable, so everything else keeps the sim's walk order.
        Collections.sort(chances, (a, b) -> a.firstArrival != b.firstArrival
                ? Integer.compare(a.firstArrival, b.firstArrival)
                : Integer.compare(b.arrival, a.arrival));
        return chances;
    }

    /**
     * The packing list for one stretch alone, built by handing the planner only its stops.
     *
     * <p>Slicing works because the planner derives its seed tallies from the stops it is given
     * ({@code SeedPlan.goingInTheGround}), and reads the run-wide allocation only where nothing
     * says what is going in the ground. Travel lines dedupe per arrival within the slice.
     *
     * @param arrived whether the player is already standing at the first arrival, so its
     *     travel was packed for by the stretch before
     */
    private static List<FarmingCarryItem> needsOf(int firstArrival, int lastArrival,
            boolean arrived, List<List<Integer>> made, List<StopPlan> plans,
            List<List<Crop>> cropFor, List<SeedPlan> seeds, PlayerState state,
            CapabilityResolver resolver, FarmingInputs inputs) {
        List<StopPlan> stretchPlans = new ArrayList<>();
        List<List<Crop>> stretchCrops = new ArrayList<>();
        for (int arrival = firstArrival; arrival <= lastArrival; arrival++) {
            for (int index : made.get(arrival)) {
                stretchPlans.add(arrived && arrival == firstArrival
                        ? CarryPlanner.withoutTravel(plans.get(index))
                        : plans.get(index));
                // Unguarded, like InventoryDemands.forTrip: plans and cropFor are built
                // aligned. An empty fallback would mean "nothing planted here" and drop
                // the axe.
                stretchCrops.add(cropFor.get(index));
            }
        }
        if (stretchPlans.isEmpty()) {
            return List.of();
        }
        return CarryPlanner.plan(stretchPlans, seeds, stretchCrops, state, resolver, inputs);
    }

    /** Every stop at one arrival. */
    private static List<StopPlan> stopsAt(int arrival, List<List<Integer>> made,
            List<StopPlan> plans) {
        List<StopPlan> here = new ArrayList<>();
        for (int index : made.get(arrival)) {
            here.add(plans.get(index));
        }
        return here;
    }

    /**
     * A stretch's needs with the way to the next bank added, one line per item.
     *
     * <p>Merged rather than appended, because {@link #shortfallOn} walks lines in order: a
     * tablet used in the stretch and again to reach the bank would come out as two
     * withdrawals of one item. Stock counts add up; a tool stays one line.
     */
    private static List<FarmingCarryItem> withTravelOn(List<FarmingCarryItem> needs,
            List<FarmingCarryItem> travel) {
        if (travel.isEmpty()) {
            return needs;
        }
        Map<Integer, FarmingCarryItem> byItem = new LinkedHashMap<>();
        for (FarmingCarryItem line : needs) {
            byItem.put(line.getItemId(), line);
        }
        for (FarmingCarryItem line : travel) {
            FarmingCarryItem already = byItem.get(line.getItemId());
            if (already == null || already.getQuantity() == 0) {
                // A counted line outranks a named one, so its count is not lost.
                if (already == null || line.getQuantity() > 0) {
                    byItem.put(line.getItemId(), line);
                }
            } else {
                // A note explains one half's count, so the sum keeps it only if both agree:
                // a gardener's fee plus a fare is no longer "to have a tree cleared".
                FarmingCarryItem summed = forShortfall(already,
                        already.getQuantity() + line.getQuantity());
                byItem.put(line.getItemId(), Objects.equals(already.getNote(), line.getNote())
                        ? summed : withoutNote(summed));
            }
        }
        return new ArrayList<>(byItem.values());
    }

    /**
     * What this stretch is short of, given what the bag already holds — and what it holds
     * after.
     *
     * <p>The {@code quantity} field splits two rules. A line with a count is stock: this
     * stretch spends it, so the next stretch fetches again. A line without one is a tool: it
     * stays in the bag, so it is asked for once, at the last bank before it is needed. An
     * energy potion carries a count, so it is offered at every bank while run energy is low.
     *
     * <p>Lines the player cannot act on at a bank are skipped: the leprechaun already holds
     * an {@code AT_LEPRECHAUN} line, and a {@code MISSING} one cannot be withdrawn. The
     * trip's packing list still shows both. {@code IN_SEED_BOX} counts as a shortfall,
     * because checking the box at the bank settles whether to withdraw.
     *
     * <p><b>Not modelled</b>, since each would need the bank's stock simulated alongside the
     * bag's:
     * <ul>
     * <li>The bank is bottomless: nothing debits owned stock as withdrawals come out. The
     * rotation deals seeds from stock, so only fixed crops can over-ask.</li>
     * <li>A seed nobody owns enough of is dropped by {@code CarryPlanner}, so its planting is
     * never debited from the bag.</li>
     * <li>Ultracompost has no quantity, so it is asked for once, as for the bottomless
     * bucket.</li>
     * <li>Past the seed box's room, the bag simulation charges each leg's kinds beyond six,
     * not the loose kinds {@link #kindsToBox} leaves out. An early leg can carry a loose
     * kind the simulation charges nothing for, which the harvest headroom absorbs.</li>
     * </ul>
     *
     * @param bagHolds what the player is carrying, per item id, mutated as the stretch is
     *                 walked. An item first met here is seeded from what is genuinely in the
     *                 bag now
     */
    static List<FarmingCarryItem> shortfallOn(List<FarmingCarryItem> needs,
            Map<Integer, Integer> bagHolds) {
        List<FarmingCarryItem> withdraw = new ArrayList<>();
        for (FarmingCarryItem line : needs) {
            // IN_SEED_BOX stays and counts as a shortfall; the panel words it as a check.
            if (line.getAvailability() == FarmingCarryItem.Availability.AT_LEPRECHAUN
                    || line.getAvailability() == FarmingCarryItem.Availability.MISSING) {
                continue;
            }
            boolean stock = line.getQuantity() > 0;
            int wants = stock ? line.getQuantity() : 1;
            int has = bagHolds.containsKey(line.getItemId())
                    ? bagHolds.get(line.getItemId())
                    : line.getCarriedCount();
            if (has < wants) {
                // Ask for the shortfall, not the whole want.
                withdraw.add(forShortfall(line, stock ? wants - has : 0));
                has = wants;
            }
            bagHolds.put(line.getItemId(), stock ? has - wants : has);
        }
        return withdraw;
    }

    private static FarmingCarryItem withoutNote(FarmingCarryItem line) {
        return new FarmingCarryItem(line.getKind(), line.getItemId(), line.getName(),
                line.getQuantity(), line.getAvailability(), line.getTag(), "",
                line.getCarriedCount());
    }

    /** The same line, re-counted as what is missing rather than as what the stretch wants. */
    private static FarmingCarryItem forShortfall(FarmingCarryItem line, int quantity) {
        return new FarmingCarryItem(line.getKind(), line.getItemId(), line.getName(),
                quantity, line.getAvailability(), line.getTag(), line.getNote(),
                line.getCarriedCount());
    }
}
