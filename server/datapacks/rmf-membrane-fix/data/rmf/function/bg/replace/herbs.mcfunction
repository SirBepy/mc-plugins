# Overrides Reg's More Foods' own replace/herbs. rmf:tick runs it for a player carrying a plain
# Phantom Membrane (vanilla item_model, no lore). Upstream clears one and gives Coffee Beans, a
# migration for items from older More Foods versions, so every real membrane from an End loot chest
# (Dungeons and Taverns, Structory Towers) or from another pack's phantom loot became Coffee Beans.
# Every plain membrane becomes the membrane More Foods' own phantom loot table drops instead (same
# id, rmf:phantom_membrane model and name, so the two stack); the count is kept.
execute store result storage rmf_membrane_fix:replace count int 1 run clear @s minecraft:phantom_membrane[item_model="phantom_membrane",lore=[]]
function rmf_membrane_fix:give_membrane with storage rmf_membrane_fix:replace
