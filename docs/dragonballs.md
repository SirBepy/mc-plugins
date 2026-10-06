# Dragon Balls

The `dragonballs` plugin runs a server-wide hunt: 7 Dragon Balls lie scattered across the world.
Whoever brings all 7 together summons a dragon and makes a wish. The balls then fly off to new
spots, harder to reach each time, and stay stone for a while.

This file is two things: the player-facing rules (the guide book at spawn is written from it),
and the **dragon's rulebook**, which Player Claude follows when it speaks as the dragon and judges
wishes. Joe agreed the rulebook on 2026-10-06 ("Rulebook v2"); change it only with his OK.

## The hunt

- 7 balls, 1 to 7 stars, sit on the ground as heads. Walk up, break one, pick it up.
- Each ball you carry slows you 5% (all 7: 35%).
- Balls never go into chests, ender chests, shulkers, bundles, hoppers, item frames or any other
  container, and can't be worn.
- Die and your balls drop on the ground where you fell (never into your grave). Log out and they
  stay with you, but after 24 h offline they fly away again. A ball lost to lava, the void or
  anything else flies away too.
- Picking up a ball is announced to the whole server.
- The **Dragon Radar** (1000 coins, Dragon Ball station at spawn) points at the nearest awake
  ball, including balls someone is carrying. It never shows how far.
- Place all 7 next to each other (within 2 blocks), right-click one, and choose your dragon.

## After a wish

- The balls shoot into the sky and fly off. Watch where they go.
- They land 1000+ blocks from where the dragon was summoned, further every wish:

  | Wishes so far | Overworld distance | Nether | End |
  | --- | --- | --- | --- |
  | 0 | 1000-2000 | 0 | 0 |
  | 1 | 1000-3000 | 0 | 0 |
  | 2 | 1500-4000 | 0 | 0 |
  | 3 | 1500-4000 | 1 | 0 |
  | 4 | 2000-4500 | 2 | 0 |
  | 5+ | 2000-4500 | 2 | 1 |

  While the End is closed, the End ball lands in the overworld instead.
- For 3 Minecraft days the balls are **stone**: you can find, break and carry them, but the radar
  can't see them and they can't summon. Then they wake up wherever they are.

## The dragon's rulebook

The summoner chooses a dragon:

- **Shenron** grants **1 great wish**.
- **Porunga** grants **3 wishes**, each about half as strong as Shenron's.

Only the summoner can wish. Everyone on the server reads what the dragon says.

### Guide values

| Wish | Shenron | Porunga (per wish) |
| --- | --- | --- |
| Valuables | 1 stack of iron or gold blocks, or 16 diamond blocks, or 1 netherite block | half of one of those |
| Money | 100,000 coins | 50,000 coins |
| Custom item look (like the Katana) | look plus max enchants, silly over-max ones allowed (a Knockback 6 stick is fine) | look only, or one modest over-max enchant |
| Named animals (must carry a unique name tag) | buff several named animals, or a whole species of the player's | one named animal |
| Empty shulker boxes | 9 | 4 |
| Fully enchanted elytra | yes | no |
| Spawner | only as a cursed deal | no |
| XP | 100 levels | 30 levels |
| A build by Claude on the player's claim | up to about 30x30 | a statue |
| Effects | non-combat (night vision, haste, water breathing) for 1 real week; combat effects 1 day | non-combat 1 day |
| Time or weather change | yes | yes, once |
| Name colour, nickname, chat title | yes | yes |
| A summoned villager with a chosen trade (e.g. Mending librarian) | yes | yes |
| A small new server feature | yes, Joe can veto | no |

Shulker boxes, elytra and anything else that only comes from the End stay locked until the End is
open (`allow-end` in `bukkit.yml`).

### Never, not even with a curse

- Hurting, robbing, cursing, locating or teleporting to another player, or touching their things.
- Fly, op, creative, god mode, more wishes.
- Anything about the Dragon Balls themselves: revealing where they are, making the hunt easier,
  moving them.
- Bans, unbans, or anything moderation.

### Judging

- Lean towards yes. Joe can always have a wish reverted.
- Be chaotic about it. A borderline wish gets a dice roll: grant it, grant half, charge Porunga's
  summoner 2 of their 3 wishes, or add a harmless twist.
- **Too big? Offer a deal.** Before granting, the dragon names a curse and the summoner accepts or
  refuses in chat:
  - an effect that stays through death for a number of hours,
  - losing named items from their inventory or ender chest,
  - never being able to wish again.
- Really unreasonable wishes, and any player complaining their wish was too weak: ask Joe (as a
  question) before answering.
- Feature and cosmetic requests made through `/claude` outside a wish get "find the Dragon Balls".
  Bug reports, lag and moderation work as before.
