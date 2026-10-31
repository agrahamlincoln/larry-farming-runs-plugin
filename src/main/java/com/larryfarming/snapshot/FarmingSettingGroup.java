package com.larryfarming.snapshot;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import lombok.Value;

/** Settings drawn together under one caption, in a fixed order. */
@Value
public class FarmingSettingGroup {

    /** The caption, named for a player: "Portal Nexus". */
    @SerializedName("label") String label;

    @SerializedName("choices") List<FarmingSettingChoice> choices;
}
