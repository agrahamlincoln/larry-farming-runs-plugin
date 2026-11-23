package com.larryfarming.farming.catalogue;

import com.larryfarming.data.Tables;
import com.larryfarming.farming.model.Capability;
import java.util.*;
import lombok.Getter;

/**
 * What a capability means in the inventory: the thing to bring so the route works, one row
 * of the carry items table per capability.
 *
 * <p>Chains name what the player can do ("icy basalt to Weiss"). This table maps that to
 * the item to pack, so the carry list derives from the chosen route.
 *
 * <p>Each line lists every variant and draws one. The sprite names the item as the player
 * looks for it in the bank, while ticking it off must accept any charge tier.
 *
 * <p>Worn items are listed too: an Ardougne cloak left in the bank fails the run as surely
 * as a forgotten spade.
 *
 * <p>Capabilities that are not objects (a quest, a house portal, a spell, the fairy ring
 * network) stay absent, and lookups return null for them.
 */
public final class CarryItems {

    private CarryItems() {
    }

    /** Something to bring, with the sprite to draw for it. */
    public static final class Carry {
        private final int[] itemIds;
        @Getter
        private final String name;
        /** Whether a teleport can use up the last of it. */
        @Getter
        private final boolean usedUp;

        /**
         * @param itemIds every id that counts as having this, the first being the one drawn;
         *     never empty, since the table refuses a row without ids
         */
        Carry(String name, boolean usedUp, int... itemIds) {
            this.name = name;
            this.usedUp = usedUp;
            this.itemIds = itemIds;
        }

        /** The id to draw the line by, by convention the full-charge one. */
        public int getItemId() {
            return itemIds[0];
        }

        /** Every id that satisfies this line, the drawn one included. */
        public int[] getItemIds() {
            return itemIds.clone();
        }
    }

    private static final Map<Capability, Carry> ITEMS = load();

    /** What to pack for this capability, or null when it is not something you carry. */
    public static Carry forCapability(Capability capability) {
        return ITEMS.get(capability);
    }

    /**
     * The item to draw beside a route taken on this capability rather than cast, or 0 when
     * there is nothing honest to draw: the item packed for it, worn items such as an
     * Ardougne cloak or a Farming cape included, so a route and its bag line draw the same
     * sprite.
     *
     * <p>A capability with no entry draws nothing. Networks such as fairy rings, spirit trees
     * and minecarts have no honest sprite, and a wrong one is worse than none.
     */
    public static int iconFor(Capability capability) {
        Carry carry = ITEMS.get(capability);
        return carry != null ? carry.getItemId() : 0;
    }

    /**
     * Every id of the travel item this id is a form of, every charge tier included, or null
     * when no route packs it.
     */
    public static int[] formsOf(int itemId) {
        for (Carry carry : ITEMS.values()) {
            for (int id : carry.itemIds) {
                if (id == itemId) {
                    return carry.getItemIds();
                }
            }
        }
        return null;
    }

    private static Map<Capability, Carry> load() {
        Map<Capability, Carry> items = new EnumMap<>(Capability.class);
        for (Tables.Row row : Tables.read("/com/larryfarming/carry-items.tsv", 5)) {
            Capability capability = row.constant(Capability.class, 0);
            int[] ids = row.integers(2);
            if (ids.length == 0) {
                throw row.error("no item ids for " + capability);
            }
            if (items.put(capability, new Carry(row.text(1), row.optionalText(4) != null, ids)) != null) {
                throw row.error("a second row for " + capability);
            }
        }
        return Collections.unmodifiableMap(items);
    }
}
