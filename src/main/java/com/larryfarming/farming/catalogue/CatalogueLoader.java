package com.larryfarming.farming.catalogue;

import com.larryfarming.data.Tables;
import com.larryfarming.engine.farming.Crop;
import com.larryfarming.farming.model.*;
import com.larryfarming.model.Skill;
import java.util.*;

/**
 * Reads the stops table: the approaches onto the travel networks, the arrivals in circuit
 * order, and every site with the stop each spellbook works it by.
 *
 * <p>Each line is a verb and its cells. A stop's lines name the {@link RunStop.Builder}
 * method they call ({@code at}, {@code accessGate}, {@code skipIfUnreachable},
 * {@code plants}, ...). A rung line names the {@link TravelOption} it makes ({@code via},
 * {@code to}, {@code swapTo}), and the lines after it until the next rung
 * change that rung ({@code walks}, {@code banks}, {@code fare}, {@code floor}, ...). Rungs
 * under an {@code arrival} line are its combined chain.
 *
 * <p>A requirement is a capability's name, {@code reach:DESTINATION},
 * {@code swap:DESTINATION}, {@code patch:KEY}, {@code zone:ZONE}, an approach's way as
 * {@code @NAME}, or {@code all(...)} and {@code any(...)} over those, comma-separated.
 *
 * <p>The file keeps each ladder as written; {@link Ladders} ranks them here.
 */
final class CatalogueLoader {

    private static final String RESOURCE = "/com/larryfarming/stops.tsv";

    /** The approach whose way is some way to stand at a fairy ring. */
    static final String FAIRY_RING_APPROACH = "RING";

    private static final CatalogueLoader LOADED = new CatalogueLoader(RESOURCE);

    /** Every approach a rung's walk leaves out, by the name rungs cite it by, in file order. */
    final Map<String, Approach> approaches;
    /** The same approaches, in the same order. */
    final List<Approach> approachList;
    /** The way of the {@link #FAIRY_RING_APPROACH}, the instance every ring rung holds. */
    final Requirement toAfairyRing;
    /** Every arrival, in circuit order. */
    final List<Arrival> circuit;
    /** Each arrival's combined chain, ranked; empty for most. */
    final Map<Arrival, List<TravelOption>> combined;
    /** Every site, in declaration order. */
    final List<Site> sites;
    /** The guild patches a contract can name. */
    final List<PatchRef> contractPatches;
    /** The patch that settles each capability that is a patch question. */
    final Map<Capability, PatchRef> patchFor;

    private CatalogueLoader(String resource) {
        Parser parser = new Parser();
        for (Tables.Row row : Tables.read(resource)) {
            try {
                parser.apply(row);
            } catch (IllegalArgumentException e) {
                // A model check refusing a value names no line; the row's error does.
                throw located(row, e);
            }
        }
        parser.finishSite();
        approaches = Collections.unmodifiableMap(parser.approaches);
        approachList = List.copyOf(approaches.values());
        Approach ring = approaches.get(FAIRY_RING_APPROACH);
        if (ring == null) {
            throw new IllegalStateException(resource + ": no approach " + FAIRY_RING_APPROACH);
        }
        toAfairyRing = ring.getWay();
        circuit = List.copyOf(parser.circuit);
        Map<Arrival, List<TravelOption>> ranked = new EnumMap<>(Arrival.class);
        parser.combined.forEach((arrival, chain) -> ranked.put(arrival, Ladders.ranked(chain, approaches.values())));
        combined = Collections.unmodifiableMap(ranked);
        sites = List.copyOf(parser.sites);
        contractPatches = List.copyOf(parser.contractPatches);
        patchFor = Collections.unmodifiableMap(parser.patchFor);
    }

    static CatalogueLoader loaded() {
        return LOADED;
    }

    /** {@code cause}'s message, located at {@code row}. */
    private static IllegalStateException located(Tables.Row row, RuntimeException cause) {
        IllegalStateException located = row.error(cause.getMessage());
        located.initCause(cause);
        return located;
    }

    /** One pass over the rows; each line adds to the approach, site, stop or rung above it. */
    private static final class Parser {

        final Map<String, Approach> approaches = new LinkedHashMap<>();
        final List<Arrival> circuit = new ArrayList<>();
        final Map<Arrival, List<TravelOption>> combined = new EnumMap<>(Arrival.class);
        final List<Site> sites = new ArrayList<>();
        final List<PatchRef> contractPatches = new ArrayList<>();
        final Map<Capability, PatchRef> patchFor = new EnumMap<>(Capability.class);

        private String approach;
        private List<Approach.Leg> legs;

        /** Where rung lines go: an arrival's combined chain or the open stop's ladder. */
        private List<TravelOption> rungs;

        private final Set<String> siteKeys = new HashSet<>();
        private final Set<String> switchKeys = new HashSet<>();
        private Tables.Row siteRow;
        private String siteKey;
        private RunId family;
        private Map<Spellbook, RunStop> siteStops;
        private Tables.Row attributes;
        private final List<PatchAttributes.BonusTier> yieldTiers = new ArrayList<>();
        private final List<PatchAttributes.BonusTier> xpTiers = new ArrayList<>();
        private SeedPolicy.DiaryOverride claim;
        private Tables.Row siteSwitch;

        private Tables.Row stopRow;
        private RunStop.Builder stop;
        private List<Spellbook> books;

        void apply(Tables.Row row) {
            String verb = row.text(0);
            switch (verb) {
                case "approach":
                    finishSite();
                    approach = row.requireWidth(2).text(1);
                    if (approaches.containsKey(approach)) {
                        throw row.error("a second approach " + approach);
                    }
                    legs = new ArrayList<>();
                    break;
                case "leg":
                    if (legs == null) {
                        throw row.error("a leg outside an approach");
                    }
                    legs.add(Approach.leg(requirement(row.requireWidth(4), 1), row.integer(2), row.constant(TravelOption.Cost.class, 3)));
                    approaches.put(approach, Approach.of(legs.toArray(new Approach.Leg[0])));
                    break;
                case "arrival":
                    finishSite();
                    Arrival arrival = row.requireWidth(2).constant(Arrival.class, 1);
                    circuit.add(arrival);
                    rungs = new ArrayList<>();
                    combined.put(arrival, rungs);
                    break;
                case "contract":
                    finishSite();
                    rungs = null;
                    contractPatches.add(patch(row, row.requireWidth(2).text(1)));
                    break;
                case "patchFor":
                    finishSite();
                    rungs = null;
                    patchFor.put(row.requireWidth(3).constant(Capability.class, 1), patch(row, row.text(2)));
                    break;

                // --- A site and what is true of its patch ---------------------------
                case "site":
                    finishSite();
                    siteKey = row.requireWidth(3).text(1);
                    if (!siteKeys.add(siteKey)) {
                        throw row.error("a second site " + siteKey);
                    }
                    siteRow = row;
                    family = row.constant(RunId.class, 2);
                    siteStops = new EnumMap<>(Spellbook.class);
                    rungs = null;
                    break;
                case "switch":
                    openSite(row);
                    siteSwitch = row.requireWidth(3);
                    if (!switchKeys.add(row.text(1))) {
                        throw row.error("a second switch " + row.text(1));
                    }
                    break;
                case "attributes":
                    openSite(row);
                    attributes = row.requireWidth(7);
                    break;
                case "yieldTier":
                case "xpTier":
                    if (attributes == null) {
                        throw row.error("a bonus tier before the site's attributes");
                    }
                    row.requireWidth(4);
                    (verb.equals("yieldTier") ? yieldTiers : xpTiers).add(
                            new PatchAttributes.BonusTier(row.text(1), row.text(2), row.integer(3)));
                    break;
                case "claim":
                    openSite(row);
                    claim = new SeedPolicy.DiaryOverride(row.requireWidth(5).text(1), row.text(2),
                            patch(row, row.text(3)), row.constant(Crop.class, 4));
                    break;

                // --- A stop, one RunStop.Builder call a line ------------------------
                case "stop":
                    openSite(row);
                    finishStop();
                    row.requireWidth(3);
                    books = new ArrayList<>();
                    for (String book : row.text(1).split(",")) {
                        books.add(Enum.valueOf(Spellbook.class, book));
                    }
                    String[] keys = row.text(2).split(",");
                    PatchRef[] patches = new PatchRef[keys.length];
                    for (int i = 0; i < keys.length; i++) {
                        patches[i] = patch(row, keys[i]);
                    }
                    stop = RunStop.builder(patches);
                    stopRow = row;
                    rungs = new ArrayList<>();
                    break;
                case "at":
                    stop(row).at(row.requireWidth(2).constant(Arrival.class, 1));
                    break;
                case "accessGate":
                    stop(row).accessGate(row.requireWidth(2).constant(Capability.class, 1));
                    break;
                case "gate":
                    stop(row).gate(UnlockGate.builder().skill(row.requireWidth(7).optionalConstant(Skill.class, 1)).level(row.integer(2))
                            .boostedFromLevel(row.integer(3)).recommendedFromLevel(row.integer(4)).diaryArea(nullable(row, 5))
                            .diaryTier(nullable(row, 6)).build());
                    break;
                case "actions":
                    String[] names = row.requireWidth(2).text(1).split(",");
                    RunStop.StopAction[] actions = new RunStop.StopAction[names.length];
                    for (int i = 0; i < names.length; i++) {
                        actions[i] = Enum.valueOf(RunStop.StopAction.class, names[i]);
                    }
                    stop(row).actions(actions);
                    break;
                case "plants":
                    stop(row).plants(row.requireWidth(2).constant(Crop.class, 1));
                    break;
                case "optional":
                    stop(row).optional(row.requireWidth(2).text(1));
                    break;
                case "errand":
                    stop(row).errand(row.requireWidth(2).text(1));
                    break;
                case "everyNthRun":
                    stop(row).everyNthRun(row.requireWidth(2).integer(1));
                    break;
                case "standingCrop":
                    stop(row.requireWidth(1)).standingCrop();
                    break;
                case "skipIfUnreachable":
                    stop(row.requireWidth(1)).skipIfUnreachable();
                    break;
                case "noAlternative":
                    stop(row.requireWidth(1)).noAlternative();
                    break;
                case "noFallbackGiven":
                    stop(row.requireWidth(1)).noFallbackGiven();
                    break;

                // --- A rung, and the lines that change the rung above them ----------
                case "via":
                    rungs(row).add(TravelOption.via(requirement(row, 1), row.requireWidth(3).text(2)));
                    break;
                case "to":
                    Destination to = row.constant(Destination.class, 1);
                    rungs(row).add(row.width() == 4 ? TravelOption.to(to, requirement(row, 2), row.text(3))
                            : TravelOption.to(to, row.requireWidth(3).text(2)));
                    break;
                case "swapTo":
                    rungs(row).add(TravelOption.swapTo(row.constant(Destination.class, 1), row.requireWidth(3).text(2)));
                    break;
                case "walks":
                    change(row, last(row).withWalk(row.requireWidth(3).integer(1), row.constant(TravelOption.Cost.class, 2)));
                    break;
                case "banks":
                case "banksUpstairs":
                    change(row, last(row).withBank(row.requireWidth(3).integer(1), verb.equals("banksUpstairs"),
                            row.constant(Bank.class, 2)));
                    break;
                case "fare":
                    change(row, last(row).withFare(row.requireWidth(2).integer(1)));
                    break;
                case "floor":
                    change(row, last(row.requireWidth(1)).asFloor());
                    break;
                case "onlyWhenCertain":
                    change(row, last(row.requireWidth(1)).onlyWhenCertain());
                    break;
                case "onlyFromAstart":
                    change(row, last(row.requireWidth(1)).onlyFromAstart());
                    break;
                default:
                    throw row.error("no verb " + verb);
            }
        }

        private void openSite(Tables.Row row) {
            if (siteKey == null) {
                throw row.error("no site above this line");
            }
        }

        private RunStop.Builder stop(Tables.Row row) {
            if (stop == null) {
                throw row.error("no stop above this line");
            }
            return stop;
        }

        private List<TravelOption> rungs(Tables.Row row) {
            if (rungs == null) {
                throw row.error("a rung outside an arrival or a stop");
            }
            return rungs;
        }

        private TravelOption last(Tables.Row row) {
            if (rungs(row).isEmpty()) {
                throw row.error("no rung above this line");
            }
            return rungs.get(rungs.size() - 1);
        }

        private void change(Tables.Row row, TravelOption changed) {
            rungs(row).set(rungs.size() - 1, changed);
        }

        private void finishStop() {
            if (stop == null) {
                return;
            }
            RunStop built;
            try {
                built = stop.chain(rungs).build();
            } catch (IllegalArgumentException | IllegalStateException e) {
                // The builder checks the whole stop, so its errors belong to the stop line.
                throw located(stopRow, e);
            }
            for (Spellbook book : books) {
                siteStops.put(book, Ladders.ranked(built, approaches.values()));
            }
            stop = null;
            rungs = null;
        }

        void finishSite() {
            finishStop();
            if (siteKey == null) {
                return;
            }
            PatchAttributes.Entry entry = attributes == null ? null : new PatchAttributes.Entry(attributes.flag(1, "immune"),
                            nullable(attributes, 2), nullable(attributes, 3), nullable(attributes, 4), List.copyOf(yieldTiers),
                            List.copyOf(xpTiers), attributes.flag(5, "easy"), attributes.flag(6, "reserved"));
            try {
                sites.add(new Site(siteKey, family, siteStops, entry, claim, siteSwitch == null ? null : siteSwitch.text(1),
                        siteSwitch == null ? null : siteSwitch.text(2)));
            } catch (IllegalStateException e) {
                throw located(siteRow, e);
            }
            siteKey = null;
            attributes = null;
            yieldTiers.clear();
            xpTiers.clear();
            claim = null;
            siteSwitch = null;
        }

        private static String nullable(Tables.Row row, int column) {
            String text = row.text(column);
            return text.isEmpty() ? null : text;
        }

        private static PatchRef patch(Tables.Row row, String key) {
            try {
                return PatchRef.named(key);
            } catch (IllegalArgumentException e) {
                throw row.error(e.getMessage());
            }
        }

        private Requirement requirement(Tables.Row row, int column) {
            String text = row.text(column);
            int[] at = {0};
            Requirement parsed = requirement(row, text, at);
            if (at[0] != text.length()) {
                throw row.error("trailing text in requirement " + text);
            }
            return parsed;
        }

        /** The requirement starting at {@code at[0]}, leaving {@code at[0]} just past it. */
        private Requirement requirement(Tables.Row row, String text, int[] at) {
            int start = at[0];
            while (at[0] < text.length() && "(),".indexOf(text.charAt(at[0])) < 0) {
                at[0]++;
            }
            String word = text.substring(start, at[0]);
            if (at[0] < text.length() && text.charAt(at[0]) == '(') {
                List<Requirement> parts = new ArrayList<>();
                do {
                    at[0]++;
                    parts.add(requirement(row, text, at));
                } while (at[0] < text.length() && text.charAt(at[0]) == ',');
                if (at[0] >= text.length() || text.charAt(at[0]) != ')') {
                    throw row.error("unclosed " + word + "( in " + text);
                }
                at[0]++;
                Requirement[] array = parts.toArray(new Requirement[0]);
                if (word.equals("all")) {
                    return Requirement.all(array);
                }
                if (word.equals("any")) {
                    return Requirement.any(array);
                }
                throw row.error("no requirement group " + word);
            }
            if (word.startsWith("@")) {
                Approach named = approaches.get(word.substring(1));
                if (named == null) {
                    throw row.error("no approach " + word + " above this line");
                }
                return named.getWay();
            }
            int colon = word.indexOf(':');
            String value = word.substring(colon + 1);
            try {
                switch (colon < 0 ? "" : word.substring(0, colon)) {
                    case "":
                        return Requirement.cap(Capability.valueOf(word));
                    case "reach":
                        return Requirement.reach(Destination.valueOf(value));
                    case "swap":
                        return Requirement.reachBySwap(Destination.valueOf(value));
                    case "patch":
                        return Requirement.reachedPatch(PatchRef.named(value));
                    case "zone":
                        return Requirement.standingIn(Zone.valueOf(value));
                    default:
                        throw row.error("no requirement kind in " + word);
                }
            } catch (IllegalArgumentException e) {
                throw row.error("no requirement " + word);
            }
        }
    }
}
