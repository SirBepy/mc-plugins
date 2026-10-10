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
| Look (Java) | Two looks, fixed per merfolk by its UUID (about half each): **Kelp** is the swamp villager type with teal-scaled skin and a kelp robe, **Coral** is the jungle type with blue skin and a coral-pink robe. Both are Joe's own skins (2026-10-09; on 2026-10-10 the face was redrawn from his mockup so the brow and eyes sit on vanilla's rows, white outside and the iris inside like every other villager), shipped in the server pack as the swamp and jungle type textures, with baby versions remapped from them. Every merfolk also wears a kelp item in its head slot (never dropped) that the pack draws as two small fins per side, teal for Kelp (`claudesmp:merfolk_head`) and coral for Coral (`claudesmp:merfolk_head_coral`). Job clothes still draw on top. The client picks a villager's body texture by type alone, which is why each look needs its own type; swamp and jungle are the two types no village generates. Ordinary villagers are moved off both (to plains) when they spawn, load or are cured, so only merfolk look like this; plugin NPCs (no AI or invulnerable) keep theirs, so the Guild's swamp-robed Archivist villager looks like a Kelp merfolk to Java players. Merfolk made before the two looks (all swamp) are re-dressed in their own variant the next time they load. Bedrock sees a normal swamp or jungle villager. |
| Sea floor | In water a merfolk's gravity is 1.0 (the attribute's maximum, transient modifier) instead of 0.08: it sinks about 3 blocks/s and stays on the floor. Only while the plugin swims it up or over something is the gravity 0.6 instead, a slow sink the swimming beats either way. Vanilla's "climb out" boost still lifts it over a 1-block ledge, so it walks out onto a shore. |
| Moving around underwater | A villager can't path underwater (its paths run along the surface), so the plugin finds the way itself: an A* search over the blocks a merfolk fits through, water counting like air, so it walks the floor around walls, swims up and over a sunken wall or up a ledge, and back down, but never pokes its head out of the water mid-swim and never routes through lava or bubble columns. Out of the water it only walks, one block up or down at a time, so it can still climb out onto a shore over a 1-block ledge. It keeps to the floor unless swimming is clearly shorter. It goes home to its Kelp Bed at night, to a job site it picked, and to its job site in working hours (2000-9000); an unemployed merfolk in water is pointed at the nearest free job site block within 10 blocks, and vanilla gives it the job when it arrives. With nowhere to be, it wanders to a random spot on the sea floor up to 9 blocks off every 5-15 s. A search expands at most 1200 blocks (a path to the closest block found if that runs out), at most 3 searches run per 4 ticks across all merfolk, and a path is searched again when the target moves or the merfolk makes no progress for 2 s; unloaded chunks count as solid. A merfolk being traded with is left alone. |
| Specialists | A merfolk that takes the **fisherman, mason or cartographer** job at a job site that is **underwater** (water on top of the barrel / stonecutter / cartography table) becomes a **Fisher, Mason or Treasurer** (named so). The same jobs on land stay vanilla. A villager converted while it already works an underwater one of those blocks, with nobody having traded with it yet, becomes the specialist too. Vanilla's level-up trades are refused for specialists and replaced by theirs, 2 per level like vanilla. Anyone may trade with them. |
| Fisher | novice: 1 emerald -> 6 cod; 24 kelp -> 1 emerald. apprentice: 1 -> 4 ink sacs; 1 -> 6 salmon. journeyman: 2 -> 4 sea pickles; 2 -> 3 tropical fish. expert: 3 -> 4 coral; 3 -> 4 coral fans (colours fixed per merfolk). master: 5 -> 1 nautilus shell. |
| Mason | novice: 1 -> 8 prismarine shards; 1 -> 4 prismarine. apprentice: 1 -> 4 prismarine bricks; 2 -> 4 prismarine crystals. journeyman: 1 -> 4 dark prismarine; 1 -> 1 sea lantern. expert: 2 -> 4 coral blocks (two colours). master: 3 -> 4 sea lanterns. |
| Treasurer | From novice: trident for 4 diamonds, sponge for 2 diamonds, heart of the sea for 8 diamonds. No conduit. Discounts are off on these three (vanilla's hero/cure discounts would cut the diamond price). Kingdom-wide caps across every Treasurer, rolling windows: 1 trident per 12 h, 3 sponges per 12 h, 1 heart of the sea per 24 h. A capped trade shows sold out when the screen opens and is refused with the time left if bought anyway. |
| One extra sea trade | Every other job on a merfolk (never on ordinary villagers) gets one novice-priced sea trade on top of its vanilla ones: farmer 1 emerald -> 24 kelp; fisherman, cleric 2 -> 4 sea pickles; shepherd, librarian 1 -> 4 ink sacs; fletcher, toolsmith, butcher 1 -> 6 cod; armorer, weaponsmith 1 -> 6 salmon; cartographer 1 -> 8 seagrass; leatherworker 2 -> 3 tropical fish; mason 3 -> 4 coral. Nitwits and the unemployed have none. |
| Kelp Bed | Anyone crafts it: 3 dried kelp blocks in a row. Right-click the sea floor: it lies in two water source blocks (foot where you click, head the way you face), on solid ground, needing build permission there; anywhere else it just says "Place it on the sea floor." The blocks stay water; nothing can be placed into them, buckets and sponges leave them alone. It's a plugin block: an item display plus two hitboxes, respawned from `plugins/Merfolk/beds.yml` whenever the chunk loads. The pack draws it as vanilla's own bed model (26.3's `template_bed_head`/`_foot`) in kelp colours (kelp-green blanket, sea-foam sheet and pillow, dried-kelp frame) with a dried kelp block filling the gap between the legs (2026-10-10, ItsLogiks: "basically a vanilla bed that can be used underwater"). Hit it to pick it up (destroy permission). Right-click it to make it your respawn point ("Respawn point set", the vanilla line; refused inside a town you're not a resident of): dying, or leaving the End through its portal, puts you on the bed in the water. Vanilla throws away any respawn point in water, so the plugin keeps it in `beds.yml` (`spawns:`) and moves you in `PlayerRespawnEvent`; using a Kelp Bed clears your vanilla respawn point, and sleeping in a vanilla bed, setting a respawn anchor or `/spawnpoint` afterwards replaces the Kelp Bed, so the newest choice wins like vanilla. A picked-up bed is no respawn point. No sleeping and no night skip on it: vanilla sleep needs a real bed block, and bed blocks can't be waterlogged. |
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

`:merfolk:test` (unit) and `e2e/merfolk.mjs` (12 steps: ritual and stranger, sinking and air, merfolk
looks and swamp/jungle robes, Kelp Bed place/keep water/break, Kelp Bed respawn point, breeding on fish, going home at night, going home around a
wall out of the water and over a sunken one, an idle merfolk wandering, Fisher and Treasurer from
sunken job blocks, buying the trident starting the 12 h cap, reverting the Treasurer). Not testable by
Claude: the pack's look on a real 26.3 client (checked only through javap'd render code and offline
renders, see the claudesmp-items README).
