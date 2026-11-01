package com.larryfarming.farming.model;

import com.larryfarming.engine.farming.FarmingWorldMap;
import lombok.*;

/**
 * A reference to one farming patch, by the same (region, transmit varbit) pair that
 * {@link FarmingWorldMap} keys on. The varbit alone is ambiguous: the same transmit
 * varbit carries different patches in different regions.
 *
 * <p>Equality ignores the label, so a hand-written reference matches the catalogue's
 * constant for the same patch.
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PatchRef {

    int regionId;
    int varbitId;

    @EqualsAndHashCode.Exclude
    String label;

    public static PatchRef of(int regionId, int varbitId, String label) {
        return new PatchRef(regionId, varbitId, label);
    }

    /**
     * The patch the registry keys {@code key}, such as {@code WEISS_HERB}, labelled with its
     * location, name and type.
     *
     * @throws IllegalArgumentException if no patch has that key
     */
    public static PatchRef named(String key) {
        FarmingWorldMap.PatchDef def = FarmingWorldMap.byCatalogueKey(key);
        if (def == null) {
            throw new IllegalArgumentException("the registry has no patch " + key);
        }
        String label = def.getPatchName().isEmpty() ? def.getLocation() + " " + def.getType()
                : def.getLocation() + " (" + def.getPatchName() + ") " + def.getType();
        return new PatchRef(def.getRegionId(), def.getVarbitId(), label);
    }

    /** The registry key, in the same {@code region.varbit} form used for persistence. */
    public String configKey() {
        return regionId + "." + varbitId;
    }

    /** The registry entry this points at, or null if it names no known patch. */
    public FarmingWorldMap.PatchDef resolve() {
        return FarmingWorldMap.byConfigKey(configKey());
    }
}
