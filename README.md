# OSRS Enhanced Hiscores

Hiscores for what the official hiscores don't track: your **loot log**, **collection log**, **quest completion times**, **achievement diaries**, **combat achievements**, **Slayer** and **hours played**, alongside levels, XP, XP gains, and boss kill counts and personal bests.

Each game mode gets its own board, like the official hiscores: Normal, Ironman, Ultimate, Hardcore Ironman, Group Ironman, Hardcore Group Ironman, **Hardcore Worlds**, Fresh Start Worlds, Leagues, Deadman, Level 3 Skillers and 1 Defence.

## Getting started

1. Install **OSRS Enhanced Hiscores** from the Plugin Hub.
2. Make sure the built-in **Loot Tracker** plugin is enabled. This plugin reads its data.
3. Open the plugin's settings and turn on **Submit data**. It's off until you do.
4. Log in. Your existing Loot Tracker history is uploaded on the first login, and new drops are sent every 30 seconds.
5. Optional: open the **Character Summary** tab and reveal your playtime so hours played can be tracked.
6. Optional: open your **Collection Log** and browse its pages to add individual items to your profile.

## What gets sent

Nothing is sent until you turn on **Submit data**. After that, the plugin sends:

| Data | Setting |
|---|---|
| Your display name, account type (ironman etc.), game mode (normal, Hardcore Worlds, Leagues…), and an anonymous RuneLite account hash used to keep your history across name changes | Always, when enabled |
| Loot Tracker totals per boss/activity (kills and items) | Loot tracker history |
| Each drop as it happens: source, items, world, time | Individual drops |
| Levels and XP, level-up dates, combat level, quest points, collection log totals, achievement diaries, combat achievement points/tasks, Slayer points and streak, hours played | Account stats |
| Quest status, and how long quests took (only those started after installing) | Quest progress |
| Boss kill counts and personal bests saved by RuneLite's Chat Commands plugin, and clue completion counts | Kill counts & PBs |
| Collection log items you view in the collection log | Collection log items |

Every field, its source and how often it's sent: [DATA.md](DATA.md).

Like any web request, your IP address reaches the server. Nothing else is collected: no chat messages (the plugin only reads its own clue completion count from them), no location, no bank or wealth, no login details.

The first time a character submits, the server issues a secret token. The plugin stores it in your RuneLite profile so nobody else can overwrite your character's data.

## Settings

| Setting | Default | |
|---|---|---|
| Submit data | Off | Master switch |
| Loot tracker history | On | Sync Loot Tracker totals |
| Individual drops | On | Recent drops feed |
| Account stats | On | Levels, quest points, collection log totals, hours played |
| Collection log items | On | Items seen in the collection log |
| Quest progress | On | Quest status and completion times |
| Kill counts & PBs | On | Official kill counts, personal bests, clue counts |
| Server URL | The public server | Only change this if you run your own server |

## Limitations

- Loot only counts while the Loot Tracker plugin is enabled. Loot from before you used RuneLite's Loot Tracker isn't known.
- Totals mirror the Loot Tracker, so resetting your Loot Tracker data lowers your totals.
- Collection log items only appear for pages you've opened in game.
- Quest times, level-up dates and XP gains only exist from when you installed the plugin.
- Kill counts and PBs come from RuneLite's Chat Commands plugin, so each boss appears after the game shows its kill count with that plugin enabled.
- Hardcore Worlds: if you **reset** a character, the Loot Tracker still holds its old loot, so loot totals carry over. Levels and other stats reset correctly.

## Website and privacy

Browse the hiscores at **https://osrsenhancedhiscores.com**. To have your data removed, open an issue at https://github.com/scottyla/osrs-enhanced-hiscores/issues with your display name. Turn off **Submit data** first, or your data comes back on your next login.

The server and website are open source: https://github.com/scottyla/osrs-enhanced-hiscores-server

## Legal

Old School RuneScape and RuneScape are trademarks of Jagex Limited. Game content and images © Jagex Limited. All rights reserved. OSRS Enhanced Hiscores is an unofficial fan project, not affiliated with, endorsed by, or sponsored by Jagex or RuneLite.

This project's own source code is BSD 2-Clause licensed (see `LICENSE`); that license does not cover Jagex's content. See `NOTICE`.
