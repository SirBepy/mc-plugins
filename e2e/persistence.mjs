import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { Vec3 } from 'vec3';
import {
  Rcon, assert, buildPlatform, createBot, quit, randomOrigin, releasePlatform, sleep, teleport,
  textDisplays, tryOpen, waitFor, waitForLine,
} from './lib.mjs';

// Two phases around a real server restart:
//   node persistence.mjs setup   -> place the in-world leaderboard at PersB, make PersA IT, give PersA a long chest grant and PersC a 3s one,
//                                    then whitelist and immediately unwhitelist PersD with no pause
//                                    before the restart (see PersD below for why)
//   (restart the e2e server)
//   node persistence.mjs verify  -> IT, head, prefix and the long grant survived; the short one
//                                    expired; PersD's revoke (made right before the kill) stayed revoked
const phase = process.argv[2];
const here = path.dirname(fileURLToPath(import.meta.url));
const stateFile = path.join(here, '..', 'testserver', 'run-e2e', 'persistence-e2e.json');
const boltDb = path.join(here, '..', 'testserver', 'run-e2e', 'plugins', 'Bolt', 'bolt.db');

/** The access map Bolt has written to disk for the protection at [x, y, z], or undefined if none yet. */
async function savedBoltAccess([x, y, z]) {
  const { DatabaseSync } = await import('node:sqlite');
  const db = new DatabaseSync(boltDb, { readOnly: true });
  try {
    const row = db.prepare('SELECT access FROM blocks WHERE x = ? AND y = ? AND z = ?').get(x, y, z);
    return row ? JSON.parse(row.access) : undefined;
  } finally {
    db.close();
  }
}
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
    const { bx: BX, bz: BZ } = randomOrigin();
    await buildPlatform(rcon, BX, BZ, PLATFORM_Y);
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
    // Bolt writes new protections and access changes to bolt.db only every 30 s, and the supervisor
    // restart is a hard kill: without this wait the chest itself could come back unprotected, so
    // anyone (PersC included) could open it. PersC's 3s grant may already be swept by then, so only
    // PersA's entry is awaited; PersC's and PersD's revokes are what the restart must not undo.
    await waitFor(async () => (await savedBoltAccess([BX, PLATFORM_Y + 1, BZ + 3]))?.[`player:${longGuest.player.uuid}`],
      'Bolt saved the chest with PersA access', 40_000);

    await join('PersD', BX + 6, BZ);
    owner.chat('/safe-chests-whitelist PersD 1h');
    await waitForLine(owner, /PersD can now open all your chests/);
    owner.chat('/safe-chests-unwhitelist PersD');
    await waitForLine(owner, /PersD can no longer open your chests/);
    // No wait here before the restart below: GrantManager's revoke tombstone (grants.yml), not a live
    // sweep having time to run, is what must keep PersD out - Bolt's own save for this revoke
    // (BoltAPI.saveProtection -> SQLStore.saveBlockProtection's discarded CompletableFuture.runAsync)
    // is deliberately given no chance to land before the kill.

    await rcon.command('tag set PersA');
    await waitFor(() => longGuest.inventory.slots[5]?.name === 'player_head', 'PersA wears the head');

    await rcon.command('op PersB');
    owner.chat('/tag leaderboard place');
    await waitForLine(owner, /Leaderboard placed/);
    await rcon.command('deop PersB');
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

    await step('a whitelist revoke made right before the hard kill stays revoked after boot', async () => {
      const deniedGuest = await join('PersD', BX + 6, BZ);
      assert(!(await tryOpen(deniedGuest, deniedGuest.blockAt(new Vec3(...chest)))), 'PersD denied');
    });

    await step('the in-world leaderboard is back after the restart', async () => {
      await waitFor(() => textDisplays(longGuest).some((d) => /Tag leaderboard/.test(d.text)
        && d.entity.position.distanceTo(owner.entity.position) < 3), 'PersA sees the board at PersB', 8_000);
    });

    owner.chat('/safe-chests-unwhitelist PersA');
    await rcon.command('op PersB');
    owner.chat('/tag leaderboard remove');
    await waitForLine(owner, /Leaderboard removed/);
    await rcon.command('deop PersB');
    await sleep(500);
    await rcon.command('tag clear');
    await releasePlatform(rcon, BX, BZ);
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
