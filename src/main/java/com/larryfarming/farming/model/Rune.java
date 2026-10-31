package com.larryfarming.farming.model;

/**
 * A rune a teleport spell burns, named so a packing list can say "3 × Law rune" rather
 * than an item ID.
 *
 * <p>Lists only runes that appear in the cost of a spell some travel chain offers, not every
 * rune in the game.
 */
public enum Rune {

    AIR(556, "Air rune"),
    WATER(555, "Water rune"),
    EARTH(557, "Earth rune"),
    FIRE(554, "Fire rune"),
    LAW(563, "Law rune"),
    SOUL(566, "Soul rune"),
    ASTRAL(9075, "Astral rune"),
    COSMIC(564, "Cosmic rune"),
    NATURE(561, "Nature rune"),
    BLOOD(565, "Blood rune");

    private final int itemId;
    private final String displayName;

    Rune(int itemId, String displayName) {
        this.itemId = itemId;
        this.displayName = displayName;
    }

    public int getItemId() {
        return itemId;
    }

    public String getDisplayName() {
        return displayName;
    }
}
