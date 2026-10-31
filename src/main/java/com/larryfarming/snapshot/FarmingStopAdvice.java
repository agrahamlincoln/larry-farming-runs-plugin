package com.larryfarming.snapshot;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * One line of a farming walkthrough: where to go, and how to get there or why not to.
 *
 * <p>Built by name rather than by position, like {@link FarmingAdvice}: with this many
 * {@code String} fields, a transposed pair would compile and put one patch's sentence under
 * another's heading.
 */
@Value
@Builder(toBuilder = true)
public class FarmingStopAdvice {

    /** What to do about this stop. */
    public enum Outcome {
        VISIT,
        /** A patch whose only route is too slow to be worth the trip. */
        SKIP,
        /** Nothing reaches it, and the route table says so. */
        UNREACHABLE,
        /**
         * Every route the table holds has failed and it names no fallback. Not a dead end:
         * the patch may still be reachable on foot.
         */
        UNROUTED
    }

    /**
     * What kind of row this is.
     *
     * <p>A bank has no {@link com.larryfarming.farming.model.PatchRef}, crop or timer, so it is not
     * a {@code RunStop}. A bank row reuses the destination, route and action fields and is
     * told apart by this discriminator.
     *
     * <p>Defaults to {@code PATCH} rather than being nullable, so no reader has to fold a
     * null into the common case.
     */
    public enum Kind {
        /** A place with patches at it. */
        PATCH,
        /**
         * The bank stop the trip most recommends, placed by {@code InventorySim} where the
         * bag gets tightest for this player's load.
         *
         * <p><b>Recommended, not required.</b> The tool leprechaun at every patch notes
         * produce, so a farm run's bag never truly fills. This and {@link #BANK_OPTIONAL}
         * sit on one scale of recommendation, so neither is worded as an order.
         */
        BANK,
        /**
         * A bank the trip walks past that the planner graded but did not pick. Shown so the
         * player can choose to unload early. Drawn quieter than a {@link #BANK} so the
         * recommended stop stays findable.
         */
        BANK_OPTIONAL,
        /**
         * The bank the player is already standing at, and what to take out of it. Partway
         * round a trip, the next bank they open, whichever that is.
         *
         * <p>Not a journey, so it takes no number in the sequence and does not count toward
         * the header's stop tally. Otherwise drawn like a {@link #BANK}: the same margin mark,
         * the same white heading, and never hidden by the offered-banks switch.
         */
        BANK_START,
        /**
         * The bag fills here and there is no bank in reach to empty it into. The player
         * must shed something or come back another day, so the walkthrough says so rather
         * than stranding them at a patch they cannot work.
         */
        OVERFLOW
    }

    @Builder.Default
    @SerializedName("kind") Kind kind = Kind.PATCH;

    /**
     * The patch as the game names it, or the place when the row covers several patches.
     *
     * <p>Display text only. Nothing keys off it: patches persist on region and varbit.
     */
    @SerializedName("patchName") String patchName;

    @SerializedName("outcome") Outcome outcome;

    /** What to do, imperative: "Icy basalt to Weiss". Empty when not visiting. */
    @SerializedName("instruction") String instruction;

    /**
     * The work already waiting here — "Check the maple", "Harvest 3 patches", "Cure the
     * disease" — one entry per thing the player does, in the order they do it. Empty when
     * there is none, or none we have seen.
     *
     * <p>Distinct from {@link #plants}, which says what to put in the ground. Without this, a
     * stop with grown crops and no seeds would show only a route.
     *
     * <p><b>Order is part of the instruction.</b> The volcanic ash lines sit above the line
     * that empties the compost bin because ash can only be added before the first bucket
     * comes out.
     *
     * <p>An act and its replanting fold into one entry: "Harvest and plant the irit".
     */
    @Builder.Default
    @SerializedName("actions") List<String> actions = List.of();

    /** One clause of reasoning, shown when the answer needs explaining. */
    @SerializedName("because") String because;

    /** What kind of skip this is, shown on the row heading; null for a visited stop. */
    @SerializedName("skipReason") FarmingSkipReason skipReason;

    /**
     * What the skip is waiting on ("toadflax"), for the heading to qualify
     * {@link #skipReason} with, or empty when there is no one thing to name.
     *
     * <p>Set only where "{@code <subject> <label>}" reads as a heading: the skips about
     * something in the ground. A seed shortfall names its seed in a clause instead.
     */
    @SerializedName("subject") String subject;

    /**
     * When whatever is growing here finishes, as an epoch second, or 0 when there is
     * nothing to count down to. Unworded for the same reason as
     * {@link FarmingRunStatus#getReadyAtEpochSec()}.
     */
    @SerializedName("readyAtEpochSec") long readyAtEpochSec;

    /**
     * True when the route rests on something unverifiable, such as a house portal or a
     * grown spirit tree. Shown as a suggestion rather than an instruction.
     */
    @SerializedName("uncertain") boolean uncertain;

    /** True for the extras the default route weaves in rather than parts of the route. */
    @SerializedName("optional") boolean optional;

    /**
     * Why this one is optional, as the catalogue words it: "regrows more slowly than a herb
     * run comes round". Empty for a stop that is simply part of the route.
     *
     * <p>Kept out of {@link #getBecause()}, which for a visited stop holds the route.
     */
    @SerializedName("optionalReason") String optionalReason;

    /**
     * What to plant at this stop, one entry per crop (see {@link FarmingPlantAdvice}). Empty
     * when nothing goes in the ground here.
     */
    @SerializedName("plants") List<FarmingPlantAdvice> plants;

    /**
     * The patches at this arrival being left alone, one entry each. Empty when every patch
     * here is being worked. With {@link #plants}, accounts for every patch at the stop.
     */
    @SerializedName("setAside") List<FarmingPatchNote> setAside;

    /**
     * The item this route is taken on (a tablet, a cloak, a basalt), or 0 when it is not
     * taken on an item. At most one of this and {@link #getTravelSpriteId()} is set.
     */
    @SerializedName("travelItemId") int travelItemId;

    /** The spell icon this route is cast from, or 0 when the route is not a spell. */
    @SerializedName("travelSpriteId") int travelSpriteId;

    /**
     * The name of the {@code Destination} this route's teleport lands at, or null for a
     * route taken on an item. Not drawn: a teleport spent on the way stays paid while the
     * next stop lands where it did.
     */
    @SerializedName("landing") String landing;

    /**
     * The produce that pictures this arrival in the row's margin: the first crop planted
     * here, or failing that the crop being harvested or checked. 0 when no single crop
     * covers the work, such as a compost bin.
     *
     * <p>Not read off {@link #getPlants()}: a replant folded into the harvest line and a
     * check both leave the plantings without that crop.
     */
    @SerializedName("iconItemId") int iconItemId;

    /**
     * A quiet aside about this stop, or empty. Two things feed it, joined with " — " when
     * both apply: the catalogue's caveat on a stop whose route has a condition, and a
     * pacing note for a woven-in extra whose patch has not been observed yet.
     *
     * <p>Kept out of {@link #getBecause()}, which holds the route or the skip reason. Unlike
     * {@link #uncertain}, which describes the route, a note describes the stop.
     */
    @SerializedName("note") String note;

    /**
     * What the row guesses, or empty: "Not seen yet, so assumed empty." when a patch the
     * row plants has never been observed. Unlike {@link #note}, drawn on the row, since the
     * guess may be the reason the player is sent here.
     */
    @SerializedName("assumption") String assumption;

    /**
     * What to take out of the bank here, for the next leg only. Empty on every other row,
     * and on a bank row the player is already carrying everything for.
     *
     * <p>Scoped to the leg, unlike the run-wide packing list, so an axe stays banked until
     * the bank before the tree patch.
     *
     * <p>Quantities are shortfalls, not requirements: two snape grass seeds on a leg that
     * plants three means one is already in the bag.
     */
    @Builder.Default
    @SerializedName("withdraw") List<FarmingCarryItem> withdraw = List.of();

    /**
     * What to put back at this bank before withdrawing: the trip's own things the rest of
     * the trip no longer needs. Only the next bank has any, worked out from the bag the
     * player holds now. Quantities are what is in the bag.
     */
    @Builder.Default
    @SerializedName("deposit") List<FarmingCarryItem> deposit = List.of();

    /**
     * Whether the bag lacks room for this bank's withdrawals and the harvest, so the
     * deposits matter rather than tidy up.
     */
    @SerializedName("depositUrgent") boolean depositUrgent;

    /**
     * Partway round, what this stop needs that the bag lacks, as one sentence: "No axe in
     * your bag. Bank at Catherby first." Empty when the bag has everything.
     */
    @Builder.Default
    @SerializedName("warning") String warning = "";

    /**
     * The farming tools to leave with the tool leprechaun once this stop is done, since no
     * later stop uses them: "Leave your spade and seed dibber with the leprechaun." Empty
     * elsewhere.
     */
    @Builder.Default
    @SerializedName("leprechaun") String leprechaun = "";
}
