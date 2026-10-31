package com.larryfarming.farming.catalogue;

import com.larryfarming.farming.model.FarmingRun;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
        return assemble(Arrivals.seats());
    }

    /** Every run, each ordered by these seats. */
    static List<FarmingRun> assemble(Seats seats) {
        List<FarmingRun> runs = new ArrayList<>(Families.ALL.size());
        for (Families.Family family : Families.ALL) {
            runs.add(assemble(family, seats));
        }
        return List.copyOf(runs);
    }

    private static FarmingRun assemble(Families.Family family, Seats seats) {
        List<Site> sites = new ArrayList<>();
        for (Site site : Site.values()) {
            if (site.family() == family.getId() && site.books().contains(family.getBook())) {
                sites.add(site);
            }
        }
        sites.sort(Comparator.<Site>comparingInt(
                        site -> seats.seat(family.getBook(), site.arrival()))
                .thenComparing(Comparator.naturalOrder()));
        FarmingRun.FarmingRunBuilder run = FarmingRun.builder()
                .id(family.getId())
                .spellbook(family.getBook())
                .unlock(family.getUnlock())
                .pacingCrop(family.getPacingCrop())
                .respawnTown(family.getRespawnTown())
                .seedPatchType(family.getSeedPatchType());
        for (Site site : sites) {
            run.stop(site.stop(family.getBook()));
        }
        return run.build();
    }
}
