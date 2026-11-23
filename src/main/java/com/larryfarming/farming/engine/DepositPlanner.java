package com.larryfarming.farming.engine;

import com.larryfarming.data.Tables;
import com.larryfarming.engine.*;
import com.larryfarming.engine.farming.Crop;
import com.larryfarming.farming.catalogue.CarryItems;
import com.larryfarming.farming.catalogue.CropData;
import com.larryfarming.model.ItemStack;
import com.larryfarming.model.PlayerState;
import com.larryfarming.snapshot.*;
import java.util.*;
import java.util.stream.Collectors;
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
 * fetched for it, what the run left behind (empty pots and buckets, produce, roots), and
 * loose seeds and saplings the rest of the trip does not plant, however they came. The
 * player's own kit stays theirs: the rune pouch, the seed box and its seeds, their coins,
 * anything else they carried before packing, and anything worn, which never sits in the
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
     * finished stops used drops off every list yet is still the trip's to put back. While
     * packing, a fetched thing stays packed as long as the bag holds it, though the list drops
     * the stop that wanted it.
     */
    public static TripRecord record(PlayerState state, FarmingAdvice built, TripRecord before) {
        if (state == null || built == null || state.getInventory() == null) {
            return before;
        }
        Map<Integer, Integer> counts = new HashMap<>();
        for (ItemStack stack : held(state)) {
            counts.merge(stack.getItemId(), stack.getQuantity(), Integer::sum);
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
        Set<Integer> packed = new HashSet<>();
        if (built.isUnderway()) {
            packed.addAll(before.packed);
        } else if (!before.underway) {
            // Still packing: a thing fetched for a stop the list has since dropped stays the
            // trip's while the bag holds it, so the first bank underway takes it back.
            before.packed.stream().filter(counts::containsKey).forEach(packed::add);
        }
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

    /**
     * What the run leaves in the bag: pots, buckets, roots and grimy herbs from the table, and
     * what farming hands back, each produce id with the logs of a felled tree among them.
     */
    private static final Set<Integer> LEFT_BEHIND = new HashSet<>();

    static {
        for (Tables.Row row : Tables.read("/com/larryfarming/left-behind.tsv", 2)) {
            LEFT_BEHIND.add(row.integer(0));
        }
        for (Crop crop : Crop.values()) {
            if (crop.isProduce() && crop.getItemId() > 0) {
                LEFT_BEHIND.add(crop.getItemId());
            }
        }
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
        // How many of each thing the rest of the trip uses, by every form of it.
        Map<Integer, Integer> later = new HashMap<>();
        for (FarmingCarryItem line : neededLater) {
            keep.addAll(CarryPlanner.formsOf(line.getItemId()));
            CarryPlanner.formsOf(line.getItemId()).forEach(form -> later.merge(form, line.getQuantity(), Integer::sum));
        }

        Map<Integer, Integer> counts = new LinkedHashMap<>();
        Map<Integer, String> names = new LinkedHashMap<>();
        for (ItemStack stack : held(state)) {
            counts.merge(stack.getItemId(), stack.getQuantity(), Integer::sum);
            names.putIfAbsent(stack.getItemId(), stack.getName());
        }

        List<FarmingCarryItem> deposits = new ArrayList<>();
        for (Map.Entry<Integer, Integer> held : counts.entrySet()) {
            int itemId = held.getKey();
            // A seed or sapling is planting stock whoever packed it: what the rest of the trip
            // does not plant goes back, and only that many when some of it still goes in.
            boolean stock = !CropData.tagForPlantable(itemId).isEmpty();
            int spare = held.getValue() - later.getOrDefault(itemId, 0);
            if (stock ? spare <= 0 : keep.contains(itemId)) {
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
            if (!stock && !packedThisTrip.contains(itemId) && !LEFT_BEHIND.contains(itemId)) {
                continue;
            }
            // No count on the tile while the whole stack goes back: a count that ticks up with
            // every herb picked would redraw the route mid-harvest. Spare planting stock beside
            // what still goes in says how many.
            deposits.add(new FarmingCarryItem(travel ? FarmingCarryItem.Kind.TRAVEL : FarmingCarryItem.Kind.TOOL,
                    itemId, names.get(itemId), spare < held.getValue() ? spare : 0, FarmingCarryItem.Availability.CARRIED,
                    CropData.tagForPlantable(itemId), "", held.getValue()));
        }
        return deposits;
    }

    /** Items that take a slot each however many: compost buckets and plant cure. */
    private static final Set<Integer> ONE_SLOT_EACH = Set.of(ItemID.BUCKET_COMPOST,
            ItemID.BUCKET_SUPERCOMPOST, ItemID.BUCKET_ULTRACOMPOST, ItemID.PLANT_CURE);

    /**
     * The inventory slots a bank's withdrawals take. A seed going into a carried box takes
     * none, nor do runes in an owned pouch or a seed-box check; a compost bucket, a sapling
     * or a tree seed to be potted takes one each; anything else stacks into one.
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
            // A seed the plan does not box is a sapling, or a tree seed to be potted into one:
            // each pot takes its own slot.
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
        boolean pouch = ItemCounts.ownsAny(state, RunePouch.POUCH_ITEM_IDS);
        return bagWouldFill(state, slotsFor(withdrawals, state, pouch));
    }

    /** The same, given the slots the withdrawals take. */
    static boolean bagWouldFill(PlayerState state, int withdrawals) {
        if (state == null || state.getInventory() == null) {
            return false;
        }
        return InventorySim.SLOTS - held(state).size() < withdrawals + InventorySim.HARVEST_HEADROOM;
    }

    /** The inventory's occupied slots, in slot order; the caller has checked it was read. */
    private static List<ItemStack> held(PlayerState state) {
        return state.getInventory().stream().filter(stack -> stack != null && stack.getItemId() > 0).collect(Collectors.toList());
    }
}
