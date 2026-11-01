package com.larryfarming.farming.model;

import java.util.*;

/**
 * The order a ladder's rungs are tried in: fewest walked tiles first, over the whole route as
 * the player takes it.
 *
 * <ul>
 * <li>Rungs within {@link #TIE_BAND} tiles of the shortest walk not yet placed are tied, and
 * a tie goes to the cheaper rung: free, then a charge, then a consumable. Equal cost keeps
 * the order the ladder lists them in.</li>
 * <li>A rung with no figure keeps its place: it follows the last of the measured rungs listed
 * above it, or leads when none is.</li>
 * <li>A floor, which starts wherever the player is, comes last.</li>
 * </ul>
 *
 * <p>The tie band is anchored on the shortest walk in it rather than chained from rung to
 * rung, so a tie never spans more than the band, and which rungs tie depends on their walks
 * alone, not on the order they are listed in. This is a grouping, not a comparator: two
 * rungs within the band of each other can still fall in different ties.
 */
public final class RungOrder {

    /** Walks this many tiles apart or fewer are tied. */
    public static final int TIE_BAND = 3;

    private RungOrder() {
    }

    /**
     * What ranking reads from one rung, by its index in the ladder.
     *
     * <p>A walk is tiles, {@link TravelOption#UNMEASURED} or {@link TravelOption#FLOOR}; a
     * measured walk has a cost.
     */
    public interface Figures {
        int walk(int rung);

        TravelOption.Cost cost(int rung);
    }

    /** The ladder ranked by each rung's own figures. */
    public static List<TravelOption> rank(List<TravelOption> ladder) {
        return rank(ladder, new Figures() {
            @Override
            public int walk(int rung) {
                return ladder.get(rung).getWalk();
            }

            @Override
            public TravelOption.Cost cost(int rung) {
                return ladder.get(rung).getCost();
            }
        });
    }

    /** The ladder ranked by the figures given, which may differ from the rungs' own. */
    public static List<TravelOption> rank(List<TravelOption> ladder, Figures figures) {
        int[] order = order(ladder.size(), figures);
        List<TravelOption> ranked = new ArrayList<>(order.length);
        for (int index : order) {
            ranked.add(ladder.get(index));
        }
        return ranked;
    }

    /** The indices of {@code size} rungs in ranked order. */
    public static int[] order(int size, Figures figures) {
        int[] walks = new int[size];
        List<Integer> measured = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            walks[i] = figures.walk(i);
            if (walks[i] != TravelOption.UNMEASURED && walks[i] != TravelOption.FLOOR) {
                measured.add(i);
            }
        }
        measured.sort(Comparator.<Integer>comparingInt(i -> walks[i]).thenComparingInt(i -> i));
        List<Integer> ranked = new ArrayList<>(size);
        int from = 0;
        while (from < measured.size()) {
            int anchor = walks[measured.get(from)];
            int to = from;
            while (to < measured.size() && walks[measured.get(to)] - anchor <= TIE_BAND) {
                to++;
            }
            List<Integer> tied = new ArrayList<>(measured.subList(from, to));
            tied.sort(Comparator.<Integer>comparingInt(i -> figures.cost(i).ordinal()).thenComparingInt(i -> i));
            ranked.addAll(tied);
            from = to;
        }
        return withTheRestPlaced(ranked, walks);
    }

    /**
     * The measured rungs in rank, with each unmeasured rung after the last-ranked measured
     * rung listed above it, and the floors at the end.
     */
    private static int[] withTheRestPlaced(List<Integer> ranked, int[] walks) {
        int size = walks.length;
        int[] rankOf = new int[size];
        for (int position = 0; position < ranked.size(); position++) {
            rankOf[ranked.get(position)] = position;
        }
        // Slot 0 holds the rungs leading the ladder; slot p + 1 those after ranked position p.
        List<List<Integer>> after = new ArrayList<>(ranked.size() + 1);
        for (int i = 0; i <= ranked.size(); i++) {
            after.add(new ArrayList<>());
        }
        List<Integer> floors = new ArrayList<>();
        int lastAbove = -1;
        for (int i = 0; i < size; i++) {
            if (walks[i] == TravelOption.FLOOR) {
                floors.add(i);
            } else if (walks[i] == TravelOption.UNMEASURED) {
                after.get(lastAbove + 1).add(i);
            } else {
                lastAbove = Math.max(lastAbove, rankOf[i]);
            }
        }
        int[] order = new int[size];
        int next = 0;
        for (int index : after.get(0)) {
            order[next++] = index;
        }
        for (int position = 0; position < ranked.size(); position++) {
            order[next++] = ranked.get(position);
            for (int index : after.get(position + 1)) {
                order[next++] = index;
            }
        }
        for (int index : floors) {
            order[next++] = index;
        }
        return order;
    }

    /** The cost of a route made of two parts: the dearer of the two. */
    public static TravelOption.Cost dearer(TravelOption.Cost a, TravelOption.Cost b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.compareTo(b) >= 0 ? a : b;
    }
}
