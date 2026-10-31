package com.larryfarming.model;

public enum Skill {
    ATTACK("Attack"),
    DEFENCE("Defence"),
    STRENGTH("Strength"),
    HITPOINTS("Hitpoints"),
    RANGED("Ranged"),
    PRAYER("Prayer"),
    MAGIC("Magic"),
    COOKING("Cooking"),
    WOODCUTTING("Woodcutting"),
    FLETCHING("Fletching"),
    FISHING("Fishing"),
    FIREMAKING("Firemaking"),
    CRAFTING("Crafting"),
    SMITHING("Smithing"),
    MINING("Mining"),
    HERBLORE("Herblore"),
    AGILITY("Agility"),
    THIEVING("Thieving"),
    SLAYER("Slayer"),
    FARMING("Farming"),
    RUNECRAFT("Runecraft"),
    HUNTER("Hunter"),
    CONSTRUCTION("Construction"),
    SAILING("Sailing");

    private final String displayName;

    Skill(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static Skill fromString(String name) {
        for (Skill s : values()) {
            if (s.displayName.equalsIgnoreCase(name)) return s;
        }
        if ("runecrafting".equalsIgnoreCase(name)) return RUNECRAFT;
        throw new IllegalArgumentException("Unknown skill: " + name);
    }
}
