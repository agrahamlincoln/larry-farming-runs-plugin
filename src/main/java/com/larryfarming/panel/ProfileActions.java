package com.larryfarming.panel;

import java.util.function.BooleanSupplier;

/** What the settings profile control asks of its owner. Every call arrives on the EDT. */
public interface ProfileActions
{
	/**
	 * Makes a profile active.
	 *
	 * @param confirmLoss asked only when unsaved changes would be dropped; false cancels
	 * @return false when the switch did not happen
	 */
	boolean switchTo(String name, BooleanSupplier confirmLoss);

	/** Saves the live settings into the active profile. */
	void save();

	/** Saves the live settings as a new profile under this name, and makes it active. */
	void create(String name);

	void rename(String from, String to);

	void delete(String name);

	/**
	 * Whether a profile may take this name: for a rename of {@code renaming}, or a new
	 * profile when that is null. Not blank, not too long, and no other profile's name.
	 */
	boolean canName(String name, String renaming);
}
