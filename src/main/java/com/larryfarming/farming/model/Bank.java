package com.larryfarming.farming.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The bank a {@link TravelOption}'s detour figure was walked to.
 *
 * <p>Lets a row name the bank rather than the arrival: two Ortus Farm rungs bank in Civitas
 * illa Fortis and four at the Hunter Guild, none at Ortus Farm.
 *
 * <p><b>On the rung, beside the tiles, never on the {@link Arrival}</b>, because one arrival's
 * rungs can land near different banks: the Tree Gnome Stronghold's royal seed pod puts the
 * player at the north bank while every other rung there walks to the south.
 *
 * <p>Each bank was identified by walking the route to it, by being the town's
 * only bank, or (Civitas illa Fortis) by checking in game.
 */
@Getter
@RequiredArgsConstructor
public enum Bank {

    /**
     * Falador east, for the park and the farm alike. The walk to this bank reproduces the
     * park and farm detour figures to within a few tiles.
     */
    FALADOR_EAST("Falador east"),

    /** Varrock east, which reproduces the detour figures; the west bank is out by thirty tiles. */
    VARROCK_EAST("Varrock east"),

    /** Canifis, the only bank in the town. */
    CANIFIS("Canifis"),

    /** Al Kharid, the only bank in the city. */
    AL_KHARID("Al Kharid"),

    /** Ardougne south, for the cloak and fairy ring Monastery rungs; the north bank is further. */
    ARDOUGNE_SOUTH("Ardougne south"),

    /**
     * Ardougne north, for the farm. The cloak lands on the farm patch, so the detour is an
     * out-and-back: 87 tiles to this bank against 131 to the south bank.
     */
    ARDOUGNE_NORTH("Ardougne north"),

    /** Hosidius, the only bank in the town. */
    HOSIDIUS("Hosidius"),

    /**
     * The Farming Guild's bank chest, in the middle tier. Covers every rung to the guild; the
     * north-wing bank is only a few seconds away.
     */
    FARMING_GUILD("Farming Guild bank chest"),

    /** Catherby, the only bank in the town, and the one both the herb and fruit figures cross. */
    CATHERBY("Catherby"),

    /**
     * Lumbridge Castle, on the second floor.
     *
     * <p>Its rungs use {@code banksUpstairs}: the walked tiles count the ground floor only,
     * so they miss two flights of stairs.
     */
    LUMBRIDGE_CASTLE("Lumbridge Castle"),

    /** Lletya, reached from the teleport crystal's landing. */
    LLETYA("Lletya"),

    /** The Museum Camp chest; named a chest because it is one. */
    MUSEUM_CAMP("Museum Camp bank chest"),

    /** Gnome Stronghold south, dead on the route from the spirit tree to the tree patch. */
    GNOME_STRONGHOLD_SOUTH("Gnome Stronghold south"),

    /**
     * Gnome Stronghold north, on the second level of the Grand Tree. Reached only by the royal
     * seed pod, which lands inside the Grand Tree a level below it.
     */
    GNOME_STRONGHOLD_NORTH("Gnome Stronghold north"),

    /**
     * Civitas illa Fortis west, for the two Ortus Farm rungs that land away from the Hunter
     * Guild: the Civitas teleport and fairy ring AJP. Identified in game.
     */
    CIVITAS_ILLA_FORTIS_WEST("Civitas illa Fortis west"),

    /**
     * The Hunter Guild's bank chest, for the Ortus Farm rungs that land at the guild. Anyone
     * can use the chest; only the banker beside it asks for guild membership.
     */
    HUNTER_GUILD("Hunter Guild bank chest");

    /**
     * Player-facing: a place to follow "Bank at" ("Falador east"), or a bank chest that
     * names itself ("Museum Camp bank chest"). Qualified only where a town has more than
     * one bank.
     */
    private final String displayName;

    /** "Bank at Falador east", or the chest's own name, which already says "bank". */
    public String getRowTitle() {
        return displayName.endsWith("bank chest") ? displayName : "Bank at " + displayName;
    }
}
