# Slot 18, "go back" arrow - byte-for-byte per FORMAT.md. Only ever called when $slot is 18.
data modify storage ulgconvert:temp new.page append value {Slot:18b,id:"minecraft:arrow",count:1,components:{"minecraft:item_model":"backpacks:gui/go_back","minecraft:custom_name":{italic:0b,color:"green",translate:"menu.backpacks.interaction.go_back"},"minecraft:custom_data":{menu_backpack:{go_back:1b,Slot:18}}}}
function ulgconvert:convert/build_page_prev
