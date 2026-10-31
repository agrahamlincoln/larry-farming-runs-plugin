package com.larryfarming.panel;

import java.util.Objects;

/**
 * Gates structural rebuilds of a Swing container so it recreates its children
 * only when its <em>structure</em> changes, not on every per-tick refresh.
 *
 * <p>The sidebar refreshes on every snapshot (one per game event). A freshly
 * added wrapping {@code JTextArea} reports a one-line preferred height until
 * layout assigns its width, then re-wraps on the next pass. Recreating it every
 * tick therefore makes its height oscillate and the panel flicker. Keeping the
 * component alive keeps its width and wrapped height stable.
 *
 * <p>Usage: build a value-equality <em>signature</em> from the structural inputs
 * only (row identities, text, met/unmet state). Progress values change
 * continuously and belong in in-place updates, <strong>not</strong> in the
 * signature. Rebuild only when the signature changes:
 *
 * <pre>{@code
 * if (guard.shouldRebuild(rowSignature(rows))) {
 *     container.removeAll();
 *     // ... add rows ...
 *     container.revalidate();
 *     container.repaint();
 * }
 * }</pre>
 *
 * <p>A signature is typically a {@code List<String>} with one delimited string
 * per row. See {@code FarmingPanel.signatureOf}.
 *
 * <p>Not thread-safe. Use only on the Swing EDT.
 */
final class RebuildGuard
{
	/** Signature of the last rebuild, or null before the first one. */
	private Object lastSignature;

	/**
	 * Returns true and records {@code signature} when it differs from the last
	 * one. Returns false when nothing structural changed, and the caller updates
	 * dynamic values in place instead.
	 *
	 * <p>A caller that builds its children at construction time must prime the
	 * guard with the initial signature, or the first refresh rebuilds needlessly.
	 */
	boolean shouldRebuild(Object signature)
	{
		if (Objects.equals(lastSignature, signature))
		{
			return false;
		}
		lastSignature = signature;
		return true;
	}

	/**
	 * Clears the recorded signature so the next {@link #shouldRebuild} call
	 * forces a rebuild. Call it whenever the container is emptied outside the
	 * guard, such as on a null snapshot.
	 */
	void reset()
	{
		lastSignature = null;
	}
}
