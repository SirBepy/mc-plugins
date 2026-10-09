# Dragonkind Evolved resource pack

Fourth source of the join pack built by `../build_server_pack.py`: the `assets/` of
`dragonkind-evolved-RP-by-kanokarob-v1.10.zip`, the resource pack Modrinth ships beside the
Dragonkind Evolved v1.15 datapack (version `ukamYQGg`, file sha512 starting `0c6c218f0d1ce7ba`),
which `server/datapacks/dragonkind-evolved-v1.15.zip` needs. Without it, the dragon's boss bar
shows its raw translation keys (`dragon.dke.difficulty.ferocious...`) and the datapack's items and
armor have no textures.

Unchanged except: its `pack.mcmeta` and `pack.png` are left out (the join pack has its own), and
`assets/dke/textures/item/forcefield_layers.pdn` (a Paint.NET source file the game never reads).
Everything else is namespaced `dke`, plus `assets/minecraft/lang/en_us.json`, which no other source
of the join pack has.

Dragonkind Evolved is by kanokarob (https://modrinth.com/datapack/dragonkind-evolved). Its license
forbids redistributing the pack without the author's written permission; Joe got that permission
from kanokarob on 2026-10-09 to merge this resource pack into the Claude SMP join pack.
