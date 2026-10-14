# Entry point for page assembly - #tier was set by convert/item.mcfunction right after classify_loop
# finished (based on the real, post-eject item count). Builds storage ulgconvert:temp new.pages,
# a list of one or two Items-style page lists, ready to drop straight onto
# contents.pages on the new backpack item.
data modify storage ulgconvert:temp new.pages set value []

execute if score #tier ulgconvert.scratch matches 0 run function ulgconvert:convert/build_pages_tier0
execute if score #tier ulgconvert.scratch matches 1 run function ulgconvert:convert/build_pages_tier1
execute if score #tier ulgconvert.scratch matches 2 run function ulgconvert:convert/build_pages_tier2
execute if score #tier ulgconvert.scratch matches 3 run function ulgconvert:convert/build_pages_tier3
