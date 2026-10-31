package com.larryfarming.panel;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javax.swing.ImageIcon;
import net.runelite.api.SpriteID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The item and interface sprites the side panel draws, fetched from the client and cached so
 * a rebuild never asks twice.
 *
 * <p>Every icon is handed out at its final size and fills in place when its pixels arrive,
 * so a row is laid out once.
 */
public class IconProvider
{
	private static final Logger log = LoggerFactory.getLogger(IconProvider.class);

	private final ItemManager itemManager;
	private final SpriteManager spriteManager;

	private final Map<String, ImageIcon> cache = new ConcurrentHashMap<>();

	private volatile Color inventoryPanelColour;
	private volatile boolean inventoryPanelColourAsked;

	/** The magic tab icon per spellbook, keyed by {@code Spellbook} name. */
	private final Map<String, ImageIcon> spellbookIcons = new ConcurrentHashMap<>();

	private static final int SPELLBOOK_ICON_SIZE = 16;

	/** Callback to repaint the panel when an async item sprite finishes loading. */
	private volatile Runnable onImageLoaded;

	public IconProvider(ItemManager itemManager, SpriteManager spriteManager)
	{
		this.itemManager = itemManager;
		this.spriteManager = spriteManager;
	}

	public void setOnImageLoaded(Runnable callback)
	{
		this.onImageLoaded = callback;
	}

	/**
	 * An item's sprite. Every item lookup goes through here and {@link #spriteAsync}.
	 */
	protected AsyncBufferedImage itemImage(int itemId)
	{
		return itemManager.getImage(itemId);
	}

	protected AsyncBufferedImage itemImage(int itemId, int quantity, boolean stackable)
	{
		return itemManager.getImage(itemId, quantity, stackable);
	}

	/**
	 * A game interface sprite, delivered to the callback once the client has decoded it.
	 * Never delivered when there is no client to decode it, which every caller already
	 * treats as "not loaded yet".
	 */
	protected void spriteAsync(int spriteId, int file,
		Consumer<BufferedImage> callback)
	{
		if (spriteManager != null)
		{
			spriteManager.getSpriteAsync(spriteId, file, callback);
		}
	}

	/**
	 * Pre-fetches the spellbook sprites. Call from the client thread. The panel reads the
	 * results on the EDT.
	 */
	@SuppressWarnings("deprecation") // SpriteID — gameval equivalent uses unreadable Jagex names
	public void loadSprites()
	{
		loadSpellbookIcon("STANDARD", SpriteID.TAB_MAGIC);
		loadSpellbookIcon("LUNAR", SpriteID.TAB_MAGIC_SPELLBOOK_LUNAR);
		loadSpellbookIcon("ARCEUUS", SpriteID.TAB_MAGIC_SPELLBOOK_ARCEUUS);
	}

	/**
	 * Fetches one spellbook sprite into an icon handed out blank at full size.
	 *
	 * <p>The panel rebuilds its rows only when the advice changes, so it never asks again
	 * for an icon that was missing at rebuild time. Publishing the instance up front lets
	 * the load land in a label already on screen. Every deferred-fill icon in this class
	 * relies on this rule.
	 */
	private void loadSpellbookIcon(String book, int spriteId)
	{
		if (spellbookIcons.containsKey(book))
		{
			return;
		}
		ImageIcon icon = new ImageIcon(new BufferedImage(SPELLBOOK_ICON_SIZE,
			SPELLBOOK_ICON_SIZE, BufferedImage.TYPE_INT_ARGB));
		spellbookIcons.put(book, icon);

		spriteAsync(spriteId, 0, img ->
		{
			if (img == null)
			{
				return;
			}
			BufferedImage sized = fitTo(img, SPELLBOOK_ICON_SIZE);
			javax.swing.SwingUtilities.invokeLater(() ->
			{
				icon.setImage(sized);
				repaintLater();
			});
		});
	}

	/**
	 * The colour of the game's inventory panel, or null until the sprite has loaded.
	 *
	 * <p>Null is the normal first answer. The caller falls back to the flat container
	 * colour, and the repaint hook brings the real colour in once it lands.
	 *
	 * <p>Call <b>from the EDT</b> only. The asked flag is a plain check-then-set, so a
	 * caller on another thread could trigger a second fetch. On a {@code SpriteManager}
	 * cache hit the callback runs inline, so the averaging can run on the EDT inside a paint.
	 */
	@SuppressWarnings("deprecation") // SpriteID — gameval equivalent uses unreadable Jagex names
	public Color getInventoryPanelColour()
	{
		if (inventoryPanelColour != null)
		{
			return inventoryPanelColour;
		}
		if (!inventoryPanelColourAsked)
		{
			inventoryPanelColourAsked = true;
			spriteAsync(SpriteID.FIXED_MODE_SIDE_PANEL_BACKGROUND, 0, img ->
			{
				if (img == null)
				{
					return;
				}
				Color average = averageOfMiddle(img);
				if (average == null)
				{
					log.debug("Inventory panel sprite had no opaque middle to average");
					return;
				}
				javax.swing.SwingUtilities.invokeLater(() ->
				{
					inventoryPanelColour = average;
					repaintLater();
				});
			});
		}
		return null;
	}

	/**
	 * The mean colour of a sprite's middle, ignoring the outer quarter on every side.
	 *
	 * <p>The edges hold the panel's bevel decoration, which would skew the average.
	 * Mostly-transparent pixels are skipped so cut-away areas do not darken the result.
	 * Returns null when nothing in the middle is opaque.
	 */
	static Color averageOfMiddle(BufferedImage sprite)
	{
		int insetX = sprite.getWidth() / 4;
		int insetY = sprite.getHeight() / 4;
		long red = 0;
		long green = 0;
		long blue = 0;
		long counted = 0;
		for (int y = insetY; y < sprite.getHeight() - insetY; y++)
		{
			for (int x = insetX; x < sprite.getWidth() - insetX; x++)
			{
				int argb = sprite.getRGB(x, y);
				if ((argb >>> 24) < 128)
				{
					continue;
				}
				red += (argb >> 16) & 0xFF;
				green += (argb >> 8) & 0xFF;
				blue += argb & 0xFF;
				counted++;
			}
		}
		if (counted == 0)
		{
			return null;
		}
		return new Color((int) (red / counted), (int) (green / counted),
			(int) (blue / counted));
	}

	/**
	 * A sprite at the size asked for, handed back untouched when it is already that size.
	 *
	 * <p>This guards correctness, not speed. Spellbook teleport icons are 20×20 and differ
	 * mainly by a five-pixel letter (Salve graveyard's S, Fenkenstrain's F). Any smooth
	 * rescale smears that letter, so callers wanting a spellbook sprite ask for its native size.
	 */
	static BufferedImage fitTo(BufferedImage sprite, int size)
	{
		return sprite.getWidth() == size && sprite.getHeight() == size
			? sprite
			: ImageUtil.resizeImage(squared(sprite), size, size);
	}

	/**
	 * The sprite centred on a transparent square as wide as its longer side, so scaling it
	 * to a square keeps its shape. A tall item cropped to its art, such as the ectophial,
	 * would otherwise be squashed flat.
	 */
	static BufferedImage squared(BufferedImage sprite)
	{
		int width = sprite.getWidth();
		int height = sprite.getHeight();
		if (width == height)
		{
			return sprite;
		}
		int side = Math.max(width, height);
		BufferedImage square = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = square.createGraphics();
		g.drawImage(sprite, (side - width) / 2, (side - height) / 2, null);
		g.dispose();
		return square;
	}

	/**
	 * Any game sprite by ID, at a fixed size. Returns a blank icon at full size that fills
	 * in place when the sprite loads (see {@link #loadSpellbookIcon}), or null for an id &lt;= 0.
	 *
	 * @param spriteId the game's sprite ID
	 * @param size     width and height in pixels
	 */
	public ImageIcon getSpriteIcon(int spriteId, int size)
	{
		return getSpriteIcon(spriteId, size, true);
	}

	/**
	 * The same sprite, drained of colour when the player cannot use what it stands for.
	 *
	 * @param held false to render in greyscale
	 */
	public ImageIcon getSpriteIcon(int spriteId, int size, boolean held)
	{
		if (spriteId <= 0)
		{
			return null;
		}
		String cacheKey = "sprite:" + spriteId + ":" + size + (held ? "" : ":grey");
		ImageIcon cached = cache.get(cacheKey);
		if (cached != null)
		{
			return cached;
		}

		ImageIcon icon = new ImageIcon(
			new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB));
		cache.put(cacheKey, icon);
		spriteAsync(spriteId, 0, img ->
		{
			if (img == null)
			{
				return;
			}
			BufferedImage fitted = fitTo(img, size);
			BufferedImage sized = held ? fitted : ImageUtil.grayscaleImage(fitted);
			javax.swing.SwingUtilities.invokeLater(() ->
			{
				icon.setImage(sized);
				repaintLater();
			});
		});
		return icon;
	}

	/**
	 * The magic tab icon for a spellbook.
	 *
	 * @param book "STANDARD", "LUNAR" or "ARCEUUS"
	 * @return the icon, blank until the sprite loads, or null for an unknown book
	 */
	public ImageIcon getSpellbookIcon(String book)
	{
		return book != null ? spellbookIcons.get(book) : null;
	}

	/**
	 * An item sprite at a fixed size, greyed out for gear the player has not earned yet.
	 *
	 * @param size   width and height in pixels
	 * @param earned false to render in greyscale
	 */
	public ImageIcon getGearIcon(int itemId, int size, boolean earned)
	{
		return getGearIcon(itemId, size, earned, false);
	}

	/**
	 * The same, with the option of cropping the sprite's transparent margin first.
	 *
	 * <p>Cropping keeps a small icon legible by spending its pixels on the art. Use it only
	 * for a row showing one item at a time: cropping gives each sprite its own scale, which
	 * breaks a gear grid where a ring should look smaller than a cape.
	 *
	 * @param fillSquare true to crop the transparent margin away before scaling
	 */
	public ImageIcon getGearIcon(int itemId, int size, boolean earned, boolean fillSquare)
	{
		String cacheKey = "gear:" + itemId + ":" + size + ":" + earned + ":" + fillSquare;
		ImageIcon cached = cache.get(cacheKey);
		if (cached != null)
		{
			return cached;
		}

		BufferedImage sprite;
		try
		{
			sprite = itemImage(itemId);
		}
		catch (RuntimeException e)
		{
			log.debug("Failed to load gear icon for item {}", itemId, e);
			return null;
		}
		if (sprite == null)
		{
			return null;
		}

		// Resizing and greyscaling read pixels an AsyncBufferedImage does not have yet, so
		// styling waits for the load. It restyles the same ImageIcon in place because labels
		// already hold this instance. Replacing it would leave a greyed item in full colour.
		ImageIcon icon = new ImageIcon(sprite);
		// Cached before the callback is registered, so a fast client thread cannot have
		// its styled result overwritten by this raw one.
		cache.put(cacheKey, icon);
		if (sprite instanceof AsyncBufferedImage)
		{
			AsyncBufferedImage async = (AsyncBufferedImage) sprite;
			async.onLoaded(() -> javax.swing.SwingUtilities.invokeLater(() ->
			{
				icon.setImage(styleGear(async, size, earned, fillSquare));
				repaintLater();
			}));
		}
		else
		{
			icon.setImage(styleGear(sprite, size, earned, fillSquare));
		}
		return icon;
	}

	/**
	 * An item sprite at the size the game draws it, drained of colour when the player has
	 * not got it.
	 *
	 * <p>Unresized on purpose, unlike {@link #getGearIcon}: a grid drawn as an inventory
	 * should hold the same 36×32 sprites the inventory does.
	 *
	 * @param held false to render in greyscale
	 */
	public ImageIcon getInventoryIcon(int itemId, boolean held)
	{
		return getInventoryIcon(itemId, 1, held);
	}

	/**
	 * The same sprite, drawn for a stack of this size.
	 *
	 * @param quantity how many, which changes the sprite for anything stackable
	 */
	public ImageIcon getInventoryIcon(int itemId, int quantity, boolean held)
	{
		int stack = Math.max(1, quantity);
		String cacheKey = "inv:" + itemId + ":" + stack + ":" + held;
		ImageIcon cached = cache.get(cacheKey);
		if (cached != null)
		{
			return cached;
		}

		BufferedImage sprite;
		try
		{
			// A non-stackable item ignores the stackable flag and draws a single sprite.
			sprite = itemImage(itemId, stack, stack > 1);
		}
		catch (RuntimeException e)
		{
			log.debug("Failed to load inventory icon for item {}", itemId, e);
			return null;
		}
		if (sprite == null)
		{
			return null;
		}

		// A held item wraps the async image as-is. Greying waits for the pixels and
		// restyles the same instance, since labels already hold it.
		ImageIcon icon = new ImageIcon(sprite);
		cache.put(cacheKey, icon);
		if (!held)
		{
			if (sprite instanceof AsyncBufferedImage)
			{
				AsyncBufferedImage async = (AsyncBufferedImage) sprite;
				async.onLoaded(() -> javax.swing.SwingUtilities.invokeLater(() ->
				{
					icon.setImage(ImageUtil.grayscaleImage(async));
					repaintLater();
				}));
			}
			else
			{
				icon.setImage(ImageUtil.grayscaleImage(sprite));
			}
		}
		else if (sprite instanceof AsyncBufferedImage)
		{
			Runnable repaint = onImageLoaded;
			if (repaint != null)
			{
				((AsyncBufferedImage) sprite).onLoaded(() ->
					javax.swing.SwingUtilities.invokeLater(repaint));
			}
		}
		return icon;
	}

	private static BufferedImage styleGear(BufferedImage sprite, int size, boolean earned,
		boolean fillSquare)
	{
		BufferedImage styled = earned ? sprite : ImageUtil.grayscaleImage(sprite);
		return fitTo(fillSquare ? cropToArt(styled) : styled, size);
	}

	/**
	 * The sprite with its fully transparent margin cropped away.
	 *
	 * <p>The result shares pixels with the sprite, which {@code ItemManager} shares with
	 * every caller, so nothing may write to it.
	 *
	 * <p>A fully transparent sprite, such as an item not yet loaded, is returned untouched.
	 */
	static BufferedImage cropToArt(BufferedImage sprite)
	{
		int left = sprite.getWidth();
		int top = sprite.getHeight();
		int right = -1;
		int bottom = -1;
		for (int y = 0; y < sprite.getHeight(); y++)
		{
			for (int x = 0; x < sprite.getWidth(); x++)
			{
				if ((sprite.getRGB(x, y) >>> 24) == 0)
				{
					continue;
				}
				left = Math.min(left, x);
				right = Math.max(right, x);
				top = Math.min(top, y);
				bottom = Math.max(bottom, y);
			}
		}
		if (right < 0)
		{
			return sprite;
		}
		return sprite.getSubimage(left, top, right - left + 1, bottom - top + 1);
	}

	/** Runs the repaint hook, if one is set. EDT. */
	private void repaintLater()
	{
		Runnable repaint = onImageLoaded;
		if (repaint != null)
		{
			repaint.run();
		}
	}
}
