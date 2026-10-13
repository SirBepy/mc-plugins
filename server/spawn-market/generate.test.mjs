// node --test server/spawn-market/generate.test.mjs
// Runs the generator and checks the emitted functions against the limits the build hits in-game.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const FN = join(here, '..', 'datapacks', 'spawn-market', 'data', 'spawn_market', 'function');
execFileSync(process.execPath, [join(here, 'generate.mjs')]);
const lines = (name) => readFileSync(join(FN, `${name}.mcfunction`), 'utf8').split('\n').filter((l) => l && !l.startsWith('#'));
const all = ['clear', 'terrain', 'structure', 'attach'].flatMap(lines);

test('every fill stays under the 32768-block command limit', () => {
  for (const l of all.filter((l) => l.startsWith('fill '))) {
    const [x0, y0, z0, x1, y1, z1] = l.split(' ').slice(1, 7).map(Number);
    const volume = (Math.abs(x1 - x0) + 1) * (Math.abs(y1 - y0) + 1) * (Math.abs(z1 - z0) + 1);
    assert.ok(volume <= 32768, `${volume} blocks: ${l}`);
  }
});

// The Market plugin owns the [Sell] signs; the datapack only leaves their spots free.
// Scoped to the `stalls:` section only (readStallsYml() below reads the whole file): head-stall's
// own `  slots:` line (2-space indent, bare key, exactly the split boundary pattern) would
// otherwise be mistaken for a stall id.
const readStallsYml = () => readFileSync(join(here, '..', '..', 'market', 'src', 'main', 'resources', 'stalls.yml'), 'utf8');
const stallsSection = readStallsYml().slice(readStallsYml().indexOf('\nstalls:\n'));
const stalls = stallsSection.split(/\n(?=  [a-z_]+:\n)/).slice(1).map((block) => ({
  id: block.match(/^ {2}([a-z_]+):/)[1],
  slots: [...block.matchAll(/\{x: (-?\d+), y: (-?\d+), z: (-?\d+), facing: (\w+)\}/g)].map((m) => m.slice(1, 4).join(' ')),
  buys: [...block.matchAll(/\{item: ([a-z_]+), amount: (\d+), price: ([\d.]+)\}/g)].map(([, item, amount, price]) => ({ item, amount, price })),
}));

test('the datapack places no [Sell] signs', () => {
  assert.equal(all.filter((l) => l.includes('{text:"[Sell]"')).length, 0);
});

test('every selling stall has a free sign spot per slot (farmer: 4, the rest: 3) and a pool that fits on a sign', () => {
  assert.deepEqual(stalls.map((s) => s.id), ['farmer', 'lumberjack', 'fishmonger', 'smith', 'mob_drops', 'rare_goods']);
  const placed = new Set(all.filter((l) => l.startsWith('setblock ')).map((l) => l.split(' ').slice(1, 4).join(' ')));
  for (const s of stalls) {
    assert.equal(s.slots.length, s.id === 'farmer' ? 4 : 3, s.id);
    for (const at of s.slots) assert.ok(!placed.has(at), `${s.id} spot ${at} is also set by the datapack`);
    assert.ok(s.buys.length >= 3, `${s.id} needs at least 3 things to rotate through`);
    for (const b of s.buys) {
      assert.ok(b.item.length <= 15 && `$${b.price}`.length <= 15, `${s.id}: ${b.item} ${b.price}`);
      assert.ok(Number(b.price) > 0, `${s.id}: ${b.item} has no price`);
    }
  }
});

test('the head-stall, wanted-board and shops spots are all 4/3/1 free sign (or head) slots, matching the model', () => {
  const content = readStallsYml();
  const headStall = content.match(/^head-stall:\n {2}name: (\w+)\n {2}color: (\w+)\n {2}slots:\n((?: {4}-.*\n)+)/m);
  assert.ok(headStall, 'no head-stall block');
  assert.equal(headStall[1], 'Heads');
  const headSlots = [...headStall[3].matchAll(/\{x: (-?\d+), y: (-?\d+), z: (-?\d+), facing: (\w+)\}/g)];
  assert.equal(headSlots.length, 4, 'head-stall needs the same 4 slots as the Farmer');
  const placed = new Set(all.filter((l) => l.startsWith('setblock ')).map((l) => l.split(' ').slice(1, 4).join(' ')));
  for (const m of headSlots) assert.ok(!placed.has(m.slice(1, 4).join(' ')), `head-stall spot ${m[0]} is also set by the datapack`);

  const wanted = content.match(/^wanted-board:\n {2}sign: \{x: (-?\d+), y: (-?\d+), z: (-?\d+), facing: (\w+)\}\n {2}head: \{x: (-?\d+), y: (-?\d+), z: (-?\d+), facing: (\w+)\}/m);
  assert.ok(wanted, 'no wanted-board block');
  assert.equal(wanted[4], 'west');
  assert.equal(wanted[8], 'west');
  assert.ok(!placed.has(wanted.slice(1, 4).join(' ')), 'wanted-board sign spot is also set by the datapack');
  assert.ok(!placed.has(wanted.slice(5, 8).join(' ')), 'wanted-board head spot is also set by the datapack');
  assert.equal(Number(wanted[6]), Number(wanted[2]) + 1, 'the head sits one block above the sign');

  const shopBlocks = [...content.matchAll(/^ {2}(\w+): \{name: (\w+), color: (\w+), signs: \[(.*)\]\}$/mg)];
  assert.deepEqual(shopBlocks.map((m) => m[1]).sort(), ['cosmetics', 'homes']);
  const shopSigns = Object.fromEntries(shopBlocks.map((m) =>
    [m[1], [...m[4].matchAll(/\{x: (-?\d+), y: (-?\d+), z: (-?\d+), facing: (\w+)\}/g)]]));
  // Homes gets one wall sign per home (4th/5th/6th, left to right); every other shop (just
  // Cosmetics so far) keeps its original single spot (Joe, 2026-10-09).
  assert.equal(shopSigns.homes.length, 3, 'Homes needs 3 signs, one per home');
  assert.equal(shopSigns.cosmetics.length, 1, 'Cosmetics keeps its single sign');
  for (const signs of Object.values(shopSigns)) {
    for (const m of signs) assert.ok(!placed.has(m.slice(1, 4).join(' ')), `a shop spot ${m[0]} is also set by the datapack`);
  }
});

test('the price lectern is an empty lectern labelled Prices that nothing else builds over', () => {
  const m = readFileSync(join(here, '..', '..', 'market', 'src', 'main', 'resources', 'stalls.yml'), 'utf8')
    .match(/^price-lectern: \{x: (-?\d+), y: (-?\d+), z: (-?\d+), facing: (\w+)\}$/m);
  assert.ok(m, 'stalls.yml has no price-lectern');
  const [, x, y, z, facing] = m;
  const at = (dy) => all.filter((l) => l.startsWith(`setblock ${x} ${Number(y) + dy} ${z} `));
  // The Market plugin puts the book in, so the datapack's lectern has none.
  assert.deepEqual(at(0), [`setblock ${x} ${y} ${z} lectern[facing=${facing}]`]);
  assert.equal(at(2).length, 1);
  assert.match(at(2)[0], /hanging_sign.*"Prices"/);
});

// head-collector is gone (replaced by the Heads corner stall, Joe 2026-10-09).
test('stalls.yml no longer has a head-collector', () => {
  assert.ok(!readStallsYml().includes('head-collector'));
});

test('every sign line fits on the sign', () => {
  for (const l of lines('attach').filter((l) => l.includes('_sign'))) {
    const max = l.includes('hanging_sign') ? 10 : 15; // hanging signs are narrower
    const msgs = l.slice(l.indexOf('_text:')).replace(/color:"[a-z_]+"/g, '').match(/"((?:[^"\\]|\\.)*)"/g);
    for (const m of msgs) assert.ok(JSON.parse(m).length <= max, `${JSON.parse(m)} > ${max}: ${l}`);
  }
});

test('a block is written by exactly one phase', () => {
  const seen = new Map();
  for (const phase of ['terrain', 'structure', 'attach']) {
    for (const l of lines(phase).filter((l) => l.startsWith('setblock '))) {
      const at = l.split(' ').slice(1, 4).join(' ');
      assert.ok(!seen.has(at), `${at} set in ${seen.get(at)} and ${phase}`);
      seen.set(at, phase);
    }
  }
});

test('build schedules the run only after force-loading the area', () => {
  const build = lines('build');
  assert.match(build[0], /^forceload add /);
  assert.ok(build.at(-1).startsWith('schedule function spawn_market:build_run'));
  assert.match(lines('build_run').at(-2), /^forceload remove /);
});

// The Dragon Balls station (2026-10-06) replaced the old Crafting corner. 'anvil' is deliberately
// not checked here: the Smith stall legitimately keeps its own (test below confirms the Dragon
// Balls stall's own former anvil/grindstone/cartography_table/loom tiles now hold a pedestal).
test('the old crafting_table/smithing_table/stonecutter/grindstone/cartography_table/loom are gone', () => {
  for (const removed of ['crafting_table', 'smithing_table', 'stonecutter', 'grindstone', 'cartography_table', 'loom']) {
    assert.equal(all.filter((l) => l.includes(removed)).length, 0, `${removed} still placed`);
  }
});

const station = JSON.parse(readFileSync(join(here, 'dragonballs-station.json'), 'utf8'));

const SETS = ['overworld', 'nether', 'end'];

test('dragonballs-station.json exports one radar sign per ball set (at the Radars shop) and 7 head-pedestal coordinates', () => {
  assert.deepEqual(Object.keys(station.shopRadarSigns), SETS);
  for (const s of SETS) {
    assert.equal(station.shopRadarSigns[s].world, 'world');
    for (const k of ['x', 'y', 'z']) assert.equal(typeof station.shopRadarSigns[s][k], 'number');
  }
  assert.equal(station.displayHeads.length, 7);
  for (const h of station.displayHeads) {
    assert.equal(h.world, 'world');
    for (const k of ['x', 'y', 'z']) assert.equal(typeof h[k], 'number');
  }
});

const RADAR_SIGNS = {
  overworld: { lines: ['[Dragon Radar]', 'Points at the', 'nearest ball', '1000 coins'], title: /text:"\[Dragon Radar\]",color:"gold",bold:true/ },
  nether: { lines: ['[Nether Radar]', 'Finds Nether', 'balls only', '5000 coins'], title: /text:"\[Nether Radar\]",color:"red",bold:true/ },
  end: { lines: ['[End Radar]', 'Finds End', 'balls only', '20000 coins'], title: /text:"\[End Radar\]",color:"light_purple",bold:true/ },
};

test('each radar sign is a real sign at its exported coordinates, with the exact 4 lines, not an Essentials shop sign', () => {
  for (const [set, want] of Object.entries(RADAR_SIGNS)) {
    const { x, y, z } = station.shopRadarSigns[set];
    const radarLine = lines('attach').find((l) => l.startsWith(`setblock ${x} ${y} ${z} `) && l.includes('_sign'));
    assert.ok(radarLine, `no ${set} sign setblock at ${x} ${y} ${z}`);
    assert.ok(!radarLine.includes('{text:"[Sell]"') && !radarLine.includes('{text:"[Buy]"'));
    const msgs = radarLine.slice(radarLine.indexOf('_text:')).replace(/color:"[a-z_]+"/g, '')
      .match(/"((?:[^"\\]|\\.)*)"/g).map((m) => JSON.parse(m));
    assert.deepEqual(msgs, want.lines);
    assert.match(radarLine, want.title);
  }
});

// Adjacent pedestals of the same state fold into one `fill` command (emit()'s row-merging), so
// this checks both setblock and fill coverage rather than assuming one setblock per coordinate.
function blockAt(phaseLines, x, y, z) {
  for (const l of phaseLines) {
    if (l.startsWith('setblock ')) {
      const [sx, sy, sz, ...rest] = l.slice('setblock '.length).split(' ');
      if (+sx === x && +sy === y && +sz === z) return rest.join(' ');
    } else if (l.startsWith('fill ')) {
      const [x0, y0, z0, x1, y1, z1, ...rest] = l.slice('fill '.length).split(' ');
      const inRange = (v, a, b) => v >= Math.min(+a, +b) && v <= Math.max(+a, +b);
      if (inRange(x, x0, x1) && inRange(y, y0, y1) && inRange(z, z0, z1)) return rest.join(' ');
    }
  }
  return null;
}

test('every exported display-head spot sits on a placed pedestal block', () => {
  const structureLines = lines('structure');
  for (const { x, y, z } of station.displayHeads) {
    const below = blockAt(structureLines, x, y - 1, z);
    assert.ok(below, `no pedestal below ${x} ${y} ${z}`);
    assert.match(below, /^chiseled_tuff_bricks/);
  }
});

const model = JSON.parse(readFileSync(join(here, 'model.json'), 'utf8'));
// The DragonBall station and the Radars corner shop each carry their own "Dragon Balls" lectern,
// so picking the FIRST match by object-key order would be ambiguous; pick the one nearest the
// Radars shop's own radar signs instead (same 5x3 stall, a few blocks apart from the station's).
const dbLecterns = Object.entries(model.blocks).filter(([, s]) => s.includes('lectern[') && s.includes('title:"Dragon Balls"'));
const distTo = (key, { x, z }) => { const [kx, , kz] = key.split(',').map(Number); return Math.abs(kx - x) + Math.abs(kz - z); };
const [lecternKey] = [...dbLecterns].map(([k]) => k).sort((a, b) => distTo(a, station.shopRadarSigns.overworld) - distTo(b, station.shopRadarSigns.overworld));
const [lecternX, lecternY, lecternZ] = lecternKey.split(',').map(Number);

test('the Radars corner shop carries its own Dragon Balls lectern, distinct from the station\'s', () => {
  assert.equal(dbLecterns.length, 2);
});

test('the radar signs, the lectern and all 7 display-head spots sit at distinct coordinates', () => {
  const keyOf = ({ x, y, z }) => `${x},${y},${z}`;
  const seen = new Set([`${lecternX},${lecternY},${lecternZ}`]);
  for (const s of SETS) {
    const k = keyOf(station.shopRadarSigns[s]);
    assert.ok(!seen.has(k), `${s} radar sign shares ${k}`);
    seen.add(k);
  }
  for (const h of station.displayHeads) {
    const k = keyOf(h);
    assert.ok(!seen.has(k), `${k} is shared between the radar sign/lectern and a display-head spot`);
    seen.add(k);
  }
  // Also distinct in plan view (x,z), regardless of y: a head one block above or below the sign
  // or lectern would still visually block it.
  const planOf = ({ x, z }) => `${x},${z}`;
  const plans = new Set([...SETS.map((s) => planOf(station.shopRadarSigns[s])), `${lecternX},${lecternZ}`]);
  for (const h of station.displayHeads) {
    const p = planOf(h);
    assert.ok(!plans.has(p), `${p} (x,z) is shared between the radar sign/lectern and a display-head spot`);
    plans.add(p);
  }
});

test('every display-head spot is air in the generated model, directly above a pedestal block', () => {
  for (const { x, y, z } of station.displayHeads) {
    assert.ok(!(`${x},${y},${z}` in model.blocks), `${x} ${y} ${z} is not air in model.json`);
    const below = model.blocks[`${x},${y - 1},${z}`];
    assert.ok(below, `no pedestal below ${x} ${y} ${z} in model.json`);
    assert.match(below, /^chiseled_tuff_bricks/);
  }
});

test('the lectern holds the Dragon Balls guide book, labelled by a matching sign', () => {
  assert.ok(all.some((l) => l.includes('lectern[') && l.includes('title:"Dragon Balls"')),
    'no lectern with the Dragon Balls book');
  assert.ok(all.some((l) => l.includes('_sign') && l.includes('"Dragon Balls"')),
    'no label sign reading Dragon Balls');
});

test('the head-stall, wanted-board and shops spots are all distinct coordinates and air in the model', () => {
  const content = readStallsYml();
  const headStall = content.match(/^head-stall:\n {2}name: \w+\n {2}color: \w+\n {2}slots:\n((?: {4}-.*\n)+)/m);
  const headSlots = [...headStall[1].matchAll(/\{x: (-?\d+), y: (-?\d+), z: (-?\d+), facing: (\w+)\}/g)]
    .map((m) => ({ x: +m[1], y: +m[2], z: +m[3] }));
  const wanted = content.match(/^wanted-board:\n {2}sign: \{x: (-?\d+), y: (-?\d+), z: (-?\d+), facing: \w+\}\n {2}head: \{x: (-?\d+), y: (-?\d+), z: (-?\d+), facing: \w+\}/m);
  const wantedSpots = [
    { x: +wanted[1], y: +wanted[2], z: +wanted[3] },
    { x: +wanted[4], y: +wanted[5], z: +wanted[6] },
  ];
  // The shops: block sits between wanted-board: and stalls: (same section-slicing trick
  // stallsSection above uses), so every {x,y,z} inside it is a shop's own sign spot.
  const shopsSection = content.slice(content.indexOf('\nshops:\n'), content.indexOf('\nstalls:\n'));
  const shopSigns = [...shopsSection.matchAll(/\{x: (-?\d+), y: (-?\d+), z: (-?\d+), facing: \w+\}/g)]
    .map((m) => ({ x: +m[1], y: +m[2], z: +m[3] }));
  const everySpot = [...headSlots, ...wantedSpots, ...shopSigns];
  const seen = new Set();
  for (const { x, y, z } of everySpot) {
    const k = `${x},${y},${z}`;
    assert.ok(!seen.has(k), `${k} is reused by two different plugin-owned spots`);
    seen.add(k);
    assert.ok(!(k in model.blocks), `${k} is not air in model.json`);
  }
});

// Stalls must not overlap each other, any existing stall/station, a lantern post, or a path
// (Joe, 2026-10-09: checked against the real side() math, not eyeballed).
const { model: genModel } = await import('./core.mjs');
const { side, CORE, STREET_V0, STREET_LEN } = genModel;
function bbox(sideName, a0) {
  const S = side(sideName);
  const us = [], vs = [];
  for (let a = -1; a <= 5; a++) for (const d of [-1, 4]) { const [u, v] = S.at(a0 + a, d); us.push(u); vs.push(v); }
  return { u0: Math.min(...us), u1: Math.max(...us), v0: Math.min(...vs), v1: Math.max(...vs) };
}
const overlaps = (a, b) => a.u0 <= b.u1 && a.u1 >= b.u0 && a.v0 <= b.v1 && a.v1 >= b.v0;
// The 8 plaza stalls (selling stalls + Guide + DragonBall), all still inside CORE.
const PLAZA_SIDES = [
  ['north', -8], ['north', 4],
  ['east', -8], ['east', 4],
  ['south', -8], ['south', 4],
  ['west', 4], ['west', -8],
];
// The 4 shop-street stalls (Heads/Radars/Homes/Cosmetics, Joe's pick 2026-10-09), 2 per side.
const STREET_SIDES = [['streetWest', 0], ['streetWest', 10], ['streetEast', 0], ['streetEast', 10]];
const STALL_SIDES = [...PLAZA_SIDES, ...STREET_SIDES];

test('no stall overlaps another stall or a lantern post; plaza stalls stay within CORE, street stalls stay within the street', () => {
  const boxes = STALL_SIDES.map(([s, a0]) => bbox(s, a0));
  for (let i = 0; i < boxes.length; i++) for (let j = i + 1; j < boxes.length; j++) {
    assert.ok(!overlaps(boxes[i], boxes[j]), `${STALL_SIDES[i]} overlaps ${STALL_SIDES[j]}`);
  }
  for (const b of PLAZA_SIDES.map(([s, a0]) => bbox(s, a0))) {
    assert.ok(Math.max(Math.abs(b.u0), Math.abs(b.u1), Math.abs(b.v0), Math.abs(b.v1)) <= CORE, 'plaza stall steps outside CORE');
  }
  for (const b of STREET_SIDES.map(([s, a0]) => bbox(s, a0))) {
    assert.ok(b.v0 >= STREET_V0 - 1 && b.v1 <= STREET_V0 + STREET_LEN, 'street stall steps outside the street corridor');
  }
  const posts = [[-10, -10], [10, -10], [-10, 10], [10, 10]];
  for (const [u, v] of posts) {
    for (const b of boxes) assert.ok(!(u >= b.u0 && u <= b.u1 && v >= b.v0 && v <= b.v1), `post ${u},${v} sits inside a stall`);
  }
});

test('the Wanted board does not overlap the centre mosaic or any stall', () => {
  const wantedBox = { u0: 4, u1: 6, v0: -3, v1: 3 };
  assert.ok(Math.max(Math.abs(wantedBox.u0), Math.abs(wantedBox.u1)) > 3, 'clear of the centre stone-brick points');
  const boxes = STALL_SIDES.map(([s, a0]) => bbox(s, a0));
  for (const b of boxes) assert.ok(!overlaps(wantedBox, b), 'Wanted board overlaps a stall');
});

test('every stall front faces open paving (no block sits on the tile right outside its entrance)', () => {
  for (const [sideName, a0] of STALL_SIDES) {
    const S = side(sideName);
    for (let a = 0; a <= 4; a++) {
      const [u, v] = S.at(a0 + a, -1);
      const key = `${model.origin[0] + u},${model.y + 1},${model.origin[1] + v}`;
      assert.ok(!(key in model.blocks), `${sideName}/${a0} front at a=${a} (${u},${v}) is blocked`);
    }
  }
});

// A carpet has no hold of its own: one over air pops off at the first neighbour update, which is
// what took 6 of the awning canopy's 15 carpets on live (2026-10-09).
test('every carpet sits on a block', () => {
  for (const [k, state] of Object.entries(model.blocks)) {
    if (!state.includes('_carpet')) continue;
    const [x, y, z] = k.split(',').map(Number);
    assert.ok(`${x},${y - 1},${z}` in model.blocks, `${state} at ${k} has air below it`);
  }
});

// attached=false hangs a sign from a chain off the block directly above it; the street stalls'
// new description signs (stalls.mjs's `desc`) hang under the same front beam the nameplate
// already does, but any future hanging sign needs this checked too (Joe, 2026-10-09).
test('every hanging sign has a solid block directly above it', () => {
  for (const [k, state] of Object.entries(model.blocks)) {
    if (!state.includes('hanging_sign')) continue;
    const [x, y, z] = k.split(',').map(Number);
    assert.ok(`${x},${y + 1},${z}` in model.blocks, `${state} at ${k} has no block above it to hang from`);
  }
});
