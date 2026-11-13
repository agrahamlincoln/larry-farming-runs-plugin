package com.larryfarming.farming.engine;

import com.larryfarming.farming.model.Arrival;
import com.larryfarming.farming.model.Bank;
import com.larryfarming.snapshot.FarmingCarryItem;
import com.larryfarming.snapshot.FarmingStopAdvice;
import java.util.*;

/**
 * The bank rows of a walkthrough: a prescribed bank in front of every leg that needs one,
 * the banks the trip passes on offer, the bag to set off with, and a row where the bag
 * overflows with no bank in reach.
 *
 * <p>A bank row stands where the player meets the bank. A prescribed bank, and an offered
 * bank on the route from the landing to the patch, come before the arrival's row: the
 * player banks on the way in.
 *
 * <p>Works on rows {@link FarmingAdviceBuilder} has already drawn, and on the bag
 * {@link InventorySim} simulated over them.
 */
final class BankStopSplicer {

    private BankStopSplicer() {
    }

    /**
     * The walkthrough with a bank stop in front of every leg that needs one.
     *
     * <p>Spliced in after every row exists, because where the bag runs out depends on what
     * every earlier arrival took out of the inventory.
     *
     * <p>The first leg never gets one: the player packs before setting out, so leg zero
     * begins at a bank. A later leg with no bank is the sim reporting that the bag fills
     * with nothing in reach to empty it into; it draws an overflow row and is detailed by
     * {@link InventorySim.Leg#getOverflow()}.
     *
     * @param rowOf where each arrival the bag was planned over sits in {@code stops}. Leg
     *     indices count only arrivals being made, while {@code stops} also holds skipped
     *     rows. A mis-sized {@code rowOf} throws rather than splicing a bank into the wrong
     *     place.
     * @param withdrawals what each bank row says to take out, or
     *     {@link WithdrawalPlanner.Withdrawals#none()} where the caller has not worked them
     *     out
     * @param underway whether the player is partway round, which changes only how the
     *     starting row is headed and lets it stand on a trip with no bank to splice
     */
    static List<FarmingStopAdvice> withBankStops(List<FarmingStopAdvice> stops, InventorySim.Plan bag, List<Integer> rowOf,
            WithdrawalPlanner.Withdrawals withdrawals, boolean underway) {
        // Asks what the legs say, not how many there are: a single leg can still be an
        // arrival nobody could carry. Partway round, the starting row can be the only bank
        // row there is, so the walk below still runs to place it.
        if (nothingToSplice(bag) && !underway) {
            return stops;
        }
        // Keyed by the row of the arrival they sit at. A prescribed bank and an offered bank
        // on the way in go *before* their arrival's row; an offered bank graded only by its
        // arrival goes *after* it. A prescribed bank is spliced at its leg's break, so it
        // keeps its own cursor.
        Map<Integer, FarmingStopAdvice> offeredBefore = new HashMap<>();
        Map<Integer, FarmingStopAdvice> offeredAfter = new HashMap<>();
        for (InventorySim.Leg leg : bag.getLegs()) {
            for (InventorySim.Opportunity passed : leg.getPassableBanks()) {
                // put is safe: rowOf is strictly increasing, legs partition the demand
                // indices contiguously, and passableBanksIn excludes each leg's first
                // arrival, so no two offers share a row. Break either and an offer
                // silently disappears.
                (passed.isOnTheWayIn() ? offeredBefore : offeredAfter).put(rowOf.get(passed.getIndex()),
                        optionalBankStop(passed, withdrawals.offeredAt(passed.getIndex())));
            }
        }

        List<FarmingStopAdvice> withBanks = new ArrayList<>(stops.size() + bag.getLegs().size()
                + offeredBefore.size() + offeredAfter.size() + 1);
        addStartingBag(withBanks, withdrawals, underway);
        int next = 0;
        for (int index = 0; index < bag.getLegs().size(); index++) {
            InventorySim.Leg leg = bag.getLegs().get(index);
            int breaksAt = rowOf.get(leg.getFrom());
            while (next < breaksAt) {
                addWithItsOffers(withBanks, stops, offeredBefore, offeredAfter, next++);
            }
            if (leg.getBankAt() != null) {
                withBanks.add(bankStop(leg, withdrawals.atLeg(index)));
            } else if (leg.getOverflow() != null) {
                withBanks.add(overflowStop(leg));
            }
        }
        while (next < stops.size()) {
            addWithItsOffers(withBanks, stops, offeredBefore, offeredAfter, next++);
        }
        return withBanks;
    }

    /** The starting row's heading before the trip begins. */
    static final String SETTING_OFF_HEADING = "Withdraw before you set off";

    /** The starting row's heading once the player is partway round, with only deposits. */
    static final String NEXT_BANK_HEADING = "Next time you bank";

    /**
     * The starting row's heading partway round when it lists withdrawals. They are for the
     * stretch the player is on, which no bank row splits, so they are fetched before going on.
     */
    static final String BANK_FIRST_HEADING = "Bank first";

    /**
     * The bag to set off with, when the trip passes a bank later and some items can wait
     * there until the stop that wants them. A trip with no bank packs the whole "Take with
     * you" list, so it gets no row until the player is partway round, when the row is the
     * only list measured against the bag they carry now.
     *
     * <p><b>Not a shorter "Take with you".</b> That grid is a checklist of everything the
     * trip needs; this row lists the withdrawals for the first stretch only.
     * {@link WithdrawalPlanner} decides whether it is worth drawing.
     *
     * <p>Headed "Withdraw before you set off" because nothing has been routed yet, so there is no
     * bank to name. Partway round, the row stands for whichever bank the player opens next:
     * "Bank first" while it lists something to fetch, which the panel draws ahead of the next
     * stop, and "Next time you bank" for deposits alone. It stays unnamed there too: a stop
     * skipped as growing carries no route, so the bank the player last passed is not known
     * here.
     */
    private static void addStartingBag(List<FarmingStopAdvice> into, WithdrawalPlanner.Withdrawals withdrawals, boolean underway) {
        List<FarmingCarryItem> first = withdrawals.getBeforeSettingOff();
        // Partway round, deposits alone are reason enough for the row.
        if (first.isEmpty() && withdrawals.getDeposits().isEmpty()) {
            return;
        }
        into.add(row(FarmingStopAdvice.Kind.BANK_START, !underway ? SETTING_OFF_HEADING
                        : first.isEmpty() ? NEXT_BANK_HEADING : BANK_FIRST_HEADING, "").withdraw(first).deposit(withdrawals.getDeposits())
                .depositUrgent(withdrawals.isDepositsUrgent()).build());
    }

    /**
     * One row of the walkthrough, between the bank offered on the way in to it and the bank
     * offered once its patches are worked.
     *
     * <p>A bank before the patches cannot take their harvest. No offered row relies on it:
     * an offered row only withdraws. The bag simulation sheds a harvest only at a prescribed
     * bank, which stands after it, before the next leg's first arrival.
     */
    private static void addWithItsOffers(List<FarmingStopAdvice> into, List<FarmingStopAdvice> stops,
            Map<Integer, FarmingStopAdvice> offeredBefore, Map<Integer, FarmingStopAdvice> offeredAfter, int row) {
        if (offeredBefore.containsKey(row)) {
            into.add(offeredBefore.get(row));
        }
        into.add(stops.get(row));
        if (offeredAfter.containsKey(row)) {
            into.add(offeredAfter.get(row));
        }
    }

    /**
     * One bank stop: where to break, and how full the inventory was going to get if they
     * did not.
     *
     * <p>The slot count ("26 of 28 slots full") gives the player a claim to check against
     * their own inventory, so the row does not read as mere caution. It goes in the route
     * line under the heading: the panel draws {@code because} only for skipped stops, and
     * {@code note} only as a hover. The bank sits at an arrival already on the circuit, so
     * there is no journey for that line to describe otherwise.
     */
    private static FarmingStopAdvice bankStop(InventorySim.Leg leg, List<FarmingCarryItem> withdraw) {
        // Named by the bank, never by the arrival: Ortus Farm has no bank, and its rungs
        // bank at the Hunter Guild or in Civitas illa Fortis.
        return row(FarmingStopAdvice.Kind.BANK, bankNamed(leg.getBankPlace()),
                leg.getPeakSlots() + " of " + InventorySim.SLOTS + " slots full." + whyHere(leg)).withdraw(withdraw).build();
    }

    /** A row of this kind under this heading, with nothing planted or set aside. */
    private static FarmingStopAdvice.FarmingStopAdviceBuilder row(FarmingStopAdvice.Kind kind, String heading, String instruction) {
        return FarmingStopAdvice.builder().kind(kind).patchName(heading)
                .outcome(FarmingStopAdvice.Outcome.VISIT).instruction(instruction).because("")
                .plants(Collections.emptyList()).setAside(Collections.emptyList());
    }

    /**
     * A bank the trip walks past: offered, with what the detour costs, and nothing else.
     *
     * <p><b>No slot count</b>, unlike {@link #bankStop}: the bag fits without this stop, so
     * a comfortable number would only make the row look like a weaker prescribed bank.
     *
     * <p>Outcome {@code VISIT} because the bank sits at an arrival already on the circuit.
     * The kind makes it read as optional: the panel draws it muted and unnumbered.
     */
    private static FarmingStopAdvice optionalBankStop(InventorySim.Opportunity passed, List<FarmingCarryItem> withdraw) {
        String cost = costOfDetour(passed.getCost());
        // Named by the bank, as in {@link #bankStop}. Offered banks carry withdrawals too.
        // Most trips never fill the bag, so every bank row on them is an offered one. A long
        // detour carries none: WithdrawalPlanner sends its items out with the bank before it.
        return row(FarmingStopAdvice.Kind.BANK_OPTIONAL, bankNamed(passed.getBankPlace()), cost.isEmpty() ? "" : cost + ".")
                .withdraw(withdraw).build();
    }

    /**
     * The head of a bank row: the bank by name where one is known, and a bare "Bank" where
     * none is.
     *
     * <p>A bank is known only where the catalogue carries a measured figure. "Bank" alone
     * cannot send the player to the wrong one of Falador's two. The arrival's name never
     * stands in for a bank's.
     *
     * <p>A named bank can be shared: every rung at a stop may carry the stop's one figure and
     * bank.
     */
    private static String bankNamed(Bank bank) {
        return bank == null ? "Bank" : bank.getRowTitle();
    }

    /**
     * What reaching this bank costs, on one scale for every bank row: "On the way", "Short
     * detour", "Long detour". Empty for an unmeasured rung; the row is still drawn.
     */
    private static String costOfDetour(Arrival.BankProximity cost) {
        if (cost == null) {
            return "";
        }
        switch (cost) {
            case AT_ARRIVAL:
                return "On the way";
            case SHORT_WALK:
                return "Short detour";
            case FAR:
                return "Long detour";
            default:
                return "";
        }
    }

    /**
     * Why this bank and not a later one, in the player's terms, as sentences that follow
     * the slot count.
     *
     * <p>The slot count says the stop is necessary; this says whether it is cheap, which
     * decides whether the player follows the row. A brought-forward bank says so, because
     * stopping with the inventory half empty otherwise looks like a mistake.
     *
     * <p>Empty unless the rung the player is taking carries a measured figure. Unmeasured
     * rungs grade from the arrival's constant, which plans the split but is not shown;
     * {@code getBankCost} is null for them.
     */
    private static String whyHere(InventorySim.Leg leg) {
        if (leg.getBankCost() == null) {
            return "";
        }
        // Never brought forward at FAR: the cut only moves for a strictly better grade, so a
        // leg that still grades FAR is one the inventory picked rather than the route.
        String early = leg.isBankBroughtForward() && leg.getBankCost() != Arrival.BankProximity.FAR
                ? " Bank here early. This bank is closer." : "";
        String cost = costOfDetour(leg.getBankCost());
        return cost.isEmpty() ? "" : " " + cost + "." + early;
    }

    /** Whether every leg is one the player just walks, with nothing to say in front of it. */
    private static boolean nothingToSplice(InventorySim.Plan bag) {
        // Offered banks count: a trip needing no stop still offers the banks it passes.
        return bag.getLegs().stream().allMatch(leg -> leg.getBankAt() == null && leg.getOverflow() == null && leg.getPassableBanks().isEmpty());
    }

    /**
     * The row for a leg the bag does not survive and no bank can be reached on.
     *
     * <p>Outcome {@code VISIT} so the fold never hides it behind the skipped patches.
     */
    private static FarmingStopAdvice overflowStop(InventorySim.Leg leg) {
        return row(FarmingStopAdvice.Kind.OVERFLOW, leg.isHarvestRoomOnly() ? "No room left for the harvest here"
                        : "Your inventory fills up here", "")
                // What the sim found, then what the player can do about it.
                .actions(List.of(leg.getOverflow(), "Drop what you can spare, or leave the rest for another trip.")).build();
    }
}
