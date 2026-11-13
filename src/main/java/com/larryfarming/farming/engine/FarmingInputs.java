package com.larryfarming.farming.engine;

import com.larryfarming.engine.farming.Crop;
import com.larryfarming.engine.farming.PatchPrediction;
import com.larryfarming.farming.detection.*;
import com.larryfarming.farming.model.*;
import com.larryfarming.model.PlayerState;
import com.larryfarming.snapshot.FarmingRunStatus;
import java.util.*;
import java.util.function.Function;
import lombok.*;

/**
 * Everything a farm run is planned against beyond the player's own state: the book the
 * panel previews, answers persisted per account, and what the patches were last seen doing.
 *
 * <p>{@link #getContractRun} differs from the rest: this planner produces it between
 * scheduling and planning, rather than receiving it from outside. It rides here so it need
 * not be threaded through {@code planRun}, {@code leaveStandingCrops} and
 * {@code adviseOnGroup} by hand.
 *
 * <p>Every field is optional. {@link #none()} plans for the open spellbook, assumes
 * nothing has been declared, and treats every patch and the house as unseen.
 *
 * <p>Copies go through a builder or a generated {@code with}, each of which names its field,
 * and both pass through the constructor's null normalisations. Several fields share a type,
 * so a positional call could transpose two of them and still compile.
 */
@Getter
@With
public final class FarmingInputs {

    private static final FarmingInputs NONE = builder().build();

    /** The book to plan teleports for, or null to follow the one the player is on. */
    @With(AccessLevel.NONE)
    private final Spellbook previewBook;
    @Getter(AccessLevel.NONE)
    @With(AccessLevel.NONE)
    private final Function<PatchRef, PatchPrediction> patches;
    /** Today's spent teleport allowances, or null when nothing has been counted. */
    @With(AccessLevel.NONE)
    private final DailyTeleportLedger ledger;
    /**
     * The second this advice is being built for, or zero when the caller did not say.
     *
     * <p>The farming layer takes the current second as an argument everywhere rather than
     * reading a clock.
     *
     * <p>Zero means "no clock was given", and readers must survive it. A finish time
     * compared against zero reads as not ready yet, which is the safe direction.
     */
    private final long nowEpochSec;
    /**
     * The crop the current contract asks for, decoded from varbit 7963, or null when no
     * contract is taken or its value is not one the plugin knows.
     *
     * <p>Without it the scheduler cannot identify the contract's patch and falls back to reading the whole
     * guild, which reports the contract ready whenever <em>anything</em> in the guild is.
     */
    private final Crop contractCrop;
    /**
     * The runs list's own verdict on the contract, or null before it has been reached.
     *
     * <p>The walkthrough's contract row derives from this verdict rather than judging the
     * contract again. The contract's stop names the guild herb patch only as a stand-in, so
     * patch-shaped rules answer the wrong question about it. One verdict keeps the runs list
     * and the walkthrough in agreement (the invariant {@code anythingToDoAt} documents).
     *
     * <p>Set after {@link RunScheduler#schedule} and before the trip is planned: the verdict
     * does not exist until the runs are scheduled, and the planning has to see it.
     */
    private final FarmingRunStatus contractRun;
    /**
     * Which crops the player is willing to have suggested. Never null: an absent value
     * reads as the default crop choices.
     */
    private final FarmingPreferences preferences;
    /**
     * What the player's own house was last seen to hold. Never null: a house never seen
     * reads as {@link HouseSight#unseen()}, whose every piece of furniture is absent.
     */
    private final HouseSight house;
    /** @see #hidesOfferedBanks */
    @Getter(AccessLevel.NONE)
    private final boolean offeredBanksHidden;
    /**
     * The item ids Larry asked for that have been seen in the bag since the trip was
     * packed, so a bank can say which of them to put back. Never null; empty when nothing
     * has been seen.
     */
    private final Set<Integer> packedThisTrip;
    /** The teleport the player has spent on the way to the next stop; see {@code TravelWatch}. */
    private final TravelTaken travelTaken;
    /**
     * The arrivals the plan on screen sends the player to, by constant name, in its order.
     * Once a trip is underway, the trip keeps that order. Never null.
     */
    private final List<String> ahead;
    /** The account's compost step, stock and thermostat. Never null: absent reads {@link CompostStock#NONE}. */
    private final CompostStock compost;
    /**
     * The volcanic ash this trip's Fertile Soil casts take, which the compost bins leave to
     * them. Zero until a plan of this snapshot has counted the casts.
     */
    private final int fertileAsh;
    /**
     * The compost bins' verdicts and the quiet picks, judged once by {@link #seeing} for
     * every reader of the copy it returns. Null before then. A generated {@code with} copy
     * carries this judgement along, but it was made for the original inputs, so the copy
     * works out its bins' verdicts and quiet picks afresh.
     */
    @Getter(AccessLevel.NONE)
    @With(AccessLevel.PRIVATE)
    private final Judgement bins;

    /**
     * What {@link #seeing} judged for one player: the bins' verdicts and the preset picks the
     * account can't plant yet. Owned by the one copy of the inputs that reads them.
     */
    @RequiredArgsConstructor
    private static final class Judgement {
        private final Map<CompostBins.Farm, CompostBins.Judged> verdicts;
        private final Set<String> quiet;
        private final PlayerState state;
        private FarmingInputs owner;
    }

    // On the constructor rather than the class, so every builder path goes through the
    // null normalisations below.
    @Builder(access = AccessLevel.PRIVATE)
    private FarmingInputs(Spellbook previewBook, Function<PatchRef, PatchPrediction> patches, DailyTeleportLedger ledger, long nowEpochSec,
            Crop contractCrop, FarmingRunStatus contractRun,
            FarmingPreferences preferences, HouseSight house, boolean offeredBanksHidden, Set<Integer> packedThisTrip, TravelTaken travelTaken,
            List<String> ahead, CompostStock compost, int fertileAsh, Judgement bins) {
        this.compost = compost != null ? compost : CompostStock.NONE;
        this.fertileAsh = fertileAsh;
        this.bins = bins;
        this.packedThisTrip = packedThisTrip != null ? Set.copyOf(packedThisTrip) : Set.of();
        this.travelTaken = travelTaken != null ? travelTaken : TravelTaken.NONE;
        this.previewBook = previewBook;
        this.patches = patches;
        this.ledger = ledger;
        this.nowEpochSec = nowEpochSec;
        this.contractCrop = contractCrop;
        this.contractRun = contractRun;
        // A caller with no preferences wants the default crops, not "plant nothing".
        this.preferences = preferences != null ? preferences : FarmingPreferences.defaults();
        this.house = house != null ? house : HouseSight.unseen();
        this.offeredBanksHidden = offeredBanksHidden;
        this.ahead = ahead != null ? List.copyOf(ahead) : List.of();
    }

    /** Nothing chosen, nothing seen. */
    public static FarmingInputs none() {
        return NONE;
    }

    /**
     * @param ledger      how much of today's rationed teleports is already spent, or null
     *                    to plan as though every allowance were untouched
     * @param nowEpochSec which day the ledger's counts should be read against
     */
    public static FarmingInputs of(Spellbook previewBook, Function<PatchRef, PatchPrediction> patches, DailyTeleportLedger ledger,
            long nowEpochSec) {
        return builder().previewBook(previewBook).patches(patches).ledger(ledger).nowEpochSec(nowEpochSec).build();
    }

    /**
     * A route resolver for this book over these inputs: today's ledger, the clock, the
     * patches and the house as last seen, the player's travel switches, and what they
     * declare their house holds. Building one is the expensive half of scoring a run, so
     * callers share it where they can.
     */
    public CapabilityResolver resolverOn(Spellbook book) {
        return new CapabilityResolver(book, ledger, nowEpochSec, this::patchAt, house).switchingOff(preferences.travelOff())
                .declaringHouse(preferences.houseDeclared()).travelling(travelTaken);
    }

    /**
     * Whether the panel leaves out the banks the route only passes. Their rows are not
     * drawn, so nothing may be fetched at them. Defaults to false: every bank is shown.
     */
    public boolean hidesOfferedBanks() {
        return offeredBanksHidden;
    }

    /**
     * Whether the contract is knowingly waiting on a crop to finish growing.
     *
     * <p>Asked negatively on purpose. "Is the contract due?" answers false for a contract
     * nobody has read yet, and would drop the Farming Guild from the run of every player
     * whose varbits have not arrived. Only PENDING says a crop is growing and when it will
     * be ready, so only PENDING is a reason not to go.
     */
    public boolean contractIsStillGrowing() {
        return contractRun != null && RunStatus.PENDING.name().equals(contractRun.getStatus());
    }

    /** The contract's one-line instruction, such as "hand the contract to Guildmaster Jane", or "". */
    public String contractDetail() {
        String detail = contractRun == null ? null : contractRun.getDetail();
        return detail == null ? "" : detail;
    }

    /**
     * Whether the contract's instruction is one its own patch will also be saying.
     *
     * @see FarmingRunStatus#isAboutThePatch()
     */
    public boolean contractIsAboutThePatch() {
        return contractRun != null && contractRun.isAboutThePatch();
    }

    /** When the contract's crop finishes, or 0 when there is nothing to count down to. */
    public long contractReadyAtEpochSec() {
        return contractRun == null ? 0L : contractRun.getReadyAtEpochSec();
    }

    /**
     * These inputs with the sites this account is seen unable to reach marked on the
     * preferences, so a run whose every reachable site is dropped switches off, and the
     * preset's picks the account can't plant marked quiet. Each entry point asks this of the
     * state it plans, so the marks follow the account: a site that opens up holds its run
     * open again, and a pick starts planting at its level or with its seed in stock. Sites
     * are read only while some site is dropped. The compost bins are judged here too, and
     * every reader of the copy reads those verdicts. A copy this returned comes back as it
     * is when asked again for the same player.
     */
    FarmingInputs seeing(PlayerState state) {
        if (isSeenFor(state)) {
            return this;
        }
        Set<String> shut = preferences.sitesOff().isEmpty() ? Set.of() : RunSettings.shutSites(state, this::patchAt);
        Set<String> quiet = Set.copyOf(RunSettings.quietPicks(preferences, state, this::patchAt));
        // Neither the bins' verdicts nor the quiet picks read the marks set below, so the
        // verdicts judged here hold for the marked copy, and seeing that copy again for the
        // same player changes nothing.
        Map<CompostBins.Farm, CompostBins.Judged> judged = CompostBins.verdicts(this, state, quiet);
        Set<String> held = new HashSet<>(quiet);
        held.addAll(CompostBins.holding(this, judged));
        FarmingPreferences marked = preferences.withSitesShut(shut).withPicksQuiet(held);
        Judgement judgement = new Judgement(judged, quiet, state);
        FarmingInputs seen = (marked == preferences ? this : withPreferences(marked)).withBins(judgement);
        judgement.owner = seen;
        return seen;
    }

    /** Whether {@link #seeing} returned this copy for this player. */
    private boolean isSeenFor(PlayerState state) {
        return bins != null && bins.owner == this && bins.state == state;
    }

    /**
     * The compost bins' verdicts: those {@link #seeing} judged for this copy and player, or
     * judged now for any other.
     */
    Map<CompostBins.Farm, CompostBins.Judged> bins(PlayerState state) {
        return isSeenFor(state) ? bins.verdicts : CompostBins.verdicts(this, state);
    }

    /**
     * The preset picks the account can't plant yet ({@link RunSettings#quietPicks}): those
     * {@link #seeing} read for this copy and player, or read now for any other.
     */
    Set<String> quietPicks(PlayerState state) {
        return isSeenFor(state) ? bins.quiet : RunSettings.quietPicks(preferences, state, this::patchAt);
    }

    /**
     * What this patch was last seen holding, or null when it has never been seen.
     *
     * <p>Null is the common case and not a failure: patch varbits only read true while
     * the player is in the region, so a patch is unknown until they have been there since
     * the plugin was installed. Callers must treat unknown as "no reason to change the
     * plan" rather than as an empty patch.
     */
    public PatchPrediction patchAt(PatchRef patch) {
        return patches != null && patch != null ? patches.apply(patch) : null;
    }
}
