package com.larryfarming.overlay;

import com.larryfarming.snapshot.FarmingRunStatus;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.runelite.api.MenuAction;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.infobox.InfoBox;

/**
 * One box naming the farming run to do next, shown only while a run is due.
 *
 * <p>It carries no idle countdown on purpose: a box that is always on screen stops being
 * read. The countdown to the next run lives in the tooltip.
 *
 * <p>The plugin adds the box when something is due and removes it when nothing is.
 */
public class FarmingAdvisorInfoBox extends InfoBox {

    /** Actionable amber, the colour this plugin's infoboxes use for "ready". */
    private static final Color COLOR_DUE = new Color(255, 200, 50);

    private final Supplier<List<FarmingRunStatus>> runs;

    // RuneLite calls getText() and getTooltip() every frame for every visible box, so both
    // strings are rebuilt only when the supplier returns a new run list. Render calls are
    // single-threaded, so no locking.
    private String textCache;
    private String tooltipCache;
    private List<FarmingRunStatus> cachedRuns;

    /**
     * @param runs supplies every run and its status, best first; the same list the
     *             side panel draws, so the two agree about what is due
     */
    public FarmingAdvisorInfoBox(BufferedImage image, Plugin plugin, Supplier<List<FarmingRunStatus>> runs) {
        super(image, plugin);
        this.runs = runs;
        // Right-clicking the box opens the side panel.
        // The list must be mutable: InfoBoxManager.addInfoBox appends its own
        // "Detach"/"Flip" entries, and an immutable list makes addInfoBox throw.
        List<OverlayMenuEntry> menu = new ArrayList<>(1);
        menu.add(new OverlayMenuEntry(MenuAction.RUNELITE_INFOBOX, MENU_OPTION, MENU_TARGET));
        setMenuEntries(menu);
    }

    /** The right-click entry that opens the side panel, matched by the plugin. */
    public static final String MENU_OPTION = "View";
    public static final String MENU_TARGET = "Farming runs";

    @Override
    public String getText() {
        refreshCache();
        return textCache;
    }

    @Override
    public Color getTextColor() {
        return COLOR_DUE;
    }

    @Override
    public String getTooltip() {
        refreshCache();
        return tooltipCache;
    }

    private void refreshCache() {
        List<FarmingRunStatus> current = runs.get();
        if (current == cachedRuns && textCache != null) {
            return;
        }
        cachedRuns = current;

        List<FarmingRunStatus> due = due(current);
        if (due.isEmpty()) {
            // Reachable only in the frame before the plugin removes the box.
            textCache = "";
            tooltipCache = "Nothing due";
            return;
        }

        // A count of the other due runs, never a name. The plugin sets the icon to the top
        // run's item. An infobox is 35 pixels wide and InfoBoxComponent centres text at
        // (size - stringWidth) / 2, so text wider than the box spills out of both sides.
        textCache = due.size() == 1 ? "" : "+" + (due.size() - 1);

        StringBuilder tooltip = new StringBuilder("Farming");
        for (FarmingRunStatus run : due) {
            appendLine(tooltip, "", run, " — ");
        }
        FarmingRunStatus next = soonest(current);
        if (next != null) {
            appendLine(tooltip, "Next: ", next, " ");
        }
        tooltipCache = tooltip.toString();
    }

    /** A tooltip line: the run's name, then its detail after {@code gap} when it has one. */
    private static void appendLine(StringBuilder tooltip, String lead, FarmingRunStatus run, String gap) {
        tooltip.append("</br>").append(lead).append(run.getRunName());
        if (run.getDetail() != null && !run.getDetail().isEmpty()) {
            tooltip.append(gap).append(run.getDetail());
        }
    }

    private static List<FarmingRunStatus> due(List<FarmingRunStatus> runs) {
        List<FarmingRunStatus> due = new ArrayList<>();
        if (runs == null) {
            return due;
        }
        for (FarmingRunStatus run : runs) {
            if ("DUE".equals(run.getStatus())) {
                due.add(run);
            }
        }
        return due;
    }

    private static FarmingRunStatus soonest(List<FarmingRunStatus> runs) {
        FarmingRunStatus soonest = null;
        if (runs == null) {
            return null;
        }
        for (FarmingRunStatus run : runs) {
            if (!"PENDING".equals(run.getStatus()) || run.getReadyAtEpochSec() <= 0) {
                continue;
            }
            if (soonest == null || run.getReadyAtEpochSec() < soonest.getReadyAtEpochSec()) {
                soonest = run;
            }
        }
        return soonest;
    }

    /** True when at least one run is due; the plugin shows the box only then. */
    public static boolean anythingDue(List<FarmingRunStatus> runs) {
        return !due(runs).isEmpty();
    }
}
