package com.larryfarming.farming.catalogue;

import com.larryfarming.engine.farming.Crop;
import com.larryfarming.engine.farming.PatchType;
import com.larryfarming.farming.model.RunId;
import com.larryfarming.farming.model.Spellbook;
import com.larryfarming.farming.model.UnlockGate;
import com.larryfarming.model.Skill;
import java.util.List;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

/**
 * What a run family holds beside its sites: the gate, the pacing crop, the respawn town and
 * the seeds. The herb family is written once per spellbook; every other family once.
 */
final class Families {

    /** The crop both herb runs are paced by, and the cycle {@link #herbRunsToGrow} counts. */
    static final Crop HERB_PACING_CROP = Crop.RANARR;

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

        /** Null when no crop decides when to go again. */
        Crop pacingCrop;

        /** Null when the run has no preference. */
        String respawnTown;

        /**
         * The patch type whose ticked crops the run plants, or null when the shared seed
         * policy decides. The preset's ticks, not the family, say which crops those are.
         */
        PatchType seedPatchType;
    }

    /** Every run, in the order the catalogue lists them. */
    static final List<Family> ALL = List.of(
            Family.builder()
                    .id(RunId.HERB)
                    .book(Spellbook.ARCEUUS)
                    .unlock(UnlockGate.none())
                    .pacingCrop(HERB_PACING_CROP)
                    // Camelot is a short run from Catherby, which this run reaches no other
                    // cheap way.
                    .respawnTown("Camelot")
                    .build(),
            Family.builder()
                    .id(RunId.HERB)
                    .book(Spellbook.LUNAR)
                    // 96 Magic for the spellbook's teleports, or 92 with a magic potion. Only
                    // the true level gates; the boost is advice.
                    .unlock(UnlockGate.boostableSkill(Skill.MAGIC, 96, 92))
                    .pacingCrop(HERB_PACING_CROP)
                    .build(),
            // The four tree runs. Each tree category keeps its own travel chain rather than
            // one ordered route, so the runs stay apart and the scheduler combines whichever
            // fall due together. A "high level" tree run is not a fifth run: after the
            // Falador elite diary that patch is disease-free and takes the magic saplings,
            // while everywhere else plants yew.
            //
            // The best Farming experience in the game.
            Family.builder()
                    .id(RunId.HARDWOOD)
                    .book(Spellbook.ANY)
                    .unlock(plantsFrom(PatchType.HARDWOOD_TREE))
                    .pacingCrop(Crop.MAHOGANY)
                    .seedPatchType(PatchType.HARDWOOD_TREE)
                    .build(),
            Family.builder()
                    .id(RunId.CALQUAT)
                    .book(Spellbook.ANY)
                    .unlock(plantsFrom(PatchType.CALQUAT))
                    .pacingCrop(Crop.CALQUAT)
                    .seedPatchType(PatchType.CALQUAT)
                    .build(),
            Family.builder()
                    .id(RunId.REGULAR_TREE)
                    .book(Spellbook.ANY)
                    .unlock(plantsFrom(PatchType.TREE))
                    .pacingCrop(Crop.YEW)
                    // Falador respawn lands beside its tree patch, so this run prefers a
                    // different town than the herb run.
                    .respawnTown("Falador")
                    .seedPatchType(PatchType.TREE)
                    .build(),
            Family.builder()
                    .id(RunId.FRUIT_TREE)
                    .book(Spellbook.ANY)
                    .unlock(plantsFrom(PatchType.FRUIT_TREE))
                    .pacingCrop(Crop.PAPAYA)
                    .seedPatchType(PatchType.FRUIT_TREE)
                    .build(),
            // The runs that are not a circuit of patches: hespori and seaweed, the contract
            // loop, and ultracompost production.
            //
            // Hespori is worth doing whenever it comes up: anima seeds, the bottomless
            // bucket, and decent XP.
            Family.builder()
                    .id(RunId.HESPORI)
                    .book(Spellbook.ANY)
                    .unlock(UnlockGate.skill(Skill.FARMING, 65))
                    .pacingCrop(Crop.HESPORI)
                    .build(),
            // One plant at a time, whose effect reaches every patch. The true level is 76;
            // a garden pie boosts from 73.
            Family.builder()
                    .id(RunId.ANIMA)
                    .book(Spellbook.ANY)
                    .unlock(UnlockGate.boostableSkill(Skill.FARMING, 76, 73))
                    // The three plants live alike, eight ticks of 640 minutes.
                    .pacingCrop(Crop.ATTAS)
                    .build(),
            // Elkhorn, the cheapest coral, plants at 28. The route waits for 99 and an Attas
            // plant, which the run's own setting holds it to, not this gate.
            Family.builder()
                    .id(RunId.CORAL)
                    .book(Spellbook.ANY)
                    .unlock(UnlockGate.skill(Skill.FARMING, 28))
                    // The three corals grow alike, four ticks of forty minutes.
                    .pacingCrop(Crop.UMBRAL_CORAL)
                    .build(),
            // Spores plant at 23 Farming. 76 is a recommendation, not a gate: the run waits
            // for an Attas anima plant, which needs 76. 73 is the garden-pie boost floor.
            Family.builder()
                    .id(RunId.SEAWEED)
                    .book(Spellbook.ANY)
                    .unlock(UnlockGate.builder()
                            .skill(Skill.FARMING)
                            .level(23)
                            .recommendedFromLevel(76)
                            .boostedFromLevel(73)
                            .build())
                    .pacingCrop(Crop.SEAWEED)
                    .build(),
            // The contract loop. One stop, because everything happens inside the guild: claim
            // from Guildmaster Jane, work whichever preplanted patch was asked for, replant.
            // The strategy that decides whether to take a contract is data of its own. The
            // true level is 65, boostable from 62 with a garden pie.
            Family.builder()
                    .id(RunId.CONTRACT)
                    .book(Spellbook.ANY)
                    .unlock(UnlockGate.boostableSkill(Skill.FARMING, 65, 62))
                    // No pacing crop: the loop turns over when the player finishes a
                    // contract and asks for another, not when anything finishes growing.
                    .build(),
            // Making ultracompost, folded into a herb run rather than run on its own.
            // Buying supercompostables for the purpose is a waste of time.
            Family.builder()
                    .id(RunId.ULTRACOMPOST)
                    .book(Spellbook.ANY)
                    .unlock(UnlockGate.none())
                    // No pacing crop: this is a production process folded into whatever run
                    // is already happening, not something that comes due on its own.
                    .build());

    /**
     * How many herb runs a crop planted on one needs before it is ready on a later one: its
     * growth time over the herb run's pacing crop's, rounded up.
     */
    static int herbRunsToGrow(Crop crop) {
        long herbSeconds = HERB_PACING_CROP.totalGrowSeconds();
        return (int) ((crop.totalGrowSeconds() + herbSeconds - 1) / herbSeconds);
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
