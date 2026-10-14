# Inventory had no free slot - drop at the player's feet instead (per Joe's conversion-decision call). 0s/short-suffix
# PickupDelay is plain SNBT, not a command time literal.
summon item ~ ~ ~ {Item:{id:"minecraft:air",count:1},Tags:["ulgconvert.dropped"],PickupDelay:0s}
data modify entity @e[tag=ulgconvert.dropped,limit=1,sort=nearest] Item.id set from storage ulgconvert:temp cur.id
data modify entity @e[tag=ulgconvert.dropped,limit=1,sort=nearest] Item.count set from storage ulgconvert:temp cur.count
execute if data storage ulgconvert:temp cur.components run data modify entity @e[tag=ulgconvert.dropped,limit=1,sort=nearest] Item.components set from storage ulgconvert:temp cur.components
tag @e[tag=ulgconvert.dropped,limit=1,sort=nearest] remove ulgconvert.dropped
