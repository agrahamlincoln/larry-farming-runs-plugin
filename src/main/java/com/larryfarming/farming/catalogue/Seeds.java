package com.larryfarming.farming.catalogue;

import com.larryfarming.farming.model.Preset;
import com.larryfarming.farming.model.SeedPolicy;

/**
 * The herb seed rotation, read from the preset's choices, and the diary claims the sites
 * carry.
 */
final class Seeds {

    private Seeds() {
    }

    static SeedPolicy policy() {
        Preset preset = Preset.DEFAULT;
        SeedPolicy.SeedPolicyBuilder policy = SeedPolicy.builder()
                .herbTierList(preset.getHerbRanking())
                .defaultCutoff(preset.getHerbCutoff())
                .goodSeedsPerRun(preset.getGoodSeedsPerRun());
        if (preset.isGuildHerbPatchReserved()) {
            policy.excludePatch(Patches.FARMING_GUILD_HERB);
        }
        // The one claim is Falador's magic, which the preset can switch off.
        if (preset.isMagicAtFaladorWithEliteDiary()) {
            for (Site site : Site.values()) {
                if (site.diaryClaim() != null) {
                    policy.override(site.diaryClaim());
                }
            }
        }
        return policy.build();
    }
}
