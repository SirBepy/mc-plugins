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

test('every [Sell] sign uses the Essentials success format and a 4-line layout', () => {
  const signs = lines('attach').filter((l) => l.includes('messages:[{text:"[Sell]"'));
  assert.equal(signs.length, 38); // Joe's 2026-10-05 price table
  for (const l of signs) {
    assert.match(l, /messages:\[\{text:"\[Sell\]",color:"dark_blue"\},"\d+","[a-z_]+","\$[\d.]+"\]/, l);
  }
});

test('every sign line fits on the sign', () => {
  for (const l of lines('attach').filter((l) => l.includes('_sign'))) {
    const max = l.includes('hanging_sign') ? 10 : 15; // hanging signs are narrower
    const msgs = l.slice(l.indexOf('messages:[')).match(/"((?:[^"\\]|\\.)*)"/g).filter((m) => !['"dark_blue"'].includes(m));
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
