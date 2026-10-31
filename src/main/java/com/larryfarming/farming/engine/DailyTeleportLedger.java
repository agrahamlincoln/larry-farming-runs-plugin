package com.larryfarming.farming.engine;

import com.larryfarming.farming.model.Capability;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Counts how many of a day's rationed teleports the player has spent, so that a route
 * they have run out of can be stepped over rather than offered again.
 *
 * <p>Two sources feed it. Where the game keeps spent uses in a varbit, the count is read
 * and handed to {@link #observeUsesSpent}, which stays right across logouts and other
 * clients. Otherwise uses are counted with {@link #recordUse} as they are seen. Uses made
 * while the plugin is not watching go unseen, so a counted allowance can look healthier
 * than it is. That error is the safe one: the player tries a spent teleport and moves down
 * the chain, where an undercount would silently skip a patch they could have farmed.
 *
 * <p>Counts belong to the tier the player <em>holds</em>, not the tier a chain asks for: a
 * chain naming an Explorer's ring 2 may be served by a held ring 3, which has no daily
 * limit. Resolve the held tier with
 * {@link Capability#bestHeldTier(Capability.TierFamily, java.util.Collection)} before
 * recording or asking.
 *
 * <p>Uses are recorded against an explicit epoch second, so the caller's clock decides the
 * daily rollover.
 */
public final class DailyTeleportLedger {

    private static final long SECONDS_PER_DAY = 86_400L;

    /** Returned for a capability with no daily cap, so comparisons need no special case. */
    public static final int UNLIMITED_REMAINING = Integer.MAX_VALUE;

    private final Map<Capability, Integer> usedToday = new EnumMap<>(Capability.class);

    /**
     * Which day the counts belong to, as whole days since the epoch. Day boundaries fall
     * at midnight UTC, matching the game's daily reset.
     */
    private long day = Long.MIN_VALUE;

    /** Records one use, ignoring capabilities the game does not ration. */
    public void recordUse(Capability capability, long nowEpochSec) {
        if (!capability.isRationedDaily()) {
            return;
        }
        rollOver(nowEpochSec);
        int spent = usedToday.getOrDefault(capability, 0);
        // Never count past the allowance, so a use seen twice cannot overstate the spend.
        if (spent < capability.getDailyUses()) {
            usedToday.put(capability, spent + 1);
        }
    }

    /**
     * Takes the game's own count of uses spent today, replacing whatever was counted here
     * rather than adding to it.
     *
     * <p>Feed this a varbit from
     * {@link com.larryfarming.farming.detection.CapabilitySources#dailyUsesCapabilitiesByVarbit()},
     * which counts upward from zero as uses are spent.
     */
    public void observeUsesSpent(Capability capability, int spent, long nowEpochSec) {
        if (!capability.isRationedDaily()) {
            return;
        }
        rollOver(nowEpochSec);
        usedToday.put(capability, clampToAllowance(capability, spent));
    }

    /** How many uses are left today, or {@link #UNLIMITED_REMAINING} if it is not rationed. */
    public int remaining(Capability capability, long nowEpochSec) {
        if (!capability.isRationedDaily()) {
            return UNLIMITED_REMAINING;
        }
        rollOver(nowEpochSec);
        return capability.getDailyUses() - usedToday.getOrDefault(capability, 0);
    }

    /** True when today's allowance is spent, so the route should be stepped over. */
    public boolean isExhausted(Capability capability, long nowEpochSec) {
        return remaining(capability, nowEpochSec) <= 0;
    }

    /** Forgets everything, for a player switching accounts. */
    public void clear() {
        usedToday.clear();
        day = Long.MIN_VALUE;
    }

    // --- persistence ---------------------------------------------------------

    /** The day the stored counts belong to, to be handed back to {@link #loadPersisted}. */
    public long exportDay() {
        return day;
    }

    public Map<Capability, Integer> exportForPersist() {
        return Collections.unmodifiableMap(new EnumMap<>(usedToday));
    }

    /**
     * Restores counts saved earlier. Counts from a day that has since ended are dropped
     * rather than carried forward, so a player returning tomorrow starts fresh.
     */
    public void loadPersisted(long savedDay, Map<Capability, Integer> saved) {
        usedToday.clear();
        day = savedDay;
        if (saved == null) {
            return;
        }
        for (Map.Entry<Capability, Integer> entry : saved.entrySet()) {
            Capability capability = entry.getKey();
            if (capability == null || !capability.isRationedDaily() || entry.getValue() == null) {
                continue;
            }
            usedToday.put(capability, clampToAllowance(capability, entry.getValue()));
        }
    }

    private static int clampToAllowance(Capability capability, int spent) {
        return Math.max(0, Math.min(spent, capability.getDailyUses()));
    }

    private void rollOver(long nowEpochSec) {
        long today = Math.floorDiv(nowEpochSec, SECONDS_PER_DAY);
        if (today != day) {
            usedToday.clear();
            day = today;
        }
    }
}
