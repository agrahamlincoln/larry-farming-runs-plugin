package com.larryfarming.farming.engine;

import static com.larryfarming.farming.engine.CropNames.displayNameOf;
import static com.larryfarming.farming.engine.CropNames.inSentence;
import static com.larryfarming.farming.engine.StopSkipRules.binIsFullAndOpen;

import com.larryfarming.engine.farming.Crop;
import com.larryfarming.engine.farming.CropState;
import com.larryfarming.engine.farming.FarmingWorldMap;
import com.larryfarming.engine.farming.PatchPrediction;
import com.larryfarming.engine.farming.PatchType;
import com.larryfarming.farming.catalogue.Compostables;
import com.larryfarming.farming.catalogue.CompostUpgrade;
import com.larryfarming.farming.catalogue.CropData;
import com.larryfarming.farming.catalogue.FarmingCatalogue;
import com.larryfarming.farming.catalogue.TravelIcons;
import com.larryfarming.farming.detection.CapabilityState;
import com.larryfarming.farming.model.Capability;
import com.larryfarming.farming.model.Destination;
import com.larryfarming.farming.model.PatchRef;
import com.larryfarming.farming.model.RunStop;
import com.larryfarming.farming.model.TeleportMeans;
import com.larryfarming.farming.model.TravelOption;
import com.larryfarming.model.PlayerState;
import com.larryfarming.snapshot.FarmingPatchNote;
import com.larryfarming.snapshot.FarmingPlantAdvice;
import com.larryfarming.snapshot.FarmingSkipReason;
import com.larryfarming.snapshot.FarmingStopAdvice;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One arrival of a walkthrough written as its row: the heading, the route, the work waiting,
 * the plantings, the compost bin lines, the patches set aside, and the quiet note.
 *
 * <p>Everything here is already decided by the time it is called. {@link FarmingAdviceBuilder}
 * plans the run and the trip and hands each arrival over, as indices into its plans.
 */
final class StopRowWriter {

    private StopRowWriter() {
    }

    /**
     * One arrival, rendered, for a caller with no per-patch allocation.
     * The empty allocation asks no tree to come down, so it can under-state a felling but
     * never invent one.
     */
    static FarmingStopAdvice adviseOnGroup(List<Integer> group, List<StopPlan> plans,
            List<List<Crop>> cropFor, FarmingInputs inputs, StopPlan routing) {
        return adviseOnGroup(group, plans, cropFor, Collections.emptyMap(),
                Collections.emptyMap(), inputs, routing);
    }

    /** One arrival, rendered, for a caller with no player to hand. */
    static FarmingStopAdvice adviseOnGroup(List<Integer> group, List<StopPlan> plans,
            List<List<Crop>> cropFor, Map<PatchRef, Crop> plantedIn,
            Map<PatchRef, Crop> recommendedIn, FarmingInputs inputs, StopPlan routing) {
        return adviseOnGroup(group, plans, cropFor, plantedIn, recommendedIn, inputs, routing,
                null);
    }

    /**
     * One arrival, rendered: where it is, how to get there, and everything to plant.
     *
     * @param routing where the row's travel is read from: its instruction, clause and two
     *     icons, nothing else. Null reads them from the lead stop. Non-null only for a visit
     *     working several patches at an arrival whose stops rank their routes differently.
     *     Which patches are worked always comes from the lead.
     * @param recommendedIn what each patch would take for a player carrying nothing, keyed
     *     like {@code plantedIn}
     * @param state the player, which names the crop a fixed-crop stop plants when it plants
     *     nothing this trip; null skips the level and stock checks behind that choice
     */
    static FarmingStopAdvice adviseOnGroup(List<Integer> group, List<StopPlan> plans,
            List<List<Crop>> cropFor, Map<PatchRef, Crop> plantedIn,
            Map<PatchRef, Crop> recommendedIn, FarmingInputs inputs, StopPlan routing,
            PlayerState state) {
        StopPlan lead = leadOf(group, plans);
        StopPlan travel = routing != null ? routing : lead;
        FarmingStopAdvice.Outcome outcome = outcomeOf(lead);

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
        for (int index : group) {
            StopPlan plan = plans.get(index);
            // Only where the player is going, so a skipped arrival lists no plantings. An
            // allotment whose watermelons go into the bin is replanted only if the harvest
            // empties it, which the bin's lines say.
            if (visiting && !CompostUpkeep.holdsReadyFill(plan.getStop(), inputs)) {
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
            String own = noteFor(plan, cropFor.get(index), inputs, state);
            if (note.isEmpty() && !own.isEmpty()) {
                note = own;
            }
        }
        // With no work, workedAreExtras is vacuously true, so every stop at the place
        // answers. Not the lead: a seedless limpwurt skip outranks a growing herb.
        boolean optional = anyWork ? workedAreExtras : everyStopIsAnExtra;

        Map<Crop, Crop> offCourse = visiting
                ? herbsOffCourse(group, plans, plantedIn, recommendedIn)
                : Collections.emptyMap();
        List<FarmingPlantAdvice> plants = new ArrayList<>(patchesPerCrop.size());
        for (Map.Entry<Crop, Integer> planted : patchesPerCrop.entrySet()) {
            Crop crop = planted.getKey();
            Crop recommended = offCourse.get(crop);
            plants.add(new FarmingPlantAdvice(displayNameOf(crop), crop.getItemId(),
                    planted.getValue(), herbSeedOf(crop),
                    recommended == null ? null : displayNameOf(recommended)));
        }

        // Only where the player is going: leadOf hands the row to a skip when every visit
        // is a ride-along. Read once, for the sentence and for the contract-line check.
        List<Work> waiting = visiting
                ? worksAt(group, plans, inputs, state)
                : Collections.emptyList();
        // Chosen before actionAt, which drops a replant of the harvested crop from plants.
        int iconItemId = pictureOf(plants, waiting);
        List<Crop> diggingUp = visiting
                ? plantsComingOut(group, plans, cropFor, inputs, state)
                : Collections.emptyList();
        List<String> actions = actionAt(waiting, plants,
                plants.isEmpty() ? Collections.emptyList()
                        : treesMakingWay(group, plans, plantedIn, inputs),
                diggingUp,
                visiting ? upgradeTheBin(group, plans, inputs) : Collections.emptyList());
        List<String> fillTheBin = visiting ? topUpTheBin(group, plans, cropFor, inputs, state)
                : Collections.emptyList();
        if (visiting) {
            // A filling bin has no act waiting, so the top-up goes after the work. The
            // upgrade lines went in above, ahead of the act that empties the bin.
            actions.addAll(fillTheBin);
            // Last, because the farmer protects what has gone in. The bag packs the same fee.
            actions.addAll(payTheFarmer(group, plans, cropFor, state));
        }
        // The errand goes first: Jane names the next contract's crop, and planting the guild
        // patches before hearing her can fill the very patch it wants. The sentence is
        // borrowed from RunScheduler so the two never disagree.
        // A watermelon contract's harvest is said once, by the bin line that puts it in the
        // big bin.
        if (visiting && anErrandIsBeingRun(group, plans)
                && !theWorkLineAlreadySaysIt(waiting, patchesPerCrop.keySet(), inputs)
                && !(inputs.contractIsAboutThePatch()
                        && saysTheContractHarvest(fillTheBin))
                && !inputs.contractDetail().isEmpty()) {
            actions.add(0, capitalise(inputs.contractDetail()));
        }

        // Every patch here not being worked, except the lead, which speaks for the row.
        List<FarmingPatchNote> setAside = new ArrayList<>();
        for (int index : group) {
            StopPlan plan = plans.get(index);
            if (plan.isVisit() || plan == lead) {
                continue;
            }
            setAside.add(new FarmingPatchNote(patchLabel(plan.getStop().primaryPatch()),
                    plan.getSkipReason(), plan.getBecause(), plan.getReadyAtEpochSec()));
        }

        return FarmingStopAdvice.builder()
                .patchName(nameOfGroup(group, plans))
                .outcome(outcome)
                // The planner's composed sentence. A destination-named rung stores only its
                // tail ("then run east"); the teleport clause is added at planning time.
                .instruction(visiting ? travel.getBecause() : "")
                .actions(actions)
                .iconItemId(iconItemId)
                .because(travel.getBecause())
                // No skip reason on a visited arrival; set-aside lines carry per-patch
                // detail. From the lead, so heading and sentence describe the same patch.
                .skipReason(visiting ? null : lead.getSkipReason())
                .subject(subjectOf(lead, inputs))
                // The lead's clock, matching the rest of the heading.
                .readyAtEpochSec(lead.getReadyAtEpochSec())
                .uncertain(uncertain)
                .optional(optional)
                .optionalReason(optional && lead.getStop().getOptionalReason() != null
                        ? lead.getStop().getOptionalReason() : "")
                .plants(plants)
                .setAside(setAside)
                .travelItemId(travelItemOf(travel))
                .travelSpriteId(travelSpriteOf(travel))
                .landing(landingOf(travel))
                .note(note)
                .assumption(visiting ? assumptionAt(group, plans, inputs) : "")
                .build();
    }

    /**
     * For each herb this arrival plants against the recommendation, the herb recommended in
     * its place.
     *
     * <p>Compared per patch, then judged per arrival: when the herbs going in here are the
     * same herbs in the same numbers the recommendation would plant, only which patch takes which has
     * changed. That is the allocation's ordering, not the player's choice, so nothing is
     * flagged. Counted, not just collected: two ranarr and a snapdragon against one ranarr
     * and two snapdragons is a seed the player chose differently.
     *
     * <p>Only patches with a recommended pick are counted, so a patch without one cannot
     * unbalance the comparison.
     *
     * <p>Where one herb line covers patches with different recommendations, the first patch
     * in the stop's order speaks for the line.
     */
    private static Map<Crop, Crop> herbsOffCourse(List<Integer> group, List<StopPlan> plans,
            Map<PatchRef, Crop> plantedIn, Map<PatchRef, Crop> recommendedIn) {
        if (plantedIn == recommendedIn || recommendedIn.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Crop, Integer> planting = new EnumMap<>(Crop.class);
        Map<Crop, Integer> advised = new EnumMap<>(Crop.class);
        Map<Crop, Crop> offCourse = new EnumMap<>(Crop.class);
        for (int index : group) {
            StopPlan plan = plans.get(index);
            if (!plan.isVisit()) {
                continue;
            }
            for (PatchRef ref : plan.getStop().getPatches()) {
                Crop actual = plantedIn.get(ref);
                if (herbSeedOf(actual) == 0) {
                    continue;
                }
                Crop recommended = recommendedIn.get(ref);
                if (recommended == null) {
                    continue;
                }
                planting.merge(actual, 1, Integer::sum);
                advised.merge(recommended, 1, Integer::sum);
                if (recommended != actual) {
                    offCourse.putIfAbsent(actual, recommended);
                }
            }
        }
        return planting.equals(advised) ? Collections.emptyMap() : offCourse;
    }

    /** The seed a herb is planted from, or 0 for anything that is not a herb. */
    private static int herbSeedOf(Crop crop) {
        CropData data = crop == null ? null : CropData.forCrop(crop);
        return data == null || data.getPatchType() != PatchType.HERB
                ? 0 : data.getSeedItemId();
    }

    /** Whether a stop being visited here is an errand rather than patch work. */
    private static boolean anErrandIsBeingRun(List<Integer> group, List<StopPlan> plans) {
        for (int index : group) {
            StopPlan plan = plans.get(index);
            if (plan.isVisit() && plan.getStop().isErrand()) {
                return true;
            }
        }
        return false;
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
    private static boolean theWorkLineAlreadySaysIt(List<Work> waiting,
            Collection<Crop> planting, FarmingInputs inputs) {
        Crop contracted = inputs.getContractCrop();
        if (contracted == null || !inputs.contractIsAboutThePatch()) {
            return false;
        }
        if (planting.contains(contracted)) {
            return true;
        }
        for (Work work : waiting) {
            if (work.crop == contracted) {
                return true;
            }
        }
        return false;
    }

    /**
     * The row's work: the most urgent errand at this arrival first, then every other
     * errand waiting there, one entry each.
     *
     * <p>Every act, since several families can share one place: a full compost bin at
     * Catherby belongs to a family the runs list calls due. One line each, ordered by
     * {@code Act}'s ranking; joined clauses are unreadable in the narrow panel column.
     *
     * @param plants  the arrival's planting lines, with any folded-in crop removed
     * @param diggingUp grown bushes and cacti coming out for a fresh plant
     * @param beforeEmptyingTheBin lines to splice in ahead of the act that empties a
     *                             compost bin, where the order is the instruction: ash
     *                             works only until the first bucket comes out. Never
     *                             non-empty without an {@code EMPTY_BIN} to sit above
     */
    private static List<String> actionAt(List<Work> waiting, List<FarmingPlantAdvice> plants,
            List<Crop> felling, List<Crop> diggingUp, List<String> beforeEmptyingTheBin) {
        // The +2 is the felling and digging lines, which no work carries.
        List<String> lines = new ArrayList<>(
                waiting.size() + beforeEmptyingTheBin.size() + 2);
        boolean dugUpSaid = false;
        // Trees still owed a mention; crossed off as lines below speak for them.
        List<Crop> unspoken = felling.isEmpty() ? felling : new ArrayList<>(felling);
        for (int i = 0; i < waiting.size(); i++) {
            Work work = waiting.get(i);
            if (work.act == PatchWork.Act.EMPTY_BIN) {
                lines.addAll(beforeEmptyingTheBin);
            }
            // The lead is the only one that may absorb a planting or announce itself a
            // replacement; the rest name their act plainly.
            String phrase;
            if (i == 0 && work.crop != null && unspoken.contains(work.crop)) {
                phrase = sayItIsAreplacement(work);
                // Crossed off only for a harvest, which is the only act sayItIsAreplacement
                // words as a felling, and only for as many trees as it claimed.
                if (work.act == PatchWork.Act.HARVEST) {
                    for (int spoken = 0; spoken < work.count; spoken++) {
                        unspoken.remove(work.crop);
                    }
                }
            } else if (i == 0) {
                boolean digs = !diggingUp.isEmpty()
                        && Collections.frequency(diggingUp, work.crop) == diggingUp.size()
                        && diggingUp.size() == work.count;
                int unfolded = plants.size();
                // A digging the fold cannot say would follow its replant, so no fold then.
                phrase = digs || diggingUp.isEmpty()
                        ? foldReplantIntoHarvest(work, plants, digs)
                        : work.act.word(work.count,
                                work.crop == null ? null : displayNameOf(work.crop));
                // A fold takes the planting out of plants, and says the digging with it.
                dugUpSaid = digs && plants.size() < unfolded;
            } else {
                phrase = work.act.word(work.count,
                        work.crop == null ? null : displayNameOf(work.crop));
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
        // tree. After the acts and before the plantings, the order the player works in.
        if (!unspoken.isEmpty()) {
            lines.add(sayItMustComeDown(unspoken));
        }
        // After any check or picking of the old plant, before the new one goes in. A grown
        // bush still unchecked reads "Check" then "Dig up": picking the load the check grows
        // is the player's call, and the plant comes out either way.
        if (!diggingUp.isEmpty() && !dugUpSaid) {
            lines.add(sayItMustBeDugUp(diggingUp));
        }
        return lines;
    }

    /**
     * The line for a tree that has to be felled before the patch under it can be replanted.
     *
     * <p>Names the stump too: a felled tree leaves a stump that takes a spade before a
     * sapling can go in.
     *
     * <p>Counts <b>trees</b>, one per patch from {@link #treesMakingWay}. Names the crop while
     * they all match and falls back to the bare count once they differ, as
     * {@link PatchWork.Act#word} does.
     */
    private static String sayItMustComeDown(List<Crop> felling) {
        Crop first = felling.get(0);
        if (felling.size() == 1) {
            return "Chop down the " + inSentence(first) + " tree and clear the stump";
        }
        boolean allAlike = true;
        for (Crop crop : felling) {
            allAlike &= crop == first;
        }
        return allAlike
                ? "Chop down " + felling.size() + " " + inSentence(first)
                        + " trees and clear the stumps"
                : "Chop down " + felling.size() + " trees and clear the stumps";
    }

    /**
     * Each grown bush or cactus at this arrival that a seed now replaces, one entry per
     * plant. Only where the stop plants: with no seed for the next crop the old plant is
     * left to bear.
     */
    private static List<Crop> plantsComingOut(List<Integer> group, List<StopPlan> plans,
            List<List<Crop>> cropFor, FarmingInputs inputs, PlayerState state) {
        List<Crop> out = new ArrayList<>();
        for (int index : group) {
            StopPlan plan = plans.get(index);
            if (!plan.isVisit() || cropFor.get(index).isEmpty()) {
                continue;
            }
            for (PatchRef ref : plan.getStop().getPatches()) {
                PatchPrediction seen = inputs.patchAt(ref);
                if (PatchWork.comesOut(seen, plan.getStop(), inputs.getPreferences(),
                        state)) {
                    out.add(seen.getCrop());
                }
            }
        }
        return out;
    }

    /** "Dig up the redberry bush", or a count once there are several. */
    private static String sayItMustBeDugUp(List<Crop> plants) {
        return plants.size() == 1
                ? "Dig up the " + inSentence(plants.get(0))
                : "Dig up the " + plants.size() + " old plants";
    }

    /** The opening of the guild line that harvests a watermelon contract into the big bin. */
    private static final String CONTRACT_INTO_THE_BIN =
            "Harvest the contract's watermelons into the compost bin";

    private static final String CLOSE_THE_BIN = "Close the compost bin";

    /** Said before a belladonna harvest: bare hands cannot pick nightshade. */
    static final String GLOVES_FOR_NIGHTSHADE = "Wear gloves to pick the nightshade";

    /** For a fill that may fall short: the game will not close a bin until it is full. */
    private static final String CLOSE_ONCE_FULL = "Close the compost bin once it is full";

    private static final String WAIT_FOR_THE_WATERMELONS =
            "Leave the compost bin open until the watermelons beside it are ready";

    /** The close line: outright where the fill is sure to fill the bin, else once full. */
    private static String closeTheBin(boolean certainToFill) {
        return certainToFill ? CLOSE_THE_BIN : CLOSE_ONCE_FULL;
    }

    /** The line for a bin left part-filled, opening with what it still takes. */
    private static String needsMore(int room, String then) {
        return "The compost bin needs " + room + " more: " + then;
    }

    /** Whether these bin lines harvest the contract's crop, so the errand need not. */
    private static boolean saysTheContractHarvest(List<String> lines) {
        for (String line : lines) {
            if (line.startsWith(CONTRACT_INTO_THE_BIN)) {
                return true;
            }
        }
        return false;
    }

    /**
     * What to put in the compost bin at this arrival, in the order the player does it, or
     * empty when nothing goes in.
     *
     * <p>A farm bin is filled from its allotments: harvest their watermelons into it until
     * it is full, close it, then replant whatever the harvest emptied. With nothing ready
     * yet, the bin waits for them. The guild's big bin takes a watermelon contract's harvest,
     * then the player's own supercompostable produce from the guild's bank.
     *
     * <p>Reads the bin's room on this visit, so a finished bin, emptied first, is refilled
     * on the same visit. What may go in depends on what is already in (see {@link BinAim}):
     * one ordinary item drags a supercompost bin down to plain compost.
     *
     * <p>Closing is its own line, because a bin does not close itself and will not rot until
     * it is closed. The game closes a bin only once it is full, so the line says to close it
     * outright only where the fill is sure to fill it, and "once it is full" where a harvest
     * may fall short. A bin the fill is known to leave short is told what it still needs and
     * left open. Below watermelon's level, only a full bin left open is closed.
     *
     * @param state the player, whose Farming level and stock decide the fill; null reads as
     *     able to grow watermelon and owning nothing
     */
    static List<String> topUpTheBin(List<Integer> group, List<StopPlan> plans,
            List<List<Crop>> cropFor, FarmingInputs inputs, PlayerState state) {
        for (int index : group) {
            StopPlan plan = plans.get(index);
            RunStop bin = plan.getStop();
            if (!plan.isVisit()
                    || !bin.getActions().contains(RunStop.StopAction.FILL_COMPOST_BIN)) {
                continue;
            }
            int room = CompostUpkeep.roomThisVisit(bin, inputs);
            if (room == 0 || !CompostUpkeep.isOn(inputs, state)) {
                if (binIsFullAndOpen(bin, inputs)) {
                    return List.of(CLOSE_THE_BIN);
                }
                continue;
            }
            BinAim aim = whatTheBinIsHeadingFor(bin, inputs);
            if (aim == BinAim.TOMATOES) {
                continue;
            }
            return CompostUpkeep.isTheBigBin(bin)
                    ? fillTheBigBin(bin, room, aim, inputs, state)
                    : fillFromTheAllotments(group, plans, cropFor, bin, room, aim, inputs);
        }
        return List.of();
    }

    /**
     * A farm bin's lines: its allotments' watermelons first, then any other ready crop
     * worth binning, else the bin waits for the watermelons growing beside it.
     */
    private static List<String> fillFromTheAllotments(List<Integer> group, List<StopPlan> plans,
            List<List<Crop>> cropFor, RunStop bin, int room, BinAim aim, FarmingInputs inputs) {
        int ready = 0;
        int replanting = 0;
        boolean growing = false;
        for (int index : group) {
            StopPlan plan = plans.get(index);
            if (!plan.isVisit() || !bin.primaryPatch().equals(plan.getStop().getFeeds())) {
                continue;
            }
            if (CompostUpkeep.holdsReadyFill(plan.getStop(), inputs)) {
                ready++;
                if (!cropFor.get(index).isEmpty()) {
                    replanting++;
                }
            } else if (!cropFor.get(index).isEmpty()) {
                // Planted today, so it is the next fill.
                growing = true;
            }
        }
        if (ready > 0) {
            List<String> lines = new ArrayList<>(3);
            lines.add("Harvest the watermelons into the compost bin until it is full");
            lines.add(closeTheBin(CompostUpkeep.harvestsFill(room, ready)));
            if (replanting == 1) {
                lines.add("Compost and replant the allotment if the harvest emptied it");
            } else if (replanting > 1) {
                lines.add("Compost and replant each allotment the harvest emptied");
            }
            return lines;
        }
        Crop nearby = somethingCompostableReadyAt(group, plans, inputs, aim);
        if (nearby != null) {
            return List.of("Top up the compost bin with " + room + " more "
                    + produceOf(nearby, room),
                    closeTheBin(CompostUpkeep.harvestsFill(room, 1)));
        }
        if (!growing && !CompostUpkeep.watermelonsGrowingFor(bin, inputs)) {
            return List.of();
        }
        // A part-filled bin says what it still takes, so the player leaves it open.
        return List.of(room < CompostUpkeep.capacityOf(bin)
                ? needsMore(room, "leave it open until the watermelons beside it are ready")
                : WAIT_FOR_THE_WATERMELONS);
    }

    /**
     * The guild's big bin. A watermelon contract's harvest goes in first, since the same
     * harvest finishes the contract. One allotment's watermelons may not fill it, so the
     * line says how many it takes, and the player's own produce from the guild's bank makes
     * up the rest. Without a contract, that produce is carried in from the bank before, and
     * a bin it leaves short stays open.
     */
    private static List<String> fillTheBigBin(RunStop bin, int room, BinAim aim,
            FarmingInputs inputs, PlayerState state) {
        if (CompostUpkeep.watermelonContractReady(inputs)) {
            List<String> lines = new ArrayList<>(3);
            String needs = CompostUpkeep.harvestsFill(room, 1) ? ""
                    : room == CompostUpkeep.capacityOf(bin) ? ", which takes " + room
                    : ", which needs " + room + " more";
            lines.add(CONTRACT_INTO_THE_BIN + needs);
            int owned = CompostUpkeep.ownedBankFill(state);
            if (owned > 0) {
                lines.add("Top up the compost bin from the guild's bank with "
                        + aim.wanted(room) + " if it has room");
            }
            lines.add(closeTheBin(CompostUpkeep.harvestsFill(room - owned, 1)));
            return lines;
        }
        int fill = CompostUpkeep.bankFillFor(bin, inputs, state);
        if (fill == 0) {
            return List.of();
        }
        String topUp = "Top up the compost bin with " + fill + " " + aim.wanted(fill);
        return fill < room
                ? List.of(topUp, needsMore(room - fill, "leave it open"))
                : List.of(topUp, CLOSE_THE_BIN);
    }

    /**
     * The protection payment at this arrival, worded for the row. {@link Protection} decides
     * it from the same planting the bag reads, so the act and the packed fee always match.
     */
    private static List<String> payTheFarmer(List<Integer> group, List<StopPlan> plans,
            List<List<Crop>> cropFor, PlayerState state) {
        List<String> lines = new ArrayList<>(1);
        for (int index : group) {
            StopPlan plan = plans.get(index);
            if (!plan.isVisit()) {
                continue;
            }
            RunStop stop = plan.getStop();
            Protection.Fee fee = Protection.paymentAt(stop, cropFor.get(index), state);
            if (fee == null) {
                continue;
            }
            String which;
            if (fee.patches == stop.getPatches().size()) {
                which = fee.patches == 1 ? "the patch" : "the patches";
            } else {
                which = fee.patches == 1 ? "one patch" : fee.patches + " patches";
            }
            lines.add("Pay " + fee.farmer + " " + fee.amount + " "
                    + fee.name.toLowerCase(Locale.ROOT) + " to protect " + which);
        }
        return lines;
    }

    /**
     * What to put in a finished bin before drawing any of it, or empty where nothing would
     * help.
     *
     * <p>A whole bin goes up a tier for one dose of compost potion or a fixed pile of ash,
     * but only before the first bucket comes out. After that the upgrade costs a dose and
     * two ash <em>per bucket</em>.
     *
     * <p>Describes the patch, not the bag; shortfalls are the packing list's job.
     *
     * <p>Reads {@link PatchWork} for whether the bin has finished: a closed bin walked away
     * from reads GROWING with its timer run out, not HARVESTABLE (see
     * {@code CarryPlanner.addCompostUpgrades}).
     */
    private static List<String> upgradeTheBin(List<Integer> group, List<StopPlan> plans,
            FarmingInputs inputs) {
        for (int index : group) {
            StopPlan plan = plans.get(index);
            if (!plan.isVisit()) {
                continue;
            }
            for (PatchRef ref : plan.getStop().getPatches()) {
                PatchPrediction seen = inputs.patchAt(ref);
                if (PatchWork.at(seen, plan.getStop(), inputs.getNowEpochSec())
                        != PatchWork.Act.EMPTY_BIN) {
                    continue;
                }
                int ash = CompostUpgrade.ashFor(seen.getCrop());
                // Rotten tomatoes and ultracompost cannot be upgraded.
                if (ash == 0) {
                    continue;
                }
                List<String> lines = new ArrayList<>(2);
                // The potion first: ash only works on supercompost.
                if (CompostUpgrade.wantsPotion(seen.getCrop())) {
                    lines.add("Add a dose of compost potion to the compost bin");
                }
                lines.add("Add " + ash + " volcanic ash to the compost bin");
                return lines;
            }
        }
        return List.of();
    }

    /**
     * What a bin will make if nothing spoils it, and so what may go in it next.
     *
     * <p>A bin yields rotten tomatoes only if filled entirely with tomatoes, supercompost
     * only if entirely supercompostable, and plain compost otherwise.
     */
    private enum BinAim {
        /** Nothing ordinary has gone in yet. */
        SUPER("supercompostable item", "supercompostable items"),
        /** Already down to plain compost, so anything the bin accepts will do. */
        ORDINARY("compostable item", "compostable items"),
        /** Nothing else may join tomatoes. "More" because this names the item itself. */
        TOMATOES("more tomato", "more tomatoes");

        private final String one;
        private final String many;

        BinAim(String one, String many) {
            this.one = one;
            this.many = many;
        }

        /** What the bin is short of, for a line with no crop to name. Follows the count. */
        private String wanted(int count) {
            return count == 1 ? one : many;
        }
    }

    /**
     * What this bin is heading for, read off what is already in it.
     *
     * <p>Only asked of a bin with room, so a filling bin decodes as the product it is on
     * course for. An empty or unseen bin aims at supercompost.
     */
    private static BinAim whatTheBinIsHeadingFor(RunStop stop, FarmingInputs inputs) {
        for (PatchRef ref : stop.getPatches()) {
            PatchPrediction seen = inputs.patchAt(ref);
            if (seen == null || seen.getState() != CropState.FILLING) {
                continue;
            }
            if (seen.getCrop() == Crop.COMPOST || seen.getCrop() == Crop.BIG_COMPOST) {
                return BinAim.ORDINARY;
            }
            if (seen.getCrop() == Crop.ROTTEN_TOMATO
                    || seen.getCrop() == Crop.BIG_ROTTEN_TOMATO) {
                return BinAim.TOMATOES;
            }
        }
        return BinAim.SUPER;
    }

    /**
     * A crop ready at this arrival that the bin would accept, or null.
     *
     * <p>Read through {@link PatchWork}, so the crop named is one the row already says to
     * harvest. A tomato bin is never offered a crop: the catalogue grows no tomatoes.
     */
    private static Crop somethingCompostableReadyAt(List<Integer> group, List<StopPlan> plans,
            FarmingInputs inputs, BinAim aim) {
        if (aim == BinAim.TOMATOES) {
            return null;
        }
        for (int index : group) {
            StopPlan plan = plans.get(index);
            if (!plan.isVisit()) {
                continue;
            }
            for (PatchRef ref : plan.getStop().getPatches()) {
                PatchPrediction seen = inputs.patchAt(ref);
                if (PatchWork.at(seen, plan.getStop(), inputs.getNowEpochSec())
                        != PatchWork.Act.HARVEST) {
                    continue;
                }
                Crop crop = seen.getCrop();
                // Worth binning, not merely accepted: a bin takes grimy herbs, and every bin
                // stands beside a herb patch.
                if (!Compostables.worthBinning(crop)) {
                    continue;
                }
                if (aim != BinAim.SUPER || Compostables.keepsAbinSuper(crop)) {
                    return crop;
                }
            }
        }
        return null;
    }

    /**
     * What the player picks off this crop and drops in the bin, worded for the count.
     *
     * <p>Named per crop where the patch and the produce differ: a palm yields coconuts.
     *
     * <p>Lists crops whose produce needs its own name, whether or not a bin can be offered
     * them today. Poison ivy shares no arrival with a bin yet, but one bush stop at a farm
     * would make it reachable. Herbs that are {@code KEEP} on {@code Compostables.GOOD_FILL}
     * never reach here; toadflax would need an entry if that changed.
     *
     * <p>Everything else is the crop's name pluralised. No crop name ends in a vowel and a
     * y, so "y" always becomes "ies".
     */
    private static String produceOf(Crop crop, int count) {
        switch (crop) {
            case PALM:
                return count == 1 ? "coconut" : "coconuts";
            case CURRY:
                return count == 1 ? "curry leaf" : "curry leaves";
            case POISON_IVY:
                return count == 1 ? "poison ivy berry" : "poison ivy berries";
            default:
                break;
        }
        // The crop's own name, never "whiteberry bush": the bin takes the berries.
        String name = crop.getDisplayName().toLowerCase(Locale.ROOT);
        if (count == 1 || name.endsWith("s")) {
            return name;
        }
        return name.endsWith("y")
                ? name.substring(0, name.length() - 1) + "ies"
                : name + "s";
    }

    /**
     * The line for an arrival whose reason to exist is the replanting, not the fruit.
     *
     * <p>Fruit on a tree will still be there next time, so the trip is for the better tree
     * going in. The line reads as a tree picked clean does, from {@link #sayItMustComeDown}:
     * fruit or none, the tree comes down and leaves a stump. Unlike
     * {@link #foldReplantIntoHarvest} it leaves the planting line alone: the crop coming out
     * and the crop going in differ.
     */
    private static String sayItIsAreplacement(Work work) {
        if (work == null || work.act != PatchWork.Act.HARVEST) {
            return work == null ? "" : work.act.word(work.count,
                    work.crop == null ? null : displayNameOf(work.crop));
        }
        return sayItMustComeDown(Collections.nCopies(work.count, work.crop));
    }

    /**
     * The trees at this arrival that a sapling is going in place of, one entry per tree.
     *
     * <p>Named as crops, not answered yes-or-no, because the wording belongs to one stop of
     * the arrival: Catherby's standing-crop flower patch must not turn "Harvest the
     * toadflax" into "Chop down the toadflax tree and clear the stump".
     *
     * <p>Any fellable tree, fruit or lumber, asked of the patch directly. Read per patch
     * from the allocation: Fossil Island's three hardwood patches may take one sapling, and
     * only that patch's tree comes down. Only the allocation knows whether a tree is
     * replaced ({@code dealOutUpgrades} declines unworthy trades), and {@code CarryPlanner}
     * packs its axe from the same allocation.
     *
     * @param plantedIn which patch each seed landed in, from {@code assignCrops}; empty when
     *     the caller has no allocation, which under-states rather than invents
     */
    private static List<Crop> treesMakingWay(List<Integer> group, List<StopPlan> plans,
            Map<PatchRef, Crop> plantedIn, FarmingInputs inputs) {
        List<Crop> felling = new ArrayList<>();
        for (int index : group) {
            StopPlan plan = plans.get(index);
            if (!plan.isVisit()) {
                continue;
            }
            for (PatchRef ref : plan.getStop().getPatches()) {
                // Nothing going in here, so nothing comes out.
                if (!plantedIn.containsKey(ref)) {
                    continue;
                }
                PatchPrediction seen = inputs.patchAt(ref);
                if (seen != null && seen.getCrop() != null && seen.isFellable()) {
                    felling.add(seen.getCrop());
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
     * be wrong: a dead or diseased crop is not what the player gets from the patch, and an
     * emptied compost bin's "crop" is the compost.
     */
    private static int pictureOf(List<FarmingPlantAdvice> plants, List<Work> waiting) {
        if (!plants.isEmpty() && plants.get(0).getProduceItemId() > 0) {
            return plants.get(0).getProduceItemId();
        }
        for (Work work : waiting) {
            boolean pictured = work.act == PatchWork.Act.HARVEST
                    || work.act == PatchWork.Act.CHECK;
            if (pictured && work.crop != null && work.crop.getItemId() > 0) {
                return work.crop.getItemId();
            }
        }
        return 0;
    }

    /**
     * The harvest and replanting of one crop, said once, but only where that crop is the
     * whole of what this arrival plants. With a second crop planted, the plant lines would
     * no longer line up under each other.
     *
     * <p>Only where the crops and counts match (the rotation often picks a ranarr and
     * replants a toadflax), and only for a harvest: a check is often the reason for the
     * trip and must not hide behind "and plant".
     *
     * @param plants the arrival's planting lines, with the folded-in crop removed
     * @param digs whether every harvested plant is dug up before its replanting, which the
     *     folded line then says between the two
     */
    private static String foldReplantIntoHarvest(Work work,
            List<FarmingPlantAdvice> plants, boolean digs) {
        if (work == null) {
            return "";
        }
        String name = work.crop == null ? null : displayNameOf(work.crop);
        if (work.act != PatchWork.Act.HARVEST || name == null || plants.size() != 1) {
            return work.act.word(work.count, name);
        }
        FarmingPlantAdvice plant = plants.get(0);
        // An off-course planting keeps its own planting, whose margin icon the panel marks.
        if (!name.equals(plant.getName()) || plant.getPatches() != work.count
                || plant.getRecommendedName() != null) {
            return work.act.word(work.count, name);
        }
        plants.remove(0);
        String verbs = digs ? "Harvest, dig up and replant " : "Harvest and replant ";
        return work.count == 1
                ? verbs + "the " + inSentence(work.crop)
                : verbs + work.count + " " + inSentence(work.crop) + " patches";
    }

    /** One act waiting at an arrival, and how much of it. */
    private static final class Work {
        private final PatchWork.Act act;
        private final int count;
        /** The crop covering every patch of this act, or null when they differ. */
        private final Crop crop;

        Work(PatchWork.Act act, int count, Crop crop) {
            this.act = act;
            this.count = count;
            this.crop = crop;
        }
    }

    /**
     * The work already waiting at this arrival, most urgent first, and empty when there is
     * none — or none we have seen.
     *
     * <p>Judged per patch: one grown tree beside a growing one is a visit, and the grown one
     * is the reason. Ranked, not reduced to one, because {@link #actionAt} prints every act,
     * one line each, in this order.
     */
    private static List<Work> worksAt(List<Integer> group, List<StopPlan> plans,
            FarmingInputs inputs, PlayerState state) {
        Map<PatchWork.Act, Integer> counts = new EnumMap<>(PatchWork.Act.class);
        Map<PatchWork.Act, Crop> only = new EnumMap<>(PatchWork.Act.class);
        for (int index : group) {
            StopPlan plan = plans.get(index);
            if (!plan.isVisit()) {
                continue;
            }
            for (PatchRef ref : plan.getStop().getPatches()) {
                // Predicted once, so the act and the crop come from one reading.
                PatchPrediction seen = inputs.patchAt(ref);
                PatchWork.Act act = PatchWork.at(seen, plan.getStop(), inputs.getNowEpochSec());
                // A feeding allotment's harvest goes into the bin, and the bin's lines say so.
                if (act == null
                        || (act == PatchWork.Act.HARVEST && plan.getStop().feedsAbin())) {
                    continue;
                }
                // Revived where the player can cast it; see Resurrection#offeredAt.
                if (act == PatchWork.Act.CLEAR_DEAD
                        && Resurrection.offeredAt(plan.getStop().patchType(), state)) {
                    act = PatchWork.Act.RESURRECT;
                }
                int found = counts.merge(act, 1, Integer::sum);
                // Kept only while one crop covers this act, so the wording can name it.
                only.put(act, found == 1 || only.get(act) == seen.getCrop()
                        ? seen.getCrop() : null);
            }
        }

        // In Act's declaration order, which is the ranking.
        List<Work> waiting = new ArrayList<>();
        for (PatchWork.Act act : PatchWork.Act.values()) {
            Integer count = counts.get(act);
            if (count != null && count > 0) {
                waiting.add(new Work(act, count, only.get(act)));
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
    private static String subjectOf(StopPlan lead, FarmingInputs inputs) {
        if (lead.getSkipReason() != FarmingSkipReason.STILL_GROWING
                && lead.getSkipReason() != FarmingSkipReason.STANDING_CROP) {
            return "";
        }
        PatchPrediction seen = inputs.patchAt(lead.getStop().primaryPatch());
        if (seen == null || seen.getCrop() == null) {
            return "";
        }
        return inSentence(seen.getCrop());
    }

    /**
     * Which plan speaks for an arrival.
     *
     * <p>The first visited stop that is a reason to go: worked, and neither an extra nor a
     * ride-along. Failing that, the most urgent skip, because the row's heading, reason and
     * sentence all come from the lead and must describe the same patch.
     *
     * <p><b>An optional stop never speaks for a place anything is waiting at.</b> Its
     * {@link RunStop#getOptionalReason()} says it is worth working in passing, not worth the
     * journey. {@code routesItself} says the same of a compost bin.
     *
     * <p>Any skip outranks an optional visit. That would drop a ready extra at an arrival
     * where every stop is an extra, but no such multi-stop arrival exists.
     */
    static StopPlan leadOf(List<Integer> group, List<StopPlan> plans) {
        StopPlan inPassing = null;
        StopPlan ridesAlong = null;
        StopPlan waiting = null;
        for (int index : group) {
            StopPlan plan = plans.get(index);
            if (plan.isVisit()) {
                if (!routesItself(plan.getStop())) {
                    if (ridesAlong == null) {
                        ridesAlong = plan;
                    }
                } else if (plan.getStop().isOptional()) {
                    if (inPassing == null) {
                        inPassing = plan;
                    }
                } else {
                    return plan;
                }
                continue;
            }
            if (waiting == null || FarmingSkipReason.moreUrgent(
                    waiting.getSkipReason(), plan.getSkipReason()) != waiting.getSkipReason()) {
                waiting = plan;
            }
        }
        // A stop that cannot justify a journey does not become one by being the only thing
        // ready: the row says what the place is waiting on instead.
        if (waiting != null) {
            return waiting;
        }
        // Only extras here. Prefer one with its own route, so the row has a route to show.
        if (inPassing != null) {
            return inPassing;
        }
        return ridesAlong != null ? ridesAlong : plans.get(group.get(0));
    }

    /**
     * Whether the player travels to this group: its row is a visit, which its lead decides.
     * A visit riding along under a skipped lead is worked only by someone already there.
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
    private static String patchLabel(PatchRef patch) {
        FarmingWorldMap.PatchDef def = patch.resolve();
        if (def == null) {
            return patch.getLabel();
        }
        String kind = kindOf(def.getType());
        String name = def.getPatchName();
        if (name == null || name.isEmpty()) {
            return Character.toUpperCase(kind.charAt(0)) + kind.substring(1);
        }
        // Hespori, anima and mushroom patches are named for their kind: "Hespori patch",
        // never "Hespori hespori".
        if (name.equalsIgnoreCase(kind)) {
            return name + " patch";
        }
        return compassPoint(name) + " " + kind;
    }

    /** A patch kind as a player says it: "compost bin", "hardwood tree", "allotment". */
    private static String kindOf(PatchType type) {
        switch (type) {
            case COMPOST:
            case BIG_COMPOST:
                return "compost bin";
            case HARDWOOD_TREE:
                return "hardwood tree";
            default:
                return type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        }
    }

    /** "North West" as the game writes a direction: "North-west". Other names unchanged. */
    private static String compassPoint(String name) {
        String[] words = name.split(" ");
        boolean direction = words.length == 2
                && (words[0].equals("North") || words[0].equals("South"))
                && (words[1].equals("East") || words[1].equals("West"));
        return direction ? words[0] + "-" + words[1].toLowerCase(Locale.ROOT) : name;
    }

    /**
     * What to call an arrival: the place when it covers several patches, the patch itself
     * when it is only one.
     *
     * <p>Counts patches, not plans: one Fossil Island stop is three hardwood patches.
     */
    private static String nameOfGroup(List<Integer> group, List<StopPlan> plans) {
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
     * for one suited to a patch the player is not visiting today. Only visited stops that
     * {@link #routesItself} count, so a compost bin cannot make a second patch.
     *
     * <p>This overload grants position ({@code mayStandHere}); a trip's walkthrough calls
     * the overload that states the allowance.
     *
     * @param reached the patches this trip has already worked, which a rung leaning on
     *     another stop is answered from. It must hold this visit's predecessors and nothing
     *     later.
     */
    static StopPlan routeForVisit(List<Integer> visit, List<StopPlan> plans,
            StopPlan lead, RunPlanner planner, PlayerState state,
            Map<PatchRef, CapabilityState> reached) {
        return routeForVisit(visit, plans, lead, planner, state, reached, true, null);
    }

    /**
     * The same, saying whether this is the visit the player is travelling to and what the
     * journey before it left them ({@link RunPlanner#planArrival} with a start).
     */
    static StopPlan routeForVisit(List<Integer> visit, List<StopPlan> plans,
            StopPlan lead, RunPlanner planner, PlayerState state,
            Map<PatchRef, CapabilityState> reached, boolean mayStandHere,
            RunPlanner.Start start) {
        if (!lead.isVisit()) {
            return null;
        }
        int working = 0;
        for (int index : visit) {
            StopPlan plan = plans.get(index);
            if (plan.isVisit() && routesItself(plan.getStop())) {
                working++;
            }
        }
        return working < 2 ? null
                : planner.planArrival(lead.getStop(), state, reached, mayStandHere, start);
    }

    /**
     * Whether this stop has an opinion about how the player arrives: any rung with a
     * requirement. A compost bin's chain is one unconditional "fill it while passing", so it
     * must not turn a one-patch visit into a two-patch one.
     */
    static boolean routesItself(RunStop stop) {
        for (TravelOption rung : stop.getTravel()) {
            if (!rung.getRequires().isUnconditional()) {
                return true;
            }
        }
        return false;
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
    private static String noteFor(StopPlan plan, List<Crop> planted, FarmingInputs inputs,
            PlayerState state) {
        RunStop stop = plan.getStop();
        Crop crop = planted.isEmpty()
                ? FixedCrops.cropFor(stop, inputs.getPreferences(), state)
                : planted.get(0);
        int everyNth = FarmingCatalogue.everyNthRun(stop, crop);
        if (plan.isVisit() && everyNth > 1 && inputs.patchAt(stop.primaryPatch()) == null) {
            return "Plant this " + howOften(everyNth) + ".";
        }
        return "";
    }

    /** What a row says when every patch it plants is unseen. */
    static final String ALL_UNSEEN = "Not seen yet, so assumed empty.";

    /** What a row says when some of the patches it plants are unseen. */
    static final String SOME_UNSEEN = "Some patches not seen yet, so assumed empty.";

    /**
     * What the row owns up to guessing: the scheduler counts an unseen patch as empty
     * ground, which may be what sent the player here. Empty when every patch the row visits
     * to plant has been seen.
     */
    private static String assumptionAt(List<Integer> group, List<StopPlan> plans,
            FarmingInputs inputs) {
        // Every patch, as the scheduler counts them: Fossil Island's three hardwood patches
        // are one stop.
        int planting = 0;
        int unseen = 0;
        for (int index : group) {
            StopPlan plan = plans.get(index);
            if (!plan.isVisit() || !plan.getStop().isPlanting()) {
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
        return unseen == planting ? ALL_UNSEEN : SOME_UNSEEN;
    }

    private static final List<String> ORDINALS = List.of("", "", "second", "third", "fourth",
            "fifth", "sixth", "seventh", "eighth", "ninth");

    /**
     * "every seventh herb run". Past the words, which no catalogue pacing reaches yet,
     * "once in 12 herb runs" rather than a made-up ordinal.
     */
    private static String howOften(int n) {
        return n < ORDINALS.size()
                ? "every " + ORDINALS.get(n) + " herb run"
                : "once in " + n + " herb runs";
    }

    // --- What a route looks like ---------------------------------------------

    /**
     * The item this stop's route is taken on, or 0 for one that is cast or walked.
     *
     * <p>A tablet answers for itself. Otherwise the first of the route's needs with an icon:
     * a rung is often an item plus something invisible (a cloak and a diary tier), and the
     * player clicks the item.
     */
    private static int travelItemOf(StopPlan plan) {
        if (!plan.isVisit()) {
            return 0;
        }
        TeleportMeans means = plan.getTeleport();
        if (means != null && means.isTablet()) {
            return means.getItemId();
        }
        for (Capability capability : plan.getNeeds()) {
            int itemId = TravelIcons.forCapability(capability);
            if (itemId > 0) {
                return itemId;
            }
        }
        return 0;
    }

    /** The spell icon this stop's route is cast from, or 0 when it is not a spell. */
    private static int travelSpriteOf(StopPlan plan) {
        TeleportMeans means = plan.isVisit() ? plan.getTeleport() : null;
        return means != null && means.isSpell() ? means.getSpriteId() : 0;
    }

    /** The place this route's teleport lands at, or null for a route written as an item. */
    private static String landingOf(StopPlan plan) {
        Destination landing = plan.isVisit() && plan.getRoute() != null
                ? plan.getRoute().getDestination() : null;
        return landing == null ? null : landing.name();
    }

    // --- Plumbing ------------------------------------------------------------

    /** A mid-sentence clause, such as the runs list's contract detail, made to open a line. */
    private static String capitalise(String sentence) {
        return sentence == null || sentence.isEmpty()
                ? ""
                : Character.toUpperCase(sentence.charAt(0)) + sentence.substring(1);
    }

    private static FarmingStopAdvice.Outcome outcomeOf(StopPlan plan) {
        switch (plan.getOutcome()) {
            case VISIT:
                return FarmingStopAdvice.Outcome.VISIT;
            case SKIP:
                return FarmingStopAdvice.Outcome.SKIP;
            case UNREACHABLE:
                return FarmingStopAdvice.Outcome.UNREACHABLE;
            default:
                return FarmingStopAdvice.Outcome.UNROUTED;
        }
    }

    /** The game's own name for the patch, so the panel and the world agree. */
    private static String nameOf(PatchRef patch) {
        FarmingWorldMap.PatchDef def = patch.resolve();
        return def != null ? def.displayName() : patch.getLabel();
    }
}
