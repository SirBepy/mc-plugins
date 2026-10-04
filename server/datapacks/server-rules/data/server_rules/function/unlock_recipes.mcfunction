# Every player knows every recipe, datapack ones included (backpacks, MasterCutter, More Foods), so
# they show in the recipe book and in client recipe viewers: since 1.21.2 a server only sends a
# player the recipes they have unlocked. Repeats every 10 seconds to cover new joins and datapack
# reloads; players who already have everything get no message.
recipe give @a *
schedule function server_rules:unlock_recipes 200t replace
