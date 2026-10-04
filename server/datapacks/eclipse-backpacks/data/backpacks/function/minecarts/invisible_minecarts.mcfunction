data merge entity @s[type=minecart] {DisplayState:{id:"minecraft:command_block",properties:{conditional:"true",facing:"down"}},DisplayOffset:4}
data merge entity @s[type=chest_minecart] {DisplayState:{id:"minecraft:command_block",properties:{conditional:"true",facing:"up"}},DisplayOffset:4}
data merge entity @s[type=furnace_minecart] {DisplayState:{id:"minecraft:command_block",properties:{conditional:"true",facing:"north"}},DisplayOffset:4}
data merge entity @s[type=tnt_minecart] {DisplayState:{id:"minecraft:command_block",properties:{conditional:"true",facing:"south"}},DisplayOffset:4}
data merge entity @s[type=hopper_minecart] {DisplayState:{id:"minecraft:command_block",properties:{conditional:"true",facing:"west"}},DisplayOffset:4}
tag @s add backpacks.invisible_minecart
tag @s[type=furnace_minecart] add backpacks.furnace
