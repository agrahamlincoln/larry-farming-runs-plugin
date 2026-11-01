package com.larryfarming.farming.catalogue;

import com.larryfarming.data.Tables;
import com.larryfarming.engine.farming.Crop;
import com.larryfarming.engine.farming.PatchType;
import com.larryfarming.farming.model.*;
import com.larryfarming.model.Skill;
import java.util.ArrayList;
import java.util.List;
import lombok.*;

/**
 * What a run family holds beside its sites: the gate, the respawn town and the seeds, one
 * row of the families table per run. The herb family is written once per spellbook; every
 * other family once.
 */
final class Families {

    private Families() {
    }

    /** One run's worth of family data: everything a run carries but its stops. */
    @Value
    @Builder
    static class Family {
        @NonNull
        RunId id;

        @NonNull
        Spellbook book;

        @NonNull
        UnlockGate unlock;

        /** Null when the run has no preference. */
        String respawnTown;

        /**
         * The patch type whose ticked crops the run plants, or null when the shared seed
         * policy decides. The preset's ticks, not the family, say which crops those are.
         */
        PatchType seedPatchType;
    }

    /** Every run, in the order the catalogue lists them. */
    static final List<Family> ALL = load();

    private static List<Family> load() {
        List<Family> families = new ArrayList<>();
        for (Tables.Row row : Tables.read("/com/larryfarming/families.tsv", 8)) {
            PatchType seeds = row.optionalConstant(PatchType.class, 7);
            Skill skill = row.optionalConstant(Skill.class, 2);
            UnlockGate unlock = seeds != null && skill == null ? plantsFrom(seeds) : UnlockGate.builder().skill(skill).level(row.integer(3))
                            .boostedFromLevel(row.integer(4)).recommendedFromLevel(row.integer(5)).build();
            String town = row.text(6);
            families.add(Family.builder().id(row.constant(RunId.class, 0)).book(row.constant(Spellbook.class, 1)).unlock(unlock)
                    .respawnTown(town.isEmpty() ? null : town).seedPatchType(seeds).build());
        }
        return List.copyOf(families);
    }

    /**
     * The level at which the game first lets a crop of this family into its patch: the
     * cheapest crop's. The scheduler raises it to the cheapest crop the player has ticked.
     */
    static UnlockGate plantsFrom(PatchType family) {
        List<Crop> crops = CropData.plantableIn(family);
        // Best first, so the cheapest is last.
        Crop cheapest = crops.get(crops.size() - 1);
        return UnlockGate.skill(Skill.FARMING, CropData.forCrop(cheapest).getFarmingLevel());
    }
}
