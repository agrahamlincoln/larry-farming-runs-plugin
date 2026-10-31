package com.larryfarming;

import com.larryfarming.farming.model.FarmingKeys;
import com.larryfarming.farming.model.Preset;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(LarryConfig.GROUP)
public interface LarryConfig extends Config
{
	/** Named so a config listener can tell our settings from every other plugin's. */
	String GROUP = "larryfarming";

	@ConfigItem(
		keyName = "showFarmingAdvisor",
		name = "Show Farming Run Infobox",
		description = "Show an infobox while a farming run is due",
		position = 1
	)
	default boolean showFarmingAdvisor()
	{
		return true;
	}

	// The farming settings below are hidden: the side panel's settings edit them. They are
	// declared so RuneLite saves them and resets them with the rest of the plugin. Each
	// default must match FarmingPreferences.defaults().
	//
	// Herbs follow a fixed ranking, not Farming level. Torstol tops it but starts off: it is
	// kept for hard contracts.

	@ConfigItem(keyName = FarmingKeys.PLANT_TORSTOL, name = "Torstol",
		description = "Plant torstol on herb runs", hidden = true, position = 19)
	default boolean plantTorstol()
	{
		return false;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_TOADFLAX, name = "Toadflax",
		description = "Plant toadflax on herb runs", hidden = true, position = 20)
	default boolean plantToadflax()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_LANTADYME, name = "Lantadyme",
		description = "Plant lantadyme on herb runs", hidden = true, position = 21)
	default boolean plantLantadyme()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_CADANTINE, name = "Cadantine",
		description = "Plant cadantine on herb runs", hidden = true, position = 22)
	default boolean plantCadantine()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_KWUARM, name = "Kwuarm",
		description = "Plant kwuarm on herb runs", hidden = true, position = 23)
	default boolean plantKwuarm()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_AVANTOE, name = "Avantoe",
		description = "Plant avantoe on herb runs", hidden = true, position = 24)
	default boolean plantAvantoe()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_IRIT, name = "Irit",
		description = "Plant irit on herb runs", hidden = true, position = 25)
	default boolean plantIrit()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_SNAPDRAGON, name = "Snapdragon",
		description = "Plant snapdragon on herb runs", hidden = true, position = 26)
	default boolean plantSnapdragon()
	{
		return false;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_RANARR, name = "Ranarr",
		description = "Plant ranarr on herb runs", hidden = true, position = 27)
	default boolean plantRanarr()
	{
		return false;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_DWARF_WEED, name = "Dwarf weed",
		description = "Plant dwarf weed on herb runs", hidden = true, position = 28)
	default boolean plantDwarfWeed()
	{
		return false;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_HARRALANDER, name = "Harralander",
		description = "Plant harralander on herb runs", hidden = true, position = 29)
	default boolean plantHarralander()
	{
		return false;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_TARROMIN, name = "Tarromin",
		description = "Plant tarromin on herb runs", hidden = true, position = 30)
	default boolean plantTarromin()
	{
		return false;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_MARRENTILL, name = "Marrentill",
		description = "Plant marrentill on herb runs", hidden = true, position = 31)
	default boolean plantMarrentill()
	{
		return false;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_GUAM, name = "Guam",
		description = "Plant guam on herb runs", hidden = true, position = 32)
	default boolean plantGuam()
	{
		return false;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_OAK, name = "Oak",
		description = "Plant oak on tree runs", hidden = true, position = 33)
	default boolean plantOak()
	{
		return false;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_WILLOW, name = "Willow",
		description = "Plant willow on tree runs", hidden = true, position = 34)
	default boolean plantWillow()
	{
		return false;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_MAPLE, name = "Maple",
		description = "Plant maple on tree runs", hidden = true, position = 35)
	default boolean plantMaple()
	{
		return false;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_YEW, name = "Yew",
		description = "Plant yew on tree runs", hidden = true, position = 36)
	default boolean plantYew()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_MAGIC, name = "Magic",
		description = "Plant magic on tree runs", hidden = true, position = 37)
	default boolean plantMagic()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_APPLE, name = "Apple",
		description = "Plant apple on fruit tree runs", hidden = true, position = 38)
	default boolean plantApple()
	{
		return false;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_BANANA, name = "Banana",
		description = "Plant banana on fruit tree runs", hidden = true, position = 39)
	default boolean plantBanana()
	{
		return false;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_ORANGE, name = "Orange",
		description = "Plant orange on fruit tree runs", hidden = true, position = 40)
	default boolean plantOrange()
	{
		return false;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_CURRY, name = "Curry",
		description = "Plant curry on fruit tree runs", hidden = true, position = 41)
	default boolean plantCurry()
	{
		return false;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_PINEAPPLE, name = "Pineapple",
		description = "Plant pineapple on fruit tree runs", hidden = true, position = 42)
	default boolean plantPineapple()
	{
		return false;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_PAPAYA, name = "Papaya",
		description = "Plant papaya on fruit tree runs", hidden = true, position = 43)
	default boolean plantPapaya()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_PALM, name = "Palm",
		description = "Plant palm on fruit tree runs", hidden = true, position = 44)
	default boolean plantPalm()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_DRAGONFRUIT, name = "Dragonfruit",
		description = "Plant dragonfruit on fruit tree runs", hidden = true, position = 45)
	default boolean plantDragonfruit()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_TEAK, name = "Teak",
		description = "Plant teak on hardwood runs", hidden = true, position = 46)
	default boolean plantTeak()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_MAHOGANY, name = "Mahogany",
		description = "Plant mahogany on hardwood runs", hidden = true, position = 47)
	default boolean plantMahogany()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_SNAPE_GRASS, name = "Snape grass",
		description = "Plant snape grass in the Catherby allotment", hidden = true,
		position = 48)
	default boolean plantSnapeGrass()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_WATERMELON, name = "Watermelon",
		description = "Plant watermelon in the Catherby allotment", hidden = true,
		position = 49)
	default boolean plantWatermelon()
	{
		return false;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_WHITE_LILY, name = "White lily",
		description = "Plant white lilies in the Catherby flower patch", hidden = true,
		position = 50)
	default boolean plantWhiteLily()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_LIMPWURT, name = "Limpwurt",
		description = "Plant limpwurt in the Hosidius flower patch", hidden = true,
		position = 51)
	default boolean plantLimpwurt()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_POTATO_CACTUS, name = "Potato cactus",
		description = "Plant potato cactus at Al Kharid", hidden = true, position = 52)
	default boolean plantPotatoCactus()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_CACTUS, name = "Cactus",
		description = "Plant cactus at Al Kharid", hidden = true, position = 53)
	default boolean plantCactus()
	{
		return false;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_WHITEBERRIES, name = "Whiteberries",
		description = "Plant whiteberries at the Ardougne Monastery", hidden = true,
		position = 54)
	default boolean plantWhiteberries()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_POISON_IVY, name = "Poison ivy",
		description = "Plant poison ivy at the Ardougne Monastery", hidden = true,
		position = 55)
	default boolean plantPoisonIvy()
	{
		return false;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_MUSHROOMS, name = "Mushrooms",
		description = "Plant bittercaps west of Canifis", hidden = true, position = 56)
	default boolean plantMushrooms()
	{
		return false;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_CALQUAT, name = "Calquat",
		description = "Suggest the calquat run", hidden = true, position = 57)
	default boolean plantCalquat()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_SEAWEED, name = "Seaweed",
		description = "Suggest the seaweed run", hidden = true, position = 58)
	default boolean plantSeaweed()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_HESPORI, name = "Hespori",
		description = "Suggest the Hespori run", hidden = true, position = 59)
	default boolean plantHespori()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_BELLADONNA, name = "Belladonna",
		description = "Plant belladonna at Draynor Manor and Auburnvale", hidden = true,
		position = 69)
	default boolean plantBelladonna()
	{
		return false;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_ATTAS, name = "Attas",
		description = "Let Larry plant Attas in the anima patch", hidden = true, position = 70)
	default boolean plantAttas()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_IASOR, name = "Iasor",
		description = "Let Larry plant Iasor in the anima patch", hidden = true, position = 71)
	default boolean plantIasor()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_KRONOS, name = "Kronos",
		description = "Let Larry plant Kronos in the anima patch", hidden = true, position = 72)
	default boolean plantKronos()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_ELKHORN_CORAL, name = "Elkhorn coral",
		description = "Let Larry plant elkhorn coral in the nurseries", hidden = true,
		position = 73)
	default boolean plantElkhornCoral()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_PILLAR_CORAL, name = "Pillar coral",
		description = "Let Larry plant pillar coral in the nurseries", hidden = true,
		position = 74)
	default boolean plantPillarCoral()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.PLANT_UMBRAL_CORAL, name = "Umbral coral",
		description = "Let Larry plant umbral coral in the nurseries", hidden = true,
		position = 75)
	default boolean plantUmbralCoral()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.PICK_FRUIT_IN_PASSING, name = "Pick fruit in passing",
		description = "Stop at a fruit tree just to pick it", hidden = true, position = 60)
	default boolean pickFruitInPassing()
	{
		return false;
	}

	@ConfigItem(keyName = FarmingKeys.WORK_COMPOST_BINS, name = "Compost bins",
		description = "Fill and empty the compost bins a run passes", hidden = true,
		position = 61)
	default boolean workCompostBins()
	{
		return true;
	}

	// Travel methods, each on while Larry may use it anywhere.

	@ConfigItem(keyName = FarmingKeys.USE_NECKLACE_OF_PASSAGE, name = "Necklace of passage",
		description = "Travel by necklace of passage", hidden = true, position = 62)
	default boolean useNecklaceOfPassage()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.USE_SALVE_GRAVEYARD_TABLETS,
		name = "Salve graveyard tablets",
		description = "Travel by Salve graveyard tablet", hidden = true, position = 63)
	default boolean useSalveGraveyardTablets()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.USE_HOUSE_FAIRY_RING, name = "House fairy ring",
		description = "Travel by the fairy ring in your house", hidden = true, position = 64)
	default boolean useHouseFairyRing()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.USE_QUEST_CAPE, name = "Quest point cape",
		description = "Travel by Quest point cape", hidden = true, position = 65)
	default boolean useQuestCape()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.USE_SLAYER_RING, name = "Slayer ring",
		description = "Travel by slayer ring", hidden = true, position = 66)
	default boolean useSlayerRing()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.USE_DIARY_CAPE, name = "Achievement diary cape",
		description = "Travel by Achievement diary cape", hidden = true, position = 67)
	default boolean useDiaryCape()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.USE_ARDOUGNE_CLOAK, name = "Ardougne cloak",
		description = "Travel by Ardougne cloak", hidden = true, position = 68)
	default boolean useArdougneCloak()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.USE_RING_OF_WEALTH, name = "Ring of wealth",
		description = "Travel by ring of wealth", hidden = true, position = 90)
	default boolean useRingOfWealth()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.USE_GAMES_NECKLACE, name = "Games necklace",
		description = "Travel by games necklace", hidden = true, position = 91)
	default boolean useGamesNecklace()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.USE_COMBAT_BRACELET, name = "Combat bracelet",
		description = "Travel by combat bracelet", hidden = true, position = 92)
	default boolean useCombatBracelet()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.USE_SKILLS_NECKLACE, name = "Skills necklace",
		description = "Travel by skills necklace", hidden = true, position = 93)
	default boolean useSkillsNecklace()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.USE_RING_OF_DUELING, name = "Ring of dueling",
		description = "Travel by ring of dueling", hidden = true, position = 94)
	default boolean useRingOfDueling()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.USE_RING_OF_THE_ELEMENTS, name = "Ring of the elements",
		description = "Travel by ring of the elements", hidden = true, position = 95)
	default boolean useRingOfTheElements()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.USE_DIGSITE_PENDANT, name = "Digsite pendant",
		description = "Travel by digsite pendant", hidden = true, position = 96)
	default boolean useDigsitePendant()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.USE_XERICS_TALISMAN, name = "Xeric's talisman",
		description = "Travel by Xeric's talisman", hidden = true, position = 97)
	default boolean useXericsTalisman()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.USE_KHAREDSTS_MEMOIRS, name = "Kharedst's memoirs",
		description = "Travel by Kharedst's memoirs or the Book of the Dead", hidden = true,
		position = 98)
	default boolean useKharedstsMemoirs()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.USE_PENDANT_OF_ATES, name = "Pendant of Ates",
		description = "Travel by pendant of Ates", hidden = true, position = 99)
	default boolean usePendantOfAtes()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.USE_SAILORS_AMULET, name = "Sailors' amulet",
		description = "Travel by Sailors' amulet", hidden = true, position = 100)
	default boolean useSailorsAmulet()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.USE_TELEPORT_CRYSTAL, name = "Teleport crystal",
		description = "Travel by teleport crystal", hidden = true, position = 101)
	default boolean useTeleportCrystal()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.USE_CHRONICLE, name = "Chronicle",
		description = "Travel by the Chronicle", hidden = true, position = 102)
	default boolean useChronicle()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.USE_QUETZAL_WHISTLE, name = "Quetzal whistle",
		description = "Travel by quetzal whistle", hidden = true, position = 103)
	default boolean useQuetzalWhistle()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.USE_ROYAL_SEED_POD, name = "Royal seed pod",
		description = "Travel by royal seed pod", hidden = true, position = 104)
	default boolean useRoyalSeedPod()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.USE_BASALT, name = "Basalt",
		description = "Travel by icy or stony basalt", hidden = true, position = 105)
	default boolean useBasalt()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.USE_MOKHAIOTL_WAYSTONE, name = "Mokhaiotl waystone",
		description = "Travel by Mokhaiotl waystone", hidden = true, position = 106)
	default boolean useMokhaiotlWaystone()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.USE_EXPLORERS_RING, name = "Explorer's ring",
		description = "Travel by Explorer's ring", hidden = true, position = 107)
	default boolean useExplorersRing()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.USE_SKILL_CAPES, name = "Skill capes",
		description = "Travel by Farming, Fishing or Hunter cape", hidden = true,
		position = 108)
	default boolean useSkillCapes()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.USE_CONSTRUCTION_CAPE, name = "Construction cape",
		description = "Travel by Construction cape", hidden = true, position = 109)
	default boolean useConstructionCape()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.USE_TELEPORT_TABLETS, name = "Teleport tablets",
		description = "Travel by teleport tablet", hidden = true, position = 110)
	default boolean useTeleportTablets()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.USE_SPELLBOOK_SWAP, name = "Spellbook Swap",
		description = "Travel by Spellbook Swap", hidden = true, position = 111)
	default boolean useSpellbookSwap()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.USE_HOUSE_PORTALS, name = "House portals",
		description = "Travel by the portals in your house", hidden = true, position = 112)
	default boolean useHousePortals()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.USE_ECTOPHIAL, name = "Ectophial",
		description = "Travel by ectophial", hidden = true, position = 113)
	default boolean useEctophial()
	{
		return true;
	}

	// Sites, each on while its run visits it.

	@ConfigItem(keyName = FarmingKeys.VISIT_WEISS, name = "Weiss herb patch",
		description = "Visit the Weiss herb patch on herb runs", hidden = true, position = 120)
	default boolean visitWeiss()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.VISIT_TROLLHEIM, name = "Trollheim herb patch",
		description = "Visit the Troll Stronghold herb patch on herb runs", hidden = true,
		position = 121)
	default boolean visitTrollheim()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.VISIT_MORYTANIA, name = "Morytania herb patch",
		description = "Visit the Morytania herb patch on herb runs", hidden = true,
		position = 122)
	default boolean visitMorytania()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.VISIT_FALADOR_HERB, name = "Falador herb patch",
		description = "Visit the Falador herb patch on herb runs", hidden = true,
		position = 123)
	default boolean visitFaladorHerb()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.VISIT_CATHERBY_HERB, name = "Catherby herb patch",
		description = "Visit the Catherby herb patch on herb runs", hidden = true,
		position = 124)
	default boolean visitCatherbyHerb()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.VISIT_ARDOUGNE_HERB, name = "Ardougne herb patch",
		description = "Visit the Ardougne herb patch on herb runs", hidden = true,
		position = 125)
	default boolean visitArdougneHerb()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.VISIT_HOSIDIUS_HERB, name = "Hosidius herb patch",
		description = "Visit the Hosidius herb patch on herb runs", hidden = true,
		position = 126)
	default boolean visitHosidiusHerb()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.VISIT_HARMONY, name = "Harmony herb patch",
		description = "Visit the Harmony Island herb patch on herb runs", hidden = true,
		position = 127)
	default boolean visitHarmony()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.VISIT_ORTUS_FARM, name = "Ortus Farm herb patch",
		description = "Visit the Ortus Farm herb patch on herb runs", hidden = true,
		position = 128)
	default boolean visitOrtusFarm()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.VISIT_FOSSIL_ISLAND, name = "Fossil Island hardwood",
		description = "Visit the Fossil Island hardwood patches", hidden = true,
		position = 129)
	default boolean visitFossilIsland()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.VISIT_LOCUS_OASIS, name = "Locus Oasis hardwood",
		description = "Visit the Locus Oasis hardwood patches", hidden = true, position = 130)
	default boolean visitLocusOasis()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.VISIT_VARROCK_TREE, name = "Varrock tree patch",
		description = "Visit the Varrock tree patch", hidden = true, position = 131)
	default boolean visitVarrockTree()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.VISIT_LUMBRIDGE_TREE, name = "Lumbridge tree patch",
		description = "Visit the Lumbridge tree patch", hidden = true, position = 132)
	default boolean visitLumbridgeTree()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.VISIT_FALADOR_TREE, name = "Falador tree patch",
		description = "Visit the Falador tree patch", hidden = true, position = 133)
	default boolean visitFaladorTree()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.VISIT_TAVERLEY_TREE, name = "Taverley tree patch",
		description = "Visit the Taverley tree patch", hidden = true, position = 134)
	default boolean visitTaverleyTree()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.VISIT_GNOME_STRONGHOLD_TREE,
		name = "Gnome Stronghold tree patch",
		description = "Visit the Gnome Stronghold tree patch", hidden = true, position = 135)
	default boolean visitGnomeStrongholdTree()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.VISIT_NEMUS_RETREAT_TREE, name = "Nemus Retreat tree patch",
		description = "Visit the Nemus Retreat tree patch", hidden = true, position = 136)
	default boolean visitNemusRetreatTree()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.VISIT_BRIMHAVEN_FRUIT, name = "Brimhaven fruit tree patch",
		description = "Visit the Brimhaven fruit tree patch", hidden = true, position = 137)
	default boolean visitBrimhavenFruit()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.VISIT_CATHERBY_FRUIT, name = "Catherby fruit tree patch",
		description = "Visit the Catherby fruit tree patch", hidden = true, position = 138)
	default boolean visitCatherbyFruit()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.VISIT_GNOME_STRONGHOLD_FRUIT,
		name = "Gnome Stronghold fruit tree patch",
		description = "Visit the Gnome Stronghold fruit tree patch", hidden = true,
		position = 139)
	default boolean visitGnomeStrongholdFruit()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.VISIT_TREE_GNOME_VILLAGE_FRUIT,
		name = "Tree Gnome Village fruit tree patch",
		description = "Visit the Tree Gnome Village fruit tree patch", hidden = true,
		position = 140)
	default boolean visitTreeGnomeVillageFruit()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.VISIT_LLETYA_FRUIT, name = "Lletya fruit tree patch",
		description = "Visit the Lletya fruit tree patch", hidden = true, position = 141)
	default boolean visitLletyaFruit()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.VISIT_KASTORI_FRUIT, name = "Kastori fruit tree patch",
		description = "Visit the Kastori fruit tree patch", hidden = true, position = 142)
	default boolean visitKastoriFruit()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.VISIT_TAI_BWO_WANNAI_CALQUAT,
		name = "Tai Bwo Wannai calquat patch",
		description = "Visit the Tai Bwo Wannai calquat patch", hidden = true, position = 143)
	default boolean visitTaiBwoWannaiCalquat()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.VISIT_KASTORI_CALQUAT, name = "Kastori calquat patch",
		description = "Visit the Kastori calquat patch", hidden = true, position = 144)
	default boolean visitKastoriCalquat()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.VISIT_GREAT_CONCH_CALQUAT,
		name = "Great Conch calquat patch",
		description = "Visit the Great Conch calquat patch", hidden = true, position = 145)
	default boolean visitGreatConchCalquat()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.VISIT_GUILD_COMPOST_BIN,
		name = "Farming Guild compost bin",
		description = "Work the Farming Guild's big compost bin", hidden = true, position = 160)
	default boolean visitGuildCompostBin()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.VISIT_FALADOR_COMPOST_BIN, name = "Falador compost bin",
		description = "Work the Falador compost bin", hidden = true, position = 161)
	default boolean visitFaladorCompostBin()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.VISIT_CATHERBY_COMPOST_BIN, name = "Catherby compost bin",
		description = "Work the Catherby compost bin", hidden = true, position = 162)
	default boolean visitCatherbyCompostBin()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.VISIT_ARDOUGNE_COMPOST_BIN, name = "Ardougne compost bin",
		description = "Work the Ardougne compost bin", hidden = true, position = 163)
	default boolean visitArdougneCompostBin()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.VISIT_HOSIDIUS_COMPOST_BIN, name = "Hosidius compost bin",
		description = "Work the Hosidius compost bin", hidden = true, position = 164)
	default boolean visitHosidiusCompostBin()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.VISIT_MORYTANIA_COMPOST_BIN,
		name = "Morytania compost bin",
		description = "Work the Morytania compost bin", hidden = true, position = 165)
	default boolean visitMorytaniaCompostBin()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.VISIT_CIVITAS_COMPOST_BIN, name = "Ortus Farm compost bin",
		description = "Work the Ortus Farm compost bin", hidden = true, position = 166)
	default boolean visitCivitasCompostBin()
	{
		return true;
	}

	// Off in the preset: no herb patch stands beside it, so it is a journey of its own.
	@ConfigItem(keyName = FarmingKeys.VISIT_PRIFDDINAS_COMPOST_BIN,
		name = "Prifddinas compost bin",
		description = "Work the Prifddinas compost bin", hidden = true, position = 167)
	default boolean visitPrifddinasCompostBin()
	{
		return false;
	}

	// What the player says their house holds, each counted only until Larry has seen it.

	@ConfigItem(keyName = FarmingKeys.DECLARE_WEISS_PORTAL, name = "Weiss portal",
		description = "Your house has a Weiss portal", hidden = true, position = 150)
	default boolean declareWeissPortal()
	{
		return false;
	}

	@ConfigItem(keyName = FarmingKeys.DECLARE_TROLL_STRONGHOLD_PORTAL,
		name = "Troll Stronghold portal",
		description = "Your house has a Troll Stronghold portal", hidden = true, position = 151)
	default boolean declareTrollStrongholdPortal()
	{
		return false;
	}

	@ConfigItem(keyName = FarmingKeys.DECLARE_KOUREND_PORTAL, name = "Kourend portal",
		description = "Your house has a Kourend portal", hidden = true, position = 152)
	default boolean declareKourendPortal()
	{
		return false;
	}

	@ConfigItem(keyName = FarmingKeys.DECLARE_FENKENSTRAIN_PORTAL,
		name = "Fenkenstrain's Castle portal",
		description = "Your house has a Fenkenstrain's Castle portal", hidden = true,
		position = 153)
	default boolean declareFenkenstrainPortal()
	{
		return false;
	}

	@ConfigItem(keyName = FarmingKeys.DECLARE_HARMONY_PORTAL, name = "Harmony Island portal",
		description = "Your house has a Harmony Island portal", hidden = true, position = 154)
	default boolean declareHarmonyPortal()
	{
		return false;
	}

	@ConfigItem(keyName = FarmingKeys.DECLARE_WEISS_IN_NEXUS, name = "Weiss in the Nexus",
		description = "Your Portal Nexus is attuned to Weiss", hidden = true, position = 155)
	default boolean declareWeissInNexus()
	{
		return false;
	}

	@ConfigItem(keyName = FarmingKeys.DECLARE_TROLL_STRONGHOLD_IN_NEXUS,
		name = "Troll Stronghold in the Nexus",
		description = "Your Portal Nexus is attuned to the Troll Stronghold", hidden = true,
		position = 156)
	default boolean declareTrollStrongholdInNexus()
	{
		return false;
	}

	@ConfigItem(keyName = FarmingKeys.DECLARE_HARMONY_IN_NEXUS,
		name = "Harmony Island in the Nexus",
		description = "Your Portal Nexus is attuned to Harmony Island", hidden = true,
		position = 157)
	default boolean declareHarmonyInNexus()
	{
		return false;
	}

	@ConfigItem(keyName = FarmingKeys.DECLARE_HOUSE_SPIRIT_TREE, name = "House spirit tree",
		description = "Your house has a spirit tree", hidden = true, position = 158)
	default boolean declareHouseSpiritTree()
	{
		return false;
	}

	@ConfigItem(keyName = FarmingKeys.DECLARE_HOUSE_FAIRY_RING, name = "House fairy ring",
		description = "Your house has a fairy ring", hidden = true, position = 159)
	default boolean declareHouseFairyRing()
	{
		return false;
	}

	// A herb's name as the planner spells it, such as RANARR, or "none".
	@ConfigItem(keyName = FarmingKeys.FAVOURITE_HERB, name = "Favourite herb",
		description = "The herb planted before your other herbs", hidden = true,
		position = 80)
	default String favouriteHerb()
	{
		return FarmingKeys.NO_FAVOURITE;
	}

	@ConfigItem(keyName = FarmingKeys.BEST_SEEDS_EVERYWHERE, name = "Best seeds everywhere",
		description = "Plant the best herb seeds in every patch, not only the diary-boosted ones",
		hidden = true, position = 81)
	default boolean bestSeedsInEveryPatch()
	{
		return false;
	}

	@ConfigItem(keyName = FarmingKeys.HERB_RUN_FIRST, name = "Herb run first",
		description = "Put the herb run first when several runs are ready", hidden = true,
		position = 82)
	default boolean herbRunFirst()
	{
		return false;
	}

	@ConfigItem(keyName = FarmingKeys.WAIT_FOR_EVERY_PATCH, name = "Wait for every patch",
		description = "Wait until every patch on a run is ready", hidden = true,
		position = 83)
	default boolean waitForEveryPatch()
	{
		return false;
	}

	// The compost stock's thresholds, in patches the stock can treat.

	@ConfigItem(keyName = FarmingKeys.MAKE_COMPOST_BELOW, name = "Make compost below",
		description = "Work the compost bins again once your ultracompost falls below this",
		hidden = true, position = 84)
	default int makeCompostBelow()
	{
		return 300;
	}

	@ConfigItem(keyName = FarmingKeys.STOP_COMPOST_AT, name = "Stop compost at",
		description = "Leave the compost bins once your ultracompost reaches this",
		hidden = true, position = 85)
	default int stopCompostAt()
	{
		return 800;
	}

	@ConfigItem(keyName = FarmingKeys.FRUIT_TREES_WAIT_FOR_SAPLINGS,
		name = "Fruit trees wait for saplings",
		description = "Wait until you have a sapling for every fruit tree patch", hidden = true,
		position = 86)
	default boolean fruitTreesWaitForSaplings()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.REPLANT_GROWN_TREES,
		name = "Replant grown trees",
		description = "Chop grown fruit trees for a sapling as good or better", hidden = true,
		position = 168)
	default boolean replantsGrownTrees()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.HERB_RUNS_WAIT_FOR_LEVEL,
		name = "Herb runs from " + Preset.HERB_RUN_LEVEL,
		description = "Leave herb runs until " + Preset.HERB_RUN_LEVEL + " Farming", hidden = true,
		position = 87)
	default boolean herbRunsWaitForLevel()
	{
		return false;
	}

	@ConfigItem(keyName = FarmingKeys.CORAL_WAITS_FOR_ATTAS,
		name = "Coral from " + Preset.CORAL_LEVEL + " with Attas",
		description = "Leave coral until " + Preset.CORAL_LEVEL
			+ " Farming and an Attas plant", hidden = true, position = 89)
	default boolean coralWaitsForAttas()
	{
		return true;
	}

	@ConfigItem(keyName = FarmingKeys.COMPOST_WITH_FERTILE_SOIL,
		name = "Cast Fertile Soil with volcanic ash",
		description = "Cast Fertile Soil with volcanic ash instead of carrying ultracompost",
		hidden = true, position = 88)
	default boolean compostWithFertileSoil()
	{
		return false;
	}
}
