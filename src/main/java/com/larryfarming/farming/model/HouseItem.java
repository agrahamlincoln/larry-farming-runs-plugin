package com.larryfarming.farming.model;

import com.larryfarming.data.Tables;
import java.util.*;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;

/**
 * Something in the player's own house that a farm run can travel by, which the player may
 * declare built before Larry has seen it.
 *
 * <p>The house items table holds one row per item, in the order the settings draw them: a
 * fixed order that toggles and status never change. The portal chamber comes first, then the
 * Portal Nexus, then the garden, then the teleport jewellery.
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class HouseItem {
    /** Where in the house an item stands, as the settings caption it. */
    @Getter
    @RequiredArgsConstructor
    public enum Group {
        PORTALS("Portals"),
        NEXUS("Portal Nexus"),
        GARDEN("Garden"),
        JEWELLERY("Jewellery");

        private final String label;
    }

    private static final List<HouseItem> ALL = Tables.read("/com/larryfarming/house-items.tsv", 4).stream()
            .map(row -> new HouseItem(row.constant(Group.class, 0), row.text(1), row.text(2), row.constant(Capability.class, 3)))
            .collect(Collectors.toUnmodifiableList());

    private static final Map<Capability, HouseItem> BY_CAPABILITY = ALL.stream()
            .collect(Collectors.toUnmodifiableMap(HouseItem::getCapability, item -> item));

    Group group;
    /** Short enough for two columns at sidebar width, beside its group's caption. */
    String label;
    /**
     * The config key that holds the declaration, true while the player says it is built. A
     * declaration counts only until Larry has seen the item. Players' saved settings sit
     * under these keys.
     */
    String key;
    Capability capability;

    /** Every house item, in the order the settings draw them. */
    public static List<HouseItem> all() {
        return ALL;
    }

    /** The item this capability is, or null for a capability no house item answers. */
    public static HouseItem of(Capability capability) {
        return BY_CAPABILITY.get(capability);
    }
}
