package com.larryfarming.farming.detection;

import com.larryfarming.data.Tables;
import com.larryfarming.engine.ItemCounts;
import com.larryfarming.engine.farming.*;
import com.larryfarming.farming.catalogue.*;
import com.larryfarming.farming.detection.CapabilityVerdict.Source;
import com.larryfarming.farming.model.Capability;
import com.larryfarming.farming.model.Spellbook;
import com.larryfarming.model.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.runelite.api.gameval.ItemID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import net.runelite.api.gameval.VarbitID;

/**
 * How each capability is answered from game state.
 *
 * <p>Kept apart from the capability vocabulary so detection rules change without touching
 * the catalogue.
 *
 * <p>A capability with no rule resolves {@link CapabilityState#UNKNOWN}, which the planner
 * still offers. Adding a rule only narrows an unknown into a definite answer.
 *
 * <p>Item ownership goes through {@link ItemCounts}, which knows about the bank, the
 * tool leprechaun and the rune pouch.
 */
public final class CapabilitySources {

    private CapabilitySources() {
    }

    /** Answers one capability, or returns null to leave it unknown. */
    @FunctionalInterface
    private interface Detector {
        CapabilityVerdict detect(PlayerState state);
    }

    /**
     * Answers one capability that is really a farming patch, given what has been seen in it.
     * Separate from {@link Detector} because only a few rules need a patch.
     */
    @FunctionalInterface
    private interface PatchDetector {
        CapabilityVerdict detect(PlayerState state, PatchSight sight);
    }

    /** Which spellbook is open. */
    public static final int SPELLBOOK_VARBIT = 4070;

    /**
     * What {@link #SPELLBOOK_VARBIT} reads on each book. Being on a book proves its unlock
     * (Lunar Diplomacy for Lunar; Arceuus needs no quest), so a caller gating a spell on the
     * book needs no separate quest check.
     */
    public static final int STANDARD_SPELLBOOK = 0;
    public static final int LUNAR_SPELLBOOK = 2;
    public static final int ARCEUUS_SPELLBOOK = 3;

    /**
     * The Knight Waves Training Grounds, which opens Camelot as a respawn point. Value 8 is
     * the finished state.
     */
    private static final int KNIGHT_WAVES_VARBIT = 3909;
    private static final int KNIGHT_WAVES_DONE = 8;

    /**
     * Fairytale II's progress value (varbit 2326) once the Fairy Godfather grants the
     * fairy rings. See {@code fairyRings} for the sources.
     */
    public static final int FAIRY_RING_PERMISSION_STAGE = 40;

    /**
     * Where the player respawns, as one flag per town. At most one is set.
     *
     * <p>Lumbridge needs no unlock and has no flag: every scanned flag reading zero means
     * Lumbridge.
     *
     * <p>The towns come from the client's names for these ids. Edgeville and the Wilderness
     * also carry a separate {@code _UNLOCKED} varbit, which indicates the bare flag records
     * the choice rather than the unlock.
     *
     * <p>Unordered: one set flag names one town, and two set flags name none, whichever is
     * read first.
     */
    private static final Map<Integer, String> RESPAWN_TOWNS = Map.of(
            VarbitID.FALADOR_SPAWN, "Falador",
            VarbitID.EDGEVILLE_SPAWN, "Edgeville",
            VarbitID.CAMELOT_SPAWN, "Camelot",
            VarbitID.WILDERNESS_SPAWN, "Ferox Enclave",
            VarbitID.CIVITAS_SPAWN, "Civitas illa Fortis",
            VarbitID.KOUREND_SPAWN, "Kourend Castle");

    /** What opens each respawn town, separate from whether it is the chosen one. */
    private static final Map<String, Detector> RESPAWN_UNLOCKS = Map.of(
            "Falador", quest("Recruitment Drive", "respawning at Falador"),
            "Camelot", state -> {
                Integer done = state.getVarbits().get(KNIGHT_WAVES_VARBIT);
                if (done != null) {
                    return done >= KNIGHT_WAVES_DONE ? CapabilityVerdict.detected(
                                    "you have finished the Knight Waves Training Grounds") : CapabilityVerdict.ruledOut(
                                    "the Knight Waves Training Grounds unlocks respawning at Camelot");
                }
                return quest("King's Ransom", "respawning at Camelot").detect(state);
            });

    /**
     * Whether the account could choose this respawn town, ignoring the one it is on.
     * {@code RESPAWN_AVAILABLE_*} answers "do you respawn there" instead, so it cannot
     * suggest a switch.
     */
    public static CapabilityState canSelectRespawn(String town, PlayerState state) {
        Detector unlock = RESPAWN_UNLOCKS.get(town);
        return unlock == null ? CapabilityState.UNKNOWN : unlock.detect(state).getState();
    }

    /**
     * The town the player respawns in, or null when the flags have not been read yet or
     * contradict each other. Null is not Lumbridge.
     */
    public static String selectedRespawnTown(PlayerState state) {
        if (state == null) {
            return null;
        }
        boolean scanned = false;
        String chosen = null;
        for (Map.Entry<Integer, String> town : RESPAWN_TOWNS.entrySet()) {
            Integer value = state.getVarbits().get(town.getKey());
            if (value == null) {
                continue;
            }
            scanned = true;
            if (value == 0) {
                continue;
            }
            // Two towns set at once is a contradiction. Answer nothing, so callers fall back
            // to judging by the unlock.
            if (chosen != null) {
                return null;
            }
            chosen = town.getValue();
        }
        if (chosen != null) {
            return chosen;
        }
        return scanned ? "Lumbridge" : null;
    }

    /** The assigned farming contract's crop, and whether it has finished growing. */
    public static final int CONTRACT_PRODUCE_VARBIT = 7963;
    public static final int CONTRACT_COMPLETE_VARBIT = 7961;

    /** Set once the Lunch by the Lancalliums page is in Kharedst's memoirs. */
    public static final int LANCALLIUMS_PAGE_VARBIT = VarbitID.HOSIDIUS_PAGE;

    /** Charges left in Kharedst's memoirs or the Book of the Dead. */
    public static final int MEMOIRS_CHARGES_VARBIT = VarbitID.VEOS_MEMOIR_CHARGES;

    /** Kharedst's memoirs and the Book of the Dead, which share the page and the charges. */
    private static final int[] LANCALLIUMS_BOOK_IDS = {
        ItemID.VEOS_KHAREDSTS_MEMOIRS, ItemID.BOOK_OF_THE_DEAD,
    };

    /**
     * The simple rules, one row each, and two tables of facts: the quest gating an item only
     * that quest hands out ({@code gate}), and the skill each Cape of Accomplishment needs at
     * 99 ({@code cape}). The first cell names the kind, which fixes the columns after it.
     *
     * <p>Declared above {@link #LOGIN_SCAN_VARBIT_IDS}, which scans the flags its varbit and
     * scroll rows read.
     */
    private static final List<Tables.Row> TABLE = Tables.read("/com/larryfarming/capability-rules.tsv");

    /**
     * The varbits this package reads that nothing else in the plugin asks for. Most are set
     * once and silent afterwards, so without a login scan they read as unset for players who
     * set them before installing the plugin.
     */
    public static final int[] LOGIN_SCAN_VARBIT_IDS = loginScanVarbitIds();

    private static int[] loginScanVarbitIds() {
        // The table's flags: Ates statues, quetzal landing sites, balloon stations, the
        // bare-handed planting lesson, the master scroll book's counts.
        IntStream flags = TABLE.stream()
                .filter(row -> row.text(0).equals("varbit") || row.text(0).equals("scroll"))
                .mapToInt(row -> row.integer(2));
        int[] own = {
            HouseLocation.HOUSE_LOCATION_VARBIT,
            SPELLBOOK_VARBIT,
            KNIGHT_WAVES_VARBIT,
            // The goals ask an ironman to make what another player can buy.
            VarbitID.IRONMAN,
            // A contract assigned before login never announces itself.
            CONTRACT_PRODUCE_VARBIT,
            CONTRACT_COMPLETE_VARBIT,
        };
        int[] books = {LANCALLIUMS_PAGE_VARBIT, MEMOIRS_CHARGES_VARBIT};
        IntStream fixed = Stream.of(own, books, DiaryTiers.allVarbits()).flatMapToInt(IntStream::of);
        // The respawn flags, and the counters of rationed teleports spent today: a day's
        // allowance can be part spent before the plugin starts.
        IntStream keyed = Stream.concat(RESPAWN_TOWNS.keySet().stream(), DiaryRewards.USES_SPENT_BY_VARBIT.keySet().stream())
                .mapToInt(Integer::intValue);
        return IntStream.concat(IntStream.concat(fixed, keyed), flags).toArray();
    }

    /** A quest, and how far along it a player must be. */
    @Value
    private static class QuestGate {
        String quest;
        QuestStatus reached;

        /** True when the player is seen short of the quest. An unread quest bars nothing. */
        boolean bars(PlayerState state) {
            QuestStatus status = state.getQuests().get(quest);
            return status != null && !hasReached(status, reached);
        }
    }

    /** Items only a quest hands out, or that work only once it is done; {@link #questItems} checks the gate. */
    private static final Map<Capability, QuestGate> QUEST_GATES = new EnumMap<>(Capability.class);

    /** Capes of Accomplishment, sold only to a player with 99 in the skill. */
    private static final Map<Capability, Skill> SKILL_CAPES = new EnumMap<>(Capability.class);

    /**
     * The capabilities built in the player's house, answered from what was seen there.
     *
     * @see HouseRule#detect for the order the answers are given in
     */
    private static final Map<Capability, HouseRule> HOUSE_RULES = new EnumMap<>(Capability.class);

    /**
     * Each teleport whose use starts a cooldown: the varp holding its last use, in minutes
     * since the epoch, and the cooldown's length in minutes.
     */
    private static final Map<Capability, int[]> COOLDOWNS = new EnumMap<>(Capability.class);

    /** The varbit counting each kind of teleport scroll the master scroll book holds. */
    private static final Map<Capability, Integer> STORED_IN_BOOK = new EnumMap<>(Capability.class);

    static {
        for (Tables.Row row : TABLE) {
            if (row.text(0).equals("scroll")) {
                STORED_IN_BOOK.put(row.constant(Capability.class, 1), row.integer(2));
            } else if (row.text(0).equals("cooldown")) {
                COOLDOWNS.put(row.requireWidth(5).constant(Capability.class, 1), new int[] {row.integer(2), row.integer(4)});
            } else if (row.text(0).equals("gate")) {
                QUEST_GATES.put(row.requireWidth(4).constant(Capability.class, 1),
                        new QuestGate(row.text(2), row.constant(QuestStatus.class, 3)));
            } else if (row.text(0).equals("cape")) {
                SKILL_CAPES.put(row.requireWidth(3).constant(Capability.class, 1), row.constant(Skill.class, 2));
            } else if (row.text(0).equals("house") || row.text(0).equals("nexus")) {
                Floor floor = new Floor(row.requireWidth(9).text(4)).skill(Skill.CONSTRUCTION, row.integer(5)).quest(row.optionalText(6));
                Skill skill = row.optionalConstant(Skill.class, 7);
                if (skill != null) {
                    floor.skill(skill, row.integer(8));
                }
                HOUSE_RULES.put(row.constant(Capability.class, 1), row.text(0).equals("house")
                        ? built(row.constant(HouseFeature.class, 2), row.text(3), floor) : attuned(row.integer(2), row.text(3), floor));
            }
        }
    }

    /**
     * When this teleport's cooldown ends, in epoch seconds: 0 for a teleport with none, or
     * one whose varp has not been read.
     */
    public static long cooldownEndsAt(Capability capability, PlayerState state) {
        int[] cooldown = COOLDOWNS.get(capability);
        Integer lastUse = cooldown == null ? null : state.getVarps().get(cooldown[0]);
        return lastUse == null ? 0L : (lastUse + cooldown[1]) * 60L;
    }

    /** Whether using this teleport starts a cooldown, so it serves one journey a trip. */
    public static boolean hasCooldown(Capability capability) {
        return COOLDOWNS.containsKey(capability);
    }

    /**
     * The scrolls of this kind the master scroll book holds: 0 for none, for a count not yet
     * read, or for a capability that is no scroll.
     */
    public static int storedInBook(Capability capability, PlayerState state) {
        Integer varbit = STORED_IN_BOOK.get(capability);
        Integer stored = varbit == null ? null : state.getVarbits().get(varbit);
        return stored == null ? 0 : stored;
    }

    /** Whether the master scroll book can hold this teleport. */
    public static boolean isStoredInBook(Capability capability) {
        return STORED_IN_BOOK.containsKey(capability);
    }

    /**
     * A reading that rises when this teleport is used without leaving the bag: its cooldown's
     * end moving on, or the master scroll book holding one fewer.
     */
    public static long useMark(Capability capability, PlayerState state) {
        return cooldownEndsAt(capability, state) - storedInBook(capability, state);
    }

    /**
     * What the player packs for this capability: the master scroll book for a scroll it holds
     * when the player owns none loose, or carries the book and no loose one; otherwise the
     * capability itself. The counts outlast a lost book, so this relies on the scroll's
     * detector, which refuses a stored scroll once the bank shows no book.
     */
    public static Capability packedAs(Capability capability, PlayerState state) {
        CarryItems.Carry loose = CarryItems.forCapability(capability);
        boolean bookInHand = ItemCounts.countCarried(state, CarryItems.forCapability(Capability.MASTER_SCROLL_BOOK).getItemIds()[0]) > 0;
        return storedInBook(capability, state) > 0 && Arrays.stream(loose.getItemIds())
                .noneMatch(id -> bookInHand ? ItemCounts.countCarried(state, id) > 0 : ItemCounts.ownsAny(state, id))
                ? Capability.MASTER_SCROLL_BOOK : capability;
    }

    /** The varps the cooldowns are read from, for the login scan and the live updates. */
    public static Set<Integer> cooldownVarps() {
        return COOLDOWNS.values().stream().map(cooldown -> cooldown[0]).collect(Collectors.toSet());
    }

    private static final int SKILL_MASTERY = 99;

    /**
     * Whether the player is seen short of what an item's source asks before it hands one
     * over: a skill cape's 99, a quest's reward. Such an item is an unlock to earn, not an
     * errand. False for anything a shop, a craft or a drop gives anyone.
     *
     * <p>The Achievement diary cape and the Quest point cape always answer true. Larry reads
     * neither every diary nor every quest, so a player without the cape is taken not to
     * have earned it.
     */
    public static boolean isBarredFromGetting(Capability capability, PlayerState state) {
        if (capability == Capability.DIARY_CAPE || capability == Capability.QUEST_CAPE) {
            return true;
        }
        if (state == null) {
            return false;
        }
        Skill skill = SKILL_CAPES.get(capability);
        if (skill != null) {
            return state.getSkillLevel(skill) < SKILL_MASTERY;
        }
        QuestGate gate = QUEST_GATES.get(capability);
        return gate != null && gate.bars(state);
    }

    private static final Map<Capability, Detector> RULES = rules();

    private static final Map<Capability, PatchDetector> PATCH_RULES = patchRules();

    /**
     * The capabilities that are farming patches, answered from the patch.
     *
     * <p>The spirit-tree rules treat an unseen patch as empty, unlike
     * {@code FarmingInputs.patchAt}'s "no reason to change the plan". A wrong no costs a
     * slower route until the player next walks past. {@link #weissHerbPatch} is the exception.
     */
    private static Map<Capability, PatchDetector> patchRules() {
        Map<Capability, PatchDetector> rules = new EnumMap<>(Capability.class);
        for (Tables.Row row : TABLE) {
            if (row.text(0).equals("spiritTree")) {
                Capability tree = row.requireWidth(4).constant(Capability.class, 1);
                rules.put(tree, grownSpiritTree(tree, row.integer(2), row.text(3)));
            }
        }
        rules.put(Capability.WEISS_HERB_PATCH, weissHerbPatch());
        return rules;
    }

    /** How a player who has not been seen with a house item can make it count. */
    private static final String TICK_IT = " or tick it under Your house";

    /**
     * One capability built in the player's house: what settles it from a sighting, the
     * levels and quests below which it cannot exist, and the words for each answer.
     *
     * <p>Either a piece of furniture, answered from the last scan of the house, or a Portal
     * Nexus destination, answered from the Nexus. See {@link #built} and {@link #attuned}.
     */
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class HouseRule {
        private final Floor floor;

        /** The furniture looked for, or null for a Portal Nexus destination. */
        private final HouseFeature feature;

        /** The Nexus destination's enum 1377 key; unused for furniture. */
        private final int destination;

        /** What the rule counts, as the "isn't counted until you …" answer names it: "a Weiss portal". */
        private final String subject;

        /** Why it counts once seen: "your house has a Weiss portal". */
        private final String seenBuilt;

        /** Why it does not once seen without it: "your house has no Weiss portal". */
        private final String seenAbsent;

        /** What the house as last seen says about this item. */
        HouseSighting sighting(HouseSight house) {
            if (feature != null) {
                if (house.has(feature)) {
                    return HouseSighting.BUILT;
                }
                return house.lookedFor(feature) ? HouseSighting.ABSENT : HouseSighting.UNSEEN;
            }
            if (house.isAttuned(destination)) {
                return HouseSighting.BUILT;
            }
            return nexusGone(house) || house.isNexusRead() ? HouseSighting.ABSENT : HouseSighting.UNSEEN;
        }

        /** Whether this is a Nexus destination in a house scanned without a Nexus. */
        private boolean nexusGone(HouseSight house) {
            return feature == null && house.isScanned() && !house.has(HouseFeature.PORTAL_NEXUS);
        }

        /**
         * The answer, in this order. Seen built is a yes, whatever the levels say. Then the
         * floor answers no when a level or quest rules the item out, since its reason names
         * what the player is missing. Then seen without it is a detected no, which no
         * declaration overrides. Then a declaration is a yes. A house never seen is a no
         * too, marked {@code INFERRED}: assuming absence costs at worst a slower route. The
         * "isn't counted until" answer names what would settle it: opening a Nexus that stands
         * but was never read, otherwise visiting the house.
         */
        CapabilityVerdict detect(PlayerState state, HouseSight house, boolean declared) {
            HouseSighting seen = sighting(house);
            if (seen == HouseSighting.BUILT) {
                return CapabilityVerdict.detected(seenBuilt);
            }
            CapabilityVerdict tooEarly = floor.detect(state);
            if (tooEarly.getState() == CapabilityState.LACK) {
                return tooEarly;
            }
            if (seen == HouseSighting.ABSENT) {
                return CapabilityVerdict.ruledOut(nexusGone(house) ? "your house has no Portal Nexus" : seenAbsent);
            }
            if (declared) {
                return CapabilityVerdict.have(Source.DECLARED, "you said " + seenBuilt);
            }
            String settle = feature == null && house.has(HouseFeature.PORTAL_NEXUS) ? "open the Nexus" : "visit your house";
            return CapabilityVerdict.lack(Source.INFERRED, subject + " isn't counted until you " + settle + TICK_IT);
        }
    }

    /** A piece of furniture, answered from the last scan of the house. */
    private static HouseRule built(HouseFeature feature, String what, Floor floor) {
        return new HouseRule(floor, feature, 0, what, "your house has " + what, "your house has no " + bare(what));
    }

    /**
     * A Portal Nexus destination, by its enum 1377 key.
     *
     * <p>A slot holding the key is a yes from any read. A full read of the Nexus without it
     * is a no, and so is a scanned house with no Nexus at all. A Nexus seen but never read
     * is not seen yet: the scan shows the Nexus stands, not what it is attuned to.
     */
    private static HouseRule attuned(int destination, String where, Floor floor) {
        return new HouseRule(floor, null, destination, where + " in your Portal Nexus",
                "your Portal Nexus is attuned to " + where, "your Portal Nexus isn't attuned to " + where);
    }

    /**
     * What the house as last seen says about one house capability, the same reading the
     * resolver answers from. {@link HouseSighting#UNSEEN} for anything not built in a house.
     */
    public static HouseSighting houseSighting(Capability capability, HouseSight house) {
        HouseRule rule = HOUSE_RULES.get(capability);
        return rule == null ? HouseSighting.UNSEEN : rule.sighting(house != null ? house : HouseSight.unseen());
    }

    /**
     * Whether a level or quest this account is seen short of rules out a house capability,
     * whatever is declared. False for anything not built in a house.
     */
    public static boolean isHouseItemOutOfReach(Capability capability, PlayerState state) {
        HouseRule rule = HOUSE_RULES.get(capability);
        return rule != null && state != null && rule.floor.detect(state).getState() == CapabilityState.LACK;
    }

    /**
     * The level a house capability's floor names as short: its Construction level, else its
     * Magic or Farming level. 0 when no level is short, though a quest may still be.
     */
    public static int houseItemLevelShort(Capability capability, PlayerState state) {
        HouseRule rule = HOUSE_RULES.get(capability);
        return rule == null || state == null ? 0 : rule.floor.levelShort(state);
    }

    /**
     * A spirit tree the player planted, read from the patch it stands in.
     *
     * <p>The quest and level answer no first. Above them the patch decides:
     * the patch-states table's spirit tree rows read raw 20 and 44 as the last of thirteen
     * stages, the fully grown, ridable state.
     */
    private static PatchDetector grownSpiritTree(Capability capability, int farmingLevel, String where) {
        Detector cannotExist = new Floor("a grown spirit tree in the " + where + " patch").skill(Skill.FARMING, farmingLevel)
                .quest("Tree Gnome Village");
        return (state, sight) -> {
            CapabilityVerdict tooEarly = cannotExist.detect(state);
            if (tooEarly.getState() == CapabilityState.LACK) {
                return tooEarly;
            }
            PatchPrediction seen = sight.at(FarmingCatalogue.patchFor(capability));
            if (seen == null) {
                return CapabilityVerdict.lack(Source.INFERRED, "check the " + where + " spirit tree patch");
            }
            if (seen.getCrop() == Crop.SPIRIT_TREE && seen.getState() == CropState.GROWING && seen.getStage() >= seen.getStages() - 1) {
                return CapabilityVerdict.detected("a grown spirit tree was seen in the " + where + " patch");
            }
            return CapabilityVerdict.ruledOut("the " + where + " spirit tree patch holds no grown tree");
        };
    }

    /**
     * The Weiss herb patch, which the fire of nourishment unlocks.
     *
     * <p>Seeing the patch at all settles it, since it only exists once the fire is lit.
     *
     * <p>Not seeing it settles nothing, unlike the spirit-tree rules. The patch is its own
     * only reason to visit Weiss, so reading it absent would shut the stop out and the patch
     * would never be seen.
     */
    private static PatchDetector weissHerbPatch() {
        Detector unlit = new Floor("a fire of nourishment at Weiss").quest("Making Friends with My Arm");
        return (state, sight) -> sight.at(FarmingCatalogue.patchFor(Capability.WEISS_HERB_PATCH)) != null
                        ? CapabilityVerdict.detected("the Weiss herb patch has been seen, so the fire is lit")
                        : unlit.detect(state);
    }

    /** The verdict for this capability, never null. Anything without a rule is UNKNOWN. */
    public static CapabilityVerdict resolve(Capability capability, PlayerState state) {
        return resolve(capability, state, PatchSight.blind());
    }

    /**
     * The same, for a caller that can say what is growing in the player's patches. Pass it
     * wherever available; see {@link PatchSight#blind()}.
     */
    public static CapabilityVerdict resolve(Capability capability, PlayerState state, PatchSight sight) {
        return resolve(capability, state, sight, HouseSight.unseen(), Set.of());
    }

    /**
     * The same, also knowing what the player's house was seen to hold (a null house is one
     * never seen) and which house items the player has declared built. A declaration counts
     * only for an item the house has not been seen to settle.
     *
     * @param houseDeclared the declared house capabilities, never null
     */
    public static CapabilityVerdict resolve(Capability capability, PlayerState state,
            PatchSight sight, HouseSight house, Set<Capability> houseDeclared) {
        CapabilityVerdict verdict = null;
        if (state != null) {
            HouseRule houseRule = HOUSE_RULES.get(capability);
            if (houseRule != null) {
                return houseRule.detect(state, house != null ? house : HouseSight.unseen(), houseDeclared.contains(capability));
            }
            PatchDetector patchRule = PATCH_RULES.get(capability);
            Detector detector = RULES.get(capability);
            if (patchRule != null) {
                verdict = patchRule.detect(state, sight != null ? sight : PatchSight.blind());
            } else if (detector != null) {
                verdict = detector.detect(state);
            }
        }
        return verdict != null ? verdict : CapabilityVerdict.unknown("the game doesn't show this");
    }

    /** True when this capability has a way of being answered from the game. */
    public static boolean isDetectable(Capability capability) {
        return RULES.containsKey(capability) || PATCH_RULES.containsKey(capability) || HOUSE_RULES.containsKey(capability);
    }

    private static Map<Capability, Detector> rules() {
        Map<Capability, Detector> rules = new EnumMap<>(Capability.class);

        // --- Diary reward items -------------------------------------------------
        // Read from the diary rather than the item, since the reward can sit in a bank
        // this plugin has not seen.
        for (Capability capability : Capability.values()) {
            if (capability.getTierFamily() != Capability.TierFamily.NONE) {
                rules.put(capability, diaryReward(capability));
            }
        }

        // Tablets and teleport spells are answered together by TeleportPlanner.
        for (Tables.Row row : TABLE) {
            Capability capability = row.constant(Capability.class, 1);
            Detector rule = tableRule(row, capability);
            if (rule != null && rules.put(capability, rule) != null) {
                throw row.error("a second rule for " + row.text(1));
            }
        }
        rules.put(Capability.KHAREDSTS_MEMOIRS, lancalliumsBook("Kharedst's memoirs", Capability.KHAREDSTS_MEMOIRS));
        rules.put(Capability.BOOK_OF_THE_DEAD, lancalliumsBook("a Book of the Dead", Capability.BOOK_OF_THE_DEAD));

        // --- Respawn points --------------------------------------------------------
        rules.put(Capability.RESPAWN_AVAILABLE_FALADOR, respawnPoint("Falador", "Sir Tiffy Cashien in Falador Park"));
        rules.put(Capability.RESPAWN_AVAILABLE_CAMELOT, respawnPoint("Camelot", "Merlin in Camelot Castle"));

        // --- Quest-gated networks -------------------------------------------------
        // The table's diary row answers whether the staff is needed.
        rules.put(Capability.FAIRY_RINGS, fairyRings(rules.get(Capability.STAFFLESS_FAIRY_RINGS)));
        // House items and planted trees are answered from the house and the patch; see
        // HOUSE_RULES and patchRules().
        return Collections.unmodifiableMap(rules);
    }

    /**
     * A diary tier, answered from its completion flag, with the reason for each answer. An
     * unread flag answers UNKNOWN.
     */
    private static Detector diaryTier(String area, String tier, String have, String lack, String unsure) {
        return state -> {
            switch (DiaryTiers.completed(area, tier, state)) {
                case HAVE:
                    return CapabilityVerdict.detected(have);
                case LACK:
                    return CapabilityVerdict.ruledOut(lack);
                default:
                    return CapabilityVerdict.unknown(unsure);
            }
        };
    }

    /**
     * One row of the rule table as its detector, or null for a row of facts. The row's
     * first cell names the kind, which fixes its width and what each cell holds.
     */
    private static Detector tableRule(Tables.Row row, Capability capability) {
        switch (row.text(0)) {
            case "carried":
                return carried(row.requireWidth(3).text(2), capability);
            case "items":
                return items(row.requireWidth(5).text(2), row.integers(3));
            case "questItem":
                if (!QUEST_GATES.containsKey(capability)) {
                    throw row.error("no gate row for " + row.text(1));
                }
                return questItems(capability, row.requireWidth(4).text(2), row.text(3));
            case "quest":
                return quest(row.requireWidth(4).text(2), row.text(3));
            case "progress":
                return questProgressAtLeast(row.requireWidth(5).text(2), row.integer(3), row.text(4));
            case "varbit":
                return varbitAtLeast(row.requireWidth(7).integer(2), row.integer(4), row.text(5), row.text(6));
            case "scroll":
                return scroll(carried(row.requireWidth(6).text(5), capability), capability, row.optionalText(4));
            case "spell":
                return spell(row.requireWidth(5).integer(2), row.optionalText(3), row.text(4));
            case "skill":
                return skill(row.requireWidth(5).constant(Skill.class, 2), row.integer(3), row.text(4));
            case "houseIn":
                return houseIn(row.requireWidth(4).text(2), row.integer(3));
            case "diary":
                if (DiaryTiers.varbitFor(row.requireWidth(7).text(2), row.text(3)) == null) {
                    throw row.error("no diary flag for " + row.text(2) + " " + row.text(3));
                }
                return diaryTier(row.text(2), row.text(3), row.text(4), row.text(5), row.text(6));
            case "open":
                String because = row.requireWidth(3).text(2);
                return state -> CapabilityVerdict.detected(because);
            case "gate":
            case "cooldown":
            case "house":
            case "nexus":
            case "cape":
            case "spiritTree":
                return null;
            default:
                throw row.error("no rule kind " + row.text(0));
        }
    }

    /**
     * The book a spell capability lives on, or null when it is not a spell.
     *
     * <p>Kept apart from the rules, which answer "has this player unlocked it". The resolver
     * applies this against the book being planned for, so a Lunar preview can offer Fertile
     * Soil to a player currently on Arceuus.
     */
    public static Spellbook spellbookFor(Capability capability) {
        switch (capability) {
            case LUMBRIDGE_HOME_TELEPORT:
                return Spellbook.STANDARD;
            case RESPAWN_TELEPORT:
                return Spellbook.ARCEUUS;
            case FERTILE_SOIL:
                return Spellbook.LUNAR;
            default:
                return null;
        }
    }

    /**
     * {@link #items}, with the ids read from the packing list so the two cannot disagree.
     *
     * @param capability the {@link CarryItems} row to take ids from
     */
    private static Detector carried(String description, Capability capability) {
        CarryItems.Carry carry = CarryItems.forCapability(capability);
        if (carry == null) {
            throw new IllegalStateException("no carry item for " + capability);
        }
        return items(description, carry.getItemIds());
    }

    /**
     * Kharedst's memoirs or the Book of the Dead, able to cast Lunch by the Lancalliums: the
     * book held, its Hosidius page added, and a charge left.
     *
     * <p>Both books read the same two varbits, since the game keeps the page and the charges
     * on the player (a destroyed book keeps its charges, per the wiki). The page is varbit
     * 6030 ({@code HOSIDIUS_PAGE}), not the quest's reward flag 6028: the quest's reward
     * flags can all be set while the book holds only two pages. Charges are varbit 6035 ({@code VEOS_MEMOIR_CHARGES}).
     */
    private static Detector lancalliumsBook(String description, Capability book) {
        Detector held = carried(description, book);
        // The shared page and charges also explain this book's rung failing for a player
        // holding the other book, whose rung fails on the same varbits.
        Detector eitherBook = items("Kharedst's memoirs or a Book of the Dead", LANCALLIUMS_BOOK_IDS);
        return state -> {
            CapabilityVerdict holding = held.detect(state);
            // "Your book" only for a player seen holding one; otherwise the holding answer
            // says what is known.
            if (eitherBook.detect(state).getState() != CapabilityState.HAVE) {
                return holding;
            }
            Integer page = state.getVarbits().get(LANCALLIUMS_PAGE_VARBIT);
            if (page != null && page == 0) {
                return CapabilityVerdict.ruledOut("your book is missing the Lunch by the Lancalliums page");
            }
            Integer charges = state.getVarbits().get(MEMOIRS_CHARGES_VARBIT);
            if (charges != null && charges == 0) {
                return CapabilityVerdict.ruledOut("your book has no charges. Recharge it at the Old Memorial");
            }
            return holding.getState() == CapabilityState.HAVE && (page == null || charges == null)
                    ? CapabilityVerdict.unknown("not sure whether your book has a charge") : holding;
        };
    }

    /**
     * Held anywhere the player can retrieve it from — bank and leprechaun included.
     *
     * <p>"Not found" is UNKNOWN until the bank has been seen, since an unseen bank reads as
     * empty.
     */
    private static Detector items(String description, int... itemIds) {
        return state -> {
            for (int itemId : itemIds) {
                if (ItemCounts.ownsAny(state, itemId)) {
                    return CapabilityVerdict.detected("you have " + description);
                }
            }
            if (state.getBankLastSeen() == null) {
                return CapabilityVerdict.unknown("not sure whether you have " + description + " until you open your bank");
            }
            return CapabilityVerdict.ruledOut("you have no " + bare(description));
        };
    }

    /**
     * The fairy ring network: Fairytale I finished and Fairytale II started as far as the
     * Fairy Godfather's permission (wiki "Fairy ring": "Partially completed Fairytale II -
     * Cure a Queen (up until you're 'granted permission' to use them)"). Every ring then
     * needs a dramen or lunar staff wielded unless the elite Lumbridge &amp; Draynor diary
     * is done.
     *
     * <p>A player known to have neither the diary nor a staff anywhere cannot use a ring,
     * so the answer is LACK and the planner drops every fairy-ring rung. Either one unread
     * keeps the quests' answer rather than shutting the rungs on a guess.
     */
    private static Detector fairyRings(Detector staffless) {
        Detector growingPains = quest("Fairytale I - Growing Pains", "the fairy ring network");
        // Varbit 2326 reaches 40 once the player has talked to the Godfather, the talk in
        // which he grants the permission. The wiki walkthrough also sends the
        // player to the Co-ordinator and Fairy Fixit before "You may now use the fairy
        // rings", but neither talk has a stage of its own, and the next stage (45) comes
        // only after riding the rings to the hideout, so 40 is the latest stage that can
        // gate them.
        Detector permission = questProgressAtLeast("Fairytale II - Cure a Queen", FAIRY_RING_PERMISSION_STAGE, "the fairy ring network");
        Detector staff = items("a dramen or lunar staff", Loadouts.DRAMEN_STAFF, Loadouts.LUNAR_STAFF);
        return state -> {
            CapabilityVerdict opened = allOf(growingPains.detect(state), permission.detect(state));
            if (opened.getState() != CapabilityState.HAVE) {
                return opened;
            }
            CapabilityVerdict noStaffNeeded = staffless.detect(state);
            if (noStaffNeeded.getState() == CapabilityState.HAVE) {
                return noStaffNeeded;
            }
            if (noStaffNeeded.getState() == CapabilityState.LACK && staff.detect(state).getState() == CapabilityState.LACK) {
                return CapabilityVerdict.ruledOut("you need a dramen or lunar staff to use fairy rings");
            }
            // A staff held, or an unread diary or unseen bank: keep the rungs. The packing
            // list still names the staff, so a player who has one is not sent off without it.
            return opened;
        };
    }

    /**
     * Every gate held. A known LACK outranks an unread gate, because one shut gate shuts
     * the whole. With none shut, the first unread gate gives the answer.
     */
    private static CapabilityVerdict allOf(CapabilityVerdict... gates) {
        for (CapabilityVerdict gate : gates) {
            if (gate.getState() == CapabilityState.LACK) {
                return gate;
            }
        }
        for (CapabilityVerdict gate : gates) {
            if (gate.getState() != CapabilityState.HAVE) {
                return gate;
            }
        }
        return gates[gates.length - 1];
    }

    /** Strips a leading article, so "a ring of dueling" reads "you have no ring of dueling". */
    private static String bare(String description) {
        for (String article : new String[]{"a ", "an ", "the "}) {
            if (description.regionMatches(true, 0, article, 0, article.length())) {
                return description.substring(article.length());
            }
        }
        return description;
    }

    /**
     * A carried item behind its {@link #QUEST_GATES} quest. Short of the quest the answer is a
     * certain no that names the quest. An unobserved quest falls through to the item check.
     *
     * @param unlocks what the quest gives, named as the player would think of it
     */
    private static Detector questItems(Capability capability, String unlocks, String description) {
        QuestGate gate = QUEST_GATES.get(capability);
        Detector held = carried(description, capability);
        return state -> {
            if (gate.bars(state)) {
                return CapabilityVerdict.lack(Source.INFERRED, gate.getQuest() + " gives you " + unlocks);
            }
            return held.detect(state);
        };
    }

    /**
     * Whether quest progress has got at least as far as a given point. Not compared by
     * ordinal, so reordering {@link QuestStatus} cannot change the answer.
     */
    private static boolean hasReached(QuestStatus status, QuestStatus wanted) {
        return wanted == QuestStatus.COMPLETED ? status == QuestStatus.COMPLETED : status != QuestStatus.NOT_STARTED;
    }

    private static Detector quest(String questName, String description) {
        return state -> {
            QuestStatus status = state.getQuests().get(questName);
            if (status == QuestStatus.COMPLETED) {
                return CapabilityVerdict.detected("you have finished " + questName);
            }
            if (status == null) {
                // Quest state arrives as it is observed, so silence is not a no.
                return CapabilityVerdict.unknown("not sure whether you have finished " + questName);
            }
            return CapabilityVerdict.ruledOut(questName + " unlocks " + description);
        };
    }

    /**
     * A place that opens partway through a quest, at a known value of its progress counter.
     * Reads the counter, not the status, since the status only says "started". The login
     * scan reads every non-zero counter.
     *
     * <p>A counter above zero but below the threshold answers UNKNOWN, not LACK: the place
     * may open at the next quest step.
     */
    private static Detector questProgressAtLeast(String questName, int opensAt, String unlocks) {
        return state -> {
            if (state.getQuests().get(questName) == QuestStatus.COMPLETED) {
                return CapabilityVerdict.detected("you have finished " + questName);
            }
            Integer progress = state.getQuestProgress().get(questName);
            if (progress == null) {
                // The login scan stores only non-zero counters, so an absent one means
                // untouched or unscanned. The status tells them apart when present.
                return state.getQuests().get(questName) == QuestStatus.NOT_STARTED ? CapabilityVerdict.ruledOut(
                                questName + " opens " + unlocks) : CapabilityVerdict.unknown(
                                "not sure how far you have got in " + questName);
            }
            if (progress >= opensAt) {
                return CapabilityVerdict.detected("you are far enough into " + questName);
            }
            return CapabilityVerdict.unknown("you have started " + questName + ", which opens " + unlocks + " partway through");
        };
    }

    /**
     * A spell, which needs a Magic level and sometimes a quest. An unobserved quest answers
     * UNKNOWN.
     */
    private static Detector spell(int magicLevel, String questName, String spellName) {
        return state -> {
            CapabilityVerdict barred = spellBarred(spellName, magicLevel, questName, state);
            return barred != null ? barred : CapabilityVerdict.detected("you can cast " + spellName);
        };
    }

    /**
     * Why a spell's Magic level or quest bars the player, or null when both are met. A null
     * quest asks for none. An unobserved quest answers UNKNOWN.
     */
    static CapabilityVerdict spellBarred(String spellName, int magicLevel, String questName, PlayerState state) {
        int magic = state.getSkillLevel(Skill.MAGIC);
        if (magic < magicLevel) {
            return CapabilityVerdict.ruledOut(spellName + " needs " + magicLevel + " Magic and you have " + magic);
        }
        if (questName == null) {
            return null;
        }
        QuestStatus status = state.getQuests().get(questName);
        if (status == null) {
            return CapabilityVerdict.unknown("not sure whether you have finished " + questName + ", which " + spellName + " needs");
        }
        return status == QuestStatus.COMPLETED ? null : CapabilityVerdict.ruledOut(spellName + " needs " + questName);
    }

    /**
     * A teleport scroll, owned loose or stored in an owned master scroll book, whose varbits
     * count the scrolls of each kind it holds. Behind its quest, when it has one.
     */
    private static Detector scroll(Detector loose, Capability capability, String quest) {
        Detector book = carried("a master scroll book", Capability.MASTER_SCROLL_BOOK);
        Detector unlocked = quest == null ? null : quest(quest, "this scroll");
        return state -> {
            CapabilityVerdict held = loose.detect(state);
            if (held.getState() != CapabilityState.HAVE) {
                if (storedInBook(capability, state) == 0 || book.detect(state).getState() != CapabilityState.HAVE) {
                    return held;
                }
                held = CapabilityVerdict.detected("your master scroll book holds one");
            }
            CapabilityVerdict gate = unlocked == null ? held : unlocked.detect(state);
            return gate.getState() == CapabilityState.HAVE ? held : gate;
        };
    }

    /**
     * A one-way varbit that has reached a given value. UNKNOWN while unread, since a varbit
     * enters the state map only once it is scanned or changes.
     */
    private static Detector varbitAtLeast(int varbitId, int value, String have, String lack) {
        return state -> {
            Integer current = state.getVarbits().get(varbitId);
            if (current == null) {
                return CapabilityVerdict.unknown("not sure whether " + have);
            }
            return current >= value ? CapabilityVerdict.detected(have) : CapabilityVerdict.ruledOut(lack);
        };
    }

    /**
     * A respawn town, unlocked as {@link #RESPAWN_UNLOCKS} answers for it, and selected at
     * {@code where}.
     *
     * <p>The selected respawn town answers outright when it has been read: a player respawns
     * in exactly one place, so every other town is a definite no. Otherwise an unlocked town
     * answers UNKNOWN, with a reason telling the player where to select it, and a locked one
     * gives the unlock's own reason.
     */
    private static Detector respawnPoint(String town, String where) {
        Detector unlocked = RESPAWN_UNLOCKS.get(town);
        return state -> {
            // The setting outranks the unlock, which only says they could choose it.
            String selected = selectedRespawnTown(state);
            if (selected != null) {
                return selected.equals(town) ? CapabilityVerdict.detected("your respawn point is " + town)
                        : CapabilityVerdict.ruledOut("your respawn point is " + selected + ", not " + town);
            }
            CapabilityVerdict verdict = unlocked.detect(state);
            return verdict.getState() == CapabilityState.HAVE ? CapabilityVerdict.unknown("set your respawn point to " + town
                    + " by talking to " + where + " to use this") : verdict;
        };
    }

    /**
     * The requirements below which a player cannot have built a thing. Set up once, through
     * its builder methods, while the rule tables are built.
     *
     * <p>A floor never answers yes or null: meeting every requirement makes a thing possible,
     * not actual. Falling short of one is LACK; otherwise the answer is UNKNOWN. An
     * unobserved quest is not a shortfall.
     */
    @RequiredArgsConstructor
    private static final class Floor implements Detector {
        private final String what;

        /**
         * Each skill's level, checked and named in the order the builder added them. Every
         * floor adds Construction first, so a Construction shortfall is the one named.
         */
        private final Map<Skill, Integer> levels = new LinkedHashMap<>();
        private String questName;

        Floor quest(String questName) {
            this.questName = questName;
            return this;
        }

        Floor skill(Skill skill, int level) {
            levels.put(skill, level);
            return this;
        }

        @Override
        public CapabilityVerdict detect(PlayerState state) {
            for (Map.Entry<Skill, Integer> needed : levels.entrySet()) {
                int level = state.getSkillLevel(needed.getKey());
                if (level < needed.getValue()) {
                    return CapabilityVerdict.lack(Source.INFERRED, what + " needs " + needed.getValue() + " "
                                    + needed.getKey().getDisplayName() + " and you have " + level);
                }
            }
            if (questName != null) {
                QuestStatus status = state.getQuests().get(questName);
                if (status != null && status != QuestStatus.COMPLETED) {
                    return CapabilityVerdict.lack(Source.INFERRED, what + " needs " + questName);
                }
            }
            return CapabilityVerdict.unknown("not sure whether you have " + what);
        }

        /** The first level {@link #detect} would name as short, or 0 for none. */
        int levelShort(PlayerState state) {
            for (Map.Entry<Skill, Integer> needed : levels.entrySet()) {
                if (state.getSkillLevel(needed.getKey()) < needed.getValue()) {
                    return needed.getValue();
                }
            }
            return 0;
        }
    }

    private static Detector skill(Skill skill, int level, String description) {
        return state -> {
            int current = state.getSkillLevel(skill);
            if (current >= level) {
                return CapabilityVerdict.detected("your " + skill.getDisplayName() + " is high enough");
            }
            return CapabilityVerdict.ruledOut(description + " needs " + level + " " + skill.getDisplayName()
                            + " and you have " + current);
        };
    }

    /**
     * A house in a named town. UNKNOWN only until the login scan reads the location varbit;
     * every reading after that is a yes or a no.
     */
    private static Detector houseIn(String town, int value) {
        return state -> {
            Boolean here = HouseLocation.isHouseIn(value, state.getVarbits());
            if (here == null) {
                return CapabilityVerdict.unknown("not sure where your house is");
            }
            if (Boolean.FALSE.equals(HouseLocation.ownsHouse(state.getVarbits()))) {
                return CapabilityVerdict.ruledOut("you do not own a house");
            }
            return here ? CapabilityVerdict.detected("your house is in " + town) : CapabilityVerdict.ruledOut(
                            "your house is not in " + town);
        };
    }

    /**
     * A diary reward, answered by whether its diary tier is finished. A higher tier replaces
     * the reward, so the best completed tier is the one the player holds.
     */
    private static Detector diaryReward(Capability capability) {
        return state -> {
            Capability earned = DiaryRewards.bestEarnedTier(capability.getTierFamily(), state.getVarbits());
            if (earned == null) {
                // A lower tier the routes cannot use, such as Explorer's ring 1, is held but no help.
                return CapabilityVerdict.ruledOut("this needs " + capability.getDisplayName() + " or better, from "
                        + DiaryRewards.earnedBy(capability));
            }
            if (capability.isSatisfiedBy(earned)) {
                String because = earned == capability ? "you have " + capability.getDisplayName()
                        : "you have " + earned.getDisplayName() + ", which does the same";
                return CapabilityVerdict.have(Source.INFERRED, because);
            }
            return CapabilityVerdict.ruledOut("you have " + earned.getDisplayName() + ", not " + capability.getDisplayName());
        };
    }
}
