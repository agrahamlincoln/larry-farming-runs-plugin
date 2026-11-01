package com.larryfarming.farming.model;

import lombok.Getter;

/**
 * The bank a {@link TravelOption}'s detour figure was walked to.
 *
 * <p>Lets a row name the bank rather than the arrival: two Ortus Farm rungs bank in Civitas
 * illa Fortis and three at the Hunter Guild, none at Ortus Farm.
 *
 * <p><b>On the rung, beside the tiles, never on the {@link Arrival}</b>, because one arrival's
 * rungs can land near different banks: the Tree Gnome Stronghold's royal seed pod puts the
 * player at the north bank while every other rung there walks to the south.
 *
 * <p>Each bank was identified by walking the route to it, by being the town's
 * only bank, or (Civitas illa Fortis) by checking in game.
 *
 * <p>The tile comes from the bank's wiki
 * {@code |map=} infobox. It locates the bank, not where a player stands.
 */
@Getter
public enum Bank {

    /**
     * Falador east, for the park and the farm alike. The walk to this bank reproduces the
     * park and farm detour figures to within a few tiles.
     */
    FALADOR_EAST("Falador east", 3013, 3356),

    /** Varrock east, which reproduces the detour figures; the west bank is out by thirty tiles. */
    VARROCK_EAST("Varrock east", 3253, 3420),

    /** Canifis, the only bank in the town. */
    CANIFIS("Canifis", 3512, 3480),

    /** Al Kharid, the only bank in the city. */
    AL_KHARID("Al Kharid", 3272, 3170),

    /** Ardougne south, for both Monastery rungs; the north bank is further. */
    ARDOUGNE_SOUTH("Ardougne south", 2655, 3284),

    /**
     * Ardougne north, for the farm. The cloak lands on the farm patch, so the detour is an
     * out-and-back: 87 tiles to this bank against 131 to the south bank.
     */
    ARDOUGNE_NORTH("Ardougne north", 2617, 3333),

    /** Hosidius, the only bank in the town. */
    HOSIDIUS("Hosidius", 1748, 3599),

    /**
     * The Farming Guild's bank chest, in the middle tier. Covers every rung to the guild; the
     * north-wing bank is only a few seconds away.
     */
    FARMING_GUILD("Farming Guild bank chest", 1252, 3742),

    /** Catherby, the only bank in the town, and the one both the herb and fruit figures cross. */
    CATHERBY("Catherby", 2809, 3442),

    /**
     * Lumbridge Castle, on the second floor.
     *
     * <p>Its rungs use {@code banksUpstairs}: the walked tiles count the ground floor only,
     * so they miss two flights of stairs.
     */
    LUMBRIDGE_CASTLE("Lumbridge Castle", 3209, 3219, 2),

    /** Lletya, reached from the teleport crystal's landing. */
    LLETYA("Lletya", 2352, 3162),

    /** The Museum Camp chest; named a chest because it is one. */
    MUSEUM_CAMP("Museum Camp bank chest", 3740, 3810),

    /** Gnome Stronghold south, dead on the route from the spirit tree to the tree patch. */
    GNOME_STRONGHOLD_SOUTH("Gnome Stronghold south", 2445, 3424),

    /**
     * Gnome Stronghold north, on the second level of the Grand Tree. Reached only by the royal
     * seed pod, which lands inside the Grand Tree a level below it.
     */
    GNOME_STRONGHOLD_NORTH("Gnome Stronghold north", 2444, 3485, 1),

    /**
     * Civitas illa Fortis west, for the two Ortus Farm rungs that land away from the Hunter
     * Guild: the Civitas teleport and fairy ring AJP. Identified in game. The wiki has no
     * page for this bank, so the tile is one of its bankers', from the Civitas banker's map.
     */
    CIVITAS_ILLA_FORTIS_WEST("Civitas illa Fortis west", 1647, 3120),

    /**
     * The Hunter Guild's bank chest, for the Ortus Farm rungs that land at the guild. Anyone
     * can use the chest; only the banker beside it asks for guild membership.
     */
    HUNTER_GUILD("Hunter Guild bank chest", 1542, 3039);

    /**
     * Player-facing: a place to follow "Bank at" ("Falador east"), or a bank chest that
     * names itself ("Museum Camp bank chest"). Qualified only where a town has more than
     * one bank.
     */
    private final String displayName;
    private final int x;
    private final int y;
    /**
     * Zero for a bank on the ground.
     *
     * <p>Identity, not cost: the plane separates the two Gnome Stronghold banks, which share
     * an x and a y. Stair cost lives in {@code banksUpstairs} and
     * {@code BankProximity.FLOOR_CHANGE_TILES}.
     */
    private final int plane;

    Bank(String displayName, int x, int y) {
        this(displayName, x, y, 0);
    }

    Bank(String displayName, int x, int y, int plane) {
        this.displayName = displayName;
        this.x = x;
        this.y = y;
        this.plane = plane;
    }

    /** "Bank at Falador east", or the chest's own name, which already says "bank". */
    public String getRowTitle() {
        return displayName.endsWith("bank chest") ? displayName : "Bank at " + displayName;
    }
}
