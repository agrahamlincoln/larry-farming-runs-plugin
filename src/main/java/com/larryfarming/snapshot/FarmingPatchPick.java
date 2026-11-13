package com.larryfarming.snapshot;

import java.util.List;
import lombok.Value;

/** An allotment or flower patch at a herb farm, and the crops its picker offers. */
@Value
public class FarmingPatchPick {

    /** The farm's settings row it sits in: "Catherby". */
    String farm;

    /** The picker's title: "Catherby south". */
    String title;

    /** The key the pick is saved under. */
    String key;

    /** The key For compost is saved under, or null for a patch without it. */
    String compostKey;

    /** The saved For compost, which the picker's checkbox shows. */
    boolean forCompost;

    /** Whether it plants for compost: for compost, with a crop the account can plant. Its square takes the badge. */
    boolean composting;

    /**
     * Off, then each crop of the patch in level order. Each option's key is the value the
     * pick saves, the pick is on, and a crop above the player's level is locked.
     */
    List<FarmingSettingChoice> options;
}
