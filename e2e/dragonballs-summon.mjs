import fs from 'node:fs';
import { Vec3 } from 'vec3';
import { assert, nbtText, sleep, teleport, waitFor, waitForLine } from './lib.mjs';
import {
  HUNTER, PLATFORM_Y, SIGN, WITNESS, configPath, logPath, openSuite, wishesPath,
} from './dragonballs-lib.mjs';

// Dragon Balls, radar and summon: buying and pointing the radar, a full summon, the dormant refusal
// and the stone phase. Bots can't answer the real choice-of-dragon Paper dialog, so this suite drives
// `shenron summon <player> <dragon>` instead - a debug-only addition to ShenronCommand that
// re-validates every trigger condition through SummonSessionManager.tryStart exactly as a real
// dialog click would (see that command's own javadoc).
// The judge commands (`/shenron say|grant|dismiss|status|curse`) accept the console and RCON, so the
// summon step answers the wish over RCON the same way Player Claude does on live. The idle timeout is
// temporarily shortened as a backstop (same restore-on-exit pattern as afk.mjs/tracker.mjs).
const {
  rcon, BX, BZ, STAND, hunter, witness, step, plain, status: rawStatus, balance, placeBallAt, shortenSummonConfig,
  collectAllSeven, finish,
} = await openSuite('DragonBalls summon');
// This suite only ever touches the overworld set; flatten to the familiar star-keyed map so the
// rest of the file (written before the 3-set split) reads unchanged.
const status = async () => (await rawStatus()).overworld;
const originalConfig = fs.readFileSync(configPath, 'utf8');
let configIsShortened = false;

try {
  // A previous run's summon leaves the balls stone for 3 in-game days; start awake, all 7 in hand.
  await rcon.command('dragonballs wake');
  await collectAllSeven(hunter, HUNTER);

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
        // The registry's own record of the placement can lag the BlockPlaceEvent by more than a
        // blind sleep (seen on dragonballs-sets.mjs's identical loop: still "held" 300ms after a
        // placeBlock that didn't throw), so confirm this star actually turned before moving on.
        await waitFor(async () => (await status())[star].state === 'placed', `${star}-star registered as placed`, 5_000);
      }
    }
    const placed = await status();
    assert(Object.values(placed).every((v) => v.state === 'placed'), `all 7 placed before summon (got ${JSON.stringify(placed)})`);

    // A ready set only stirs without a Dragon Radar in the main hand.
    const ballSpot = Object.values(placed)[0];
    const ballBlock = hunter.blockAt(new Vec3(ballSpot.x, ballSpot.y, ballSpot.z));
    const emptySlot = hunter.inventory.slots.findIndex((s, i) => i >= 36 && i <= 44 && !s);
    assert(emptySlot >= 0, 'DBHunter has an empty hotbar slot');
    hunter.setQuickBarSlot(emptySlot - 36);
    const hintSince = hunter.lines.length;
    // Mineflayer's own actionBar event never fires for ViaBackwards' NBT action bar (see tracker.mjs).
    const recordActionBar = (packet) => {
      if (packet.isActionBar !== false) hunter.lines.push(nbtText(packet.text ?? packet.content));
    };
    hunter._client.on('action_bar', recordActionBar);
    hunter._client.on('system_chat', recordActionBar);
    await hunter.activateBlock(ballBlock);
    await waitForLine(hunter, /The balls stir\.\.\. but need a Dragon Radar/, 5_000, hintSince);
    hunter._client.removeListener('action_bar', recordActionBar);
    hunter._client.removeListener('system_chat', recordActionBar);
    assert(!/passed/i.test(await rcon.command('execute if entity @e[type=ender_dragon,tag=dragonballs.shenron]')),
      'no dragon without a radar');

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

    // The judge side, over RCON exactly like Player Claude drives it on live.
    const saySince = witness.lines.length;
    await rcon.command('shenron say Your wish is heard, mortal.');
    await waitForLine(witness, /Your wish is heard, mortal\./, 5_000, saySince);

    const grantReply = plain(await rcon.command('shenron grant 1 e2e diamond sword'));
    assert(!/No dragon|cannot|only/i.test(grantReply), `grant accepted (got "${grantReply.trim()}")`);
    await waitFor(() => fs.readFileSync(wishesPath, 'utf8').includes('"type":"grant","count":1,"note":"e2e diamond sword"'),
      'wishes.jsonl recorded the grant', 5_000);
    const judgeStatus = plain(await rcon.command('shenron status'));
    assert(/wishesLeft=2/.test(judgeStatus), `Porunga has 2 of 3 wishes left after one grant (got "${judgeStatus.trim()}")`);

    const hasSlowness = async () => /slowness/.test(await rcon.command(`data get entity ${WITNESS} active_effects`));
    await rcon.command(`shenron curse effect ${WITNESS} slowness 0 1`);
    await waitFor(hasSlowness, 'the curse applies slowness at once', 3_000);
    await rcon.command(`minecraft:kill ${WITNESS}`);
    await waitFor(async () => witness.health > 0 && await hasSlowness(), 'the curse comes back after death and respawn', 8_000);
    await rcon.command(`shenron curse lift ${WITNESS}`);
    await waitFor(async () => !(await hasSlowness()), 'lifting the curse removes the slowness', 3_000);

    const byeSince = witness.lines.length;
    await rcon.command('shenron dismiss Farewell.');
    await waitForLine(witness, /Farewell\./, 5_000, byeSince);
    await waitFor(async () => !/passed/i.test(await rcon.command('execute if entity @e[type=ender_dragon,tag=dragonballs.shenron]')),
      'the dragon despawns once dismissed', 12_000);
    // Scattering loads (often generates) each destination chunk asynchronously, so the registry can
    // take well over 20 s to catch up on a fresh e2e world; an instrumented run measured one star
    // still short of "flown away" at the old 45s cutoff, settling at 42-50s depending on the run.
    await waitFor(async () => {
      const afterEnd = await status();
      // The overworld set never leaves its own dimension (each of the 3 sets stays in its own
      // dimension now), so distance from the summon spot is what "flown away" means here.
      const flownAway = (v) => v.state !== 'placed' || v.world !== 'world'
        || Math.max(Math.abs(v.x - BX), Math.abs(v.z - BZ)) > 500;
      return Object.values(afterEnd).every((v) => v.stone && flownAway(v));
    }, 'all 7 are stone and have flown away from the summon spot', 90_000);

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
    const overworldBall = Object.entries(s).find(([, v]) => v.state === 'placed' && v.world === 'world');
    assert(overworldBall, `at least one stone ball landed in the overworld (got ${JSON.stringify(s)})`);
    const [starStr, loc] = overworldBall;
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

  await finish();
} catch (error) {
  await finish(error);
} finally {
  if (configIsShortened) {
    fs.writeFileSync(configPath, originalConfig);
    await rcon.command('dragonballs reload').catch(() => {});
  }
  await rcon.command('minecraft:kill @e[type=ender_dragon,tag=dragonballs.shenron]').catch(() => {});
}
