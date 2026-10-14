# macro arg: {slot: <0-26>}, the "inventory.N" /item-command argument index (see
# eject_find_slot.mcfunction). Vanilla outright blocks `data modify entity <player> ...` writes
# ("Unable to modify player data", confirmed empirically for both equipment.* paths AND
# Inventory[{Slot:Nb}] - this is a genuine vanilla restriction on player entities specifically, not
# an RCON-only shadow like give/clear/item). The only sanctioned way to put arbitrary item NBT into
# a player's slot is `item replace ... from entity <non-player>` - so cur is built on the (reused)
# scratch entity first, same as classify_stage.mcfunction, then moved onto the player.
kill @e[tag=ulgconvert.scratch]
summon chest_minecart ~ ~210 ~ {Tags:["ulgconvert.scratch"],Invisible:1b,NoGravity:1b,Invulnerable:1b,Silent:1b}
item replace entity @e[tag=ulgconvert.scratch,limit=1] container.0 with minecraft:stone
data modify entity @e[tag=ulgconvert.scratch,limit=1] Items[{Slot:0b}].id set from storage ulgconvert:temp cur.id
data modify entity @e[tag=ulgconvert.scratch,limit=1] Items[{Slot:0b}].count set from storage ulgconvert:temp cur.count
execute if data storage ulgconvert:temp cur.components run data modify entity @e[tag=ulgconvert.scratch,limit=1] Items[{Slot:0b}].components set from storage ulgconvert:temp cur.components
$item replace entity @s inventory.$(slot) from entity @e[tag=ulgconvert.scratch,limit=1] container.0
kill @e[tag=ulgconvert.scratch]
