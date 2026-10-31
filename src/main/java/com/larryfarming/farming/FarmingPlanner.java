package com.larryfarming.farming;

import com.larryfarming.engine.StateEngine;
import com.larryfarming.engine.EngineListener;
import com.larryfarming.engine.farming.Crop;
import com.larryfarming.engine.farming.FarmingTracker;
import com.larryfarming.engine.farming.FarmingWorldMap;
import com.larryfarming.farming.detection.ArdougneFarmTeleportWatch;
import com.larryfarming.farming.detection.BottomlessBucket;
import com.larryfarming.farming.detection.CapabilitySources;
import com.larryfarming.farming.detection.DiaryRewards;
import com.larryfarming.farming.detection.HouseFeature;
import com.larryfarming.farming.detection.HouseSight;
import com.larryfarming.farming.detection.PlantingWatch;
import com.larryfarming.farming.detection.TravelTaken;
import com.larryfarming.farming.detection.TravelWatch;
import com.larryfarming.farming.engine.CompostStock;
import com.larryfarming.farming.engine.DailyTeleportLedger;
import com.larryfarming.farming.engine.DepositPlanner;
import com.larryfarming.farming.engine.FarmingAdviceBuilder;
import com.larryfarming.farming.engine.FarmingInputs;
import com.larryfarming.farming.engine.PlantingGate;
import com.larryfarming.farming.model.Capability;
import com.larryfarming.farming.model.Destination;
import com.larryfarming.farming.model.FarmingPreferences;
import com.larryfarming.farming.model.RunId;
import com.larryfarming.farming.model.Spellbook;
import com.larryfarming.model.PlayerState;
import com.larryfarming.report.Journal;
import com.larryfarming.report.Trace;
import com.larryfarming.snapshot.FarmingAdvice;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.larryfarming.snapshot.FarmingRunStatus;
import com.larryfarming.snapshot.FarmingStopAdvice;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * Plans the farm runs for the account the {@link StateEngine} holds, and publishes the advice
 * the side panel and the advisor infobox draw.
 *
 * <p>Owns everything the plan needs beyond the engine's player state and patch tracker: the
 * crop settings, the day's spent teleports, the contract crop, what the house was seen to
 * hold, the previewed spellbook, the bottomless bucket's last reading and whether compost
 * upkeep is on.
 *
 * <p>Listens to the engine, so the plan is rebuilt whenever the account's state changes, on
 * the same thread. Its own inputs only mark it dirty, and {@link #flushIfDirty()} rebuilds
 * once per tick. A rebuild that comes out equal to the last advice publishes nothing, so a
 * quiet tick leaves the panel alone.
 *
 * <p>Client thread only, apart from {@link #getAdvice()} and the patch-hiding predicate,
 * which read volatile fields.
 */
@Slf4j
public class FarmingPlanner implements EngineListener {

    /** Builds the advice from the player state and planner inputs. */
    @FunctionalInterface
    interface Advisor {
        FarmingAdvice advise(PlayerState state, FarmingInputs inputs, long nowEpochSec);
    }

    /** The daily-teleport varbits the game reports, keyed for lookup on every varbit change. */
    private static final Map<Integer, Capability> DAILY_TELEPORT_VARBITS =
            CapabilitySources.dailyUsesCapabilitiesByVarbit();

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
     * the default runs.
     *
     * <p>Volatile: the config listener writes it on the client thread, and
     * {@link #hiddenByPreferences} reads it from any thread, the EDT included. The value is
     * swapped whole.
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
    @Getter
    private Spellbook spellbookPreview;

    /** The panel's optional banks are switched off, so no withdrawal is listed at one. */
    private boolean offeredBanksHidden;

    /** What Larry asked for that has been in this account's bag this trip. */
    private DepositPlanner.TripRecord tripRecord = DepositPlanner.TripRecord.EMPTY;

    /** What the contract asks for, or null while the contract slot is empty. */
    @Getter
    private Crop contractCrop;

    /** What the player's own house was last seen to hold. Saved on every change. */
    @Getter
    private HouseSight house = HouseSight.unseen();

    /**
     * The uses of ultracompost the bottomless bucket was last read to hold, from its dialogue
     * or from the leprechaun's varbits, less a use for each patch treated from it since.
     * Null while it has never been read. Saved on every change.
     */
    @Getter
    private Integer bottomlessBucketUses;

    /**
     * Whether compost upkeep was last on, or null before it has been decided. Saved on every
     * change, so the gap between the two thresholds keeps its state across a logout.
     */
    @Getter
    private Boolean compostUpkeep;

    /**
     * The next second at which a farming run comes ready, or zero when none is waiting.
     * Set from each rebuild and consumed by {@link #flushIfDirty()}.
     */
    private long wakeAtEpochSec;

    /** An input changed since the last rebuild. */
    private boolean dirty;

    /**
     * The farm run as last planned, or null when there is nothing to plan for. Volatile: the
     * infobox reads it while rendering.
     */
    private volatile FarmingAdvice advice;

    /** Moves forward by one each time {@link #advice} changes, and never goes back. */
    @Getter
    private long adviceVersion;

    /** The epoch second now: the wall clock, unless {@link #setClock} fixed it. */
    private LongSupplier clock = () -> Instant.now().getEpochSecond();

    /** The second the last plan was built at, or zero before the first. */
    private long plannedAtEpochSec;

    /** Where each published plan and planner input is recorded, or null for nowhere. */
    private Journal journal;

    private FarmingPlanner(StateEngine engine, FarmingPlannerPersistence persistence,
            Advisor advisor) {
        this.engine = engine;
        this.persistence = persistence;
        this.advisor = advisor;
    }

    /**
     * A planner listening to {@code engine} and hiding the patches its settings switch off.
     * Attach before the engine's first load, so that load's rebuild is planned.
     */
    public static FarmingPlanner attachTo(StateEngine engine,
            FarmingPlannerPersistence persistence) {
        return attachTo(engine, persistence, FarmingAdviceBuilder::forPlayer);
    }

    static FarmingPlanner attachTo(StateEngine engine, FarmingPlannerPersistence persistence,
            Advisor advisor) {
        FarmingPlanner planner = new FarmingPlanner(engine, persistence, advisor);
        engine.setListener(planner);
        // Installed once: the predicate reads the preferences field on every call.
        engine.getFarmingTracker().setHidden(planner::hiddenByPreferences);
        return planner;
    }

    /** The farm run as last published, or null. Safe from any thread. */
    public FarmingAdvice getAdvice() {
        return advice;
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
        rebuild(clock.getAsLong());
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
        bottomlessBucketUses = null;
        compostUpkeep = null;
        // The book this account is not on; another account may be on it.
        spellbookPreview = null;
        tripRecord = DepositPlanner.TripRecord.EMPTY;
        wakeAtEpochSec = 0L;
        plannedAtEpochSec = 0L;
        dirty = false;
        publish(null);
    }

    /** Read this account's saved planner state. Runs after the clear, never before it. */
    private void loadPersistedState() {
        // The last contract seen, so the panel can name the crop before its varbit is readable.
        contractCrop = persistence.loadFarmingContract();

        house = persistence.loadHouse();
        bottomlessBucketUses = persistence.loadBottomlessBucket();
        compostUpkeep = persistence.loadCompostUpkeep();

        FarmingPlannerPersistence.DailyTeleportLedgerState ledgerState =
                persistence.loadDailyTeleportLedger();
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
        FarmingPreferences wanted = preferences != null ? preferences
                : FarmingPreferences.defaults();
        if (wanted.equals(this.preferences)) {
            return;
        }
        this.preferences = wanted;
        if (journal != null) {
            journal.changed("settings", wanted);
        }
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
        record("spellbookPreview", "book", spellbook != null ? spellbook.name() : null);
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
        record("showBanks", "shown", String.valueOf(shown));
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
        record("contract", "crop", crop != null ? crop.name() : null);
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

    /**
     * The bottomless bucket said how many uses of ultracompost it holds, on a check or a
     * fill. Replaces the last reading.
     */
    public void bottomlessBucketRead(int uses) {
        observeBucket(Math.max(0, uses));
    }

    /**
     * A patch was treated from the bottomless bucket, so it holds one use fewer. Without a
     * reading there is nothing to take the use from.
     */
    public void bottomlessBucketUsed() {
        Integer uses = bottomlessBucketUses;
        if (uses != null && uses > 0) {
            observeBucket(uses - 1);
        }
    }

    /**
     * The bottomless bucket went into something that changed its uses unseen, such as a
     * compost bin emptied into it, so its figure is forgotten until the next reading.
     */
    public void bottomlessBucketChangedUnseen() {
        if (bottomlessBucketUses == null) {
            return;
        }
        bottomlessBucketUses = null;
        persistence.saveBottomlessBucket(null);
        record("bucket", "uses", null);
        dirty = true;
    }

    /** Takes and saves a new figure for the bucket, and re-plans against it. */
    private void observeBucket(int uses) {
        if (bottomlessBucketUses != null && bottomlessBucketUses == uses) {
            return;
        }
        bottomlessBucketUses = uses;
        persistence.saveBottomlessBucket(uses);
        record("bucket", "uses", String.valueOf(uses));
        dirty = true;
    }

    /** Takes and saves a new sight of the house, and re-plans against it. */
    private void observeHouse(HouseSight next) {
        house = next;
        persistence.saveHouse(next);
        if (journal != null) {
            journal.changed("house", next);
        }
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
        dailyTeleportLedger.observeUsesSpent(capability, value, clock.getAsLong());
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
        long nowSec = clock.getAsLong();
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
        Capability held = DiaryRewards.bestEarnedTier(
                Capability.TierFamily.ARDOUGNE_CLOAK, state.getVarbits());
        if (held == null) {
            return;
        }
        dailyTeleportLedger.recordUse(held, clock.getAsLong());
        Trace.debug(log, "Ardougne cloak farm teleport charged against {}", held);
        saveDailyTeleportLedger();
        dirty = true;
    }

    private void saveDailyTeleportLedger() {
        persistence.saveDailyTeleportLedger(dailyTeleportLedger.exportDay(),
                dailyTeleportLedger.exportForPersist());
    }

    /** Whether the player has switched this patch off; see {@link PlantingGate#hidesPatch}. */
    private boolean hiddenByPreferences(FarmingWorldMap.PatchDef patch) {
        return PlantingGate.hidesPatch(patch, preferences);
    }

    // ---------------------------------------------------------------
    // Planning
    // ---------------------------------------------------------------

    /**
     * Re-plan if an input changed since the last rebuild, or a run came due. Call once per
     * game tick, after the engine's own flush.
     */
    public void flushIfDirty() {
        flushIfDirty(clock.getAsLong());
    }

    // ---------------------------------------------------------------
    // Bug reports
    // ---------------------------------------------------------------

    /** Record each published plan and planner input in {@code journal}, or nowhere if null. */
    public void setJournal(Journal journal) {
        this.journal = journal;
    }

    /**
     * Plan at the second {@code clock} gives rather than the wall clock's.
     */
    public void setClock(LongSupplier clock) {
        this.clock = clock;
    }

    /**
     * The second the last plan was built at, or zero before the first. A bug report records
     * it, so a plan rebuilt from the report counts down as the one on screen did.
     */
    public long getPlannedAtEpochSec() {
        return plannedAtEpochSec;
    }

    /** What the trip has packed and the bag's low marks: state no setting holds. */
    public DepositPlanner.TripRecord getTripRecord() {
        return tripRecord;
    }

    /** Whether the panel's passed banks are hidden. */
    public boolean isOfferedBanksHidden() {
        return offeredBanksHidden;
    }

    /**
     * Puts back what a checkpoint held beyond the settings, and plans again at the next
     * flush.
     */
    public void restore(DepositPlanner.TripRecord trip, Spellbook preview, boolean banksHidden,
            TravelTaken travelTaken, String travelTakenFor) {
        tripRecord = trip;
        spellbookPreview = preview;
        offeredBanksHidden = banksHidden;
        travelWatch.restore(travelTaken, travelTakenFor);
        dirty = true;
    }

    /** The teleport spent on the way to the next stop, for a checkpoint. */
    public TravelTaken getTravelTaken() {
        return travelWatch.taken();
    }

    /** The patch the spent teleport was cast for, or null with none spent. */
    public String getTravelTakenFor() {
        return travelWatch.takenFor();
    }

    /**
     * One tick of the teleport watch. Call once per game tick, before the engine's flush, so
     * a plan built this tick already knows the teleport is paid.
     */
    public void watchTravel() {
        PlayerState state = engine.getPlayerState();
        FarmingStopAdvice heading = headingStop();
        if (state == null || !travelWatch.tick(state,
                heading == null ? null : heading.getPatchName(),
                heading == null || heading.getLanding() == null ? null
                        : Destination.valueOf(heading.getLanding()))) {
            return;
        }
        dirty = true;
        TravelTaken taken = travelWatch.taken();
        if (taken.isNone()) {
            Trace.debug(log, "Teleport no longer paid for: the stop it was spent for is done");
        } else {
            Trace.debug(log, "Teleport spent on the way to {}: {}", travelWatch.takenFor(),
                    taken);
        }
        if (journal != null) {
            JsonObject data = new JsonObject();
            JsonArray places = new JsonArray();
            taken.getPlaces().forEach(place -> places.add(place.name()));
            JsonArray items = new JsonArray();
            taken.getItems().forEach(item -> items.add(item.name()));
            data.add("places", places);
            data.add("items", items);
            data.addProperty("for", travelWatch.takenFor());
            journal.event("travelTaken", data);
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
            Trace.debug(log, "A planting showed or its hold ran out; planning again");
        } else if (!was && plantingWatch.holding()) {
            Trace.debug(log, "A seed left the bag; holding the plan until the patch shows it");
        }
    }

    /** The stop the advice on screen sends the player to next, or null for none. */
    private FarmingStopAdvice headingStop() {
        if (advice == null || advice.getStops() == null) {
            return null;
        }
        for (FarmingStopAdvice stop : advice.getStops()) {
            if (stop.getKind() == FarmingStopAdvice.Kind.PATCH
                    && stop.getOutcome() == FarmingStopAdvice.Outcome.VISIT) {
                return stop;
            }
        }
        return null;
    }

    private void record(String type, String field, String value) {
        if (journal == null) {
            return;
        }
        JsonObject data = new JsonObject();
        data.addProperty(field, value);
        journal.event(type, data);
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

    /** Arms the wake-up directly, without building a run that waits on a crop. */
    void armWakeAt(long readyAtEpochSec) {
        wakeAtEpochSec = readyAtEpochSec;
    }

    /** The second the wake-up is armed for, or zero. */
    long wakeAt() {
        return wakeAtEpochSec;
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
        plannedAtEpochSec = nowEpochSec;
        // The clock enters the farming layer here and nowhere below it. Everything under
        // the scheduler takes the epoch second as an argument, so the time can be fixed.
        FarmingInputs inputs = inputs(nowEpochSec);
        inputs = inputs.withCompostStock(compostStock(state, inputs));
        FarmingAdvice next = advisor.advise(state, inputs, nowEpochSec);
        // The trip record reads the advice it feeds. A changed packed set plans once more
        // before publishing, so the next bank's deposits see it at once. Twice is enough:
        // the deposits feed no list the record reads.
        DepositPlanner.TripRecord record = DepositPlanner.record(state, next, tripRecord);
        boolean replan = !record.getPacked().equals(tripRecord.getPacked());
        tripRecord = record;
        if (replan) {
            next = advisor.advise(state, inputs.withPackedThisTrip(record.getPacked()),
                    nowEpochSec);
        }
        armWake(next, nowEpochSec);
        publish(next);
    }

    /**
     * Counts the compost stock and moves the upkeep thermostat on it, saving a change.
     *
     * <p>A bucket stored at the leprechaun gives its figure exactly, so it replaces the last
     * reading and is still known once the player takes it out. A bucket seen empty, with no
     * filled one anywhere, has been emptied, and its reading is 0.
     *
     * <p>A count that reads short once would switch upkeep on and keep it on until the high
     * threshold. So while the bank has never been seen, or a bucket the player holds has no
     * figure, the count is only a floor: it can switch upkeep off, never on.
     */
    private CompostStock compostStock(PlayerState state, FarmingInputs inputs) {
        Integer known = BottomlessBucket.isStored(state)
                ? Integer.valueOf(BottomlessBucket.storedUltracompostUses(state))
                : CompostStock.bucketSeenEmpty(state) ? Integer.valueOf(0) : null;
        if (known != null && !known.equals(bottomlessBucketUses)) {
            bottomlessBucketUses = known;
            persistence.saveBottomlessBucket(known);
        }
        int patches = CompostStock.count(state, bottomlessBucketUses);
        boolean bucketUnread = CompostStock.bucketUnread(state, bottomlessBucketUses);
        if (compostUpkeep != null && holdsTheThermostat(advice, inputs)) {
            return new CompostStock(patches, compostUpkeep, bucketUnread);
        }
        boolean whole = state.getBankLastSeen() != null && !bucketUnread;
        boolean on = CompostStock.upkeepAfter(compostUpkeep, patches, whole, preferences);
        // A floor decides only "off". Never decided and short of a whole count, upkeep is on
        // by default, and stays undecided.
        boolean decided = whole || !on;
        if (decided && !Boolean.valueOf(on).equals(compostUpkeep)) {
            Trace.debug(log, "Compost upkeep {} at {} ultracompost", on ? "on" : "off", patches);
            compostUpkeep = on;
            persistence.saveCompostUpkeep(on);
        }
        return new CompostStock(patches, on, bucketUnread);
    }

    /**
     * Whether the trip on screen keeps the upkeep decision where it is.
     *
     * <p>Held while the player is at the bins on this trip: the bins are on the trip shown
     * and one was seen this trip. Emptying a bin into the bucket is how the stock rises
     * mid-trip, and the bins still ahead stay on. A ready bin nobody has reached yet holds
     * nothing, so compost bought before setting off still switches upkeep off.
     *
     * <p>Held too while the player is partway round, so a stock falling below the low
     * threshold does not add bins mid-trip. Read off the last advice, so the decision moves
     * one rebuild after the trip ends.
     */
    private static boolean holdsTheThermostat(FarmingAdvice shown, FarmingInputs inputs) {
        if (shown == null) {
            return false;
        }
        return shown.isUnderway() || shown.getTripRuns() != null
                && shown.getTripRuns().contains(RunId.ULTRACOMPOST.name())
                && CompostStock.aBinSeenThisTrip(inputs);
    }

    /** Every field of the advice is drawn, so it compares whole. */
    private void publish(FarmingAdvice next) {
        if (Objects.equals(advice, next)) {
            return;
        }
        advice = next;
        adviceVersion++;
        if (journal != null) {
            journal.plan(next, tripRecord.getPacked());
        }
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
        return FarmingInputs.of(spellbookPreview,
                patch -> {
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
