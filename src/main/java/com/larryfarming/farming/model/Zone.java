package com.larryfarming.farming.model;

import java.util.*;

/**
 * Somewhere the player can be standing, as the game reports it: a named place over a set
 * of map regions.
 *
 * <p>A {@link Destination} is where a teleport puts the player and
 * {@link Requirement.Kind#REACHED_PATCH} is where the trip has taken them. A zone is where
 * the game reports them now, whether the plan sent them or not. A player already in a zone
 * should not be asked for the teleport there, and a ride free from here should outrank
 * one that is not.
 *
 * <p>Zones use map regions (64 by 64 tiles), the resolution {@code PlayerState.getRegionId()}
 * carries. Every rung written against a zone must be true anywhere in it.
 *
 * <p>Each id comes from a tile checked against the wiki or in game, by
 * {@code regionId = (x >> 6) << 8 | (y >> 6)}, or from a walk. {@code FarmingWorldMap}'s ids
 * for a place of the same name cover where a patch cluster's varbits transmit, not where a
 * teleport lands. A missing id only leaves a rung unused. A wrong one drops a teleport from
 * the player's bag.
 */
public enum Zone {

    /**
     * Where the Civitas illa Fortis teleport lands: the landing, not the whole city.
     *
     * <p>A zone on a destination claims that arriving costs nothing, which holds only near
     * the landing. Sunrise Palace ({@code 1681,3136}) and the quetzal landing site where the
     * Renu rungs depart ({@code 1697,3140}) both fall in region 6705.
     *
     * <p>{@code FarmingWorldMap}'s six ids for this name cover the Ortus Farm cluster (x
     * 1536–1663), which stops short of the palace and is 100 tiles from the quetzal site.
     *
     * <p>The palace lands on the region's southern edge ({@code y == 3136} is its first
     * row), so the zone extends east and north along the run to Renu. The city's west bank
     * lies south-west. The zone leaves it out, because the run to Renu is not free from the
     * bank.
     */
    CIVITAS_ILLA_FORTIS("Civitas illa Fortis", 6705),

    /**
     * The Tree Gnome Stronghold, for the rung that rides its spirit tree.
     *
     * <p>Three ids, each with a tile behind it: the spirit tree {@code 2461,3444}, the tree
     * patch {@code 2436,3415} and the fruit patch {@code 2476,3446} all fall in 9781, the
     * Grand Tree in 9782, and the Slayer cave entrance in 9525.
     *
     * <p>9526 is deliberately absent despite appearing in {@code FarmingWorldMap}'s
     * registration. It covers x 2368–2431, y 3456–3519, north-west of the tree, and no rung
     * conditioned on this zone starts there.
     */
    GNOME_STRONGHOLD("the Tree Gnome Stronghold", 9781, 9782, 9525);

    private final String displayName;
    private final Set<Integer> regionIds;

    Zone(String displayName, int... regionIds) {
        if (regionIds.length == 0) {
            throw new IllegalArgumentException(displayName + ": a zone is somewhere");
        }
        this.displayName = displayName;
        Set<Integer> ids = new LinkedHashSet<>(regionIds.length * 2);
        for (int regionId : regionIds) {
            ids.add(regionId);
        }
        this.regionIds = Collections.unmodifiableSet(ids);
    }

    /**
     * Player-facing, written to sit mid-sentence with its article: "you are already at the
     * Tree Gnome Stronghold".
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Whether a player in this region is standing in this place.
     *
     * <p>Null answers false, unlike other unreadable state that answers {@code UNKNOWN}. A
     * false "already here" would remove a teleport from the player's bag.
     */
    public boolean covers(Integer regionId) {
        return regionId != null && regionIds.contains(regionId);
    }

    /**
     * The zone this region is in, or null when no zone covers it.
     *
     * <p>A cheap pre-check, run on every game event. A null means position cannot change
     * any answer, so a position-aware plan would equal a blind one and can be skipped.
     */
    public static Zone covering(Integer regionId) {
        for (Zone zone : values()) {
            if (zone.covers(regionId)) {
                return zone;
            }
        }
        return null;
    }
}
