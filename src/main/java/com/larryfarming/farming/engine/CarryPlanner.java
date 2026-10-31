package com.larryfarming.farming.engine;

import com.larryfarming.engine.ItemCounts;
import com.larryfarming.engine.SeedBox;
import com.larryfarming.engine.farming.Crop;
import com.larryfarming.engine.farming.CropState;
import com.larryfarming.engine.farming.FarmingWorldMap;
import com.larryfarming.engine.farming.PatchPrediction;
import com.larryfarming.engine.farming.PatchType;
import com.larryfarming.farming.catalogue.CarryItems;
import com.larryfarming.farming.catalogue.CompostUpgrade;
import com.larryfarming.farming.catalogue.CropData;
import com.larryfarming.farming.catalogue.Loadouts;
import com.larryfarming.farming.catalogue.Teleports;
import com.larryfarming.farming.detection.CapabilityResolver;
import com.larryfarming.farming.detection.CapabilitySources;
import com.larryfarming.farming.detection.CapabilityState;
import com.larryfarming.farming.detection.RingEnergyCharges;
import com.larryfarming.farming.model.Arrival;
import com.larryfarming.farming.model.Capability;
import com.larryfarming.farming.model.FarmingPreferences;
import com.larryfarming.farming.model.PatchRef;
import com.larryfarming.farming.model.Rune;
import com.larryfarming.farming.model.RunStop;
import com.larryfarming.farming.model.TeleportMeans;
import com.larryfarming.model.EquipmentSlot;
import com.larryfarming.model.ItemStack;
import com.larryfarming.model.PlayerState;
import com.larryfarming.model.QuestStatus;
import com.larryfarming.model.Skill;
import com.larryfarming.snapshot.FarmingCarryItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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

    private static final int SPADE = 952;
    private static final int SEED_DIBBER = 5343;
    private static final int BOTTOMLESS_COMPOST_BUCKET = 22997;
    /** Both ids the bucket exists as; the resolver treats either as owning one. */
    private static final int[] BOTTOMLESS_COMPOST_BUCKET_IDS = {22994, 22997};
    private static final int ULTRACOMPOST = 21483;

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

    /**
     * Every axe, best first; the line draws whichever one the player has.
     *
     * <p>Ordered by metal tier, with each Forestry two-handed felling axe right after the
     * one-handed axe of the same metal. Within a tier the one-handed axe comes first because
     * it leaves the shield slot free.
     */
    private static final int[] AXES = {
        20011, 28226, // 3rd age
        23673, 28220, // crystal
        13241,        // infernal
        6739, 28217,  // dragon
        23279,        // gilded
        1359, 28214,  // rune
        1357, 28211,  // adamant
        1355, 28208,  // mithril
        1361, 28205,  // black
        1353, 28202,  // steel
        1349, 28199,  // iron
        1351, 28196,  // bronze
    };

    /**
     * What to draw when the player has no axe: the bronze axe, not the first of
     * {@link #AXES}, which would show a third age axe as the thing to fetch.
     */
    private static final int PLAIN_AXE = 1351;

    /** The log basket and the forestry basket, each in its open and closed form. */
    private static final int[] LOG_BASKETS = {28140, 28142, 28143, 28145};

    /**
     * The seed box, closed and open. Must match the ids {@code SeedBox} gates its contents
     * on, or the line never ticks.
     */
    private static final int[] SEED_BOX_IDS = {ItemID.SEED_BOX, ItemID.SEED_BOX_OPEN};

    /** What to carry for the guild's big bin, {@link CompostUpkeep#BANK_FILL} as item ids. */
    private static final int[] FILL_ITEMS = fillItems();

    private static int[] fillItems() {
        int[] ids = new int[CompostUpkeep.BANK_FILL.size()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = CompostUpkeep.BANK_FILL.get(i).getItemId();
        }
        return ids;
    }

    /**
     * The bag for a trip, which may be one run or several walked as one circuit.
     *
     * <p>Takes every stop at once rather than being called per run: runes and tablets are
     * deduplicated per <em>arrival</em>, and that dedupe must span run boundaries. Seeds
     * arrive already split per run and are merged here.
     *
     * @param plans the stops as planned, so only routes actually being taken are packed
     *              for; in any order
     * @param seeds one allocation per run, in any order
     */
    public static List<FarmingCarryItem> plan(List<StopPlan> plans, List<SeedPlan> seeds,
            PlayerState state, CapabilityResolver resolver) {
        return plan(plans, seeds, null, state, resolver);
    }

    /**
     * The same bag, told what each stop is planting.
     *
     * @param cropFor what goes in the ground at each stop, index-aligned with {@code plans},
     *                or null where the caller does not know. The seed lines and the axe both
     *                read it. Null falls back to the allocation, which packs every seed
     *                dealt and keeps the axe on the list
     */
    public static List<FarmingCarryItem> plan(List<StopPlan> plans, List<SeedPlan> seeds,
            List<List<Crop>> cropFor, PlayerState state, CapabilityResolver resolver) {
        return plan(plans, seeds, cropFor, state, resolver, FarmingInputs.none());
    }

    /**
     * The same bag again, told what is standing in the patches.
     *
     * @param inputs what has been seen at each patch: decides whether a patch holds a tree to
     *               fell and whether it is diseased. An unseen patch keeps the axe on the list
     */
    public static List<FarmingCarryItem> plan(List<StopPlan> plans, List<SeedPlan> seeds,
            List<List<Crop>> cropFor, PlayerState state, CapabilityResolver resolver,
            FarmingInputs inputs) {
        List<FarmingCarryItem> carry = new ArrayList<>();

        carry.add(tool(SPADE, "Spade", state));
        // Otto's planting lesson retires the dibber permanently.
        if (resolver.resolve(Capability.BARE_HAND_PLANTING, state).getState()
                != CapabilityState.HAVE) {
            carry.add(tool(SEED_DIBBER, "Seed dibber", state));
        }
        // A Fertile Soil caster carries the spell's runes and ash instead of compost; see
        // addTravel and addCompostUpgrades.
        boolean fertile = FertileSoil.inUse(
                (inputs != null ? inputs : FarmingInputs.none()).getPreferences(), state,
                resolver.getBook());
        int fertileCasts = fertile ? FertileSoil.castsFor(plans, cropFor) : 0;
        boolean compost = packsCompost(plans, fertile);
        // Otherwise one compost line: the bottomless bucket if owned, loose ultracompost.
        // Ownership goes through the resolver so the bag agrees with the rest of the plugin
        // about the bucket's two ids.
        if (compost && resolver.resolve(Capability.BOTTOMLESS_COMPOST_BUCKET, state).getState()
                == CapabilityState.HAVE) {
            // No AT_LEPRECHAUN branch: the bucket's varbits (7915/7916) describe its
            // contents wherever it is, and nothing observable says the leprechaun holds it.
            // A stored bucket reads as BANKED, one withdrawal too many rather than none.
            boolean inTheBag = carriedAny(state, BOTTOMLESS_COMPOST_BUCKET_IDS);
            carry.add(new FarmingCarryItem(FarmingCarryItem.Kind.TOOL,
                    BOTTOMLESS_COMPOST_BUCKET, "Bottomless compost bucket", 0,
                    inTheBag
                            ? FarmingCarryItem.Availability.CARRIED
                            : FarmingCarryItem.Availability.BANKED,
                    // Carried count is capped at one: a second bottomless bucket is no spare.
                    "", "", inTheBag ? 1 : 0));
        } else if (compost) {
            carry.add(tool(ULTRACOMPOST, "Ultracompost", state));
        }

        int clearingCoins = addTreeTools(carry, plans, cropFor, state, inputs);
        addStaff(carry, plans, state, resolver);
        addSecateurs(carry, plans, state, resolver);
        int curePlantCasts = addCureTools(carry, plans, state, inputs);
        addDivingGear(carry, plans, state);
        addProtectionPayments(carry, plans, cropFor, state);
        // What is actually going in the ground, not what the rotation dealt out: a seat can
        // decline its dealt seed, such as a fruit tree left standing because no sapling on
        // hand matches or beats it, as the replant setting asks.
        List<SeedAllocation.Tally> planting = cropFor == null
                ? SeedPlan.shoppingList(seeds)
                : SeedPlan.goingInTheGround(plans, cropFor);
        addSeedBox(carry, planting, state);
        addSeeds(carry, planting, state);
        addFixedSeeds(carry, plans, state, inputs != null ? inputs : FarmingInputs.none());
        addTravel(carry, plans, state, curePlantCasts,
                Resurrection.castsToPack(plans, state), fertileCasts);
        addCoins(carry, plans, state, clearingCoins);
        addEnergyPotion(carry, state);
        // InventoryDemands charges slots off the same BinNeeds reading, so the bag and the
        // simulation agree on what the bins need.
        BinNeeds bins = BinNeeds.over(plans, inputs, state);
        addGuildBinFill(carry, bins, state);
        addCompostUpgrades(carry, bins, fertileCasts * FertileSoil.ASH_PER_CAST, state);

        return withoutSeedsTheyDoNotHave(carry);
    }

    /**
     * What it takes to reach these stops and nothing more: the travel items, runes and
     * fares, plus the fairy-ring staff and the secateurs it pushes out of the weapon slot.
     *
     * <p>A bank at a stop's arrival opens only once the player is there, so the way there
     * has to come out of an earlier bank. {@link #withoutTravel} strips the same things from
     * the stop, so the two halves add up to {@link #plan}.
     */
    static List<FarmingCarryItem> travelFor(List<StopPlan> plans, PlayerState state,
            CapabilityResolver resolver) {
        List<FarmingCarryItem> carry = new ArrayList<>();
        addStaff(carry, plans, state, resolver);
        addSecateurs(carry, plans, state, resolver);
        addTravel(carry, plans, state, 0, 0, 0);
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
        if (line.getItemId() == RING_OF_CHAROS_ACTIVATED
                || line.getAvailability() == FarmingCarryItem.Availability.AT_LEPRECHAUN) {
            return false;
        }
        for (int staff : STAFF_IDS) {
            if (line.getItemId() == staff) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a {@link #travelFor} line is used up on the way: a stack of tablets, one
     * broken per arrival, or runes, withdrawn to the cast. Coins carry a count too, but a
     * fare leaves the stack in the bag.
     */
    static boolean spentOnTheWay(FarmingCarryItem line) {
        return line.getKind() == FarmingCarryItem.Kind.RUNES
                || line.getKind() == FarmingCarryItem.Kind.TRAVEL && line.getQuantity() > 0
                        && line.getItemId() != COINS;
    }

    /**
     * Every item id that counts for the same packing-list line as this one: each axe, basket,
     * staff and bucket form, every charge tier of a travel item, and a crop's seed, seedlings
     * and sapling. A line names one id; the bag may hold any of these.
     */
    static Set<Integer> formsOf(int itemId) {
        for (int[] family : new int[][]{AXES, LOG_BASKETS, STAFF_IDS,
                BOTTOMLESS_COMPOST_BUCKET_IDS}) {
            for (int id : family) {
                if (id == itemId) {
                    return idsOf(family);
                }
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
        Set<Integer> ids = new HashSet<>();
        for (int id : family) {
            ids.add(id);
        }
        return ids;
    }

    /** The same stop with no way of getting there, for a stretch already standing at it. */
    static StopPlan withoutTravel(StopPlan plan) {
        return new StopPlan(plan.getStop(), plan.getOutcome(), null, plan.getConfidence(),
                plan.getBecause(), plan.getSkipReason(), plan.getReadyAtEpochSec(), Set.of(),
                null);
    }

    /**
     * The bag, less the seeds the player owns none of.
     *
     * <p>The stop that wanted an unowned seed already names the shortfall, so listing the
     * seed here would ask for a planting the run has dropped. Seeds only: an unowned tool
     * stays on the list, because a missing spade or axe costs the whole trip.
     */
    private static List<FarmingCarryItem> withoutSeedsTheyDoNotHave(
            List<FarmingCarryItem> carry) {
        List<FarmingCarryItem> packable = new ArrayList<>(carry.size());
        for (FarmingCarryItem item : carry) {
            if (item.isOwned() || item.getKind() != FarmingCarryItem.Kind.SEED) {
                packable.add(item);
            }
        }
        return packable;
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
    private static void addCoins(List<FarmingCarryItem> carry, List<StopPlan> plans,
            PlayerState state, int clearingCoins) {
        int rides = 0;
        int charters = 0;
        int fares = 0;
        // The same total priced as though the ring of charos(a) were worn.
        int wornFares = 0;
        for (StopPlan plan : plans) {
            if (!plan.isVisit()) {
                continue;
            }
            if (plan.getNeeds() != null
                    && plan.getNeeds().contains(Capability.LOVAKENGJ_MINECART)) {
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
            boolean cabinFever = hasFinishedCabinFever(state);
            fares += charterFare(fare, cabinFever, isWearingCharos(state));
            wornFares += charterFare(fare, cabinFever, true);
            charters++;
        }
        if (rides > 0 && !waivesTheMinecartFare(state)) {
            fares += rides * MINECART_FARE;
            wornFares += rides * MINECART_FARE;
        }
        if (fares == 0 && clearingCoins == 0) {
            return;
        }
        // Priced as the player stands. The worn-ring price rides along as a note, which the
        // panel shows on hover.
        int total = fares + clearingCoins;
        FarmingCarryItem coins = item(FarmingCarryItem.Kind.TRAVEL, COINS,
                "Coins", total, total, state);
        String trees = clearingCoins > GARDENER_FEE ? "trees" : "a tree";
        String note = clearingCoins == 0 ? "For boat and minecart fares."
                : fares == 0 ? "To have " + trees + " cleared."
                : "For fares, and to have " + trees + " cleared.";
        if (charters > 0 && ownsCharos(state) && !isWearingCharos(state)) {
            note = note + " " + (wornFares + clearingCoins)
                    + " if you wear your ring of charos(a).";
        }
        coins = withNote(coins, note);
        carry.add(coins);
        // The ring gets its own line, kept once worn. Only for a player who owns one, since
        // it is a Garden of Tranquillity reward.
        if (charters > 0 && ownsCharos(state)) {
            carry.add(withNote(item(FarmingCarryItem.Kind.TRAVEL, RING_OF_CHAROS_ACTIVATED,
                    "Ring of charos(a)", 0, 1, state), "Halves charter fares when worn."));
        }
    }

    private static FarmingCarryItem withNote(FarmingCarryItem item, String note) {
        return new FarmingCarryItem(item.getKind(), item.getItemId(), item.getName(),
                item.getQuantity(), item.getAvailability(), item.getTag(), note,
                item.getCarriedCount());
    }

    /** Whether this route pays a charter captain, the only fares the discounts apply to. */
    private static boolean isCharter(StopPlan plan) {
        return plan.getNeeds() != null && plan.getNeeds().contains(Capability.CHARTER_SHIP);
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

    private static boolean hasFinishedCabinFever(PlayerState state) {
        return state != null
                && state.getQuests().get("Cabin Fever") == QuestStatus.COMPLETED;
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
        return ItemCounts.countOwned(state, RING_OF_CHAROS_ACTIVATED, null) > 0;
    }

    private static boolean waivesTheMinecartFare(PlayerState state) {
        return state != null
                && state.getQuests().get("The Forsaken Tower") == QuestStatus.COMPLETED;
    }

    private static final int COINS = 995;

    /** Coins per Lovakengj minecart ride. */
    private static final int MINECART_FARE = 20;

    /** The activated ring. The plain one (4202) discounts nothing. */
    private static final int RING_OF_CHAROS_ACTIVATED = 6465;

    /**
     * The two ways to cure a diseased crop, and which patches take which.
     *
     * <p>From the wiki: "Players may treat plants in diseased herb, flower, allotment, hops,
     * hardwood tree, celastrus, belladonna, cactus, and mushroom patches with plant cure...
     * For diseased trees, fruit trees, spirit trees and bushes, the player may use secateurs
     * on the plant to trim the diseased branch." Hardwood trees take plant cure, so the
     * split cannot be derived from whether the patch grows a tree.
     *
     * <p>Deliberately not exhaustive over {@link PatchType}. A patch the wiki sentence does
     * not name (coral, for one, can take disease) draws no cure line, because a wrong cure
     * is a wasted slot.
     */
    private static final Set<PatchType> PRUNED_WITH_SECATEURS = EnumSet.of(
            PatchType.TREE, PatchType.FRUIT_TREE, PatchType.SPIRIT_TREE, PatchType.BUSH);

    private static final Set<PatchType> CURED_WITH_PLANT_CURE = EnumSet.of(
            PatchType.HERB, PatchType.FLOWER, PatchType.ALLOTMENT, PatchType.HOPS,
            PatchType.HARDWOOD_TREE, PatchType.CELASTRUS, PatchType.BELLADONNA,
            PatchType.CACTUS, PatchType.MUSHROOM);

    /**
     * Shares one leprechaun slot with magic secateurs, which {@code ItemCounts}
     * disambiguates with the type varbit.
     */
    private static final int PLAIN_SECATEURS = 5329;

    /** Either opens a fairy ring; the dramen staff first, as the one a player meets first. */
    private static final int[] STAFF_IDS = {Loadouts.DRAMEN_STAFF, Loadouts.LUNAR_STAFF};

    /** Consumed per patch cured, so this line carries a count. */
    private static final int PLANT_CURE = 6036;

    /**
     * Cure Plant, the Lunar spell that cures one patch per cast for no inventory slot.
     *
     * <p>Elemental staves are not modelled for any spell, so a staff of earth user is
     * over-asked eight earth runes a cast.
     */
    private static final int CURE_PLANT_MAGIC_LEVEL = 66;
    private static final int EARTH_PER_CURE_PLANT = 8;
    private static final int ASTRAL_PER_CURE_PLANT = 1;


    /** The gardener's fee to clear a regular, fruit or hardwood tree. */
    private static final int GARDENER_FEE = 200;

    /**
     * The Woodcutting level to chop each lumber tree once grown, the same as the wild tree.
     * Fruit trees chop at any level.
     */
    private static final Map<Crop, Integer> WOODCUTTING_LEVEL = Map.of(
            Crop.OAK, 15, Crop.WILLOW, 30, Crop.TEAK, 35, Crop.MAPLE, 45,
            Crop.MAHOGANY, 50, Crop.YEW, 60, Crop.CAMPHOR, 66, Crop.MAGIC, 75,
            Crop.IRONWOOD, 80, Crop.ROSEWOOD, 92);

    /** How a grown tree comes out of its patch before the next one goes in. */
    private enum Clearing {
        /** Chopped down, then the stump dug up. */
        AXE,
        /** Cleared by the patch's gardener for {@link #GARDENER_FEE}, no logs given. */
        GARDENER
    }

    /** What the trees being replaced ask of the bag. */
    private static final class TreeWork {
        private boolean axe;
        private boolean logs;
        private int coins;
    }

    /**
     * The axe for any tree being chopped, the log basket for runs that produce logs, and
     * the gardener's coins for any tree above the player's Woodcutting level.
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
            carry.add(item(FarmingCarryItem.Kind.TOOL, bestOwned(state, AXES, PLAIN_AXE),
                    "Axe", 0, 1, state, AXES));
        }
        if (work.logs) {
            FarmingCarryItem basket = item(FarmingCarryItem.Kind.TOOL, LOG_BASKETS[0],
                    "Log basket", 0, 1, state, LOG_BASKETS);
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
    private static final Set<Capability> CAPE_SLOT_TELEPORTS = EnumSet.of(
            Capability.ARDOUGNE_CLOAK_1, Capability.ARDOUGNE_CLOAK_2,
            Capability.ARDOUGNE_CLOAK_3, Capability.ARDOUGNE_CLOAK_4,
            Capability.FARMING_CAPE, Capability.CONSTRUCTION_CAPE, Capability.FISHING_CAPE,
            Capability.HUNTER_CAPE, Capability.QUEST_CAPE, Capability.DIARY_CAPE);

    /**
     * Where the log basket goes on this trip.
     *
     * <p>The basket is wearable in the cape slot, so worn it costs no inventory slot. A
     * route that teleports by a cape-slot item takes the slot first. Either way one of the
     * two takes an inventory slot, so the cape is worn for its teleport and the basket
     * rides in the bag.
     */
    static BasketPlace basketPlace(List<StopPlan> plans, List<List<Crop>> cropFor,
            PlayerState state, FarmingInputs inputs) {
        if (state == null || bestOwned(state, LOG_BASKETS, 0) == 0
                || !treeWorkIn(plans, cropFor, state, inputs).logs) {
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
    static Set<Integer> treeToolsFor(List<StopPlan> plans, List<List<Crop>> cropFor,
            PlayerState state, FarmingInputs inputs) {
        TreeWork work = treeWorkIn(plans, cropFor, state, inputs);
        Set<Integer> tools = new HashSet<>();
        if (work.axe) {
            tools.add(PLAIN_AXE);
        }
        if (work.coins > 0) {
            tools.add(COINS);
        }
        return tools;
    }

    /** What clearing these stops' grown trees asks of the bag, patch by patch. */
    private static TreeWork treeWorkIn(List<StopPlan> plans, List<List<Crop>> cropFor,
            PlayerState state, FarmingInputs inputs) {
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
            for (PatchRef ref : plan.getStop().getPatches()) {
                FarmingWorldMap.PatchDef def = ref.resolve();
                if (def == null) {
                    continue;
                }
                // Asked per patch, because one stop can cover several patches (the three
                // Fossil Island hardwoods are one stop).
                Clearing clearing = clearingAt(def.getType(), ref, state, inputs);
                if (clearing == Clearing.AXE) {
                    work.axe = true;
                    work.logs |= def.getType() == PatchType.TREE
                            || def.getType() == PatchType.HARDWOOD_TREE;
                } else if (clearing == Clearing.GARDENER) {
                    work.coins += GARDENER_FEE;
                }
            }
        }
        return work;
    }

    /**
     * How the tree in this patch comes out, or null when nothing stands to be cleared.
     *
     * <p>Calquat is absent: per the wiki it is cleared with a spade like a bush, even though
     * it is classified as a tree. No run visits a redwood, celastrus or crystal tree patch.
     */
    private static Clearing clearingAt(PatchType type, PatchRef ref, PlayerState state,
            FarmingInputs inputs) {
        if (type != PatchType.TREE && type != PatchType.HARDWOOD_TREE
                && type != PatchType.FRUIT_TREE) {
            return null;
        }
        if (nothingToFellAt(inputs, ref)) {
            return null;
        }
        // A fruit tree chops at any level.
        if (type == PatchType.FRUIT_TREE) {
            return Clearing.AXE;
        }
        return tooHighToChop(inputs == null ? null : inputs.patchAt(ref), state)
                ? Clearing.GARDENER : Clearing.AXE;
    }

    /**
     * Whether the player's Woodcutting level is below the standing tree's. An unseen tree
     * keeps the axe.
     */
    private static boolean tooHighToChop(PatchPrediction seen, PlayerState state) {
        if (seen == null || seen.getCrop() == null || state == null) {
            return false;
        }
        Integer needs = WOODCUTTING_LEVEL.get(seen.getCrop());
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
    private static void addDivingGear(List<FarmingCarryItem> carry, List<StopPlan> plans,
            PlayerState state) {
        if (!anyDiveAmong(plans)) {
            return;
        }
        if (ItemCounts.countOwned(state, MEDALLION_OF_THE_DEEP, null) > 0) {
            carry.add(withNote(tool(MEDALLION_OF_THE_DEEP, "Medallion of the deep", state),
                    "Wear it instead of the helmet and apparatus."));
            return;
        }
        carry.add(withNote(tool(FISHBOWL_HELMET, "Fishbowl helmet", state),
                "Wear both. The oxygen lasts only while you wear them."));
        carry.add(tool(DIVING_APPARATUS, "Diving apparatus", state));
    }

    /**
     * The farmers' fees, for the stops that pay one. {@link Protection} pays only from a fee
     * the player already owns, so this line never asks for something to gather, and
     * {@code InventoryDemands} keeps a slot for exactly these.
     *
     * @param cropFor what goes in the ground at each stop, or null, which pays for every
     *                patch at the stop where the fee does not follow the crop
     */
    private static void addProtectionPayments(List<FarmingCarryItem> carry,
            List<StopPlan> plans, List<List<Crop>> cropFor, PlayerState state) {
        for (int index = 0; index < plans.size(); index++) {
            StopPlan plan = plans.get(index);
            if (!plan.isVisit()) {
                continue;
            }
            Protection.Fee fee = cropFor == null
                    ? Protection.paymentAt(plan.getStop(), plan.getStop().getPatches().size(),
                            null, state)
                    : Protection.paymentAt(plan.getStop(), cropFor.get(index), state);
            if (fee == null) {
                continue;
            }
            carry.add(withNote(item(FarmingCarryItem.Kind.TOOL, fee.itemId, fee.name,
                    fee.amount, fee.amount, state), fee.farmer + "'s protection fee."));
        }
    }

    /**
     * Whether the bag packs its compost line, the bottomless bucket or loose ultracompost.
     * The packing list and the bag simulation both ask this, so they agree on the bucket's
     * slot. A trip of only anima plants or coral takes no compost and leaves the line off. A
     * Fertile Soil trip leaves it off too, unless a bin empties into the bucket or a seaweed
     * patch keeps its compost.
     *
     * @param fertile whether the trip casts Fertile Soil
     */
    static boolean packsCompost(List<StopPlan> plans, boolean fertile) {
        return usesCompost(plans) && (!fertile || FertileSoil.keepsTheCompost(plans));
    }

    /**
     * Whether any visited stop on this trip treats its patch or empties a bin into the
     * bucket. A skipped or unreachable stop does nothing there, so it packs nothing, the
     * same rule {@link FertileSoil#keepsTheCompost} follows.
     */
    private static boolean usesCompost(List<StopPlan> plans) {
        for (StopPlan plan : plans) {
            if (!plan.isVisit()) {
                continue;
            }
            List<RunStop.StopAction> actions = plan.getStop().getActions();
            if (actions.contains(RunStop.StopAction.COMPOST)
                    || actions.contains(RunStop.StopAction.EMPTY_COMPOST_BIN)) {
                return true;
            }
        }
        return false;
    }

    /** Whether any stop on this trip is a seaweed or coral patch. */
    private static boolean anyDiveAmong(List<StopPlan> plans) {
        for (StopPlan plan : plans) {
            if (!plan.isVisit()) {
                continue;
            }
            for (PatchRef ref : plan.getStop().getPatches()) {
                FarmingWorldMap.PatchDef def = ref.resolve();
                if (def != null && (def.getType() == PatchType.SEAWEED
                        || def.getType() == PatchType.CORAL)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether this stop puts nothing in the ground, so nothing there needs clearing first.
     * A null or short {@code cropFor} answers false, keeping the axe.
     */
    private static boolean nothingIsPlantedAt(List<List<Crop>> cropFor, int index) {
        return cropFor != null && index < cropFor.size() && cropFor.get(index).isEmpty();
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
    private static int addCureTools(List<FarmingCarryItem> carry, List<StopPlan> plans,
            PlayerState state, FarmingInputs inputs) {
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
                FarmingWorldMap.PatchDef def = ref.resolve();
                if (def == null || !standsDiseased(inputs, ref) || !counted.add(ref)) {
                    continue;
                }
                // Counted before the tables: a cast cures any patch type.
                sick++;
                if (PRUNED_WITH_SECATEURS.contains(def.getType())) {
                    // Secateurs are not used up, so one pair covers the run.
                    prune = true;
                } else if (CURED_WITH_PLANT_CURE.contains(def.getType())) {
                    cures++;
                }
            }
        }
        if (sick > 0 && canCastCurePlant(state) && hasRunesToCure(state, sick)) {
            return sick;
        }
        // Magic secateurs prune as well as any pair, and a player who owns them already
        // brings them: the weapon slot asks for them, or the bag does beside the staff.
        if (prune && ItemCounts.countOwned(state, Loadouts.MAGIC_SECATEURS, null) == 0) {
            carry.add(tool(PLAIN_SECATEURS, "Secateurs", state));
        }
        if (cures > 0) {
            carry.add(item(FarmingCarryItem.Kind.TOOL, PLANT_CURE, "Plant cure", cures,
                    cures, state, PLANT_CURE));
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
        return ItemCounts.countOwned(state, Rune.EARTH.getItemId(), null)
                        >= casts * EARTH_PER_CURE_PLANT
                && ItemCounts.countOwned(state, Rune.ASTRAL.getItemId(), null)
                        >= casts * ASTRAL_PER_CURE_PLANT;
    }

    /**
     * Whether this player can cure a patch with a spell rather than a bottle.
     *
     * <p>Being on the Lunar spellbook implies Lunar Diplomacy is done. The book comes from
     * the varbit, not the resolver, which carries the book the <em>run</em> is written for.
     * Uses the real Magic level, so a boost or an unread level falls back to the bottle.
     */
    private static boolean canCastCurePlant(PlayerState state) {
        if (state == null) {
            return false;
        }
        Map<Integer, Integer> varbits = state.getVarbits();
        Integer book = varbits == null ? null : varbits.get(CapabilitySources.SPELLBOOK_VARBIT);
        return book != null && book == CapabilitySources.LUNAR_SPELLBOOK
                && state.getSkillLevel(Skill.MAGIC) >= CURE_PLANT_MAGIC_LEVEL;
    }

    /** Whether this patch has been seen diseased. */
    private static boolean standsDiseased(FarmingInputs inputs, PatchRef ref) {
        PatchPrediction seen = inputs == null ? null : inputs.patchAt(ref);
        return seen != null && seen.getState() == CropState.DISEASED;
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
        PatchPrediction seen = inputs == null ? null : inputs.patchAt(ref);
        if (seen == null) {
            return false;
        }
        if (seen.getState() == CropState.DEAD || seen.getState() == CropState.DISEASED
                || seen.isIdle()) {
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
        return seen.getState() == CropState.GROWING && now > 0
                && seen.readinessAt(now) == PatchPrediction.Readiness.GROWING;
    }

    /**
     * One energy potion, when the bar is low (see {@link EnergyTopUp}).
     *
     * <p>One potion, not the dose count, to save slots; the dose count only words the note.
     * Kept even when unowned, unlike a seed.
     */
    private static void addEnergyPotion(List<FarmingCarryItem> carry, PlayerState state) {
        if (!EnergyTopUp.worthCarrying(state)) {
            return;
        }
        // The ring's free restores are counted first, since they can make the potion unneeded.
        int free = addRingRestores(carry, state);
        if (!EnergyTopUp.worthCarrying(state, free)) {
            return;
        }
        int[] potions = EnergyTopUp.potionIds();
        int best = bestOwned(state, potions, potions[0]);
        FarmingCarryItem line = item(FarmingCarryItem.Kind.TOOL, best, "Energy potion",
                1, 1, state, potions);
        int doses = EnergyTopUp.dosesToFill(state, free);
        String note = "Run energy is " + EnergyTopUp.percentOf(state) + "%. "
                + doses + (doses == 1 ? " dose fills it" : " doses fill it")
                + (free > 0 ? " after your Explorer's ring." : ".");
        carry.add(withNote(line, note));
    }

    /**
     * The Explorer's ring, while it still has a run-energy restore left today. Called only
     * when the bar is low enough to warrant a potion.
     *
     * <p>If the travel lines already list the ring, no second line is added, but its
     * restores still count against the potion.
     *
     * @return everything the ring could put back today, for the potion decision. Zero when
     *         there is no ring, when the diary flags have not been read, or when the day's
     *         restores are spent.
     */
    private static int addRingRestores(List<FarmingCarryItem> carry, PlayerState state) {
        RingEnergyCharges.Charges ring = RingEnergyCharges.of(state);
        if (ring == null || ring.getRestoresLeft() <= 0) {
            return 0;
        }
        int[] ringIds = RingEnergyCharges.ringItemIds();
        if (!alreadyListed(carry, ringIds)) {
            // A TOOL line, beside the potion: in this role the ring replaces a drink.
            FarmingCarryItem line = item(FarmingCarryItem.Kind.TOOL, ring.getItemId(),
                    ring.getName(), 0, 1, state, ringIds);
            carry.add(withNote(line, ring.getRestoresLeft() + " energy "
                    + (ring.getRestoresLeft() == 1 ? "restore" : "restores")
                    + " left today, " + ring.getPercentPerRestore() + "% each."));
        }
        return ring.getFreePercent();
    }

    /** Whether the bag already names one of these items, whatever put it there. */
    private static boolean alreadyListed(List<FarmingCarryItem> carry, int... itemIds) {
        for (FarmingCarryItem line : carry) {
            for (int itemId : itemIds) {
                if (line.getItemId() == itemId) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Produce to carry for the guild's big bin, named at the bank before it.
     *
     * <p>The other bins are filled from the allotments beside them, which costs no bank trip
     * and no inventory slots. The guild has no allotment of its own to
     * spare, and a bank, so there the player's own supercompostable produce tops it up.
     * {@link BinNeeds} counts only what the player owns, so the line never asks them to buy.
     */
    private static void addGuildBinFill(List<FarmingCarryItem> carry, BinNeeds needs,
            PlayerState state) {
        int room = needs.getFillItems();
        if (room == 0) {
            return;
        }
        int best = bestOwned(state, FILL_ITEMS, FILL_ITEMS[0]);
        carry.add(withNote(item(FarmingCarryItem.Kind.TOOL, best, fillNameOf(best),
                        room, room, state, FILL_ITEMS),
                "The Farming Guild's compost bin needs " + room
                        + (room == 1 ? " more item." : " more items.")));
    }

    /** The game's name for a filler, index for index with {@link #FILL_ITEMS}. */
    private static final String[] FILL_NAMES = {
            "Pineapple", "Watermelon", "Coconut", "Papaya fruit", "Poison ivy berries",
            "Jangerberries"};

    private static String fillNameOf(int itemId) {
        for (int i = 0; i < FILL_ITEMS.length; i++) {
            if (FILL_ITEMS[i] == itemId) {
                return FILL_NAMES[i];
            }
        }
        return FILL_NAMES[0];
    }

    /**
     * What the bins finishing on this trip need to come out a tier better.
     *
     * <p>A whole bin upgrades in place only between opening it and taking the first bucket
     * out; after that the upgrade costs a dose and two ash per bucket. So the items must come
     * from the bank before the farm.
     *
     * <p>A plain-compost bin needs both lines: the potion makes supercompost, and the ash
     * then makes ultracompost.
     *
     * <p>{@code InventoryDemands} charges each line one slot off the same {@link BinNeeds}.
     */
    private static void addCompostUpgrades(List<FarmingCarryItem> carry, BinNeeds needs,
            int fertileSoilAsh, PlayerState state) {
        if (needs.getDoses() > 0) {
            carry.add(withNote(potion(needs.getDoses(), state),
                    "One dose turns a finished bin of compost into supercompost."));
        }
        // One ash line for the bins and Fertile Soil together.
        int ash = needs.getAsh() + fertileSoilAsh;
        if (ash > 0) {
            String note = fertileSoilAsh == 0
                    ? "Turns each finished bin of supercompost into ultracompost."
                    : needs.getAsh() == 0
                            ? "Two per Fertile Soil cast, to give ultracompost."
                            : "Two per Fertile Soil cast; the rest upgrades the finished bins.";
            carry.add(withNote(item(FarmingCarryItem.Kind.TOOL, CompostUpgrade.VOLCANIC_ASH,
                    "Volcanic ash", ash, ash, state), note));
        }
    }

    /**
     * The compost potion line, counted in doses rather than in vials.
     *
     * <p>Bypasses {@link #item}, which counts items: one four-dose vial answers four bins, so
     * every count here is multiplied out by {@code dosesIn}. The sprite follows the
     * requirement, not the holding (see {@code CompostUpgrade.potionForDoses}).
     *
     * <p>No {@code AT_LEPRECHAUN} branch: the leprechaun cannot store compost potions.
     *
     * <p>Carrying a count makes it stock to {@code WithdrawalPlanner}, so each bank row asks
     * only for the doses its own stretch will spend.
     */
    private static FarmingCarryItem potion(int doses, PlayerState state) {
        int carried = 0;
        int owned = 0;
        for (int itemId : CompostUpgrade.compostPotions()) {
            int per = CompostUpgrade.dosesIn(itemId);
            carried += ItemCounts.countCarried(state, itemId, null) * per;
            owned += ItemCounts.countOwned(state, itemId, null) * per;
        }
        FarmingCarryItem.Availability availability;
        if (carried >= doses) {
            availability = FarmingCarryItem.Availability.CARRIED;
        } else if (owned >= doses) {
            availability = FarmingCarryItem.Availability.BANKED;
        } else {
            availability = FarmingCarryItem.Availability.MISSING;
        }
        return new FarmingCarryItem(FarmingCarryItem.Kind.TOOL,
                CompostUpgrade.potionForDoses(doses), "Compost potion", doses, availability,
                "", "", carried);
    }

    /**
     * The first of these the player owns, or {@code none} when they own none, including
     * before the bank has been seen.
     */
    private static int bestOwned(PlayerState state, int[] itemIds, int none) {
        for (int itemId : itemIds) {
            if (ItemCounts.countOwned(state, itemId, null) > 0) {
                return itemId;
            }
        }
        return none;
    }

    /**
     * Whether the trip needs a fairy-ring staff wielded: a visit rides a fairy ring, and
     * {@link Capability#STAFFLESS_FAIRY_RINGS} (the elite Lumbridge &amp; Draynor diary) is
     * not known to be held. UNKNOWN keeps the staff.
     *
     * <p>The staff then holds the loadout plan's weapon slot, and the bag lists
     * it too unless it is already on the player (see {@code addStaff}).
     */
    public static boolean staffWielded(List<StopPlan> plans, PlayerState state,
            CapabilityResolver resolver) {
        boolean ringsUsed = false;
        for (StopPlan plan : plans) {
            if (plan.isVisit() && plan.getNeeds().contains(Capability.FAIRY_RINGS)) {
                ringsUsed = true;
                break;
            }
        }
        return ringsUsed && resolver.resolve(Capability.STAFFLESS_FAIRY_RINGS, state)
                .getState() != CapabilityState.HAVE;
    }

    /**
     * Magic secateurs, when the staff holds the weapon slot and the player owns a pair.
     * They raise yield from the inventory, so they ride there. Without the staff the
     * loadout plan puts them in the weapon slot instead, never both.
     */
    private static void addSecateurs(List<FarmingCarryItem> carry, List<StopPlan> plans,
            PlayerState state, CapabilityResolver resolver) {
        if (!staffWielded(plans, state, resolver)
                || ItemCounts.countOwned(state, Loadouts.MAGIC_SECATEURS, null) == 0) {
            return;
        }
        carry.add(withNote(item(FarmingCarryItem.Kind.TOOL, Loadouts.MAGIC_SECATEURS,
                        "Magic secateurs", 0, 1, state, Loadouts.MAGIC_SECATEURS),
                "More herbs, allotment crops and berries."));
    }

    /**
     * The fairy-ring staff, when the trip needs one wielded and the player has none on
     * them. The weapon slot asks for it as well, but a ring refuses the player without it,
     * so the bag names it like any tool the trip cannot do without. Either staff serves;
     * the line names the one the player owns, and the dramen staff when they own neither.
     */
    private static void addStaff(List<FarmingCarryItem> carry, List<StopPlan> plans,
            PlayerState state, CapabilityResolver resolver) {
        if (!staffWielded(plans, state, resolver) || carriedAny(state, STAFF_IDS)) {
            return;
        }
        int staff = bestOwned(state, STAFF_IDS, Loadouts.DRAMEN_STAFF);
        String name = staff == Loadouts.LUNAR_STAFF ? "Lunar staff" : "Dramen staff";
        carry.add(withNote(item(FarmingCarryItem.Kind.TOOL, staff, name, 0, 1, state,
                        STAFF_IDS),
                "Wield it to use fairy rings."));
    }

    /**
     * The seed box, on a trip with seeds to put in it, listed only when owned.
     *
     * <p>{@code InventorySim} charges the box a slot on the same test.
     */
    private static void addSeedBox(List<FarmingCarryItem> carry,
            List<SeedAllocation.Tally> planting, PlayerState state) {
        if (planting.isEmpty()) {
            return;
        }
        FarmingCarryItem box = item(FarmingCarryItem.Kind.TOOL, SEED_BOX_IDS[0],
                "Seed box", 0, 1, state, SEED_BOX_IDS);
        if (box.isOwned()) {
            carry.add(box);
        }
    }

    /**
     * One line per crop, with how many patches take it, across every run on the trip. The
     * tally is the same one the panel's seed line prints.
     */
    private static void addSeeds(List<FarmingCarryItem> carry,
            List<SeedAllocation.Tally> planting, PlayerState state) {
        for (SeedAllocation.Tally tally : planting) {
            CropData data = CropData.forCrop(tally.getCrop());
            if (data == null) {
                continue;
            }
            carry.add(seedLine(data, tally.getCount(), state));
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
            return item(FarmingCarryItem.Kind.SEED, seed, data.getSeedName(),
                    count, count, state, seed);
        }
        int saplings = ownedOf(state, List.of(data.getSaplingItemId()));
        int potted = saplings + ownedOf(state, List.of(data.getWateredSeedlingItemId(),
                data.getSeedlingItemId()));
        // Named and drawn for the furthest stage they hold any of.
        String pottedName = saplings > 0 ? data.getSaplingName() : data.getSeedlingName();
        String name;
        if (saplings >= count || potted >= count) {
            name = pottedName;
        } else if (potted > 0) {
            name = pottedName + " or " + data.getSeedName().toLowerCase(Locale.ROOT);
        } else {
            name = data.getSeedName();
        }
        return item(FarmingCarryItem.Kind.SEED, potted > 0 ? pottedIcon(state, data) : seed,
                name, count, count, state, ids(data.getPlantingForms()));
    }

    /** How many of any of these the player has, wherever they keep them. */
    private static int ownedOf(PlayerState state, List<Integer> itemIds) {
        int owned = 0;
        for (int itemId : itemIds) {
            owned += ItemCounts.countOwned(state, itemId, null);
        }
        return owned;
    }

    /** The furthest-along potted stage the player holds, else the seed. */
    private static int pottedIcon(PlayerState state, CropData data) {
        for (int stage : List.of(data.getSaplingItemId(),
                data.getWateredSeedlingItemId(), data.getSeedlingItemId())) {
            if (ItemCounts.countOwned(state, stage, null) > 0) {
                return stage;
            }
        }
        return data.getSeedItemId();
    }

    private static int[] ids(List<Integer> itemIds) {
        int[] out = new int[itemIds.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = itemIds.get(i);
        }
        return out;
    }

    /**
     * Seeds for the stops that plant one particular thing: snape grass, a white lily, a
     * potato cactus, or the crop the player ticked in its place.
     *
     * <p>These stops bypass the seed allocation, so their seeds are packed here.
     *
     * <p>The count is {@code seedsPerPatch} times the stop's open patches, because one stop
     * can cover several patches (the two seaweed patches are one dive), and a patch holding
     * any living crop takes no seed.
     */
    private static void addFixedSeeds(List<FarmingCarryItem> carry, List<StopPlan> plans,
            PlayerState state, FarmingInputs inputs) {
        FarmingPreferences prefs = inputs.getPreferences();
        Map<Crop, Integer> needed = new LinkedHashMap<>();
        for (StopPlan plan : plans) {
            if (!plan.isVisit() || !plan.getStop().isFixedCrop()) {
                continue;
            }
            Crop crop = FixedCrops.cropFor(plan.getStop(), prefs, state);
            if (crop == null) {
                continue;
            }
            int open = CropAssigner.openFixedPatches(plan.getStop(), inputs, state);
            CropData data = CropData.forCrop(crop);
            if (data != null && open > 0) {
                needed.merge(crop, data.getSeedsPerPatch() * open, Integer::sum);
            }
        }

        for (Map.Entry<Crop, Integer> entry : needed.entrySet()) {
            CropData data = CropData.forCrop(entry.getKey());
            carry.add(seedLine(data, entry.getValue(), state));
        }
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
            PlayerState state, int curePlantCasts, int resurrectCasts, int fertileCasts) {
        Map<Integer, FarmingCarryItem> byItem = new LinkedHashMap<>();
        Map<Integer, Integer> tabletsNeeded = new LinkedHashMap<>();
        Map<Integer, String> tabletNames = new LinkedHashMap<>();
        Map<Rune, Integer> runesNeeded = new EnumMap<>(Rune.class);
        // Cure Plant runes merge here with the teleport runes, so each rune gets one line.
        if (curePlantCasts > 0) {
            runesNeeded.merge(Rune.EARTH, curePlantCasts * EARTH_PER_CURE_PLANT, Integer::sum);
            runesNeeded.merge(Rune.ASTRAL, curePlantCasts * ASTRAL_PER_CURE_PLANT,
                    Integer::sum);
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
                CarryItems.Carry carryItem = CarryItems.forCapability(capability);
                if (carryItem != null) {
                    // One line per item for the whole trip. Every charge tier counts towards
                    // it, since each tier is a different item id. computeIfAbsent avoids
                    // rescanning every container at each later stop.
                    byItem.computeIfAbsent(carryItem.getItemId(),
                            id -> item(FarmingCarryItem.Kind.TRAVEL, id,
                                    carryItem.getName(), 0, 1, state,
                                    carryItem.getItemIds()));
                    continue;
                }
                // A spell capability with no item, such as the respawn teleport: runes,
                // one cast per arrival.
                if (!firstTimeHere) {
                    continue;
                }
                for (Map.Entry<Rune, Integer> cost : Teleports.runesFor(capability).entrySet()) {
                    runesNeeded.merge(cost.getKey(), cost.getValue(), Integer::sum);
                }
            }

            TeleportMeans means = plan.getTeleport();
            if (means == null || !firstTimeHere) {
                continue;
            }
            if (means.isTablet()) {
                tabletsNeeded.merge(means.getItemId(), 1, Integer::sum);
                tabletNames.putIfAbsent(means.getItemId(), means.getDisplayName());
            } else {
                for (Map.Entry<Rune, Integer> cost : means.getRunes().entrySet()) {
                    runesNeeded.merge(cost.getKey(), cost.getValue(), Integer::sum);
                }
            }
        }

        // Fertile Soil's runes, one cast a composted patch, in place of carried compost. Not
        // capped: the player chose the spell, so a shortfall shows as runes to fetch.
        if (fertileCasts > 0) {
            for (Map.Entry<Rune, Integer> cost : FertileSoil.runesPerCast().entrySet()) {
                runesNeeded.merge(cost.getKey(), fertileCasts * cost.getValue(),
                        Integer::sum);
            }
        }
        // Resurrect Crops runes merge here too: an Arceuus herb run pays its teleports from
        // the same runes. They go in last, capped at the runes left over, so the casts never
        // take a rune a teleport line needs.
        int casts = Math.min(resurrectCasts, Resurrection.castsLeftAfter(runesNeeded, state));
        for (Map.Entry<Rune, Integer> cost : Resurrection.runesPerCast().entrySet()) {
            if (casts > 0) {
                runesNeeded.merge(cost.getKey(), casts * cost.getValue(), Integer::sum);
            }
        }

        carry.addAll(byItem.values());

        for (Map.Entry<Integer, Integer> tablet : tabletsNeeded.entrySet()) {
            int itemId = tablet.getKey();
            carry.add(item(FarmingCarryItem.Kind.TRAVEL, itemId, tabletNames.get(itemId),
                    tablet.getValue(), tablet.getValue(), state));
        }

        for (Map.Entry<Rune, Integer> rune : runesNeeded.entrySet()) {
            int itemId = rune.getKey().getItemId();
            carry.add(item(FarmingCarryItem.Kind.RUNES, itemId,
                    rune.getKey().getDisplayName(), rune.getValue(), rune.getValue(), state));
        }
    }

    /** True when any of these ids is on the player right now. */
    private static boolean carriedAny(PlayerState state, int... itemIds) {
        for (int itemId : itemIds) {
            if (ItemCounts.countCarried(state, itemId, null) > 0) {
                return true;
            }
        }
        return false;
    }

    private static FarmingCarryItem tool(int itemId, String name, PlayerState state) {
        return item(FarmingCarryItem.Kind.TOOL, itemId, name, 0, 1, state);
    }

    /**
     * One line of the bag, with the item's availability: carried, at the tool leprechaun,
     * in the seed box, banked, or missing.
     *
     * @param quantity what the run needs, or 0 when the count is not the point
     * @param needed   how many make the line satisfied — one, for the lines that show no
     *                 count at all
     */
    private static FarmingCarryItem item(FarmingCarryItem.Kind kind, int itemId, String name,
            int quantity, int needed, PlayerState state) {
        return item(kind, itemId, name, quantity, needed, state, itemId);
    }

    /**
     * The general form, for a line more than one item can satisfy.
     *
     * @param iconItemId  the item drawn and tagged, which is one of {@code acceptedIds}
     * @param acceptedIds every id that counts towards the line — a tree needs a seed
     *                    <em>or</em> the sapling it becomes, and either one fills the patch
     */
    private static FarmingCarryItem item(FarmingCarryItem.Kind kind, int iconItemId,
            String name, int quantity, int needed, PlayerState state, int... acceptedIds) {
        int carried = 0;
        int stored = 0;
        int owned = 0;
        for (int itemId : acceptedIds) {
            carried += ItemCounts.countCarried(state, itemId, null);
            stored += ItemCounts.countInLeprechaunStorage(state, itemId);
            owned += ItemCounts.countOwned(state, itemId, null);
        }
        FarmingCarryItem.Availability availability;
        if (carried >= needed) {
            availability = FarmingCarryItem.Availability.CARRIED;
        } else if (carried + stored >= needed) {
            // Ranked above BANKED: the leprechaun is at the patch, so nothing needs fetching.
            availability = FarmingCarryItem.Availability.AT_LEPRECHAUN;
        } else if (owned >= needed) {
            availability = theBoxWouldFinishIt(state, needed - carried - stored,
                    owned - carried - stored, acceptedIds)
                    ? FarmingCarryItem.Availability.IN_SEED_BOX
                    : FarmingCarryItem.Availability.BANKED;
        } else {
            availability = FarmingCarryItem.Availability.MISSING;
        }
        return new FarmingCarryItem(kind, iconItemId, name, quantity, availability,
                CropData.tagForPlantable(iconItemId), "", carried);
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
    private static boolean theBoxWouldFinishIt(PlayerState state, int shortOf,
            int ownedElsewhere, int... acceptedIds) {
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
