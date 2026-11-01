package com.larryfarming.model;

public enum EquipmentSlot {
    HEAD("Head"),
    CAPE("Cape"),
    AMULET("Amulet"),
    WEAPON("Weapon"),
    BODY("Body"),
    SHIELD("Shield"),
    LEGS("Legs"),
    GLOVES("Gloves"),
    BOOTS("Boots"),
    RING("Ring"),
    AMMO("Ammo");

    private final String displayName;

    EquipmentSlot(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
