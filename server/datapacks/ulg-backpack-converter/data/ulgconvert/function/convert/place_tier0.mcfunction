# macro arg: {item_slot_arg}. Builds the new item on the scratch staging entity (summoned earlier
# in item.mcfunction, still alive) rather than writing directly onto the player's slot:
# equipment.offhand (and presumably the other virtual equipment-view paths) rejects data
# modify/execute store writes ("Unable to modify player data", confirmed empirically against this
# server) even though READS from those same paths work fine - item replace's entity-to-entity copy
# is what actually re-homes a built item onto any player slot, equipment included. This mirrors
# exactly how bp/summon/init.mcfunction itself moves the real backpack item in and out of a hand.
#
# Base item literal copied verbatim from eclipse-backpacks' give/backpack/tiers/1.mcfunction (tier
# 0, 3 slots) so a converted backpack is byte-identical to a freshly given one before init/pages are
# layered on. Mirrors bp/summon/init.mcfunction's own "Set Id" / "Set slots data" steps: init.id from
# the just-reserved new.id, init.slots is the tier's own known capacity (no need to read it back),
# contents.pages overwritten with what build_pages assembled.
item replace entity @e[tag=ulgconvert.scratch,limit=1] container.0 with command_block[item_model="backpacks:backpacks/normal/1/brown",item_name={translate:'item.backpacks.normal.1'},max_stack_size=1,custom_data={backpacks:{real_backpack:1b,tier:0,contents:{slots:3}}},rarity=uncommon,lore=[{color:'blue',italic:true,text:'Backpacks!'}]]
execute store result entity @e[tag=ulgconvert.scratch,limit=1] Items[{Slot:0b}].components."minecraft:custom_data".backpacks.init.id int 1 run data get storage ulgconvert:temp new.id
data modify entity @e[tag=ulgconvert.scratch,limit=1] Items[{Slot:0b}].components."minecraft:custom_data".backpacks.init.slots set value 3
data modify entity @e[tag=ulgconvert.scratch,limit=1] Items[{Slot:0b}].components."minecraft:custom_data".backpacks.contents.pages set from storage ulgconvert:temp new.pages
$item replace entity @s $(item_slot_arg) from entity @e[tag=ulgconvert.scratch,limit=1] container.0
