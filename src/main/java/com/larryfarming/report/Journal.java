package com.larryfarming.report;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;
import com.larryfarming.snapshot.FarmingAdvice;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

/**
 * What Larry saw and planned this session, for a bug report: the events he acted on, each
 * plan he published, checkpoints of everything the planner read, and his diagnostic notes.
 *
 * <p>Every entry is held as compact JSON text, bounded by size, dropping the oldest entries
 * first. Each is complete on its own; only {@link #writeSession} shortens plans and
 * checkpoints to their changes since the one before, so dropping an entry never breaks a
 * chain, and the parsing that takes runs off the client thread.
 *
 * <p>Nothing here names the account. The callers record items, varbits, patches and
 * settings, never a player name.
 *
 * <p>Client thread only, apart from {@link #note}, which any thread may call, and
 * {@link #snapshot}'s result, which is immutable.
 */
public final class Journal {

    /** The report format's version. Bump it for any change a reader would trip over. */
    public static final int SCHEMA = 1;

    /**
     * The characters of text the journal keeps to. Strings of plain JSON take a byte or two
     * a character, so this holds the journal near 16 MB at most.
     */
    static final long BUDGET_CHARS = 8_000_000L;
    /** Checkpoints kept at most; each holds the whole player state. */
    static final int MAX_CHECKPOINTS = 40;
    /** A full plan or checkpoint at least this often, so a reader can start partway. */
    private static final int FULL_EVERY = 25;

    /**
     * Advice fields left out of a plan entry: the settings blocks the panel draws, which
     * repeat the preferences. A changed setting is recorded as its own event.
     */
    private static final Set<String> SETTINGS_FIELDS = Set.of("runSettings", "travelGroups",
            "house", "runFirst", "waitForEveryPatch");

    /** One thing the journal recorded. Immutable once made. */
    public static final class Entry {
        public enum Kind {
            EVENT, PLAN, CHECKPOINT, NOTE
        }

        private final long seq;
        private final long atMillis;
        private final int tick;
        private final Kind kind;
        /** An event's type, a checkpoint's reason, or empty. */
        private final String label;
        /** A note's plain text; JSON for the other kinds. */
        private final String text;

        Entry(long seq, long atMillis, int tick, Kind kind, String label, String text) {
            this.seq = seq;
            this.atMillis = atMillis;
            this.tick = tick;
            this.kind = kind;
            this.label = label;
            this.text = text;
        }

        public long getSeq() {
            return seq;
        }

        public Kind getKind() {
            return kind;
        }

        public String getLabel() {
            return label;
        }

        long size() {
            return 64 + label.length() + text.length();
        }
    }

    private final Gson gson;
    private final LongSupplier clockMillis;
    private final IntSupplier tick;
    private final Deque<Entry> entries = new ArrayDeque<>();
    /** Notes from any thread, moved into {@link #entries} on the client thread. */
    private final Queue<Entry> pendingNotes = new ConcurrentLinkedQueue<>();
    private long nextSeq = 1;
    private long held;
    private int checkpoints;
    private long startedMillis;
    /** The last plan recorded, so an unchanged one is not recorded twice. */
    private String lastPlan;
    /** The last value {@link #changed} recorded under each type. */
    private final Map<String, JsonElement> lastValues = new HashMap<>();

    /**
     * @param gson        the client's Gson, for the advice
     * @param clockMillis the wall clock
     * @param tick        the game tick, or 0 while none is known
     */
    public Journal(Gson gson, LongSupplier clockMillis, IntSupplier tick) {
        this.gson = gson;
        this.clockMillis = clockMillis;
        this.tick = tick;
        this.startedMillis = clockMillis.getAsLong();
    }

    /** Forget everything and start a new session, as at a login. */
    public void startSession() {
        entries.clear();
        pendingNotes.clear();
        held = 0;
        checkpoints = 0;
        lastPlan = null;
        lastValues.clear();
        startedMillis = clockMillis.getAsLong();
    }

    /** An input Larry acted on, in Larry's own terms. */
    public void event(String type, JsonObject data) {
        add(Entry.Kind.EVENT, type, data.toString());
    }

    /**
     * A value Larry holds, as it changed: the changes from the last one recorded under
     * {@code type}, or the whole value the first time. Nothing when it did not change.
     */
    public void changed(String type, Object value) {
        JsonElement now = value == null ? JsonNull.INSTANCE : gson.toJsonTree(value);
        JsonElement was = lastValues.get(type);
        if (now.equals(was)) {
            return;
        }
        lastValues.put(type, now);
        JsonObject data = new JsonObject();
        if (was == null) {
            data.add("full", now);
        } else {
            data.add("diff", JsonDiff.between(was, now));
        }
        event(type, data);
    }

    /**
     * The advice as published, with what the trip has packed so far. Skipped when nothing
     * recorded changed since the last plan, as when only a settings block moved.
     */
    public void plan(FarmingAdvice advice, Set<Integer> packedThisTrip) {
        String text = planTree(gson, advice, packedThisTrip).toString();
        if (text.equals(lastPlan)) {
            return;
        }
        lastPlan = text;
        add(Entry.Kind.PLAN, advice == null ? "none" : "", text);
    }

    /** A plan entry's tree: the advice less its settings blocks, with the packed set. */
    public static JsonObject planTree(Gson gson, FarmingAdvice advice,
            Set<Integer> packedThisTrip) {
        JsonObject tree = advice == null ? new JsonObject()
                : gson.toJsonTree(advice).getAsJsonObject();
        for (String field : SETTINGS_FIELDS) {
            tree.remove(field);
        }
        JsonArray packed = new JsonArray();
        for (int id : new TreeSet<>(packedThisTrip)) {
            packed.add(id);
        }
        tree.add("packedThisTrip", packed);
        return tree;
    }

    /** Everything the planner read at this moment, so the plan can be rebuilt from it. */
    public void checkpoint(String why, JsonObject inputs) {
        add(Entry.Kind.CHECKPOINT, why, inputs.toString());
        checkpoints++;
        while (checkpoints > MAX_CHECKPOINTS) {
            dropOldestCheckpoint();
        }
    }

    /** One of Larry's diagnostic lines. Safe from any thread. */
    public void note(String text) {
        // Numbered when drained, so notes interleave with the client thread's entries.
        pendingNotes.add(new Entry(0, clockMillis.getAsLong(), tick.getAsInt(),
                Entry.Kind.NOTE, "", text));
    }

    /** Every entry held, oldest first, notes included. The list does not change after. */
    public Session snapshot() {
        drainNotes();
        return new Session(startedMillis, new ArrayList<>(entries));
    }

    /** How many entries are held. */
    public int size() {
        drainNotes();
        return entries.size();
    }

    private void add(Entry.Kind kind, String label, String text) {
        drainNotes();
        store(new Entry(nextSeq++, clockMillis.getAsLong(), tick.getAsInt(), kind, label,
                text));
    }

    private void drainNotes() {
        Entry note;
        while ((note = pendingNotes.poll()) != null) {
            store(new Entry(nextSeq++, note.atMillis, note.tick, Entry.Kind.NOTE, "",
                    note.text));
        }
    }

    private void store(Entry entry) {
        entries.addLast(entry);
        held += entry.size();
        while (held > BUDGET_CHARS && entries.size() > 1) {
            Entry dropped = entries.removeFirst();
            held -= dropped.size();
            if (dropped.kind == Entry.Kind.CHECKPOINT) {
                checkpoints--;
            }
        }
    }

    /** The oldest checkpoint but the first, which anchors the start of the session. */
    private void dropOldestCheckpoint() {
        boolean first = true;
        for (Iterator<Entry> it = entries.iterator(); it.hasNext(); ) {
            Entry entry = it.next();
            if (entry.kind != Entry.Kind.CHECKPOINT) {
                continue;
            }
            if (first) {
                first = false;
                continue;
            }
            it.remove();
            held -= entry.size();
            checkpoints--;
            return;
        }
    }

    /** A session's entries, fixed at the moment they were taken. */
    public static final class Session {
        private final long startedMillis;
        private final List<Entry> entries;

        Session(long startedMillis, List<Entry> entries) {
            this.startedMillis = startedMillis;
            this.entries = entries;
        }

        public List<Entry> getEntries() {
            return entries;
        }
    }

    /**
     * Writes a session as the report format's {@code session} object: its start and its
     * entries, each plan and checkpoint as its changes since the one before when that is
     * the shorter. Pure, so it runs off the client thread, where its parsing belongs.
     */
    public static void writeSession(JsonWriter out, Session session) throws IOException {
        out.beginObject();
        out.name("started").value(session.startedMillis);
        out.name("entries").beginArray();
        JsonElement lastPlan = null;
        JsonElement lastCheckpoint = null;
        int plansSinceFull = 0;
        int checkpointsSinceFull = 0;
        for (Entry entry : session.entries) {
            out.beginObject();
            out.name("seq").value(entry.seq);
            out.name("at").value(entry.atMillis);
            out.name("tick").value(entry.tick);
            out.name("kind").value(entry.kind.name().toLowerCase(Locale.ROOT));
            switch (entry.kind) {
                case EVENT:
                    out.name("type").value(entry.label);
                    out.name("data").jsonValue(entry.text);
                    break;
                case NOTE:
                    out.name("text").value(entry.text);
                    break;
                case PLAN: {
                    JsonElement tree = new JsonParser().parse(entry.text);
                    plansSinceFull = writeTree(out, entry.text, tree, lastPlan, plansSinceFull);
                    lastPlan = tree;
                    break;
                }
                case CHECKPOINT: {
                    out.name("why").value(entry.label);
                    JsonElement tree = new JsonParser().parse(entry.text);
                    checkpointsSinceFull = writeTree(out, entry.text, tree, lastCheckpoint,
                            checkpointsSinceFull);
                    lastCheckpoint = tree;
                    break;
                }
                default:
                    break;
            }
            out.endObject();
        }
        out.endArray();
        out.endObject();
    }

    /**
     * Writes a tree as {@code "full"}, or as {@code "diff"} from {@code previous} when that is
     * under half the length. Returns how many diffs have run since the last full.
     */
    private static int writeTree(JsonWriter out, String full, JsonElement tree,
            JsonElement previous, int sinceFull) throws IOException {
        if (previous != null && sinceFull + 1 < FULL_EVERY) {
            String diff = JsonDiff.between(previous, tree).toString();
            if (diff.length() * 2 < full.length()) {
                out.name("diff").jsonValue(diff);
                return sinceFull + 1;
            }
        }
        out.name("full").jsonValue(full);
        return 0;
    }

    /** A saved session read back, or null when the text is not one. */
    static JsonObject parseSession(String text) {
        try {
            JsonElement parsed = new JsonParser().parse(text);
            return parsed.isJsonObject() && parsed.getAsJsonObject().has("entries")
                    ? parsed.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
