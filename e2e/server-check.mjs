import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { Rcon } from './lib.mjs';

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

// Gamerules and world border, set once per world by the server-rules datapack (see
// server/datapacks/server-rules/data/server_rules/function/load.mcfunction).
const sleepRule = await rcon.command('gamerule players_sleeping_percentage');
report('players_sleeping_percentage = 50', /\bset to 50\b/.test(sleepRule), sleepRule);
const fireRule = await rcon.command('gamerule fire_spread_radius_around_player');
report('fire_spread_radius_around_player = 0', /\bset to 0\b/.test(fireRule), fireRule);
const overworldBorder = await rcon.command('worldborder get');
report('overworld worldborder = 10000', /\b10000\b/.test(overworldBorder), overworldBorder);
const netherBorder = await rcon.command('execute in minecraft:the_nether run worldborder get');
report('nether worldborder = 2500', /\b2500\b/.test(netherBorder), netherBorder);

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

await rcon.command('stop');
rcon.close();

console.log(failures ? `\n${failures} check(s) failed` : '\nAll checks passed');
process.exitCode = failures ? 1 : 0;
