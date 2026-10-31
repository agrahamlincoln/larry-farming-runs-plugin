package com.larryfarming.farming.catalogue;

import com.larryfarming.farming.model.Capability;
import java.util.EnumMap;
import java.util.Map;

/**
 * What a capability means in the inventory: the thing to bring so the route works.
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
        private final String name;

        /**
         * @param itemIds every id that counts as having this, the first being the one drawn
         */
        Carry(String name, int... itemIds) {
            // Fail on the bad row by name. This runs in the static initialiser, which
            // capability detection also depends on.
            if (itemIds.length == 0) {
                throw new IllegalArgumentException("no item ids for " + name);
            }
            this.name = name;
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

        public String getName() {
            return name;
        }
    }

    private static final Map<Capability, Carry> ITEMS = build();

    /** What to pack for this capability, or null when it is not something you carry. */
    public static Carry forCapability(Capability capability) {
        return ITEMS.get(capability);
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

    private static Map<Capability, Carry> build() {
        Map<Capability, Carry> items = new EnumMap<>(Capability.class);

        // Teleport tablets are absent: tablet or runes depends on the player's spellbook, so
        // the packing list takes them from the teleport chosen for each stop.

        // Diary rewards. Each tier accepts every tier above it, since a player owns only
        // their best.
        items.put(Capability.ARDOUGNE_CLOAK_1, new Carry("Ardougne cloak",
                13121, 13122, 13123, 13124));
        items.put(Capability.ARDOUGNE_CLOAK_2, new Carry("Ardougne cloak 2",
                13122, 13123, 13124));
        items.put(Capability.ARDOUGNE_CLOAK_3, new Carry("Ardougne cloak 3",
                13123, 13124));
        items.put(Capability.ARDOUGNE_CLOAK_4, new Carry("Ardougne cloak 4", 13124));
        items.put(Capability.EXPLORERS_RING_2, new Carry("Explorer's ring 2",
                13126, 13127, 13128));
        items.put(Capability.EXPLORERS_RING_3, new Carry("Explorer's ring 3",
                13127, 13128));
        items.put(Capability.EXPLORERS_RING_4, new Carry("Explorer's ring 4", 13128));

        // Skill capes, in both the plain and trimmed forms.
        items.put(Capability.FARMING_CAPE, new Carry("Farming cape", 9810, 9811));
        items.put(Capability.CONSTRUCTION_CAPE, new Carry("Construction cape", 9789, 9790));
        items.put(Capability.FISHING_CAPE, new Carry("Fishing cape", 9798, 9799));
        items.put(Capability.HUNTER_CAPE, new Carry("Hunter cape", 9948, 9949));
        items.put(Capability.QUEST_CAPE, new Carry("Quest point cape", 9813, 13068));
        items.put(Capability.DIARY_CAPE, new Carry("Achievement diary cape", 19476, 13069));

        // Basalts, both from Making Friends with My Arm.
        items.put(Capability.ICY_BASALT, new Carry("Icy basalt", 22599));
        items.put(Capability.STONY_BASALT, new Carry("Stony basalt", 22601));

        // Charged jewellery, full charge first, down to the last working charge. Spent and
        // inert tiers teleport nowhere and are left out throughout: e.g. an uncharged ring of
        // wealth (2572, or 12785 imbued) and an inert Xeric's talisman (13392).
        items.put(Capability.RING_OF_DUELING, new Carry("Ring of dueling",
                2552, 2554, 2556, 2558, 2560, 2562, 2564, 2566));
        items.put(Capability.GAMES_NECKLACE, new Carry("Games necklace",
                3853, 3855, 3857, 3859, 3861, 3863, 3865, 3867));
        // The uncharged bracelet (11126) is absent. The two highest charge tiers have ids
        // far from the other four, so full charge is 11972, not 11118.
        items.put(Capability.COMBAT_BRACELET, new Carry("Combat bracelet",
                11972, 11974, 11118, 11120, 11122, 11124));
        // The digsite pendant's ids number upward from (1), so full charge is the highest id.
        items.put(Capability.DIGSITE_PENDANT, new Carry("Digsite pendant",
                11194, 11193, 11192, 11191, 11190));
        items.put(Capability.RING_OF_WEALTH, new Carry("Ring of wealth",
                11980, 11982, 11984, 11986, 11988, 20786, 20787, 20788, 20789, 20790));
        // 11113 (spent) is absent. The two highest charge tiers have ids far from the other
        // four, so full charge is 11968, not 11105.
        items.put(Capability.SKILLS_NECKLACE, new Carry("Skills necklace",
                11968, 11970, 11105, 11107, 11109, 11111));
        items.put(Capability.XERICS_TALISMAN, new Carry("Xeric's talisman", 13393));
        // One id each: the charges live on a varbit, not on the item.
        items.put(Capability.KHAREDSTS_MEMOIRS, new Carry("Kharedst's memoirs", 21760));
        items.put(Capability.BOOK_OF_THE_DEAD, new Carry("Book of the Dead", 25818));
        // The eternal ring joins the eight charge tiers: the same teleport, uncharged.
        items.put(Capability.SLAYER_RING, new Carry("Slayer ring",
                11866, 11867, 11868, 11869, 11870, 11871, 11872, 11873, 21268));
        // A spent ring of the elements becomes 26815, which teleports nowhere.
        items.put(Capability.RING_OF_THE_ELEMENTS, new Carry("Ring of the elements", 26818));
        items.put(Capability.PENDANT_OF_ATES, new Carry("Pendant of Ates", 29893));
        // The inert amulet (32398) is not a route.
        items.put(Capability.SAILORS_AMULET, new Carry("Sailors' amulet", 32399));

        // Every crafted tier of the whistle. Without a row here the packing list falls
        // through to teleport runes and packs nothing. The tiers are upgrades, not charges,
        // so the basic whistle is drawn.
        items.put(Capability.QUETZAL_WHISTLE, new Carry("Quetzal whistle",
                29271, 29273, 29275, 33120));

        // Teleports beside the Wizards' Tower fairy ring, which is how an account with no
        // Ardougne diary gets onto the network at all.
        items.put(Capability.NECKLACE_OF_PASSAGE, new Carry("Necklace of passage",
                21146, 21149, 21151, 21153, 21155));

        // One-off transport items.
        items.put(Capability.ROYAL_SEED_POD, new Carry("Royal seed pod", 19564));
        items.put(Capability.CHRONICLE, new Carry("Chronicle", 13660));
        // The full one only: the empty one (4252) cannot teleport.
        items.put(Capability.ECTOPHIAL, new Carry("Ectophial", 4251));
        items.put(Capability.TELEPORT_CRYSTAL, new Carry("Teleport crystal",
                13102, 6099, 6100, 6101, 6102, 23946));
        items.put(Capability.MOKHAIOTL_WAYSTONE, new Carry("Mokhaiotl waystone", 31099));
        // One id, because a scroll of redirection produces a tablet for exactly one town.
        items.put(Capability.BRIMHAVEN_TABLET, new Carry("Brimhaven teleport tablet", 11745));

        return items;
    }
}
