import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { PRICE_PER_KILL, catalogue, headsOf, price, textureHash } from './generate.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const yml = readFileSync(join(here, '..', '..', 'market', 'src', 'main', 'resources', 'heads.yml'), 'utf8');
const heads = [...yml.matchAll(/\{name: "([^"]+)", texture: (\w+), chance: ([\d.e-]+), price: ([\d.]+)\}/g)]
  .map(([, name, texture, chance, p]) => ({ name, texture, chance: Number(chance), price: Number(p) }));

const profile = (hash) => ({
  properties: [{ name: 'textures', value: Buffer.from(JSON.stringify({ textures: { SKIN: { url: `http://textures.minecraft.net/texture/${hash}` } } })).toString('base64') }],
});
const head = (name, hash, condition) => ({
  type: 'item', name: 'minecraft:player_head', condition,
  modifier: [{ type: 'set_components', components: { 'minecraft:profile': profile(hash), 'minecraft:item_name': name } }],
});
const chance = (c) => ({ type: 'random_chance_with_enchanted_bonus', unenchanted_chance: c });

test('a texture hash is the last segment of the skin url', () => {
  assert.equal(textureHash(profile('abc123')), 'abc123');
  assert.equal(textureHash({}), null);
});

test('chances multiply down alternatives and into referenced variant tables', () => {
  const all = new Map([
    ['sheep/red', { pools: [{ entries: [head('Red Sheep Head', 'red', { type: 'all_of', terms: [chance(0.5)] })] }] }],
    ['sheep', {
      pools: [{
        condition: { type: 'all_of', terms: [{ type: 'killed_by_player' }, chance(0.1)] },
        entries: [{ type: 'minecraft:alternatives', children: [
          { type: 'minecraft:loot_table', value: 'more_mob_heads:entities/sheep/red' },
          head('Plain Sheep Head', 'plain'),
        ] }],
      }],
    }],
  ]);
  assert.deepEqual(headsOf(all.get('sheep'), all), [
    { name: 'Red Sheep Head', texture: 'red', chance: 0.05 },
    { name: 'Plain Sheep Head', texture: 'plain', chance: 0.1 },
  ]);
  assert.deepEqual([...catalogue(all).keys()], ['sheep'], 'a variant table is not a mob of its own');
});

test('every shipped head pays $0.50 over its drop chance, and no two share a texture', () => {
  assert.ok(heads.length > 150, `${heads.length} heads`);
  for (const h of heads) assert.equal(h.price, price(h.chance), h.name);
  assert.equal(PRICE_PER_KILL, 0.5);
  assert.equal(new Set(heads.map((h) => h.texture)).size, heads.length);
  assert.equal(heads.find((h) => h.name === 'Breeze Head').price, 5);
});
