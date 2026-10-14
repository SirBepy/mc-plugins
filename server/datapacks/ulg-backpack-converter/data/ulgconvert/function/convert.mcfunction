# Entry point: `execute as <player> run function ulgconvert:convert`. Covers the ulg pack's
# documented canonical carry location (offhand, confirmed from source - see FORMAT.md). Inventory
# and ender chest slots convert the same way via ulgconvert:convert/item, just not auto-scanned yet
# here - see README.md ("Entry points" / "Scope not yet wired") for the per-slot nbt/item_slot_arg
# pairing (inventory has a hotbar-vs-rest split, confirmed empirically, NOT 1:1 with the raw Slot
# number) and how to drive it manually in the meantime.
function ulgconvert:convert/item {nbt:"equipment.offhand",item_slot_arg:"weapon.offhand",source_desc:"offhand"}
