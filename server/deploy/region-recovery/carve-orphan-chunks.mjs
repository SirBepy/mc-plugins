// Forensic recovery attempt: Anvil region files don't truly delete a chunk's old bytes when it's
// rewritten - the header just stops pointing at those sectors, and the bytes sit there as "free"
// space until something else happens to reuse them. If a revert/resave triggered a chunk resave
// (new size -> new sectors) rather than reusing the same sectors, the PRE-revert chunk bytes for
// a target chunk may still be sitting unreferenced in this exact file. Scans every 4KB sector not
// claimed by the current header table, tries to decompress+parse it as an NBT chunk, and reports
// any hit whose xPos/zPos matches the target - regardless of what the header currently says.
//
// Built during the StormieTown Towny-revert recovery (2026-10-13/14, see memory
// towny-revert-on-unclaim-incident.md) and promoted here to a committed location so a future
// live data-loss incident doesn't need to rebuild this from scratch under time pressure.
//
// Usage: node carve-orphan-chunks.mjs <region.mca> <targetChunkX> <targetChunkZ>
import { readFileSync } from 'node:fs';
import { inflateSync, gunzipSync } from 'node:zlib';
import { readNbt } from './lib/nbt.mjs';

const SECTOR = 4096;
const path = process.argv[2];
const targetX = Number(process.argv[3]);
const targetZ = Number(process.argv[4]);
if (!path || Number.isNaN(targetX) || Number.isNaN(targetZ)) {
  console.error('usage: node carve-orphan-chunks.mjs <region.mca> <targetChunkX> <targetChunkZ>');
  process.exit(1);
}

const buf = readFileSync(path);
const header = new DataView(buf.buffer, buf.byteOffset, SECTOR);

const claimed = new Set();
for (let i = 0; i < 1024; i++) {
  const loc = header.getUint32(i * 4);
  if (loc === 0) continue;
  const sector = loc >>> 8;
  const count = loc & 0xff;
  for (let s = 0; s < count; s++) claimed.add(sector + s);
}

const totalSectors = Math.floor(buf.length / SECTOR);
console.log(`region has ${totalSectors} sectors, ${claimed.size} claimed, ${totalSectors - claimed.size} free to scan`);

function tryDecompress(type, data) {
  try {
    switch (type) {
      case 1: return gunzipSync(data);
      case 2: return inflateSync(data);
      case 3: return data;
      default: return null;
    }
  } catch { return null; }
}

let hits = 0;
for (let sector = 2; sector < totalSectors; sector++) {
  if (claimed.has(sector)) continue;
  const offset = sector * SECTOR;
  if (offset + 5 > buf.length) continue;
  const view = new DataView(buf.buffer, buf.byteOffset + offset, 5);
  const length = view.getUint32(0);
  const type = view.getUint8(4);
  if (length < 1 || length > SECTOR * 64) continue; // sanity bound
  if (offset + 4 + length > buf.length) continue;
  const raw = buf.subarray(offset + 5, offset + 4 + length);
  const decompressed = tryDecompress(type, raw);
  if (!decompressed) continue;
  let nbt;
  try { nbt = readNbt(decompressed); } catch { continue; }
  const xPos = nbt.xPos ?? nbt.Level?.xPos;
  const zPos = nbt.zPos ?? nbt.Level?.zPos;
  if (xPos === undefined) continue;
  if (sector % 50 === 0) process.stdout.write(`.`);
  if (xPos === targetX && zPos === targetZ) {
    hits++;
    const entities = nbt.block_entities ?? nbt.TileEntities ?? [];
    console.log(`\nHIT at sector ${sector}: chunk ${xPos},${zPos}, blockEntities=${entities.length}, Status=${nbt.Status}`);
    for (const be of entities) {
      if (!be.id) continue;
      const items = be.Items ?? [];
      console.log(`  ${be.id} at ${be.x},${be.y},${be.z}: ${items.length} stacks`);
      for (const it of items) console.log(`    - ${it.id} x${it.count ?? it.Count ?? '?'}`);
    }
  }
}
console.log(`\ndone, ${hits} matching orphan chunk(s) found for ${targetX},${targetZ}`);
