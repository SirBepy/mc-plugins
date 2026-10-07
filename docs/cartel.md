# The cartel flip phone

Archontopouloi's Shenron wish, granted in game on 2026-10-06 at 20:55 UTC (`shenron grant 1 Cartel
flip phone...` in `plugins/DragonBalls/wishes.jsonl`), built by the `cartel` plugin. Joe's spec
(2026-10-06, ~20:50-20:55 UTC) set the gear, the 1 real day hit cooldown and the 10-block spawn
distance, and explicitly allowed Assassinate Mode against players: an override of the Dragon Balls
rulebook's "never hurt another player" line for this wish only. Everything below that Joe didn't
specify was picked by Claude and is listed under "Limits". On 2026-10-07 Joe made both cousins
fully ranged (see "The crossbow"), replacing the dragon's council's Marco-only pistol from the same
day.

## What the owner gets

- A **Cartel Flip Phone**, given automatically the first time the owner (`owner` in
  `plugins/Cartel/config.yml`, Archontopouloi's UUID) joins with the plugin running. It is a clock
  with the server pack's `cartel_phone` look; without the pack, or on Bedrock, it shows as a clock.
- Right-clicking it opens a chest menu (Bedrock players get the same menu through Geyser):
  - **Guard Mode**: Leonel and Marco Salamanca appear beside the owner and follow them, fighting
    any monster within 16 blocks of the owner and any player or mob that hurt the owner or one of
    the cousins in the last 15 seconds. They leave a player who did nothing alone.
  - **Assassinate Mode**: a list of online players in survival or adventure; pick one, confirm, and
    the cousins appear **10 blocks from the target** (so the target sees them coming) and hunt
    only that player. Everyone reads, in dark red: "[Target Name], you have made enemies of the
    cartel."
  - **Trophy**: when the cousins kill the target, Archontopouloi gets that player's head (a player
    head with their skin, named after them) in his inventory, or at his feet if it's full. If he is
    offline at that moment, he gets it the next time he joins. A target killed by anything else
    (another player, a fall, the owner himself) gives no head. Guard Mode kills give no heads.
    Added by Joe, ~21:35 UTC 2026-10-06.
  - **Call them off** (while a call is active): sends them home early; the cooldown stays used.
- Anyone else who right-clicks the phone gets "The line is dead. This phone isn't yours."

## The cousins

Two adult zombies named Leonel Salamanca and Marco Salamanca, wearing a **chainmail chestplate and
chainmail leggings** (no helmet, no boots) and holding a **crossbow drawn as a black pistol**, per
Joe's spec of 2026-10-07 (until then: full iron armor and Sharpness X netherite swords). Every piece
carries the server pack's `claudesmp:salamanca` equipment look, which paints their bald heads,
wraparound sunglasses, goatees, grey suits and silver-toed boots over the whole zombie. The head and
feet carry the look on a piece of paper (Joe's pick: the full look, with stats that are exactly
chainmail chest and legs): paper has no armor value, and the client draws any item with an
equipment look through the armor layer, never as an item on the head. Players without the pack see
plain zombies with name tags and crossbows.

- 40 health (a plain zombie has 20), movement speed 0.33 (a zombie walks at 0.23), follow range 64.
  No Resistance, no extra armor or toughness, no enchanted armor.
- **Nothing drops when they die**: no gear, no XP, no zombie loot. Their gear can't be picked up
  or knocked off either (drop chance 0, armor and crossbow unbreakable).
- They never hurt the owner or each other, and the owner can't hurt them.
- They don't burn in daylight, don't turn into drowned, don't break doors, don't pick up items, and
  never call zombie reinforcements.
- They are never saved to disk: a restart, or the chunk they stand in unloading, removes them.

## The crossbow (Joe, 2026-10-07)

Melee-only cousins could be cheesed: a target who pillared 3 blocks up during Assassinate Mode just
waited out the 3 minutes. The dragon's council first voted Marco a conditional pistol (with a 36 h
cooldown as the price); Archontopouloi refused that deal, and Joe replaced it the same evening, at
about 20:50 UTC, with this:

- **Both cousins, both modes, ranged only.** The zombie's melee goal is removed when they spawn, so
  they never hit anyone with their hands.
- **The crossbow:** Piercing IV, Quick Charge III and Power V, unbreakable, with the server pack's
  `cartel_pistol` look (the Los Pollos Hermanos gun's side-on pistol recoloured to black gunmetal
  with a wooden grip). Without the pack (and on Bedrock) it's a plain enchanted crossbow.
- **How the enchantments work:** a zombie has no ranged attack, so the plugin fires each bolt
  itself (Paper launches it with no weapon attached, so nothing on the crossbow applies by itself).
  It applies all three by hand, on every bolt:
  - **Power V** (not a crossbow enchantment in vanilla): the bolt's base damage is set to 2.5 times
    an arrow's 2, i.e. +150%, which is also exactly what vanilla's Power V adds to a bow's arrow
    (+1, then +0.5 per level above I). Vanilla multiplies the base by the bolt's speed when it hits.
  - **Piercing IV:** the bolt passes through 4 entities.
  - **Quick Charge III:** a reload takes 10 ticks (half a second), vanilla's 25 minus 5 per level.
  - Like every vanilla crossbow bolt, it is **critical**: a random bonus on top (Joe's pick).
- **Bolts:** fired at a pillager's bolt speed (1.6) at the target's chest, lifted for the drop the way
  a pillager aims. That makes **8 damage (4 hearts) before armor, 8-13 with the crit's bonus**.
  Measured on the 26.2 test server: see the e2e log line "one bolt" (bare vs full Protection IV
  netherite). They fire with the Pollos gun's gunshot sound (silent without the pack).
- **Who they hurt:** in Assassinate Mode only the target; in Guard Mode only who the cousins may
  fight (monsters near the owner, anyone who hurt the owner or a cousin in the last 15 s). A bolt
  that pierces into anyone else, or one still flying after the call ended, does nothing. No
  knockback, no fire. Nobody can pick the bolts up. A cousin holds his shot while the other cousin or
  the owner stands in the line of fire.
- **Rhythm (Claude's picks, copied from the pillager):** after loading, he aims for 1-2 s before
  each bolt, so roughly one bolt per 1.5-2.5 s per cousin. He only fires with no block in the way
  and within 16 blocks.
- **Keeping distance (Claude's picks, from the pillager's 8 blocks):** farther than 8 blocks, or
  with no clear shot, he walks in; inside 4 blocks (horizontally) he backs off to 8, which is also
  how he gets a line over a pillar's top from its base; in between he stands and shoots.

## Limits (picked by Claude, 2026-10-06)

| | Guard Mode | Assassinate Mode |
| --- | --- | --- |
| Lasts | 10 minutes | 3 minutes |
| Cooldown | 2 real hours from the call | 24 real hours from the call (Joe; the council's 36 h never went live) |
| Ends early when | the owner dies or logs out; both cousins die | the target dies, logs out, or switches to creative/spectator; both cousins die |

- One call at a time; a new call needs the old one to end or be called off.
- Cooldowns are real time and survive restarts (`plugins/Cartel/state.yml`).
- A call is only spent if the cousins actually appear. No room to stand (open air, inside a wall,
  deep water) refuses the call and costs nothing.
- **Outrunning them**: if the target gets more than 48 blocks from both cousins, or into another
  world, the cousins pick up the trail and re-appear 10 blocks from them, at most **2 times** per
  hit. After that they give up.
- **Guard leash**: a cousin with nothing to fight walks back to the owner; one more than 24 blocks
  away (or in another world) is brought back to the owner's side.
- **Who can be targeted**: any other online player in survival or adventure, Java or Bedrock. Not
  the owner, not players in creative or spectator (ghosts from the immortality wish included).
- **Logging out**: if the target logs out the hit ends and the cooldown stays used. If the owner
  logs out during a hit, the cousins keep going.
- **Towns**: Towny removes monsters in towns with mobs off, so `town_mob_removal_ignored_spawn_causes`
  in `server/config/plugins/Towny/settings/config.yml` includes `CUSTOM` (plugin-spawned mobs).
  No other plugin on this server spawns monsters with that reason.
- The phone can't be thrown away (Q), is kept through death (taken out of the drops before the
  graves datapack sees them), and nobody but the owner can pick it up.

## Admin

`/cartel` (op or console): `give <player>` (another phone, e.g. if one was lost), `owner <player>`,
`status` (owner, cooldowns, the active call), `dismiss` (ends the active call), `reset` (clears the
owner's cooldowns).
