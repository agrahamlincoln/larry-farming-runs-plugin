package com.larryfarming.farming.engine;

import static com.larryfarming.farming.engine.CropNames.displayNameOf;
import static com.larryfarming.farming.engine.CropNames.inSentence;

import com.larryfarming.engine.farming.*;
import com.larryfarming.farming.catalogue.*;
import com.larryfarming.farming.detection.CapabilitySources;
import com.larryfarming.farming.detection.CapabilityState;
import com.larryfarming.farming.model.*;
import com.larryfarming.model.PlayerState;
import com.larryfarming.snapshot.*;
import java.util.*;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

/**
 * One arrival of a walkthrough written as its row: the heading, the route, the work waiting,
 * the plantings, the patches set aside, and the quiet note.
 *
 * <p>Everything here is already decided by the time it is called. {@link FarmingAdviceBuilder}
 * plans the run and the trip and hands each arrival over, as indices into its plans.
 *
 * <p>An instance holds one arrival while its row is written; every method other classes
 * call is static.
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
final class StopRowWriter {

    /** The arrival, as indices into {@link #plans}. */
    private final List<Integer> group;
    private final List<StopPlan> plans;
    /** The seeds dealt to each stop, which say a fixed-crop stop's replanting. */
    private final List<List<Crop>> cropFor;
    /**
     * Which patch each seed landed in, from {@code assignCrops}; empty when the caller has no
     * allocation, which under-states rather than invents.
     */
    private final Map<PatchRef, Crop> plantedIn;
    private final FarmingInputs inputs;
    /** The player, or null to skip the level and stock checks. */
    private final PlayerState state;

    /**
     * One arrival, rendered: where it is, how to get there, and everything to plant.
     *
     * @param routing where the row's travel is read from: its instruction, clause and two
     *     icons, nothing else. Null reads them from the lead stop. Non-null only for a visit
     *     working several patches at an arrival whose stops rank their routes differently.
     *     Which patches are worked always comes from the lead.
     * @param state the player, which names the crop a fixed-crop stop plants when it plants
     *     nothing this trip; null skips the level and stock checks behind that choice
     */
    static FarmingStopAdvice adviseOnGroup(List<Integer> group, List<StopPlan> plans,
            List<List<Crop>> cropFor, Map<PatchRef, Crop> plantedIn, FarmingInputs inputs, StopPlan routing, PlayerState state) {
        return new StopRowWriter(group, plans, cropFor, plantedIn, inputs, state).advise(routing);
    }

    private FarmingStopAdvice advise(StopPlan routing) {
        StopPlan lead = leadOf(group, plans);
        StopPlan travel = routing != null ? routing : lead;
        // The two enums name the same four outcomes.
        FarmingStopAdvice.Outcome outcome = FarmingStopAdvice.Outcome.valueOf(lead.getOutcome().name());

        // Tallied by crop across the arrival, in first-met order. Counted in patches, not
        // seeds; the bag answers how many seeds a patch takes.
        boolean visiting = lead.isVisit();
        Map<Crop, Integer> patchesPerCrop = new LinkedHashMap<>();
        // The hedge belongs to the route this row prints. A sibling's doubt is moot: an
        // arrival is one journey, so reaching it reaches every patch there.
        boolean uncertain = travel.isUncertain();
        boolean anyWork = false;
        boolean workedAreExtras = true;
        boolean everyStopIsAnExtra = true;
        String note = "";
        CompostBins.Row bin = visiting ? CompostBins.rowAt(group, plans, inputs, state) : CompostBins.Row.NONE;
        for (int index : group) {
            StopPlan plan = plans.get(index);
            // Only where the player is going, so a skipped arrival lists no plantings. The
            // allotments harvested into the bin come after the rest, below.
            if (visiting && !bin.getFills().contains(index)) {
                for (Crop crop : cropFor.get(index)) {
                    patchesPerCrop.merge(crop, 1, Integer::sum);
                }
            }
            // Optional only when everything being worked here is optional. A required patch
            // left growing contributes no work, so it does not count.
            if (plan.isVisit()) {
                anyWork = true;
                workedAreExtras &= plan.getStop().isOptional();
            }
            everyStopIsAnExtra &= plan.getStop().isOptional();
            String own = noteFor(plan, cropFor.get(index));
            if (note.isEmpty() && !own.isEmpty()) {
                note = own;
            }
        }
        // The allotments harvested into the bin replant with the rest, so a crop is planted in
        // one line. Last, so the row is still pictured by what the farm's other patches plant.
        bin.getFills().forEach(index -> cropFor.get(index).forEach(crop -> patchesPerCrop.merge(crop, 1, Integer::sum)));
        // With no work, workedAreExtras is vacuously true, so every stop at the place
        // answers. Not the lead: a seedless limpwurt skip outranks a growing herb.
        boolean optional = anyWork ? workedAreExtras : everyStopIsAnExtra;

        List<FarmingPlantAdvice> plants = new ArrayList<>(patchesPerCrop.size());
        patchesPerCrop.forEach((crop, patches) -> plants.add(new FarmingPlantAdvice(displayNameOf(crop), crop.getItemId(), patches)));

        Map<PatchRef, Crop> felling = plants.isEmpty() ? Collections.emptyMap() : treesMakingWay();
        Map<PatchRef, Crop> diggingUp = visiting ? plantsComingOut() : Collections.emptyMap();
        // Only where the player is going: leadOf hands the row to a skip when every visit
        // here is an extra. Read once, for the sentence and for the contract-line check.
        List<Work> waiting = visiting ? worksAt(bin.getFills(), felling, diggingUp) : Collections.emptyList();
        int iconItemId = pictureOf(plants, waiting);
        List<String> actions = actionAt(waiting, plants, felling.values(), diggingUp.values());
        // The bin first: emptying it stocks the bucket for the patches here, and a full bin
        // closed at once starts rotting sooner.
        actions.addAll(0, bin.getLines());
        if (visiting) {
            // Last, because the farmer protects what has gone in. The bag packs the same fee.
            actions.addAll(payTheFarmer());
        }
        // The errand goes first: Jane names the next contract's crop, and planting the guild
        // patches before hearing her can fill the very patch it wants. The sentence is
        // borrowed from RunScheduler so the two never disagree.
        if (visiting && anErrandIsBeingRun() && !theWorkLineAlreadySaysIt(waiting, patchesPerCrop.keySet())
                && !inputs.contractDetail().isEmpty() && !inputs.contractDetail().startsWith(RunScheduler.HERB_PATCH_WORK)) {
            actions.add(0, capitalise(inputs.contractDetail()));
        }

        // Every patch here not being worked, except the lead, which speaks for the row.
        List<FarmingPatchNote> setAside = new ArrayList<>();
        for (int index : group) {
            StopPlan plan = plans.get(index);
            PatchPick pick = FarmingCatalogue.pickOf(plan.getStop());
            // A player's own pick harvested with no seed to replant says why it stays empty.
            if (visiting && plan.isVisit() && pick != null && cropFor.get(index).isEmpty()
                    && inputs.getPreferences().picksChosen().contains(pick.getSite())) {
                StopPlan unplanted = StopSkipRules.skipIfNoSeed(plan.getStop(),
                        FixedCrops.cropFor(plan.getStop(), inputs.getPreferences(), state), state);
                setAside.add(new FarmingPatchNote(patchLabel(plan.getStop().primaryPatch()),
                        unplanted != null ? unplanted.getSkipReason() : FarmingSkipReason.NO_SEED,
                        unplanted != null ? unplanted.getBecause() : "", 0L));
                continue;
            }
            // A preset pick past the seed stock says nothing, as one wholly out of its seed does.
            if (plan.isVisit() || plan == lead || pick != null && plan.getSkipReason() == FarmingSkipReason.NO_SEED
                    && plan.getBecause().isEmpty() && !inputs.getPreferences().picksChosen().contains(pick.getSite())) {
                continue;
            }
            setAside.add(new FarmingPatchNote(patchLabel(plan.getStop().primaryPatch()),
                    plan.getSkipReason(), plan.getBecause(), plan.getReadyAtEpochSec()));
        }

        return FarmingStopAdvice.builder().patchName(nameOfGroup()).arrival(lead.getStop().getArrival().name()).outcome(outcome)
                // The planner's composed sentence. A destination-named rung stores only its
                // tail ("then run east"); the teleport clause is added at planning time.
                .instruction(visiting ? travel.getBecause() : "").actions(actions).iconItemId(iconItemId).because(travel.getBecause())
                // No skip reason on a visited arrival; set-aside lines carry per-patch
                // detail. From the lead, so heading and sentence describe the same patch.
                .skipReason(visiting ? null : lead.getSkipReason()).subject(subjectOf(lead))
                // The lead's clock, matching the rest of the heading.
                .readyAtEpochSec(lead.getReadyAtEpochSec()).uncertain(uncertain).optional(optional)
                .optionalReason(optional && lead.getStop().getOptionalReason() != null ? lead.getStop().getOptionalReason() : "")
                .plants(plants).setAside(setAside).travelItemId(travelItemOf(travel)).travelSpriteId(travelSpriteOf(travel))
                .landing(landingOf(travel)).note(note).assumption(visiting ? assumptionAt() : "").build();
    }

    /** Whether a stop being visited here is an errand rather than patch work. */
    private boolean anErrandIsBeingRun() {
        return visitsIn(group, plans).stream().anyMatch(plan -> plan.getStop().isErrand());
    }

    /** The stops at this arrival the player is visiting, in the arrival's order. */
    static List<StopPlan> visitsIn(List<Integer> group, List<StopPlan> plans) {
        List<StopPlan> visits = new ArrayList<>(group.size());
        for (int index : group) {
            if (plans.get(index).isVisit()) {
                visits.add(plans.get(index));
            }
        }
        return visits;
    }

    /**
     * Whether the row's own work line is already saying what the contract's line would.
     *
     * <p>When the contract's instruction is about a guild patch this row is already working
     * or planting, borrowing it prints the same act twice.
     *
     * <p>Two halves. The runs list says whether the instruction is about the patch at all
     * (a hand-in to Jane names the crop too, and must survive). This row says whether the
     * arrival is working that patch.
     *
     * <p>Matched on the crop, not the act: both come from one prediction of one patch, but a
     * health check and a harvest word the same tree differently. An act covering two crops
     * names neither, so the contract's line survives.
     */
    private boolean theWorkLineAlreadySaysIt(List<Work> waiting, Collection<Crop> planting) {
        Crop contracted = inputs.getContractCrop();
        return contracted != null && inputs.contractIsAboutThePatch()
                && (planting.contains(contracted) || waiting.stream().anyMatch(work -> work.crop == contracted));
    }

    /**
     * The row's work: the most urgent errand at this arrival first, then every other
     * errand waiting there, one entry each.
     *
     * <p>Every act, since several families can share one place. One line each, ordered by
     * {@code Act}'s ranking; joined clauses are unreadable in the narrow panel column.
     *
     * @param plants the arrival's plantings, which an act line that replants a crop marks as said
     * @param felling grown trees coming down for a sapling, one entry per tree
     * @param diggingUp grown bushes, cacti and calquats coming out for a fresh plant
     */
    private List<String> actionAt(List<Work> waiting, List<FarmingPlantAdvice> plants, Collection<Crop> felling,
            Collection<Crop> diggingUp) {
        // The +3 is the chopping, paying and digging lines, which no work carries.
        List<String> lines = new ArrayList<>(waiting.size() + 3);
        boolean dugUpSaid = false;
        // Where the first check or harvest line sits, which the felling lines go ahead of; -1 until one is.
        int firstRoutine = -1;
        // Trees still owed a mention; crossed off as lines below speak for them.
        List<Crop> unspoken = new ArrayList<>(felling);
        for (int i = 0; i < waiting.size(); i++) {
            Work work = waiting.get(i);
            // Only the lead may announce itself a replacement or say a digging. Every act may
            // say its own crop's replanting, so the Farming Guild reads "Kill, replant Hespori"
            // and "Harvest, replant irit" rather than leaving both to the planting line.
            String phrase;
            if (i == 0 && work.crop != null && unspoken.contains(work.crop)) {
                phrase = sayItIsAreplacement(work, plants);
                // Crossed off only for a harvest, which is the only act sayItIsAreplacement
                // words as a felling, and only for as many trees as it claimed.
                if (work.act == PatchWork.Act.HARVEST) {
                    for (int spoken = 0; spoken < work.count; spoken++) {
                        unspoken.remove(work.crop);
                    }
                }
            } else if (i == 0) {
                // A check never says a digging: the check of a plant coming out goes unsaid.
                boolean digs = work.act == PatchWork.Act.HARVEST && !diggingUp.isEmpty() && Collections.frequency(diggingUp, work.crop) == diggingUp.size()
                        && diggingUp.size() == work.count;
                // A digging the fold cannot say would follow its replant, so no fold then.
                phrase = digs || diggingUp.isEmpty() ? foldReplant(work, plants, digs) : work.word();
                // A fold says the digging with it.
                dugUpSaid = digs && !phrase.equals(work.word());
            } else {
                phrase = diggingUp.isEmpty() ? foldReplant(work, plants, false) : work.word();
            }
            if (firstRoutine < 0 && (work.act == PatchWork.Act.CHECK || work.act == PatchWork.Act.HARVEST)) {
                firstRoutine = lines.size();
            }
            // Bare hands take damage instead of the nightshade. The gloves that work run to
            // over a hundred items, so the line asks for gloves without checking which.
            if (work.act == PatchWork.Act.HARVEST && work.crop == Crop.BELLADONNA) {
                lines.add(GLOVES_FOR_NIGHTSHADE);
            }
            if (!phrase.isEmpty()) {
                lines.add(phrase);
            }
        }
        // A spent tree has no act of its own: PatchWork.at answers null to a checked lumber
        // tree, and worksAt leaves out the check of a tree coming down. Either way its clearing
        // line comes before the checks and picking and before the plantings. Felling is the
        // long job, and the fruit tree lies nearer the way in at Kastori, the stop with both.
        // The trees the player chops get one line and the trees the gardener clears another.
        List<Crop> paid = new ArrayList<>();
        List<Crop> chopped = new ArrayList<>();
        for (Crop tree : unspoken) {
            (gardenerClears(tree) ? paid : chopped).add(tree);
        }
        int clearing = firstRoutine < 0 ? lines.size() : firstRoutine;
        int saidBefore = plants.size() - unsaid(plants).size();
        if (!chopped.isEmpty()) {
            lines.add(clearing++, sayItMustComeDown(chopped, false, plants));
        }
        if (!paid.isEmpty()) {
            lines.add(clearing, sayItMustComeDown(paid, true, plants));
        }
        // These lines go ahead of the routine line that may already have claimed a planting,
        // so their plantings move ahead of it too, and the margin keeps the row's order.
        int said = plants.size() - unsaid(plants).size();
        Collections.rotate(plants.subList(0, said), said - saidBefore);
        // After any picking of the old plant, before the new one goes in. A grown bush still
        // unchecked reads only "Dig up": picking the load the check grows is the player's
        // call, and the plant comes out either way.
        if (!diggingUp.isEmpty() && !dugUpSaid) {
            lines.add(chain("Dig up", diggingUp, plants));
        }
        return lines;
    }

    /**
     * One row line: the verb, the crop when one covers every patch, and the patch count past
     * one. "Harvest ranarr ×3", "Check maple", "Cure ×2". The margin icon pictures the crop.
     */
    static String line(String verb, Crop crop, int patches) {
        return verb + (crop == null ? "" : " " + inSentence(crop)) + FarmingPlantAdvice.countOf(patches);
    }

    /**
     * The line for trees that have to be felled before the patches under them can be
     * replanted, one entry per tree from {@link #treesMakingWay}. Clearing the stump goes
     * unsaid: a player does not leave a felled tree to regrow.
     *
     * @param paid whether the bag packs the gardener's fee for these trees rather than an axe
     * @param plants the arrival's plantings, which the line marks as said for the crops it replants
     */
    private static String sayItMustComeDown(List<Crop> felling, boolean paid, List<FarmingPlantAdvice> plants) {
        // "to clear" says what the fee buys, since a farmer's fee beside it protects crops.
        return chain(paid ? "Pay the gardener to clear" : "Chop down", felling, plants);
    }

    /**
     * An act on plants coming out, naming each crop with its patch count. When the same crops
     * go back in, in the same counts, the line says their replanting too: "Chop down, replant
     * yew, dragonfruit". Otherwise it reads "Chop down yew, dragonfruit", and the
     * planting keeps its place in the planting line.
     *
     * @param crops one entry per plant coming out
     * @param plants the arrival's plantings, which the line marks as said for the crops it replants
     */
    private static String chain(String verb, Collection<Crop> crops, List<FarmingPlantAdvice> plants) {
        Map<String, Integer> out = new LinkedHashMap<>();
        crops.forEach(crop -> out.merge(displayNameOf(crop), 1, Integer::sum));
        StringJoiner names = new StringJoiner(", ", verb + (claim(plants, out) ? ", replant " : " "), "");
        out.forEach((name, patches) -> names.add(inSentence(name) + FarmingPlantAdvice.countOf(patches)));
        return names.toString();
    }

    /**
     * Marks as said the plantings an act line replants, so the planting line leaves them
     * out. All or none: each crop must still be unsaid and planted in as many patches as the
     * line counts, or the line says no replanting.
     *
     * <p>The claimed plantings move up behind those already said, so the margin pictures
     * the act lines' crops first, as the row says them, and the planting line's after.
     *
     * @param replanted the patches of each crop, by its planting's name
     * @return whether the line says the replanting
     */
    private static boolean claim(List<FarmingPlantAdvice> plants, Map<String, Integer> replanted) {
        List<FarmingPlantAdvice> claimed = unsaid(plants).stream()
                .filter(plant -> Integer.valueOf(plant.getPatches()).equals(replanted.get(plant.getName()))).collect(Collectors.toList());
        // One planting per crop, so a match for every crop is as many matches as crops.
        if (claimed.size() != replanted.size()) {
            return false;
        }
        plants.removeAll(claimed);
        plants.addAll(plants.size() - unsaid(plants).size(), claimed.stream().map(plant -> plant.withSaid(true)).collect(Collectors.toList()));
        return true;
    }

    /** Whether the gardener clears this grown tree, the same answer the bag packs by. */
    private boolean gardenerClears(Crop tree) {
        CropData data = CropData.forCrop(tree);
        return data != null && CarryPlanner.gardenerClears(data.getPatchType(), tree, state);
    }

    /**
     * Each grown plant at this arrival that a seed now replaces, by its patch: a bush or
     * cactus, or a calquat. Only where the stop plants: with no seed for the next crop the
     * old plant is left to bear.
     *
     * <p>A calquat is a tree the player clears with a spade, as a bush, not with an axe, and
     * the game offers that only after its health check. So a calquat being replanted is dug
     * up, checked or not; a dead one has its own line. Only the wording treats it as a bush:
     * planning keeps it a tree, spent once picked.
     */
    private Map<PatchRef, Crop> plantsComingOut() {
        Map<PatchRef, Crop> out = new LinkedHashMap<>();
        for (int index : group) {
            StopPlan plan = plans.get(index);
            if (!plan.isVisit() || cropFor.get(index).isEmpty()) {
                continue;
            }
            for (PatchRef ref : plan.getStop().getPatches()) {
                PatchPrediction seen = inputs.patchAt(ref);
                // A calquat stop has one patch, so planting here is planting in it, and the seed
                // dealt there already rules out a calquat still growing or diseased.
                boolean calquatStands = ref.type() == PatchType.CALQUAT && seen != null && seen.getCrop() != null && !seen.isIdle()
                        && seen.getState() != CropState.DEAD;
                if (calquatStands || PatchWork.comesOut(seen, plan.getStop(), inputs.getPreferences(), state)) {
                    out.put(ref, seen.getCrop());
                }
            }
        }
        return out;
    }

    /** Said before a belladonna harvest: bare hands cannot pick nightshade. */
    static final String GLOVES_FOR_NIGHTSHADE = "Wear gloves to pick the nightshade";

    /**
     * The protection payment at this arrival, worded for the row. {@link Protection} decides
     * it from the same planting the bag reads, so the act and the packed fee always match.
     */
    private List<String> payTheFarmer() {
        List<String> lines = new ArrayList<>(1);
        for (int index : group) {
            StopPlan plan = plans.get(index);
            if (!plan.isVisit()) {
                continue;
            }
            Protection.Fee fee = Protection.paymentAt(plan.getStop(), cropFor.get(index).size(), state);
            if (fee != null) {
                lines.add("Pay " + fee.farmer + " " + fee.amount + " " + fee.name.toLowerCase(Locale.ROOT) + " for " + fee.patches
                        + (fee.patches == 1 ? " patch" : " patches"));
            }
        }
        return lines;
    }

    /**
     * The line for an arrival whose reason to exist is the replanting, not the fruit.
     *
     * <p>Fruit on a tree will still be there next time, so the trip is for the better tree
     * going in. The line reads as a tree picked clean does, from {@link #sayItMustComeDown}:
     * fruit or none, the tree comes down. The planting keeps its own line unless the same
     * crop goes back in.
     */
    private String sayItIsAreplacement(Work work, List<FarmingPlantAdvice> plants) {
        return work.act != PatchWork.Act.HARVEST ? work.word()
                : sayItMustComeDown(Collections.nCopies(work.count, work.crop), gardenerClears(work.crop), plants);
    }

    /**
     * The trees at this arrival that a sapling is going in place of, by their patch.
     *
     * <p>Named as crops, not answered yes-or-no, because the wording belongs to one stop of
     * the arrival: Catherby's standing-crop flower patch must not turn "Harvest toadflax"
     * into "Chop down toadflax, clear stump".
     *
     * <p>Any grown tree, fruit or lumber, checked or still owed its health check, asked of
     * the patch by the rule the bag packs by. Read per patch from the allocation: Fossil
     * Island's three hardwood patches may take one sapling, and only that patch's tree comes
     * down. Only the allocation knows whether a tree is replaced ({@code dealOutUpgrades}
     * declines unworthy trades), and {@code CarryPlanner} packs its axe or the gardener's fee
     * from the same allocation. With no allocation it under-states rather than invents.
     */
    private Map<PatchRef, Crop> treesMakingWay() {
        Map<PatchRef, Crop> felling = new LinkedHashMap<>();
        for (StopPlan plan : visitsIn(group, plans)) {
            for (PatchRef ref : plan.getStop().getPatches()) {
                // Nothing going in here, so nothing comes out.
                if (!plantedIn.containsKey(ref)) {
                    continue;
                }
                PatchPrediction seen = inputs.patchAt(ref);
                if (seen != null && seen.getCrop() != null && CarryPlanner.treeStandsAt(ref.type(), ref, inputs)) {
                    felling.put(ref, seen.getCrop());
                }
            }
        }
        return felling;
    }

    /**
     * The produce to picture an arrival by: what goes in first, else the crop being
     * harvested or checked. 0 when neither names one.
     *
     * <p>Harvest and check only. The other acts carry a crop too, but picturing it would
     * be wrong: a dead or diseased crop is not what the player gets from the patch.
     */
    private static int pictureOf(List<FarmingPlantAdvice> plants, List<Work> waiting) {
        if (!plants.isEmpty() && plants.get(0).getProduceItemId() > 0) {
            return plants.get(0).getProduceItemId();
        }
        for (Work work : waiting) {
            boolean pictured = work.act == PatchWork.Act.HARVEST || work.act == PatchWork.Act.CHECK;
            if (pictured && work.crop != null && work.crop.getItemId() > 0) {
                return work.crop.getItemId();
            }
        }
        return 0;
    }

    /**
     * A harvest or a kill and the replanting of the same crop, said once: "Harvest, replant
     * ranarr ×2", "Kill, replant Hespori".
     *
     * <p>Only where the crops and counts match (the rotation often picks a ranarr and
     * replants a toadflax), and only where the patches worked are the ones replanted: a
     * ranarr going into the empty patch beside a harvested one is no replant of it. Other
     * crops planted here stay in the planting line.
     *
     * @param plants the arrival's plantings, which a fold marks as said for its crop
     * @param digs whether every harvested plant is dug up before its replanting, which the
     *     folded line then says between the two
     */
    private static String foldReplant(Work work, List<FarmingPlantAdvice> plants, boolean digs) {
        if (work.crop == null || work.act != PatchWork.Act.HARVEST && work.act != PatchWork.Act.KILL || !work.replanted
                || !claim(plants, Collections.singletonMap(displayNameOf(work.crop), work.count))) {
            return work.word();
        }
        return line(work.act.verb + (digs ? ", dig up, replant" : ", replant"), work.crop, work.count);
    }

    /** The plantings no act line has claimed, which the planting line says. */
    private static List<FarmingPlantAdvice> unsaid(List<FarmingPlantAdvice> plants) {
        return plants.stream().filter(plant -> !plant.isSaid()).collect(Collectors.toList());
    }

    /** One act waiting at an arrival, and how much of it. */
    @AllArgsConstructor
    private static final class Work {
        private final PatchWork.Act act;
        private final int count;
        /** The crop covering every patch of this act, or null when they differ. */
        private final Crop crop;
        /** Whether each patch of this act takes its own crop back in, by the seeds dealt. */
        private final boolean replanted;

        /** The act said plainly, naming the crop where one covers it. */
        String word() {
            return line(act.verb, crop, count);
        }
    }

    /**
     * The work already waiting at this arrival, most urgent first, and empty when there is
     * none — or none we have seen.
     *
     * <p>Judged per patch: one grown tree beside a growing one is a visit, and the grown one
     * is the reason. Ranked, not reduced to one, because {@link #actionAt} prints every act,
     * one line each, in this order.
     *
     * <p>A plant being felled or dug up is checked on the way, which the player knows, so its
     * check is not work of its own here; that line says only the checks of plants staying.
     *
     * <p>{@link #cropFor} says a fixed-crop stop's replanting, and {@link #plantedIn} whether
     * any other stop's patches are the ones replanted; an empty allocation claims none of those.
     */
    private List<Work> worksAt(Set<Integer> except, Map<PatchRef, Crop> felling, Map<PatchRef, Crop> diggingUp) {
        Map<PatchWork.Act, Integer> counts = new EnumMap<>(PatchWork.Act.class);
        Map<PatchWork.Act, Crop> only = new EnumMap<>(PatchWork.Act.class);
        // The acts with a patch that does not take its own crop back.
        Set<PatchWork.Act> notReplanted = EnumSet.noneOf(PatchWork.Act.class);
        for (int index : group) {
            StopPlan plan = plans.get(index);
            if (!plan.isVisit() || except.contains(index)) {
                continue;
            }
            for (PatchRef ref : plan.getStop().getPatches()) {
                // Predicted once, so the act and the crop come from one reading.
                PatchPrediction seen = inputs.patchAt(ref);
                PatchWork.Act act = PatchWork.at(seen, plan.getStop(), inputs);
                if (act == null || act == PatchWork.Act.CHECK && (felling.containsKey(ref) || diggingUp.containsKey(ref))) {
                    continue;
                }
                int found = counts.merge(act, 1, Integer::sum);
                // Kept only while one crop covers this act, so the wording can name it.
                only.put(act, found == 1 || only.get(act) == seen.getCrop() ? seen.getCrop() : null);
                // A fixed-crop stop's seeds are dealt by count, not patch, so none is in plantedIn.
                Crop replanting = plan.getStop().isFixedCrop() && cropFor.get(index).contains(seen.getCrop()) ? seen.getCrop() : plantedIn.get(ref);
                if (replanting != seen.getCrop()) {
                    notReplanted.add(act);
                }
            }
        }

        // In Act's declaration order, which is the ranking.
        List<Work> waiting = new ArrayList<>();
        for (PatchWork.Act act : PatchWork.Act.values()) {
            Integer count = counts.get(act);
            if (count != null && count > 0) {
                waiting.add(new Work(act, count, only.get(act), !notReplanted.contains(act)));
            }
        }
        return waiting;
    }

    /**
     * What a skipped arrival is waiting on, for the heading to name, or empty when it has
     * nothing to add.
     *
     * <p>Only the skips about something in the ground, and only from the patch the row is
     * headed by, so heading and hover name the same crop. A seed shortfall stays empty —
     * its own clause names the seed, and "no seed" cannot absorb it.
     */
    private String subjectOf(StopPlan lead) {
        if (lead.getSkipReason() != FarmingSkipReason.STILL_GROWING && lead.getSkipReason() != FarmingSkipReason.STANDING_CROP) {
            return "";
        }
        PatchPrediction seen = inputs.patchAt(lead.getStop().primaryPatch());
        return seen == null || seen.getCrop() == null ? "" : inSentence(seen.getCrop());
    }

    /**
     * Which plan speaks for an arrival.
     *
     * <p>The first visited stop that is a reason to go: worked, and not an extra. Failing
     * that, the most urgent skip, because the row's heading, reason and sentence all come
     * from the lead and must describe the same patch.
     *
     * <p><b>An optional stop never speaks for a place anything is waiting at.</b> Its
     * {@link RunStop#getOptionalReason()} says it is worth working in passing, not worth the
     * journey.
     *
     * <p>Any skip outranks an optional visit, except a skip of a picked patch, which is
     * worked beside the herb patch: at Ortus Farm, whose herb patch has no fixed place in
     * the run, a growing allotment leaves the ready herb patch to speak.
     */
    static StopPlan leadOf(List<Integer> group, List<StopPlan> plans) {
        StopPlan inPassing = null;
        StopPlan waiting = null;
        boolean onlyPicksWait = true;
        for (int index : group) {
            StopPlan plan = plans.get(index);
            if (plan.isVisit()) {
                if (plan.getStop().isOptional()) {
                    if (inPassing == null) {
                        inPassing = plan;
                    }
                } else {
                    return plan;
                }
                continue;
            }
            onlyPicksWait &= plan.getStop().isPicked();
            if (waiting == null || FarmingSkipReason.moreUrgent(waiting.getSkipReason(), plan.getSkipReason()) != waiting.getSkipReason()) {
                waiting = plan;
            }
        }
        // A stop that cannot justify a journey does not become one by being the only thing
        // ready: the row says what the place is waiting on instead.
        if (waiting != null && !(onlyPicksWait && inPassing != null)) {
            return waiting;
        }
        // Only extras here.
        return inPassing != null ? inPassing : plans.get(group.get(0));
    }

    /**
     * Whether the player travels to this group: its row is a visit, which its lead decides.
     * A visit under a skipped lead is worked only by someone already there.
     *
     * <p>Every pass that walks the trip must read journeys this way, or the position
     * allowance and the reached patches go to an arrival the walkthrough never sends the
     * player to. False for an empty group, which {@link #leadOf} cannot answer.
     */
    static boolean isJourney(List<Integer> group, List<StopPlan> plans) {
        return !group.isEmpty() && leadOf(group, plans).isVisit();
    }

    /**
     * A patch named for a row that already says where it is: "Flower", "North allotment".
     *
     * <p>{@link FarmingWorldMap.PatchDef#displayName()} leads with the location, which would
     * repeat the block's heading.
     */
    static String patchLabel(PatchRef patch) {
        FarmingWorldMap.PatchDef def = patch.resolve();
        if (def == null) {
            return patch.getLabel();
        }
        String kind = kindOf(def.getType());
        String name = def.getPatchName();
        if (name == null || name.isEmpty()) {
            return capitalise(kind);
        }
        // Hespori, anima and mushroom patches are named for their kind: "Hespori patch",
        // never "Hespori hespori".
        if (name.equalsIgnoreCase(kind)) {
            return name + " patch";
        }
        return compassPoint(name) + " " + kind;
    }

    /** A patch kind as a player says it: "hardwood tree", "allotment". */
    static String kindOf(PatchType type) {
        return type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    /** "North West" as the game writes a direction: "North-west". Other names unchanged. */
    private static String compassPoint(String name) {
        String[] words = name.split(" ");
        boolean direction = words.length == 2 && (words[0].equals("North") || words[0].equals("South"))
                && (words[1].equals("East") || words[1].equals("West"));
        return direction ? words[0] + "-" + words[1].toLowerCase(Locale.ROOT) : name;
    }

    /**
     * What to call an arrival: the place when it covers several patches, the patch itself
     * when it is only one.
     *
     * <p>Counts patches, not plans: one Fossil Island stop is three hardwood patches.
     */
    private String nameOfGroup() {
        PatchRef primary = plans.get(group.get(0)).getStop().primaryPatch();
        int patches = 0;
        for (int index : group) {
            patches += plans.get(index).getStop().getPatches().size();
        }
        if (patches == 1) {
            return nameOf(primary);
        }
        FarmingWorldMap.PatchDef def = primary.resolve();
        return def != null ? def.getLocation() : nameOf(primary);
    }

    /**
     * The arrival's own route for a visit that earns one, or null to keep the lead's.
     *
     * <p>The visit must work <em>more than one</em> patch. With a single patch actionable,
     * that patch's own chain is right, and the combined ranking would trade its best rung
     * for one suited to a patch the player is not visiting today.
     *
     * <p>Says whether this is the visit the player is travelling to and what the journey
     * before it left them ({@link RunPlanner#planArrival} with a start).
     *
     * @param reached the patches this trip has already worked, which a rung leaning on
     *     another stop is answered from. It must hold this visit's predecessors and nothing
     *     later.
     */
    static StopPlan routeForVisit(List<Integer> visit, List<StopPlan> plans, StopPlan lead, RunPlanner planner, PlayerState state,
            Map<PatchRef, CapabilityState> reached, boolean mayStandHere, RunPlanner.Start start) {
        return !lead.isVisit() || visitsIn(visit, plans).size() < 2 ? null
                : planner.planArrival(lead.getStop(), state, reached, mayStandHere, start);
    }

    /**
     * A quiet aside about this stop, worth saying even about a confident visit. Why a stop
     * was skipped lives in {@link StopPlan#getBecause()} instead.
     *
     * <p>Two sources, joined when both apply: the stop's own caveat from the catalogue, and,
     * for a paced extra whose patch is unseen, a note that the visit follows the crop's
     * pacing rather than the patch. Once seen, {@link StopSkipRules#skipIfStillGrowing}
     * decides.
     *
     * @param planted what the stop is planting, which names the crop being paced
     */
    private String noteFor(StopPlan plan, List<Crop> planted) {
        RunStop stop = plan.getStop();
        Crop crop = planted.isEmpty() ? FixedCrops.cropFor(stop, inputs.getPreferences(), state) : planted.get(0);
        int everyNth = FarmingCatalogue.everyNthRun(stop, crop);
        if (plan.isVisit() && everyNth > 1 && inputs.patchAt(stop.primaryPatch()) == null) {
            return "Planted once in " + everyNth + " herb runs.";
        }
        return "";
    }

    /**
     * What the row owns up to guessing: the scheduler counts an unseen patch as empty
     * ground, which may be what sent the player here. "Unseen, assumed empty", with a count
     * when only some are. Empty when every patch the row visits to plant has been seen.
     */
    private String assumptionAt() {
        // Every patch, as the scheduler counts them: Fossil Island's three hardwood patches
        // are one stop.
        int planting = 0;
        int unseen = 0;
        for (StopPlan plan : visitsIn(group, plans)) {
            if (!plan.getStop().isPlanting()) {
                continue;
            }
            for (PatchRef patch : plan.getStop().getPatches()) {
                planting++;
                if (inputs.patchAt(patch) == null) {
                    unseen++;
                }
            }
        }
        if (unseen == 0) {
            return "";
        }
        return (unseen == planting ? "Unseen" : unseen + " unseen") + ", assumed empty";
    }

    // --- What a route looks like ---------------------------------------------

    /**
     * The item this stop's route is taken on, or 0 for one that is cast or walked.
     *
     * <p>A tablet answers for itself. Otherwise the first of the route's needs with an icon:
     * a rung is often an item plus something invisible (a cloak and a diary tier), and the
     * player clicks the item.
     */
    private int travelItemOf(StopPlan plan) {
        if (!plan.isVisit()) {
            return 0;
        }
        TeleportMeans means = plan.getTeleport();
        if (means != null && means.isTablet()) {
            return means.getItemId();
        }
        for (Capability capability : plan.getNeeds()) {
            // A scroll read from the book is clicked on the book.
            int itemId = CarryItems.iconFor(state == null ? capability : CapabilitySources.packedAs(capability, state));
            if (itemId > 0) {
                return itemId;
            }
        }
        return 0;
    }

    /** The spell icon this stop's route is cast from, or 0 when it is not a spell. */
    private static int travelSpriteOf(StopPlan plan) {
        if (!plan.isVisit()) {
            return 0;
        }
        TeleportMeans means = plan.getTeleport();
        if (means != null && means.isSpell()) {
            return means.getSpriteId();
        }
        // A spell with no destination, such as Minigame Teleport, is a need of the route.
        return plan.getNeeds().stream().mapToInt(Teleports::iconFor).filter(icon -> icon > 0).findFirst().orElse(0);
    }

    /** The place this route's teleport lands at, or null for a route written as an item. */
    private static String landingOf(StopPlan plan) {
        Destination landing = plan.isVisit() && plan.getRoute() != null ? plan.getRoute().getDestination() : null;
        return landing == null ? null : landing.name();
    }

    // --- Plumbing ------------------------------------------------------------

    /** A mid-sentence clause, such as the runs list's contract detail, made to open a line. */
    static String capitalise(String sentence) {
        return sentence == null || sentence.isEmpty() ? "" : Character.toUpperCase(sentence.charAt(0)) + sentence.substring(1);
    }

    /** The game's own name for the patch, so the panel and the world agree. */
    private static String nameOf(PatchRef patch) {
        FarmingWorldMap.PatchDef def = patch.resolve();
        return def != null ? def.displayName() : patch.getLabel();
    }
}
