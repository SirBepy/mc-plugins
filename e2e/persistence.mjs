import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { Vec3 } from 'vec3';
import {
  Rcon, assert, createBot, quit, sleep, teleport, tryOpen, waitFor, waitForLine,
} from './lib.mjs';

// Two phases around a real server restart:
//   node persistence.mjs setup   -> make PersA IT, give PersA a long chest grant and PersC a 3s one
//   (restart the e2e server)
//   node persistence.mjs verify  -> IT, head, prefix and the long grant survived; the short one expired
const phase = process.argv[2];
const here = path.dirname(fileURLToPath(import.meta.url));
const stateFile = path.join(here, '..', 'testserver', 'run-e2e', 'persistence-e2e.json');
const PLATFORM_Y = 200;
const PREFIX = '☣';

const rcon = await Rcon.connect();
const bots = {};
const results = [];
async function step(name, fn) {
  await fn();
  results.push(name);
  console.log(`PASS ${name}`);
}

async function join(name, x, z) {
  const bot = await createBot(name);
  bots[name] = bot;
  await rcon.command(`minecraft:gamemode survival ${name}`);
  await teleport(rcon, bot, x, PLATFORM_Y + 1, z);
  return bot;
}

try {
  if (phase === 'setup') {
    const BX = 16 * Math.floor(Math.random() * 40 - 20);
    const BZ = 16 * Math.floor(Math.random() * 40 - 20);
    await rcon.command(`minecraft:forceload add ${BX - 16} ${BZ - 16} ${BX + 16} ${BZ + 16}`);
    await rcon.command(`minecraft:fill ${BX - 8} ${PLATFORM_Y} ${BZ - 8} ${BX + 8} ${PLATFORM_Y} ${BZ + 8} minecraft:stone`);
    await rcon.command(`minecraft:fill ${BX - 8} ${PLATFORM_Y + 1} ${BZ - 8} ${BX + 8} ${PLATFORM_Y + 4} ${BZ + 8} minecraft:air`);
    const owner = await join('PersB', BX, BZ);
    const longGuest = await join('PersA', BX + 2, BZ);
    const shortGuest = await join('PersC', BX + 4, BZ);

    await rcon.command('minecraft:give PersB minecraft:chest 1');
    await waitFor(() => owner.inventory.items().some((i) => i.name === 'chest'), 'chest given');
    await owner.equip(owner.inventory.items().find((i) => i.name === 'chest'), 'hand');
    const floor = owner.blockAt(new Vec3(BX, PLATFORM_Y, BZ + 3));
    await owner.lookAt(floor.position.offset(0.5, 1, 0.5), true);
    await owner.placeBlock(floor, new Vec3(0, 1, 0));

    owner.chat('/safe-chests-whitelist PersA 1h');
    await waitForLine(owner, /PersA can now open all your chests/);
    owner.chat('/safe-chests-whitelist PersC 3s');
    await waitForLine(owner, /PersC can now open all your chests/);
    await rcon.command('tag set PersA');
    await waitFor(() => longGuest.inventory.slots[5]?.name === 'player_head', 'PersA wears the head');
    fs.writeFileSync(stateFile, JSON.stringify({ BX, BZ, chest: [BX, PLATFORM_Y + 1, BZ + 3] }));
    // The supervisor restart kills the process, which would drop unsaved chunks (the chest itself);
    // plugin state files are written on every change and need no flush.
    await rcon.command('save-all flush');
    console.log('setup done: restart the e2e server, then run "node persistence.mjs verify"');
    void shortGuest;
  } else if (phase === 'verify') {
    const { BX, BZ, chest } = JSON.parse(fs.readFileSync(stateFile, 'utf8'));
    await rcon.command(`minecraft:forceload add ${BX - 16} ${BZ - 16} ${BX + 16} ${BZ + 16}`);

    await step('IT survived the restart while PersA was offline', async () => {
      const who = (await rcon.command('tag who')).replace(/§./g, '');
      assert(who.includes('PersA is IT'), who);
    });

    const owner = await join('PersB', BX, BZ);
    const longGuest = await join('PersA', BX + 2, BZ);
    const shortGuest = await join('PersC', BX + 4, BZ);
    const chestBlock = longGuest.blockAt(new Vec3(...chest));

    await step('PersA still wears the head and shows the IT prefix after rejoining', async () => {
      await waitFor(() => longGuest.inventory.slots[5]?.name === 'player_head', 'head on');
      await waitFor(() => owner.players.PersA?.displayName?.toString().includes(PREFIX), 'tab prefix');
    });

    await step('the 1h chest grant survived the restart', async () => {
      assert(chestBlock?.name === 'chest', 'chest still there');
      assert(await tryOpen(longGuest, chestBlock), 'PersA opens');
    });

    await step('the 3s chest grant that expired during downtime is revoked', async () => {
      assert(!(await tryOpen(shortGuest, shortGuest.blockAt(new Vec3(...chest)))), 'PersC denied');
    });

    owner.chat('/safe-chests-unwhitelist PersA');
    await sleep(500);
    await rcon.command('tag clear');
    await rcon.command(`minecraft:forceload remove ${BX - 16} ${BZ - 16} ${BX + 16} ${BZ + 16}`);
    fs.rmSync(stateFile);
    console.log(`\nPersistence e2e: ${results.length} passed`);
  } else {
    throw new Error('usage: node persistence.mjs setup|verify');
  }
} catch (error) {
  console.error(`FAIL after ${results.length} passed: ${error.message}`);
  process.exitCode = 1;
} finally {
  await quit(...Object.values(bots));
  rcon.close();
}
