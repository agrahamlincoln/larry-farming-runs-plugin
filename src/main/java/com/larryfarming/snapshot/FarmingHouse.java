package com.larryfarming.snapshot;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import lombok.Value;

/**
 * What the player's house holds, as the farming tab's house settings draw it: one row per
 * item a farm run can travel by, which the player may declare built until Larry sees it.
 */
@Value
public class FarmingHouse {

    /** How much of the house Larry has seen. */
    public enum Sight {
        /** No scan of the house yet. */
        NOT_SEEN,
        /** Scanned, but some Nexus destination still waits on a read of the Nexus. */
        NEXUS_NOT_SEEN,
        /** Every item settled by what Larry saw. */
        SEEN
    }

    @SerializedName("sight") Sight sight;

    /**
     * The items under their captions, in a fixed order. In each choice, on means built or
     * declared. Seen means Larry has seen the item, and a seen item takes no click. Locked
     * means the house cannot hold it: seen without it, or short of a level or quest, with
     * the level in the choice's level where one is short.
     */
    @SerializedName("groups") List<FarmingSettingGroup> groups;
}
