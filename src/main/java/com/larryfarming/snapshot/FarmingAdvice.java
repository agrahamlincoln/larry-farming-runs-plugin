package com.larryfarming.snapshot;

import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * A farming run, worked out for this player: the stops in the default route's order, each with
 * the best route they can actually take.
 *
 * <p>Everything here is already decided. The panel only shows it; route choice stays in
 * the engine.
 */
@Value
@Builder(toBuilder = true)
public class FarmingAdvice {

    /** The run this walks, named for a player: "Herb run", or "Farm run" for a trip. */
    String runName;

    /**
     * Every crop family with a stop on what is showing, as {@code RunId.name()}, in the
     * order the trip reaches them. One entry for a single run, so trips and single runs read
     * the same way.
     *
     * <p>Order is the circuit's, not the ranking's: a trip opening at the Farming Guild
     * covers the contract first whatever the scheduler ranked it.
     */
    List<String> tripRuns;

    /**
     * True when the player is partway round: a patch on this walkthrough was seen growing
     * within one trip's span, in a family with an arrival still to make. The packing list
     * then describes a bank the player has already left, so the panel heads it as what the
     * rest of the run needs rather than what to take.
     *
     * <p>A primitive, so every builder path that never looks reads false.
     */
    boolean underway;

    /** The trip stop by stop, in the order it is walked. Derive stop counts from this list. */
    List<FarmingStopAdvice> stops;

    /** Gear to put on now, then gear to work toward. Already grouped and ordered. */
    List<FarmingUpgrade> upgrades;

    /** What to have in the bag: tools, seeds, and what the chosen routes need. */
    List<FarmingCarryItem> carry;

    /**
     * What the first stretch, up to the first later bank, needs in the bag: the "now" half
     * of {@link #getCarry()}. The rest comes out of the later banks, whose rows list it.
     * Empty when no later bank splits the trip, where the whole list is needed now, and once
     * underway, where the packing list is not drawn.
     */
    @Builder.Default
    List<FarmingCarryItem> carryNow = List.of();

    /**
     * What the run plants, in one line: "2 toadflax, 5 irit". Empty when nothing can be
     * planted, in which case {@link #getSeedWarning()} says why.
     */
    String seedSummary;

    /**
     * What is missing or unknown about the seeds, or empty when the answer is solid.
     * Kept apart from the summary so the panel can show it as a warning.
     */
    String seedWarning;

    /**
     * The stores Larry has never looked in, asked for in one line, such as "Check your bank
     * and seed vault." Empty once he has seen every store the player can reach.
     */
    String lookOnce;

    /**
     * The respawn point the default route uses for this run, when it is not the one set.
     * Empty otherwise.
     *
     * <p>A suggestion, not a step: an account has one respawn point and the default route
     * uses different ones on different runs. The run is planned around the respawn point
     * actually set, never around this.
     */
    String respawnAction;

    /**
     * Which carried herb seeds the run leaves in the bag because the player has not ticked
     * them, as a sentence telling them where to tick. Empty otherwise.
     *
     * <p>Ticks decide what is planted, so the run never plants an unticked herb. This tells
     * the player why the seeds they brought are passed over.
     */
    String herbNote;

    /**
     * The book the bag was packed for: "STANDARD", "LUNAR" or "ARCEUUS". It decides which
     * teleports are free and which cost a tablet.
     */
    String carryFor;

    /** The book the player is actually on. {@link #getCarryFor()} follows it unless previewing. */
    String spellbookOpen;

    /**
     * Every farming run and whether it is worth doing, best first.
     *
     * <p>Includes every family the trip covers; the panel marks the trip's families by
     * checking this list against {@link #getTripRuns()}. Ordered by each run's value,
     * not by which timer expired first: an idle hardwood patch costs
     * more than an idle herb patch.
     *
     * <p>This ranking differs from the trip's circuit order on purpose. The panel draws the
     * list in {@link #getRunOrder()} instead and marks it from the trip.
     */
    List<FarmingRunStatus> runs;

    /**
     * Every run id in the order the RUNS list draws them. Fixed, so a row never moves when
     * a run comes due or is switched off. Rows not named here go last.
     */
    List<String> runOrder;

    /**
     * The runs the player has switched off, with status {@code OFF}. Kept out of
     * {@link #getRuns()}, which lists only runs worth planning, so the panel can still draw
     * the row that switches one back on.
     */
    List<FarmingRunStatus> switchedOff;

    /** The settings behind each run, as the side panel draws them under its row. */
    List<FarmingRunSettings> runSettings;

    /** Which run goes first when several are due, drawn under the RUNS heading. */
    FarmingSettingPick runFirst;

    /** The checkbox under the runs list: whether a run waits for every patch to need a visit. */
    FarmingSettingChoice waitForEveryPatch;

    /**
     * Every travel method under its caption, in a fixed order, each switched on or off. A
     * square marked missing is one the player has nothing for.
     */
    List<FarmingSettingGroup> travelGroups;

    /** What the player's house holds, seen or declared, drawn after the travel methods. */
    FarmingHouse house;

    /**
     * True when the player has no usable way to stand at a fairy ring, through their
     * switches or by owning none. False when the network itself is shut, by quest or
     * staff, since no switch helps then.
     */
    boolean fairyRingBlocked;
}
