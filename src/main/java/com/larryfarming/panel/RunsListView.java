package com.larryfarming.panel;

import com.larryfarming.snapshot.FarmingAdvice;
import com.larryfarming.snapshot.FarmingRunSettings;
import com.larryfarming.snapshot.FarmingRunStatus;
import com.larryfarming.snapshot.FarmingSettingChoice;
import com.larryfarming.snapshot.FarmingSettingPick;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The farming tab's RUNS section: one row per run with its status, and under each row the
 * settings that decide what the run plants.
 *
 * <p>Clicking a row opens or shuts its settings. A run one setting switches carries that
 * checkbox on its row instead, and a click anywhere on its row flips the box.
 */
class RunsListView extends JPanel
{
	private static final Color COLOR_DUE = FarmingPanel.COLOR_DUE;
	private static final Color COLOR_ROUTE = ColorScheme.LIGHT_GRAY_COLOR;
	/** Grey means "not possible": a run the player has not unlocked, or has no route for. */
	private static final Color COLOR_SKIPPED = ColorScheme.MEDIUM_GRAY_COLOR;
	/** The status line of a run not due yet: quieter than the name, never grey. */
	private static final Color COLOR_QUIET = PanelColors.META;
	/** A run switched off: the light text faded, as an unticked square fades its sprite. */
	static final Color COLOR_OFF = new Color(
		ColorScheme.LIGHT_GRAY_COLOR.getRed(), ColorScheme.LIGHT_GRAY_COLOR.getGreen(),
		ColorScheme.LIGHT_GRAY_COLOR.getBlue(), 90);
	/** An opened run and its settings share this, so they read as one block. */
	private static final Color COLOR_OPEN = ColorScheme.DARKER_GRAY_COLOR;
	/** The arrow beside a run with settings, in the folds' own characters. */
	private static final String CHEVRON_SHUT = "▸";
	private static final String CHEVRON_OPEN = "▾";

	private static final String STATUS_DUE = "DUE";
	private static final String STATUS_LOCKED = "LOCKED";
	private static final String STATUS_NO_ROUTE = "NO_ROUTE";

	private final JLabel heading;
	private final JPanel container;

	/**
	 * The runs whose settings are open, by run id. Kept across rebuilds, so a re-plan after a
	 * click leaves the run open; the panel's own, not saved.
	 */
	private final Set<String> openRuns = new HashSet<>();

	/**
	 * The detail label of each run row, by run id. The engine words this countdown itself,
	 * so each refresh copies the newest text onto the existing label. The label does not
	 * wrap, so it stays out of the guard's signature.
	 */
	private final Map<String, JLabel> runDetails = new HashMap<>();

	private BiConsumer<String, String> settingCallback;

	RunsListView(JLabel heading, JPanel container)
	{
		this.heading = heading;
		this.container = container;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);
		setAlignmentX(LEFT_ALIGNMENT);
		add(heading);
		add(container);
	}

	void setSettingCallback(BiConsumer<String, String> callback)
	{
		this.settingCallback = callback;
	}

	/** Empties the list, for a tab with no advice. */
	void clear()
	{
		runDetails.clear();
		container.removeAll();
		setVisible(false);
	}

	/**
	 * Builds every row afresh from the advice. The caller decides when, through its guard,
	 * and passes the width the rows wrap against.
	 */
	void rebuild(FarmingAdvice advice, IconProvider icons, int width)
	{
		runDetails.clear();
		container.removeAll();
		Map<String, FarmingRunSettings> settingsFor = settingsByRun(advice);
		List<FarmingRunStatus> off = listOf(advice.getSwitchedOff());
		boolean divider = false;
		for (FarmingRunStatus run : rowsOf(advice.getRuns(), off, advice.getRunOrder()))
		{
			if (divider)
			{
				container.add(PanelColors.divider());
			}
			FarmingRunSettings settings = settingsFor.get(run.getRunId());
			// Built whether open or shut, then shown and hidden in place by a click on the row.
			JPanel opened = settings != null
				&& settings.getShape() != FarmingRunSettings.Shape.SWITCH
				? buildRunSettings(settings, icons) : null;
			if (opened != null)
			{
				opened.setVisible(openRuns.contains(run.getRunId()));
			}
			container.add(FarmingPanel.sized(buildRunRow(run, advice, icons, settings,
				off.contains(run), opened), width));
			if (opened != null)
			{
				container.add(opened);
			}
			divider = true;
		}
		setVisible(!listOf(advice.getRuns()).isEmpty() || !off.isEmpty());
	}

	/** Copies each run row's detail text from the newest advice onto the existing label. */
	void showDetails(List<FarmingRunStatus> runs)
	{
		if (runs == null)
		{
			return;
		}
		for (FarmingRunStatus run : runs)
		{
			JLabel detail = runDetails.get(run.getRunId());
			if (detail != null)
			{
				showRunDetail(detail, run.getDetail());
			}
		}
	}

	/**
	 * The list's structural inputs, for the tab's guard. A scheduled run gives its id and
	 * status only: its detail text is a worded countdown, updated in place through
	 * {@link #runDetails}.
	 * Which runs are open is not here either: a click shows and hides a block in place, and
	 * a rebuild reopens it from {@link #openRuns}.
	 */
	static void addSignature(List<String> signature, FarmingAdvice advice)
	{
		for (FarmingRunStatus run : listOf(advice.getRuns()))
		{
			signature.add("r:" + run.getRunId() + "|" + run.getStatus());
		}
		// Trip membership decides each row's colour and weight.
		signature.add("t:" + advice.getTripRuns());
		signature.add("ro:" + advice.getRunOrder());
		// A setting the player changes moves the re-planned advice here, which rebuilds the
		// blocks with the confirmed state. An off row's detail is fixed text, not a
		// countdown, and showDetails never reaches it, so it goes in whole.
		for (FarmingRunStatus run : listOf(advice.getSwitchedOff()))
		{
			signature.add("o:" + run.getRunId() + "|" + run.getDetail());
		}
		for (FarmingRunSettings settings : listOf(advice.getRunSettings()))
		{
			signature.add("s:" + settings);
		}
	}

	/**
	 * One run and its status.
	 *
	 * @param settings the run's settings, or null for a run with none
	 * @param switchedOff whether the player switched the run off; drawn dimmed
	 * @param opened the run's settings block, which a click on the row shows and hides, or
	 *     null for a run with no block
	 */
	private JPanel buildRunRow(FarmingRunStatus run, FarmingAdvice advice, IconProvider icons,
		FarmingRunSettings settings, boolean switchedOff, JPanel opened)
	{
		// Membership of the trip: a trip covers several families.
		boolean onTrip = !switchedOff && coversRun(advice, run.getRunId());
		boolean due = !switchedOff && STATUS_DUE.equals(run.getStatus());
		// Grey means not possible. A run merely not due yet keeps its colours.
		boolean impossible = !switchedOff && notPossible(run.getStatus());

		JPanel row = new JPanel(new GridBagLayout());
		// Opaque only while open, so the row and its settings read as one block.
		row.setOpaque(opened != null && opened.isVisible());
		row.setBackground(COLOR_OPEN);
		row.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 4));
		row.setAlignmentX(LEFT_ALIGNMENT);

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.weightx = 0;
		gbc.anchor = GridBagConstraints.NORTHWEST;
		gbc.fill = GridBagConstraints.NONE;

		JLabel icon = null;
		if (icons != null && run.getIconItemId() > 0)
		{
			// Greyscale only for a run that cannot be done; faint for one switched off.
			ImageIcon sprite = icons.getInventoryIcon(run.getIconItemId(), !impossible);
			if (sprite != null)
			{
				icon = new JLabel(switchedOff ? new DimmedIcon(sprite) : sprite);
				icon.setBorder(BorderFactory.createEmptyBorder(1, 0, 0, 5));
				gbc.gridheight = GridBagConstraints.REMAINDER;
				row.add(icon, gbc);
				gbc.gridheight = 1;
			}
		}

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setOpaque(false);

		JLabel name = new JLabel(run.getRunName());
		name.setFont(onTrip || due
			? FontManager.getRunescapeBoldFont()
			: FontManager.getRunescapeSmallFont());
		name.setForeground(switchedOff ? COLOR_OFF : runColour(run.getStatus(), onTrip));
		name.setAlignmentX(LEFT_ALIGNMENT);
		text.add(name);

		// Always added, even when empty, so showDetails can fill it in place later. Its
		// colour depends on status, which is in the signature. A run not due yet keeps a
		// quieter status line, but grey only when the run is not possible.
		JLabel detail = new JLabel();
		detail.setFont(FontManager.getRunescapeSmallFont());
		detail.setForeground(switchedOff ? COLOR_OFF
			: due ? COLOR_ROUTE
			: impossible ? COLOR_SKIPPED : COLOR_QUIET);
		detail.setAlignmentX(LEFT_ALIGNMENT);
		text.add(detail);
		runDetails.put(run.getRunId(), detail);
		showRunDetail(detail, run.getDetail());

		gbc.gridx = 1;
		gbc.weightx = 1.0;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		row.add(text, gbc);

		JComponent accessory = accessory(settings, row, name, detail, icon, opened);
		if (accessory != null)
		{
			gbc.gridx = 2;
			gbc.weightx = 0;
			gbc.fill = GridBagConstraints.NONE;
			gbc.anchor = GridBagConstraints.CENTER;
			row.add(accessory, gbc);
		}
		return row;
	}

	/** Hidden rather than blank, so an empty detail takes no room. */
	private static void showRunDetail(JLabel detail, String text)
	{
		boolean anything = text != null && !text.isBlank();
		detail.setVisible(anything);
		detail.setText(anything ? text : "");
	}

	/**
	 * The control at a run row's right end: a checkbox for a run one setting switches, a
	 * chevron for a run with a settings block, or null for a run with neither. Either way a
	 * left click on the whole row does what the control does: it flips the box, or it opens
	 * and shuts the block.
	 */
	private JComponent accessory(FarmingRunSettings settings, JPanel row, JLabel name,
		JLabel detail, JLabel icon, JPanel opened)
	{
		if (settings == null)
		{
			return null;
		}
		FarmingSettingChoice only = settings.getRunSwitch();
		if (settings.getShape() == FarmingRunSettings.Shape.SWITCH && only != null)
		{
			JCheckBox box = runSwitch(only, settings.getRunId(), name, detail, icon);
			// A click anywhere on the row flips the box, as a click on a family row opens
			// it. The box itself takes its own clicks, so they never reach the row.
			row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			row.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					if (SwingUtilities.isLeftMouseButton(e))
					{
						box.doClick(0);
					}
				}
			});
			return box;
		}
		if (opened == null)
		{
			return null;
		}
		JLabel chevron = new JLabel();
		chevron.setFont(FontManager.getRunescapeSmallFont());
		chevron.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 2));
		showChevron(chevron, opened.isVisible());
		String runId = settings.getRunId();
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		row.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (!SwingUtilities.isLeftMouseButton(e))
				{
					return;
				}
				boolean open = !opened.isVisible();
				if (open)
				{
					openRuns.add(runId);
				}
				else
				{
					openRuns.remove(runId);
				}
				opened.setVisible(open);
				row.setOpaque(open);
				showChevron(chevron, open);
				container.revalidate();
				container.repaint();
			}
		});
		return chevron;
	}

	private static void showChevron(JLabel chevron, boolean open)
	{
		chevron.setText(open ? CHEVRON_OPEN : CHEVRON_SHUT);
		chevron.setForeground(open ? Color.WHITE : PanelColors.HEADER);
	}

	/**
	 * The checkbox on a run one setting switches. Dims the row at once, before the re-plan.
	 */
	private JCheckBox runSwitch(FarmingSettingChoice only, String runId, JLabel name,
		JLabel detail, JLabel icon)
	{
		Icon builtIcon = icon != null ? icon.getIcon() : null;
		JCheckBox box = FarmingPanel.checkbox(null);
		box.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
		box.setSelected(only.isOn());
		box.setToolTipText(switchTip(only.isOn()));
		// The row as built, put back by a re-tick. Untick then re-tick before the re-plan
		// leaves the preferences where they started, so no re-plan comes to redraw it.
		Color builtColour = name.getForeground();
		Font builtFont = name.getFont();
		Color builtDetailColour = detail.getForeground();
		String builtDetail = detail.getText();
		box.addActionListener(e ->
		{
			boolean on = box.isSelected();
			box.setToolTipText(switchTip(on));
			// Switching on restores the row as built, and the re-plan, which knows the
			// status, redraws it if that has moved.
			if (on)
			{
				name.setForeground(builtColour);
				name.setFont(builtFont);
				detail.setForeground(builtDetailColour);
				if (icon != null)
				{
					icon.setIcon(builtIcon instanceof DimmedIcon
						? ((DimmedIcon) builtIcon).getIcon() : builtIcon);
				}
				// Back in the in-place updates, so the next refresh brings the countdown up
				// to date.
				showRunDetail(detail, builtDetail);
				runDetails.put(runId, detail);
			}
			else
			{
				name.setForeground(COLOR_OFF);
				name.setFont(FontManager.getRunescapeSmallFont());
				detail.setForeground(COLOR_OFF);
				if (icon != null && builtIcon != null && !(builtIcon instanceof DimmedIcon))
				{
					icon.setIcon(new DimmedIcon(builtIcon));
				}
				// Out of the in-place updates, which would write the old status back before
				// the re-plan arrives.
				runDetails.remove(runId);
				showRunDetail(detail, "off");
			}
			revalidate();
			repaint();
			send(only.getKey(), String.valueOf(on));
		});
		return box;
	}

	private static String switchTip(boolean on)
	{
		return on ? "Switch this run off" : "Switch this run on";
	}

	/**
	 * A run's settings block, shown under its row while open: the crop grid, then the sites
	 * it visits, then the crops it plants in passing, then its checkboxes, then its labelled
	 * picks. Nothing here wraps, so the block can be rebuilt without the re-measure flicker.
	 */
	private JPanel buildRunSettings(FarmingRunSettings settings, IconProvider icons)
	{
		// As wide as the row above it, even when nothing in it stretches: a block of only a
		// grid and a checkbox otherwise stops at their width and leaves a strip of the
		// list's colour beside it.
		JPanel block = new JPanel()
		{
			@Override
			public Dimension getMaximumSize()
			{
				return new Dimension(Short.MAX_VALUE, getPreferredSize().height);
			}
		};
		block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
		block.setOpaque(true);
		block.setBackground(COLOR_OPEN);
		block.setAlignmentX(LEFT_ALIGNMENT);
		// Indented to the row's own inset. The row's text column sits too far right for a
		// four-wide grid to fit the sidebar.
		block.setBorder(BorderFactory.createEmptyBorder(0, 6, 7, 4));

		if (settings.getChoicesCaption() != null)
		{
			block.add(SiteList.caption(settings.getChoicesCaption(), 0));
		}
		block.add(SettingGrid.toggles(listOf(settings.getChoices()), icons, this::send));

		List<FarmingSettingChoice> sites = listOf(settings.getSites());
		if (!sites.isEmpty())
		{
			block.add(SiteList.caption("Patches", 6));
			block.add(new SiteList(sites, this::send));
		}

		List<FarmingSettingChoice> extras = listOf(settings.getExtras());
		if (!extras.isEmpty())
		{
			block.add(SiteList.caption("Also plant on this run", 6));
			block.add(SettingGrid.toggles(extras, icons, this::send));
		}
		for (FarmingSettingChoice choice : listOf(settings.getSwitches()))
		{
			JCheckBox box = FarmingPanel.checkbox(choice.getLabel());
			box.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
			box.setSelected(choice.isOn());
			box.setToolTipText(choice.getTip());
			box.addActionListener(e ->
				send(choice.getKey(), String.valueOf(box.isSelected())));
			block.add(box);
		}
		List<FarmingSettingPick> picks = listOf(settings.getPicks());
		for (int i = 0; i < picks.size(); i++)
		{
			JPanel row = SettingRow.of(picks.get(i), this::send);
			if (i == 0)
			{
				// Set apart from the grids above, as the extras' caption is.
				row.setBorder(BorderFactory.createEmptyBorder(8, 2, 0, 0));
			}
			block.add(row);
		}
		return block;
	}

	private void send(String key, String value)
	{
		if (settingCallback != null && key != null)
		{
			settingCallback.accept(key, value);
		}
	}

	/**
	 * @param onTrip whether the trip on screen has stops for this family. White (on the
	 *     trip now) outranks amber (due). A due family the trip left out stays amber.
	 */
	private static Color runColour(String status, boolean onTrip)
	{
		if (onTrip)
		{
			return Color.WHITE;
		}
		if (STATUS_DUE.equals(status))
		{
			return COLOR_DUE;
		}
		// Grey only for a run that cannot be done. Waiting, unseen or growing is not that.
		return notPossible(status) ? COLOR_SKIPPED : COLOR_ROUTE;
	}

	/**
	 * Whether the run cannot be done today: locked behind a level or unlock, or with no
	 * route to any of its stops. Both draw grey with a greyscale icon.
	 */
	static boolean notPossible(String status)
	{
		return STATUS_LOCKED.equals(status) || STATUS_NO_ROUTE.equals(status);
	}

	/**
	 * Whether today's trip has a stop for this family. A missing trip list covers nothing.
	 *
	 * <p>The null-id check comes first because the trip list is immutable and its
	 * {@code contains(null)} throws.
	 */
	private static boolean coversRun(FarmingAdvice advice, String runId)
	{
		List<String> onTrip = advice.getTripRuns();
		return runId != null && onTrip != null && onTrip.contains(runId);
	}

	private static Map<String, FarmingRunSettings> settingsByRun(FarmingAdvice advice)
	{
		Map<String, FarmingRunSettings> byRun = new HashMap<>();
		for (FarmingRunSettings settings : listOf(advice.getRunSettings()))
		{
			byRun.put(settings.getRunId(), settings);
		}
		return byRun;
	}

	/**
	 * Every run, scheduled or switched off, in the advice's fixed order, so no row moves when
	 * a run comes due or is switched. A run the order does not name goes last, in the order
	 * it came.
	 */
	static List<FarmingRunStatus> rowsOf(List<FarmingRunStatus> runs,
		List<FarmingRunStatus> off, List<String> order)
	{
		List<FarmingRunStatus> rows = new ArrayList<>(listOf(runs));
		rows.addAll(off);
		List<String> fixed = listOf(order);
		rows.sort(Comparator.comparingInt(run ->
		{
			// An immutable list's indexOf(null) throws.
			int at = run.getRunId() == null ? -1 : fixed.indexOf(run.getRunId());
			return at < 0 ? Integer.MAX_VALUE : at;
		}));
		return rows;
	}

	private static <T> List<T> listOf(List<T> list)
	{
		return list != null ? list : List.of();
	}
}
