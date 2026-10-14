data remove storage ulgconvert:temp old.inventory[0]
execute store result score #remaining ulgconvert.scratch run data get storage ulgconvert:temp old.inventory
execute if score #remaining ulgconvert.scratch matches 1.. run function ulgconvert:convert/classify_loop
