# macro arg: {slot: <0-26>} - the /item command's "inventory.N" slot ARGUMENT index, which (verified
# empirically against this server, not assumed) addresses the 27 **non-hotbar** main-inventory
# slots, i.e. raw NBT Inventory Slot 9-35, NOT Slot 0-26 - "inventory.0" put a test item at
# Slot: 9b. Hotbar (Slot 0-8) is deliberately not scanned here; 27 free slots to try is enough for
# this prototype's eject fallback, and keeps the one index space (no hotbar.N branch needed).
# Past 26, drop at the player's feet instead.
$execute unless items entity @s inventory.$(slot) * run return run function ulgconvert:convert/eject_place_inventory {slot:$(slot)}
$scoreboard players set #next ulgconvert.scratch $(slot)
scoreboard players add #next ulgconvert.scratch 1
execute if score #next ulgconvert.scratch matches ..26 run return run function ulgconvert:convert/eject_find_slot_next
function ulgconvert:convert/eject_place_feet
