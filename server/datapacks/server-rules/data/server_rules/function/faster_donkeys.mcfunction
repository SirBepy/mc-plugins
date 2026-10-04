# Donkeys and mules get the average horse speed (vanilla: donkey/mule 0.175, horse 0.1125-0.3375,
# average 0.225). The tag marks entities already handled so the base value is only set once and a
# later manual /attribute change sticks. Re-runs every 5 seconds to catch newly spawned or loaded ones.
execute as @e[type=#server_rules:slow_pack_animals,tag=!server_rules.fast] run attribute @s minecraft:movement_speed base set 0.225
tag @e[type=#server_rules:slow_pack_animals,tag=!server_rules.fast] add server_rules.fast
schedule function server_rules:faster_donkeys 100t replace
