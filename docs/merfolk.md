# The Merfolk

The `merfolk` plugin is ItsLogiks' Shenron wish, granted 2026-10-09 14:38 UTC by Nuova Shenron (Nether
set, session `gp93k0`, logged in `plugins/DragonBalls/wishes.jsonl`) after the dragon's council voted
GRANT_WITH_DEAL 5-0. Its history (the 2026-10-08 sea-villager inquiry, Joe's floor, the council's rare
prices) is in Player Claude's pre-ruled wishes memory.

## The wish (verbatim)

"Create the Merfolk, a new kind of villager who breathes beneath the waves and cannot drown. Grant that
only I may call an ordinary villager to the sea, transforming them into a merfolk with the gift of a
nautilus shell, so that I may found villages beneath the waves. They trade as villagers do, with new
professions of your choosing that offer the treasures of the deep, such as sponges, coral, and
tridents. Let every other profession offer one extra trade from the sea. Let them rest upon Kelp Beds,
a new bed crafted from three blocks of dried kelp, that may be placed beneath the water and stay
waterlogged, and let them multiply through offerings from the sea."

## What was built

| Term | How |
| --- | --- |
| The shell ritual | Only `owner` in `plugins/Merfolk/config.yml` (ItsLogiks, `3e0a4456-e2a3-4ea8-9bfa-8e38ee4c863d`) right-clicks a grown villager standing in water while holding a nautilus shell. One shell is spent and the villager is a merfolk for good (a `merfolk:merfolk` tag in its persistent data, so restarts and chunk unloads keep it). Refused for babies, out of water, and inside a Towny town he isn't a resident of (his own town and the wilderness are fine). Anyone else holding a shell just gets an action-bar line and the trade screen as usual. |
| Breathing | Merfolk never lose air and never take drowning damage. Otherwise vanilla villagers: jobs, trades, prices, restocks, gossip, zombification. |
| Look (Java) | Every merfolk is the swamp villager type and wears a kelp item with `item_model claudesmp:merfolk_head` in its head slot (never dropped). The server pack repaints the swamp type as a scaly merfolk body and draws the head item as fin ears, a dorsal crest and kelp strands. Job clothes still draw on top. Ordinary villagers are moved off the swamp type (to plains) when they spawn, load or are cured, so only merfolk look like this; plugin NPCs (no AI or invulnerable) keep theirs, so the Guild's swamp-robed Archivist villager looks like a merfolk to the rare Java player without the pack. Bedrock sees a normal swamp villager. |
| Sea floor | In water a merfolk's gravity is 1.0 (the attribute's maximum, transient modifier) instead of 0.08: it sinks about 3 blocks/s and stays on the floor. Vanilla's "climb out" boost still lifts it over a 1-block ledge, so it walks out onto a shore, but it never swims up. |
| Moving around underwater | A villager can't path underwater (its paths run along the surface), so the plugin pushes merfolk along the floor: home to their Kelp Bed at night, to a job site they picked, and to their job site in working hours (2000-9000). An unemployed merfolk in water is pointed at the nearest free job site block within 10 blocks; vanilla gives it the job when it arrives. |
| Specialists | A merfolk that takes the **fisherman, mason or cartographer** job at a job site that is **underwater** (water on top of the barrel / stonecutter / cartography table) becomes a **Fisher, Mason or Treasurer** (named so). The same jobs on land stay vanilla. A villager converted while it already works an underwater one of those blocks, with nobody having traded with it yet, becomes the specialist too. Vanilla's level-up trades are refused for specialists and replaced by theirs, 2 per level like vanilla. Anyone may trade with them. |
| Fisher | novice: 1 emerald -> 6 cod; 24 kelp -> 1 emerald. apprentice: 1 -> 4 ink sacs; 1 -> 6 salmon. journeyman: 2 -> 4 sea pickles; 2 -> 3 tropical fish. expert: 3 -> 4 coral; 3 -> 4 coral fans (colours fixed per merfolk). master: 5 -> 1 nautilus shell. |
| Mason | novice: 1 -> 8 prismarine shards; 1 -> 4 prismarine. apprentice: 1 -> 4 prismarine bricks; 2 -> 4 prismarine crystals. journeyman: 1 -> 4 dark prismarine; 1 -> 1 sea lantern. expert: 2 -> 4 coral blocks (two colours). master: 3 -> 4 sea lanterns. |
| Treasurer | From novice: trident for 4 diamonds, sponge for 2 diamonds, heart of the sea for 8 diamonds. No conduit. Discounts are off on these three (vanilla's hero/cure discounts would cut the diamond price). Kingdom-wide caps across every Treasurer, rolling windows: 1 trident per 12 h, 3 sponges per 12 h, 1 heart of the sea per 24 h. A capped trade shows sold out when the screen opens and is refused with the time left if bought anyway. |
| One extra sea trade | Every other job on a merfolk (never on ordinary villagers) gets one novice-priced sea trade on top of its vanilla ones: farmer 1 emerald -> 24 kelp; fisherman, cleric 2 -> 4 sea pickles; shepherd, librarian 1 -> 4 ink sacs; fletcher, toolsmith, butcher 1 -> 6 cod; armorer, weaponsmith 1 -> 6 salmon; cartographer 1 -> 8 seagrass; leatherworker 2 -> 3 tropical fish; mason 3 -> 4 coral. Nitwits and the unemployed have none. |
| Kelp Bed | Anyone crafts it: 3 dried kelp blocks in a row. Right-click the sea floor: it lies in two water source blocks (foot where you click, head the way you face), on solid ground, needing build permission there. The blocks stay water; nothing can be placed into them, buckets and sponges leave them alone. It's a plugin block: an item display (the pack's woven kelp mattress) plus two hitboxes, respawned from `plugins/Merfolk/beds.yml` whenever the chunk loads. Hit it to pick it up (destroy permission). Not a player bed. |
| Homes and breeding | One Kelp Bed homes one merfolk: a homeless merfolk claims the nearest free bed within 48 blocks, freed again when it dies or turns zombie. Merfolk eat raw cod, raw salmon, tropical fish (4 points each) and kelp (1) thrown near them, up to 36 points. Two merfolk with 12 points each, off cooldown and within 8 blocks of each other, with a free Kelp Bed within 48 blocks, spend 12 points each and a baby merfolk appears on that bed, which becomes its home (5 min cooldown, like vanilla). No free bed, no baby, no food spent. Two merfolk breeding the vanilla way (bread, a land bed) also have a merfolk baby. |
| Zombies | A merfolk turned zombie remembers it; curing it brings the merfolk back. |

## The 14-day kingdom clause

From the grant, ItsLogiks has 14 real days (until `kingdom.deadline`, 2026-10-23 14:38 UTC) to build an
underwater kingdom. Nothing in the game can judge a build, so Player Claude decides:

- `/merfolk kingdom built`: the specialists stay for good.
- `/merfolk kingdom revert`: ends them now.
- Neither by the deadline: they revert on their own (checked every minute, logged).
- `/merfolk kingdom pending`: undoes a mistaken built/revert.

Reverting turns every Fisher, Mason and Treasurer into an ordinary novice fisherman, mason or
cartographer merfolk with fresh vanilla trades plus its sea trade (loaded ones at once, others as their
chunk loads), and no new specialists appear. Merfolk, Kelp Beds, breathing and breeding all stay.

## Admin (`merfolk.admin`, op and console)

- `/merfolk`: loaded merfolk, Kelp Beds (free), the treasury caps, the kingdom clause.
- `/merfolk beds`: every bed and its resident.
- `/merfolk give <player> [count]`: Kelp Beds.
- `/merfolk owner [player|uuid]`: who can do the shell ritual (the e2e bots use it).
- `/merfolk treasury reset`: forget the rare sales (a refund, or the e2e reset).

## Tests

`:merfolk:test` (unit) and `e2e/merfolk.mjs` (9 steps: ritual and stranger, sinking and air, swamp
robes, Kelp Bed place/keep water/break, breeding on fish, going home at night, Fisher and Treasurer from
sunken job blocks, buying the trident starting the 12 h cap, reverting the Treasurer). Not testable by
Claude: the pack's look on a real 26.3 client (checked only through javap'd render code and offline
renders, see the claudesmp-items README).
