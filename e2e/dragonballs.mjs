import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { Vec3 } from 'vec3';
import {
  Rcon, assert, buildPlatform, createBot, quit, randomOrigin, releasePlatform, sleep, teleport,
  waitFor, waitForLine,
} from './lib.mjs';

// Dragon Balls: 7 heads, collect all 7 within 2 blocks and right-click one to summon. Rules and the
// console protocol: docs/dragonballs.md. Bots can't answer the real choice-of-dragon Paper dialog,
// so this suite drives `shenron summon <player> <dragon>` instead - a debug-only addition to
// ShenronCommand that re-validates every trigger condition through SummonSessionManager.tryStart
// exactly as a real dialog click would (see that command's own javadoc).
//
// IMPORTANT, confirmed empirically before writing this suite: Brigadier's `.requires` is checked
// top-down, so a console-only top-level node hides every child from a non-console sender before the
// dispatcher even looks at which child was typed. `/shenron say|grant|dismiss|status|curse` are
// still gated to a real ConsoleCommandSender (RCON's sender is a DIFFERENT type,
// RemoteConsoleCommandSender, by design - see docs/dragonballs.md's "Answer only through console
// commands") and so stay unreachable here; only `summon` was moved off that gate (permission-only)
// since it never bypasses a rule and is the only way an RCON-driven bot can start a session at all.
// This suite therefore cannot exercise say/grant/curse/dismiss - it ends a session via the idle
// timeout instead (config temporarily shortened, same restore-on-exit pattern as afk.mjs/tracker.mjs).
const here = path.dirname(fileURLToPath(import.meta.url));
const configPath = path.join(here, '..', 'testserver', 'run-e2e', 'plugins', 'DragonBalls', 'config.yml');
const wishesPath = path.join(here, '..', 'testserver', 'run-e2e', 'plugins', 'DragonBalls', 'wishes.jsonl');
const logPath = path.join(here, '..', 'testserver', 'run-e2e', 'logs', 'latest.log');

const PLATFORM_Y = 200;
const { bx: BX, bz: BZ } = randomOrigin();
// All 7 within Chebyshev radius 1 of (BX, BZ) - well inside summon.radius (default 2). The bots
// stand one block further out (BZ + 2) so their own hitbox never occupies a column they're about
// to place into (BlockPlaceEvent is refused into an occupied space) while staying within reach.
const OFFSETS = [[0, 0], [1, 0], [0, 1], [1, 1], [-1, 0], [0, -1], [-1, -1]];
const STAND = { x: BX, y: PLATFORM_Y + 1, z: BZ + 2 };
const HUNTER = 'DBHunter';
const WITNESS = 'DBWitness';
// Default station.radar-sign coords (config.yml) - the e2e world has no spawn-market build, so a
// small floor + sign are carved out here, same spirit as market.mjs's "no spawn market" comment.
const SIGN = { x: 413, y: 116, z: -873 };

const rcon = await Rcon.connect();
const bots = {};
const results = [];
const originalConfig = fs.readFileSync(configPath, 'utf8');
let configIsShortened = false;

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
  let out = set(text, 'idle-timeout-seconds', 2);
  out = set(out, 'summoner-offline-seconds', 2);
  return out;
}

async function collectAllSeven(bot, playerName) {
  for (let star = 1; star <= 7; star++) {
    await rcon.command(`dragonballs give ${playerName} ${star}`);
  }
  await sleep(300);
}

try {
  await buildPlatform(rcon, BX, BZ, PLATFORM_Y);

  const hunter = await createBot(HUNTER);
  bots[HUNTER] = hunter;
  const witness = await createBot(WITNESS);
  bots[WITNESS] = witness;
  // /clear is a no-op against an offline player, so this must run after both bots connect - a
  // fresh e2e run otherwise inherits whatever junk a previous run (or manual probing) left behind.
  await rcon.command(`minecraft:clear ${HUNTER}`);
  await rcon.command(`minecraft:clear ${WITNESS}`);
  await rcon.command(`minecraft:gamemode survival ${HUNTER}`);
  await rcon.command(`minecraft:gamemode survival ${WITNESS}`);
  await teleport(rcon, hunter, STAND.x, STAND.y, STAND.z);
  await teleport(rcon, witness, STAND.x + 3, STAND.y, STAND.z);

  // --- 1. plugin enabled, status lists 7 balls, reset under our control -------------------------
  await step('dragonballs status lists all 7 balls', async () => {
    // A previous run's summon leaves the balls stone for 3 in-game days; start awake.
    await rcon.command('dragonballs wake');
    await collectAllSeven(hunter, HUNTER);
    const s = await status();
    assert(Object.keys(s).length === 7, `status has 7 stars (got ${JSON.stringify(s)})`);
    for (let star = 1; star <= 7; star++) {
      assert(s[star].state === 'held' && s[star].holder === HUNTER, `star ${star} held by ${HUNTER} (got ${JSON.stringify(s[star])})`);
    }
  });

  // --- 2. dupe guard: give the same star to two players, only one copy exists --------------------
  await step('give to a second player leaves only one copy', async () => {
    await rcon.command(`dragonballs give ${WITNESS} 1`);
    await sleep(200);
    const s = await status();
    assert(s[1].state === 'held' && s[1].holder === WITNESS, `star 1 now held by ${WITNESS} (got ${JSON.stringify(s[1])})`);
    await rcon.command(`dragonballs give ${HUNTER} 1`);
    await sleep(200);
    const after = await status();
    assert(after[1].state === 'held' && after[1].holder === HUNTER, `star 1 back with ${HUNTER} (got ${JSON.stringify(after[1])})`);
  });

  // --- 3. give/slowness/drop/pickup broadcast ------------------------------------------------------
  let witnessPickedUpStar;
  await step('carrying 1 then 3 balls slows the holder by 5% then 15%; dropping one updates it', async () => {
    // /dragonballs give never fires a pickup/drop event, so it never recomputes the slowness
    // modifier on its own (see BallSlownessListener); a toss+self-pickup cycle launders it through
    // a real EntityPickupItemEvent, exactly like a player bouncing a ball in hand would.
    // The reset left all 7 with the hunter; park 2-7 with the witness and clear them from its
    // inventory so the hunter really carries one.
    for (let star = 2; star <= 7; star++) await rcon.command(`dragonballs give ${WITNESS} ${star}`);
    await rcon.command(`minecraft:clear ${WITNESS} minecraft:player_head`);
    await rcon.command(`minecraft:clear ${HUNTER} minecraft:player_head`);
    await rcon.command(`dragonballs give ${HUNTER} 1`);
    await sleep(300);
    const tossAndRepickup = async () => {
      const ball = hunter.inventory.items().find((i) => i.name === 'player_head');
      await hunter.tossStack(ball);
      await sleep(300);
      const s = await status();
      const dropped = Object.entries(s).find(([, v]) => v.state === 'dropped');
      assert(dropped, `a star is dropped after toss (got ${JSON.stringify(s)})`);
      const [, loc] = dropped;
      // The registry's location is where the ball left the hand; a tossed item flies on for a block
      // or two, so walk the bot onto where the entity actually landed (after its pickup delay).
      await sleep(1_700);
      const pos = await rcon.command(
        `data get entity @e[type=minecraft:item,limit=1,sort=nearest,x=${loc.x},y=${loc.y},z=${loc.z}] Pos`);
      const [x, y, z] = (pos.match(/-?\d+(\.\d+)?(?=d)/g) ?? [loc.x, loc.y, loc.z]).map(Number);
      await teleport(rcon, hunter, x, y, z);
      await waitFor(async () => (await status())[dropped[0]].state === 'held', 'DBHunter re-picked up the tossed star', 5_000);
    };

    await tossAndRepickup();
    await waitFor(async () => near(await speedModifier(HUNTER), -0.05), '1 ball = -0.05 movement speed modifier', 3_000);

    await rcon.command(`dragonballs give ${HUNTER} 2`);
    await rcon.command(`dragonballs give ${HUNTER} 3`);
    await sleep(200);
    await tossAndRepickup(); // re-triggers a recompute that now scans all 3 held balls
    await waitFor(async () => near(await speedModifier(HUNTER), -0.15), '3 balls = -0.15 movement speed modifier', 3_000);

    // Drop one (without picking it back up) and confirm the modifier drops to -0.10 immediately.
    const ball = hunter.inventory.items().find((i) => i.name === 'player_head');
    await hunter.tossStack(ball);
    await waitFor(async () => near(await speedModifier(HUNTER), -0.10), 'dropping one ball updates the modifier to -0.10', 3_000);

    const s = await status();
    const [starStr, loc] = Object.entries(s).find(([, v]) => v.state === 'dropped');
    witnessPickedUpStar = Number(starStr);

    // A DIFFERENT player picking it up (not the dropper) must still get the "found" broadcast -
    // recentlySelfDropped only suppresses it for the player who dropped it within the last 30s.
    const since = witness.lines.length;
    await teleport(rcon, witness, loc.x, loc.y, loc.z);
    await waitForLine(witness, new RegExp(`found the ${witnessPickedUpStar}-Star Dragon Ball!`), 5_000, since);
    await waitFor(async () => near(await speedModifier(WITNESS), -0.05), 'DBWitness now shows -0.05 after the pickup', 3_000);
  });

  // Return everything to DBHunter for the next scenarios.
  await rcon.command(`dragonballs give ${HUNTER} ${witnessPickedUpStar}`);
  await sleep(200);

  // --- 4. container guard: a ball never goes into a chest -------------------------------------------
  await step('a ball cannot be clicked into a chest; the chest stays empty and the ball stays held', async () => {
    const chestPos = { x: BX + 4, y: PLATFORM_Y + 1, z: BZ + 2 };
    await rcon.command(`minecraft:setblock ${chestPos.x} ${chestPos.y} ${chestPos.z} minecraft:chest`);
    await sleep(300);
    await teleport(rcon, hunter, chestPos.x - 1, PLATFORM_Y + 1, chestPos.z);
    const chestBlock = hunter.blockAt(new Vec3(chestPos.x, chestPos.y, chestPos.z));
    const win = await openWindow(hunter, chestBlock);
    const ballSlot = win.slots.findIndex((s) => s && s.name === 'player_head');
    assert(ballSlot !== -1, 'a ball is visible in the opened window');
    await hunter.clickWindow(ballSlot, 0, 0); // pick the ball up onto the cursor (own inventory slot: allowed)
    await sleep(200);
    await hunter.clickWindow(0, 0, 0); // try to place it into chest slot 0: must be cancelled
    await sleep(200);
    await hunter.clickWindow(ballSlot, 0, 0); // put it back down in its own slot
    await sleep(200);
    await hunter.closeWindow(win);
    await sleep(300);

    const items = await rcon.command(`minecraft:data get block ${chestPos.x} ${chestPos.y} ${chestPos.z} Items`);
    assert(/\[\]/.test(plain(items)), `chest stayed empty (got ${items.trim()})`);
    const s = await status();
    assert(Object.values(s).some((v) => v.state === 'held' && v.holder === HUNTER), 'the ball is still held by DBHunter');
    await rcon.command(`minecraft:setblock ${chestPos.x} ${chestPos.y} ${chestPos.z} minecraft:air`);
  });

  // --- 5. hopper guard: a hopper under a dropped ball never takes it ---------------------------------
  await step('a hopper never sucks up a dropped ball', async () => {
    const hopperPos = { x: BX + 6, y: PLATFORM_Y, z: BZ + 2 };
    await rcon.command(`minecraft:setblock ${hopperPos.x} ${hopperPos.y} ${hopperPos.z} minecraft:hopper[facing=down]`);
    await sleep(300);
    await teleport(rcon, hunter, hopperPos.x, PLATFORM_Y + 1, hopperPos.z);
    const ball = hunter.inventory.items().find((i) => i.name === 'player_head');
    const before = await status();
    const starThrown = before && Object.entries(before).find(([, v]) => v.state === 'held' && v.holder === HUNTER)[0];
    await hunter.tossStack(ball);
    await sleep(2_000); // a hopper pulls from directly above on its own short cooldown, well under 2s

    const hopperItems = await rcon.command(`minecraft:data get block ${hopperPos.x} ${hopperPos.y} ${hopperPos.z} Items`);
    assert(/\[\]/.test(plain(hopperItems)), `hopper never swallowed the ball (got ${hopperItems.trim()})`);
    const s = await status();
    assert(s[starThrown].state === 'dropped', `star ${starThrown} is still a free-standing dropped item (got ${JSON.stringify(s[starThrown])})`);

    // --- bonus coverage while the ball is still sitting there: invulnerable, no quick despawn --
    const loc = s[starThrown];
    const dmg = await rcon.command(`minecraft:execute at ${HUNTER} run damage @e[type=item,x=${loc.x},y=${loc.y},z=${loc.z},distance=..2,limit=1] 1000 minecraft:in_fire`);
    await sleep(500);
    const stillThere = await status();
    assert(stillThere[starThrown].state === 'dropped', `the ball survives /damage (got ${dmg.trim()}; status ${JSON.stringify(stillThere[starThrown])})`);

    await teleport(rcon, hunter, loc.x, loc.y, loc.z);
    await waitFor(async () => (await status())[starThrown].state === 'held', 'DBHunter re-picked up the surviving ball', 5_000);
    await rcon.command(`minecraft:setblock ${hopperPos.x} ${hopperPos.y} ${hopperPos.z} minecraft:air`);
  });

  // --- 6. death drop: balls land near the death spot, not in a grave --------------------------------
  await step('dying drops carried balls near the death spot, not into a grave', async () => {
    await teleport(rcon, hunter, STAND.x, STAND.y, STAND.z);
    const before = await status();
    const heldStars = Object.entries(before).filter(([, v]) => v.state === 'held' && v.holder === HUNTER).map(([k]) => Number(k));
    assert(heldStars.length > 0, 'DBHunter holds at least one ball before dying');
    await rcon.command(`minecraft:kill ${HUNTER}`);
    await sleep(1_500);
    const after = await status();
    for (const star of heldStars) {
      assert(after[star].state === 'dropped', `star ${star} dropped on death (got ${JSON.stringify(after[star])})`);
      assert(Math.abs(after[star].x - STAND.x) < 6 && Math.abs(after[star].z - STAND.z) < 6,
        `star ${star} dropped near the death spot (got ${JSON.stringify(after[star])})`);
    }
    // Re-collect everything back under DBHunter for the next scenarios.
    for (let star = 1; star <= 7; star++) await rcon.command(`dragonballs give ${HUNTER} ${star}`);
    await sleep(300);
  });

  // --- 7. place/break: breaking a placed ball gives the ball item back, not a plain head ------------
  await step('placing then breaking a ball gives the exact same ball back', async () => {
    await teleport(rcon, hunter, STAND.x, STAND.y, STAND.z);
    const ball = hunter.inventory.items().find((i) => i.name === 'player_head');
    await hunter.equip(ball, 'hand');
    await placeBallAt(hunter, 0);
    await sleep(300);
    const { x, y, z } = ballOffset(0);
    const pbv = plain(await rcon.command(`minecraft:data get block ${x} ${y} ${z} PublicBukkitValues`));
    assert(/dragonballs:star/.test(pbv), `placed block carries the dragonballs:star PDC tag (got ${pbv})`);
    const before = await status();
    const placedStar = Object.entries(before).find(([, v]) => v.state === 'placed' && v.x === x && v.z === z)[0];

    const block = hunter.blockAt(new Vec3(x, y, z));
    const countBefore = hunter.inventory.items().filter((i) => i.name === 'player_head').length;
    await hunter.dig(block);
    await sleep(500);
    const countAfter = hunter.inventory.items().filter((i) => i.name === 'player_head').length;
    assert(countAfter === countBefore + 1, `breaking it returns exactly one ball item (before=${countBefore} after=${countAfter})`);
    const after = await status();
    assert(after[placedStar].state === 'held' && after[placedStar].holder === HUNTER,
      `star ${placedStar} is held by DBHunter again (got ${JSON.stringify(after[placedStar])})`);
  });

  // --- 8. radar: purchase, can't-afford, and tracking the nearest active ball -----------------------
  await step('buying a Dragon Radar costs 1000 and the sign refuses without enough', async () => {
    await rcon.command(`minecraft:forceload add ${SIGN.x - 8} ${SIGN.z - 8} ${SIGN.x + 8} ${SIGN.z + 8}`);
    await rcon.command(`minecraft:fill ${SIGN.x - 2} ${SIGN.y - 1} ${SIGN.z - 2} ${SIGN.x + 2} ${SIGN.y - 1} ${SIGN.z + 2} minecraft:stone`);
    await rcon.command(`minecraft:fill ${SIGN.x - 2} ${SIGN.y} ${SIGN.z - 2} ${SIGN.x + 2} ${SIGN.y + 3} ${SIGN.z + 2} minecraft:air`);
    await rcon.command(`minecraft:setblock ${SIGN.x} ${SIGN.y} ${SIGN.z} minecraft:oak_sign`);
    await sleep(300);
    await teleport(rcon, hunter, SIGN.x, SIGN.y, SIGN.z + 1);
    const signBlock = hunter.blockAt(new Vec3(SIGN.x, SIGN.y, SIGN.z));

    await rcon.command(`eco set ${HUNTER} 2000`);
    const before = await balance(HUNTER);
    const compassesBefore = hunter.inventory.items().filter((i) => i.name === 'compass').length;
    await hunter.activateBlock(signBlock);
    await waitFor(async () => (await balance(HUNTER)) === before - 1000, 'buying a radar withdraws exactly 1000', 5_000);
    await waitFor(() => hunter.inventory.items().filter((i) => i.name === 'compass').length === compassesBefore + 1,
      'DBHunter received a new compass', 5_000);

    await rcon.command(`eco set ${HUNTER} 0`);
    const sinceBroke = hunter.lines.length;
    await hunter.activateBlock(signBlock);
    await sleep(500);
    await waitForLine(hunter, /A Dragon Radar costs/, 3_000, sinceBroke);
    assert((await balance(HUNTER)) === 0, 'balance unchanged when the sign refuses');
    assert(hunter.inventory.items().filter((i) => i.name === 'compass').length === compassesBefore + 1,
      'no second compass was granted without paying');

    await rcon.command(`minecraft:setblock ${SIGN.x} ${SIGN.y} ${SIGN.z} minecraft:air`);
    await rcon.command(`minecraft:forceload remove ${SIGN.x - 8} ${SIGN.z - 8} ${SIGN.x + 8} ${SIGN.z + 8}`);
  });

  await step('holding the radar points the compass target at the nearest active ball', async () => {
    const radar = hunter.inventory.items().find((i) => i.name === 'compass');
    assert(radar, 'DBHunter holds the purchased radar');
    // Place a lone active (non-stone) ball well away from the rest of the cluster, so there is an
    // unambiguous single nearest target for the radar to point at.
    const target = { x: BX + 20, y: PLATFORM_Y, z: BZ + 20 };
    await rcon.command(`minecraft:fill ${target.x - 1} ${target.y} ${target.z - 1} ${target.x + 1} ${target.y} ${target.z + 1} minecraft:stone`);
    await rcon.command(`minecraft:fill ${target.x - 1} ${target.y + 1} ${target.z - 1} ${target.x + 1} ${target.y + 4} ${target.z + 1} minecraft:air`);
    const before = await status();
    const radarStar = Object.entries(before).find(([, v]) => v.state === 'held' && v.holder === HUNTER)[0];
    // Stand one block off the target column: BlockPlaceEvent refuses a space the placer's own
    // hitbox already occupies (confirmed empirically while testing placeBallAt above).
    await teleport(rcon, witness, target.x, target.y + 1, target.z + 1);
    await rcon.command(`dragonballs give ${WITNESS} ${radarStar}`);
    await sleep(200);
    const ball = witness.inventory.items().find((i) => i.name === 'player_head');
    await witness.equip(ball, 'hand');
    const targetBlock = witness.blockAt(new Vec3(target.x, target.y, target.z));
    await witness.placeBlock(targetBlock, new Vec3(0, 1, 0));
    await sleep(300);

    await hunter.equip(radar, 'hand');
    await waitFor(() => hunter.spawnPoint && Math.floor(hunter.spawnPoint.x) === target.x
      && Math.floor(hunter.spawnPoint.z) === target.z, 'compass target moved to the lone active ball', 4_000);

    // Clean up: break it back, return to DBHunter.
    const block = witness.blockAt(new Vec3(target.x, target.y + 1, target.z));
    await witness.dig(block).catch(() => {});
    await rcon.command(`dragonballs give ${HUNTER} ${radarStar}`);
  });

  // --- 9. summon flow via the debug `shenron summon` command -----------------------------------------
  await step('summoning via the debug command broadcasts, spawns the dragon, and captures a wish', async () => {
    fs.writeFileSync(configPath, shortenSummonConfig(originalConfig));
    configIsShortened = true;
    await rcon.command('dragonballs reload');

    await collectAllSeven(hunter, HUNTER);
    await teleport(rcon, hunter, STAND.x, STAND.y, STAND.z);
    for (let i = 0; i < 7; i++) {
      const star = i + 1;
      const s = await status();
      if (s[star].state !== 'placed') {
        const ball = hunter.inventory.items().find((it) => it.name === 'player_head');
        await hunter.equip(ball, 'hand');
        await placeBallAt(hunter, i);
        await sleep(150);
      }
    }
    await sleep(300);
    const placed = await status();
    assert(Object.values(placed).every((v) => v.state === 'placed'), `all 7 placed before summon (got ${JSON.stringify(placed)})`);

    const since = witness.lines.length;
    const reply = plain(await rcon.command(`shenron summon ${HUNTER} porunga`));
    // The console sender gets the server-wide broadcast too, so a success echoes it back.
    assert(!/Cannot summon|not online|No placed|Unknown dragon/.test(reply), `summon via RCON reports no error (got "${reply.trim()}")`);
    await waitForLine(witness, /DBHunter has summoned Porunga!/, 5_000, since);
    await waitForLine(witness, /I am the Eternal Dragon\. State your wish\. I shall grant three\./, 5_000, since);

    await waitFor(async () => /passed/i.test(await rcon.command('execute if entity @e[type=ender_dragon,tag=dragonballs.shenron]')),
      'the Shenron/Porunga dragon entity exists', 5_000);

    const wishSince = witness.lines.length;
    const logSizeBefore = fs.existsSync(logPath) ? fs.statSync(logPath).size : 0;
    hunter.chat('I wish for a diamond sword');
    await waitForLine(witness, /DBHunter to Porunga: I wish for a diamond sword/, 5_000, wishSince);

    await waitFor(() => {
      if (!fs.existsSync(logPath)) return false;
      const added = fs.readFileSync(logPath, 'utf8').slice(logSizeBefore);
      return /\[DragonBalls\] WISH \S+ Porunga \d+ DBHunter/.test(added);
    }, 'server log has a [DragonBalls] WISH line', 5_000);

    await waitFor(() => {
      if (!fs.existsSync(wishesPath)) return false;
      const lines = fs.readFileSync(wishesPath, 'utf8').trim().split('\n');
      return lines.some((l) => l.includes('"message":"I wish for a diamond sword"'));
    }, 'wishes.jsonl recorded the wish', 5_000);

    // No RCON path can /shenron grant|dismiss (console-only by design, confirmed unreachable over
    // RCON above); the idle timeout (shortened to 2s, checked every 5s tick) ends the session instead.
    await waitFor(async () => !/passed/i.test(await rcon.command('execute if entity @e[type=ender_dragon,tag=dragonballs.shenron]')),
      'the dragon despawns once the idle timeout ends the session', 12_000);
    // Scattering loads each destination chunk asynchronously, so the registry catches up over a few seconds.
    await waitFor(async () => {
      const afterEnd = await status();
      return Object.values(afterEnd).every((v) => v.stone && (v.state !== 'placed' || Math.abs(v.x - BX) > 500));
    }, 'all 7 are stone and have flown away from the summon spot', 20_000);

    fs.writeFileSync(configPath, originalConfig);
    configIsShortened = false;
    await rcon.command('dragonballs reload');
  });

  // --- 10. dormant: summon is refused while stone --------------------------------------------------
  await step('summon is refused while the balls are dormant', async () => {
    const reply = plain(await rcon.command(`shenron summon ${HUNTER} shenron`));
    assert(/Cannot summon/.test(reply), `dormant summon refused (got "${reply.trim()}")`);
    await waitFor(async () => !/passed/i.test(await rcon.command('execute if entity @e[type=ender_dragon,tag=dragonballs.shenron]')),
      'still no dragon', 2_000);
  });

  // --- 11. a stone ball can still be found, broken and carried -------------------------------------
  await step('a stone ball can be broken and picked up as its stone item', async () => {
    const s = await status();
    const [starStr, loc] = Object.entries(s).find(([, v]) => v.state === 'placed');
    const star = Number(starStr);
    await rcon.command(`minecraft:forceload add ${loc.x - 8} ${loc.z - 8} ${loc.x + 8} ${loc.z + 8}`);
    await teleport(rcon, hunter, loc.x, loc.y + 1, loc.z);
    const block = hunter.blockAt(new Vec3(loc.x, loc.y, loc.z));
    const countBefore = hunter.inventory.items().filter((i) => i.name === 'player_head').length;
    await hunter.dig(block);
    await sleep(500);
    const countAfter = hunter.inventory.items().filter((i) => i.name === 'player_head').length;
    assert(countAfter === countBefore + 1, 'breaking a stone ball gives an item back');
    const after = await status();
    assert(after[star].state === 'held' && after[star].holder === HUNTER && after[star].stone,
      `star ${star} held, still stone (got ${JSON.stringify(after[star])})`);
    await rcon.command(`minecraft:forceload remove ${loc.x - 8} ${loc.z - 8} ${loc.x + 8} ${loc.z + 8}`);
  });

  console.log(`\nDragonBalls e2e: ${results.length} passed`);
} catch (error) {
  console.error(`FAIL after ${results.length} passed: ${error.message}\n${error.stack}`);
  process.exitCode = 1;
} finally {
  if (configIsShortened) {
    fs.writeFileSync(configPath, originalConfig);
    await rcon.command('dragonballs reload').catch(() => {});
  }
  await rcon.command('minecraft:kill @e[type=ender_dragon,tag=dragonballs.shenron]').catch(() => {});
  await rcon.command(`minecraft:clear ${HUNTER}`).catch(() => {});
  await rcon.command(`minecraft:clear ${WITNESS}`).catch(() => {});
  await quit(...Object.values(bots));
  rcon.close();
}
