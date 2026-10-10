import bedrock from 'bedrock-protocol';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { Rcon, readGeyserBedrockPort } from './lib.mjs';

// Bedrock players see the Dragon Balls summon dragon as its 3D model: a headless Bedrock client on
// server/local's Geyser (its configured bedrock port; login validation is off there only, see
// bedrock.mjs) watches a Shenron
// summon and checks what Geyser sends it. DragonBalls registers one custom entity per model part
// with Geyser (bedrock/GeyserDragons) plus a Bedrock pack, and swaps each blank text display
// carrier it spawns into the part's entity, so the client must be offered the pack, know the
// claudesmp:dragon_* identifiers, receive 39 of them (Shenron's head, 37 segments, tail) sized and
// posed by properties, never an Ender Dragon, see them move and turn, and see them go on dismiss.
// Untestable here: how the models look in a real Bedrock client.
//
// Needs 7 placed Overworld balls, which only a player's block place records, so the suite seeds
// them in plugins/DragonBalls/balls.yml while server/local is STOPPED:
//   node bedrock-dragons.mjs --seed     (server/local stopped; backs balls.yml up first)
//   ...boot server/local...
//   node bedrock-dragons.mjs            (server/local running)
//   node bedrock-dragons.mjs --restore  (server/local stopped again; puts the old balls.yml back)

const here = path.dirname(fileURLToPath(import.meta.url));
// BEDROCK_SERVER_DIR points the suite at another Geyser server folder (a private test copy).
const localDir = process.env.BEDROCK_SERVER_DIR ?? path.join(here, '..', 'server', 'local');
const ballsFile = path.join(localDir, 'plugins', 'DragonBalls', 'balls.yml');
const backupFile = `${ballsFile}.bedrock-dragons-backup`;
const logFile = path.join(localDir, 'logs', 'latest.log');
const PACK_UUID = '6f1d2b6e-3c1a-4c55-9d0e-6b8f0c2a7d41';
// Balls float in the sky over spawn so no terrain is needed: the summon only reads the registry.
const BALLS = { x: 0, y: 200, z: 0 };

async function rconUp() {
  try {
    (await Rcon.connect(localDir)).close();
    return true;
  } catch {
    return false;
  }
}

if (process.argv.includes('--seed') || process.argv.includes('--restore')) {
  if (await rconUp()) {
    console.error('server/local is running; stop it first (the plugin rewrites balls.yml on shutdown)');
    process.exit(1);
  }
  if (process.argv.includes('--restore')) {
    if (fs.existsSync(backupFile)) fs.renameSync(backupFile, ballsFile);
    console.log('balls.yml restored');
    process.exit(0);
  }
  if (!fs.existsSync(backupFile) && fs.existsSync(ballsFile)) fs.copyFileSync(ballsFile, backupFile);
  let yml = 'balls:\n';
  // A summon needs all 7 within summon.radius (2, Chebyshev) of the first: a 3x3 patch.
  for (let star = 1; star <= 7; star++) {
    const x = BALLS.x + ((star - 1) % 3);
    const z = BALLS.z + Math.floor((star - 1) / 3);
    yml += `  '${star}':\n    state: PLACED\n    stone: false\n    world: world\n`
      + `    x: ${x}.0\n    y: ${BALLS.y}.0\n    z: ${z}.0\n    heldSince: 0\n    lastSeen: 0\n`;
  }
  fs.writeFileSync(ballsFile, yml);
  console.log(`seeded 7 placed balls at y ${BALLS.y} over spawn; boot server/local, then run without --seed`);
  process.exit(0);
}

let failures = 0;
function report(name, pass, detail) {
  console.log(`${pass ? 'PASS' : 'FAIL'} ${name}${detail ? ` - ${detail}` : ''}`);
  if (!pass) failures++;
}
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
async function waitFor(check, ms) {
  const end = Date.now() + ms;
  while (Date.now() < end) {
    if (check()) return true;
    await sleep(200);
  }
  return check();
}

const USERNAME = 'BedrockE2E';
const FLOODGATE_NAME = `.${USERNAME}`;
const ZERO_UUID = '00000000-0000-0000-0000-000000000000';
for (const p of [
  path.join(localDir, 'world', 'playerdata', `${ZERO_UUID}.dat`),
  path.join(localDir, 'world', 'playerdata', `${ZERO_UUID}.dat_old`),
]) {
  fs.rmSync(p, { force: true });
}

const packsOffered = [];
let identifiers = '';
const dragons = new Map(); // runtime id -> add_entity packet
const enderDragons = [];
const moved = new Set();
const turned = new Set();
const removed = new Set();
let kickReason = null;

const bedrockPort = readGeyserBedrockPort(localDir);
const client = bedrock.createClient({ host: '127.0.0.1', port: bedrockPort, username: USERNAME, offline: true });
client.on('kick', (reason) => { kickReason = reason; });
client.on('error', (err) => console.error('[bedrock client error]', err.message));
client.on('resource_packs_info', (p) => {
  for (const pack of [...(p.texture_packs ?? []), ...(p.resource_packs ?? [])]) packsOffered.push(pack.uuid);
});
client.on('available_entity_identifiers', (p) => { identifiers = JSON.stringify(p.nbt ?? p); });
client.on('add_entity', (p) => {
  if (String(p.entity_type).startsWith('claudesmp:dragon_')) dragons.set(String(p.runtime_id), p);
  if (p.entity_type === 'minecraft:ender_dragon') enderDragons.push(p);
});
for (const name of ['move_entity', 'move_entity_delta']) {
  client.on(name, (p) => moved.add(String(p.runtime_entity_id ?? p.runtime_id)));
}
client.on('set_entity_data', (p) => {
  const props = p.properties ?? {};
  if ((props.floats?.length ?? 0) > 0) turned.add(String(p.runtime_entity_id));
});
client.on('remove_entity', (p) => removed.add(String(p.entity_id_self ?? p.runtime_entity_id ?? p.entity_unique_id)));

await new Promise((resolve, reject) => {
  const timer = setTimeout(() => reject(new Error('bot did not spawn within 30s')), 30_000);
  client.once('spawn', () => { clearTimeout(timer); resolve(); });
  client.once('close', () => { clearTimeout(timer); reject(new Error(`closed before spawn (kick: ${JSON.stringify(kickReason)})`)); });
});

const rcon = await Rcon.connect(localDir);
const log = fs.readFileSync(logFile, 'utf8');
report('DragonBalls registered the models with Geyser at boot',
  /Bedrock players see the summon dragons' models \(10 Geyser custom entities\)/.test(log));
// bedrock-protocol reads a pack uuid's two 64-bit halves byte-reversed.
const swapped = (uuid) => {
  const hex = uuid.replaceAll('-', '');
  const flip = (h) => h.match(/../g).reverse().join('');
  const s = flip(hex.slice(0, 16)) + flip(hex.slice(16));
  return `${s.slice(0, 8)}-${s.slice(8, 12)}-${s.slice(12, 16)}-${s.slice(16, 20)}-${s.slice(20)}`;
};
report('the client was offered the Bedrock dragon pack',
  packsOffered.includes(PACK_UUID) || packsOffered.includes(swapped(PACK_UUID)), JSON.stringify(packsOffered));
report('the client knows claudesmp:dragon_shenron_head', identifiers.includes('claudesmp:dragon_shenron_head'));

try {
  await rcon.command('shenron dismiss').catch(() => {});
  await rcon.command('dragonballs wake overworld');
  await rcon.command(`minecraft:forceload add ${BALLS.x - 16} ${BALLS.z - 16} ${BALLS.x + 16} ${BALLS.z + 16}`);
  await rcon.command(`minecraft:fill ${BALLS.x - 1} ${BALLS.y - 2} ${BALLS.z + 3} ${BALLS.x + 1} ${BALLS.y - 2} ${BALLS.z + 5} minecraft:stone`);
  await rcon.command(`minecraft:tp ${FLOODGATE_NAME} ${BALLS.x} ${BALLS.y - 1} ${BALLS.z + 4}`);
  await sleep(2000);

  const reply = await rcon.command(`shenron summon ${FLOODGATE_NAME} shenron overworld`);
  report('the summon starts', !/Cannot summon|not online|No placed|Unknown/.test(reply), reply.trim());

  await waitFor(() => dragons.size >= 39, 15_000);
  const parts = [...dragons.values()].map((p) => p.entity_type);
  const count = (id) => parts.filter((t) => t === id).length;
  report('Bedrock gets all 39 Shenron parts as custom entities', dragons.size === 39, `${dragons.size}: ${[...new Set(parts)].join(', ')}`);
  report('one head, one tail, two legged segments', count('claudesmp:dragon_shenron_head') === 1
    && count('claudesmp:dragon_shenron_tail') === 1 && count('claudesmp:dragon_shenron_body_legs') === 2);
  report('no Ender Dragon stand-in reaches Bedrock', enderDragons.length === 0);

  if (dragons.size === 0) throw new Error('no dragon parts reached Bedrock; nothing further to check');
  const sample = dragons.values().next().value;
  const props = sample.properties ?? {};
  report('each part spawns with its skin and pose properties',
    [...dragons.values()].every((p) => (p.properties?.ints?.length ?? 0) === 1 && (p.properties?.floats?.length ?? 0) === 2),
    JSON.stringify(props));
  report('the Overworld skin is index 0', [...dragons.values()].every((p) => p.properties?.ints?.[0]?.value === 0));
  const scaleOf = (p) => (p.metadata ?? []).find((m) => m.key === 'scale')?.value;
  report('every part has a real size (Geyser draws a text display at scale 0)',
    [...dragons.values()].every((p) => scaleOf(p) > 0.5), `head scale ${scaleOf([...dragons.values()].find((p) => p.entity_type.endsWith('head')))}`);
  const above = [...dragons.values()].every((p) => p.position.y > BALLS.y && Math.hypot(p.position.x - BALLS.x, p.position.z - BALLS.z) < 40);
  report('the parts float above the balls', above, `sample ${JSON.stringify(sample?.position)}`);

  await sleep(3000);
  const ids = [...dragons.keys()];
  report('the parts move', ids.filter((id) => moved.has(id)).length >= 30, `${ids.filter((id) => moved.has(id)).length}/39`);
  report('the parts turn (pose property updates)', ids.filter((id) => turned.has(id)).length >= 30, `${ids.filter((id) => turned.has(id)).length}/39`);

  await rcon.command('shenron dismiss');
  await waitFor(() => ids.every((id) => removed.has(id)), 10_000);
  report('dismissing removes every part from Bedrock', ids.every((id) => removed.has(id)), `${ids.filter((id) => removed.has(id)).length}/39`);
  report('the bot was never kicked', kickReason === null, JSON.stringify(kickReason));
} finally {
  await rcon.command(`minecraft:forceload remove ${BALLS.x - 16} ${BALLS.z - 16} ${BALLS.x + 16} ${BALLS.z + 16}`).catch(() => {});
  await rcon.command(`kick ${FLOODGATE_NAME} e2e bedrock dragons done`).catch(() => {});
  rcon.close();
  client.close();
}

console.log(failures ? `\n${failures} check(s) failed` : '\nAll checks passed');
process.exit(failures ? 1 : 0);
