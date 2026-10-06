import { Vec3 } from 'vec3';
import {
  Rcon, assert, buildPlatform, createBot, quit, randomOrigin, sleep, teleport,
} from './lib.mjs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// Shared setup and helpers for the two Dragon Balls suites: dragonballs.mjs (ball lifecycle) and
// dragonballs-summon.mjs (radar, summon and the stone phase). Rules and the console protocol:
// docs/dragonballs.md.
const here = path.dirname(fileURLToPath(import.meta.url));
export const configPath = path.join(here, '..', 'testserver', 'run-e2e', 'plugins', 'DragonBalls', 'config.yml');
export const wishesPath = path.join(here, '..', 'testserver', 'run-e2e', 'plugins', 'DragonBalls', 'wishes.jsonl');
export const logPath = path.join(here, '..', 'testserver', 'run-e2e', 'logs', 'latest.log');

export const PLATFORM_Y = 200;
// All 7 within Chebyshev radius 1 of (BX, BZ) - well inside summon.radius (default 2). The bots
// stand one block further out (BZ + 2) so their own hitbox never occupies a column they're about
// to place into (BlockPlaceEvent is refused into an occupied space) while staying within reach.
const OFFSETS = [[0, 0], [1, 0], [0, 1], [1, 1], [-1, 0], [0, -1], [-1, -1]];
export const HUNTER = 'DBHunter';
export const WITNESS = 'DBWitness';
// Default station.radar-sign coords (config.yml) - the e2e world has no spawn-market build, so a
// small floor + sign are carved out here, same spirit as market.mjs's "no spawn market" comment.
export const SIGN = { x: 413, y: 116, z: -873 };

/**
 * Connects RCON, builds a fresh platform and logs both bots in on it with empty inventories.
 * Returns the suite's state plus every helper bound to it, and `finish(error)` for the cleanup.
 */
export async function openSuite(suiteName) {
  const rcon = await Rcon.connect();
  const { bx: BX, bz: BZ } = randomOrigin();
  const STAND = { x: BX, y: PLATFORM_Y + 1, z: BZ + 2 };
  const bots = {};
  const results = [];

  // DB_ONLY=<regex> runs just the matching steps, for rerunning one scenario without the whole suite.
  const only = process.env.DB_ONLY ? new RegExp(process.env.DB_ONLY) : null;

  async function step(name, fn) {
    if (only && !only.test(name)) return;
    await fn();
    results.push(name);
    console.log(`PASS ${name}`);
  }

  const plain = (s) => s.replace(/§./g, '');

  function ballOffset(index) {
    const [dx, dz] = OFFSETS[index];
    return { x: BX + dx, y: PLATFORM_Y, z: BZ + dz };
  }

  /** Parses `dragonballs status`'s one-line-per-star output into {state, world, x, y, z, holder, stone}. */
  function parseStatus(text) {
    const out = {};
    for (const line of plain(text).split(/\r?\n/)) {
      const m = line.match(/^(?<star>\d)-star: (?:placed at (?<pw>\S+) (?<px>-?\d+),(?<py>-?\d+),(?<pz>-?\d+)|dropped at (?<dw>\S+) (?<dx>-?\d+),(?<dy>-?\d+),(?<dz>-?\d+)|held by (?<holder>\S+))(?<stone>\s*\(stone\))?/);
      if (!m) continue;
      const g = m.groups;
      const star = Number(g.star);
      const stone = Boolean(g.stone);
      if (g.pw) out[star] = { state: 'placed', world: g.pw, x: Number(g.px), y: Number(g.py), z: Number(g.pz), stone };
      else if (g.dw) out[star] = { state: 'dropped', world: g.dw, x: Number(g.dx), y: Number(g.dy), z: Number(g.dz), stone };
      else out[star] = { state: 'held', holder: g.holder, stone };
    }
    return out;
  }

  async function status() {
    return parseStatus(await rcon.command('dragonballs status'));
  }

  // The attribute command prints the stored double, e.g. -0.05000000074505806, never a clean -0.05.
  function near(actual, expected) {
    return actual !== null && Math.abs(actual - expected) < 1e-6;
  }

  async function speedModifier(playerName) {
    const out = plain(await rcon.command(`attribute ${playerName} minecraft:movement_speed modifier value get dragonballs:carrying-slow`));
    if (process.env.DB_DEBUG) console.log('speed raw:', JSON.stringify(out));
    const m = out.match(/-?\d+\.?\d*/);
    return m ? Number(m[0]) : null;
  }

  async function balance(playerName) {
    const out = plain(await rcon.command(`balance ${playerName}`));
    const m = out.match(/\$([\d,]+(?:\.\d+)?)/);
    assert(m, `balance readable (got ${out.trim()})`);
    return Number(m[1].replace(/,/g, ''));
  }

  /** Hunter stands at STAND (one block outside the cluster, within reach of all 7 columns) and
   * places `star` (must already be in hand) on top of the floor block at ballOffset(index). */
  async function placeBallAt(bot, index) {
    const { x, y, z } = ballOffset(index);
    const ref = bot.blockAt(new Vec3(x, y, z));
    await bot.placeBlock(ref, new Vec3(0, 1, 0));
  }

  // A toss flies about 4 blocks, and each re-pickup leaves the bot where the ball landed, so tossing
  // from wherever it stands walks it off the 16x16 platform within three tosses (the ball then falls
  // to the terrain far below). Every toss starts from STAND, aimed at the platform centre.
  async function tossTowardCentre(bot) {
    await teleport(rcon, bot, STAND.x, STAND.y, STAND.z);
    await bot.lookAt(new Vec3(BX + 0.5, PLATFORM_Y + 1, BZ + 0.5), true);
    const ball = bot.inventory.items().find((i) => i.name === 'player_head');
    await bot.tossStack(ball);
  }

  // Other suites leave item entities lying around the e2e world, so an unbounded nearest-item
  // selector can land on one of those instead of the ball that was just tossed.
  function ballEntityNear(loc) {
    return `@e[type=minecraft:item,limit=1,sort=nearest,x=${loc.x},y=${loc.y},z=${loc.z},distance=..6]`;
  }

  async function openWindow(bot, block) {
    const opened = new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('window never opened')), 3_000);
      bot.once('windowOpen', (w) => {
        clearTimeout(timer);
        resolve(w);
      });
    });
    await bot.activateBlock(block);
    return opened;
  }

  function shortenSummonConfig(text) {
    assert(/^summon:/m.test(text), 'DragonBalls config has a summon: section (delete a stale run-e2e config.yml and restart)');
    const set = (t, key, value) => t.replace(new RegExp(`^(  ${key}:).*$`, 'm'), `$1 ${value}`);
    // Long enough for the judge commands below, short enough that a broken dismiss still ends the run.
    let out = set(text, 'idle-timeout-seconds', 60);
    out = set(out, 'summoner-offline-seconds', 2);
    return out;
  }

  async function collectAllSeven(bot, playerName) {
    for (let star = 1; star <= 7; star++) {
      await rcon.command(`dragonballs give ${playerName} ${star}`);
    }
    await sleep(300);
  }

  async function finish(error) {
    if (error) {
      console.error(`FAIL after ${results.length} passed: ${error.message}\n${error.stack}`);
      process.exitCode = 1;
    } else {
      console.log(`\n${suiteName} e2e: ${results.length} passed`);
    }
    await rcon.command(`minecraft:clear ${HUNTER}`).catch(() => {});
    await rcon.command(`minecraft:clear ${WITNESS}`).catch(() => {});
    await quit(...Object.values(bots));
    rcon.close();
  }

  const suite = {
    rcon, BX, BZ, STAND, step, plain, ballOffset, status, near, speedModifier, balance, placeBallAt,
    tossTowardCentre, ballEntityNear, openWindow, shortenSummonConfig, collectAllSeven, finish,
  };
  try {
    await buildPlatform(rcon, BX, BZ, PLATFORM_Y);
    suite.hunter = bots[HUNTER] = await createBot(HUNTER);
    suite.witness = bots[WITNESS] = await createBot(WITNESS);
    // /clear is a no-op against an offline player, so this must run after both bots connect - a
    // fresh e2e run otherwise inherits whatever junk a previous run (or manual probing) left behind.
    await rcon.command(`minecraft:clear ${HUNTER}`);
    await rcon.command(`minecraft:clear ${WITNESS}`);
    await rcon.command(`minecraft:gamemode survival ${HUNTER}`);
    await rcon.command(`minecraft:gamemode survival ${WITNESS}`);
    await teleport(rcon, suite.hunter, STAND.x, STAND.y, STAND.z);
    await teleport(rcon, suite.witness, STAND.x + 3, STAND.y, STAND.z);
  } catch (error) {
    await finish(error);
    throw error;
  }
  return suite;
}
