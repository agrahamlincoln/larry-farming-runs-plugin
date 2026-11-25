package com.larryfarming.farming.engine;

import com.larryfarming.engine.ItemCounts;
import com.larryfarming.engine.farming.*;
import com.larryfarming.farming.catalogue.CropData;
import com.larryfarming.farming.catalogue.FarmingCatalogue;
import com.larryfarming.farming.detection.BottomlessBucket;
import com.larryfarming.farming.model.*;
import com.larryfarming.model.PlayerState;
import com.larryfarming.snapshot.FarmingPlantAdvice;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Value;
import net.runelite.api.gameval.ItemID;

/**
 * The compost bin beside each herb farm's allotments, and what the player is to do about it.
 * A bin is not a stop: it is the compost patch in the map region of the farm whose
 * allotments are picked for compost.
 */
final class CompostBins {

    /** Volcanic ash that lifts a bin of supercompost to ultracompost. */
    static final int ASH_PER_BIN = 25;
    /** A bin closes on 15 items, and 15 buckets of compost come out of it. */
    static final int BIN_HOLDS = 15;
    /** Compost potion of one to four doses. */
    static final int[] POTIONS = {
        ItemID.SUPERCOMPOST_POTION_1, ItemID.SUPERCOMPOST_POTION_2,
        ItemID.SUPERCOMPOST_POTION_3, ItemID.SUPERCOMPOST_POTION_4};
    /** An empty bucket and its note. */
    static final int[] EMPTY_BUCKETS = {ItemID.BUCKET_EMPTY, ItemID.Cert.BUCKET_EMPTY};

    /** A farm's bin, the arrival it is worked from, and the allotments that feed it, by site. */
    @Value
    static class Farm {
        PatchRef bin;
        Arrival arrival;
        Map<PatchRef, String> allotments;
    }

    /** What to do at a farm's bin. */
    enum Verdict {
        /** Empty, filling or unseen: harvest the allotments into it. */
        FILL,
        /** Finished, its upgrade covered: upgrade it and empty it. */
        EMPTY,
        /**
         * Left as it stands this run, with the crops standing beside it: rotting, full and
         * still open, short of its upgrade or of empty buckets, or at Ortus Farm with no
         * bottomless bucket to take it.
         */
        LEAVE
    }

    /**
     * A bin's verdict, what the farm's row says of it, the empty buckets its compost goes
     * into, and the volcanic ash it takes. Both counts are what the bin empties into or uses,
     * or what it waits for.
     *
     * <p>The line is null for none. For {@link Verdict#EMPTY} it is the steps the row's
     * "Compost:" line opens with ("add 25 volcanic ash, empty into buckets"); for
     * {@link Verdict#LEAVE} it is the row's whole sentence.
     */
    @Value
    static class Judged {
        Verdict verdict;
        String line;
        int buckets;
        int ash;
    }

    /**
     * What the bins at a set of arrivals want carried: the potion and ash, whether any
     * empties into the bottomless bucket, the empty buckets the rest fill, and whether the
     * player's empty buckets run low.
     */
    @Value
    static class Needs {
        int doses;
        int ash;
        boolean bucket;
        int buckets;
        boolean restock;
    }

    /** The farms with a bin, by map region, in the stops table's order. */
    static final Map<Integer, Farm> FARMS = farms();

    private CompostBins() {
    }

    /** Each herb farm with allotments that can feed a bin, in the order the herb sites are listed. */
    private static Map<Integer, Farm> farms() {
        Map<Integer, Farm> farms = new LinkedHashMap<>();
        for (FarmingCatalogue.SiteSwitch site : FarmingCatalogue.siteSwitches(RunId.HERB)) {
            // The allotments, bin and herb patch of a farm share its map region.
            int region = site.getStop().primaryPatch().getRegionId();
            Map<PatchRef, String> allotments = new LinkedHashMap<>();
            FarmingCatalogue.picksByPatch().forEach((ref, pick) -> {
                if (ref.getRegionId() == region && pick.getCompostKey() != null) {
                    allotments.put(ref, pick.getSite());
                }
            });
            for (FarmingWorldMap.PatchDef def : FarmingWorldMap.allPatches()) {
                if (def.getRegionId() == region && def.getType() == PatchType.COMPOST && !allotments.isEmpty()) {
                    farms.put(region, new Farm(PatchRef.of(region, def.getVarbitId(), def.displayName()), site.getStop().getArrival(),
                            Collections.unmodifiableMap(allotments)));
                }
            }
        }
        return Collections.unmodifiableMap(farms);
    }

    /** The farm any stop of this arrival belongs to, or null for an arrival with no bin. */
    static Farm farmAt(List<Integer> group, List<StopPlan> plans) {
        return group.stream().map(index -> farmOf(plans.get(index))).filter(Objects::nonNull).findFirst().orElse(null);
    }

    /** The farm whose map region holds this stop's patch, or null. */
    private static Farm farmOf(StopPlan plan) {
        return FARMS.get(plan.getStop().primaryPatch().getRegionId());
    }

    /**
     * Whether a bucket has come out of this finished bin. The upgrade costs the whole bin's
     * potion or ash however little is left, so a started bin is only emptied.
     */
    private static boolean started(PatchPrediction bin) {
        return bin.getState() == CropState.HARVESTABLE && bin.getStage() + 1 < BIN_HOLDS;
    }

    /** Doses of compost potion to lift a finished bin to the account's step. */
    static int dosesFor(PatchPrediction bin, Crop step) {
        return !started(bin) && bin.getCrop() == Crop.COMPOST && step.ordinal() >= Crop.SUPERCOMPOST.ordinal() ? 1 : 0;
    }

    /** Volcanic ash to lift a finished bin to the account's step. */
    static int ashFor(PatchPrediction bin, Crop step) {
        return !started(bin) && step == Crop.ULTRACOMPOST && (bin.getCrop() == Crop.COMPOST || bin.getCrop() == Crop.SUPERCOMPOST)
                ? ASH_PER_BIN : 0;
    }

    /**
     * The empty buckets the player owns anywhere, the leprechaun's included, or only those
     * carried, noted or not.
     */
    static int emptyBuckets(PlayerState state, boolean carried) {
        return Arrays.stream(EMPTY_BUCKETS)
                .map(itemId -> carried ? ItemCounts.countCarried(state, itemId) : ItemCounts.countOwned(state, itemId)).sum();
    }

    /**
     * The verdict at each farm Larry works. The finished bins share the potion, ash and
     * empty buckets the player owns, in the order the plan on screen reaches their farms,
     * and a farm off that plan comes after them. The bins are judged before the trip is
     * planned, so the plan on screen is the trip; before there is one, they share in table
     * order. Fertile Soil's casts take their ash first, and the bins share what is left. A
     * bin the stock doesn't cover waits.
     *
     * <p>A bin empties into the bottomless bucket when the player owns it as an item, it has
     * been read, and it is empty or holds the bin's compost once upgraded. In game the plain
     * empty buckets in the inventory fill first, and the bottomless bucket takes the rest of
     * the bin. Those carried buckets are used up for later bins, and the row doesn't name
     * them. When the carried buckets alone hold every item left in the bin, the bin empties
     * into plain buckets, and the carried ones count first toward it. Every other bin empties
     * into plain buckets, carried, brought noted or taken from the leprechaun, and the compost
     * is stored at the leprechaun. Ortus Farm has no leprechaun, so its bin is emptied only
     * into the bottomless bucket and otherwise waits without a line.
     *
     * <p>{@link FarmingInputs#seeing} judges them once, and the holds, the rows and the bag
     * all read that judgement.
     */
    static Map<Farm, Judged> verdicts(FarmingInputs inputs, PlayerState state) {
        return verdicts(inputs, state, RunSettings.quietPicks(inputs.getPreferences(), state, inputs::patchAt));
    }

    /**
     * The verdicts, given the preset picks the account can't plant yet, which work no bin.
     *
     * @param quiet {@link RunSettings#quietPicks} for these inputs and this player
     */
    static Map<Farm, Judged> verdicts(FarmingInputs inputs, PlayerState state, Set<String> quiet) {
        Map<Farm, Judged> verdicts = new LinkedHashMap<>();
        CompostStock compost = inputs.getCompost();
        // Before the bank is seen the stock is a floor, so the bins stand quietly.
        if (!compost.isOn() || state == null || state.getBankLastSeen() == null) {
            return verdicts;
        }
        List<String> ahead = inputs.getAhead();
        List<Farm> order = new ArrayList<>(FARMS.values());
        order.sort(Comparator.comparingInt(farm -> ahead.contains(farm.getArrival().name()) ? ahead.indexOf(farm.getArrival().name())
                : ahead.size()));
        Crop step = compost.getStep();
        // What the player owns, and what is left of it for the next bin.
        int ownedDoses = doses(state, false);
        int ownedAsh = ItemCounts.countOwned(state, ItemID.FOSSIL_VOLCANIC_ASH);
        int ownedEmpties = emptyBuckets(state, false);
        int doses = ownedDoses;
        int ash = Math.max(0, ownedAsh - inputs.getFertileAsh());
        int empties = ownedEmpties;
        int carried = ItemCounts.countCarried(state, ItemID.BUCKET_EMPTY);
        // An unread bucket may hold any kind, so no bin goes into it.
        boolean bottomless = BottomlessBucket.toHand(state) && !compost.isBucketUnread();
        // The bucket holds one kind at a time: what it holds now, then what the bins put in it.
        Crop into = compost.getBucketKind();
        for (Farm farm : order) {
            if (!worked(farm, inputs.getPreferences(), quiet)) {
                continue;
            }
            PatchPrediction seen = inputs.patchAt(farm.getBin());
            CropState at = seen == null ? CropState.EMPTY : seen.getState();
            boolean full = at == CropState.FILLING && seen.getStage() + 1 >= BIN_HOLDS;
            // A closed bin reads harvestable only beside it. From anywhere else it reads
            // growing with its timer run out.
            if (!full && at != CropState.HARVESTABLE && at != CropState.GROWING) {
                verdicts.put(farm, new Judged(Verdict.FILL, null, 0, 0));
                continue;
            }
            int binDoses = dosesFor(seen, step);
            int binAsh = ashFor(seen, step);
            // The compost the bin gives once upgraded. A started bin below the step stays
            // out of the bottomless bucket, which holds the step's compost.
            Crop kind = binDoses + binAsh > 0 ? step : seen.getCrop();
            boolean compostKind = kind != Crop.ROTTEN_TOMATO;
            int left = started(seen) ? seen.getStage() + 1 : BIN_HOLDS;
            // The carried empty buckets fill first. When they hold the whole bin, the bin
            // empties into them; otherwise the bottomless bucket takes the rest.
            boolean intoBucket = compostKind && bottomless && carried < left
                    && (into != null ? kind == into : kind.compareTo(step) >= 0 || !started(seen));
            // One plain bucket for each item left in the bin.
            int buckets = compostKind && !intoBucket ? left : 0;
            // The carried buckets a bottomless-bucket bin fills first are used up all the same.
            int firstFilled = intoBucket ? carried : 0;
            // Ortus Farm has no leprechaun to take buckets from or store the compost with.
            if (buckets > 0 && !LeprechaunPlanner.hasLeprechaun(farm.getArrival())) {
                verdicts.put(farm, new Judged(Verdict.LEAVE, null, 0, 0));
                continue;
            }
            boolean rots = at == CropState.GROWING && !seen.isReadyAt(inputs.getNowEpochSec());
            boolean shortOfDose = binDoses > doses;
            boolean shortOfAsh = binAsh > ash;
            boolean shortOfBuckets = !full && !rots && !shortOfDose && !shortOfAsh && buckets > empties;
            String leave = full ? "Close the compost bin and leave the crops beside it for next run"
                    : rots ? "Leave the compost bin while it rots"
                    // Some they own may be spoken for, so the line asks for the shortfall as more.
                    : shortOfDose || shortOfAsh ? "Leave the compost bin until you have "
                            + upgrade(!shortOfDose ? null : ownedDoses > 0 ? "another dose of compost potion" : DOSE,
                                    !shortOfAsh ? null : binAsh - ash + (ownedAsh > 0 ? " more" : "") + " volcanic ash")
                    : shortOfBuckets ? "Leave the compost bin until you have " + (buckets - empties) + (ownedEmpties > 0 ? " more" : "")
                            + (buckets - empties == 1 ? " empty bucket" : " empty buckets") : null;
            if (leave != null) {
                verdicts.put(farm, new Judged(Verdict.LEAVE, leave, shortOfBuckets ? buckets : 0, !full && !rots && shortOfAsh ? binAsh : 0));
                continue;
            }
            doses -= binDoses;
            ash -= binAsh;
            empties -= buckets + firstFilled;
            carried = Math.max(0, carried - buckets - firstFilled);
            into = intoBucket ? kind : into;
            String upgrade = upgrade(binDoses > 0 ? DOSE : null, binAsh > 0 ? ASH_PER_BIN + " volcanic ash" : null);
            String where = intoBucket ? " into your bottomless bucket" : buckets > 0 ? " into buckets" : " the bin";
            // The upgrade leads when there is one: it is the step a player forgets, where
            // emptying the bin is obvious. The row joins these steps with its fill.
            verdicts.put(farm, new Judged(Verdict.EMPTY, (upgrade.isEmpty() ? "" : "add " + upgrade + ", ") + "empty" + where,
                    buckets, binAsh));
        }
        return verdicts;
    }

    /**
     * Whether the trip's Fertile Soil casts change what the bins are told, so the bins are
     * judged again on the ash the casts leave them: the casts and the bins emptied together
     * want more volcanic ash than the player owns, or a bin waits for ash, whose line counts
     * what it is short of. Bins that need no ash never change, and nor do bins on a trip
     * that casts nothing.
     *
     * @param fertileAsh the ash the trip's casts take
     */
    static boolean outrunTheAsh(FarmingInputs inputs, PlayerState state, int fertileAsh) {
        if (fertileAsh == 0) {
            return false;
        }
        int ash = fertileAsh;
        boolean leans = false;
        for (Judged judged : inputs.bins(state).values()) {
            if (judged.getAsh() > 0 && judged.getVerdict() == Verdict.LEAVE) {
                return true;
            }
            leans |= judged.getAsh() > 0;
            ash += judged.getAsh();
        }
        return leans && ash > ItemCounts.countOwned(state, ItemID.FOSSIL_VOLCANIC_ASH);
    }

    /** One dose of compost potion, as a row names it. */
    private static final String DOSE = "a dose of compost potion";

    /** The potion and ash as a row names them: "a dose of compost potion and 25 volcanic ash", or "". Either may be null. */
    private static String upgrade(String dose, String ash) {
        return dose == null ? ash == null ? "" : ash : ash == null ? dose : dose + " and " + ash;
    }

    /**
     * The picked sites Larry holds back. With the thermostat off or the bank unseen, every
     * allotment marked for compost. Otherwise the allotments with a crop standing beside a
     * bin Larry leaves, since they cannot feed it; an empty one plants, so it is grown by
     * the time the bin is worked again.
     */
    static Set<String> holding(FarmingInputs inputs, Map<Farm, Judged> verdicts) {
        Set<String> sites = new HashSet<>();
        // No farm is worked with the thermostat off or the bank unseen. Nor is one while
        // every for-compost pick is quiet or Off, which plant nothing either way.
        boolean idle = verdicts.isEmpty();
        for (Farm farm : FARMS.values()) {
            Judged judged = verdicts.get(farm);
            if (idle || judged != null && judged.getVerdict() == Verdict.LEAVE) {
                farm.getAllotments().forEach((ref, site) -> {
                    PatchPrediction seen = inputs.patchAt(ref);
                    if (inputs.getPreferences().plantsForCompost(site) && (idle || seen == null || !seen.isIdle())) {
                        sites.add(site);
                    }
                });
            }
        }
        return sites;
    }

    /**
     * What the bins at the visited farms want in the bag: the potion and ash of every bin
     * about to be emptied, and the bottomless bucket or empty buckets it empties into. A bin
     * left as it stands asks for nothing; its row names what it needs. The empty buckets run
     * low when a bin here wants them and fewer than a bin's worth would be left after the
     * trip.
     */
    static Needs over(List<StopPlan> plans, FarmingInputs inputs, PlayerState state) {
        Map<Farm, Judged> verdicts = inputs.bins(state);
        // Null for a stop at no farm, which has no verdict.
        Set<Farm> visited = plans.stream().filter(StopPlan::isVisit).map(CompostBins::farmOf)
                .collect(Collectors.toCollection(HashSet::new));
        int doses = 0;
        int ash = 0;
        boolean bucket = false;
        int buckets = 0;
        boolean wanted = false;
        for (Farm farm : visited) {
            Judged judged = verdicts.get(farm);
            wanted |= judged != null && judged.getBuckets() > 0;
            if (judged != null && judged.getVerdict() == Verdict.EMPTY) {
                PatchPrediction bin = inputs.patchAt(farm.getBin());
                doses += dosesFor(bin, inputs.getCompost().getStep());
                ash += judged.getAsh();
                buckets += judged.getBuckets();
                bucket |= judged.getBuckets() == 0 && bin.getCrop() != Crop.ROTTEN_TOMATO;
            }
        }
        return new Needs(doses, ash, bucket, buckets, wanted && emptyBuckets(state, false) - buckets < BIN_HOLDS);
    }

    /** What the row says about the bin at an arrival. */
    @Value
    static class Row {
        List<String> lines;
        /** Plan indices of the for-compost allotments harvested into the bin. */
        Set<Integer> fills;

        /** No bin here, or nothing to do at it. */
        static final Row NONE = new Row(List.of(), Set.of());
    }

    /**
     * Among allotment crops, every one needing 47 Farming or more makes supercompost and
     * every one below makes plain compost (OSRS wiki, "Compost Bin").
     */
    private static final int SUPERCOMPOSTABLE_LEVEL = 47;

    /**
     * The bin's lines for the arrival, in the order the player does them. The patches that
     * fill the bin replant with the row's other plantings, so a patch with no seed to plant
     * is harvested and left empty.
     */
    static Row rowAt(List<Integer> group, List<StopPlan> plans, FarmingInputs inputs, PlayerState state) {
        Farm farm = farmAt(group, plans);
        Judged judged = judgedAt(farm, inputs, state);
        if (judged == null) {
            return Row.NONE;
        }
        // A bin Larry leaves is a state rather than steps, and it holds the crops beside it,
        // so its sentence is all the row says of them.
        if (judged.getVerdict() == Verdict.LEAVE) {
            return new Row(judged.getLine() == null ? List.of() : List.of(judged.getLine()), Set.of());
        }
        // The bin's steps and its fill are one chain, so the row spends one line on them.
        List<String> steps = new ArrayList<>();
        if (judged.getLine() != null) {
            steps.add(judged.getLine());
        }
        PatchPrediction bin = inputs.patchAt(farm.getBin());
        boolean filling = bin != null && bin.getState() == CropState.FILLING;
        List<Integer> supers = new ArrayList<>();
        List<Integer> plain = new ArrayList<>();
        boolean onlyTomatoes = true;
        for (int index : group) {
            StopPlan plan = plans.get(index);
            PatchPick pick = FarmingCatalogue.pickOf(plan.getStop());
            if (!plan.isVisit() || pick == null || !inputs.getPreferences().plantsForCompost(pick.getSite())) {
                continue;
            }
            PatchPrediction seen = inputs.patchAt(plan.getStop().primaryPatch());
            if (PatchWork.at(seen, plan.getStop(), inputs) == PatchWork.Act.HARVEST) {
                boolean high = CropData.forCrop(seen.getCrop()).getFarmingLevel() >= SUPERCOMPOSTABLE_LEVEL;
                (high ? supers : plain).add(index);
                onlyTomatoes &= seen.getCrop() == Crop.TOMATO;
            }
        }
        // A bin filling with more than plain compost takes no ordinary crop, and a tomato
        // makes compost only beside another compostable: another patch's crop, or the
        // compost already filling the bin.
        boolean aimsHigh = judged.getVerdict() == Verdict.FILL && filling && bin.getCrop() != Crop.COMPOST;
        Set<Integer> fills = new LinkedHashSet<>(!supers.isEmpty() ? supers : aimsHigh || onlyTomatoes && !filling ? List.of() : plain);
        if (!fills.isEmpty()) {
            // The patches are named by their crop, which tells them apart where a compass point
            // would not, and a crop grown on both reads as a count. Each fill was seen ready to
            // harvest, so its crop is known. The game stops the harvest when the bin is full and
            // the rest of that patch takes another harvest, which the line leaves out. The
            // replanting joins the row's plantings, so each crop is planted in one line.
            List<Crop> crops = fills.stream().map(index -> inputs.patchAt(plans.get(index).getStop().primaryPatch()).getCrop())
                    .collect(Collectors.toList());
            Set<Crop> distinct = new LinkedHashSet<>(crops);
            // "Fill", not "Harvest", so the line does not read as a repeat of the row's own harvest line.
            steps.add("fill with " + distinct.stream().map(CropNames::inSentence).collect(Collectors.joining(" and "))
                    + (distinct.size() == 1 ? FarmingPlantAdvice.countOf(crops.size()) : ""));
        }
        // The plan counts both allotments as needed, so the bag never packs short. A bin the
        // first one fills is full on the next read, which holds the second standing as next
        // run's fill, so no line has to say which to plant.
        return new Row(steps.isEmpty() ? List.of() : List.of("Compost: " + String.join(", ", steps)), fills);
    }

    /** The verdict on a farm's bin, or null for none: no farm, or a null player. */
    private static Judged judgedAt(Farm farm, FarmingInputs inputs, PlayerState state) {
        return farm == null ? null : inputs.bins(state).get(farm);
    }

    /** Whether the bin at an arrival is emptied into empty buckets this visit, or waits for them. */
    static boolean wantsBucketsAt(List<Integer> group, List<StopPlan> plans, FarmingInputs inputs, PlayerState state) {
        Judged judged = judgedAt(farmAt(group, plans), inputs, state);
        return judged != null && judged.getBuckets() > 0;
    }

    /** Doses of compost potion the player owns, or only those carried. */
    static int doses(PlayerState state, boolean carried) {
        int doses = 0;
        for (int i = 0; i < POTIONS.length; i++) {
            doses += (i + 1) * (carried ? ItemCounts.countCarried(state, POTIONS[i]) : ItemCounts.countOwned(state, POTIONS[i]));
        }
        return doses;
    }

    /** Whether Larry works this farm's bin: some allotment there {@link #composts}. */
    private static boolean worked(Farm farm, FarmingPreferences prefs, Set<String> quiet) {
        return farm.getAllotments().values().stream().anyMatch(site -> composts(site, prefs, quiet));
    }

    /**
     * Whether this allotment plants for compost: picked for compost with a crop the account
     * can plant. A preset pick quiet for its level or its seed does not count. The bins' own
     * hold is left out, since it follows from their verdicts.
     */
    static boolean composts(String site, FarmingPreferences prefs, Set<String> quiet) {
        return prefs.plantsForCompost(site) && prefs.picks().get(site) != null && !quiet.contains(site);
    }
}
