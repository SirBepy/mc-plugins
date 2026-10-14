# ulg -> Eclipse backpack converter (legacy-backpack conversion, prototype)

Converts one old "Backpacks & More" backpack (`minecraft:carrot_on_a_stick`, namespace `ulg`,
`custom_data.bp.Inventory`) in place into a new Eclipse "Backpacks!" backpack of the smallest tier
that fits its contents, holding the same items. Ground truth for both formats:
`.for_bepy/backpack-capture/FORMAT.md` (gitignored scratch, captured 2026-10-10).

Not wired into ServerArtifacts.kt or any live path - command-triggered only, for proving the
conversion logic on a private server before Joe picks a live rollout time (the governing backlog
item's own open question).

## Entry points

- `execute as <player> run function ulgconvert:convert` - converts an old backpack held in the
  player's **offhand** (the ulg pack's documented canonical carry location, confirmed from its own
  source in FORMAT.md). No-ops silently if the offhand item isn't an old backpack.
- `execute as <player> run function ulgconvert:convert/item {nbt:"<path>",item_slot_arg:"<arg>",source_desc:"<label>"}`
  - the general form `convert` calls internally. Works for any slot, but **the NBT path and the
    `/item`-command slot argument are NOT simply "same number, different spelling" for the main
    inventory** - confirmed empirically against this server (not assumed): `/item replace entity
    <p> inventory.0 with ...` landed the item at raw NBT `Slot: 9b`, not `Slot: 0b`. The real
    mapping: hotbar slots (raw NBT Slot 0-8) use argument `hotbar.0`-`hotbar.8`; the other 27 main
    slots (raw NBT Slot 9-35) use argument `inventory.0`-`inventory.26` (argument index = Slot - 9).
    So a correct pair for an item already sitting at a known raw Slot N is:
    - N 0-8 (hotbar): `nbt:"Inventory[{Slot:Nb}]"`, `item_slot_arg:"hotbar.N"`
    - N 9-35 (rest of main inventory): `nbt:"Inventory[{Slot:Nb}]"`, `item_slot_arg:"inventory.(N-9)"`
    - ender chest slot N (0-26, **not independently verified against this server** - no hotbar-style
      split is expected since there's no ender-chest hotbar, but only offhand and one hotbar-slot
      inventory case were actually tested this dispatch): `nbt:"EnderItems[{Slot:Nb}]"`,
      `item_slot_arg:"enderchest.N"`
  - offhand (the one pairing proven correct AND exercised the most): `nbt:"equipment.offhand"`,
    `item_slot_arg:"weapon.offhand"`

**Scope not yet wired:** an auto-scanner that walks every inventory (0-35) and ender chest (0-26)
slot for every online player. The per-slot mechanism above is fully general and was exercised
directly (not through a scanner) for every test case in the dispatch report; wiring the 0-35/0-26
loops is a mechanical follow-up (two more recursive functions in the same style as
`convert/eject_find_slot.mcfunction`), intentionally left out of this prototype to keep it testable
within scope.

## A vanilla gotcha this converter has to route around everywhere

Vanilla outright rejects `data modify entity <player> ...` for a REAL PLAYER entity - every attempt
replies `Unable to modify player data`, confirmed empirically for **both** `equipment.*` paths
(e.g. `equipment.offhand...`) **and** the plain `Inventory[{Slot:Nb}]` list that backs the main
inventory. Reads (`data get`, `set from entity <player> ...`) work fine; only writes are blocked,
and only for players specifically (a `chest_minecart` or any other entity has no such restriction).
The only vanilla-sanctioned way to put an arbitrary (unknown-shape) item into a player's slot is
`item replace entity <player> <slot> from entity <non-player-entity> <slot>` - so every place this
converter builds or restores an item that ends up on a player, it first assembles the item on a
reused scratch `chest_minecart` (`@e[tag=ulgconvert.scratch]` - the same entity classification
stages through) and then moves it onto the player with `item replace ... from entity`, never a
direct `data modify entity <player> ...`. See `convert/place_tier0.mcfunction`,
`convert/eject_place_inventory.mcfunction`, and `restore/item.mcfunction`.

## What it does, per found old backpack

1. Reserves a fresh id from the real pack's own `$global backpacks.id` counter (same
   get-then-increment order as `backpacks:bp/summon/init.mcfunction`) - **requires
   `server/datapacks/eclipse-backpacks` to already be loaded**; converting with it absent/disabled
   fails loudly (scoreboard objective not found) rather than silently colliding ids with a later
   real first-open.
2. Classifies every item in the old `Inventory` list: a bundle or a non-empty shulker box is routed
   to the player directly (first free inventory slot, else dropped at their feet) per Joe's
   decision, since Eclipse would eject it right back out on first open anyway. Everything else is
   queued for the new backpack.
3. Picks the smallest tier (0/1/2/3 = 3/9/18/40 slots) that fits the **post-eject** real item count.
4. Builds `contents.pages` byte-for-byte to FORMAT.md's captured shape: real items placed into the
   unlocked region starting from its **highest** slot number and working down (the page walker
   processes slot 26 downto 0, consuming the oldest not-yet-placed old item each time it reaches an
   unlocked slot - confirmed empirically: a 3-item old backpack converted to tier 0 landed the
   first old item at slot 2, not slot 0), filler (`black_stained_glass_pane`) in every locked slot,
   pagination arrows at slot 26 (next) / 18 (back) where applicable. An unlocked slot with no item
   queued for it gets no entry at all (matches the real pack's own sparse behaviour).
5. Replaces the old item in its exact original slot with a byte-identical "never opened" new-tier
   base item (literal copied from `eclipse-backpacks`' own `give/backpack/tiers/*.mcfunction`), then
   layers `init.id` / `init.slots` / `contents.pages` on top - the same three fields
   `bp/summon/init.mcfunction` itself writes on a real first open.
6. Writes every original old item (untouched, full components) to a backup record.

## Backup / restore storage scheme

`storage ulgconvert:backup backups.<newBackpackId>` = 
```
{
  id: <int>,                 # == the new backpack's custom_data.backpacks.init.id
  tier: <int>,
  owner_uuid: [I; ...],      # player UUID at conversion time
  source: "offhand" | "inventory:<N>" | "enderchest:<N>",
  converted_at: <long>,      # gametime ticks
  backup: [ {Slot:<old-ulg-slot>, id, count, components}, ... ],  # every original item, untouched
  ejected: [ {Slot:<old-ulg-slot>, id, count, components}, ... ] # the subset handed to the player
}
```

Keying the backup by the *new* backpack's own id (rather than, say, the old ulg `bp.id.uniq`, or
player UUID + timestamp) means "which backup goes with this backpack" is a single lookup with no
separate index to keep in sync: read `custom_data.backpacks.init.id` off the backpack in question,
then `data get storage ulgconvert:backup backups.<that id>`.

**Restore path:** `execute as <player> run function ulgconvert:restore/item {id:<backpackId>,
index:<arrayIndex>, slot:<0-26>}` pulls `backups.<id>.backup[<index>]` (or hand-edit the command to
point at `.ejected[<index>]` for an ejected item) and gives it to the player at the named inventory
slot, components and all. `slot` is the `/item` command's `inventory.N` argument index (0-26, raw
NBT Slot 9-35 - see the mapping note above), not a raw Inventory Slot number. Only a single-item
restore is implemented; restoring every item from a backup record is the same call looped over
`0..(list length - 1)`, not built as a batch command here since no restore was needed during this
dispatch's testing beyond proving the path works.
