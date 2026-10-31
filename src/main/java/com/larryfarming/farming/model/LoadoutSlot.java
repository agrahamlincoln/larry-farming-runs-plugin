package com.larryfarming.farming.model;

import com.larryfarming.model.EquipmentSlot;
import java.util.ArrayList;
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

    public static Builder builder(EquipmentSlot slot, String purpose) {
        return new Builder(slot, purpose);
    }

    /** Hand-written so a slot's definition reads one tier per line. */
    public static final class Builder {

        private final EquipmentSlot slot;
        private final String purpose;
        private final List<GearPick> tiers = new ArrayList<>();
        private boolean niceToHave;
        private String group;
        private boolean mustWear;
        private String tip = "Wear your %s to save an inventory slot.";
        private GearPick goal;
        private String goalSentence;

        private Builder(EquipmentSlot slot, String purpose) {
            this.slot = slot;
            this.purpose = purpose;
        }

        public Builder partOf(String group) {
            this.group = group;
            return this;
        }

        public Builder tier(String displayName, String benefit, int... itemIds) {
            return tier(GearPick.of(displayName, benefit, itemIds));
        }

        /** A tier built elsewhere, such as one any of several named items fills. */
        public Builder tier(GearPick pick) {
            tiers.add(pick);
            return this;
        }

        /** Replaces the default goal, the last tier with an "Earn …" sentence. */
        public Builder goal(GearPick pick, String sentence) {
            this.goal = pick;
            this.goalSentence = sentence;
            return this;
        }

        /** The item works only while worn; see {@link LoadoutSlot#mustWear}. */
        public Builder mustWear() {
            this.mustWear = true;
            return this;
        }

        /**
         * Replaces the default "Wear your %s to save an inventory slot.", which suits only an
         * item that works from the inventory.
         */
        public Builder tip(String template) {
            this.tip = template;
            return this;
        }

        public Builder niceToHave() {
            this.niceToHave = true;
            return this;
        }

        public LoadoutSlot build() {
            if (tiers.isEmpty()) {
                throw new IllegalStateException(
                        "nothing to put in the " + slot.getDisplayName() + " slot");
            }
            GearPick cheapest = tiers.get(tiers.size() - 1);
            GearPick target = goal != null ? goal : cheapest;
            String sentence = goalSentence != null ? goalSentence
                    : "Earn " + target.getDisplayName() + " for " + target.getBenefit() + ".";
            return new LoadoutSlot(slot, List.copyOf(tiers), purpose, niceToHave, group,
                    mustWear, tip, target, sentence);
        }
    }
}
