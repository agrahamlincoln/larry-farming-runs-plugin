package com.larryfarming.farming.engine;

import static com.larryfarming.farming.engine.CropNames.displayNameOf;
import static com.larryfarming.farming.engine.CropNames.inSentence;

import com.larryfarming.engine.farming.*;
import com.larryfarming.farming.catalogue.*;
import com.larryfarming.farming.detection.CapabilityState;
import com.larryfarming.farming.model.*;
import com.larryfarming.model.PlayerState;
import com.larryfarming.snapshot.*;
import java.util.*;
import lombok.AllArgsConstructor;

/**
 * One arrival of a walkthrough written as its row: the heading, the route, the work waiting,
 * the plantings, the patches set aside, and the quiet note.
 *
 * <p>Everything here is already decided by the time it is called. {@link FarmingAdviceBuilder}
 * plans the run and the trip and hands each arrival over, as indices into its plans.
 */
final class StopRowWriter {

    private StopRowWriter() {
    }

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
        for (int index : group) {
            StopPlan plan = plans.get(index);
            // Only where the player is going, so a skipped arrival lists no plantings.
            if (visiting) {
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

        List<FarmingPlantAdvice> plants = new ArrayList<>(patchesPerCrop.size());
        for (Map.Entry<Crop, Integer> planted : patchesPerCrop.entrySet()) {
            Crop crop = planted.getKey();
            plants.add(new FarmingPlantAdvice(displayNameOf(crop), crop.getItemId(), planted.getValue()));
        }

        // Only where the player is going: leadOf hands the row to a skip when every visit
        // here is an extra. Read once, for the sentence and for the contract-line check.
        List<Work> waiting = visiting ? worksAt(group, plans, inputs) : Collections.emptyList();
        // Chosen before actionAt, which drops a replant of the harvested crop from plants.
        int iconItemId = pictureOf(plants, waiting);
        List<Crop> diggingUp = visiting ? plantsComingOut(group, plans, cropFor, inputs, state) : Collections.emptyList();
        List<String> actions = actionAt(waiting, plants, plants.isEmpty() ? Collections.emptyList()
                        : treesMakingWay(group, plans, plantedIn, inputs), diggingUp);
        if (visiting) {
            // Last, because the farmer protects what has gone in. The bag packs the same fee.
            actions.addAll(payTheFarmer(group, plans, cropFor, state));
        }
        // The errand goes first: Jane names the next contract's crop, and planting the guild
        // patches before hearing her can fill the very patch it wants. The sentence is
        // borrowed from RunScheduler so the two never disagree.
        if (visiting && anErrandIsBeingRun(group, plans) && !theWorkLineAlreadySaysIt(waiting, patchesPerCrop.keySet(), inputs)
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

        return FarmingStopAdvice.builder().patchName(nameOfGroup(group, plans)).outcome(outcome)
                // The planner's composed sentence. A destination-named rung stores only its
                // tail ("then run east"); the teleport clause is added at planning time.
                .instruction(visiting ? travel.getBecause() : "").actions(actions).iconItemId(iconItemId).because(travel.getBecause())
                // No skip reason on a visited arrival; set-aside lines carry per-patch
                // detail. From the lead, so heading and sentence describe the same patch.
                .skipReason(visiting ? null : lead.getSkipReason()).subject(subjectOf(lead, inputs))
                // The lead's clock, matching the rest of the heading.
                .readyAtEpochSec(lead.getReadyAtEpochSec()).uncertain(uncertain).optional(optional)
                .optionalReason(optional && lead.getStop().getOptionalReason() != null ? lead.getStop().getOptionalReason() : "")
                .plants(plants).setAside(setAside).travelItemId(travelItemOf(travel)).travelSpriteId(travelSpriteOf(travel))
                .landing(landingOf(travel)).note(note).assumption(visiting ? assumptionAt(group, plans, inputs) : "").build();
    }

    /** Whether a stop being visited here is an errand rather than patch work. */
    private static boolean anErrandIsBeingRun(List<Integer> group, List<StopPlan> plans) {
        for (StopPlan plan : visitsIn(group, plans)) {
            if (plan.getStop().isErrand()) {
                return true;
            }
        }
        return false;
    }

    /** The stops at this arrival the player is visiting, in the arrival's order. */
    private static List<StopPlan> visitsIn(List<Integer> group, List<StopPlan> plans) {
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
    private static boolean theWorkLineAlreadySaysIt(List<Work> waiting, Collection<Crop> planting, FarmingInputs inputs) {
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
     * <p>Every act, since several families can share one place. One line each, ordered by
     * {@code Act}'s ranking; joined clauses are unreadable in the narrow panel column.
     *
     * @param plants  the arrival's planting lines, with any folded-in crop removed
     * @param diggingUp grown bushes and cacti coming out for a fresh plant
     */
    private static List<String> actionAt(List<Work> waiting, List<FarmingPlantAdvice> plants, List<Crop> felling, List<Crop> diggingUp) {
        // The +2 is the felling and digging lines, which no work carries.
        List<String> lines = new ArrayList<>(waiting.size() + 2);
        boolean dugUpSaid = false;
        // Trees still owed a mention; crossed off as lines below speak for them.
        List<Crop> unspoken = felling.isEmpty() ? felling : new ArrayList<>(felling);
        for (int i = 0; i < waiting.size(); i++) {
            Work work = waiting.get(i);
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
                boolean digs = !diggingUp.isEmpty() && Collections.frequency(diggingUp, work.crop) == diggingUp.size()
                        && diggingUp.size() == work.count;
                int unfolded = plants.size();
                // A digging the fold cannot say would follow its replant, so no fold then.
                phrase = digs || diggingUp.isEmpty() ? foldReplantIntoHarvest(work, plants, digs) : work.word();
                // A fold takes the planting out of plants, and says the digging with it.
                dugUpSaid = digs && plants.size() < unfolded;
            } else {
                phrase = work.word();
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
     * One row line: the verb, the crop when one covers every patch, and the patch count past
     * one. "Harvest ranarr ×3", "Check maple", "Cure ×2". The margin icon pictures the crop.
     */
    static String line(String verb, Crop crop, int patches) {
        return verb + (crop == null ? "" : " " + inSentence(crop)) + FarmingPlantAdvice.countOf(patches);
    }

    /** The crop every entry names, or null when they differ. */
    private static Crop sharedCrop(List<Crop> crops) {
        return Collections.frequency(crops, crops.get(0)) == crops.size() ? crops.get(0) : null;
    }

    /**
     * The line for trees that have to be felled before the patches under them can be
     * replanted, one entry per tree from {@link #treesMakingWay}. Names the stump too: a
     * felled tree leaves a stump that takes a spade before a sapling can go in.
     */
    private static String sayItMustComeDown(List<Crop> felling) {
        return line("Chop down", sharedCrop(felling), felling.size()) + (felling.size() == 1 ? ", clear stump" : ", clear stumps");
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
                if (PatchWork.comesOut(seen, plan.getStop(), inputs.getPreferences(), state)) {
                    out.add(seen.getCrop());
                }
            }
        }
        return out;
    }

    /** "Dig up redberry bush ×2". */
    private static String sayItMustBeDugUp(List<Crop> plants) {
        return line("Dig up", sharedCrop(plants), plants.size());
    }

    /** Said before a belladonna harvest: bare hands cannot pick nightshade. */
    static final String GLOVES_FOR_NIGHTSHADE = "Wear gloves to pick the nightshade";

    /**
     * The protection payment at this arrival, worded for the row. {@link Protection} decides
     * it from the same planting the bag reads, so the act and the packed fee always match.
     */
    private static List<String> payTheFarmer(List<Integer> group, List<StopPlan> plans, List<List<Crop>> cropFor, PlayerState state) {
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
     * fruit or none, the tree comes down and leaves a stump. Unlike
     * {@link #foldReplantIntoHarvest} it leaves the planting line alone: the crop coming out
     * and the crop going in differ.
     */
    private static String sayItIsAreplacement(Work work) {
        return work.act != PatchWork.Act.HARVEST ? work.word() : sayItMustComeDown(Collections.nCopies(work.count, work.crop));
    }

    /**
     * The trees at this arrival that a sapling is going in place of, one entry per tree.
     *
     * <p>Named as crops, not answered yes-or-no, because the wording belongs to one stop of
     * the arrival: Catherby's standing-crop flower patch must not turn "Harvest toadflax"
     * into "Chop down toadflax, clear stump".
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
        for (StopPlan plan : visitsIn(group, plans)) {
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
    private static String foldReplantIntoHarvest(Work work, List<FarmingPlantAdvice> plants, boolean digs) {
        if (work.act != PatchWork.Act.HARVEST || work.crop == null || plants.size() != 1) {
            return work.word();
        }
        FarmingPlantAdvice plant = plants.get(0);
        if (!displayNameOf(work.crop).equals(plant.getName()) || plant.getPatches() != work.count) {
            return work.word();
        }
        plants.remove(0);
        return line(digs ? "Harvest, dig up, replant" : "Harvest, replant", work.crop, work.count);
    }

    /** One act waiting at an arrival, and how much of it. */
    @AllArgsConstructor
    private static final class Work {
        private final PatchWork.Act act;
        private final int count;
        /** The crop covering every patch of this act, or null when they differ. */
        private final Crop crop;

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
     */
    private static List<Work> worksAt(List<Integer> group, List<StopPlan> plans, FarmingInputs inputs) {
        Map<PatchWork.Act, Integer> counts = new EnumMap<>(PatchWork.Act.class);
        Map<PatchWork.Act, Crop> only = new EnumMap<>(PatchWork.Act.class);
        for (StopPlan plan : visitsIn(group, plans)) {
            for (PatchRef ref : plan.getStop().getPatches()) {
                // Predicted once, so the act and the crop come from one reading.
                PatchPrediction seen = inputs.patchAt(ref);
                PatchWork.Act act = PatchWork.at(seen, plan.getStop(), inputs.getNowEpochSec());
                if (act == null) {
                    continue;
                }
                int found = counts.merge(act, 1, Integer::sum);
                // Kept only while one crop covers this act, so the wording can name it.
                only.put(act, found == 1 || only.get(act) == seen.getCrop() ? seen.getCrop() : null);
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
        if (lead.getSkipReason() != FarmingSkipReason.STILL_GROWING && lead.getSkipReason() != FarmingSkipReason.STANDING_CROP) {
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
     * <p>The first visited stop that is a reason to go: worked, and not an extra. Failing
     * that, the most urgent skip, because the row's heading, reason and sentence all come
     * from the lead and must describe the same patch.
     *
     * <p><b>An optional stop never speaks for a place anything is waiting at.</b> Its
     * {@link RunStop#getOptionalReason()} says it is worth working in passing, not worth the
     * journey.
     *
     * <p>Any skip outranks an optional visit. That would drop a ready extra at an arrival
     * where every stop is an extra, but no such multi-stop arrival exists.
     */
    static StopPlan leadOf(List<Integer> group, List<StopPlan> plans) {
        StopPlan inPassing = null;
        StopPlan waiting = null;
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
            if (waiting == null || FarmingSkipReason.moreUrgent(waiting.getSkipReason(), plan.getSkipReason()) != waiting.getSkipReason()) {
                waiting = plan;
            }
        }
        // A stop that cannot justify a journey does not become one by being the only thing
        // ready: the row says what the place is waiting on instead.
        if (waiting != null) {
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

    /** A patch kind as a player says it: "hardwood tree", "allotment". */
    private static String kindOf(PatchType type) {
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
        if (!lead.isVisit()) {
            return null;
        }
        return visitsIn(visit, plans).size() < 2 ? null : planner.planArrival(lead.getStop(), state, reached, mayStandHere, start);
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
    private static String noteFor(StopPlan plan, List<Crop> planted, FarmingInputs inputs, PlayerState state) {
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
    private static String assumptionAt(List<Integer> group, List<StopPlan> plans, FarmingInputs inputs) {
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
        Destination landing = plan.isVisit() && plan.getRoute() != null ? plan.getRoute().getDestination() : null;
        return landing == null ? null : landing.name();
    }

    // --- Plumbing ------------------------------------------------------------

    /** A mid-sentence clause, such as the runs list's contract detail, made to open a line. */
    private static String capitalise(String sentence) {
        return sentence == null || sentence.isEmpty() ? "" : Character.toUpperCase(sentence.charAt(0)) + sentence.substring(1);
    }

    /** The game's own name for the patch, so the panel and the world agree. */
    private static String nameOf(PatchRef patch) {
        FarmingWorldMap.PatchDef def = patch.resolve();
        return def != null ? def.displayName() : patch.getLabel();
    }
}
