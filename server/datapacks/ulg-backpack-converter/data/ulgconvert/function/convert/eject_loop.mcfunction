# Hands every entry in new.ejected (bundles / non-empty shulker boxes, per Joe's conversion-decision call) to
# the player: first free main-inventory slot, else dropped at their feet. Runs after build_pages so
# ejection never races the real-item placement above.
execute store result score #remaining ulgconvert.scratch run data get storage ulgconvert:temp new.ejected
execute if score #remaining ulgconvert.scratch matches 0 run return 0

data modify storage ulgconvert:temp cur set from storage ulgconvert:temp new.ejected[0]
data modify storage ulgconvert:temp eject_slot set value {slot:0}
function ulgconvert:convert/eject_find_slot with storage ulgconvert:temp eject_slot

data remove storage ulgconvert:temp new.ejected[0]
function ulgconvert:convert/eject_loop
