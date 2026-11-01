package com.larryfarming.engine;

import com.larryfarming.model.*;
import lombok.Value;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

import java.util.*;

/**
 * How many of an item the player has, and where: carried, banked, or in storage they must
 * walk to.
 *
 * <p>Counts the inventory, the worn equipment and the bank, plus every container the farm
 * run cares about outside them: the rune pouch and the seed box travel with the player, so
 * their contents count wherever the container is; the seed vault and the tool leprechaun
 * are storage, so they count toward ownership only.
 *
 * <p>An item matches by id when the id is non-zero, otherwise by case-insensitive name.
 */
public final class ItemCounts {

    private ItemCounts() {
    }

    /**
     * How many of an item the player owns anywhere they can retrieve it from: inventory,
     * equipment, bank, rune pouch, seed box, seed vault or the tool leprechaun.
     */
    public static int countOwned(PlayerState state, int itemId, String itemName) {
        if (state == null || (itemId == 0 && itemName == null)) {
            return 0;
        }
        SideContainerCounts side = sideContainerCounts(state, itemId, itemName);
        return countIn(state.getInventory(), itemId, itemName) + countInEquipment(state.getEquipment(), itemId, itemName)
                + countIn(state.getBank(), itemId, itemName) + side.carried + side.banked + side.stored;
    }

    /**
     * How many of an item the player has <em>on them</em> right now: inventory, worn, or in a
     * carried container such as the rune pouch, as opposed to owning something they would
     * have to fetch.
     */
    public static int countCarried(PlayerState state, int itemId, String itemName) {
        if (state == null || (itemId == 0 && itemName == null)) {
            return 0;
        }
        return countIn(state.getInventory(), itemId, itemName) + countInEquipment(state.getEquipment(), itemId, itemName)
                + sideContainerCounts(state, itemId, itemName).carried;
    }

    /**
     * How many of an item the tool leprechaun is holding. Already part of {@link #countOwned},
     * but a packing list must tell it apart from the bank: a spade at the leprechaun is
     * already waiting at the patch.
     */
    public static int countInLeprechaunStorage(PlayerState state, int itemId) {
        return state == null ? 0 : leprechaunStorageQuantity(state, itemId);
    }

    /**
     * Whether the tool leprechaun takes this item into storage: the tools, the composts,
     * plant cure, buckets, either secateurs and the bottomless compost bucket.
     */
    public static boolean leprechaunStores(int itemId) {
        return LEPRECHAUN_STORAGE_VARBITS.containsKey(itemId) || itemId == ItemID.SECATEURS || itemId == ItemID.FAIRY_ENCHANTED_SECATEURS
                // The bottomless compost bucket's stored slot has varbits of its own.
                || itemId == ItemID.BOTTOMLESS_COMPOST_BUCKET || itemId == ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED;
    }

    /** Whether the player owns at least one of the item anywhere; see {@link #countOwned}. */
    public static boolean ownsAny(PlayerState state, int itemId) {
        return countOwned(state, itemId, null) > 0;
    }

    /** Counts items matching {@code itemId}/{@code itemName} in one container's snapshot. */
    static int countItemInStorage(List<ItemStack> storage, int itemId, String itemName) {
        if (storage == null) {
            return 0;
        }
        int total = 0;
        for (ItemStack item : storage) {
            if (itemMatches(item, itemId, itemName)) {
                total += item.getQuantity();
            }
        }
        return total;
    }

    private static boolean itemMatches(ItemStack item, int itemId, String itemName) {
        if (itemId != 0) {
            return item.getItemId() == itemId;
        }
        return itemName.equalsIgnoreCase(item.getName());
    }

    /** The inventory or the bank: loose items plus the contents of filled sacks and baskets. */
    private static int countIn(List<ItemStack> items, int itemId, String itemName) {
        return countItemInStorage(items, itemId, itemName) + countContainerContents(items, itemId);
    }

    private static int countInEquipment(Map<EquipmentSlot, ItemStack> equipment, int itemId, String itemName) {
        int total = 0;
        for (ItemStack item : equipment.values()) {
            if (itemMatches(item, itemId, itemName)) {
                total += item.getQuantity();
            }
        }
        return total;
    }

    // ---------------------------------------------------------------
    // Containers outside the inventory, equipment and bank
    // ---------------------------------------------------------------

    /**
     * One item's holdings across every side container, split by where the count belongs.
     * The buckets are exclusive, so an owned total never double-counts.
     */
    @Value
    private static class SideContainerCounts {
        /** Held in a container that travels, and that container is in the inventory. */
        int carried;
        /** Held in a container that travels, and that container is in the bank. */
        int banked;
        /** Held in storage the player owns and must travel to before use. */
        int stored;

        static final SideContainerCounts NONE = new SideContainerCounts(0, 0, 0);

        static SideContainerCounts of(int carried, int banked, int stored) {
            return (carried | banked | stored) == 0 ? NONE : new SideContainerCounts(carried, banked, stored);
        }
    }

    private static SideContainerCounts sideContainerCounts(PlayerState state, int itemId, String itemName) {
        int carried = 0;
        int banked = 0;
        int stored = 0;

        int pouched = runePouchQuantity(state, itemId);
        if (pouched > 0) {
            if (isPouchCarried(state)) {
                carried += pouched;
            } else if (holdsPouch(state.getBank())) {
                banked += pouched;
            }
        }

        // An untrusted seed box tally answers ownership but not "is it on me" (see
        // SeedBox#trustedOf). Under-counting carried seeds costs a needless withdrawal;
        // over-counting sends the player off without seeds to plant.
        int boxed = countItemInStorage(state.getSeedBox(), itemId, itemName);
        if (boxed > 0) {
            int trusted = SeedBox.trustedOf(state, itemId, boxed);
            carried += trusted;
            int unvouched = boxed - trusted;
            if (unvouched > 0 && SeedBox.banked(state)) {
                banked += unvouched;
            } else if (unvouched > 0 && (SeedBox.carried(state) || state.getBankLastSeen() == null)) {
                // Owned, whereabouts unsettled: an unread box in the bag, or a bank never seen.
                // A box absent from both the inventory and a seen bank is gone, seeds and all.
                stored += unvouched;
            }
        }

        stored += countItemInStorage(state.getSeedVault(), itemId, itemName) + leprechaunStorageQuantity(state, itemId);

        return SideContainerCounts.of(carried, banked, stored);
    }

    private static int runePouchQuantity(PlayerState state, int itemId) {
        if (itemId == 0) {
            return 0;
        }
        Integer qty = state.getRunePouch().get(itemId);
        return (qty == null || qty <= 0) ? 0 : qty;
    }

    /** True when a rune pouch is in the inventory, or defensively worn. */
    private static boolean isPouchCarried(PlayerState state) {
        if (holdsPouch(state.getInventory())) {
            return true;
        }
        for (ItemStack item : state.getEquipment().values()) {
            if (RunePouch.POUCH_ITEM_IDS.contains(item.getItemId())) {
                return true;
            }
        }
        return false;
    }

    private static boolean holdsPouch(List<ItemStack> items) {
        if (items == null) {
            return false;
        }
        for (ItemStack item : items) {
            if (RunePouch.POUCH_ITEM_IDS.contains(item.getItemId())) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------
    // Filled sacks and baskets
    // ---------------------------------------------------------------

    /**
     * Maps a base item to its filled sack or basket variants, whose contents add to the loose
     * count ("Potatoes(10)" contributes 10 potatoes).
     *
     * <p>Variant IDs step by 2 from the container base:
     * {@code containerBaseId + (count - 1) * 2 = variantId}
     */
    @Value
    private static class ContainerType {
        int baseItemId;
        int containerBaseId;
        int maxCount;
    }

    private static final List<ContainerType> CONTAINER_TYPES = List.of(
            // Sacks (capacity 10)
            new ContainerType(1942, 5420, 10),  // Potato  → Potatoes(1..10)
            new ContainerType(1957, 5440, 10),  // Onion   → Onions(1..10)
            new ContainerType(1965, 5460, 10),  // Cabbage → Cabbages(1..10)
            // Baskets (capacity 5)
            new ContainerType(1955, 5378, 5),   // Cooking apple → Apples(1..5)
            new ContainerType(2108, 5388, 5),   // Orange        → Oranges(1..5)
            new ContainerType(5504, 5400, 5),   // Strawberry    → Strawberries(1..5)
            new ContainerType(1963, 5408, 5),   // Banana        → Bananas(1..5)
            new ContainerType(1982, 5960, 5)    // Tomato        → Tomatoes(1..5)
    );

    private static int countContainerContents(List<ItemStack> items, int itemId) {
        if (itemId == 0) {
            return 0;
        }
        for (ContainerType ct : CONTAINER_TYPES) {
            if (ct.baseItemId != itemId) {
                continue;
            }
            int total = 0;
            int last = ct.containerBaseId + (ct.maxCount - 1) * 2;
            for (ItemStack item : items) {
                int id = item.getItemId();
                if (id >= ct.containerBaseId && id <= last) {
                    int offset = id - ct.containerBaseId;
                    if (offset % 2 == 0) {
                        total += (offset / 2 + 1) * item.getQuantity();
                    }
                }
            }
            return total;
        }
        return 0;
    }

    // ---------------------------------------------------------------
    // Tool leprechaun storage
    // ---------------------------------------------------------------

    /**
     * One slice of a tool-leprechaun stored count: a varbit, and where its value sits in
     * the assembled total.
     *
     * <p>The game splits each slot's count across two or three varbits: a narrow low field in
     * the original farming varp, plus "extra" varbits added when the storage caps were raised.
     */
    @Value
    private static class StoredSlice {
        int varbitId;
        /** Bit offset this slice occupies in the assembled total (0 for the low field). */
        int shift;
    }

    /**
     * Maps a farming item ID to the varbit slices holding how many of it the tool leprechaun
     * stores.
     *
     * <p>Varbit IDs are RuneLite's gameval FARMING_TOOLS_*. Each slice's shift is the width of
     * everything below it, and the slices compose to the documented caps: 1000 for supplies,
     * 100 for tools. The comments below state the low field's width, which the next slice's
     * shift must match.
     */
    private static final Map<Integer, List<StoredSlice>> LEPRECHAUN_STORAGE_VARBITS = Map.ofEntries(
            // Supplies. Ultracompost and plant cure each have a single ten-bit varbit (0-1023).
            Map.entry(21483, List.of(new StoredSlice(5732, 0))),   // Ultracompost
            Map.entry(6036, List.of(new StoredSlice(6268, 0))),    // Plant cure
            Map.entry(6034, List.of(new StoredSlice(1443, 0),      // Supercompost, 8 bits
                    new StoredSlice(6267, 8))), Map.entry(6032, List.of(new StoredSlice(1442, 0),      // Compost, 8 bits
                    new StoredSlice(6266, 8))),
            // The bucket takes three slices: 5 bits, then 3, then 2.
            Map.entry(1925, List.of(new StoredSlice(1441, 0),      // Bucket
                    new StoredSlice(4731, 5), new StoredSlice(6265, 8))),
            // Tools: a one-bit low field, the low bit of a seven-bit count.
            Map.entry(952, List.of(new StoredSlice(1437, 0),       // Spade
                    new StoredSlice(8361, 1))), Map.entry(5343, List.of(new StoredSlice(1436, 0),      // Seed dibber
                    new StoredSlice(8358, 1))), Map.entry(5341, List.of(new StoredSlice(1435, 0),      // Rake
                    new StoredSlice(8357, 1))), Map.entry(5325, List.of(new StoredSlice(1440, 0),      // Gardening trowel
                    new StoredSlice(8360, 1)))
    );

    /**
     * Varbit slices holding the shared secateurs count. Plain and magic secateurs occupy
     * one storage slot between them, so the count alone cannot say which is in it.
     */
    private static final List<StoredSlice> SECATEURS_STORAGE_VARBITS = List.of(new StoredSlice(1438, 0), new StoredSlice(8359, 1));

    /** Which secateurs fill the shared slot: 1 for magic secateurs, 0 for plain. */
    private static final int SECATEURS_TYPE_VARBIT = 1848;

    /**
     * Which watering can the leprechaun is holding, or 0 for none.
     *
     * <p>Not a count: only one can is stored, and the slot records <em>which</em> can. The
     * value is the water level plus one: 1 for an empty {@code Watering can}, 2 through 9 for
     * {@code Watering can(1)} through {@code Watering can(8)}.
     */
    private static final int WATERING_CAN_STORAGE_VARBIT = VarbitID.FARMING_TOOLS_WATERINGCAN;

    /**
     * Watering can item IDs indexed by water level, empty first. Spelled out because the id
     * run has a gap at 5332.
     */
    private static final int[] WATERING_CAN_ITEM_IDS = {
            ItemID.WATERING_CAN_0, ItemID.WATERING_CAN_1, ItemID.WATERING_CAN_2,
            ItemID.WATERING_CAN_3, ItemID.WATERING_CAN_4, ItemID.WATERING_CAN_5,
            ItemID.WATERING_CAN_6, ItemID.WATERING_CAN_7, ItemID.WATERING_CAN_8,
    };

    /**
     * Varbit IDs backing tool-leprechaun storage, read at login so stored items count before
     * the next deposit changes a varbit.
     *
     * <p>Must stay declared below every table it is collected from: a static field initialised
     * from a later one reads null.
     */
    public static final Set<Integer> LEPRECHAUN_STORAGE_VARBIT_IDS = collectLeprechaunVarbitIds();

    private static Set<Integer> collectLeprechaunVarbitIds() {
        Set<Integer> ids = new HashSet<>();
        for (List<StoredSlice> slices : LEPRECHAUN_STORAGE_VARBITS.values()) {
            for (StoredSlice slice : slices) {
                ids.add(slice.getVarbitId());
            }
        }
        for (StoredSlice slice : SECATEURS_STORAGE_VARBITS) {
            ids.add(slice.getVarbitId());
        }
        ids.add(SECATEURS_TYPE_VARBIT);
        ids.add(WATERING_CAN_STORAGE_VARBIT);
        return Set.copyOf(ids);
    }

    private static int wateringCanStorageValue(int itemId) {
        for (int level = 0; level < WATERING_CAN_ITEM_IDS.length; level++) {
            if (WATERING_CAN_ITEM_IDS[level] == itemId) {
                return level + 1;
            }
        }
        return 0;
    }

    /**
     * Quantity of a farming item the tool leprechaun is holding, or 0 for items it does not
     * store. Storage is per account: every leprechaun opens the same store.
     */
    private static int leprechaunStorageQuantity(PlayerState state, int itemId) {
        if (itemId == ItemID.SECATEURS || itemId == ItemID.FAIRY_ENCHANTED_SECATEURS) {
            boolean magicStored = varbitValue(state, SECATEURS_TYPE_VARBIT) == 1;
            if (magicStored != (itemId == ItemID.FAIRY_ENCHANTED_SECATEURS)) {
                return 0;
            }
            return assemble(state, SECATEURS_STORAGE_VARBITS);
        }
        int canValue = wateringCanStorageValue(itemId);
        if (canValue != 0) {
            return varbitValue(state, WATERING_CAN_STORAGE_VARBIT) == canValue ? 1 : 0;
        }
        List<StoredSlice> slices = LEPRECHAUN_STORAGE_VARBITS.get(itemId);
        return slices == null ? 0 : assemble(state, slices);
    }

    private static int assemble(PlayerState state, List<StoredSlice> slices) {
        int total = 0;
        for (StoredSlice slice : slices) {
            total += varbitValue(state, slice.getVarbitId()) << slice.getShift();
        }
        return total;
    }

    /** A varbit as a count: unread (absent from the state) and negative read as zero. */
    private static int varbitValue(PlayerState state, int varbitId) {
        Integer value = state.getVarbits().get(varbitId);
        return (value != null && value > 0) ? value : 0;
    }
}
