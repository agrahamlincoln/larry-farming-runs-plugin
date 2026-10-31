package com.larryfarming.snapshot;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.With;

/**
 * One control in the farming tab's settings: a crop to tick, an item to switch, or a
 * checkbox.
 *
 * <p>Carries the config key, so the panel never decides which setting a control means.
 * Every key is a boolean read as "on": a click writes the control's new state to it.
 */
@Value
@AllArgsConstructor
public class FarmingSettingChoice {

    /** The {@code LarryConfig} key this control writes, such as {@code plantRanarr}. */
    @SerializedName("key") String key;

    /** Named for a player: "Ranarr", "Compost bins". */
    @SerializedName("label") String label;

    /** The sprite to draw, or 0 for a checkbox, which draws none. */
    @SerializedName("itemId") int itemId;

    /** The short tag drawn on the sprite, as the inventory labels it. */
    @SerializedName("tag") String tag;

    /** The Farming level the crop needs. */
    @SerializedName("level") int level;

    /** True when the setting is in force: the crop or checkbox is ticked, the item picked. */
    @SerializedName("on") boolean on;

    /**
     * True for an item the player does not own, drawn grey but still clickable. Never set
     * while ownership is unread: an unread bank shows the item as owned.
     */
    @SerializedName("missing") boolean missing;

    /**
     * True when the setting cannot apply: the player's Farming level is below
     * {@link #level}, or for a patch, the account cannot reach it yet. Not clickable.
     */
    @SerializedName("locked") boolean locked;

    /**
     * A game sprite to draw instead of an item, such as a spell's icon, or 0 to draw
     * {@link #itemId}.
     */
    @SerializedName("spriteId") int spriteId;

    /**
     * Why a missing item is missing, said after its label, or null for the usual "None seen
     * yet": "None built" for house furniture seen absent.
     */
    @SerializedName("missingNote") String missingNote;

    /**
     * True for a house item Larry has seen, built or not. The player cannot change it, so it
     * takes no click.
     */
    @SerializedName("seen") boolean seen;

    /**
     * One short line for a checkbox whose label cannot make its effect obvious, or null for
     * none.
     */
    @With
    @SerializedName("tip") String tip;

    /** A control drawn as an item, or as a plain checkbox for item 0. */
    public FarmingSettingChoice(String key, String label, int itemId, String tag, int level,
            boolean on, boolean missing, boolean locked) {
        this(key, label, itemId, tag, level, on, missing, locked, 0);
    }

    /** The same, drawn as a game sprite where {@code spriteId} is not 0. */
    public FarmingSettingChoice(String key, String label, int itemId, String tag, int level,
            boolean on, boolean missing, boolean locked, int spriteId) {
        this(key, label, itemId, tag, level, on, missing, locked, spriteId, null, false, null);
    }

    /** The same, with a missing item's reason. */
    public FarmingSettingChoice(String key, String label, int itemId, String tag, int level,
            boolean on, boolean missing, boolean locked, int spriteId, String missingNote) {
        this(key, label, itemId, tag, level, on, missing, locked, spriteId, missingNote, false,
                null);
    }
}
