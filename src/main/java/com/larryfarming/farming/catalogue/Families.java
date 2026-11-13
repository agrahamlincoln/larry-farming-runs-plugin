package com.larryfarming.farming.catalogue;

import com.larryfarming.data.Tables;
import com.larryfarming.engine.farming.Crop;
import com.larryfarming.engine.farming.PatchType;
import com.larryfarming.farming.model.*;
import com.larryfarming.model.Skill;
import java.util.ArrayList;
import java.util.List;

/**
 * What a run family holds beside its sites: the gate, the respawn town and the seeds, one
 * row of the families table per run. The herb family is written once per spellbook; every
 * other family once.
 */
final class Families {

    private Families() {
    }

    /**
     * Every run, in the order the catalogue lists them, as its family data alone: everything
     * a run carries but its stops, which {@link RunAssembler} adds. The preset's ticks, not
     * the family, say which crops a seed patch type plants.
     */
    static final List<FarmingRun> ALL = load();

    private static List<FarmingRun> load() {
        List<FarmingRun> families = new ArrayList<>();
        for (Tables.Row row : Tables.read("/com/larryfarming/families.tsv", 8)) {
            PatchType seeds = row.optionalConstant(PatchType.class, 7);
            Skill skill = row.optionalConstant(Skill.class, 2);
            UnlockGate unlock = seeds != null && skill == null ? plantsFrom(seeds) : UnlockGate.builder().skill(skill).level(row.integer(3))
                            .boostedFromLevel(row.integer(4)).recommendedFromLevel(row.integer(5)).build();
            families.add(FarmingRun.builder().id(row.constant(RunId.class, 0)).spellbook(row.constant(Spellbook.class, 1)).unlock(unlock)
                    .respawnTown(row.optionalText(6)).seedPatchType(seeds).build());
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
