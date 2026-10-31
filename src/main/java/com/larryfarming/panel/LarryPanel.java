package com.larryfarming.panel;

import com.larryfarming.farming.FarmingPlanner;
import com.larryfarming.snapshot.FarmingAdvice;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Rectangle;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * The sidebar: the farm run as last planned, drawn by {@link FarmingPanel}.
 *
 * <p>Redraws when the planner publishes new advice. A ticker re-words the countdowns between
 * plans, so "ready in" lines keep moving while nothing in the game changes.
 */
public class LarryPanel extends PluginPanel
{
	/** Scrollbar width plus the right border and padding of the RuneLite sidebar icons. */
	private static final int SIDEBAR_TRIM = 14;
	/** The content width. Anything wider runs under the sidebar icons. */
	static final int CONTENT_WIDTH = PluginPanel.PANEL_WIDTH - SIDEBAR_TRIM;

	/** How often the countdowns are re-worded between plans. */
	private static final int COUNTDOWN_TICK_MS = 15_000;

	private final FarmingPanel farmingPanel;

	/** Re-words the countdowns between plans; runs only while the panel is on screen. */
	private final Timer countdownTicker;

	private volatile FarmingPlanner planner;
	private volatile IconProvider iconProvider;

	/** The advice version last handed to the EDT; -1 forces the next refresh through. */
	private long lastAdviceVersion = -1;

	/**
	 * While no account is logged in the panel asks the player to log in, whatever the planner
	 * still holds for the account that left. Volatile: set on the client thread, read by the
	 * ticker on the EDT.
	 */
	private volatile boolean loggedOut = true;

	/** The advice on screen, re-shown by the ticker. EDT only. */
	private FarmingAdvice shown;

	/** What "Report a problem" does; null hides nothing, the button just does nothing. */
	private volatile ReportActions reportActions;

	public LarryPanel(FarmingPanel farmingPanel)
	{
		super(false);
		this.farmingPanel = farmingPanel;
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		farmingPanel.setAlignmentX(LEFT_ALIGNMENT);
		JScrollPane scroll = new JScrollPane(wrapForScrolling(farmingPanel, reportRow()));
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		add(scroll, BorderLayout.CENTER);

		// Started in addNotify, not here: a panel built but never shown would otherwise tick
		// forever through a dropped classloader.
		countdownTicker = new Timer(COUNTDOWN_TICK_MS, e -> farmingPanel.display(shown, iconProvider));
		farmingPanel.display(null, null);
	}

	/** The planner whose advice the panel draws, or null to draw none. */
	public void setPlanner(FarmingPlanner planner)
	{
		this.planner = planner;
		this.lastAdviceVersion = -1;
	}

	public void setIconProvider(IconProvider iconProvider)
	{
		this.iconProvider = iconProvider;
	}

	/** What the "Report a problem" button does. */
	public void setReportActions(ReportActions actions)
	{
		this.reportActions = actions;
	}

	/**
	 * The foot of the tab: "Report a problem", under everything else, logged in or out. Rare,
	 * so it sits below the settings, but always visible: the tab hides no controls.
	 */
	private JPanel reportRow()
	{
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		row.setOpaque(false);
		row.setAlignmentX(LEFT_ALIGNMENT);
		// The farming panel's own 8px inset, so the button lines up with the tab above it.
		row.setBorder(BorderFactory.createEmptyBorder(4, 8, 10, 8));
		SmallButton report = new SmallButton("Report a problem");
		report.addActionListener(e ->
		{
			ReportActions actions = reportActions;
			if (actions != null)
			{
				ReportDialog.open(this, actions);
			}
		});
		row.add(report);
		row.setMaximumSize(new Dimension(Short.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	/**
	 * The sidebar opening re-shows the panel, and its rows wrapped against the width from when
	 * it was last on screen. Forget the last build so the redraw re-measures.
	 */
	@Override
	public void onActivate()
	{
		farmingPanel.forgetLastBuild();
		farmingPanel.display(shown, iconProvider);
	}

	@Override
	public void addNotify()
	{
		super.addNotify();
		countdownTicker.start();
	}

	@Override
	public void removeNotify()
	{
		countdownTicker.stop();
		super.removeNotify();
	}

	/** Stops the ticker. Call when tearing the panel down. */
	public void dispose()
	{
		countdownTicker.stop();
	}

	/**
	 * Draws the planner's advice if a new plan was published since the last call. Call from
	 * the thread that drives the planner, once per tick. The drawing runs on the EDT.
	 */
	public void refresh()
	{
		FarmingPlanner current = planner;
		if (current == null || loggedOut)
		{
			return;
		}
		long version = current.getAdviceVersion();
		if (version == lastAdviceVersion)
		{
			return;
		}
		lastAdviceVersion = version;
		FarmingAdvice advice = current.getAdvice();
		SwingUtilities.invokeLater(() -> show(advice));
	}

	/**
	 * Asks the player to log in. The planner still holds the account that left until the
	 * next login replaces it, so its advice stays off screen until then.
	 */
	public void showLoggedOut()
	{
		loggedOut = true;
		SwingUtilities.invokeLater(() -> show(null));
	}

	/** An account has loaded: the next {@link #refresh} draws its plan. */
	public void showLoggedIn()
	{
		if (loggedOut)
		{
			loggedOut = false;
			lastAdviceVersion = -1;
		}
	}

	/** Repaints after an icon arrives. Safe from any thread. */
	public void iconLoaded()
	{
		SwingUtilities.invokeLater(this::repaint);
	}

	private void show(FarmingAdvice advice)
	{
		shown = advice;
		farmingPanel.display(advice, iconProvider);
	}

	/**
	 * Packs a panel to the top of its scroll pane at a fixed width, so wrapping text
	 * knows where to break and short content does not stretch to fill the viewport.
	 */
	private static JPanel wrapForScrolling(JPanel content, JPanel foot)
	{
		JPanel fixedWidth = new FixedWidthPanel();
		fixedWidth.setLayout(new BoxLayout(fixedWidth, BoxLayout.Y_AXIS));
		fixedWidth.setBackground(ColorScheme.DARK_GRAY_COLOR);
		fixedWidth.add(content);
		fixedWidth.add(foot);

		JPanel northWrapper = new ScrollAnchoredPanel();
		northWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		northWrapper.add(fixedWidth, BorderLayout.NORTH);
		return northWrapper;
	}

	/**
	 * The scroll pane's view, which stops its contents from scrolling the viewport.
	 *
	 * <p>Rows rebuild while the player reads, and Swing components ask to be scrolled into
	 * view when added or focused. {@code scrollRectToVisible} propagates up the parent chain,
	 * so swallowing it here, directly below the viewport, blocks every such request. The
	 * scrollbar and mouse wheel drive the viewport directly and still work.
	 */
	private static class ScrollAnchoredPanel extends JPanel
	{
		ScrollAnchoredPanel()
		{
			super(new BorderLayout());
		}

		@Override
		public void scrollRectToVisible(Rectangle aRect)
		{
			// Empty on purpose. See class doc.
		}
	}

	/**
	 * A JPanel whose preferred width is {@link #CONTENT_WIDTH}, so text does not render
	 * behind the RuneLite sidebar icon strip.
	 */
	private static class FixedWidthPanel extends JPanel
	{
		@Override
		public Dimension getPreferredSize()
		{
			return new Dimension(CONTENT_WIDTH, super.getPreferredSize().height);
		}
	}
}
