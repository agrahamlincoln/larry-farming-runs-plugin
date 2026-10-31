package com.larryfarming.engine;

import com.larryfarming.model.EquipmentSlot;
import com.larryfarming.model.ItemStack;
import com.larryfarming.model.PlayerState;
import com.larryfarming.model.QuestStatus;
import com.larryfarming.model.Skill;
import com.larryfarming.model.SkillState;
import lombok.Value;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Folds one game event into the {@link PlayerState}. Stateless: every method takes the state
 * it changes.
 */
public final class EventApplier {

    private EventApplier() {
    }

    @Value
    public static class SkillSnapshot {
        int level;
        long xp;
    }

    public static void applyStatChanged(PlayerState state, String skillName, long xp, int level) {
        Skill skill;
        try {
            skill = Skill.fromString(skillName);
        } catch (IllegalArgumentException e) {
            return;
        }
        state.getSkills().put(skill, new SkillState(level, xp));
    }

    public static void applyQuestCompleted(PlayerState state, String questName) {
        state.getQuests().put(questName, QuestStatus.COMPLETED);
    }

    /**
     * The account as the client reports it at login: every skill, the quests finished or
     * started, and what the player is carrying and wearing.
     */
    public static void applyLoginState(
            PlayerState state,
            Map<String, SkillSnapshot> skills,
            List<String> completedQuests,
            List<String> startedQuests,
            List<ItemStack> inventory,
            Map<EquipmentSlot, ItemStack> equipment) {

        if (skills != null) {
            for (Map.Entry<String, SkillSnapshot> entry : skills.entrySet()) {
                try {
                    Skill skill = Skill.fromString(entry.getKey());
                    SkillSnapshot snap = entry.getValue();
                    state.getSkills().put(skill, new SkillState(snap.getLevel(), snap.getXp()));
                } catch (IllegalArgumentException e) {
                    // Skip unknown skills
                }
            }
        }

        if (completedQuests != null) {
            for (String quest : completedQuests) {
                state.getQuests().put(quest, QuestStatus.COMPLETED);
            }
        }

        if (startedQuests != null) {
            for (String quest : startedQuests) {
                state.getQuests().put(quest, QuestStatus.IN_PROGRESS);
            }
        }

        if (inventory != null) {
            state.setInventory(inventory);
        }

        if (equipment != null) {
            state.getEquipment().clear();
            state.getEquipment().putAll(equipment);
        }
    }

    public static void applyInventoryChanged(PlayerState state, List<ItemStack> items) {
        state.setInventory(items);
    }

    public static void applyBankChanged(PlayerState state, List<ItemStack> items) {
        state.setBank(items);
        state.setBankLastSeen(Instant.now());
    }

    /**
     * Replace the tracked seed vault contents. Reported only when the player opens the
     * vault, so like the bank it is a snapshot of the last look, not a live figure.
     */
    public static void applySeedVaultChanged(PlayerState state, List<ItemStack> items) {
        state.setSeedVault(items);
        state.setSeedVaultLastSeen(Instant.now());
    }

    /**
     * Replace the tracked seed box contents and stamp them as verified.
     *
     * <p>Fed by two sources. The item container ({@code SEED_BOX}, 573) has never been
     * observed to arrive. The chat listing from checking the box does arrive: an empty box
     * as an empty list, a full one as the seeds it names. Either is a whole-box statement, so
     * it replaces the tally rather than adjusting it. This is the only way an over-count
     * comes down.
     */
    public static void applySeedBoxChanged(PlayerState state, List<ItemStack> items) {
        state.setSeedBox(items);
        state.setSeedBoxLastSeen(Instant.now());
        // The only place verifiedAt is stamped: every other seed-box path adjusts a tally.
        state.setSeedBoxVerifiedAt(Instant.now());
    }

    /**
     * Adds seeds the player has just put in the box.
     *
     * <p>A running tally, unlike every other container here, because the game never sends
     * inventory {@code SEED_BOX} unasked. Seeds boxed before the plugin watched are
     * invisible and a missed movement is never corrected, so the tally drifts both ways.
     *
     * <p>{@link #applySeedBoxTaken} clamps at zero, which clears an under-count, but an
     * over-count survives every empty. Only {@link #applySeedBoxChanged} overwrites the whole
     * box.
     */
    public static void applySeedBoxStored(PlayerState state, ItemStack stored) {
        List<ItemStack> box = new ArrayList<>(state.getSeedBox());
        for (int i = 0; i < box.size(); i++) {
            ItemStack held = box.get(i);
            if (held.getItemId() == stored.getItemId()) {
                box.set(i, new ItemStack(held.getItemId(),
                        held.getQuantity() + stored.getQuantity(), held.getName()));
                commitSeedBox(state, box, stored.getItemId(), stored.getQuantity());
                return;
            }
        }
        box.add(stored);
        commitSeedBox(state, box, stored.getItemId(), stored.getQuantity());
    }

    /**
     * Removes seeds that have left the box, whether emptied into the bag or planted.
     *
     * <p>Matched by name, only against what the box holds. The chat line ("Emptied 1 x
     * Asgarnian seed to your inventory") does not name its container, so a name the box does
     * not hold is ignored rather than reported: the line came from another container.
     *
     * @param itemName as the game said it, which for a seed planted straight from the box is
     *                 lower-case and plural — see {@link #namesTheSameItem}
     * @param quantity how many left, as a positive number
     */
    public static void applySeedBoxTaken(PlayerState state, String itemName, int quantity) {
        List<ItemStack> box = new ArrayList<>(state.getSeedBox());
        for (int i = 0; i < box.size(); i++) {
            ItemStack held = box.get(i);
            if (!namesTheSameItem(held.getName(), itemName)) {
                continue;
            }
            int left = held.getQuantity() - quantity;
            if (left > 0) {
                box.set(i, new ItemStack(held.getItemId(), left, held.getName()));
            } else {
                // Clamp at empty: seeds boxed before the plugin watched can make more leave
                // than the tally knew went in.
                box.remove(i);
            }
            commitSeedBox(state, box, held.getItemId(), -quantity);
            return;
        }
    }

    /**
     * Case-insensitive name match with an optional trailing "s": the box says "Potato cactus
     * seed" when filled and "3 potato cactus seeds were taken" when planted from.
     */
    private static boolean namesTheSameItem(String held, String spoken) {
        if (held == null || spoken == null) {
            return false;
        }
        if (held.equalsIgnoreCase(spoken)) {
            return true;
        }
        return spoken.length() > 1
                && (spoken.endsWith("s") || spoken.endsWith("S"))
                && held.equalsIgnoreCase(spoken.substring(0, spoken.length() - 1));
    }

    /**
     * @param movedId the seed the chat line moved
     * @param moved   how many went in, negative for out
     */
    private static void commitSeedBox(PlayerState state, List<ItemStack> box, int movedId,
            int moved) {
        state.setSeedBox(box);
        state.setSeedBoxLastSeen(Instant.now());
        // Any watched movement lets every kind in the tally take a place in the box; see
        // SeedBox#contentsCarried.
        state.setSeedBoxWatchedAt(Instant.now());
        // What the watched movements vouch for; see SeedBox#trustedOf.
        state.getSeedBoxWatchedNet().merge(movedId, moved,
                (was, change) -> Math.max(0, was + change));
        state.getSeedBoxWatchedNet().computeIfPresent(movedId,
                (id, net) -> net > 0 ? net : null);
    }

    /**
     * Merges deposit-box deposits into the existing bank state. A deposit box does not show
     * the bank, so the items that left the inventory are added to the last known bank. The
     * next real bank open corrects it.
     */
    public static void applyDepositInference(PlayerState state, List<ItemStack> depositedItems) {
        List<ItemStack> bank = new ArrayList<>(state.getBank());

        for (ItemStack deposited : depositedItems) {
            boolean merged = false;
            for (int i = 0; i < bank.size(); i++) {
                if (deposited.getItemId() == bank.get(i).getItemId()) {
                    ItemStack existing = bank.get(i);
                    bank.set(i, new ItemStack(
                            existing.getItemId(),
                            existing.getQuantity() + deposited.getQuantity(),
                            existing.getName()));
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                bank.add(deposited);
            }
        }

        state.setBank(bank);
    }

    public static void applyEquipmentChanged(PlayerState state,
            Map<EquipmentSlot, ItemStack> equipment) {
        state.getEquipment().clear();
        state.getEquipment().putAll(equipment);
    }

    /**
     * A quest moving on: started or finished by its state, and its stage counter when the
     * quest has one this plugin reads.
     */
    public static void applyQuestProgress(PlayerState state, String questName,
            QuestStatus status, Integer stage) {
        QuestStatus current = state.getQuests().get(questName);
        if (status == QuestStatus.IN_PROGRESS) {
            if (current == null || current == QuestStatus.NOT_STARTED) {
                state.getQuests().put(questName, QuestStatus.IN_PROGRESS);
            }
        } else if (status == QuestStatus.COMPLETED) {
            state.getQuests().put(questName, QuestStatus.COMPLETED);
        }

        if (stage != null) {
            state.getQuestProgress().put(questName, stage);
        }
    }
}
