// node --test server/market-prices/generate.test.mjs
// Checks the recipe pricing on small fixtures, and the committed prices.yml for gaps. Regenerating
// needs the 26.3 client jar, so this reads the committed output instead of running generate.mjs.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { FUEL, computePrices, resolveTags } from './pricing.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const tags = resolveTags(new Map([
  ['logs', ['minecraft:oak_log', '#minecraft:birch_logs']],
  ['birch_logs', ['minecraft:birch_log']],
]));

test('tags resolve through nested tags', () => {
  assert.deepEqual([...tags.get('logs')].sort(), ['birch_log', 'oak_log']);
});

test('a recipe costs its inputs divided by what it makes, using the cheapest tag member', () => {
  const recipes = [
    { type: 'minecraft:crafting_shapeless', ingredients: ['#minecraft:logs'], result: { id: 'minecraft:planks', count: 4 } },
    { type: 'minecraft:crafting_shaped', pattern: ['#', '#'], key: { '#': 'minecraft:planks' }, result: { id: 'minecraft:stick', count: 4 } },
    { type: 'minecraft:smelting', ingredient: 'minecraft:raw', result: { id: 'minecraft:ingot' } },
  ];
  const prices = computePrices(recipes, tags, { oak_log: 2, birch_log: 1, raw: 1 });
  assert.equal(prices.get('planks'), 0.25); // the $1 birch log, not the $2 oak
  assert.equal(prices.get('stick'), 0.125);
  assert.equal(prices.get('ingot'), 1 + FUEL);
});

test('fixed prices win over recipes, and the cheaper of two recipes is used', () => {
  const recipes = [
    { type: 'minecraft:crafting_shapeless', ingredients: ['minecraft:a'], result: { id: 'minecraft:b' } },
    { type: 'minecraft:crafting_shapeless', ingredients: ['minecraft:c', 'minecraft:c'], result: { id: 'minecraft:b' } },
    { type: 'minecraft:crafting_shapeless', ingredients: ['minecraft:a'], result: { id: 'minecraft:fixed' } },
  ];
  const prices = computePrices(recipes, tags, { a: 5, c: 1, fixed: 9 });
  assert.equal(prices.get('b'), 2);
  assert.equal(prices.get('fixed'), 9);
});

test('special recipes that make no new item are ignored', () => {
  const recipes = [{ type: 'minecraft:crafting_special_repairitem' }];
  assert.equal(computePrices(recipes, tags, {}).size, 0);
});

const yml = readFileSync(join(here, '..', '..', 'market', 'src', 'main', 'resources', 'prices.yml'), 'utf8');
const section = (name) => Object.fromEntries([...yml.split(`\n${name}:\n`)[1].split(/\n[a-z]/)[0]
  .matchAll(/^ {2}([a-z0-9_]+): ([\d.]+)$/gm)].map((m) => [m[1], Number(m[2])]));
const items = section('items');

test('prices.yml prices every item with a positive number', () => {
  assert.ok(Object.keys(items).length > 1400, `only ${Object.keys(items).length} items`);
  for (const [id, price] of Object.entries(items)) assert.ok(price > 0, `${id} costs ${price}`);
});

test('market items are twice their stall base and hand-gathered items stay dearer than farm output', () => {
  assert.equal(items.diamond, 20);
  assert.equal(items.raw_iron, 1.25);
  assert.equal(items.raw_copper, 0.4);
  assert.equal(items.oak_log, 0.9);
  assert.equal(items.stripped_oak_log, items.oak_log);
  // Tall-tree logs pay half, Nether stems 20% over a log, and stripping keeps each one's price.
  for (const log of ['spruce_log', 'jungle_log', 'dark_oak_log', 'pale_oak_log']) {
    assert.equal(items[log], items.oak_log / 2, log);
    assert.equal(items[`stripped_${log}`], items[log], `stripped_${log}`);
  }
  for (const stem of ['crimson_stem', 'warped_stem']) {
    assert.ok(Math.abs(items[stem] - items.oak_log * 1.2) < 1e-4, stem);
    assert.equal(items[`stripped_${stem}`], items[stem], `stripped_${stem}`);
  }
  assert.ok(Math.abs(items.iron_ingot - (items.raw_iron + FUEL)) < 1e-4, 'an ingot costs its smelted raw ore');
  assert.ok(items.netherite_ingot > 200);
});

test('every spawn-market item is suggested at twice its current stall price (rerun generate.mjs after a stall change)', () => {
  const stalls = readFileSync(join(here, '..', '..', 'market', 'src', 'main', 'resources', 'stalls.yml'), 'utf8');
  const aliases = { membrane: 'phantom_membrane' };
  for (const [, item, amount, price] of stalls.matchAll(/\{item: ([a-z_]+), amount: (\d+), price: ([\d.]+)\}/g)) {
    const id = aliases[item] ?? item;
    assert.ok(Math.abs(items[id] - (2 * Number(price)) / Number(amount)) < 1e-4, `${id}: ${items[id]} vs stall ${price}/${amount}`);
  }
});

test('creative-only items are untradeable, not priced', () => {
  const untradeable = yml.split('\nuntradeable:\n')[1];
  for (const id of ['cow_spawn_egg', 'command_block', 'bedrock', 'barrier']) {
    assert.ok(untradeable.includes(`  - ${id}\n`), id);
    assert.ok(!(id in items), id);
  }
});
