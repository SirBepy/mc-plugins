#!/usr/bin/env node
// Writes market/src/main/resources/heads.yml, the Head collector's catalogue: every textured mob
// head the More Mob Heads datapack (server/datapacks/more-mob-heads-*.zip) can drop, grouped by
// mob, each with its drop chance and base price.
//
// A head is recognised in game by its skin texture hash, the last segment of the textures.minecraft.net
// URL inside its profile; a real player's head carries a name and id as well, which the plugin refuses.
//
// Price is by drop chance (Joe, 2026-10-08), farmed mob or not: PRICE_PER_KILL / chance, so the
// expected pay per player kill is the same for every mob. $0.50 a kill matches the spawn market's
// $20 wither skeleton skull, a 2.5% drop. The chance is the unenchanted one: looting barely moves it.
//
// Usage: node server/market-heads/generate.mjs
import { mkdtempSync, readFileSync, readdirSync, rmSync, statSync, writeFileSync, existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { extractZip } from '../lib/extract-zip.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const DATAPACKS = join(here, '..', 'datapacks');
const OUT = join(here, '..', '..', 'market', 'src', 'main', 'resources', 'heads.yml');
export const PRICE_PER_KILL = 0.5;

/** Every json file under dir, keyed by its path relative to dir without the extension. */
function tables(dir) {
  const out = new Map();
  if (!existsSync(dir)) return out;
  const walk = (d) => {
    for (const f of readdirSync(d)) {
      const p = join(d, f);
      if (statSync(p).isDirectory()) walk(p);
      else if (f.endsWith('.json')) out.set(relative(dir, p).replaceAll('\\', '/').replace(/\.json$/, ''), JSON.parse(readFileSync(p, 'utf8')));
    }
  };
  walk(dir);
  return out;
}

/** The chance a set of loot conditions lets through: the product of its random-chance terms. */
function chanceOf(conditions) {
  let chance = 1;
  for (const c of [conditions ?? []].flat()) {
    const type = (c.type ?? c.condition ?? '').replace('minecraft:', '');
    if (type === 'random_chance_with_enchanted_bonus') chance *= c.unenchanted_chance;
    else if (type === 'random_chance') chance *= typeof c.chance === 'number' ? c.chance : 1;
    else if (type === 'all_of') chance *= chanceOf(c.terms);
  }
  return chance;
}

/** The skin texture hash inside a profile component, e.g. a275728af7e6... for the Breeze Head. */
export function textureHash(profile) {
  const value = profile?.properties?.find((p) => p.name === 'textures')?.value;
  if (!value) return null;
  const url = JSON.parse(Buffer.from(value, 'base64').toString('utf8')).textures?.SKIN?.url;
  return url ? url.slice(url.lastIndexOf('/') + 1) : null;
}

/**
 * Every head a loot table can drop, as {name, texture, chance}. Follows alternatives/group children
 * and loot_table references into the pack's own tables, multiplying the chances along the way.
 */
export function headsOf(table, all, chance = 1) {
  const heads = [];
  const visit = (entry, p) => {
    p *= chanceOf(entry.conditions ?? entry.condition);
    const type = (entry.type ?? '').replace('minecraft:', '');
    if (entry.children) entry.children.forEach((child) => visit(child, p));
    if (type === 'loot_table' && typeof entry.value === 'string') {
      const ref = all.get(entry.value.replace(/^more_mob_heads:entities\//, ''));
      if (ref) heads.push(...headsOf(ref, all, p));
    }
    if (type === 'item' && entry.name === 'minecraft:player_head') {
      for (const mod of entry.modifier ?? entry.functions ?? []) {
        const components = mod.components ?? {};
        const texture = textureHash(components['minecraft:profile']);
        if (texture) heads.push({ name: components['minecraft:item_name'], texture, chance: p });
      }
    }
  };
  for (const pool of table.pools ?? []) {
    const p = chance * chanceOf(pool.conditions ?? pool.condition);
    (pool.entries ?? []).forEach((entry) => visit(entry, p));
  }
  return heads;
}

/** Readable mob name for a loot table id: zombified_piglin reads as Zombified piglin. */
const mobName = (id) => id.charAt(0).toUpperCase() + id.slice(1).replaceAll('_', ' ');

export function catalogue(all) {
  const mobs = new Map();
  for (const [id, table] of [...all].sort(([a], [b]) => a.localeCompare(b))) {
    if (id.includes('/')) continue; // a variant table (sheep/black), reached from its mob's own table
    const byTexture = new Map();
    for (const head of headsOf(table, all)) {
      const seen = byTexture.get(head.texture);
      if (!seen || head.chance > seen.chance) byTexture.set(head.texture, head);
    }
    if (byTexture.size) mobs.set(id, [...byTexture.values()]);
  }
  return mobs;
}

export const price = (chance) => +(PRICE_PER_KILL / chance).toFixed(2);

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  const zip = readdirSync(DATAPACKS).find((f) => /^more-mob-heads-.*\.zip$/.test(f));
  if (!zip) throw new Error(`no more-mob-heads-*.zip in ${DATAPACKS}`);
  const tmp = mkdtempSync(join(tmpdir(), 'mc-heads-'));
  try {
    extractZip(join(DATAPACKS, zip), tmp);
    // The pack's only overlay covers 26.3's data format, so its tables replace the base ones.
    const all = tables(join(tmp, 'data', 'more_mob_heads', 'loot_table', 'entities'));
    for (const overlay of readdirSync(tmp).filter((d) => d.startsWith('overlay_'))) {
      for (const [id, table] of tables(join(tmp, overlay, 'data', 'more_mob_heads', 'loot_table', 'entities'))) all.set(id, table);
    }
    const mobs = catalogue(all);
    const lines = [
      `# Generated by server/market-heads/generate.mjs from ${zip} - do not edit by hand.`,
      `# The Head collector buys these. texture is the skin hash in the head's profile; price is`,
      `# $${PRICE_PER_KILL.toFixed(2)} / chance, the unenchanted drop chance on a player kill.`,
      'mobs:',
    ];
    let count = 0;
    for (const [id, heads] of mobs) {
      lines.push(`  ${id}:`, `    name: ${JSON.stringify(mobName(id))}`, '    heads:');
      for (const h of heads) {
        lines.push(`      - {name: ${JSON.stringify(h.name)}, texture: ${h.texture}, chance: ${+h.chance.toFixed(6)}, price: ${price(h.chance)}}`);
        count++;
      }
    }
    writeFileSync(OUT, lines.join('\n') + '\n');
    console.log(`${mobs.size} mobs, ${count} heads, written to ${OUT}`);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
}
