package com.larryfarming.report;

import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import com.larryfarming.engine.StateEngine;
import com.larryfarming.farming.FarmingPlanner;
import com.larryfarming.persistence.ConfigStore;
import com.larryfarming.snapshot.FarmingAdvice;
import com.larryfarming.snapshot.FarmingRunStatus;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

/**
 * Runs the journal for the logged-in account: starts a session at each login, takes
 * checkpoints when a trip starts or a run's status changes, keeps the session saved in the
 * plugin's own data directory, and builds a report from it.
 *
 * <p>The file in the data directory holds the account's latest session, so a report made
 * after a relog or a crash still has the run before. The file's name is a hash of the RS
 * profile key, which stays on the player's machine; the report itself names no account.
 *
 * <p>Client thread, apart from the work it hands to {@code io}. {@code io} must run its tasks
 * one at a time in order, as the client's executor does: a login's read of the previous
 * session then always lands before the new session's first save replaces it.
 */
@Slf4j
public final class ReportService {

    /** A save every this many ticks, about five minutes, so a crash loses little. */
    private static final int SAVE_EVERY_TICKS = 500;

    /** The suffix of the file a save writes before it takes the session file's place. */
    private static final String PART = ".part";

    /** What a report says about where it came from, beside the player's own words. */
    public static final class Origin {
        private final String larry;
        private final String runelite;
        private final String profileType;
        private final int ironman;

        /**
         * @param larry       Larry's version as the panel shows it, "v1.0.0", or "dev"
         * @param runelite    the client's version
         * @param profileType the RuneLite profile type, such as STANDARD
         * @param ironman     the raw value of the game's ironman varbit
         */
        public Origin(String larry, String runelite, String profileType, int ironman) {
            this.larry = larry;
            this.runelite = runelite;
            this.profileType = profileType;
            this.ironman = ironman;
        }

        public String getLarry() {
            return larry;
        }

        public String getRunelite() {
            return runelite;
        }

        public String getProfileType() {
            return profileType;
        }
    }

    private final Journal journal;
    private final ConfigStore store;
    private final String group;
    private final StateEngine engine;
    private final FarmingPlanner planner;
    /** The data directory, or null when it could not be opened; nothing is saved then. */
    private final Filepath directory;
    private final Executor io;
    private final LongSupplier clockMillis;

    /** The account the session belongs to, by file name; null while logged out. */
    private String sessionFile;
    /** The account the previous session belongs to, by file name, or null. */
    private String previousFile;
    /** The previous session as read from its file, or null. Written by the {@code io} task. */
    private volatile String previousText;
    /** The previous session when it ended in this client run, or null. */
    private Journal.Session previousSession;
    private FarmingAdvice lastAdvice;
    private Map<String, String> lastStatuses = new HashMap<>();
    private int ticksSinceSave;

    public ReportService(Journal journal, ConfigStore store, String group, StateEngine engine,
            FarmingPlanner planner, Filepath directory, Executor io, LongSupplier clockMillis) {
        this.journal = journal;
        this.store = store;
        this.group = group;
        this.engine = engine;
        this.planner = planner;
        this.directory = directory;
        this.io = io;
        this.clockMillis = clockMillis;
    }

    /**
     * An account has loaded, its login already recorded. After a world hop the session goes
     * on. Otherwise this account's session starts here, with its previous one read back from
     * its file unless this client run still holds it.
     */
    public void accountLoaded() {
        String file = fileFor(store.rsProfileKey());
        if (file.equals(sessionFile)) {
            checkpoint("login");
            return;
        }
        if (sessionFile != null) {
            loggedOut();
        }
        if (!file.equals(previousFile)) {
            previousSession = null;
            previousText = null;
            previousFile = file;
            if (directory != null) {
                // Read off the client thread, and before any later save of this account.
                io.execute(() -> previousText = read(file));
            }
        }
        sessionFile = file;
        lastAdvice = null;
        lastStatuses = new HashMap<>();
        ticksSinceSave = 0;
        checkpoint("login");
    }

    /**
     * The account logged out: its session is saved, and kept in memory as the previous one,
     * since the save may not have landed when the next login wants it. The journal starts
     * afresh either way, so a login cut short leaves nothing for the next account's session.
     */
    public void loggedOut() {
        if (sessionFile != null) {
            save();
            previousSession = journal.snapshot();
            previousText = null;
            previousFile = sessionFile;
            sessionFile = null;
        }
        journal.startSession();
    }

    /**
     * After the tick's re-plan: a checkpoint when a trip starts or a run's status moves,
     * where runs most often go wrong, and a save every few minutes.
     */
    public void afterTick() {
        FarmingAdvice advice = planner.getAdvice();
        if (sessionFile != null && advice != lastAdvice) {
            boolean wasUnderway = lastAdvice != null && lastAdvice.isUnderway();
            Map<String, String> statuses = statuses(advice);
            if (advice != null && advice.isUnderway() && !wasUnderway) {
                checkpoint("trip started");
            } else if (lastAdvice != null && !statuses.equals(lastStatuses)) {
                checkpoint("run status");
            }
            lastAdvice = advice;
            lastStatuses = statuses;
        }
        if (++ticksSinceSave >= SAVE_EVERY_TICKS) {
            ticksSinceSave = 0;
            save();
        }
    }

    /**
     * Saves the session as it stands, off the client thread. Written beside the session file
     * first, so a crash mid-write leaves the last good save, which {@link #read} falls back
     * to.
     */
    public void save() {
        if (sessionFile == null || directory == null) {
            return;
        }
        Journal.Session session = journal.snapshot();
        String file = sessionFile;
        io.execute(() -> {
            try {
                Filepath part = directory.join(file + PART);
                part.write(sessionText(session));
                Filepath target = directory.join(file);
                target.deleteIfExists();
                part.moveTo(target);
            } catch (IOException | RuntimeException e) {
                log.debug("Could not save the bug report journal", e);
            }
        });
    }

    /**
     * Takes a last checkpoint, then builds the report on the {@code io} thread and hands its
     * text to {@code ready} there, or runs {@code failed} there if it could not be built.
     */
    public void buildReport(String message, Origin origin, Consumer<String> ready,
            Runnable failed) {
        if (sessionFile != null) {
            checkpoint("report");
        }
        Journal.Session session = journal.snapshot();
        // Logged in, the previous session goes in only if it is this account's, so one
        // report never mixes two accounts' runs.
        boolean ours = sessionFile == null || sessionFile.equals(previousFile);
        Journal.Session earlier = ours ? previousSession : null;
        boolean readEarlier = ours && earlier == null;
        long made = clockMillis.getAsLong();
        io.execute(() -> {
            try {
                // Read here, on io, so the login's read of the file has landed.
                String previous = earlier != null ? sessionText(earlier)
                        : readEarlier ? previousText : null;
                ready.accept(reportText(message, origin, previous, session, made));
            } catch (IOException | RuntimeException e) {
                log.warn("Could not build the bug report", e);
                failed.run();
            }
        });
    }

    private void checkpoint(String why) {
        if (engine.getPlayerState() == null) {
            return;
        }
        // The second the plan on screen was made at, so a plan rebuilt from the checkpoint
        // counts down as that one did.
        long planned = planner.getPlannedAtEpochSec();
        JsonObject inputs = Checkpoints.capture(store, group, engine, planner,
                planned > 0 ? planned : clockMillis.getAsLong() / 1000L);
        journal.checkpoint(why, inputs);
    }

    private static Map<String, String> statuses(FarmingAdvice advice) {
        Map<String, String> statuses = new HashMap<>();
        if (advice == null || advice.getRuns() == null) {
            return statuses;
        }
        for (FarmingRunStatus run : advice.getRuns()) {
            statuses.put(run.getRunId(), run.getStatus());
        }
        return statuses;
    }

    /** The session saved for an account, or null. A save cut short leaves its part file. */
    private String read(String file) {
        String text = readSession(file);
        return text != null ? text : readSession(file + PART);
    }

    private String readSession(String file) {
        try {
            Filepath path = directory.join(file);
            if (!path.exists()) {
                return null;
            }
            StringBuilder text = new StringBuilder();
            try (Reader reader = path.openReader()) {
                char[] buffer = new char[8192];
                int read;
                while ((read = reader.read(buffer)) >= 0) {
                    text.append(buffer, 0, read);
                }
            }
            // A file that does not parse, from a crash mid-write say, is no session.
            return Journal.parseSession(text.toString()) != null ? text.toString() : null;
        } catch (IOException | RuntimeException e) {
            log.debug("Could not read the bug report journal", e);
            return null;
        }
    }

    /** The session as the data directory keeps it: one {@code session} object. */
    static String sessionText(Journal.Session session) throws IOException {
        StringWriter text = new StringWriter();
        try (JsonWriter out = new JsonWriter(text)) {
            Journal.writeSession(out, session);
        }
        return text.toString();
    }

    /** The whole report: where it came from, the player's words, and both sessions. */
    static String reportText(String message, Origin origin, String previous,
            Journal.Session session, long madeMillis) throws IOException {
        StringWriter text = new StringWriter();
        try (JsonWriter out = new JsonWriter(text)) {
            out.beginObject();
            out.name("schema").value(Journal.SCHEMA);
            out.name("larry").value(origin.larry);
            out.name("runelite").value(origin.runelite);
            out.name("made").value(madeMillis);
            out.name("message").value(Objects.toString(message, ""));
            out.name("account").beginObject();
            out.name("profileType").value(origin.profileType);
            out.name("ironman").value(origin.ironman);
            out.endObject();
            out.name("sessions").beginArray();
            if (previous != null) {
                out.jsonValue(previous);
            }
            Journal.writeSession(out, session);
            out.endArray();
            out.endObject();
        }
        return text.toString();
    }

    /** "journal-" and the first 16 hex digits of the profile key's SHA-256. */
    static String fileFor(String profileKey) {
        String key = Objects.toString(profileKey, "none");
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder("journal-");
            for (int i = 0; i < 8; i++) {
                hex.append(String.format(Locale.ROOT, "%02x", hash[i]));
            }
            return hex.append(".json").toString();
        } catch (NoSuchAlgorithmException e) {
            // Every Java platform has SHA-256.
            throw new IllegalStateException(e);
        }
    }
}
