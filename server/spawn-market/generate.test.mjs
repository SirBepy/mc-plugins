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
const stalls = readFileSync(join(here, '..', '..', 'market', 'src', 'main', 'resources', 'stalls.yml'), 'utf8')
  .split(/\n(?=  [a-z_]+:\n)/).slice(1).map((block) => ({
    id: block.match(/^ {2}([a-z_]+):/)[1],
    slots: [...block.matchAll(/\{x: (-?\d+), y: (-?\d+), z: (-?\d+), facing: (\w+)\}/g)].map((m) => m.slice(1, 4).join(' ')),
    buys: [...block.matchAll(/\{item: ([a-z_]+), amount: (\d+), price: ([\d.]+)\}/g)].map(([, item, amount, price]) => ({ item, amount, price })),
  }));

test('the datapack places no [Sell] signs', () => {
  assert.equal(all.filter((l) => l.includes('{text:"[Sell]"')).length, 0);
});

test('every selling stall has 3 free sign spots and a pool that fits on a sign', () => {
  assert.deepEqual(stalls.map((s) => s.id), ['farmer', 'lumberjack', 'fishmonger', 'smith', 'mob_drops', 'rare_goods']);
  const placed = new Set(all.filter((l) => l.startsWith('setblock ')).map((l) => l.split(' ').slice(1, 4).join(' ')));
  for (const s of stalls) {
    assert.equal(s.slots.length, 3, s.id);
    for (const at of s.slots) assert.ok(!placed.has(at), `${s.id} spot ${at} is also set by the datapack`);
    assert.ok(s.buys.length >= 3, `${s.id} needs at least 3 things to rotate through`);
    for (const b of s.buys) {
      assert.ok(b.item.length <= 15 && `$${b.price}`.length <= 15, `${s.id}: ${b.item} ${b.price}`);
      assert.ok(Number(b.price) > 0, `${s.id}: ${b.item} has no price`);
    }
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

test('the Head collector spot is open floor the plugin can stand its NPC on', () => {
  const m = readFileSync(join(here, '..', '..', 'market', 'src', 'main', 'resources', 'stalls.yml'), 'utf8')
    .match(/^head-collector: \{x: (-?\d+), y: (-?\d+), z: (-?\d+), facing: (\w+)\}$/m);
  assert.ok(m, 'stalls.yml has no head-collector');
  const [, x, y, z] = m;
  const at = (dy) => all.filter((l) => l.startsWith(`setblock ${x} ${Number(y) + dy} ${z} `));
  assert.equal(at(-1).length, 1, 'something to stand on');
  assert.deepEqual([...at(0), ...at(1)], [], 'two blocks of headroom');
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

test('dragonballs-station.json exports one radar sign per ball set and 7 head-pedestal coordinates', () => {
  assert.deepEqual(Object.keys(station.radarSigns), SETS);
  for (const s of SETS) {
    assert.equal(station.radarSigns[s].world, 'world');
    for (const k of ['x', 'y', 'z']) assert.equal(typeof station.radarSigns[s][k], 'number');
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
    const { x, y, z } = station.radarSigns[set];
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
const lecternKey = Object.entries(model.blocks).find(([, s]) => s.includes('lectern[') && s.includes('title:"Dragon Balls"'))[0];
const [lecternX, lecternY, lecternZ] = lecternKey.split(',').map(Number);

test('the radar signs, the lectern and all 7 display-head spots sit at distinct coordinates', () => {
  const keyOf = ({ x, y, z }) => `${x},${y},${z}`;
  const seen = new Set([`${lecternX},${lecternY},${lecternZ}`]);
  for (const s of SETS) {
    const k = keyOf(station.radarSigns[s]);
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
  const plans = new Set([...SETS.map((s) => planOf(station.radarSigns[s])), `${lecternX},${lecternZ}`]);
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
