# Locked, non-arrow slot: the exact filler item FORMAT.md captured byte-for-byte from a real open
# (bp/container/init/fill.mcfunction gives the same item, Slot added as a second step - mirrored
# here as two writes for the same reason: the literal value below can't itself contain $slot).
data modify storage ulgconvert:temp new.page append value {id:"minecraft:black_stained_glass_pane",count:1,components:{"minecraft:item_model":"backpacks:gui/empty","minecraft:custom_name":"","minecraft:custom_data":{menu_backpack:{empty:1b}},"minecraft:tooltip_display":{hide_tooltip:1b}}}
execute store result storage ulgconvert:temp new.page[-1].Slot byte 1 run scoreboard players get $slot ulgconvert.scratch
execute store result storage ulgconvert:temp new.page[-1].components."minecraft:custom_data".menu_backpack.Slot int 1 run scoreboard players get $slot ulgconvert.scratch
