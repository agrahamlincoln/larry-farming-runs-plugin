package com.larryfarming.farming.engine;

import com.larryfarming.engine.farming.Crop;
import com.larryfarming.engine.farming.PatchType;
import com.larryfarming.farming.model.*;
import java.util.*;
import lombok.*;

/**
 * How full the bag gets as the trip is walked, and therefore where it has to break at a
 * bank.
 *
 * <p>Seeds stack, so a crop costs one slot of seed however many patches take it. Produce
 * does not stack, but the tool leprechaun notes it for free, so a harvest costs one slot
 * per <em>kind</em> picked. Saplings do not stack, and each leaves an empty pot behind:
 * they create nearly all the pressure. Every figure here is a game fact checked against the
 * game or the wiki.
 *
 * <p>Pure arithmetic over a list of {@link Demand}s, with no catalogue and no player state.
 */
public final class InventorySim {

    private InventorySim() {
    }

    /** Inventory size. */
    public static final int SLOTS = 28;

    /**
     * How far off the route a bank may be and still be worth <em>offering</em>.
     *
     * <p>Set in the middle of the widest gap in the walked detour figures, so no bank sits
     * close to the line. It keeps detours such as the Fishing Guild and excludes the Ortus
     * Farm quetzal rungs, whose bank chest lies back south-west of the landing.
     */
    public static final int OFFERED_WITHIN_TILES = 75;

    /**
     * Slots kept free for produce the player has not picked yet.
     *
     * <p>A harvest is charged one slot per <em>kind</em>, which is the bag after a trip to
     * the leprechaun, not while picking. Loose produce piles up until the player notes it,
     * so free slots set the batch size between leprechaun trips.
     *
     * <p>An ultracompost herb patch yields a guaranteed minimum of six herbs before bonuses. Ten clears that with room
     * for bonus lives and leaves eighteen working slots. Yield is random per life, so a flat
     * floor replaces a per-patch model. The advisory bank rows let the player adjust in
     * context, so this is not configurable.
     */
    public static final int HARVEST_HEADROOM = 10;

    /**
     * The most a leg may be planned to reach before a bank stop is placed.
     *
     * <p>This decides where the trip breaks; {@link #SLOTS} still decides whether an arrival
     * is possible at all. An arrival needing more than this but no more than {@code SLOTS}
     * is tight, not impossible.
     */
    public static final int WORKING_SLOTS = SLOTS - HARVEST_HEADROOM;

    /**
     * How many <em>kinds</em> of seed a seed box holds. The wiki has it at "up to
     * 2,147,483,647 seeds of six different types", so only the type count binds.
     */
    private static final int SEED_BOX_KINDS = 6;

    /** Something carried the whole way, costing one slot from the first arrival to the last. */
    public enum Fixture {
        /** Carried every run. Nothing else digs a patch up. */
        SPADE,
        /** Left behind once Barbarian Training (15 Farming) allows bare-handed planting. */
        SEED_DIBBER,
        /**
         * Twenty-eight logs in one slot. The leprechaun will not note logs, so without a
         * basket they are dropped.
         */
        LOG_BASKET,
        /**
         * Ten thousand uses in one slot. Without it the compost is drawn from the
         * leprechaun at each farm and the bucket handed straight back, which costs a slot
         * only while standing there.
         */
        BOTTOMLESS_BUCKET,
        /**
         * Three rune types in one slot (four for the divine pouch, which costs the same).
         * Runes already stack, so the pouch saves two slots net.
         */
        RUNE_POUCH,
        /**
         * Six seed <em>kinds</em> in one slot, any quantity of each. A seventh kind costs a
         * slot like any loose seed. Holds seeds only, not saplings.
         */
        SEED_BOX,
        /**
         * Volcanic ash for Fertile Soil cast to give ultracompost. Ash stacks, so one slot
         * covers any amount.
         */
        VOLCANIC_ASH
    }

    /**
     * What one arrival wants out of the bag.
     *
     * <p>Sets rather than counts, because each costs one slot per <em>kind</em>: seeds
     * stack, noted produce stacks, and a farmer takes a noted payment.
     */
    @Getter
    public static final class Demand {

        private final Arrival at;
        /** Distinct seed types planted here. One slot each however many patches take one. */
        private final Set<Crop> seeds;
        /** Saplings carried in. One slot each, since saplings do not stack. */
        private final int saplings;
        /** Distinct kinds of thing picked here. One slot each, once noted. */
        private final Set<Crop> produce;
        /**
         * Patch types paid for here. One slot per kind of payment, since a farmer takes a
         * noted one. Never herbs or flowers, which no farmer will protect.
         */
        private final Set<PatchType> protections;
        @Getter(AccessLevel.NONE)
        private final int bankTiles;
        @Getter(AccessLevel.NONE)
        private final boolean bankUpstairs;
        @Getter(AccessLevel.NONE)
        private final Bank bank;
        /**
         * Items used here, by item id. One slot each, from the bank of the leg that first
         * needs one to the end of the trip, since nothing is deposited.
         */
        private final Set<Integer> tools;
        /**
         * Items that bring the player here, by item id. Charged like {@link #getTools()},
         * but from the arrival before: the way to a bank has to be in the bag before the
         * bank is reached.
         */
        private final Set<Integer> travel;
        /** Those of {@link #getTravel()} used up on the way here. */
        private final Set<Integer> spentOnTheWay;

        /**
         * @param bankTiles     extra tiles the cheapest bank stop here costs on the route the
         *                      player is actually taking, or {@link TravelOption#UNMEASURED}
         * @param bankUpstairs  whether reaching that bank changes floor
         * @param bank          which bank those tiles were walked to, or null when unmeasured
         * @param tools        items used here, such as the axe, by item id
         * @param travel        items that bring the player here, such as the ectophial or a
         *                      stack of tablets, by item id
         * @param spentOnTheWay those of {@code travel} used up getting here, such as a
         *                      tablet. The stack leaves the bag at the last arrival that
         *                      spends one
         */
        public Demand(Arrival at, Set<Crop> seeds, int saplings, Set<Crop> produce,
                Set<PatchType> protections, int bankTiles, boolean bankUpstairs, Bank bank,
                Set<Integer> tools, Set<Integer> travel, Set<Integer> spentOnTheWay) {
            this.tools = copyOf(tools);
            this.travel = copyOf(travel);
            this.spentOnTheWay = copyOf(spentOnTheWay);
            if (!this.travel.containsAll(this.spentOnTheWay)) {
                throw new IllegalArgumentException("only a travel item is spent on the way");
            }
            if (at == null) {
                throw new IllegalArgumentException("a demand belongs to an arrival");
            }
            if (saplings < 0) {
                throw new IllegalArgumentException("saplings cannot be negative");
            }
            this.at = at;
            this.seeds = copyOf(seeds);
            this.saplings = saplings;
            this.produce = copyOf(produce);
            this.protections = copyOf(protections);
            this.bankTiles = bankTiles;
            this.bankUpstairs = bankUpstairs;
            this.bank = bank;
        }

        /**
         * An immutable copy, not a view: {@link InventorySim#plan} reads each demand many
         * times, so a caller mutating its set would change the answer mid-plan.
         * {@code Set.copyOf} also rejects null members up front.
         */
        private static <T> Set<T> copyOf(Set<T> set) {
            return set == null ? Collections.emptySet() : Set.copyOf(set);
        }

        /**
         * What a bank costs here, on the route this player is taking rather than on average.
         *
         * <p>Prefers the measured figure and falls back to the {@link Arrival}'s constant for
         * an unmeasured rung.
         */
        public Arrival.BankProximity getBank() {
            return bankTiles == TravelOption.UNMEASURED ? at.getBank() : Arrival.BankProximity.of(bankTiles, bankUpstairs);
        }

        /**
         * Whether that grade came from walking this player's own route.
         *
         * <p>Deciding <em>where</em> to break may use the arrival's constant. Telling the
         * player about the bank may not: the constant grades the whole arrival, not this
         * route, so only a measured figure is shown.
         */
        public boolean isBankMeasured() {
            return bankTiles != TravelOption.UNMEASURED;
        }

        /**
         * What a bank stop here costs in walked tiles, the floor change included, or
         * {@link TravelOption#UNMEASURED} where the rung has no figure.
         *
         * <p>Offering a bank needs the figure, not the grade: {@link Arrival.BankProximity#FAR}
         * spans banks on the way and banks that are an errand. Choosing where the trip
         * breaks can work in grades.
         */
        public int bankCostInTiles() {
            return bankTiles == TravelOption.UNMEASURED ? TravelOption.UNMEASURED : bankTiles + (bankUpstairs
                            ? Arrival.BankProximity.FLOOR_CHANGE_TILES : 0);
        }

        /**
         * Which bank the cheapest walked rung here goes to, or null when none was walked.
         *
         * <p>Non-null only when {@link #isBankMeasured()}: the name comes off the same rung
         * as the figure.
         */
        public Bank getBankPlace() {
            // Gated here so the invariant holds for every constructor.
            return isBankMeasured() ? bank : null;
        }
    }

    /**
     * A bank the player passes on a leg without being told to stop at it.
     *
     * <p>Shown as an offer so the player can overrule the plan: they know how much they are
     * about to pick, and the sim only knows a flat reserve.
     */
    @Getter
    @AllArgsConstructor(access = AccessLevel.PACKAGE)
    public static final class Opportunity {

        /** Index of the arrival, into the list handed to {@link #plan}. */
        private final int index;
        private final Arrival at;
        /**
         * What the detour costs on the rung this player took, or null when it has no figure
         * (see {@link Demand#isBankMeasured()}).
         */
        private final Arrival.BankProximity cost;
        /**
         * Which bank this is, or null when the rung that reaches it has no figure.
         *
         * <p>Not {@code getBank()}: {@link Demand#getBank()} returns a grade, not a place.
         */
        private final Bank bankPlace;
    }

    /**
     * One unbroken leg of the trip: what the player sets out from a bank carrying, and how
     * far they get before they need another.
     */
    @Getter
    public static final class Leg {

        /** Index of the first demand on this leg, into the list handed to {@link #plan}. */
        private final int from;
        /** Index of the last demand on this leg, inclusive. */
        private final int to;
        /**
         * Where the player banks before setting out, or null when nobody banks them onto
         * this leg.
         *
         * <p>Null on the first leg because the trip starts at a bank. Null on a later leg
         * means the previous leg ran out of room with no bank in reach, and
         * {@link #getOverflow()} says why.
         */
        private final Arrival bankAt;
        /**
         * What that bank costs to reach on the route the player is taking, or null when
         * nobody banks them onto this leg.
         *
         * <p>Stored rather than looked up from {@link #getBankAt()}: the same place can be a
         * free stop off one rung and a long detour off another.
         */
        private final Arrival.BankProximity bankCost;
        /**
         * Which bank that is, or null when the rung this player took to it has no figure.
         *
         * <p>Null whenever {@link #getBankCost()} is null: both come off an unmeasured rung.
         */
        private final Bank bankPlace;
        /**
         * Whether the trip stopped here sooner than the bag made it.
         *
         * <p>True when the split was moved back to reach a cheaper bank than the last one
         * the bag would have allowed. The player is told so that an early bank stop does not
         * look like a mistake.
         */
        private final boolean bankBroughtForward;
        /** The fullest the bag gets on this leg, which is what had to fit. */
        private final int peakSlots;
        /**
         * Why this leg does not fit, or null when it does.
         *
         * <p>{@code getBankAt() == null && getFrom() > 0} implies this is non-null.
         * {@link Plan#getOverflows()} holds the same sentences as a whole-trip list.
         */
        private final String overflow;
        /**
         * Whether the shortfall only eats into the room kept for the harvest, with every
         * item still fitting. False when nothing overflows.
         */
        private final boolean harvestRoomOnly;
        /**
         * Banks on this leg the trip is not stopping at, in the order they are passed.
         *
         * <p>Empty rather than null when there are none. Excludes the arrival this leg
         * starts from, which is where {@link #getBankAt()} already sends them.
         */
        private final List<Opportunity> passableBanks;

        // Legs are built through plan().
        Leg(int from, int to, Arrival bankAt, Arrival.BankProximity bankCost, Bank bankPlace,
                boolean bankBroughtForward, int peakSlots, String overflow, boolean harvestRoomOnly, List<Opportunity> passableBanks) {
            this.from = from;
            this.to = to;
            this.bankAt = bankAt;
            this.bankCost = bankCost;
            this.bankPlace = bankPlace;
            this.bankBroughtForward = bankBroughtForward;
            this.peakSlots = peakSlots;
            this.overflow = overflow;
            this.harvestRoomOnly = overflow != null && harvestRoomOnly;
            this.passableBanks = List.copyOf(passableBanks);
        }
    }

    /**
     * Where the trip breaks, and where it could not be made to.
     *
     * <p>An overflowing leg with no bank in reach is reported, not silently split, so the
     * player can shed something or plan a second trip.
     */
    @Getter
    public static final class Plan {

        private final List<Leg> legs;
        /** One sentence per leg that does not fit, naming where it fills and why. */
        private final List<String> overflows;

        Plan(List<Leg> legs, List<String> overflows) {
            this.legs = List.copyOf(legs);
            this.overflows = List.copyOf(overflows);
        }

        public boolean fits() {
            return overflows.isEmpty();
        }
    }

    /**
     * Breaks the trip into legs that fit, banking as late as each leg allows.
     *
     * <p>Banking late avoids detours the player does not need. The trip starts at a bank,
     * so the first leg runs as far as it can.
     *
     * @param demands       every arrival being visited, in the order the circuit visits them
     * @param fixtures      what the player carries the whole way
     * @param potsAreKept   true when planting a sapling leaves the empty pot in the bag;
     *                      see {@code InventoryDemands.POTS_ARE_KEPT}
     */
    public static Plan plan(List<Demand> demands, Set<Fixture> fixtures, boolean potsAreKept) {
        List<Demand> stops = demands == null ? Collections.emptyList() : demands;
        int carried = fixtures == null ? 0 : fixtures.size();
        // The seed box is charged only on a trip that plants seeds, the same condition
        // CarryPlanner uses to list it.
        boolean seedsBoxed = fixtures != null && fixtures.contains(Fixture.SEED_BOX) && plantsSeeds(stops);
        if (fixtures != null && fixtures.contains(Fixture.SEED_BOX) && !seedsBoxed) {
            carried--;
        }

        List<Leg> legs = new ArrayList<>();
        List<String> overflows = new ArrayList<>();
        int from = 0;
        Arrival bankAt = null;
        // These describe the next leg to be built: the iteration that chooses a cut sets
        // them one turn before that leg exists. A shortfall belongs to the leg the player
        // carries on into over-full.
        Arrival.BankProximity bankCost = null;
        Bank bankPlace = null;
        boolean broughtForward = false;
        String unbanked = null;
        boolean harvestRoomOnly = false;
        while (from < stops.size()) {
            int to = furthestThatFits(stops, from, carried, potsAreKept, seedsBoxed);
            if (to < from && peakOver(stops, from, from, carried, potsAreKept, seedsBoxed) <= SLOTS) {
                // Tight, not impossible: one arrival over WORKING_SLOTS but within SLOTS.
                // Taken as a leg of one rather than reported. Every path below still
                // advances `from`, so the loop still terminates.
                to = from;
            }
            if (to < from) {
                // One arrival alone exceeds the bag. Nothing to split, so report it.
                //
                // Required for termination: this branch must advance `from`. The paths
                // below all advance it too, since latestBankIn searches [from + 1, to + 1].
                // Without this branch the loop builds legs for the same arrival forever.
                Demand alone = stops.get(from);
                int wanted = peakOver(stops, from, from, carried, potsAreKept, seedsBoxed);
                String said = alone.getAt().getDisplayName() + " alone needs " + wanted + " inventory slots.";
                overflows.add(said);
                // An earlier shortfall and this one can both apply; report both.
                legs.add(new Leg(from, from, bankAt, bankCost, bankPlace, broughtForward,
                        wanted, unbanked == null ? said : unbanked + " " + said, false, passableBanksIn(stops, from, from)));
                from++;
                bankAt = null;
                bankCost = null;
                bankPlace = null;
                broughtForward = false;
                unbanked = null;
                harvestRoomOnly = false;
                continue;
            }
            boolean last = to + 1 >= stops.size();
            int cut = last ? -1 : latestBankIn(stops, from + 1, to + 1);
            if (cut < 0) {
                legs.add(new Leg(from, to, bankAt, bankCost, bankPlace, broughtForward,
                        peakOver(stops, from, to, carried, potsAreKept, seedsBoxed), unbanked,
                        harvestRoomOnly, passableBanksIn(stops, from, to)));
                if (last) {
                    break;
                }
                // The next arrival does not fit and no bank is in reach. Name both ends.
                // Exceeding SLOTS is a real shortfall. Eating into the harvest reserve only
                // means picking less, and must not be reported as a full bag.
                boolean beyondTheBag = peakOver(stops, from, to + 1, carried, potsAreKept, seedsBoxed) > SLOTS;
                String full = stops.get(to + 1).getAt().getDisplayName();
                unbanked = (beyondTheBag ? "Your inventory fills at " : "Your inventory has no room left for the harvest at ")
                        + full + ". No bank lies between " + stops.get(from).getAt().getDisplayName() + " and " + full + ".";
                harvestRoomOnly = !beyondTheBag;
                overflows.add(unbanked);
                from = to + 1;
                bankAt = null;
                bankCost = null;
                bankPlace = null;
                broughtForward = false;
                continue;
            }
            int latest = cut;
            cut = orAcheaperBankNoLater(stops, from, cut, carried, potsAreKept, seedsBoxed);
            // The leg ends before the cut, not at `to`. The bank arrival opens the next leg,
            // since its demands come out of the bank. Ending at `to` would put arrivals on
            // two legs and charge their saplings twice.
            legs.add(new Leg(from, cut - 1, bankAt, bankCost, bankPlace, broughtForward,
                    peakOver(stops, from, cut - 1, carried, potsAreKept, seedsBoxed),
                    unbanked, harvestRoomOnly, passableBanksIn(stops, from, cut - 1)));
            unbanked = null;
            harvestRoomOnly = false;
            from = cut;
            bankAt = stops.get(cut).getAt();
            // Null where the rung has no figure; see Demand#isBankMeasured.
            bankCost = stops.get(cut).isBankMeasured() ? stops.get(cut).getBank() : null;
            bankPlace = stops.get(cut).getBankPlace();
            broughtForward = cut < latest;
        }
        return new Plan(legs, overflows);
    }

    /**
     * The last arrival this leg can reach before the bag is fuller than it holds.
     *
     * <p>Returns {@code from - 1} when the first arrival alone exceeds
     * {@link #WORKING_SLOTS}.
     */
    private static int furthestThatFits(List<Demand> stops, int from, int carried, boolean potsAreKept, boolean seedsBoxed) {
        int to = from - 1;
        for (int end = from; end < stops.size(); end++) {
            if (peakOver(stops, from, end, carried, potsAreKept, seedsBoxed) > WORKING_SLOTS) {
                break;
            }
            to = end;
        }
        return to;
    }

    /**
     * The fullest the bag gets over one leg, walked rather than counted.
     *
     * <p>Order matters: seeds and payments leave as they are spent, and produce arrives as
     * it is picked. Within one arrival the harvest comes first, because a patch must be
     * emptied before it takes a seed.
     */
    private static int peakOver(List<Demand> stops, int from, int to, int carried, boolean potsAreKept, boolean seedsBoxed) {
        // Withdrawn before setting out: one slot per kind, for the whole leg's worth.
        Set<Crop> seedsLeft = EnumSet.noneOf(Crop.class);
        Set<PatchType> paymentsLeft = EnumSet.noneOf(PatchType.class);
        int saplingsLeft = 0;
        for (int i = from; i <= to; i++) {
            seedsLeft.addAll(stops.get(i).getSeeds());
            paymentsLeft.addAll(stops.get(i).getProtections());
            saplingsLeft += stops.get(i).getSaplings();
        }

        Items items = Items.over(stops, to);
        Set<Crop> picked = EnumSet.noneOf(Crop.class);
        int pots = 0;
        // Setting off: from the trip's starting bank on the opening leg, otherwise from the
        // bank at the leg's first arrival, reached already.
        int peak = carried + items.heldAt(from == 0 ? -1 : from) + seedSlots(seedsLeft.size(), seedsBoxed)
                + paymentsLeft.size() + saplingsLeft;
        for (int i = from; i <= to; i++) {
            Demand stop = stops.get(i);
            // Peak read after picking and before spending: the fullest moment at an arrival
            // is the harvest in hand with this stop's seed, payment and sapling still held.
            picked.addAll(stop.getProduce());
            peak = Math.max(peak, carried + items.heldAt(i) + seedSlots(seedsLeft.size(), seedsBoxed)
                    + paymentsLeft.size() + saplingsLeft + pots + picked.size());

            // Spent on the way out. A planted sapling leaves its empty pot unless the pot is
            // destroyed. Nothing below raises the total, so the peak is not re-read.
            saplingsLeft -= stop.getSaplings();
            if (potsAreKept) {
                pots += stop.getSaplings();
            }
            seedsLeft.removeAll(stop.getSeeds());
            paymentsLeft.removeAll(stop.getProtections());
        }
        return peak;
    }

    /**
     * The tools and travel items in the bag on a leg, one slot each.
     *
     * <p>Each comes out at the bank of the leg that first needs it, as the seeds do, and
     * stays to the end of the trip, because nothing is deposited. A travel item is needed
     * from the arrival before the one it reaches. A stack of tablets alone leaves the bag,
     * at the last arrival this leg's withdrawal reaches: each bank fetches only its own
     * stretch's tablets, so a later use comes out of a later bank.
     */
    private static final class Items {

        /** Each item the leg has withdrawn by its end, and the arrival it is gone at. */
        private final List<Integer> goneAt;

        private Items(List<Integer> goneAt) {
            this.goneAt = goneAt;
        }

        /** Over a leg ending at {@code to}. */
        static Items over(List<Demand> stops, int to) {
            Map<Integer, Integer> firstNeeded = new HashMap<>();
            Map<Integer, Integer> lastSpent = new HashMap<>();
            for (int i = 0; i < stops.size(); i++) {
                Demand stop = stops.get(i);
                for (int item : stop.getTools()) {
                    firstNeeded.merge(item, i, Math::min);
                }
                for (int item : stop.getTravel()) {
                    firstNeeded.merge(item, Math.max(0, i - 1), Math::min);
                }
                // Up to the arrival after the leg, whose tablet is spent reaching the next
                // bank. Tablets for beyond it are fetched there.
                if (i <= to + 1) {
                    for (int item : stop.getSpentOnTheWay()) {
                        lastSpent.merge(item, i, Math::max);
                    }
                }
            }
            List<Integer> goneAt = new ArrayList<>();
            for (Map.Entry<Integer, Integer> needed : firstNeeded.entrySet()) {
                if (needed.getValue() <= to) {
                    goneAt.add(lastSpent.getOrDefault(needed.getKey(), Integer.MAX_VALUE));
                }
            }
            return new Items(goneAt);
        }

        /** How many are in the bag while standing at this arrival, -1 for the first bank. */
        int heldAt(int arrival) {
            int held = 0;
            for (int gone : goneAt) {
                if (gone > arrival) {
                    held++;
                }
            }
            return held;
        }
    }

    /**
     * What a leg's seed kinds cost in slots. With the box along, only kinds beyond
     * {@link #SEED_BOX_KINDS} cost a slot; the box's own slot is charged with the fixtures.
     */
    private static int seedSlots(int kinds, boolean seedsBoxed) {
        return seedsBoxed ? Math.max(0, kinds - SEED_BOX_KINDS) : kinds;
    }

    /** Whether any stop on the trip puts a seed in the ground, as opposed to a sapling. */
    private static boolean plantsSeeds(List<Demand> stops) {
        for (Demand stop : stops) {
            if (!stop.getSeeds().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The latest arrival in this window the player can bank at, or -1 when none can.
     *
     * <p>Latest rather than cheapest, so the leg count stays minimal; a {@code FAR} bank
     * still counts. {@link #orAcheaperBankNoLater} then moves the cut to a cheaper bank
     * where that costs no extra leg. An arrival whose bank grades {@code NONE} is never a
     * candidate.
     */
    private static int latestBankIn(List<Demand> stops, int from, int to) {
        for (int i = Math.min(to, stops.size() - 1); i >= from; i--) {
            if (stops.get(i).getBank() != Arrival.BankProximity.NONE) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Every bank on a leg the trip is walking past.
     *
     * <p>Starts at {@code from + 1}: the leg's first arrival is where it was banked onto (or,
     * on the opening leg, the trip's starting bank).
     *
     * <p>Unmeasured rungs come back with a null cost rather than being dropped.
     *
     * <p>Not gated on how full the bag is. These rows are offers: banking is convenient when
     * passing, and the player judges whether to take one.
     *
     * <p>Only banks the trip genuinely walks past are offered; see {@link #offeredHere}.
     */
    private static List<Opportunity> passableBanksIn(List<Demand> stops, int from, int to) {
        List<Opportunity> passed = new ArrayList<>();
        for (int i = from + 1; i <= to && i < stops.size(); i++) {
            Demand stop = stops.get(i);
            if (!offeredHere(stop)) {
                continue;
            }
            passed.add(new Opportunity(i, stop.getAt(), stop.isBankMeasured() ? stop.getBank() : null, stop.getBankPlace()));
        }
        return passed;
    }

    /**
     * Whether this arrival's bank is worth putting a row in front of the player.
     *
     * <p>A measured route is judged on its tiles against {@link #OFFERED_WITHIN_TILES},
     * since the {@code FAR} grade spans banks on the way and banks that are an errand. An
     * unmeasured bank is offered only when the arrival's constant says it is on the way,
     * because that constant grades the whole arrival.
     *
     * <p>This narrows only what is <em>offered</em>. A forced break still uses
     * {@link #latestBankIn}, which may pick a real detour.
     */
    private static boolean offeredHere(Demand stop) {
        int walked = stop.bankCostInTiles();
        return walked == TravelOption.UNMEASURED ? stop.getBank().onTheWay() : walked <= OFFERED_WITHIN_TILES;
    }

    /**
     * The same cut, moved back to a better bank when moving it costs the trip nothing.
     *
     * <p>{@link #latestBankIn} minimises the number of legs and ignores walking cost. This
     * moves the cut to an earlier, cheaper bank <b>as long as the trip does not gain a leg
     * by cutting sooner</b>.
     *
     * <p>Only a strictly better <em>grade</em> moves the cut, never a few tiles inside one.
     * Grades exist for measured and unmeasured rungs alike, so both compare on equal terms.
     * Ties keep the later arrival.
     */
    private static int orAcheaperBankNoLater(List<Demand> stops, int from, int cut, int carried, boolean potsAreKept, boolean seedsBoxed) {
        Arrival.BankProximity best = stops.get(cut).getBank();
        if (best == Arrival.BankProximity.AT_ARRIVAL) {
            return cut;
        }
        int allowed = legsFrom(stops, cut, carried, potsAreKept, seedsBoxed);
        int chosen = cut;
        for (int i = cut - 1; i > from; i--) {
            Arrival.BankProximity here = stops.get(i).getBank();
            if (here.compareTo(best) < 0 && legsFrom(stops, i, carried, potsAreKept, seedsBoxed) <= allowed) {
                best = here;
                chosen = i;
            }
        }
        return chosen;
    }

    /**
     * How many legs the rest of the trip takes from here, cutting as late as it can.
     *
     * <p>The yardstick for {@link #orAcheaperBankNoLater}. Deliberately the plain
     * latest-bank walk, not a recursive copy of the caller, so it terminates and is
     * deterministic. Latest-bank cutting gives the minimal count.
     */
    private static int legsFrom(List<Demand> stops, int start, int carried, boolean potsAreKept, boolean seedsBoxed) {
        int legs = 0;
        int from = start;
        while (from < stops.size()) {
            int to = furthestThatFits(stops, from, carried, potsAreKept, seedsBoxed);
            legs++;
            if (to < from) {
                // Covers both plan()'s tight-arrival and impossible-arrival branches, which
                // each emit one leg and advance one arrival.
                from++;
                continue;
            }
            if (to + 1 >= stops.size()) {
                break;
            }
            int cut = latestBankIn(stops, from + 1, to + 1);
            from = cut < 0 ? to + 1 : cut;
        }
        return legs;
    }
}
