package com.larryfarming.snapshot;

import java.util.List;
import lombok.Value;

/** Settings drawn together under one caption, in a fixed order. */
@Value
public class FarmingSettingGroup {

    /** The caption, named for a player: "Portal Nexus". */
    String label;

    List<FarmingSettingChoice> choices;
}
