import { Vec3 } from 'vec3';
import {
  Rcon, assert, buildPlatform, createBot, quit, randomOrigin, releasePlatform, runDir, sleep, teleport, waitFor,
} from './lib.mjs';
import path from 'node:path';

// Shared setup and helpers for the three Dragon Balls suites: dragonballs.mjs (ball lifecycle),
// dragonballs-summon.mjs (radar, summon and the stone phase, overworld set only) and
// dragonballs-sets.mjs (the Nether/End sets, cross-dimension guard, and the structure scatter
// check). Rules and the console protocol: docs/dragonballs.md.
// Follows lib.mjs's own E2E_RUN_DIR override so a private-server run reads the config and log it
// actually booted, not the checkout-wide default.
export const configPath = path.join(runDir, 'plugins', 'DragonBalls', 'config.yml');
export const wishesPath = path.join(runDir, 'plugins', 'DragonBalls', 'wishes.jsonl');
export const logPath = path.join(runDir, 'logs', 'latest.log');

export const PLATFORM_Y = 200;
// All 7 within Chebyshev radius 1 of (BX, BZ) - well inside summon.radius (default 2). The bots
// stand one block further out (BZ + 2) so their own hitbox never occupies a column they're about
// to place into (BlockPlaceEvent is refused into an occupied space) while staying within reach.
export const OFFSETS = [[0, 0], [1, 0], [0, 1], [1, 1], [-1, 0], [0, -1], [-1, -1]];
export const HUNTER = 'DBHunter';
export const WITNESS = 'DBWitness';
// station.radar-signs.<set> coords (config.yml); the e2e world has no spawn-market build, so a
// small floor + sign(s) are carved out here, same spirit as market.mjs's "no spawn market" comment.
export const SIGNS = {
  overworld: { x: 422, y: 116, z: -841, price: 1000 },
  nether: { x: 422, y: 116, z: -839, price: 5000 },
  end: { x: 422, y: 117, z: -840, price: 20000 },
};
// Back-compat name for dragonballs-summon.mjs, which only ever deals with the overworld sign.
export const SIGN = SIGNS.overworld;

// A separate flat platform inside the Nether, for dragonballs-sets.mjs's own set of scenarios.
// Chosen within findNetherSafeY's own scan band (32-108) so the carved floor never collides with
// the natural bedrock roof (~127).
export const NETHER_PLATFORM_Y = 90;

/** Same shape as buildPlatform/releasePlatform in lib.mjs, but carved inside the Nether via
 * `execute in`, since RCON's console sender otherwise always targets the default (overworld)
 * world. Air goes up 20 blocks (not just a few), so the summon-dragon's ceiling scan (up to
 * nether-max-height, default 18) never meets an undeterminable natural block instead of the
 * explicit ceiling a scenario adds with addNetherCeiling. */
export async function buildNetherPlatform(rcon, bx, bz, y) {
  await rcon.command(`minecraft:execute in minecraft:the_nether run forceload add ${bx - 16} ${bz - 16} ${bx + 16} ${bz + 16}`);
  await rcon.command(`minecraft:execute in minecraft:the_nether run fill ${bx - 8} ${y} ${bz - 8} ${bx + 8} ${y} ${bz + 8} minecraft:stone`);
  await rcon.command(`minecraft:execute in minecraft:the_nether run fill ${bx - 8} ${y + 1} ${bz - 8} ${bx + 8} ${y + 20} ${bz + 8} minecraft:air`);
}

export async function releaseNetherPlatform(rcon, bx, bz) {
  await rcon.command(`minecraft:execute in minecraft:the_nether run forceload remove ${bx - 16} ${bz - 16} ${bx + 16} ${bz + 16}`).catch(() => {});
}

/** A thin solid layer `height` blocks above the Nether platform's floor, so a summon's ceiling
 * scan has a known, deterministic distance to clamp against (real cavern terrain above the
 * platform's own carved-out air isn't predictable). Paired with removeNetherCeiling. */
/** A placed ball's hitbox: the Interaction entity floating in block (x, y, z), 0.125 above its floor. */
export function ballHitbox(bot, x, y, z) {
  return Object.values(bot.entities).find((e) => e.name === 'interaction'
    && Math.abs(e.position.x - (x + 0.5)) < 0.1 && Math.abs(e.position.z - (z + 0.5)) < 0.1
    && Math.floor(e.position.y) === y);
}

/** Right-clicks the top of the floor block at (x, y, z) with a ball in hand, the way a player
 * places a head; the plugin floats the ball in the block above instead, so this waits for its
 * hitbox (Mineflayer's placeBlock waits for a block that never comes). A column a prior pickup just
 * vacated can still hold the dropped-item entity for a tick or two and refuse the placement, so a
 * short retry rides that out instead of failing the whole suite on one unlucky tick. */
export async function placeBallOn(bot, x, y, z) {
  const ref = bot.blockAt(new Vec3(x, y, z));
  for (let attempt = 1; ; attempt++) {
    try {
      // The caller may already have equipped it, but a retry needs its own: a refused attempt can
      // leave the hand desynced from the server's view of it.
      const ball = bot.inventory.items().find((it) => it.name === 'player_head');
      if (ball) await bot.equip(ball, 'hand');
      await bot.activateBlock(ref, new Vec3(0, 1, 0));
      await waitFor(() => ballHitbox(bot, x, y + 1, z), `a ball floats above ${x} ${y} ${z}`, 3_000);
      return;
    } catch (error) {
      if (attempt >= 3) throw error;
      await sleep(250);
    }
  }
}

export async function addNetherCeiling(rcon, bx, bz, y, height) {
  await rcon.command(`minecraft:execute in minecraft:the_nether run fill ${bx - 8} ${y + height} ${bz - 8} ${bx + 8} ${y + height} ${bz + 8} minecraft:netherrack`);
}

export async function removeNetherCeiling(rcon, bx, bz, y, height) {
  await rcon.command(`minecraft:execute in minecraft:the_nether run fill ${bx - 8} ${y + height} ${bz - 8} ${bx + 8} ${y + height} ${bz + 8} minecraft:air`);
}

/** Crosses into another dimension via `execute in` (vanilla /teleport can't change worlds on its
 * own), confirmed by the server's own authoritative Dimension tag rather than mineflayer's bot.game
 * state (never inspected elsewhere in this codebase, so not worth relying on here either). */
export async function crossDimensionTeleport(rcon, bot, dimension, x, y, z) {
  // The inner command needs the minecraft: prefix too - EssentialsX shadows plain "tp" globally,
  // including inside an execute-in context, and its own /tp never follows that context's dimension.
  await rcon.command(`minecraft:execute in ${dimension} run minecraft:tp ${bot.username} ${x} ${y} ${z}`);
  await waitFor(async () => (await rcon.command(`data get entity ${bot.username} Dimension`)).includes(dimension),
    `${bot.username} arrived in ${dimension}`, 15_000);
  await sleep(1_000);
  await bot.waitForChunksToLoad().catch(() => {});
  await sleep(500);
}

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

  /** Parses `dragonballs status`'s "-- Overworld/Nether/End --" sectioned, one-line-per-star output
   * into {overworld, nether, end}, each a {star: {state, world, x, y, z, holder, stone}} map. The 3
   * sections all number their stars 1-7, so the header is what the dormant "sleeps" line and every
   * ball line below it get filed under - without it, a later section's star N would silently clobber
   * an earlier one's (the bug this replaced). */
  function parseStatus(text) {
    const sets = { overworld: {}, nether: {}, end: {} };
    let current = null;
    for (const line of plain(text).split(/\r?\n/)) {
      const header = line.match(/^-- (Overworld|Nether|End) --$/);
      if (header) {
        current = header[1].toLowerCase();
        continue;
      }
      if (!current) continue;
      const m = line.match(/^(?<star>\d)-star: (?:placed at (?<pw>\S+) (?<px>-?\d+),(?<py>-?\d+),(?<pz>-?\d+)|dropped at (?<dw>\S+) (?<dx>-?\d+),(?<dy>-?\d+),(?<dz>-?\d+)|held by (?<holder>\S+))(?<stone>\s*\(stone\))?/);
      if (!m) continue;
      const g = m.groups;
      const star = Number(g.star);
      const stone = Boolean(g.stone);
      if (g.pw) sets[current][star] = { state: 'placed', world: g.pw, x: Number(g.px), y: Number(g.py), z: Number(g.pz), stone };
      else if (g.dw) sets[current][star] = { state: 'dropped', world: g.dw, x: Number(g.dx), y: Number(g.dy), z: Number(g.dz), stone };
      else sets[current][star] = { state: 'held', holder: g.holder, stone };
    }
    return sets;
  }

  /** {overworld, nether, end}, each a star-keyed map - see parseStatus. */
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
   * floats a ball over the floor block at ballOffset(index). */
  async function placeBallAt(bot, index) {
    const { x, y, z } = ballOffset(index);
    await placeBallOn(bot, x, y, z);
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

  /** Gives all 7 of `set`'s balls (default overworld, matching /dragonballs give's own default) to
   * playerName in one go. */
  async function collectAllSeven(bot, playerName, set) {
    const suffix = set ? ` ${set}` : '';
    for (let star = 1; star <= 7; star++) {
      await rcon.command(`dragonballs give ${playerName} ${star}${suffix}`);
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
    await releasePlatform(rcon, BX, BZ);
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
