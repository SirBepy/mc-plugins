# @s is the menu!

# Update menu:
tag @s add backpacks.this_update_backpack_menu
$execute as @a[tag=backpacks.offhand] if score @s backpacks.id = @n[tag=backpacks.this_update_backpack_menu] backpacks.id run data modify entity @n[tag=backpacks.this_update_backpack_menu] Items set from entity @s equipment.offhand.components."minecraft:custom_data".backpacks.contents.pages[$(page)]
$execute as @a[tag=backpacks.mainhand] if score @s backpacks.id = @n[tag=backpacks.this_update_backpack_menu] backpacks.id run data modify entity @n[tag=backpacks.this_update_backpack_menu] Items set from entity @s SelectedItem.components."minecraft:custom_data".backpacks.contents.pages[$(page)]
tag @s remove backpacks.this_update_backpack_menu

# Record which page is on screen right where the Items swap happens. A command-chain-length
# cutoff (65536) between interaction/main's page-score change and this function leaves
# backpacks.pages ahead of the display; save/main reads this score instead, so the next save
# still lands on the page actually shown.
$scoreboard players set @s backpacks.displayed_page $(page)
