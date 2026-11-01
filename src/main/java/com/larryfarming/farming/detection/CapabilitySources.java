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
import net.runelite.api.gameval.ItemID;
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
     * (Lunar Diplomacy for Lunar, Sins of the Father for Arceuus), so a caller gating a spell
     * on the book needs no separate quest check.
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
     */
    private static final int RESPAWN_FALADOR_VARBIT = VarbitID.FALADOR_SPAWN;
    private static final int RESPAWN_EDGEVILLE_VARBIT = VarbitID.EDGEVILLE_SPAWN;
    private static final int RESPAWN_CAMELOT_VARBIT = VarbitID.CAMELOT_SPAWN;
    private static final int RESPAWN_FEROX_VARBIT = VarbitID.WILDERNESS_SPAWN;
    private static final int RESPAWN_CIVITAS_VARBIT = VarbitID.CIVITAS_SPAWN;
    private static final int RESPAWN_KOUREND_VARBIT = VarbitID.KOUREND_SPAWN;

    private static final int[] RESPAWN_VARBIT_IDS = {
        RESPAWN_FALADOR_VARBIT, RESPAWN_EDGEVILLE_VARBIT, RESPAWN_CAMELOT_VARBIT,
        RESPAWN_FEROX_VARBIT, RESPAWN_CIVITAS_VARBIT, RESPAWN_KOUREND_VARBIT,
    };

    /** What opens each respawn town, separate from whether it is the chosen one. */
    private static final Map<String, Detector> RESPAWN_UNLOCKS = respawnUnlocks();

    private static Map<String, Detector> respawnUnlocks() {
        Map<String, Detector> unlocks = new LinkedHashMap<>();
        unlocks.put("Falador", quest("Recruitment Drive", "respawning at Falador"));
        unlocks.put("Camelot", state -> {
            Integer done = state.getVarbits().get(KNIGHT_WAVES_VARBIT);
            if (done != null) {
                return done >= KNIGHT_WAVES_DONE ? CapabilityVerdict.have(Source.DETECTED,
                                "you have finished the Knight Waves Training Grounds") : CapabilityVerdict.lack(Source.DETECTED,
                                "finish the Knight Waves Training Grounds to respawn at " + "Camelot");
            }
            return quest("King's Ransom", "respawning at Camelot").detect(state);
        });
        return Collections.unmodifiableMap(unlocks);
    }

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
        for (int varbit : RESPAWN_VARBIT_IDS) {
            Integer value = state.getVarbits().get(varbit);
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
            chosen = townFor(varbit);
        }
        if (chosen != null) {
            return chosen;
        }
        return scanned ? "Lumbridge" : null;
    }

    private static String townFor(int varbit) {
        switch (varbit) {
            case RESPAWN_FALADOR_VARBIT:
                return "Falador";
            case RESPAWN_EDGEVILLE_VARBIT:
                return "Edgeville";
            case RESPAWN_CAMELOT_VARBIT:
                return "Camelot";
            case RESPAWN_FEROX_VARBIT:
                return "Ferox Enclave";
            case RESPAWN_CIVITAS_VARBIT:
                return "Civitas illa Fortis";
            default:
                return "Kourend Castle";
        }
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
     * Counters of rationed teleports spent today, scanned at login because a day's allowance
     * can be part spent before the plugin starts.
     *
     * <p>Must stay declared above {@link #LOGIN_SCAN_VARBIT_IDS}, whose initialiser reads it
     * (static fields initialise in source order).
     */
    private static final int[] DAILY_USE_VARBIT_IDS = DiaryRewards.usesSpentVarbitIds();

    /**
     * The simple rules, one row each, and two tables of facts: the quest gating an item only
     * that quest hands out ({@code gate}), and the skill each Cape of Accomplishment needs at
     * 99 ({@code cape}). The first cell names the kind, which fixes the columns after it.
     *
     * <p>Declared above {@link #LOGIN_SCAN_VARBIT_IDS}, which scans the flags its varbit and
     * balloon rows read.
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
        // bare-handed planting lesson.
        List<Integer> flags = new ArrayList<>();
        for (Tables.Row row : TABLE) {
            if (row.text(0).equals("varbit") || row.text(0).equals("balloon")) {
                flags.add(row.integer(2));
            }
        }
        int[] own = {
            HouseLocation.HOUSE_LOCATION_VARBIT,
            SPELLBOOK_VARBIT,
            KNIGHT_WAVES_VARBIT,
            // A contract assigned before login never announces itself.
            CONTRACT_PRODUCE_VARBIT,
            CONTRACT_COMPLETE_VARBIT,
            RESPAWN_FALADOR_VARBIT,
            RESPAWN_EDGEVILLE_VARBIT,
            RESPAWN_CAMELOT_VARBIT,
            RESPAWN_FEROX_VARBIT,
            RESPAWN_CIVITAS_VARBIT,
            RESPAWN_KOUREND_VARBIT,
            LANCALLIUMS_PAGE_VARBIT,
            MEMOIRS_CHARGES_VARBIT,
        };
        int[] diaries = DiaryTiers.allVarbits();
        int[] all = new int[own.length + diaries.length + DAILY_USE_VARBIT_IDS.length + flags.size()];
        System.arraycopy(own, 0, all, 0, own.length);
        System.arraycopy(diaries, 0, all, own.length, diaries.length);
        System.arraycopy(DAILY_USE_VARBIT_IDS, 0, all, own.length + diaries.length, DAILY_USE_VARBIT_IDS.length);
        for (int i = 0; i < flags.size(); i++) {
            all[all.length - flags.size() + i] = flags.get(i);
        }
        return all;
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

    static {
        for (Tables.Row row : TABLE) {
            if (row.text(0).equals("gate")) {
                QUEST_GATES.put(row.requireWidth(4).constant(Capability.class, 1),
                        new QuestGate(row.text(2), row.constant(QuestStatus.class, 3)));
            } else if (row.text(0).equals("cape")) {
                SKILL_CAPES.put(row.requireWidth(3).constant(Capability.class, 1), row.constant(Skill.class, 2));
            }
        }
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

    private static final Map<Capability, HouseRule> HOUSE_RULES = houseRules();

    /**
     * The capabilities that are farming patches, answered from the patch.
     *
     * <p>The spirit-tree rules treat an unseen patch as empty, unlike
     * {@code FarmingInputs.patchAt}'s "no reason to change the plan". A wrong no costs a
     * slower route until the player next walks past. {@link #weissHerbPatch} is the exception.
     */
    private static Map<Capability, PatchDetector> patchRules() {
        Map<Capability, PatchDetector> rules = new EnumMap<>(Capability.class);
        rules.put(Capability.SPIRIT_TREE_GROWN_BRIMHAVEN, grownSpiritTree(Capability.SPIRIT_TREE_GROWN_BRIMHAVEN, 83, "Brimhaven"));
        // 85 rather than 83: the patch is in the guild's north wing.
        rules.put(Capability.SPIRIT_TREE_GROWN_FARMING_GUILD, grownSpiritTree(Capability.SPIRIT_TREE_GROWN_FARMING_GUILD, 85,
                        "Farming Guild"));
        rules.put(Capability.SPIRIT_TREE_GROWN_PORT_SARIM, grownSpiritTree(Capability.SPIRIT_TREE_GROWN_PORT_SARIM, 83, "Port Sarim"));
        rules.put(Capability.WEISS_HERB_PATCH, weissHerbPatch());
        return rules;
    }

    /**
     * The capabilities built in the player's house, answered from what was seen there.
     *
     * <p>Levels are base levels. The Nexus refuses a boosted Magic level, but the portal
     * chamber accepts one (wiki), so a chamber portal's floor names no Magic level.
     *
     * @see HouseRule#detect for the order the answers are given in
     */
    private static Map<Capability, HouseRule> houseRules() {
        Map<Capability, HouseRule> rules = new EnumMap<>(Capability.class);
        // Chamber portals all need 50 Construction. The Troll Stronghold portal is directed
        // with salts and basalt, so it asks for no Magic level.
        rules.put(Capability.POH_PORTAL_WEISS, portal(HouseFeature.PORTAL_WEISS, "a Weiss portal", "Making Friends with My Arm"));
        rules.put(Capability.POH_PORTAL_TROLL_STRONGHOLD, portal(HouseFeature.PORTAL_TROLL_STRONGHOLD, "a Troll Stronghold portal",
                        "Making Friends with My Arm"));
        rules.put(Capability.POH_PORTAL_KOUREND, portal(HouseFeature.PORTAL_KOUREND, "a Kourend portal", "Client of Kourend"));
        rules.put(Capability.POH_PORTAL_FENKENSTRAIN, portal(HouseFeature.PORTAL_FENKENSTRAIN,
                "a Fenkenstrain's Castle portal", "Priest in Peril"));
        rules.put(Capability.POH_PORTAL_HARMONY, portal(HouseFeature.PORTAL_HARMONY, "a Harmony Island portal", "The Great Brain Robbery"));
        // The Nexus room needs 72 Construction, and the Nexus asks for the teleport's own
        // Magic level on top.
        rules.put(Capability.POH_NEXUS_WEISS, nexus(PortalNexus.WEISS, "Weiss", "Making Friends with My Arm", 66));
        rules.put(Capability.POH_NEXUS_TROLL_STRONGHOLD, nexus(PortalNexus.TROLL_STRONGHOLD,
                "the Troll Stronghold", "Making Friends with My Arm", 66));
        rules.put(Capability.POH_NEXUS_HARMONY, nexus(PortalNexus.HARMONY_ISLAND, "Harmony Island", "The Great Brain Robbery", 65));
        rules.put(Capability.POH_SPIRIT_TREE, built(HouseFeature.SPIRIT_TREE, "a spirit tree", floor("a spirit tree in your house")
                        .construction(75).skill(Skill.FARMING, 83).build()));
        rules.put(Capability.POH_FAIRY_RING, built(HouseFeature.FAIRY_RING, "a fairy ring", floor("a fairy ring in your house")
                        .construction(85).build()));
        return Collections.unmodifiableMap(rules);
    }

    private static HouseRule portal(HouseFeature feature, String what, String quest) {
        return built(feature, what, floor(what).construction(50).quest(quest).build());
    }

    private static HouseRule nexus(int destination, String where, String quest, int magic) {
        return attuned(destination, where, floor(where + " in your Portal Nexus")
                .construction(72).quest(quest).skill(Skill.MAGIC, magic).build());
    }

    /** How a player who has not been seen with a house item can make it count. */
    private static final String TICK_IT = " or tick it under Your house";

    /**
     * One capability built in the player's house: what settles it from a sighting, the
     * levels and quests below which it cannot exist, and the words for each answer.
     */
    private abstract static class HouseRule {
        private final Floor floor;

        HouseRule(Floor floor) {
            this.floor = floor;
        }

        /** What the house as last seen says about this item. */
        abstract HouseSighting sighting(HouseSight house);

        /** Why it counts once seen: "your house has a Weiss portal". */
        abstract String seenBuilt();

        /** Why it does not once seen without it: "your house has no Weiss portal". */
        abstract String seenAbsent(HouseSight house);

        /** Why it counts on the player's word alone. */
        abstract String declared();

        /** Why it does not before it is seen or declared, naming what would settle it. */
        abstract String unseen(HouseSight house);

        /**
         * The answer, in this order. Seen built is a yes, whatever the levels say. Then the
         * floor answers no when a level or quest rules the item out, since its reason names
         * what the player is missing. Then seen without it is a detected no, which no
         * declaration overrides. Then a declaration is a yes. A house never seen is a no
         * too, marked {@code INFERRED}: assuming absence costs at worst a slower route.
         */
        CapabilityVerdict detect(PlayerState state, HouseSight house, boolean declared) {
            HouseSighting seen = sighting(house);
            if (seen == HouseSighting.BUILT) {
                return CapabilityVerdict.have(Source.DETECTED, seenBuilt());
            }
            CapabilityVerdict tooEarly = floor.detect(state);
            if (tooEarly.getState() == CapabilityState.LACK) {
                return tooEarly;
            }
            if (seen == HouseSighting.ABSENT) {
                return CapabilityVerdict.lack(Source.DETECTED, seenAbsent(house));
            }
            if (declared) {
                return CapabilityVerdict.have(Source.DECLARED, declared());
            }
            return CapabilityVerdict.lack(Source.INFERRED, unseen(house));
        }
    }

    /** A piece of furniture, answered from the last scan of the house. */
    private static HouseRule built(HouseFeature feature, String what, Floor floor) {
        return new HouseRule(floor) {
            @Override
            HouseSighting sighting(HouseSight house) {
                if (house.has(feature)) {
                    return HouseSighting.BUILT;
                }
                return house.isScanned() ? HouseSighting.ABSENT : HouseSighting.UNSEEN;
            }

            @Override
            String seenBuilt() {
                return "your house has " + what;
            }

            @Override
            String seenAbsent(HouseSight house) {
                return "your house has no " + bare(what);
            }

            @Override
            String declared() {
                return "you said your house has " + what;
            }

            @Override
            String unseen(HouseSight house) {
                return what + " isn't counted until you visit your house" + TICK_IT;
            }
        };
    }

    /**
     * A Portal Nexus destination, by its enum 1377 key.
     *
     * <p>A slot holding the key is a yes from any read. A full read of the Nexus without it
     * is a no, and so is a scanned house with no Nexus at all. A Nexus seen but never read
     * is not seen yet: the scan shows the Nexus stands, not what it is attuned to.
     */
    private static HouseRule attuned(int destination, String where, Floor floor) {
        return new HouseRule(floor) {
            @Override
            HouseSighting sighting(HouseSight house) {
                if (house.isAttuned(destination)) {
                    return HouseSighting.BUILT;
                }
                if (house.isScanned() && !house.has(HouseFeature.PORTAL_NEXUS) || house.isNexusRead()) {
                    return HouseSighting.ABSENT;
                }
                return HouseSighting.UNSEEN;
            }

            @Override
            String seenBuilt() {
                return "your Portal Nexus is attuned to " + where;
            }

            @Override
            String seenAbsent(HouseSight house) {
                return house.isScanned() && !house.has(HouseFeature.PORTAL_NEXUS) ? "your house has no Portal Nexus"
                        : "your Portal Nexus isn't attuned to " + where;
            }

            @Override
            String declared() {
                return "you said your Portal Nexus is attuned to " + where;
            }

            @Override
            String unseen(HouseSight house) {
                return house.has(HouseFeature.PORTAL_NEXUS) ? where + " in your Portal Nexus isn't counted until you open the Nexus"
                                + TICK_IT : where + " in your Portal Nexus isn't counted until you visit your house" + TICK_IT;
            }
        };
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
        Detector cannotExist = floor("a grown spirit tree in the " + where + " patch").skill(Skill.FARMING, farmingLevel)
                .quest("Tree Gnome Village").build();
        return (state, sight) -> {
            CapabilityVerdict tooEarly = cannotExist.detect(state);
            if (tooEarly != null && tooEarly.getState() == CapabilityState.LACK) {
                return tooEarly;
            }
            PatchPrediction seen = sight.at(FarmingCatalogue.patchFor(capability));
            if (seen == null) {
                return CapabilityVerdict.lack(Source.INFERRED, "check the " + where + " spirit tree patch");
            }
            if (seen.getCrop() == Crop.SPIRIT_TREE && seen.getState() == CropState.GROWING && seen.getStage() >= seen.getStages() - 1) {
                return CapabilityVerdict.have(Source.DETECTED, "a grown spirit tree was seen in the " + where + " patch");
            }
            return CapabilityVerdict.lack(Source.DETECTED, "the " + where + " spirit tree patch holds no grown tree");
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
        Detector unlit = floor("a fire of nourishment at Weiss").quest("Making Friends with My Arm").build();
        return (state, sight) -> sight.at(FarmingCatalogue.patchFor(Capability.WEISS_HERB_PATCH)) != null
                        ? CapabilityVerdict.have(Source.DETECTED, "the Weiss herb patch has been seen, so the fire is lit")
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
            Detector rule = tableRule(row, row.constant(Capability.class, 1));
            if (rule != null && rules.put(row.constant(Capability.class, 1), rule) != null) {
                throw row.error("a second rule for " + row.text(1));
            }
        }
        rules.put(Capability.KHAREDSTS_MEMOIRS, lancalliumsBook("Kharedst's memoirs", Capability.KHAREDSTS_MEMOIRS));
        rules.put(Capability.BOOK_OF_THE_DEAD, lancalliumsBook("a Book of the Dead", Capability.BOOK_OF_THE_DEAD));

        // --- Respawn points --------------------------------------------------------
        // Falador's unlock is Recruitment Drive alone, so it needs no unlock varbit.
        rules.put(Capability.RESPAWN_AVAILABLE_FALADOR, respawnPoint("Recruitment Drive", "Falador",
                        "Sir Tiffy Cashien in Falador Park", 0, 0));
        rules.put(Capability.RESPAWN_AVAILABLE_CAMELOT, respawnPoint("King's Ransom", "Camelot", "the Knight Waves Training Grounds",
                        KNIGHT_WAVES_VARBIT, KNIGHT_WAVES_DONE));

        // --- Quest-gated networks -------------------------------------------------
        // The elite Lumbridge & Draynor diary lifts the staff requirement (wiki "Fairy
        // ring"). No quest does. An unread diary flag stays UNKNOWN, so the bag keeps
        // packing the staff.
        Detector staffless = state -> {
            switch (DiaryTiers.completed("Lumbridge", DiaryTiers.ELITE, state)) {
                case HAVE:
                    return CapabilityVerdict.have(Source.DETECTED, "your elite Lumbridge & Draynor diary lets you use fairy rings "
                                    + "without a staff");
                case LACK:
                    return CapabilityVerdict.lack(Source.DETECTED, "wield a dramen or lunar staff to use fairy rings");
                default:
                    return CapabilityVerdict.unknown("not sure whether you have the elite Lumbridge & Draynor diary");
            }
        };
        rules.put(Capability.STAFFLESS_FAIRY_RINGS, staffless);
        // Varbit 4480. Varbit 4585 holds the player's default landing, which the house
        // portal follows; the spell and the tablet offer the Exchange whatever it holds.
        rules.put(Capability.VARROCK_DIARY_MEDIUM, state -> {
            switch (DiaryTiers.completed("Varrock", DiaryTiers.MEDIUM, state)) {
                case HAVE:
                    return CapabilityVerdict.have(Source.DETECTED, "your medium Varrock diary lets the Varrock teleport land at the "
                                    + "Grand Exchange");
                case LACK:
                    return CapabilityVerdict.lack(Source.DETECTED, "the Varrock teleport lands at the Grand Exchange only after the "
                                    + "medium Varrock diary");
                default:
                    return CapabilityVerdict.unknown("not sure whether you have the medium Varrock diary");
            }
        });
        rules.put(Capability.FAIRY_RINGS, fairyRings(staffless));
        // House items and planted trees are answered from the house and the patch; see
        // houseRules() and patchRules().
        return Collections.unmodifiableMap(rules);
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
            case "balloon":
                return balloonStation(row.requireWidth(5).integer(2), row.text(4));
            case "spell":
                String quest = row.requireWidth(5).text(3);
                return spell(row.integer(2), quest.isEmpty() ? null : quest, row.text(4));
            case "skill":
                return skill(row.requireWidth(5).constant(Skill.class, 2), row.integer(3), row.text(4));
            case "houseIn":
                return houseIn(capability, row.requireWidth(3).text(2));
            case "open":
                String because = row.requireWidth(3).text(2);
                return state -> CapabilityVerdict.have(Source.DETECTED, because);
            case "gate":
            case "cape":
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
        return items(description, idsFor(capability));
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
                return CapabilityVerdict.lack(Source.DETECTED, "your book is missing the Lunch by the Lancalliums page");
            }
            Integer charges = state.getVarbits().get(MEMOIRS_CHARGES_VARBIT);
            if (charges != null && charges == 0) {
                return CapabilityVerdict.lack(Source.DETECTED, "your book has no charges. Recharge it at the Old Memorial");
            }
            if (holding.getState() != CapabilityState.HAVE) {
                return holding;
            }
            if (page == null || charges == null) {
                return CapabilityVerdict.unknown("not sure whether your book has a charge");
            }
            return holding;
        };
    }

    private static int[] idsFor(Capability capability) {
        CarryItems.Carry carry = CarryItems.forCapability(capability);
        if (carry == null) {
            throw new IllegalStateException("no carry item for " + capability);
        }
        return carry.getItemIds();
    }

    /**
     * Held anywhere the player can retrieve it from — bank and leprechaun included.
     *
     * <p>"Not found" is UNKNOWN until the bank has been seen, since an unseen bank reads as
     * empty. {@link com.larryfarming.farming.engine.LoadoutPlanner} reads an unseen bank the same way.
     */
    private static Detector items(String description, int... itemIds) {
        return state -> {
            for (int itemId : itemIds) {
                if (ItemCounts.ownsAny(state, itemId)) {
                    return CapabilityVerdict.have(Source.DETECTED, "you have " + description);
                }
            }
            if (state.getBankLastSeen() == null) {
                return CapabilityVerdict.unknown("not sure whether you have " + description + " until you open your bank");
            }
            return CapabilityVerdict.lack(Source.DETECTED, "you have no " + bare(description));
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
            CapabilityVerdict held = staff.detect(state);
            if (held.getState() == CapabilityState.HAVE) {
                return opened;
            }
            if (noStaffNeeded.getState() == CapabilityState.LACK && held.getState() == CapabilityState.LACK) {
                return CapabilityVerdict.lack(Source.DETECTED, "you need a dramen or lunar staff to use fairy rings");
            }
            // Unread diary or unseen bank: keep the rungs. The packing list still names the
            // staff, so a player who has one is not sent off without it.
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
        if (wanted == QuestStatus.COMPLETED) {
            return status == QuestStatus.COMPLETED;
        }
        return status != QuestStatus.NOT_STARTED;
    }

    private static Detector quest(String questName, String description) {
        return state -> {
            QuestStatus status = state.getQuests().get(questName);
            if (status == QuestStatus.COMPLETED) {
                return CapabilityVerdict.have(Source.DETECTED, "you have finished " + questName);
            }
            if (status == null) {
                // Quest state arrives as it is observed, so silence is not a no.
                return CapabilityVerdict.unknown("not sure whether you have finished " + questName);
            }
            return CapabilityVerdict.lack(Source.DETECTED, questName + " unlocks " + description);
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
                return CapabilityVerdict.have(Source.DETECTED, "you have finished " + questName);
            }
            Integer progress = state.getQuestProgress().get(questName);
            if (progress == null) {
                // The login scan stores only non-zero counters, so an absent one means
                // untouched or unscanned. The status tells them apart when present.
                return state.getQuests().get(questName) == QuestStatus.NOT_STARTED ? CapabilityVerdict.lack(Source.DETECTED,
                                questName + " opens " + unlocks) : CapabilityVerdict.unknown(
                                "not sure how far you have got in " + questName);
            }
            if (progress >= opensAt) {
                return CapabilityVerdict.have(Source.DETECTED, "you are far enough into " + questName);
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
            int magic = state.getSkillLevel(Skill.MAGIC);
            if (magic < magicLevel) {
                return CapabilityVerdict.lack(Source.DETECTED, spellName + " needs " + magicLevel + " Magic and you have " + magic);
            }
            QuestStatus status = questName == null ? null : state.getQuests().get(questName);
            if (questName == null || status == QuestStatus.COMPLETED) {
                return CapabilityVerdict.have(Source.DETECTED, "you can cast " + spellName);
            }
            if (status == null) {
                return CapabilityVerdict.unknown("not sure whether you have finished " + questName + ", which " + spellName + " needs");
            }
            return CapabilityVerdict.lack(Source.DETECTED, spellName + " needs " + questName);
        };
    }

    /**
     * One balloon station, flagged once the player has flown there. The flag already
     * implies Enlightened Journey and its Firemaking level.
     */
    private static Detector balloonStation(int varbitId, String where) {
        return varbitAtLeast(varbitId, 1, "you have unlocked the balloon station at " + where,
                "you have not unlocked the balloon station at " + where);
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
            return current >= value ? CapabilityVerdict.have(Source.DETECTED, have) : CapabilityVerdict.lack(Source.DETECTED, lack);
        };
    }

    /**
     * The answer read off the player's respawn setting, or null when it has not been read.
     * A player respawns in exactly one place, so every other town is a definite no.
     */
    private static CapabilityVerdict fromSelection(String town, PlayerState state) {
        String selected = selectedRespawnTown(state);
        if (selected == null) {
            return null;
        }
        return selected.equals(town) ? CapabilityVerdict.have(Source.DETECTED, "your respawn point is " + town)
                : CapabilityVerdict.lack(Source.DETECTED, "your respawn point is " + selected + ", not " + town);
    }

    /**
     * A respawn town, unlocked by a quest and, where {@code unlockVarbit} is non-zero, a flag
     * read ahead of the quest.
     *
     * <p>The selected respawn town answers outright when it has been read. Otherwise an
     * unlocked town answers UNKNOWN, with a reason telling the player where to select it.
     */
    private static Detector respawnPoint(String questName, String town, String where, int unlockVarbit, int unlockValue) {
        Detector questGate = quest(questName, "respawning at " + town);
        return state -> {
            String selectIt = "set your respawn point to " + town + " at " + where + " to use this";
            // The setting outranks the unlock, which only says they could choose it.
            CapabilityVerdict settled = fromSelection(town, state);
            if (settled != null) {
                return settled;
            }
            Integer unlock = unlockVarbit != 0 ? state.getVarbits().get(unlockVarbit) : null;
            if (unlock != null) {
                return unlock >= unlockValue ? CapabilityVerdict.unknown(selectIt) : CapabilityVerdict.lack(Source.DETECTED,
                                where + " unlocks respawning at " + town);
            }
            CapabilityVerdict verdict = questGate.detect(state);
            return verdict.getState() == CapabilityState.HAVE ? CapabilityVerdict.unknown(selectIt) : verdict;
        };
    }

    private static Floor floor(String what) {
        return new Floor(what);
    }

    /**
     * The requirements below which a player cannot have built a thing. Set up once, through
     * its builder methods, while the rule tables are built.
     *
     * <p>A floor never answers yes or null: meeting every requirement makes a thing possible,
     * not actual. Falling short of one is LACK; otherwise the answer is UNKNOWN. An
     * unobserved quest is not a shortfall.
     */
    private static final class Floor implements Detector {
        private final String what;
        private int construction;
        private String questName;
        private Skill skill;
        private int skillLevel;

        Floor(String what) {
            this.what = what;
        }

        Floor construction(int level) {
            this.construction = level;
            return this;
        }

        Floor quest(String questName) {
            this.questName = questName;
            return this;
        }

        Floor skill(Skill skill, int level) {
            this.skill = skill;
            this.skillLevel = level;
            return this;
        }

        Floor build() {
            return this;
        }

        @Override
        public CapabilityVerdict detect(PlayerState state) {
            if (construction > 0) {
                int level = state.getSkillLevel(Skill.CONSTRUCTION);
                if (level < construction) {
                    return CapabilityVerdict.lack(Source.INFERRED, what + " needs " + construction + " Construction and you have " + level);
                }
            }
            if (skill != null) {
                int level = state.getSkillLevel(skill);
                if (level < skillLevel) {
                    return CapabilityVerdict.lack(Source.INFERRED, what + " needs " + skillLevel + " "
                                    + skill.getDisplayName() + " and you have " + level);
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
            if (construction > 0 && state.getSkillLevel(Skill.CONSTRUCTION) < construction) {
                return construction;
            }
            if (skill != null && state.getSkillLevel(skill) < skillLevel) {
                return skillLevel;
            }
            return 0;
        }
    }

    private static Detector skill(Skill skill, int level, String description) {
        return state -> {
            int current = state.getSkillLevel(skill);
            if (current >= level) {
                return CapabilityVerdict.have(Source.DETECTED, "your " + skill.getDisplayName() + " is high enough");
            }
            return CapabilityVerdict.lack(Source.DETECTED, description + " needs " + level + " " + skill.getDisplayName()
                            + " and you have " + current);
        };
    }

    /**
     * A house in a named town. UNKNOWN only until the login scan reads the location varbit;
     * every reading after that is a yes or a no.
     */
    private static Detector houseIn(Capability location, String town) {
        return state -> {
            Boolean here = HouseLocation.isHouseIn(location, state.getVarbits());
            if (here == null) {
                return CapabilityVerdict.unknown("not sure where your house is");
            }
            if (Boolean.FALSE.equals(HouseLocation.ownsHouse(state.getVarbits()))) {
                return CapabilityVerdict.lack(Source.DETECTED, "you do not own a house");
            }
            return here ? CapabilityVerdict.have(Source.DETECTED, "your house is in " + town) : CapabilityVerdict.lack(Source.DETECTED,
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
                return CapabilityVerdict.lack(Source.DETECTED, "you have not earned " + capability.getDisplayName() + " yet");
            }
            if (capability.isSatisfiedBy(earned)) {
                String because = earned == capability ? "you have " + capability.getDisplayName()
                        : "you have " + earned.getDisplayName() + ", which does the same";
                return CapabilityVerdict.have(Source.INFERRED, because);
            }
            return CapabilityVerdict.lack(Source.DETECTED, "you have " + earned.getDisplayName() + ", not " + capability.getDisplayName());
        };
    }

    /**
     * Every capability whose daily teleport allowance the game itself counts, keyed by the
     * counting varbit. Used for live varbit changes and to prime a fresh login. A view of
     * {@link DiaryRewards}, which owns the varbit facts.
     */
    public static Map<Integer, Capability> dailyUsesCapabilitiesByVarbit() {
        Map<Integer, Capability> byVarbit = new LinkedHashMap<>();
        for (Capability capability : Capability.values()) {
            if (DiaryRewards.reportsUsesSpent(capability)) {
                byVarbit.put(DiaryRewards.usesSpentVarbit(capability), capability);
            }
        }
        return Collections.unmodifiableMap(byVarbit);
    }
}
