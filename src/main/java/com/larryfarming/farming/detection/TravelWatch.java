package com.larryfarming.farming.detection;

import com.larryfarming.engine.ItemCounts;
import com.larryfarming.farming.catalogue.CarryItems;
import com.larryfarming.farming.catalogue.Teleports;
import com.larryfarming.farming.model.Capability;
import com.larryfarming.farming.model.Destination;
import com.larryfarming.farming.model.Rune;
import com.larryfarming.farming.model.TeleportMeans;
import com.larryfarming.model.ItemStack;
import com.larryfarming.model.PlayerState;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Notices a teleport spent on the way to the next stop, so the plan stops asking for it.
 *
 * <p>The cast takes the runes, the tablet or a ring's last charge out of the bag before the
 * player lands. Without this watch, the next plan would see them gone and send the player to
 * the bank for more, all the way from the landing to the patch.
 *
 * <p><b>Spent, then landed.</b> A tick whose losses match one teleport's whole cost, and fit
 * no other spell or tablet, marks it spent at once, so the bank row never flashes the runes
 * back while the player is in the air. The player must then land: a jump to a region that
 * does not border the last one within {@link #LANDING_TICKS}. Runes or a ring that leave without one went somewhere else,
 * and the mark is dropped.
 *
 * <p><b>Paid until the stop is worked.</b> Once landed, the teleport stays paid while the
 * plan's next patch is the one it was cast for, or lands where it did. A route can go on from
 * the landing by a glider or a fairy ring, which a jump alone would read as a second
 * teleport. Another spent teleport replaces the mark, the trip ending forgets it, and
 * {@link #MAX_TICKS} ends one the player wandered off from.
 *
 * <p>Losses in a tick when the bank changed are never a teleport: that is the player
 * banking.
 *
 * <p>No RuneLite types.
 */
public final class TravelWatch {

    /** A teleport lands three or four ticks after its cast. Ten allows for lag. */
    static final int LANDING_TICKS = 10;

    /** Ten minutes. No stop is that far from the place its teleport lands. */
    static final int MAX_TICKS = 1000;

    private static final Set<Rune> ELEMENTAL =
            EnumSet.of(Rune.AIR, Rune.WATER, Rune.EARTH, Rune.FIRE);

    /**
     * The travel items a teleport can use up: jewellery crumbling on its last charge, a
     * basalt or tablet broken, an ectophial emptied. A cape, a cloak or a ring of unlimited
     * use leaves the bag only when the player puts it somewhere, never on a teleport.
     */
    private static final Set<Capability> USED_UP = EnumSet.of(
            Capability.RING_OF_DUELING, Capability.GAMES_NECKLACE, Capability.COMBAT_BRACELET,
            Capability.DIGSITE_PENDANT, Capability.RING_OF_WEALTH, Capability.SKILLS_NECKLACE,
            Capability.SLAYER_RING, Capability.NECKLACE_OF_PASSAGE,
            Capability.TELEPORT_CRYSTAL, Capability.ECTOPHIAL, Capability.ICY_BASALT,
            Capability.STONY_BASALT, Capability.BRIMHAVEN_TABLET);

    private Map<Integer, Integer> carriedBefore;
    private List<ItemStack> bankBefore;
    private Integer regionBefore;

    private TravelTaken spent = TravelTaken.NONE;
    /** The next patch when the teleport was spent; the mark lasts while it stays next. */
    private String spentFor;
    private int ticksSinceSpent;
    private boolean landed;

    /** The teleport spent on the way to the next stop, or {@link TravelTaken#NONE}. */
    public TravelTaken taken() {
        return spent;
    }

    /** The patch the teleport was spent for, or null with none spent. */
    public String takenFor() {
        return spentFor;
    }

    /** Forget the teleport and every reading, as at logout. */
    public void clear() {
        forget();
        carriedBefore = null;
        bankBefore = null;
        regionBefore = null;
    }

    /** Put back a teleport recorded as spent, as landed. */
    public void restore(TravelTaken taken, String heading) {
        clear();
        spent = taken;
        spentFor = heading;
        landed = true;
    }

    /** One game tick, for a next stop whose route lands nowhere a teleport could. */
    public boolean tick(PlayerState state, String heading) {
        return tick(state, heading, null);
    }

    /**
     * One game tick.
     *
     * @param heading the patch the plan sends the player to next, or null for none
     * @param landing where that stop's route lands, or null for a route taken on an item.
     *     A teleport spent for one stop stays paid for the next while this is its place:
     *     the Falador park tree and the Falador farm are a walk apart, not two casts
     * @return whether {@link #taken()} or {@link #takenFor()} changed
     */
    public boolean tick(PlayerState state, String heading, Destination landing) {
        TravelTaken was = spent;
        String wasFor = spentFor;
        Map<Integer, Integer> carried = carried(state);
        Integer region = state.getRegionId();
        boolean jumped = regionBefore != null && region != null
                && !bordering(regionBefore, region);
        boolean banked = bankBefore != null && state.getBank() != bankBefore;

        if (!spent.isNone()) {
            ticksSinceSpent++;
            // Before the landing, no next patch says nothing: the plan may not be rebuilt yet.
            // After it, no next patch means the trip is done, and a mark left over would pay
            // for the first stop of the next one.
            boolean movedOn = heading == null ? landed : !heading.equals(spentFor);
            if (movedOn && landed && landing != null && spent.reached(landing)) {
                spentFor = heading;
                movedOn = false;
            }
            if (!landed && jumped) {
                landed = true;
            } else if (!landed && ticksSinceSpent > LANDING_TICKS || movedOn
                    || ticksSinceSpent > MAX_TICKS) {
                forget();
            }
        }
        if (carriedBefore != null && !banked) {
            TravelTaken now = spentBetween(carriedBefore, carried);
            if (!now.isNone()) {
                spent = now;
                spentFor = heading;
                ticksSinceSpent = 0;
                landed = jumped;
            }
        }

        carriedBefore = carried;
        bankBefore = state.getBank();
        regionBefore = region;
        return !spent.equals(was) || !Objects.equals(spentFor, wasFor);
    }

    private void forget() {
        spent = TravelTaken.NONE;
        spentFor = null;
        ticksSinceSpent = 0;
        landed = false;
    }

    /**
     * The teleport whose whole cost these losses are, or {@link TravelTaken#NONE}.
     *
     * <p>A spell matches when every rune lost is one it takes, in exactly its amount, and
     * every rune but the elementals was lost: a staff or tome pays those. A tablet matches
     * on one tablet gone. A travel item matches when one of its forms is gone and none
     * came in its place: a ring crumbling on its last charge, not a ring of dueling (8)
     * becoming a (7), which the plan still counts as a ring.
     */
    static TravelTaken spentBetween(Map<Integer, Integer> before, Map<Integer, Integer> after) {
        Map<Rune, Integer> runesLost = new HashMap<>();
        for (Rune rune : Rune.values()) {
            int lost = lost(before, after, rune.getItemId());
            if (lost > 0) {
                runesLost.put(rune, lost);
            }
        }
        Set<Destination> places = EnumSet.noneOf(Destination.class);
        Set<String> means = new HashSet<>();
        for (Destination destination : Destination.values()) {
            for (TeleportMeans way : Teleports.to(destination)) {
                if (way.isSpell() ? paidFor(way.getRunes(), runesLost)
                        : way.isTablet() && lost(before, after, way.getItemId()) == 1) {
                    places.add(destination);
                    means.add(way.getDisplayName());
                }
            }
        }
        // One spell or tablet, which may reach two places (Varrock and the Grand Exchange).
        // A law rune alone, with a staff paying the rest, fits half the standard book: that
        // is no telling which, and a wrong guess drops a teleport the player still needs.
        if (means.size() == 1) {
            return TravelTaken.to(places);
        }
        if (!means.isEmpty()) {
            return TravelTaken.NONE;
        }
        for (Capability capability : USED_UP) {
            CarryItems.Carry carry = CarryItems.forCapability(capability);
            if (carry != null && usedUp(carry.getItemIds(), before, after)) {
                return TravelTaken.using(capability);
            }
        }
        return TravelTaken.NONE;
    }

    private static boolean paidFor(Map<Rune, Integer> cost, Map<Rune, Integer> lost) {
        if (lost.isEmpty()) {
            return false;
        }
        for (Map.Entry<Rune, Integer> rune : lost.entrySet()) {
            if (!rune.getValue().equals(cost.get(rune.getKey()))) {
                return false;
            }
        }
        for (Rune rune : cost.keySet()) {
            if (!ELEMENTAL.contains(rune) && !lost.containsKey(rune)) {
                return false;
            }
        }
        return true;
    }

    private static boolean usedUp(int[] forms, Map<Integer, Integer> before,
            Map<Integer, Integer> after) {
        int lost = 0;
        for (int id : forms) {
            int change = after.getOrDefault(id, 0) - before.getOrDefault(id, 0);
            if (change > 0) {
                return false;
            }
            lost -= change;
        }
        return lost == 1;
    }

    private static int lost(Map<Integer, Integer> before, Map<Integer, Integer> after, int id) {
        return before.getOrDefault(id, 0) - after.getOrDefault(id, 0);
    }

    /** What the player has on them of every rune, tablet and travel item a route can spend. */
    private static Map<Integer, Integer> carried(PlayerState state) {
        Map<Integer, Integer> carried = new HashMap<>();
        for (int id : WATCHED) {
            int held = ItemCounts.countCarried(state, id, null);
            if (held > 0) {
                carried.put(id, held);
            }
        }
        return carried;
    }

    /** Whether two map regions touch, sides or corners: walking crosses only these. */
    static boolean bordering(int a, int b) {
        return Math.abs((a >> 8) - (b >> 8)) <= 1 && Math.abs((a & 0xFF) - (b & 0xFF)) <= 1;
    }

    private static final int[] WATCHED = watched();

    private static int[] watched() {
        Set<Integer> ids = new TreeSet<>();
        for (Rune rune : Rune.values()) {
            ids.add(rune.getItemId());
        }
        for (Destination destination : Destination.values()) {
            for (TeleportMeans means : Teleports.to(destination)) {
                if (means.isTablet()) {
                    ids.add(means.getItemId());
                }
            }
        }
        for (Capability capability : Capability.values()) {
            CarryItems.Carry carry = CarryItems.forCapability(capability);
            if (carry != null) {
                for (int id : carry.getItemIds()) {
                    ids.add(id);
                }
            }
        }
        return ids.stream().mapToInt(Integer::intValue).toArray();
    }
}
