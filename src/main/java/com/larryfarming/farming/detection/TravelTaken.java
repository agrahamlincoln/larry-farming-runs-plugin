package com.larryfarming.farming.detection;

import com.larryfarming.farming.model.Capability;
import com.larryfarming.farming.model.Destination;
import java.util.Set;
import lombok.Value;

/**
 * A teleport the player has already spent on the way to the stop they are heading for: the
 * places its runes or tablet reach, or the travel item it used up.
 *
 * <p>Spent is paid. The runes, the tablet or the ring's last charge left the bag when the
 * player cast or rubbed it, so asking for them again would send the player back to the
 * bank for a teleport they have already taken. See {@link TravelWatch}.
 */
@Value
public class TravelTaken {

    public static final TravelTaken NONE = new TravelTaken(Set.of(), Set.of());

    /**
     * Every place the spent runes or tablet could have reached. Usually one; a staff that
     * pays the elemental runes can leave two spells with the same law runes.
     */
    Set<Destination> places;

    /** The travel item the teleport used up: a ring's last charge, a full ectophial. */
    Set<Capability> items;

    public static TravelTaken to(Set<Destination> places) {
        return places.isEmpty() ? NONE : new TravelTaken(Set.copyOf(places), Set.of());
    }

    public static TravelTaken using(Capability item) {
        return new TravelTaken(Set.of(), Set.of(item));
    }

    public boolean isNone() {
        return places.isEmpty() && items.isEmpty();
    }

    /** Whether the player has already teleported towards this place. */
    public boolean reached(Destination destination) {
        return places.contains(destination);
    }

    /** Whether the player has already used this travel item up on the way. */
    public boolean used(Capability capability) {
        return items.contains(capability);
    }
}
