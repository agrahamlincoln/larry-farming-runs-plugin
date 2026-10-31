package com.larryfarming.farming.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Value;

/**
 * One item worth wearing on a farm run, and why.
 *
 * <p>Named by item rather than by {@link Capability} because the panel draws the item.
 *
 * <p>A pick usually has several IDs: charged jewellery has a different ID at every charge
 * level, and skill capes come trimmed and untrimmed.
 *
 * <p>A pick can also stand for interchangeable items with names of their own, such as the
 * dramen and lunar staves, which open fairy rings alike. Each is one of its
 * {@link #forms}, so the panel can name and draw the one the player holds.
 */
@Value
public class GearPick {

    /** Every item ID that counts as wearing this. The first is the sprite shown. */
    List<Integer> itemIds;

    String displayName;

    /** One clause on what it buys, for the row under the slot. */
    String benefit;

    /** The named items this pick accepts alike, or empty when it is one item. */
    List<GearPick> forms;

    public static GearPick of(String displayName, String benefit, int... itemIds) {
        if (itemIds.length == 0) {
            throw new IllegalArgumentException(displayName + " needs at least one item id");
        }
        List<Integer> ids = new ArrayList<>(itemIds.length);
        for (int id : itemIds) {
            ids.add(id);
        }
        return new GearPick(List.copyOf(ids), displayName, benefit, List.of());
    }

    /**
     * A pick any of these items fills, alike. The first form is drawn while the player
     * holds none of them.
     */
    public static GearPick anyOf(String displayName, String benefit, GearPick... forms) {
        if (forms.length == 0) {
            throw new IllegalArgumentException(displayName + " needs at least one form");
        }
        List<Integer> ids = new ArrayList<>();
        for (GearPick form : forms) {
            ids.addAll(form.getItemIds());
        }
        return new GearPick(List.copyOf(ids), displayName, benefit, List.of(forms));
    }

    /** The form that accepts this item, or this pick when it has no forms or none fits. */
    public GearPick formHolding(int itemId) {
        for (GearPick form : forms) {
            if (form.getItemIds().contains(itemId)) {
                return form;
            }
        }
        return this;
    }

    /** The sprite to draw for this pick. */
    public int getIconItemId() {
        return itemIds.get(0);
    }
}
