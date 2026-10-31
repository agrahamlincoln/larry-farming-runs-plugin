package com.larryfarming;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.larryfarming.farming.model.FarmingKeys;
import com.larryfarming.farming.model.Preset;
import com.larryfarming.persistence.ConfigStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import lombok.extern.slf4j.Slf4j;

/**
 * Named, saved sets of farming settings the player can swap between.
 *
 * <p>The live farming keys the planner reads ({@link ConfiguredCrops}) are the working copy:
 * a change applies and persists at once. A profile holds a saved snapshot, written only by
 * {@link #save()} or {@link #create}. The active profile has unsaved changes exactly while
 * the live values differ from its snapshot; that is derived on every call, never stored.
 *
 * <p>A profile is built in for each of {@link Preset#BUILT_IN}, the default route first. A
 * built-in profile holds its preset and cannot be saved over, renamed or deleted. The player
 * may still change settings under it and save the result as a new profile.
 *
 * <p>Deleting the active profile keeps the live settings and leaves {@link #NO_PROFILE}
 * active: no snapshot, so always unsaved, until the player saves a new profile or switches.
 *
 * <p>Snapshots are stored as a map of farming key to value under {@link #PROFILES_KEY}, and
 * {@link #ACTIVE_KEY} names the active profile. A key missing from a snapshot takes the
 * built-in profile's value. An on/off value is stored as a JSON boolean; other values as
 * strings. Everything lives in the plugin's config group, so it follows the RuneLite profile
 * as the farming keys do. Nothing is cached.
 *
 * <p>Every change runs on one thread, the EDT, so two changes never interleave their
 * read-modify-write of the stored list. Each write posts a {@code ConfigChanged}
 * synchronously on the writing thread. {@link #isApplying()} is true on that thread alone
 * while a change is writing, so the plugin ignores exactly those events: the caller redraws
 * once afterwards, and a switch calls its re-plan once at the end.
 */
@Slf4j
final class SettingsProfiles
{
	static final String ACTIVE_KEY = "activeProfile";
	static final String PROFILES_KEY = "profiles";
	/** Where an unreadable profile list is kept before it is replaced, so it is never lost. */
	static final String UNREADABLE_KEY = "profilesUnreadable";
	/** The profile that holds settings found at first load that differ from the preset. */
	static final String CUSTOM_NAME = "My settings";
	/** What is active after the active profile is deleted. A reserved name, never stored. */
	static final String NO_PROFILE = "No profile";
	/** Short enough that the settings header shows the whole name at sidebar width. */
	static final int MAX_NAME_LENGTH = 16;

	private static final String NAME = "name";
	private static final String SETTINGS = "settings";

	private final ConfigStore store;
	private final Gson gson;
	private final Runnable replan;
	/** The thread writing a change, or null. Other threads' config events are not this class's. */
	private volatile Thread applying;

	/**
	 * @param replan called once after a change that moved the live settings
	 */
	SettingsProfiles(ConfigStore store, Gson gson, Runnable replan)
	{
		this.store = store;
		this.gson = gson;
		this.replan = replan;
	}

	/** The default built-in profile's name, active until the player picks another. */
	static String builtIn()
	{
		return Preset.DEFAULT.getLabel();
	}

	static boolean isBuiltIn(String name)
	{
		return presetNamed(name) != null;
	}

	/** The built-in preset with this name in any case, or null for none. */
	private static Preset presetNamed(String name)
	{
		for (Preset preset : Preset.BUILT_IN)
		{
			if (preset.getLabel().equalsIgnoreCase(name))
			{
				return preset;
			}
		}
		return null;
	}

	/**
	 * Whether this names no profile the player can save, rename or delete: a built-in profile,
	 * or {@link #NO_PROFILE}.
	 */
	static boolean isFixed(String name)
	{
		return isBuiltIn(name) || NO_PROFILE.equalsIgnoreCase(name);
	}

	/** Whether this config key is one of the profiles' own, as opposed to a setting. */
	static boolean isProfileKey(String key)
	{
		return ACTIVE_KEY.equals(key) || PROFILES_KEY.equals(key);
	}

	/**
	 * Settles the stored profiles on start and on a RuneLite profile switch.
	 *
	 * <p>The first time, nothing names an active profile. Live settings that match the
	 * preset make the built-in profile active; any others are saved as
	 * {@link #CUSTOM_NAME} and made active, so nothing reads as unsaved. An active name with
	 * no snapshot (a damaged list) gets the live settings as its snapshot. An unreadable list is set aside here, before anything
	 * could save over it.
	 */
	void load()
	{
		quietly(() ->
		{
			setAsideUnreadable();
			renameClashesWithBuiltIns();
			if (store.getConfiguration(LarryConfig.GROUP, ACTIVE_KEY) == null)
			{
				if (Preset.DEFAULT.matches(ConfiguredCrops.from(store)))
				{
					setActive(builtIn());
				}
				else
				{
					String name = CUSTOM_NAME;
					for (int n = 2; !available(name, null); n++)
					{
						name = CUSTOM_NAME + " " + n;
					}
					// Saved before anything names it active, so no step loses the settings.
					create(name);
				}
				return;
			}
			String active = active();
			List<StoredProfile> profiles = stored();
			if (!isFixed(active) && find(profiles, active) == null)
			{
				profiles.add(new StoredProfile(active, live()));
				save(profiles);
			}
		});
	}

	/**
	 * Renames a saved profile that shares a built-in profile's name, so the built-in does not
	 * hide it. It takes the first free numbered name, and stays active if it was.
	 */
	private void renameClashesWithBuiltIns()
	{
		List<StoredProfile> profiles = stored();
		boolean renamed = false;
		for (StoredProfile profile : profiles)
		{
			if (!isBuiltIn(profile.name))
			{
				continue;
			}
			String was = profile.name;
			String name = was;
			for (int n = 2; !available(name, null) || nameIn(profiles, name); n++)
			{
				// Cut short so the number always fits under the length limit.
				String suffix = " " + n;
				name = was.substring(0, Math.min(was.length(),
					MAX_NAME_LENGTH - suffix.length())) + suffix;
			}
			profile.name = name;
			renamed = true;
			if (was.equals(store.getConfiguration(LarryConfig.GROUP, ACTIVE_KEY)))
			{
				setActive(name);
			}
		}
		if (renamed)
		{
			save(profiles);
		}
	}

	/** Whether a profile in this list has this name in any case. */
	private static boolean nameIn(List<StoredProfile> profiles, String name)
	{
		return profiles.stream().anyMatch(profile -> profile.name.equalsIgnoreCase(name));
	}

	/**
	 * Whether the calling thread's config events belong to a change this class is making.
	 * False on every other thread, so an outside change arriving meanwhile still counts.
	 */
	boolean isApplying()
	{
		return applying == Thread.currentThread();
	}

	/** Runs a change's writes with {@link #isApplying()} true on this thread. Nests. */
	private void quietly(Runnable writes)
	{
		Thread previous = applying;
		applying = Thread.currentThread();
		try
		{
			writes.run();
		}
		finally
		{
			applying = previous;
		}
	}

	/**
	 * Every profile's name: the built-in ones first, then the rest in the order made, then
	 * {@link #NO_PROFILE} while it is active. A hand-edited list's blank, reserved or repeated
	 * names are left out, so no two entries read alike.
	 */
	List<String> names()
	{
		List<String> names = new ArrayList<>();
		for (Preset preset : Preset.BUILT_IN)
		{
			names.add(preset.getLabel());
		}
		for (StoredProfile profile : stored())
		{
			if (!profile.name.isBlank() && !isFixed(profile.name)
				&& names.stream().noneMatch(profile.name::equalsIgnoreCase))
			{
				names.add(profile.name);
			}
		}
		String active = active();
		if (!names.contains(active))
		{
			names.add(active);
		}
		return Collections.unmodifiableList(names);
	}

	/** The active profile's name. */
	String active()
	{
		String name = store.getConfiguration(LarryConfig.GROUP, ACTIVE_KEY);
		return name == null || name.isBlank() ? builtIn() : name;
	}

	/**
	 * Whether the live settings differ from the active profile's saved ones. Always true
	 * under {@link #NO_PROFILE}, which has none.
	 */
	boolean hasUnsavedChanges()
	{
		String active = active();
		return NO_PROFILE.equalsIgnoreCase(active)
			|| !live().equals(savedValues(stored(), active));
	}

	/** Whether {@link #save()} would write anything: unsaved changes on a profile not fixed. */
	boolean canSave()
	{
		return !isFixed(active()) && hasUnsavedChanges();
	}

	/**
	 * Saves the live settings into the active profile.
	 *
	 * @return false under a built-in profile or {@link #NO_PROFILE}
	 */
	boolean save()
	{
		String active = active();
		if (isFixed(active))
		{
			return false;
		}
		quietly(() ->
		{
			List<StoredProfile> profiles = stored();
			StoredProfile found = find(profiles, active);
			if (found == null)
			{
				profiles.add(new StoredProfile(active, live()));
			}
			else
			{
				found.settings = live();
			}
			save(profiles);
		});
		return true;
	}

	/**
	 * Whether a profile could be named this, for a rename of {@code renaming} or a new one
	 * when that is null: not blank, not too long, and no other profile's name in any case.
	 * Renaming a profile to its own name is no rename, but a change of case is.
	 */
	boolean canName(String name, String renaming)
	{
		String wanted = clean(name);
		return !wanted.equals(renaming) && available(wanted, renaming);
	}

	/**
	 * Saves the live settings, unsaved changes included, as a new profile and makes it
	 * active. The live settings do not move, so nothing re-plans.
	 *
	 * @return false for a name that is blank, too long or taken
	 */
	boolean create(String name)
	{
		String wanted = clean(name);
		if (!available(wanted, null))
		{
			return false;
		}
		quietly(() ->
		{
			List<StoredProfile> profiles = stored();
			profiles.add(new StoredProfile(wanted, live()));
			save(profiles);
			setActive(wanted);
		});
		return true;
	}

	/** @return false for a fixed profile, an unknown one, or an unusable new name */
	boolean rename(String from, String to)
	{
		String wanted = clean(to);
		if (isFixed(from) || !names().contains(from) || !canName(wanted, from))
		{
			return false;
		}
		quietly(() ->
		{
			List<StoredProfile> profiles = stored();
			StoredProfile found = find(profiles, from);
			if (found == null)
			{
				profiles.add(new StoredProfile(wanted, live()));
			}
			else
			{
				found.name = wanted;
			}
			save(profiles);
			if (from.equals(active()))
			{
				setActive(wanted);
			}
		});
		return true;
	}

	/**
	 * Deletes a profile. The live settings stay as they are, so nothing re-plans; deleting
	 * the active one leaves {@link #NO_PROFILE} active.
	 *
	 * @return false for a fixed profile or an unknown one
	 */
	boolean delete(String name)
	{
		if (isFixed(name) || !names().contains(name))
		{
			return false;
		}
		quietly(() ->
		{
			List<StoredProfile> profiles = stored();
			profiles.removeIf(profile -> profile.name.equals(name));
			save(profiles);
			if (name.equals(active()))
			{
				setActive(NO_PROFILE);
			}
		});
		return true;
	}

	/**
	 * Makes a profile active and writes its saved settings, re-planning once.
	 *
	 * @param confirmLoss asked only when the active profile has unsaved changes, which the
	 *     switch drops; false cancels the switch
	 * @return false for an unknown profile or a cancelled switch
	 */
	boolean switchTo(String name, BooleanSupplier confirmLoss)
	{
		if (!names().contains(name))
		{
			return false;
		}
		if (name.equals(active()))
		{
			return true;
		}
		if (hasUnsavedChanges() && !confirmLoss.getAsBoolean())
		{
			return false;
		}
		Map<String, String> target = savedValues(stored(), name);
		int[] written = new int[1];
		quietly(() ->
		{
			setActive(name);
			written[0] = ConfiguredCrops.apply(store, target);
		});
		if (written[0] > 0)
		{
			replan.run();
		}
		return true;
	}

	/**
	 * A profile's saved settings, for every key: the preset's for the built-in profile, and
	 * otherwise its snapshot over the preset's. A profile with no snapshot reads as the live
	 * settings, so it never shows changes nobody made.
	 */
	private Map<String, String> savedValues(List<StoredProfile> profiles, String name)
	{
		Preset preset = presetNamed(name);
		if (preset != null)
		{
			return ConfiguredCrops.valuesOf(preset.preferences());
		}
		StoredProfile found = find(profiles, name);
		if (found == null)
		{
			return live();
		}
		Map<String, String> saved = new HashMap<>();
		for (Map.Entry<String, String> setting : found.settings.entrySet())
		{
			if (FarmingKeys.all().contains(setting.getKey()))
			{
				saved.put(setting.getKey(), setting.getValue());
			}
		}
		// Read back through the preferences, so the snapshot compares in the live values'
		// own spelling, and a missing key takes the preset's value.
		return ConfiguredCrops.valuesOf(ConfiguredCrops.fromValues(saved));
	}

	private Map<String, String> live()
	{
		return ConfiguredCrops.valuesOf(ConfiguredCrops.from(store));
	}

	private static String clean(String name)
	{
		return name == null ? "" : name.strip();
	}

	/** Whether no profile but {@code except} has this name in any case, and it fits. */
	private boolean available(String name, String except)
	{
		if (name.isEmpty() || name.length() > MAX_NAME_LENGTH || isFixed(name))
		{
			return false;
		}
		for (String taken : names())
		{
			if (!taken.equals(except) && taken.equalsIgnoreCase(name))
			{
				return false;
			}
		}
		return true;
	}

	private static StoredProfile find(List<StoredProfile> profiles, String name)
	{
		for (StoredProfile profile : profiles)
		{
			if (profile.name.equals(name))
			{
				return profile;
			}
		}
		return null;
	}

	private void setActive(String name)
	{
		store.setConfiguration(LarryConfig.GROUP, ACTIVE_KEY, name);
	}

	/**
	 * The stored profiles, in the order made, or none for an unreadable list. Only reads, so
	 * a redraw from any thread writes nothing; {@link #save(List)} sets an unreadable list
	 * aside before it writes over one.
	 */
	private List<StoredProfile> stored()
	{
		List<StoredProfile> profiles = parse(store.getConfiguration(LarryConfig.GROUP,
			PROFILES_KEY));
		return profiles == null ? new ArrayList<>() : profiles;
	}

	/**
	 * Moves an unreadable profile list to {@link #UNREADABLE_KEY}, replacing any older one
	 * there, so the player's names and settings stay in the config to recover by hand.
	 */
	private void setAsideUnreadable()
	{
		String raw = store.getConfiguration(LarryConfig.GROUP, PROFILES_KEY);
		if (parse(raw) != null)
		{
			return;
		}
		log.info("Farming profiles unreadable, kept under {} and started afresh",
			UNREADABLE_KEY);
		store.setConfiguration(LarryConfig.GROUP, UNREADABLE_KEY, raw);
		store.unsetConfiguration(LarryConfig.GROUP, PROFILES_KEY);
	}

	/** The profiles in this stored list, none for no list, or null when it is unreadable. */
	private List<StoredProfile> parse(String raw)
	{
		List<StoredProfile> profiles = new ArrayList<>();
		if (raw == null || raw.isBlank())
		{
			return profiles;
		}
		try
		{
			JsonArray array = gson.fromJson(raw, JsonArray.class);
			if (array == null)
			{
				throw new JsonParseException("not a list");
			}
			for (JsonElement element : array)
			{
				JsonObject entry = element.getAsJsonObject();
				if (entry.get(NAME) == null)
				{
					throw new JsonParseException("a profile has no name");
				}
				Map<String, String> settings = new LinkedHashMap<>();
				JsonObject values = entry.getAsJsonObject(SETTINGS);
				if (values != null)
				{
					for (Map.Entry<String, JsonElement> value : values.entrySet())
					{
						// A JSON boolean reads as "true" or "false".
						settings.put(value.getKey(), value.getValue().getAsString());
					}
				}
				profiles.add(new StoredProfile(entry.get(NAME).getAsString(), settings));
			}
		}
		catch (JsonParseException | IllegalStateException | ClassCastException
			| UnsupportedOperationException e)
		{
			log.debug("Unreadable farming profiles", e);
			return null;
		}
		return profiles;
	}

	private void save(List<StoredProfile> profiles)
	{
		setAsideUnreadable();
		JsonArray array = new JsonArray();
		for (StoredProfile profile : profiles)
		{
			JsonObject entry = new JsonObject();
			entry.addProperty(NAME, profile.name);
			JsonObject values = new JsonObject();
			for (Map.Entry<String, String> setting : profile.settings.entrySet())
			{
				String value = setting.getValue();
				if ("true".equals(value) || "false".equals(value))
				{
					values.addProperty(setting.getKey(), Boolean.valueOf(value));
				}
				else
				{
					values.addProperty(setting.getKey(), value);
				}
			}
			entry.add(SETTINGS, values);
			array.add(entry);
		}
		store.setConfiguration(LarryConfig.GROUP, PROFILES_KEY, gson.toJson(array));
	}

	private static final class StoredProfile
	{
		String name;
		Map<String, String> settings;

		StoredProfile(String name, Map<String, String> settings)
		{
			this.name = name;
			this.settings = settings;
		}
	}
}
