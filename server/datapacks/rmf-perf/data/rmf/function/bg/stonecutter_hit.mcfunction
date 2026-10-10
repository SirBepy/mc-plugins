# Shared by rmf-perf's stonecutter_damage override: runs once per entity already confirmed to be
# standing on a stonecutter (the `as`/`at` context is set by the caller), instead of each caller
# re-running its own damage/sound/particle lines. Values match upstream Reg's More Foods exactly.
damage @s 4 rmf:slicing by @s
playsound minecraft:ui.stonecutter.take_result block @a ~ ~ ~ 1 2
particle minecraft:damage_indicator ~ ~ ~ 0.1 0.1 0.1 0.01 10
