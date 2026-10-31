package com.larryfarming.farming.catalogue;

import com.larryfarming.farming.model.GearPick;
import com.larryfarming.farming.model.LoadoutSlot;
import com.larryfarming.model.EquipmentSlot;
import java.util.ArrayList;
import java.util.List;

/**
 * What to wear on a farm run.
 *
 * <p>Gear comes as ordered fallbacks ("Max cape → Farming cape → Ardougne cloak"), plus
 * magic secateurs and the farmer's outfit. Encoded as tiers, not a fixed kit.
 *
 * <p>Only two things must be worn to work (wiki): the Farmer's outfit, whose experience
 * bonus counts only while worn, and a dramen or lunar staff, which a fairy ring needs
 * wielded unless the elite Lumbridge &amp; Draynor diary is done. Everything else works
 * from the inventory: the Explorer's ring and Ardougne cloak teleport from it, Xeric's
 * talisman is rubbed, and magic secateurs raise yield from it. Wearing those only saves
 * inventory slots. The log basket, worn on a tree run that needs no cape, is asked for
 * only worn, since the slot it frees is all it adds.
 *
 * <p>The weapon slot holds the staff on a run that needs one, and the secateurs
 * otherwise. Swapping them mid-run buys nothing, so with the staff wielded the secateurs
 * ride in the inventory and the packing list names them.
 *
 * <p>Slots with nothing useful to say are left out.
 */
public final class Loadouts {

    private Loadouts() {
    }

    /** The four outfit slots answer to one name in the upgrade list. */
    public static final String FARMERS_OUTFIT = "Farmer's outfit";
    private static final String OUTFIT_PURPOSE = "2.5% more Farming experience as a set";

    /** Magic secateurs, which raise yield from the inventory as well as the hand. */
    public static final int MAGIC_SECATEURS = 7409;
    public static final int DRAMEN_STAFF = 772;
    public static final int LUNAR_STAFF = 9084;

    /**
     * Wiki "Magic secateurs": herbs, allotments, bushes and more, but not seaweed or cacti.
     * The sentence names the three a farm run sees most.
     */
    private static final LoadoutSlot SECATEURS = LoadoutSlot.builder(EquipmentSlot.WEAPON,
                    "More herbs, allotment crops and berries")
            .tier("Magic secateurs", "more herbs, allotment crops and berries",
                    MAGIC_SECATEURS)
            .tip("Bring your %s. They give more herbs, allotment crops and berries.")
            .build();

    private static final GearPick DRAMEN = GearPick.of("Dramen staff", "fairy rings",
            DRAMEN_STAFF);

    /**
     * One tier, not two: the staves open a ring alike, so a ladder would ask a lunar-staff
     * wielder to swap to a dramen staff for nothing. The goal names the dramen staff, which
     * Lost City makes and which comes long before Lunar Diplomacy.
     */
    private static final LoadoutSlot STAFF = LoadoutSlot.builder(EquipmentSlot.WEAPON,
                    "Opens fairy rings")
            .tier(GearPick.anyOf("Dramen or lunar staff", "fairy rings", DRAMEN,
                    GearPick.of("Lunar staff", "fairy rings", LUNAR_STAFF)))
            .goal(DRAMEN, "Make a dramen staff in Lost City to use fairy rings.")
            .mustWear()
            .tip("Wield your %s to use fairy rings.")
            .build();

    private static final List<LoadoutSlot> THE_REST = List.of(
            // Best first.
            LoadoutSlot.builder(EquipmentSlot.CAPE, "A teleport to a farming patch")
                    // Not every max cape variant is listed. An unlisted variant falls
                    // through to the Farming cape, which the player must own.
                    .tier("Max cape", "the Farming cape teleport",
                            13280, 13342, 13329, 20447, 24134, 13337, 13331, 13333, 13335,
                            20760, 21285, 21289, 24133, 21776, 24232, 24238, 24242)
                    .tier("Farming cape", "unlimited teleports to the Farming Guild",
                            9810, 9811)
                    .tier("Ardougne cloak 4", "unlimited teleports to the Ardougne patch",
                            13124)
                    .tier("Ardougne cloak 3", "five teleports a day to the Ardougne patch",
                            13123)
                    .tier("Ardougne cloak 2", "three teleports a day to the Ardougne patch",
                            13122)
                    .build(),

            // Ring 1 has no teleport but restores run energy, which earns the slot. Every
            // tier restores energy from the inventory too (wiki), so wearing saves a slot.
            // LoadoutPlanner drops the slot on a run the ring cannot help.
            LoadoutSlot.builder(EquipmentSlot.RING,
                            "Falador farm teleports and run energy restores")
                    .tier("Explorer's ring 4", "unlimited Falador teleports", 13128)
                    .tier("Explorer's ring 3", "unlimited Falador teleports", 13127)
                    .tier("Explorer's ring 2", "three Falador teleports a day", 13126)
                    .tier("Explorer's ring 1", "run energy restores", 13125)
                    .tip("Wear your %s to save a slot.")
                    .build(),

            LoadoutSlot.builder(EquipmentSlot.AMULET, "A teleport towards the Farming Guild")
                    // Excludes the uncharged necklace (11113), which teleports nowhere.
                    .tier("Skills necklace", "teleports to the Farming Guild",
                            11105, 11107, 11109, 11111, 11968, 11970)
                    .tier("Xeric's talisman", "teleports into Kourend", 13393)
                    .niceToHave()
                    .build(),

            // Four slots, one decision: partOf groups them under one name in the panel.
            outfitPiece(EquipmentSlot.HEAD, "Farmer's strawhat", 13646, 13647),
            // The farmer's shirt counts the same as the jacket.
            outfitPiece(EquipmentSlot.BODY, "Farmer's jacket", 13642, 13643),
            outfitPiece(EquipmentSlot.LEGS, "Farmer's boro trousers", 13640, 13641),
            outfitPiece(EquipmentSlot.BOOTS, "Farmer's boots", 13644, 13645));

    private static LoadoutSlot outfitPiece(EquipmentSlot slot, String name, int... itemIds) {
        return LoadoutSlot.builder(slot, OUTFIT_PURPOSE)
                .tier(name, "Farming experience", itemIds)
                .niceToHave()
                .mustWear()
                .partOf(FARMERS_OUTFIT)
                .tip("Wear your %s for more Farming experience.")
                .build();
    }

    /** The one sentence for an outfit owned but not all worn. */
    public static final String OUTFIT_TIP =
            "Wear the Farmer's outfit for 2.5% more Farming experience.";

    /**
     * The log basket, worn in the cape slot (wiki infobox: slot cape). Worn, it costs no
     * inventory slot; carried, it costs the one slot that wearing it frees, so only wearing
     * it counts here.
     */
    private static final LoadoutSlot LOG_BASKET = LoadoutSlot.builder(EquipmentSlot.CAPE,
                    "Holds the logs you cut without an inventory slot")
            .tier("Log basket", "28 logs without an inventory slot",
                    28140, 28142, 28143, 28145)
            .niceToHave()
            .mustWear()
            .tip("Wear your %s to save an inventory slot.")
            .build();

    /**
     * The slots worth filling on a run, in the order the panel shows them.
     *
     * @param staffWielded whether the run uses a fairy ring that needs a staff in hand,
     *     which then takes the weapon slot from the secateurs
     */
    public static List<LoadoutSlot> forRun(boolean staffWielded) {
        return forRun(staffWielded, false);
    }

    /** The same slots, with the log basket in the cape slot when {@code basketWorn}. */
    public static List<LoadoutSlot> forRun(boolean staffWielded, boolean basketWorn) {
        List<LoadoutSlot> slots = new ArrayList<>(THE_REST.size() + 1);
        slots.add(staffWielded ? STAFF : SECATEURS);
        for (LoadoutSlot slot : THE_REST) {
            slots.add(basketWorn && slot.getSlot() == EquipmentSlot.CAPE ? LOG_BASKET : slot);
        }
        return slots;
    }
}
