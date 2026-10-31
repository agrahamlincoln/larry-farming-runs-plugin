package com.larryfarming;

import com.larryfarming.engine.farming.Crop;
import com.larryfarming.farming.model.Capability;
import com.larryfarming.farming.model.FarmingKeys;
import com.larryfarming.farming.model.FarmingPreferences;
import com.larryfarming.farming.model.HouseItem;
import com.larryfarming.farming.model.Preset;
import com.larryfarming.farming.model.TravelMethod;
import com.larryfarming.persistence.ConfigStore;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Reads the saved farming settings into the preferences the planner works on, and writes
 * the ones the farming tab changes.
 *
 * <p>Every key comes from {@link FarmingKeys}. An unsaved key reads as the default, so the
 * defaults live in {@link Preset#DEFAULT} alone.
 * {@link LarryConfig}'s declared defaults must agree with them.
 *
 * <p>Values are config strings: "true" or "false" for every key but three. The favourite
 * herb holds a herb's {@link Crop} name or {@link FarmingKeys#NO_FAVOURITE}, and the two
 * compost thresholds each hold a whole number.
 */
final class ConfiguredCrops
{
	private ConfiguredCrops()
	{
	}

	/**
	 * Whether this changed setting is one the farm-run planner cares about.
	 *
	 * <p>The plugin saves its own state into the same config group, so a listener matching
	 * on the group alone would re-plan on every save.
	 */
	static boolean isCropKey(String key)
	{
		return key != null && FarmingKeys.all().contains(key);
	}

	static FarmingPreferences from(ConfigStore store)
	{
		if (store == null)
		{
			return FarmingPreferences.defaults();
		}
		return read(key -> store.getConfiguration(LarryConfig.GROUP, key));
	}

	/**
	 * The preferences a saved set of values describes, each missing key taking the
	 * default, as {@link #from(ConfigStore)} reads an unsaved one.
	 */
	static FarmingPreferences fromValues(Map<String, String> values)
	{
		return read(values::get);
	}

	private static FarmingPreferences read(UnaryOperator<String> saved)
	{
		FarmingPreferences defaults = FarmingPreferences.defaults();
		Set<Crop> enabled = EnumSet.noneOf(Crop.class);
		for (Map.Entry<Crop, String> tick : FarmingKeys.CROPS.entrySet())
		{
			if (read(saved, tick.getValue(), defaults.allows(tick.getKey())))
			{
				enabled.add(tick.getKey());
			}
		}
		Set<TravelMethod> off = EnumSet.noneOf(TravelMethod.class);
		for (TravelMethod method : TravelMethod.values())
		{
			if (!read(saved, method.getKey(), defaults.uses(method)))
			{
				off.add(method);
			}
		}
		Set<String> sitesOff = new HashSet<>();
		for (String site : FarmingKeys.SITES)
		{
			if (!read(saved, site, defaults.visits(site)))
			{
				sitesOff.add(site);
			}
		}
		Set<Capability> declared = EnumSet.noneOf(Capability.class);
		for (HouseItem item : HouseItem.values())
		{
			if (read(saved, item.getKey(), defaults.declares(item)))
			{
				declared.add(item.getCapability());
			}
		}
		String favourite = saved.apply(FarmingKeys.FAVOURITE_HERB);
		return defaults.toBuilder()
			.enabled(enabled)
			.picksFruitInPassing(read(saved, FarmingKeys.PICK_FRUIT_IN_PASSING,
				defaults.picksFruitInPassing()))
			.worksCompostBins(read(saved, FarmingKeys.WORK_COMPOST_BINS,
				defaults.worksCompostBins()))
			.travelOff(off)
			.sitesOff(sitesOff)
			.houseDeclared(declared)
			.favouriteHerb(favourite == null ? defaults.favouriteHerb() : herbNamed(favourite))
			.plantsBestSeedsEverywhere(read(saved, FarmingKeys.BEST_SEEDS_EVERYWHERE,
				defaults.plantsBestSeedsEverywhere()))
			.herbRunFirst(read(saved, FarmingKeys.HERB_RUN_FIRST, defaults.herbRunFirst()))
			.waitsForEveryPatch(read(saved, FarmingKeys.WAIT_FOR_EVERY_PATCH,
				defaults.waitsForEveryPatch()))
			.fruitTreesWaitForSaplings(read(saved, FarmingKeys.FRUIT_TREES_WAIT_FOR_SAPLINGS,
				defaults.fruitTreesWaitForSaplings()))
			.replantsGrownTrees(read(saved, FarmingKeys.REPLANT_GROWN_TREES,
				defaults.replantsGrownTrees()))
			.herbRunsWaitForLevel(read(saved, FarmingKeys.HERB_RUNS_WAIT_FOR_LEVEL,
				defaults.herbRunsWaitForLevel()))
			.coralWaitsForAttas(read(saved, FarmingKeys.CORAL_WAITS_FOR_ATTAS,
				defaults.coralWaitsForAttas()))
			.compostsWithFertileSoil(read(saved, FarmingKeys.COMPOST_WITH_FERTILE_SOIL,
				defaults.compostsWithFertileSoil()))
			.makeCompostBelow(readNumber(saved, FarmingKeys.MAKE_COMPOST_BELOW,
				defaults.makeCompostBelow()))
			.stopCompostAt(readNumber(saved, FarmingKeys.STOP_COMPOST_AT,
				defaults.stopCompostAt()))
			.build();
	}

	/** A saved whole number, or the default when it is unsaved or not a number. */
	private static int readNumber(UnaryOperator<String> saved, String key, int unsaved)
	{
		String value = saved.apply(key);
		if (value == null)
		{
			return unsaved;
		}
		try
		{
			return Integer.parseInt(value.trim());
		}
		catch (NumberFormatException ex)
		{
			return unsaved;
		}
	}

	/** The herb tick named this, or null for none, an unknown name, or a crop with no tick. */
	private static Crop herbNamed(String name)
	{
		for (Crop herb : FarmingPreferences.HERB_CHOICES)
		{
			if (herb.name().equals(name))
			{
				return herb;
			}
		}
		return null;
	}

	/**
	 * Sets every farming setting named here to its value, writing only those that differ:
	 * each write posts a {@code ConfigChanged}. A key outside {@link FarmingKeys} is ignored.
	 *
	 * @return how many settings were written
	 */
	static int apply(ConfigStore store, Map<String, String> values)
	{
		if (store == null)
		{
			return 0;
		}
		Map<String, String> current = valuesOf(from(store));
		int written = 0;
		for (Map.Entry<String, String> setting : values.entrySet())
		{
			if (isCropKey(setting.getKey()) && setting.getValue() != null
				&& !setting.getValue().equals(current.get(setting.getKey())))
			{
				store.setConfiguration(LarryConfig.GROUP, setting.getKey(), setting.getValue());
				written++;
			}
		}
		return written;
	}

	/** The value each farming key holds under these preferences, for every key. */
	static Map<String, String> valuesOf(FarmingPreferences prefs)
	{
		Map<String, String> values = new HashMap<>();
		for (Map.Entry<Crop, String> tick : FarmingKeys.CROPS.entrySet())
		{
			values.put(tick.getValue(), String.valueOf(prefs.allows(tick.getKey())));
		}
		for (TravelMethod method : TravelMethod.values())
		{
			values.put(method.getKey(), String.valueOf(prefs.uses(method)));
		}
		for (String site : FarmingKeys.SITES)
		{
			values.put(site, String.valueOf(prefs.visits(site)));
		}
		for (HouseItem item : HouseItem.values())
		{
			values.put(item.getKey(), String.valueOf(prefs.declares(item)));
		}
		values.put(FarmingKeys.PICK_FRUIT_IN_PASSING, String.valueOf(prefs.picksFruitInPassing()));
		values.put(FarmingKeys.WORK_COMPOST_BINS, String.valueOf(prefs.worksCompostBins()));
		Crop favourite = prefs.favouriteHerb();
		values.put(FarmingKeys.FAVOURITE_HERB,
			favourite == null ? FarmingKeys.NO_FAVOURITE : favourite.name());
		values.put(FarmingKeys.BEST_SEEDS_EVERYWHERE,
			String.valueOf(prefs.plantsBestSeedsEverywhere()));
		values.put(FarmingKeys.HERB_RUN_FIRST, String.valueOf(prefs.herbRunFirst()));
		values.put(FarmingKeys.WAIT_FOR_EVERY_PATCH, String.valueOf(prefs.waitsForEveryPatch()));
		values.put(FarmingKeys.FRUIT_TREES_WAIT_FOR_SAPLINGS,
			String.valueOf(prefs.fruitTreesWaitForSaplings()));
		values.put(FarmingKeys.REPLANT_GROWN_TREES, String.valueOf(prefs.replantsGrownTrees()));
		values.put(FarmingKeys.HERB_RUNS_WAIT_FOR_LEVEL,
			String.valueOf(prefs.herbRunsWaitForLevel()));
		values.put(FarmingKeys.CORAL_WAITS_FOR_ATTAS,
			String.valueOf(prefs.coralWaitsForAttas()));
		values.put(FarmingKeys.COMPOST_WITH_FERTILE_SOIL,
			String.valueOf(prefs.compostsWithFertileSoil()));
		values.put(FarmingKeys.MAKE_COMPOST_BELOW, String.valueOf(prefs.makeCompostBelow()));
		values.put(FarmingKeys.STOP_COMPOST_AT, String.valueOf(prefs.stopCompostAt()));
		return values;
	}

	private static boolean read(UnaryOperator<String> saved, String key, boolean unsaved)
	{
		String value = saved.apply(key);
		return value == null ? unsaved : Boolean.parseBoolean(value);
	}

	/**
	 * Saves a setting the farming tab changed. Refuses any key outside {@link FarmingKeys},
	 * so the tab can write farming settings and nothing else in the group, such as saved
	 * progress.
	 *
	 * <p>The favourite herb and its tick move together. Picking an unticked herb as the
	 * favourite ticks it, and unticking the favourite leaves no favourite. Each pair is
	 * written in the order that never saves a favourite without its tick. The compost
	 * thresholds move together too: the high one stays above the low one.
	 *
	 * <p>Each write posts a {@code ConfigChanged}, which re-plans the run.
	 *
	 * @return whether the value was written
	 */
	static boolean write(ConfigStore store, String key, String value)
	{
		if (store == null || !isCropKey(key) || value == null)
		{
			return false;
		}
		Map<String, String> writes = new LinkedHashMap<>();
		FarmingPreferences prefs = from(store);
		if (FarmingKeys.FAVOURITE_HERB.equals(key))
		{
			Crop herb = herbNamed(value);
			if (herb != null && !prefs.allows(herb))
			{
				writes.put(FarmingKeys.CROPS.get(herb), "true");
			}
		}
		else if (prefs.favouriteHerb() != null
			&& key.equals(FarmingKeys.CROPS.get(prefs.favouriteHerb()))
			&& !Boolean.parseBoolean(value))
		{
			writes.put(FarmingKeys.FAVOURITE_HERB, FarmingKeys.NO_FAVOURITE);
		}
		else if (FarmingKeys.MAKE_COMPOST_BELOW.equals(key))
		{
			// Raising the low threshold to the high one or past it lifts the high one a
			// step above it, written first so the saved pair is never the wrong way round.
			int low = readNumber(k -> value, key, prefs.makeCompostBelow());
			if (low >= prefs.stopCompostAt())
			{
				writes.put(FarmingKeys.STOP_COMPOST_AT,
					String.valueOf(Math.min(FarmingPreferences.COMPOST_MOST,
						low + FarmingPreferences.COMPOST_STEP)));
			}
		}
		writes.put(key, value);
		for (Map.Entry<String, String> write : writes.entrySet())
		{
			store.setConfiguration(LarryConfig.GROUP, write.getKey(), write.getValue());
		}
		return true;
	}

}
