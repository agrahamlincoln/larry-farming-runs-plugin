package com.larryfarming.farming.model;

import com.larryfarming.engine.farming.Crop;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The config keys of every farming setting, spelled once.
 *
 * <p>Constants rather than an enum so {@code LarryConfig}'s {@code @ConfigItem} annotations
 * can name them. The side panel writes these keys, the plugin listens for them, and the
 * planner reads them, all through this class.
 */
public final class FarmingKeys {

    // Herbs.
    public static final String PLANT_TORSTOL = "plantTorstol";
    public static final String PLANT_TOADFLAX = "plantToadflax";
    public static final String PLANT_LANTADYME = "plantLantadyme";
    public static final String PLANT_CADANTINE = "plantCadantine";
    public static final String PLANT_KWUARM = "plantKwuarm";
    public static final String PLANT_AVANTOE = "plantAvantoe";
    public static final String PLANT_IRIT = "plantIrit";
    public static final String PLANT_SNAPDRAGON = "plantSnapdragon";
    public static final String PLANT_RANARR = "plantRanarr";
    public static final String PLANT_DWARF_WEED = "plantDwarfWeed";
    public static final String PLANT_HARRALANDER = "plantHarralander";
    public static final String PLANT_TARROMIN = "plantTarromin";
    public static final String PLANT_MARRENTILL = "plantMarrentill";
    public static final String PLANT_GUAM = "plantGuam";

    // Regular trees.
    public static final String PLANT_OAK = "plantOak";
    public static final String PLANT_WILLOW = "plantWillow";
    public static final String PLANT_MAPLE = "plantMaple";
    public static final String PLANT_YEW = "plantYew";
    public static final String PLANT_MAGIC = "plantMagic";

    // Fruit trees.
    public static final String PLANT_APPLE = "plantApple";
    public static final String PLANT_BANANA = "plantBanana";
    public static final String PLANT_ORANGE = "plantOrange";
    public static final String PLANT_CURRY = "plantCurry";
    public static final String PLANT_PINEAPPLE = "plantPineapple";
    public static final String PLANT_PAPAYA = "plantPapaya";
    public static final String PLANT_PALM = "plantPalm";
    public static final String PLANT_DRAGONFRUIT = "plantDragonfruit";

    // Hardwoods.
    public static final String PLANT_TEAK = "plantTeak";
    public static final String PLANT_MAHOGANY = "plantMahogany";

    // Crops grown in one named patch, and the single-crop runs.
    public static final String PLANT_SNAPE_GRASS = "plantSnapeGrass";
    public static final String PLANT_WATERMELON = "plantWatermelon";
    public static final String PLANT_WHITE_LILY = "plantWhiteLily";
    public static final String PLANT_LIMPWURT = "plantLimpwurt";
    public static final String PLANT_POTATO_CACTUS = "plantPotatoCactus";
    public static final String PLANT_CACTUS = "plantCactus";
    public static final String PLANT_WHITEBERRIES = "plantWhiteberries";
    public static final String PLANT_POISON_IVY = "plantPoisonIvy";
    public static final String PLANT_MUSHROOMS = "plantMushrooms";
    public static final String PLANT_BELLADONNA = "plantBelladonna";
    public static final String PLANT_CALQUAT = "plantCalquat";
    public static final String PLANT_SEAWEED = "plantSeaweed";
    public static final String PLANT_HESPORI = "plantHespori";

    // Anima plants, each the player lets Larry choose.
    public static final String PLANT_ATTAS = "plantAttas";
    public static final String PLANT_IASOR = "plantIasor";
    public static final String PLANT_KRONOS = "plantKronos";

    // Coral frags, each the player lets Larry plant in the nurseries.
    public static final String PLANT_ELKHORN_CORAL = "plantElkhornCoral";
    public static final String PLANT_PILLAR_CORAL = "plantPillarCoral";
    public static final String PLANT_UMBRAL_CORAL = "plantUmbralCoral";

    // Switches that plant nothing.
    public static final String PICK_FRUIT_IN_PASSING = "pickFruitInPassing";
    public static final String WORK_COMPOST_BINS = "workCompostBins";
    // The compost stock's two thresholds, each a whole number of patches as a config string:
    // the bins are worked once the stock falls below the first, and left once it reaches
    // the second.
    public static final String MAKE_COMPOST_BELOW = "makeCompostBelow";
    public static final String STOP_COMPOST_AT = "stopCompostAt";
    // Travel methods, see TravelMethod, each switching every use of the method.
    public static final String USE_HOUSE_FAIRY_RING = "useFairyRingInHouse";
    public static final String USE_QUEST_CAPE = "useQuestCape";
    public static final String USE_SLAYER_RING = "useSlayerRing";
    public static final String USE_DIARY_CAPE = "useDiaryCape";
    public static final String USE_NECKLACE_OF_PASSAGE = "useNecklaceOfPassageTeleports";
    public static final String USE_ARDOUGNE_CLOAK = "useArdougneCloak";
    public static final String USE_SALVE_GRAVEYARD_TABLETS = "useSalveGraveyardTeleports";
    public static final String USE_RING_OF_WEALTH = "useRingOfWealth";
    public static final String USE_GAMES_NECKLACE = "useGamesNecklace";
    public static final String USE_COMBAT_BRACELET = "useCombatBracelet";
    public static final String USE_SKILLS_NECKLACE = "useSkillsNecklace";
    public static final String USE_RING_OF_DUELING = "useRingOfDueling";
    public static final String USE_RING_OF_THE_ELEMENTS = "useRingOfTheElements";
    public static final String USE_DIGSITE_PENDANT = "useDigsitePendant";
    public static final String USE_XERICS_TALISMAN = "useXericsTalisman";
    public static final String USE_KHAREDSTS_MEMOIRS = "useKharedstsMemoirs";
    public static final String USE_PENDANT_OF_ATES = "usePendantOfAtes";
    public static final String USE_SAILORS_AMULET = "useSailorsAmulet";
    public static final String USE_TELEPORT_CRYSTAL = "useTeleportCrystal";
    public static final String USE_CHRONICLE = "useChronicle";
    public static final String USE_QUETZAL_WHISTLE = "useQuetzalWhistle";
    public static final String USE_ROYAL_SEED_POD = "useRoyalSeedPod";
    public static final String USE_ECTOPHIAL = "useEctophial";
    public static final String USE_BASALT = "useBasalt";
    public static final String USE_MOKHAIOTL_WAYSTONE = "useMokhaiotlWaystone";
    public static final String USE_EXPLORERS_RING = "useExplorersRing";
    public static final String USE_SKILL_CAPES = "useSkillCapes";
    public static final String USE_CONSTRUCTION_CAPE = "useConstructionCape";
    public static final String USE_TELEPORT_TABLETS = "useTeleportTablets";
    public static final String USE_SPELLBOOK_SWAP = "useSpellbookSwap";
    public static final String USE_HOUSE_PORTALS = "useHousePortals";

    // Sites a run visits, each true while the run includes it. Each key is named for its
    // place and written out here rather than derived from the catalogue's constant names,
    // so renaming a constant keeps a saved choice. The herb run's extras have their crop
    // switches instead.
    public static final String VISIT_WEISS = "visitWeiss";
    public static final String VISIT_TROLLHEIM = "visitTrollheim";
    public static final String VISIT_MORYTANIA = "visitMorytania";
    public static final String VISIT_FALADOR_HERB = "visitFaladorHerb";
    public static final String VISIT_CATHERBY_HERB = "visitCatherbyHerb";
    public static final String VISIT_ARDOUGNE_HERB = "visitArdougneHerb";
    public static final String VISIT_HOSIDIUS_HERB = "visitHosidiusHerb";
    public static final String VISIT_HARMONY = "visitHarmony";
    public static final String VISIT_ORTUS_FARM = "visitOrtusFarm";
    public static final String VISIT_FOSSIL_ISLAND = "visitFossilIsland";
    public static final String VISIT_LOCUS_OASIS = "visitLocusOasis";
    public static final String VISIT_TAI_BWO_WANNAI_CALQUAT = "visitTaiBwoWannaiCalquat";
    public static final String VISIT_KASTORI_CALQUAT = "visitKastoriCalquat";
    public static final String VISIT_GREAT_CONCH_CALQUAT = "visitGreatConchCalquat";
    public static final String VISIT_VARROCK_TREE = "visitVarrockTree";
    public static final String VISIT_LUMBRIDGE_TREE = "visitLumbridgeTree";
    public static final String VISIT_FALADOR_TREE = "visitFaladorTree";
    public static final String VISIT_TAVERLEY_TREE = "visitTaverleyTree";
    public static final String VISIT_GNOME_STRONGHOLD_TREE = "visitGnomeStrongholdTree";
    public static final String VISIT_NEMUS_RETREAT_TREE = "visitNemusRetreatTree";
    public static final String VISIT_BRIMHAVEN_FRUIT = "visitBrimhavenFruit";
    public static final String VISIT_CATHERBY_FRUIT = "visitCatherbyFruit";
    public static final String VISIT_GNOME_STRONGHOLD_FRUIT = "visitGnomeStrongholdFruit";
    public static final String VISIT_TREE_GNOME_VILLAGE_FRUIT = "visitTreeGnomeVillageFruit";
    public static final String VISIT_LLETYA_FRUIT = "visitLletyaFruit";
    public static final String VISIT_KASTORI_FRUIT = "visitKastoriFruit";
    public static final String VISIT_GUILD_COMPOST_BIN = "visitGuildCompostBin";
    public static final String VISIT_FALADOR_COMPOST_BIN = "visitFaladorCompostBin";
    public static final String VISIT_CATHERBY_COMPOST_BIN = "visitCatherbyCompostBin";
    public static final String VISIT_ARDOUGNE_COMPOST_BIN = "visitArdougneCompostBin";
    public static final String VISIT_HOSIDIUS_COMPOST_BIN = "visitHosidiusCompostBin";
    public static final String VISIT_MORYTANIA_COMPOST_BIN = "visitMorytaniaCompostBin";
    public static final String VISIT_CIVITAS_COMPOST_BIN = "visitCivitasCompostBin";
    public static final String VISIT_PRIFDDINAS_COMPOST_BIN = "visitPrifddinasCompostBin";

    // What the player says their house holds, see HouseItem, each true while declared built.
    // A declaration counts only until Larry has seen the item.
    public static final String DECLARE_WEISS_PORTAL = "declareWeissPortal";
    public static final String DECLARE_TROLL_STRONGHOLD_PORTAL = "declareTrollStrongholdPortal";
    public static final String DECLARE_KOUREND_PORTAL = "declareKourendPortal";
    public static final String DECLARE_FENKENSTRAIN_PORTAL = "declareFenkenstrainPortal";
    public static final String DECLARE_HARMONY_PORTAL = "declareHarmonyPortal";
    public static final String DECLARE_WEISS_IN_NEXUS = "declareWeissInNexus";
    public static final String DECLARE_TROLL_STRONGHOLD_IN_NEXUS =
            "declareTrollStrongholdInNexus";
    public static final String DECLARE_HARMONY_IN_NEXUS = "declareHarmonyInNexus";
    public static final String DECLARE_HOUSE_SPIRIT_TREE = "declareHouseSpiritTree";
    public static final String DECLARE_HOUSE_FAIRY_RING = "declareHouseFairyRing";

    /** Every site's key, which the catalogue maps to its site. */
    public static final List<String> SITES = List.of(VISIT_WEISS, VISIT_TROLLHEIM,
            VISIT_MORYTANIA, VISIT_FALADOR_HERB, VISIT_CATHERBY_HERB, VISIT_ARDOUGNE_HERB,
            VISIT_HOSIDIUS_HERB, VISIT_HARMONY, VISIT_ORTUS_FARM, VISIT_FOSSIL_ISLAND,
            VISIT_LOCUS_OASIS, VISIT_TAI_BWO_WANNAI_CALQUAT, VISIT_KASTORI_CALQUAT,
            VISIT_GREAT_CONCH_CALQUAT, VISIT_VARROCK_TREE, VISIT_LUMBRIDGE_TREE,
            VISIT_FALADOR_TREE, VISIT_TAVERLEY_TREE, VISIT_GNOME_STRONGHOLD_TREE,
            VISIT_NEMUS_RETREAT_TREE, VISIT_BRIMHAVEN_FRUIT, VISIT_CATHERBY_FRUIT,
            VISIT_GNOME_STRONGHOLD_FRUIT, VISIT_TREE_GNOME_VILLAGE_FRUIT, VISIT_LLETYA_FRUIT,
            VISIT_KASTORI_FRUIT, VISIT_GUILD_COMPOST_BIN, VISIT_FALADOR_COMPOST_BIN,
            VISIT_CATHERBY_COMPOST_BIN, VISIT_ARDOUGNE_COMPOST_BIN, VISIT_HOSIDIUS_COMPOST_BIN,
            VISIT_MORYTANIA_COMPOST_BIN, VISIT_CIVITAS_COMPOST_BIN,
            VISIT_PRIFDDINAS_COMPOST_BIN);

    // How the planner ranks and paces. The favourite herb holds a herb's Crop name, or
    // NO_FAVOURITE; the others are booleans.
    public static final String FAVOURITE_HERB = "favouriteHerb";
    public static final String BEST_SEEDS_EVERYWHERE = "bestSeedsInEveryPatch";
    public static final String HERB_RUN_FIRST = "herbRunFirst";
    public static final String WAIT_FOR_EVERY_PATCH = "waitForEveryPatch";
    public static final String FRUIT_TREES_WAIT_FOR_SAPLINGS = "fruitTreesWaitForSaplings";
    public static final String REPLANT_GROWN_TREES = "replantGrownTrees";
    public static final String HERB_RUNS_WAIT_FOR_LEVEL = "herbRunsWaitForLevel";
    public static final String CORAL_WAITS_FOR_ATTAS = "coralWaitsForAttas";
    public static final String COMPOST_WITH_FERTILE_SOIL = "compostWithFertileSoil";

    /** The favourite herb's value when there is none. */
    public static final String NO_FAVOURITE = "none";

    /** Each crop's tick, for every crop {@link FarmingPreferences#hasAswitch} accepts. */
    public static final Map<Crop, String> CROPS = cropKeys();

    private static final Set<String> ALL = allKeys();

    private FarmingKeys() {
    }

    private static Map<Crop, String> cropKeys() {
        Map<Crop, String> keys = new EnumMap<>(Crop.class);
        keys.put(Crop.TORSTOL, PLANT_TORSTOL);
        keys.put(Crop.TOADFLAX, PLANT_TOADFLAX);
        keys.put(Crop.LANTADYME, PLANT_LANTADYME);
        keys.put(Crop.CADANTINE, PLANT_CADANTINE);
        keys.put(Crop.KWUARM, PLANT_KWUARM);
        keys.put(Crop.AVANTOE, PLANT_AVANTOE);
        keys.put(Crop.IRIT, PLANT_IRIT);
        keys.put(Crop.SNAPDRAGON, PLANT_SNAPDRAGON);
        keys.put(Crop.RANARR, PLANT_RANARR);
        keys.put(Crop.DWARF_WEED, PLANT_DWARF_WEED);
        keys.put(Crop.HARRALANDER, PLANT_HARRALANDER);
        keys.put(Crop.TARROMIN, PLANT_TARROMIN);
        keys.put(Crop.MARRENTILL, PLANT_MARRENTILL);
        keys.put(Crop.GUAM, PLANT_GUAM);
        keys.put(Crop.OAK, PLANT_OAK);
        keys.put(Crop.WILLOW, PLANT_WILLOW);
        keys.put(Crop.MAPLE, PLANT_MAPLE);
        keys.put(Crop.YEW, PLANT_YEW);
        keys.put(Crop.MAGIC, PLANT_MAGIC);
        keys.put(Crop.APPLE, PLANT_APPLE);
        keys.put(Crop.BANANA, PLANT_BANANA);
        keys.put(Crop.ORANGE, PLANT_ORANGE);
        keys.put(Crop.CURRY, PLANT_CURRY);
        keys.put(Crop.PINEAPPLE, PLANT_PINEAPPLE);
        keys.put(Crop.PAPAYA, PLANT_PAPAYA);
        keys.put(Crop.PALM, PLANT_PALM);
        keys.put(Crop.DRAGONFRUIT, PLANT_DRAGONFRUIT);
        keys.put(Crop.TEAK, PLANT_TEAK);
        keys.put(Crop.MAHOGANY, PLANT_MAHOGANY);
        keys.put(Crop.SNAPE_GRASS, PLANT_SNAPE_GRASS);
        keys.put(Crop.WATERMELON, PLANT_WATERMELON);
        keys.put(Crop.WHITE_LILY, PLANT_WHITE_LILY);
        keys.put(Crop.LIMPWURT, PLANT_LIMPWURT);
        keys.put(Crop.POTATO_CACTUS, PLANT_POTATO_CACTUS);
        keys.put(Crop.CACTUS, PLANT_CACTUS);
        keys.put(Crop.WHITEBERRIES, PLANT_WHITEBERRIES);
        keys.put(Crop.POISON_IVY, PLANT_POISON_IVY);
        keys.put(Crop.MUSHROOM, PLANT_MUSHROOMS);
        keys.put(Crop.BELLADONNA, PLANT_BELLADONNA);
        keys.put(Crop.CALQUAT, PLANT_CALQUAT);
        keys.put(Crop.SEAWEED, PLANT_SEAWEED);
        keys.put(Crop.HESPORI, PLANT_HESPORI);
        keys.put(Crop.ATTAS, PLANT_ATTAS);
        keys.put(Crop.IASOR, PLANT_IASOR);
        keys.put(Crop.KRONOS, PLANT_KRONOS);
        keys.put(Crop.ELKHORN_CORAL, PLANT_ELKHORN_CORAL);
        keys.put(Crop.PILLAR_CORAL, PLANT_PILLAR_CORAL);
        keys.put(Crop.UMBRAL_CORAL, PLANT_UMBRAL_CORAL);
        return Collections.unmodifiableMap(keys);
    }

    private static Set<String> allKeys() {
        Set<String> all = new HashSet<>(CROPS.values());
        all.add(PICK_FRUIT_IN_PASSING);
        all.add(WORK_COMPOST_BINS);
        all.add(MAKE_COMPOST_BELOW);
        all.add(STOP_COMPOST_AT);
        for (TravelMethod method : TravelMethod.values()) {
            all.add(method.getKey());
        }
        all.addAll(SITES);
        for (HouseItem item : HouseItem.values()) {
            all.add(item.getKey());
        }
        all.add(FAVOURITE_HERB);
        all.add(BEST_SEEDS_EVERYWHERE);
        all.add(HERB_RUN_FIRST);
        all.add(WAIT_FOR_EVERY_PATCH);
        all.add(FRUIT_TREES_WAIT_FOR_SAPLINGS);
        all.add(REPLANT_GROWN_TREES);
        all.add(HERB_RUNS_WAIT_FOR_LEVEL);
        all.add(CORAL_WAITS_FOR_ATTAS);
        all.add(COMPOST_WITH_FERTILE_SOIL);
        return Collections.unmodifiableSet(all);
    }

    /** Every key a farming setting is saved under. */
    public static Set<String> all() {
        return ALL;
    }
}
