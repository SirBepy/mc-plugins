# Tier 1, 9 slots, 1 page, unlocked 0-8.
data modify storage ulgconvert:temp new.page set value []
scoreboard players set $slot ulgconvert.scratch 26
scoreboard players set $unlocked_end ulgconvert.scratch 8
scoreboard players set $has_next ulgconvert.scratch 0
scoreboard players set $has_back ulgconvert.scratch 0
function ulgconvert:convert/build_page_slot
data modify storage ulgconvert:temp new.pages append from storage ulgconvert:temp new.page
