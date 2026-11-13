package com.larryfarming.farming.model;

import com.larryfarming.data.Tables;
import lombok.Getter;

/**
 * Somewhere a farm run teleports to, as opposed to the particular way of getting there.
 *
 * <p>A chain rung such as "Catherby teleport tablet" really means "be at Catherby". A
 * player on the Lunar book with the runes should cast instead of spending a tablet.
 * Naming the place lets one rung serve both means without changing the chain's ordering
 * between places.
 *
 * <p>A destination may also name the {@link Zone} it lands in. A player already inside
 * that zone reaches the rung for free: no cast, no tablet, nothing to pack. The zone is
 * null for destinations whose regions have not been walked, so the shortcut never fires
 * there. A zone is set only from a walked or checked tile. A wrong one would drop a
 * teleport from the packing list that the player then needs.
 *
 * <p>Each destination's name and zone come from the destinations table.
 */
public enum Destination {

    CATHERBY,
    CAMELOT,
    VARROCK,
    /**
     * The Varrock teleport's second landing (3165, 3479), which the medium Varrock diary
     * adds. Its means are Varrock's spell and tablet, which carry no diary gate, so every
     * rung that asks for this place also asks for {@link Capability#VARROCK_DIARY_MEDIUM}.
     */
    GRAND_EXCHANGE,
    LUMBRIDGE,
    FALADOR,
    ARDOUGNE,
    /** Never zoned: the house is wherever the player built it, which nothing reports. */
    HOUSE,
    FISHING_GUILD,
    FENKENSTRAINS_CASTLE,
    SALVE_GRAVEYARD,
    DRAYNOR_MANOR,
    ARCEUUS_LIBRARY,
    HARMONY_ISLAND,
    KOUREND_CASTLE,
    CIVITAS_ILLA_FORTIS;

    static {
        Rows.TABLE.requireAll(values());
    }

    @Getter
    private final String displayName;
    /** The zone this lands in, or null where it has not been walked. */
    @Getter
    private final Zone zone;

    Destination() {
        Tables.Row row = Rows.TABLE.of(name());
        this.displayName = row.text(1);
        this.zone = row.optionalConstant(Zone.class, 2);
    }

    /**
     * The destinations table, held apart so the constructors can read it; see
     * {@link Tables#byName}.
     */
    private static final class Rows {
        static final Tables.Named TABLE = Tables.byName("/com/larryfarming/destinations.tsv", 3);
    }
}
