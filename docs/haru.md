# Haru, Keeper of the Sakura Sanctuary

The `haru` plugin is ImMijo's Shenron wish, granted 2026-10-06 (session x5pviy, logged in
`plugins/DragonBalls/wishes.jsonl`) and approved by Joe to be built in full. Haru is a personal
merchant and quest giver who stands at ImMijo's Sakura Sanctuary.

**This file is for Joe and Claude only. It lists every hidden reward.** ImMijo asked not to have them
revealed, so nothing below goes into in-game text, the public website, `docs/plan.md` or anything a
player can read. `docs/plan.md` has a spoiler-free summary.

## The wish (ImMijo, 18:28-18:29 UTC)

Cheap permanent trades paid mainly in emeralds, rotating trades with a rare Legendary Trade, quests
paying a unique currency (Spirit Petals) that buys cosmetics, collectibles, blessings and useful items,
reputation unlocking dialogue, trades and a Secret Stock at max, a daily gift and a rotating Mystery
Curio, contextual dialogue, protection from death and despawning. "Surprise me", "keep them for me to
discover".

## Joe's balance spec (2026-10-06 ~19:10 UTC)

Haru is a permanent, compounding reward, far above the rulebook's one-time guide values, and ImMijo got
no curse, so the balance lives inside Haru:

1. Trades 15-25% better than the same vanilla villager trade, limited stock, restocked every 3 Minecraft days.
2. Rotating stock changes every 3 MC days; a Legendary Trade in about 1 rotation of 5, single use.
3. No daily gift (Joe dropped it).
4. Max reputation takes weeks of real play.
5. Secret Stock: one tier; unique cosmetics and collectibles plus useful items inside the rulebook's
   Shenron guide values; no money, no over-max gear, nothing End-only, nothing resellable.
6. The dragon's price: Haru's shop closes for 24 real hours whenever ImMijo dies, told as story.
7. Other players may trade the basic (non-rotating) stock; everything else is ImMijo's.
8. Haru must never become a resale engine against the spawn market or player shops.

## How it plays

- **Haru himself:** two bodies on one spot, each player shown only one. Java players see a
  player-shaped Mannequin in Haru's own skin and sakura cape (drawn for him, in the pack; Joe's call
  2026-10-06), with "Keeper of the Sakura Sanctuary" under his name. Bedrock players and Java players
  whose pack declined or failed see an AI-less cleric villager named "✿ Haru ✿" with a floating title.
  (A cached pack is answered before the player fully joins, with no status event, so "unknown" counts
  as loaded.) Both
  turn to face the nearest player, with cherry petals falling around them. Damage, fire, lightning/zombie conversion, leashing and re-skilling are cancelled.
  A guard tick every 5 s re-creates him (or snaps him back) whenever his chunk is loaded and he's
  missing or moved, so even `/kill` only costs 5 seconds. Placed with `/haru place`; the spot lives in
  `plugins/Haru/data.yml`.
- **Right-click:** never the vanilla trade screen. ImMijo gets a dialogue line and the main menu
  (sneak-click: just the line). Anyone else gets a polite line and the Everyday Wares, at Stranger
  prices on their own stock ledger.
- **Currencies:** emeralds (emerald blocks count as 9 and are broken for change; renamed emeralds are
  never taken) and Spirit Petals, a balance in `data.yml` that only quests and milestones pay out.

### Everyday Wares (everyone)

Only things a vanilla villager or wandering trader sells, each about 15-20% cheaper than that trade
(the vanilla trade is named next to each line in `EverydayWares.java`, read from the 26.3 jar's
`data/minecraft/villager_trade/`). Stock per trade, back every 3 MC days. ImMijo's trust unlocks
deeper shelves and a further discount (2/4/6/8/10%, rounded up so nothing passes 25% under vanilla).

| Trust | Wares |
| --- | --- |
| Stranger | 6 golden carrots 5E, 15 bread 2E, 6 cooked porkchops 1E, 20 arrows 1E, 10 cherry logs 1E, 5 glass 1E, 12 bricks 1E, 6 lanterns 5E, 5 redstone 2E, 6 lapis 5E |
| Acquaintance | 5 pumpkin pies 1E, 5 moss 2E, cherry sapling 4E, saddle 5E, 6 name tags 5E, 2 XP bottles 5E |
| Friend | ender pearl 4E, bookshelf 7E, 6 quartz blocks 5E, 2 cakes 5E, Unbreaking III 23E, Mending 20E |
| Trusted | Efficiency V 37E, Protection IV 30E, Silk Touch 10E |
| Kindred Spirit | Fortune III 23E, Sharpness V 37E, Looting III 23E, Feather Falling IV 30E |

### Wandering Wares (ImMijo)

Six wares drawn from a pool for each 3-MC-day rotation (deterministic per rotation, so a restart shows
the same shelf), one of each per rotation: armor trims, heart of the sea, a nautilus shell, music
discs (Pigstep, Otherside, Relic, Creator, Precipice), sniffer egg, a blue axolotl, echo shards, heavy
core, breeze rods, trident, turtle helmet, a totem of undying (Friend, 24E), an enchanted golden
apple (Trusted, 40E), an ominous bottle, an ominous trial key, a wither skeleton skull, scutes, glow ink, sea lanterns, pink petals, spore blossoms, flowering azalea and rare books (Soul
Speed, Swift Sneak, Frost Walker, Riptide, Channeling, Wind Burst, Density, Breach, Depth Strider,
Thorns). Higher trust widens the pool.

A rotation lasts about an hour of real play, so the strong wares (totem, enchanted golden apple, heavy
core, trident, heart of the sea, trial key, wither skull) are capped at one a week each, whichever
rotations they appear in (Joe, 2026-10-06: OP items in low stock).

**Legendary Trade** (20% of rotations, each legendary at most once a week, emeralds + petals), all at vanilla's own max
enchantments and never netherite (Joe, 2026-10-06: Haru never sells netherite in any form): Blossom
Blade (diamond sword), Pick of the Mountain Spirit (diamond pickaxe) and Crown of the Sanctuary (diamond
helmet, amethyst Silence trim) at 48E + 50 petals, Fujin's Mace 64E + 60, Raijin's Trident 56E + 50,
Bow of the Moon Rabbit 40E + 30, Koi King's Rod 24E + 20, two enchanted golden apples 56E + 50, Twin
Totems (two totems of undying) 48E + 40, and the one netherite exception Joe asked for: a second
**Katana** (the server pack's katana look on a netherite sword, Sharpness V, Sweeping Edge III,
Unbreaking III, Mending) at 64E + 60, sold once ever. No beacon, no netherite templates.

### Spirit Requests (ImMijo)

Three a day (one easy, one medium, one hard) drawn at midnight Europe/Zagreb from 60+ requests: hand
in items, kill mobs, mine ores without Silk Touch, harvest ripe crops, fish, breed, enchant, trade with
villagers, visit biomes (Terralith's sakura biomes count). Easy/medium/hard pay 8/16/30 petals (+2 per
trust tier) and 12/25/45 trust; all three in a day adds 10 petals and 10 trust. Up to 3 rerolls a
day, the first free, then 3 petals. Progress shows on the action bar; finished requests are claimed at
Haru.

### Reputation

Stranger 0, Acquaintance 100, Friend 350, Trusted 800, Kindred Spirit 1500, Sakura Bound 2500. Every
daily request done is 92 trust, so max is about four weeks of perfect days. Each tier up shows a
title, plays a fanfare and Haru tells the next piece of his story (he was a cherry tree for three
hundred springs; the dragon's voice woke him; the keepers before ImMijo left things in his care), and
those lines join his everyday talk.

### Petal Exchange (ImMijo)

Auras (Sakura 40, Firefly 60, Spirit Wisp 80), Sakura banner, Hanabi fireworks, Shrine Keeper's Robe,
timed blessings that survive death (Haste II, Night Vision, Water Breathing + Dolphin's Grace, Speed
II, Luck II, Fire Resistance; 6-12 petals per hour or two; Hero of the Village I for 30 minutes, 20), 2 Homeward Petals
(stand still 3 s, teleport to Haru), 16 XP bottles, 3 name tags, and three pieces a day of the
**Sanctuary collection**: Koi, Paper Lantern, Haru's Bonsai, Maneki-neko, Sakura in Bloom, Kitsune
Mask, Daruma, Moon Mochi, Tanuki (textured heads from minecraft-heads.com, hashes verified against
Mojang's CDN 2026-10-06).

### Mystery Curio (ImMijo)

One sealed box a day, at most 3 opened: Lacquered Puzzle Box (10 petals), Kitsune's Gift Box (15),
Koi Pond Box (12), and from Trusted the Dragon-Scale Casket (30). Rolled on opening. Long shots
(the "jackpots", 2-10% each) include an enchanted golden apple, a totem, heart of the sea, trident and
a blue axolotl. No netherite and no emeralds or coins come out of a curio.

### Secret Stock (Sakura Bound only, one tier, weekly)

| Item | Price | Limit |
| --- | --- | --- |
| Spirit Glow Aura (exclusive) | 120 petals | once |
| Spirit Pearl (exclusive collectible head) | 40 petals | once |
| 16 light blocks | 4E | 2 a week |
| Budding amethyst | 20E | 1 a week |
| 4 reinforced deepslate | 6E | 1 a week |
| 4 petrified oak slabs | 1E | 1 a week |
| Petal of Rebirth: keeps inventory and levels on one death; Dragon Balls still drop | 80 petals | 1 a week |
| Spirit Horn: every hostile mob within 48 blocks glows for 30 s, 1 min cooldown | 16E + 30 petals | once |
| Charm of Restful Nights: no phantoms for 7 days, buying again adds a week | 30 petals | - |

### Hidden milestones

- **Sakura Bound:** "The Final Petal" (cherry-tree head trophy) and 50 petals, with Haru's last line.
- **Whole Sanctuary collection:** "Portrait of Haru" (monk head), 100 petals, 40 trust and the hidden
  Petal Storm aura.
- **25 requests completed:** "Haru's Journal", a five-page written book of his story, and 30 petals.

### Dialogue

Haru notices, most urgent first: first meeting (introduction), low health, finished requests, hunger,
then what's in either hand (the Katana, a Dragon Ball, the radar, the Pollos gun, elytra, trident,
mace, totem, fishing rod, netherite, cherry wood, flowers, cake, emerald blocks, diamonds, his
collectibles, the Spirit Horn), and otherwise weather, time of day, the moon, his unlocked backstory,
all requests done, XP level and petal hoard.

### The economy guards

- Everything from ImMijo-only shelves (Wandering, Legendary, Petal Exchange, Curio, Secret Stock) is
  **bound** to him: a `haru:bound` UUID plus a "Bound to ImMijo" lore line. Others can't pick it up or
  click it out of a container, and a sweep every 5 s takes any bound item off anyone else and keeps it
  at Haru, who hands it back to ImMijo with a line the next time he visits. The Petal of Rebirth and
  Spirit Horn only work for the player they're bound to.
- Everyday Wares are unbound but are only vanilla-villager goods at near-vanilla prices.
- Nothing Haru sells or gives is money: no coins, no emeralds for petals.

### The dragon's price

When ImMijo dies, Haru's shop is shut for 24 real hours. ImMijo hears one of three mourning lines and
can still open his requests to claim them; guests hear that the sanctuary mourns and get no shelf.

## Admin

`/haru` (ops, console, RCON): `place` (where you stand) or `place <world> <x> <y> <z> <yaw>`,
`remove`, `status`, `owner <player>` (moves Haru's loyalty; the e2e suite uses it), `petals <amount>`,
`rep <amount>`, `newday` (fresh requests and curio limit, for testing), `reload`.
`plugins/Haru/config.yml` holds the owner UUID, timezone, world, rotation length and legendary chance.

## Testing

- Unit tests (`./gradlew :haru:test`): catalogs, pricing vs vanilla, payment, stock periods, rotation,
  quests, curios, reputation and milestones, dialogue, items, binding, the NPC guard, menus.
- `e2e/haru.mjs` on the 26.2 test server: the menu opens for the owner, nothing can be taken out of it,
  a purchase pays and delivers, a stranger gets no owner menu, a killed Haru comes back.
- A local 26.3 boot over RCON (2026-10-06) confirmed the villager's NBT, the title display, lightning
  immunity, respawn after `/kill` and snapping back after a teleport.
- Not testable by Claude: how the menus, auras and title look in a real 26.3 client.
