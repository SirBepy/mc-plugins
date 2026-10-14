# Put storage ulgconvert:temp cur into the scratch entity's container.0 for a predicate test only.
# "with air" first so Items has a Slot:0 element to target (data modify's [{filter}] addressing needs
# an existing match); then id/count/components are copied individually so the scratch entity's own
# Slot:0 is never overwritten by cur's (old, irrelevant) Slot field - a whole-compound "set from"
# here would re-home the live entity's item to cur's old slot number instead of slot 0.
item replace entity @e[tag=ulgconvert.scratch,limit=1] container.0 with air
data modify entity @e[tag=ulgconvert.scratch,limit=1] Items[{Slot:0b}].id set from storage ulgconvert:temp cur.id
data modify entity @e[tag=ulgconvert.scratch,limit=1] Items[{Slot:0b}].count set from storage ulgconvert:temp cur.count
execute if data storage ulgconvert:temp cur.components run data modify entity @e[tag=ulgconvert.scratch,limit=1] Items[{Slot:0b}].components set from storage ulgconvert:temp cur.components
