package com.larryfarming.panel;

import java.awt.*;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * The colour of the game's inventory panel, painted behind the slots of an item grid.
 *
 * <p>Only slot cells are filled. The gaps between them keep the panel's own background.
 *
 * <p>A flat colour, not the sprite: the inventory sprite carries a bevel and shading that
 * show up as artifacts when tiled or scaled into a sidebar grid. The colour still comes from
 * the sprite so it follows any change the game makes.
 */
class InventoryBackdrop extends JPanel
{
	/**
	 * Client-property key marking a child as a slot rather than a blank padding out a row.
	 * Blanks stay unfilled.
	 *
	 * <p>Stored on the child, not in a side list, so removing a child can never leave a stale
	 * entry that paints a rectangle where it used to be.
	 */
	private static final String SLOT = "larryfarming.inventorySlot";

	private IconProvider icons;

	InventoryBackdrop(LayoutManager layout)
	{
		super(layout);
		// Transparent: paintComponent fills only the slots.
		setOpaque(false);
		setAlignmentX(LEFT_ALIGNMENT);
	}

	/**
	 * Sets the colour source on each rebuild. Not a constructor argument because some grids
	 * are built before {@code LarryPlugin.startUp} creates the {@link IconProvider}.
	 */
	void setIcons(IconProvider icons)
	{
		this.icons = icons;
	}

	/** Adds a cell that should have the game's panel colour behind it. */
	void addSlot(JComponent slot)
	{
		slot.putClientProperty(SLOT, Boolean.TRUE);
		add(slot);
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		Color panel = icons == null ? null : icons.getInventoryPanelColour();
		// Fallback until the sprite loads (every first paint of a session).
		g.setColor(panel == null ? PanelColors.CONTAINER_BG : panel);
		// Reads x/y/width/height directly: getBounds() allocates a Rectangle per child, and
		// this runs on every snapshot's repaint.
		for (int i = 0, count = getComponentCount(); i < count; i++)
		{
			Component child = getComponent(i);
			if (!(child instanceof JComponent) || ((JComponent) child).getClientProperty(SLOT) == null)
			{
				continue;
			}
			int width = child.getWidth();
			int height = child.getHeight();
			if (width > 0 && height > 0)
			{
				g.fillRect(child.getX(), child.getY(), width, height);
			}
		}
	}
}
