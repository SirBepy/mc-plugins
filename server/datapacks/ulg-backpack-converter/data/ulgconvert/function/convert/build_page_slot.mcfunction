# Walks one page's 27 container slots from 26 down to 0 (same direction as the real pack's own
# bp/container/init/fill.mcfunction), deciding per-slot what - if anything - belongs in the sparse
# contents.pages entry for that Slot. Current slot/thresholds live in scoreboard (set by the
# build_pages_tier* caller and by build_page_prev), never passed as macro args, so none of this
# chain needs macros at all.
#
# Priority, matching FORMAT.md exactly: next-page arrow (slot 26) > go-back arrow (slot 18) >
# a queued real item if this slot is inside the unlocked range > nothing (truly empty, unlocked,
# ran out of real items) > filler (locked, non-arrow slot).

execute if score $slot ulgconvert.scratch matches 26 if score $has_next ulgconvert.scratch matches 1 run return run function ulgconvert:convert/build_page_next_arrow
execute if score $slot ulgconvert.scratch matches 18 if score $has_back ulgconvert.scratch matches 1 run return run function ulgconvert:convert/build_page_back_arrow
execute if score $slot ulgconvert.scratch <= $unlocked_end ulgconvert.scratch if data storage ulgconvert:temp new.real[0] run return run function ulgconvert:convert/build_page_real_item
execute unless score $slot ulgconvert.scratch <= $unlocked_end ulgconvert.scratch run function ulgconvert:convert/build_page_filler

function ulgconvert:convert/build_page_prev
