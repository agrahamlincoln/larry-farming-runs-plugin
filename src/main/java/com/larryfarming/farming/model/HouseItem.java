package com.larryfarming.farming.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Something in the player's own house that a farm run can travel by, which the player may
 * declare built before Larry has seen it.
 *
 * <p>Declared in the order the settings draw them, which never changes: the portal chamber,
 * then the Portal Nexus, then the garden.
 */
public enum HouseItem {
    WEISS_PORTAL(Group.PORTALS, "Weiss", FarmingKeys.DECLARE_WEISS_PORTAL,
            Capability.POH_PORTAL_WEISS),
    TROLL_STRONGHOLD_PORTAL(Group.PORTALS, "Troll Stronghold",
            FarmingKeys.DECLARE_TROLL_STRONGHOLD_PORTAL, Capability.POH_PORTAL_TROLL_STRONGHOLD),
    KOUREND_PORTAL(Group.PORTALS, "Kourend", FarmingKeys.DECLARE_KOUREND_PORTAL,
            Capability.POH_PORTAL_KOUREND),
    FENKENSTRAIN_PORTAL(Group.PORTALS, "Fenkenstrain's", FarmingKeys.DECLARE_FENKENSTRAIN_PORTAL,
            Capability.POH_PORTAL_FENKENSTRAIN),
    HARMONY_PORTAL(Group.PORTALS, "Harmony", FarmingKeys.DECLARE_HARMONY_PORTAL,
            Capability.POH_PORTAL_HARMONY),
    WEISS_IN_NEXUS(Group.NEXUS, "Weiss", FarmingKeys.DECLARE_WEISS_IN_NEXUS,
            Capability.POH_NEXUS_WEISS),
    TROLL_STRONGHOLD_IN_NEXUS(Group.NEXUS, "Troll Stronghold",
            FarmingKeys.DECLARE_TROLL_STRONGHOLD_IN_NEXUS, Capability.POH_NEXUS_TROLL_STRONGHOLD),
    HARMONY_IN_NEXUS(Group.NEXUS, "Harmony", FarmingKeys.DECLARE_HARMONY_IN_NEXUS,
            Capability.POH_NEXUS_HARMONY),
    SPIRIT_TREE(Group.GARDEN, "Spirit tree", FarmingKeys.DECLARE_HOUSE_SPIRIT_TREE,
            Capability.POH_SPIRIT_TREE),
    FAIRY_RING(Group.GARDEN, "Fairy ring", FarmingKeys.DECLARE_HOUSE_FAIRY_RING,
            Capability.POH_FAIRY_RING);

    /** Where in the house an item stands, as the settings caption it. */
    public enum Group {
        PORTALS("Portals"),
        NEXUS("Portal Nexus"),
        GARDEN("Garden");

        private final String label;

        Group(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private static final Map<Capability, HouseItem> BY_CAPABILITY = byCapability();

    private final Group group;
    private final String label;
    private final String key;
    private final Capability capability;

    HouseItem(Group group, String label, String key, Capability capability) {
        this.group = group;
        this.label = label;
        this.key = key;
        this.capability = capability;
    }

    private static Map<Capability, HouseItem> byCapability() {
        Map<Capability, HouseItem> map = new EnumMap<>(Capability.class);
        for (HouseItem item : values()) {
            map.put(item.capability, item);
        }
        return Collections.unmodifiableMap(map);
    }

    /** The item this capability is, or null for a capability no house item answers. */
    public static HouseItem of(Capability capability) {
        return BY_CAPABILITY.get(capability);
    }

    public Group getGroup() {
        return group;
    }

    /** Short enough for two columns at sidebar width, beside its group's caption. */
    public String getLabel() {
        return label;
    }

    /** The config key that holds the declaration, true while the player says it is built. */
    public String getKey() {
        return key;
    }

    public Capability getCapability() {
        return capability;
    }
}
