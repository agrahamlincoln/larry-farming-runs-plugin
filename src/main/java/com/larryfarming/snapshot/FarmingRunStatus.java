package com.larryfarming.snapshot;

import com.google.gson.annotations.SerializedName;
import lombok.Value;

/**
 * One farming run, and whether it is worth doing right now.
 *
 * <p>Everything is already decided and worded. The panel and the infobox both read this
 * and neither works anything out.
 */
@Value
public class FarmingRunStatus {

    /** The activity, as {@code RunId.name()}, so a click can ask for this run back. */
    @SerializedName("runId") String runId;

    /** Named for a player: "Hardwood run". */
    @SerializedName("runName") String runName;

    /**
     * {@code RunStatus.name()}: DUE, PENDING, LOCKED, NO_ROUTE or UNSEEN. A run the player
     * has switched off reads OFF ({@code RunSettings.STATUS_OFF}).
     */
    @SerializedName("status") String status;

    /**
     * The one line that goes beside the name: "3 patches ready", "in 2d 4h",
     * "needs 65 Farming", "not seen these patches yet". Worded here, not in the panel.
     */
    @SerializedName("detail") String detail;

    /**
     * When the first patch finishes growing, or 0 when there is nothing to count down
     * to. Only meaningful while {@link #getStatus()} is PENDING.
     *
     * <p>A raw second rather than a formatted string so the panel can tick it down in
     * place without a structural rebuild of the row.
     */
    @SerializedName("readyAtEpochSec") long readyAtEpochSec;

    /** How many patches want attention. */
    @SerializedName("actionableCount") int actionableCount;

    /** The run's characteristic item, for the row icon and the infobox. */
    @SerializedName("iconItemId") int iconItemId;

    /**
     * True when this verdict rests on predictions rather than on patches seen recently.
     * Prediction is the normal case: a patch only reports while the player stands beside it.
     */
    @SerializedName("predicted") boolean predicted;

    /**
     * True when the player is out on this run right now and one of its woven-in extras is
     * still to do (an empty Ortus Farm patch, whiteberries ready at the Monastery), with
     * the run proper already worked out.
     *
     * <p>Kept apart from {@link #getStatus()}: extras are things to do while out there, not
     * reasons to go, so they must not make a run due. This flag tells a finished trip from
     * one abandoned early, so the walkthrough does not move to another run before the last
     * stop. Requiring "right now" keeps a run with a leftover extra from reading as an
     * unfinished trip for days.
     */
    @SerializedName("tripUnfinished") boolean tripUnfinished;

    /**
     * True when the detail names work in the contract's own patch ("harvest your
     * avantoes") rather than an errand only Guildmaster Jane can settle.
     *
     * <p>The walkthrough borrows {@link #detail} for the guild's row. A patch-work detail
     * would repeat the row's own harvest clause, so the walkthrough leads with the detail
     * only for Jane's errands.
     *
     * <p>Set where the verdict is composed, not inferred from the wording.
     */
    @SerializedName("aboutThePatch") boolean aboutThePatch;
}
