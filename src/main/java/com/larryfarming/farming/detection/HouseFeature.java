package com.larryfarming.farming.detection;

import java.util.Arrays;
import java.util.Set;
import net.runelite.api.gameval.ObjectID;

/**
 * The built furniture a house scan looks for: what the farm runs' house routes stand on.
 *
 * <p>Each is matched by the object ids of the built thing, one per frame or skin. Build-mode
 * hotspots carry ids of their own and match nothing here: the portal spaces
 * ({@code POH_TELEROOM_1} to {@code _3} and {@code _7}), the Nexus spaces
 * ({@code POH_TELENEXUS_*}, {@code POH_NEXUS_4_AMULET}, {@code POH_NEXUS_5_AMULET}) and the
 * superior garden's centrepiece space ({@code POH_SUPERIOR_GARDEN_HOTSPOT_TREERING}).
 *
 * <p>None of these portals is a multiloc, so the spawned id is the built one.
 */
public enum HouseFeature {

    PORTAL_WEISS(ObjectID.POH_PORTAL_TEAK_WEISS, ObjectID.POH_PORTAL_MAG_WEISS,
            ObjectID.POH_PORTAL_MARBLE_WEISS, ObjectID.POH_PORTAL_LEAGUE_5_WEISS),
    /**
     * The Troll Stronghold portal. Not the Trollheim portal
     * ({@code POH_PORTAL_*_TROLLHEIM}), which lands on the mountain far from the patch.
     */
    PORTAL_TROLL_STRONGHOLD(ObjectID.POH_PORTAL_TEAK_STRONGHOLD,
            ObjectID.POH_PORTAL_MAG_STRONGHOLD, ObjectID.POH_PORTAL_MARBLE_STRONGHOLD,
            ObjectID.POH_PORTAL_LEAGUE_5_STRONGHOLD),
    PORTAL_KOUREND(ObjectID.POH_PORTAL_TEAK_KOUREND, ObjectID.POH_PORTAL_MAG_KOUREND,
            ObjectID.POH_PORTAL_MARBLE_KOUREND, ObjectID.POH_PORTAL_LEAGUE_5_KOUREND),
    PORTAL_FENKENSTRAIN(ObjectID.POH_PORTAL_TEAK_FENKENSTRAIN,
            ObjectID.POH_PORTAL_MAG_FENKENSTRAIN, ObjectID.POH_PORTAL_MARBLE_FENKENSTRAIN,
            ObjectID.POH_PORTAL_LEAGUE_5_FENKENSTRAIN),
    PORTAL_HARMONY(ObjectID.POH_PORTAL_TEAK_HARMONY_ISLAND,
            ObjectID.POH_PORTAL_MAG_HARMONY_ISLAND, ObjectID.POH_PORTAL_MARBLE_HARMONY_ISLAND,
            ObjectID.POH_PORTAL_LEAGUE_5_HARMONY_ISLAND),
    /**
     * A Portal Nexus of any tier. It proves the Nexus stands, not what it is attuned to,
     * which the slot varbits answer ({@link PortalNexus}).
     */
    PORTAL_NEXUS(ObjectID.POH_NEXUS_PORTAL_1, ObjectID.POH_NEXUS_PORTAL_2,
            ObjectID.POH_NEXUS_PORTAL_3, ObjectID.POH_NEXUS_PORTAL_LEAGUE_5),
    /** The spirit tree, alone or as the spiritual fairy tree, in every skin. */
    SPIRIT_TREE(ObjectID.POH_SPIRIT_TREE, ObjectID.XMAS20_POH_SPIRIT_TREE,
            ObjectID.LEAGUE_5_POH_SPIRIT_TREE, ObjectID.POH_SPIRIT_RING,
            ObjectID.XMAS20_POH_SPIRIT_RING, ObjectID.LEAGUE_5_POH_SPIRIT_RING),
    /** The fairy ring, alone or as the spiritual fairy tree, which is both. */
    FAIRY_RING(ObjectID.POH_FAIRY_RING, ObjectID.POH_SPIRIT_RING,
            ObjectID.XMAS20_POH_SPIRIT_RING, ObjectID.LEAGUE_5_POH_SPIRIT_RING);

    private static final HouseFeature[] ALL = values();

    /** Every id any feature matches, sorted, so a miss costs one binary search. */
    private static final int[] ANY_ID = anyId();

    private final int[] objectIds;

    HouseFeature(int... objectIds) {
        this.objectIds = objectIds;
    }

    private static int[] anyId() {
        return Arrays.stream(values())
                .flatMapToInt(feature -> Arrays.stream(feature.objectIds))
                .distinct()
                .sorted()
                .toArray();
    }

    /**
     * Adds what this object proves built, which for most objects is nothing. Allocates
     * nothing on a miss: every object in every loaded scene passes through here.
     */
    public static void addBuilt(int objectId, Set<HouseFeature> into) {
        if (Arrays.binarySearch(ANY_ID, objectId) < 0) {
            return;
        }
        for (HouseFeature feature : ALL) {
            for (int id : feature.objectIds) {
                if (id == objectId) {
                    into.add(feature);
                    break;
                }
            }
        }
    }
}
