package com.larryfarming.farming.detection;

/** What the player's house, as last seen, says about one thing that might be built in it. */
public enum HouseSighting {
    /** Seen built, or a Nexus slot read holding it. */
    BUILT,
    /** Looked for and not there. */
    ABSENT,
    /** Not looked for yet, so nothing is known. */
    UNSEEN
}
