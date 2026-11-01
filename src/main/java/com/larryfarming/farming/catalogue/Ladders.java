package com.larryfarming.farming.catalogue;

import com.larryfarming.farming.model.*;
import java.util.Collection;
import java.util.List;

/**
 * Every ladder the registry holds, ranked by its rungs' walks as far as the catalogue alone
 * can rank them ({@link RungOrder}).
 *
 * <p>A rung asking for an {@link Approach} has no total until the account says which way in
 * the player takes, so here it keeps its written place, and the planner ranks it per account.
 * Every other rung's figure is the same for everyone, so its place is settled once.
 */
final class Ladders {

    private Ladders() {
    }

    /** The stop with its ladder ranked. */
    static RunStop ranked(RunStop stop, Collection<Approach> approaches) {
        return stop.withLadder(ranked(stop.getTravel(), approaches));
    }

    /** The ladder ranked, each rung leaning on one of these approaches kept in its place. */
    static List<TravelOption> ranked(List<TravelOption> ladder, Collection<Approach> approaches) {
        return List.copyOf(RungOrder.rank(ladder, new RungOrder.Figures() {
            @Override
            public int walk(int rung) {
                TravelOption option = ladder.get(rung);
                return asksForAnApproach(option, approaches) && option.hasWalkFigure() ? TravelOption.UNMEASURED : option.getWalk();
            }

            @Override
            public TravelOption.Cost cost(int rung) {
                return ladder.get(rung).getCost();
            }
        }));
    }

    private static boolean asksForAnApproach(TravelOption rung, Collection<Approach> approaches) {
        for (Approach approach : approaches) {
            if (rung.getRequires().contains(approach.getWay())) {
                return true;
            }
        }
        return false;
    }
}
