package com.larryfarming.farming.engine;

import com.larryfarming.data.Tables;
import com.larryfarming.engine.ItemCounts;
import com.larryfarming.farming.detection.CapabilityResolver;
import com.larryfarming.farming.detection.CapabilityState;
import com.larryfarming.farming.detection.CapabilityVerdict;
import com.larryfarming.farming.detection.DiaryTiers;
import com.larryfarming.farming.model.Capability;
import com.larryfarming.farming.model.RunId;
import com.larryfarming.farming.model.Spellbook;
import com.larryfarming.model.PlayerState;
import com.larryfarming.model.QuestStatus;
import com.larryfarming.model.Skill;
import com.larryfarming.model.SkillState;
import com.larryfarming.snapshot.FarmingRunStatus;
import com.larryfarming.snapshot.FarmingUpgrade;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Experience;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.util.QuantityFormatter;

/**
 * What the player could work toward between runs: quests, diary tiers, skill levels, house
 * builds and gear, read from {@code goals.tsv}. Goals that open a patch come first, closest
 * first. After them, by the same order, come the goals that only shorten a walk and those
 * whose reward is off the runs, such as the Farmer's outfit.
 *
 * <p>A goal counts only the steps the table names for it, a quest's direct prerequisites
 * rather than its whole tree, so a long chain undercounts. Each nearer step that unlocks
 * something is a goal of its own, and ranks above it. Between goals the same number of steps
 * away, the one fewer experience points short ranks first, then the one opening more
 * patches, then the one saving more seconds.
 *
 * <p>A payoff that also needs a way in, such as Priest in Peril's Canifis patches, ranks by the
 * nearest way's missing steps added to the goal's own, and its sentence names that way.
 *
 * <p>The table's patch counts and seconds only rank goals. A goal's sentence names the place,
 * never those counts or seconds. A goal drops out once the player holds something its unless
 * column names, and once their own run already reaches every patch it opens. A goal that
 * shortens a walk stays, and says it opens the place to a player whose run reaches none of
 * its patches.
 *
 * <p>The list stays silent on what Larry cannot settle: a goal whose reward is unread, such
 * as a house never visited, or one switched off in the settings, never shows.
 */
@Slf4j
public final class GoalPlanner {

    private GoalPlanner() {
    }

    /** A goal further off than this many steps waits until its steps come within reach. */
    static final int MOST_STEPS = 2;

    /** The levels short a skill level counts as one step for. */
    static final int LEVELS_A_STEP = 10;

    /** Every goal, in table order. */
    static final List<Goal> GOALS = Tables.read("/com/larryfarming/goals.tsv", 15).stream().map(row -> new Goal(row.text(0),
                    steps(row.text(1)), steps(row.optionalText(2)), row.optionalText(3), row.integer(4), row.optionalText(6), row.integer(7),
                    row.integer(8), row.text(9), row.text(10), row.optionalText(11) == null ? List.of()
                            : Arrays.stream(row.text(11).split(" \\| ")).map(GoalPlanner::steps).collect(Collectors.toList()),
                    row.optionalText(12) == null ? Set.of() : Set.of(row.text(12).split("; ")),
                    row.optionalText(13) == null ? List.of() : List.of(row.text(13).split(" \\| ")), row.optionalText(14)))
            .collect(Collectors.toUnmodifiableList());

    /** Every goal by its key, for the ways in that name one. */
    private static final Map<String, Goal> BY_KEY = GOALS.stream().collect(Collectors.toMap(goal -> goal.key, goal -> goal));

    /** The last goals line logged, so an unchanged list logs once rather than on every plan. */
    private static final AtomicReference<String> LOGGED = new AtomicReference<>();

    /**
     * One thing a goal asks for or gives, as the table writes it: its kind, a space, then
     * what it names. A quest or started quest by name, a skill level ("MAGIC 66"), a diary
     * tier ("Ardougne Medium"), a capability, or the item ids of which owning any will do.
     * "iron " before a step asks it of an ironman only: a player who can trade buys the item.
     */
    @AllArgsConstructor
    static final class Step {
        final String kind;
        final String name;
        /** A skill step's skill and level; null and 0 for any other. */
        final Skill skill;
        final int level;
        final boolean ironOnly;

        static Step of(String text) {
            boolean iron = text.startsWith("iron ");
            String[] parts = (iron ? text.substring(5) : text).split(" ", 2);
            if (!parts[0].equals("skill")) {
                return new Step(parts[0], parts[1], null, 0, iron);
            }
            String[] level = parts[1].split(" ");
            return new Step(parts[0], parts[1], Skill.valueOf(level[0]), Integer.parseInt(level[1]), iron);
        }

        /**
         * Whether the player has it, LACK only when the game says so. A quest not yet read,
         * a capability Larry can't settle, such as an unseen house, or one switched off in
         * the settings, never reads LACK: it neither counts as a step nor names a goal.
         */
        CapabilityState met(PlayerState state, CapabilityResolver resolver) {
            if (ironOnly) {
                Integer mode = state.getVarbits().get(VarbitID.IRONMAN);
                if (mode == null || mode == 0) {
                    return mode == null ? CapabilityState.UNKNOWN : CapabilityState.HAVE;
                }
            }
            QuestStatus quest = state.getQuests().get(name);
            switch (kind) {
                case "quest":
                    return quest == null ? CapabilityState.UNKNOWN : held(quest == QuestStatus.COMPLETED);
                case "started":
                    return quest == null ? CapabilityState.UNKNOWN : held(quest != QuestStatus.NOT_STARTED);
                case "skill":
                    return held(state.getSkillLevel(skill) >= level);
                case "diary":
                    return DiaryTiers.completed(name.substring(0, name.lastIndexOf(' ')), tier(), state);
                case "has": {
                    // A declared house build holds; anything else not read from the game is
                    // unsettled, so a switched-off cape neither names a goal nor outdoes one.
                    CapabilityVerdict verdict = resolver.resolve(Capability.valueOf(name), state);
                    return verdict.getSource() == CapabilityVerdict.Source.DETECTED || verdict.getState() == CapabilityState.HAVE
                            ? verdict.getState() : CapabilityState.UNKNOWN;
                }
                case "owns":
                    // Owning reads the bank too, so before the bank is seen it is unsettled.
                    return Arrays.stream(name.split(" ")).anyMatch(id -> ItemCounts.ownsAny(state, Integer.parseInt(id)))
                            ? CapabilityState.HAVE : state.getBankLastSeen() == null ? CapabilityState.UNKNOWN : CapabilityState.LACK;
                default:
                    throw new IllegalArgumentException("no step kind " + kind);
            }
        }

        private String tier() {
            return name.substring(name.lastIndexOf(' ') + 1);
        }

        /**
         * How many steps this unmet step counts for: one, but a level one for every
         * {@link #LEVELS_A_STEP} levels short, so a 99 far off is no near goal.
         */
        int weight(PlayerState state) {
            return skill == null ? 1 : 1 + Math.max(0, level - state.getSkillLevel(skill) - 1) / LEVELS_A_STEP;
        }

        /** The experience points this step is short of, or 0 for a step that is no skill level. */
        long xpShort(PlayerState state) {
            SkillState held = skill == null ? null : state.getSkills().get(skill);
            return held == null ? 0 : Math.max(0, Experience.getXpForLevel(level) - held.getXp());
        }

        /** What to do about this step, mid-sentence: "finish Lost City", "reach Magic 66 (12.1K xp)". */
        String phrase(PlayerState state) {
            switch (kind) {
                case "has": {
                    // A house stands in one town, so a player moves it rather than gets one.
                    String what = Capability.valueOf(name).getDisplayName();
                    return what.startsWith("House in ") ? "move your house to " + what.substring("House in ".length())
                            : "get a " + Character.toLowerCase(what.charAt(0)) + what.substring(1);
                }
                case "skill":
                    return "reach " + skill.getDisplayName() + " " + level + " ("
                            + QuantityFormatter.quantityToRSDecimalStack(Math.toIntExact(xpShort(state))) + " xp)";
                case "diary":
                    return "finish the " + tier().toLowerCase(Locale.ROOT) + " " + name.substring(0, name.lastIndexOf(' ')) + " diary";
                case "started":
                    return "start " + name;
                default:
                    return "finish " + name;
            }
        }
    }

    private static CapabilityState held(boolean yes) {
        return yes ? CapabilityState.HAVE : CapabilityState.LACK;
    }

    /** One row of the table. */
    @AllArgsConstructor
    static final class Goal {
        final String key;
        /** Every step held means the goal is reached. */
        final List<Step> gives;
        final List<Step> requires;
        /** The goal that follows this one in its chain, shown only once this one is reached, or null. */
        final String next;
        final int iconItemId;
        /** The run the payoff helps, or null for a payoff on no run. */
        final String run;
        final int patches;
        final int seconds;
        /** What to do, mid-sentence: "make icy basalt". */
        final String action;
        /** One sentence on what it gives: "A shorter walk to Hosidius on the herb run." */
        final String payoff;
        /** What other goals give that leaves this one nothing to gain: holding any one group drops it. */
        final List<List<Step>> unless;
        /**
         * The labels of the patches it opens, or for a goal that shortens a walk, those it
         * opens instead for a player whose run reaches none of them; empty for neither.
         */
        final Set<String> opens;
        /**
         * The ways in, any one of which the payoff also needs: another goal by its key, or
         * steps. Priest in Peril opens Canifis only with a way in, such as fairy rings.
         * Empty for a payoff the goal's own steps reach.
         */
        final List<String> via;
        /** What a shorter walk says instead where it opens the patches in opens, or null. */
        final String opening;

        /** Whether the player already holds a way round this goal, so it would gain them nothing. */
        boolean outdone(PlayerState state, CapabilityResolver resolver) {
            return unless.stream().anyMatch(group -> group.stream().allMatch(step -> step.met(state, resolver) == CapabilityState.HAVE));
        }
    }

    /** A cell of steps split on "; ", or none for an empty cell. */
    private static List<Step> steps(String cell) {
        return cell == null ? List.of() : Arrays.stream(cell.split("; ")).map(Step::of).collect(Collectors.toList());
    }

    /**
     * The gear rows, then the goals within reach, closest first. One row stands for each
     * chain: a goal waits while the one before it in its chain is unreached.
     *
     * @param runs the runs list, so a goal paying off only on a run the player does not do
     *     (switched off, or still locked) never ranks
     * @param reached the labels of the patches each run's plan visits, as the runs list
     *     planned it: a goal opening only those has nothing to give, and a goal shortening a
     *     walk to a place with none of them opens it instead
     */
    public static List<FarmingUpgrade> after(List<FarmingUpgrade> gear, PlayerState state, FarmingInputs inputs, List<FarmingRunStatus> runs,
            Map<RunId, Set<String>> reached) {
        // Owning, not using: no book, no daily allowance and no cooldown has a say in it.
        CapabilityResolver resolver = new CapabilityResolver(Spellbook.ANY, null, Long.MAX_VALUE, inputs::patchAt, inputs.getHouse())
                .switchingOff(inputs.getPreferences().travelOff()).declaringHouse(inputs.getPreferences().houseDeclared());
        Set<String> doing = runs.stream().filter(run -> !RunSettings.STATUS_OFF.equals(run.getStatus()) && !RunStatus.LOCKED.name().equals(run.getStatus()))
                .map(FarmingRunStatus::getRunId).collect(Collectors.toSet());
        // A goal for a run they don't do is no link in a chain either, so the next shows.
        List<Goal> open = new ArrayList<>();
        // Each goal that could show and what became of it, for a bug report's debug log.
        List<String> fates = new ArrayList<>();
        for (Goal goal : GOALS) {
            if (goal.gives.stream().anyMatch(step -> step.met(state, resolver) == CapabilityState.LACK)
                    && (goal.run == null || doing.contains(goal.run))) {
                if (goal.outdone(state, resolver)) {
                    fates.add(goal.key + " outdone");
                } else {
                    open.add(goal);
                }
            }
        }
        Set<String> waiting = open.stream().map(goal -> goal.next).collect(Collectors.toSet());
        List<Standing> ranked = new ArrayList<>();
        for (Goal goal : open) {
            if (waiting.contains(goal.key)) {
                continue;
            }
            Standing standing = new Standing(goal);
            boolean read = standing.add(goal.requires, state, resolver) && wayIn(standing, state, resolver);
            String fate = null;
            if (!read) {
                // Unread, so how far off the goal is can't be told.
                standing.steps = MOST_STEPS + 1;
                fate = "unread";
            }
            // A patch this player's run already reaches by some other way is no reason to work
            // toward the goal. A walk it shortens to a place the run reaches none of the patches
            // at is a place it opens instead: Camelot Teleport opens Catherby for a player with
            // no other way there.
            Set<String> visits = goal.run == null ? Set.of() : reached.getOrDefault(RunId.valueOf(goal.run), Set.of());
            standing.opens = goal.opening != null && Collections.disjoint(visits, goal.opens);
            standing.patches = standing.opens ? goal.opens.size() : goal.patches;
            if (goal.opening == null && !goal.opens.isEmpty() && visits.containsAll(goal.opens)) {
                fate = "reached";
            } else if (standing.steps <= MOST_STEPS) {
                ranked.add(standing);
            }
            fates.add(goal.key + " " + (fate != null ? fate : standing.steps));
        }
        String line = String.join(", ", fates);
        if (!line.equals(LOGGED.getAndSet(line))) {
            log.debug("Goals by steps away: {}", line);
        }
        ranked.sort(Comparator.<Standing>comparingInt(s -> s.patches > 0 ? 0 : 1).thenComparingInt(s -> s.steps)
                .thenComparingLong(s -> s.xpShort).thenComparingInt(s -> -s.patches).thenComparingInt(s -> -s.goal.seconds));
        List<FarmingUpgrade> rows = new ArrayList<>(gear);
        for (Standing s : ranked) {
            rows.add(new FarmingUpgrade(FarmingUpgrade.Kind.GOAL, s.goal.iconItemId, s.goal.action, s.sentence(state)));
        }
        return rows;
    }

    /**
     * Ranks a goal by the nearest of its ways in, the first listed on a tie, and names that
     * way's first missing part for the sentence. A way is parts split on "; ": another goal
     * by its key, counted with what it requires, or a step. False when no way is held and one
     * is unread, since the unread one may be held. A way's own ways in are not followed.
     */
    private static boolean wayIn(Standing standing, PlayerState state, CapabilityResolver resolver) {
        Standing nearest = null;
        String nearestPhrase = null;
        int nearestParts = 0;
        boolean unread = false;
        for (String way : standing.goal.via) {
            Standing each = new Standing(standing.goal);
            String phrase = null;
            int parts = 0;
            boolean read = true;
            for (String part : way.split("; ")) {
                Goal other = BY_KEY.get(part);
                // A goal reached is held, whatever it asked on the way.
                if (other != null && other.gives.stream().allMatch(step -> step.met(state, resolver) == CapabilityState.HAVE)) {
                    continue;
                }
                int before = each.unmet;
                read &= each.add(other != null ? beyond(other, standing.goal) : List.of(Step.of(part)), state, resolver);
                if (each.unmet > before) {
                    parts++;
                    phrase = phrase != null ? phrase : other != null ? other.action : Step.of(part).phrase(state);
                }
            }
            if (!read) {
                unread = true;
            } else if (nearest == null || each.steps < nearest.steps) {
                nearest = each;
                nearestPhrase = phrase;
                nearestParts = parts;
            }
        }
        if (nearest != null && nearest.first == null) {
            return true;
        }
        if (nearest != null && !unread) {
            standing.steps += nearest.steps;
            standing.xpShort += nearest.xpShort;
            standing.via = nearestPhrase + (nearestParts > 1 ? " and " + (nearestParts - 1) + " more" : "");
        }
        return !unread;
    }

    /**
     * What another goal named as a way in asks: what it gives and requires, less what the goal
     * being shown already gives or requires, which it counts itself. Ghosts Ahoy as Priest in
     * Peril's way in doesn't count Priest in Peril again.
     */
    private static List<Step> beyond(Goal way, Goal shown) {
        List<Step> steps = new ArrayList<>(way.gives);
        steps.addAll(way.requires);
        steps.removeIf(step -> Stream.concat(shown.gives.stream(), shown.requires.stream())
                .anyMatch(own -> own.kind.equals(step.kind) && own.name.equals(step.name)));
        return steps;
    }

    /** How far one goal is from this player. */
    private static final class Standing {
        final Goal goal;
        /** Unmet steps, a far level counting for more than one; see {@link Step#weight}. */
        int steps;
        /** Unmet steps, each counted once, as the sentence names them. */
        int unmet;
        long xpShort;
        /** The first unmet step in the table's order, or null with none. */
        Step first;
        /** What the nearest way in still asks, mid-sentence, or null when the player holds one. */
        String via;
        /** Whether a walk it shortens goes to a place this player's run misses, so it says opening. */
        boolean opens;
        /** The patches it opens for this player, which rank it. */
        int patches;

        Standing(Goal goal) {
            this.goal = goal;
        }

        /** Counts the unmet steps; false at the first unread one, since then how far off can't be told. */
        boolean add(List<Step> needed, PlayerState state, CapabilityResolver resolver) {
            for (Step step : needed) {
                CapabilityState met = step.met(state, resolver);
                if (met == CapabilityState.UNKNOWN) {
                    return false;
                }
                if (met == CapabilityState.LACK) {
                    steps += step.weight(state);
                    unmet++;
                    xpShort += step.xpShort(state);
                    if (first == null) {
                        first = step;
                    }
                }
            }
            return true;
        }

        /**
         * "Make icy basalt now. …", or "Reach Construction 72 (12.1K xp), then add Weiss to a
         * Portal Nexus in your house. …", naming the first step left and counting the rest.
         * A way in still lacking closes the payoff: "… on the herb run once you also finish
         * Ghosts Ahoy for the ectophial."
         */
        String sentence(PlayerState state) {
            String lead = first == null ? goal.action + " now"
                    : first.phrase(state) + (unmet > 1 ? " and " + (unmet - 1) + " more" : "") + ", then " + goal.action;
            String says = opens ? goal.opening : goal.payoff;
            String payoff = via == null ? says : says.substring(0, says.length() - 1) + " once you also " + via + ".";
            return Character.toUpperCase(lead.charAt(0)) + lead.substring(1) + ". " + payoff;
        }
    }
}
