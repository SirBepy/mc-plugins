import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  Rcon, assert, buildPlatform, createBot, quit, randomOrigin, releasePlatform, sleep, teleport,
  waitFor, waitForLine,
} from './lib.mjs';

// AFK handling with second-scale thresholds: the live Tag config is rewritten for this suite and
// restored afterwards, then applied with /tag reload. AfkA (IT) and AfkB stand still; AfkC keeps
// turning its head, so it is the only player the idle IT can be passed to.
const here = path.dirname(fileURLToPath(import.meta.url));
const configPath = path.join(here, '..', 'testserver', 'run-e2e', 'plugins', 'Tag', 'config.yml');
const THRESHOLD = 3;
const PASS_AFTER = 8;
const WARNING = 3;

const PLATFORM_Y = 200;
const { bx: BX, bz: BZ } = randomOrigin();
const rcon = await Rcon.connect();
const bots = {};
const original = fs.readFileSync(configPath, 'utf8');

const results = [];
async function step(name, fn) {
  await fn();
  results.push(name);
  console.log(`PASS ${name}`);
}

async function whoIsIt() {
  return (await rcon.command('tag who')).replace(/§./g, '').trim();
}

function withShortAfk(text) {
  assert(/^afk:/m.test(text), 'Tag config has an afk: section (delete a stale run-e2e Tag config.yml and restart)');
  const set = (t, key, value) => t.replace(new RegExp(`^(  ${key}:).*$`, 'm'), `$1 ${value}`);
  let out = set(text, 'enabled', 'true');
  out = set(out, 'threshold-seconds', THRESHOLD);
  out = set(out, 'auto-pass', 'true');
  out = set(out, 'it-pass-after-seconds', PASS_AFTER);
  out = set(out, 'it-pass-warning-seconds', WARNING);
  out = set(out, 'check-interval-seconds', 1);
  return out;
}

let fidget;
try {
  fs.writeFileSync(configPath, withShortAfk(original));
  await rcon.command('tag reload');
  await buildPlatform(rcon, BX, BZ, PLATFORM_Y);
  await rcon.command('tag clear');

  for (const [name, dx] of [['AfkA', 0], ['AfkB', 2], ['AfkC', 4]]) {
    const bot = await createBot(name);
    bots[name] = bot;
    await rcon.command(`minecraft:gamemode survival ${name}`);
    await rcon.command(`minecraft:clear ${name}`);
    await teleport(rcon, bot, BX + dx, PLATFORM_Y + 1, BZ);
    bot.chat('/tag optin');
  }
  const { AfkA, AfkB, AfkC } = bots;
  let yaw = 0;
  fidget = setInterval(() => {
    yaw = (yaw + 0.6) % (Math.PI * 2);
    AfkC.look(yaw, 0, true).catch(() => {});
  }, 400);
  // Joining and teleporting three bots takes several seconds, long enough for AfkA to already be
  // past the warning point; a head turn restarts both idle clocks right before AfkA becomes IT.
  await AfkA.look(1, 0, true);
  await AfkB.look(1, 0, true);
  await sleep(1500);

  const markA = AfkA.lines.length;
  const markB = AfkB.lines.length;
  const markC = AfkC.lines.length;
  await rcon.command('tag set AfkA');

  await step('idle IT gets a one-time warning before the pass', async () => {
    await waitForLine(AfkA, /idle as IT/, (PASS_AFTER + 5) * 1000, markA);
    assert(!AfkB.lines.slice(markB).some((l) => /idle as IT/.test(l)), 'only IT is warned');
  });

  await step('idle IT passes to the only active player, not the idle one', async () => {
    await waitForLine(AfkC, /AfkA was away too long, IT passed to AfkC/, (PASS_AFTER + 5) * 1000, markC);
    assert((await whoIsIt()).includes('AfkC is IT'), 'AfkC is IT');
    assert(AfkA.lines.slice(markA).filter((l) => /idle as IT/.test(l)).length === 1, 'warning sent once');
  });

  await step('an AFK player can still be tagged', async () => {
    const target = AfkC.players.AfkB?.entity;
    assert(target, 'AfkC can see AfkB');
    const mark = AfkA.lines.length;
    await AfkC.lookAt(target.position.offset(0, 1.6, 0), true);
    AfkC.attack(target);
    await waitForLine(AfkA, /AfkC tagged AfkB/, 5_000, mark);
    assert((await whoIsIt()).includes('AfkB is IT'), 'AfkB is IT');
  });

  console.log(`\nAFK e2e: ${results.length} passed`);
} catch (error) {
  console.error(`FAIL after ${results.length} passed: ${error.message}`);
  process.exitCode = 1;
} finally {
  clearInterval(fidget);
  fs.writeFileSync(configPath, original);
  await rcon.command('tag reload').catch(() => {});
  await rcon.command('tag clear').catch(() => {});
  await releasePlatform(rcon, BX, BZ);
  await quit(...Object.values(bots));
  rcon.close();
}
