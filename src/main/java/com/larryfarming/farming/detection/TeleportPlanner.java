package com.larryfarming.farming.detection;

import com.larryfarming.engine.ItemCounts;
import com.larryfarming.farming.catalogue.Teleports;
import com.larryfarming.farming.model.Destination;
import com.larryfarming.farming.model.Rune;
import com.larryfarming.farming.model.Spellbook;
import com.larryfarming.farming.model.TeleportMeans;
import com.larryfarming.farming.model.TravelMethod;
import com.larryfarming.farming.model.Zone;
import com.larryfarming.model.PlayerState;
import com.larryfarming.model.QuestStatus;
import com.larryfarming.model.Skill;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import lombok.Value;

/**
 * Decides how this player, on the book they are actually on, should reach a place.
 *
 * <p>Ranking:
 * <ol>
 *   <li>Already inside the destination's {@link com.larryfarming.farming.model.Zone}: free, and
 *       nothing for the bag to carry.</li>
 *   <li>A castable spell on the open book. It spends runes the player carries anyway and
 *       saves the tablet for a run on the wrong book.</li>
 *   <li>A tablet, which works from any book.</li>
 * </ol>
 * {@link #best} never ranks a spell on another book: changing book is a decision for the
 * whole run, and the run already names its book.
 *
 * <p>Spellbook Swap is the one way to borrow such a spell for a single cast, and only a rung
 * that names it asks: {@link #bySwap}. The rung's place in its chain ranks the swap against
 * the tablet and the house, so this class never weighs one against the others.
 */
public final class TeleportPlanner {

    private TeleportPlanner() {
    }

    /** How to get there, how sure we are, and what to tell the player about it. */
    @Value
    public static class Choice {
        /** The way to go, or null when there is none. */
        TeleportMeans means;
        CapabilityState state;
        String because;
    }

    /**
     * The best way to reach this place.
     *
     * @param onBook the book the player is on, which decides which spells are castable
     *               without a swap
     * @param readsPosition whether the player's own position may answer this. False for a
     *               stop further down the trip, which the player will have left this place
     *               long before reaching — see {@link CapabilityResolver#blindToPosition()}
     */
    public static Choice best(Destination destination, PlayerState state, Spellbook onBook,
            boolean readsPosition) {
        return best(destination, state, onBook, readsPosition, true);
    }

    /**
     * The same, with the player's say over tablets.
     *
     * <p>{@link CapabilityResolver} passes the standard book until the login read of the
     * spellbook varbit, so a refused tablet answers LACK in that window even for a player on
     * the Arceuus book.
     *
     * @param tablets false when the player refuses this place's tablet. The tablet then
     *               never answers, owned or not, and with no spell on the open book the
     *               place is out of reach whatever the bank holds.
     */
    public static Choice best(Destination destination, PlayerState state, Spellbook onBook,
            boolean readsPosition, boolean tablets) {
        return best(destination, state, onBook, readsPosition,
                tablets ? null : TravelMethod.TELEPORT_TABLETS);
    }

    /**
     * The same, naming the switch that refuses this place's tablet, so a shortfall can say
     * which one the player turned off.
     *
     * @param tabletsOff the switch refusing this place's tablet, or null while it is allowed
     */
    public static Choice best(Destination destination, PlayerState state, Spellbook onBook,
            boolean readsPosition, TravelMethod tabletsOff) {
        boolean tablets = tabletsOff == null;
        if (state == null) {
            return new Choice(null, CapabilityState.UNKNOWN,
                    "not sure how you would reach " + destination.getDisplayName());
        }

        // Already there: no means, so the bag asks for no runes or tablet. This removes a
        // consumable from the bag, so it must fire only on a position the game reported;
        // Zone.covers reads an unknown region as a no.
        Zone zone = destination.getZone();
        if (readsPosition && zone != null && zone.covers(state.getRegionId())) {
            return new Choice(null, CapabilityState.HAVE,
                    "you are already at " + zone.getDisplayName());
        }

        TeleportMeans unconfirmed = null;
        String unconfirmedBecause = null;
        for (TeleportMeans means : Teleports.to(destination)) {
            if (!means.isSpell() || means.getSpellbook() != onBook) {
                continue;
            }
            Choice cast = castable(means, state);
            if (cast.getState() == CapabilityState.HAVE) {
                return cast;
            }
            if (cast.getState() == CapabilityState.UNKNOWN && unconfirmed == null) {
                unconfirmed = means;
                unconfirmedBecause = cast.getBecause();
            }
        }

        for (TeleportMeans means : Teleports.to(destination)) {
            if (!tablets || !means.isTablet() || !owns(means, state)) {
                continue;
            }
            // A tablet behind a quest breaks only once the quest is finished.
            Choice locked = locked(means, state);
            if (locked == null) {
                return new Choice(means, CapabilityState.HAVE,
                        // Tablets lead with a place, so the name keeps its capital.
                        "you have a " + means.getDisplayName());
            }
            if (locked.getState() == CapabilityState.UNKNOWN && unconfirmed == null) {
                unconfirmed = means;
                unconfirmedBecause = locked.getBecause();
            }
        }

        if (unconfirmed != null) {
            return new Choice(unconfirmed, CapabilityState.UNKNOWN, unconfirmedBecause);
        }
        // Checked before the unseen bank: with the tablet refused and no spell on this
        // book, nothing the bank could hold would change the answer.
        if (!tablets && !hasSpellOn(destination, onBook)) {
            return new Choice(null, CapabilityState.LACK,
                    "you switched off tablets to " + destination.getDisplayName());
        }
        // An unseen bank cannot prove the player is out of tablets or runes, so answer
        // UNKNOWN like every item rule. LACK here drops the stop's patches from the run.
        if (state.getBankLastSeen() == null) {
            return new Choice(null, CapabilityState.UNKNOWN,
                    "open your bank to check your teleports to "
                            + destination.getDisplayName());
        }
        return new Choice(null, CapabilityState.LACK,
                shortfall(destination, state, onBook, tabletsOff));
    }

    private static boolean owns(TeleportMeans tablet, PlayerState state) {
        return ItemCounts.countOwned(state, tablet.getItemId(), null) > 0;
    }

    private static boolean hasSpellOn(Destination destination, Spellbook onBook) {
        for (TeleportMeans means : Teleports.to(destination)) {
            if (means.isSpell() && means.getSpellbook() == onBook) {
                return true;
            }
        }
        return false;
    }

    /**
     * The best way to reach this place by Spellbook Swap and its spell from another book,
     * for a rung written with {@code TravelOption.swapTo}.
     *
     * <p>Both casts must be open to the player: the swap's 96 Magic and Dream Mentor, and the
     * second spell's own level and quest. Levels are true levels, as everywhere here. The
     * runes are both casts' together; banked runes count. With the bank unseen, a shortfall
     * reads UNKNOWN as it does in {@link #best}, but the means stays named so the bag still
     * lists every rune.
     *
     * @param onBook the book the swap is cast from, which must be the swap's own
     */
    public static Choice bySwap(Destination destination, PlayerState state, Spellbook onBook,
            boolean readsPosition) {
        if (state == null) {
            return new Choice(null, CapabilityState.UNKNOWN,
                    "not sure how you would reach " + destination.getDisplayName());
        }
        Zone zone = destination.getZone();
        if (readsPosition && zone != null && zone.covers(state.getRegionId())) {
            return new Choice(null, CapabilityState.HAVE,
                    "you are already at " + zone.getDisplayName());
        }
        TeleportMeans swap = Teleports.bySwap(destination);
        if (swap == null) {
            return new Choice(null, CapabilityState.LACK, "no spell on another spellbook reaches "
                    + destination.getDisplayName());
        }
        if (onBook != swap.getSpellbook()) {
            return new Choice(null, CapabilityState.LACK,
                    Teleports.SPELLBOOK_SWAP.getDisplayName() + " is on the "
                            + swap.getSpellbook().getDisplayName() + " spellbook");
        }
        Choice locked = locked(swap, state);
        if (locked == null) {
            locked = locked(swap.getSwappedSpell(), state);
        }
        if (locked != null) {
            return new Choice(swap, locked.getState(), locked.getBecause());
        }
        Rune missing = shortOf(swap, state);
        if (missing == null) {
            return new Choice(swap, CapabilityState.HAVE, "you can cast " + swap.getDisplayName());
        }
        return state.getBankLastSeen() == null
                ? new Choice(swap, CapabilityState.UNKNOWN,
                        "open your bank to check your runes for " + swap.getDisplayName())
                : new Choice(swap, CapabilityState.LACK, shortReason(missing, swap));
    }

    /**
     * Whether this spell can be cast, runes included. Banked runes count, since this is read
     * before setting off.
     */
    private static Choice castable(TeleportMeans means, PlayerState state) {
        Choice locked = locked(means, state);
        if (locked != null) {
            return locked;
        }
        Rune missing = shortOf(means, state);
        if (missing != null) {
            return new Choice(means, CapabilityState.LACK, shortReason(missing, means));
        }
        return new Choice(means, CapabilityState.HAVE,
                "you can cast " + means.getDisplayName());
    }

    /**
     * Why the player cannot use this spell or tablet whatever runes they hold, or null when
     * its level and quest are met. A tablet asks for no level. An unobserved quest answers
     * UNKNOWN.
     */
    private static Choice locked(TeleportMeans means, PlayerState state) {
        int magic = state.getSkillLevel(Skill.MAGIC);
        if (magic < means.getMagicLevel()) {
            return new Choice(means, CapabilityState.LACK,
                    means.getDisplayName() + " needs " + means.getMagicLevel()
                            + " Magic and you have " + magic);
        }
        if (means.getQuestName() != null) {
            QuestStatus status = state.getQuests().get(means.getQuestName());
            if (status == null) {
                return new Choice(means, CapabilityState.UNKNOWN,
                        "not sure whether you have finished " + means.getQuestName()
                                + ", which " + means.getDisplayName() + " needs");
            }
            if (status != QuestStatus.COMPLETED) {
                return new Choice(means, CapabilityState.LACK,
                        means.getDisplayName() + " needs " + means.getQuestName());
            }
        }
        return null;
    }

    /** The first rune the player owns too few of for one cast, or null when none is short. */
    private static Rune shortOf(TeleportMeans means, PlayerState state) {
        for (Map.Entry<Rune, Integer> cost : means.getRunes().entrySet()) {
            Rune rune = cost.getKey();
            if (ItemCounts.countOwned(state, rune.getItemId(), null) < cost.getValue()) {
                return rune;
            }
        }
        return null;
    }

    /**
     * Whether a spell on the open book reaches this place and only runes keep the player from
     * casting it: its level and quest are met. False with no state to read.
     */
    public static boolean wantsOnlyRunes(Destination destination, PlayerState state,
            Spellbook onBook) {
        if (state == null) {
            return false;
        }
        for (TeleportMeans means : Teleports.to(destination)) {
            if (means.isSpell() && means.getSpellbook() == onBook
                    && locked(means, state) == null && shortOf(means, state) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether some way to this place waits on no quest the player is seen short of: a
     * tablet they allow, or a spell on the open book whatever its Magic level. True with no
     * state to read.
     *
     * @param tablets false when the player refuses this place's tablet
     */
    public static boolean isOpenToGet(Destination destination, PlayerState state,
            Spellbook onBook, boolean tablets) {
        if (state == null) {
            return true;
        }
        for (TeleportMeans means : Teleports.to(destination)) {
            boolean offered = means.isTablet()
                    ? tablets
                    : means.isSpell() && means.getSpellbook() == onBook;
            if (offered && !questBarred(means, state)) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@link #isOpenToGet} for a Spellbook Swap rung: the swap is cast from the open book,
     * and neither it nor the spell it borrows waits on an unfinished quest.
     */
    public static boolean isOpenBySwap(Destination destination, PlayerState state,
            Spellbook onBook) {
        TeleportMeans swap = Teleports.bySwap(destination);
        if (swap == null || swap.getSpellbook() != onBook) {
            return false;
        }
        return state == null
                || (!questBarred(swap, state) && !questBarred(swap.getSwappedSpell(), state));
    }

    /** True when the player is seen short of this spell's or tablet's quest. */
    private static boolean questBarred(TeleportMeans means, PlayerState state) {
        if (means.getQuestName() == null) {
            return false;
        }
        QuestStatus status = state.getQuests().get(means.getQuestName());
        return status != null && status != QuestStatus.COMPLETED;
    }

    private static String shortReason(Rune rune, TeleportMeans means) {
        return "you are short of " + rune.getDisplayName().toLowerCase() + "s for "
                + means.getDisplayName();
    }

    /**
     * Why nothing works, one reason per means. An off-book spell is reported as being on
     * another spellbook, never as a missing requirement the player may already have.
     */
    private static String shortfall(Destination destination, PlayerState state,
            Spellbook onBook, TravelMethod tabletsOff) {
        // A set: one switch can refuse several tablets, and its reason reads once.
        Set<String> reasons = new LinkedHashSet<>();
        for (TeleportMeans means : Teleports.to(destination)) {
            if (means.isTablet()) {
                Choice locked = tabletsOff == null && owns(means, state)
                        ? locked(means, state) : null;
                reasons.add(tabletsOff != null
                        ? tabletsOff.switchedOffReason()
                        : locked != null
                                ? locked.getBecause()
                                // Tablets lead with a place, so the name keeps its capital.
                                : "you have no " + means.getDisplayName());
            } else if (means.getSpellbook() != onBook) {
                reasons.add(means.getDisplayName() + " is on the "
                        + means.getSpellbook().getDisplayName() + " spellbook");
            } else {
                reasons.add(castable(means, state).getBecause());
            }
        }
        if (reasons.isEmpty()) {
            return "nothing you have reaches " + destination.getDisplayName();
        }
        return String.join(", and ", reasons);
    }
}
