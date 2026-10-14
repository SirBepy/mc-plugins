# Tier 3, 40 slots, 2 pages (FORMAT.md table): page0 unlocked 0-25 + next-page arrow at 26;
# page1 unlocked 0-13 + go-back arrow at 18 (14-26 except 18 filler). The real-item queue
# (new.real) is shared and consumed in order across both pages, so page0 fills first.
data modify storage ulgconvert:temp new.page set value []
scoreboard players set $slot ulgconvert.scratch 26
scoreboard players set $unlocked_end ulgconvert.scratch 25
scoreboard players set $has_next ulgconvert.scratch 1
scoreboard players set $has_back ulgconvert.scratch 0
function ulgconvert:convert/build_page_slot
data modify storage ulgconvert:temp new.pages append from storage ulgconvert:temp new.page

data modify storage ulgconvert:temp new.page set value []
scoreboard players set $slot ulgconvert.scratch 26
scoreboard players set $unlocked_end ulgconvert.scratch 13
scoreboard players set $has_next ulgconvert.scratch 0
scoreboard players set $has_back ulgconvert.scratch 1
function ulgconvert:convert/build_page_slot
data modify storage ulgconvert:temp new.pages append from storage ulgconvert:temp new.page
