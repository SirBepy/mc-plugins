# @s is the menu, showing page 1 with a "go back" arrow on it!
# A later page's layout saved over page 1 (ImMijo's netherite backpack on live, 2026-10-10) leaves
# page 1 with "go back" and locked slots but no "next page", so the backpack opens on what looks
# like the last page and never reaches the real one. Page 1 of a backpack with more pages has no
# locked slots and only "next page" at 26; this display is saved back over page 1 on the next save.

data remove entity @s Items[{components:{"minecraft:custom_data":{menu_backpack:{go_back:1b}}}}]
data remove entity @s Items[{components:{"minecraft:custom_data":{menu_backpack:{empty:1b}}}}]
execute unless items entity @s container.26 * run item replace entity @s container.26 with arrow[custom_name={color:'green',italic:false,translate:'menu.backpacks.interaction.next_page'},item_model="backpacks:gui/next_page",custom_data={menu_backpack:{next_page:1b,Slot:26}}]
