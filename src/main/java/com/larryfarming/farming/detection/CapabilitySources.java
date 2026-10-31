package com.larryfarming.farming.detection;

import com.larryfarming.engine.ItemCounts;
import com.larryfarming.engine.farming.Crop;
import com.larryfarming.engine.farming.CropState;
import com.larryfarming.engine.farming.PatchPrediction;
import com.larryfarming.farming.catalogue.CarryItems;
import com.larryfarming.farming.catalogue.FarmingCatalogue;
import com.larryfarming.farming.catalogue.Loadouts;
import com.larryfarming.farming.model.Capability;
import com.larryfarming.farming.model.Spellbook;
import com.larryfarming.model.PlayerState;
import com.larryfarming.model.QuestStatus;
import com.larryfarming.model.Skill;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /** Otto's planting lesson: 1 tasked, 2 seed planted, 3 reported back. */
    private static final int BARBARIAN_PLANTING_VARBIT = 9609;

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
                return done >= KNIGHT_WAVES_DONE
                        ? CapabilityVerdict.have(CapabilityVerdict.Source.DETECTED,
                                "you have finished the Knight Waves Training Grounds")
                        : CapabilityVerdict.lack(CapabilityVerdict.Source.DETECTED,
                                "finish the Knight Waves Training Grounds to respawn at "
                                        + "Camelot");
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
        return unlock == null ? CapabilityState.UNKNOWN
                : unlock.detect(state).getState();
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
     * The varbits this package reads that nothing else in the plugin asks for. Most are set
     * once and silent afterwards, so without a login scan they read as unset for players who
     * set them before installing the plugin.
     */
    public static final int[] LOGIN_SCAN_VARBIT_IDS = loginScanVarbitIds();

    private static int[] loginScanVarbitIds() {
        int[] own = {
            HouseLocation.HOUSE_LOCATION_VARBIT,
            BARBARIAN_PLANTING_VARBIT,
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
            // Pendant of Ates statues and quetzal landing sites.
            VarbitID.PENDANT_OF_ATES_TLATI_FOUND,
            VarbitID.PENDANT_OF_ATES_AUBURN_FOUND,
            VarbitID.QUETZAL_KASTORI,
            // Balloon stations, unlocked by flying there once.
            VarbitID.ZEP_MULTI_PICCARD,
            VarbitID.ZEP_MULTI_CAST,
            VarbitID.ZEP_MULTI_GNO,
            VarbitID.ZEP_MULTI_VARR,
            // No change event fires at login while this counter reads zero (per RuneLite's
            // item-charge plugin), so only the scan makes it readable.
            RingEnergyCharges.SPENT_VARBIT,
            LANCALLIUMS_PAGE_VARBIT,
            MEMOIRS_CHARGES_VARBIT,
        };
        int[] diaries = DiaryTiers.allVarbits();
        int[] all = new int[own.length + diaries.length + DAILY_USE_VARBIT_IDS.length];
        System.arraycopy(own, 0, all, 0, own.length);
        System.arraycopy(diaries, 0, all, own.length, diaries.length);
        System.arraycopy(DAILY_USE_VARBIT_IDS, 0, all, own.length + diaries.length,
                DAILY_USE_VARBIT_IDS.length);
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

    /**
     * Items only a quest hands out, or that work only once it is done. Each is untradeable
     * or useless before the quest, so no shop or craft gets a player round it. Holding one
     * shows the quest passed, so most are detected from the item alone; {@link #questItems}
     * also checks the gate.
     */
    private static final Map<Capability, QuestGate> QUEST_GATES = questGates();

    private static Map<Capability, QuestGate> questGates() {
        Map<Capability, QuestGate> gates = new EnumMap<>(Capability.class);
        gates.put(Capability.TELEPORT_CRYSTAL,
                new QuestGate("Mourning's End Part I", QuestStatus.IN_PROGRESS));
        gates.put(Capability.PENDANT_OF_ATES,
                new QuestGate("The Heart of Darkness", QuestStatus.COMPLETED));
        gates.put(Capability.MOKHAIOTL_WAYSTONE,
                new QuestGate("The Final Dawn", QuestStatus.COMPLETED));
        // King Narnode Shareen's reward, untradeable.
        gates.put(Capability.ROYAL_SEED_POD,
                new QuestGate("Monkey Madness II", QuestStatus.COMPLETED));
        // Veos hands the memoirs over as the quest's reward.
        gates.put(Capability.KHAREDSTS_MEMOIRS,
                new QuestGate("Client of Kourend", QuestStatus.COMPLETED));
        gates.put(Capability.BOOK_OF_THE_DEAD,
                new QuestGate("A Kingdom Divided", QuestStatus.COMPLETED));
        // Untradeable, and the quest is needed "to make or use" one.
        gates.put(Capability.DIGSITE_PENDANT,
                new QuestGate("The Dig Site", QuestStatus.COMPLETED));
        // Velorina hands it over as the quest ends.
        gates.put(Capability.ECTOPHIAL,
                new QuestGate("Ghosts Ahoy", QuestStatus.COMPLETED));
        return gates;
    }

    /** Capes of Accomplishment, sold only to a player with 99 in the skill. */
    private static final Map<Capability, Skill> SKILL_CAPES = skillCapes();

    private static Map<Capability, Skill> skillCapes() {
        Map<Capability, Skill> capes = new EnumMap<>(Capability.class);
        capes.put(Capability.FARMING_CAPE, Skill.FARMING);
        capes.put(Capability.CONSTRUCTION_CAPE, Skill.CONSTRUCTION);
        capes.put(Capability.FISHING_CAPE, Skill.FISHING);
        capes.put(Capability.HUNTER_CAPE, Skill.HUNTER);
        return capes;
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
        rules.put(Capability.SPIRIT_TREE_GROWN_BRIMHAVEN,
                grownSpiritTree(Capability.SPIRIT_TREE_GROWN_BRIMHAVEN, 83, "Brimhaven"));
        // 85 rather than 83: the patch is in the guild's north wing.
        rules.put(Capability.SPIRIT_TREE_GROWN_FARMING_GUILD,
                grownSpiritTree(Capability.SPIRIT_TREE_GROWN_FARMING_GUILD, 85,
                        "Farming Guild"));
        rules.put(Capability.SPIRIT_TREE_GROWN_PORT_SARIM,
                grownSpiritTree(Capability.SPIRIT_TREE_GROWN_PORT_SARIM, 83, "Port Sarim"));
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
        rules.put(Capability.POH_PORTAL_WEISS, built(HouseFeature.PORTAL_WEISS,
                "a Weiss portal", floor("a Weiss portal")
                        .construction(50)
                        .quest("Making Friends with My Arm")
                        .build()));
        // Directed with salts and basalt, so it asks for no Magic level.
        rules.put(Capability.POH_PORTAL_TROLL_STRONGHOLD,
                built(HouseFeature.PORTAL_TROLL_STRONGHOLD, "a Troll Stronghold portal",
                        floor("a Troll Stronghold portal")
                                .construction(50)
                                .quest("Making Friends with My Arm")
                                .build()));
        rules.put(Capability.POH_PORTAL_KOUREND, built(HouseFeature.PORTAL_KOUREND,
                "a Kourend portal", floor("a Kourend portal")
                        .construction(50)
                        .quest("Client of Kourend")
                        .build()));
        rules.put(Capability.POH_PORTAL_FENKENSTRAIN,
                built(HouseFeature.PORTAL_FENKENSTRAIN, "a Fenkenstrain's Castle portal",
                        floor("a Fenkenstrain's Castle portal")
                                .construction(50)
                                .quest("Priest in Peril")
                                .build()));
        rules.put(Capability.POH_PORTAL_HARMONY, built(HouseFeature.PORTAL_HARMONY,
                "a Harmony Island portal", floor("a Harmony Island portal")
                        .construction(50)
                        .quest("The Great Brain Robbery")
                        .build()));
        // The Nexus asks for the teleport's own Magic level on top of the room.
        rules.put(Capability.POH_NEXUS_WEISS, attuned(PortalNexus.WEISS, "Weiss",
                floor("Weiss in your Portal Nexus")
                        .construction(72)
                        .quest("Making Friends with My Arm")
                        .skill(Skill.MAGIC, 66)
                        .build()));
        rules.put(Capability.POH_NEXUS_TROLL_STRONGHOLD,
                attuned(PortalNexus.TROLL_STRONGHOLD, "the Troll Stronghold",
                        floor("the Troll Stronghold in your Portal Nexus")
                                .construction(72)
                                .quest("Making Friends with My Arm")
                                .skill(Skill.MAGIC, 66)
                                .build()));
        rules.put(Capability.POH_NEXUS_HARMONY, attuned(PortalNexus.HARMONY_ISLAND,
                "Harmony Island", floor("Harmony Island in your Portal Nexus")
                        .construction(72)
                        .quest("The Great Brain Robbery")
                        .skill(Skill.MAGIC, 65)
                        .build()));
        rules.put(Capability.POH_SPIRIT_TREE, built(HouseFeature.SPIRIT_TREE,
                "a spirit tree", floor("a spirit tree in your house")
                        .construction(75)
                        .skill(Skill.FARMING, 83)
                        .build()));
        rules.put(Capability.POH_FAIRY_RING, built(HouseFeature.FAIRY_RING,
                "a fairy ring", floor("a fairy ring in your house")
                        .construction(85)
                        .build()));
        return Collections.unmodifiableMap(rules);
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
                return CapabilityVerdict.have(CapabilityVerdict.Source.DETECTED, seenBuilt());
            }
            CapabilityVerdict tooEarly = floor.detect(state);
            if (tooEarly.getState() == CapabilityState.LACK) {
                return tooEarly;
            }
            if (seen == HouseSighting.ABSENT) {
                return CapabilityVerdict.lack(CapabilityVerdict.Source.DETECTED,
                        seenAbsent(house));
            }
            if (declared) {
                return CapabilityVerdict.have(CapabilityVerdict.Source.DECLARED, declared());
            }
            return CapabilityVerdict.lack(CapabilityVerdict.Source.INFERRED, unseen(house));
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
                if (house.isScanned() && !house.has(HouseFeature.PORTAL_NEXUS)
                        || house.isNexusRead()) {
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
                return house.isScanned() && !house.has(HouseFeature.PORTAL_NEXUS)
                        ? "your house has no Portal Nexus"
                        : "your Portal Nexus isn't attuned to " + where;
            }

            @Override
            String declared() {
                return "you said your Portal Nexus is attuned to " + where;
            }

            @Override
            String unseen(HouseSight house) {
                return house.has(HouseFeature.PORTAL_NEXUS)
                        ? where + " in your Portal Nexus isn't counted until you open the Nexus"
                                + TICK_IT
                        : where + " in your Portal Nexus isn't counted until you visit your house"
                                + TICK_IT;
            }
        };
    }

    /**
     * What the house as last seen says about one house capability, the same reading the
     * resolver answers from. {@link HouseSighting#UNSEEN} for anything not built in a house.
     */
    public static HouseSighting houseSighting(Capability capability, HouseSight house) {
        HouseRule rule = HOUSE_RULES.get(capability);
        return rule == null ? HouseSighting.UNSEEN
                : rule.sighting(house != null ? house : HouseSight.unseen());
    }

    /**
     * Whether a level or quest this account is seen short of rules out a house capability,
     * whatever is declared. False for anything not built in a house.
     */
    public static boolean isHouseItemOutOfReach(Capability capability, PlayerState state) {
        HouseRule rule = HOUSE_RULES.get(capability);
        return rule != null && state != null
                && rule.floor.detect(state).getState() == CapabilityState.LACK;
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
     * {@code PatchType.decodeSpiritTree} reads raw 20 and 44 as the last of thirteen stages,
     * the fully grown, ridable state.
     */
    private static PatchDetector grownSpiritTree(Capability capability, int farmingLevel,
            String where) {
        Detector cannotExist = floor("a grown spirit tree in the " + where + " patch")
                .skill(Skill.FARMING, farmingLevel)
                .quest("Tree Gnome Village")
                .build();
        return (state, sight) -> {
            CapabilityVerdict tooEarly = cannotExist.detect(state);
            if (tooEarly != null && tooEarly.getState() == CapabilityState.LACK) {
                return tooEarly;
            }
            PatchPrediction seen = sight.at(FarmingCatalogue.patchFor(capability));
            if (seen == null) {
                return CapabilityVerdict.lack(CapabilityVerdict.Source.INFERRED,
                        "check the " + where + " spirit tree patch");
            }
            if (seen.getCrop() == Crop.SPIRIT_TREE
                    && seen.getState() == CropState.GROWING
                    && seen.getStage() >= seen.getStages() - 1) {
                return CapabilityVerdict.have(CapabilityVerdict.Source.DETECTED,
                        "a grown spirit tree was seen in the " + where + " patch");
            }
            return CapabilityVerdict.lack(CapabilityVerdict.Source.DETECTED,
                    "the " + where + " spirit tree patch holds no grown tree");
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
        Detector unlit = floor("a fire of nourishment at Weiss")
                .quest("Making Friends with My Arm")
                .build();
        return (state, sight) ->
                sight.at(FarmingCatalogue.patchFor(Capability.WEISS_HERB_PATCH)) != null
                        ? CapabilityVerdict.have(CapabilityVerdict.Source.DETECTED,
                                "the Weiss herb patch has been seen, so the fire is lit")
                        : unlit.detect(state);
    }

    /** The verdict for this capability, never null. Anything without a rule is UNKNOWN. */
    public static CapabilityVerdict resolve(Capability capability, PlayerState state) {
        return resolve(capability, state, PatchSight.blind(), HouseSight.unseen());
    }

    /**
     * The same, for a caller that can say what is growing in the player's patches. Pass it
     * wherever available; see {@link PatchSight#blind()}.
     */
    public static CapabilityVerdict resolve(Capability capability, PlayerState state,
            PatchSight sight) {
        return resolve(capability, state, sight, HouseSight.unseen());
    }

    /**
     * The same, also knowing what the player's house was seen to hold. A null house is one
     * never seen.
     */
    public static CapabilityVerdict resolve(Capability capability, PlayerState state,
            PatchSight sight, HouseSight house) {
        return resolve(capability, state, sight, house, Set.of());
    }

    /**
     * The same, also knowing which house items the player has declared built. A declaration
     * counts only for an item the house has not been seen to settle.
     *
     * @param houseDeclared the declared house capabilities, never null
     */
    public static CapabilityVerdict resolve(Capability capability, PlayerState state,
            PatchSight sight, HouseSight house, Set<Capability> houseDeclared) {
        if (state == null) {
            return CapabilityVerdict.unknown("the game doesn't show this");
        }
        HouseRule houseRule = HOUSE_RULES.get(capability);
        if (houseRule != null) {
            return houseRule.detect(state, house != null ? house : HouseSight.unseen(),
                    houseDeclared.contains(capability));
        }
        PatchDetector patchRule = PATCH_RULES.get(capability);
        if (patchRule != null) {
            CapabilityVerdict seen = patchRule.detect(state,
                    sight != null ? sight : PatchSight.blind());
            return seen != null
                    ? seen
                    : CapabilityVerdict.unknown("the game doesn't show this");
        }
        Detector detector = RULES.get(capability);
        if (detector == null) {
            return CapabilityVerdict.unknown("the game doesn't show this");
        }
        CapabilityVerdict verdict = detector.detect(state);
        return verdict != null
                ? verdict
                : CapabilityVerdict.unknown("the game doesn't show this");
    }

    /** True when this capability has a way of being answered from the game. */
    public static boolean isDetectable(Capability capability) {
        return RULES.containsKey(capability) || PATCH_RULES.containsKey(capability)
                || HOUSE_RULES.containsKey(capability);
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

        // --- Skill capes --------------------------------------------------------
        rules.put(Capability.FARMING_CAPE, carried("a Farming cape",
                Capability.FARMING_CAPE));
        rules.put(Capability.CONSTRUCTION_CAPE, carried("a Construction cape",
                Capability.CONSTRUCTION_CAPE));
        rules.put(Capability.FISHING_CAPE, carried("a Fishing cape",
                Capability.FISHING_CAPE));
        rules.put(Capability.HUNTER_CAPE, carried("a Hunter cape", Capability.HUNTER_CAPE));
        // Read from the cape, not the quests or diaries behind it: both capes must be
        // bought afterwards.
        rules.put(Capability.QUEST_CAPE, carried("a Quest point cape",
                Capability.QUEST_CAPE));
        rules.put(Capability.DIARY_CAPE, carried("an Achievement diary cape",
                Capability.DIARY_CAPE));

        // --- Jewellery, by every charge tier that still works --------------------
        // The ids come from CarryItems so detection and the packing list always agree.
        // CarryItems records which tiers are excluded and why.
        rules.put(Capability.RING_OF_DUELING, carried("a ring of dueling",
                Capability.RING_OF_DUELING));
        rules.put(Capability.GAMES_NECKLACE, carried("a games necklace",
                Capability.GAMES_NECKLACE));
        rules.put(Capability.COMBAT_BRACELET, carried("a charged combat bracelet",
                Capability.COMBAT_BRACELET));
        rules.put(Capability.DIGSITE_PENDANT, carried("a digsite pendant",
                Capability.DIGSITE_PENDANT));
        rules.put(Capability.RING_OF_WEALTH, carried("a charged ring of wealth",
                Capability.RING_OF_WEALTH));
        rules.put(Capability.SKILLS_NECKLACE, carried("a skills necklace",
                Capability.SKILLS_NECKLACE));
        rules.put(Capability.XERICS_TALISMAN, carried("a charged Xeric's talisman",
                Capability.XERICS_TALISMAN));
        rules.put(Capability.KHAREDSTS_MEMOIRS,
                lancalliumsBook("Kharedst's memoirs", Capability.KHAREDSTS_MEMOIRS));
        rules.put(Capability.BOOK_OF_THE_DEAD,
                lancalliumsBook("a Book of the Dead", Capability.BOOK_OF_THE_DEAD));
        rules.put(Capability.ROYAL_SEED_POD, carried("a royal seed pod",
                Capability.ROYAL_SEED_POD));
        rules.put(Capability.CHRONICLE, carried("a Chronicle", Capability.CHRONICLE));
        rules.put(Capability.NECKLACE_OF_PASSAGE, carried("a necklace of passage",
                Capability.NECKLACE_OF_PASSAGE));
        rules.put(Capability.SLAYER_RING, carried("a slayer ring", Capability.SLAYER_RING));
        rules.put(Capability.RING_OF_THE_ELEMENTS,
                carried("a charged ring of the elements", Capability.RING_OF_THE_ELEMENTS));
        rules.put(Capability.SAILORS_AMULET, carried("a charged sailors' amulet",
                Capability.SAILORS_AMULET));

        // --- Consumable teleports ------------------------------------------------
        // Both basalts are craftable only after Making Friends with My Arm, so no quest
        // check is needed.
        rules.put(Capability.ICY_BASALT, carried("icy basalt", Capability.ICY_BASALT));
        rules.put(Capability.STONY_BASALT, carried("stony basalt", Capability.STONY_BASALT));

        // --- Teleports behind a quest ---------------------------------------------
        // Arianwyn hands the crystal over on the first arrival in Lletya, partway through
        // Mourning's End Part I, so having started the quest is the gate. Every charge
        // state and the eternal crystal count. The uncharged seed and the Gauntlet's
        // crystals do not.
        rules.put(Capability.TELEPORT_CRYSTAL, questItems(Capability.TELEPORT_CRYSTAL,
                "the teleport crystal", "a charged teleport crystal"));
        // Both drop sources (Amoxliatl, Frost Nagua) need The Heart of Darkness done.
        // Only the charged pendant is a route; the inert one needs frozen tears first.
        rules.put(Capability.PENDANT_OF_ATES, questItems(Capability.PENDANT_OF_ATES,
                "the pendant of Ates", "a charged pendant of Ates"));
        // Each statue sets its own flag the first time it is touched. Read as "not zero",
        // whatever the flag's bit width.
        rules.put(Capability.ATES_STATUE_KASTORI,
                varbitAtLeast(VarbitID.PENDANT_OF_ATES_TLATI_FOUND, 1,
                        "the Kastori statue is activated",
                        "the Kastori statue has not been activated yet"));
        rules.put(Capability.ATES_STATUE_NEMUS_RETREAT,
                varbitAtLeast(VarbitID.PENDANT_OF_ATES_AUBURN_FOUND, 1,
                        "the Nemus Retreat statue is activated",
                        "the Nemus Retreat statue has not been activated yet"));
        // Waystones drop from the Doom of Mokhaiotl and are consumed by the teleport. They
        // only work after The Final Dawn.
        rules.put(Capability.MOKHAIOTL_WAYSTONE, questItems(Capability.MOKHAIOTL_WAYSTONE,
                "the Mokhaiotl waystone", "a Mokhaiotl waystone"));
        rules.put(Capability.ECTOPHIAL, questItems(Capability.ECTOPHIAL,
                "the ectophial", "a full ectophial"));

        // Tablets and teleport spells are answered together by TeleportPlanner.

        // --- Farming supplies ----------------------------------------------------
        rules.put(Capability.SPADE, items("a spade", 952));
        rules.put(Capability.SEED_DIBBER, items("a seed dibber", 5343));
        rules.put(Capability.MAGIC_SECATEURS, items("magic secateurs", 7409));
        rules.put(Capability.BOTTOMLESS_COMPOST_BUCKET,
                items("a bottomless compost bucket", 22994, 22997));
        rules.put(Capability.ULTRACOMPOST, items("ultracompost", 21483));
        // The perk unlocks at value 2, when the seed is planted (wiki: "immediately upon
        // successfully planting the seed barehand"). The miniquest ends later, at 3, after
        // reporting back to Otto.
        rules.put(Capability.BARE_HAND_PLANTING, varbitAtLeast(BARBARIAN_PLANTING_VARBIT, 2,
                "you can plant seeds bare-handed", "you still need a seed dibber"));

        // --- Respawn points --------------------------------------------------------
        // Falador's unlock is Recruitment Drive alone, so it needs no unlock varbit.
        rules.put(Capability.RESPAWN_AVAILABLE_FALADOR,
                respawnPoint("Recruitment Drive", "Falador",
                        "Sir Tiffy Cashien in Falador Park"));
        rules.put(Capability.RESPAWN_AVAILABLE_CAMELOT,
                respawnPoint("King's Ransom", "Camelot", "the Knight Waves Training Grounds",
                        KNIGHT_WAVES_VARBIT, KNIGHT_WAVES_DONE));

        // --- Quest-gated networks -------------------------------------------------
        // The elite Lumbridge & Draynor diary lifts the staff requirement (wiki "Fairy
        // ring"). No quest does. An unread diary flag stays UNKNOWN, so the bag keeps
        // packing the staff.
        Detector staffless = state -> {
            switch (DiaryTiers.completed("Lumbridge", DiaryTiers.ELITE, state)) {
                case HAVE:
                    return CapabilityVerdict.have(CapabilityVerdict.Source.DETECTED,
                            "your elite Lumbridge & Draynor diary lets you use fairy rings "
                                    + "without a staff");
                case LACK:
                    return CapabilityVerdict.lack(CapabilityVerdict.Source.DETECTED,
                            "wield a dramen or lunar staff to use fairy rings");
                default:
                    return CapabilityVerdict.unknown(
                            "not sure whether you have the elite Lumbridge & Draynor diary");
            }
        };
        rules.put(Capability.STAFFLESS_FAIRY_RINGS, staffless);
        // Varbit 4480. Varbit 4585 holds the player's default landing, which the house
        // portal follows; the spell and the tablet offer the Exchange whatever it holds.
        rules.put(Capability.VARROCK_DIARY_MEDIUM, state -> {
            switch (DiaryTiers.completed("Varrock", DiaryTiers.MEDIUM, state)) {
                case HAVE:
                    return CapabilityVerdict.have(CapabilityVerdict.Source.DETECTED,
                            "your medium Varrock diary lets the Varrock teleport land at the "
                                    + "Grand Exchange");
                case LACK:
                    return CapabilityVerdict.lack(CapabilityVerdict.Source.DETECTED,
                            "the Varrock teleport lands at the Grand Exchange only after the "
                                    + "medium Varrock diary");
                default:
                    return CapabilityVerdict.unknown(
                            "not sure whether you have the medium Varrock diary");
            }
        });
        rules.put(Capability.FAIRY_RINGS, fairyRings(staffless));
        // The wiki also names a one-time boat with Regulus Cento, but that ride is part of
        // finishing Children of the Sun and has no separate flag. The same holds for
        // VARLAMORE_ACCESS below.
        rules.put(Capability.VARLAMORE_FAIRY_RINGS, quest("Children of the Sun",
                "the Varlamore fairy rings"));
        rules.put(Capability.VARLAMORE_ACCESS, quest("Children of the Sun", "reaching Varlamore"));
        // Started, not finished: Arianwyn leads the player into Lletya partway through, at
        // quest counter (varplayer 517) value 2.
        rules.put(Capability.LLETYA_ACCESS,
                questProgressAtLeast("Mourning's End Part I", 2, "Lletya"));
        rules.put(Capability.PRIFDDINAS_ACCESS, quest("Song of the Elves", "Prifddinas"));
        // One flag per station. Enlightened Journey opens only the Entrana-Taverley route;
        // the rest are unlocked by flying to them.
        rules.put(Capability.BALLOON_STATION_TAVERLEY,
                balloonStation(VarbitID.ZEP_MULTI_PICCARD, "Taverley"));
        rules.put(Capability.BALLOON_STATION_CASTLE_WARS,
                balloonStation(VarbitID.ZEP_MULTI_CAST, "Castle Wars"));
        rules.put(Capability.BALLOON_STATION_GRAND_TREE,
                balloonStation(VarbitID.ZEP_MULTI_GNO, "the Grand Tree"));
        rules.put(Capability.BALLOON_STATION_VARROCK,
                balloonStation(VarbitID.ZEP_MULTI_VARR, "Varrock"));
        rules.put(Capability.GNOME_GLIDER, quest("The Grand Tree", "the gnome gliders"));
        rules.put(Capability.CANAL_BARGE, quest("Bone Voyage", "the barge to Fossil Island"));
        // Started, not finished: Brother Tranquility ferries anyone who has begun the
        // quest. The tablet's stricter bar belongs to the rung that names the tablet.
        rules.put(Capability.HARMONY_ISLAND_ACCESS,
                questProgressAtLeast("The Great Brain Robbery", 10, "Harmony Island"));
        // --- Whether a patch is the player's to work at all -------------------------
        rules.put(Capability.MORYTANIA_ACCESS,
                quest("Priest in Peril", "entering Morytania"));
        // The roof patch is My Arm's Big Adventure's reward. Eadgar's Ruse only opens a
        // teleport.
        rules.put(Capability.TROLL_STRONGHOLD_HERB_PATCH,
                quest("My Arm's Big Adventure", "the Troll Stronghold herb patch"));
        // WEISS_HERB_PATCH is answered from the patch itself — see patchRules().
        rules.put(Capability.LOCUS_OASIS_PATCH,
                quest("The Ribbiting Tale of a Lily Pad Labour Dispute",
                        "the Locus Oasis hardwood patches"));
        // The counter reaches 6 when the player hands Floopa the bandage, which opens the
        // island.
        rules.put(Capability.GREAT_CONCH_ACCESS,
                questProgressAtLeast("Troubled Tortugans", 6, "the Great Conch"));
        // Chet lets nobody at the coral until the quest is finished, however far into it.
        rules.put(Capability.CORAL_NURSERY_ACCESS,
                quest("Troubled Tortugans", "the coral nurseries"));
        // Open to everyone: no Kourend favour is required. The only cost is a 20gp toll per
        // ride, which The Forsaken Tower waives. Given a rule because an undeclared capability answers UNKNOWN for ever;
        // the same applies to CHARTER_SHIP and MOUNTAIN_GUIDE below.
        rules.put(Capability.LOVAKENGJ_MINECART,
                state -> CapabilityVerdict.have(CapabilityVerdict.Source.DETECTED,
                        "the minecart network is open to everyone"));
        rules.put(Capability.CHARTER_SHIP,
                state -> CapabilityVerdict.have(CapabilityVerdict.Source.DETECTED,
                        "the charter ships sail for anyone who pays the fare"));
        // Detected on the item, not the quest: the whistle is crafted after ten Hunters'
        // Rumours, not handed over with Twilight's Promise.
        rules.put(Capability.QUETZAL_WHISTLE, carried("a quetzal whistle",
                Capability.QUETZAL_WHISTLE));
        rules.put(Capability.BRIMHAVEN_TABLET, carried("a Brimhaven teleport tablet",
                Capability.BRIMHAVEN_TABLET));
        // Renu flies the same network as the whistle, but only from a landing site the
        // player has walked to. The quest gifts it, and there is no item to hold.
        rules.put(Capability.RENU, quest("Twilight's Promise", "riding Renu"));
        // Kastori is a player-built landing site (no Construction level needed), so this
        // flag alone answers it.
        rules.put(Capability.QUETZAL_SITE_KASTORI,
                varbitAtLeast(VarbitID.QUETZAL_KASTORI, 1,
                        "the Kastori landing site is built",
                        "the Kastori landing site has not been built"));
        // The mountain guide walks anyone between Quetzacalli Gorge and Auburn Valley, free.
        rules.put(Capability.MOUNTAIN_GUIDE,
                state -> CapabilityVerdict.have(CapabilityVerdict.Source.DETECTED,
                        "the mountain guide walks anyone who asks"));

        // --- Spells ----------------------------------------------------------------
        // Only spells with no fixed destination; TeleportPlanner answers the rest. These
        // rules answer "could this player ever cast it". spellbookFor records each spell's
        // book, and CapabilityResolver enforces it.
        rules.put(Capability.RESPAWN_TELEPORT, spell(34, null, "Respawn Teleport"));
        rules.put(Capability.RESURRECT_CROP, spell(78, null, "Resurrect Crops"));
        rules.put(Capability.FERTILE_SOIL, spell(83, "Lunar Diplomacy", "Fertile Soil"));
        rules.put(Capability.HUMIDIFY, spell(68, "Dream Mentor", "Humidify"));

        // --- Skill-gated shortcuts ------------------------------------------------
        rules.put(Capability.FOSSIL_ISLAND_ZIPLINE, skill(Skill.AGILITY, 74, "the zipline"));
        rules.put(Capability.TAVERLEY_CLIMBING_ROCKS,
                skill(Skill.AGILITY, 66, "the climbing rocks into Taverley"));

        // What is built in the house is answered from the house; see houseRules().

        // No Farming level: eight of the network's destinations are permanent trees.
        rules.put(Capability.SPIRIT_TREE_NETWORK,
                quest("Tree Gnome Village", "the spirit tree network"));
        // The Stronghold's tree is the one departure with a second quest. Same unlock as the
        // gliders, but a separate capability so the player is told which journey it is.
        rules.put(Capability.SPIRIT_TREE_STRONGHOLD,
                quest("The Grand Tree", "the Gnome Stronghold spirit tree"));
        // Player-planted trees are answered from the patch; see patchRules().

        // --- Where the house is ----------------------------------------------------
        // The house's town is the one fact about it readable from anywhere, and decides
        // where every "redirected" chain lands.
        rules.put(Capability.POH_IN_HOSIDIUS, houseIn(Capability.POH_IN_HOSIDIUS, "Hosidius"));
        rules.put(Capability.POH_IN_BRIMHAVEN, houseIn(Capability.POH_IN_BRIMHAVEN, "Brimhaven"));
        rules.put(Capability.POH_IN_TAVERLEY, houseIn(Capability.POH_IN_TAVERLEY, "Taverley"));

        return Collections.unmodifiableMap(rules);
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
            case RESURRECT_CROP:
                return Spellbook.ARCEUUS;
            case FERTILE_SOIL:
            case HUMIDIFY:
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
        Detector eitherBook = items("Kharedst's memoirs or a Book of the Dead",
                LANCALLIUMS_BOOK_IDS);
        return state -> {
            CapabilityVerdict holding = held.detect(state);
            // "Your book" only for a player seen holding one; otherwise the holding answer
            // says what is known.
            if (eitherBook.detect(state).getState() != CapabilityState.HAVE) {
                return holding;
            }
            Integer page = state.getVarbits().get(LANCALLIUMS_PAGE_VARBIT);
            if (page != null && page == 0) {
                return CapabilityVerdict.lack(CapabilityVerdict.Source.DETECTED,
                        "your book is missing the Lunch by the Lancalliums page");
            }
            Integer charges = state.getVarbits().get(MEMOIRS_CHARGES_VARBIT);
            if (charges != null && charges == 0) {
                return CapabilityVerdict.lack(CapabilityVerdict.Source.DETECTED,
                        "your book has no charges. Recharge it at the Old Memorial");
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
                    return CapabilityVerdict.have(CapabilityVerdict.Source.DETECTED,
                            "you have " + description);
                }
            }
            if (state.getBankLastSeen() == null) {
                return CapabilityVerdict.unknown("not sure whether you have " + description
                        + " until you open your bank");
            }
            return CapabilityVerdict.lack(CapabilityVerdict.Source.DETECTED,
                    "you have no " + bare(description));
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
        Detector permission = questProgressAtLeast("Fairytale II - Cure a Queen",
                FAIRY_RING_PERMISSION_STAGE, "the fairy ring network");
        Detector staff = items("a dramen or lunar staff",
                Loadouts.DRAMEN_STAFF, Loadouts.LUNAR_STAFF);
        return state -> {
            CapabilityVerdict opened = allOf(growingPains.detect(state),
                    permission.detect(state));
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
            if (noStaffNeeded.getState() == CapabilityState.LACK
                    && held.getState() == CapabilityState.LACK) {
                return CapabilityVerdict.lack(CapabilityVerdict.Source.DETECTED,
                        "you need a dramen or lunar staff to use fairy rings");
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
    private static Detector questItems(Capability capability, String unlocks,
            String description) {
        QuestGate gate = QUEST_GATES.get(capability);
        Detector held = carried(description, capability);
        return state -> {
            if (gate.bars(state)) {
                return CapabilityVerdict.lack(CapabilityVerdict.Source.INFERRED,
                        gate.getQuest() + " gives you " + unlocks);
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
                return CapabilityVerdict.have(CapabilityVerdict.Source.DETECTED,
                        "you have finished " + questName);
            }
            if (status == null) {
                // Quest state arrives as it is observed, so silence is not a no.
                return CapabilityVerdict.unknown(
                        "not sure whether you have finished " + questName);
            }
            return CapabilityVerdict.lack(CapabilityVerdict.Source.DETECTED,
                    questName + " unlocks " + description);
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
                return CapabilityVerdict.have(CapabilityVerdict.Source.DETECTED,
                        "you have finished " + questName);
            }
            Integer progress = state.getQuestProgress().get(questName);
            if (progress == null) {
                // The login scan stores only non-zero counters, so an absent one means
                // untouched or unscanned. The status tells them apart when present.
                return state.getQuests().get(questName) == QuestStatus.NOT_STARTED
                        ? CapabilityVerdict.lack(CapabilityVerdict.Source.DETECTED,
                                questName + " opens " + unlocks)
                        : CapabilityVerdict.unknown(
                                "not sure how far you have got in " + questName);
            }
            if (progress >= opensAt) {
                return CapabilityVerdict.have(CapabilityVerdict.Source.DETECTED,
                        "you are far enough into " + questName);
            }
            return CapabilityVerdict.unknown("you have started " + questName
                    + ", which opens " + unlocks + " partway through");
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
                return CapabilityVerdict.lack(CapabilityVerdict.Source.DETECTED,
                        spellName + " needs " + magicLevel + " Magic and you have " + magic);
            }
            if (questName == null) {
                return CapabilityVerdict.have(CapabilityVerdict.Source.DETECTED,
                        "you can cast " + spellName);
            }
            QuestStatus status = state.getQuests().get(questName);
            if (status == QuestStatus.COMPLETED) {
                return CapabilityVerdict.have(CapabilityVerdict.Source.DETECTED,
                        "you can cast " + spellName);
            }
            if (status == null) {
                return CapabilityVerdict.unknown(
                        "not sure whether you have finished " + questName + ", which "
                                + spellName + " needs");
            }
            return CapabilityVerdict.lack(CapabilityVerdict.Source.DETECTED,
                    spellName + " needs " + questName);
        };
    }

    /**
     * One balloon station, flagged once the player has flown there. The flag already
     * implies Enlightened Journey and its Firemaking level.
     */
    private static Detector balloonStation(int varbitId, String where) {
        return varbitAtLeast(varbitId, 1,
                "you have unlocked the balloon station at " + where,
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
            return current >= value
                    ? CapabilityVerdict.have(CapabilityVerdict.Source.DETECTED, have)
                    : CapabilityVerdict.lack(CapabilityVerdict.Source.DETECTED, lack);
        };
    }

    /**
     * A respawn town whose unlock is a plain quest, with no separate flag to read.
     *
     * <p>The selected respawn town answers outright when it has been read. Otherwise an
     * unlocked town answers UNKNOWN, with a reason telling the player where to select it.
     */
    private static Detector respawnPoint(String questName, String town, String where) {
        Detector questGate = quest(questName, "respawning at " + town);
        return state -> {
            CapabilityVerdict settled = fromSelection(town, state);
            if (settled != null) {
                return settled;
            }
            CapabilityVerdict verdict = questGate.detect(state);
            return verdict.getState() == CapabilityState.HAVE
                    ? CapabilityVerdict.unknown("set your respawn point to " + town
                            + " at " + where + " to use this")
                    : verdict;
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
        return selected.equals(town)
                ? CapabilityVerdict.have(CapabilityVerdict.Source.DETECTED,
                        "your respawn point is " + town)
                : CapabilityVerdict.lack(CapabilityVerdict.Source.DETECTED,
                        "your respawn point is " + selected + ", not " + town);
    }

    private static Detector respawnPoint(String questName, String town, String where,
            int unlockVarbit, int unlockValue) {
        Detector questGate = quest(questName, "respawning at " + town);
        return state -> {
            String selectIt =
                    "set your respawn point to " + town + " at " + where + " to use this";
            // The setting outranks the unlock, which only says they could choose it.
            CapabilityVerdict settled = fromSelection(town, state);
            if (settled != null) {
                return settled;
            }
            Integer unlock = state.getVarbits().get(unlockVarbit);
            if (unlock != null) {
                return unlock >= unlockValue
                        ? CapabilityVerdict.unknown(selectIt)
                        : CapabilityVerdict.lack(CapabilityVerdict.Source.DETECTED,
                                where + " unlocks respawning at " + town);
            }
            CapabilityVerdict verdict = questGate.detect(state);
            return verdict.getState() == CapabilityState.HAVE
                    ? CapabilityVerdict.unknown(selectIt)
                    : verdict;
        };
    }

    private static FloorBuilder floor(String what) {
        return new FloorBuilder(what);
    }

    /**
     * The requirements below which a player cannot have built a thing.
     *
     * <p>A floor never answers yes: meeting every requirement makes a thing possible, not
     * actual. Falling short of one is LACK; otherwise the answer is UNKNOWN. An unobserved
     * quest is not a shortfall.
     */
    private static final class FloorBuilder {
        private final String what;
        private int construction;
        private String questName;
        private Skill skill;
        private int skillLevel;

        FloorBuilder(String what) {
            this.what = what;
        }

        FloorBuilder construction(int level) {
            this.construction = level;
            return this;
        }

        FloorBuilder quest(String questName) {
            this.questName = questName;
            return this;
        }

        FloorBuilder skill(Skill skill, int level) {
            this.skill = skill;
            this.skillLevel = level;
            return this;
        }

        Floor build() {
            return new Floor(what, construction, questName, skill, skillLevel);
        }
    }

    /** A built {@link FloorBuilder}: never null, never yes. */
    private static final class Floor implements Detector {
        private final String what;
        private final int construction;
        private final String questName;
        private final Skill skill;
        private final int skillLevel;

        Floor(String what, int construction, String questName, Skill skill, int skillLevel) {
            this.what = what;
            this.construction = construction;
            this.questName = questName;
            this.skill = skill;
            this.skillLevel = skillLevel;
        }

        @Override
        public CapabilityVerdict detect(PlayerState state) {
            if (construction > 0) {
                int level = state.getSkillLevel(Skill.CONSTRUCTION);
                if (level < construction) {
                    return CapabilityVerdict.lack(CapabilityVerdict.Source.INFERRED,
                            what + " needs " + construction
                                    + " Construction and you have " + level);
                }
            }
            if (skill != null) {
                int level = state.getSkillLevel(skill);
                if (level < skillLevel) {
                    return CapabilityVerdict.lack(CapabilityVerdict.Source.INFERRED,
                            what + " needs " + skillLevel + " "
                                    + skill.getDisplayName() + " and you have " + level);
                }
            }
            if (questName != null) {
                QuestStatus status = state.getQuests().get(questName);
                if (status != null && status != QuestStatus.COMPLETED) {
                    return CapabilityVerdict.lack(CapabilityVerdict.Source.INFERRED,
                            what + " needs " + questName);
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
                return CapabilityVerdict.have(CapabilityVerdict.Source.DETECTED,
                        "your " + skill.getDisplayName() + " is high enough");
            }
            return CapabilityVerdict.lack(CapabilityVerdict.Source.DETECTED,
                    description + " needs " + level + " " + skill.getDisplayName()
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
                return CapabilityVerdict.lack(CapabilityVerdict.Source.DETECTED,
                        "you do not own a house");
            }
            return here
                    ? CapabilityVerdict.have(CapabilityVerdict.Source.DETECTED,
                            "your house is in " + town)
                    : CapabilityVerdict.lack(CapabilityVerdict.Source.DETECTED,
                            "your house is not in " + town);
        };
    }

    /**
     * A diary reward, answered by whether its diary tier is finished. A higher tier replaces
     * the reward, so the best completed tier is the one the player holds.
     */
    private static Detector diaryReward(Capability capability) {
        return state -> {
            Capability earned = DiaryRewards.bestEarnedTier(
                    capability.getTierFamily(), state.getVarbits());
            if (earned == null) {
                return CapabilityVerdict.lack(CapabilityVerdict.Source.DETECTED,
                        "you have not earned " + capability.getDisplayName() + " yet");
            }
            if (capability.isSatisfiedBy(earned)) {
                String because = earned == capability
                        ? "you have " + capability.getDisplayName()
                        : "you have " + earned.getDisplayName() + ", which does the same";
                return CapabilityVerdict.have(CapabilityVerdict.Source.INFERRED, because);
            }
            return CapabilityVerdict.lack(CapabilityVerdict.Source.DETECTED,
                    "you have " + earned.getDisplayName() + ", not "
                            + capability.getDisplayName());
        };
    }

    /** Every capability the tables can answer, for reporting what stays unknown. */
    public static List<Capability> detectable() {
        List<Capability> detectable = new ArrayList<>(RULES.keySet());
        detectable.addAll(PATCH_RULES.keySet());
        detectable.addAll(HOUSE_RULES.keySet());
        return Collections.unmodifiableList(detectable);
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
