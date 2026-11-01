package com.larryfarming.panel;

import com.larryfarming.snapshot.*;
import java.awt.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.*;
import javax.swing.*;
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
	 * Leprechauns in the Prifddinas Grand Library. The panel's one piece of lore.
	 */
	static final String HUNGRY_PATIENCE = "They watch. They wait. But theirs is a hungry patience.";

	/** {@code RunStatus} names, as the runs list carries them. */
	private static final String STATUS_DUE = "DUE";
	private static final String STATUS_PENDING = "PENDING";

	/** Appended to the settings header while no fairy ring can be reached. */
	private static final String SETTINGS_NO_FAIRY_RING = ": no fairy ring access";

	/** The settings fold's last control, which puts every setting back. */
	static final String RESET_LABEL = "Reset to the default route";

	private final JLabel titleLabel;
	/** Larry's face, name and the plugin version, at the top logged in or out. */
	private final BrandHeader brandHeader;
	/** The whole panel while no account is logged in. */
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
	 * The runs list: the panel's main content on a day with nothing due, and inside the
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
	/** The wait-for-every-patch checkbox, which sits above the fold switches. */
	final JCheckBox waitBox;
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

	/** Puts every farming setting back to the default route, last in the settings fold. */
	private final SmallButton resetButton = new SmallButton(RESET_LABEL);

	/** Writes the default route's settings; null until the plugin wires it. */
	private Runnable resetCallback;

	/**
	 * The player's choice to draw skipped stops. Off by default because skipped stops
	 * outnumber the ones being walked. A day with nothing due follows it too: the runs list
	 * already says when each run is ready.
	 */
	private boolean showSkipped;

	private Consumer<Boolean> showSkippedCallback;

	/**
	 * Whether the banks the route passes are drawn. Separate from {@link #showSkipped}
	 * because an offered bank is available, not skipped. On by default: the rows are
	 * unnumbered and outside the stop count.
	 */
	private boolean showBanks = true;

	private Consumer<Boolean> showBanksCallback;

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
	 * <p>Countdown text must stay out of the guard's signature, or the panel would rebuild and
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

		respawnNote = PanelText.small("", COLOR_UNCERTAIN);
		respawnNote.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

		herbNote = PanelText.small("", COLOR_UNCERTAIN);
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
		laterContainer = PanelText.stack();
		wearHeading = sectionHeading("Wear");
		wearList = new GearList(FarmingUpgrade.Kind.EQUIP);

		nowHeading = sectionHeading("First stop");
		nowContainer = PanelText.stack();
		stopsHeading = sectionHeading("Then");
		stopsContainer = container();

		goalsHeading = sectionHeading("Between runs");
		goalsList = new GearList(FarmingUpgrade.Kind.GOAL);

		runsList = new RunsListView(sectionHeading("Runs"), container(), this::sendSetting);
		idleRunsSlot = PanelText.stack();

		runFirstCaption = SiteList.caption("", 0);
		runFirstChips = new ChoiceChips(this::sendSetting);
		runFirstChips.setAlignmentX(LEFT_ALIGNMENT);
		runFirstChips.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));

		waitBox = checkbox(null);
		waitBox.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
		waitBox.addActionListener(e -> sendSetting(waitKey, String.valueOf(waitBox.isSelected())));

		planning = PanelText.stack();
		planning.add(sectionHeading("Planning"));
		planning.add(runFirstCaption);
		planning.add(runFirstChips);
		planning.add(waitBox);
		planning.setVisible(false);

		routeHeading = sectionHeading("Show on the route");
		routeHeading.setVisible(false);

		settingsFold = new Fold("Settings");
		settingsFold.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
		settingsFold.content().add(runsList);
		settingsFold.content().add(planning);
		settingsFold.content().add(routeHeading);
		settingsFold.content().add(showSkippedBox);
		settingsFold.content().add(showBanksBox);
		travel = new TravelFold(this::sendSetting);
		settingsFold.content().add(travel);
		house = new HouseFold(this::sendSetting);
		settingsFold.content().add(house);
		resetButton.addActionListener(e -> confirmReset());
		JPanel resetRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		resetRow.setOpaque(false);
		resetRow.setAlignmentX(LEFT_ALIGNMENT);
		resetRow.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
		resetRow.add(resetButton);
		resetRow.setMaximumSize(new Dimension(Short.MAX_VALUE, resetRow.getPreferredSize().height));
		settingsFold.content().add(resetRow);

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
	public void setSettingsOpenCallback(Consumer<Boolean> callback)
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
	public void setShowSkippedCallback(Consumer<Boolean> callback)
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
		reportAndRedraw(showSkippedCallback, show);
	}

	private void reportAndRedraw(Consumer<Boolean> callback, boolean show)
	{
		if (callback != null)
		{
			callback.accept(show);
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
	public void setShowBanksCallback(Consumer<Boolean> callback)
	{
		this.showBanksCallback = callback;
	}

	private void toggleShowBanks(boolean show)
	{
		this.showBanks = show;
		reportAndRedraw(showBanksCallback, show);
	}

	/**
	 * Receives a farming setting the player changed in the panel, as its config key and the
	 * value to write. The plugin saves it, and the re-plan that follows redraws the panel.
	 */
	public void setSettingCallback(BiConsumer<String, String> callback)
	{
		settingCallback = callback;
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
	public void setTravelOpenCallback(Consumer<Boolean> callback)
	{
		travel.setOpenCallback(callback);
	}

	/** Puts the remembered house fold state back on screen, without reporting a change. */
	public void setHouseOpen(boolean open)
	{
		house.setOpen(open);
	}

	/** Receives the house fold's new state when the player opens or shuts it. */
	public void setHouseOpenCallback(Consumer<Boolean> callback)
	{
		house.setOpenCallback(callback);
	}

	/** The panel's one checkbox style, for the fold boxes and the farming settings alike. */
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
	 * <p>Call when the panel is switched to: its rows wrapped against a width from when it was
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
	public void setSpellbookCallback(Consumer<String> callback)
	{
		spellbookPicker.setSpellbookCallback(callback);
	}

	/**
	 * Shows a run, rebuilding only when the advice itself has changed.
	 *
	 * <p>Called on every game event. The rows hold wrapping text, and recreating it on each
	 * call would flicker the panel.
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

		int visits = countFrom(advice, 0, FarmingPanel::isNumbered);
		Phase phase = visits == 0 ? Phase.IDLE : advice.isUnderway() ? Phase.UNDERWAY : Phase.PACKING;
		// Underway, every row before the next stop is behind the player: a finished stop (it
		// turned into a skip when planted) or a bank already passed. Those rows are never
		// drawn, so the switches' counts start where the drawing does.
		int from = phase == Phase.UNDERWAY ? nextStopIndex(advice) : 0;
		// Counted from the rows so the fold label matches exactly what the fold hides: every
		// arrival that is not a VISIT. Must match the fold rule below, so the label never
		// offers more or fewer rows than clicking reveals.
		int foldedStops = countFrom(advice, from, stop -> stop.getOutcome() != FarmingStopAdvice.Outcome.VISIT);
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
		brandHeader.showMood(mood(idle, underway, pending || due), idle && pending && !due ? HUNGRY_PATIENCE : null);
		// The picker keeps its own change check, so calling it every refresh is cheap.
		spellbookPicker.rebuild(advice.getCarryFor(), advice.getSpellbookOpen(), icons);
		String nextRun = nextRunText(advice.getRuns(), Instant.now().getEpochSecond());
		nextRunLabel.setText(nextRun);

		showSkippedBox.setText(foldLabel(foldedStops, foldedPatches));
		// The optional banks still ahead. Recommended bank stops are always drawn.
		int banks = countFrom(advice, from, stop -> stop.getKind() == FarmingStopAdvice.Kind.BANK_OPTIONAL);
		showBanksBox.setText(banks == 1 ? "1 bank you may pass" : banks + " banks you may pass");

		boolean suggesting = showNote(respawnNote, advice.getRespawnAction());
		boolean untickedSeeds = showNote(herbNote, advice.getHerbNote());

		String look = advice.getLookOnce();
		boolean unlooked = PanelText.hasText(look);
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

		// The rows wrap against the panel's inner width less the route box's border. Read off
		// the panel, since a box hidden until now reports no width of its own.
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
			boolean numbered = isNumbered(stop);
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
			if (stop.getKind() == FarmingStopAdvice.Kind.BANK_START && !haveNow && !stop.getWithdraw().isEmpty())
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
		carryView.rebuild(phase == Phase.PACKING ? (split ? carryNow(advice) : carry) : null, icons, width);
		carryHeading.setVisible(carry != null && !carry.isEmpty());
		rebuildLater(phase == Phase.PACKING && split ? stops : null, drawBanks, icons, width);

		boolean wear = wearList.rebuild(phase == Phase.PACKING ? advice.getUpgrades() : null, icons, width);
		wearHeading.setVisible(wear);
		boolean goals = goalsList.rebuild(phase == Phase.IDLE ? advice.getUpgrades() : null, icons, width);
		goalsHeading.setVisible(goals);

		// Inside the settings fold the list sits behind the fold content's left inset.
		int runsWidth = phase == Phase.IDLE || width == 0 ? width : Math.max(0, width - settingsFold.content().getInsets().left);
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
	private void rebuildLater(List<FarmingStopAdvice> stops, boolean drawBanks, IconProvider icons, int width)
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
				JPanel grid = PanelText.stack();
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
		return PanelText.listOf(advice.getCarryNow());
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
	 * Where the player is in a farm run, which decides what the top of the panel shows: the
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
	private void showSections(Phase phase, boolean respawn, boolean herbs, boolean unlooked, boolean nextRun)
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
		settingsFold.setHeader(blocked ? SETTINGS_NO_FAIRY_RING : "", blocked ? COLOR_DUE : PanelColors.HEADER);
	}

	/**
	 * Receives the player's confirmed "Reset to the default route". The plugin writes the
	 * default route's value for every farming setting.
	 */
	public void setResetCallback(Runnable callback)
	{
		resetCallback = callback;
	}

	/** Asks before a reset, since it overwrites every farming setting at once. */
	private void confirmReset()
	{
		String[] options = {"Reset", "Cancel"};
		boolean reset = JOptionPane.showOptionDialog(this, "Every farming setting goes back to the default route.", "Larry",
			JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]) == 0;
		if (reset && resetCallback != null)
		{
			resetCallback.run();
		}
	}

	/**
	 * Puts the runs list on the panel itself on a day with nothing due, where it answers "when
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
		// First in the settings fold, ahead of the settings that order the runs.
		home.add(runsList, 0);
		home.revalidate();
		settingsFold.content().revalidate();
	}

	/** Restyles a stop row as the card for the stop the player is heading to. */
	private static JPanel asNowCard(JPanel row)
	{
		row.setOpaque(true);
		row.setBackground(PanelColors.CONTAINER_BG);
		row.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 3, 0, 0, COLOR_DUE),
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
			if (run.getReadyAtEpochSec() > now && (soonest == null || run.getReadyAtEpochSec() < soonest.getReadyAtEpochSec()))
			{
				soonest = run;
			}
		}
		if (soonest == null)
		{
			return "";
		}
		return soonest.getRunName() + " ready in " + FarmingDurations.until(soonest.getReadyAtEpochSec(), now);
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
	private static void addWithdrawals(JPanel text, FarmingStopAdvice stop, IconProvider icons, boolean captioned)
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
				text.add(PanelText.small("Withdraw", COLOR_DUE));
			}
			addBankGrid(text, fetch, icons, true);
		}
		// One line however many seeds: checking the box once answers them all.
		if (checkSeedBox)
		{
			text.add(PanelText.small(CarryView.CHECK_SEED_BOX, COLOR_DUE));
		}
	}

	/**
	 * A bank row's grid of squares, deposits and withdrawals alike.
	 *
	 * @param due whether they are something to do, bordered orange. A deposit the bag has
	 *     room to keep draws without one
	 */
	private static void addBankGrid(JPanel text, List<FarmingCarryItem> items, IconProvider icons, boolean due)
	{
		List<JPanel> squares = new ArrayList<>(items.size());
		for (FarmingCarryItem item : items)
		{
			ItemSquare cell = ItemSquare.of(item, icons, true);
			cell.setBorder(due ? BorderFactory.createLineBorder(CarryView.COLOR_TO_WITHDRAW) : BorderFactory.createEmptyBorder(1, 1, 1, 1));
			cell.setToolTipText(CarryView.label(item));
			squares.add(cell);
		}
		InventoryBackdrop grid = SettingGrid.grid(squares, icons);
		grid.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
		text.add(grid);
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
		text.add(PanelText.small("Deposit", urgent ? COLOR_DUE : PanelColors.META));
		addBankGrid(text, deposit, icons, urgent);
	}

	/** Every kind of bank row. All get the same margin mark. */
	private static boolean isBankRow(FarmingStopAdvice stop)
	{
		return stop.getKind() == FarmingStopAdvice.Kind.BANK || stop.getKind() == FarmingStopAdvice.Kind.BANK_OPTIONAL
			|| stop.getKind() == FarmingStopAdvice.Kind.BANK_START;
	}

	/**
	 * Bank rows that take no number: an optional bank is outside the sequence, and the
	 * starting bank comes before it. Decides numbering only, not drawing.
	 */
	private static boolean isUnnumberedBankRow(FarmingStopAdvice stop)
	{
		return stop.getKind() == FarmingStopAdvice.Kind.BANK_OPTIONAL || stop.getKind() == FarmingStopAdvice.Kind.BANK_START;
	}

	/**
	 * A row that takes a number, by the rule that numbers the rows: every {@code VISIT}
	 * except unnumbered bank rows. A bank stop at an arrival already listed counts separately.
	 */
	private static boolean isNumbered(FarmingStopAdvice stop)
	{
		return stop.getOutcome() == FarmingStopAdvice.Outcome.VISIT && !isUnnumberedBankRow(stop);
	}

	/** Counts the stops from {@code from} on that {@code which} picks. */
	private static int countFrom(FarmingAdvice advice, int from, Predicate<FarmingStopAdvice> which)
	{
		int count = 0;
		List<FarmingStopAdvice> stops = advice.getStops();
		for (int i = from; i < stops.size(); i++)
		{
			if (which.test(stops.get(i)))
			{
				count++;
			}
		}
		return count;
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
	 * Where the next stop sits in the stop list: the first numbered visit, or the list's
	 * end when none is left.
	 */
	private static int nextStopIndex(FarmingAdvice advice)
	{
		List<FarmingStopAdvice> stops = advice.getStops();
		for (int i = 0; i < stops.size(); i++)
		{
			if (isNumbered(stops.get(i)))
			{
				return i;
			}
		}
		return stops.size();
	}

	/**
	 * Counts the set-aside patch lines inside visited stops. A skipped stop's lines are not
	 * counted: they reveal with their row, which the folded stops already count.
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
		JTextArea line = PanelText.small("", CarryView.COLOR_TO_WITHDRAW);
		line.setBorder(border);
		return line;
	}

	/**
	 * Puts a live note's words on its long-lived area, when there are any.
	 *
	 * @return whether the note has anything to say
	 */
	private static boolean showNote(JTextArea note, String words)
	{
		boolean any = PanelText.hasText(words);
		if (any)
		{
			note.setText(words);
		}
		return any;
	}

	/** Adds a small line in {@code colour}, when there is anything to say. */
	private static void addLine(JPanel text, String line, Color colour)
	{
		if (PanelText.hasText(line))
		{
			text.add(PanelText.small(line, colour));
		}
	}

	/**
	 * One stop: its seed sprite, the patch, the route, and what goes in the ground.
	 *
	 * @param drawSkipped whether the skipped-stops fold is open, which also lists a stop's
	 *     set-aside patches
	 */
	private JPanel buildStopRow(FarmingStopAdvice stop, int number, IconProvider icons, boolean drawSkipped)
	{
		boolean visiting = stop.getOutcome() == FarmingStopAdvice.Outcome.VISIT;
		String note = stop.getNote();
		boolean hasNote = PanelText.hasText(note);

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
			// No crop to show (a contract errand alone, say). Reserve the margin so every visited
			// patch row's text shares one left edge. Other non-patch rows keep full width.
			gbc.gridheight = GridBagConstraints.REMAINDER;
			row.add(Box.createRigidArea(new Dimension(ITEM_SPRITE_WIDTH + 5, 0)), gbc);
			gbc.gridheight = 1;
		}

		JPanel text = PanelText.stack();

		// A wrapping area, not a JLabel: a skipped stop's heading is long and a JLabel
		// would truncate it with an ellipsis.
		// An offered bank is a choice, not a step, so it reads quieter than the stops.
		boolean step = visiting && stop.getKind() != FarmingStopAdvice.Kind.BANK_OPTIONAL;
		JTextArea name = PanelText.textArea(headingOf(stop, number), headingColour(stop),
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
			if (!PanelText.hasText(stop.getSubject()) && !headingCarriesBecause(stop))
			{
				addLine(text, stop.getBecause(), COLOR_SKIPPED);
			}
			// The heading covers only the most urgent patch. These lines cover the rest.
			addSetAside(text, stop, drawSkipped);
			PanelText.addFill(row, gbc, 1, text);
			return row;
		}

		String detail = stop.getInstruction();
		if (PanelText.hasText(detail))
		{
			text.add(routeLine(stop, detail, icons));
		}
		// Before anything to do here: the stop cannot be done without it. Orange, as
		// everything still needed is.
		addLine(text, stop.getWarning(), CarryView.COLOR_TO_WITHDRAW);

		// Check/harvest acts, above the planting lines. One line per act in the given
		// order. The sequence matters (a bush checked before it is dug up), so the lines
		// stay unsorted and unmerged.
		for (String act : actions(stop))
		{
			addLine(text, act, COLOR_DUE);
		}

		addDeposits(text, stop, icons);
		// While packing, a later bank's withdrawals stand in its band at the top; drawing
		// them here too would show the same grid twice.
		boolean inABand = laterBandsDrawn && (stop.getKind() == FarmingStopAdvice.Kind.BANK
			|| stop.getKind() == FarmingStopAdvice.Kind.BANK_OPTIONAL);
		if (!inABand)
		{
			// Before setting off the starting row's heading says "Withdraw" already.
			addWithdrawals(text, stop, icons, stop.getKind() != FarmingStopAdvice.Kind.BANK_START || drawingUnderway);
		}

		// One line per crop, counted in patches, not seeds (an allotment takes three seeds
		// but is one planting). Empty when the action line already said "harvest and plant".
		for (FarmingPlantAdvice plant : plants(stop))
		{
			text.add(PanelText.small(plantingOf(plant), COLOR_DUE));
		}

		// After the plantings, since the tools are needed until the stop is done. Grey: it
		// saves slots, and nothing fails without it.
		addLine(text, stop.getLeprechaun(), PanelColors.META);

		// Grey and last of the stop's own lines: it qualifies the work above, and the guess
		// may be why the player is sent here at all.
		addLine(text, stop.getAssumption(), PanelColors.META);

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

		PanelText.addFill(row, gbc, 1, text);
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
			if (!PanelText.hasText(clause))
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
			JTextArea line = PanelText.small(patchNoteText(aside), COLOR_SKIPPED);
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
		return stop.getOutcome() == FarmingStopAdvice.Outcome.VISIT ? number + ". " + stop.getPatchName()
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

		PanelText.addFill(line, gbc, col, PanelText.small(detail, detailColour(stop)));
		return line;
	}

	/**
	 * A planting line, counted in patches as the act lines are: "Plant toadflax",
	 * "Plant toadflax ×2". The crop reads lower case mid-sentence, as the game writes it.
	 */
	static String plantingOf(FarmingPlantAdvice plant)
	{
		return "Plant " + inSentence(plant.getName()) + FarmingPlantAdvice.countOf(plant.getPatches());
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
	private static JComponent marginOf(FarmingStopAdvice stop, boolean visiting, IconProvider icons)
	{
		if (isBankRow(stop))
		{
			ImageIcon bank = icons == null ? null : icons.getSpriteIcon(SpriteID.Mapfunction.BANK, 16);
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
			ImageIcon crop = icons == null || harvest <= 0 ? null : icons.getInventoryIcon(harvest, true);
			return crop == null ? null : marginLabel(crop);
		}
		JPanel column = PanelText.stack();
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

	/** One planting's crop in the margin, named in the hover. */
	private static JLabel cropIcon(FarmingPlantAdvice plant, IconProvider icons)
	{
		JLabel icon = new JLabel(icons == null ? null : icons.getInventoryIcon(plant.getProduceItemId(), true));
		icon.setAlignmentX(LEFT_ALIGNMENT);
		icon.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
		icon.setToolTipText(plant.getName());
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
		boolean hasSubject = PanelText.hasText(subject);
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
		return (reason == FarmingSkipReason.NO_SEED || reason == FarmingSkipReason.NEEDS_LEVEL) && PanelText.hasText(because);
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
		return PanelText.listOf(stop.getPlants());
	}

	/** A stop's acts, never null (see {@link #plants}). */
	private static List<String> actions(FarmingStopAdvice stop)
	{
		return PanelText.listOf(stop.getActions());
	}

	private static List<FarmingPatchNote> setAside(FarmingStopAdvice stop)
	{
		return PanelText.listOf(stop.getSetAside());
	}

	/** A set-aside patch line, e.g. "Flower — growing, ready in 32m". */
	private static String patchNoteText(FarmingPatchNote aside)
	{
		FarmingSkipReason reason = aside.getReason();
		String label = reason != null ? reason.getLabel() : "skipped";
		return reason == FarmingSkipReason.STANDING_CROP ? aside.getPatchName() + " — " + label
			: aside.getPatchName() + " — " + withCountdown(label, aside.getReadyAtEpochSec());
	}

	/**
	 * Every structural input of the rebuilt rows, and nothing that ticks. Live values set in
	 * place (title, notes, worded countdowns) stay out, or each change would rebuild the
	 * wrapping rows and flicker.
	 *
	 * @param drawSkipped whether the skipped-stops fold is open
	 */
	private static List<Object> signatureOf(FarmingAdvice advice, Phase phase, boolean drawSkipped, boolean drawBanks)
	{
		// Each entry sits at a fixed position, so no two inputs can be mistaken for each other.
		List<Object> signature = new ArrayList<>();
		// The phase and both folds decide which rows and sections exist. So does the cut-off
		// for rows behind the player, which needs no entry of its own: it follows from the
		// phase and each stop's kind and outcome, all signed below.
		signature.add(phase);
		signature.add(drawSkipped);
		signature.add(drawBanks);
		RunsListView.addSignature(signature, advice);
		// Previewing a book with the same kit changes no item but must still rebuild.
		signature.add(advice.getCarryFor());
		signature.add(advice.getSpellbookOpen());
		List<List<Object>> stops = new ArrayList<>(advice.getStops().size());
		for (FarmingStopAdvice stop : advice.getStops())
		{
			stops.add(shapeOf(stop));
		}
		signature.add(stops);
		// The note can change alone (e.g. owning a ring of charos changes the coins note, not
		// the quantity).
		signature.add(shapesOf(PanelText.listOf(advice.getCarry())));
		signature.add(shapesOf(carryNow(advice)));
		signature.add(PanelText.listOf(advice.getUpgrades()));
		return signature;
	}

	/**
	 * A stop as its row is built. The finish instant stays in, never the worded countdown,
	 * and so does each set-aside line's reason, the tooltip set only on a rebuild. The landing
	 * goes, since no row draws it. The deposits are their item ids alone, since a deposit
	 * tile draws no count, so a harvest piling up does not redraw.
	 */
	private static List<Object> shapeOf(FarmingStopAdvice stop)
	{
		List<Integer> deposit = new ArrayList<>(stop.getDeposit().size());
		for (FarmingCarryItem item : stop.getDeposit())
		{
			deposit.add(item.getItemId());
		}
		return List.of(stop.toBuilder().landing(null)
			// The list changes while the player stands at the bank, so the guard must see it.
			.withdraw(shapesOf(PanelText.listOf(stop.getWithdraw()))).deposit(List.of()).build(), deposit);
	}

	/**
	 * Carry lines as the panel draws them: without the count already carried, which no line
	 * shows. Availability stays, since it decides grid square versus seed-box line.
	 */
	private static List<FarmingCarryItem> shapesOf(List<FarmingCarryItem> items)
	{
		List<FarmingCarryItem> shapes = new ArrayList<>(items.size());
		for (FarmingCarryItem item : items)
		{
			shapes.add(item.withCarriedCount(0));
		}
		return shapes;
	}

	@Override
	public Dimension getMaximumSize()
	{
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}
}
