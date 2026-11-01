package com.larryfarming.engine;

import java.util.List;
import java.util.Set;

import com.larryfarming.model.ItemStack;
import com.larryfarming.model.PlayerState;
import net.runelite.api.gameval.ItemID;

/**
 * The Tithe Farm seed box: what counts as one, and where it is.
 *
 * <p>Unlike the tackle box and the seed vault, the seed box is a carried container: seeds in
 * a box in the bag are in the bag. {@code ItemCounts} counts its contents toward
 * wherever the box is, as it does for the rune pouch.
 *
 * <p>The pouch is read from varbits and corrects itself every tick. The game never reports
 * the seed box's contents as a container, so its tally is summed from chat lines and can
 * drift.
 */
public final class SeedBox {

    private SeedBox() {
    }

    /**
     * Closed and open: one item in two states. Open and Close swap the id in place, and a
     * player may own only one box.
     *
     * <p>Named through {@code ItemID} because nearby ids are traps: {@code ItemID.SEEDBOX} is
     * a <em>seed pack</em>, and the box has a bank placeholder id too.
     */
    public static final Set<Integer> BOX_ITEM_IDS = Set.of(ItemID.SEED_BOX, ItemID.SEED_BOX_OPEN);

    /** True when a seed box is in the inventory, or defensively worn. */
    public static boolean carried(PlayerState state) {
        return state != null && (holdsBox(state.getInventory()) || holdsBoxEquipment(state));
    }

    /**
     * True when the box is in the bag and its tally's kinds are worth counting against the
     * box's six places: the box was listed this session, or seeds were watched moving.
     *
     * <p>Box-wide and loose on purpose. A stale kind wrongly counted costs one loose seed
     * slot, never a seed. {@link #trustedOf} answers how many of one seed count as carried.
     */
    public static boolean contentsCarried(PlayerState state) {
        return carried(state) && (state.getSeedBoxVerifiedAt() != null || state.getSeedBoxWatchedAt() != null);
    }

    /**
     * How many of one seed's tally count as carried: all of it once the whole box was listed
     * this session, otherwise only what was watched going in this session, less what was
     * watched leaving.
     *
     * <p>A tally carried over from an earlier session can hold seeds that left by a route no
     * chat line reported. Counting those as carried skips their withdrawal and sends the
     * player to the patch without them. A watched movement vouches only for what it moved,
     * so it never turns that stale part into carried seeds.
     *
     * @param itemId  the seed asked about, or 0 when only a name is known, which only a full
     *                listing can vouch for
     * @param tallied what the tally holds of it
     */
    public static int trustedOf(PlayerState state, int itemId, int tallied) {
        if (!carried(state) || tallied <= 0) {
            return 0;
        }
        if (state.getSeedBoxVerifiedAt() != null) {
            return tallied;
        }
        return itemId > 0 ? Math.min(tallied, state.getSeedBoxWatchedNet().getOrDefault(itemId, 0)) : 0;
    }

    /**
     * What the box's tally claims it holds of one item, trusted or not.
     *
     * <p>Deliberately ungated: where {@link #trustedOf} vouches for less than the tally, a
     * caller still needs this number to tell "checking the box would find something" from
     * "checking would change nothing".
     */
    public static int tallied(PlayerState state, int itemId) {
        // The name-matching path underneath dereferences the null name when the id is 0.
        return state == null || itemId == 0 ? 0 : ItemCounts.countItemInStorage(state.getSeedBox(), itemId, null);
    }

    /** True when a seed box is sitting in the bank. */
    public static boolean banked(PlayerState state) {
        return state != null && holdsBox(state.getBank());
    }

    private static boolean holdsBox(List<ItemStack> items) {
        if (items == null) {
            return false;
        }
        for (ItemStack item : items) {
            if (BOX_ITEM_IDS.contains(item.getItemId())) {
                return true;
            }
        }
        return false;
    }

    private static boolean holdsBoxEquipment(PlayerState state) {
        if (state.getEquipment() == null) {
            return false;
        }
        for (ItemStack item : state.getEquipment().values()) {
            if (item != null && BOX_ITEM_IDS.contains(item.getItemId())) {
                return true;
            }
        }
        return false;
    }
}
