// Reads a .mca region file and prints every chest/barrel/shulker/hopper/furnace block entity in
// one or more chunks (local coords, 0-31 within the region), so a recovered/spliced region file
// can be spot-checked for its loot before deploying it live.
//
// Built during the StormieTown Towny-revert recovery (2026-10-13/14, see memory
// towny-revert-on-unclaim-incident.md) and promoted here to a committed location so a future
// live data-loss incident doesn't need to rebuild this from scratch under time pressure.
//
// Usage: node verify-chest.mjs <region.mca> <localChunkX>,<localChunkZ> [...]
import { readRegionChunks } from "./lib/region.mjs";

const [, , path, ...pairs] = process.argv;
if (!path || pairs.length === 0) {
  console.error("usage: node verify-chest.mjs <region.mca> <localChunkX>,<localChunkZ> [...]");
  process.exit(1);
}
const targets = new Set(pairs.map((p) => p.trim()));

for (const { cx, cz, nbt } of readRegionChunks(path, (cx, cz, err) => console.error("err", cx, cz, err.message))) {
  if (!targets.has(`${cx},${cz}`)) continue;
  const entities = nbt.block_entities ?? nbt.TileEntities ?? [];
  console.log(`chunk local ${cx},${cz}: status=${nbt.Status} blockEntities=${entities.length}`);
  for (const be of entities) {
    const id = be.id;
    if (!id) continue;
    if (/chest|barrel|shulker|hopper|furnace/i.test(id)) {
      const items = be.Items ?? [];
      console.log(`  ${id} at ${be.x},${be.y},${be.z}: ${items.length} item stack(s)`);
      for (const it of items) {
        console.log(`    - ${it.id} x${it.count ?? it.Count ?? "?"}`);
      }
    }
  }
}
