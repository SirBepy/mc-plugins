import bedrock from 'bedrock-protocol';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { Rcon, readGeyserBedrockPort } from './lib.mjs';

// Headless Bedrock client against server/local's Geyser listener (19132). Local-test-only: Geyser's
// Xbox Live login check is disabled ONLY in server/local's assembled config (see
// server/build.gradle.kts applyLocalGeyserOverrides), never in server/config, so an unauthenticated
// bedrock-protocol client can log in here. This exists to catch the exact failure class seen before
// the Geyser preview-build pin: a Java-protocol mismatch on the Geyser<->Paper link kicking the
// player for "Invalid move player packet" once it starts sending movement.

const here = path.dirname(fileURLToPath(import.meta.url));
const localDir = path.join(here, '..', 'server', 'local');
const logFile = path.join(localDir, 'logs', 'latest.log');

let failures = 0;
function report(name, pass, detail) {
  console.log(`${pass ? 'PASS' : 'FAIL'} ${name}${detail ? ` - ${detail}` : ''}`);
  if (!pass) failures++;
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

const USERNAME = 'BedrockE2E';
// Floodgate's default username-prefix (server/config/plugins/floodgate/config.yml), so the Java
// server (and RCON) sees Bedrock players under this name instead of the raw Bedrock gamertag.
const FLOODGATE_NAME = `.${USERNAME}`;
// Floodgate assigns every unauthenticated/offline Bedrock login the same zero UUID (no real XUID to
// derive one from), so repeated runs of this script all reuse the same Java player-data file.
// Without clearing it, a prior run's end-of-test state (position, velocity) carries into the next
// one and can trip vanilla's "moved too quickly"/flying checks for reasons that have nothing to do
// with Geyser - wiping it keeps each run equivalent to a brand-new player's first join.
const ZERO_UUID = '00000000-0000-0000-0000-000000000000';
// Paper 26.3 moved per-player data from world/playerdata/ to world/players/data/ (confirmed by
// listing server/local/world/players/data/ this session); the old path silently matched nothing,
// so this wipe was a no-op and every repeated run inherited the previous run's end-of-test
// position, which is what actually tripped vanilla's floating-too-long/flying kick on a second
// run, not a Geyser regression.
for (const p of [
  path.join(localDir, 'world', 'players', 'data', `${ZERO_UUID}.dat`),
  path.join(localDir, 'world', 'players', 'data', `${ZERO_UUID}.dat_old`),
  path.join(localDir, 'plugins', 'Essentials', 'userdata', `${ZERO_UUID}.yml`),
]) {
  fs.rmSync(p, { force: true });
}

const bedrockPort = readGeyserBedrockPort(localDir);

const client = bedrock.createClient({
  host: '127.0.0.1',
  port: bedrockPort,
  username: USERNAME,
  offline: true,
});

let kickReason = null;
let tick = 0n;
function authInput(position, delta) {
  const packet = {
    pitch: 0,
    yaw: 0,
    position,
    move_vector: { x: 1, z: 0 },
    head_yaw: 0,
    input_data: [],
    input_mode: 'mouse',
    play_mode: 'normal',
    interaction_model: 'touch',
    interact_rotation: { x: 0, z: 0 },
    tick,
    delta,
    analogue_move_vector: { x: 1, z: 0 },
    camera_orientation: { x: 0, y: 0, z: 1 },
    raw_move_vector: { x: 1, z: 0 },
  };
  tick += 1n;
  return packet;
}

// start_game.player_position is just a placeholder (observed: a fixed {0, 69, 0}, not the real
// spot) - the actual spawn position arrives moments later as a server-bound "move_player" packet
// (mode "reset" then "teleport"). Geyser's BedrockMovePlayer treats that as a pending teleport and
// drops every player_auth_input (never forwards position to the Java server) until one arrives whose
// position matches it, so movement below must be computed relative to this, not to the RCON baseline
// (which uses the Java-side feet position, a different offset).
let lastKnownPosition = null;
const textsReceived = [];
client.on('text', (packet) => { textsReceived.push(packet.message ?? ''); });
client.on('kick', (reason) => { kickReason = reason; });
client.on('error', (err) => console.error('[bedrock client error]', err.message));
client.on('move_player', (packet) => { lastKnownPosition = packet.position; });

async function waitForSpawn() {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('bot did not spawn within 30s')), 30_000);
    client.once('spawn', () => { clearTimeout(timer); resolve(); });
    client.once('close', () => { clearTimeout(timer); reject(new Error(`connection closed before spawn (kick: ${JSON.stringify(kickReason)})`)); });
  });
}

try {
  await waitForSpawn();
  report('bot joins and spawns (start_game + play_status player_spawn)', true);
} catch (e) {
  report('bot joins and spawns (start_game + play_status player_spawn)', false, e.message);
  console.log(`\n${failures} check(s) failed`);
  process.exitCode = 1;
  process.exit(1);
}

const rcon = await Rcon.connect(localDir);

// --- the Java server sees the player, under Floodgate's prefixed name ---
const listOutput = await rcon.command('list');
report(`RCON "list" shows ${FLOODGATE_NAME}`, listOutput.includes(FLOODGATE_NAME), listOutput);

// --- chat relayed from Bedrock to the Java server ---
const chatMessage = `bedrock-e2e-chat-${Date.now()}`;
client.queue('text', {
  type: 'chat',
  // category is the actual on-the-wire union discriminant (0 message_only / 1 authored / 2
  // parameters); it must agree with "type" (chat/whisper/announcement => authored) or the real
  // server reads the wrong branch - it read our source_name as the whole message when this was
  // wrongly left at "message_only".
  category: 'authored',
  needs_translation: false,
  source_name: USERNAME,
  xuid: '',
  platform_chat_id: '',
  has_filtered_message: false,
  message: chatMessage,
});
await sleep(2000);
const logAfterChat = fs.readFileSync(logFile, 'utf8');
report('server log recorded the bot chat message', logAfterChat.includes(chatMessage));
// Names' Bedrock tag: the chat line comes back as "[BE] .BedrockE2E: <message>" (colour codes stripped).
const echoed = textsReceived.map((t) => t.replace(/§./g, '')).find((t) => t.includes(chatMessage));
report('chat shows [BE] in front of the Bedrock player\'s name', echoed?.startsWith(`[BE] ${FLOODGATE_NAME}`), echoed);

// --- movement: a few blocks of player_auth_input packets, Geyser's server-authoritative movement
// packet (replaces MovePlayer client->server as of StartGame's ServerAuthoritativeMovementMode) ---
async function getPos() {
  const res = await rcon.command(`data get entity ${FLOODGATE_NAME} Pos`);
  const m = res.match(/\[(-?[\d.]+)d,\s*(-?[\d.]+)d,\s*(-?[\d.]+)d\]/);
  if (!m) throw new Error(`could not parse position from RCON reply: ${res}`);
  return { x: Number(m[1]), y: Number(m[2]), z: Number(m[3]) };
}

let moveError = null;
let start;
let end;
try {
  // The server-bound move_player teleport can arrive a little after 'spawn' fires.
  for (let i = 0; i < 20 && !lastKnownPosition; i++) await sleep(100);
  if (!lastKnownPosition) throw new Error('never received a move_player packet with the spawn position');
  const spawnPosition = lastKnownPosition;
  start = await getPos();

  // Geyser's teleport-confirmation entry for the spawn position isn't necessarily live the instant
  // 'spawn' fires (it can be created a little later, once chunks finish loading), and it only
  // forgives 20 unconfirmed move packets (TeleportCache.RESEND_THRESHOLD) before re-sending the
  // teleport and ignoring movement in the meantime. So keep echoing the unchanged spawn position for
  // longer than that window, instead of a single packet, so one of them is guaranteed to land after
  // the entry exists and inside its forgiveness window.
  for (let i = 0; i < 25; i++) {
    client.queue('player_auth_input', authInput(spawnPosition, { x: 0, y: 0, z: 0 }));
    await sleep(50);
  }

  // ~4.3 blocks/sec, vanilla walking speed, to stay under any server-side anti-cheat speed check -
  // this test is only about the Geyser<->Java protocol link surviving movement, not speed limits.
  const STEP = 0.2;
  const TICKS = 30;
  let pos = { ...spawnPosition };
  for (let i = 0; i < TICKS; i++) {
    pos = { x: pos.x + STEP, y: pos.y, z: pos.z };
    client.queue('player_auth_input', authInput(pos, { x: STEP, y: 0, z: 0 }));
    await sleep(50);
  }
  await sleep(1500);
  end = await getPos();
} catch (e) {
  moveError = e;
}

report('bot was not kicked while sending movement (no "Invalid move player packet")', kickReason === null, kickReason ? JSON.stringify(kickReason) : undefined);

if (moveError) {
  report('server-side position changed to follow the movement', false, moveError.message);
} else {
  const moved = Math.hypot(end.x - start.x, end.y - start.y, end.z - start.z);
  report('server-side position changed to follow the movement', moved > 3, `start=${JSON.stringify(start)} end=${JSON.stringify(end)} moved=${moved.toFixed(2)} blocks`);
}

await rcon.command(`kick ${FLOODGATE_NAME} e2e bedrock test done`).catch(() => {});
rcon.close();
client.close();

console.log(failures ? `\n${failures} check(s) failed` : '\nAll checks passed');
process.exitCode = failures ? 1 : 0;
