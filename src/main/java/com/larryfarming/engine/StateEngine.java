package com.larryfarming.engine;

import com.larryfarming.engine.farming.DecodedPatch;
import com.larryfarming.engine.farming.FarmingTracker;
import com.larryfarming.engine.farming.FarmingWorldMap;
import com.larryfarming.model.ContractTally;
import com.larryfarming.model.EquipmentSlot;
import com.larryfarming.model.ItemStack;
import com.larryfarming.model.PlayerState;
import com.larryfarming.model.QuestStatus;
import com.larryfarming.model.Skill;
import com.larryfarming.persistence.StatePersistence;
import com.larryfarming.report.ItemDelta;
import com.larryfarming.report.Journal;
import com.larryfarming.report.Trace;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Holds the logged-in account's state: the {@link PlayerState} read from game events, and
 * the {@link FarmingTracker} fed by patch scans.
 *
 * <p>Events change the state and mark it dirty. {@link #flushIfDirty()} runs once per game
 * tick and tells the {@link EngineListener} once, so a tick's events share one re-plan.
 *
 * <p>Client thread only.
 */
@Slf4j
public class StateEngine {

    private final StatePersistence persistence;

    @Getter
    private final FarmingTracker farmingTracker = new FarmingTracker();

    /** The logged-in account's state, or null before the first login of the session. */
    @Getter
    private PlayerState playerState;

    /** Hears account loads, varbit changes and flushes; null when nothing listens. */
    private EngineListener listener;

    /** An event changed the state since the last flush. */
    private boolean dirty;

    /** Where each event is recorded for a bug report, or null to record nothing. */
    private Journal journal;

    public StateEngine(StatePersistence persistence) {
        this.persistence = persistence;
    }

    /** Install the one listener, or null to remove it. Set before the first load. */
    public void setListener(EngineListener listener) {
        this.listener = listener;
    }

    /** Record every event in {@code journal} from now on, or nowhere when null. */
    public void setJournal(Journal journal) {
        this.journal = journal;
    }

    /**
     * Load the current RS profile's saved state, replacing whatever the engine held.
     *
     * <p>Runs at every login, not once per client run. The engine outlives a logout, so the
     * account logging in may not be the one that left.
     */
    public void loadPersistedState() {
        clearAccountState();
        // Straight after the clear, so a listener's own per-account state is swapped at the
        // same moment as the engine's and is loaded before the announcement at the end.
        if (listener != null) {
            listener.accountLoading();
        }

        playerState = persistence.loadPlayerState();

        int[] farmTickOffset = persistence.loadFarmingTickOffset();
        farmingTracker.loadPersisted(persistence.loadFarmingPatches(),
                farmTickOffset != null ? farmTickOffset[0] : -1,
                farmTickOffset != null ? farmTickOffset[1] : -1,
                farmTickOffset != null ? farmTickOffset[2] : 1);

        announce();
    }

    /**
     * Forget everything held for the account that played last. Kept: the listener, which
     * clears its own per-account state in {@link EngineListener#accountLoading}.
     */
    private void clearAccountState() {
        playerState = null;
        dirty = false;
        farmingTracker.clear();
    }

    // ---------------------------------------------------------------
    // Events, from the plugin's subscribers
    // ---------------------------------------------------------------

    /** The account as the client reports it at login. Creates the state on a first login. */
    public void onLogin(Map<String, EventApplier.SkillSnapshot> skills,
                        List<String> completedQuests,
                        List<String> startedQuests,
                        List<ItemStack> inventory,
                        Map<EquipmentSlot, ItemStack> equipment) {
        if (playerState == null) {
            playerState = new PlayerState();
        }
        EventApplier.applyLoginState(playerState, skills, completedQuests, startedQuests,
                inventory, equipment);
        if (journal != null) {
            JsonObject data = new JsonObject();
            data.addProperty("skills", skills.size());
            data.addProperty("questsDone", completedQuests.size());
            data.addProperty("questsStarted", startedQuests.size());
            data.add("inventory", ItemDelta.between(List.of(), inventory));
            data.add("equipment", ItemDelta.between(List.of(), equipment.values()));
            journal.event("login", data);
        }
        afterEvent();
    }

    public void onStatChanged(String skillName, long xp, int level) {
        if (playerState == null) return;
        Map<Skill, Integer> before = journal != null ? levels() : null;
        EventApplier.applyStatChanged(playerState, skillName, xp, level);
        // Only a level is worth a line: experience moves with every action.
        if (before != null && !before.equals(levels())) {
            JsonObject data = new JsonObject();
            data.addProperty("skill", skillName);
            data.addProperty("level", level);
            journal.event("level", data);
        }
        afterEvent();
    }

    public void onInventoryChanged(List<ItemStack> items) {
        if (playerState == null) return;
        List<ItemStack> before = copy(playerState.getInventory());
        EventApplier.applyInventoryChanged(playerState, items);
        recordItems("inventory", before, playerState.getInventory());
        afterEvent();
    }

    public void onBankChanged(List<ItemStack> items) {
        if (playerState == null) return;
        List<ItemStack> before = copy(playerState.getBank());
        EventApplier.applyBankChanged(playerState, items);
        recordItems("bank", before, playerState.getBank());
        afterEvent();
    }

    public void onSeedVaultChanged(List<ItemStack> items) {
        if (playerState == null) return;
        List<ItemStack> before = copy(playerState.getSeedVault());
        EventApplier.applySeedVaultChanged(playerState, items);
        recordItems("seedVault", before, playerState.getSeedVault());
        afterEvent();
    }

    public void onSeedBoxChanged(List<ItemStack> items) {
        if (playerState == null) return;
        // The tally before a listing replaces it shows what drifted, and so which movement
        // went unread.
        Trace.debug(log, "Seed box listed {}; the tally said {}", tally(items),
                tally(playerState.getSeedBox()));
        List<ItemStack> before = copy(playerState.getSeedBox());
        EventApplier.applySeedBoxChanged(playerState, items);
        recordItems("seedBoxListed", before, playerState.getSeedBox());
        afterEvent();
    }

    /** Records seeds the player has put in their seed box. */
    public void onSeedBoxStored(ItemStack stored) {
        if (playerState == null) return;
        List<ItemStack> before = copy(playerState.getSeedBox());
        EventApplier.applySeedBoxStored(playerState, stored);
        Trace.debug(log, "Seed box stored {} x {}; tally now {}", stored.getQuantity(),
                stored.getName(), tally(playerState.getSeedBox()));
        recordItems("seedBoxStored", before, playerState.getSeedBox());
        afterEvent();
    }

    /** "3 x Guam seed, 1 x Whiteberry seed", for the seed box debug lines. */
    private static String tally(List<ItemStack> box) {
        StringBuilder out = new StringBuilder();
        for (ItemStack stack : box) {
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(stack.getQuantity()).append(" x ").append(stack.getName());
        }
        return out.length() == 0 ? "nothing" : out.toString();
    }

    /**
     * Records seeds leaving the seed box, emptied into the bag or planted out of it.
     *
     * @param itemName as the game named it; a name the box is not holding is ignored
     * @param quantity how many left, as a positive number
     */
    public void onSeedBoxTaken(String itemName, int quantity) {
        if (playerState == null) return;
        List<ItemStack> before = copy(playerState.getSeedBox());
        EventApplier.applySeedBoxTaken(playerState, itemName, quantity);
        Trace.debug(log, "Seed box gave up {} x {}; tally now {}", quantity, itemName,
                tally(playerState.getSeedBox()));
        recordItems("seedBoxTaken", before, playerState.getSeedBox());
        afterEvent();
    }

    /** The rune pouch contents (rune item ID → quantity), read from the pouch varbits. */
    public void onRunePouchChanged(Map<Integer, Integer> contents) {
        if (playerState == null) return;
        if (journal != null && !contents.equals(playerState.getRunePouch())) {
            JsonObject data = new JsonObject();
            for (Map.Entry<Integer, Integer> rune : new TreeMap<>(contents).entrySet()) {
                data.addProperty(String.valueOf(rune.getKey()), rune.getValue());
            }
            journal.event("runePouch", data);
        }
        playerState.setRunePouch(contents);
        afterEvent();
    }

    /** Called when items leave the inventory while a deposit box is open. */
    public void onDepositInferred(List<ItemStack> depositedItems) {
        if (playerState == null) return;
        List<ItemStack> before = copy(playerState.getBank());
        EventApplier.applyDepositInference(playerState, depositedItems);
        recordItems("bankDepositInferred", before, playerState.getBank());
        afterEvent();
    }

    public void onEquipmentChanged(Map<EquipmentSlot, ItemStack> equipment) {
        if (playerState == null) return;
        List<ItemStack> before = journal != null
                ? new ArrayList<>(playerState.getEquipment().values()) : null;
        EventApplier.applyEquipmentChanged(playerState, equipment);
        if (before != null) {
            recordItems("equipment", before, playerState.getEquipment().values());
        }
        afterEvent();
    }

    /**
     * A quest started or finished, or a tracked quest's stage counter moved.
     *
     * @param status the quest's state now, or null when only the stage is reported
     * @param stage  the stage counter, or null for a quest whose stage is not read
     */
    public void onQuestProgress(String questName, QuestStatus status, Integer stage) {
        if (playerState == null) return;
        QuestStatus statusBefore = playerState.getQuests().get(questName);
        Integer stageBefore = playerState.getQuestProgress().get(questName);
        EventApplier.applyQuestProgress(playerState, questName, status, stage);
        // Only a quest that moved: the login scan reports every quest the account has, and
        // all but a few read as they were saved.
        boolean moved = !Objects.equals(statusBefore, playerState.getQuests().get(questName))
                || !Objects.equals(stageBefore, playerState.getQuestProgress().get(questName));
        if (journal != null && moved) {
            JsonObject data = new JsonObject();
            // As the state holds them after the change, which need not be as reported.
            QuestStatus statusNow = playerState.getQuests().get(questName);
            data.addProperty("quest", questName);
            data.addProperty("status", statusNow != null ? statusNow.name() : null);
            data.addProperty("stage", playerState.getQuestProgress().get(questName));
            journal.event("quest", data);
        }
        afterEvent();
    }

    /**
     * Run energy, in the client's own hundredths of a percent.
     *
     * <p>The caller calls this only when the energy band changes. Energy moves almost every
     * tick while running, and a re-plan per tick would churn the panel.
     *
     * @param low whether the bar is low enough to pack a top-up, as the caller latches it
     */
    public void onRunEnergyChanged(int energy, boolean low) {
        if (playerState == null) return;
        playerState.setRunEnergy(energy);
        playerState.setRunEnergyLow(low);
        if (journal != null) {
            JsonObject data = new JsonObject();
            data.addProperty("energy", energy);
            data.addProperty("low", low);
            journal.event("runEnergy", data);
        }
        afterEvent();
    }

    public void onRegionChanged(int newRegionId) {
        if (playerState == null) return;
        playerState.setRegionId(newRegionId);
        record("region", "region", newRegionId);
        afterEvent();
    }

    /** The player handed a farming contract in to Guildmaster Jane. */
    public void onContractHandedIn(Instant now) {
        if (playerState == null) return;
        ContractTally.handIn(playerState, now);
        refreshContractRate(now);
        record("contractHandedIn", "handedIn", playerState.getContractsHandedIn().size());
        afterEvent();
    }

    /**
     * Brings the contracts-a-day figure up to the clock, starting the count on its first
     * call. Re-plans only when the figure moves by a tenth, since it drifts as the window
     * fills.
     */
    public void refreshContractRate(Instant now) {
        if (playerState == null) return;
        if (playerState.getContractsWatchedSince() == null) {
            ContractTally.startWatching(playerState, now);
            persistence.markDirty();
        }
        Double rate = ContractTally.perDay(playerState, now);
        Double rounded = rate == null ? null : Math.round(rate * 10) / 10.0;
        if (!Objects.equals(rounded, playerState.getContractsPerDay())) {
            playerState.setContractsPerDay(rounded);
            dirty = true;
        }
    }

    /** A varbit took a new value. An unchanged value is a no-op. */
    public void onVarbitValueChanged(int varbitId, int value) {
        if (playerState == null) return;
        Integer prev = playerState.getVarbits().get(varbitId);
        if (prev != null && prev == value) return;
        playerState.getVarbits().put(varbitId, value);
        if (journal != null) {
            JsonObject data = new JsonObject();
            data.addProperty("id", varbitId);
            data.addProperty("from", prev);
            data.addProperty("to", value);
            journal.event("varbit", data);
        }
        if (listener != null) {
            listener.varbitChanged(varbitId, value);
        }
        afterEvent();
    }

    /**
     * Fold live patch varbit values for one farming region into the tracker. Called per
     * game tick while the player stands in a tracked region, because the transmit varbits
     * are valid only near the patches. Persists immediately on change so offline growth
     * survives a crash.
     *
     * <p>Marks nothing dirty: the plan reads the tracker through at its next rebuild.
     */
    public void updateFarming(FarmingWorldMap.RegionDef region, Map<Integer, Integer> varbitValues,
                              boolean allowOffsetLearning, long nowEpochSec) {
        Map<String, Integer> before = journal != null ? patchValues(region) : null;
        boolean changed = farmingTracker.observeRegion(region, varbitValues,
                allowOffsetLearning, nowEpochSec);
        if (changed) {
            persistence.saveFarmingPatches(farmingTracker.exportForPersist());
            if (before != null) {
                recordPatches(before, patchValues(region));
            }
        }
        if (farmingTracker.consumeOffsetDirty()) {
            persistence.saveFarmingTickOffset(farmingTracker.getTickOffsetSeconds(),
                    farmingTracker.getTickOffsetPrecisionMins(),
                    farmingTracker.getTickOffsetResolutionSeconds());
        }
    }

    // ---------------------------------------------------------------
    // Flushing and saving
    // ---------------------------------------------------------------

    /**
     * Mark the state changed after a bulk write straight into the player state, such as the
     * login varbit scan.
     */
    public void triggerResolve() {
        afterEvent();
    }

    /**
     * Ask for a re-plan at the next flush without changing the state: a game var moved that
     * the plan does not read. The plan's "ready in" lines are worked out when it is built, so
     * re-planning as the game moves keeps them current.
     */
    public void markStale() {
        dirty = true;
    }

    /** Tell the listener once if any event changed the state since the last flush. */
    public void flushIfDirty() {
        if (dirty) {
            announce();
        }
    }

    /** Save the player state if it changed and the debounce allows. Call once per tick. */
    public void tickSave() {
        persistence.saveIfNeeded(playerState);
    }

    /** Save the player state now. Call at logout and shutdown. */
    public void forceSave() {
        persistence.forceSave(playerState);
    }

    private void afterEvent() {
        persistence.markDirty();
        dirty = true;
    }

    // ---------------------------------------------------------------
    // Journal
    // ---------------------------------------------------------------

    /** A copy to compare against after a change, or null while nothing is recorded. */
    private List<ItemStack> copy(List<ItemStack> items) {
        if (journal == null) {
            return null;
        }
        return items != null ? new ArrayList<>(items) : new ArrayList<>();
    }

    /** One event naming what a container gained and lost, if anything moved. */
    private void recordItems(String type, List<ItemStack> before, Collection<ItemStack> after) {
        if (journal == null || before == null) {
            return;
        }
        JsonArray moved = ItemDelta.between(before, after);
        if (moved.size() == 0) {
            return;
        }
        JsonObject data = new JsonObject();
        data.add("items", moved);
        journal.event(type, data);
    }

    private void record(String type, String field, int value) {
        if (journal == null) {
            return;
        }
        JsonObject data = new JsonObject();
        data.addProperty(field, value);
        journal.event(type, data);
    }

    private Map<Skill, Integer> levels() {
        Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
        playerState.getSkills().forEach((skill, state) -> levels.put(skill, state.getLevel()));
        return levels;
    }

    /** Each tracked patch in the region and the raw value it holds, by config key. */
    private Map<String, Integer> patchValues(FarmingWorldMap.RegionDef region) {
        Map<String, Integer> values = new HashMap<>();
        for (FarmingWorldMap.PatchDef patch : region.getPatches()) {
            Integer value = farmingTracker.rawValue(patch);
            if (value != null) {
                values.put(patch.configKey(), value);
            }
        }
        return values;
    }

    /** One event per patch whose value changed, named and decoded as the tracker sees it. */
    private void recordPatches(Map<String, Integer> before, Map<String, Integer> after) {
        for (Map.Entry<String, Integer> now : after.entrySet()) {
            Integer was = before.get(now.getKey());
            if (now.getValue().equals(was)) {
                continue;
            }
            FarmingWorldMap.PatchDef patch = FarmingWorldMap.byConfigKey(now.getKey());
            JsonObject data = new JsonObject();
            data.addProperty("patch", now.getKey());
            data.addProperty("name", patch != null ? patch.displayName() : null);
            data.addProperty("from", was);
            data.addProperty("to", now.getValue());
            DecodedPatch reads = patch != null ? patch.getType().decode(now.getValue()) : null;
            if (reads != null) {
                data.addProperty("crop", reads.getCrop() != null ? reads.getCrop().name() : null);
                data.addProperty("state", reads.getState() != null ? reads.getState().name() : null);
                data.addProperty("stage", reads.getStage());
                if (reads.isFellable()) {
                    data.addProperty("treeStanding", true);
                }
            }
            journal.event("patch", data);
        }
    }

    private void announce() {
        dirty = false;
        if (listener != null) {
            listener.stateChanged();
        }
    }
}
