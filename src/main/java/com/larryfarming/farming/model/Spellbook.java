package com.larryfarming.farming.model;

/**
 * Which spellbook a run is written for. Herb runs are written once per book,
 * because the teleports change both the patch order and the fallback chains.
 */
public enum Spellbook {
    /** The run does not depend on a spellbook. */
    ANY,
    ARCEUUS,
    LUNAR,

    /**
     * No run is written for the standard book, but it carries most fallback teleports, so
     * castability checks need to name it.
     */
    STANDARD;

    /** How to say this in a sentence: "you are on the Arceuus spellbook". */
    public String getDisplayName() {
        switch (this) {
            case ARCEUUS:
                return "Arceuus";
            case LUNAR:
                return "Lunar";
            case STANDARD:
                return "standard";
            default:
                return "any";
        }
    }
}
