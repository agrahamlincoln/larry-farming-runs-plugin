package com.larryfarming.panel;

import com.larryfarming.snapshot.FarmingAdvice;
import com.larryfarming.snapshot.FarmingCarryItem;
import com.larryfarming.snapshot.FarmingDurations;
import com.larryfarming.snapshot.FarmingPatchNote;
import com.larryfarming.snapshot.FarmingPlantAdvice;
import com.larryfarming.snapshot.FarmingRunStatus;
import com.larryfarming.snapshot.FarmingSettingChoice;
import com.larryfarming.snapshot.FarmingSettingPick;
import com.larryfarming.snapshot.FarmingSkipReason;
import com.larryfarming.snapshot.FarmingStopAdvice;
import com.larryfarming.snapshot.FarmingUpgrade;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.border.Border;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Walks the player through a farming run: every stop in the run's order, what to take,
 * and what to wear.
 *
 * <p>Read at a glance, never driven: the top of the panel follows the run on its own. On a
 * day with nothing due it shows when each run is ready; before setting off, the packing
 * list; once underway, the next stop. Set-and-forget controls sit in a shut fold at the
 * bottom.
 *
 * <p>Rows are an icon beside wrapping text on a GridBag, so text wraps to the sidebar width
 * instead of running off the edge.
 *
 * <p>Holds no reasoning of its own. The advisor decides teleports, seeds and gear before
 * the advice reaches this panel.
 */
public class FarmingPanel extends JPanel
{
	private static final Color COLOR_SKIPPED = ColorScheme.MEDIUM_GRAY_COLOR;
	private static final Color COLOR_UNCERTAIN = ColorScheme.BRAND_ORANGE;
	private static final Color COLOR_SHORTFALL = ColorScheme.PROGRESS_ERROR_COLOR;
	private static final Color COLOR_ROUTE = ColorScheme.LIGHT_GRAY_COLOR;
	/**
	 * The teleport icon size on a route line. The game draws spellbook sprites at 20px, and
	 * rescaling one blurs it (see {@link IconProvider#fitTo}).
	 */
	private static final int TRAVEL_ICON = 20;
	/** The game's item sprite width, used for a stop's crop icon. */
	private static final int ITEM_SPRITE_WIDTH = 36;
	/**
	 * Amber for every do-this line on a stop, matching the advisor infobox when a run is due.
	 * Not green, because green ({@code PROGRESS_COMPLETE_COLOR}) means "done" elsewhere in the
	 * panel.
	 */
	static final Color COLOR_DUE = new Color(255, 200, 50);

	/**
	 * The packing list's heading, shown only before the trip. Partway round, the bank row
	 * "Bank first" lists what is still missing, and "Next time you bank" what to put back,
	 * instead.
	 */
	private static final String CARRY_HEADING = "Take with you";

	/**
	 * The mood's tooltip while nothing is due and some run counts down, from the book On
	 * Leprechauns in the Prifddinas Grand Library. The tab's one piece of lore.
	 */
	static final String HUNGRY_PATIENCE = "They watch. They wait. But theirs is a hungry patience.";

	/** {@code RunStatus} names, as the runs list carries them. */
	private static final String STATUS_DUE = "DUE";
	private static final String STATUS_PENDING = "PENDING";

	/** Appended to the settings header while no fairy ring can be reached. */
	private static final String SETTINGS_NO_FAIRY_RING = ": no fairy ring access";

	private final JLabel titleLabel;
	/** Larry's face, name and the plugin version, at the top logged in or out. */
	private final BrandHeader brandHeader;
	/** The whole tab while no account is logged in. */
	private final LoggedOutCard loggedOutCard;
	private final SpellbookPicker spellbookPicker;
	private final JCheckBox showSkippedBox;
	private final JCheckBox showBanksBox;
	private final JTextArea respawnNote;
	private final JTextArea herbNote;

	/** When the next run is ready, on a day with nothing due. Re-worded in place. */
	private final JLabel nextRunLabel;

	/** The packing list's heading, with the spellbook chips at its right before the trip. */
	private final JPanel carryRow;
	private final JLabel carryHeading;
	/** The stores Larry has never looked in, above the grid they could change. */
	private final JTextArea lookOnceNote;
	/** The same line on a day with nothing due, above the runs it could bring due. */
	private final JTextArea lookOnceIdleNote;
	private final CarryView carryView;
	/** Under the packing grid: a band per later bank, over what that bank hands over. */
	private final JPanel laterContainer;

	/** Set for the length of a structural rebuild: whether the Later bands are on screen. */
	private boolean laterBandsDrawn;

	/** Set for the length of a structural rebuild: whether the player is partway round. */
	private boolean drawingUnderway;
	private final JLabel wearHeading;
	private final GearList wearList;

	/** The stop the player is heading for, drawn as a highlighted card above the rest. */
	private final JLabel nowHeading;
	private final JPanel nowContainer;
	private final JLabel stopsHeading;
	private final JPanel stopsContainer;

	/** Gear worth earning, shown only between runs, when there is time to act on it. */
	private final JLabel goalsHeading;
	private final GearList goalsList;

	/**
	 * The runs list: the tab's main content on a day with nothing due, and inside the
	 * settings fold otherwise. Moved between {@link #idleRunsSlot} and the fold.
	 */
	private final RunsListView runsList;
	private final JPanel idleRunsSlot;

	/**
	 * The PLANNING group under the runs list: which run goes first when herbs and hardwood
	 * fall due together, and whether a run waits until every patch on it needs a visit.
	 * It stays in the settings fold when the list leaves it on a day with nothing due.
	 */
	private final JPanel planning;
	private final JLabel runFirstCaption;
	private final ChoiceChips runFirstChips;
	private final JCheckBox waitBox;
	private String waitKey;

	/** Heads the two route switches, shown while either is. */
	private final JLabel routeHeading;

	private BiConsumer<String, String> settingCallback;

	/**
	 * Set-and-forget controls: the runs, their crops, their order and when they fall due,
	 * the route's two filters, the travel methods and the player's house.
	 */
	private final Fold settingsFold;
	private final TravelFold travel;

	/** What the player's house holds, seen or declared, after the travel methods. */
	private final HouseFold house;

	/** Whether the settings header carries the fairy-ring warning. */
	private boolean settingsWarned;

	/** The settings profile control, first in the settings fold. */
	private final ProfilePicker profilePicker = new ProfilePicker();

	/** The active settings profile's name, or null before the plugin has shown any. */
	private String settingsProfile;

	/** Whether the live settings differ from the active profile's saved ones. */
	private boolean settingsUnsaved;

	/** The settings header's suffix as drawn, so it is re-set only on a change. */
	private String settingsSuffix = "";

	/**
	 * The player's choice to draw skipped stops. Off by default because skipped stops
	 * outnumber the ones being walked. A day with nothing due follows it too: the runs list
	 * already says when each run is ready.
	 */
	private boolean showSkipped;

	private java.util.function.Consumer<Boolean> showSkippedCallback;

	/**
	 * Whether the banks the route passes are drawn. Separate from {@link #showSkipped}
	 * because an offered bank is available, not skipped. On by default: the rows are
	 * unnumbered and outside the stop count.
	 */
	private boolean showBanks = true;

	private java.util.function.Consumer<Boolean> showBanksCallback;

	/**
	 * The advice the rows on screen were built from, so a fold toggle can redraw at once
	 * instead of waiting for the next game event. The fold flags are in the guard's
	 * signature, so re-running {@link #display} rebuilds.
	 */
	private FarmingAdvice lastAdvice;

	private IconProvider lastIcons;

	private final RebuildGuard guard = new RebuildGuard();

	/**
	 * Updaters that re-word each countdown label in place.
	 *
	 * <p>Countdown text must stay out of the guard's signature, or the tab would rebuild and
	 * flicker every minute. The signature holds the finish instant instead.
	 *
	 * <p>Rebuilt with the rows, so a closure never outlives its label.
	 */
	private final List<Runnable> countdowns = new ArrayList<>();

	public FarmingPanel()
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		titleLabel = new JLabel();
		titleLabel.setFont(FontManager.getRunescapeBoldFont());
		titleLabel.setForeground(Color.WHITE);
		titleLabel.setAlignmentX(LEFT_ALIGNMENT);

		brandHeader = new BrandHeader();
		loggedOutCard = new LoggedOutCard();
		loggedOutCard.setVisible(false);

		spellbookPicker = new SpellbookPicker();

		showSkippedBox = checkbox(null);
		showSkippedBox.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
		showSkippedBox.setVisible(false);
		// ActionListener, not ItemListener: setSelected() fires an ItemEvent, so an
		// ItemListener would treat restoring the saved value as a click and re-save it.
		showSkippedBox.addActionListener(e -> toggleShowSkipped(showSkippedBox.isSelected()));

		showBanksBox = checkbox(null);
		showBanksBox.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
		showBanksBox.setVisible(false);
		// Sync the box to showBanks, which starts true while a JCheckBox starts unselected.
		showBanksBox.setSelected(showBanks);
		showBanksBox.addActionListener(e -> toggleShowBanks(showBanksBox.isSelected()));

		respawnNote = PanelText.textArea("", COLOR_UNCERTAIN,
			FontManager.getRunescapeSmallFont());
		respawnNote.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

		herbNote = PanelText.textArea("", COLOR_UNCERTAIN,
			FontManager.getRunescapeSmallFont());
		herbNote.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

		nextRunLabel = new JLabel();
		nextRunLabel.setFont(FontManager.getRunescapeFont());
		nextRunLabel.setForeground(Color.WHITE);
		nextRunLabel.setAlignmentX(LEFT_ALIGNMENT);
		nextRunLabel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

		// Each heading carries the gap above its section, so hiding a section hides its
		// spacing too.
		carryHeading = sectionHeading(CARRY_HEADING);
		carryRow = new JPanel(new BorderLayout());
		carryRow.setOpaque(false);
		carryRow.setAlignmentX(LEFT_ALIGNMENT);
		carryRow.add(carryHeading, BorderLayout.WEST);
		// Bottom-aligned with the heading's text, which sits on the heading's bottom inset.
		JPanel pickerSlot = new JPanel(new BorderLayout());
		pickerSlot.setOpaque(false);
		pickerSlot.setBorder(BorderFactory.createEmptyBorder(6, 0, 2, 0));
		pickerSlot.add(spellbookPicker, BorderLayout.SOUTH);
		carryRow.add(pickerSlot, BorderLayout.EAST);
		lookOnceNote = lookOnceLine(BorderFactory.createEmptyBorder(0, 2, 4, 0));
		// With no seed known, nothing comes due, so a first login opens here.
		lookOnceIdleNote = lookOnceLine(BorderFactory.createEmptyBorder(8, 2, 0, 0));
		carryView = new CarryView();
		laterContainer = new JPanel();
		laterContainer.setLayout(new BoxLayout(laterContainer, BoxLayout.Y_AXIS));
		laterContainer.setOpaque(false);
		laterContainer.setAlignmentX(LEFT_ALIGNMENT);
		wearHeading = sectionHeading("Wear");
		wearList = new GearList(FarmingUpgrade.Kind.EQUIP);

		nowHeading = sectionHeading("First stop");
		nowContainer = new JPanel();
		nowContainer.setLayout(new BoxLayout(nowContainer, BoxLayout.Y_AXIS));
		nowContainer.setOpaque(false);
		nowContainer.setAlignmentX(LEFT_ALIGNMENT);
		stopsHeading = sectionHeading("Then");
		stopsContainer = container();

		goalsHeading = sectionHeading("Between runs");
		goalsList = new GearList(FarmingUpgrade.Kind.GOAL);

		runsList = new RunsListView(sectionHeading("Runs"), container());
		idleRunsSlot = new JPanel();
		idleRunsSlot.setLayout(new BoxLayout(idleRunsSlot, BoxLayout.Y_AXIS));
		idleRunsSlot.setOpaque(false);
		idleRunsSlot.setAlignmentX(LEFT_ALIGNMENT);

		runFirstCaption = SiteList.caption("", 0);
		runFirstChips = new ChoiceChips(this::sendSetting);
		runFirstChips.setAlignmentX(LEFT_ALIGNMENT);
		runFirstChips.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));

		waitBox = checkbox(null);
		waitBox.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
		waitBox.addActionListener(e -> sendSetting(waitKey,
			String.valueOf(waitBox.isSelected())));

		planning = new JPanel();
		planning.setLayout(new BoxLayout(planning, BoxLayout.Y_AXIS));
		planning.setOpaque(false);
		planning.setAlignmentX(LEFT_ALIGNMENT);
		planning.add(sectionHeading("Planning"));
		planning.add(runFirstCaption);
		planning.add(runFirstChips);
		planning.add(waitBox);
		planning.setVisible(false);

		routeHeading = sectionHeading("Show on the route");
		routeHeading.setVisible(false);

		settingsFold = new Fold("Settings");
		settingsFold.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
		settingsFold.content().add(profilePicker);
		settingsFold.content().add(runsList);
		settingsFold.content().add(planning);
		settingsFold.content().add(routeHeading);
		settingsFold.content().add(showSkippedBox);
		settingsFold.content().add(showBanksBox);
		travel = new TravelFold();
		settingsFold.content().add(travel);
		house = new HouseFold();
		settingsFold.content().add(house);

		add(brandHeader);
		add(titleLabel);
		add(loggedOutCard);
		add(respawnNote);
		add(herbNote);
		add(nextRunLabel);
		add(lookOnceIdleNote);
		add(idleRunsSlot);
		add(carryRow);
		add(lookOnceNote);
		add(carryView);
		add(wearHeading);
		add(wearList);
		add(laterContainer);
		add(nowHeading);
		add(nowContainer);
		add(stopsHeading);
		add(stopsContainer);
		add(goalsHeading);
		add(goalsList);
		add(settingsFold);
	}

	/** Puts the remembered settings-fold state back on screen, without reporting a change. */
	public void setSettingsOpen(boolean open)
	{
		settingsFold.setOpen(open);
	}

	/** Receives the settings fold's new state when the player opens or shuts it. */
	public void setSettingsOpenCallback(java.util.function.Consumer<Boolean> callback)
	{
		settingsFold.setOpenCallback(callback);
	}

	/**
	 * Restores the saved skipped-stops setting without reporting it as a change. The plugin
	 * owns persistence; changes leave through {@link #setShowSkippedCallback}.
	 */
	public void setShowSkipped(boolean show)
	{
		this.showSkipped = show;
		showSkippedBox.setSelected(show);
	}

	/**
	 * Receives the new skipped-stops setting when the player clicks the box. Never fired by
	 * {@link #setShowSkipped}, so a restore does not write back.
	 */
	public void setShowSkippedCallback(java.util.function.Consumer<Boolean> callback)
	{
		this.showSkippedCallback = callback;
	}

	/**
	 * Applies a click on the skipped-stops box, reports it, and redraws at once rather than
	 * waiting for a snapshot that may not come while the player is idle or logged out.
	 */
	private void toggleShowSkipped(boolean show)
	{
		this.showSkipped = show;
		if (showSkippedCallback != null)
		{
			showSkippedCallback.accept(show);
		}
		display(lastAdvice, lastIcons);
	}

	/** Puts the remembered bank-row setting back on screen, without reporting a change. */
	public void setShowBanks(boolean show)
	{
		this.showBanks = show;
		showBanksBox.setSelected(show);
	}

	/** Receives the new bank-row setting when the player clicks the box. */
	public void setShowBanksCallback(java.util.function.Consumer<Boolean> callback)
	{
		this.showBanksCallback = callback;
	}

	private void toggleShowBanks(boolean show)
	{
		this.showBanks = show;
		if (showBanksCallback != null)
		{
			showBanksCallback.accept(show);
		}
		display(lastAdvice, lastIcons);
	}

	/**
	 * Receives a farming setting the player changed on the tab, as its config key and the
	 * value to write. The plugin saves it, and the re-plan that follows redraws the tab.
	 */
	public void setSettingCallback(BiConsumer<String, String> callback)
	{
		settingCallback = callback;
		runsList.setSettingCallback(callback);
		travel.setSettingCallback(callback);
		house.setSettingCallback(callback);
	}

	/** The wait-for-every-patch checkbox, which sits above the fold switches. */
	JCheckBox waitBox()
	{
		return waitBox;
	}

	private void sendSetting(String key, String value)
	{
		if (settingCallback != null && key != null)
		{
			settingCallback.accept(key, value);
		}
	}

	/**
	 * Shows the settings that order and pace every run. Each sits on a long-lived control
	 * and is set in place, so calling this on every refresh cannot flicker.
	 */
	private void showRunPacing(FarmingAdvice advice)
	{
		FarmingSettingPick runFirst = advice.getRunFirst();
		if (runFirst != null)
		{
			runFirstCaption.setText(runFirst.getLabel());
			runFirstChips.show(runFirst);
		}
		runFirstCaption.setVisible(runFirst != null);
		runFirstChips.setVisible(runFirst != null);
		FarmingSettingChoice wait = advice.getWaitForEveryPatch();
		if (wait != null)
		{
			waitKey = wait.getKey();
			waitBox.setText(wait.getLabel());
			waitBox.setSelected(wait.isOn());
		}
		waitBox.setVisible(wait != null);
		planning.setVisible(runFirst != null || wait != null);
	}

	/** Puts the remembered travel fold state back on screen, without reporting a change. */
	public void setTravelOpen(boolean open)
	{
		travel.setOpen(open);
	}

	/** Receives the travel fold's new state when the player opens or shuts it. */
	public void setTravelOpenCallback(java.util.function.Consumer<Boolean> callback)
	{
		travel.setOpenCallback(callback);
	}

	/** Puts the remembered house fold state back on screen, without reporting a change. */
	public void setHouseOpen(boolean open)
	{
		house.setOpen(open);
	}

	/** Receives the house fold's new state when the player opens or shuts it. */
	public void setHouseOpenCallback(java.util.function.Consumer<Boolean> callback)
	{
		house.setOpenCallback(callback);
	}

	/** The tab's one checkbox style, for the fold boxes and the farming settings alike. */
	static JCheckBox checkbox(String text)
	{
		JCheckBox box = new JCheckBox(text);
		box.setFont(FontManager.getRunescapeSmallFont());
		box.setForeground(PanelColors.HEADER);
		box.setOpaque(false);
		box.setFocusPainted(false);
		box.setAlignmentX(LEFT_ALIGNMENT);
		box.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		return box;
	}

	/**
	 * Forces the next {@link #display} to rebuild even if the advice is unchanged.
	 *
	 * <p>Call when the tab is switched to: its rows wrapped against a width from when it was
	 * last visible, and the guard's signature cannot see a layout change.
	 */
	public void forgetLastBuild()
	{
		guard.reset();
	}

	/**
	 * Receives a request to plan for a different spellbook, as the book's name, or null to
	 * follow the player's own.
	 */
	public void setSpellbookCallback(java.util.function.Consumer<String> callback)
	{
		spellbookPicker.setSpellbookCallback(callback);
	}

	/**
	 * Shows a run, rebuilding only when the advice itself has changed.
	 *
	 * <p>Called on every game event. The rows hold wrapping text, and recreating it on each
	 * call would flicker the tab.
	 */
	public void display(FarmingAdvice advice, IconProvider icons)
	{
		lastAdvice = advice;
		lastIcons = icons;

		if (advice == null)
		{
			// reset() rather than a sentinel signature: the containers are emptied out of
			// band here, so a stale signature must not later read as "unchanged".
			guard.reset();
			countdowns.clear();
			nowContainer.removeAll();
			stopsContainer.removeAll();
			runsList.clear();
			carryView.rebuild(null, icons, 0);
			laterContainer.removeAll();
			wearList.rebuild(null, icons, 0);
			goalsList.rebuild(null, icons, 0);
			titleLabel.setVisible(false);
			loggedOutCard.setVisible(true);
			brandHeader.showMood(BrandHeader.DESCRIPTOR, null);
			showSections(Phase.NONE, false, false, false, false);
			revalidate();
			repaint();
			return;
		}

		int visits = visitedStops(advice);
		Phase phase = visits == 0 ? Phase.IDLE
			: advice.isUnderway() ? Phase.UNDERWAY : Phase.PACKING;
		// Underway, every row before the next stop is behind the player: a finished stop (it
		// turned into a skip when planted) or a bank already passed. Those rows are never
		// drawn, so the switches' counts start where the drawing does.
		int from = phase == Phase.UNDERWAY ? nextStopIndex(advice) : 0;
		// Counted from the rows so the fold label matches exactly what the fold hides: every
		// arrival that is not a VISIT.
		int foldedStops = foldedStops(advice, from);
		int foldedPatches = foldedPatches(advice);

		boolean drawSkipped = showSkipped;
		boolean drawBanks = showBanks;

		// Live values, set before the guard on every refresh. Each sits on a long-lived,
		// non-wrapping component, so re-wording it cannot flicker. Keep them out of the
		// signature, or every change would tear down the wrapping rows below.
		// A day with nothing due has no run to name, and the header above already says Larry.
		titleLabel.setText(phase == Phase.IDLE ? "" : advice.getRunName());
		titleLabel.setVisible(phase != Phase.IDLE);
		loggedOutCard.setVisible(false);
		boolean idle = phase == Phase.IDLE;
		boolean underway = phase == Phase.UNDERWAY;
		boolean pending = anyRunIn(advice.getRuns(), STATUS_PENDING);
		boolean due = anyRunIn(advice.getRuns(), STATUS_DUE);
		brandHeader.showMood(mood(idle, underway, pending || due),
			idle && pending && !due ? HUNGRY_PATIENCE : null);
		// The picker keeps its own change check, so calling it every refresh is cheap.
		spellbookPicker.rebuild(advice.getCarryFor(), advice.getSpellbookOpen(), icons);
		String nextRun = nextRunText(advice.getRuns(), Instant.now().getEpochSecond());
		nextRunLabel.setText(nextRun);

		showSkippedBox.setText(foldLabel(foldedStops, foldedPatches));
		int banks = bankRowsIn(advice, from);
		showBanksBox.setText(banks == 1 ? "1 bank you may pass"
			: banks + " banks you may pass");

		String respawn = advice.getRespawnAction();
		boolean suggesting = respawn != null && !respawn.isBlank();
		if (suggesting)
		{
			respawnNote.setText(respawn);
		}

		String herbs = advice.getHerbNote();
		boolean untickedSeeds = herbs != null && !herbs.isBlank();
		if (untickedSeeds)
		{
			herbNote.setText(herbs);
		}

		String look = advice.getLookOnce();
		boolean unlooked = look != null && !look.isBlank();
		if (unlooked && !look.equals(lookOnceNote.getText()))
		{
			lookOnceNote.setText(look);
			lookOnceIdleNote.setText(look);
		}

		// Visibility changes relayout the parent, but the guard's early return skips
		// revalidate(). Sections and switches are safe only while every input deciding their
		// visibility (the phase, outcomes, kinds, each stop's set-aside list) is also in the
		// signature. The notes and the next-run line are live values on long-lived
		// components, deliberately outside it.
		showSections(phase, suggesting, untickedSeeds, unlooked, !nextRun.isEmpty());
		showSkippedBox.setVisible(foldedStops > 0 || foldedPatches > 0);
		showBanksBox.setVisible(visits > 0 && banks > 0);
		routeHeading.setVisible(showSkippedBox.isVisible() || showBanksBox.isVisible());
		placeRunsList(phase);
		showRunPacing(advice);
		travel.show(advice, icons);
		house.show(advice);
		// Mirrors the travel fold's own warning, which it shows only while it has squares.
		showSettingsWarning(travel.isVisible() && advice.isFairyRingBlocked());

		if (!guard.shouldRebuild(signatureOf(advice, phase, drawSkipped, drawBanks)))
		{
			// Same rows: only re-word the countdowns in place.
			for (Runnable countdown : countdowns)
			{
				countdown.run();
			}
			runsList.showDetails(advice.getRuns());
			return;
		}

		// The rows wrap against the tab's inner width less the route box's border. Read off
		// the tab, since a box hidden until now reports no width of its own.
		int width = Math.max(0, getWidth() - getInsets().left - getInsets().right - 2);

		countdowns.clear();
		nowContainer.removeAll();
		stopsContainer.removeAll();
		drawingUnderway = phase == Phase.UNDERWAY;
		laterBandsDrawn = phase == Phase.PACKING && !carryNow(advice).isEmpty();
		boolean haveNow = false;
		int number = 0;
		boolean divider = false;
		List<FarmingStopAdvice> stops = advice.getStops();
		for (int index = 0; index < stops.size(); index++)
		{
			FarmingStopAdvice stop = stops.get(index);
			// Every VISIT is numbered, extras included, except unnumbered bank rows. Numbered
			// before the fold, so folded skips leave no gaps.
			//
			// Numbering and drawing are separate decisions. Optional banks are unnumbered but
			// follow the bank switch, not the skipped-stops fold.
			boolean numbered = stop.getOutcome() == FarmingStopAdvice.Outcome.VISIT
				&& !isUnnumberedBankRow(stop);
			if (numbered)
			{
				number++;
			}
			// The starting bank row stays: it names what to fetch at the next bank opened.
			if (index < from && stop.getKind() != FarmingStopAdvice.Kind.BANK_START)
			{
				continue;
			}
			if (stop.getKind() == FarmingStopAdvice.Kind.BANK_OPTIONAL)
			{
				if (!drawBanks)
				{
					continue;
				}
			}
			else if (stop.getKind() == FarmingStopAdvice.Kind.BANK_START)
			{
				// Before the trip, the packing grid above already lists what to withdraw.
				if (phase == Phase.PACKING)
				{
					continue;
				}
			}
			else if (stop.getOutcome() != FarmingStopAdvice.Outcome.VISIT && !drawSkipped)
			{
				continue;
			}
			JPanel row = buildStopRow(stop, number, icons, drawSkipped);
			// Partway round, a starting row with something to fetch is fetched before the next
			// stop, so it sits above that stop's card rather than among the stops after it.
			if (stop.getKind() == FarmingStopAdvice.Kind.BANK_START && !haveNow
				&& !stop.getWithdraw().isEmpty())
			{
				nowContainer.add(sized(row, width));
				continue;
			}
			// The first numbered stop is where the player is heading: finished stops turn
			// into skips, so the card moves down the route on its own.
			if (numbered && !haveNow)
			{
				// The card has no box around it, so it takes the border's two pixels back. Not
				// before layout: sized() would wrap the card against a 2px width.
				nowContainer.add(sized(asNowCard(row), width > 0 ? width + 2 : 0));
				haveNow = true;
				continue;
			}
			if (divider)
			{
				stopsContainer.add(PanelColors.divider());
			}
			stopsContainer.add(sized(row, width));
			divider = true;
		}
		nowHeading.setText(sectionLabel(phase == Phase.UNDERWAY ? "Next stop" : "First stop"));
		boolean haveBankFirst = nowContainer.getComponentCount() > (haveNow ? 1 : 0);
		nowHeading.setVisible(haveNow || haveBankFirst);
		nowContainer.setVisible(haveNow || haveBankFirst);
		stopsHeading.setText(sectionLabel(haveNow ? "Then" : "Route"));
		stopsHeading.setVisible(divider);
		stopsContainer.setVisible(divider);

		List<FarmingCarryItem> carry = advice.getCarry();
		// Split at the first later bank: what to pack now, then what each bank hands over.
		boolean split = !carryNow(advice).isEmpty();
		carryView.rebuild(phase == Phase.PACKING ? (split ? carryNow(advice) : carry) : null,
			icons, width);
		carryHeading.setVisible(carry != null && !carry.isEmpty());
		rebuildLater(phase == Phase.PACKING && split ? stops : null, drawBanks, icons, width);

		boolean wear = wearList.rebuild(phase == Phase.PACKING ? advice.getUpgrades() : null,
			icons, width);
		wearHeading.setVisible(wear);
		boolean goals = goalsList.rebuild(phase == Phase.IDLE ? advice.getUpgrades() : null,
			icons, width);
		goalsHeading.setVisible(goals);

		// Inside the settings fold the list sits behind the fold content's left inset.
		int runsWidth = phase == Phase.IDLE || width == 0 ? width
			: Math.max(0, width - settingsFold.content().getInsets().left);
		runsList.rebuild(advice, icons, runsWidth);

		revalidate();
		repaint();
	}

	/**
	 * A band and a grid for every later bank that hands something over, in walk order. An
	 * optional bank counts only while the bank switch draws it.
	 *
	 * @param stops the walkthrough, or null to clear the bands
	 */
	private void rebuildLater(List<FarmingStopAdvice> stops, boolean drawBanks,
		IconProvider icons, int width)
	{
		laterContainer.removeAll();
		boolean any = false;
		if (stops != null)
		{
			for (FarmingStopAdvice stop : stops)
			{
				boolean later = stop.getKind() == FarmingStopAdvice.Kind.BANK
					|| (drawBanks && stop.getKind() == FarmingStopAdvice.Kind.BANK_OPTIONAL);
				if (!later || stop.getWithdraw().isEmpty())
				{
					continue;
				}
				laterContainer.add(laterBand(stop.getPatchName()));
				// The bank row's own grid, so a band and its row below cannot drift apart.
				JPanel grid = new JPanel();
				grid.setLayout(new BoxLayout(grid, BoxLayout.Y_AXIS));
				grid.setOpaque(false);
				grid.setAlignmentX(LEFT_ALIGNMENT);
				addWithdrawals(grid, stop, icons, false);
				laterContainer.add(grid);
				any = true;
			}
		}
		laterContainer.setVisible(any);
	}

	/** The packing list's "now" half, empty when no later bank splits the trip. */
	private static List<FarmingCarryItem> carryNow(FarmingAdvice advice)
	{
		return advice.getCarryNow() == null ? List.of() : advice.getCarryNow();
	}

	/** "LATER: BANK AT CATHERBY" on a filled strip, so the band reads apart from the bag. */
	private static JPanel laterBand(String bank)
	{
		JLabel label = new JLabel(sectionLabel("Later: " + bank));
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(PanelColors.META);
		JPanel strip = new JPanel(new BorderLayout());
		strip.setBackground(PanelColors.CONTAINER_BG);
		strip.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
		strip.add(label);
		JPanel band = new JPanel(new BorderLayout());
		band.setOpaque(false);
		band.setAlignmentX(LEFT_ALIGNMENT);
		band.setBorder(BorderFactory.createEmptyBorder(8, 0, 2, 0));
		band.add(strip);
		band.setMaximumSize(new Dimension(Integer.MAX_VALUE, band.getPreferredSize().height));
		return band;
	}

	/**
	 * Where the player is in a farm run, which decides what the top of the tab shows: the
	 * runs on a day with nothing due, the packing list before setting off, the next stop
	 * once underway.
	 */
	private enum Phase
	{
		NONE,
		IDLE,
		PACKING,
		UNDERWAY
	}

	/**
	 * Shows the sections the phase always has and hides the rest. Sections that also hide
	 * themselves when empty (bag, gear lists, route boxes) are only ever turned off here;
	 * the rebuild turns them on, so the guard's early return cannot leave one on screen
	 * empty.
	 */
	private void showSections(Phase phase, boolean respawn, boolean herbs, boolean unlooked,
		boolean nextRun)
	{
		boolean any = phase != Phase.NONE;
		boolean idle = phase == Phase.IDLE;
		boolean packing = phase == Phase.PACKING;
		respawnNote.setVisible(any && respawn);
		herbNote.setVisible(any && herbs);
		nextRunLabel.setVisible(idle && nextRun);
		idleRunsSlot.setVisible(idle);
		carryRow.setVisible(packing);
		lookOnceNote.setVisible(packing && unlooked);
		lookOnceIdleNote.setVisible(idle && unlooked);
		spellbookPicker.setVisible(packing);
		carryView.setVisible(packing && carryView.isVisible());
		laterContainer.setVisible(packing && laterContainer.isVisible());
		wearHeading.setVisible(packing && wearHeading.isVisible());
		wearList.setVisible(packing && wearList.isVisible());
		goalsHeading.setVisible(idle && goalsHeading.isVisible());
		goalsList.setVisible(idle && goalsList.isVisible());
		settingsFold.setVisible(any);
		if (!any)
		{
			nowHeading.setVisible(false);
			nowContainer.setVisible(false);
			stopsHeading.setVisible(false);
			stopsContainer.setVisible(false);
			showSkippedBox.setVisible(false);
			showBanksBox.setVisible(false);
			routeHeading.setVisible(false);
			travel.setVisible(false);
			house.setVisible(false);
		}
	}

	/**
	 * Repeats the travel fold's fairy-ring warning on the settings header, so it reads with both
	 * folds shut. Re-worded only on a change, since the header relays out when set.
	 */
	private void showSettingsWarning(boolean blocked)
	{
		if (blocked == settingsWarned)
		{
			return;
		}
		settingsWarned = blocked;
		showSettingsHeader();
	}

	/**
	 * Names the active settings profile on the settings header, starred while it has unsaved
	 * changes, so both read with the fold shut. The fairy-ring warning takes the name's place
	 * while it applies: the player has to act on it, the two do not fit side by side, and
	 * the dropdown still names the profile. The star stays, after "Settings".
	 */
	private void showSettingsHeader()
	{
		String star = settingsUnsaved ? "*" : "";
		String suffix = settingsWarned ? star + SETTINGS_NO_FAIRY_RING
			: settingsProfile != null ? ": " + settingsProfile + star
			: "";
		if (suffix.equals(settingsSuffix))
		{
			return;
		}
		settingsSuffix = suffix;
		settingsFold.setHeader(suffix, settingsWarned ? COLOR_DUE : PanelColors.HEADER);
	}

	/**
	 * Shows the settings profiles, the built-in one first, and names the active one on the
	 * settings header, starred while it has unsaved changes. Call on the EDT. Updates in
	 * place, so it is safe on every refresh.
	 *
	 * @param fixed whether the active entry can't be saved, renamed or deleted
	 */
	public void showProfiles(List<String> names, String active, boolean fixed,
		boolean unsaved)
	{
		profilePicker.show(names, active, fixed, unsaved);
		settingsProfile = active;
		settingsUnsaved = unsaved;
		showSettingsHeader();
	}

	/** Receives what the player does with the settings profile control. */
	public void setProfileActions(ProfileActions actions)
	{
		profilePicker.setActions(actions);
	}

	/**
	 * Puts the runs list on the tab itself on a day with nothing due, where it answers "when
	 * is my next run", and into the settings fold otherwise, where it holds the run switches.
	 */
	private void placeRunsList(Phase phase)
	{
		JPanel home = phase == Phase.IDLE ? idleRunsSlot : settingsFold.content();
		if (runsList.getParent() == home)
		{
			return;
		}
		if (runsList.getParent() != null)
		{
			runsList.getParent().remove(runsList);
		}
		// In the settings fold it goes under the profile control, which the settings belong to.
		home.add(runsList, home == idleRunsSlot ? 0 : 1);
		home.revalidate();
		settingsFold.content().revalidate();
	}

	/** Restyles a stop row as the card for the stop the player is heading to. */
	private static JPanel asNowCard(JPanel row)
	{
		row.setOpaque(true);
		row.setBackground(PanelColors.CONTAINER_BG);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, COLOR_DUE),
			BorderFactory.createEmptyBorder(6, 4, 6, 4)));
		return row;
	}

	/**
	 * Larry's mood under his name: his one line of personality, so it never carries a fact
	 * the plan needs. The plan below says what is due and when.
	 *
	 * @param timed whether any run is due or counting down
	 */
	static String mood(boolean idle, boolean underway, boolean timed)
	{
		if (underway)
		{
			return "Keeping watch.";
		}
		if (!idle)
		{
			return "Up and about.";
		}
		// A day with no run counting down and none due means no patch has been seen yet,
		// or every run is shut.
		return timed ? "Having a drink." : "Waiting on your patches.";
	}

	private static boolean anyRunIn(List<FarmingRunStatus> runs, String status)
	{
		if (runs == null)
		{
			return false;
		}
		for (FarmingRunStatus run : runs)
		{
			if (status.equals(run.getStatus()))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * "Herb run ready in 1h 20m" for the run that finishes soonest, or "" when no run is
	 * counting down. Worded on every refresh, so the countdown ticks in place.
	 *
	 * @param now the current epoch second
	 */
	static String nextRunText(List<FarmingRunStatus> runs, long now)
	{
		if (runs == null)
		{
			return "";
		}
		FarmingRunStatus soonest = null;
		for (FarmingRunStatus run : runs)
		{
			if (run.getReadyAtEpochSec() > now
				&& (soonest == null || run.getReadyAtEpochSec() < soonest.getReadyAtEpochSec()))
			{
				soonest = run;
			}
		}
		if (soonest == null)
		{
			return "";
		}
		return soonest.getRunName() + " ready in "
			+ FarmingDurations.until(soonest.getReadyAtEpochSec(), now);
	}

	/**
	 * Draws what to fetch at this bank as a captioned grid of inventory squares, using the
	 * packing grid's sprites. Draws nothing when there is nothing to fetch.
	 *
	 * <p>Every square is orange. The list holds only what is missing at this point in the
	 * route, so the packing grid's green "already carried" border never applies. An item's
	 * availability describes the bag at departure, not at this bank.
	 *
	 * <p>Exception: {@code IN_SEED_BOX} items leave the grid and become one "check your seed
	 * box" line underneath ({@code CarryPlanner.theBoxWouldFinishIt} sets that state only
	 * where fetching would be unnecessary).
	 *
	 * @param captioned whether to head the grid "Withdraw". The starting row's heading says
	 *     it already before setting off, and a Later band names its bank instead
	 */
	private static void addWithdrawals(JPanel text, FarmingStopAdvice stop, IconProvider icons,
		boolean captioned)
	{
		List<FarmingCarryItem> withdraw = stop.getWithdraw();
		if (withdraw == null || withdraw.isEmpty())
		{
			return;
		}
		List<FarmingCarryItem> fetch = new ArrayList<>(withdraw.size());
		boolean checkSeedBox = false;
		for (FarmingCarryItem item : withdraw)
		{
			if (item.getAvailability() == FarmingCarryItem.Availability.IN_SEED_BOX)
			{
				checkSeedBox = true;
			}
			else
			{
				fetch.add(item);
			}
		}
		if (!fetch.isEmpty())
		{
			if (captioned)
			{
				text.add(PanelText.textArea("Withdraw", COLOR_DUE,
					FontManager.getRunescapeSmallFont()));
			}

			List<JPanel> squares = new ArrayList<>(fetch.size());
			for (FarmingCarryItem item : fetch)
			{
				squares.add(withdrawalSquare(item, icons));
			}
			InventoryBackdrop grid = SettingGrid.grid(squares, icons);
			grid.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
			text.add(grid);
		}
		// One line however many seeds: checking the box once answers them all.
		if (checkSeedBox)
		{
			text.add(PanelText.textArea(CarryView.CHECK_SEED_BOX, COLOR_DUE,
				FontManager.getRunescapeSmallFont()));
		}
	}

	/**
	 * The withdrawal grid's structural inputs, for the guard. The list changes while the
	 * player stands at the bank, so the guard must see it.
	 */
	private static String withdrawalSignature(FarmingStopAdvice stop)
	{
		List<FarmingCarryItem> withdraw = stop.getWithdraw();
		if (withdraw == null || withdraw.isEmpty())
		{
			return "";
		}
		StringBuilder out = new StringBuilder();
		for (FarmingCarryItem item : withdraw)
		{
			// Availability is structural: it decides grid square versus seed-box line.
			// The name is the square's tooltip.
			out.append(item.getItemId()).append('x').append(item.getQuantity())
				.append('/').append(item.getName())
				.append('/').append(item.getNote())
				.append('/').append(item.getTag())
				.append('/').append(item.getAvailability()).append(';');
		}
		return out.toString();
	}

	/**
	 * The deposit grid's structural inputs: which items, and whether they are urgent. Ids
	 * only, since a deposit tile draws no count, so a harvest piling up does not redraw.
	 */
	private static String depositSignature(FarmingStopAdvice stop)
	{
		StringBuilder out = new StringBuilder(stop.isDepositUrgent() ? "!" : "");
		for (FarmingCarryItem item : stop.getDeposit())
		{
			out.append(item.getItemId()).append(',');
		}
		return out.toString();
	}

	private static JPanel withdrawalSquare(FarmingCarryItem item, IconProvider icons)
	{
		return bankSquare(item, icons, true);
	}

	/**
	 * One square of a bank row's grid, deposits and withdrawals alike.
	 *
	 * @param due whether it is something to do, bordered orange. A deposit the bag has room
	 *     to keep draws without one
	 */
	private static JPanel bankSquare(FarmingCarryItem item, IconProvider icons, boolean due)
	{
		ImageIcon sprite = icons != null && item.getItemId() > 0
			? icons.getInventoryIcon(item.getItemId(), item.getQuantity(), true)
			: null;
		// The game draws the count into the sprite for stacks of two or more. Only a single
		// item needs one drawn here.
		ItemSquare cell = new ItemSquare(sprite,
			item.getQuantity() == 1 ? "1" : null,
			item.getTag());
		cell.setPreferredSize(new Dimension(ItemSquare.SIZE, ItemSquare.SIZE));
		cell.setMinimumSize(new Dimension(ItemSquare.SIZE, ItemSquare.SIZE));
		cell.setBorder(due
			? BorderFactory.createLineBorder(CarryView.COLOR_TO_WITHDRAW)
			: BorderFactory.createEmptyBorder(1, 1, 1, 1));
		cell.setToolTipText(CarryView.label(item));
		return cell;
	}

	/**
	 * Draws what to put back at this bank, above what to take out. Orange when the bag lacks
	 * room without it; grey, as a tidy-up, while the bag has room.
	 */
	private static void addDeposits(JPanel text, FarmingStopAdvice stop, IconProvider icons)
	{
		List<FarmingCarryItem> deposit = stop.getDeposit();
		if (deposit == null || deposit.isEmpty())
		{
			return;
		}
		boolean urgent = stop.isDepositUrgent();
		text.add(PanelText.textArea("Deposit", urgent ? COLOR_DUE : PanelColors.META,
			FontManager.getRunescapeSmallFont()));
		List<JPanel> squares = new ArrayList<>(deposit.size());
		for (FarmingCarryItem item : deposit)
		{
			squares.add(bankSquare(item, icons, urgent));
		}
		InventoryBackdrop grid = SettingGrid.grid(squares, icons);
		grid.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
		text.add(grid);
	}

	/** Every kind of bank row. All get the same margin mark. */
	private static boolean isBankRow(FarmingStopAdvice stop)
	{
		return stop.getKind() == FarmingStopAdvice.Kind.BANK
			|| stop.getKind() == FarmingStopAdvice.Kind.BANK_OPTIONAL
			|| stop.getKind() == FarmingStopAdvice.Kind.BANK_START;
	}

	/**
	 * Bank rows that take no number: an optional bank is outside the sequence, and the
	 * starting bank comes before it. Decides numbering only, not drawing.
	 */
	private static boolean isUnnumberedBankRow(FarmingStopAdvice stop)
	{
		return stop.getKind() == FarmingStopAdvice.Kind.BANK_OPTIONAL
			|| stop.getKind() == FarmingStopAdvice.Kind.BANK_START;
	}

	/**
	 * Counts the optional banks the route passes from {@code from} on. Recommended bank stops
	 * are always drawn and are not counted.
	 */
	private static int bankRowsIn(FarmingAdvice advice, int from)
	{
		int banks = 0;
		List<FarmingStopAdvice> stops = advice.getStops();
		for (int i = from; i < stops.size(); i++)
		{
			if (stops.get(i).getKind() == FarmingStopAdvice.Kind.BANK_OPTIONAL)
			{
				banks++;
			}
		}
		return banks;
	}

	private static JPanel container()
	{
		JPanel container = new JPanel();
		container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
		container.setBackground(PanelColors.CONTAINER_BG);
		container.setAlignmentX(LEFT_ALIGNMENT);
		container.setBorder(BorderFactory.createLineBorder(PanelColors.CONTAINER_BORDER, 1));
		return container;
	}

	private static JLabel sectionHeading(String text)
	{
		JLabel heading = new JLabel(sectionLabel(text));
		heading.setFont(FontManager.getRunescapeSmallFont());
		heading.setForeground(PanelColors.HEADER);
		heading.setAlignmentX(LEFT_ALIGNMENT);
		heading.setBorder(BorderFactory.createEmptyBorder(10, 2, 3, 0));
		return heading;
	}

	/** A section heading's text as drawn: every section label is upper case. */
	private static String sectionLabel(String text)
	{
		return text.toUpperCase();
	}

	/**
	 * Gives a row its wrapping width before first paint. Without it the text measures as one
	 * line and visibly reflows once layout assigns a width.
	 */
	static JPanel sized(JPanel row, int width)
	{
		if (width > 0)
		{
			row.setSize(width, row.getPreferredSize().height);
			row.doLayout();
		}
		return row;
	}

	/**
	 * Counts the stops the trip is making, by the same rule that numbers the rows: every
	 * {@code VISIT} except unnumbered bank rows. A bank stop at an arrival already listed
	 * counts separately.
	 */
	private static int visitedStops(FarmingAdvice advice)
	{
		int visits = 0;
		for (FarmingStopAdvice stop : advice.getStops())
		{
			if (stop.getOutcome() == FarmingStopAdvice.Outcome.VISIT
				&& !isUnnumberedBankRow(stop))
			{
				visits++;
			}
		}
		return visits;
	}

	/**
	 * Counts the rows the fold hides, from {@code from} on. Must match the fold rule in
	 * {@link #display}, so the label never offers more or fewer rows than clicking reveals.
	 */
	private static int foldedStops(FarmingAdvice advice, int from)
	{
		int folded = 0;
		List<FarmingStopAdvice> stops = advice.getStops();
		for (int i = from; i < stops.size(); i++)
		{
			if (stops.get(i).getOutcome() != FarmingStopAdvice.Outcome.VISIT)
			{
				folded++;
			}
		}
		return folded;
	}

	/**
	 * Where the next stop sits in the stop list: the first numbered visit, or the list's
	 * end when none is left.
	 */
	private static int nextStopIndex(FarmingAdvice advice)
	{
		List<FarmingStopAdvice> stops = advice.getStops();
		for (int i = 0; i < stops.size(); i++)
		{
			if (stops.get(i).getOutcome() == FarmingStopAdvice.Outcome.VISIT
				&& !isUnnumberedBankRow(stops.get(i)))
			{
				return i;
			}
		}
		return stops.size();
	}

	/**
	 * Counts the set-aside patch lines inside visited stops. A skipped stop's lines are not
	 * counted: they reveal with their row, which {@link #foldedStops} already counts.
	 */
	private static int foldedPatches(FarmingAdvice advice)
	{
		int folded = 0;
		for (FarmingStopAdvice stop : advice.getStops())
		{
			if (stop.getOutcome() == FarmingStopAdvice.Outcome.VISIT)
			{
				folded += setAside(stop).size();
			}
		}
		return folded;
	}

	/**
	 * The fold label, e.g. "12 skipped", under the SHOW ON THE ROUTE heading. The same text
	 * whether open or shut, so the label does not change under the cursor.
	 *
	 * <p>One number for skipped stops plus set-aside patches, so the label names neither.
	 * The box shows when either is non-zero, so counting stops alone could read "0 skipped".
	 * The caller hides the box when the total is zero.
	 */
	private static String foldLabel(int stops, int patches)
	{
		return (stops + patches) + " skipped";
	}

	/**
	 * The line asking for the stores Larry has never looked in. Orange like the packing
	 * list's own still-needed items: until the player looks, the plan may be short of what
	 * they own.
	 */
	private static JTextArea lookOnceLine(Border border)
	{
		JTextArea line = PanelText.textArea("", CarryView.COLOR_TO_WITHDRAW,
			FontManager.getRunescapeSmallFont());
		line.setBorder(border);
		return line;
	}

	/**
	 * One stop: its seed sprite, the patch, the route, and what goes in the ground.
	 *
	 * @param drawSkipped whether the skipped-stops fold is open, which also lists a stop's
	 *     set-aside patches
	 */
	private JPanel buildStopRow(FarmingStopAdvice stop, int number, IconProvider icons,
		boolean drawSkipped)
	{
		boolean visiting = stop.getOutcome() == FarmingStopAdvice.Outcome.VISIT;
		String note = stop.getNote();
		boolean hasNote = note != null && !note.isBlank();

		JPanel row = new JPanel(new GridBagLayout());
		row.setOpaque(false);
		row.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 4));
		row.setAlignmentX(LEFT_ALIGNMENT);

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.weightx = 0;
		gbc.anchor = GridBagConstraints.NORTHWEST;
		gbc.fill = GridBagConstraints.NONE;

		// Margin: one crop per planting, stacked, at the game's sprite size (rescaling
		// distorts it). A row planting nothing shows the crop it harvests, and a bank row
		// the minimap bank symbol. A skipped stop gets no crop.
		JComponent margin = marginOf(stop, visiting, icons);
		if (margin != null)
		{
			gbc.gridheight = GridBagConstraints.REMAINDER;
			row.add(margin, gbc);
			gbc.gridheight = 1;
		}
		else if (visiting && stop.getKind() == FarmingStopAdvice.Kind.PATCH)
		{
			// No crop to show (a compost bin, say). Reserve the margin so every visited
			// patch row's text shares one left edge. Other non-patch rows keep full width.
			gbc.gridheight = GridBagConstraints.REMAINDER;
			row.add(Box.createRigidArea(new Dimension(ITEM_SPRITE_WIDTH + 5, 0)), gbc);
			gbc.gridheight = 1;
		}

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setOpaque(false);

		// A wrapping area, not a JLabel: a skipped stop's heading is long and a JLabel
		// would truncate it with an ellipsis.
		// An offered bank is a choice, not a step, so it reads quieter than the stops.
		boolean step = visiting && stop.getKind() != FarmingStopAdvice.Kind.BANK_OPTIONAL;
		JTextArea name = PanelText.textArea(headingOf(stop, number),
			headingColour(stop),
			step ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeSmallFont());
		text.add(name);
		if (!visiting && stop.getReadyAtEpochSec() > 0)
		{
			countdowns.add(() -> name.setText(headingOf(stop, number)));
		}

		// A skip shows its reason on the row, not only in the hover, unless the heading
		// already carries it (the stop has a subject).
		if (!visiting)
		{
			String tooltip = hover(stop.getBecause(), hasNote ? note : null);
			row.setToolTipText(tooltip);
			name.setToolTipText(tooltip);
			String because = stop.getBecause();
			boolean saidInHeading = (stop.getSubject() != null && !stop.getSubject().isBlank())
				|| headingCarriesBecause(stop);
			if (!saidInHeading && because != null && !because.isBlank())
			{
				text.add(PanelText.textArea(because, COLOR_SKIPPED,
					FontManager.getRunescapeSmallFont()));
			}
			// The heading covers only the most urgent patch. These lines cover the rest.
			addSetAside(text, stop, drawSkipped);
			gbc.gridx = 1;
			gbc.weightx = 1.0;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			row.add(text, gbc);
			return row;
		}

		String detail = stop.getInstruction();
		if (detail != null && !detail.isBlank())
		{
			text.add(routeLine(stop, detail, icons));
		}
		// Before anything to do here: the stop cannot be done without it. Orange, as
		// everything still needed is.
		if (stop.getWarning() != null && !stop.getWarning().isBlank())
		{
			text.add(PanelText.textArea(stop.getWarning(), CarryView.COLOR_TO_WITHDRAW,
				FontManager.getRunescapeSmallFont()));
		}

		// Check/harvest acts, above the planting lines. One line per act in the given
		// order: the sequence matters (e.g. a compost bin upgrade), so never sort or merge.
		for (String act : actions(stop))
		{
			if (act == null || act.isBlank())
			{
				continue;
			}
			text.add(PanelText.textArea(act, COLOR_DUE,
				FontManager.getRunescapeSmallFont()));
		}

		addDeposits(text, stop, icons);
		// While packing, a later bank's withdrawals stand in its band at the top; drawing
		// them here too would show the same grid twice.
		boolean inABand = laterBandsDrawn && (stop.getKind() == FarmingStopAdvice.Kind.BANK
			|| stop.getKind() == FarmingStopAdvice.Kind.BANK_OPTIONAL);
		if (!inABand)
		{
			// Before setting off the starting row's heading says "Withdraw" already.
			addWithdrawals(text, stop, icons,
				stop.getKind() != FarmingStopAdvice.Kind.BANK_START || drawingUnderway);
		}

		// One line per crop, counted in patches, not seeds (an allotment takes three seeds
		// but is one planting). Empty when the action line already said "harvest and plant".
		for (FarmingPlantAdvice plant : plants(stop))
		{
			text.add(PanelText.textArea(plantingOf(plant), COLOR_DUE,
				FontManager.getRunescapeSmallFont()));
		}

		// After the plantings, since the tools are needed until the stop is done. Grey: it
		// saves slots, and nothing fails without it.
		if (stop.getLeprechaun() != null && !stop.getLeprechaun().isBlank())
		{
			text.add(PanelText.textArea(stop.getLeprechaun(), PanelColors.META,
				FontManager.getRunescapeSmallFont()));
		}

		// Grey and last of the stop's own lines: it qualifies the work above, and the guess
		// may be why the player is sent here at all.
		if (stop.getAssumption() != null && !stop.getAssumption().isBlank())
		{
			text.add(PanelText.textArea(stop.getAssumption(), PanelColors.META,
				FontManager.getRunescapeSmallFont()));
		}

		// Below the plantings: a row leads with what the player acts on.
		addSetAside(text, stop, drawSkipped);

		// isOptional is not displayed: optional crops already have a player toggle in
		// settings, and the flag only drives routing.

		// Route hedges and default-route hints go in the hover.
		if (hasNote)
		{
			row.setToolTipText(note);
			name.setToolTipText(note);
		}

		gbc.gridx = 1;
		gbc.weightx = 1.0;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		row.add(text, gbc);
		return row;
	}

	/**
	 * A hover from the clauses that have something to say, or null for none.
	 *
	 * <p>Null rather than "": {@code setToolTipText("")} shows an empty tooltip box.
	 */
	private static String hover(String... clauses)
	{
		StringBuilder joined = new StringBuilder();
		for (String clause : clauses)
		{
			if (clause == null || clause.isBlank())
			{
				continue;
			}
			if (joined.length() > 0)
			{
				joined.append(" — ");
			}
			joined.append(clause);
		}
		return joined.length() == 0 ? null : joined.toString();
	}

	/**
	 * One grey line per patch at this arrival that nobody is working, with its reason in
	 * the hover. Drawn on visited and skipped rows alike, so every patch gets either a
	 * planting line or one of these. Hidden with the skipped stops when the fold is shut.
	 */
	private void addSetAside(JPanel text, FarmingStopAdvice stop, boolean drawSkipped)
	{
		if (!drawSkipped)
		{
			return;
		}
		for (FarmingPatchNote aside : setAside(stop))
		{
			JTextArea line = PanelText.textArea(patchNoteText(aside), COLOR_SKIPPED,
				FontManager.getRunescapeSmallFont());
			line.setToolTipText(hover(aside.getBecause()));
			text.add(line);
			if (aside.getReadyAtEpochSec() > 0)
			{
				countdowns.add(() -> line.setText(patchNoteText(aside)));
			}
		}
	}

	/**
	 * A row's heading: its number and place when the trip is on, its place and why not
	 * when it is off.
	 *
	 * <p>A skipped heading carries a countdown, so {@link #countdowns} re-words it in place.
	 */
	private static String headingOf(FarmingStopAdvice stop, int number)
	{
		// No number: the counter has not advanced for this row, so prefixing it would
		// repeat the previous row's number.
		if (isUnnumberedBankRow(stop))
		{
			return stop.getPatchName();
		}
		return stop.getOutcome() == FarmingStopAdvice.Outcome.VISIT
			? number + ". " + stop.getPatchName()
			: stop.getPatchName() + " — " + statusOf(stop);
	}

	/**
	 * The route line, with its teleport icon inline on the first line. The row's margin
	 * column belongs to the crop.
	 */
	private static JPanel routeLine(FarmingStopAdvice stop, String detail, IconProvider icons)
	{
		JPanel line = new JPanel(new GridBagLayout());
		line.setOpaque(false);
		line.setAlignmentX(LEFT_ALIGNMENT);

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridy = 0;
		gbc.anchor = GridBagConstraints.NORTHWEST;

		int col = 0;
		ImageIcon sprite = travelIcon(stop, icons);
		if (sprite != null)
		{
			JLabel icon = new JLabel(sprite);
			icon.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));
			gbc.gridx = col++;
			gbc.weightx = 0;
			gbc.fill = GridBagConstraints.NONE;
			line.add(icon, gbc);
		}

		gbc.gridx = col;
		gbc.weightx = 1.0;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		line.add(PanelText.textArea(detail, detailColour(stop),
			FontManager.getRunescapeSmallFont()), gbc);
		return line;
	}

	/**
	 * A planting line in words: "Plant toadflax", "Plant toadflax in 2 patches". The crop
	 * reads lower case mid-sentence, as the game writes it.
	 */
	static String plantingOf(FarmingPlantAdvice plant)
	{
		String crop = inSentence(plant.getName());
		return plant.getPatches() > 1
			? "Plant " + crop + " in " + plant.getPatches() + " patches"
			: "Plant " + crop;
	}

	/** A crop name for mid-sentence: lower case, except Hespori, which is a name. */
	private static String inSentence(String crop)
	{
		return "Hespori".equals(crop) ? crop : crop.toLowerCase(java.util.Locale.ROOT);
	}

	/**
	 * A stop's margin: the bank symbol on a bank row; on a visit, one crop per planting
	 * stacked top to bottom, or the crop it harvests when it plants nothing. Null for a
	 * skipped stop or a visit with nothing to picture.
	 */
	private static JComponent marginOf(FarmingStopAdvice stop, boolean visiting,
		IconProvider icons)
	{
		if (isBankRow(stop))
		{
			ImageIcon bank = icons == null ? null
				: icons.getSpriteIcon(SpriteID.Mapfunction.BANK, 16);
			return bank == null ? null : marginLabel(bank);
		}
		if (!visiting)
		{
			return null;
		}
		List<FarmingPlantAdvice> pictured = new ArrayList<>();
		for (FarmingPlantAdvice plant : plants(stop))
		{
			if (plant.getProduceItemId() > 0)
			{
				pictured.add(plant);
			}
		}
		if (pictured.isEmpty())
		{
			int harvest = stop.getIconItemId();
			ImageIcon crop = icons == null || harvest <= 0 ? null
				: icons.getInventoryIcon(harvest, true);
			return crop == null ? null : marginLabel(crop);
		}
		JPanel column = new JPanel();
		column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
		column.setOpaque(false);
		column.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));
		for (int i = 0; i < pictured.size(); i++)
		{
			if (i > 0)
			{
				column.add(Box.createRigidArea(new Dimension(0, 2)));
			}
			column.add(cropIcon(pictured.get(i), icons));
		}
		return column;
	}

	/** A lone margin icon, inset as the stacked crops are. */
	private static JLabel marginLabel(ImageIcon sprite)
	{
		JLabel icon = new JLabel(sprite);
		icon.setBorder(BorderFactory.createEmptyBorder(1, 0, 0, 5));
		return icon;
	}

	/**
	 * One planting's crop in the margin. Outlined in amber when the seeds the player
	 * carries took it off the recommended herb, with the recommendation in the hover. The run
	 * still plants what they carry.
	 */
	private static JLabel cropIcon(FarmingPlantAdvice plant, IconProvider icons)
	{
		JLabel icon = new JLabel(icons == null ? null
			: icons.getInventoryIcon(plant.getProduceItemId(), true));
		icon.setAlignmentX(LEFT_ALIGNMENT);
		boolean offCourse = plant.getRecommendedName() != null;
		// The same 1px either way, so an outline never shifts the sprite.
		icon.setBorder(offCourse ? BorderFactory.createLineBorder(COLOR_DUE)
			: BorderFactory.createEmptyBorder(1, 1, 1, 1));
		icon.setToolTipText(offCourse
			? "Recommended: " + plant.getRecommendedName()
			: plant.getName());
		return icon;
	}

	/** The tablet, worn item or spell icon this route is taken on, or null for neither. */
	private static ImageIcon travelIcon(FarmingStopAdvice stop, IconProvider icons)
	{
		if (icons == null)
		{
			return null;
		}
		if (stop.getTravelItemId() > 0)
		{
			// Cropped to the art: a lone icon needs no shared scale, and a tablet needs
			// every pixel at this size.
			return icons.getGearIcon(stop.getTravelItemId(), TRAVEL_ICON, true, true);
		}
		return icons.getSpriteIcon(stop.getTravelSpriteId(), TRAVEL_ICON);
	}

	/**
	 * A skipped stop's short status from its skip reason, plus time left where something is
	 * growing.
	 */
	private static String statusOf(FarmingStopAdvice stop)
	{
		FarmingSkipReason reason = stop.getSkipReason();
		String subject = stop.getSubject();
		boolean hasSubject = subject != null && !subject.isBlank();
		if (reason == FarmingSkipReason.STANDING_CROP)
		{
			// Left in on purpose, so no countdown: nothing is waited for.
			return hasSubject ? "keep the " + subject : reason.getLabel();
		}
		if (headingCarriesBecause(stop))
		{
			return stop.getBecause();
		}
		String label = reason != null ? reason.getLabel() : "skipped";
		if (hasSubject)
		{
			label = subject + " " + label;
		}
		return withCountdown(label, stop.getReadyAtEpochSec());
	}

	/**
	 * Whether a skip's heading says its whole reason: a seed or level shortfall, whose
	 * clause ("no acorn", "toadflax needs 38 Farming") is short and more exact than the
	 * label. The row then draws no second line saying the same thing.
	 */
	private static boolean headingCarriesBecause(FarmingStopAdvice stop)
	{
		FarmingSkipReason reason = stop.getSkipReason();
		String because = stop.getBecause();
		return (reason == FarmingSkipReason.NO_SEED || reason == FarmingSkipReason.NEEDS_LEVEL)
			&& because != null && !because.isBlank();
	}

	/** "growing, ready in 2h 10m", or the label alone when no finish time is known. */
	private static String withCountdown(String label, long readyAtEpochSec)
	{
		String left = FarmingDurations.until(readyAtEpochSec, Instant.now().getEpochSecond());
		return left.isEmpty() ? label : label + ", ready in " + left;
	}

	private static Color headingColour(FarmingStopAdvice stop)
	{
		// Checked before the outcome: an overflow row is a VISIT (so the fold never hides
		// it) and would otherwise draw white like an ordinary stop.
		if (stop.getKind() == FarmingStopAdvice.Kind.OVERFLOW)
		{
			return COLOR_SHORTFALL;
		}
		// Optional banks draw light, not grey: grey means "nobody is going here".
		if (stop.getKind() == FarmingStopAdvice.Kind.BANK_OPTIONAL)
		{
			return COLOR_ROUTE;
		}
		switch (stop.getOutcome())
		{
			case VISIT:
				return stop.isUncertain() ? COLOR_UNCERTAIN : Color.WHITE;
			case SKIP:
				return COLOR_SKIPPED;
			default:
				return COLOR_SHORTFALL;
		}
	}

	private static Color detailColour(FarmingStopAdvice stop)
	{
		return stop.isUncertain() ? COLOR_UNCERTAIN : COLOR_ROUTE;
	}

	/** A stop's plants, never null: a builder that sets no plants leaves the field null. */
	private static List<FarmingPlantAdvice> plants(FarmingStopAdvice stop)
	{
		return stop.getPlants() != null ? stop.getPlants() : List.of();
	}

	/** A stop's acts, never null (see {@link #plants}). */
	private static List<String> actions(FarmingStopAdvice stop)
	{
		return stop.getActions() != null ? stop.getActions() : List.of();
	}

	private static List<FarmingPatchNote> setAside(FarmingStopAdvice stop)
	{
		return stop.getSetAside() != null ? stop.getSetAside() : List.of();
	}

	/**
	 * The set-aside lines' structural inputs: patch, reason and finish instant. Never the
	 * worded countdown (see {@link #countdowns}).
	 */
	private static String setAsideSignature(FarmingStopAdvice stop)
	{
		StringBuilder all = new StringBuilder();
		for (FarmingPatchNote aside : setAside(stop))
		{
			// The reason's sentence is the line's tooltip, set only on a rebuild.
			all.append(aside.getPatchName()).append('~').append(aside.getReason())
				.append('~').append(aside.getReadyAtEpochSec())
				.append('~').append(aside.getBecause()).append('/');
		}
		return all.toString();
	}

	/** A set-aside patch line, e.g. "Flower — growing, ready in 32m". */
	private static String patchNoteText(FarmingPatchNote aside)
	{
		FarmingSkipReason reason = aside.getReason();
		String label = reason != null ? reason.getLabel() : "skipped";
		return reason == FarmingSkipReason.STANDING_CROP
			? aside.getPatchName() + " — " + label
			: aside.getPatchName() + " — " + withCountdown(label, aside.getReadyAtEpochSec());
	}

	/**
	 * Every structural input of the rebuilt rows, and nothing that ticks. Live values set in
	 * place (title, notes, worded countdowns) stay out, or each change would rebuild the
	 * wrapping rows and flicker.
	 *
	 * @param drawSkipped whether the skipped-stops fold is open
	 */
	private static List<String> signatureOf(FarmingAdvice advice, Phase phase,
		boolean drawSkipped, boolean drawBanks)
	{
		List<String> signature = new ArrayList<>();
		// The phase and both folds decide which rows and sections exist. So does the cut-off
		// for rows behind the player, which needs no entry of its own: it follows from the
		// phase and each stop's kind and outcome, all signed below.
		signature.add("f:" + phase + "|" + drawSkipped + "|" + drawBanks);
		RunsListView.addSignature(signature, advice);
		// Previewing a book with the same kit changes no item but must still rebuild.
		signature.add("b:" + advice.getCarryFor() + "|" + advice.getSpellbookOpen());
		for (FarmingStopAdvice stop : advice.getStops())
		{
			// The finish instant, never the worded countdown. Kind decides the heading's
			// colour and numbering.
			signature.add(stop.getKind() + "|" + stop.getPatchName() + "|" + stop.getOutcome()
				// The act texts, not their count: each is its own text area.
				+ "|" + stop.getInstruction() + "|" + actions(stop)
				+ "|" + stop.getBecause()
				+ "|" + stop.getSkipReason() + "|" + stop.getSubject()
				+ "|" + stop.getReadyAtEpochSec()
				+ "|" + stop.isUncertain() + "|" + stop.isOptional()
				+ "|" + plants(stop) + "|" + stop.getIconItemId() + "|" + setAsideSignature(stop)
				+ "|" + stop.getTravelItemId() + "|" + stop.getTravelSpriteId()
				+ "|" + stop.getNote()
				+ "|" + withdrawalSignature(stop)
				+ "|" + depositSignature(stop)
				+ "|" + stop.getWarning() + "|" + stop.getLeprechaun()
				+ "|" + stop.getAssumption());
		}
		if (advice.getCarry() != null)
		{
			for (FarmingCarryItem item : advice.getCarry())
			{
				// The note can change alone (e.g. owning a ring of charos changes the coins
				// note, not the quantity).
				signature.add("c:" + item.getItemId() + "|" + item.getName()
					+ "|" + item.getQuantity() + "|" + item.getAvailability()
					+ "|" + item.getNote() + "|" + item.getTag());
			}
		}
		for (FarmingCarryItem item : carryNow(advice))
		{
			signature.add("n:" + item.getItemId() + "|" + item.getName()
				+ "|" + item.getQuantity() + "|" + item.getAvailability()
				+ "|" + item.getNote() + "|" + item.getTag());
		}
		if (advice.getUpgrades() != null)
		{
			for (FarmingUpgrade upgrade : advice.getUpgrades())
			{
				signature.add("u:" + upgrade.getKind() + "|" + upgrade.getItemId()
					+ "|" + upgrade.getName() + "|" + upgrade.getDetail());
			}
		}
		return signature;
	}

	@Override
	public Dimension getMaximumSize()
	{
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}
}
