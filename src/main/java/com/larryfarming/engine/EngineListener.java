package com.larryfarming.engine;

/**
 * Hears what the {@link StateEngine} does to the account it holds, for code that plans
 * against the same player state without the engine knowing what it plans.
 *
 * <p>Every call arrives on the thread driving the engine, which in the plugin is the client
 * thread, and from inside the engine call that caused it.
 */
public interface EngineListener {

    /**
     * The engine has just forgotten the last account and is about to load the next one's
     * saved state. Nothing has been announced for the new account yet, so state loaded here
     * is in place for the load's first {@link #stateChanged}.
     */
    default void accountLoading() {
    }

    /** A varbit the engine holds took a new value, from a live change event. */
    default void varbitChanged(int varbitId, int value) {
    }

    /**
     * The engine has folded in a batch of changes: a load, or the events of one tick. Fires
     * whether or not anything the listener reads moved.
     */
    default void stateChanged() {
    }
}
