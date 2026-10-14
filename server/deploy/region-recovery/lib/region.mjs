// Vendored from sirbepys-minecraft-server/tools/lib/region.mjs so this repo's region recovery
// tools don't depend on a sibling repo's relative path.
import { readFileSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { inflateSync, gunzipSync } from 'node:zlib';
import { readNbt } from './nbt.mjs';

const SECTOR = 4096;
const EXTERNAL_FLAG = 0x80;

// Yields { cx, cz, nbt } for every readable chunk in an Anvil .mca file. cx/cz are local (0-31).
// A chunk that fails to decode (torn read while the live server was writing it) is skipped,
// never fatal: the next sync picks it up once the file's mtime changes again.
export function* readRegionChunks(path, onError = () => {}) {
  const file = readFileSync(path);
  if (file.length < SECTOR * 2) return;
  const header = new DataView(file.buffer, file.byteOffset, SECTOR);

  for (let i = 0; i < 1024; i++) {
    const loc = header.getUint32(i * 4);
    if (loc === 0) continue;
    const offset = (loc >>> 8) * SECTOR;
    const cx = i & 31;
    const cz = i >> 5;
    try {
      if (offset + 5 > file.length) throw new Error('offset past end of file');
      const view = new DataView(file.buffer, file.byteOffset + offset, 5);
      const length = view.getUint32(0);
      const type = view.getUint8(4);
      let data;
      if (type & EXTERNAL_FLAG) {
        const mcc = join(dirname(path), `c.${regionXZ(path).rx * 32 + cx}.${regionXZ(path).rz * 32 + cz}.mcc`);
        if (!existsSync(mcc)) throw new Error(`missing external chunk ${mcc}`);
        data = readFileSync(mcc);
      } else {
        data = file.subarray(offset + 5, offset + 4 + length);
      }
      yield { cx, cz, nbt: readNbt(decompress(type & ~EXTERNAL_FLAG, data)) };
    } catch (err) {
      onError(cx, cz, err);
    }
  }
}

function decompress(type, data) {
  switch (type) {
    case 1: return gunzipSync(data);
    case 2: return inflateSync(data);
    case 3: return data;
    default: throw new Error(`unsupported chunk compression ${type}`);
  }
}

export function regionXZ(path) {
  const m = /r\.(-?\d+)\.(-?\d+)\.mca$/.exec(path);
  if (!m) throw new Error(`not a region file: ${path}`);
  return { rx: Number(m[1]), rz: Number(m[2]) };
}
