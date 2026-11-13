package com.larryfarming.farming.engine;

import com.larryfarming.engine.ItemCounts;
import com.larryfarming.engine.farming.Crop;
import com.larryfarming.farming.detection.BottomlessBucket;
import com.larryfarming.farming.model.FarmingPreferences;
import com.larryfarming.model.PlayerState;
import java.util.EnumSet;
import java.util.Locale;
import lombok.Value;
import lombok.With;
import net.runelite.api.gameval.ItemID;

/**
 * The account's compost: the best step it has reached, how many patches its stock can treat,
 * and whether the thermostat has Larry making more.
 */
@Value
public class CompostStock {

    /** A plan made with no reading: compost, nothing counted, making more. */
    public static final CompostStock NONE = new CompostStock(Crop.COMPOST, 0, true, false, false, null);

    /** COMPOST, SUPERCOMPOST or ULTRACOMPOST; the ordinal is the ladder. */
    Crop step;
    /** Patches the stock can treat. */
    int uses;
    /** Whether the allotments marked for compost plant and the bins are worked; {@code withOn} holds it. */
    @With
    boolean on;
    /** Whether {@link #uses} counts the whole stock, rather than a floor under it. */
    boolean whole;
    /** Whether a filled bottomless bucket away from the leprechaun has no reading, or reads empty. */
    boolean bucketUnread;
    /** The compost in the bucket the player carries or banks, or null when it is empty or unread. */
    Crop bucketKind;

    /** Whether the stock decided {@link #on}, rather than a thermostat never decided. */
    public boolean isDecided() {
        return whole || !on;
    }

    /**
     * The stock this player holds. The count never reads high: a reading counts only while a
     * filled bucket is owned, and a count that may read short is a floor, which can switch the
     * thermostat off but never on. Between the thresholds the thermostat keeps its last state.
     *
     * @param savedStep the best step reached so far, or null
     * @param wasOn the thermostat's last decision, or null when it has made none
     * @param reading what a carried or banked bucket was last read holding, or null
     */
    public static CompostStock of(PlayerState state, Crop savedStep, Boolean wasOn, BottomlessBucket.Reading reading,
            FarmingPreferences prefs) {
        boolean stored = BottomlessBucket.isStored(state);
        boolean filled = ItemCounts.ownsAny(state, ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED);
        BottomlessBucket.Reading held = stored ? BottomlessBucket.stored(state) : filled ? reading : null;
        Crop inBucket = held != null ? held.getKind() : Crop.COMPOST;
        // The ladder steps up on stock the account owns: the compost itself, or what makes it.
        Crop seen = Crop.COMPOST;
        if (ItemCounts.ownsAny(state, ItemID.BUCKET_SUPERCOMPOST) || inBucket == Crop.SUPERCOMPOST || CompostBins.doses(state, false) > 0) {
            seen = Crop.SUPERCOMPOST;
        }
        if (ItemCounts.ownsAny(state, ItemID.BUCKET_ULTRACOMPOST) || inBucket == Crop.ULTRACOMPOST
                || ItemCounts.countOwned(state, ItemID.FOSSIL_VOLCANIC_ASH, null) >= CompostBins.ASH_PER_BIN) {
            seen = Crop.ULTRACOMPOST;
        }
        Crop step = savedStep != null && savedStep.compareTo(seen) > 0 ? savedStep : seen;
        int perBucket = BottomlessBucket.owned(state) ? 2 : 1;
        int uses = held != null && held.getKind().compareTo(step) >= 0 ? held.getUses() : 0;
        for (Crop kind : EnumSet.range(step, Crop.ULTRACOMPOST)) {
            uses += perBucket * ItemCounts.countOwned(state, kind.getItemId(), null);
        }
        // A filled bucket read empty was read before it was filled.
        boolean unread = !stored && filled && (reading == null || reading.getUses() == 0);
        boolean whole = state != null && state.getBankLastSeen() != null && !unread;
        boolean on = wasOn == null || wasOn;
        if (uses >= prefs.stopCompostAt()) {
            on = false;
        } else if (whole && uses < prefs.makeCompostBelow()) {
            on = true;
        }
        return new CompostStock(step, uses, on, whole, unread, !stored && filled && !unread ? reading.getKind() : null);
    }

    /**
     * The herb run's paused line, or null while compost is being made or the count is only a
     * floor. An unread bucket comes first, since the count it leaves is only a floor.
     *
     * @param resumeBelow the threshold the thermostat switches back on below
     */
    public String pausedLine(int resumeBelow) {
        if (bucketUnread) {
            return "Check your bottomless bucket";
        }
        // Grouped as the threshold picks show their numbers.
        return on || !whole ? null : String.format(Locale.ENGLISH, "Compost: %,d uses stocked, resumes below %,d", uses, resumeBelow);
    }

    /**
     * The stock beside the thresholds while compost is being made and the player could stop
     * early, or null. Null while compost is not being made, while the count is only a floor,
     * and below {@code resumeBelow}, where the thermostat would switch straight back on.
     */
    public String stockedLine(int resumeBelow) {
        return on && whole && uses >= resumeBelow ? String.format(Locale.ENGLISH, "%,d uses", uses) : null;
    }
}
