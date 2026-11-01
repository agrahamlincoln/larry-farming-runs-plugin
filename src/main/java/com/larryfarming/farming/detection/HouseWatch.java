package com.larryfarming.farming.detection;

import java.util.*;
import lombok.Value;

/**
 * Reads what is built in the player's own house as it loads, and nothing from anyone else's.
 *
 * <p>The sequence: a click into a house, then the "Loading house" interface opens, then a
 * scene load, then the interface closes once the house is sent. The click decides whose
 * house it is. Object spawns are collected into a pending set from the load until the
 * interface closes, so furniture sent after the scene itself still counts. The first tick
 * with the interface closed commits the set as a complete scan, but only when a scene load
 * belongs to that interface, the player stands in a house region, and the house is confirmed
 * as their own. Any other house, a load the interface never announced, or an interface that
 * never closes before a logout or hop, learns nothing.
 *
 * <p>A solo ironman can enter no house but their own, so their every house load is theirs.
 * A group ironman can enter their group's houses and is judged by the click like anyone.
 *
 * <p>No RuneLite types. Client thread only.
 */
public final class HouseWatch {

    /**
     * How long a click into a house stays fresh, in ticks. A teleport lands in four or five,
     * and running to a portal from across a town takes some twenty. A click older than this
     * leaves the load unknown, which learns nothing.
     */
    static final int ENTRY_WINDOW_TICKS = 50;

    /**
     * The map regions a house is built from, by instance template: every house, wherever
     * its portal stands, is copied from these.
     */
    private static final int[] HOUSE_REGIONS = {7534, 7535, 7790, 7791, 8046, 8047, 8302, 8303};

    /** {@code VarbitID.IRONMAN}'s values for the ironman, ultimate and hardcore modes. */
    private static final int FIRST_SOLO_IRONMAN = 1;
    private static final int LAST_SOLO_IRONMAN = 3;

    /** A finished scan of the player's own house, and the signal that proved it theirs. */
    @Value
    public static class Scan {
        Set<HouseFeature> found;
        String signal;
    }

    private HouseEntry lastEntry;
    private int lastEntryTick;

    /** Whether the house the player is in, or is loading, is known to be their own. */
    private boolean ownHouse;
    /** What proved {@link #ownHouse}, for the log. */
    private String ownSignal;
    /** Whether the player is in, or is loading, a house, whoever's it is. */
    private boolean inHouse;

    /** The "Loading house" interface opened and has not yet been seen closed. */
    private boolean awaitingClose;
    /** A scene load belongs to the open interface, so its close ends a real house load. */
    private boolean loadAttached;

    /** A scene is loading. */
    private boolean loading;
    /** A scene finished loading, and no tick has passed since. */
    private boolean loaded;

    private final Set<HouseFeature> pending = EnumSet.noneOf(HouseFeature.class);

    /** Whether this map region, by instance template, is part of a house. */
    public static boolean isHouseRegion(int templateRegionId) {
        for (int region : HOUSE_REGIONS) {
            if (region == templateRegionId) {
                return true;
            }
        }
        return false;
    }

    /** Whether an {@code IRONMAN} varbit value is a mode that visits no other house. */
    public static boolean isSoloIronman(int ironmanVarbit) {
        return ironmanVarbit >= FIRST_SOLO_IRONMAN && ironmanVarbit <= LAST_SOLO_IRONMAN;
    }

    /**
     * A click that leads into a house, or null for any other click, which changes nothing.
     *
     * <p>A click that may lead into someone else's house, guest or unknown, also ends the
     * player's claim on the house they stand in at once. So a group teleport out of their own
     * house can never commit the group member's house, whatever order the load's events
     * arrive in.
     */
    public void entryClicked(HouseEntry entry, int tick) {
        if (entry == null) {
            return;
        }
        lastEntry = entry;
        lastEntryTick = tick;
        if (entry.getOwner() != HouseEntry.Owner.OWN) {
            notOwn();
        }
    }

    /**
     * The "Loading house" interface opened: decide whose house is loading, and spend the
     * click that led here.
     *
     * <p>A house reloading around a player already confirmed in their own, with no click
     * into a house since, stays theirs: only the owner can switch building mode, and leaving
     * the house shows on the next tick outside a house region. Any click into a house since,
     * however old, voids that: a click too old to judge says nothing about where it led.
     *
     * <p>An interface that opens again before a tick saw it close belongs to the same load,
     * so it can end the claim but never make one: an own-house click that went nowhere,
     * made in someone else's house, cannot claim that house when its interface reopens.
     *
     * @return what was decided, in fixed words, for the log
     */
    public String houseLoading(boolean soloIronman, int tick) {
        HouseEntry entry = lastEntry;
        boolean fresh = isFresh(tick);
        boolean reopened = awaitingClose;
        lastEntry = null;
        inHouse = true;
        awaitingClose = true;
        // The interface may open before the load starts, during it, or just after it ends.
        loadAttached = (reopened && loadAttached) || loading || loaded;
        if (soloIronman) {
            return own("solo ironman");
        }
        if (reopened) {
            boolean keepsClaim = entry == null || (fresh && entry.getOwner() == HouseEntry.Owner.OWN);
            if (ownHouse && keepsClaim) {
                return "your own house still, its loading interface reopened";
            }
            notOwn();
            return "a house not known as yours, its loading interface reopened";
        }
        if (entry == null) {
            if (ownHouse) {
                return own("reloaded inside your own house");
            }
            return "an unknown house, entered by no recognised click";
        }
        if (fresh && entry.getOwner() == HouseEntry.Owner.OWN) {
            return own(entry.getSignal());
        }
        notOwn();
        if (!fresh) {
            return "an unknown house, by a click too old to judge";
        }
        return (entry.getOwner() == HouseEntry.Owner.GUEST ? "someone else's house, by " : "an unknown house, by ") + entry.getSignal();
    }

    /** The game state went to LOADING: a new scene is coming, so start the set afresh. */
    public void sceneLoading() {
        pending.clear();
        loading = true;
        loaded = false;
    }

    /** The game state went to LOGGED_IN after a load. */
    public void sceneLoaded() {
        if (loading) {
            loading = false;
            loaded = true;
            if (awaitingClose) {
                loadAttached = true;
            }
        }
    }

    /**
     * A game object spawned. Collected from the start of a scene load until the "Loading
     * house" interface closes, whoever's house it may turn out to be: the commit, not the
     * collection, checks the owner. Allocates nothing.
     */
    public void objectSpawned(int objectId) {
        if (loading || loaded || awaitingClose) {
            HouseFeature.addBuilt(objectId, pending);
        }
    }

    /** Whether {@link #tick} needs to know where the player stands this tick. */
    public boolean wantsRegion() {
        return loaded || ownHouse || inHouse || awaitingClose;
    }

    /** Whether {@link #tick} needs to know if the "Loading house" interface is still open. */
    public boolean awaitsInterfaceClose() {
        return awaitingClose;
    }

    /**
     * A game tick, with whether the player stands in a house region and whether the
     * "Loading house" interface is still open. The interface is read only while
     * {@link #awaitsInterfaceClose()}; pass false otherwise.
     *
     * @return the scan to commit, replacing the stored one, or null
     */
    public Scan tick(boolean inHouseRegion, boolean loadingInterfaceOpen) {
        boolean waiting = awaitingClose && (loadingInterfaceOpen || loading);
        // Before its load the interface can open with the player still outside, so standing
        // outside ends nothing while it waits on a load. Once it has closed, it does.
        if (!inHouseRegion && !(waiting && !loadAttached)) {
            notOwn();
            inHouse = false;
        }
        boolean justLoaded = loaded;
        loaded = false;
        if (!awaitingClose) {
            if (justLoaded) {
                // A load no interface announced: no house this can vouch for.
                pending.clear();
            }
            return null;
        }
        if (waiting) {
            return null;
        }
        boolean complete = loadAttached && ownHouse && inHouseRegion;
        awaitingClose = false;
        loadAttached = false;
        if (!complete) {
            pending.clear();
            return null;
        }
        Set<HouseFeature> found = pending.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(EnumSet.copyOf(pending));
        pending.clear();
        return new Scan(found, ownSignal);
    }

    /** Whether the player stands in a house known to be their own, for the Nexus read. */
    public boolean isInOwnHouse() {
        return ownHouse && !loading && !loaded && !awaitingClose;
    }

    /**
     * Whether the player is in, or is loading, a house not known to be their own. Its
     * Nexus may write the slot varbits, so no slot read there is credited to the player.
     */
    public boolean isInOtherHouse() {
        return inHouse && !ownHouse;
    }

    /**
     * Forgets everything, for a logout, a world hop or the plugin stopping. A load whose
     * interface had not closed by then commits nothing.
     */
    public void clear() {
        lastEntry = null;
        notOwn();
        inHouse = false;
        awaitingClose = false;
        loadAttached = false;
        loading = false;
        loaded = false;
        pending.clear();
    }

    private boolean isFresh(int tick) {
        return lastEntry != null && tick >= lastEntryTick && tick - lastEntryTick <= ENTRY_WINDOW_TICKS;
    }

    private String own(String signal) {
        ownHouse = true;
        ownSignal = signal;
        return "your own house, by " + signal;
    }

    private void notOwn() {
        ownHouse = false;
        ownSignal = null;
    }
}
