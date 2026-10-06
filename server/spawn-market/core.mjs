// Shared state threaded through every phase of the spawn market generator: the block model,
// phase buckets, palette, Y/terrain constants, the seeded RNG and a couple of cross-phase
// helpers (ground lookup, side geometry, sign NBT). Each phase module in this directory takes
// the exported `model` object as its first argument and mutates `model.blocks`/`model.phases`/
// `model.surfaceY` in place; nothing here depends on any phase module. Phases must still run in
// generate.mjs's original order: `rand()`/`pick()` are only called from the terrain phase and the
// corner-gardens phase, and the mulberry32 seed makes their call ORDER part of the output.
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

export const here = dirname(fileURLToPath(import.meta.url));

// The live ground heightmap around spawn, taken 2026-10-05 from the website map's region cache,
// so the slopes blend into the real hillside.
const snap = JSON.parse(readFileSync(join(here, 'terrain-snapshot.json'), 'utf8'));
const [CX, CZ] = snap.center;
const Y = 114; // plaza surface: median live ground in the footprint, so cuts and fills stay <= 4
const CORE = 18; // flat square half-width
const PAVED = 11; // paved square half-width; stall fronts start one block past it
const BLEND = 6; // ring past CORE that slopes back to the natural ground
const OUTER = CORE + BLEND;
const CLEAR_TOP = Y + 30; // tallest spruce near spawn tops out ~y140

// mulberry32: deterministic, so regenerating gives byte-identical functions.
let seed = 0x5eed;
function rand() {
  seed |= 0; seed = (seed + 0x6d2b79f5) | 0;
  let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
}
const pick = (weighted) => {
  const total = weighted.reduce((s, [, w]) => s + w, 0);
  let r = rand() * total;
  for (const [v, w] of weighted) if ((r -= w) < 0) return v;
  return weighted[0][0];
};

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
const set = (u, y, v, state, phase = 'build') => {
  const key = `${u},${y},${v}`;
  blocks.set(key, state);
  for (const p of Object.values(phases)) p.delete(key);
  phases[phase].set(key, state);
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
function side(name) {
  const F = PAVED + 1;
  return {
    north: { at: (a, d) => [a, -(F + d)], inward: 'south', axis: 'x', plusA: 'east' },
    south: { at: (a, d) => [-a, F + d], inward: 'north', axis: 'x', plusA: 'west' },
    west: { at: (a, d) => [-(F + d), -a], inward: 'east', axis: 'z', plusA: 'north' },
    east: { at: (a, d) => [F + d, a], inward: 'west', axis: 'z', plusA: 'south' },
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
  rand, pick, groundAt,
  blocks, phases, set, fill,
  OPPOSITE, ROTATION, side,
  P, sign,
  surfaceY,
};
