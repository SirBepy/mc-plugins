#!/usr/bin/env node
// Writes market/src/main/resources/heads.yml, the Heads stall's catalogue: every textured mob
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
// On top of that, a one-time scarcity multiplier (Joe, 2026-10-17: "any head that isn't easily
// farmable should be expensive") baked in from live's real kill counts rather than loot-table odds
// alone - a mob killed thousands of times is obviously farmable regardless of how rare its head
// drop is, and a mob nobody's ever killed is obviously not. KILL_COUNTS is a snapshot (summed
// minecraft:killed stats across every world/players/stats/*.json on live, 2026-10-17, 25 players,
// 31270 kills total), not a live query - it doesn't change until someone deliberately re-pulls it
// and reruns this generator. A mob missing from the snapshot (never killed) gets the same
// bottom-of-the-barrel treatment as the rarest mob that IS in it.
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

export const KILL_COUNTS = {
  zombie: 7156, skeleton: 6547, creeper: 6454, slime: 1185, spider: 760, magma_cube: 744,
  pillager: 697, husk: 671, shulker: 524, endermite: 478, piglin: 455, phantom: 450, blaze: 427,
  enderman: 411, drowned: 349, wither_skeleton: 300, vindicator: 257, breeze: 252, stray: 251,
  cow: 244, silverfish: 221, parched: 214, hoglin: 188, witch: 176, zombified_piglin: 173,
  pig: 172, chicken: 171, cave_spider: 158, vex: 158, piglin_brute: 135, zombie_villager: 129,
  sheep: 119, bogged: 77, rabbit: 68, squid: 67, ghast: 52, evoker: 48, camel_husk: 41,
  guardian: 39, ravager: 39, horse: 25, sulfur_cube: 23, bat: 22, glow_squid: 19, villager: 14,
  trader_llama: 12, zombie_nautilus: 12, skeleton_horse: 10, copper_golem: 8, wolf: 7, nautilus: 7,
  salmon: 6, tropical_fish: 6, camel: 5, wandering_trader: 4, turtle: 4, iron_golem: 3,
  ender_dragon: 3, zombie_horse: 3, dolphin: 3, armadillo: 3, donkey: 2, goat: 2, creaking: 2,
  fox: 2, mooshroom: 2, wither: 1, pufferfish: 1, illusioner: 1, allay: 1,
};
const MOST_KILLED = Math.max(...Object.values(KILL_COUNTS));
const SCARCITY_CAP = 20;

/** 1 for a mob killed as often as the server's most-farmed one, up to SCARCITY_CAP for one killed
 * rarely or never. Square root so the gap between "common" and "rare" doesn't swing by 1000x. */
export const scarcity = (id) => Math.min(SCARCITY_CAP, Math.sqrt(MOST_KILLED / Math.max(KILL_COUNTS[id] ?? 0, 1)));

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

export const price = (chance, id) => +(PRICE_PER_KILL / chance * scarcity(id)).toFixed(2);

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
      `# The Heads stall buys these. texture is the skin hash in the head's profile; price is`,
      `# $${PRICE_PER_KILL.toFixed(2)} / chance / scarcity, the unenchanted drop chance on a player`,
      `# kill times a 2026-10-17 kill-count scarcity multiplier (1x for a heavily-farmed mob, up to`,
      `# ${SCARCITY_CAP}x for a rare or never-killed one).`,
      'mobs:',
    ];
    let count = 0;
    for (const [id, heads] of mobs) {
      lines.push(`  ${id}:`, `    name: ${JSON.stringify(mobName(id))}`, '    heads:');
      for (const h of heads) {
        lines.push(`      - {name: ${JSON.stringify(h.name)}, texture: ${h.texture}, chance: ${+h.chance.toFixed(6)}, price: ${price(h.chance, id)}}`);
        count++;
      }
    }
    writeFileSync(OUT, lines.join('\n') + '\n');
    console.log(`${mobs.size} mobs, ${count} heads, written to ${OUT}`);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
}
