package com.larryfarming.farming.model;

import com.larryfarming.data.Tables;
import java.util.*;
import lombok.Getter;

/**
 * A way of travelling the player can switch off in the side panel: one teleport item, a
 * family of them, or a group of spells or tablets.
 *
 * <p>Switched off, the method is never used, wherever a route would take it: a switched-off
 * quest cape reaches neither a fairy ring nor anywhere else. A ladder then falls to its next
 * rung, and a stop whose only rungs need the method is skipped with the switch named.
 *
 * <p>Each method covers a group of {@link Capability capabilities}, and three cover
 * teleports that are a place rather than an item: every teleport tablet, the Salve graveyard
 * tablet alone, and Spellbook Swap. Switching tablets off leaves the spells to the same
 * places.
 *
 * <p>Networks the player walks onto (spirit trees, gliders, charter ships, minecarts) and
 * the house's own location are not methods: nothing is spent on them.
 *
 * <p>Each method's key, names, group and capabilities come from the travel methods table.
 * The panel draws the squares under their {@link Group} captions, each group in declared
 * order.
 */
public enum TravelMethod {
    RING_OF_WEALTH,
    GAMES_NECKLACE,
    COMBAT_BRACELET,
    SKILLS_NECKLACE,
    RING_OF_DUELING,
    RING_OF_THE_ELEMENTS,
    DIGSITE_PENDANT,
    NECKLACE_OF_PASSAGE,
    SLAYER_RING,
    XERICS_TALISMAN,
    KHAREDSTS_MEMOIRS,
    PENDANT_OF_ATES,
    SAILORS_AMULET,
    TELEPORT_CRYSTAL,
    CHRONICLE,
    QUETZAL_WHISTLE,
    ROYAL_SEED_POD,
    ECTOPHIAL,
    BASALT,
    MOKHAIOTL_WAYSTONE,
    EXPLORERS_RING,
    ARDOUGNE_CLOAK,
    DIARY_CAPE,
    QUEST_CAPE,
    SKILL_CAPES,
    CONSTRUCTION_CAPE,
    /** Every teleport tablet, the redirected house tab among them. The spells still count. */
    TELEPORT_TABLETS,
    /** The Salve graveyard tablet alone. The Arceuus spell still counts. */
    SALVE_GRAVEYARD_TABLETS,
    /** Spellbook Swap, and so every spell borrowed from another book for one cast. */
    SPELLBOOK_SWAP,
    HOUSE_PORTALS,
    HOUSE_FAIRY_RING;

    static {
        Rows.TABLE.requireAll(values());
    }

    private static final Map<Capability, TravelMethod> BY_CAPABILITY = byCapability();

    /** The config key that switches it, true while it may be used. */
    @Getter
    private final String key;
    /** Named for a player: "Necklace of passage". */
    @Getter
    private final String label;
    private final String inSentence;
    /** The caption the method's square sits under in the side panel. */
    @Getter
    private final Group group;
    /**
     * The capabilities this method covers, the one drawn on its square first. Empty for a
     * method that covers a place's tablet or spell rather than an item.
     */
    @Getter
    private final List<Capability> capabilities;

    TravelMethod() {
        Tables.Row row = Rows.TABLE.of(name());
        this.key = row.text(1);
        this.group = row.constant(Group.class, 2);
        this.label = row.text(3);
        this.inSentence = row.text(4);
        this.capabilities = row.constants(Capability.class, 5);
    }

    /**
     * The travel methods table, held apart so the constructors can read it; see
     * {@link Tables#byName}.
     */
    private static final class Rows {
        static final Tables.Named TABLE = Tables.byName("/com/larryfarming/travel-methods.tsv", 6);
    }

    private static Map<Capability, TravelMethod> byCapability() {
        Map<Capability, TravelMethod> map = new EnumMap<>(Capability.class);
        for (TravelMethod method : values()) {
            for (Capability capability : method.capabilities) {
                if (map.put(capability, method) != null) {
                    throw new IllegalStateException(capability + " is in two travel methods");
                }
            }
        }
        return Collections.unmodifiableMap(map);
    }

    /** The method this capability belongs to, or null when no switch covers it. */
    public static TravelMethod of(Capability capability) {
        return BY_CAPABILITY.get(capability);
    }

    /** The panel's captions over the travel squares, in the order drawn. */
    public enum Group {
        JEWELLERY("Jewellery"),
        ITEMS("Other items"),
        REWARDS("Capes and diary rewards"),
        SPELLS("Spells and tablets"),
        HOUSE("House");

        @Getter
        private final String label;

        Group(String label) {
            this.label = label;
        }
    }

    /** Why a route needing it is not taken: "you switched off the slayer ring". */
    public String switchedOffReason() {
        return "you switched off " + inSentence;
    }
}
