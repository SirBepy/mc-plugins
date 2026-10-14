# Tier 2, 18 slots - base item literal from give/backpack/tiers/3.mcfunction. See place_tier0.mcfunction.
item replace entity @e[tag=ulgconvert.scratch,limit=1] container.0 with repeating_command_block[item_model="backpacks:backpacks/normal/3/brown",item_name={translate:'item.backpacks.normal.3'},max_stack_size=1,custom_data={backpacks:{real_backpack:1b,tier:2,contents:{slots:18}}},rarity=uncommon,lore=[{color:'blue',italic:true,text:'Backpacks!'}]]
execute store result entity @e[tag=ulgconvert.scratch,limit=1] Items[{Slot:0b}].components."minecraft:custom_data".backpacks.init.id int 1 run data get storage ulgconvert:temp new.id
data modify entity @e[tag=ulgconvert.scratch,limit=1] Items[{Slot:0b}].components."minecraft:custom_data".backpacks.init.slots set value 18
data modify entity @e[tag=ulgconvert.scratch,limit=1] Items[{Slot:0b}].components."minecraft:custom_data".backpacks.contents.pages set from storage ulgconvert:temp new.pages
$item replace entity @s $(item_slot_arg) from entity @e[tag=ulgconvert.scratch,limit=1] container.0
