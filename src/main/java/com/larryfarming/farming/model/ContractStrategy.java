package com.larryfarming.farming.model;

import com.larryfarming.engine.farming.Crop;
import com.larryfarming.engine.farming.PatchType;
import java.util.List;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

/**
 * What to keep growing in the Farming Guild so a contract is likely to be already done
 * when Jane asks for it.
 *
 * <p>Preplanting only. Which contract to accept is deliberately not modelled: the player
 * decides that in Jane's dialogue with the offer in front of them, and can ask for another.
 */
@Value
@Builder
public class ContractStrategy {

    /**
     * One crop kept growing in the guild in anticipation of being asked for it.
     * A slot lists alternatives where either crop serves: lantadyme or cadantine both fill the
     * same herb patch. One slot per patch keeps the preplant list within the guild's
     * patch count.
     */
    @Value
    public static class PreplantSlot {
        List<Crop> acceptable;
        PatchType patchType;
    }

    @Singular("mediumPreplant")
    List<PreplantSlot> mediumPreplants;

    @Singular("hardPreplant")
    List<PreplantSlot> hardPreplants;
}
