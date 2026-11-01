package com.larryfarming.farming.detection;

import com.larryfarming.engine.ItemCounts;
import com.larryfarming.farming.catalogue.CarryItems;
import com.larryfarming.farming.engine.DailyTeleportLedger;
import com.larryfarming.farming.model.*;
import com.larryfarming.model.PlayerState;
import java.util.*;

/**
 * Answers what a player can currently do, both for single capabilities and for the
 * boolean expressions travel rungs are written as.
 *
 * <p>The game is the source, and the player is never asked; they may only declare what
 * their house holds before Larry has seen it. What cannot be read yet is either taken to be
 * absent (a house interior neither seen nor declared) or left
 * {@link CapabilityState#UNKNOWN}. An unknown route is still offered, but never preferred
 * over a certain one.
 */
public final class CapabilityResolver {

    /** Decides which teleport spells are castable without a swap. Defaults to Standard. */
    private final Spellbook onBook;

    /**
     * Today's spent rationed teleports, or null to skip allowance checks entirely. See
     * {@link #applyDailyLimit}.
     */
    private final DailyTeleportLedger dailyLedger;

    /** The instant {@link #dailyLedger} is read against, so its day rolls over correctly. */
    private final long nowEpochSec;

    /** Never null: a caller with nothing to offer gets {@link PatchSight#blind()}. */
    private final PatchSight sight;

    /** Never null: a caller with nothing to offer gets {@link HouseSight#unseen()}. */
    private final HouseSight house;

    /**
     * Whether the player's own position may answer a question. True for the stop they are
     * travelling to and false for every later stop, which they will have left by then.
     * Governs both {@link #teleport}'s already-there check and {@code STANDING_IN}, which
     * must switch together. See {@link #blindToPosition()}.
     */
    private final boolean readsPosition;

    /** Travel methods the player has switched off. See {@link #switchingOff}. */
    private final Set<TravelMethod> switchedOff;

    /** House items the player has declared built. See {@link #declaringHouse}. */
    private final Set<Capability> houseDeclared;

    /**
     * The teleport already spent on the way to the next stop. Read only with
     * {@link #readsPosition}: it pays for the stop it was cast for, never a later one.
     */
    private final TravelTaken taken;

    /** The book this resolver plans for: the one the trip will be walked on. */
    public Spellbook getBook() {
        return onBook;
    }

    /**
     * A resolver for this book, weighing daily teleport allowances (a null ledger never finds
     * one exhausted), what is growing in the player's patches, and what their house was last
     * seen to hold. Without a sight, patch capabilities such as a grown spirit tree read as
     * absent; without a house, every house capability does.
     */
    public CapabilityResolver(Spellbook onBook, DailyTeleportLedger dailyLedger, long nowEpochSec, PatchSight sight, HouseSight house) {
        this(onBook, dailyLedger, nowEpochSec, sight, house, true, Collections.<TravelMethod>emptySet(), Collections.<Capability>emptySet(),
                TravelTaken.NONE);
    }

    private CapabilityResolver(Spellbook onBook, DailyTeleportLedger dailyLedger,
            long nowEpochSec, PatchSight sight, HouseSight house, boolean readsPosition,
            Set<TravelMethod> switchedOff, Set<Capability> houseDeclared, TravelTaken taken) {
        this.onBook = onBook != null ? onBook : Spellbook.STANDARD;
        this.dailyLedger = dailyLedger;
        this.nowEpochSec = nowEpochSec;
        this.sight = sight != null ? sight : PatchSight.blind();
        this.house = house != null ? house : HouseSight.unseen();
        this.readsPosition = readsPosition;
        this.switchedOff = switchedOff;
        this.houseDeclared = houseDeclared;
        this.taken = taken;
    }

    /**
     * The same resolver, answering as though it did not know where the player was. Use it
     * for stops further down the trip, where the player's position will have changed.
     * Derived from this resolver so the two cannot drift in anything else they carry.
     */
    public CapabilityResolver blindToPosition() {
        return readsPosition ? new CapabilityResolver(onBook, dailyLedger, nowEpochSec, sight, house, false,
                        switchedOff, houseDeclared, taken) : this;
    }

    /**
     * The same resolver, with these travel methods switched off. Feed it
     * {@code FarmingPreferences.travelOff()}.
     *
     * <p>A switched-off method fails every rung that needs it, wherever the rung leads: its
     * capabilities resolve as lacking, with the switch as the reason, and switched-off
     * tablets or Spellbook Swap never answer for a place. Routes, packing and skip reasons
     * all ask here, so all three follow the switch.
     */
    public CapabilityResolver switchingOff(Set<TravelMethod> methods) {
        Set<TravelMethod> wanted = methods == null || methods.isEmpty() ? Collections.<TravelMethod>emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(methods));
        return wanted.equals(switchedOff) ? this : new CapabilityResolver(onBook, dailyLedger, nowEpochSec, sight, house,
                        readsPosition, wanted, houseDeclared, taken);
    }

    /**
     * The same resolver, taking the player's word for these house items. Feed it
     * {@code FarmingPreferences.houseDeclared()}.
     *
     * <p>A declaration counts only for an item the house has not been seen to settle:
     * what was seen always wins, and a level or quest the player lacks still rules an item
     * out.
     */
    public CapabilityResolver declaringHouse(Set<Capability> declared) {
        Set<Capability> wanted = declared == null || declared.isEmpty() ? Collections.<Capability>emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(declared));
        return wanted.equals(houseDeclared) ? this : new CapabilityResolver(onBook, dailyLedger, nowEpochSec, sight, house,
                        readsPosition, switchedOff, wanted, taken);
    }

    /**
     * The same resolver, knowing the teleport the player has spent on the way to the stop
     * they are heading for. That stop reads the teleport as taken: no runes, tablet or ring
     * to bring for it.
     */
    public CapabilityResolver travelling(TravelTaken spent) {
        TravelTaken wanted = spent != null ? spent : TravelTaken.NONE;
        return wanted.equals(taken) ? this : new CapabilityResolver(onBook, dailyLedger, nowEpochSec, sight, house,
                        readsPosition, switchedOff, houseDeclared, wanted);
    }

    /** Whether a spent teleport may answer for the stop this resolver plans. */
    public boolean hasTravelTaken() {
        return readsPosition && !taken.isNone();
    }

    /** The travel switch that fails this capability, or null while none does. */
    private TravelMethod switchFailing(Capability capability) {
        TravelMethod method = TravelMethod.of(capability);
        return method != null && switchedOff.contains(method) ? method : null;
    }

    /** Whether this resolver may answer from where the player is standing. */
    public boolean readsPosition() {
        return readsPosition;
    }

    public Spellbook getSpellbook() {
        return onBook;
    }

    /**
     * How this player would reach a place today: standing there already, then spell, then
     * a tablet they have not switched off.
     */
    public TeleportPlanner.Choice teleport(Destination destination, PlayerState state) {
        if (readsPosition && taken.reached(destination)) {
            return teleported(destination);
        }
        return TeleportPlanner.best(destination, state, onBook, readsPosition, tabletsOff(destination));
    }

    /**
     * {@link #teleport}, for the place a rung arrives at: by Spellbook Swap where the rung
     * names the swap.
     */
    public TeleportPlanner.Choice teleportFor(Requirement rung, Destination destination, PlayerState state) {
        if (rung.contains(Requirement.reachBySwap(destination))) {
            return bySwap(destination, state);
        }
        return teleport(destination, state);
    }

    /**
     * Taken already: no means, so the bag asks for nothing, as for a player standing in the
     * place's zone.
     */
    private static TeleportPlanner.Choice teleported(Destination destination) {
        return new TeleportPlanner.Choice(null, CapabilityState.HAVE, "you have teleported to " + destination.getDisplayName());
    }

    /**
     * Whether a tablet may take the player to this place: tablets are on, and so is this
     * place's own tablet where it has a switch of its own.
     */
    private boolean tabletsAllowed(Destination destination) {
        return tabletsOff(destination) == null;
    }

    /** The switch that refuses this place's tablet, or null while the tablet is allowed. */
    private TravelMethod tabletsOff(Destination destination) {
        if (switchedOff.contains(TravelMethod.TELEPORT_TABLETS)) {
            return TravelMethod.TELEPORT_TABLETS;
        }
        if (destination == Destination.SALVE_GRAVEYARD && switchedOff.contains(TravelMethod.SALVE_GRAVEYARD_TABLETS)) {
            return TravelMethod.SALVE_GRAVEYARD_TABLETS;
        }
        return null;
    }

    /** {@link TeleportPlanner#bySwap}, unless the player has switched the swap off. */
    private TeleportPlanner.Choice bySwap(Destination destination, PlayerState state) {
        if (switchedOff.contains(TravelMethod.SPELLBOOK_SWAP)) {
            return new TeleportPlanner.Choice(null, CapabilityState.LACK, TravelMethod.SPELLBOOK_SWAP.switchedOffReason());
        }
        if (readsPosition && taken.reached(destination)) {
            return teleported(destination);
        }
        return TeleportPlanner.bySwap(destination, state, onBook, readsPosition);
    }

    /**
     * What the player can do about this one capability, and why we think so. A capability
     * whose travel method the player switched off is lacking, whatever they own.
     */
    public CapabilityVerdict resolve(Capability capability, PlayerState state) {
        TravelMethod off = switchFailing(capability);
        if (off != null) {
            return CapabilityVerdict.lack(CapabilityVerdict.Source.DECLARED, off.switchedOffReason());
        }
        if (readsPosition && taken.used(capability)) {
            return CapabilityVerdict.have(CapabilityVerdict.Source.DETECTED,
                    "you have used your " + capability.getDisplayName() + " on the way");
        }
        CapabilityVerdict verdict = resolveOwnership(capability, state);
        if (verdict.getState() != CapabilityState.HAVE) {
            return verdict;
        }
        CapabilityVerdict rationed = applyDailyLimit(capability, state);
        return rationed != null ? rationed : verdict;
    }

    /** {@link #resolve}, before today's teleport allowance gets a say. */
    private CapabilityVerdict resolveOwnership(Capability capability, PlayerState state) {
        // A spell on another book is a LACK for this plan, whatever the account has
        // unlocked. ANY means no book is settled, so it falls through to the rules.
        Spellbook needs = CapabilitySources.spellbookFor(capability);
        if (needs != null && onBook != Spellbook.ANY && needs != onBook) {
            return CapabilityVerdict.lack(CapabilityVerdict.Source.DETECTED,
                    capability.getDisplayName() + " is on the " + needs.getDisplayName()
                            + " spellbook. You are on the " + onBook.getDisplayName() + " spellbook");
        }
        if (CapabilitySources.isDetectable(capability)) {
            CapabilityVerdict detected = CapabilitySources.resolve(capability, state, sight, house, houseDeclared);
            // A rule can still answer UNKNOWN, e.g. a varbit the login scan has not read.
            if (detected.getState() != CapabilityState.UNKNOWN) {
                return detected;
            }
        }
        return CapabilityVerdict.unknown("not sure whether you have " + capability.getDisplayName());
    }

    /**
     * Downgrades an owned capability to {@code LACK} when today's allowance for it is
     * spent, or returns null to leave the ownership verdict standing.
     *
     * <p>The allowance belongs to the tier the player holds, not the tier a chain names: a
     * chain asking for Explorer's ring 2 from a player holding ring 3 has no daily limit.
     * Every tier of the family is re-resolved through {@link #resolveOwnership} (not
     * {@link #resolve}, which would recurse) and {@link Capability#bestHeldTier} picks the
     * one to ask the ledger about.
     */
    private CapabilityVerdict applyDailyLimit(Capability capability, PlayerState state) {
        if (dailyLedger == null) {
            return null;
        }
        Capability held = capability;
        // Substituting the held tier may only relax a ration, never impose one. An
        // Ardougne cloak 1's Monastery teleport is unlimited, and must not inherit cloak 2's
        // farm-teleport limit.
        if (capability.isRationedDaily() && capability.getTierFamily() != Capability.TierFamily.NONE) {
            Set<Capability> owned = new HashSet<>();
            for (Capability tier : Capability.values()) {
                if (tier.getTierFamily() == capability.getTierFamily()
                        && resolveOwnership(tier, state).getState() == CapabilityState.HAVE) {
                    owned.add(tier);
                }
            }
            held = Capability.bestHeldTier(capability.getTierFamily(), owned);
        }
        if (held == null || !held.isRationedDaily()) {
            return null;
        }
        if (dailyLedger.isExhausted(held, nowEpochSec)) {
            return CapabilityVerdict.lack(CapabilityVerdict.Source.DETECTED,
                    "you have used today's " + held.getDisplayName() + " teleports");
        }
        return null;
    }

    /**
     * Whether a travel rung's requirement holds.
     *
     * <p>{@code reachedPatches} maps the stops already planned for this run to how sure each
     * one is, so a rung like "run north from the Falador tree patch" is free for a player
     * going there anyway. A rung standing on an uncertain stop inherits its uncertainty.
     */
    public CapabilityState evaluate(Requirement requirement, PlayerState state, Map<PatchRef, CapabilityState> reachedPatches) {
        return evaluate(requirement, null, state, reachedPatches);
    }

    /**
     * {@link #evaluate}, reading one sub-expression as held. Asks whether a rung would hold
     * but for that part, such as a fairy ring rung but for a way to stand at a ring.
     */
    public CapabilityState evaluateGranting(Requirement requirement, Requirement granted,
            PlayerState state, Map<PatchRef, CapabilityState> reachedPatches) {
        return evaluate(requirement, granted, state, reachedPatches);
    }

    private CapabilityState evaluate(Requirement requirement, Requirement granted,
            PlayerState state, Map<PatchRef, CapabilityState> reachedPatches) {
        if (granted != null && requirement.equals(granted)) {
            return CapabilityState.HAVE;
        }
        switch (requirement.getKind()) {
            case CAPABILITY:
                return resolve(requirement.getCapability(), state).getState();
            case DESTINATION:
                return (requirement.isBySwap() ? bySwap(requirement.getDestination(), state)
                        : teleport(requirement.getDestination(), state)).getState();
            case REACHED_PATCH:
                return reachedPatches == null ? CapabilityState.LACK : reachedPatches.getOrDefault(requirement.getPatch(),
                                CapabilityState.LACK);
            // Never UNKNOWN: a wrongly assumed position costs the teleport the player did
            // not pack, so an unread region is a no.
            case STANDING_IN:
                return readsPosition && state != null && requirement.getZone().covers(state.getRegionId()) ? CapabilityState.HAVE
                        : CapabilityState.LACK;
            case ALL:
                return CapabilityState.all(evaluateParts(requirement, granted, state, reachedPatches));
            case ANY:
                return CapabilityState.any(evaluateParts(requirement, granted, state, reachedPatches));
            default:
                throw new IllegalArgumentException("unknown requirement kind " + requirement.getKind());
        }
    }

    private List<CapabilityState> evaluateParts(Requirement requirement, Requirement granted,
            PlayerState state, Map<PatchRef, CapabilityState> reachedPatches) {
        List<CapabilityState> states = new ArrayList<>(requirement.getParts().size());
        for (Requirement part : requirement.getParts()) {
            states.add(evaluate(part, granted, state, reachedPatches));
        }
        return states;
    }

    /**
     * The capabilities this rung leans on for this player, which can be fewer than it
     * mentions. Under an "or" only the taken branch counts (see {@link #takenBranch}).
     * When no branch is settled, every capability the "or" mentions stands.
     */
    public Set<Capability> reliedOn(Requirement requirement, PlayerState state, Map<PatchRef, CapabilityState> reachedPatches) {
        switch (requirement.getKind()) {
            // A travel item already used up on the way has nothing left to pack.
            case CAPABILITY:
                return readsPosition && taken.used(requirement.getCapability()) ? Collections.emptySet()
                        : Collections.singleton(requirement.getCapability());
            // A destination is packed for from the teleport the planner chooses (see
            // reliedOnDestination). The other two name ground, not anything to bring.
            case DESTINATION:
            case REACHED_PATCH:
            case STANDING_IN:
                return Collections.emptySet();
            case ALL: {
                Set<Capability> needed = new LinkedHashSet<>();
                for (Requirement part : requirement.getParts()) {
                    needed.addAll(reliedOn(part, state, reachedPatches));
                }
                return needed;
            }
            case ANY: {
                Requirement taken = takenBranch(requirement, state, reachedPatches);
                if (taken != null) {
                    return reliedOn(taken, state, reachedPatches);
                }
                return requirement.leafCapabilities();
            }
            default:
                throw new IllegalArgumentException("unknown requirement kind " + requirement.getKind());
        }
    }

    /**
     * The place this rung actually travels to for this player, or null when it names none.
     *
     * <p>The counterpart of {@link #reliedOn} for rungs that are an "or" of items and a place,
     * such as the fairy ring approaches with a Salve graveyard teleport among them. The bag
     * packs the teleport chosen to reach the place.
     *
     * <p>Only the taken branch counts. An "or" nothing settles contributes no destination,
     * unlike {@code reliedOn}'s widest-answer fallback, because a teleport named in error is a
     * consumable withdrawn and spent.
     */
    public Destination reliedOnDestination(Requirement requirement, PlayerState state, Map<PatchRef, CapabilityState> reachedPatches) {
        switch (requirement.getKind()) {
            case DESTINATION:
                return requirement.getDestination();
            case ALL:
                for (Requirement part : requirement.getParts()) {
                    Destination found = reliedOnDestination(part, state, reachedPatches);
                    if (found != null) {
                        return found;
                    }
                }
                return null;
            case ANY: {
                Requirement taken = takenBranch(requirement, state, reachedPatches);
                return taken == null ? null : reliedOnDestination(taken, state, reachedPatches);
            }
            default:
                return null;
        }
    }

    /**
     * The branch of an "or" the player would take: the first {@code HAVE}, else the first
     * {@code UNKNOWN}, or null when every branch is a {@code LACK}.
     *
     * <p>That picks the branch the route took only while the unknowns under one "or" are
     * interchangeable, so the catalogue must keep {@code REACHED_PATCH} out from under an
     * "or".
     *
     * <p>Public so the planner can rank a rung by the walk of the way in it takes, from the
     * same answer the bag packs for.
     */
    public Requirement takenBranch(Requirement requirement, PlayerState state, Map<PatchRef, CapabilityState> reachedPatches) {
        Requirement taken = firstMatching(requirement, state, reachedPatches, CapabilityState.HAVE);
        return taken != null ? taken : firstMatching(requirement, state, reachedPatches, CapabilityState.UNKNOWN);
    }

    /** The first part of an "or" in the state asked for. */
    private Requirement firstMatching(Requirement requirement, PlayerState state,
            Map<PatchRef, CapabilityState> reachedPatches, CapabilityState wanted) {
        for (Requirement part : requirement.getParts()) {
            if (evaluate(part, state, reachedPatches) == wanted) {
                return part;
            }
        }
        return null;
    }

    /**
     * How far a rung is from holding, nearest first after {@link #NONE}. A skip names the
     * best-ranked rung with the nearest gap.
     */
    public enum Gap {
        /** Nothing to name: the rung holds, or it wants only ground the run does not cover. */
        NONE,
        /**
         * Only a restock of what the player already has: runes for a spell on the open book
         * whose level and quest are met, or charges for an item they own.
         */
        RESTOCK,
        /**
         * Only a teleport to a place: a spell short of its Magic level, or a tablet to buy.
         */
        TELEPORT,
        /** Items the player has none of, and could buy, make or win today. */
        ITEM,
        /**
         * A quest, diary, level or other flag, or a switch the player turned off. Also an
         * item or a place behind one the player lacks: a skill cape below 99, a quest's
         * reward, a tablet whose quest is unfinished.
         */
        UNLOCK
    }

    /**
     * The gap between a rung and this player. An "all" is as far as its farthest part, an
     * "or" as near as its nearest, and an "or" with a branch held has no gap. Reached patches
     * and positions count as no gap, as in {@link #explainShortfall}.
     */
    public Gap gapTo(Requirement requirement, PlayerState state) {
        switch (requirement.getKind()) {
            case CAPABILITY:
                return gapToCapability(requirement.getCapability(), state);
            case DESTINATION:
                return gapToPlace(requirement, state);
            case REACHED_PATCH:
            case STANDING_IN:
                return Gap.NONE;
            case ALL: {
                Gap farthest = Gap.NONE;
                for (Requirement part : requirement.getParts()) {
                    Gap gap = gapTo(part, state);
                    if (gap.compareTo(farthest) > 0) {
                        farthest = gap;
                    }
                }
                return farthest;
            }
            case ANY: {
                Gap nearest = Gap.NONE;
                for (Requirement part : requirement.getParts()) {
                    if (evaluate(part, state, null) == CapabilityState.HAVE) {
                        return Gap.NONE;
                    }
                    Gap gap = gapTo(part, state);
                    if (gap != Gap.NONE && (nearest == Gap.NONE || gap.compareTo(nearest) < 0)) {
                        nearest = gap;
                    }
                }
                return nearest;
            }
            default:
                throw new IllegalArgumentException("unknown requirement kind " + requirement.getKind());
        }
    }

    /**
     * A carried item the player owns yet cannot use is a restock: an empty Book of the Dead,
     * or an Explorer's ring with today's teleports spent. An item whose source asks for
     * something the player lacks, such as a cape behind every diary, is an unlock, and so is
     * an item the player switched off. Any other item read from the bank is one to get, and
     * anything read from a flag is an unlock.
     */
    private Gap gapToCapability(Capability capability, PlayerState state) {
        if (resolve(capability, state).getState() == CapabilityState.HAVE) {
            return Gap.NONE;
        }
        if (switchFailing(capability) != null) {
            return Gap.UNLOCK;
        }
        CarryItems.Carry carry = CarryItems.forCapability(capability);
        if (carry != null && state != null) {
            for (int itemId : carry.getItemIds()) {
                if (ItemCounts.ownsAny(state, itemId)) {
                    return Gap.RESTOCK;
                }
            }
        }
        return capability.getDetectability() == Capability.Detectability.ITEM && !CapabilitySources.isBarredFromGetting(capability, state)
                ? Gap.ITEM : Gap.UNLOCK;
    }

    /**
     * A spell on the open book short only of runes is a restock, and any other way in that
     * no unfinished quest bars is a teleport to get. A place whose every way in waits on a
     * quest, or on tablets or a swap the player switched off, is an unlock.
     */
    private Gap gapToPlace(Requirement place, PlayerState state) {
        Destination destination = place.getDestination();
        boolean tablets = tabletsAllowed(destination);
        TeleportPlanner.Choice choice = place.isBySwap() ? bySwap(destination, state) : teleport(destination, state);
        if (choice.getState() == CapabilityState.HAVE) {
            return Gap.NONE;
        }
        if (place.isBySwap()) {
            return !switchedOff.contains(TravelMethod.SPELLBOOK_SWAP) && TeleportPlanner.isOpenBySwap(destination, state, onBook)
                    ? Gap.TELEPORT : Gap.UNLOCK;
        }
        if (TeleportPlanner.wantsOnlyRunes(destination, state, onBook)) {
            return Gap.RESTOCK;
        }
        return TeleportPlanner.isOpenToGet(destination, state, onBook, tablets) ? Gap.TELEPORT : Gap.UNLOCK;
    }

    /**
     * What stands between a rung and the player, to explain a skip: the reasons for the
     * first two unmet destinations and capabilities, or "" when nothing is missing.
     *
     * <p>Ignores reached patches. A rung that fell through because the run skips its
     * neighbouring patch has no item to name.
     */
    public String explainShortfall(Requirement requirement, PlayerState state) {
        Set<Destination> places = new LinkedHashSet<>();
        Set<Capability> capabilities = new LinkedHashSet<>();
        collectExplained(requirement, state, places, capabilities);
        List<String> missing = new ArrayList<>();
        for (Destination destination : places) {
            TeleportPlanner.Choice choice = teleportFor(requirement, destination, state);
            // Varrock and the Grand Exchange share a spell and a tablet, so both can give
            // the same reason; it reads once.
            if (choice.getState() != CapabilityState.HAVE && !missing.contains(choice.getBecause())) {
                missing.add(choice.getBecause());
            }
        }
        for (Capability capability : capabilities) {
            CapabilityVerdict verdict = resolve(capability, state);
            if (verdict.getState() != CapabilityState.HAVE) {
                missing.add(verdict.getBecause());
            }
        }
        if (missing.isEmpty()) {
            return "";
        }
        if (missing.size() == 1) {
            return missing.get(0);
        }
        return String.join(", and ", missing.subList(0, Math.min(2, missing.size())));
    }

    /**
     * The places and capabilities a shortfall names, in the order the requirement lists
     * them. Under an "or", a way in that also asks for a capability the player lacks is left
     * out while another way stays open. Without the medium Varrock diary the Grand Exchange
     * landing is no way in, and naming it would point the player at a landing they cannot
     * choose, or spend one of the two reasons on it.
     */
    private void collectExplained(Requirement requirement, PlayerState state, Set<Destination> places, Set<Capability> capabilities) {
        switch (requirement.getKind()) {
            case DESTINATION:
                places.add(requirement.getDestination());
                return;
            case CAPABILITY:
                capabilities.add(requirement.getCapability());
                return;
            case ALL:
                for (Requirement part : requirement.getParts()) {
                    collectExplained(part, state, places, capabilities);
                }
                return;
            case ANY: {
                List<Requirement> open = new ArrayList<>();
                for (Requirement way : requirement.getParts()) {
                    if (!asksForALack(way, state)) {
                        open.add(way);
                    }
                }
                for (Requirement way : open.isEmpty() ? requirement.getParts() : open) {
                    collectExplained(way, state, places, capabilities);
                }
                return;
            }
            default:
                return;
        }
    }

    /** Whether this "and" asks, directly, for a capability the player lacks. */
    private boolean asksForALack(Requirement way, PlayerState state) {
        if (way.getKind() != Requirement.Kind.ALL) {
            return false;
        }
        for (Requirement part : way.getParts()) {
            if (part.getKind() == Requirement.Kind.CAPABILITY && resolve(part.getCapability(), state).getState() == CapabilityState.LACK) {
                return true;
            }
        }
        return false;
    }
}
