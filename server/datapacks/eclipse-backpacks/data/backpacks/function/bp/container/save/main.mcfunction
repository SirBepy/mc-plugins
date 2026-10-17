# @s is the menu!
# tellraw @a "###############"
# tellraw @a "save main"
# Get Player:
tag @s add backpacks.backpack_menu_save_this
execute as @a if score @s backpacks.id = @n[tag=backpacks.backpack_menu_save_this] backpacks.id run tag @s add backpacks.backpack_menu_save_this_player

# Get item Id:
execute store result storage backpacks:temp item.id int 1 run scoreboard players get @s backpacks.id
# backpacks.displayed_page, not backpacks.pages: it only changes in the same breath the menu's
# Items are actually replaced from a stored page (summon/summon, update/update), so a save
# always lands on the page that's really on screen, even if a command-chain-length cutoff left
# the navigation score (backpacks.pages) ahead of a display that never caught up.
execute store result storage backpacks:temp item.page int 1 run scoreboard players get @s backpacks.displayed_page

# Search:
execute as @p[tag=backpacks.backpack_menu_save_this_player] if function backpacks:bp/check/cursor as @n[tag=backpacks.backpack_menu_save_this] if score @s backpacks.type matches 0 run function backpacks:bp/container/save/save_to_cursor with storage backpacks:temp item
execute as @p[tag=backpacks.backpack_menu_save_this_player] if function backpacks:bp/check/cursor as @n[tag=backpacks.backpack_menu_save_this] if score @s backpacks.type matches 1.. run function backpacks:bp/container/enderchest/save

execute as @p[tag=backpacks.backpack_menu_save_this_player] if function backpacks:bp/check/inv as @n[tag=backpacks.backpack_menu_save_this] if score @s backpacks.type matches 0 run function backpacks:bp/container/save/search_inv with storage backpacks:temp item
execute as @p[tag=backpacks.backpack_menu_save_this_player] if function backpacks:bp/check/inv as @n[tag=backpacks.backpack_menu_save_this] if score @s backpacks.type matches 1.. run function backpacks:bp/container/enderchest/save
execute as @p[tag=backpacks.backpack_menu_save_this_player] unless function backpacks:bp/check/inv as @n[tag=backpacks.backpack_menu_save_this] if score @s backpacks.type matches 0 run function backpacks:bp/container/save/search_entity with storage backpacks:temp item
execute as @p[tag=backpacks.backpack_menu_save_this_player] unless function backpacks:bp/check/inv as @n[tag=backpacks.backpack_menu_save_this] if score @s backpacks.type matches 1.. run function backpacks:bp/container/enderchest/save

# Reset:
tag @s remove backpacks.backpack_menu_save_this
tag @a remove backpacks.backpack_menu_save_this_player
