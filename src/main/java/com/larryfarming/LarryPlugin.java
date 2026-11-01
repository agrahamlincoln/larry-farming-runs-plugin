package com.larryfarming;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.larryfarming.engine.*;
import com.larryfarming.engine.farming.*;
import com.larryfarming.farming.FarmingPlanner;
import com.larryfarming.farming.FarmingPlannerPersistence;
import com.larryfarming.farming.catalogue.CropData;
import com.larryfarming.farming.detection.*;
import com.larryfarming.farming.model.FarmingPreferences;
import com.larryfarming.farming.model.Spellbook;
import com.larryfarming.model.*;
import com.larryfarming.overlay.FarmingAdvisorInfoBox;
import com.larryfarming.panel.*;
import com.larryfarming.persistence.ConfigStore;
import com.larryfarming.persistence.StatePersistence;
import com.larryfarming.snapshot.FarmingAdvice;
import com.larryfarming.snapshot.FarmingRunStatus;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.*;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.util.*;

@Slf4j
@PluginDescriptor(
	name = "Larry: Farming Runs",
	description = "Larry plans the route. You plant the patches. He learns your teleports and unlocks as you play, so there's nothing to set up.",
	tags = {"farming", "farm run", "herb run", "tree run", "fruit tree", "hardwood", "seaweed",
		"hespori", "allotment", "contract", "compost", "seeds", "spirit tree", "teleport", "route",
		"patches", "timers", "ironman", "leprechaun", "planner", "helper"},
	// The Plugin Hub's name for Larry; the client keeps his data directory under it.
	internalName = "larry-farming-runs"
)
public class LarryPlugin extends Plugin
{
	private static final int INVENTORY_SIZE = 28;

	private static final EquipmentSlot[] EQUIPMENT_SLOTS = EquipmentSlot.values();
	/** Each of {@link #EQUIPMENT_SLOTS}' index in the worn container; the names match. */
	private static final int[] EQUIPMENT_SLOT_INDICES = Arrays.stream(EQUIPMENT_SLOTS)
		.mapToInt(slot -> EquipmentInventorySlot.valueOf(slot.name()).getSlotIdx()).toArray();

	/** Matches RuneLite widget colour/formatting tags. */
	private static final Pattern WIDGET_TAG = Pattern.compile("<[^>]+>");

	/**
	 * Varbits stored in the player state, because the planner reads them. Every other varbit
	 * change only asks for a re-plan.
	 */
	private static final Set<Integer> WATCHED_VARBITS = watchedVarbits();

	/** The assigned contract's produce; zero while the contract slot is empty. */
	private static final int VARBIT_FARMING_CONTRACT_TYPE = CapabilitySources.CONTRACT_PRODUCE_VARBIT;
	/** Seed pack: fallback advisor icon. */
	private static final int ADVISOR_FALLBACK_ICON_ITEM_ID = 22993;

	/** Login ticks to wait for the local player's name before reading the account anyway. */
	private static final int LOGIN_READY_MAX_TICKS = 10;
	/** Loads tried per login before tracking pauses for the session; see sendLoginSnapshot. */
	private static final int LOGIN_LOAD_ATTEMPTS = 3;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private LarryConfig config;

	@Inject
	private ItemManager itemManager;

	@Inject
	private SpriteManager spriteManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private Gson gson;

	@Inject
	private InfoBoxManager infoBoxManager;

	@Inject
	private ClientToolbar clientToolbar;

	private LarryPanel panel;
	private IconProvider iconProvider;
	private NavigationButton navButton;

	/**
	 * Volatile: written in {@code startUp} and {@code shutDown}, and read from the client
	 * thread and the config listener. An unsafely published engine can be seen
	 * half-constructed.
	 */
	private volatile StateEngine engine;

	/** Plans the farm runs over {@link #engine}'s account. Volatile for the same reason. */
	private volatile FarmingPlanner planner;

	private final int[] previousXp = new int[Skill.values().length];

	// Container snapshots for change detection
	private long[] previousInventorySlots;
	private int[] previousEquipmentIds;
	private int previousBankHash;
	private int previousSeedVaultHash;
	private int previousSeedBoxHash;

	/**
	 * Item ids by lower-case name, for every item seen in a container this session.
	 *
	 * <p>Exists for the seed box, whose chat lines ("Stored 6 x Avantoe seed in your seed
	 * box") carry no id. Seeds being boxed were in the bag or bank moments earlier, so this
	 * has seen them by the time the line arrives.
	 *
	 * <p>Keys are canonical names, so a noted stack resolves to its unnoted item. Bank
	 * placeholders are excluded: they carry the real item's name against their own id, which
	 * the player owns none of. Not persisted.
	 */
	private final Map<String, Integer> itemIdsByName = new HashMap<>();

	/**
	 * The Check listing being read right now, or null when none is open.
	 *
	 * <p>A listing arrives as one chat message per seed with no closing line. It ends at the
	 * first message that is not one of its entries, or at the end of the tick.
	 */
	private SeedBoxListing seedBoxListing;

	/** Each quest's state on the last tick, to spot one starting or finishing. */
	private final Map<Quest, QuestState> previousQuestStates = new LinkedHashMap<>();

	/** The map region the player stood in at the last report, or -1 before the first. */
	private int lastRegionId = -1;

	/**
	 * Counts the Ardougne cloak's daily farm teleports, which the game exposes no var for.
	 * Armed by the menu click and confirmed by the arrival, so a click that teleported
	 * nobody is not charged against the day's allowance.
	 */
	private final ArdougneFarmTeleportWatch ardougneFarmTeleport = new ArdougneFarmTeleportWatch();

	/**
	 * Reads what is built in the player's own house while it loads: whose house it is from
	 * the click that led in, the furniture from the load's object spawns.
	 */
	private final HouseWatch houseWatch = new HouseWatch();

	// Deposit box inference: snapshot inventory when deposit box opens,
	// diff on inventory changes to infer bank additions.
	private boolean depositBoxOpen = false;
	/** Ticks until the just-opened seed vault is checked for an empty one; zero for none. */
	private int seedVaultCheckIn;
	private Map<Integer, ItemStack> depositBoxInventorySnapshot;

	// Login readiness gate
	private boolean loginReady = false;
	private boolean loginSnapshotPending = false;
	private int loginTickCounter = 0;
	/** Loads that have thrown since the last login, or since the last load that worked. */
	private int loginLoadFailures;

	// Farming patches. The game sends nearby patch states through a shared pool of
	// varbits whose meaning depends on the map region, so patches are sampled per tick
	// by (region, varbit), never from VarbitChanged. As in RuneLite's Time Tracking
	// plugin, sample only after two ticks in the same region (the varbits lag region
	// entry) and never while a modal widget is open (varbits are not sent then). A scan
	// is held a tick and dropped if the player has left the region, because on a teleport
	// out the varbits lead the move (see FarmingScanHold).

	/** Player location on the previous game tick, for the same-region gate. */
	private WorldPoint lastFarmTickLocation;
	/** This tick's patch scan, applied next tick if the player is still in its region. */
	private final FarmingScanHold farmScanHold = new FarmingScanHold();
	/** Farming regions sampled on the previous scan. A change suppresses offset learning once. */
	private Set<FarmingWorldMap.RegionDef> lastFarmRegions = new HashSet<>();
	/** Tick when the last modal widget closed; offset learning needs a quiet tick after it. */
	private int lastModalCloseTick = -1;

	private FarmingAdvisorInfoBox farmingAdvisorInfoBox;
	/** Item id of the advisor box's current icon, so it is only swapped when it changes. */
	private int advisorIconItemId;

	/**
	 * Whether a session on these worlds is the account's real progress.
	 *
	 * <p>PvP Arena worlds hand every player a preset loadout with most quests, stats and
	 * spellbooks unlocked. Reading that as the account would plan routes it cannot take, and
	 * because the engine outlives a hop, the saved state would carry them back to the real
	 * profile. RuneLite keeps a separate profile for these worlds, but that only separates
	 * storage, not the in-memory engine, so nothing is read on these worlds.
	 *
	 * @param worldTypes the current world's types, from {@link Client#getWorldType()}
	 * @return false when the session must leave the account untouched, true otherwise
	 */
	static boolean tracksProgressOn(Set<WorldType> worldTypes)
	{
		return !worldTypes.contains(WorldType.PVP_ARENA);
	}

	@Override
	protected void startUp()
	{
		ConfigStore store = ConfigStore.of(configManager);
		StateEngine stateEngine = new StateEngine(new StatePersistence(store, gson));
		FarmingPlannerPersistence plannerPersistence = new FarmingPlannerPersistence(store);
		FarmingPlanner farmingPlanner = FarmingPlanner.attachTo(stateEngine, plannerPersistence);
		farmingPlanner.setPreferences(ConfiguredCrops.from(store));
		boolean showBanks = plannerPersistence.loadFarmingShowBanks();
		farmingPlanner.setOfferedBanksShown(showBanks);

		// Published only once they hold the settings, so the client thread never sees them without.
		engine = stateEngine;
		planner = farmingPlanner;

		FarmingPanel farmingPanel = new FarmingPanel();
		// Client thread: the preview changes what the planner plans, and plans are built there.
		farmingPanel.setSpellbookCallback(book -> clientThread.invoke(() ->
			farmingPlanner.setSpellbookPreview(book == null ? null : Spellbook.valueOf(book))));
		// A hidden bank holds no withdrawals, so the switch re-plans as well as redraws.
		farmingPanel.setShowBanks(showBanks);
		farmingPanel.setShowBanksCallback(show ->
		{
			plannerPersistence.saveFarmingShowBanks(show);
			clientThread.invoke(() -> farmingPlanner.setOfferedBanksShown(show));
		});

		// EDT is fine here: these only choose what the panel draws and save a config key.
		// They touch no game state.
		farmingPanel.setShowSkipped(plannerPersistence.loadFarmingShowSkipped());
		farmingPanel.setShowSkippedCallback(plannerPersistence::saveFarmingShowSkipped);
		farmingPanel.setTravelOpen(plannerPersistence.loadFarmingTravelOpen());
		farmingPanel.setTravelOpenCallback(plannerPersistence::saveFarmingTravelOpen);
		farmingPanel.setHouseOpen(plannerPersistence.loadFarmingHouseOpen());
		farmingPanel.setHouseOpenCallback(plannerPersistence::saveFarmingHouseOpen);
		farmingPanel.setSettingsOpen(plannerPersistence.loadFarmingSettingsOpen());
		farmingPanel.setSettingsOpenCallback(plannerPersistence::saveFarmingSettingsOpen);

		// A setting changed in the panel saves to its config key. The save posts a
		// ConfigChanged, and onConfigChanged re-plans from it.
		farmingPanel.setSettingCallback((key, value) -> ConfiguredCrops.write(store, key, value));
		// Each key written posts a ConfigChanged, which re-plans as a single change does.
		farmingPanel.setResetCallback(() -> ConfiguredCrops.apply(store, ConfiguredCrops.valuesOf(FarmingPreferences.defaults())));

		panel = new LarryPanel(farmingPanel);
		iconProvider = new IconProvider(itemManager, spriteManager);
		iconProvider.setOnImageLoaded(panel::iconLoaded);
		panel.setIconProvider(iconProvider);
		panel.setPlanner(farmingPlanner);

		navButton = NavigationButton.builder().tooltip("Larry").icon(ImageUtil.loadImageResource(LarryPlugin.class, "panel_icon.png"))
			.priority(6).panel(panel).build();
		clientToolbar.addNavigation(navButton);

		endSession();

		// Arm the login gate here when the plugin starts while already logged in.
		// GameStateChanged only fires on a transition, so waiting for LOGGED_IN would leave
		// every handler gated behind loginReady forever.
		clientThread.invokeLater(() ->
		{
			if (client.getGameState() != GameState.LOGGED_IN || !tracksProgressOn(client.getWorldType()))
			{
				return;
			}
			loginSnapshotPending = true;
			loginTickCounter = 0;
			initializeQuestStates();
			initializeXpTracking();
			learnSeedNames();
		});
	}

	@Override
	protected void shutDown()
	{
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}
		if (panel != null)
		{
			panel.dispose();
			panel = null;
		}

		StateEngine stateEngine = engine;
		// Before the login snapshot runs the state was never loaded, and saving it would
		// write an empty account over the real one.
		if (stateEngine != null && loginReady)
		{
			stateEngine.forceSave();
		}
		engine = null;
		planner = null;
		// RuneLite keeps the plugin instance across disable and enable, so a listing left
		// open here would swallow the next "2 x Thing." chat line after re-enabling, and a
		// held patch scan would reach the next engine, hours stale.
		endSession();
	}

	/**
	 * Drops everything that belongs to the session being left. The engine outlives a logout
	 * or hop, and the plugin instance outlives a disable, so anything still held would be
	 * credited to the next account or the next start.
	 */
	private void endSession()
	{
		resetSessionCaches();
		loginReady = false;
		loginSnapshotPending = false;
		depositBoxOpen = false;
		depositBoxInventorySnapshot = null;
		seedVaultCheckIn = 0;
		// An open listing would read its first "2 x Thing." as a seed in the box.
		seedBoxListing = null;
		// A hop can land the next account at the Ardougne patch inside the window.
		ardougneFarmTeleport.clear();
		// A house half-read, or a click into one, belongs to the account that left.
		houseWatch.clear();
		removeFarmingAdvisorInfoBox();
		lastFarmTickLocation = null;
		lastFarmRegions = new HashSet<>();
		farmScanHold.clear();
	}

	/**
	 * Forget the change-detection baselines the handlers compare each event against. Each
	 * describes the account that was playing, so one kept past a logout would drop the next
	 * account's first change when it matched. Also forgets how many loads failed, so the next
	 * login tries afresh.
	 */
	private void resetSessionCaches()
	{
		Arrays.fill(previousXp, -1);
		previousInventorySlots = null;
		previousEquipmentIds = null;
		previousBankHash = 0;
		previousSeedVaultHash = 0;
		previousSeedBoxHash = 0;
		previousQuestStates.clear();
		// The player state's region is transient, so the next session starts with none.
		lastRegionId = -1;
		lastModalCloseTick = -1;
		loginLoadFailures = 0;
	}

	@Provides
	LarryConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(LarryConfig.class);
	}

	// ---------------------------------------------------------------
	// Settings
	// ---------------------------------------------------------------

	/**
	 * Pushes crop settings to the planner, whose advice is only rebuilt on request. The
	 * infobox toggle needs nothing here; it is read every tick.
	 *
	 * <p>Filtered by key, not just group: the plugin saves the account into the same group,
	 * posting a {@code ConfigChanged} per save.
	 *
	 * <p>Hopped to the client thread because the config panel writes from the EDT and the
	 * planner's dirty flag is client-thread state. Setting it from the EDT races a concurrent
	 * flush and loses the rebuild.
	 */
	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (planner == null || !LarryConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		if (ConfiguredCrops.isCropKey(event.getKey()))
		{
			reloadPreferences();
		}
	}

	/** Reloads the farming settings for the RuneLite profile switched to. */
	@Subscribe
	public void onProfileChanged(ProfileChanged event)
	{
		if (planner == null)
		{
			return;
		}
		reloadPreferences();
	}

	private void reloadPreferences()
	{
		clientThread.invokeLater(() ->
		{
			FarmingPlanner current = planner;
			if (current != null)
			{
				current.setPreferences(ConfiguredCrops.from(ConfigStore.of(configManager)));
			}
		});
	}

	// ---------------------------------------------------------------
	// Login and logout
	// ---------------------------------------------------------------

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		StateEngine stateEngine = engine;
		if (stateEngine == null)
		{
			return;
		}

		// Every scene load, house or not: a house's furniture spawns during its load.
		if (event.getGameState() == GameState.LOADING)
		{
			houseWatch.sceneLoading();
			return;
		}

		if (event.getGameState() == GameState.LOGGED_IN)
		{
			houseWatch.sceneLoaded();
			// LOGGED_IN is not a login. The client re-enters it after every loading screen
			// (teleports, boats, stairs). Re-reading the account then would reload the saved
			// state and wipe every live reading, such as the seed box read stamp.
			//
			// A real login arrives with no session established and none pending. The
			// LOGIN_SCREEN/HOPPING arm below clears both flags, so logouts, hops and fresh
			// starts still count.
			if (loginReady || loginSnapshotPending)
			{
				return;
			}
			// The saved state failed to load on every attempt; stay paused until a logout or
			// hop rather than retrying at every loading screen.
			if (loginLoadFailures >= LOGIN_LOAD_ATTEMPTS)
			{
				return;
			}
			if (!tracksProgressOn(client.getWorldType()))
			{
				log.debug("PvP Arena world: farm run planning paused until you leave");
				return;
			}
			loginSnapshotPending = true;
			loginTickCounter = 0;

			clientThread.invokeLater(() ->
			{
				initializeQuestStates();
				initializeXpTracking();
				// Item compositions need the client thread. startUp makes the same call
				// for a plugin enabled mid-session.
				learnSeedNames();
			});
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
		{
			if (loginReady)
			{
				stateEngine.forceSave();
			}
			endSession();
			LarryPanel shown = panel;
			if (shown != null)
			{
				shown.showLoggedOut();
			}
		}
	}

	/** The baseline this class diffs against to spot a quest changing state. */
	private void initializeQuestStates()
	{
		previousQuestStates.clear();
		for (Quest quest : Quest.values())
		{
			QuestState state = questState(quest);
			if (state != null)
			{
				previousQuestStates.put(quest, state);
			}
		}
	}

	/** A quest's state, or null for one that cannot be queried. */
	private QuestState questState(Quest quest)
	{
		try
		{
			return quest.getState(client);
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	@SuppressWarnings("deprecation") // Skill.OVERALL — no gameval replacement
	private void initializeXpTracking()
	{
		for (Skill skill : Skill.values())
		{
			if (skill == Skill.OVERALL)
			{
				continue;
			}
			previousXp[skill.ordinal()] = client.getSkillExperience(skill);
		}
	}

	/**
	 * Reads the account into the engine.
	 *
	 * <p>Without a local player there is no profile to load, so this does nothing. A caller
	 * that set {@code loginReady} anyway would let the first save write an unloaded, empty
	 * state over the account's real one.
	 *
	 * <p>A load that throws leaves the engine holding part of the account, and a save would
	 * write that part over the whole profile. So a failure closes the save gate and re-arms
	 * the login gate to try again next tick. After {@link #LOGIN_LOAD_ATTEMPTS} failures the
	 * session stays paused, saving nothing, until the next login.
	 *
	 * @return false when there was no local player, the world is not tracked
	 *         ({@link #tracksProgressOn}) or the saved state failed to load, in which case
	 *         the caller must not open the login gate
	 */
	@SuppressWarnings("deprecation") // Skill.OVERALL — no gameval replacement
	private boolean sendLoginSnapshot()
	{
		StateEngine stateEngine = engine;
		if (stateEngine == null || client.getLocalPlayer() == null || !tracksProgressOn(client.getWorldType()))
		{
			return false;
		}

		// The RS profile is available now.
		try
		{
			stateEngine.loadPersistedState();
		}
		catch (RuntimeException e)
		{
			loginReady = false;
			loginLoadFailures++;
			if (loginLoadFailures < LOGIN_LOAD_ATTEMPTS)
			{
				log.debug("Failed to load the saved account (attempt {}), retrying next tick", loginLoadFailures, e);
				loginSnapshotPending = true;
			}
			else
			{
				log.warn("Failed to load the saved account; planning paused until the next " + "login, and nothing is saved", e);
				loginSnapshotPending = false;
			}
			return false;
		}
		loginLoadFailures = 0;

		Map<String, EventApplier.SkillSnapshot> skills = new LinkedHashMap<>();
		for (Skill skill : Skill.values())
		{
			if (skill == Skill.OVERALL)
			{
				continue;
			}
			skills.put(skill.getName(), new EventApplier.SkillSnapshot(client.getRealSkillLevel(skill), client.getSkillExperience(skill)));
		}

		List<String> completedQuests = new ArrayList<>();
		List<String> startedQuests = new ArrayList<>();
		for (Quest quest : Quest.values())
		{
			QuestState qs = questState(quest);
			if (qs == QuestState.FINISHED)
			{
				completedQuests.add(quest.getName());
			}
			else if (qs == QuestState.IN_PROGRESS)
			{
				startedQuests.add(quest.getName());
			}
		}

		List<ItemStack> inventory = new ArrayList<>();
		ItemContainer invContainer = client.getItemContainer(InventoryID.INV);
		if (invContainer != null)
		{
			inventory = readInventory(invContainer.getItems());
		}

		ItemContainer eqContainer = client.getItemContainer(InventoryID.WORN);
		Map<EquipmentSlot, ItemStack> equipment = eqContainer != null ? readEquipment(eqContainer.getItems())
			: new EnumMap<>(EquipmentSlot.class);

		stateEngine.onLogin(skills, completedQuests, startedQuests, inventory, equipment);

		// Read the vars the planner reads, so values set before this session count at once.
		// Writes the player state directly, then asks for a single re-plan.
		try
		{
			PlayerState ps = stateEngine.getPlayerState();
			for (int varbitId : WATCHED_VARBITS)
			{
				readVarbitIntoState(ps, varbitId);
			}

			// Records never-started quests too: absence is not "no". A capability behind an
			// unrecorded quest resolves unsure, which keeps offering a route the player
			// cannot take.
			seedQuestStates(ps);

			// The scan writes the state directly, bypassing the engine's varbit events, so
			// the daily-teleport ledger must be primed explicitly. Otherwise an allowance
			// spent before this session looks untouched.
			FarmingPlanner farmingPlanner = planner;
			if (farmingPlanner != null)
			{
				farmingPlanner.primeDailyTeleportLedgerFromLogin();
				// Any Nexus slot already sent proves its destination. A zero here may be a
				// slot not sent yet, so this only adds.
				farmingPlanner.nexusAttuned(PortalNexus.attunedIn(this::varbitOrZero));
			}

			// Live var changes are the only other source of quest stages, so read the
			// current values for quests progressed before this session.
			for (Map.Entry<Integer, String> entry : QuestStages.BY_VARBIT.entrySet())
			{
				recordQuestStage(ps, entry.getValue(), client.getVarbitValue(entry.getKey()));
			}
			for (Map.Entry<Integer, String> entry : QuestStages.BY_VARP.entrySet())
			{
				recordQuestStage(ps, entry.getValue(), client.getVarpValue(entry.getKey()));
			}

			// Runes already in the pouch count from login.
			updateRunePouch();

			stateEngine.triggerResolve();
		}
		catch (RuntimeException e)
		{
			log.warn("Failed to read the account's vars at login", e);
		}
		// The profile loaded. A failed var scan only leaves some vars unread, which the live
		// handlers repair.
		return true;
	}

	/** A quest stage read at login; the scan stores only stages past zero. */
	private static void recordQuestStage(PlayerState ps, String questName, int value)
	{
		if (value != 0)
		{
			ps.getQuestProgress().put(questName, value);
		}
	}

	/**
	 * Writes every quest's state into the player state at login, including NOT_STARTED.
	 *
	 * <p>Called from the login snapshot, because the player state is created lazily and can be
	 * replaced by a saved one. Only by then is {@code ps} certainly the live instance.
	 */
	private void seedQuestStates(PlayerState ps)
	{
		for (Quest quest : Quest.values())
		{
			QuestStatus status = toQuestStatus(questState(quest));
			if (status != null)
			{
				ps.getQuests().put(quest.getName(), status);
			}
		}
	}

	private static QuestStatus toQuestStatus(QuestState state)
	{
		if (state == null)
		{
			return null;
		}
		switch (state)
		{
			case FINISHED:
				return QuestStatus.COMPLETED;
			case IN_PROGRESS:
				return QuestStatus.IN_PROGRESS;
			case NOT_STARTED:
				return QuestStatus.NOT_STARTED;
			default:
				return null;
		}
	}

	/**
	 * Reads and stores one varbit.
	 *
	 * <p>An id this client does not know is skipped: the plugin builds against a floating
	 * RuneLite release, so a newer varbit may be missing from the running client.
	 */
	private void readVarbitIntoState(PlayerState ps, int varbitId)
	{
		try
		{
			ps.getVarbits().put(varbitId, client.getVarbitValue(varbitId));
		}
		catch (IndexOutOfBoundsException e)
		{
			log.debug("Varbit {} does not exist in this client, skipping", varbitId);
		}
	}

	/** A varbit's value, or zero for an id this client does not know. */
	private int varbitOrZero(int varbitId)
	{
		try
		{
			return client.getVarbitValue(varbitId);
		}
		catch (IndexOutOfBoundsException e)
		{
			return 0;
		}
	}

	private static Set<Integer> watchedVarbits()
	{
		Set<Integer> ids = new HashSet<>(ItemCounts.LEPRECHAUN_STORAGE_VARBIT_IDS);
		ids.addAll(BottomlessBucket.STORED_VARBITS);
		for (int id : CapabilitySources.LOGIN_SCAN_VARBIT_IDS)
		{
			ids.add(id);
		}
		return Set.copyOf(ids);
	}

	// ---------------------------------------------------------------
	// Skills
	// ---------------------------------------------------------------

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		StateEngine stateEngine = engine;
		if (stateEngine == null || !loginReady)
		{
			return;
		}

		Skill skill = event.getSkill();
		int ordinal = skill.ordinal();
		int currentXp = event.getXp();
		int xpGained = 0;

		if (previousXp[ordinal] >= 0)
		{
			xpGained = currentXp - previousXp[ordinal];
		}
		previousXp[ordinal] = currentXp;

		if (xpGained <= 0)
		{
			return;
		}

		stateEngine.onStatChanged(skill.getName(), currentXp, event.getLevel());
	}

	// ---------------------------------------------------------------
	// Chat: the seed box
	// ---------------------------------------------------------------

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (engine == null || !loginReady)
		{
			return;
		}
		if (!SeedBoxChat.isReadableGameChat(event.getType()))
		{
			return;
		}

		// Tags are stripped only when present. This handler sees SPAM-tier skilling lines,
		// which rarely carry a tag, and the replace would allocate a string per message.
		String raw = event.getMessage();
		String message = raw.indexOf('<') < 0 ? raw : WIDGET_TAG.matcher(raw).replaceAll("");

		// Checked before the movement lines. A whole-box statement is the only correction
		// for a drifted tally, since movement lines only adjust the count.
		if (SeedBoxChat.isSeedBoxReportedEmpty(message))
		{
			seedBoxListing = null;
			engine.onSeedBoxChanged(List.of());
			return;
		}
		if (SeedBoxChat.isSeedBoxContentsHeader(message))
		{
			seedBoxListing = new SeedBoxListing();
			return;
		}
		if (seedBoxListing != null && SeedBoxListing.read(message, seedBoxListing, itemIdsByName))
		{
			return;
		}
		// Anything else ends the listing. The entry pattern matches common chat shapes, so
		// an open listing would swallow the next unrelated "2 x Thing.".
		commitSeedBoxListing();

		SeedBoxChat.parseSeedBoxMove(message).ifPresent(this::applySeedBoxMove);
	}

	/**
	 * Hands a finished Check listing to the engine, if one was being read.
	 *
	 * <p>Committed once, at the end, not per line: each commit stamps the box as verified,
	 * so a per-line commit cut short would mark a half-read box as known. The whole listing
	 * arrives within one tick, so the tick ends it.
	 *
	 * <p>Entries that could not be named are dropped and the rest committed. Short is the
	 * safe direction: a missing seed asks the player to withdraw it, while an invented one is
	 * a planting they cannot make. See {@link #learnSeedNames}.
	 */
	private void commitSeedBoxListing()
	{
		SeedBoxListing listing = seedBoxListing;
		StateEngine stateEngine = engine;
		if (listing == null || stateEngine == null)
		{
			return;
		}
		seedBoxListing = null;
		if (listing.getLines() == 0)
		{
			// The game prints the header only when there is something to list, so this is
			// a lost listing, not an empty box. An empty box has its own message.
			return;
		}
		if (listing.getSeeds().size() < listing.getLines())
		{
			log.debug("Seed box listed {} lines and {} were names we know", listing.getLines(), listing.getSeeds().size());
		}
		stateEngine.onSeedBoxChanged(listing.getSeeds());
	}

	/**
	 * Teaches {@link #itemIdsByName} every farming seed's name. A seed box holds the seeds
	 * that are not in the inventory, so without this a Check made before opening the bank
	 * would resolve nothing and commit the box as empty.
	 *
	 * <p>Names come from item compositions, not crop names, because the listing prints the
	 * game's own wording. Requires the client thread. {@code putIfAbsent}, so ids reported
	 * by a real container win.
	 */
	private void learnSeedNames()
	{
		for (CropData data : CropData.values())
		{
			int seed = data.getSeedItemId();
			if (seed <= 0)
			{
				continue;
			}
			ItemComposition comp = itemManager.getItemComposition(seed);
			if (comp != null && comp.getName() != null && !comp.getName().isEmpty())
			{
				itemIdsByName.putIfAbsent(comp.getName().toLowerCase(Locale.ROOT), seed);
			}
		}
	}

	/**
	 * Folds a seed box movement into the tracked contents.
	 *
	 * <p>Seeds leaving are applied by name, resolved by the engine against the box. Seeds
	 * arriving need an item id from {@link #itemIdsByName}. An unknown name is dropped: an
	 * entry with a placeholder id satisfies no packing line and only makes the box look
	 * occupied.
	 */
	private void applySeedBoxMove(SeedBoxChat.SeedBoxMove move)
	{
		if (move.getQuantity() < 0)
		{
			engine.onSeedBoxTaken(move.getItemName(), -move.getQuantity());
			return;
		}

		Integer itemId = itemIdsByName.get(move.getItemName().toLowerCase(Locale.ROOT));
		if (itemId == null)
		{
			log.debug("Seed box stored an item never seen in a tracked container: {}", move.getItemName());
			return;
		}
		engine.onSeedBoxStored(new ItemStack(itemId, move.getQuantity(), move.getItemName()));
	}

	// ---------------------------------------------------------------
	// Game tick
	// ---------------------------------------------------------------

	@Subscribe
	public void onGameTick(GameTick event)
	{
		StateEngine stateEngine = engine;
		FarmingPlanner farmingPlanner = planner;
		if (stateEngine == null || farmingPlanner == null)
		{
			return;
		}

		// A Check listing arrives within one tick, so the tick ends it.
		commitSeedBoxListing();

		// Sprites require client thread access
		if (iconProvider != null)
		{
			iconProvider.loadSprites();
		}

		if (loginSnapshotPending)
		{
			loginTickCounter++;
			Player localPlayer = client.getLocalPlayer();
			String name = localPlayer != null ? localPlayer.getName() : null;

			// Ready only if the snapshot actually ran (it no-ops without a local player).
			// Otherwise the state was never loaded, and the first save would write an empty
			// account over the real one. Staying armed is the safe failure.
			if ((name != null && !name.isEmpty()) || loginTickCounter >= LOGIN_READY_MAX_TICKS)
			{
				if (sendLoginSnapshot())
				{
					loginSnapshotPending = false;
					loginReady = true;
					log.debug("Login ready after {} tick(s)", loginTickCounter);
				}
			}

			// Only once the state exists, or the first region would be dropped and the plan
			// left position-blind until the player crosses a boundary.
			if (loginReady)
			{
				handleRegionTracking();
				LarryPanel shown = panel;
				if (shown != null)
				{
					shown.showLoggedIn();
				}
			}
			return;
		}

		if (!loginReady)
		{
			return;
		}

		// Before anything that routes from the player's position. The planner prices a
		// teleport at nothing for a player already at its arrival, so the region must be
		// this tick's.
		handleRegionTracking();

		// Before the re-plan, so a house scan committed this tick is planned this tick.
		handleHouse();

		// After the region and before the re-plan, so a teleport spent this tick is already
		// gone from the allowance the route is picked against.
		handleArdougneFarmTeleport();

		handleFarming();

		if (seedVaultCheckIn > 0 && --seedVaultCheckIn == 0)
		{
			checkOpenedSeedVault();
		}

		// Read the contract crop before the advisor box reconciles.
		readContractCrop();

		reconcileFarmingAdvisorInfoBox();

		pollQuestStates();

		// Before the engine's flush, so the re-plan it announces already knows a teleport
		// cast this tick is paid.
		farmingPlanner.watchTravel();
		// Likewise, so the re-plan waits while a seed is going into the ground.
		farmingPlanner.watchPlanting();
		// Batched: once per tick, not per event.
		stateEngine.flushIfDirty();
		// After the engine's flush, whose announcement re-plans too. This catches the
		// planner's own inputs and a run coming due with no game event at all.
		farmingPlanner.flushIfDirty();

		stateEngine.tickSave();

		LarryPanel shown = panel;
		if (shown != null)
		{
			shown.refresh();
		}
	}

	/** Quests starting or finishing this tick; the client posts no event for either. */
	private void pollQuestStates()
	{
		if (previousQuestStates.isEmpty())
		{
			return;
		}
		for (Quest quest : Quest.values())
		{
			QuestState currentState = questState(quest);
			if (currentState == null)
			{
				continue;
			}
			QuestState previousState = previousQuestStates.put(quest, currentState);
			if (previousState == null || previousState == currentState)
			{
				continue;
			}
			if (currentState == QuestState.FINISHED)
			{
				engine.onQuestProgress(quest.getName(), QuestStatus.COMPLETED, null);
			}
			else if (previousState == QuestState.NOT_STARTED && currentState == QuestState.IN_PROGRESS)
			{
				engine.onQuestProgress(quest.getName(), QuestStatus.IN_PROGRESS, null);
			}
		}
	}

	/**
	 * Per-tick farming patch sampling for the tracked regions the player stands in. A scan
	 * is only trusted once two consecutive ticks share a map region, because patch varbits
	 * arrive a tick after region entry.
	 */
	private void handleFarming()
	{
		// Patch varbits are only sent once the post-login welcome screen is dismissed, and
		// it does not register as modal. Nulling the location makes the same-region gate
		// demand two clean ticks after dismissal.
		if (isShown(InterfaceID.WelcomeScreen.MOTW))
		{
			lastFarmTickLocation = null;
			farmScanHold.clear();
			return;
		}

		WorldPoint current = playerLocation();
		WorldPoint previous = lastFarmTickLocation;
		lastFarmTickLocation = current;

		// Last tick's scan counts only if the player is still where it was read.
		if (current == null)
		{
			farmScanHold.clear();
		}
		else
		{
			for (FarmingScanHold.Scan scan : farmScanHold.release(farmRegionsAt(current)))
			{
				engine.updateFarming(scan.getRegion(), scan.getValues(), scan.isAllowOffsetLearning(), scan.getObservedAtEpochSec());
			}
			if (farmScanHold.wasDropped())
			{
				// The next scan applied follows the gap; forgetting the regions keeps it
				// from learning the offset.
				lastFarmRegions = new HashSet<>();
			}
		}

		if (previous == null || current == null || previous.getRegionID() != current.getRegionID())
		{
			return;
		}

		// Patch varbits are not sent while a modal widget is open, so a scan would read
		// stale values. Interfaces drawn over the inventory count too: the seed box, tool
		// leprechaun and bank pin all report MODAL_NOCLICKTHROUGH.
		for (WidgetNode widgetNode : client.getComponentTable())
		{
			if (widgetNode.getModalMode() != WidgetModalMode.NON_MODAL)
			{
				return;
			}
		}

		Set<FarmingWorldMap.RegionDef> regions = farmRegionsAt(previous);
		boolean regionChanged = !regions.equals(lastFarmRegions);
		lastFarmRegions = regions;
		if (regions.isEmpty())
		{
			return;
		}

		// The first scan after entering a region or closing a modal can land mid-way
		// through missed growth and would poison the learned farm-tick offset. Sample the
		// values but skip offset learning.
		boolean allowOffsetLearning = !regionChanged && client.getTickCount() - lastModalCloseTick > 1;

		long now = Instant.now().getEpochSecond();
		List<FarmingScanHold.Scan> scans = new ArrayList<>();
		for (FarmingWorldMap.RegionDef region : regions)
		{
			Map<Integer, Integer> values = new HashMap<>();
			for (FarmingWorldMap.PatchDef patch : region.getPatches())
			{
				values.put(patch.getVarbitId(), client.getVarbitValue(patch.getVarbitId()));
			}
			scans.add(new FarmingScanHold.Scan(region, values, allowOffsetLearning, now));
		}
		farmScanHold.hold(regions, scans);
	}

	/** The farming patch clusters whose bounds hold this point. */
	private static Set<FarmingWorldMap.RegionDef> farmRegionsAt(WorldPoint point)
	{
		Set<FarmingWorldMap.RegionDef> regions = new HashSet<>();
		for (FarmingWorldMap.RegionDef region : FarmingWorldMap.regionsAt(point.getRegionID()))
		{
			if (region.isInBounds(point.getX(), point.getY(), point.getPlane()))
			{
				regions.add(region);
			}
		}
		return regions;
	}

	/**
	 * Read the contracted crop from varbit 7963. It holds the contract from login, so the
	 * crop never depends on having seen Guildmaster Jane assign it.
	 *
	 * <p>A value the table does not know clears the crop. The crop held from before belongs
	 * to an earlier contract, and a wrong crop sends the player to the wrong patch.
	 */
	private void readContractCrop()
	{
		int value = client.getVarbitValue(VARBIT_FARMING_CONTRACT_TYPE);
		Crop crop = ContractProduce.byVarbitValue(value);
		if (crop == null && value != 0 && planner.getContractCrop() != null)
		{
			log.debug("Unrecognised farming contract varbit value: {}", value);
		}
		// The scheduler uses it to know which guild patch the contract sits in.
		planner.setContractCrop(crop);
	}

	/** Show the advisor box exactly while a run is due. */
	private void reconcileFarmingAdvisorInfoBox()
	{
		FarmingPlanner farmingPlanner = planner;
		// No player state means the account is being replaced, and the advice still
		// describes the one that is going.
		FarmingAdvice advice = engine.getPlayerState() != null ? farmingPlanner.getAdvice() : null;
		List<FarmingRunStatus> runs = advice != null ? advice.getRuns() : null;
		if (!config.showFarmingAdvisor() || !FarmingAdvisorInfoBox.anythingDue(runs))
		{
			removeFarmingAdvisorInfoBox();
			return;
		}

		int iconItemId = topDueIcon(runs);
		if (farmingAdvisorInfoBox == null)
		{
			advisorIconItemId = iconItemId;
			farmingAdvisorInfoBox = new FarmingAdvisorInfoBox(itemManager.getImage(iconItemId), this, () ->
				{
					FarmingAdvice current = farmingPlanner.getAdvice();
					return current != null ? current.getRuns() : null;
				});
			infoBoxManager.addInfoBox(farmingAdvisorInfoBox);
		}
		else if (iconItemId != advisorIconItemId)
		{
			advisorIconItemId = iconItemId;
			farmingAdvisorInfoBox.setImage(itemManager.getImage(iconItemId));
			infoBoxManager.updateInfoBoxImage(farmingAdvisorInfoBox);
		}
	}

	/** Icon of the first due run, falling back to a seed pack. */
	private static int topDueIcon(List<FarmingRunStatus> runs)
	{
		if (runs != null)
		{
			for (FarmingRunStatus run : runs)
			{
				if ("DUE".equals(run.getStatus()) && run.getIconItemId() > 0)
				{
					return run.getIconItemId();
				}
			}
		}
		return ADVISOR_FALLBACK_ICON_ITEM_ID;
	}

	private void removeFarmingAdvisorInfoBox()
	{
		if (farmingAdvisorInfoBox != null)
		{
			infoBoxManager.removeInfoBox(farmingAdvisorInfoBox);
			farmingAdvisorInfoBox = null;
			advisorIconItemId = 0;
		}
	}

	/** Right-clicking the advisor box opens the side panel that explains what it names. */
	@Subscribe
	public void onOverlayMenuClicked(OverlayMenuClicked event)
	{
		OverlayMenuEntry entry = event.getEntry();
		if (entry != null && navButton != null && FarmingAdvisorInfoBox.MENU_OPTION.equals(entry.getOption())
			&& FarmingAdvisorInfoBox.MENU_TARGET.equals(entry.getTarget()))
		{
			clientToolbar.openPanel(navButton);
		}
	}

	/**
	 * Commits a scan of the player's own house on the first tick after its "Loading house"
	 * interface closes, and notices the player leaving a house. See {@link HouseWatch}.
	 */
	private void handleHouse()
	{
		if (!houseWatch.wantsRegion())
		{
			return;
		}
		Player local = client.getLocalPlayer();
		boolean inHouse = local != null && HouseWatch.isHouseRegion(
			WorldPoint.fromLocalInstance(client, local.getLocalLocation()).getRegionID());
		// Whether the "Loading house" interface is still up, polled only while a house load
		// waits on it: the interface's absence settles it, so a close event the client never
		// fires cannot leave a load waiting.
		HouseWatch.Scan scan = houseWatch.tick(inHouse, houseWatch.awaitsInterfaceClose() && isShown(InterfaceID.PohLoading.UNIVERSE));
		FarmingPlanner farmingPlanner = planner;
		if (scan == null || farmingPlanner == null)
		{
			return;
		}
		// The building-mode varbit is logged only. Nothing here decides on it.
		log.debug("House scan committed: own house by {}, building mode varbit {}, found {}",
			scan.getSignal(), client.getVarbitValue(VarbitID.POH_BUILDING_MODE), scan.getFound());
		farmingPlanner.houseScanned(scan.getFound(), Instant.now().getEpochSecond());
	}

	/** Where the local player stands, or null with no local player. */
	private WorldPoint playerLocation()
	{
		Player local = client.getLocalPlayer();
		return local != null ? local.getWorldLocation() : null;
	}

	/** Whether the widget exists and is not hidden. */
	private boolean isShown(int componentId)
	{
		Widget widget = client.getWidget(componentId);
		return widget != null && !widget.isHidden();
	}

	private void handleRegionTracking()
	{
		WorldPoint at = playerLocation();
		if (at == null)
		{
			return;
		}
		int regionId = at.getRegionID();
		if (regionId != lastRegionId)
		{
			lastRegionId = regionId;
			engine.onRegionChanged(regionId);
		}
	}

	// ---------------------------------------------------------------
	// Vars
	// ---------------------------------------------------------------

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		StateEngine stateEngine = engine;
		FarmingPlanner farmingPlanner = planner;
		if (stateEngine == null || farmingPlanner == null || !loginReady)
		{
			return;
		}

		boolean read = false;
		int varbitId = event.getVarbitId();
		if (varbitId != -1)
		{
			int value = client.getVarbitValue(varbitId);
			if (WATCHED_VARBITS.contains(varbitId))
			{
				stateEngine.onVarbitValueChanged(varbitId, value);
				read = true;
			}

			String quest = QuestStages.BY_VARBIT.get(varbitId);
			if (quest != null)
			{
				stateEngine.onQuestProgress(quest, null, value);
				read = true;
			}

			// A spellbook preview is moot once the player actually switches books.
			if (varbitId == CapabilitySources.SPELLBOOK_VARBIT)
			{
				farmingPlanner.setSpellbookPreview(null);
			}

			// Re-read the pouch so pouched runes count toward rune counts.
			if (RunePouch.isContentVarbit(varbitId))
			{
				updateRunePouch();
				read = true;
			}

			// A saved Nexus slot taking a value proves that destination attuned, except in a
			// house not known to be the player's, whose own Nexus may be what wrote it.
			if (PortalNexus.isSlot(varbitId))
			{
				if (value != 0 && !houseWatch.isInOtherHouse())
				{
					farmingPlanner.nexusAttuned(Set.of(PortalNexus.destination(value)));
				}
				read = true;
			}
		}

		String quest = QuestStages.BY_VARP.get(event.getVarpId());
		if (quest != null)
		{
			stateEngine.onQuestProgress(quest, null, client.getVarpValue(event.getVarpId()));
			read = true;
		}

		// Patch varbits are region-relative and polled in handleFarming(). Any other var
		// changing still re-plans, so the plan's countdowns follow the game.
		if (!read)
		{
			stateEngine.markStale();
		}
	}

	/** Re-reads the rune pouch contents from its varbits. Client thread. */
	private void updateRunePouch()
	{
		EnumComposition runeEnum = client.getEnum(EnumID.RUNEPOUCH_RUNE);
		if (runeEnum == null)
		{
			return;
		}
		engine.onRunePouchChanged(RunePouch.read(client::getVarbitValue, runeEnum::getIntValue));
	}

	// ---------------------------------------------------------------
	// Containers
	// ---------------------------------------------------------------

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (engine == null || !loginReady)
		{
			return;
		}

		int containerId = event.getContainerId();
		Item[] items = event.getItemContainer().getItems();
		if (containerId == InventoryID.INV)
		{
			handleInventoryChanged(items);
		}
		else if (containerId == InventoryID.WORN)
		{
			handleEquipmentChanged(items);
		}
		else if (containerId == InventoryID.BANK)
		{
			readIfMoved(items, previousBankHash, h -> previousBankHash = h, engine::onBankChanged);
		}
		else if (containerId == InventoryID.SEED_VAULT)
		{
			readSeedVault(items, "its container change");
		}
		else if (containerId == InventoryID.SEED_BOX)
		{
			// Never observed to fire: the box is read from its Check listing in chat. If the
			// container is ever reported, its snapshot replaces the tally.
			readIfMoved(items, previousSeedBoxHash, h -> previousSeedBoxHash = h, engine::onSeedBoxChanged);
		}
	}

	/**
	 * Hands a container's contents to {@code read} if they moved since {@code previous}, the
	 * hash they had then, so a reopen with nothing moved is a no-op. The new hash is
	 * remembered before the read, so contents a read throws on are not read again until they
	 * move.
	 */
	private void readIfMoved(Item[] items, int previous, IntConsumer remember, Consumer<List<ItemStack>> read)
	{
		int hash = 1;
		for (Item item : items)
		{
			if (item.getId() != -1)
			{
				hash = 31 * hash + item.getId();
				hash = 31 * hash + item.getQuantity();
			}
		}
		if (hash != previous)
		{
			remember.accept(hash);
			read.accept(toItemStacks(items));
		}
	}

	/**
	 * Takes the vault's contents if they moved since the last read.
	 *
	 * @param source which path read them, for the debug log
	 */
	private void readSeedVault(Item[] items, String source)
	{
		readIfMoved(items, previousSeedVaultHash, h -> previousSeedVaultHash = h, stacks ->
		{
			log.debug("Seed vault read from {}: {} stacks", source, stacks.size());
			engine.onSeedVaultChanged(stacks);
		});
	}

	/**
	 * Reads the vault a full tick after its interface opened, empty or not. An empty vault
	 * has no container: the game sends none, so no container change fires. The tick's wait
	 * lets a filled vault's container arrive first, and a vault closed before then is left
	 * unread.
	 */
	private void checkOpenedSeedVault()
	{
		if (!isShown(InterfaceID.SeedVault.UNIVERSE))
		{
			log.debug("Seed vault closed within a tick of opening; left unread");
			return;
		}
		// A container the client already holds posts no change when the vault reopens
		// unchanged, so it is read here too. The hash drops a repeat.
		ItemContainer vault = client.getItemContainer(InventoryID.SEED_VAULT);
		if (vault != null)
		{
			log.debug("Seed vault opened holding {} stacks", vault.count());
			readSeedVault(vault.getItems(), "its open interface");
			return;
		}
		readSeedVault(new Item[0], "its open interface, which has no container");
	}

	private void handleInventoryChanged(Item[] items)
	{
		long[] currentSlots = new long[INVENTORY_SIZE];
		for (int i = 0; i < INVENTORY_SIZE; i++)
		{
			currentSlots[i] = i < items.length && items[i].getId() != -1
				? ((long) items[i].getId() << 32) | (items[i].getQuantity() & 0xFFFFFFFFL) : -1L;
		}
		if (previousInventorySlots != null && Arrays.equals(previousInventorySlots, currentSlots))
		{
			return;
		}
		previousInventorySlots = currentSlots;

		List<ItemStack> itemStacks = readInventory(items);

		// Before updating the inventory, so the bank updates in the same tick.
		if (depositBoxOpen)
		{
			inferDeposits(itemStacks);
		}

		engine.onInventoryChanged(itemStacks);
	}

	private void handleEquipmentChanged(Item[] items)
	{
		int[] currentIds = new int[EQUIPMENT_SLOT_INDICES.length];
		for (int i = 0; i < EQUIPMENT_SLOT_INDICES.length; i++)
		{
			int slotIdx = EQUIPMENT_SLOT_INDICES[i];
			currentIds[i] = slotIdx < items.length ? items[slotIdx].getId() : -1;
		}
		if (previousEquipmentIds != null && Arrays.equals(previousEquipmentIds, currentIds))
		{
			return;
		}
		previousEquipmentIds = currentIds;

		engine.onEquipmentChanged(readEquipment(items));
	}

	/** The first 28 slots, noted items resolved to their base, names learned for the seed box. */
	private List<ItemStack> readInventory(Item[] items)
	{
		return toItemStacks(Arrays.copyOf(items, Math.min(INVENTORY_SIZE, items.length)));
	}

	/** The worn items by slot, each named from its item composition. */
	private Map<EquipmentSlot, ItemStack> readEquipment(Item[] items)
	{
		Map<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
		for (int i = 0; i < EQUIPMENT_SLOTS.length; i++)
		{
			int slotIdx = EQUIPMENT_SLOT_INDICES[i];
			if (slotIdx < items.length && items[slotIdx].getId() != -1)
			{
				ItemComposition comp = itemManager.getItemComposition(items[slotIdx].getId());
				equipment.put(EQUIPMENT_SLOTS[i], new ItemStack(items[slotIdx].getId(), 1, comp.getName()));
			}
		}
		return equipment;
	}

	/**
	 * Occupied slots as item stacks, with noted items resolved to their unnoted form. Bank
	 * placeholders pass through as themselves with quantity 0, which counts as nothing.
	 */
	private List<ItemStack> toItemStacks(Item[] items)
	{
		List<ItemStack> itemStacks = new ArrayList<>();
		for (Item slot : items)
		{
			if (slot.getId() != -1)
			{
				CanonicalItem item = resolveCanonicalItem(slot.getId());
				itemStacks.add(new ItemStack(item.itemId, slot.getQuantity(), item.name));
				rememberItemId(itemIdsByName, item);
			}
		}
		return itemStacks;
	}

	/**
	 * Records an item's name against its id, so a later chat line naming it resolves to an
	 * id. See {@link #itemIdsByName}.
	 *
	 * <p>Placeholders are skipped. A bank placeholder reports the real item's name with its
	 * own id, which would overwrite a good entry with an id the player owns none of.
	 *
	 * @param known the session's name-to-id map
	 */
	static void rememberItemId(Map<String, Integer> known, CanonicalItem item)
	{
		if (item.placeholder || item.name == null || item.name.isEmpty())
		{
			return;
		}
		known.put(item.name.toLowerCase(Locale.ROOT), item.itemId);
	}

	/**
	 * Canonical identity of an item: its un-noted base ID and name.
	 *
	 * <p>Noted items have their own ids, and the planner matches items by exact base id, so
	 * every snapshot must collapse notes to their base before reaching the player state.
	 *
	 * <p>Bank placeholders are not collapsed: they pass through with quantity 0, which counts
	 * as nothing. They are flagged for {@link #rememberItemId}, which reads names, not
	 * quantities.
	 */
	static final class CanonicalItem
	{
		final int itemId;
		final String name;
		final boolean placeholder;

		CanonicalItem(int itemId, String name, boolean placeholder)
		{
			this.itemId = itemId;
			this.name = name;
			this.placeholder = placeholder;
		}
	}

	/**
	 * Resolves an item ID to its canonical (un-noted) base ID and name.
	 *
	 * <p>A bank placeholder is flagged, never resolved. Its id must reach the bank snapshot
	 * unchanged: the seed box and rune pouch are looked for in the bank by id regardless of
	 * quantity, so a resolved placeholder would read as a container the player still owns.
	 */
	private CanonicalItem resolveCanonicalItem(int itemId)
	{
		ItemComposition comp = itemManager.getItemComposition(itemId);
		if (comp.getNote() != -1)
		{
			ItemComposition linked = itemManager.getItemComposition(comp.getLinkedNoteId());
			return new CanonicalItem(linked.getId(), linked.getName(), false);
		}
		return new CanonicalItem(itemId, comp.getName(), comp.getPlaceholderTemplateId() != -1);
	}

	// ---------------------------------------------------------------
	// Interfaces and objects: the deposit box, the house, the Portal Nexus
	// ---------------------------------------------------------------

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		int group = event.getGroupId();
		if (group == InterfaceID.BANK_DEPOSITBOX)
		{
			depositBoxOpen = true;
			depositBoxInventorySnapshot = snapshotInventory();
		}
		else if (group == InterfaceID.POH_LOADING && engine != null && loginReady)
		{
			String decided = houseWatch.houseLoading(HouseWatch.isSoloIronman(client.getVarbitValue(VarbitID.IRONMAN)),
				client.getTickCount());
			log.debug("Loading a house: {}", decided);
		}
		else if (group == InterfaceID.TELENEXUS_TELEPORT)
		{
			readNexus();
		}
		else if (group == InterfaceID.SEED_VAULT)
		{
			seedVaultCheckIn = 2;
		}
	}

	/**
	 * Reads the whole Portal Nexus while its teleport menu is open in the player's own house,
	 * where a slot reading zero is a real empty slot. Anyone else's Nexus teaches nothing.
	 */
	private void readNexus()
	{
		FarmingPlanner farmingPlanner = planner;
		if (engine == null || farmingPlanner == null || !loginReady || !houseWatch.isInOwnHouse())
		{
			return;
		}
		Set<Integer> destinations = PortalNexus.fullRead(this::varbitOrZero);
		if (destinations == null)
		{
			log.debug("Portal Nexus menu opened with every slot reading zero; nothing learned");
			return;
		}
		log.debug("Portal Nexus read in full: destinations {}", destinations);
		farmingPlanner.nexusRead(destinations, Instant.now().getEpochSecond());
	}

	/** Every spawned object, for the house scan. Cheap while no scene is loading. */
	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		houseWatch.objectSpawned(event.getGameObject().getId());
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		// Farming offset learning must skip the tick after any modal closes: patch varbits
		// resume mid-tick and would read as a phantom growth tick.
		if (event.getModalMode() != WidgetModalMode.NON_MODAL)
		{
			lastModalCloseTick = client.getTickCount();
		}

		if (event.getGroupId() == InterfaceID.BANK_DEPOSITBOX)
		{
			depositBoxOpen = false;
			depositBoxInventorySnapshot = null;
		}
	}

	/**
	 * The current inventory by item id, for deposit inference. Noted items are resolved to
	 * their un-noted form, the one the bank holds.
	 */
	private Map<Integer, ItemStack> snapshotInventory()
	{
		ItemContainer container = client.getItemContainer(InventoryID.INV);
		return container == null ? new HashMap<>() : byItem(toItemStacks(container.getItems()));
	}

	/** Stacks of one item summed under its id, keeping its name. */
	private static Map<Integer, ItemStack> byItem(List<ItemStack> stacks)
	{
		Map<Integer, ItemStack> byId = new HashMap<>();
		for (ItemStack stack : stacks)
		{
			byId.merge(stack.getItemId(), stack, (a, b) -> new ItemStack(a.getItemId(), a.getQuantity() + b.getQuantity(), a.getName()));
		}
		return byId;
	}

	/**
	 * Infers bank deposits while the deposit box is open, by diffing the inventory against
	 * its snapshot from when the box opened or the previous inference.
	 */
	private void inferDeposits(List<ItemStack> currentInventory)
	{
		if (depositBoxInventorySnapshot == null)
		{
			return;
		}

		Map<Integer, ItemStack> current = byItem(currentInventory);

		// Items that decreased were deposited
		List<ItemStack> deposited = new ArrayList<>();
		for (ItemStack had : depositBoxInventorySnapshot.values())
		{
			ItemStack left = current.get(had.getItemId());
			int have = left == null ? 0 : left.getQuantity();
			if (have < had.getQuantity())
			{
				deposited.add(new ItemStack(had.getItemId(), had.getQuantity() - have, had.getName()));
			}
		}

		if (!deposited.isEmpty())
		{
			log.debug("Deposit box inference: {} item type(s) deposited", deposited.size());
			engine.onDepositInferred(deposited);
		}

		depositBoxInventorySnapshot = current;
	}

	// ---------------------------------------------------------------
	// Menu clicks: Ardougne cloak teleports and ways into a house
	// ---------------------------------------------------------------

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (engine == null || !loginReady)
		{
			return;
		}

		// Whose house a click leads into. The "Loading house" interface spends it.
		houseWatch.entryClicked(HouseEntry.of(clickKind(event.getMenuAction()),
			event.getMenuOption(), event.getMenuTarget(), dialogLine(event)), client.getTickCount());

		// Only armed here. The tick handler confirms whether the teleport happened.
		if (ArdougneFarmTeleportWatch.isFarmTeleportOption(event.getMenuOption())
			&& ArdougneFarmTeleportWatch.isCloak(clickedItemId(event)))
		{
			WorldPoint from = playerLocation();
			if (from != null)
			{
				ardougneFarmTeleport.clicked(client.getTickCount(), from.getX(), from.getY());
			}
		}
	}

	/** The kind of menu entry a click was, as {@link HouseEntry} tells house entries apart. */
	private static HouseEntry.Click clickKind(MenuAction action)
	{
		if (action == null)
		{
			return HouseEntry.Click.OTHER;
		}
		switch (action)
		{
			case GAME_OBJECT_FIRST_OPTION:
			case GAME_OBJECT_SECOND_OPTION:
			case GAME_OBJECT_THIRD_OPTION:
			case GAME_OBJECT_FOURTH_OPTION:
			case GAME_OBJECT_FIFTH_OPTION:
				return HouseEntry.Click.OBJECT;
			case WIDGET_CONTINUE:
				return HouseEntry.Click.DIALOG;
			default:
				return HouseEntry.Click.OTHER;
		}
	}

	/** The clicked dialog option's line, or null when the click was no dialog option. */
	private static String dialogLine(MenuOptionClicked event)
	{
		if (event.getMenuAction() != MenuAction.WIDGET_CONTINUE)
		{
			return null;
		}
		Widget widget = event.getWidget();
		return widget != null ? widget.getText() : null;
	}

	/**
	 * The item a menu click was made on, whether it is being carried or worn.
	 *
	 * <p>{@code MenuOptionClicked.getItemId()} answers only for inventory item ops. A worn
	 * item's op carries no item id, so it is read off the equipment widget, as RuneLite's
	 * {@code MenuEntrySwapperPlugin} does. Most cloak teleports are made from the worn slot.
	 *
	 * @return the item id, or -1 when the click was not on an item
	 */
	private static int clickedItemId(MenuOptionClicked event)
	{
		int itemId = event.getItemId();
		if (itemId != -1)
		{
			return itemId;
		}
		Widget widget = event.getMenuEntry().getWidget();
		if (widget == null || WidgetUtil.componentToInterface(widget.getId()) != InterfaceID.WORNITEMS)
		{
			return -1;
		}
		// The slot widget holds the item in child 1.
		Widget child = widget.getChild(1);
		return child != null ? child.getItemId() : -1;
	}

	/**
	 * Charges the day's allowance when an armed Ardougne cloak teleport lands. See
	 * {@link ArdougneFarmTeleportWatch}.
	 */
	private void handleArdougneFarmTeleport()
	{
		if (!ardougneFarmTeleport.isArmed())
		{
			return;
		}
		WorldPoint at = playerLocation();
		if (at != null && ardougneFarmTeleport.arrived(client.getTickCount(), at.getX(), at.getY()))
		{
			planner.recordArdougneFarmTeleport();
		}
	}
}
