# Changelog

## 1.0.0+26.2

First public build for Minecraft 26.2.

`/find <item> [structure]` locates a structure, tps you there, and opens loot containers until the item shows up. `/finder` does the same.

Tab on `/find` lists pause, resume and stop first. After that, item suggestions are only things that actually drop from structure loot (singleplayer). If the loot table says chest-only, it skips barrels and the rest.

Travel is spectator. Opening a chest switches to creative so the loot actually generates. Stop restores the gamemode you had before.

Sneak once to pause or resume, sneak twice to stop. Needs cheats or op (level 2).