package com.larryfarming.report;

import org.slf4j.Logger;
import org.slf4j.helpers.MessageFormatter;

/**
 * Larry's diagnostic lines: each goes to the log at DEBUG and, while a journal is installed,
 * into the journal too, whatever level the client logs at. The client logs at INFO unless
 * told otherwise, so without this a player's bug report would carry none of them.
 *
 * <p>The same rules as any log line: never a player name or other account identifier.
 */
public final class Trace {

    private static volatile Journal journal;

    private Trace() {
    }

    /** Sends every line from now on to {@code sink} as well, or to none when null. */
    public static void install(Journal sink) {
        journal = sink;
    }

    /** As {@link Logger#debug(String, Object...)}, recorded in the journal as well. */
    public static void debug(Logger log, String format, Object... args) {
        log.debug(format, args);
        Journal sink = journal;
        if (sink != null) {
            sink.note(MessageFormatter.arrayFormat(format, args).getMessage());
        }
    }
}
