package com.larryfarming;

import com.larryfarming.panel.FarmingPanel;
import com.larryfarming.panel.ProfileActions;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import javax.swing.SwingUtilities;

/**
 * Connects the panel's profile control to the saved profiles: carries out each action, then
 * shows the result. Every action arrives on the EDT, as every config write from the panel
 * does; the re-plan a switch needs goes through {@link SettingsProfiles}.
 */
final class ProfileControl implements ProfileActions
{
	private final SettingsProfiles profiles;
	private final FarmingPanel panel;
	/** Whether a redraw is queued on the EDT and has not run yet. */
	private final AtomicBoolean queued = new AtomicBoolean();

	ProfileControl(SettingsProfiles profiles, FarmingPanel panel)
	{
		this.profiles = profiles;
		this.panel = panel;
	}

	/**
	 * Shows the profiles as they now stand. Safe from any thread. Off the EDT it queues one
	 * redraw that reads the profiles when it runs, so a burst of config events (a RuneLite
	 * profile switch posts one per changed key) draws once, and never draws a state older
	 * than one the EDT has already drawn.
	 */
	void refresh()
	{
		if (SwingUtilities.isEventDispatchThread())
		{
			show();
		}
		else if (queued.compareAndSet(false, true))
		{
			SwingUtilities.invokeLater(() ->
			{
				queued.set(false);
				show();
			});
		}
	}

	private void show()
	{
		String active = profiles.active();
		panel.showProfiles(profiles.names(), active, SettingsProfiles.isFixed(active),
			profiles.hasUnsavedChanges());
	}

	@Override
	public boolean switchTo(String name, BooleanSupplier confirmLoss)
	{
		boolean switched = profiles.switchTo(name, confirmLoss);
		refresh();
		return switched;
	}

	@Override
	public void save()
	{
		profiles.save();
		refresh();
	}

	@Override
	public void create(String name)
	{
		profiles.create(name);
		refresh();
	}

	@Override
	public void rename(String from, String to)
	{
		profiles.rename(from, to);
		refresh();
	}

	@Override
	public void delete(String name)
	{
		profiles.delete(name);
		refresh();
	}

	@Override
	public boolean canName(String name, String renaming)
	{
		return profiles.canName(name, renaming);
	}
}
