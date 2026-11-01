package com.larryfarming.farming.model;

import java.util.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Something in the player's own house that a farm run can travel by, which the player may
 * declare built before Larry has seen it.
 *
 * <p>Declared in the order the settings draw them, which never changes: the portal chamber,
 * then the Portal Nexus, then the garden.
 */
@Getter
@RequiredArgsConstructor
public enum HouseItem {
    WEISS_PORTAL(Group.PORTALS, "Weiss", FarmingKeys.DECLARE_WEISS_PORTAL, Capability.POH_PORTAL_WEISS),
    TROLL_STRONGHOLD_PORTAL(Group.PORTALS, "Troll Stronghold",
            FarmingKeys.DECLARE_TROLL_STRONGHOLD_PORTAL, Capability.POH_PORTAL_TROLL_STRONGHOLD),
    KOUREND_PORTAL(Group.PORTALS, "Kourend", FarmingKeys.DECLARE_KOUREND_PORTAL, Capability.POH_PORTAL_KOUREND),
    FENKENSTRAIN_PORTAL(Group.PORTALS, "Fenkenstrain's", FarmingKeys.DECLARE_FENKENSTRAIN_PORTAL, Capability.POH_PORTAL_FENKENSTRAIN),
    HARMONY_PORTAL(Group.PORTALS, "Harmony", FarmingKeys.DECLARE_HARMONY_PORTAL, Capability.POH_PORTAL_HARMONY),
    WEISS_IN_NEXUS(Group.NEXUS, "Weiss", FarmingKeys.DECLARE_WEISS_IN_NEXUS, Capability.POH_NEXUS_WEISS),
    TROLL_STRONGHOLD_IN_NEXUS(Group.NEXUS, "Troll Stronghold",
            FarmingKeys.DECLARE_TROLL_STRONGHOLD_IN_NEXUS, Capability.POH_NEXUS_TROLL_STRONGHOLD),
    HARMONY_IN_NEXUS(Group.NEXUS, "Harmony", FarmingKeys.DECLARE_HARMONY_IN_NEXUS, Capability.POH_NEXUS_HARMONY),
    SPIRIT_TREE(Group.GARDEN, "Spirit tree", FarmingKeys.DECLARE_HOUSE_SPIRIT_TREE, Capability.POH_SPIRIT_TREE),
    FAIRY_RING(Group.GARDEN, "Fairy ring", FarmingKeys.DECLARE_HOUSE_FAIRY_RING, Capability.POH_FAIRY_RING);

    /** Where in the house an item stands, as the settings caption it. */
    @Getter
    @RequiredArgsConstructor
    public enum Group {
        PORTALS("Portals"),
        NEXUS("Portal Nexus"),
        GARDEN("Garden");

        private final String label;
    }

    private static final Map<Capability, HouseItem> BY_CAPABILITY = byCapability();

    private final Group group;
    /** Short enough for two columns at sidebar width, beside its group's caption. */
    private final String label;
    /** The config key that holds the declaration, true while the player says it is built. */
    private final String key;
    private final Capability capability;

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
}
