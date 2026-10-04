# Runs on every server start via #minecraft:load, but only applies once per world: the
# "initialized" score guards against stomping an owner's later /gamerule or /worldborder change
# after a restart.
#
# Gamerule ids verified on a live Paper 26.3 build 143 server (read from
# dimensions/minecraft/<dim>/data/minecraft/game_rules.dat, not guessed): they were renamed to
# snake_case resource ids, and fire spread is now an integer radius, not the old doFireTick
# boolean toggle.
scoreboard objectives add server_rules dummy
# Above the once-per-world guard: the donkey loop reschedules itself, but a restart clears schedules.
function server_rules:faster_donkeys
execute if score initialized server_rules matches 1 run return 0
scoreboard players set initialized server_rules 1

gamerule players_sleeping_percentage 50
# fire_spread_radius_around_player replaced doFireTick; 0 means fire never spreads from its source.
gamerule fire_spread_radius_around_player 0
# Hides the Player Locator Bar, which points every player at every other player within range.
gamerule locator_bar false
