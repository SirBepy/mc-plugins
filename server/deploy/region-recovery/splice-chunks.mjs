// Splices specific chunks' raw (still-compressed) data from an OLD .mca region file into a
// NEW .mca region file, leaving every other chunk in the NEW file byte-for-byte untouched.
// No NBT decode/re-encode needed: Anvil chunk payloads are opaque blobs behind a 4KB sector
// header, so a chunk can be relocated into a different region file's container as-is.
//
// Built during the StormieTown Towny-revert recovery (2026-10-13/14, see memory
// towny-revert-on-unclaim-incident.md) and promoted here to a committed location so a future
// live data-loss incident doesn't need to rebuild this from scratch under time pressure.
//
// Usage: node splice-chunks.mjs <old.mca> <new.mca> <out.mca> <globalChunkX>,<globalChunkZ> [...]
import { readFileSync, writeFileSync } from 'node:fs';

const SECTOR = 4096;

function regionXZ(path) {
  const m = /r\.(-?\d+)\.(-?\d+)\.mca$/.exec(path);
  if (!m) throw new Error(`not a region file name: ${path}`);
  return { rx: Number(m[1]), rz: Number(m[2]) };
}

// Returns the raw stored bytes for one chunk (4-byte length prefix + 1-byte compression type +
// payload), or null if the chunk is unset in this region.
function readRawChunk(buf, localIndex) {
  const header = new DataView(buf.buffer, buf.byteOffset, SECTOR);
  const loc = header.getUint32(localIndex * 4);
  if (loc === 0) return null;
  const offset = (loc >>> 8) * SECTOR;
  const lenView = new DataView(buf.buffer, buf.byteOffset + offset, 4);
  const length = lenView.getUint32(0); // includes the 1 compression-type byte
  return buf.subarray(offset, offset + 4 + length);
}

const [, , oldPath, newPath, outPath, ...coords] = process.argv;
if (!oldPath || !newPath || !outPath || coords.length === 0) {
  console.error('usage: node splice-chunks.mjs <old.mca> <new.mca> <out.mca> <gx>,<gz> [...]');
  process.exit(1);
}

const { rx, rz } = regionXZ(newPath);
const oldRegionXZ = regionXZ(oldPath);
if (oldRegionXZ.rx !== rx || oldRegionXZ.rz !== rz) {
  throw new Error(`region mismatch: old is r.${oldRegionXZ.rx}.${oldRegionXZ.rz}, new is r.${rx}.${rz}`);
}

const oldBuf = readFileSync(oldPath);
let outBuf = Buffer.from(readFileSync(newPath)); // mutable copy, grown as needed

// Pad to a sector boundary first so appends land cleanly.
if (outBuf.length % SECTOR !== 0) {
  const pad = SECTOR - (outBuf.length % SECTOR);
  outBuf = Buffer.concat([outBuf, Buffer.alloc(pad)]);
}

const now = Math.floor(Date.now() / 1000);

for (const pair of coords) {
  const [gxStr, gzStr] = pair.split(',');
  const gx = Number(gxStr), gz = Number(gzStr);
  const localCx = gx - rx * 32;
  const localCz = gz - rz * 32;
  if (localCx < 0 || localCx > 31 || localCz < 0 || localCz > 31) {
    throw new Error(`chunk ${gx},${gz} is not inside region r.${rx}.${rz}`);
  }
  const localIndex = localCx + localCz * 32;

  const raw = readRawChunk(oldBuf, localIndex);
  if (!raw) {
    console.error(`chunk ${gx},${gz}: unset in OLD region, skipping`);
    continue;
  }

  const sectorsNeeded = Math.ceil(raw.length / SECTOR);
  const appendOffset = outBuf.length;
  const appendSector = appendOffset / SECTOR;
  const padded = Buffer.alloc(sectorsNeeded * SECTOR);
  raw.copy(padded);
  outBuf = Buffer.concat([outBuf, padded]);

  const header = new DataView(outBuf.buffer, outBuf.byteOffset, SECTOR);
  header.setUint32(localIndex * 4, (appendSector << 8) | sectorsNeeded);
  const tsHeader = new DataView(outBuf.buffer, outBuf.byteOffset + SECTOR, SECTOR);
  tsHeader.setUint32(localIndex * 4, now);

  console.log(`chunk ${gx},${gz} (local ${localCx},${localCz}): spliced ${raw.length}B -> sector ${appendSector} (${sectorsNeeded} sectors)`);
}

writeFileSync(outPath, outBuf);
console.log(`wrote ${outPath} (${outBuf.length} bytes)`);
