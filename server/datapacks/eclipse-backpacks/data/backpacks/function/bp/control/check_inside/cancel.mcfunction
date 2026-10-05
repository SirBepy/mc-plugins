# @p[advancements={backpacks:not_holding_bp=true}] is the player at player! | @s & @n[tag=backpacks.backpack_kill_this] are the menu!
# say cancel
# Add a timer to stop crounching from removing the menu:
tag @p[advancements={backpacks:not_holding_bp=true}] add backpacks.give_back_timer
scoreboard players set @p[advancements={backpacks:not_holding_bp=true}] backpacks.give_back 1

# Reset:
tag @s remove backpacks.backpack_kill_this
advancement revoke @p[advancements={backpacks:not_holding_bp=true}] only backpacks:not_holding_bp

