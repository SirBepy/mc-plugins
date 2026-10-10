# Override of Reg's More Foods' data/rmf/function/bg/stonecutter_damage.mcfunction (upstream
# v1.5.2+26.3). A live vanilla perf profile taken 2026-10-10 (5 players) showed rmf:tick at
# ~30% of the whole server tick, ~26% of that from this function's original 6 lines: each one
# scans every non-player entity (or every non-creative player) and evaluates the
# rmf:on_stonecutter predicate - a location_check plus an inverted entity-type-tag check - once per
# entity, per line. That's up to 6 full predicate evaluations per matching entity per tick for one
# block check. This version evaluates the config score once up front (so the whole function is a
# no-op the instant the feature is off, instead of still scanning every entity first), folds the
# entity-type-tag exclusion into the @e selector itself (a cheap built-in filter, not a predicate),
# replaces the predicate's location_check with a direct `if block` test, and does one pass per
# group that calls the shared stonecutter_hit function instead of repeating the same check 3x for
# damage/sound/particle.

execute if score rmf.config rmf.stonecutterDamage matches 1 as @e[type=!player,type=!#rmf:immune_to_stonecutter] at @s if block ~ ~ ~ minecraft:stonecutter run function rmf:bg/stonecutter_hit
execute if score rmf.config rmf.stonecutterDamage matches 1 as @a[gamemode=!creative,gamemode=!spectator] at @s if block ~ ~ ~ minecraft:stonecutter run function rmf:bg/stonecutter_hit
