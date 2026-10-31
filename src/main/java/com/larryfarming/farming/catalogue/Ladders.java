package com.larryfarming.farming.catalogue;

import com.larryfarming.farming.model.Approach;
import com.larryfarming.farming.model.RunStop;
import com.larryfarming.farming.model.RungOrder;
import com.larryfarming.farming.model.TravelOption;
import java.util.List;

/**
 * Every ladder the registry holds, ranked by its rungs' walks as far as the catalogue alone
 * can rank them ({@link RungOrder}).
 *
 * <p>A rung asking for an {@link Approach} has no total until the account says which way in
 * the player takes, so here it keeps its written place, and the planner ranks it per account.
 * Every other rung's figure is the same for everyone, so its place is settled once.
 *
 * <p>Reads only {@link Networks}, so a {@link Site} constant may call it while it is built.
 */
final class Ladders {

    private Ladders() {
    }

    /** The stop with its ladder ranked. */
    static RunStop ranked(RunStop stop) {
        return stop.withLadder(ranked(stop.getTravel()));
    }

    /** The ladder ranked, each rung leaning on an approach kept in its written place. */
    static List<TravelOption> ranked(List<TravelOption> ladder) {
        return List.copyOf(RungOrder.rank(ladder, new RungOrder.Figures() {
            @Override
            public int walk(int rung) {
                TravelOption option = ladder.get(rung);
                return asksForAnApproach(option) && option.hasWalkFigure()
                        ? TravelOption.UNMEASURED
                        : option.getWalk();
            }

            @Override
            public TravelOption.Cost cost(int rung) {
                return ladder.get(rung).getCost();
            }
        }));
    }

    /** Whether the rung's walk leaves out an approach the account decides. */
    static boolean asksForAnApproach(TravelOption rung) {
        for (Approach approach : Networks.APPROACHES) {
            if (rung.getRequires().contains(approach.getWay())) {
                return true;
            }
        }
        return false;
    }
}
