package com.larryfarming.farming;

import com.larryfarming.engine.StateEngine;
import com.larryfarming.engine.EngineListener;
import com.larryfarming.engine.farming.*;
import com.larryfarming.farming.detection.*;
import com.larryfarming.farming.engine.*;
import com.larryfarming.farming.model.*;
import com.larryfarming.model.PlayerState;
import com.larryfarming.snapshot.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;

/**
 * Plans the farm runs for the account the {@link StateEngine} holds, and publishes the advice
 * the side panel and the advisor infobox draw.
 *
 * <p>Owns everything the plan needs beyond the engine's player state and patch tracker: the
 * crop settings, the day's spent teleports, the contract crop, what the house was seen to
 * hold and the previewed spellbook.
 *
 * <p>Listens to the engine, so the plan is rebuilt whenever the account's state changes, on
 * the same thread. Its own inputs only mark it dirty, and {@link #flushIfDirty()} rebuilds
 * once per tick. A rebuild that comes out equal to the last advice publishes nothing, so a
 * quiet tick leaves the panel alone.
 *
 * <p>Client thread only, apart from {@link #getAdvice()}, which reads a volatile field.
 */
@Slf4j
public class FarmingPlanner implements EngineListener {

    /** Builds the advice from the player state and planner inputs. */
    @FunctionalInterface
    interface Advisor {
        FarmingAdvice advise(PlayerState state, FarmingInputs inputs, long nowEpochSec);
    }

    /** The daily-teleport varbits the game reports, keyed for lookup on every varbit change. */
    private static final Map<Integer, Capability> DAILY_TELEPORT_VARBITS = CapabilitySources.dailyUsesCapabilitiesByVarbit();

    /**
     * A run this close is not worth a dedicated wake-up rebuild; the next event will
     * surface it anyway.
     */
    private static final long WAKE_FLOOR_SECONDS = 5L;

    private final StateEngine engine;
    private final FarmingPlannerPersistence persistence;
    private final Advisor advisor;

    /**
     * Which crops the player is willing to have suggested, as ticked in the panel's settings.
     *
     * <p>Starts at the defaults, so a planner built before the plugin reads its config plans
     * the default runs. The value is swapped whole.
     */
    private volatile FarmingPreferences preferences = FarmingPreferences.defaults();

    /**
     * How many of today's rationed teleports the player has spent (Explorer's ring cabbage
     * patch, Ardougne cloak farm patch), so a route used up today is not offered again. See
     * {@link DailyTeleportLedger} for the counting rules.
     */
    @Getter
    private final DailyTeleportLedger dailyTeleportLedger = new DailyTeleportLedger();

    /** The teleport spent on the way to the next stop, which that stop then reads as paid. */
    private final TravelWatch travelWatch = new TravelWatch();

    /** A seed going into the ground before the patch shows it, which holds the plan. */
    private final PlantingWatch plantingWatch = new PlantingWatch();

    /** A book the player asked to see the farm run planned for, or null for their own. */
    private Spellbook spellbookPreview;

    /** The panel's optional banks are switched off, so no withdrawal is listed at one. */
    private boolean offeredBanksHidden;

    /**
     * What Larry asked for that has been in this account's bag this trip, and the bag's low
     * marks: state no setting holds.
     */
    private DepositPlanner.TripRecord tripRecord = DepositPlanner.TripRecord.EMPTY;

    /** What the contract asks for, or null while the contract slot is empty. */
    @Getter
    private Crop contractCrop;

    /** What the player's own house was last seen to hold. Saved on every change. */
    @Getter
    private HouseSight house = HouseSight.unseen();

    /**
     * The next second at which a farming run comes ready, or zero when none is waiting.
     * Set from each rebuild and consumed by {@link #flushIfDirty()}.
     */
    private long wakeAtEpochSec;

    /** An input changed since the last rebuild. */
    private boolean dirty;

    /**
     * The farm run as last planned, or null when there is nothing to plan for. Volatile: the
     * infobox reads it while rendering, so it is safe from any thread.
     */
    @Getter
    private volatile FarmingAdvice advice;

    /** Moves forward by one each time {@link #advice} changes, and never goes back. */
    @Getter
    private long adviceVersion;


    private FarmingPlanner(StateEngine engine, FarmingPlannerPersistence persistence, Advisor advisor) {
        this.engine = engine;
        this.persistence = persistence;
        this.advisor = advisor;
    }

    /**
     * A planner listening to {@code engine} and hiding the patches its settings switch off.
     * Attach before the engine's first load, so that load's rebuild is planned.
     */
    public static FarmingPlanner attachTo(StateEngine engine, FarmingPlannerPersistence persistence) {
        return attachTo(engine, persistence, FarmingAdviceBuilder::forPlayer);
    }

    static FarmingPlanner attachTo(StateEngine engine, FarmingPlannerPersistence persistence, Advisor advisor) {
        FarmingPlanner planner = new FarmingPlanner(engine, persistence, advisor);
        engine.setListener(planner);
        return planner;
    }

    // ---------------------------------------------------------------
    // Engine events
    // ---------------------------------------------------------------

    /** The account is changing: forget the last one's state and load the next one's. */
    @Override
    public void accountLoading() {
        clearAccountState();
        loadPersistedState();
    }

    @Override
    public void varbitChanged(int varbitId, int value) {
        observeDailyTeleportVarbit(varbitId, value);
    }

    @Override
    public void stateChanged() {
        // A seed on its way into the ground: the plan on screen still counts it, and one
        // built now would send the player back to the bank for another.
        if (plantingWatch.holding()) {
            dirty = true;
            return;
        }
        rebuild(now());
    }

    /**
     * Forget everything held for the account that played last.
     *
     * <p>Kept across logins: the crop settings (client settings), the advice version (the
     * panel needs it to only move forward), and the wiring.
     */
    private void clearAccountState() {
        dailyTeleportLedger.clear();
        travelWatch.clear();
        plantingWatch.clear();
        contractCrop = null;
        house = HouseSight.unseen();
        // The book this account is not on; another account may be on it.
        spellbookPreview = null;
        tripRecord = DepositPlanner.TripRecord.EMPTY;
        wakeAtEpochSec = 0L;
        dirty = false;
        publish(null);
    }

    /** Read this account's saved planner state. Runs after the clear, never before it. */
    private void loadPersistedState() {
        // The last contract seen, so the panel can name the crop before its varbit is readable.
        contractCrop = persistence.loadFarmingContract();

        house = persistence.loadHouse();

        FarmingPlannerPersistence.DailyTeleportLedgerState ledgerState = persistence.loadDailyTeleportLedger();
        dailyTeleportLedger.loadPersisted(ledgerState.getDay(), ledgerState.getUsed());
    }

    // ---------------------------------------------------------------
    // Inputs
    // ---------------------------------------------------------------

    /**
     * Take the player's crop settings and plan against them.
     *
     * <p>A no-op when nothing changed, so unrelated settings in the config group do not cost
     * a re-plan.
     */
    public void setPreferences(FarmingPreferences preferences) {
        FarmingPreferences wanted = preferences != null ? preferences : FarmingPreferences.defaults();
        if (wanted.equals(this.preferences)) {
            return;
        }
        this.preferences = wanted;
        dirty = true;
    }

    /**
     * Plan the farm run's teleports for a book the player is not on, because they asked
     * to see it. Null goes back to following the open book.
     *
     * <p>Not persisted, and cleared when the player swaps books.
     */
    public void setSpellbookPreview(Spellbook spellbook) {
        if (spellbookPreview == spellbook) {
            return;
        }
        spellbookPreview = spellbook;
        dirty = true;
    }

    /**
     * Whether the panel draws the banks the route only passes. A bank the player never sees
     * cannot hold a withdrawal, so the switch changes the plan, not just the drawing.
     */
    public void setOfferedBanksShown(boolean shown) {
        if (offeredBanksHidden == !shown) {
            return;
        }
        offeredBanksHidden = !shown;
        dirty = true;
    }

    /**
     * The crop the current farming contract asks for, decoded by the plugin from varbit 7963.
     * Saved on change, so a logout doesn't lose the name.
     *
     * <p>The scheduler needs it to know which guild patch holds the contract. Without it,
     * the guild reports ready whenever anything in it is.
     */
    public void setContractCrop(Crop crop) {
        if (contractCrop == crop) {
            return;
        }
        contractCrop = crop;
        persistence.saveFarmingContract(crop);
        dirty = true;
    }

    /**
     * One complete scan of the player's own house: everything a house feature was found
     * for while it loaded. Replaces the last scan, so furniture taken down reads absent.
     */
    public void houseScanned(Set<HouseFeature> found, long nowEpochSec) {
        observeHouse(house.scanned(found, nowEpochSec));
    }

    /**
     * The Portal Nexus read in full on its teleport menu, in the player's own house.
     * Replaces the attunement, so a destination since removed reads absent.
     */
    public void nexusRead(Set<Integer> destinations, long nowEpochSec) {
        observeHouse(house.nexusRead(destinations, nowEpochSec));
    }

    /**
     * Nexus destinations proven attuned by a slot read anywhere else. Only adds, since a
     * slot reading zero there may not have been sent yet.
     */
    public void nexusAttuned(Collection<Integer> destinations) {
        HouseSight next = house.attuning(destinations);
        if (next != house) {
            observeHouse(next);
        }
    }


    /** Takes and saves a new sight of the house, and re-plans against it. */
    private void observeHouse(HouseSight next) {
        house = next;
        persistence.saveHouse(next);
        dirty = true;
    }

    /**
     * Feeds a live varbit change into {@link #dailyTeleportLedger} when the game uses that
     * varbit to count spent teleports. The game's count replaces the ledger's, because it
     * stays right across time the plugin was not watching.
     */
    private void observeDailyTeleportVarbit(int varbitId, int value) {
        Capability capability = DAILY_TELEPORT_VARBITS.get(varbitId);
        if (capability == null) {
            return;
        }
        dailyTeleportLedger.observeUsesSpent(capability, value, now());
        saveDailyTeleportLedger();
        dirty = true;
    }

    /**
     * Primes {@link #dailyTeleportLedger} from whatever the login scan already found in the
     * player state's varbits. The scan writes straight into the state, bypassing the engine's
     * varbit events, so this stands in for them. Safe to call every login:
     * {@link DailyTeleportLedger#observeUsesSpent} treats an unchanged value as a no-op.
     */
    public void primeDailyTeleportLedgerFromLogin() {
        PlayerState state = engine.getPlayerState();
        if (state == null || DAILY_TELEPORT_VARBITS.isEmpty()) {
            return;
        }
        long nowSec = now();
        for (Map.Entry<Integer, Capability> entry : DAILY_TELEPORT_VARBITS.entrySet()) {
            Integer value = state.getVarbits().get(entry.getKey());
            if (value != null) {
                dailyTeleportLedger.observeUsesSpent(entry.getValue(), value, nowSec);
            }
        }
        saveDailyTeleportLedger();
        dirty = true;
    }

    /**
     * Charges one Ardougne farm teleport against the cloak the player actually holds.
     *
     * <p>The counterpart to {@link #observeDailyTeleportVarbit} for the rationed route with no
     * readable game counter. Called when {@link ArdougneFarmTeleportWatch} has seen a click
     * land, not when it was made.
     *
     * <p>The tier comes from the diary, because the allowance belongs to the cloak the player
     * owns. A cloak 4 has no daily limit; {@link DailyTeleportLedger#recordUse} treats it as
     * a no-op.
     */
    public void recordArdougneFarmTeleport() {
        PlayerState state = engine.getPlayerState();
        if (state == null) {
            return;
        }
        Capability held = DiaryRewards.bestEarnedTier(Capability.TierFamily.ARDOUGNE_CLOAK, state.getVarbits());
        if (held == null) {
            return;
        }
        dailyTeleportLedger.recordUse(held, now());
        log.debug("Ardougne cloak farm teleport charged against {}", held);
        saveDailyTeleportLedger();
        dirty = true;
    }

    private void saveDailyTeleportLedger() {
        persistence.saveDailyTeleportLedger(dailyTeleportLedger.exportDay(), dailyTeleportLedger.exportForPersist());
    }

    // ---------------------------------------------------------------
    // Planning
    // ---------------------------------------------------------------

    /**
     * Re-plan if an input changed since the last rebuild, or a run came due. Call once per
     * game tick, after the engine's own flush.
     */
    public void flushIfDirty() {
        flushIfDirty(now());
    }

    /** The epoch second now, from the wall clock. */
    private static long now() {
        return Instant.now().getEpochSecond();
    }

    /**
     * One tick of the teleport watch. Call once per game tick, before the engine's flush, so
     * a plan built this tick already knows the teleport is paid.
     */
    public void watchTravel() {
        PlayerState state = engine.getPlayerState();
        FarmingStopAdvice heading = headingStop();
        if (state == null || !travelWatch.tick(state, heading == null ? null : heading.getPatchName(),
                heading == null || heading.getLanding() == null ? null : Destination.valueOf(heading.getLanding()))) {
            return;
        }
        dirty = true;
        TravelTaken taken = travelWatch.taken();
        if (taken.isNone()) {
            log.debug("Teleport no longer paid for: the stop it was spent for is done");
        } else {
            log.debug("Teleport spent on the way to {}: {}", travelWatch.takenFor(), taken);
        }
    }

    /**
     * One tick of the planting watch. Call once per game tick, before the engine's flush, so
     * the rebuild that flush asks for waits while a seed is going in.
     */
    public void watchPlanting() {
        PlayerState state = engine.getPlayerState();
        if (state == null) {
            return;
        }
        boolean was = plantingWatch.holding();
        if (plantingWatch.tick(state, engine.getFarmingTracker().getChanges())) {
            // The flush after this rebuilds; dirty already says so.
            log.debug("A planting showed or its hold ran out; planning again");
        } else if (!was && plantingWatch.holding()) {
            log.debug("A seed left the bag; holding the plan until the patch shows it");
        }
    }

    /** The stop the advice on screen sends the player to next, or null for none. */
    private FarmingStopAdvice headingStop() {
        if (advice == null || advice.getStops() == null) {
            return null;
        }
        for (FarmingStopAdvice stop : advice.getStops()) {
            if (stop.getKind() == FarmingStopAdvice.Kind.PATCH && stop.getOutcome() == FarmingStopAdvice.Outcome.VISIT) {
                return stop;
            }
        }
        return null;
    }

    /** {@link #flushIfDirty()} against a stated second rather than the clock. */
    void flushIfDirty(long nowEpochSec) {
        if (dueForWake(nowEpochSec)) {
            dirty = true;
        }
        if (dirty && !plantingWatch.holding()) {
            rebuild(nowEpochSec);
        }
    }

    /**
     * Whether a farming run has come due purely with the passage of time.
     *
     * <p>Nothing in the game announces a crop finishing, so without this the advisor would
     * learn a run is ready only when some unrelated event next fires.
     *
     * <p>Looks ahead rather than polling: the soonest ready time the scheduler computed is
     * the only future second at which the answer can change. This costs one comparison per
     * tick.
     */
    boolean dueForWake(long nowEpochSec) {
        if (wakeAtEpochSec <= 0 || nowEpochSec < wakeAtEpochSec) {
            return false;
        }
        // Cleared as it fires; the rebuild it triggers arms the next one.
        wakeAtEpochSec = 0L;
        return true;
    }

    /**
     * Plan against the engine's player state as it stands, and publish the result if it
     * differs from the last. With no player state there is nothing to plan, and the last
     * advice stands, as the engine keeps its last snapshot.
     */
    private void rebuild(long nowEpochSec) {
        dirty = false;
        PlayerState state = engine.getPlayerState();
        if (state == null) {
            return;
        }
        // The clock enters the farming layer here and nowhere below it. Everything under
        // the scheduler takes the epoch second as an argument, so the time can be fixed.
        FarmingInputs inputs = inputs(nowEpochSec);
        FarmingAdvice next = advisor.advise(state, inputs, nowEpochSec);
        // The trip record reads the advice it feeds. A changed packed set plans once more
        // before publishing, so the next bank's deposits see it at once. Twice is enough:
        // the deposits feed no list the record reads.
        DepositPlanner.TripRecord record = DepositPlanner.record(state, next, tripRecord);
        boolean replan = !record.getPacked().equals(tripRecord.getPacked());
        tripRecord = record;
        if (replan) {
            next = advisor.advise(state, inputs.withPackedThisTrip(record.getPacked()), nowEpochSec);
        }
        armWake(next, nowEpochSec);
        publish(next);
    }

    /** Every field of the advice is drawn, so it compares whole. */
    private void publish(FarmingAdvice next) {
        if (Objects.equals(advice, next)) {
            return;
        }
        advice = next;
        adviceVersion++;
    }

    /**
     * What the farm run is planned against beyond the player state: the previewed book, the
     * contract, preferences, and the patches' and the house's last-seen contents.
     *
     * <p>The patch lookup reads through to the tracker, so a run planned mid-tick sees the
     * same patches the timers do.
     */
    private FarmingInputs inputs(long nowSec) {
        FarmingTracker tracker = engine.getFarmingTracker();
        return FarmingInputs.of(spellbookPreview, patch -> {
                    FarmingWorldMap.PatchDef def = patch.resolve();
                    return def != null ? tracker.predict(def, nowSec) : null;
                },
                dailyTeleportLedger,
                nowSec)
                .withContractCrop(contractCrop)
                .withPreferences(preferences)
                .withHouse(house)
                .withOfferedBanksHidden(offeredBanksHidden)
                .withPackedThisTrip(tripRecord.getPacked())
                .withTravelTaken(travelWatch.taken());
    }

    /**
     * When to look at the farming runs again, from the advice just built: the soonest
     * moment any waiting run comes ready, or zero when nothing is waiting on a clock.
     */
    void armWake(FarmingAdvice built, long nowEpochSec) {
        wakeAtEpochSec = 0L;
        if (built == null || built.getRuns() == null) {
            return;
        }
        for (FarmingRunStatus run : built.getRuns()) {
            long readyAt = run.getReadyAtEpochSec();
            // Skip anything all but arrived. A patch whose estimate has been overtaken keeps
            // reporting itself due imminently, and waking on it would rebuild every second.
            if (readyAt <= nowEpochSec + WAKE_FLOOR_SECONDS) {
                continue;
            }
            if (wakeAtEpochSec == 0L || readyAt < wakeAtEpochSec) {
                wakeAtEpochSec = readyAt;
            }
        }
    }
}
