package com.larryfarming.farming.catalogue;

import com.larryfarming.farming.model.Arrival;
import com.larryfarming.farming.model.PatchRef;
import com.larryfarming.farming.model.RunId;
import com.larryfarming.farming.model.RunStop;
import com.larryfarming.farming.model.SeedPolicy;
import com.larryfarming.farming.model.Spellbook;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The patch registry: every site a run visits, the family it belongs to, the stop each
 * spellbook works it by, and what is true of its patch beyond the route there.
 *
 * <p>One constant per place, even where the herb runs write a ladder per book. They write
 * two because which teleports are cheap depends on the spellbook the player is on. Weiss on
 * Arceuus and Weiss on Lunar are one site with two stops, which differ only in their ladder
 * and their terminal. So a site has one identity and one place in this list whatever the
 * book, and the order a run walks its sites can be keyed by book without forking them.
 *
 * <p>A site outside the herb family is written for {@link Spellbook#ANY}. A herb site
 * lists the books whose run takes it: both, or Lunar alone for Harmony Island.
 *
 * <p>Declaration order is the order the sites at one arrival are worked in.
 *
 * <p>Each stop's ladder is ranked by its rungs' walks here ({@link Ladders}), so the stop a
 * run works is the ranked one.
 *
 * <p>{@link PatchAttributes} and {@link Seeds} build their tables from {@link #values()},
 * so nothing a constant's construction calls may reach either of them, or
 * {@link FarmingCatalogue}, whose policy {@link Seeds} builds. Such a cycle reads the sites
 * half-built.
 */
enum Site {

    // === Herb runs ==============================================================

    // The seven herb patches in the default route's Arceuus order: Weiss, Trollheim,
    // Morytania, Falador, Catherby, Ardougne, Hosidius.
    WEISS(RunId.HERB, perBook(Sites.weissArceuus(), Sites.weissLunar()),
            Sites.weissAttributes()),
    TROLLHEIM(RunId.HERB, perBook(Sites.trollheimArceuus(), Sites.trollheimLunar()),
            Sites.trollheimAttributes()),
    MORYTANIA(RunId.HERB, perBook(Sites.morytaniaArceuus(), Sites.morytaniaLunar())),
    FALADOR_HERB(RunId.HERB, perBook(Sites.faladorHerbArceuus(), Sites.faladorHerbLunar()),
            Sites.faladorHerbAttributes()),
    CATHERBY_HERB(RunId.HERB, bothBooks(Sites.catherbyHerb()),
            Sites.catherbyHerbAttributes()),
    ARDOUGNE_HERB(RunId.HERB, bothBooks(Sites.ardougneHerb())),
    // The default route's Lunar run calls this patch Kourend.
    HOSIDIUS_HERB(RunId.HERB, perBook(Sites.hosidiusHerbArceuus(), Sites.hosidiusHerbLunar()),
            Sites.hosidiusHerbAttributes()),
    HARMONY_HERB(RunId.HERB, lunarOnly(Sites.harmonyHerb()), Sites.harmonyHerbAttributes()),
    SNAPE_GRASS(RunId.HERB, bothBooks(Sites.snapeGrass())),
    WHITE_LILY(RunId.HERB, bothBooks(Sites.whiteLily())),
    LIMPWURT(RunId.HERB, perBook(Sites.limpwurtArceuus(), Sites.limpwurtLunar())),
    POTATO_CACTUS(RunId.HERB, bothBooks(Sites.potatoCactus())),
    WHITEBERRIES(RunId.HERB, bothBooks(Sites.whiteberries())),
    MUSHROOMS(RunId.HERB, bothBooks(Sites.mushrooms())),
    BELLADONNA(RunId.HERB, bothBooks(Sites.belladonna())),
    AUBURNVALE_BELLADONNA(RunId.HERB, bothBooks(Sites.auburnvaleBelladonna())),
    // Every rung but the two that cast the Civitas spell is off the spellbook, so Lunar
    // shares the stop. The spell is Standard, so both drop out on Lunar.
    ORTUS_FARM(RunId.HERB, bothBooks(Sites.ortusFarm())),

    // === Tree runs ==============================================================

    FOSSIL_ISLAND(RunId.HARDWOOD, anyBook(Sites.fossilIsland())),
    LOCUS_OASIS(RunId.HARDWOOD, anyBook(Sites.locusOasis())),

    TAI_BWO_WANNAI_CALQUAT(RunId.CALQUAT, anyBook(Sites.taiBwoWannaiCalquat())),
    KASTORI_CALQUAT(RunId.CALQUAT, anyBook(Sites.kastoriCalquat())),
    GREAT_CONCH_CALQUAT(RunId.CALQUAT, anyBook(Sites.greatConchCalquat())),

    VARROCK_TREE(RunId.REGULAR_TREE, anyBook(Sites.varrockTree())),
    LUMBRIDGE_TREE(RunId.REGULAR_TREE, anyBook(Sites.lumbridgeTree())),
    FALADOR_TREE(RunId.REGULAR_TREE, anyBook(Sites.faladorTree()),
            Sites.faladorTreeDiaryClaim()),
    TAVERLEY_TREE(RunId.REGULAR_TREE, anyBook(Sites.taverleyTree())),
    GRAND_TREE(RunId.REGULAR_TREE, anyBook(Sites.grandTreeRegular())),
    NEMUS_RETREAT_TREE(RunId.REGULAR_TREE, anyBook(Sites.nemusRetreatTree())),

    BRIMHAVEN_FRUIT(RunId.FRUIT_TREE, anyBook(Sites.brimhavenFruit())),
    CATHERBY_FRUIT(RunId.FRUIT_TREE, anyBook(Sites.catherbyFruit())),
    GRAND_TREE_FRUIT(RunId.FRUIT_TREE, anyBook(Sites.grandTreeFruit())),
    TREE_GNOME_VILLAGE_FRUIT(RunId.FRUIT_TREE, anyBook(Sites.treeGnomeVillageFruit())),
    LLETYA_FRUIT(RunId.FRUIT_TREE, anyBook(Sites.lletyaFruit())),
    KASTORI_FRUIT(RunId.FRUIT_TREE, anyBook(Sites.kastoriFruit())),

    // === Special runs ===========================================================

    HESPORI(RunId.HESPORI, anyBook(Sites.hespori())),
    ANIMA(RunId.ANIMA, anyBook(Sites.anima())),
    CORAL(RunId.CORAL, anyBook(Sites.coral())),
    SEAWEED(RunId.SEAWEED, anyBook(Sites.seaweed())),
    GUILD_CONTRACT(RunId.CONTRACT, anyBook(Sites.guildContract()), Sites.guildHerbAttributes()),

    GUILD_COMPOST_BIN(RunId.ULTRACOMPOST, anyBook(Sites.guildCompostBin())),
    CATHERBY_COMPOST_BIN(RunId.ULTRACOMPOST, anyBook(Sites.catherbyCompostBin())),
    ARDOUGNE_COMPOST_BIN(RunId.ULTRACOMPOST, anyBook(Sites.ardougneCompostBin())),
    FALADOR_COMPOST_BIN(RunId.ULTRACOMPOST, anyBook(Sites.faladorCompostBin())),
    HOSIDIUS_COMPOST_BIN(RunId.ULTRACOMPOST, anyBook(Sites.hosidiusCompostBin())),
    MORYTANIA_COMPOST_BIN(RunId.ULTRACOMPOST, anyBook(Sites.morytaniaCompostBin())),
    CIVITAS_COMPOST_BIN(RunId.ULTRACOMPOST, anyBook(Sites.civitasCompostBin())),
    PRIFDDINAS_COMPOST_BIN(RunId.ULTRACOMPOST, anyBook(Sites.prifddinasCompostBin())),

    // The allotments that fill the bins, switched with their bin's site.
    FALADOR_NORTH_WEST_ALLOTMENT(RunId.ULTRACOMPOST, anyBook(Sites.faladorNorthWestAllotment())),
    FALADOR_SOUTH_EAST_ALLOTMENT(RunId.ULTRACOMPOST, anyBook(Sites.faladorSouthEastAllotment())),
    CATHERBY_NORTH_ALLOTMENT(RunId.ULTRACOMPOST, anyBook(Sites.catherbyNorthAllotment())),
    CATHERBY_SOUTH_ALLOTMENT(RunId.ULTRACOMPOST, anyBook(Sites.catherbySouthAllotment())),
    ARDOUGNE_NORTH_ALLOTMENT(RunId.ULTRACOMPOST, anyBook(Sites.ardougneNorthAllotment())),
    ARDOUGNE_SOUTH_ALLOTMENT(RunId.ULTRACOMPOST, anyBook(Sites.ardougneSouthAllotment())),
    HOSIDIUS_NORTH_EAST_ALLOTMENT(RunId.ULTRACOMPOST,
            anyBook(Sites.hosidiusNorthEastAllotment())),
    HOSIDIUS_SOUTH_WEST_ALLOTMENT(RunId.ULTRACOMPOST,
            anyBook(Sites.hosidiusSouthWestAllotment())),
    MORYTANIA_NORTH_WEST_ALLOTMENT(RunId.ULTRACOMPOST,
            anyBook(Sites.morytaniaNorthWestAllotment())),
    MORYTANIA_SOUTH_EAST_ALLOTMENT(RunId.ULTRACOMPOST,
            anyBook(Sites.morytaniaSouthEastAllotment())),
    CIVITAS_NORTH_ALLOTMENT(RunId.ULTRACOMPOST, anyBook(Sites.civitasNorthAllotment())),
    CIVITAS_SOUTH_ALLOTMENT(RunId.ULTRACOMPOST, anyBook(Sites.civitasSouthAllotment())),
    PRIFDDINAS_NORTH_ALLOTMENT(RunId.ULTRACOMPOST, anyBook(Sites.prifddinasNorthAllotment())),
    PRIFDDINAS_SOUTH_ALLOTMENT(RunId.ULTRACOMPOST, anyBook(Sites.prifddinasSouthAllotment()));

    private final RunId family;
    private final Map<Spellbook, RunStop> stops;
    private final PatchAttributes.Entry attributes;
    private final SeedPolicy.DiaryOverride diaryClaim;

    Site(RunId family, Map<Spellbook, RunStop> stops) {
        this(family, stops, null, null);
    }

    Site(RunId family, Map<Spellbook, RunStop> stops, PatchAttributes.Entry attributes) {
        this(family, stops, attributes, null);
    }

    Site(RunId family, Map<Spellbook, RunStop> stops, SeedPolicy.DiaryOverride diaryClaim) {
        this(family, stops, null, diaryClaim);
    }

    Site(RunId family, Map<Spellbook, RunStop> stops, PatchAttributes.Entry attributes,
            SeedPolicy.DiaryOverride diaryClaim) {
        this.family = family;
        Map<Spellbook, RunStop> ranked = new EnumMap<>(Spellbook.class);
        for (Map.Entry<Spellbook, RunStop> book : stops.entrySet()) {
            ranked.put(book.getKey(), Ladders.ranked(book.getValue()));
        }
        this.stops = Collections.unmodifiableMap(ranked);
        this.attributes = attributes;
        this.diaryClaim = diaryClaim;
        // One place: every book's stop works the same patches from the same arrival.
        RunStop first = this.stops.values().iterator().next();
        for (RunStop stop : this.stops.values()) {
            if (!stop.getPatches().equals(first.getPatches())
                    || stop.getArrival() != first.getArrival()) {
                throw new IllegalStateException(name() + ": the books' stops are two places");
            }
        }
        // Attributes and a claim describe one patch, so the site must work only that one.
        if ((attributes != null || diaryClaim != null) && first.getPatches().size() != 1) {
            throw new IllegalStateException(name() + ": per-patch data on a site of "
                    + first.getPatches().size() + " patches");
        }
        if (diaryClaim != null && !diaryClaim.getPatch().equals(first.primaryPatch())) {
            throw new IllegalStateException(name() + ": its diary claim names another patch");
        }
    }

    RunId family() {
        return family;
    }

    /** The patch this site works, the first where it works several. */
    PatchRef patch() {
        return stops.values().iterator().next().primaryPatch();
    }

    /** Every patch this site works, the same on every book. */
    List<PatchRef> patches() {
        return stops.values().iterator().next().getPatches();
    }

    /** A stop of this site's, for what every book's stop shares: its patches and gates. */
    RunStop anyStop() {
        return stops.values().iterator().next();
    }

    /** What makes this site's patch a better home for a seed, or null when nothing does. */
    PatchAttributes.Entry attributes() {
        return attributes;
    }

    /** The crop a diary keeps for this site's patch, or null when none does. */
    SeedPolicy.DiaryOverride diaryClaim() {
        return diaryClaim;
    }

    /** The books whose run lists this site, in enum order. */
    Set<Spellbook> books() {
        return stops.keySet();
    }

    Arrival arrival() {
        return stops.values().iterator().next().getArrival();
    }

    /**
     * The stop this book's run works the site by: its ladder and terminal for that book.
     *
     * @throws IllegalArgumentException if no run on this book lists the site
     */
    RunStop stop(Spellbook book) {
        RunStop stop = stops.get(book);
        if (stop == null) {
            throw new IllegalArgumentException(name() + " is on no " + book + " run");
        }
        return stop;
    }

    private static Map<Spellbook, RunStop> perBook(RunStop arceuus, RunStop lunar) {
        return Map.of(Spellbook.ARCEUUS, arceuus, Spellbook.LUNAR, lunar);
    }

    private static Map<Spellbook, RunStop> bothBooks(RunStop stop) {
        return perBook(stop, stop);
    }

    private static Map<Spellbook, RunStop> lunarOnly(RunStop stop) {
        return Map.of(Spellbook.LUNAR, stop);
    }

    private static Map<Spellbook, RunStop> anyBook(RunStop stop) {
        return Map.of(Spellbook.ANY, stop);
    }
}
