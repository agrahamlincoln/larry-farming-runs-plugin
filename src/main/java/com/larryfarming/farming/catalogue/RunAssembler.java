package com.larryfarming.farming.catalogue;

import com.larryfarming.farming.model.FarmingRun;
import java.util.*;

/**
 * Builds every run from the registry: a family's data from {@link Families}, and the sites
 * of that family on that book from {@link Site}, each worked by that book's stop.
 *
 * <p>A run lists its sites in its book's seat order: by the seat of each site's arrival on
 * the family's book, and at one arrival in the order {@link Site} declares them.
 */
final class RunAssembler {

    private RunAssembler() {
    }

    /** Every run, in the order {@link Families#ALL} lists the families. */
    static List<FarmingRun> assemble() {
        List<FarmingRun> runs = new ArrayList<>(Families.ALL.size());
        for (Families.Family family : Families.ALL) {
            runs.add(assemble(family));
        }
        return List.copyOf(runs);
    }

    private static FarmingRun assemble(Families.Family family) {
        List<Site> sites = new ArrayList<>();
        for (Site site : Site.values()) {
            if (site.family() == family.getId() && site.books().contains(family.getBook())) {
                sites.add(site);
            }
        }
        // Stable, so sites at one arrival keep their declared order.
        sites.sort(Comparator.comparingInt(site -> Arrivals.seat(family.getBook(), site.arrival())));
        FarmingRun.FarmingRunBuilder run = FarmingRun.builder().id(family.getId()).spellbook(family.getBook()).unlock(family.getUnlock())
                .respawnTown(family.getRespawnTown()).seedPatchType(family.getSeedPatchType());
        for (Site site : sites) {
            run.stop(site.stop(family.getBook()));
        }
        return run.build();
    }
}
