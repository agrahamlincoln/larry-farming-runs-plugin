package com.larryfarming.farming.engine;

import com.larryfarming.data.Tables;
import com.larryfarming.engine.ItemCounts;
import com.larryfarming.engine.SeedBox;
import com.larryfarming.engine.farming.*;
import com.larryfarming.farming.catalogue.*;
import com.larryfarming.farming.detection.*;
import com.larryfarming.farming.model.*;
import com.larryfarming.model.*;
import com.larryfarming.snapshot.FarmingCarryItem;
import com.larryfarming.snapshot.FarmingCarryItem.Availability;
import java.util.*;
import java.util.stream.Collectors;
import net.runelite.api.gameval.ItemID;

/**
 * What to take on the run: tools, seeds, and whatever the chosen routes depend on.
 *
 * <p>Derived from the plan, so the travel lines name only the teleports this player is
 * actually taking today.
 *
 * <p>Held items stay on the list rather than being filtered out: the list is a pre-run
 * checklist, and a line that vanishes once owned cannot be checked off.
 *
 * <p>Throughout, the list errs towards packing a tool it cannot prove is unneeded. A tool
 * packed unused costs a slot; a tool left behind can cost the trip.
 */
public final class CarryPlanner {

    private CarryPlanner() {
    }

    /**
     * Breathing gear for the underwater patches. Both pieces must be worn (helmet on the
     * head, apparatus in the cape slot) or oxygen drains. Unlocked by the Freeing Pirate
     * Pete subquest of Recipe for Disaster, so the lines stay on the list unowned.
     *
     * <p>MISSING here means "not seen", not "not owned": Murphy re-issues both free, and
     * both can sit in a boat's cargo hold, which nothing here models.
     *
     * <p>Not the 2017 Halloween apparatus (21723), which shares the name and does nothing.
     */
    private static final int FISHBOWL_HELMET = 7534;
    private static final int DIVING_APPARATUS = 7535;

    /**
     * A neck-slot item that replaces both the helmet and the apparatus. Listed only for a
     * player who owns one, since it needs Sailing 63 and eight fragments.
     */
    private static final int MEDALLION_OF_THE_DEEP = 32386;

    /** The item families and game facts behind the tool lines, by name. */
    private static final Tables.Named TOOLS = Tables.byName("/com/larryfarming/tools.tsv", 3);

    /** Every axe, best first; the line draws whichever one the player has. */
    private static final int[] AXES = TOOLS.of("axes").integers(1);

    /**
     * What to draw when the player has no axe: the bronze axe, not the first of
     * {@link #AXES}, which would show a third age axe as the thing to fetch.
     */
    private static final int PLAIN_AXE = ItemID.BRONZE_AXE;

    /** The log basket and the forestry basket, each in its closed and open form. */
    private static final int[] LOG_BASKETS = TOOLS.of("logBaskets").integers(1);

    /**
     * The seed box, closed and open. Must match the ids {@code SeedBox} gates its contents
     * on, or the line never ticks.
     */
    private static final int[] SEED_BOX_IDS = {ItemID.SEED_BOX, ItemID.SEED_BOX_OPEN};

    /**
     * The bag for a trip, which may be one run or several walked as one circuit.
     *
     * <p>Takes every stop at once rather than being called per run: runes and tablets are
     * deduplicated per <em>arrival</em>, and that dedupe must span run boundaries. Seeds
     * are tallied across every run's stops into one line per crop.
     *
     * @param plans   the stops as planned, so only routes actually being taken are packed
     *                for; in any order
     * @param cropFor what goes in the ground at each stop, index-aligned with {@code plans}.
     *                The seed lines, the fees, the casts and the axe all read it
     * @param inputs what has been seen at each patch: decides whether a patch holds a tree to
     *               fell and whether it is diseased. An unseen patch keeps the axe on the list
     */
    public static List<FarmingCarryItem> plan(List<StopPlan> plans,
            List<List<Crop>> cropFor, PlayerState state, CapabilityResolver resolver, FarmingInputs inputs) {
        if (inputs == null) {
            inputs = FarmingInputs.none();
        }
        List<FarmingCarryItem> carry = new ArrayList<>();

        carry.add(tool(ItemID.SPADE, "Spade", state));
        // Otto's planting lesson retires the dibber permanently.
        if (resolver.resolve(Capability.BARE_HAND_PLANTING, state).getState() != CapabilityState.HAVE) {
            carry.add(tool(ItemID.DIBBER, "Seed dibber", state));
        }
        // A Fertile Soil caster carries the spell's runes and ash instead of compost; see
        // addTravel and addCompostUpgrades.
        boolean fertile = FertileSoil.inUse(inputs.getPreferences(), state, resolver.getBook());
        int fertileCasts = fertile ? FertileSoil.castsFor(plans, cropFor) : 0;
        CompostBins.Needs bins = CompostBins.over(plans, inputs, state);
        // Otherwise one compost line: the bottomless bucket if owned, loose ultracompost.
        if (packsBucket(plans, fertile, bins, state)) {
            // No AT_LEPRECHAUN branch: only a bucket owned as an item, carried or banked,
            // packs. One stored at the leprechaun shows only in its varbits (7915/7916).
            boolean inTheBag = BottomlessBucket.ITEM_IDS.stream().anyMatch(id -> ItemCounts.countCarried(state, id) > 0);
            carry.add(new FarmingCarryItem(FarmingCarryItem.Kind.TOOL,
                    ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED, "Bottomless compost bucket", 0, inTheBag
                            ? Availability.CARRIED : Availability.BANKED,
                    // Carried count is capped at one: a second bottomless bucket is no spare.
                    "", "", inTheBag ? 1 : 0));
        } else if (packsCompost(plans, fertile)) {
            carry.add(tool(ItemID.BUCKET_ULTRACOMPOST, "Ultracompost", state));
        }

        int clearingCoins = addTreeTools(carry, plans, cropFor, state, inputs);
        addFairyRingGear(carry, plans, state, resolver);
        addMagicSecateurs(carry, state);
        int curePlantCasts = addCureTools(carry, plans, state, inputs);
        addDivingGear(carry, plans, state);
        addProtectionPayments(carry, plans, cropFor, state);
        // What is actually going in the ground, not what the rotation dealt out: a seat can
        // decline its dealt seed, such as a fruit tree left standing because no sapling on
        // hand matches or beats it, as the replant setting asks.
        List<SeedAllocation.Tally> planting = SeedPlan.goingInTheGround(plans, cropFor);
        addSeedBox(carry, planting, state);
        addSeeds(carry, planting, state);
        addFixedSeeds(carry, plans, cropFor, state, inputs);
        addTravel(carry, plans, state, curePlantCasts, fertileCasts);
        addCoins(carry, plans, state, clearingCoins);
        addCompostUpgrades(carry, bins, fertileCasts * FertileSoil.ASH_PER_CAST, state);
        carry.addAll(emptyBuckets(bins, state));

        // Less the seeds the player owns none of. The stop that wanted an unowned seed
        // already names the shortfall, so listing the seed here would ask for a planting the
        // run has dropped. Seeds only: an unowned tool stays on the list, because a missing
        // spade or axe costs the whole trip.
        carry.removeIf(item -> !item.isOwned() && item.getKind() == FarmingCarryItem.Kind.SEED);
        return carry;
    }

    /**
     * What it takes to reach these stops and nothing more: the travel items, runes and
     * fares, plus the fairy-ring staff.
     *
     * <p>A bank at a stop's arrival opens only once the player is there, so the way there
     * has to come out of an earlier bank. {@link #withoutTravel} strips the same things from
     * the stop, so the two halves add up to {@link #plan}.
     */
    static List<FarmingCarryItem> travelFor(List<StopPlan> plans, PlayerState state, CapabilityResolver resolver) {
        List<FarmingCarryItem> carry = new ArrayList<>();
        addFairyRingGear(carry, plans, state, resolver);
        addTravel(carry, plans, state, 0, 0);
        addCoins(carry, plans, state, 0);
        return carry;
    }

    /**
     * Whether a {@link #travelFor} line costs an inventory slot. With a rune pouch the runes
     * ride in it, and {@code InventorySim} charges the pouch as a fixture; without one, each
     * rune type is a stack of its own. The staff and the ring of charos(a) are worn. A line
     * the leprechaun holds is not in the bag either.
     *
     * <p>A pouch is assumed to hold every rune type the trip casts. It holds three, or four
     * for the divine pouch, which covers the teleports Larry routes by.
     */
    static boolean takesAbagSlot(FarmingCarryItem line, boolean ownsRunePouch) {
        if (line.getKind() == FarmingCarryItem.Kind.RUNES) {
            return !ownsRunePouch;
        }
        return line.getItemId() != RING_OF_CHAROS_ACTIVATED && line.getAvailability() != Availability.AT_LEPRECHAUN
                && Arrays.stream(STAFF_IDS).noneMatch(staff -> staff == line.getItemId());
    }

    /**
     * Whether a {@link #travelFor} line is used up on the way: a stack of tablets, one
     * broken per arrival, or runes, withdrawn to the cast. Coins carry a count too, but a
     * fare leaves the stack in the bag.
     */
    static boolean spentOnTheWay(FarmingCarryItem line) {
        return line.getKind() == FarmingCarryItem.Kind.RUNES || line.getKind() == FarmingCarryItem.Kind.TRAVEL && line.getQuantity() > 0
                        && line.getItemId() != ItemID.COINS;
    }

    /**
     * Every item id that counts for the same packing-list line as this one: each axe, basket,
     * staff and bucket form, each dose of compost potion, an empty bucket and its note, every
     * charge tier of a travel item, and a crop's seed, seedlings and sapling. A line names one
     * id; the bag may hold any of these.
     */
    static Set<Integer> formsOf(int itemId) {
        if (BottomlessBucket.ITEM_IDS.contains(itemId)) {
            return BottomlessBucket.ITEM_IDS;
        }
        for (int[] family : new int[][]{AXES, LOG_BASKETS, STAFF_IDS, CompostBins.POTIONS, CompostBins.EMPTY_BUCKETS}) {
            if (Arrays.stream(family).anyMatch(id -> id == itemId)) {
                return idsOf(family);
            }
        }
        int[] travel = CarryItems.formsOf(itemId);
        if (travel != null) {
            return idsOf(travel);
        }
        for (CropData data : CropData.values()) {
            List<Integer> forms = data.getPlantingForms();
            if (itemId > 0 && forms.contains(itemId)) {
                Set<Integer> planting = new HashSet<>(forms);
                // Seed-planted crops store 0 in their potted columns.
                planting.remove(0);
                return planting;
            }
        }
        return Set.of(itemId);
    }

    private static Set<Integer> idsOf(int[] family) {
        return Arrays.stream(family).boxed().collect(Collectors.toCollection(HashSet::new));
    }

    /** The same stop with no way of getting there, for a stretch already standing at it. */
    static StopPlan withoutTravel(StopPlan plan) {
        return plan.withRoute(null).withNeeds(Set.of()).withTeleport(null);
    }

    /**
     * One coins line for everything the day charges: the chosen routes' fares and the fees
     * for trees someone else clears.
     *
     * <p>Boat fares come from each route. The minecart toll comes from the
     * {@link Capability#LOVAKENGJ_MINECART} capability instead, because it belongs to the
     * network and The Forsaken Tower waives it for good. An unobserved quest still packs
     * the fare.
     *
     * @param clearingCoins the gardeners' fees to clear trees, from {@link #addTreeTools}
     */
    private static void addCoins(List<FarmingCarryItem> carry, List<StopPlan> plans, PlayerState state, int clearingCoins) {
        int rides = 0;
        int charters = 0;
        int fares = 0;
        // The same total priced as though the ring of charos(a) were worn.
        int wornFares = 0;
        for (StopPlan plan : plans) {
            if (!plan.isVisit()) {
                continue;
            }
            if (plan.getNeeds().contains(Capability.LOVAKENGJ_MINECART)) {
                rides++;
            }
            if (plan.getRoute() == null || plan.getRoute().getFareCoins() == 0) {
                continue;
            }
            int fare = plan.getRoute().getFareCoins();
            if (!isCharter(plan)) {
                fares += fare;
                wornFares += fare;
                continue;
            }
            boolean cabinFever = finished(state, "Cabin Fever");
            fares += charterFare(fare, cabinFever, isWearingCharos(state));
            wornFares += charterFare(fare, cabinFever, true);
            charters++;
        }
        if (rides > 0 && !finished(state, "The Forsaken Tower")) {
            fares += rides * MINECART_FARE;
            wornFares += rides * MINECART_FARE;
        }
        if (fares == 0 && clearingCoins == 0) {
            return;
        }
        // Priced as the player stands. The worn-ring price rides along as a note, which the
        // panel shows on hover.
        int total = fares + clearingCoins;
        FarmingCarryItem coins = item(FarmingCarryItem.Kind.TRAVEL, ItemID.COINS, "Coins", total, total, state);
        String trees = clearingCoins > GARDENER_FEE ? "trees" : "a tree";
        String note = clearingCoins == 0 ? "For boat and minecart fares." : fares == 0 ? "To have " + trees + " cleared."
                : "For fares, and to have " + trees + " cleared.";
        if (charters > 0 && ownsCharos(state) && !isWearingCharos(state)) {
            note = note + " Only " + (wornFares + clearingCoins) + " coins if you wear your ring of charos(a).";
        }
        carry.add(coins.withNote(note));
        // The ring gets its own line, kept once worn. Only for a player who owns one, since
        // it is a Garden of Tranquillity reward.
        if (charters > 0 && ownsCharos(state)) {
            carry.add(item(FarmingCarryItem.Kind.TRAVEL, RING_OF_CHAROS_ACTIVATED,
                    "Ring of charos(a)", 0, 1, state).withNote("Halves charter fares when worn."));
        }
    }

    /** Whether this route pays a charter captain, the only fares the discounts apply to. */
    private static boolean isCharter(StopPlan plan) {
        return plan.getNeeds().contains(Capability.CHARTER_SHIP);
    }

    /**
     * What a charter costs: halved by Cabin Fever, and halved again by a worn ring of
     * charos(a). Rounded up at each halving, so the packed coins are never one short.
     */
    private static int charterFare(int base, boolean cabinFever, boolean wearingCharos) {
        int fare = base;
        if (cabinFever) {
            fare = (fare + 1) / 2;
        }
        if (wearingCharos) {
            fare = (fare + 1) / 2;
        }
        return fare;
    }

    private static boolean finished(PlayerState state, String quest) {
        return state != null && state.getQuests().get(quest) == QuestStatus.COMPLETED;
    }

    /** Worn, not merely owned: the discount needs the ring on the finger. */
    private static boolean isWearingCharos(PlayerState state) {
        if (state == null) {
            return false;
        }
        ItemStack ring = state.getEquipment().get(EquipmentSlot.RING);
        return ring != null && ring.getItemId() == RING_OF_CHAROS_ACTIVATED;
    }

    /** Holds one anywhere it could be fetched from, the bank included. */
    private static boolean ownsCharos(PlayerState state) {
        return ItemCounts.ownsAny(state, RING_OF_CHAROS_ACTIVATED);
    }

    /** Coins per Lovakengj minecart ride. */
    private static final int MINECART_FARE = 20;

    /** The activated ring. The plain one (4202) discounts nothing. */
    private static final int RING_OF_CHAROS_ACTIVATED = 6465;

    /**
     * The two ways to cure a diseased crop, and which patches take which.
     *
     * <p>Deliberately not exhaustive over {@link PatchType}. A patch in neither set draws no
     * cure line, because a wrong cure is a wasted slot.
     */
    private static final Set<PatchType> PRUNED_WITH_SECATEURS = Set.copyOf(TOOLS.of("prunedWithSecateurs").constants(PatchType.class, 1));

    private static final Set<PatchType> CURED_WITH_PLANT_CURE = Set.copyOf(TOOLS.of("curedWithPlantCure").constants(PatchType.class, 1));

    /** Either opens a fairy ring; the dramen staff first, as the one a player meets first. */
    private static final int[] STAFF_IDS = {Loadouts.DRAMEN_STAFF, Loadouts.LUNAR_STAFF};

    /**
     * Cure Plant, the Lunar spell that cures one patch per cast for no inventory slot.
     *
     * <p>Elemental staves are not modelled for any spell, so a staff of earth user is
     * over-asked eight earth runes a cast.
     */
    private static final int CURE_PLANT_MAGIC_LEVEL = 66;
    private static final Map<Rune, Integer> CURE_PLANT_RUNES = Map.of(Rune.EARTH, 8, Rune.ASTRAL, 1);


    /** The gardener's fee to clear a regular, fruit or hardwood tree. */
    private static final int GARDENER_FEE = 200;

    /**
     * The Woodcutting level to chop each lumber or hardwood tree once grown, the same as the
     * wild tree. Fruit trees chop at any level.
     */
    private static final Map<Crop, Integer> WOODCUTTING_LEVEL = Tables.read("/com/larryfarming/woodcutting-levels.tsv", 2).stream()
            .collect(Collectors.toMap(row -> row.constant(Crop.class, 0), row -> row.integer(1)));

    /** What the trees being replaced ask of the bag. */
    private static final class TreeWork {
        private boolean axe;
        private boolean logs;
        private int coins;
    }

    /**
     * The axe for any tree being chopped, the log basket for runs that produce logs, and
     * the gardener's coins for any tree {@link #gardenerClears} names.
     *
     * <p>A grown tree must come out before the patch takes the next sapling, so the axe is
     * required and listed even when unowned. The basket is optional, so it is listed only
     * when owned. Fruit trees yield fruit, which a log basket will not hold.
     *
     * @return the coins the clearing costs, for the one coins line
     */
    private static int addTreeTools(List<FarmingCarryItem> carry, List<StopPlan> plans,
            List<List<Crop>> cropFor, PlayerState state, FarmingInputs inputs) {
        TreeWork work = treeWorkIn(plans, cropFor, state, inputs);
        if (work.axe) {
            carry.add(tool(bestOwned(state, AXES, PLAIN_AXE), "Axe", state, AXES));
        }
        if (work.logs) {
            FarmingCarryItem basket = tool(LOG_BASKETS[0], "Log basket", state, LOG_BASKETS);
            if (basket.isOwned()) {
                carry.add(basket);
            }
        }
        return work.coins;
    }

    /** Where the log basket rides on a trip. */
    enum BasketPlace {
        /** Left in the bank: no logs come off, or the player owns no basket. */
        NONE,
        /** In the inventory, because a route needs the cape slot for its teleport. */
        BAG,
        /** Worn in the cape slot, which no route on the trip needs. */
        BACK
    }

    /** Teleports from items worn in the cape slot. */
    private static final Set<Capability> CAPE_SLOT_TELEPORTS = Set.copyOf(TOOLS.of("capeSlotTeleports").constants(Capability.class, 1));

    static {
        TOOLS.requireAllRead();
    }

    /**
     * Where the log basket goes on this trip.
     *
     * <p>The basket is wearable in the cape slot, so worn it costs no inventory slot. A
     * route that teleports by a cape-slot item takes the slot first. Either way one of the
     * two takes an inventory slot, so the cape is worn for its teleport and the basket
     * rides in the bag.
     */
    static BasketPlace basketPlace(List<StopPlan> plans, List<List<Crop>> cropFor, PlayerState state, FarmingInputs inputs) {
        if (state == null || bestOwned(state, LOG_BASKETS, 0) == 0 || !treeWorkIn(plans, cropFor, state, inputs).logs) {
            return BasketPlace.NONE;
        }
        for (StopPlan plan : plans) {
            if (plan.isVisit() && !Collections.disjoint(plan.getNeeds(), CAPE_SLOT_TELEPORTS)) {
                return BasketPlace.BAG;
            }
        }
        return BasketPlace.BACK;
    }

    /**
     * The items clearing these stops' trees puts in the bag, by item id: the plain axe
     * standing for any axe, and coins for a fee.
     */
    static Set<Integer> treeToolsFor(List<StopPlan> plans, List<List<Crop>> cropFor, PlayerState state, FarmingInputs inputs) {
        TreeWork work = treeWorkIn(plans, cropFor, state, inputs);
        Set<Integer> tools = new HashSet<>();
        if (work.axe) {
            tools.add(PLAIN_AXE);
        }
        if (work.coins > 0) {
            tools.add(ItemID.COINS);
        }
        return tools;
    }

    /** What clearing these stops' grown trees asks of the bag, patch by patch. */
    private static TreeWork treeWorkIn(List<StopPlan> plans, List<List<Crop>> cropFor, PlayerState state, FarmingInputs inputs) {
        TreeWork work = new TreeWork();
        for (int index = 0; index < plans.size(); index++) {
            StopPlan plan = plans.get(index);
            if (!plan.isVisit()) {
                continue;
            }
            // The axe follows the planting, not the patch: a tree left standing is not
            // cleared, so it needs no axe.
            if (nothingIsPlantedAt(cropFor, index)) {
                continue;
            }
            // Asked per patch, because one stop can cover several patches (the three Fossil
            // Island hardwoods are one stop). A grown tree is either cleared by the gardener
            // for a fee, no logs given, or chopped down and its stump dug up.
            for (PatchRef ref : plan.getStop().getPatches()) {
                PatchType type = ref.type();
                if (!treeStandsAt(type, ref, inputs)) {
                    continue;
                }
                PatchPrediction seen = inputs.patchAt(ref);
                if (gardenerClears(type, seen == null ? null : seen.getCrop(), state)) {
                    work.coins += GARDENER_FEE;
                } else {
                    work.axe = true;
                    work.logs |= type != PatchType.FRUIT_TREE;
                }
            }
        }
        return work;
    }

    /**
     * Whether a tree may stand in this patch and has to come out before the next one goes in:
     * a tree patch not known to hold nothing to fell. Calquat is absent: per the wiki it is
     * cleared with a spade like a bush, even though it is classified as a tree. No run visits
     * a redwood, celastrus or crystal tree patch. A grown tree still owed its health
     * check stands, as a checked one does. The row's clearing line asks the same question,
     * so the line and the bag agree.
     */
    static boolean treeStandsAt(PatchType type, PatchRef ref, FarmingInputs inputs) {
        return (type == PatchType.TREE || type == PatchType.HARDWOOD_TREE || type == PatchType.FRUIT_TREE)
                && !nothingToFellAt(inputs, ref);
    }

    /**
     * Whether the gardener clears this grown tree for {@link #GARDENER_FEE}, rather than the
     * player chopping it. The row's wording asks the same question, so the line and the bag
     * agree.
     *
     * <p>Only for a lumber tree or hardwood above the player's Woodcutting level: chopping
     * is free and gives the logs, so the fee is the last resort. An unseen tree keeps the
     * axe. A fruit tree chops at any level.
     *
     * @param crop the tree standing there, or null when unseen
     */
    static boolean gardenerClears(PatchType type, Crop crop, PlayerState state) {
        if (type != PatchType.TREE && type != PatchType.HARDWOOD_TREE || crop == null || state == null) {
            return false;
        }
        Integer needs = WOODCUTTING_LEVEL.get(crop);
        return needs != null && state.getSkillLevel(Skill.WOODCUTTING) < needs;
    }

    /**
     * Breathing gear, for a trip with any underwater stop.
     *
     * <p>Asked of the patch, not the planting (unlike the axe): visiting the patch at all
     * needs it.
     *
     * <p>No inventory slot is charged and no {@code InventorySim.Fixture} exists for it,
     * because all of it is worn on the dive.
     */
    private static void addDivingGear(List<FarmingCarryItem> carry, List<StopPlan> plans, PlayerState state) {
        if (!anyDiveAmong(plans)) {
            return;
        }
        if (ItemCounts.ownsAny(state, MEDALLION_OF_THE_DEEP)) {
            carry.add(tool(MEDALLION_OF_THE_DEEP, "Medallion of the deep", state).withNote("Wear it instead of the helmet and apparatus."));
            return;
        }
        carry.add(tool(FISHBOWL_HELMET, "Fishbowl helmet", state).withNote("Wear both. The oxygen lasts only while you wear them."));
        carry.add(tool(DIVING_APPARATUS, "Diving apparatus", state));
    }

    /**
     * The farmers' fees, for the stops that pay one. {@link Protection} pays only from a fee
     * the player already owns, so this line never asks for something to gather, and
     * {@code InventoryDemands} keeps a slot for exactly these.
     *
     * @param cropFor what goes in the ground at each stop, which sets the patches paid for
     */
    private static void addProtectionPayments(List<FarmingCarryItem> carry,
            List<StopPlan> plans, List<List<Crop>> cropFor, PlayerState state) {
        for (int index = 0; index < plans.size(); index++) {
            StopPlan plan = plans.get(index);
            if (!plan.isVisit()) {
                continue;
            }
            int planting = cropFor.get(index).size();
            Protection.Fee fee = Protection.paymentAt(plan.getStop(), planting, state);
            if (fee == null) {
                continue;
            }
            carry.add(item(FarmingCarryItem.Kind.TOOL, fee.itemId, fee.name,
                    fee.amount, fee.amount, state).withNote(fee.farmer + "'s protection fee."));
        }
    }

    /**
     * Whether the bag packs its compost line, the bottomless bucket or loose ultracompost.
     * The packing list and the bag simulation both ask this, so they agree on the bucket's
     * slot. A trip of only anima plants takes no compost and leaves the line off, and so
     * does a Fertile Soil trip, whose casts treat every patch.
     *
     * @param fertile whether the trip casts Fertile Soil
     */
    static boolean packsCompost(List<StopPlan> plans, boolean fertile) {
        return !fertile && plans.stream().anyMatch(FertileSoil::composts);
    }

    /**
     * Whether the bag packs the bottomless bucket: the player owns it as an item, and the
     * compost line packs or a bin empties into it. The packing list and the bag simulation
     * both ask this.
     */
    static boolean packsBucket(List<StopPlan> plans, boolean fertile, CompostBins.Needs bins, PlayerState state) {
        return BottomlessBucket.toHand(state) && (packsCompost(plans, fertile) || bins.isBucket());
    }

    /**
     * Whether any visited stop on this trip covers a seaweed patch. Asked of every patch the
     * stop covers, not of its own patch type.
     */
    private static boolean anyDiveAmong(List<StopPlan> plans) {
        return plans.stream().filter(StopPlan::isVisit).flatMap(plan -> plan.getStop().getPatches().stream())
                .anyMatch(ref -> ref.type() == PatchType.SEAWEED);
    }

    /**
     * Whether this stop puts nothing in the ground, so nothing there needs clearing first.
     * A short {@code cropFor} answers false, keeping the axe.
     */
    private static boolean nothingIsPlantedAt(List<List<Crop>> cropFor, int index) {
        return index < cropFor.size() && cropFor.get(index).isEmpty();
    }

    /**
     * What it takes to cure the patches seen diseased.
     *
     * <p>Drawn only for disease actually seen, unlike the axe's pack-by-default. A cure is
     * per patch and per kind, so packing against the chance of disease would add both items
     * to every trip. Disease does not kill on the cycle it appears, so a patch that turns
     * sick en route is caught on the next run.
     *
     * <p>A player who can cast Cure Plant and owns the runes is packed runes instead of
     * either item. The spell cures any patch type, including ones the tables above do not
     * place.
     *
     * @return how many casts the trip needs, for {@code addTravel} to price in runes; zero
     *         when the items were named instead or nothing is sick. The caller prices them
     *         so the cure runes merge with a Lunar run's teleport runes into one line each
     */
    private static int addCureTools(List<FarmingCarryItem> carry, List<StopPlan> plans, PlayerState state, FarmingInputs inputs) {
        boolean prune = false;
        // Counted once per patch: two runs on one trip can name the same patch.
        Set<PatchRef> counted = new HashSet<>();
        int cures = 0;
        int sick = 0;
        for (StopPlan plan : plans) {
            if (!plan.isVisit()) {
                continue;
            }
            for (PatchRef ref : plan.getStop().getPatches()) {
                PatchType type = ref.type();
                if (type == null || !CropAssigner.diseased(ref, inputs) || !counted.add(ref)) {
                    continue;
                }
                // Counted before the tables: a cast cures any patch type.
                sick++;
                if (PRUNED_WITH_SECATEURS.contains(type)) {
                    // Secateurs are not used up, so one pair covers the run.
                    prune = true;
                } else if (CURED_WITH_PLANT_CURE.contains(type)) {
                    cures++;
                }
            }
        }
        if (sick > 0 && canCastCurePlant(state) && hasRunesToCure(state, sick)) {
            return sick;
        }
        // Magic secateurs prune as well as any pair, and a player who owns them already
        // brings them: the packing list names them (see addMagicSecateurs).
        // The plain pair shares one leprechaun slot with magic secateurs, which ItemCounts
        // tells apart with the type varbit.
        if (prune && !ItemCounts.ownsAny(state, Loadouts.MAGIC_SECATEURS)) {
            carry.add(tool(ItemID.SECATEURS, "Secateurs", state));
        }
        // Consumed per patch cured, so this line carries a count.
        if (cures > 0) {
            carry.add(item(FarmingCarryItem.Kind.TOOL, ItemID.PLANT_CURE, "Plant cure", cures, cures, state));
        }
        return 0;
    }

    /**
     * Whether the player owns the runes for every cast, the bank included.
     *
     * <p>Without the runes the bag names the items instead: a gardener sells plant cure
     * beside the patch, where missing runes leave the player unable to cure at all.
     */
    private static boolean hasRunesToCure(PlayerState state, int casts) {
        return CURE_PLANT_RUNES.entrySet().stream()
                .allMatch(rune -> ItemCounts.countOwned(state, rune.getKey().getItemId()) >= casts * rune.getValue());
    }

    /**
     * Whether this player can cure a patch with a spell rather than a bottle.
     *
     * <p>Being on the Lunar spellbook implies Lunar Diplomacy is done. The book comes from
     * the varbit, not the resolver, which carries the book the <em>run</em> is written for.
     * Uses the real Magic level, so a boost or an unread level falls back to the bottle.
     */
    private static boolean canCastCurePlant(PlayerState state) {
        return FarmingAdviceBuilder.openBookOf(state) == Spellbook.LUNAR && state.getSkillLevel(Skill.MAGIC) >= CURE_PLANT_MAGIC_LEVEL;
    }

    /**
     * Whether this patch is <em>known</em> to hold nothing an axe could fell. An unseen patch
     * answers false.
     *
     * <ul>
     * <li>Dead: comes out with a spade.</li>
     * <li>Diseased: cured, not felled; {@code addCureTools} packs for it.</li>
     * <li>Idle: weeds decode as state {@code GROWING}, so this relies on
     *     {@link PatchPrediction#isIdle()} rather than the state.</li>
     * <li>A stump: shares a {@code CropState} with the standing tree, so
     *     {@link PatchPrediction#isFellable()} separates them ("Chop down" vs "Clear").</li>
     * <li>Part-grown: decided by readiness, not state, because a tree still owed its health
     *     check also decodes {@code GROWING} and does need the axe.
     *     {@code LIKELY_READY} keeps the axe.</li>
     * </ul>
     */
    private static boolean nothingToFellAt(FarmingInputs inputs, PatchRef ref) {
        PatchPrediction seen = inputs.patchAt(ref);
        if (seen == null) {
            return false;
        }
        if (seen.getState() == CropState.DEAD || seen.getState() == CropState.DISEASED || seen.isIdle()) {
            return true;
        }
        // A HARVESTABLE patch that is not fellable is a stump or a spade-cleared crop.
        if (seen.getState() == CropState.HARVESTABLE && !seen.isFellable()) {
            return true;
        }
        // GROWING only: a fruit tree restocking fruit is HARVESTABLE with a timer running,
        // which readiness would call growing while the tree is fellable. A zero clock would
        // read every timer as growing, so it keeps the axe.
        //
        // The tree owed its health check keeps the axe only because its finish time is past
        // and reads READY. This relies on every tree crop having a growth rate.
        long now = inputs.getNowEpochSec();
        return seen.getState() == CropState.GROWING && now > 0 && seen.readinessAt(now) == PatchPrediction.Readiness.GROWING;
    }

    /**
     * What the finished compost bins and Fertile Soil take to come out ultracompost: the
     * potion and one ash line for both.
     *
     * @param fertileAsh the ash Fertile Soil casts take
     */
    private static void addCompostUpgrades(List<FarmingCarryItem> carry, CompostBins.Needs bins, int fertileAsh, PlayerState state) {
        if (bins.getDoses() > 0) {
            carry.add(potion(bins.getDoses(), state).withNote("One dose turns a finished bin of compost into supercompost."));
        }
        // The bins empty only on ash the player owns, so with bins to upgrade the bag takes
        // what is owned and Fertile Soil's last casts give supercompost.
        int ash = bins.getAsh() > 0 ? Math.min(bins.getAsh() + fertileAsh, ItemCounts.countOwned(state, ItemID.FOSSIL_VOLCANIC_ASH))
                : fertileAsh;
        if (ash > 0) {
            carry.add(item(FarmingCarryItem.Kind.TOOL, ItemID.FOSSIL_VOLCANIC_ASH, "Volcanic ash", ash, ash, state)
                    .withNote("For ultracompost: 25 per finished bin, two per Fertile Soil cast."));
        }
    }

    /** A stack of empty buckets to store at the leprechaun, asked for when they run low. */
    static final int BUCKET_RESTOCK = 100;

    /**
     * The empty buckets the bins empty into, which the leprechaun hands out at the farm.
     * Those carried count first, then those it stores, and the bag packs only the rest,
     * noted, in one slot. When the player's empty buckets run low, the lines ask for a noted
     * stack of {@link #BUCKET_RESTOCK} to store there instead: what the bank holds of it, and
     * the rest to buy, under the plain bucket so the two lines stay apart.
     *
     * <p>The packing list and the bag simulation both read these lines, so the bag charges
     * the noted slot only for a {@code BANKED} line.
     */
    static List<FarmingCarryItem> emptyBuckets(CompostBins.Needs bins, PlayerState state) {
        int need = bins.getBuckets();
        if (need == 0 && !bins.isRestock()) {
            return List.of();
        }
        int carried = CompostBins.emptyBuckets(state, true);
        int stored = ItemCounts.countInLeprechaunStorage(state, ItemID.BUCKET_EMPTY);
        if (!bins.isRestock()) {
            // The bins share only the buckets the player owns, so the bank covers a shortfall.
            Availability availability = carried >= need ? Availability.CARRIED
                    : carried + stored >= need ? Availability.AT_LEPRECHAUN : Availability.BANKED;
            boolean banked = availability == Availability.BANKED;
            return List.of(bucketLine(ItemID.Cert.BUCKET_EMPTY, banked ? need - stored : need, availability, carried,
                    banked ? "Noted, for the compost bins." : "For the compost bins."));
        }
        int inBank = CompostBins.emptyBuckets(state, false) - carried - stored;
        int toHand = Math.min(BUCKET_RESTOCK, carried + inBank);
        boolean banked = carried < toHand;
        List<FarmingCarryItem> lines = new ArrayList<>(2);
        if (toHand > 0) {
            lines.add(bucketLine(ItemID.Cert.BUCKET_EMPTY, toHand,
                    banked ? Availability.BANKED : Availability.CARRIED, carried,
                    banked ? "Noted, to store with the leprechaun." : "For the compost bins."));
        }
        if (toHand < BUCKET_RESTOCK) {
            lines.add(bucketLine(ItemID.BUCKET_EMPTY, BUCKET_RESTOCK - toHand, Availability.MISSING, 0,
                    (banked ? "" : "Noted, to store with the leprechaun. ") + "Farming shops sell them."));
        }
        return lines;
    }

    /** One empty bucket line: the noted or plain sprite, how many, and where they are. */
    private static FarmingCarryItem bucketLine(int itemId, int quantity, Availability availability, int carried,
            String note) {
        return new FarmingCarryItem(FarmingCarryItem.Kind.TOOL, itemId, "Empty bucket", quantity, availability, "", "", carried)
                .withNote(note);
    }

    /** Whether the bag packs noted empty buckets from the bank, which take a slot. */
    static boolean bringsBuckets(CompostBins.Needs bins, PlayerState state) {
        return emptyBuckets(bins, state).stream().anyMatch(line -> line.getAvailability() == Availability.BANKED);
    }

    /**
     * The compost potion line, counted in doses: one slot holds up to four doses. The
     * sprite is the full vial. The leprechaun cannot store potions, so there is no
     * {@code AT_LEPRECHAUN} branch.
     */
    private static FarmingCarryItem potion(int doses, PlayerState state) {
        int carried = CompostBins.doses(state, true);
        Availability availability = carried >= doses ? Availability.CARRIED
                : CompostBins.doses(state, false) >= doses ? Availability.BANKED : Availability.MISSING;
        return new FarmingCarryItem(FarmingCarryItem.Kind.TOOL, ItemID.SUPERCOMPOST_POTION_4, "Compost potion", doses, availability, "", "", carried);
    }

    /**
     * The first of these the player owns, or {@code none} when they own none, including
     * before the bank has been seen.
     */
    private static int bestOwned(PlayerState state, int[] itemIds, int none) {
        return Arrays.stream(itemIds).filter(itemId -> ItemCounts.ownsAny(state, itemId)).findFirst().orElse(none);
    }

    /**
     * Whether the trip needs a fairy-ring staff wielded: a visit rides a fairy ring, and
     * {@link Capability#STAFFLESS_FAIRY_RINGS} (the elite Lumbridge &amp; Draynor diary) is
     * not known to be held. UNKNOWN keeps the staff.
     *
     * <p>The staff then holds the loadout plan's weapon slot, and the bag lists
     * it too unless it is already on the player (see {@code addFairyRingGear}).
     */
    public static boolean staffWielded(List<StopPlan> plans, PlayerState state, CapabilityResolver resolver) {
        boolean ringsUsed = plans.stream().anyMatch(plan -> plan.isVisit() && plan.getNeeds().contains(Capability.FAIRY_RINGS));
        return ringsUsed && resolver.resolve(Capability.STAFFLESS_FAIRY_RINGS, state).getState() != CapabilityState.HAVE;
    }

    /**
     * What a trip that needs a fairy-ring staff wielded ({@link #staffWielded}) adds to the
     * bag; nothing otherwise.
     *
     * <p>The staff, when the player has none on them. The weapon slot asks for it as well,
     * but a ring refuses the player without it, so the bag names it like any tool the trip
     * cannot do without. Either staff serves; the line names the one the player owns, and
     * the dramen staff when they own neither.
     */
    private static void addFairyRingGear(List<FarmingCarryItem> carry, List<StopPlan> plans, PlayerState state, CapabilityResolver resolver) {
        if (!staffWielded(plans, state, resolver)) {
            return;
        }
        if (!carriedAny(state, STAFF_IDS)) {
            int staff = bestOwned(state, STAFF_IDS, Loadouts.DRAMEN_STAFF);
            String name = staff == Loadouts.LUNAR_STAFF ? "Lunar staff" : "Dramen staff";
            carry.add(tool(staff, name, state, STAFF_IDS).withNote("Wield it to use fairy rings."));
        }
    }

    /**
     * Magic secateurs, when the player owns a pair. They raise yield from the inventory as
     * well as the hand, so they are something to pack like any tool, worn or not.
     */
    private static void addMagicSecateurs(List<FarmingCarryItem> carry, PlayerState state) {
        if (ItemCounts.ownsAny(state, Loadouts.MAGIC_SECATEURS)) {
            carry.add(tool(Loadouts.MAGIC_SECATEURS, "Magic secateurs", state).withNote("More herbs, allotment crops and berries."));
        }
    }

    /**
     * The seed box, on a trip with seeds to put in it, listed only when owned. A trip that
     * plants only trees leaves it behind: see {@link CropData#boxesAsAseed}.
     *
     * <p>Where the kinds it holds and the kinds the trip plants come to more than it fits,
     * the line names the kinds the trip does not plant, to empty at the bank: the plan
     * gives their room to the trip's seeds.
     *
     * <p>{@code InventorySim} charges the box a slot on the same test.
     */
    private static void addSeedBox(List<FarmingCarryItem> carry, List<SeedAllocation.Tally> planting, PlayerState state) {
        if (planting.isEmpty()) {
            return;
        }
        FarmingCarryItem box = tool(SEED_BOX_IDS[0], "Seed box", state, SEED_BOX_IDS);
        if (!box.isOwned()) {
            return;
        }
        Set<Integer> kinds = new HashSet<>();
        planting.forEach(tally -> Optional.ofNullable(CropData.forCrop(tally.getCrop())).map(CropData::getSeedItemId)
                .filter(CropData::boxesAsAseed).ifPresent(kinds::add));
        if (kinds.isEmpty()) {
            return;
        }
        List<String> idle = new ArrayList<>();
        for (ItemStack stack : SeedBox.contentsCarried(state) ? state.getSeedBox() : List.<ItemStack>of()) {
            // Any kind in the box holds a place, tree seeds included, though the plan never
            // boxes one itself.
            if (stack.getItemId() != 0 && kinds.add(stack.getItemId())) {
                idle.add(stack.getName().toLowerCase(Locale.ROOT));
            }
        }
        // A trip planting more kinds than the box holds, with nothing idle in it, has nothing
        // to empty: the planner already sends the overflow to later banks.
        carry.add(!idle.isEmpty() && kinds.size() > SeedBox.KINDS_HELD ? box.withNote("Empty out the " + String.join(", ", idle) + ".") : box);
    }

    /**
     * One line per crop, with how many patches take it, across every run on the trip. The
     * tally is the same one the panel's seed line prints.
     */
    private static void addSeeds(List<FarmingCarryItem> carry, List<SeedAllocation.Tally> planting, PlayerState state) {
        for (SeedAllocation.Tally tally : planting) {
            CropData data = CropData.forCrop(tally.getCrop());
            if (data != null) {
                carry.add(seedLine(data, tally.getCount(), state));
            }
        }
    }

    /**
     * One bag line for a crop's planting stock, in whichever form the player has it.
     *
     * <p>For a sapling-planted crop, seeds, seedlings and saplings all count towards the line.
     * The row names the potted form only when potted stock alone fills the run, and
     * "Oak sapling or acorn" (or "Oak seedling or acorn") when the rest must still be
     * potted. Every name is the game's own, from {@link CropData}.
     */
    private static FarmingCarryItem seedLine(CropData data, int count, PlayerState state) {
        int seed = data.getSeedItemId();
        if (!data.isSaplingPlanted()) {
            return item(FarmingCarryItem.Kind.SEED, seed, data.getSeedName(), count, count, state);
        }
        // Furthest-along first, so the line is named and drawn for the furthest stage they
        // hold any of.
        int[] stages = {data.getSaplingItemId(), data.getWateredSeedlingItemId(), data.getSeedlingItemId()};
        int potted = Arrays.stream(stages).map(stage -> ItemCounts.countOwned(state, stage)).sum();
        String pottedName = ItemCounts.ownsAny(state, stages[0]) ? data.getSaplingName() : data.getSeedlingName();
        String name;
        if (potted >= count) {
            name = pottedName;
        } else if (potted > 0) {
            name = pottedName + " or " + data.getSeedName().toLowerCase(Locale.ROOT);
        } else {
            name = data.getSeedName();
        }
        FarmingCarryItem line = item(FarmingCarryItem.Kind.SEED, bestOwned(state, stages, seed), name, count, count, state,
                ids(data.getPlantingForms()));
        // A watered seedling grows into a sapling in the bag with no errand, so it counts as
        // ready. A bare seed needs potting and an unwatered seedling needs water, and both
        // are done before setting off. With neither owned there is nothing to pot.
        int ready = ItemCounts.countOwned(state, stages[0]) + ItemCounts.countOwned(state, stages[1]);
        int toPot = ItemCounts.countOwned(state, seed) + ItemCounts.countOwned(state, stages[2]);
        return ready < count && toPot > 0 ? line.withNote(FarmingCarryItem.POT_SEEDS_FIRST) : line;
    }

    private static int[] ids(List<Integer> itemIds) {
        return itemIds.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Seeds for the stops that plant one particular thing: snape grass, a white lily, a
     * potato cactus, or the crop the player ticked in its place.
     *
     * <p>These stops bypass the seed allocation, so their seeds are packed here.
     *
     * <p>The count is {@code seedsPerPatch} times the patches the stop plants, because one
     * stop can cover several patches (the two seaweed patches are one dive). Those are the
     * patches dealt a seed, which the stock bounds across the trip, so one crop picked at
     * many patches packs what is owned and a later stretch asks for no more.
     */
    private static void addFixedSeeds(List<FarmingCarryItem> carry, List<StopPlan> plans, List<List<Crop>> cropFor, PlayerState state,
            FarmingInputs inputs) {
        FarmingPreferences prefs = inputs.getPreferences();
        Map<Crop, Integer> needed = new LinkedHashMap<>();
        for (int index = 0; index < plans.size(); index++) {
            StopPlan plan = plans.get(index);
            if (!plan.isVisit() || !plan.getStop().isFixedCrop()) {
                continue;
            }
            Crop crop = FixedCrops.cropFor(plan.getStop(), prefs, state);
            if (crop == null) {
                continue;
            }
            int open = cropFor.get(index).size();
            CropData data = CropData.forCrop(crop);
            if (data != null && open > 0) {
                needed.merge(crop, data.getSeedsPerPatch() * open, Integer::sum);
            }
        }
        needed.forEach((crop, count) -> carry.add(seedLine(CropData.forCrop(crop), count, state)));
    }

    /**
     * The items behind the routes being taken, each listed once however many stops use it.
     *
     * <p>A spell rung is packed as runes, totalled across every stop that casts; the rune
     * pouch gets no line of its own. A Spellbook Swap rung packs the swap's runes and the
     * second spell's together. Reads each route's chosen needs, so an "or" contributes
     * only the branch being taken.
     */
    private static void addTravel(List<FarmingCarryItem> carry, List<StopPlan> plans,
            PlayerState state, int curePlantCasts, int fertileCasts) {
        List<TravelLine> travelLines = new ArrayList<>();
        Map<Integer, Integer> tabletsNeeded = new LinkedHashMap<>();
        Map<Integer, String> tabletNames = new LinkedHashMap<>();
        Map<Rune, Integer> runesNeeded = new EnumMap<>(Rune.class);
        // Cure Plant runes merge here with the teleport runes, so each rune gets one line.
        if (curePlantCasts > 0) {
            CURE_PLANT_RUNES.forEach((rune, n) -> runesNeeded.merge(rune, curePlantCasts * n, Integer::sum));
        }
        // Places already paid for, so several stops at one of them cost one trip.
        Set<Arrival> arrivedAt = EnumSet.noneOf(Arrival.class);

        for (StopPlan plan : plans) {
            if (!plan.isVisit() || plan.getRoute() == null) {
                continue;
            }
            // Teleports are counted per arrival, not per stop: Catherby's three patches cost
            // one teleport. Arrival is the same key the itinerary groups visits on.
            boolean firstTimeHere = arrivedAt.add(plan.getStop().getArrival());

            for (Capability capability : plan.getNeeds()) {
                CarryItems.Carry carryItem = CarryItems.forCapability(CapabilitySources.packedAs(capability, state));
                if (carryItem != null) {
                    addTravelItem(travelLines, carryItem, state);
                    continue;
                }
                // A spell capability with no item, such as the respawn teleport: runes,
                // one cast per arrival.
                if (!firstTimeHere) {
                    continue;
                }
                Teleports.runesFor(capability).forEach((rune, n) -> runesNeeded.merge(rune, n, Integer::sum));
            }

            TeleportMeans means = plan.getTeleport();
            if (means == null || !firstTimeHere) {
                continue;
            }
            if (means.isTablet()) {
                tabletsNeeded.merge(means.getItemId(), 1, Integer::sum);
                tabletNames.putIfAbsent(means.getItemId(), means.getDisplayName());
            } else {
                means.getRunes().forEach((rune, n) -> runesNeeded.merge(rune, n, Integer::sum));
            }
        }

        // Fertile Soil's runes, one cast a composted patch, in place of carried compost. Not
        // capped: the player chose the spell, so a shortfall shows as runes to fetch.
        if (fertileCasts > 0) {
            FertileSoil.runesPerCast().forEach((rune, n) -> runesNeeded.merge(rune, fertileCasts * n, Integer::sum));
        }
        travelLines.forEach(line -> carry.add(line.item(state)));

        tabletsNeeded.forEach((itemId, count) ->
                carry.add(item(FarmingCarryItem.Kind.TRAVEL, itemId, tabletNames.get(itemId), count, count, state)));
        runesNeeded.forEach((rune, count) ->
                carry.add(item(FarmingCarryItem.Kind.RUNES, rune.getItemId(), rune.getDisplayName(), count, count, state)));
    }

    /**
     * Adds a travel item to the trip, one line per item however many stops use it. Every
     * charge tier counts towards a line, since each tier is a different item id.
     *
     * <p>Carries that one owned item covers share a line, which then accepts only the ids
     * they share: a max cape covers the Farming and the Construction cape alike, and the
     * elite Ardougne cloak covers the easy one's teleport.
     */
    private static void addTravelItem(List<TravelLine> lines, CarryItems.Carry carry, PlayerState state) {
        int[] ids = carry.getItemIds();
        for (TravelLine line : lines) {
            int[] shared = Arrays.stream(line.accepted).filter(id -> Arrays.stream(ids).anyMatch(other -> other == id)).toArray();
            // Two lines already filled from the player's own pockets stay apart: folding them
            // onto a banked shared item would send the player to the bank for nothing.
            boolean bothCarried = carriedAny(state, line.accepted) && carriedAny(state, ids);
            if (bestHeld(state, shared) != 0 && (carriedAny(state, shared) || !bothCarried)) {
                line.accepted = shared;
                line.folded |= line.carry.getItemId() != carry.getItemId();
                return;
            }
            if (line.carry.getItemId() == carry.getItemId()) {
                return;
            }
        }
        lines.add(new TravelLine(carry));
    }

    /** A travel line: the carry that opened it and the ids still able to fill it. */
    private static final class TravelLine {
        final CarryItems.Carry carry;
        int[] accepted;
        /** Whether another carry shares the line, which the item the player holds then names. */
        boolean folded;

        TravelLine(CarryItems.Carry carry) {
            this.carry = carry;
            this.accepted = carry.getItemIds();
        }

        FarmingCarryItem item(PlayerState state) {
            if (!folded) {
                return CarryPlanner.item(FarmingCarryItem.Kind.TRAVEL, carry.getItemId(), carry.getName(), 0, 1, state, accepted);
            }
            int held = bestHeld(state, accepted);
            String name = Loadouts.nameOf(held);
            return CarryPlanner.item(FarmingCarryItem.Kind.TRAVEL, held, name != null ? name : carry.getName(), 0, 1, state, accepted);
        }
    }

    /** The first of these on the player, else the first they own, else 0. */
    private static int bestHeld(PlayerState state, int[] itemIds) {
        return Arrays.stream(itemIds).filter(itemId -> ItemCounts.countCarried(state, itemId) > 0).findFirst()
                .orElseGet(() -> bestOwned(state, itemIds, 0));
    }

    /** True when any of these ids is on the player right now. */
    private static boolean carriedAny(PlayerState state, int... itemIds) {
        return Arrays.stream(itemIds).anyMatch(itemId -> ItemCounts.countCarried(state, itemId) > 0);
    }

    /**
     * A tool line: no count shown, satisfied by one.
     *
     * @param acceptedIds as for {@link #item}; none means only {@code itemId}
     */
    private static FarmingCarryItem tool(int itemId, String name, PlayerState state, int... acceptedIds) {
        return item(FarmingCarryItem.Kind.TOOL, itemId, name, 0, 1, state, acceptedIds);
    }

    /**
     * One line of the bag, with the item's availability: carried, at the tool leprechaun,
     * in the seed box, banked, or missing.
     *
     * @param iconItemId  the item drawn and tagged, which is one of the accepted ids
     * @param quantity    what the run needs, or 0 when the count is not the point
     * @param needed      how many make the line satisfied — one, for the lines that show no
     *                    count at all
     * @param acceptedIds every id that counts towards the line, for a line more than one item
     *                    can satisfy — a tree needs a seed <em>or</em> the sapling it becomes,
     *                    and either one fills the patch. None means only {@code iconItemId}
     */
    private static FarmingCarryItem item(FarmingCarryItem.Kind kind, int iconItemId,
            String name, int quantity, int needed, PlayerState state, int... acceptedIds) {
        int[] ids = acceptedIds.length == 0 ? new int[]{iconItemId} : acceptedIds;
        int carried = 0;
        int stored = 0;
        int owned = 0;
        for (int itemId : ids) {
            carried += ItemCounts.countCarried(state, itemId);
            stored += ItemCounts.countInLeprechaunStorage(state, itemId);
            owned += ItemCounts.countOwned(state, itemId);
        }
        Availability availability;
        if (carried >= needed) {
            availability = Availability.CARRIED;
        } else if (carried + stored >= needed) {
            // Ranked above BANKED: the leprechaun is at the patch, so nothing needs fetching.
            availability = Availability.AT_LEPRECHAUN;
        } else if (owned >= needed) {
            availability = theBoxWouldFinishIt(state, needed - carried - stored, owned - carried - stored, ids)
                    ? Availability.IN_SEED_BOX : Availability.BANKED;
        } else {
            availability = Availability.MISSING;
        }
        return new FarmingCarryItem(kind, iconItemId, name, quantity, availability, CropData.tagForPlantable(iconItemId), "", carried);
    }

    /**
     * True when only a carried seed box with unconfirmed contents could, by its tally, cover
     * the shortfall: the bank and seed vault cannot, and the box would cover all of it.
     *
     * <p>The tally is summed from chat lines and trusted per seed ({@code SeedBox.trustedOf}).
     * An unconfirmed tally may hold seeds that are long gone, so where the bank or vault holds
     * enough the line stays a withdrawal: a needless withdrawal costs a click; a skipped one
     * costs the planting. {@code IN_SEED_BOX} asks the player to check the box only where both fall
     * short. Checking prints the contents to chat, which settles the tally.
     *
     * <p>Whole shortfall or nothing: the bank row drops an {@code IN_SEED_BOX} item from its
     * fetch list, so a partial cover falls through to {@code BANKED}. Downstream counts still
     * treat the line as a withdrawal, in case the box turns out empty.
     *
     * @param shortOf how many the line still wants after loose carried items and leprechaun
     *                storage are counted
     * @param ownedElsewhere what the player owns beyond those, untrusted box seeds included
     */
    private static boolean theBoxWouldFinishIt(PlayerState state, int shortOf, int ownedElsewhere, int... acceptedIds) {
        if (shortOf <= 0 || !SeedBox.carried(state)) {
            return false;
        }
        int unconfirmed = 0;
        for (int itemId : acceptedIds) {
            int tallied = SeedBox.tallied(state, itemId);
            unconfirmed += tallied - SeedBox.trustedOf(state, itemId, tallied);
        }
        return unconfirmed >= shortOf && ownedElsewhere - unconfirmed < shortOf;
    }
}
