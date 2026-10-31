package com.larryfarming.farming.catalogue;

import com.larryfarming.engine.farming.FarmingWorldMap;
import com.larryfarming.engine.farming.PatchType;
import com.larryfarming.farming.model.PatchRef;
import java.util.ArrayList;
import java.util.List;

/**
 * Named references to the patches the runs use, resolved from
 * {@link FarmingWorldMap} by human-readable identity rather than written as region
 * and varbit numbers.
 *
 * <p>A pair like {@code 12083.4774} says nothing about which patch it is, and one
 * location often holds patches of different types with different downgrade chains.
 * Resolving by identity also makes a mistyped reference fail at class load.
 *
 * <p>Registry locations and common names differ: the "Trollheim" herb patch is
 * the registry's Troll Stronghold, "Morytania" is Canifis, the "Brimhaven" calquat is at
 * Tai Bwo Wannai, the "Locus Oasis" hardwood is in the Avium Savannah, and "Nemus
 * Retreat" is in Auburnvale.
 */
final class Patches {

    /**
     * Every reference that failed to resolve. Declared first so it exists before the
     * constants below initialise.
     */
    private static final List<String> UNRESOLVED = new ArrayList<>();

    private Patches() {
    }

    // --- Herb patches, in the order the Arceuus run visits them ---------------
    public static final PatchRef WEISS_HERB = find("Weiss", "", PatchType.HERB);
    public static final PatchRef TROLLHEIM_HERB = find("Troll Stronghold", "", PatchType.HERB);
    public static final PatchRef MORYTANIA_HERB = find("Canifis", "", PatchType.HERB);
    public static final PatchRef FALADOR_HERB = find("Falador", "", PatchType.HERB);
    public static final PatchRef CATHERBY_HERB = find("Catherby", "", PatchType.HERB);
    public static final PatchRef ARDOUGNE_HERB = find("Ardougne", "", PatchType.HERB);
    public static final PatchRef HOSIDIUS_HERB = find("Hosidius", "", PatchType.HERB);

    /** The eighth stop of the Lunar run, behind the Elite Morytania diary. */
    public static final PatchRef HARMONY_HERB = find("Harmony", "", PatchType.HERB);

    /** Ortus Farm, woven into herb runs rather than holding a fixed place in them. */
    public static final PatchRef ORTUS_HERB = find("Civitas illa Fortis", "", PatchType.HERB);

    /** Deliberately kept out of herb runs; its yield is reserved for contracts. */
    public static final PatchRef FARMING_GUILD_HERB = find("Farming Guild", "", PatchType.HERB);

    // --- Hardwood ------------------------------------------------------------
    // Fossil Island is three patches reached by one journey, so it is one stop.
    public static final PatchRef FOSSIL_ISLAND_HARDWOOD_EAST =
            find("Fossil Island", "East", PatchType.HARDWOOD_TREE);
    public static final PatchRef FOSSIL_ISLAND_HARDWOOD_MIDDLE =
            find("Fossil Island", "Middle", PatchType.HARDWOOD_TREE);
    public static final PatchRef FOSSIL_ISLAND_HARDWOOD_WEST =
            find("Fossil Island", "West", PatchType.HARDWOOD_TREE);
    public static final PatchRef LOCUS_OASIS_HARDWOOD =
            find("Avium Savannah", "", PatchType.HARDWOOD_TREE);

    // --- Regular trees -------------------------------------------------------
    public static final PatchRef VARROCK_TREE = find("Varrock", "", PatchType.TREE);
    public static final PatchRef LUMBRIDGE_TREE = find("Lumbridge", "", PatchType.TREE);
    public static final PatchRef FALADOR_TREE = find("Falador", "", PatchType.TREE);
    public static final PatchRef TAVERLEY_TREE = find("Taverley", "", PatchType.TREE);
    public static final PatchRef GRAND_TREE_TREE = find("Gnome Stronghold", "", PatchType.TREE);
    public static final PatchRef NEMUS_RETREAT_TREE = find("Auburnvale", "", PatchType.TREE);

    // --- Fruit trees ---------------------------------------------------------
    public static final PatchRef BRIMHAVEN_FRUIT_TREE = find("Brimhaven", "", PatchType.FRUIT_TREE);
    public static final PatchRef CATHERBY_FRUIT_TREE = find("Catherby", "", PatchType.FRUIT_TREE);
    public static final PatchRef GRAND_TREE_FRUIT_TREE =
            find("Gnome Stronghold", "", PatchType.FRUIT_TREE);
    public static final PatchRef TREE_GNOME_VILLAGE_FRUIT_TREE =
            find("Tree Gnome Village", "", PatchType.FRUIT_TREE);
    public static final PatchRef LLETYA_FRUIT_TREE = find("Lletya", "", PatchType.FRUIT_TREE);
    public static final PatchRef KASTORI_FRUIT_TREE = find("Kastori", "", PatchType.FRUIT_TREE);

    // --- Calquat -------------------------------------------------------------
    public static final PatchRef TAI_BWO_WANNAI_CALQUAT =
            find("Tai Bwo Wannai", "", PatchType.CALQUAT);
    public static final PatchRef KASTORI_CALQUAT = find("Kastori", "", PatchType.CALQUAT);
    public static final PatchRef GREAT_CONCH_CALQUAT = find("Great Conch", "", PatchType.CALQUAT);

    // --- Single-patch runs ---------------------------------------------------
    public static final PatchRef HESPORI = find("Farming Guild", "Hespori", PatchType.HESPORI);
    public static final PatchRef ANIMA = find("Farming Guild", "Anima", PatchType.ANIMA);
    public static final PatchRef SEAWEED_NORTH = find("Fossil Island", "North", PatchType.SEAWEED);
    public static final PatchRef SEAWEED_SOUTH = find("Fossil Island", "South", PatchType.SEAWEED);
    public static final PatchRef CORAL_EAST = find("Great Conch", "East", PatchType.CORAL);
    public static final PatchRef CORAL_WEST = find("Great Conch", "West", PatchType.CORAL);

    // --- Woven-in extra crops ------------------------------------------------
    public static final PatchRef AL_KHARID_CACTUS = find("Al Kharid", "", PatchType.CACTUS);
    /** The Monastery bush patch, a separate region from the Ardougne herb cluster. */
    public static final PatchRef ARDOUGNE_MONASTERY_BUSH =
            find("Ardougne", "Monastery", PatchType.BUSH);
    /**
     * The bittercap patch west of Canifis, five tiles from fairy ring CKS. The registry
     * files it under "Morytania", while the herb patch is under "Canifis".
     */
    public static final PatchRef MORT_MYRE_MUSHROOM =
            find("Morytania", "Mushroom", PatchType.MUSHROOM);
    /** The belladonna patch in Draynor Manor's grounds, west of the manor. */
    public static final PatchRef DRAYNOR_MANOR_BELLADONNA =
            find("Draynor Manor", "", PatchType.BELLADONNA);
    /**
     * The belladonna patch at the east end of Auburnvale town, about 85 tiles from the
     * Nemus Retreat tree patch the registry files beside it.
     */
    public static final PatchRef AUBURNVALE_BELLADONNA =
            find("Auburnvale", "", PatchType.BELLADONNA);
    public static final PatchRef CATHERBY_NORTH_ALLOTMENT =
            find("Catherby", "North", PatchType.ALLOTMENT);
    public static final PatchRef CATHERBY_FLOWER = find("Catherby", "", PatchType.FLOWER);
    public static final PatchRef HOSIDIUS_FLOWER = find("Hosidius", "", PatchType.FLOWER);

    // --- Farming Guild, for contracts ----------------------------------------
    public static final PatchRef FARMING_GUILD_TREE = find("Farming Guild", "", PatchType.TREE);
    public static final PatchRef FARMING_GUILD_BUSH = find("Farming Guild", "", PatchType.BUSH);
    public static final PatchRef FARMING_GUILD_FLOWER = find("Farming Guild", "", PatchType.FLOWER);
    public static final PatchRef FARMING_GUILD_ALLOTMENT_NORTH =
            find("Farming Guild", "North", PatchType.ALLOTMENT);
    public static final PatchRef FARMING_GUILD_ALLOTMENT_SOUTH =
            find("Farming Guild", "South", PatchType.ALLOTMENT);
    public static final PatchRef FARMING_GUILD_CACTUS = find("Farming Guild", "", PatchType.CACTUS);
    public static final PatchRef FARMING_GUILD_FRUIT_TREE =
            find("Farming Guild", "", PatchType.FRUIT_TREE);
    public static final PatchRef FARMING_GUILD_CELASTRUS =
            find("Farming Guild", "", PatchType.CELASTRUS);
    public static final PatchRef FARMING_GUILD_REDWOOD =
            find("Farming Guild", "", PatchType.REDWOOD);

    /**
     * The guild patches a contract can name, published through
     * {@link FarmingCatalogue#contractPatches()}.
     *
     */
    static final List<PatchRef> CONTRACT_PATCHES = List.of(
            FARMING_GUILD_HERB,
            FARMING_GUILD_TREE,
            FARMING_GUILD_BUSH,
            FARMING_GUILD_FLOWER,
            FARMING_GUILD_ALLOTMENT_NORTH,
            FARMING_GUILD_ALLOTMENT_SOUTH,
            FARMING_GUILD_CACTUS,
            FARMING_GUILD_FRUIT_TREE,
            FARMING_GUILD_CELASTRUS,
            FARMING_GUILD_REDWOOD);

    // --- Compost bins --------------------------------------------------------
    public static final PatchRef ARDOUGNE_COMPOST = find("Ardougne", "", PatchType.COMPOST);
    public static final PatchRef CATHERBY_COMPOST = find("Catherby", "", PatchType.COMPOST);
    public static final PatchRef FALADOR_COMPOST = find("Falador", "", PatchType.COMPOST);
    public static final PatchRef HOSIDIUS_COMPOST = find("Hosidius", "", PatchType.COMPOST);
    public static final PatchRef MORYTANIA_COMPOST = find("Canifis", "", PatchType.COMPOST);
    public static final PatchRef FARMING_GUILD_BIG_COMPOST =
            find("Farming Guild", "", PatchType.BIG_COMPOST);
    public static final PatchRef CIVITAS_COMPOST =
            find("Civitas illa Fortis", "", PatchType.COMPOST);
    public static final PatchRef PRIFDDINAS_COMPOST = find("Prifddinas", "", PatchType.COMPOST);

    // --- The allotments that feed the bins -------------------------------------
    // Catherby north is declared with the woven-in extras above: it grows snape grass while
    // that crop is on.
    public static final PatchRef FALADOR_NORTH_WEST_ALLOTMENT =
            find("Falador", "North West", PatchType.ALLOTMENT);
    public static final PatchRef FALADOR_SOUTH_EAST_ALLOTMENT =
            find("Falador", "South East", PatchType.ALLOTMENT);
    public static final PatchRef CATHERBY_SOUTH_ALLOTMENT =
            find("Catherby", "South", PatchType.ALLOTMENT);
    public static final PatchRef ARDOUGNE_NORTH_ALLOTMENT =
            find("Ardougne", "North", PatchType.ALLOTMENT);
    public static final PatchRef ARDOUGNE_SOUTH_ALLOTMENT =
            find("Ardougne", "South", PatchType.ALLOTMENT);
    public static final PatchRef HOSIDIUS_NORTH_EAST_ALLOTMENT =
            find("Hosidius", "North East", PatchType.ALLOTMENT);
    public static final PatchRef HOSIDIUS_SOUTH_WEST_ALLOTMENT =
            find("Hosidius", "South West", PatchType.ALLOTMENT);
    public static final PatchRef MORYTANIA_NORTH_WEST_ALLOTMENT =
            find("Canifis", "North West", PatchType.ALLOTMENT);
    public static final PatchRef MORYTANIA_SOUTH_EAST_ALLOTMENT =
            find("Canifis", "South East", PatchType.ALLOTMENT);
    public static final PatchRef CIVITAS_NORTH_ALLOTMENT =
            find("Civitas illa Fortis", "North", PatchType.ALLOTMENT);
    public static final PatchRef CIVITAS_SOUTH_ALLOTMENT =
            find("Civitas illa Fortis", "South", PatchType.ALLOTMENT);
    public static final PatchRef PRIFDDINAS_NORTH_ALLOTMENT =
            find("Prifddinas", "North", PatchType.ALLOTMENT);
    public static final PatchRef PRIFDDINAS_SOUTH_ALLOTMENT =
            find("Prifddinas", "South", PatchType.ALLOTMENT);

    // --- Spirit trees ---------------------------------------------------------
    // Not stops on any run. A grown spirit tree is a travel option, and its patch reports
    // whether it is grown. All five plantable trees are listed; see
    // FarmingCatalogue.patchFor for which ones back a capability.
    public static final PatchRef BRIMHAVEN_SPIRIT_TREE =
            find("Brimhaven", "", PatchType.SPIRIT_TREE);
    public static final PatchRef ETCETERIA_SPIRIT_TREE =
            find("Etceteria", "", PatchType.SPIRIT_TREE);
    public static final PatchRef FARMING_GUILD_SPIRIT_TREE =
            find("Farming Guild", "", PatchType.SPIRIT_TREE);
    public static final PatchRef HOSIDIUS_SPIRIT_TREE =
            find("Hosidius", "", PatchType.SPIRIT_TREE);
    public static final PatchRef PORT_SARIM_SPIRIT_TREE =
            find("Port Sarim", "", PatchType.SPIRIT_TREE);

    /**
     * The single registry entry matching this identity, or an unusable placeholder if
     * there is no such entry or more than one.
     *
     * <p>Failures are collected so every bad entry is reported, then the static
     * initialiser below fails class loading.
     */
    private static PatchRef find(String location, String patchName, PatchType type) {
        String description = describe(location, patchName, type);
        PatchRef found = null;
        for (FarmingWorldMap.PatchDef def : FarmingWorldMap.allPatches()) {
            if (def.getLocation().equals(location)
                    && def.getPatchName().equals(patchName)
                    && def.getType() == type) {
                if (found != null) {
                    UNRESOLVED.add("more than one patch matches " + description);
                    return unusable(description);
                }
                found = PatchRef.of(def.getRegionId(), def.getVarbitId(), description);
            }
        }
        if (found == null) {
            UNRESOLVED.add("no patch matches " + description);
            return unusable(description);
        }
        return found;
    }

    /** A reference that cannot resolve, held only long enough to report it. */
    private static PatchRef unusable(String description) {
        return PatchRef.of(-1, -1, description);
    }

    private static String describe(String location, String patchName, PatchType type) {
        return patchName.isEmpty()
                ? location + " " + type
                : location + " (" + patchName + ") " + type;
    }

    static {
        if (!UNRESOLVED.isEmpty()) {
            throw new IllegalStateException(
                    "the farming catalogue names patches the registry does not have: "
                            + String.join("; ", UNRESOLVED));
        }
    }

}
