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
