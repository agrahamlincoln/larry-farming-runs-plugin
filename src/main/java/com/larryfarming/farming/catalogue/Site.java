package com.larryfarming.farming.catalogue;

import com.larryfarming.farming.model.*;
import java.util.*;

/**
 * One place a run visits, as the stops table lists it: the family it belongs to, the stop
 * each spellbook works it by, what is true of its patch beyond the route there, and the
 * switch that drops it from its run.
 *
 * <p>A site has one identity whatever the book. Weiss on Arceuus and Weiss on Lunar are one
 * site with two stops, which differ only in their ladder and their terminal. A site outside
 * the herb family is on {@link Spellbook#ANY}.
 *
 * <p>The table's order is the order the sites at one arrival are worked in.
 *
 * <p>{@link PatchAttributes} and {@link Seeds} build their tables from {@link #values()}, so
 * nothing loading the sites may reach either of them, or {@link FarmingCatalogue}, whose
 * policy {@link Seeds} builds. Such a cycle reads the sites half-built.
 */
final class Site {

    private final String key;
    private final RunId family;
    private final Map<Spellbook, RunStop> stops;
    private final PatchAttributes.Entry attributes;
    private final SeedPolicy.DiaryOverride diaryClaim;
    private final String switchKey;
    private final String switchLabel;

    Site(String key, RunId family, Map<Spellbook, RunStop> stops, PatchAttributes.Entry attributes, SeedPolicy.DiaryOverride diaryClaim,
            String switchKey, String switchLabel) {
        if (stops.isEmpty()) {
            throw new IllegalStateException(key + ": no stop on any book");
        }
        this.key = key;
        this.family = family;
        this.stops = Collections.unmodifiableMap(new EnumMap<>(stops));
        this.attributes = attributes;
        this.diaryClaim = diaryClaim;
        this.switchKey = switchKey;
        this.switchLabel = switchLabel;
        // One place: every book's stop works the same patches from the same arrival.
        RunStop first = anyStop();
        for (RunStop stop : this.stops.values()) {
            if (!stop.getPatches().equals(first.getPatches()) || stop.getArrival() != first.getArrival()) {
                throw new IllegalStateException(key + ": the books' stops are two places");
            }
        }
        // Attributes and a claim describe one patch, so the site must work only that one.
        if ((attributes != null || diaryClaim != null) && first.getPatches().size() != 1) {
            throw new IllegalStateException(key + ": per-patch data on a site of " + first.getPatches().size() + " patches");
        }
        if (diaryClaim != null && !diaryClaim.getPatch().equals(first.primaryPatch())) {
            throw new IllegalStateException(key + ": its diary claim names another patch");
        }
    }

    /** Every site, in the table's order. */
    static List<Site> values() {
        return CatalogueLoader.loaded().sites;
    }

    /** The site's key, which never changes. */
    String name() {
        return key;
    }

    @Override
    public String toString() {
        return key;
    }

    RunId family() {
        return family;
    }

    /** The patch this site works, the first where it works several. */
    PatchRef patch() {
        return anyStop().primaryPatch();
    }

    /** Every patch this site works, the same on every book. */
    List<PatchRef> patches() {
        return anyStop().getPatches();
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

    /** The {@code FarmingKeys} key that holds whether the runs visit it, or null for none. */
    String switchKey() {
        return switchKey;
    }

    /** The site named for a player, as its run's settings list it: "Weiss". */
    String switchLabel() {
        return switchLabel;
    }

    /** The books whose run lists this site, in enum order. */
    Set<Spellbook> books() {
        return stops.keySet();
    }

    Arrival arrival() {
        return anyStop().getArrival();
    }

    /**
     * The stop this book's run works the site by: its ladder and terminal for that book.
     *
     * @throws IllegalArgumentException if no run on this book lists the site
     */
    RunStop stop(Spellbook book) {
        RunStop stop = stops.get(book);
        if (stop == null) {
            throw new IllegalArgumentException(key + " is on no " + book + " run");
        }
        return stop;
    }
}
