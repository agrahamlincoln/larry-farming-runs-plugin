package com.larryfarming.farming.model;

import com.larryfarming.model.EquipmentSlot;
import java.util.List;
import lombok.Value;

/**
 * What goes in one equipment slot on a farm run, best first.
 *
 * <p>Shaped like a travel chain: gear comes as ordered fallbacks, such as
 * "Max cape → Farming cape → Ardougne cloak". A player wears the best tier they have
 * reached. The panel shows every tier so the player can see the next upgrade.
 */
@Value
public class LoadoutSlot {

    EquipmentSlot slot;

    /** Best first. Never empty. */
    List<GearPick> tiers;

    /**
     * Why this slot is worth filling, shown as a heading. Written about the slot rather
     * than any one item, so it stays true across tiers.
     */
    String purpose;

    /**
     * True when leaving the slot empty costs only convenience: a missing cape costs a
     * walk, while missing magic secateurs cost yield on every patch they work on.
     */
    boolean niceToHave;

    /**
     * The group this slot belongs to, or null when it stands alone. The farmer's outfit
     * spans four slots that the panel lists as one item.
     */
    String group;

    /**
     * True when carrying the item does not serve the run. The Farmer's outfit and a staff
     * for fairy rings work only while worn, and the log basket's only gain is the inventory
     * slot that wearing it frees. Everything else works from the inventory too, so carrying
     * it fills the slot as far as the run is concerned.
     */
    boolean mustWear;

    /**
     * The sentence for an owned item that is not where the run wants it, with {@code %s}
     * for the item's name mid-sentence: "Wear your %s to save an inventory slot."
     */
    String tip;

    /**
     * The tier to work toward while none is owned. The cheapest rung by default, since it
     * is the one within reach.
     */
    GearPick goal;

    /** The whole sentence for the goal row: "Earn Ardougne cloak 2 for …". */
    String goalSentence;
}
