# Pulls one original item back out of a backup record and gives it to the executing player. Invoked
# `execute as <player> run function ulgconvert:restore/item {id:<backpackId>, index:<arrayIndexInto
# .backup>, slot:<0-26>}`.
# id/index address storage ulgconvert:backup backups.<id>.backup[<index>] - the exact original item
# (components and all) as it sat in the old ulg Inventory before conversion; .ejected[<index>] holds
# the bundle/shulker items that were handed over live instead of placed, same shape, same restore
# path if one of those also needs reconstructing from the backup record.
#
# `slot` is the /item command's "inventory.N" argument index (0-26 = the 27 non-hotbar main-inventory
# slots - see convert/eject_find_slot.mcfunction's comment), not a raw Inventory Slot number.
#
# Vanilla blocks `data modify entity <player> ...` writes outright ("Unable to modify player data",
# confirmed empirically - see convert/eject_place_inventory.mcfunction's comment), so cur is built on
# a scratch entity first and moved onto the player via `item replace ... from entity`, same pattern.
$data modify storage ulgconvert:temp cur set from storage ulgconvert:backup backups.$(id).backup[$(index)]
kill @e[tag=ulgconvert.scratch]
summon chest_minecart ~ ~210 ~ {Tags:["ulgconvert.scratch"],Invisible:1b,NoGravity:1b,Invulnerable:1b,Silent:1b}
item replace entity @e[tag=ulgconvert.scratch,limit=1] container.0 with minecraft:stone
data modify entity @e[tag=ulgconvert.scratch,limit=1] Items[{Slot:0b}].id set from storage ulgconvert:temp cur.id
data modify entity @e[tag=ulgconvert.scratch,limit=1] Items[{Slot:0b}].count set from storage ulgconvert:temp cur.count
execute if data storage ulgconvert:temp cur.components run data modify entity @e[tag=ulgconvert.scratch,limit=1] Items[{Slot:0b}].components set from storage ulgconvert:temp cur.components
$item replace entity @s inventory.$(slot) from entity @e[tag=ulgconvert.scratch,limit=1] container.0
kill @e[tag=ulgconvert.scratch]
