# Cornerstone

Cornerstone is a client-side Fabric mod that copies an area and saves it as a list of `setblock` and `fill` commands. You can replay the saved commands later, in the same spot or somewhere new. Think of it like a more powerful `clone`. 

Because it runs on the client, you can use it on any server where you have operator permissions. Singleplayer worlds need commands enabled.

## Installation

Drop the Cornerstone jar into your `mods` folder along with Fabric API, then launch the game.

## Quick start

1. Run `/cornerstone select` to turn on selection mode.
2. Left click a block to set the first corner.
3. Right click a block to set the second corner.
4. Run `/cornerstone select` again to turn selection mode off.
5. Run `/cornerstone save myhouse` to save everything between the two corners.
6. Run `/cornerstone run myhouse` to rebuild it.

While selection mode is on, left and right clicks only pick corners.

## Commands

| Command | What it does |
| --- | --- |
| `/cornerstone select` | Turns selection mode on or off. Your corners are kept when it is off. |
| `/cornerstone pos1 <x> <y> <z>` | Sets the first corner directly, no clicking or travel needed. |
| `/cornerstone pos2 <x> <y> <z>` | Sets the second corner directly, no clicking or travel needed. |
| `/cornerstone clear` | Forgets both corners. |
| `/cornerstone save <name>` | Saves the selected area. Air blocks are skipped. |
| `/cornerstone save <name> air` | Saves the selected area including air, so running it also clears space. |
| `/cornerstone run <name>` | Rebuilds the save where it was originally captured. |
| `/cornerstone run <name> here` | Rebuilds the save with its lowest corner at your feet. |
| `/cornerstone run <name> at <x> <y> <z>` | Rebuilds the save with its lowest corner at the given coordinates. |
| `/cornerstone list` | Lists your saves with their sizes. |
| `/cornerstone delete <name>` | Deletes a save. |
| `/cornerstone cancel` | Stops a run that is in progress. |

## Saves

Saves live in `config/cornerstone.json` inside your Minecraft folder. Each save holds its commands as plain text, so you can open the file and read or edit them.

The same file has a `commandsPerTick` setting (default 5). It controls how many commands are sent to the server each tick when you run a save. Lower it if a server struggles with large builds, or raise it to finish faster. Restart the game after editing the file.

## Good to know

- You need operator permissions to run a save, since it uses `setblock` and `fill`.
- The whole area must be in loaded chunks when you save. Stand close to it first.
- Areas are unlimited in size, but large regions come with a disclaimer: saving scans every block and may freeze the game briefly, saves get very big, and running sends ~100 commands/second by default so big builds take a long time and may get you kicked for spam. Prefer smaller saves when you can.
- Only block states are saved. Chest contents, sign text, and other block entity data are not.
- Blocks are placed from the bottom up, but things that need support (torches, sand, and similar) can still fall or pop off while a build is in progress.
- Command feedback from `setblock` and `fill` is hidden from chat during a run, and a progress message appears above your hotbar.