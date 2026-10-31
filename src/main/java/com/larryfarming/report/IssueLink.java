package com.larryfarming.report;

import com.larryfarming.snapshot.FarmingAdvice;
import com.larryfarming.snapshot.FarmingRunStatus;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The link that opens a new issue for Larry: a title naming the runs and the day, and the
 * player's words in the body. The report itself is too big for any link, so the body asks
 * the player to attach the file they saved.
 */
public final class IssueLink {

    /** Where Larry's issues are filed. */
    static final String NEW_ISSUE =
            "https://github.com/agrahamlincoln/larry-farming-runs-plugin/issues/new";

    /**
     * The longest body the link carries, encoded. GitHub refuses an over-long link with
     * "414 URI Too Long", and its documentation gives no figure, so this stays well short.
     */
    static final int LONGEST_BODY = 5500;

    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private IssueLink() {
    }

    /**
     * The runs on the trip shown, by their names in the panel, in the trip's order. Empty
     * when nothing is planned.
     */
    public static List<String> runsOf(FarmingAdvice advice) {
        List<String> names = new ArrayList<>();
        if (advice == null || advice.getTripRuns() == null || advice.getRuns() == null) {
            return names;
        }
        for (String id : advice.getTripRuns()) {
            for (FarmingRunStatus run : advice.getRuns()) {
                if (run.getRunId().equals(id)) {
                    names.add(run.getRunName());
                }
            }
        }
        return names;
    }

    /**
     * "Bug report: Herb run and Tree run, 27 Oct 2026", or "Bug report, 27 Oct 2026" with no
     * run planned.
     */
    static String title(List<String> runs, LocalDate day) {
        String when = DAY.format(day);
        if (runs.isEmpty()) {
            return "Bug report, " + when;
        }
        String named = runs.size() == 1 ? runs.get(0)
                : String.join(", ", runs.subList(0, runs.size() - 1)) + " and "
                        + runs.get(runs.size() - 1);
        return "Bug report: " + named + ", " + when;
    }

    /** The new-issue link for {@code message}, with the versions and the kind of world. */
    public static String forMessage(String message, ReportService.Origin origin,
            List<String> runs, LocalDate day) {
        String words = message == null ? "" : message.trim();
        // The profile type names the kind of world, standard or a league, not the account.
        String worlds = origin.getProfileType().toLowerCase(Locale.ROOT).replace('_', ' ');
        String footer = "\n\nLarry " + origin.getLarry() + ", RuneLite " + origin.getRunelite()
                + ", " + worlds + " worlds\n\n"
                + "Please drag the report you saved into this box. Its name starts with "
                + "larry-report and ends with .json.";
        String body = "**What went wrong**\n" + words;
        // Cut the player's words, never the footer, until the link fits.
        while (encode(body + footer).length() > LONGEST_BODY && !body.isEmpty()) {
            body = body.substring(0, Math.max(0, body.length() - 200));
        }
        return NEW_ISSUE + "?title=" + encode(title(runs, day)) + "&body="
                + encode(body + footer);
    }

    private static String encode(String text) {
        try {
            // URLEncoder is a form encoder and writes a space as "+"; "%20" reads as a space
            // in any URL query.
            return URLEncoder.encode(text, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            // Every Java platform has UTF-8.
            throw new IllegalStateException(e);
        }
    }
}
