# Larry: Farming Runs

**Larry plans the route. You plant the patches.**
He learns your teleports and unlocks as you play, so there's nothing to set up.

Larry is a RuneLite plugin for farm runs. Log in, and he works out which patches to visit, in
what order, how to get to each one, what to plant and what to bring.

## My patches are ready. Where do I go?

Larry lists your run in order, with the teleport for each stop. He picks the route from the
teleports you actually have, so a new diary reward or quest unlock changes it as soon as the
game reports it. A spirit tree you plant joins the route once he has seen it fully grown.

## What do I bring?

Larry builds the carry list for the run: seeds, compost, tools and the runes or items each
teleport needs.

## What do I plant?

Larry suggests a seed for each patch from your Farming level and what you have.

## Do I have to set anything up?

No. Larry reads your quests, diaries, spellbook and unlocks from the game. He sees what's built
in your house when you visit it, and what your Portal Nexus holds when you open it. Until then,
you can tick your portals, Nexus, spirit tree and fairy ring under Your house. Other settings
exist for choices only you can make, such as which runs and
patches to include and which teleports Larry may use, and you can save them as profiles to swap
between.

Larry makes no network requests. He never moves, clicks or acts for you.

If Larry gets a run wrong, press Report a problem at the foot of his panel. He keeps notes on
your latest session in his folder under `.runelite/plugin-data`. A report covers that session
and the one before it, and he saves it wherever you choose, for you to attach to a GitHub
issue. The report never holds your
player name, and nothing leaves your computer unless you send it.

## Credits

- Larry's default settings follow the farming route in the
  [BRUHsailer Ironman guide](https://oldschool.runescape.wiki/w/Guide:BRUHsailer) by So Iron
  BRUH and ParasailerOSRS.
- **[RuneLite](https://github.com/runelite/runelite)**, whose Time Tracking plugin code Larry's
  patch tracking is derived from, and whose Item Identification plugin and Varbits table supply
  farming item tags and diary varbit ids.
- **[Quest Helper](https://github.com/Zoinkwiz/quest-helper)** by Zoinkwiz and its
  contributors, for the quest var ids that gate routes.
- **The [Old School RuneScape Wiki](https://oldschool.runescape.wiki/)**, for item, patch and
  teleport facts.
- The [Shortest Path](https://github.com/Skretzo/shortest-path) plugin, whose transport tables
  helped place teleport landing tiles.

Licenses and full attributions: [NOTICE](NOTICE). Larry is BSD 2-Clause ([LICENSE](LICENSE)).

Old School RuneScape and RuneScape are trademarks of Jagex Ltd. Larry: Farming Runs is not
affiliated with or endorsed by Jagex.
