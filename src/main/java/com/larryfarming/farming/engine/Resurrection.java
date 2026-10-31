package com.larryfarming.farming.engine;

import com.larryfarming.engine.ItemCounts;
import com.larryfarming.engine.farming.FarmingWorldMap;
import com.larryfarming.engine.farming.PatchType;
import com.larryfarming.farming.catalogue.Teleports;
import com.larryfarming.farming.detection.CapabilitySources;
import com.larryfarming.farming.model.Capability;
import com.larryfarming.farming.model.PatchRef;
import com.larryfarming.farming.model.Rune;
import com.larryfarming.model.PlayerState;
import com.larryfarming.model.Skill;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Whether the player brings a dead crop back with the Arceuus Resurrect Crops spell rather
 * than digging it up, and how many casts the bag carries runes for.
 *
 * <p>The spell revives a dead patch: half the time at 78 Magic, three times in four at 99.
 * A failed cast clears the patch, so the player plants it as they would after digging, and
 * the seed stays packed either way. A crop revived once cannot be revived again; the game
 * says so, and Larry, who cannot see it, still offers the cast.
 *
 * <p>A death shows only once the player reaches the patch, so the bag carries runes for one
 * cast per herb patch on the trip, from the runes left once the teleports have theirs. The
 * row offers the cast at a herb patch, which the bag packed for, and at any other dead crop
 * only while the player already carries a cast's runes.
 *
 * <p>Not modelled: rune stacks taking bag slots for a player without a rune pouch, as for
 * Cure Plant; and a trip split by a bank, whose stretches each count every rune owned.
 */
final class Resurrection {

    /** The Magic level Resurrect Crops needs. */
    static final int MAGIC_LEVEL = 78;

    private Resurrection() {
    }

    /**
     * Whether the player can cast it now: on the Arceuus spellbook, with the Magic level and
     * the runes for at least one cast. The book comes from the varbit, as for Cure Plant:
     * the spell is cast from the book the player has open. The real Magic level, so an
     * unread level or a boost answers no.
     */
    static boolean offered(PlayerState state) {
        return state != null && onArceuus(state)
                && state.getSkillLevel(Skill.MAGIC) >= MAGIC_LEVEL
                && castsIn(state, false) > 0;
    }

    /**
     * Whether the row offers the cast on a dead crop in this kind of patch: always at a herb
     * patch, whose runes the bag carries, and elsewhere only with a cast's runes already on
     * the player.
     */
    static boolean offeredAt(PatchType type, PlayerState state) {
        return offered(state) && revives(type)
                && (type == PatchType.HERB || castsIn(state, true) > 0);
    }

    /**
     * How many casts the bag wants: one per herb patch these stops visit, each counted once.
     * Zero when the spell is not {@linkplain #offered offered}. The runes go into the bag
     * only as far as {@link #castsLeftAfter} allows.
     */
    static int castsToPack(List<StopPlan> plans, PlayerState state) {
        if (!offered(state)) {
            return 0;
        }
        Set<PatchRef> herbs = new HashSet<>();
        for (StopPlan plan : plans) {
            if (!plan.isVisit()) {
                continue;
            }
            for (PatchRef ref : plan.getStop().getPatches()) {
                FarmingWorldMap.PatchDef def = ref.resolve();
                if (def != null && def.getType() == PatchType.HERB) {
                    herbs.add(ref);
                }
            }
        }
        return herbs.size();
    }

    /**
     * How many whole casts the player's runes still make once the bag's other rune lines
     * have taken theirs, so adding the casts never asks for more of a rune than they own.
     */
    static int castsLeftAfter(Map<Rune, Integer> alreadyNeeded, PlayerState state) {
        int casts = Integer.MAX_VALUE;
        for (Map.Entry<Rune, Integer> cost : runesPerCast().entrySet()) {
            int spare = ItemCounts.countOwned(state, cost.getKey().getItemId(), null)
                    - alreadyNeeded.getOrDefault(cost.getKey(), 0);
            casts = Math.min(casts, Math.max(0, spare) / cost.getValue());
        }
        return casts == Integer.MAX_VALUE ? 0 : casts;
    }

    /** Whether the spell works on a dead crop in this kind of patch: all but two do. */
    static boolean revives(PatchType type) {
        return type != PatchType.HESPORI && type != PatchType.ANIMA;
    }

    /** The runes one cast takes. */
    static Map<Rune, Integer> runesPerCast() {
        return Teleports.runesFor(Capability.RESURRECT_CROP);
    }

    /**
     * How many whole casts the player's runes make: those on them, the pouch included, or
     * every rune they own, the bank included.
     */
    private static int castsIn(PlayerState state, boolean carriedOnly) {
        int casts = Integer.MAX_VALUE;
        for (Map.Entry<Rune, Integer> cost : runesPerCast().entrySet()) {
            int itemId = cost.getKey().getItemId();
            int held = carriedOnly ? ItemCounts.countCarried(state, itemId, null)
                    : ItemCounts.countOwned(state, itemId, null);
            casts = Math.min(casts, held / cost.getValue());
        }
        return casts == Integer.MAX_VALUE ? 0 : casts;
    }

    private static boolean onArceuus(PlayerState state) {
        Map<Integer, Integer> varbits = state.getVarbits();
        Integer book = varbits == null ? null : varbits.get(CapabilitySources.SPELLBOOK_VARBIT);
        return book != null && book == CapabilitySources.ARCEUUS_SPELLBOOK;
    }
}
