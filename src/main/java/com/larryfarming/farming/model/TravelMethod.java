package com.larryfarming.farming.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A way of travelling the player can switch off on the Farming tab: one teleport item, a
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
 * <p>The tab draws the squares under their {@link Group} captions, each group in declared
 * order.
 */
public enum TravelMethod {
    RING_OF_WEALTH(FarmingKeys.USE_RING_OF_WEALTH, "Ring of wealth", "the ring of wealth",
            Capability.RING_OF_WEALTH),
    GAMES_NECKLACE(FarmingKeys.USE_GAMES_NECKLACE, "Games necklace", "the games necklace",
            Capability.GAMES_NECKLACE),
    COMBAT_BRACELET(FarmingKeys.USE_COMBAT_BRACELET, "Combat bracelet", "the combat bracelet",
            Capability.COMBAT_BRACELET),
    SKILLS_NECKLACE(FarmingKeys.USE_SKILLS_NECKLACE, "Skills necklace", "the skills necklace",
            Capability.SKILLS_NECKLACE),
    RING_OF_DUELING(FarmingKeys.USE_RING_OF_DUELING, "Ring of dueling", "the ring of dueling",
            Capability.RING_OF_DUELING),
    RING_OF_THE_ELEMENTS(FarmingKeys.USE_RING_OF_THE_ELEMENTS, "Ring of the elements",
            "the ring of the elements", Capability.RING_OF_THE_ELEMENTS),
    DIGSITE_PENDANT(FarmingKeys.USE_DIGSITE_PENDANT, "Digsite pendant", "the digsite pendant",
            Capability.DIGSITE_PENDANT),
    NECKLACE_OF_PASSAGE(FarmingKeys.USE_NECKLACE_OF_PASSAGE, "Necklace of passage",
            "the necklace of passage", Capability.NECKLACE_OF_PASSAGE),
    SLAYER_RING(FarmingKeys.USE_SLAYER_RING, "Slayer ring", "the slayer ring",
            Capability.SLAYER_RING),
    XERICS_TALISMAN(FarmingKeys.USE_XERICS_TALISMAN, "Xeric's talisman", "Xeric's talisman",
            Capability.XERICS_TALISMAN),
    KHAREDSTS_MEMOIRS(FarmingKeys.USE_KHAREDSTS_MEMOIRS, "Kharedst's memoirs",
            "Kharedst's memoirs", Capability.KHAREDSTS_MEMOIRS, Capability.BOOK_OF_THE_DEAD),
    PENDANT_OF_ATES(FarmingKeys.USE_PENDANT_OF_ATES, "Pendant of Ates", "the pendant of Ates",
            Capability.PENDANT_OF_ATES),
    SAILORS_AMULET(FarmingKeys.USE_SAILORS_AMULET, "Sailors' amulet", "the Sailors' amulet",
            Capability.SAILORS_AMULET),
    TELEPORT_CRYSTAL(FarmingKeys.USE_TELEPORT_CRYSTAL, "Teleport crystal",
            "the teleport crystal", Capability.TELEPORT_CRYSTAL),
    CHRONICLE(FarmingKeys.USE_CHRONICLE, "Chronicle", "the Chronicle", Capability.CHRONICLE),
    QUETZAL_WHISTLE(FarmingKeys.USE_QUETZAL_WHISTLE, "Quetzal whistle", "the quetzal whistle",
            Capability.QUETZAL_WHISTLE),
    ROYAL_SEED_POD(FarmingKeys.USE_ROYAL_SEED_POD, "Royal seed pod", "the royal seed pod",
            Capability.ROYAL_SEED_POD),
    ECTOPHIAL(FarmingKeys.USE_ECTOPHIAL, "Ectophial", "the ectophial", Capability.ECTOPHIAL),
    BASALT(FarmingKeys.USE_BASALT, "Basalt", "basalt", Capability.STONY_BASALT,
            Capability.ICY_BASALT),
    MOKHAIOTL_WAYSTONE(FarmingKeys.USE_MOKHAIOTL_WAYSTONE, "Mokhaiotl waystone",
            "the Mokhaiotl waystone", Capability.MOKHAIOTL_WAYSTONE),
    EXPLORERS_RING(FarmingKeys.USE_EXPLORERS_RING, "Explorer's ring", "the Explorer's ring",
            Capability.EXPLORERS_RING_2, Capability.EXPLORERS_RING_3,
            Capability.EXPLORERS_RING_4),
    ARDOUGNE_CLOAK(FarmingKeys.USE_ARDOUGNE_CLOAK, "Ardougne cloak", "the Ardougne cloak",
            Capability.ARDOUGNE_CLOAK_1, Capability.ARDOUGNE_CLOAK_2,
            Capability.ARDOUGNE_CLOAK_3, Capability.ARDOUGNE_CLOAK_4),
    DIARY_CAPE(FarmingKeys.USE_DIARY_CAPE, "Achievement diary cape",
            "the Achievement diary cape", Capability.DIARY_CAPE),
    QUEST_CAPE(FarmingKeys.USE_QUEST_CAPE, "Quest point cape", "the Quest point cape",
            Capability.QUEST_CAPE),
    SKILL_CAPES(FarmingKeys.USE_SKILL_CAPES, "Skill capes", "skill capes",
            Capability.FARMING_CAPE, Capability.FISHING_CAPE, Capability.HUNTER_CAPE),
    CONSTRUCTION_CAPE(FarmingKeys.USE_CONSTRUCTION_CAPE, "Construction cape",
            "the Construction cape", Capability.CONSTRUCTION_CAPE),
    /** Every teleport tablet, the redirected house tab among them. The spells still count. */
    TELEPORT_TABLETS(FarmingKeys.USE_TELEPORT_TABLETS, "Teleport tablets", "teleport tablets",
            Capability.BRIMHAVEN_TABLET),
    /** The Salve graveyard tablet alone. The Arceuus spell still counts. */
    SALVE_GRAVEYARD_TABLETS(FarmingKeys.USE_SALVE_GRAVEYARD_TABLETS,
            "Salve graveyard tablets", "Salve graveyard tablets"),
    /** Spellbook Swap, and so every spell borrowed from another book for one cast. */
    SPELLBOOK_SWAP(FarmingKeys.USE_SPELLBOOK_SWAP, "Spellbook Swap", "Spellbook Swap"),
    HOUSE_PORTALS(FarmingKeys.USE_HOUSE_PORTALS, "House portals", "house portals",
            Capability.POH_PORTAL_WEISS, Capability.POH_PORTAL_TROLL_STRONGHOLD,
            Capability.POH_PORTAL_KOUREND, Capability.POH_PORTAL_FENKENSTRAIN,
            Capability.POH_PORTAL_HARMONY, Capability.POH_NEXUS_WEISS,
            Capability.POH_NEXUS_HARMONY, Capability.POH_NEXUS_TROLL_STRONGHOLD),
    HOUSE_FAIRY_RING(FarmingKeys.USE_HOUSE_FAIRY_RING, "Fairy ring in your house",
            "the fairy ring in your house", Capability.POH_FAIRY_RING);

    private static final Map<Capability, TravelMethod> BY_CAPABILITY = byCapability();

    private final String key;
    private final String label;
    private final String inSentence;
    private final List<Capability> capabilities;

    TravelMethod(String key, String label, String inSentence, Capability... capabilities) {
        this.key = key;
        this.label = label;
        this.inSentence = inSentence;
        this.capabilities = List.of(capabilities);
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

    /** The config key that switches it, true while it may be used. */
    public String getKey() {
        return key;
    }

    /** Named for a player: "Necklace of passage". */
    public String getLabel() {
        return label;
    }

    /**
     * The capabilities this method covers, the one drawn on its square first. Empty for a
     * method that covers a place's tablet or spell rather than an item.
     */
    public List<Capability> getCapabilities() {
        return capabilities;
    }

    /** The caption the method's square sits under on the tab. */
    public Group getGroup() {
        switch (this) {
            case RING_OF_WEALTH:
            case GAMES_NECKLACE:
            case COMBAT_BRACELET:
            case SKILLS_NECKLACE:
            case RING_OF_DUELING:
            case RING_OF_THE_ELEMENTS:
            case DIGSITE_PENDANT:
            case NECKLACE_OF_PASSAGE:
            case SLAYER_RING:
            case XERICS_TALISMAN:
            case PENDANT_OF_ATES:
            case SAILORS_AMULET:
                return Group.JEWELLERY;
            case EXPLORERS_RING:
            case ARDOUGNE_CLOAK:
            case DIARY_CAPE:
            case QUEST_CAPE:
            case SKILL_CAPES:
            case CONSTRUCTION_CAPE:
                return Group.REWARDS;
            case TELEPORT_TABLETS:
            case SALVE_GRAVEYARD_TABLETS:
            case SPELLBOOK_SWAP:
                return Group.SPELLS;
            case HOUSE_PORTALS:
            case HOUSE_FAIRY_RING:
                return Group.HOUSE;
            case KHAREDSTS_MEMOIRS:
            case TELEPORT_CRYSTAL:
            case CHRONICLE:
            case QUETZAL_WHISTLE:
            case ROYAL_SEED_POD:
            case ECTOPHIAL:
            case BASALT:
            case MOKHAIOTL_WAYSTONE:
                return Group.ITEMS;
            default:
                // Every method names its group, so a new one cannot land under the wrong caption.
                throw new IllegalStateException(this + " has no group");
        }
    }

    /** The tab's captions over the travel squares, in the order drawn. */
    public enum Group {
        JEWELLERY("Jewellery"),
        ITEMS("Other items"),
        REWARDS("Capes and diary rewards"),
        SPELLS("Spells and tablets"),
        HOUSE("House");

        private final String label;

        Group(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    /** Why a route needing it is not taken: "you switched off the slayer ring". */
    public String switchedOffReason() {
        return "you switched off " + inSentence;
    }
}
