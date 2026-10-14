# Pops storage ulgconvert:temp old.inventory[0] (the ulg Inventory list is sparse but array order
# is still "one real item per element", so index 0 is always "the next one to handle" regardless of
# its original old Slot number - we never need that old Slot value again after this). Every entry is
# backed up verbatim first, then staged into the scratch entity's container.0 (id/count/components
# only, never Slot - see classify_stage.mcfunction) so a real #bundles / #shulker_boxes item
# predicate can be tested against it; vanilla has no way to test an item predicate against a bare
# stored compound, only against a real container slot.
data modify storage ulgconvert:temp cur set from storage ulgconvert:temp old.inventory[0]
data modify storage ulgconvert:temp new.backup append from storage ulgconvert:temp cur

function ulgconvert:convert/classify_stage

execute if items entity @e[tag=ulgconvert.scratch,limit=1] container.0 #minecraft:bundles run return run function ulgconvert:convert/classify_eject
execute if items entity @e[tag=ulgconvert.scratch,limit=1] container.0 #minecraft:shulker_boxes[!minecraft:container=[]] run return run function ulgconvert:convert/classify_eject

data modify storage ulgconvert:temp new.real append from storage ulgconvert:temp cur
function ulgconvert:convert/classify_next
