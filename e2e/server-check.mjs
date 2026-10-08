import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { Rcon, sleep } from './lib.mjs';

// One-shot post-boot sanity check for the real server assembly (server/local), run manually after
// `:server:assembleLocalServer` + a boot through the supervisor. Not part of e2e/run-all.mjs: this
// checks static server state (permissions, config, gamerules), not plugin behavior against bots.

const here = path.dirname(fileURLToPath(import.meta.url));
const localDir = path.join(here, '..', 'server', 'local');

let failures = 0;
function report(name, pass, detail) {
  console.log(`${pass ? 'PASS' : 'FAIL'} ${name}${detail ? ` - ${detail}` : ''}`);
  if (!pass) failures++;
}

// --- LuckPerms group permissions ---------------------------------------------------------------
// LuckPerms produces NO output at all over RCON for any subcommand, including plain `lp` and
// `lp listgroups` (confirmed live this session: empty even with generous polling and multiple
// invocation forms). This is unrelated to the general async-command truncation issue Rcon.command
// works around elsewhere in this file - LP's command sender just never receives a response LP will
// write back to over RCON. So group permissions are verified against LuckPerms' own yaml-storage
// files instead: storage-method is yaml (server/config/plugins/LuckPerms/config.yml), so these ARE
// the data LuckPerms actually loads and resolves permissions from, not a guess.
function loadGroup(name) {
  const file = path.join(localDir, 'plugins/LuckPerms/yaml-storage/groups', `${name}.yml`);
  const text = fs.readFileSync(file, 'utf8');
  const permissions = [];
  let inPermissions = false;
  for (const line of text.split(/\r?\n/)) {
    if (/^permissions:/.test(line)) { inPermissions = true; continue; }
    if (inPermissions) {
      const m = line.match(/^-\s*(.+)$/);
      // A negated node is written as "- node:" followed by an indented "value: false"; it grants
      // nothing, so it is dropped rather than counted as a granted permission.
      if (m) permissions.push(m[1].trim().replace(/^'(.*)'$/, '$1').replace(/:$/, ''));
      else if (/^\s+value:\s*false\s*$/.test(line)) permissions.pop();
      else if (line.trim() !== '' && !line.trim().startsWith('#') && !/^\s/.test(line)) inPermissions = false;
    }
  }
  return permissions;
}

function resolveEffective(groups, name, seen = new Set()) {
  if (seen.has(name)) return new Set(); // guards a cyclic group.* parent loop
  seen.add(name);
  const perms = new Set();
  for (const node of groups.get(name) ?? []) {
    if (node.startsWith('group.')) {
      for (const p of resolveEffective(groups, node.slice('group.'.length), seen)) perms.add(p);
    } else if (!node.startsWith('weight.')) {
      perms.add(node);
    }
  }
  return perms;
}

function hasPermission(effective, node) {
  return effective.has(node) || effective.has('*');
}

const groupNames = ['default', 'mod', 'admin'];
let groups;
try {
  groups = new Map(groupNames.map((g) => [g, loadGroup(g)]));
  report('lp listgroups shows default, mod, admin', groupNames.every((g) => groups.has(g)));
} catch (e) {
  report('lp listgroups shows default, mod, admin', false, e.message);
  groups = new Map(groupNames.map((g) => [g, []]));
}

const modEffective = resolveEffective(groups, 'mod');
const adminEffective = resolveEffective(groups, 'admin');
report('lp group mod permission check tag.admin -> true', hasPermission(modEffective, 'tag.admin'));
report('lp group mod permission check essentials.tempban -> true', hasPermission(modEffective, 'essentials.tempban'));
report('lp group mod permission check names.admin -> not true', !hasPermission(modEffective, 'names.admin'));
report('lp group admin permission check names.admin -> true (via *)', hasPermission(adminEffective, 'names.admin'));

// --- Live RCON checks ----------------------------------------------------------------------------
const rcon = await Rcon.connect(localDir);

// Command ownership: compare the bare label against the owning plugin's own namespaced alias (must
// match - same command instance) and against essentials:<label> (must differ and be substantive -
// proves Essentials still answers under its own namespace but isn't the bare-label owner). HuskHomes
// and Names both reply asynchronously, so the bare/owner responses are typically empty strings, not
// "Unknown or incomplete command..." (vanilla's reply for a label nobody registered at all) - that
// distinction is the actual ownership signal, not the message content.
const UNKNOWN_COMMAND = /Unknown or incomplete command/;
async function checkOwnership(label, ownerNamespace) {
  const bare = await rcon.command(label);
  const owned = await rcon.command(`${ownerNamespace}:${label}`);
  const essentials = await rcon.command(`essentials:${label}`);
  const bareIsRegistered = !UNKNOWN_COMMAND.test(bare);
  const bareMatchesOwner = bare === owned;
  const essentialsIsSubstantive = essentials.length > 0 && !UNKNOWN_COMMAND.test(essentials);
  const essentialsDiffers = essentials !== bare;
  const pass = bareIsRegistered && bareMatchesOwner && essentialsIsSubstantive && essentialsDiffers;
  report(
    `/${label} resolves to ${ownerNamespace}, not Essentials`,
    pass,
    pass ? undefined : `bare=${JSON.stringify(bare)} owner=${JSON.stringify(owned)} essentials=${JSON.stringify(essentials)}`,
  );
}
await checkOwnership('home', 'huskhomes');
await checkOwnership('sethome', 'huskhomes');
await checkOwnership('tpa', 'huskhomes');
await checkOwnership('back', 'huskhomes');
await checkOwnership('nick', 'names');

// Gamerules set once per world by the server-rules datapack (see
// server/datapacks/server-rules/data/server_rules/function/load.mcfunction).
const sleepRule = await rcon.command('gamerule players_sleeping_percentage');
report('players_sleeping_percentage = 50', /\bset to 50\b/.test(sleepRule), sleepRule);
const fireRule = await rcon.command('gamerule fire_spread_radius_around_player');
report('fire_spread_radius_around_player = 0', /\bset to 0\b/.test(fireRule), fireRule);
const locatorRule = await rcon.command('gamerule locator_bar');
report('locator_bar = false', /\bset to false\b/.test(locatorRule), locatorRule);

// Datapacks: `datapack list`'s own chat component is truncated by the server itself past a certain
// length (confirmed live - it cuts off mid-list before every committed pack name fits, independent
// of RCON transport), so completeness is checked against the actual files under
// server/local/world/datapacks instead; `datapack list`'s enabled count is still cross-checked as a
// coarse live signal.
const datapackList = await rcon.command('datapack list');
const countMatch = datapackList.match(/There are (\d+) data pack\(s\) enabled/);
const enabledCount = countMatch ? Number(countMatch[1]) : 0;

// :server:assembleLocalServer writes this manifest with the exact filenames it placed into
// world/datapacks (committed server/datapacks/ entries plus the Stardust zips it derived from
// ServerArtifacts.kt's pinned URLs), so this check never needs its own copy of either list.
const manifestPath = path.join(localDir, 'datapacks.manifest.json');
let manifest;
try {
  manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
} catch (e) {
  throw new Error(`Could not read ${manifestPath} - run :server:assembleLocalServer first (${e.message})`);
}
const expectedPacks = [...manifest.bundled, ...manifest.stardust];
const actualFiles = new Set(fs.readdirSync(path.join(localDir, 'world/datapacks')));
const missingPacks = expectedPacks.filter((p) => !actualFiles.has(p));
report(
  `world/datapacks contains every committed pack plus all ${manifest.stardust.length} Stardust packs`,
  missingPacks.length === 0,
  missingPacks.length ? `missing: ${missingPacks.join(', ')}` : `${expectedPacks.length} packs on disk`,
);
report(
  'datapack list enabled count covers all of them (>= packs-on-disk + vanilla)',
  enabledCount >= expectedPacks.length + 1,
  datapackList,
);

// Per-tick NBT reads serialize the whole entity (a player's full inventory, backpack contents
// included) and were ~65% of the live server's tick on 2026-10-05. Tick-trigger advancements in
// our folder datapacks must not use nbt predicates, and eclipse-backpacks' tick must not select
// dropped items by nbt.
const packsDir = path.join(localDir, 'world/datapacks');
const nbtTickAdvancements = [];
for (const pack of fs.readdirSync(packsDir, { withFileTypes: true }).filter((d) => d.isDirectory())) {
  const files = fs.readdirSync(path.join(packsDir, pack.name), { recursive: true })
    .filter((f) => /[\\/]advancements?[\\/].*\.json$/.test(f));
  for (const file of files) {
    const text = fs.readFileSync(path.join(packsDir, pack.name, file), 'utf8');
    if (text.includes('"minecraft:tick"') && /"(minecraft:)?nbt"/.test(text)) nbtTickAdvancements.push(`${pack.name}/${file}`);
  }
}
report('no tick-trigger advancement checks player NBT', nbtTickAdvancements.length === 0, nbtTickAdvancements.join(', '));
const backpacksTick = fs.readFileSync(path.join(packsDir, 'eclipse-backpacks/data/backpacks/function/tick.mcfunction'), 'utf8');
report('eclipse-backpacks tick selects dropped items without nbt=', !/@e\[type=item,[^\]]*nbt=/.test(backpacksTick));

// `scoreboard objectives list` is truncated past ~200 objectives, so probe each one directly.
for (const objective of ['backpacks.summoned', 'backpacks.give_back']) {
  const res = await rcon.command(`scoreboard players set #check ${objective} 0`);
  report(`eclipse-backpacks objective ${objective} exists`, res.startsWith('Set '), res);
  await rcon.command(`scoreboard players reset #check ${objective}`);
}

// A thrown tier-0 small backpack (chain command block) upgrades to tier 1; a repeating command block
// at tier 0 matches no upgrade rule and must stay untouched.
const bpItem = (id, tier, tag) => `summon item 8 200 8 {NoGravity:1b,PickupDelay:32767,Tags:["${tag}"],Item:{id:"minecraft:${id}",count:1,components:{"minecraft:custom_data":{backpacks:{real_backpack:1b,tier:${tier},contents:{slots:3}}}}}}`;
const tierOf = (tag) => rcon.command(`data get entity @n[tag=${tag}] Item.components."minecraft:custom_data".backpacks.tier`);
await rcon.command('forceload add 0 0');
await rcon.command(bpItem('chain_command_block', 0, 'check.bp_upgrade'));
await rcon.command(bpItem('repeating_command_block', 0, 'check.bp_control'));
await sleep(1000);
const upgradedTier = await tierOf('check.bp_upgrade');
const controlTier = await tierOf('check.bp_control');
report('thrown tier-0 backpack upgrades to tier 1', /: 1$/.test(upgradedTier), upgradedTier);
report('non-matching thrown item is left alone', /: 0$/.test(controlTier), controlTier);
// minecraft:kill, since Essentials' /kill only targets players.
await rcon.command('minecraft:kill @e[tag=check.bp_upgrade]');
await rcon.command('minecraft:kill @e[tag=check.bp_control]');
await rcon.command('forceload remove 0 0');

// Chunky: confirm the dimension names it actually accepts (a bogus name gets the generic usage
// string back instead of "[Chunky] World changed to ...", so that's the accept/reject signal).
async function chunkyAccepts(world) {
  const res = await rcon.command(`chunky world ${world}`);
  return res.startsWith('[Chunky] World changed to');
}
for (const world of ['world', 'world_nether']) {
  report(`chunky world ${world} is a valid Chunky target`, await chunkyAccepts(world));
}

// Difficulty and plugin list.
const difficulty = await rcon.command('difficulty');
report('difficulty = normal', /normal/i.test(difficulty), difficulty);
const plugins = await rcon.command('plugins');
const expectedPlugins = [
  'Bolt', 'Chunky', 'DiscordSRV', 'Essentials', 'floodgate', 'Geyser-Spigot', 'HuskHomes',
  'LuckPerms', 'Names', 'packetevents', 'PvPManager', 'QuickShop-Hikari', 'SafeChests', 'Tag',
  'Towny', 'Vault', 'ViaBackwards', 'ViaVersion', 'voicechat',
];
const missingPlugins = expectedPlugins.filter((p) => !plugins.includes(p));
report('plugins lists every expected plugin', missingPlugins.length === 0, missingPlugins.join(', ') || plugins);

// Read after boot, not just after assemble: Towny rewrites its config on startup, so this proves
// the free-towns override survived that rewrite.
const townyConfig = fs.readFileSync(path.join(localDir, 'plugins/Towny/settings/config.yml'), 'utf8');
const paidTownyPrices = ['price_new_town', 'price_claim_townblock', 'price_outpost', 'price_town_upkeep',
  'price_new_nation', 'price_nation_upkeep', 'price_death']
  .filter((key) => !new RegExp(`^\\s+${key}: '0(\\.0)?'`, 'm').test(townyConfig));
report('Towny towns are free (creation, claims, upkeep, death cost all 0)', paidTownyPrices.length === 0,
  paidTownyPrices.join(', ') || 'all 0');
const closedWilderness = ['build', 'destroy', 'item_use', 'switch']
  .filter((what) => !new RegExp(`^\\s+unclaimed_zone_${what}: 'true'`, 'm').test(townyConfig));
report('Towny wilderness stays open (build, destroy, item use, switch)', closedWilderness.length === 0,
  closedWilderness.join(', ') || 'all open');
// The town_level limits are all 0, so the flat cap only applies while the level switch is off.
const bonusCap = townyConfig.match(/^\s+max_purchased_blocks: '(\d+)'/m)?.[1];
const bonusPerLevel = townyConfig.match(/^\s+max_purchased_blocks_uses_town_levels: '(\w+)'/m)?.[1];
report('Towny towns can buy up to 64 bonus chunks', bonusCap === '64' && bonusPerLevel === 'false',
  `max_purchased_blocks ${bonusCap}, uses_town_levels ${bonusPerLevel}`);
// Player TNT stays blown up; only mob/natural explosions (creepers, withers...) heal in the wild.
// The config line seeds new worlds, the per-world file is what an existing world actually uses.
const regenEntities = townyConfig.match(/wild_revert_on_mob_explosion:[\s\S]*?entities: (.*)/)?.[1] ?? '';
report('Towny default wild regen excludes TNT', !/\bTNT(_MINECART)?\b/.test(regenEntities), regenEntities);
const worldRegen = fs.readFileSync(path.join(localDir, 'plugins/Towny/data/worlds/world.txt'), 'utf8')
  .match(/^PlotManagementWildRegenEntities=(.*)$/m)?.[1] ?? '';
report('Towny world wild regen excludes TNT', !/\btnt(_minecart)?\b/i.test(worldRegen), worldRegen);
// Bed and respawn anchor bombs (mining ancient debris) stay blown up too: block explosions are only
// ever those two, always player-made, so their wild revert is off in every world.
const blockRevertDefault = townyConfig.match(/wild_revert_on_block_explosion:[\s\S]*?enabled: '(\w+)'/)?.[1];
report('Towny default wild regen leaves bed/anchor explosions', blockRevertDefault === 'false',
  `enabled ${blockRevertDefault}`);
const blockRevertWorlds = ['world', 'world_nether', 'world_the_end']
  .filter((world) => !/^usingPlotManagementWildRegenBlocks=false\s*$/m.test(
    fs.readFileSync(path.join(localDir, `plugins/Towny/data/worlds/${world}.txt`), 'utf8')));
report('Towny worlds leave bed/anchor explosions', blockRevertWorlds.length === 0,
  blockRevertWorlds.join(', ') || 'off in all 3');

await rcon.command('stop');
rcon.close();

console.log(failures ? `\n${failures} check(s) failed` : '\nAll checks passed');
process.exitCode = failures ? 1 : 0;
