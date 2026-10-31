package com.larryfarming.farming.catalogue;

import com.larryfarming.farming.model.Capability;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * What to draw beside a route that is taken on an item rather than cast.
 *
 * <p>Wider than {@link CarryItems}, which holds only packed items: this also covers worn
 * items such as an Ardougne cloak or a Farming cape.
 *
 * <p>A capability with no entry draws nothing. Networks such as fairy rings, spirit trees
 * and minecarts have no honest sprite, and a wrong one is worse than none.
 */
public final class TravelIcons {

    private TravelIcons() {
    }

    private static final Map<Capability, Integer> ICONS = build();

    /** The item to draw for this capability, or 0 when there is nothing honest to draw. */
    public static int forCapability(Capability capability) {
        Integer itemId = ICONS.get(capability);
        return itemId != null ? itemId : 0;
    }

    private static Map<Capability, Integer> build() {
        Map<Capability, Integer> icons = new EnumMap<>(Capability.class);

        // Diary rewards, drawn at the tier the chain asks for.
        icons.put(Capability.ARDOUGNE_CLOAK_1, 13121);
        icons.put(Capability.ARDOUGNE_CLOAK_2, 13122);
        icons.put(Capability.ARDOUGNE_CLOAK_3, 13123);
        icons.put(Capability.ARDOUGNE_CLOAK_4, 13124);
        icons.put(Capability.EXPLORERS_RING_2, 13126);
        icons.put(Capability.EXPLORERS_RING_3, 13127);
        icons.put(Capability.EXPLORERS_RING_4, 13128);
        icons.put(Capability.DIARY_CAPE, 19476);

        // Skill capes, at their untrimmed id.
        icons.put(Capability.FARMING_CAPE, 9810);
        icons.put(Capability.CONSTRUCTION_CAPE, 9789);
        icons.put(Capability.HUNTER_CAPE, 9948);
        icons.put(Capability.FISHING_CAPE, 9798);
        icons.put(Capability.QUEST_CAPE, 9813);

        // Jewellery and charged items, at full charge, matching the id CarryItems draws.
        // The full-charge id is not always the lowest: the skills necklace's top tiers
        // sit far from the rest.
        icons.put(Capability.SKILLS_NECKLACE, 11968);
        icons.put(Capability.GAMES_NECKLACE, 3853);
        icons.put(Capability.COMBAT_BRACELET, 11972);
        icons.put(Capability.RING_OF_DUELING, 2552);
        icons.put(Capability.RING_OF_WEALTH, 11980);
        icons.put(Capability.DIGSITE_PENDANT, 11194);
        icons.put(Capability.XERICS_TALISMAN, 13393);
        icons.put(Capability.KHAREDSTS_MEMOIRS, 21760);
        icons.put(Capability.BOOK_OF_THE_DEAD, 25818);
        icons.put(Capability.SLAYER_RING, 11866);
        icons.put(Capability.RING_OF_THE_ELEMENTS, 26818);
        icons.put(Capability.ROYAL_SEED_POD, 19564);
        icons.put(Capability.CHRONICLE, 13660);
        icons.put(Capability.ECTOPHIAL, 4251);
        icons.put(Capability.TELEPORT_CRYSTAL, 13102);
        icons.put(Capability.PENDANT_OF_ATES, 29893);
        icons.put(Capability.SAILORS_AMULET, 32399);
        icons.put(Capability.QUETZAL_WHISTLE, 29271);
        icons.put(Capability.NECKLACE_OF_PASSAGE, 21146);
        icons.put(Capability.MOKHAIOTL_WAYSTONE, 31099);
        icons.put(Capability.BRIMHAVEN_TABLET, 11745);

        // Consumables.
        icons.put(Capability.ICY_BASALT, 22599);
        icons.put(Capability.STONY_BASALT, 22601);

        return Collections.unmodifiableMap(icons);
    }
}
