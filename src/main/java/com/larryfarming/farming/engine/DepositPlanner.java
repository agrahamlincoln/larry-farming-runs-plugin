package com.larryfarming.farming.engine;

import com.larryfarming.engine.*;
import com.larryfarming.engine.farming.Crop;
import com.larryfarming.farming.catalogue.CarryItems;
import com.larryfarming.farming.catalogue.CropData;
import com.larryfarming.model.ItemStack;
import com.larryfarming.model.PlayerState;
import com.larryfarming.snapshot.*;
import java.util.*;
import lombok.*;
import net.runelite.api.gameval.ItemID;

/**
 * What to put back at the next bank: the trip's own things that the rest of the trip has no
 * more use for.
 *
 * <p>Worked out from the bag the player holds now, for the next bank only. A later bank's
 * list would be a guess at a bag that does not exist yet.
 *
 * <p>Only the trip's own things are named: what Larry asked the player to bring and they
 * fetched for it, and what the run left behind (empty pots and buckets, produce, roots). The
 * player's own kit stays theirs: the rune pouch, the seed box and its seeds, their coins,
 * anything they carried before packing, and anything worn, which never sits in the
 * inventory. Produce they brought from elsewhere, fruit for eating say, reads as left behind
 * and may be named.
 */
public final class DepositPlanner {

    private DepositPlanner() {
    }

    /** What the trip has packed so far, and the bag it was packed from. */
    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class TripRecord {

        /** Nothing seen yet. */
        public static final TripRecord EMPTY = new TripRecord(Set.of(), Map.of(), true);

        /** The item ids Larry asked for that the player fetched for this trip. */
        private final Set<Integer> packed;
        /** The fewest of each item the bag held while packing, by item id. */
        private final Map<Integer, Integer> lowest;
        /** Whether the advice this record last read was underway. */
        private final boolean underway;

        /** A record as {@link #getPacked}, {@link #getLowest} and {@link #isUnderway} read it. */
        public static TripRecord of(Set<Integer> packed, Map<Integer, Integer> lowest, boolean underway) {
            return new TripRecord(Set.copyOf(packed), Map.copyOf(lowest), underway);
        }
    }

    /**
     * The trip record after the advice just built.
     *
     * <p>An item counts as packed when Larry lists it and the bag holds more of it than the
     * fewest it held while packing: the player fetched it. A ring they always wear in the
     * bag, or a coin stack, never dips, so it stays theirs. The low marks reset when a new
     * packing list follows a trip. Underway the packed set only grows, since a thing only
     * finished stops used drops off every list yet is still the trip's to put back.
     */
    public static TripRecord record(PlayerState state, FarmingAdvice built, TripRecord before) {
        if (state == null || built == null || state.getInventory() == null) {
            return before;
        }
        Map<Integer, Integer> counts = new HashMap<>();
        for (ItemStack stack : state.getInventory()) {
            if (stack != null && stack.getItemId() > 0) {
                counts.merge(stack.getItemId(), stack.getQuantity(), Integer::sum);
            }
        }
        Map<Integer, Integer> lowest;
        if (built.isUnderway()) {
            lowest = before.lowest;
        } else if (before.underway) {
            // A new packing list: the bag as it stands is the player's own.
            lowest = counts;
        } else {
            lowest = new HashMap<>();
            for (Map.Entry<Integer, Integer> low : before.lowest.entrySet()) {
                int now = counts.getOrDefault(low.getKey(), 0);
                if (now > 0) {
                    lowest.put(low.getKey(), Math.min(now, low.getValue()));
                }
            }
        }
        Set<Integer> asked = askedFor(built);
        Set<Integer> packed = built.isUnderway() ? new HashSet<>(before.packed) : new HashSet<>();
        for (Map.Entry<Integer, Integer> held : counts.entrySet()) {
            int itemId = held.getKey();
            if (itemId != ItemID.COINS && asked.contains(itemId) && held.getValue() > lowest.getOrDefault(itemId, 0)) {
                packed.add(itemId);
            }
        }
        return TripRecord.of(packed, lowest, built.isUnderway());
    }

    /** Every form of everything the packing list and the bank rows name. */
    private static Set<Integer> askedFor(FarmingAdvice built) {
        Set<Integer> asked = new HashSet<>();
        List<List<FarmingCarryItem>> lists = new ArrayList<>();
        lists.add(built.getCarry() == null ? List.of() : built.getCarry());
        for (FarmingStopAdvice stop : built.getStops() == null ? List.<FarmingStopAdvice>of() : built.getStops()) {
            if (stop.getWithdraw() != null) {
                lists.add(stop.getWithdraw());
            }
        }
        for (List<FarmingCarryItem> list : lists) {
            for (FarmingCarryItem line : list) {
                asked.addAll(CarryPlanner.formsOf(line.getItemId()));
            }
        }
        return asked;
    }

    /** The roots a regular tree's stump gives when dug up. */
    private static final Set<Integer> ROOTS = Set.of(ItemID.OAK_ROOTS, ItemID.WILLOW_ROOTS,
            ItemID.MAPLE_ROOTS, ItemID.YEW_ROOTS, ItemID.MAGIC_ROOTS);

    /**
     * A herb patch's harvest, which comes grimy. The crop table names the clean herb, which
     * the patch never gives.
     */
    private static final Set<Integer> GRIMY_HERBS = Set.of(ItemID.UNIDENTIFIED_GUAM,
            ItemID.UNIDENTIFIED_MARENTILL, ItemID.UNIDENTIFIED_TARROMIN, ItemID.UNIDENTIFIED_HARRALANDER, ItemID.UNIDENTIFIED_RANARR,
            ItemID.UNIDENTIFIED_TOADFLAX, ItemID.UNIDENTIFIED_IRIT, ItemID.UNIDENTIFIED_AVANTOE, ItemID.UNIDENTIFIED_KWUARM,
            ItemID.UNIDENTIFIED_SNAPDRAGON, ItemID.UNIDENTIFIED_HUASCA, ItemID.UNIDENTIFIED_CADANTINE, ItemID.UNIDENTIFIED_LANTADYME,
            ItemID.UNIDENTIFIED_DWARF_WEED, ItemID.UNIDENTIFIED_TORSTOL);

    /** What farming hands back: each produce id, the logs of a felled tree among them. */
    private static final Set<Integer> PRODUCE = produceIds();

    private static Set<Integer> produceIds() {
        Set<Integer> ids = new HashSet<>();
        for (Crop crop : Crop.values()) {
            if (crop.isProduce() && crop.getItemId() > 0) {
                ids.add(crop.getItemId());
            }
        }
        return ids;
    }

    /**
     * The deposits for the next bank, in the bag's own order, one line per item.
     *
     * @param packedThisTrip the item ids Larry asked for that have been in the bag this trip
     * @param neededLater    what the trip still needs from that bank on; nothing a later
     *     stretch uses is put back
     * @param lastBank       whether no bank worth fetching at follows this one, so a travel
     *     item may be the player's way home and stays
     */
    static List<FarmingCarryItem> atNextBank(PlayerState state, Set<Integer> packedThisTrip,
            List<FarmingCarryItem> neededLater, boolean lastBank) {
        if (state == null || state.getInventory() == null) {
            return List.of();
        }
        Set<Integer> keep = new HashSet<>(RunePouch.POUCH_ITEM_IDS);
        keep.addAll(SeedBox.BOX_ITEM_IDS);
        for (FarmingCarryItem line : neededLater) {
            keep.addAll(CarryPlanner.formsOf(line.getItemId()));
        }

        Map<Integer, Integer> counts = new LinkedHashMap<>();
        Map<Integer, String> names = new LinkedHashMap<>();
        for (ItemStack stack : state.getInventory()) {
            if (stack == null || stack.getItemId() <= 0) {
                continue;
            }
            counts.merge(stack.getItemId(), stack.getQuantity(), Integer::sum);
            names.putIfAbsent(stack.getItemId(), stack.getName());
        }

        List<FarmingCarryItem> deposits = new ArrayList<>();
        for (Map.Entry<Integer, Integer> held : counts.entrySet()) {
            int itemId = held.getKey();
            if (keep.contains(itemId)) {
                continue;
            }
            boolean travel = CarryItems.formsOf(itemId) != null;
            if (travel && lastBank) {
                continue;
            }
            // A packed farming tool goes back to the leprechaun at its last stop, as that
            // stop's card says, not to a bank: two places for one thing would be one too many.
            if (packedThisTrip.contains(itemId) && ItemCounts.leprechaunStores(itemId)) {
                continue;
            }
            if (!packedThisTrip.contains(itemId) && !leftBehind(itemId)) {
                continue;
            }
            // No count on the tile: the whole stack goes back, and a count that ticks up with
            // every herb picked would redraw the route mid-harvest.
            deposits.add(new FarmingCarryItem(travel ? FarmingCarryItem.Kind.TRAVEL : FarmingCarryItem.Kind.TOOL,
                    itemId, names.get(itemId), 0, FarmingCarryItem.Availability.CARRIED, "", "", held.getValue()));
        }
        return deposits;
    }

    /** What the run itself leaves in the bag: pots, buckets, produce and roots. */
    static boolean leftBehind(int itemId) {
        return itemId == ItemID.PLANTPOT_EMPTY || itemId == ItemID.BUCKET_EMPTY || ROOTS.contains(itemId) || PRODUCE.contains(itemId)
                || GRIMY_HERBS.contains(itemId);
    }

    /** Items that take a slot each however many: compost buckets and plant cure. */
    private static final Set<Integer> ONE_SLOT_EACH = Set.of(ItemID.BUCKET_COMPOST,
            ItemID.BUCKET_SUPERCOMPOST, ItemID.BUCKET_ULTRACOMPOST, ItemID.PLANT_CURE);

    /**
     * The inventory slots a bank's withdrawals take. A seed going into a carried box takes
     * none, nor do runes in an owned pouch or a seed-box check; a compost bucket or a
     * sapling takes one each; anything else stacks into one.
     */
    static int slotsFor(List<FarmingCarryItem> withdrawals, PlayerState state, boolean ownsRunePouch) {
        int slots = 0;
        for (FarmingCarryItem line : withdrawals) {
            if (line.getAvailability() == FarmingCarryItem.Availability.IN_SEED_BOX
                    || (line.getKind() == FarmingCarryItem.Kind.RUNES && ownsRunePouch)
                    || (line.getKind() == FarmingCarryItem.Kind.SEED && SeedBox.carried(state)
                            && CropData.boxesAsAseed(line.getItemId()))) {
                continue;
            }
            // A seed the box does not take is a sapling or a tree seed: counted one each,
            // which over-counts the tree seeds, the safe way.
            boolean eachTakesOne = ONE_SLOT_EACH.contains(line.getItemId()) || (line.getKind() == FarmingCarryItem.Kind.SEED
                            && !CropData.boxesAsAseed(line.getItemId()));
            slots += eachTakesOne ? Math.max(1, line.getQuantity()) : 1;
        }
        return slots;
    }

    /**
     * Whether the bag is short of room at that bank: fewer free slots than the bank's
     * withdrawals take plus the harvest headroom. Until then, depositing is a tidy-up the
     * player may skip.
     */
    static boolean bagWouldFill(PlayerState state, List<FarmingCarryItem> withdrawals) {
        boolean pouch = state != null && RunePouch.POUCH_ITEM_IDS.stream().anyMatch(id -> ItemCounts.countOwned(state, id, null) > 0);
        return bagWouldFill(state, slotsFor(withdrawals, state, pouch));
    }

    /** The same, given the slots the withdrawals take. */
    static boolean bagWouldFill(PlayerState state, int withdrawals) {
        if (state == null || state.getInventory() == null) {
            return false;
        }
        int used = 0;
        for (ItemStack stack : state.getInventory()) {
            if (stack != null && stack.getItemId() > 0) {
                used++;
            }
        }
        return InventorySim.SLOTS - used < withdrawals + InventorySim.HARVEST_HEADROOM;
    }
}
