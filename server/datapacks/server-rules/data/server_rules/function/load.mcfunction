# Runs on every server start via #minecraft:load, but only applies once per world: the
# "initialized" score guards against stomping an owner's later /gamerule or /worldborder change
# after a restart.
#
# Gamerule ids verified on a live Paper 26.3 build 143 server (read from
# dimensions/minecraft/<dim>/data/minecraft/game_rules.dat, not guessed): they were renamed to
# snake_case resource ids, and fire spread is now an integer radius, not the old doFireTick
# boolean toggle.
scoreboard objectives add server_rules dummy
execute if score initialized server_rules matches 1 run return 0
scoreboard players set initialized server_rules 1

gamerule players_sleeping_percentage 50
# fire_spread_radius_around_player replaced doFireTick; 0 means fire never spreads from its source.
gamerule fire_spread_radius_around_player 0
# Hides the Player Locator Bar, which points every player at every other player within range.
gamerule locator_bar false

# Paper 26.3 keeps a separate world border per dimension (confirmed via each dimension's own
# world_border.dat), so the nether needs its own execute-in. A #minecraft:load function's command
# source position is the overworld's 0,0,0, so "~ ~" centers on the origin (world spawn is chosen
# near it, small against a 5000 radius); execute in carries the same raw coordinates into the nether.
worldborder center ~ ~
worldborder set 10000
execute in minecraft:the_nether run worldborder center ~ ~
execute in minecraft:the_nether run worldborder set 2500
