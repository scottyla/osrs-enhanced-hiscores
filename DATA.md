# Data sent by OSRS Enhanced Hiscores

Nothing is sent until the player turns on **Submit data**. It's off by default and shows RuneLite's third-party IP warning.

## Where and how

- `POST https://osrsenhancedhiscores.com/api/v1/submit`, JSON body (`Submission.java`), through the injected `OkHttpClient` (`enqueue`). JSON is built off the client thread.
- Header `X-Hiscores-Token`: a random token the server returns on a character's first submission. It's stored in RS-profile config (`osrsenhancedhiscores.token`) and stops others overwriting that character.
- Response: `{"ok":true}`, or `{"ok":true,"token":"…"}` on first submission.
- Only standard worlds, Hardcore Worlds, Fresh Start, Leagues and Deadman. Beta, PvP Arena, quest speedrunning and tournament worlds are skipped.

## When

| What | How often |
|---|---|
| Batch of changes (drops, quests, kill counts, collection log items, re-saved loot sources) | Every 2 minutes, only if something changed |
| Stats | Every 5 minutes, or on a level-up |
| Full Loot Tracker history and kill counts | Once per character per client session |
| Full quest list | On each login |
| After an error or HTTP 429 | Waits 1 → 15 minutes (doubling), honours `Retry-After` |

## Fields

| Field | Source | Setting |
|---|---|---|
| `accountHash` | `client.getAccountHash()` | always |
| `username` | local player name | always |
| `accountType` | `VarbitID.IRONMAN` | always |
| `profileType`, `gameMode` | `RuneScapeProfileType`, `HiscoreEndpoint.fromWorldTypes` | always |
| `stats.skills` (level, XP), `combatLevel` | `getRealSkillLevel`, `getSkillExperience` | Account stats |
| `stats.questPoints` | `VarPlayerID.QP` | Account stats |
| `stats.collectionLog*` (totals per category) | `VarPlayerID.COLLECTION_COUNT*` | Account stats |
| `stats.playtimeMinutes` | `VarClientID.ACCOUNT_SUMMARY_PLAYTIME`, only if revealed in game | Account stats |
| `stats.diaries` | `*_DIARY_*_COMPLETE`, `ATJUN_*_DONE` varbits | Account stats |
| `stats.combatAchievement*` | `VarbitID.CA_POINTS`, `CA_TASK_COMPLETED_*` varps (bit count) | Account stats |
| `stats.slayer*` | `VarbitID.SLAYER_POINTS`, `SLAYER_TASKS_COMPLETED` | Account stats |
| `levelUps` (skill, level, time) | `StatChanged` | Account stats |
| `lootSources` (name, kills, item ids/qty) | Loot Tracker's saved config (`loottracker`, read-only) | Loot tracker history |
| `drops` (source, items, world, time) | `LootReceived` | Individual drops |
| `collectionLogItems` (item id, qty) | script 4100 args via `ScriptPreFired`, passive | Collection log items |
| `quests` (state, start/finish times) | `Quest.getState` every 30 s and on QP change | Quest progress |
| `killCounts` (kc, pb) | Chat Commands' saved config (`killcount`/`personalbest`, read-only) and the clue completion chat message | Kill counts & PBs |
| `items` (name, GE price, HA value for ids above) | `ItemManager` | always |

## Stored locally

RS-profile config in the `osrsenhancedhiscores` group: `token`, `questStarts` (quest start times) and `playedMs` (logged-in time, used to time quests).

## Not sent

Bank or wealth, chat messages (only the clue count is parsed locally), location, other players, login details.

## Server

Source: https://github.com/scottyla/osrs-enhanced-hiscores-server
- Rate limits: 12 submissions per minute per IP, and about 4 per minute per character.
- Retention: the latest 500 drops per character.
- Data removal: on request via this repo's issues.
