import {
  Rcon, assert, buildPlatform, createBot, nbtText, quit, randomOrigin, releasePlatform, sleep, teleport, waitFor,
  waitForLine,
} from './lib.mjs';

// Haru, ImMijo's Shenron-wish merchant: right-clicking him opens his full chest menu for his owner,
// emerald wares can be bought from it, nothing can be taken out of it, strangers get a word and only
// the Everyday Wares, and a killed Haru is back on the next guard tick (every 5 s). The suite makes its own bot the
// owner (`haru owner`), since the e2e world's ImMijo never joins.
const PLATFORM_Y = 200;
const { bx: BX, bz: BZ } = randomOrigin();
const OWNER = 'HaruOwner';
const GUEST = 'HaruGuest';
const SPOT = { x: BX + 0.5, y: PLATFORM_Y + 1, z: BZ + 0.5 };
const AT_SPOT = `x=${SPOT.x},y=${SPOT.y},z=${SPOT.z},distance=..1`;

const rcon = await Rcon.connect();
const bots = [];
const results = [];
async function step(name, fn) {
  await fn();
  results.push(name);
  console.log(`PASS ${name}`);
}

// Window titles arrive as NBT text components; JSON.stringify keeps any text nbtText can't reach.
const titleOf = (window) => (typeof window?.title === 'string' ? window.title
  : `${nbtText(window?.title)} ${JSON.stringify(window?.title)}`);
const containerSize = (window) => window.slots.length - 36;
const haruEntity = (bot) => Object.values(bot.entities)
  .find((e) => e.name === 'villager' && e.position.distanceTo(bot.entity.position) < 6);

function nextWindow(bot, timeoutMs = 5_000) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(`${bot.username}: no window opened`)), timeoutMs);
    bot.once('windowOpen', (window) => {
      clearTimeout(timer);
      resolve(window);
    });
  });
}

// Counted server-side: Mineflayer's own inventory lags while a container window is open.
async function count(player, item) {
  const out = await rcon.command(`minecraft:clear ${player} minecraft:${item} 0`);
  const match = out.match(/Found (\d+)/);
  return match ? Number(match[1]) : 0;
}

try {
  await buildPlatform(rcon, BX, BZ, PLATFORM_Y);
  const owner = await createBot(OWNER);
  const guest = await createBot(GUEST);
  bots.push(owner, guest);
  for (const bot of bots) {
    await rcon.command(`minecraft:gamemode survival ${bot.username}`);
    await rcon.command(`minecraft:clear ${bot.username}`);
  }
  await rcon.command(`haru owner ${OWNER}`);
  await rcon.command(`haru place world ${SPOT.x} ${SPOT.y} ${SPOT.z} 0`);
  await teleport(rcon, owner, BX, PLATFORM_Y + 1, BZ + 3);
  await teleport(rcon, guest, BX + 2, PLATFORM_Y + 1, BZ + 3);

  await step('Haru stands at his spot as an AI-less, invulnerable villager', async () => {
    assert(/Test passed/.test(await rcon.command(`minecraft:execute if entity @e[type=villager,${AT_SPOT}]`)), 'villager at the spot');
    const noAi = await rcon.command(`minecraft:data get entity @e[type=villager,${AT_SPOT},limit=1] NoAI`);
    assert(/1b/.test(noAi), `NoAI set (got ${noAi})`);
    const invulnerable = await rcon.command(`minecraft:data get entity @e[type=villager,${AT_SPOT},limit=1] Invulnerable`);
    assert(/1b/.test(invulnerable), `Invulnerable set (got ${invulnerable})`);
  });

  let menu;
  await step('the owner right-clicks Haru and gets his menu and a greeting', async () => {
    await waitFor(() => haruEntity(owner), 'owner sees Haru');
    const since = owner.lines.length;
    const opened = nextWindow(owner);
    await owner.activateEntity(haruEntity(owner));
    menu = await opened;
    assert(/Sanctuary/.test(titleOf(menu)), `main menu title (got ${titleOf(menu)})`);
    assert(containerSize(menu) === 54, `six rows (got ${containerSize(menu)})`);
    await waitForLine(owner, /Haru/, 5_000, since);
  });

  await step('nothing can be taken out of the menu', async () => {
    const before = owner.inventory.items().length;
    await owner.clickWindow(0, 0, 0);
    await sleep(500);
    assert(!owner.inventory.cursorItem && owner.inventory.items().length === before, 'the pane stayed in the menu');
  });

  await step('buying 5 redstone for 2 emeralds from the Everyday Wares', async () => {
    await rcon.command(`minecraft:give ${OWNER} minecraft:emerald 10`);
    await waitFor(async () => (await count(OWNER, 'emerald')) === 10, 'owner holds 10 emeralds');
    const opened = nextWindow(owner);
    await owner.clickWindow(20, 0, 0);
    const wares = await opened;
    assert(/Everyday Wares/.test(titleOf(wares)), `wares title (got ${titleOf(wares)})`);
    const redstoneSlot = wares.slots.findIndex((s, i) => i < containerSize(wares) && s && s.name === 'redstone');
    assert(redstoneSlot >= 0, 'redstone is on the shelf');
    await owner.clickWindow(redstoneSlot, 0, 0);
    await waitFor(async () => (await count(OWNER, 'redstone')) === 5, 'owner got 5 redstone', 5_000);
    const emeralds = await count(OWNER, 'emerald');
    assert(emeralds === 8, `paid 2 emeralds (has ${emeralds})`);
    owner.closeWindow(wares);
  });

  await step('a stranger gets a polite word and only the Everyday Wares', async () => {
    await waitFor(() => haruEntity(guest), 'guest sees Haru');
    const since = guest.lines.length;
    const opened = nextWindow(guest);
    await guest.activateEntity(haruEntity(guest));
    const shelf = await opened;
    assert(/Everyday Wares/.test(titleOf(shelf)), `guest shelf title (got ${titleOf(shelf)})`);
    await waitForLine(guest, /seven stars|I serve|spoken for/, 5_000, since);
    guest.closeWindow(shelf);
  });

  await step('a killed Haru is back within the next guard tick', async () => {
    await rcon.command(`minecraft:kill @e[type=villager,${AT_SPOT}]`);
    await waitFor(async () => /Test passed/.test(await rcon.command(`minecraft:execute if entity @e[type=villager,${AT_SPOT}]`)),
      'Haru back at his spot', 10_000);
  });

  console.log(`\nHaru e2e: ${results.length} passed`);
} catch (error) {
  console.error(`FAIL after ${results.length} passed: ${error.message}`);
  process.exitCode = 1;
} finally {
  await rcon.command('haru remove').catch(() => {});
  await releasePlatform(rcon, BX, BZ);
  await quit(...bots);
  rcon.close();
}
