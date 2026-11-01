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
     * {@link InventorySim.Plan#getOverflows()}.
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
        // Keyed by the row they follow: a prescribed bank goes *before* the arrival opening
        // a leg, an offered one *after* the arrival it passes. Threading both insertion
        // points through one cursor misplaces rows.
        Map<Integer, FarmingStopAdvice> offered = new LinkedHashMap<>();
        // The bank each offer is at, so a prescribed row can tell it is the same bank. By
        // identity: two offers can be equal rows at different banks.
        Map<FarmingStopAdvice, Bank> offeredBank = new IdentityHashMap<>();
        for (InventorySim.Leg leg : bag.getLegs()) {
            for (InventorySim.Opportunity passed : leg.getPassableBanks()) {
                // put is safe: rowOf is strictly increasing, legs partition the demand
                // indices contiguously, and passableBanksIn excludes each leg's first
                // arrival, so no two offers share a row. Break either and an offer
                // silently disappears.
                FarmingStopAdvice row = optionalBankStop(passed, withdrawals.afterArrival(passed.getIndex()));
                offered.put(rowOf.get(passed.getIndex()), row);
                if (passed.getBankPlace() != null) {
                    offeredBank.put(row, passed.getBankPlace());
                }
            }
        }

        List<FarmingStopAdvice> withBanks = new ArrayList<>(stops.size() + bag.getLegs().size() + offered.size() + 1);
        addStartingBag(withBanks, withdrawals, underway);
        int next = 0;
        for (int index = 0; index < bag.getLegs().size(); index++) {
            InventorySim.Leg leg = bag.getLegs().get(index);
            int breaksAt = rowOf.get(leg.getFrom());
            while (next < breaksAt) {
                withBanks.add(stops.get(next));
                addIfOffered(withBanks, offered, next++);
            }
            if (leg.getBankAt() != null) {
                withBanks.add(inPlaceOfTheSameBankOffered(withBanks, offeredBank,
                        leg.getBankPlace(), bankStop(leg, withdrawals.atLeg(index))));
            } else if (leg.getOverflow() != null) {
                withBanks.add(overflowStop(leg));
            }
        }
        while (next < stops.size()) {
            withBanks.add(stops.get(next));
            addIfOffered(withBanks, offered, next++);
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
     * A prescribed bank, taking the place of the same bank offered on the row just before
     * it, with that row's withdrawals added to its own.
     *
     * <p>The bank a trip passes after one arrival can be the bank the next leg opens at:
     * Falador east, between the park tree and the farm. Two rows would send the player into
     * one bank twice.
     */
    private static FarmingStopAdvice inPlaceOfTheSameBankOffered(List<FarmingStopAdvice> rows,
            Map<FarmingStopAdvice, Bank> offeredBank, Bank bank, FarmingStopAdvice prescribed) {
        if (rows.isEmpty() || bank == null) {
            return prescribed;
        }
        FarmingStopAdvice before = rows.get(rows.size() - 1);
        // By the bank, not the heading: an unmeasured bank is headed a bare "Bank".
        if (before.getKind() != FarmingStopAdvice.Kind.BANK_OPTIONAL || offeredBank.get(before) != bank) {
            return prescribed;
        }
        rows.remove(rows.size() - 1);
        // Each list is a shortfall for its own stretch, so a line on both adds up.
        List<FarmingCarryItem> withdraw = new ArrayList<>(before.getWithdraw());
        for (FarmingCarryItem item : prescribed.getWithdraw()) {
            int both = indexOfLine(withdraw, item);
            if (both < 0) {
                withdraw.add(item);
                continue;
            }
            FarmingCarryItem offered = withdraw.get(both);
            withdraw.set(both, item.withQuantity(offered.getQuantity() + item.getQuantity()));
        }
        return prescribed.toBuilder().withdraw(withdraw).build();
    }

    private static int indexOfLine(List<FarmingCarryItem> lines, FarmingCarryItem item) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).getItemId() == item.getItemId() && lines.get(i).getName().equals(item.getName())) {
                return i;
            }
        }
        return -1;
    }

    /** The offered-bank row that follows this one, if any. */
    private static void addIfOffered(List<FarmingStopAdvice> into, Map<Integer, FarmingStopAdvice> offered, int row) {
        FarmingStopAdvice passed = offered.get(row);
        if (passed != null) {
            into.add(passed);
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
        for (InventorySim.Leg leg : bag.getLegs()) {
            // Offered banks count: a trip needing no stop still offers the banks it passes.
            if (leg.getBankAt() != null || leg.getOverflow() != null || !leg.getPassableBanks().isEmpty()) {
                return false;
            }
        }
        return true;
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
