package com.larryfarming.farming.detection;

import lombok.Value;

/**
 * An answer about one capability, together with where the answer came from and a clause
 * explaining it.
 *
 * <p>The advisor shows the reason when it tells a player to skip a patch, so the skip reads
 * as a judgement rather than a fault. The reason also makes a wrong answer diagnosable.
 */
@Value
public class CapabilityVerdict {

    /** Where an answer came from, which decides how much to trust it. */
    public enum Source {
        /** Read from game state: an item held, a varbit set, a level reached. */
        DETECTED,
        /**
         * Set by the player in the panel: a house item declared before Larry has seen it, or
         * a travel method switched off.
         */
        DECLARED,
        /** Worked out from something else, such as a diary tier implying its reward. */
        INFERRED,
        /** Nothing to go on. */
        UNDETERMINED
    }

    CapabilityState state;
    Source source;

    /** One clause, phrased for a player rather than a developer. */
    String because;

    public static CapabilityVerdict have(Source source, String because) {
        return new CapabilityVerdict(CapabilityState.HAVE, source, because);
    }

    public static CapabilityVerdict lack(Source source, String because) {
        return new CapabilityVerdict(CapabilityState.LACK, source, because);
    }

    public static CapabilityVerdict unknown(String because) {
        return new CapabilityVerdict(CapabilityState.UNKNOWN, Source.UNDETERMINED, because);
    }
}
