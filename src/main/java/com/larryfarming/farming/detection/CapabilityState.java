package com.larryfarming.farming.detection;

import java.util.Collection;

/**
 * Whether a player can do something, allowing for the fact that a plugin often cannot
 * tell.
 *
 * <p>{@link #UNKNOWN} is a real answer, and it must never be treated as {@link #LACK}.
 * Much of what the routes lean on is invisible to the plugin, such as house layout,
 * portal attunements and whether a spirit tree is grown. A player with a fully built house
 * must not be told to skip half their patches because the plugin could not see inside it.
 */
public enum CapabilityState {
    HAVE,
    LACK,
    UNKNOWN;

    /**
     * Combines alternatives, where any one will do. One HAVE settles it even if everything
     * else is unknown. Empty yields LACK.
     */
    public static CapabilityState any(Collection<CapabilityState> states) {
        boolean sawUnknown = false;
        for (CapabilityState state : states) {
            if (state == HAVE) {
                return HAVE;
            }
            if (state == UNKNOWN) {
                sawUnknown = true;
            }
        }
        return sawUnknown ? UNKNOWN : LACK;
    }

    /**
     * Combines requirements that must all hold. One LACK settles it. Otherwise a single
     * UNKNOWN leaves the whole result UNKNOWN. Empty yields HAVE, so a rung that asks for
     * nothing is available to everyone.
     */
    public static CapabilityState all(Collection<CapabilityState> states) {
        boolean sawUnknown = false;
        for (CapabilityState state : states) {
            if (state == LACK) {
                return LACK;
            }
            if (state == UNKNOWN) {
                sawUnknown = true;
            }
        }
        return sawUnknown ? UNKNOWN : HAVE;
    }
}
