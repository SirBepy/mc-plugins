import { Vec3 } from 'vec3';
import {
  Rcon, assert, createBot, quit, sleep, teleport, tryOpen, waitFor, waitForClick, waitForLine,
} from './lib.mjs';

// Owner places chests on a stone platform high above terrain; Guest tries every access path.
const PLATFORM_Y = 200;
// A fresh spot every run, so protections and grants left by an earlier run can't leak in.
const BX = 16 * Math.floor(Math.random() * 250 - 125);
const BZ = 16 * Math.floor(Math.random() * 250 - 125);
const rcon = await Rcon.connect();
let owner;
let guest;

async function placeChest(bot, x, z) {
  const gave = await rcon.command(`minecraft:give ${bot.username} minecraft:chest 1`);
  await waitFor(() => bot.inventory.items().some((i) => i.name === 'chest'), `chest in inventory (give said: ${gave.trim()})`);
  await bot.equip(bot.inventory.items().find((i) => i.name === 'chest'), 'hand');
  const floor = bot.blockAt(new Vec3(x, PLATFORM_Y, z));
  await bot.lookAt(floor.position.offset(0.5, 1, 0.5), true);
  await bot.placeBlock(floor, new Vec3(0, 1, 0));
  const chest = bot.blockAt(new Vec3(x, PLATFORM_Y + 1, z));
  assert(chest?.name === 'chest', `chest placed at ${x},${z}`);
  return chest;
}

async function lookAtBlock(bot, block) {
  await bot.lookAt(block.position.offset(0.5, 0.5, 0.5), true);
  await sleep(300);
}

const results = [];
async function step(name, fn) {
  await fn();
  results.push(name);
  console.log(`PASS ${name}`);
}

try {
  // /fill silently does nothing in unloaded chunks.
  await rcon.command(`minecraft:forceload add ${BX - 16} ${BZ - 16} ${BX + 16} ${BZ + 16}`);
  await rcon.command(`minecraft:fill ${BX - 8} ${PLATFORM_Y} ${BZ - 8} ${BX + 8} ${PLATFORM_Y} ${BZ + 8} minecraft:stone`);
  await rcon.command(`minecraft:fill ${BX - 8} ${PLATFORM_Y + 1} ${BZ - 8} ${BX + 8} ${PLATFORM_Y + 4} ${BZ + 8} minecraft:air`);
  owner = await createBot('ChestOwner');
  guest = await createBot('ChestGuest');
  for (const bot of [owner, guest]) {
    await rcon.command(`minecraft:gamemode survival ${bot.username}`);
    await rcon.command(`minecraft:clear ${bot.username}`);
  }
  owner.chat('/safe-chests-unwhitelist ChestGuest');
  await teleport(rcon, owner, BX, PLATFORM_Y + 1, BZ);
  await teleport(rcon, guest, BX + 2, PLATFORM_Y + 1, BZ);

  const chest = await placeChest(owner, BX, BZ + 3);
  await sleep(500);

  await step('owner can open own chest', async () => {
    assert(await tryOpen(owner, chest), 'owner opens');
  });

  await step('guest is denied with a request-access prompt', async () => {
    const mark = guest.lines.length;
    assert(!(await tryOpen(guest, chest)), 'guest must not open');
    await waitForLine(guest, /belongs to ChestOwner/, 5_000, mark);
    await waitForClick(guest, /^\/safe-chests-one-time$/);
  });

  let promptMark;
  await step('request reaches owner with allow-once/5 min/always/deny buttons', async () => {
    promptMark = owner.clicks.length;
    await lookAtBlock(guest, chest);
    guest.chat('/safe-chests-one-time');
    await waitForLine(owner, /ChestGuest wants to open your chest/);
    for (const action of ['allow-once', 'five-min', 'always', 'deny']) {
      await waitForClick(owner, new RegExp(`^/safe-chests-respond \\S+ ${action}$`));
    }
  });

  await step('a non-owner cannot answer the request', async () => {
    const allowOnce = owner.clicks.slice(promptMark).find((c) => c.endsWith(' allow-once'));
    const mark = guest.lines.length;
    guest.chat(allowOnce);
    // The plugin deliberately answers impostors like an unknown id, revealing nothing.
    await waitForLine(guest, /not your request|no longer exists/, 5_000, mark);
  });

  await step('allow once opens exactly once', async () => {
    const allowOnce = owner.clicks.slice(promptMark).find((c) => c.endsWith(' allow-once'));
    owner.chat(allowOnce);
    await waitForLine(guest, /open ChestOwner's chest once/);
    assert(await tryOpen(guest, chest), 'first open allowed');
    await sleep(500);
    assert(!(await tryOpen(guest, chest)), 'second open denied');
  });

  await step('deny button tells the requester', async () => {
    const mark = owner.clicks.length;
    await lookAtBlock(guest, chest);
    guest.chat('/safe-chests-one-time');
    const deny = await (async () => {
      await waitFor(() => owner.clicks.slice(mark).some((c) => c.endsWith(' deny')), 'deny button');
      return owner.clicks.slice(mark).find((c) => c.endsWith(' deny'));
    })();
    owner.chat(deny);
    await waitForLine(guest, /ChestOwner denied your request/);
    assert(!(await tryOpen(guest, chest)), 'still denied');
  });

  await step('timed whitelist opens all chests, then expires', async () => {
    owner.chat('/safe-chests-whitelist ChestGuest 5s');
    await waitForLine(guest, /can now open all of ChestOwner's chests/);
    assert(await tryOpen(guest, chest), 'whitelisted open');
    await waitFor(async () => !(await tryOpen(guest, chest, 1_500)), 'timed grant revoked', 40_000);
  });

  await step('permanent whitelist also covers chests placed afterwards', async () => {
    owner.chat('/safe-chests-whitelist ChestGuest');
    await waitForLine(owner, /ChestGuest can now open all your chests/);
    const second = await placeChest(owner, BX - 3, BZ + 3);
    await sleep(500);
    assert(await tryOpen(guest, chest), 'old chest');
    assert(await tryOpen(guest, second), 'chest placed after the grant');
  });

  await step('unwhitelist revokes access', async () => {
    owner.chat('/safe-chests-unwhitelist ChestGuest');
    await waitForLine(owner, /ChestGuest can no longer open your chests/);
    await sleep(300);
    assert(!(await tryOpen(guest, chest)), 'denied after unwhitelist');
  });

  await step('request while owner is offline says so', async () => {
    owner.quit();
    owner = undefined;
    await sleep(1500);
    const mark = guest.lines.length;
    await lookAtBlock(guest, chest);
    guest.chat('/safe-chests-one-time');
    await waitForLine(guest, /ChestOwner is offline/, 5_000, mark);
  });

  console.log(`\nSafeChests e2e: ${results.length} passed`);
} catch (error) {
  console.error(`FAIL after ${results.length} passed: ${error.message}`);
  process.exitCode = 1;
} finally {
  await rcon.command(`minecraft:forceload remove ${BX - 16} ${BZ - 16} ${BX + 16} ${BZ + 16}`).catch(() => {});
  await quit(owner, guest);
  rcon.close();
}
