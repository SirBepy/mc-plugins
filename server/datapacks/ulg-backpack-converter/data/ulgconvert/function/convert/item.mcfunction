# Convert one found old-format ulg backpack in place. Always invoked `execute as <player> run
# function ulgconvert:convert/item {...}` with 3 macro args:
#   nbt           - the data get/modify NBT path to the item, e.g. "equipment.offhand",
#                   "Inventory[{Slot:5b}]", "EnderItems[{Slot:12b}]"
#   item_slot_arg - the matching /item-command slot argument, e.g. "weapon.offhand",
#                   "inventory.5", "enderchest.12"
#   source_desc   - free-text label recorded on the backup entry, e.g. "offhand", "inventory:5"
#
# Guard first (no side effects yet): must actually be an old ulg backpack at that spot.
$execute unless items entity @s $(item_slot_arg) minecraft:carrot_on_a_stick run return 0
$execute unless data entity @s $(nbt).components."minecraft:custom_data".bp run return 0

# Reserve a fresh id from the real pack's own global counter, same get-then-increment order as
# backpacks:bp/summon/init.mcfunction (read current value for us, then bump it for the next
# first-open or conversion) - this is why eclipse-backpacks must already be loaded.
execute store result storage ulgconvert:temp new.id int 1 run scoreboard players get $global backpacks.id
scoreboard players add $global backpacks.id 1

data modify storage ulgconvert:temp new.owner_uuid set from entity @s UUID
$data modify storage ulgconvert:temp new.source set value "$(source_desc)"
execute store result storage ulgconvert:temp new.converted_at long 1 run time query gametime

# Whole-list copy of the old backpack's Inventory - legal without any [{filter}] match since it
# replaces old.inventory wholesale, works regardless of what arbitrary components the old items
# carry.
$data modify storage ulgconvert:temp old.inventory set from entity @s $(nbt).components."minecraft:custom_data".bp.Inventory
data modify storage ulgconvert:temp new.real set value []
data modify storage ulgconvert:temp new.ejected set value []
data modify storage ulgconvert:temp new.backup set value []

# Scratch staging entity - used for item-predicate classification (see classify_stage.mcfunction)
# AND, further down, for actually building the new item (place_tier*.mcfunction): stays alive until
# placement is done, then killed.
kill @e[tag=ulgconvert.scratch]
execute at @s run summon chest_minecart ~ ~200 ~ {Tags:["ulgconvert.scratch"],Invisible:1b,NoGravity:1b,Invulnerable:1b,Silent:1b}

execute store result score #count ulgconvert.scratch run data get storage ulgconvert:temp old.inventory
execute if score #count ulgconvert.scratch matches 1.. run function ulgconvert:convert/classify_loop

# Smallest tier that fits the REAL (post-eject) item count - 3/9/18/40 per Joe's sizing decision.
execute store result score #realcount ulgconvert.scratch run data get storage ulgconvert:temp new.real
scoreboard players set #tier ulgconvert.scratch 3
execute if score #realcount ulgconvert.scratch matches ..3 run scoreboard players set #tier ulgconvert.scratch 0
execute if score #realcount ulgconvert.scratch matches 4..9 run scoreboard players set #tier ulgconvert.scratch 1
execute if score #realcount ulgconvert.scratch matches 10..18 run scoreboard players set #tier ulgconvert.scratch 2
execute store result storage ulgconvert:temp new.tier int 1 run scoreboard players get #tier ulgconvert.scratch

function ulgconvert:convert/build_pages

$execute if score #tier ulgconvert.scratch matches 0 run function ulgconvert:convert/place_tier0 {item_slot_arg:"$(item_slot_arg)"}
$execute if score #tier ulgconvert.scratch matches 1 run function ulgconvert:convert/place_tier1 {item_slot_arg:"$(item_slot_arg)"}
$execute if score #tier ulgconvert.scratch matches 2 run function ulgconvert:convert/place_tier2 {item_slot_arg:"$(item_slot_arg)"}
$execute if score #tier ulgconvert.scratch matches 3 run function ulgconvert:convert/place_tier3 {item_slot_arg:"$(item_slot_arg)"}

kill @e[tag=ulgconvert.scratch]

# save_backup MUST run before eject_loop: eject_loop destructively pops new.ejected (each entry
# removed as it's handed to the player), so capturing the backup record after it ran would always
# see an empty .ejected list regardless of how many items were actually ejected (caught empirically -
# ejected.length read back as 0 even when items genuinely were ejected).
function ulgconvert:convert/save_backup with storage ulgconvert:temp new
function ulgconvert:convert/eject_loop
