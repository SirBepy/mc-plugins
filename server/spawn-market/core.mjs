// Shared state threaded through every phase of the spawn market generator: the block model,
// phase buckets, palette, Y/terrain constants, the seeded RNG and a couple of cross-phase
// helpers (ground lookup, side geometry, sign NBT). Each phase module in this directory takes
// the exported `model` object as its first argument and mutates `model.blocks`/`model.phases`/
// `model.surfaceY` in place; nothing here depends on any phase module. Phases must still run in
// generate.mjs's original order: `rand()`/`pick()` are only called from the terrain phase and the
// corner-gardens phase, and the mulberry32 seed makes their call ORDER part of the output.
// street.mjs's own cosmetic paving/dirt-patch rolls draw from a SEPARATE `streetRand`/`streetPick`
// stream instead (below): it has to run before buildStalls, ahead of the gardens phase, and
// sharing the main stream would shift every garden roll relative to HEAD's pre-street build.
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

export const here = dirname(fileURLToPath(import.meta.url));

// The live ground heightmap around spawn, taken 2026-10-05 from the website map's region cache,
// so the slopes blend into the real hillside.
const snap = JSON.parse(readFileSync(join(here, 'terrain-snapshot.json'), 'utf8'));
const [CX, CZ] = snap.center;
const Y = 114; // plaza surface: median live ground in the footprint, so cuts and fills stay <= 4
// The shop street (Joe's pick, 2026-10-09, over the corner-stall and widened-plaza mock-ups he
// was shown): the corner-stall mock-up put every one of the 4 new stalls (Heads/Radars/Homes/
// Cosmetics) facing a neighbour's side wall across a 2-block alley, so they go on a separate
// paved street south of the plaza instead (street.mjs/stalls.mjs) - that also keeps selling
// (plaza) and buying (street) apart, and leaves room for more shops later.
const CORE = 18; // flat square half-width
const PAVED = 11; // paved square half-width; stall fronts start one block past it
const BLEND = 6; // ring past CORE that slopes back to the natural ground
const OUTER = CORE + BLEND;
const CLEAR_TOP = Y + 30; // tallest spruce near spawn tops out ~y140

// The street leaving the plaza on the south axis, starting right where the south path's own
// blend ring already reaches natural ground (v = OUTER, 24). 2 stalls per side (stalls.mjs),
// a0 = 0 and 10; length and width checked 2026-10-09 against the live region cache
// (terrain-snapshot.json, radius 60): ground stays within a block or two of Y=114 out to v=44,
// then drops fast, so the street (ending at STREET_V0 + 10 + 5 + STREET_BLEND = 24+15+4 = 43)
// stays inside the gentle part.
// STREET_V0 = OUTER (24) + a 2-block gap, so the entrance arch (street.mjs, at STREET_V0 - 2 =
// 24) has room before the first stall's own eave column (a0 + a=-1 = STREET_V0 - 1); at
// STREET_V0 = OUTER exactly the two collided (checked by rendering 2026-10-09 - the arch sat
// inside the first stall's wall).
export const STREET_V0 = OUTER + 2;
export const STREET_HALF = 2; // paved width 5 (3-5 wide, Joe's spec)
export const STREET_LEN = 15; // last stall's far column (a0 10 + a up to 5)
export const STREET_BLEND = 4; // taper back to natural ground past the last stall and past the paved width

// mulberry32: deterministic, so regenerating gives byte-identical functions. Two independent
// streams off this one factory: `rand`/`pick` (terrain + corner-gardens, the pair the header
// comment's call-order invariant covers) and `streetRand`/`streetPick` (street.mjs only).
function makeRng(seed) {
  let s = seed;
  const rand = () => {
    s |= 0; s = (s + 0x6d2b79f5) | 0;
    let t = Math.imul(s ^ (s >>> 15), 1 | s);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
  const pick = (weighted) => {
    const total = weighted.reduce((sum, [, w]) => sum + w, 0);
    let r = rand() * total;
    for (const [v, w] of weighted) if ((r -= w) < 0) return v;
    return weighted[0][0];
  };
  return { rand, pick };
}
const { rand, pick } = makeRng(0x5eed);
const { rand: streetRand, pick: streetPick } = makeRng(0x57eee7);

function groundAt(u, v) {
  const R = snap.radius;
  const i = (v + R) * (2 * R + 1) + (u + R);
  return { y: snap.ground[i], top: snap.top[i] };
}

// ---- block model -------------------------------------------------------------------------
// Keyed by plaza-relative u (x), y (absolute), v (z). Later writes win, so terrain goes first.
const blocks = new Map();
// Each block lives in exactly one phase: a later write moves it, so phase order can't resurrect
// an overwritten block (a garden fern placed in 'attach' would otherwise replace a tree trunk).
const phases = { terrain: new Map(), build: new Map(), attach: new Map() };

// Sub-division of the 'build' phase only, one bucket per stall/station/shared-structure module
// (emit.mjs writes one .mcfunction per bucket instead of one flat structure.mcfunction). Same
// "a block lives in exactly one place" rule as `phases` above: a later write in a different group
// deletes the key from its old group's bucket, so regrouping can never resurrect a stale block.
let currentGroup = 'shared';
const groupOrder = [];
const groupMaps = new Map();
const groupFor = (name) => {
  if (!groupMaps.has(name)) { groupMaps.set(name, new Map()); groupOrder.push(name); }
  return groupMaps.get(name);
};
const withGroup = (name, fn) => {
  const prev = currentGroup;
  currentGroup = name;
  try { return fn(); } finally { currentGroup = prev; }
};
const structureGroups = () => groupOrder.map((name) => [name, groupMaps.get(name)]);

const set = (u, y, v, state, phase = 'build') => {
  const key = `${u},${y},${v}`;
  blocks.set(key, state);
  for (const p of Object.values(phases)) p.delete(key);
  phases[phase].set(key, state);
  if (phase === 'build') {
    for (const g of groupMaps.values()) g.delete(key);
    groupFor(currentGroup).set(key, state);
  }
};
const fill = (u0, y0, v0, u1, y1, v1, state, phase = 'build') => {
  for (let u = Math.min(u0, u1); u <= Math.max(u0, u1); u++)
    for (let y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
      for (let v = Math.min(v0, v1); v <= Math.max(v0, v1); v++) set(u, y, v, state, phase);
};

const OPPOSITE = { north: 'south', south: 'north', east: 'west', west: 'east' };
const ROTATION = { south: 0, west: 4, north: 8, east: 12 }; // standing/hanging sign rotation per facing

// A side of the square, as a frame: `a` runs along the side, `d` is distance from the plaza edge
// (0 = stall front). `inward` is the direction facing the plaza centre.
// streetWest/streetEast are the same shape, but framed on the street's own centreline (u = 0,
// v = STREET_V0 + a) instead of the plaza's: stall() never knows the difference, so every
// exported coordinate still comes out of the same a0/d math.
function side(name) {
  const F = PAVED + 1;
  const SF = STREET_HALF + 1;
  return {
    north: { at: (a, d) => [a, -(F + d)], inward: 'south', axis: 'x', plusA: 'east' },
    south: { at: (a, d) => [-a, F + d], inward: 'north', axis: 'x', plusA: 'west' },
    west: { at: (a, d) => [-(F + d), -a], inward: 'east', axis: 'z', plusA: 'north' },
    east: { at: (a, d) => [F + d, a], inward: 'west', axis: 'z', plusA: 'south' },
    streetWest: { at: (a, d) => [-(SF + d), STREET_V0 + a], inward: 'east', axis: 'z', plusA: 'south' },
    streetEast: { at: (a, d) => [SF + d, STREET_V0 + a], inward: 'west', axis: 'z', plusA: 'south' },
  }[name];
}

// Block palette: spruce/dark oak timber stalls on trial-chamber tuff (Joe's 2026-10-05 pick from
// four rendered variants: plain timber, tuff, tuff with copper roofs, deepslate bricks).
const P = {
  roof: 'dark_oak', post: 'spruce_log', beam: 'stripped_spruce_log', wall: 'spruce_planks', base: 'tuff_bricks',
  footing: 'polished_tuff', floor: 'spruce_planks', rail: 'spruce_fence', postBase: 'tuff_brick_wall', light: 'lantern',
  mosaic: { center: 'chiseled_tuff_bricks', cross: 'tuff_bricks', corner: 'chiseled_tuff', ring: 'polished_tuff' },
  paving: [['tuff_bricks', 16], ['polished_tuff', 10], ['tuff', 10], ['cobblestone', 14], ['mossy_cobblestone', 8],
    ['coarse_dirt', 6], ['gravel', 4], ['chiseled_tuff', 2]],
};

// Lines are plain strings or { text, color, bold } components. A dye colour on the sign itself
// also makes all text glow; a per-line color/bold overrides just that line (e.g. one bold line in
// an otherwise plain sign).
function sign(block, lines, { color, back = false } = {}) {
  const componentOf = (l) => {
    if (typeof l === 'string') return JSON.stringify(l);
    const parts = [`text:${JSON.stringify(l.text)}`];
    if (l.color) parts.push(`color:"${l.color}"`);
    if (l.bold) parts.push('bold:true');
    return `{${parts.join(',')}}`;
  };
  const text = `{${color ? `color:"${color}",has_glowing_text:1b,` : ''}messages:[${lines.map(componentOf).join(',')}]}`;
  return `${block}{front_text:${text}${back ? `,back_text:${text}` : ''}}`;
}

// Filled in by the terrain phase, read by the lantern-posts/paths/gardens phase and by emit's
// clear/cut pass.
const surfaceY = new Map();

export const model = {
  here, snap, CX, CZ, Y, CORE, PAVED, BLEND, OUTER, CLEAR_TOP,
  STREET_V0, STREET_HALF, STREET_LEN, STREET_BLEND,
  rand, pick, streetRand, streetPick, groundAt,
  blocks, phases, set, fill,
  withGroup, structureGroups,
  OPPOSITE, ROTATION, side,
  P, sign,
  surfaceY,
};
