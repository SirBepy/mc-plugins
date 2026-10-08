import { Vec3 } from 'vec3';
import { assert, sleep, teleport, waitFor, waitForLine } from './lib.mjs';
import { HUNTER, PLATFORM_Y, WITNESS, ballHitbox, openSuite } from './dragonballs-lib.mjs';

// Dragon Balls, ball lifecycle: status, the dupe guard, slowness while carrying, the container and
// hopper guards, death drops and place/break. Radar and summon live in dragonballs-summon.mjs.
const {
  rcon, BX, BZ, STAND, hunter, witness, step, plain, ballOffset, status: rawStatus, near, speedModifier, placeBallAt,
  tossTowardCentre, ballEntityNear, openWindow, collectAllSeven, finish,
} = await openSuite('DragonBalls');
// This suite only ever touches the overworld set; flatten to the familiar star-keyed map so the
// rest of the file (written before the 3-set split) reads unchanged.
const status = async () => (await rawStatus()).overworld;

try {
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
      await tossTowardCentre(hunter);
      await sleep(300);
      const s = await status();
      const dropped = Object.entries(s).find(([, v]) => v.state === 'dropped');
      assert(dropped, `a star is dropped after toss (got ${JSON.stringify(s)})`);
      const [, loc] = dropped;
      // The registry's location is where the ball left the hand; a tossed item flies on for a block
      // or two, so walk the bot onto where the entity actually landed (after its pickup delay).
      await sleep(1_700);
      const pos = await rcon.command(
        `data get entity ${ballEntityNear(loc)} Pos`);
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
    // The hunter steps back across the platform at once, before the ~2 s pickup delay ends, or it
    // re-grabs its own ball before the witness gets there.
    await tossTowardCentre(hunter);
    await rcon.command(`minecraft:tp ${HUNTER} ${BX - 6} ${PLATFORM_Y + 1} ${BZ - 6}`);
    await waitFor(async () => near(await speedModifier(HUNTER), -0.10), 'dropping one ball updates the modifier to -0.10', 3_000);

    const s = await status();
    const [starStr, loc] = Object.entries(s).find(([, v]) => v.state === 'dropped');
    witnessPickedUpStar = Number(starStr);
    const landed = await rcon.command(`data get entity ${ballEntityNear(loc)} Pos`);
    const [lx, ly, lz] = (landed.match(/-?\d+(\.\d+)?(?=d)/g) ?? [loc.x, loc.y, loc.z]).map(Number);

    // A DIFFERENT player picking it up (not the dropper) must still get the "found" broadcast -
    // recentlySelfDropped only suppresses it for the player who dropped it within the last 30s.
    const since = witness.lines.length;
    await teleport(rcon, witness, lx, ly, lz);
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
    await hunter.tossStack(ball);
    // The first head in the bot's inventory isn't necessarily the lowest star, so read which one left.
    // Waiting for it also makes sure the server handled the drop before the teleport below, or the
    // ball would spawn at the hunter's new spot.
    let starThrown;
    await waitFor(async () => {
      starThrown = Object.entries(await status()).find(([, v]) => v.state === 'dropped')?.[0];
      return starThrown !== undefined;
    }, 'one ball is dropped after the toss', 1_000);
    // The hunter would re-grab its own ball once the 1.5 s pickup delay ends, so it steps away, and
    // the ball is set down right on top of the hopper.
    await rcon.command(`minecraft:tp ${HUNTER} ${BX - 6} ${PLATFORM_Y + 1} ${BZ - 6}`);
    await rcon.command(`minecraft:execute as ${ballEntityNear({ ...hopperPos, y: hopperPos.y + 1 })} run tp @s ${hopperPos.x + 0.5} ${hopperPos.y + 1} ${hopperPos.z + 0.5}`);
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

  // --- 7. place/pick up: a placed ball floats (no head block) and hitting it gives it back ----------
  await step('a placed ball floats, spins and hitting it gives the exact same ball back', async () => {
    await teleport(rcon, hunter, STAND.x, STAND.y, STAND.z);
    const ball = hunter.inventory.items().find((i) => i.name === 'player_head');
    await hunter.equip(ball, 'hand');
    const countBefore = hunter.inventory.items().filter((i) => i.name === 'player_head').length;
    await placeBallAt(hunter, 0);
    // placeBallAt floats the ball in the block above the floor block at ballOffset.
    const { x, z } = ballOffset(0);
    const y = ballOffset(0).y + 1;
    const blockThere = plain(await rcon.command(`minecraft:execute if block ${x} ${y} ${z} minecraft:air`));
    assert(/passed/i.test(blockThere), `no head block is left where the ball floats (got ${blockThere.trim()})`);
    const display = plain(await rcon.command(
      `minecraft:execute if entity @e[type=minecraft:item_display,tag=dragonballs.placed_ball,x=${x + 0.5},y=${y + 0.5},z=${z + 0.5},distance=..0.1]`));
    assert(/passed/i.test(display), `a ball display floats in the block's centre (got ${display.trim()})`);
    assert(hunter.inventory.items().filter((i) => i.name === 'player_head').length === countBefore - 1,
      'placing took the ball out of the inventory');
    const before = await status();
    const placedStar = Object.entries(before).find(([, v]) => v.state === 'placed' && v.x === x && v.z === z)[0];

    const spinBefore = plain(await rcon.command(
      `minecraft:data get entity @e[type=minecraft:item_display,tag=dragonballs.placed_ball,limit=1,x=${x + 0.5},y=${y + 0.5},z=${z + 0.5},distance=..0.1] transformation.left_rotation`));
    await sleep(2_200);
    const spinAfter = plain(await rcon.command(
      `minecraft:data get entity @e[type=minecraft:item_display,tag=dragonballs.placed_ball,limit=1,x=${x + 0.5},y=${y + 0.5},z=${z + 0.5},distance=..0.1] transformation.left_rotation`));
    assert(spinBefore !== spinAfter, `the ball turned (before ${spinBefore.trim()}, after ${spinAfter.trim()})`);

    hunter.attack(ballHitbox(hunter, x, y, z));
    await waitFor(() => hunter.inventory.items().filter((i) => i.name === 'player_head').length === countBefore,
      'hitting it returns exactly one ball item', 3_000);
    const gone = plain(await rcon.command(
      `minecraft:execute if entity @e[tag=dragonballs.placed_ball_hitbox,x=${x + 0.5},y=${y},z=${z + 0.5},distance=..1]`));
    assert(!/passed/i.test(gone), `the floating ball is gone once picked up (got ${gone.trim()})`);
    const after = await status();
    assert(after[placedStar].state === 'held' && after[placedStar].holder === HUNTER,
      `star ${placedStar} is held by DBHunter again (got ${JSON.stringify(after[placedStar])})`);
  });

  await finish();
} catch (error) {
  await finish(error);
}
