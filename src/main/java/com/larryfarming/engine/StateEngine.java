package com.larryfarming.engine;

import com.larryfarming.engine.farming.*;
import com.larryfarming.model.*;
import com.larryfarming.persistence.StatePersistence;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

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

    public StateEngine(StatePersistence persistence) {
        this.persistence = persistence;
    }

    /** Install the one listener, or null to remove it. Set before the first load. */
    public void setListener(EngineListener listener) {
        this.listener = listener;
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
        farmingTracker.loadPersisted(persistence.loadFarmingPatches(), farmTickOffset != null ? farmTickOffset[0] : -1,
                farmTickOffset != null ? farmTickOffset[1] : -1, farmTickOffset != null ? farmTickOffset[2] : 1);

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
    public void onLogin(Map<String, EventApplier.SkillSnapshot> skills, List<String> completedQuests, List<String> startedQuests,
                        List<ItemStack> inventory, Map<EquipmentSlot, ItemStack> equipment) {
        if (playerState == null) {
            playerState = new PlayerState();
        }
        EventApplier.applyLoginState(playerState, skills, completedQuests, startedQuests, inventory, equipment);
        afterEvent();
    }

    public void onStatChanged(String skillName, long xp, int level) {
        if (playerState == null) return;
        EventApplier.applyStatChanged(playerState, skillName, xp, level);
        afterEvent();
    }

    /** Applies a change to the player state, if an account is loaded. */
    private void change(Runnable apply) {
        if (playerState == null) return;
        apply.run();
        afterEvent();
    }

    public void onInventoryChanged(List<ItemStack> items) {
        change(() -> EventApplier.applyInventoryChanged(playerState, items));
    }

    public void onBankChanged(List<ItemStack> items) {
        change(() -> EventApplier.applyBankChanged(playerState, items));
    }

    public void onSeedVaultChanged(List<ItemStack> items) {
        change(() -> EventApplier.applySeedVaultChanged(playerState, items));
    }

    public void onSeedBoxChanged(List<ItemStack> items) {
        change(() -> {
            // The tally before a listing replaces it shows what drifted, and so which
            // movement went unread.
            log.debug("Seed box listed {}; the tally said {}", tally(items), tally(playerState.getSeedBox()));
            EventApplier.applySeedBoxChanged(playerState, items);
        });
    }

    /** Records seeds the player has put in their seed box. */
    public void onSeedBoxStored(ItemStack stored) {
        change(() -> {
            EventApplier.applySeedBoxStored(playerState, stored);
            log.debug("Seed box stored {} x {}; tally now {}", stored.getQuantity(), stored.getName(), tally(playerState.getSeedBox()));
        });
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
        change(() -> {
            EventApplier.applySeedBoxTaken(playerState, itemName, quantity);
            log.debug("Seed box gave up {} x {}; tally now {}", quantity, itemName, tally(playerState.getSeedBox()));
        });
    }

    /** The rune pouch contents (rune item ID → quantity), read from the pouch varbits. */
    public void onRunePouchChanged(Map<Integer, Integer> contents) {
        change(() -> playerState.setRunePouch(contents));
    }

    /** Called when items leave the inventory while a deposit box is open. */
    public void onDepositInferred(List<ItemStack> depositedItems) {
        change(() -> EventApplier.applyDepositInference(playerState, depositedItems));
    }

    public void onEquipmentChanged(Map<EquipmentSlot, ItemStack> equipment) {
        change(() -> EventApplier.applyEquipmentChanged(playerState, equipment));
    }

    /**
     * A quest started or finished, or a tracked quest's stage counter moved.
     *
     * @param status the quest's state now, or null when only the stage is reported
     * @param stage  the stage counter, or null for a quest whose stage is not read
     */
    public void onQuestProgress(String questName, QuestStatus status, Integer stage) {
        change(() -> EventApplier.applyQuestProgress(playerState, questName, status, stage));
    }

    public void onRegionChanged(int newRegionId) {
        change(() -> playerState.setRegionId(newRegionId));
    }

    /** A varbit took a new value. An unchanged value is a no-op. */
    public void onVarbitValueChanged(int varbitId, int value) {
        if (playerState == null) return;
        Integer prev = playerState.getVarbits().get(varbitId);
        if (prev != null && prev == value) return;
        playerState.getVarbits().put(varbitId, value);
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
        if (farmingTracker.observeRegion(region, varbitValues, allowOffsetLearning, nowEpochSec)) {
            persistence.saveFarmingPatches(farmingTracker.exportForPersist());
        }
        if (farmingTracker.consumeOffsetDirty()) {
            persistence.saveFarmingTickOffset(farmingTracker.getTickOffsetSeconds(), farmingTracker.getTickOffsetPrecisionMins(),
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

    private void announce() {
        dirty = false;
        if (listener != null) {
            listener.stateChanged();
        }
    }
}
