package com.larryfarming.farming.engine;

import com.larryfarming.engine.ItemCounts;
import com.larryfarming.farming.catalogue.Loadouts;
import com.larryfarming.farming.detection.RingEnergyCharges;
import com.larryfarming.farming.model.GearPick;
import com.larryfarming.farming.model.LoadoutSlot;
import com.larryfarming.model.EquipmentSlot;
import com.larryfarming.model.ItemStack;
import com.larryfarming.model.PlayerState;
import com.larryfarming.snapshot.FarmingCarryItem;
import com.larryfarming.snapshot.FarmingUpgrade;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
    private static final Set<String> PROPER_FIRST_WORDS =
            Set.of("Farming", "Ardougne", "Explorer's", "Xeric's", "Farmer's");

    /**
     * Every slot worth filling, in catalogue order.
     *
     * @param staffWielded whether the run needs a fairy-ring staff in hand, which then
     *     holds the weapon slot (see {@link Loadouts#forRun})
     */
    public static List<SlotAdvice> plan(PlayerState state, boolean staffWielded) {
        return plan(state, staffWielded, false);
    }

    /**
     * Every slot worth filling, in catalogue order.
     *
     * <p>The ring slot earns its place only while the Explorer's ring helps this run: an
     * energy restore left today, or a Falador farm teleport the route takes. Otherwise the
     * slot is left out, so a spent ring earns no tip and no nag. Unread charges count as
     * left, since hiding a charged ring costs more than showing a spent one.
     *
     * @param staffWielded whether the run needs a fairy-ring staff in hand
     * @param ringTeleports whether the route teleports to the Falador farm by the ring,
     *     which the planner only allows while the ring has a teleport left today
     */
    public static List<SlotAdvice> plan(PlayerState state, boolean staffWielded,
            boolean ringTeleports) {
        return plan(state, staffWielded, ringTeleports, false);
    }

    /**
     * The same slots, with the log basket asked for in the cape slot.
     *
     * @param basketWorn whether the trip cuts logs with the player's own basket and no route
     *     needs a cape's teleport (see {@code CarryPlanner.basketPlace})
     */
    public static List<SlotAdvice> plan(PlayerState state, boolean staffWielded,
            boolean ringTeleports, boolean basketWorn) {
        List<LoadoutSlot> slots = Loadouts.forRun(staffWielded, basketWorn);
        List<SlotAdvice> advice = new ArrayList<>(slots.size());
        RingEnergyCharges.Charges ring = RingEnergyCharges.of(state);
        for (LoadoutSlot slot : slots) {
            if (slot.getSlot() == EquipmentSlot.RING) {
                if (!ringHelps(ring, ringTeleports)) {
                    continue;
                }
                advice.add(withRingNote(planSlot(slot, state), ring, ringTeleports));
                continue;
            }
            advice.add(planSlot(slot, state));
        }
        return advice;
    }

    private static boolean ringHelps(RingEnergyCharges.Charges ring, boolean ringTeleports) {
        return ring == null || ring.getRestoresLeft() > 0 || ringTeleports;
    }

    /**
     * The ring slot's sentences, with what the ring does for this run added: "It has 2
     * energy restores left today." and "Teleport to the Falador farm with it."
     */
    private static SlotAdvice withRingNote(SlotAdvice advice, RingEnergyCharges.Charges ring,
            boolean ringTeleports) {
        if (advice.getVerdict() == SlotAdvice.Verdict.NOTHING_YET) {
            return advice;
        }
        StringBuilder note = new StringBuilder();
        if (ring != null && ring.getRestoresLeft() > 0) {
            int left = ring.getRestoresLeft();
            note.append(" It has ").append(left)
                    .append(left == 1 ? " energy restore" : " energy restores")
                    .append(" left today.");
        }
        if (ringTeleports) {
            note.append(" Teleport to the Falador farm with it.");
        }
        // The slot's purpose names both uses. A run not teleporting by it names the one left.
        String purpose = ringTeleports ? advice.getPurpose() : "Run energy restores";
        String tip = advice.getTip().isEmpty() ? "" : advice.getTip() + note;
        return new SlotAdvice(advice.getSlot(), purpose, advice.isNiceToHave(),
                advice.getGroup(), advice.isMustWear(), tip, advice.getTiers(),
                advice.getVerdict(), advice.getBest(), advice.getBecause() + note,
                advice.isConfident(), advice.getGoal(), advice.getGoalSentence());
    }

    private static SlotAdvice planSlot(LoadoutSlot slot, PlayerState state) {
        ItemStack worn = state != null ? state.getEquipment().get(slot.getSlot()) : null;
        boolean confident = state != null && state.getBankLastSeen() != null;

        List<SlotAdvice.Tier> tiers = new ArrayList<>(slot.getTiers().size());
        // Indices rather than references: two tiers sharing an item ID would make an
        // identity comparison silently call a worn lower rung "the best you own".
        int best = -1;
        int onHandAt = -1;
        for (int i = 0; i < slot.getTiers().size(); i++) {
            GearPick pick = slot.getTiers().get(i);
            SlotAdvice.Standing standing;
            int carried = firstCarried(state, pick);
            int owned = firstOwned(state, pick);
            if (worn != null && pick.getItemIds().contains(worn.getItemId())) {
                standing = SlotAdvice.Standing.WORN;
                pick = pick.formHolding(worn.getItemId());
            } else if (carried > 0) {
                standing = SlotAdvice.Standing.CARRIED;
                pick = pick.formHolding(carried);
            } else if (owned > 0) {
                standing = SlotAdvice.Standing.OWNED;
                pick = pick.formHolding(owned);
            } else {
                standing = SlotAdvice.Standing.UNEARNED;
            }
            tiers.add(new SlotAdvice.Tier(pick, standing));
            // Best-first order means the first hit is the best, on hand or not.
            if (best < 0 && standing != SlotAdvice.Standing.UNEARNED) {
                best = i;
            }
            if (onHandAt < 0 && servesTheRun(slot, standing)) {
                onHandAt = i;
            }
        }

        if (best < 0) {
            // Nothing found means "no" only once the bank has been seen.
            if (!confident) {
                return advice(slot, tiers, SlotAdvice.Verdict.NOTHING_YET, null, "",
                        "Open your bank to check this slot.", false);
            }
            return advice(slot, tiers, SlotAdvice.Verdict.NOTHING_YET, null, "",
                    slot.getGoalSentence(), true);
        }
        // The form held, so a lunar staff is named and drawn as one.
        GearPick pick = tiers.get(best).getPick();
        if (best == onHandAt) {
            boolean wearing = tiers.get(best).getStanding() == SlotAdvice.Standing.WORN;
            return advice(slot, tiers, SlotAdvice.Verdict.SET, pick, "",
                    (wearing ? "You are wearing " : "You carry ")
                            + inSentence(pick.getDisplayName()) + ".",
                    confident);
        }
        String tip = String.format(slot.getTip(), inSentence(pick.getDisplayName()));
        return advice(slot, tiers, SlotAdvice.Verdict.SWAP, pick, tip, tip, confident);
    }

    /** Where an item must be for the run: worn, or for most items carried will do. */
    private static boolean servesTheRun(LoadoutSlot slot, SlotAdvice.Standing standing) {
        return standing == SlotAdvice.Standing.WORN
                || (!slot.isMustWear() && standing == SlotAdvice.Standing.CARRIED);
    }

    private static SlotAdvice advice(LoadoutSlot slot, List<SlotAdvice.Tier> tiers,
            SlotAdvice.Verdict verdict, GearPick best, String tip, String because,
            boolean confident) {
        return new SlotAdvice(slot.getSlot(), slot.getPurpose(), slot.isNiceToHave(),
                slot.getGroup(), slot.isMustWear(), tip, tiers, verdict, best, because,
                confident, slot.getGoal(), slot.getGoalSentence());
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
        return names.size() == 1 ? names.get(0)
                : String.join(", ", names.subList(0, names.size() - 1)) + " " + conjunction
                        + " " + names.get(names.size() - 1);
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

    private static void addEquips(List<SlotAdvice> plan, List<FarmingUpgrade> rows,
            boolean niceToHave, List<String> groupsDone) {
        for (SlotAdvice slot : plan) {
            if (slot.getVerdict() != SlotAdvice.Verdict.SWAP
                    || slot.isNiceToHave() != niceToHave) {
                continue;
            }
            if (slot.getGroup() == null) {
                rows.add(new FarmingUpgrade(FarmingUpgrade.Kind.EQUIP,
                        slot.getBest().getIconItemId(), slot.getBest().getDisplayName(),
                        slot.getTip()));
            } else if (!groupsDone.contains(slot.getGroup())) {
                // One row for the outfit, drawn as the first piece to put on.
                groupsDone.add(slot.getGroup());
                rows.add(new FarmingUpgrade(FarmingUpgrade.Kind.EQUIP,
                        slot.getBest().getIconItemId(), slot.getGroup(), Loadouts.OUTFIT_TIP));
            }
        }
    }

    private static void addGoals(List<SlotAdvice> plan, List<FarmingUpgrade> rows,
            List<String> groupsDone) {
        for (SlotAdvice slot : plan) {
            if (slot.getVerdict() != SlotAdvice.Verdict.NOTHING_YET) {
                continue;
            }
            if (slot.getGroup() == null) {
                GearPick goal = slot.getGoal();
                rows.add(new FarmingUpgrade(FarmingUpgrade.Kind.GOAL,
                        goal.getIconItemId(), goal.getDisplayName(), slot.getGoalSentence()));
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
        for (SlotAdvice slot : plan) {
            if (!group.equals(slot.getGroup())) {
                continue;
            }
            total++;
            if (slot.getVerdict() == SlotAdvice.Verdict.NOTHING_YET) {
                // Draw the group as the first piece still missing.
                if (icon == 0) {
                    icon = slot.getGoal().getIconItemId();
                }
            } else {
                have++;
            }
        }
        String progress = group + ": you have " + have + " of " + total
                + " pieces. The set gives 2.5% more Farming experience.";
        return new FarmingUpgrade(FarmingUpgrade.Kind.GOAL, icon, group, progress);
    }

    /** The plan's advice for one slot, or null when the run has none for it. */
    public static SlotAdvice at(List<SlotAdvice> plan, EquipmentSlot slot) {
        for (SlotAdvice advice : plan) {
            if (advice.getSlot() == slot) {
                return advice;
            }
        }
        return null;
    }
}
