# Base case: slot 0 was just processed by build_page_slot, stop. Otherwise step down one slot and
# keep walking. $slot is scoreboard-only (see build_page_slot.mcfunction for why).
execute if score $slot ulgconvert.scratch matches 0 run return 0
scoreboard players remove $slot ulgconvert.scratch 1
function ulgconvert:convert/build_page_slot
