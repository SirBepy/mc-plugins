// Minimal big-endian NBT reader (Java Edition). Long arrays come back as BigInt64Array views
// because biome and heightmap data are bit-packed into 64-bit longs.
//
// Vendored from sirbepys-minecraft-server/tools/lib/nbt.mjs so this repo's region recovery
// tools don't depend on a sibling repo's relative path.

const END = 0, BYTE = 1, SHORT = 2, INT = 3, LONG = 4, FLOAT = 5, DOUBLE = 6,
  BYTE_ARRAY = 7, STRING = 8, LIST = 9, COMPOUND = 10, INT_ARRAY = 11, LONG_ARRAY = 12;

const decoder = new TextDecoder();

export function readNbt(buf) {
  const view = new DataView(buf.buffer, buf.byteOffset, buf.byteLength);
  let pos = 0;

  const str = () => {
    const len = view.getUint16(pos);
    pos += 2;
    const s = decoder.decode(buf.subarray(pos, pos + len));
    pos += len;
    return s;
  };

  const payload = (type) => {
    switch (type) {
      case BYTE: return view.getInt8(pos++);
      case SHORT: { const v = view.getInt16(pos); pos += 2; return v; }
      case INT: { const v = view.getInt32(pos); pos += 4; return v; }
      case LONG: { const v = view.getBigInt64(pos); pos += 8; return v; }
      case FLOAT: { const v = view.getFloat32(pos); pos += 4; return v; }
      case DOUBLE: { const v = view.getFloat64(pos); pos += 8; return v; }
      case BYTE_ARRAY: {
        const n = view.getInt32(pos); pos += 4;
        const v = buf.subarray(pos, pos + n); pos += n;
        return v;
      }
      case STRING: return str();
      case LIST: {
        const t = view.getInt8(pos++);
        const n = view.getInt32(pos); pos += 4;
        const out = new Array(Math.max(n, 0));
        for (let i = 0; i < n; i++) out[i] = payload(t);
        return out;
      }
      case COMPOUND: {
        const out = {};
        for (;;) {
          const t = view.getInt8(pos++);
          if (t === END) return out;
          out[str()] = payload(t);
        }
      }
      case INT_ARRAY: {
        const n = view.getInt32(pos); pos += 4;
        const out = new Int32Array(n);
        for (let i = 0; i < n; i++, pos += 4) out[i] = view.getInt32(pos);
        return out;
      }
      case LONG_ARRAY: {
        const n = view.getInt32(pos); pos += 4;
        const out = new BigInt64Array(n);
        for (let i = 0; i < n; i++, pos += 8) out[i] = view.getBigInt64(pos);
        return out;
      }
      default: throw new Error(`Unknown NBT tag ${type} at ${pos - 1}`);
    }
  };

  const rootType = view.getInt8(pos++);
  if (rootType !== COMPOUND) throw new Error(`Root tag is ${rootType}, expected compound`);
  str();
  return payload(COMPOUND);
}

// Palette entries come as a bare id string, or (26.x) a compound whose id sits under `Name`,
// `id`, or an empty-string key.
export function paletteName(entry) {
  if (typeof entry === 'string') return entry;
  if (!entry || typeof entry !== 'object') return null;
  return entry.Name ?? entry.id ?? entry[''] ?? null;
}
