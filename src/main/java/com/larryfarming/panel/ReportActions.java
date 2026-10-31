package com.larryfarming.panel;

import java.awt.Component;

/** What the "Report a problem" dialog's buttons do. */
public interface ReportActions
{
	/** How a save went, told on the EDT. */
	interface Outcome
	{
		/** The report is being built or written. */
		void working(String what);

		/**
		 * The report was written.
		 *
		 * @param fileName   the name it was saved under
		 * @param showFolder opens the folder holding it
		 */
		void saved(String fileName, Runnable showFolder);

		/** Nothing was written, for the reason given, such as the player cancelling. */
		void notSaved(String why);
	}

	/** Builds the report and asks where to save it. */
	void save(String message, Component parent, Outcome outcome);

	/** Opens the issue page in the browser, the player's words prefilled. */
	void openIssue(String message);
}
