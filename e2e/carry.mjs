import { Vec3 } from 'vec3';
import {
  Rcon, assert, buildPlatform, createBot, quit, randomOrigin, releasePlatform, sleep, teleport, waitFor,
} from './lib.mjs';

// Carry: a bot picks a cow up (sneak + right-click), puts it down on a block, throws it, loses it
// on teleport, and an outsider is refused inside someone else's Towny town.
const PLATFORM_Y = 200;
const { bx: BX, bz: BZ } = randomOrigin();
const TOWN = `e2ecarry${Date.now() % 100000}`;
const COW = '@e[type=minecraft:cow,tag=e2e_carry,limit=1]';

const rcon = await Rcon.connect();
const bots = {};
const results = [];
async function step(name, fn) {
  await fn();
  results.push(name);
  console.log(`PASS ${name}`);
}

const plain = (s) => s.replace(/§./g, '');

async function cowIsCarried() {
  return /Test passed/.test(await rcon.command(`minecraft:execute as ${COW} on vehicle if entity @s[type=minecraft:player]`));
}

async function cowPosition() {
  const out = await rcon.command(`minecraft:data get entity ${COW} Pos`);
  const nums = [...out.matchAll(/(-?\d+(?:\.\d+)?)d/g)].map((m) => Number(m[1]));
  assert(nums.length === 3, `cow position readable (got ${out.trim()})`);
  return new Vec3(nums[0], nums[1], nums[2]);
}

function cowEntity(bot) {
  return bot.nearestEntity((e) => e.name === 'cow');
}

// The server takes the sneak state from the interact packet itself, and Mineflayer's
// activateEntity always sends sneaking: false, so the packet is written by hand.
async function pickUp(bot) {
  await waitFor(() => cowEntity(bot), `${bot.username} can see the cow`);
  const cow = cowEntity(bot);
  await bot.lookAt(cow.position.offset(0, 0.7, 0), true);
  bot._client.write('use_entity', { target: cow.id, hand: 0, location: { x: 0, y: 0.5, z: 0 }, sneaking: true });
  await sleep(500);
}

try {
  await buildPlatform(rcon, BX, BZ, PLATFORM_Y);
  for (const [name, dx] of [['CarryOwner', 0], ['CarryThief', -4]]) {
    const bot = await createBot(name);
    bots[name] = bot;
    await rcon.command(`minecraft:gamemode survival ${name}`);
    await rcon.command(`minecraft:clear ${name}`);
    await teleport(rcon, bot, BX + dx, PLATFORM_Y + 1, BZ);
  }
  const { CarryOwner: owner, CarryThief: thief } = bots;
  await rcon.command(`minecraft:summon minecraft:cow ${BX + 2} ${PLATFORM_Y + 1} ${BZ} {Tags:["e2e_carry"]}`);
  await sleep(500);

  await step('sneak + right-click picks the cow up', async () => {
    await pickUp(owner);
    await waitFor(cowIsCarried, 'cow rides the owner', 5_000);
  });

  await step('right-clicking a block puts the cow down on it', async () => {
    const floor = owner.blockAt(new Vec3(BX + 3, PLATFORM_Y, BZ + 2));
    await owner.lookAt(floor.position.offset(0.5, 1, 0.5), true);
    await owner.activateBlock(floor, new Vec3(0, 1, 0)).catch(() => {});
    await waitFor(async () => !(await cowIsCarried()), 'cow dismounted', 5_000);
    const pos = await cowPosition();
    assert(Math.floor(pos.x) === BX + 3 && Math.floor(pos.z) === BZ + 2, `cow on the clicked block (got ${pos})`);
  });

  await step('sneak + left-click throws the cow', async () => {
    await pickUp(owner);
    await waitFor(cowIsCarried, 'cow rides the owner again', 5_000);
    const start = owner.entity.position.clone();
    await owner.look(-Math.PI / 2, 0, true); // yaw -90 deg in mineflayer faces +x
    owner.setControlState('sneak', true);
    await sleep(300);
    owner.swingArm('right');
    await sleep(1500);
    owner.setControlState('sneak', false);
    assert(!(await cowIsCarried()), 'cow no longer carried');
    const pos = await cowPosition();
    assert(pos.distanceTo(start) > 2, `cow landed away from the thrower (thrower ${start}, cow ${pos})`);
  });

  await step('teleporting puts the cow down at the departure point', async () => {
    await rcon.command(`minecraft:tp ${COW} ${BX + 1} ${PLATFORM_Y + 1} ${BZ}`);
    await teleport(rcon, owner, BX, PLATFORM_Y + 1, BZ);
    await pickUp(owner);
    await waitFor(cowIsCarried, 'cow rides the owner before the teleport', 5_000);
    await teleport(rcon, owner, BX - 6, PLATFORM_Y + 1, BZ - 6);
    assert(!(await cowIsCarried()), 'cow left behind');
    const pos = await cowPosition();
    assert(pos.distanceTo(new Vec3(BX, PLATFORM_Y + 1, BZ)) < 3, `cow stayed near the departure point (got ${pos})`);
  });

  await step('an outsider cannot pick up a cow inside someone else\'s town', async () => {
    await teleport(rcon, owner, BX, PLATFORM_Y + 1, BZ);
    await rcon.command(`townyadmin town new ${TOWN} CarryOwner`);
    await waitFor(async () => plain(await rcon.command(`townyadmin town ${TOWN} toggle pvp off`))
      .includes('PVP has been Disabled'), 'town exists', 10_000);
    // The PvP toggle confirms the town exists, not that Towny's permission cache for this claim is
    // warm yet; a thief attempt landing in that gap is let through, only for the retry below to
    // see it refused once the cache catches up.
    await teleport(rcon, thief, BX + 1, PLATFORM_Y + 1, BZ + 1);
    await waitFor(async () => {
      await pickUp(thief);
      await sleep(500);
      if (!(await cowIsCarried())) return true;
      await pickUp(thief); // let go so the next attempt starts clean
      return false;
    }, 'thief was refused', 5_000);
  });

  await step('the town\'s own mayor can still carry it', async () => {
    await pickUp(owner);
    await waitFor(cowIsCarried, 'cow rides the mayor', 5_000);
  });

  console.log(`\nCarry e2e: ${results.length} passed`);
} catch (error) {
  console.error(`FAIL after ${results.length} passed: ${error.message}`);
  process.exitCode = 1;
} finally {
  await rcon.command(`townyadmin town ${TOWN} delete`).catch(() => {});
  await rcon.command('confirm').catch(() => {});
  await rcon.command('minecraft:kill @e[type=minecraft:cow,tag=e2e_carry]').catch(() => {});
  await releasePlatform(rcon, BX, BZ);
  await quit(...Object.values(bots));
  rcon.close();
}
