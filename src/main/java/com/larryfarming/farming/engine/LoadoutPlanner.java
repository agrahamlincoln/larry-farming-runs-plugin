package com.larryfarming.farming.engine;

import com.larryfarming.engine.ItemCounts;
import com.larryfarming.farming.catalogue.Loadouts;
import com.larryfarming.farming.model.GearPick;
import com.larryfarming.farming.model.LoadoutSlot;
import com.larryfarming.model.*;
import com.larryfarming.snapshot.FarmingCarryItem;
import com.larryfarming.snapshot.FarmingUpgrade;
import java.util.*;

/**
 * Works out what the player should be wearing or carrying, and what they have instead.
 *
 * <p>Each tier is worn, carried, owned elsewhere or unearned: "your Ardougne cloak is in
 * the bank" is actionable before the run, and "you do not have one" is not.
 *
 * <p>Only the Farmer's outfit and a fairy-ring staff must be worn (see {@link Loadouts}).
 * Every other item works from the inventory, so carrying it fills its slot as far as the
 * run is concerned and earns no advice. Owning it elsewhere earns a tip to wear it, which
 * saves a slot.
 *
 * <p>Nothing here is a requirement. A farm run with an empty cape slot still works, so
 * the output is graded advice, and unearned tiers are reported as goals.
 */
public final class LoadoutPlanner {

    private LoadoutPlanner() {
    }

    /**
     * Item names that start with a proper noun keep their capital mid-sentence ("your
     * Explorer's ring 2"). Every other name is lower-cased there ("your magic secateurs").
     */
    private static final Set<String> PROPER_FIRST_WORDS = Set.of("Farming", "Ardougne", "Explorer's", "Xeric's", "Farmer's");

    /**
     * Every slot worth filling, in catalogue order.
     *
     * <p>The ring slot earns its place only while the route takes the Explorer's ring's
     * Falador farm teleport. Otherwise the slot is left out, so the ring earns no tip and no
     * nag.
     *
     * @param staffWielded whether the run needs a fairy-ring staff in hand, which then
     *     holds the weapon slot (see {@link Loadouts#forRun})
     * @param ringTeleports whether the route teleports to the Falador farm by the ring,
     *     which the planner only allows while the ring has a teleport left today
     * @param basketWorn whether the trip cuts logs with the player's own basket and no route
     *     needs a cape's teleport (see {@code CarryPlanner.basketPlace})
     */
    public static List<SlotAdvice> plan(PlayerState state, boolean staffWielded, boolean ringTeleports, boolean basketWorn) {
        List<LoadoutSlot> slots = Loadouts.forRun(staffWielded, basketWorn);
        List<SlotAdvice> advice = new ArrayList<>(slots.size());
        for (LoadoutSlot slot : slots) {
            if (slot.getSlot() == EquipmentSlot.RING) {
                if (ringTeleports) {
                    advice.add(withRingNote(planSlot(slot, state)));
                }
                continue;
            }
            advice.add(planSlot(slot, state));
        }
        return advice;
    }

    /** The ring slot's tip, with "Teleport to the Falador farm with it." added. */
    private static SlotAdvice withRingNote(SlotAdvice advice) {
        String tip = advice.getTip();
        return tip.isEmpty() ? advice : advice.withTip(tip + " Teleport to the Falador farm with it.");
    }

    /**
     * The slot's verdict, decided by the first tier the player holds: best-first order
     * makes it the best they own, on hand or not. Nothing held is a goal, whether or not the
     * bank has been seen.
     */
    private static SlotAdvice planSlot(LoadoutSlot slot, PlayerState state) {
        ItemStack worn = state != null ? state.getEquipment().get(slot.getSlot()) : null;
        for (GearPick pick : slot.getTiers()) {
            // The form held, so a lunar staff is named and drawn as one.
            if (worn != null && pick.getItemIds().contains(worn.getItemId())) {
                return new SlotAdvice(slot, SlotAdvice.Verdict.SET, pick.formHolding(worn.getItemId()), "");
            }
            int carried = firstCarried(state, pick);
            if (carried > 0) {
                // Carrying serves the run for every item that need not be worn.
                GearPick held = pick.formHolding(carried);
                return slot.isMustWear() ? swap(slot, held) : new SlotAdvice(slot, SlotAdvice.Verdict.SET, held, "");
            }
            int owned = firstOwned(state, pick);
            if (owned > 0) {
                return swap(slot, pick.formHolding(owned));
            }
        }
        return new SlotAdvice(slot, SlotAdvice.Verdict.NOTHING_YET, null, "");
    }

    /** Advice to put on or bring {@code best}, which the player owns but not where the run wants it. */
    private static SlotAdvice swap(LoadoutSlot slot, GearPick best) {
        return new SlotAdvice(slot, SlotAdvice.Verdict.SWAP, best, String.format(slot.getTip(), inSentence(best.getDisplayName())));
    }

    /** An item name for mid-sentence, as the game writes it there. */
    static String inSentence(String name) {
        int space = name.indexOf(' ');
        String first = space < 0 ? name : name.substring(0, space);
        if (PROPER_FIRST_WORDS.contains(first)) {
            return name;
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    /** These items' names mid-sentence: "spade, seed dibber and ultracompost". */
    static String inSentence(List<FarmingCarryItem> items, String conjunction) {
        List<String> names = new ArrayList<>(items.size());
        for (FarmingCarryItem item : items) {
            names.add(inSentence(item.getName()));
        }
        return LookOnce.joined(names, conjunction);
    }

    /** The first of this pick's items in the inventory, or 0 when none is. */
    private static int firstCarried(PlayerState state, GearPick pick) {
        for (int itemId : pick.getItemIds()) {
            if (ItemCounts.countCarried(state, itemId, null) > 0) {
                return itemId;
            }
        }
        return 0;
    }

    /**
     * The first charge level, trim or form of this item the player owns, including in the
     * bank, or 0 when they own none.
     */
    private static int firstOwned(PlayerState state, GearPick pick) {
        for (int itemId : pick.getItemIds()) {
            if (ItemCounts.countOwned(state, itemId, null) > 0) {
                return itemId;
            }
        }
        return 0;
    }

    /**
     * The gear list as rows, ready to render: what to put on or bring now, then what to
     * work toward. Every row's detail is the whole sentence to show.
     *
     * <p>Slots that are already right are left out. Grouped slots collapse to one row: the
     * Farmer's outfit is one decision across four slots.
     *
     * <p>Things to act on come first, and among them the essential slots before the
     * nice-to-have ones.
     */
    public static List<FarmingUpgrade> upgrades(List<SlotAdvice> plan) {
        List<FarmingUpgrade> rows = new ArrayList<>();
        List<String> groupsDone = new ArrayList<>();
        addEquips(plan, rows, false, groupsDone);
        addEquips(plan, rows, true, groupsDone);
        addGoals(plan, rows, groupsDone);
        return rows;
    }

    private static void addEquips(List<SlotAdvice> plan, List<FarmingUpgrade> rows, boolean niceToHave, List<String> groupsDone) {
        for (SlotAdvice advice : plan) {
            String group = advice.getSlot().getGroup();
            if (advice.getVerdict() != SlotAdvice.Verdict.SWAP || advice.getSlot().isNiceToHave() != niceToHave) {
                continue;
            }
            if (group == null) {
                rows.add(new FarmingUpgrade(FarmingUpgrade.Kind.EQUIP, advice.getBest().getIconItemId(), advice.getBest().getDisplayName(),
                        advice.getTip()));
            } else if (!groupsDone.contains(group)) {
                // One row for the outfit, drawn as the first piece to put on.
                groupsDone.add(group);
                rows.add(new FarmingUpgrade(FarmingUpgrade.Kind.EQUIP,
                        advice.getBest().getIconItemId(), group, Loadouts.OUTFIT_TIP));
            }
        }
    }

    private static void addGoals(List<SlotAdvice> plan, List<FarmingUpgrade> rows, List<String> groupsDone) {
        for (SlotAdvice advice : plan) {
            LoadoutSlot slot = advice.getSlot();
            if (advice.getVerdict() != SlotAdvice.Verdict.NOTHING_YET) {
                continue;
            }
            if (slot.getGroup() == null) {
                GearPick goal = slot.getGoal();
                rows.add(new FarmingUpgrade(FarmingUpgrade.Kind.GOAL, goal.getIconItemId(), goal.getDisplayName(), slot.getGoalSentence()));
            } else if (!groupsDone.contains(slot.getGroup())) {
                groupsDone.add(slot.getGroup());
                rows.add(groupRow(plan, slot.getGroup()));
            }
        }
    }

    /** One row standing for every slot in a group, counting how far along the player is. */
    private static FarmingUpgrade groupRow(List<SlotAdvice> plan, String group) {
        int total = 0;
        int have = 0;
        int icon = 0;
        for (SlotAdvice advice : plan) {
            if (!group.equals(advice.getSlot().getGroup())) {
                continue;
            }
            total++;
            if (advice.getVerdict() == SlotAdvice.Verdict.NOTHING_YET) {
                // Draw the group as the first piece still missing.
                if (icon == 0) {
                    icon = advice.getSlot().getGoal().getIconItemId();
                }
            } else {
                have++;
            }
        }
        String progress = group + ": you have " + have + " of " + total + " pieces. The set gives 2.5% more Farming experience.";
        return new FarmingUpgrade(FarmingUpgrade.Kind.GOAL, icon, group, progress);
    }
}
