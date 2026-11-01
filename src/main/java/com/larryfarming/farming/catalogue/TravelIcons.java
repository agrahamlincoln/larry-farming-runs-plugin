package com.larryfarming.farming.catalogue;

import com.larryfarming.farming.model.Capability;

/**
 * What to draw beside a route that is taken on an item rather than cast: the item
 * {@link CarryItems} packs for it, worn items such as an Ardougne cloak or a Farming cape
 * included, so a route and its bag line draw the same sprite.
 *
 * <p>A capability with no entry draws nothing. Networks such as fairy rings, spirit trees
 * and minecarts have no honest sprite, and a wrong one is worse than none.
 */
public final class TravelIcons {

    private TravelIcons() {
    }

    /** The item to draw for this capability, or 0 when there is nothing honest to draw. */
    public static int forCapability(Capability capability) {
        CarryItems.Carry carry = CarryItems.forCapability(capability);
        return carry != null ? carry.getItemId() : 0;
    }
}
