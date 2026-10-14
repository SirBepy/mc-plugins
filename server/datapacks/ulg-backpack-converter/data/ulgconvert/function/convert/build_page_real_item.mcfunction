# Pop the front of the real-item queue (new.real, already classified by classify_loop - never a
# bundle/non-empty shulker) and place it at the current slot. Whole-compound append then a single
# Slot overwrite: new.real entries still carry their OLD ulg Inventory Slot value at this point
# (harmless - it's plain storage, nothing has lived-entity-loaded it), so this is the one write
# that makes them correct for the NEW backpack's page.
data modify storage ulgconvert:temp new.page append from storage ulgconvert:temp new.real[0]
execute store result storage ulgconvert:temp new.page[-1].Slot byte 1 run scoreboard players get $slot ulgconvert.scratch
data remove storage ulgconvert:temp new.real[0]
function ulgconvert:convert/build_page_prev
