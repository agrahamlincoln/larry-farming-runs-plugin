package com.larryfarming.farming.model;

import com.larryfarming.data.Tables;
import java.util.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Something in the player's own house that a farm run can travel by, which the player may
 * declare built before Larry has seen it.
 *
 * <p>Declared in the order the settings draw them, which never changes: the portal chamber,
 * then the Portal Nexus, then the garden, then the teleport jewellery.
 *
 * <p>Each item's group, label, config key and capability come from the house items table.
 */
@Getter
public enum HouseItem {
    WEISS_PORTAL,
    TROLL_STRONGHOLD_PORTAL,
    KOUREND_PORTAL,
    FENKENSTRAIN_PORTAL,
    HARMONY_PORTAL,
    WEISS_IN_NEXUS,
    TROLL_STRONGHOLD_IN_NEXUS,
    HARMONY_IN_NEXUS,
    SPIRIT_TREE,
    FAIRY_RING,
    // Drawn in two columns, left to right: the boxes down the left, the mounts down the right.
    BASIC_JEWELLERY_BOX,
    MOUNTED_XERICS_TALISMAN,
    FANCY_JEWELLERY_BOX,
    MOUNTED_DIGSITE_PENDANT,
    ORNATE_JEWELLERY_BOX,
    MOUNTED_GLORY;

    static {
        Rows.TABLE.requireAll(values());
    }

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

    private static final Map<Capability, HouseItem> BY_CAPABILITY = byCapability();

    private final Group group;
    /** Short enough for two columns at sidebar width, beside its group's caption. */
    private final String label;
    /**
     * The config key that holds the declaration, true while the player says it is built. A
     * declaration counts only until Larry has seen the item. Players' saved settings sit
     * under these keys.
     */
    private final String key;
    private final Capability capability;

    HouseItem() {
        Tables.Row row = Rows.TABLE.of(name());
        this.group = row.constant(Group.class, 1);
        this.label = row.text(2);
        this.key = row.text(3);
        this.capability = row.constant(Capability.class, 4);
    }

    /**
     * The house items table, held apart so the constructors can read it; see
     * {@link Tables#byName}.
     */
    private static final class Rows {
        static final Tables.Named TABLE = Tables.byName("/com/larryfarming/house-items.tsv", 5);
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
}
