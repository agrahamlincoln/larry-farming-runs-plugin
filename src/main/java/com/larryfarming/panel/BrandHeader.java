package com.larryfarming.panel;

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.*;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.ImageUtil;

/**
 * The strip at the top of the panel, logged in or out: Larry's face, his name with his mood
 * under it, and the plugin's version on the right.
 */
class BrandHeader extends JPanel
{
	/** Larry's own 16-pixel sidebar art, which every face in the panel scales up. */
	private static final String FACE = "/com/larryfarming/panel_icon.png";

	/**
	 * Leprechaun green, for the rule under the strip. The green that means "carried" is a
	 * different colour and belongs to item squares alone.
	 */
	private static final Color BRAND = new Color(0x2f9e4f);
	/** Leprechaun green lightened, for Larry's name on the dark panel. */
	private static final Color BRAND_LIGHT = new Color(0x46b866);

	/** Under the name while logged out, where Larry has no mood to show. */
	static final String DESCRIPTOR = "Farming Runs";

	private final JLabel mood;

	BrandHeader()
	{
		super(new BorderLayout(6, 0));
		setOpaque(false);
		setAlignmentX(LEFT_ALIGNMENT);
		// The rule under the strip, then a gap before whatever the panel shows below it.
		setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0), BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 0, 2, 0, BRAND), BorderFactory.createEmptyBorder(0, 2, 6, 0))));

		add(new JLabel(face(2)), BorderLayout.WEST);

		JPanel name = PanelText.stack();
		name.add(label("Larry", FontManager.getRunescapeBoldFont(), BRAND_LIGHT));
		mood = label(DESCRIPTOR, FontManager.getRunescapeSmallFont(), PanelColors.META);
		name.add(mood);
		add(name, BorderLayout.CENTER);

		JLabel version = label(LarryVersion.SHOWN, FontManager.getRunescapeSmallFont(), PanelColors.META);
		version.setVerticalAlignment(SwingConstants.TOP);
		add(version, BorderLayout.EAST);

		setMaximumSize(new Dimension(Short.MAX_VALUE, getPreferredSize().height));
	}

	/**
	 * Words the line under Larry's name in place. A plain label that never wraps, so a new
	 * mood on any refresh never changes the panel's height.
	 *
	 * @param tooltip shown on hover, or null for none
	 */
	void showMood(String text, String tooltip)
	{
		mood.setText(text);
		mood.setForeground(DESCRIPTOR.equals(text) ? PanelColors.META : PanelColors.HEADER);
		mood.setToolTipText(tooltip);
	}

	/** Larry's face at a whole multiple of its 16 pixels, kept sharp. */
	static ImageIcon face(int scale)
	{
		BufferedImage art = ImageUtil.loadImageResource(BrandHeader.class, FACE);
		return new ImageIcon(art.getScaledInstance(art.getWidth() * scale, art.getHeight() * scale, Image.SCALE_REPLICATE));
	}

	private static JLabel label(String text, Font font, Color colour)
	{
		JLabel label = new JLabel(text);
		label.setFont(font);
		label.setForeground(colour);
		return label;
	}
}
