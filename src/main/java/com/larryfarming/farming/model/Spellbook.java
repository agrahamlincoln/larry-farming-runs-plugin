package com.larryfarming.farming.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Which spellbook a run is written for. Herb runs are written once per book, because the
 * teleports change the fallback chains; every book walks the same circuit.
 */
@Getter
@RequiredArgsConstructor
public enum Spellbook {
    /** The run does not depend on a spellbook. */
    ANY("any"),
    ARCEUUS("Arceuus"),
    LUNAR("Lunar"),

    /**
     * No run is written for the standard book, but it carries most fallback teleports, so
     * castability checks need to name it.
     */
    STANDARD("standard");

    /** How to say this in a sentence: "you are on the Arceuus spellbook". */
    private final String displayName;
}
