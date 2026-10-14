# One scratch objective holds every fake-player working value this pack uses ($slot, $unlocked_end,
# $has_next, $has_back, #count, #remaining, #realcount, #tier) - none of it is ever shown to players.
scoreboard objectives add ulgconvert.scratch dummy

# backpacks.id (the real Eclipse pack's own id counter) is NOT created here - this converter only
# ever reads/increments it, and depends on server/datapacks/eclipse-backpacks being loaded first so
# the objective and its $global seed (init/world.mcfunction) already exist. Converting with that
# pack absent/disabled will error loudly (scoreboard objective not found) rather than silently
# assigning colliding ids - that's intentional.
