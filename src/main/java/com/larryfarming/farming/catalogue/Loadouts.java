package com.larryfarming.farming.catalogue;

import com.larryfarming.data.Tables;
import com.larryfarming.farming.model.GearPick;
import com.larryfarming.farming.model.LoadoutSlot;
import com.larryfarming.model.EquipmentSlot;
import java.util.*;
import net.runelite.api.gameval.ItemID;

/**
 * What to wear on a farm run, read from {@code loadouts.tsv}.
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
 * <p>The weapon slot holds the staff on a run that needs one. Magic secateurs work from
 * the inventory, so owned ones are something to pack and the packing list names them; their
 * slot here only sets them as a goal for a player without a pair.
 *
 * <p>Slots with nothing useful to say are left out.
 */
public final class Loadouts {

    private Loadouts() {
    }

    /** Magic secateurs, which raise yield from the inventory as well as the hand. */
    public static final int MAGIC_SECATEURS = ItemID.FAIRY_ENCHANTED_SECATEURS;
    public static final int DRAMEN_STAFF = 772;
    public static final int LUNAR_STAFF = 9084;

    /** The one sentence for an outfit owned but not all worn. */
    public static final String OUTFIT_TIP = "Wear the Farmer's outfit for 2.5% more Farming experience.";

    /**
     * Slots {@link #forRun} adds only on some runs: the staff when a ring needs it, the
     * secateurs while unowned, the basket when worn. The rest always show.
     */
    private static final Set<String> CHOSEN = Set.of("SECATEURS", "STAFF", "LOG_BASKET");

    /** Every slot by its key, in the table's order, which is the order the panel shows. */
    private static final Map<String, LoadoutSlot> SLOTS = read(Tables.read("/com/larryfarming/loadouts.tsv"));

    private static Map<String, LoadoutSlot> read(List<Tables.Row> rows) {
        Map<String, List<Tables.Row>> byKey = new LinkedHashMap<>();
        for (Tables.Row row : rows) {
            byKey.computeIfAbsent(row.text(1), k -> new ArrayList<>()).add(row);
        }
        Map<String, LoadoutSlot> slots = new LinkedHashMap<>();
        byKey.forEach((key, group) -> slots.put(key, slot(group)));
        if (!slots.keySet().containsAll(CHOSEN)) {
            throw new IllegalStateException("loadouts.tsv: needs a slot for each of " + CHOSEN);
        }
        return Collections.unmodifiableMap(slots);
    }

    /** One slot from its rows: the slot row first, then its forms, tiers and goal. */
    private static LoadoutSlot slot(List<Tables.Row> rows) {
        Tables.Row head = rows.get(0);
        if (!head.text(0).equals("slot")) {
            throw head.error("a slot's rows start with its slot row");
        }
        head.requireWidth(7);
        List<GearPick> tiers = new ArrayList<>();
        List<GearPick> forms = new ArrayList<>();
        GearPick goal = null;
        String sentence = null;
        for (Tables.Row row : rows.subList(1, rows.size())) {
            switch (row.text(0)) {
                case "form":
                    forms.add(pick(row));
                    break;
                case "tier":
                    tiers.add(row.requireWidth(6).integers(4).length > 0 ? pick(row)
                            : GearPick.anyOf(row.text(2), row.text(3), forms.toArray(new GearPick[0])));
                    break;
                case "goal":
                    goal = forms.stream().filter(f -> f.getDisplayName().equals(row.text(2))).findFirst()
                            .orElseThrow(() -> row.error("no form " + row.text(2)));
                    sentence = row.requireWidth(4).text(3);
                    break;
                default:
                    throw row.error("no row kind " + row.text(0) + " within a slot");
            }
        }
        if (tiers.isEmpty()) {
            throw head.error("nothing to put in the slot");
        }
        // Without a goal row, the goal is the cheapest tier, the one within reach.
        GearPick target = goal != null ? goal : tiers.get(tiers.size() - 1);
        return new LoadoutSlot(head.constant(EquipmentSlot.class, 2), List.copyOf(tiers), head.flag(4, "yes"),
                head.optionalText(6), head.flag(5, "yes"), head.text(3), target,
                sentence != null ? sentence : "Earn " + target.getDisplayName() + " for " + target.getBenefit() + ".");
    }

    private static GearPick pick(Tables.Row row) {
        return GearPick.of(row.requireWidth(6).text(2), row.text(3), row.integers(4));
    }

    /** The name of the worn gear this item is, such as "Max cape", or null when no slot lists it. */
    public static String nameOf(int itemId) {
        for (LoadoutSlot slot : SLOTS.values()) {
            for (GearPick tier : slot.getTiers()) {
                if (tier.getItemIds().contains(itemId)) {
                    return tier.formHolding(itemId).getDisplayName();
                }
            }
        }
        return null;
    }

    /**
     * The slots worth filling on a run, in the order the panel shows them.
     *
     * @param staffWielded whether the run uses a fairy ring that needs a staff in hand
     * @param basketWorn whether the log basket takes the cape slot
     * @param ownsSecateurs whether the player owns magic secateurs, which the packing list
     *     then names; the slot stays only to set them as a goal. Its tip is empty, so this
     *     must count ownership as {@code LoadoutPlanner} does, or a pair it finds would
     *     earn a blank swap row
     */
    public static List<LoadoutSlot> forRun(boolean staffWielded, boolean basketWorn, boolean ownsSecateurs) {
        List<LoadoutSlot> slots = new ArrayList<>(SLOTS.size());
        if (staffWielded) {
            slots.add(SLOTS.get("STAFF"));
        }
        if (!ownsSecateurs) {
            slots.add(SLOTS.get("SECATEURS"));
        }
        SLOTS.forEach((key, slot) -> {
            if (!CHOSEN.contains(key)) {
                slots.add(basketWorn && slot.getSlot() == EquipmentSlot.CAPE ? SLOTS.get("LOG_BASKET") : slot);
            }
        });
        return slots;
    }
}
