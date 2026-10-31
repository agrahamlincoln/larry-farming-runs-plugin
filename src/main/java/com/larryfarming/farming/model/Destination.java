package com.larryfarming.farming.model;

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
 * there. Never guess a zone: a wrong one drops a teleport from the packing list that the
 * player then needs.
 */
public enum Destination {

    CATHERBY("Catherby"),
    CAMELOT("Camelot"),
    VARROCK("Varrock"),
    /**
     * The Varrock teleport's second landing (3165, 3479), which the medium Varrock diary
     * adds. Its means are Varrock's spell and tablet, which carry no diary gate, so every
     * rung that asks for this place also asks for {@link Capability#VARROCK_DIARY_MEDIUM}.
     */
    GRAND_EXCHANGE("the Grand Exchange"),
    LUMBRIDGE("Lumbridge"),
    FALADOR("Falador"),
    ARDOUGNE("Ardougne"),
    /** Never zoned: the house is wherever the player built it, which nothing reports. */
    HOUSE("your house"),
    FISHING_GUILD("the Fishing Guild"),
    FENKENSTRAINS_CASTLE("Fenkenstrain's Castle"),
    SALVE_GRAVEYARD("the Salve graveyard"),
    DRAYNOR_MANOR("Draynor Manor"),
    ARCEUUS_LIBRARY("the Arceuus Library"),
    HARMONY_ISLAND("Harmony Island"),
    CIVITAS_ILLA_FORTIS("Civitas illa Fortis", Zone.CIVITAS_ILLA_FORTIS);

    private final String displayName;
    private final Zone zone;

    Destination(String displayName) {
        this(displayName, null);
    }

    Destination(String displayName, Zone zone) {
        this.displayName = displayName;
        this.zone = zone;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** The zone this lands in, or null where it has not been walked. */
    public Zone getZone() {
        return zone;
    }
}
