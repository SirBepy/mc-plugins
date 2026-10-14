# Slot 26, "next page" arrow - byte-for-byte per FORMAT.md. Only ever called when $slot is 26
# (build_page_slot's own guard), so the literal Slot:26 below always matches; still routed through
# build_page_prev afterwards same as every other branch.
data modify storage ulgconvert:temp new.page append value {Slot:26b,id:"minecraft:arrow",count:1,components:{"minecraft:item_model":"backpacks:gui/next_page","minecraft:custom_name":{italic:0b,color:"green",translate:"menu.backpacks.interaction.next_page"},"minecraft:custom_data":{menu_backpack:{next_page:1b,Slot:26}}}}
function ulgconvert:convert/build_page_prev
