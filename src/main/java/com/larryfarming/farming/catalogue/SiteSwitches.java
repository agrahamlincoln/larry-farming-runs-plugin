package com.larryfarming.farming.catalogue;

import com.larryfarming.engine.farming.FarmingWorldMap;
import com.larryfarming.farming.model.FarmingKeys;
import com.larryfarming.farming.model.PatchRef;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Which sites the player may drop from a run, the key each is saved under, and the name the
 * run's settings give it.
 *
 * <p>Every site of the herb, tree, fruit tree, hardwood and calquat runs has a switch, except
 * the herb run's extras: each of those grows a crop with a switch of its own, which already
 * drops it. Each compost bin has a switch too, and the allotments feeding a bin go with it
 * (see {@link FarmingCatalogue#siteKey(com.larryfarming.farming.model.RunStop)}). The runs one
 * setting switches (seaweed, Hespori) and the contract have no site switches.
 */
final class SiteSwitches {

    private SiteSwitches() {
    }

    /** A site's key and the name its run's settings show. */
    static final class Entry {
        final String key;
        final String label;

        Entry(String key, String label) {
            this.key = key;
            this.label = label;
        }
    }

    static final Map<Site, Entry> BY_SITE = bySite();

    /** Every patch a switchable site works, to its site's key. */
    static final Map<PatchRef, String> BY_PATCH = byPatch();

    /**
     * The same, by the world map's patch. Identity-keyed: {@code PatchDef} has no
     * {@code equals}, and every instance comes from the map's own registry.
     */
    static final Map<FarmingWorldMap.PatchDef, String> BY_PATCH_DEF = byPatchDef();

    private static Map<Site, Entry> bySite() {
        Map<Site, Entry> sites = new EnumMap<>(Site.class);
        put(sites, Site.WEISS, FarmingKeys.VISIT_WEISS, "Weiss");
        put(sites, Site.TROLLHEIM, FarmingKeys.VISIT_TROLLHEIM, "Trollheim");
        put(sites, Site.MORYTANIA, FarmingKeys.VISIT_MORYTANIA, "Morytania");
        put(sites, Site.FALADOR_HERB, FarmingKeys.VISIT_FALADOR_HERB, "Falador");
        put(sites, Site.CATHERBY_HERB, FarmingKeys.VISIT_CATHERBY_HERB, "Catherby");
        put(sites, Site.ARDOUGNE_HERB, FarmingKeys.VISIT_ARDOUGNE_HERB, "Ardougne");
        put(sites, Site.HOSIDIUS_HERB, FarmingKeys.VISIT_HOSIDIUS_HERB, "Hosidius");
        put(sites, Site.HARMONY_HERB, FarmingKeys.VISIT_HARMONY, "Harmony");
        put(sites, Site.ORTUS_FARM, FarmingKeys.VISIT_ORTUS_FARM, "Ortus Farm");
        put(sites, Site.FOSSIL_ISLAND, FarmingKeys.VISIT_FOSSIL_ISLAND, "Fossil Island");
        put(sites, Site.LOCUS_OASIS, FarmingKeys.VISIT_LOCUS_OASIS, "Locus Oasis");
        put(sites, Site.TAI_BWO_WANNAI_CALQUAT, FarmingKeys.VISIT_TAI_BWO_WANNAI_CALQUAT,
                "Tai Bwo Wannai");
        put(sites, Site.KASTORI_CALQUAT, FarmingKeys.VISIT_KASTORI_CALQUAT, "Kastori");
        put(sites, Site.GREAT_CONCH_CALQUAT, FarmingKeys.VISIT_GREAT_CONCH_CALQUAT,
                "Great Conch");
        put(sites, Site.VARROCK_TREE, FarmingKeys.VISIT_VARROCK_TREE, "Varrock");
        put(sites, Site.LUMBRIDGE_TREE, FarmingKeys.VISIT_LUMBRIDGE_TREE, "Lumbridge");
        put(sites, Site.FALADOR_TREE, FarmingKeys.VISIT_FALADOR_TREE, "Falador");
        put(sites, Site.TAVERLEY_TREE, FarmingKeys.VISIT_TAVERLEY_TREE, "Taverley");
        // "Stronghold" and "Gnome Village" are short forms: the fruit tree list sets them side
        // by side, and the full names do not fit together at sidebar width.
        put(sites, Site.GRAND_TREE, FarmingKeys.VISIT_GNOME_STRONGHOLD_TREE, "Stronghold");
        put(sites, Site.NEMUS_RETREAT_TREE, FarmingKeys.VISIT_NEMUS_RETREAT_TREE,
                "Nemus Retreat");
        put(sites, Site.BRIMHAVEN_FRUIT, FarmingKeys.VISIT_BRIMHAVEN_FRUIT, "Brimhaven");
        put(sites, Site.CATHERBY_FRUIT, FarmingKeys.VISIT_CATHERBY_FRUIT, "Catherby");
        put(sites, Site.GRAND_TREE_FRUIT, FarmingKeys.VISIT_GNOME_STRONGHOLD_FRUIT,
                "Stronghold");
        put(sites, Site.TREE_GNOME_VILLAGE_FRUIT, FarmingKeys.VISIT_TREE_GNOME_VILLAGE_FRUIT,
                "Gnome Village");
        put(sites, Site.LLETYA_FRUIT, FarmingKeys.VISIT_LLETYA_FRUIT, "Lletya");
        put(sites, Site.KASTORI_FRUIT, FarmingKeys.VISIT_KASTORI_FRUIT, "Kastori");
        put(sites, Site.GUILD_COMPOST_BIN, FarmingKeys.VISIT_GUILD_COMPOST_BIN,
                "Farming Guild");
        put(sites, Site.FALADOR_COMPOST_BIN, FarmingKeys.VISIT_FALADOR_COMPOST_BIN, "Falador");
        put(sites, Site.CATHERBY_COMPOST_BIN, FarmingKeys.VISIT_CATHERBY_COMPOST_BIN,
                "Catherby");
        put(sites, Site.ARDOUGNE_COMPOST_BIN, FarmingKeys.VISIT_ARDOUGNE_COMPOST_BIN,
                "Ardougne");
        put(sites, Site.HOSIDIUS_COMPOST_BIN, FarmingKeys.VISIT_HOSIDIUS_COMPOST_BIN,
                "Hosidius");
        put(sites, Site.MORYTANIA_COMPOST_BIN, FarmingKeys.VISIT_MORYTANIA_COMPOST_BIN,
                "Morytania");
        put(sites, Site.CIVITAS_COMPOST_BIN, FarmingKeys.VISIT_CIVITAS_COMPOST_BIN,
                "Ortus Farm");
        put(sites, Site.PRIFDDINAS_COMPOST_BIN, FarmingKeys.VISIT_PRIFDDINAS_COMPOST_BIN,
                "Prifddinas");
        return Collections.unmodifiableMap(sites);
    }

    private static void put(Map<Site, Entry> sites, Site site, String key, String label) {
        sites.put(site, new Entry(key, label));
    }

    private static Map<PatchRef, String> byPatch() {
        Map<PatchRef, String> patches = new HashMap<>();
        for (Map.Entry<Site, Entry> site : BY_SITE.entrySet()) {
            for (PatchRef patch : site.getKey().patches()) {
                patches.put(patch, site.getValue().key);
            }
        }
        return Collections.unmodifiableMap(patches);
    }

    private static Map<FarmingWorldMap.PatchDef, String> byPatchDef() {
        Map<FarmingWorldMap.PatchDef, String> defs = new IdentityHashMap<>();
        for (Map.Entry<PatchRef, String> patch : BY_PATCH.entrySet()) {
            FarmingWorldMap.PatchDef def = patch.getKey().resolve();
            if (def != null) {
                defs.put(def, patch.getValue());
            }
        }
        return Collections.unmodifiableMap(defs);
    }
}
