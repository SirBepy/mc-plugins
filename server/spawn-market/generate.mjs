#!/usr/bin/env node
// Generates the spawn market build: a taiga-village square at world spawn with six themed
// [Sell] stalls, a crafting corner, a reserved stall and a rules board. Writes the
// spawn-market datapack's functions plus model.json (every block it sets, for offline renders).
//
// Original design. Layout ideas (stalls ringing a square, themed props per trade) were taken as
// inspiration only from "Market Place / Marktplatz" and "10 Market Stall Pack" (Planet Minecraft)
// and "Market Square" by wonderpatch (minecraft-schematics.com #1401); none of their blocks or
// files are reused, since none carry a reuse licence.
//
// Usage: node server/spawn-market/generate.mjs
// Terrain comes from terrain-snapshot.json, the live ground heightmap around spawn taken
// 2026-10-05 from the website map's region cache, so the slopes blend into the real hillside.
import { mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const PACK = join(here, '..', 'datapacks', 'spawn-market');
const FN = join(PACK, 'data', 'spawn_market', 'function');

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
const model = new Map();
// Each block lives in exactly one phase: a later write moves it, so phase order can't resurrect
// an overwritten block (a garden fern placed in 'attach' would otherwise replace a tree trunk).
const phases = { terrain: new Map(), build: new Map(), attach: new Map() };
const set = (u, y, v, state, phase = 'build') => {
  const key = `${u},${y},${v}`;
  model.set(key, state);
  for (const p of Object.values(phases)) p.delete(key);
  phases[phase].set(key, state);
};
const fill = (u0, y0, v0, u1, y1, v1, state, phase = 'build') => {
  for (let u = Math.min(u0, u1); u <= Math.max(u0, u1); u++)
    for (let y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
      for (let v = Math.min(v0, v1); v <= Math.max(v0, v1); v++) set(u, y, v, state, phase);
};

const OPPOSITE = { north: 'south', south: 'north', east: 'west', west: 'east' };

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

// ---- terrain ------------------------------------------------------------------------------
const SOIL = new Set(['grass_block', 'coarse_dirt', 'podzol', 'dirt', 'rooted_dirt', 'moss_block']);
// Block palette: spruce/dark oak timber stalls on trial-chamber tuff (Joe's 2026-10-05 pick from
// four rendered variants: plain timber, tuff, tuff with copper roofs, deepslate bricks).
const P = {
  roof: 'dark_oak', post: 'spruce_log', beam: 'stripped_spruce_log', wall: 'spruce_planks', base: 'tuff_bricks',
  footing: 'polished_tuff', floor: 'spruce_planks', rail: 'spruce_fence', postBase: 'tuff_brick_wall', light: 'lantern',
  mosaic: { center: 'chiseled_tuff_bricks', cross: 'tuff_bricks', corner: 'chiseled_tuff', ring: 'polished_tuff' },
  paving: [['tuff_bricks', 16], ['polished_tuff', 10], ['tuff', 10], ['cobblestone', 14], ['mossy_cobblestone', 8],
    ['coarse_dirt', 6], ['gravel', 4], ['chiseled_tuff', 2]],
};

const surfaceY = new Map();
for (let v = -OUTER; v <= OUTER; v++) {
  for (let u = -OUTER; u <= OUTER; u++) {
    const g = groundAt(u, v);
    const r = Math.max(Math.abs(u), Math.abs(v));
    let target = Y;
    if (r > CORE) target = Math.round(Y + ((g.y - Y) * (r - CORE)) / BLEND);
    surfaceY.set(`${u},${v}`, target);
    if (r > CORE && target === g.y) continue; // ring column already at its natural height
    for (let y = g.y + 1; y < target; y++) set(u, y, v, 'dirt', 'terrain');
    let top = SOIL.has(g.top) ? g.top : 'grass_block';
    if (r <= PAVED) top = pick(P.paving);
    else if (r <= CORE && rand() < 0.18) top = pick([['coarse_dirt', 2], ['podzol', 2], ['moss_block', 1]]);
    set(u, target, v, top, 'terrain');
  }
}

// ---- centre: compass mosaic, bell frame -----------------------------------------------------
for (let u = -2; u <= 2; u++) for (let v = -2; v <= 2; v++) {
  const ring = Math.max(Math.abs(u), Math.abs(v));
  const cross = u === 0 || v === 0;
  const m = P.mosaic;
  set(u, Y, v, ring === 0 ? m.center : ring === 2 ? m.ring : cross ? m.cross : m.corner, 'terrain');
}
for (const a of [-3, 3]) {
  set(a, Y, 0, 'stone_bricks', 'terrain');
  set(0, Y, a, 'stone_bricks', 'terrain');
}
// Bell on a little spruce gallows north of the spawn tile (taiga meeting-point style).
fill(-1, Y + 1, -5, -1, Y + 3, -5, 'spruce_log');
fill(1, Y + 1, -5, 1, Y + 3, -5, 'spruce_log');
fill(-1, Y + 4, -5, 1, Y + 4, -5, 'stripped_spruce_log[axis=x]');
set(-2, Y + 4, -5, 'spruce_stairs[facing=east,half=top]');
set(2, Y + 4, -5, 'spruce_stairs[facing=west,half=top]');
fill(-1, Y + 5, -5, 1, Y + 5, -5, 'spruce_slab[type=bottom]');
set(0, Y + 3, -5, 'bell[attachment=ceiling,facing=north]', 'attach');
set(-1, Y, -5, 'cobblestone', 'terrain');
set(0, Y, -5, 'cobblestone', 'terrain');
set(1, Y, -5, 'cobblestone', 'terrain');

// Small hand-built spruce for the corner gardens (saplings would grow unpredictably).
function miniSpruce(pu, pv) {
  fill(pu, Y + 1, pv, pu, Y + 5, pv, 'spruce_log');
  const layers = [[Y + 2, 2], [Y + 3, 1], [Y + 4, 1.5], [Y + 5, 1], [Y + 6, 0]];
  for (const [y, r] of layers) for (let i = -2; i <= 2; i++) for (let j = -2; j <= 2; j++) {
    if ((i || j) || y === Y + 6) {
      const inside = r === 1.5 ? Math.max(Math.abs(i), Math.abs(j)) <= 1 : Math.abs(i) + Math.abs(j) <= r;
      if (inside) set(pu + i, y, pv + j, 'spruce_leaves[persistent=true]');
    }
  }
}

// ---- rules board, facing the spawn tile from the south ---------------------------------------
fill(-2, Y + 1, 5, -2, Y + 3, 5, 'spruce_log');
fill(2, Y + 1, 5, 2, Y + 3, 5, 'spruce_log');
fill(-1, Y + 1, 5, 1, Y + 2, 5, 'spruce_planks');
set(-1, Y + 3, 5, 'spruce_trapdoor[facing=north,half=top,open=false]');
set(0, Y + 3, 5, 'spruce_trapdoor[facing=north,half=top,open=false]');
set(1, Y + 3, 5, 'spruce_trapdoor[facing=north,half=top,open=false]');
for (let u = -3; u <= 3; u++) {
  set(u, Y + 4, 5, `${P.roof}_slab[type=bottom]`);
  set(u, Y + 4, 4, `${P.roof}_stairs[facing=south,half=bottom]`);
  set(u, Y + 4, 6, `${P.roof}_stairs[facing=north,half=bottom]`);
}
// Rules text is a placeholder: Joe edits these signs in-game.
const RULES = [
  // The board faces north, so +u reads on the viewer's left.
  [1, Y + 2, ['', 'Server rules', '(edit me)', '']],
  [0, Y + 2, ['Welcome to', 'Los Pollos', 'MineHermanos', '']],
  [-1, Y + 2, ['', 'Commands', '(edit me)', '']],
  [0, Y + 1, ['Sell items at', 'the stalls', 'around the', 'square']],
];
for (const [u, y, lines] of RULES) set(u, y, 4, sign('spruce_wall_sign[facing=north]', lines), 'attach');
set(-3, Y + 1, 4, `${P.light}[hanging=false]`, 'attach');
set(3, Y + 1, 4, `${P.light}[hanging=false]`, 'attach');

// Lines are plain strings or { text, color } components.
function sign(block, lines) {
  const component = (l) => (typeof l === 'string' ? JSON.stringify(l) : `{text:${JSON.stringify(l.text)},color:"${l.color}"}`);
  return `${block}{front_text:{messages:[${lines.map(component).join(',')}]}}`;
}

// ---- stalls ---------------------------------------------------------------------------------
// [Sell] lines follow Essentials' sell sign: [Sell] / amount / item / price for that amount.
// Prices from Joe's 2026-10-05 table; amount 64 except stack-of-16 and rare items.
// Line 1 must match Essentials' signFormatSuccess (<dark_blue>[Sell], from its messages.properties):
// a player-written sign gets recoloured on placement, a command-placed one has to arrive that way.
const sell = (amount, item, each) => [{ text: '[Sell]', color: 'dark_blue' }, String(amount), item, `$${+(amount * each).toFixed(2)}`];
// Props: [state, a, d, dy]. The 5x3 stalls have no spare floor, so props stand outside the side
// walls under the eaves (a = -1 or 5).
const STALLS = [
  {
    side: 'north', a0: -8, name: 'Farmer', wool: 'yellow',
    signs: [sell(64, 'wheat', 0.1), sell(64, 'carrot', 0.1), sell(64, 'potato', 0.1), sell(64, 'beetroot', 0.1),
      sell(64, 'pumpkin', 0.5), sell(64, 'sugar_cane', 0.05), sell(64, 'cactus', 0.05), sell(64, 'bamboo', 0.05),
      sell(64, 'melon_slice', 0.05)],
    props: [['hay_block[axis=y]', -1, 1], ['hay_block[axis=AXIS]', -1, 2], ['composter', 5, 1], ['pumpkin', 5, 2],
      ['carved_pumpkin[facing=INWARD]', 5, 2, 1]],
  },
  {
    side: 'north', a0: 4, name: 'Lumberjack', wool: 'brown',
    signs: ['oak', 'spruce', 'birch', 'jungle', 'acacia', 'dark_oak', 'mangrove', 'cherry', 'pale_oak']
      .map((w) => sell(64, `${w}_log`, 0.5)),
    props: [['oak_log[axis=AXIS]', -1, 1], ['birch_log[axis=AXIS]', -1, 2], ['spruce_log[axis=AXIS]', -1, 1, 1],
      ['stripped_oak_log[axis=y]', 5, 1], ['dark_oak_log[axis=AXIS]', 5, 2]],
  },
  {
    side: 'east', a0: -8, name: 'Fishmonger', wool: 'light_blue',
    signs: [sell(64, 'cod', 0.75), sell(64, 'salmon', 0.75), sell(16, 'pufferfish', 2), sell(16, 'tropical_fish', 2),
      sell(64, 'ink_sac', 1)],
    props: [['barrel[facing=up]', -1, 1], ['barrel[facing=up]', -1, 2], ['water_cauldron[level=3]', 5, 1],
      ['barrel[facing=up]', 5, 2], ['dried_kelp_block', -1, 1, 1]],
  },
  {
    side: 'east', a0: 4, name: 'Smith', wool: 'gray',
    signs: [sell(64, 'iron_ingot', 1), sell(64, 'gold_ingot', 2)],
    props: [['blast_furnace[facing=INWARD]', -1, 1], ['smoker[facing=INWARD]', -1, 2], ['anvil[facing=AXISFACE]', 5, 1],
      ['lantern[hanging=false]', -1, 1, 1]],
  },
  {
    side: 'south', a0: -8, name: 'Mob drops', wool: 'green',
    signs: [sell(64, 'rotten_flesh', 0.25), sell(64, 'bone', 0.25), sell(64, 'string', 0.25), sell(64, 'feather', 0.25),
      sell(64, 'leather', 1), sell(64, 'rabbit_hide', 1), sell(64, 'gunpowder', 1.5), sell(64, 'spider_eye', 1.5),
      sell(64, 'slime_ball', 1.5)],
    props: [['bone_block[axis=y]', -1, 1], ['bone_block[axis=AXIS]', -1, 2], ['skeleton_skull[rotation=0]', -1, 1, 1],
      ['barrel[facing=up]', 5, 1]],
  },
  {
    side: 'south', a0: 4, name: 'Rare goods', wool: 'purple',
    signs: [sell(16, 'ender_pearl', 8), sell(16, 'blaze_rod', 10), sell(16, 'phantom_membrane', 5), sell(16, 'ghast_tear', 25)],
    props: [['crying_obsidian', -1, 1], ['amethyst_block', -1, 2], ['purple_candle[candles=3,lit=true]', -1, 1, 1],
      ['obsidian', 5, 1], ['end_rod[facing=up]', 5, 1, 1]],
  },
  {
    side: 'west', a0: 4, name: 'Crafting', wool: 'orange',
    // Inside against the back wall, plus the rest under the eaves.
    stations: [['crafting_table', 1], ['smithing_table', 2], ['stonecutter[facing=INWARD]', 3]],
    props: [['anvil[facing=AXISFACE]', -1, 1], ['grindstone[face=floor,facing=INWARD]', -1, 2],
      ['cartography_table', 5, 1], ['loom[facing=INWARD]', 5, 2]],
  },
  {
    side: 'west', a0: -8, name: 'Reserved', wool: 'white',
    props: [['barrel[facing=up]', -1, 1], ['barrel[facing=up]', 5, 1]],
  },
];

// A 5-wide, 3-deep open-front stall: a (0..4) along the side, d (0..3) away from the plaza,
// back wall at d = 3 carrying a 3x3 sign wall on its inner face.
function stall({ side: sideName, a0, name, wool, signs = [], props, stations = [] }) {
  const S = side(sideName);
  const at = (a, d, y, state, phase) => { const [u, v] = S.at(a0 + a, d); set(u, y, v, state, phase); };
  const inward = S.inward;
  const along = S.axis; // log axis running along the stall
  const across = along === 'x' ? 'z' : 'x';
  const resolve = (s) => s.replace('INWARD', inward).replace('AXISFACE', along === 'x' ? 'east' : 'south')
    .replace('AXIS', across);
  for (let a = -1; a <= 5; a++) for (let d = 0; d <= 3; d++) {
    const under = a < 0 || a > 4; // eaves strip either side
    at(a, d, Y, d === 3 ? P.footing : under ? 'coarse_dirt' : P.floor, 'terrain');
  }
  for (const a of [0, 4]) for (const d of [0, 3]) for (let y = Y + 1; y <= Y + 4; y++) at(a, d, y, P.post);
  for (let a = 1; a <= 3; a++) {
    at(a, 3, Y + 1, P.base);
    for (let y = Y + 2; y <= Y + 4; y++) at(a, 3, y, P.wall);
    at(a, 0, Y + 4, `${P.beam}[axis=${along}]`);
  }
  for (const a of [0, 4]) for (const d of [1, 2]) {
    at(a, d, Y + 1, P.wall);
    at(a, d, Y + 2, P.rail);
  }
  // Steep gable roof, ridge running from the plaza to the back, one block of overhang all round.
  const minusA = OPPOSITE[S.plusA];
  for (let d = -1; d <= 4; d++) for (let a = -1; a <= 5; a++) {
    if (a === 2) { at(a, d, Y + 7, `${P.roof}_slab[type=bottom]`); continue; }
    at(a, d, Y + 5 + Math.min(a, 4 - a), `${P.roof}_stairs[facing=${a < 2 ? S.plusA : minusA},half=bottom]`);
  }
  // Gable infill: stall colour facing the plaza so stalls read apart from across the square.
  for (const [d, state] of [[0, `${wool}_wool`], [3, P.wall]]) {
    for (let a = 1; a <= 3; a++) for (let y = Y + 5; y < Y + 5 + Math.min(a, 4 - a); y++) at(a, d, y, state);
  }
  // Name sign on the gable, above the opening, so it never hides the top row of [Sell] signs.
  at(2, -1, Y + 5, sign(`spruce_wall_sign[facing=${inward}]`, ['', name, '', '']), 'attach');
  at(-1, -1, Y + 3, `${P.light}[hanging=true]`, 'attach');
  at(5, -1, Y + 3, `${P.light}[hanging=true]`, 'attach');
  for (const [state, a] of stations) at(a, 2, Y + 1, resolve(state));
  signs.forEach((lines, i) => {
    at(1 + (i % 3), 2, Y + 3 - Math.floor(i / 3), sign(`spruce_wall_sign[facing=${inward}]`, lines), 'attach');
  });
  for (const [state, a, d, dy = 0] of props) at(a, d, Y + 1 + dy, resolve(state), dy ? 'attach' : 'build');
}
STALLS.forEach(stall);

// ---- lantern posts, paths, corner gardens -----------------------------------------------------
const post = (u, v) => {
  set(u, Y, v, 'cobblestone', 'terrain');
  set(u, Y + 1, v, P.postBase);
  set(u, Y + 2, v, 'spruce_fence');
  set(u, Y + 3, v, `${P.light}[hanging=false]`, 'attach');
};
for (const [u, v] of [[-10, -10], [10, -10], [-10, 10], [10, 10]]) post(u, v);
for (const s of ['north', 'south', 'east', 'west']) for (const a of [-2, 2]) post(...side(s).at(a, -1));

// Paths out of the square on all four axes, following the blended ground.
for (const s of ['north', 'south', 'east', 'west']) {
  for (let d = 0; d <= OUTER - PAVED - 1; d++) for (let a = -1; a <= 1; a++) {
    const [u, v] = side(s).at(a, d);
    const y = surfaceY.get(`${u},${v}`);
    set(u, y, v, pick([['dirt_path', 6], ['gravel', 2], ['coarse_dirt', 1], ['cobblestone', 1]]), 'terrain');
  }
}

// Corner gardens between the stalls: berry bushes, ferns, a bench, and a well in the north-east.
for (const [su, sv] of [[1, 1], [-1, 1], [-1, -1]]) {
  for (let i = 13; i <= 17; i++) for (let j = 13; j <= 17; j++) {
    const u = su * i, v = sv * j;
    set(u, Y, v, 'podzol', 'terrain');
    const r = rand();
    if (r < 0.18) set(u, Y + 1, v, 'sweet_berry_bush[age=3]', 'attach');
    else if (r < 0.4) set(u, Y + 1, v, 'fern', 'attach');
    else if (r < 0.52) { set(u, Y + 1, v, 'spruce_leaves[persistent=true]'); }
  }
  set(su * 12, Y + 1, sv * 15, `spruce_stairs[facing=${su > 0 ? 'east' : 'west'},half=bottom]`);
  miniSpruce(su * 15, sv * 15);
}
// Well (north-east corner): cobble ring, water, spruce roof.
{
  const u0 = 13, v0 = -17;
  for (let i = 0; i <= 4; i++) for (let j = 0; j <= 4; j++) {
    const edge = i === 0 || j === 0 || i === 4 || j === 4;
    set(u0 + i, Y, v0 + j, 'cobblestone', 'terrain');
    if (edge) set(u0 + i, Y + 1, v0 + j, i % 4 === 0 && j % 4 === 0 ? 'mossy_cobblestone' : 'cobblestone_wall');
  }
  fill(u0 + 1, Y - 3, v0 + 1, u0 + 3, Y - 3, v0 + 3, 'cobblestone', 'terrain');
  fill(u0 + 1, Y - 2, v0 + 1, u0 + 3, Y, v0 + 3, 'water', 'terrain');
  for (const [i, j] of [[0, 0], [4, 0], [0, 4], [4, 4]]) {
    set(u0 + i, Y + 1, v0 + j, 'mossy_cobblestone');
    fill(u0 + i, Y + 2, v0 + j, u0 + i, Y + 3, v0 + j, 'spruce_fence');
  }
  for (let i = -1; i <= 5; i++) {
    for (let j = -1; j <= 5; j++) {
      const ring = Math.min(i + 1, j + 1, 5 - i, 5 - j);
      if (ring === 0) set(u0 + i, Y + 4, v0 + j, `spruce_slab[type=bottom]`);
      else if (ring === 1) set(u0 + i, Y + 4, v0 + j, 'spruce_planks');
      else set(u0 + i, Y + 5, v0 + j, 'spruce_slab[type=bottom]');
    }
  }
  set(u0 + 2, Y + 4, v0 + 2, `${P.light}[hanging=true]`, 'attach');
}

// ---- emit -----------------------------------------------------------------------------------
const abs = (u, y, v, state) => `${CX + u} ${y} ${CZ + v} ${state}`;
const MAX_FILL = 32768;

function boxFills(x0, y0, z0, x1, y1, z1, state) {
  const out = [];
  const layer = (x1 - x0 + 1) * (z1 - z0 + 1);
  const step = Math.max(1, Math.floor(MAX_FILL / layer));
  for (let y = y0; y <= y1; y += step) {
    out.push(`fill ${x0} ${y} ${z0} ${x1} ${Math.min(y1, y + step - 1)} ${z1} ${state}`);
  }
  return out;
}

// Collapses runs of identical blocks along x into fills; everything else is a setblock.
function emit(last) {
  const byRow = new Map();
  for (const [key, state] of last) {
    const [u, y, v] = key.split(',').map(Number);
    const row = `${y},${v}`;
    if (!byRow.has(row)) byRow.set(row, []);
    byRow.get(row).push([u, state]);
  }
  const lines = [];
  for (const [row, cells] of [...byRow].sort()) {
    const [y, v] = row.split(',').map(Number);
    cells.sort((a, b) => a[0] - b[0]);
    for (let i = 0; i < cells.length;) {
      let j = i;
      while (j + 1 < cells.length && cells[j + 1][0] === cells[j][0] + 1 && cells[j + 1][1] === cells[i][1] && !cells[i][1].includes('{')) j++;
      const [u, state] = cells[i];
      lines.push(j > i ? `fill ${CX + u} ${y} ${CZ + v} ${CX + cells[j][0]} ${y} ${CZ + v} ${state}`
        : `setblock ${abs(u, y, v, state)}`);
      i = j + 1;
    }
  }
  return lines;
}

const x0 = CX - OUTER, x1 = CX + OUTER, z0 = CZ - OUTER, z1 = CZ + OUTER;
const header = (what) => [`# Generated by server/spawn-market/generate.mjs - do not edit by hand. ${what}`];

// Everything above the new ground goes: trees, grass, the old torches. Per column, so the
// blend ring keeps whatever natural ground already sits at its target height.
const clear = [...boxFills(CX - CORE, Y + 1, CZ - CORE, CX + CORE, CLEAR_TOP, CZ + CORE, 'air')];
for (let v = -OUTER; v <= OUTER; v++) for (let u = -OUTER; u <= OUTER; u++) {
  if (Math.max(Math.abs(u), Math.abs(v)) <= CORE) continue;
  clear.push(`fill ${CX + u} ${surfaceY.get(`${u},${v}`) + 1} ${CZ + v} ${CX + u} ${CLEAR_TOP} ${CZ + v} air`);
}
// Cutting ground down to the plaza: everything between the new surface and the old one.
for (let v = -OUTER; v <= OUTER; v++) for (let u = -OUTER; u <= OUTER; u++) {
  const g = groundAt(u, v).y, t = surfaceY.get(`${u},${v}`);
  if (g > t) clear.push(`fill ${CX + u} ${t + 1} ${CZ + v} ${CX + u} ${g} ${CZ + v} air`);
}

rmSync(FN, { recursive: true, force: true });
mkdirSync(FN, { recursive: true });
const write = (name, lines) => writeFileSync(join(FN, `${name}.mcfunction`), lines.join('\n') + '\n');

write('build', [
  ...header('Entry point.'),
  '# Loads the chunks first: fill/setblock fail on unloaded chunks, and forceload only takes',
  '# effect on a later tick, hence the schedule.',
  `forceload add ${x0} ${z0} ${x1} ${z1}`,
  'tellraw @a {"text":"[spawn-market] building in 2s...","color":"gray"}',
  'schedule function spawn_market:build_run 40t',
]);
write('build_run', [
  ...header('Runs the phases in order.'),
  'function spawn_market:clear',
  'function spawn_market:terrain',
  'function spawn_market:structure',
  'function spawn_market:attach',
  'kill @e[type=item,x=' + x0 + ',y=' + (Y - 10) + ',z=' + z0 + ',dx=' + (2 * OUTER) + ',dy=60,dz=' + (2 * OUTER) + ']',
  `forceload remove ${x0} ${z0} ${x1} ${z1}`,
  'tellraw @a {"text":"[spawn-market] done","color":"gray"}',
]);
write('clear', [...header('Clears trees and cuts the hill.'), ...clear]);
write('terrain', [...header('Fills, paving, paths.'), ...emit(phases.terrain)]);
write('structure', [...header('Stalls, well, bell frame, posts.'), ...emit(phases.build)]);
write('attach', [...header('Blocks that need support: signs, lanterns, bell, plants.'), ...emit(phases.attach)]);

const modelOut = { origin: [CX, CZ], y: Y, outer: OUTER, clearTop: CLEAR_TOP, surface: Object.fromEntries(surfaceY), blocks: {} };
for (const [k, s] of model) {
  const [u, y, v] = k.split(',').map(Number);
  modelOut.blocks[`${CX + u},${y},${CZ + v}`] = s;
}
writeFileSync(join(here, 'model.json'), JSON.stringify(modelOut));
const count = (n) => readFileSync(join(FN, `${n}.mcfunction`), 'utf8').split('\n').length - 2;
console.log(`clear ${count('clear')}, terrain ${count('terrain')}, structure ${count('structure')}, attach ${count('attach')} commands; ${model.size} blocks`);
