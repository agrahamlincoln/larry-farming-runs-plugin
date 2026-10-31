package com.larryfarming.farming.engine;

/**
 * Whether a farming run is worth doing right now.
 *
 * <p>"Not now" splits four ways because each calls for a different message: waiting
 * (count down), locked (name what is missing), no route (name what blocks the way), and
 * unseen (admit we cannot tell).
 */
public enum RunStatus {

    /**
     * Something at one of the stops wants attention: a crop finished growing, a patch
     * caught disease or died, or a patch is standing empty.
     *
     * <p>An empty patch counts on purpose. A player who has never planted hardwood trees
     * has nothing growing to wait for, and a scheduler that only watched timers would
     * stay silent about the most valuable run in the game.
     */
    DUE,

    /** Everything is growing. There is a time to count down to. */
    PENDING,

    /** The player has not unlocked this run yet, and we can name what is missing. */
    LOCKED,

    /**
     * Unlocked, but no stop on the route proper has a route this player can take: every
     * one is a definite refusal, such as a run only fairy rings reach with every way to a
     * ring switched off. Like {@link #LOCKED}, nothing the player does today on the run
     * itself helps, so it is never due.
     *
     * <p>Judged on the variant {@link RunVariants} chose, the same one the walkthrough
     * shows, not on every version of the run.
     *
     * <p>Only a definite refusal counts. A route resting on something unread (an unseen
     * bank, a house whose town is unknown) is a guess, and a guess leaves the run as it is.
     */
    NO_ROUTE,

    /**
     * Not one of this run's patches has ever been observed, so there is nothing to
     * predict from.
     *
     * <p>Patch varbits only transmit while the player is standing in the patch's region,
     * so a fresh install knows nothing about anything planted before it. The first walk
     * past any patch settles it.
     */
    UNSEEN
}
