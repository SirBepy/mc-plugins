import {
  Rcon, assert, buildPlatform, createBot, quit, randomOrigin, releasePlatform, sleep, teleport,
  waitFor, waitForLine,
} from './lib.mjs';

// Towny land claims must never block a tag: TownA founds a town on the platform (console-created,
// so no money is involved), town PvP is turned off, and IT tags a visitor standing inside it.
const PLATFORM_Y = 200;
const { bx: BX, bz: BZ } = randomOrigin();
const TOWN = `e2etown${Date.now() % 100000}`;

const rcon = await Rcon.connect();
const bots = {};
const results = [];
async function step(name, fn) {
  await fn();
  results.push(name);
  console.log(`PASS ${name}`);
}

const plain = (s) => s.replace(/§./g, '');

try {
  await buildPlatform(rcon, BX, BZ, PLATFORM_Y);
  await rcon.command('tag clear');
  for (const [name, dx] of [['TownA', 0], ['TownB', 2]]) {
    const bot = await createBot(name);
    bots[name] = bot;
    await rcon.command(`minecraft:gamemode survival ${name}`);
    await rcon.command(`minecraft:clear ${name}`);
    await teleport(rcon, bot, BX + dx, PLATFORM_Y + 1, BZ);
    bot.chat('/tag optin');
  }
  const { TownA, TownB } = bots;
  await sleep(1000);

  await step('a town is founded on the platform with PvP off', async () => {
    await rcon.command(`townyadmin town new ${TOWN} TownA`);
    await waitFor(async () => plain(await rcon.command(`townyadmin town ${TOWN} toggle pvp off`))
      .includes('PVP has been Disabled'), 'town exists and PvP is off', 10_000);
  });

  await step('IT tags a visitor inside the town, dealing no damage', async () => {
    await rcon.command('tag set TownA');
    await waitFor(() => TownA.inventory.slots[5]?.name === 'player_head', 'TownA wears the head');
    await waitFor(() => TownA.players.TownB?.entity, 'TownA can see TownB');
    const target = TownA.players.TownB.entity;
    const healthBefore = TownB.health;
    const mark = TownB.lines.length;
    await TownA.lookAt(target.position.offset(0, 1.6, 0), true);
    TownA.attack(target);
    await waitForLine(TownB, /TownA tagged TownB/, 5_000, mark);
    assert(plain(await rcon.command('tag who')).includes('TownB is IT'), 'TownB is IT');
    await sleep(500);
    assert(TownB.health === healthBefore, `no damage (${healthBefore} -> ${TownB.health})`);
  });

  console.log(`\nTowny e2e: ${results.length} passed`);
} catch (error) {
  console.error(`FAIL after ${results.length} passed: ${error.message}`);
  process.exitCode = 1;
} finally {
  await rcon.command(`townyadmin town ${TOWN} delete`).catch(() => {});
  await rcon.command('confirm').catch(() => {});
  await rcon.command('tag clear').catch(() => {});
  await releasePlatform(rcon, BX, BZ);
  await quit(...Object.values(bots));
  rcon.close();
}
