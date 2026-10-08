import fs from 'node:fs';
import { Vec3 } from 'vec3';
import { assert, nbtText, sleep, teleport, waitFor, waitForLine } from './lib.mjs';
import {
  HUNTER, NETHER_PLATFORM_Y, OFFSETS, SIGNS, WITNESS, addNetherCeiling, ballHitbox, buildNetherPlatform, configPath,
  crossDimensionTeleport, logPath, openSuite, placeBallOn, releaseNetherPlatform, removeNetherCeiling,
} from './dragonballs-lib.mjs';

// Ceiling height for the summon-dragon headroom check in step 4: tall enough that the dragon's
// own ceiling scan (nether-max-height, default 18) sees it, short enough that the hover-height
// clamp (nether-ceiling-clearance/nether-min-height, defaults 2/3) visibly kicks in.
const SUMMON_CEILING_HEIGHT = 10;

// Dragon Balls, the Nether/End sets: status across all 3 sets, buying the Nether/End radars (price
// and look), the cross-dimension "stays behind" guard, a real Nether summon (wrong-set radar
// refused and not spent, a matching debug-summon starts it, a second set is refused while one is
// active, dismiss re-scatters only that set) and the overworld scatter's structure placements.
// Overworld lifecycle/radar/summon: dragonballs.mjs / dragonballs-summon.mjs. Rules and the console
// protocol: docs/dragonballs.md.
const {
  rcon, BX, BZ, STAND, hunter, witness, step, plain, status, balance, collectAllSeven, shortenSummonConfig, finish,
  placeBallAt,
} = await openSuite('DragonBalls sets');

// A second platform, inside the Nether, at the same flat (x, z) as the overworld one openSuite
// already built - safe only because they're different worlds, and a Nether ball can never be
// placed anywhere else.
const NETHER_STAND = { x: BX, y: NETHER_PLATFORM_Y + 1, z: BZ + 2 };

function netherBallOffset(index) {
  const [dx, dz] = OFFSETS[index];
  return { x: BX + dx, y: NETHER_PLATFORM_Y, z: BZ + dz };
}

async function placeNetherBallAt(bot, index) {
  const { x, y, z } = netherBallOffset(index);
  await placeBallOn(bot, x, y, z);
}

async function buildSign(pos) {
  await rcon.command(`minecraft:fill ${pos.x - 1} ${pos.y - 1} ${pos.z - 1} ${pos.x + 1} ${pos.y - 1} ${pos.z + 1} minecraft:stone`);
  await rcon.command(`minecraft:fill ${pos.x - 1} ${pos.y} ${pos.z - 1} ${pos.x + 1} ${pos.y + 3} ${pos.z + 1} minecraft:air`);
  await rcon.command(`minecraft:setblock ${pos.x} ${pos.y} ${pos.z} minecraft:oak_sign`);
}

async function clearSign(pos) {
  await rcon.command(`minecraft:setblock ${pos.x} ${pos.y} ${pos.z} minecraft:air`);
}

const originalConfig = fs.readFileSync(configPath, 'utf8');
let configIsShortened = false;

try {
  await buildNetherPlatform(rcon, BX, BZ, NETHER_PLATFORM_Y);
  await rcon.command('dragonballs wake');

  // --- 1. status lists all three sets, Nether/End still exactly as the boot scatter left them ------
  await step('dragonballs status lists all three sets with 7 balls each', async () => {
    const s = await status();
    for (const set of ['overworld', 'nether', 'end']) {
      assert(Object.keys(s[set]).length === 7, `${set} status has 7 stars (got ${JSON.stringify(s[set])})`);
    }
    // Nether and End are untouched by the overworld-only suites that ran before this one, so every
    // one of their 14 balls should still be exactly where the plugin's first-boot scatter put them.
    for (const set of ['nether', 'end']) {
      for (let star = 1; star <= 7; star++) {
        assert(s[set][star].state === 'placed' && !s[set][star].stone,
          `${set} star ${star} placed and awake since boot (got ${JSON.stringify(s[set][star])})`);
      }
    }
  });

  // --- 2. radar shop: Nether/End price and look, keep one Overworld radar for step 4 ----------------
  await step('buying a Nether radar costs exactly 5000 and shows the Nether look; the End sign sells at 20000', async () => {
    await rcon.command(`minecraft:forceload add ${SIGNS.overworld.x - 8} ${SIGNS.overworld.z - 8} ${SIGNS.overworld.x + 8} ${SIGNS.overworld.z + 8}`);
    for (const pos of Object.values(SIGNS)) await buildSign(pos);
    await sleep(300);
    await rcon.command(`eco set ${HUNTER} 30000`);

    const buyAndCheck = async (set, expectName, expectModel, keep) => {
      const pos = SIGNS[set];
      // Cleared at the start of every call, not just the end of the previous one, and given a flat
      // sleep rather than a tight wait on the bot's own inventory view - that view lags noticeably
      // behind a /clear (unlike a plugin-driven addItem), and the count check below is an absolute
      // "== 1", not a delta off whatever this read happens to see.
      await rcon.command(`minecraft:clear ${HUNTER} minecraft:compass`);
      await sleep(500);
      await teleport(rcon, hunter, pos.x, pos.y, pos.z + 1);
      const signBlock = hunter.blockAt(new Vec3(pos.x, pos.y, pos.z));
      const before = await balance(HUNTER);
      await hunter.activateBlock(signBlock);
      await waitFor(async () => (await balance(HUNTER)) === before - pos.price,
        `buying the ${set} radar withdraws exactly ${pos.price}`, 5_000);
      await waitFor(() => hunter.inventory.items().filter((i) => i.name === 'compass').length === 1,
        `DBHunter received the ${set} radar`, 5_000);
      const radar = hunter.inventory.items().find((i) => i.name === 'compass');
      await hunter.equip(radar, 'hand');
      await sleep(300); // lets the equip's own window-click round trip land server-side first
      // Querying the whole SelectedItem truncates mid-NBT (RCON's response size cap) before it ever
      // reaches custom_name/custom_model_data; asking for each component path alone stays well under it.
      let name = plain(await rcon.command(`minecraft:data get entity ${HUNTER} SelectedItem.components."minecraft:custom_name"`));
      // A radar with an awake ball in range is named "Dragon Radar: 4-star (nearest), Close", whose
      // extra components push the base text past the reply cap; ask for the base text alone then.
      if (!name.includes(expectName)) {
        name = plain(await rcon.command(`minecraft:data get entity ${HUNTER} SelectedItem.components."minecraft:custom_name".text`));
      }
      const model = plain(await rcon.command(`minecraft:data get entity ${HUNTER} SelectedItem.components."minecraft:custom_model_data"`));
      assert(name.includes(expectName), `${set} radar shows "${expectName}" (got ${name})`);
      assert(model.includes(expectModel), `${set} radar carries the ${expectModel} model (got ${model})`);
      if (!keep) await rcon.command(`minecraft:clear ${HUNTER} minecraft:compass`);
    };

    await buyAndCheck('nether', 'Nether Dragon Radar', 'dragon_radar_nether', false);
    await buyAndCheck('end', 'End Dragon Radar', 'dragon_radar_end', false);
    // Kept deliberately: step 4 needs an Overworld radar in hand to prove it's refused on a Nether ball.
    await buyAndCheck('overworld', 'Dragon Radar', 'dragon_radar', true);

    for (const pos of Object.values(SIGNS)) await clearSign(pos);
    await rcon.command(`minecraft:forceload remove ${SIGNS.overworld.x - 8} ${SIGNS.overworld.z - 8} ${SIGNS.overworld.x + 8} ${SIGNS.overworld.z + 8}`);
  });

  // --- 3. dimension guard: a Nether ball stays behind, an Overworld ball travels ---------------------
  await step('a Nether ball is left behind when its holder leaves the Nether; an Overworld ball travels with them', async () => {
    await rcon.command(`minecraft:clear ${WITNESS}`);
    // Both balls are given only once DBWitness is already standing in the Nether - giving the
    // Overworld one beforehand would make its OWN inbound crossing (overworld -> nether) the trigger
    // that leaves it behind, defeating the "carried in, then given" setup this scenario needs.
    await crossDimensionTeleport(rcon, witness, 'minecraft:the_nether', NETHER_STAND.x, NETHER_STAND.y, NETHER_STAND.z);
    await rcon.command(`dragonballs give ${WITNESS} 1 nether`);
    await rcon.command(`dragonballs give ${WITNESS} 1`);
    await sleep(300);

    const before = await status();
    assert(before.nether[1].state === 'held' && before.nether[1].holder === WITNESS, 'the Nether ball is held, in the Nether');
    assert(before.overworld[1].state === 'held' && before.overworld[1].holder === WITNESS, 'the Overworld ball is held too');

    await crossDimensionTeleport(rcon, witness, 'minecraft:overworld', STAND.x, STAND.y, STAND.z);
    const after = await status();
    assert(after.nether[1].state === 'dropped', `the Nether ball dropped behind (got ${JSON.stringify(after.nether[1])})`);
    assert(after.nether[1].world === 'world_nether'
      && Math.abs(after.nether[1].x - NETHER_STAND.x) < 4 && Math.abs(after.nether[1].z - NETHER_STAND.z) < 4,
      `it dropped at the Nether departure point (got ${JSON.stringify(after.nether[1])}, expected near ${NETHER_STAND.x},${NETHER_STAND.z})`);
    assert(after.overworld[1].state === 'held' && after.overworld[1].holder === WITNESS,
      `the Overworld ball stayed with DBWitness (got ${JSON.stringify(after.overworld[1])})`);

    await rcon.command(`dragonballs give ${HUNTER} 1 nether`);
    await rcon.command(`dragonballs give ${HUNTER} 1`);
    await sleep(200);
  });

  // --- 4. a real Nether summon: wrong radar refused, matching debug-summon starts it, a second set --
  //        is blocked while active, and dismiss re-scatters only the Nether set -----------------------
  await step("an Overworld radar does not summon a ready Nether set; a session blocks a second set; dismiss re-scatters only the Nether set", async () => {
    fs.writeFileSync(configPath, shortenSummonConfig(originalConfig));
    configIsShortened = true;
    await rcon.command('dragonballs reload');

    await rcon.command(`minecraft:clear ${HUNTER} minecraft:player_head`);
    // The second-set refusal below needs an Overworld ball placed to summon from, and an earlier
    // suite's cleanup can leave the whole Overworld set held by an offline bot, so place one here.
    await rcon.command(`dragonballs give ${HUNTER} 1`);
    await sleep(200);
    await teleport(rcon, hunter, STAND.x, STAND.y, STAND.z);
    await hunter.equip(hunter.inventory.items().find((it) => it.name === 'player_head'), 'hand');
    await placeBallAt(hunter, 0);
    await waitFor(async () => (await status()).overworld[1].state === 'placed', 'the Overworld 1-star registered as placed', 5_000);
    await collectAllSeven(hunter, HUNTER, 'nether');
    await crossDimensionTeleport(rcon, hunter, 'minecraft:the_nether', NETHER_STAND.x, NETHER_STAND.y, NETHER_STAND.z);
    for (let i = 0; i < 7; i++) {
      const star = i + 1;
      const s = await status();
      if (s.nether[star].state !== 'placed') {
        const ball = hunter.inventory.items().find((it) => it.name === 'player_head');
        await hunter.equip(ball, 'hand');
        await placeNetherBallAt(hunter, i);
        // The registry's own record of the placement can lag the BlockPlaceEvent by more than a
        // blind sleep (seen: still "held" 300ms after a placeBlock that didn't throw), so confirm
        // this star actually turned before moving to the next one.
        await waitFor(async () => (await status()).nether[star].state === 'placed', `${star}-star registered as placed`, 5_000);
      }
    }
    const placed = (await status()).nether;
    assert(Object.values(placed).every((v) => v.state === 'placed' && !v.stone),
      `all 7 Nether balls placed and awake before summon (got ${JSON.stringify(placed)})`);

    // The Overworld radar kept from step 2: readiness is READY, but it's the wrong set's radar, so
    // the ball only stirs (same as holding no radar at all) and nothing is spent.
    const overworldRadar = hunter.inventory.items().find((i) => i.name === 'compass');
    assert(overworldRadar, 'DBHunter still holds the Overworld radar bought in step 2');
    await hunter.equip(overworldRadar, 'hand');
    const ballSpot = Object.values(placed)[0];
    const ball = ballHitbox(hunter, ballSpot.x, ballSpot.y, ballSpot.z);
    assert(ball, 'DBHunter sees the floating Nether ball');
    const hintSince = hunter.lines.length;
    // Mineflayer's own actionBar event never fires for ViaBackwards' NBT action bar (see tracker.mjs).
    const recordActionBar = (packet) => {
      if (packet.isActionBar !== false) hunter.lines.push(nbtText(packet.text ?? packet.content));
    };
    hunter._client.on('action_bar', recordActionBar);
    hunter._client.on('system_chat', recordActionBar);
    await hunter.activateEntity(ball);
    await waitForLine(hunter, /The balls stir\.\.\. but need a Nether Dragon Radar/, 5_000, hintSince);
    hunter._client.removeListener('action_bar', recordActionBar);
    hunter._client.removeListener('system_chat', recordActionBar);
    assert(!/passed/i.test(await rcon.command('execute if entity @e[type=ender_dragon,tag=dragonballs.shenron]')),
      'no dragon summoned from the wrong-set radar');
    assert(hunter.inventory.items().some((i) => i.name === 'compass'), 'the Overworld radar was not consumed');

    // Snapshot the Overworld set before the Nether session even starts, to prove dismiss leaves it alone.
    const overworldBefore = (await status()).overworld;

    // A solid roof so the dragon's headroom clamp (the bug this whole feature fixes: a Nether
    // dragon spawning inside the cavern ceiling) has something real to clamp against.
    await addNetherCeiling(rcon, BX, BZ, NETHER_PLATFORM_Y, SUMMON_CEILING_HEIGHT);

    const since = witness.lines.length;
    const reply = plain(await rcon.command(`shenron summon ${HUNTER} shenron nether`));
    assert(!/Cannot summon|not online|No placed|Unknown dragon|Unknown set/.test(reply),
      `Nether summon via RCON reports no error (got "${reply.trim()}")`);
    // A Nether-set Shenron is Nuova Shenron to players (docs/dragonballs.md).
    await waitForLine(witness, /DBHunter has summoned Nuova Shenron!/, 5_000, since);
    await waitFor(async () => /passed/i.test(await rcon.command('execute if entity @e[type=ender_dragon,tag=dragonballs.shenron]')),
      'the Shenron dragon entity exists', 5_000);

    // --- 4a. the Nether dragon is half-scale, in world_nether, and never clipped into a block ----
    const posReply = plain(await rcon.command(
      'minecraft:execute in minecraft:the_nether run data get entity @e[type=ender_dragon,tag=dragonballs.shenron,limit=1] Pos'));
    assert(!/No entity was found/.test(posReply), `the dragon resolves inside world_nether (got "${posReply.trim()}")`);
    const posMatch = posReply.match(/\[(-?\d+\.?\d*)d?,\s*(-?\d+\.?\d*)d?,\s*(-?\d+\.?\d*)d?\]/);
    assert(posMatch, `dragon Pos is parseable (got "${posReply.trim()}")`);
    const dragonX = Math.round(Number(posMatch[1]));
    const dragonY = Math.floor(Number(posMatch[2]));
    const dragonZ = Math.round(Number(posMatch[3]));

    const scaleReply = plain(await rcon.command(
      'minecraft:execute in minecraft:the_nether run attribute @e[type=ender_dragon,tag=dragonballs.shenron,limit=1] minecraft:scale get'));
    const scaleMatch = scaleReply.match(/-?\d+\.?\d*/);
    assert(scaleMatch && Math.abs(Number(scaleMatch[0]) - 0.5) < 1e-6,
      `the Nether dragon's scale attribute is 0.5 (got "${scaleReply.trim()}")`);

    const feetCheck = plain(await rcon.command(
      `minecraft:execute in minecraft:the_nether run execute if block ${dragonX} ${dragonY} ${dragonZ} minecraft:air`));
    assert(/passed/i.test(feetCheck), `the block at the dragon's own position is air (got "${feetCheck.trim()}")`);
    // ~4 blocks is the half-scale body's own height (DragonPlacement.BODY_HEIGHT_AT_SCALE_1 * 0.5);
    // it staying air (well below the ceiling at NETHER_PLATFORM_Y + 10) proves the clamp worked.
    const headCheck = plain(await rcon.command(
      `minecraft:execute in minecraft:the_nether run execute if block ${dragonX} ${dragonY + 4} ${dragonZ} minecraft:air`));
    assert(/passed/i.test(headCheck), `the block just above the dragon's head is air (got "${headCheck.trim()}")`);

    // --- 4b. the Nether's ball-strike flame burst never left real fire behind -----------------------
    for (let i = 0; i < 7; i++) {
      const { x, y, z } = netherBallOffset(i);
      const fireCheck = plain(await rcon.command(`minecraft:execute in minecraft:the_nether run execute if block ${x} ${y + 1} ${z} minecraft:fire`));
      assert(!/passed/i.test(fireCheck), `no real fire at nether ball spot ${i} after the summon's flame burst (got "${fireCheck.trim()}")`);
    }

    const secondSetReply = plain(await rcon.command(`shenron summon ${HUNTER} shenron overworld`));
    assert(/Cannot summon/.test(secondSetReply),
      `a second set's summon is refused while one is already active (got "${secondSetReply.trim()}")`);

    const byeSince = witness.lines.length;
    await rcon.command('shenron dismiss Farewell, Nether.');
    await waitForLine(witness, /Farewell, Nether\./, 5_000, byeSince);
    await waitFor(async () => !/passed/i.test(await rcon.command('execute if entity @e[type=ender_dragon,tag=dragonballs.shenron]')),
      'the dragon despawns once dismissed', 12_000);

    // The Nether scatter radius for a set's first-ever wish is 125-375 blocks (the overworld's
    // 1000-3000 band at 1/8 scale) from the summon cluster, so >100 away is an unambiguous "moved".
    await waitFor(async () => {
      const afterNether = (await status()).nether;
      return Object.values(afterNether).every((v) => v.stone
        && (v.state !== 'placed' || Math.max(Math.abs(v.x - BX), Math.abs(v.z - BZ)) > 100));
    }, 'all 7 Nether balls are stone and have moved away from the summon cluster', 90_000);

    const overworldAfter = (await status()).overworld;
    assert(JSON.stringify(overworldAfter) === JSON.stringify(overworldBefore),
      `the Overworld set is untouched by the Nether set's own scatter (before ${JSON.stringify(overworldBefore)}, after ${JSON.stringify(overworldAfter)})`);

    await removeNetherCeiling(rcon, BX, BZ, NETHER_PLATFORM_Y, SUMMON_CEILING_HEIGHT);

    fs.writeFileSync(configPath, originalConfig);
    configIsShortened = false;
    await rcon.command('dragonballs reload');
  });

  // --- 5. overworld scatter: every star lands inside a structure, verified via the placement log ---
  await step('dragonballs scatter puts every overworld ball inside a structure', async () => {
    const logSizeBefore = fs.existsSync(logPath) ? fs.statSync(logPath).size : 0;
    await rcon.command('dragonballs scatter');
    const placedLines = () => {
      if (!fs.existsSync(logPath)) return [];
      const added = fs.readFileSync(logPath, 'utf8').slice(logSizeBefore);
      return added.match(/\[DragonBalls\] Placed overworld \d-star at .*/g) ?? [];
    };
    // The 7 stars search one after another, a couple of structure lookups per tick each.
    await waitFor(() => placedLines().length >= 7, 'all 7 overworld stars placed', 90_000);
    const lines = placedLines();
    lines.forEach((line) => console.log(`  ${line}`));
    const inStructures = lines.filter((line) => /\(structure \S+\)/.test(line));
    // A plain-ground fallback is legitimate when no listed structure fits the ring even widened,
    // so one is tolerated; more means the structure search itself is broken.
    assert(inStructures.length >= 6,
      `at least 6 of 7 overworld balls landed in a structure (got ${inStructures.length}; lines above)`);
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
  // Safe even if step 4 already removed it (same fill, now a no-op) or never got there.
  await removeNetherCeiling(rcon, BX, BZ, NETHER_PLATFORM_Y, SUMMON_CEILING_HEIGHT).catch(() => {});
  await releaseNetherPlatform(rcon, BX, BZ);
}
