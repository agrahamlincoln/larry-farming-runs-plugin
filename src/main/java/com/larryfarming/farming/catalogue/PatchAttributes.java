package com.larryfarming.farming.catalogue;

import com.larryfarming.farming.model.PatchRef;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Value;

/**
 * What makes one patch a better home for a seed than another: a view of the attributes
 * each {@link Site} carries.
 *
 * <p>Records facts and gates only; {@link com.larryfarming.farming.engine.PatchPriority} turns
 * them into a score.
 *
 * <p>Ortus Farm's disease
 * protection is deliberately left out: it depends on Champion rank at the Fortis
 * Colosseum, which nothing detects, and crediting immunity a player lacks is the error
 * this ranking exists to prevent.
 *
 * <p>An absent patch scores as ordinary.
 */
public final class PatchAttributes {

    /** One diary tier and the percentage bonus it grants at this patch. */
    @Value
    public static class BonusTier {
        String diaryArea;
        String diaryTier;
        int percent;
    }

    /** Everything the ranking knows about one patch. */
    @Value
    public static class Entry {
        /** Immune by the game's own design, needing no unlock. */
        boolean alwaysDiseaseFree;
        /** Diary that grants immunity here, or null. */
        String diseaseFreeDiaryArea;
        String diseaseFreeDiaryTier;
        /** Quest that grants immunity here, or null. */
        String diseaseFreeQuest;
        /** Harvest-life bonuses, one per qualifying diary tier. Never null. */
        List<BonusTier> yieldTiers;
        /**
         * Experience bonuses, one per qualifying diary tier. Never null. Separate from
         * {@link #yieldTiers} because extra herbs outrank extra experience.
         */
        List<BonusTier> xpTiers;
        /**
         * Reachable cheaply and reliably. Only a tie-break among patches with neither a
         * bonus nor immunity; not a stand-in for
         * {@link com.larryfarming.farming.detection.TeleportPlanner}.
         */
        boolean easyTravel;
        /**
         * Held back for farming contracts. Sinks the patch below patches with no bonus at
         * all.
         */
        boolean reservedForContracts;
    }

    private static final Map<PatchRef, Entry> BY_PATCH = table();

    private PatchAttributes() {
    }

    /** This patch's attributes, or null when it has none worth ranking on. */
    public static Entry of(PatchRef patch) {
        return patch == null ? null : BY_PATCH.get(patch);
    }

    /**
     * Read off the sites, in {@link Site} order. Nothing in {@link Site}'s own
     * initialisation may call into this class, or this would read the sites half-built.
     */
    private static Map<PatchRef, Entry> table() {
        Map<PatchRef, Entry> entries = new LinkedHashMap<>();
        for (Site site : Site.values()) {
            if (site.attributes() != null
                    && entries.put(site.patch(), site.attributes()) != null) {
                throw new IllegalStateException(site + ": a second site ranks its patch");
            }
        }
        return Collections.unmodifiableMap(entries);
    }

    /** Every patch this table ranks. */
    public static List<PatchRef> ranked() {
        return new ArrayList<>(BY_PATCH.keySet());
    }
}
