package com.larryfarming.model;

import java.util.Locale;

public enum Skill {
    ATTACK,
    DEFENCE,
    STRENGTH,
    HITPOINTS,
    RANGED,
    PRAYER,
    MAGIC,
    COOKING,
    WOODCUTTING,
    FLETCHING,
    FISHING,
    FIREMAKING,
    CRAFTING,
    SMITHING,
    MINING,
    HERBLORE,
    AGILITY,
    THIEVING,
    SLAYER,
    FARMING,
    RUNECRAFT,
    HUNTER,
    CONSTRUCTION,
    SAILING;

    /** The skill as the game names it: every skill's name is one capitalised word. */
    public String getDisplayName() {
        return name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT);
    }

    /** Matches the game's skill name, or the constant's, ignoring case. */
    public static Skill fromString(String name) {
        for (Skill s : values()) {
            if (s.name().equalsIgnoreCase(name)) return s;
        }
        if ("runecrafting".equalsIgnoreCase(name)) return RUNECRAFT;
        throw new IllegalArgumentException("Unknown skill: " + name);
    }
}
