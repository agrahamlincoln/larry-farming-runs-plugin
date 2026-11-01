package com.larryfarming.farming.engine;

/**
 * Whether a farming run is worth doing right now.
 *
 * <p>"Not now" splits three ways because each draws differently: waiting (count down),
 * locked (grey, naming what is missing or what blocks the way), and unseen (admit we
 * cannot tell).
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

    /**
     * Nothing the player does today on the run itself helps, so it is never due. Either the
     * run is not unlocked yet, or no stop on the route proper has a route this player can
     * take: every one a definite refusal, such as a run only fairy rings reach with every
     * way to a ring switched off. The detail names what is missing or what blocks the way.
     *
     * <p>Routes are judged on the variant {@link RunVariants} chose, the one the walkthrough
     * shows. A route resting on something unread (an unseen bank, a house whose town is
     * unknown) is a guess, and a guess leaves the run as it is.
     */
    LOCKED,

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
