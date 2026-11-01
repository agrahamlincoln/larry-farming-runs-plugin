package com.larryfarming.farming.model;

import com.larryfarming.engine.farming.PatchType;
import java.util.List;
import lombok.*;

/** One repeatable farming activity: an ordered set of stops, and what unlocks it. */
@Value
@Builder
public class FarmingRun {

    @NonNull
    RunId id;

    @NonNull
    Spellbook spellbook;

    @NonNull
    UnlockGate unlock;

    /**
     * The town this run prefers its respawn point set to, or null when it has no
     * preference.
     *
     * <p>Advisory, not a requirement. Runs prefer different respawn points, and switching costs a trip across the map. The run is planned
     * around wherever the respawn point actually is.
     */
    String respawnTown;

    @Singular("stop")
    List<RunStop> stops;

    /**
     * The patch type whose ticked crops this run plants, or null when the shared seed
     * policy decides. The player's ticks pick the crops, best first; a player out of the
     * best seed drops a tier rather than skipping the run.
     *
     * <p>A run states its seeds in at most one way: this type, or a stop that names its
     * crop outright ({@link RunStop.Builder#plants}). Stating neither hands the patch to the
     * shared herb rotation. Stating both packs the same seed twice.
     */
    PatchType seedPatchType;
}
