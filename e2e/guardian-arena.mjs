import {
  Rcon, assert, buildPlatform, createBot, quit, randomOrigin, releasePlatform, sleep, teleport, waitFor, waitForLine,
} from './lib.mjs';

// Guardian arenas, played by one bot: with the Sentinel's arena set, a seed used outside it is refused,
// used inside it the Sentinel rises at the arena's center, the bot is switched to Adventure and can
// neither walk nor be teleported out, and killing the Sentinel releases it back to Survival. Ashen
// Embers are refused everywhere while the Tyrant has no arena.
const PLATFORM_Y = 200;
const { bx: BX, bz: BZ } = randomOrigin();
// A fresh name each run: Guardian kills are permanent per player.
const BOT = `Arena${Math.floor(Math.random() * 1e6)}`;
// buildPlatform lays a 17x17 floor centered on (BX, BZ); the arena is its middle 9x9.
const ARENA = { x1: BX - 4, z1: BZ - 4, x2: BX + 4, z2: BZ + 4 };
const SEED = 'minecraft:oak_sapling[minecraft:custom_data={PublicBukkitValues:{"minibosses:sentinel_seed":1b}}]';
const EMBER = 'minecraft:fire_charge[minecraft:custom_data={PublicBukkitValues:{"minibosses:ashen_ember":1b}}]';

const rcon = await Rcon.connect();
const bots = [];
const results = [];
async function step(name, fn) {
  await fn();
  results.push(name);
  console.log(`PASS ${name}`);
}

const gameMode = async () => (await rcon.command(`minecraft:data get entity ${BOT} playerGameType`)).match(/(\d+)$/)?.[1];
// One block of slack: the lock checks block crossings, and the Sentinel's slam and sweep knockback can
// leave the bot standing on the arena's edge with its feet a hair past the last block.
const inArena = (pos) => pos.x >= ARENA.x1 - 1 && pos.x < ARENA.x2 + 2 && pos.z >= ARENA.z1 - 1 && pos.z < ARENA.z2 + 2;

function nextWindow(bot, timeoutMs = 5_000) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(`${bot.username}: no window opened`)), timeoutMs);
    bot.once('windowOpen', (window) => {
      clearTimeout(timer);
      resolve(window);
    });
  });
}

async function hold(bot, item, name) {
  await rcon.command(`minecraft:give ${BOT} ${item}`);
  await waitFor(() => bot.inventory.items().find((i) => i.name === name), `bot holds ${name}`);
  await bot.equip(bot.inventory.items().find((i) => i.name === name), 'hand');
}

try {
  await buildPlatform(rcon, BX, BZ, PLATFORM_Y);
  const bot = await createBot(BOT);
  bots.push(bot);
  await rcon.command(`minecraft:gamemode survival ${BOT}`);
  await rcon.command(`minecraft:clear ${BOT}`);
  await rcon.command(`minecraft:effect give ${BOT} minecraft:resistance 600 4 true`);
  await rcon.command(`guardianadmin arena set overgrown_sentinel world ${ARENA.x1} ${PLATFORM_Y} ${ARENA.z1} ${ARENA.x2} ${PLATFORM_Y + 10} ${ARENA.z2}`);

  await step('an Ashen Ember is refused while the Tyrant has no arena', async () => {
    await teleport(rcon, bot, BX, PLATFORM_Y + 1, BZ);
    await hold(bot, EMBER, 'fire_charge');
    const since = bot.lines.length;
    bot.activateItem();
    await waitForLine(bot, /not been prepared/, 5_000, since);
    await rcon.command(`minecraft:clear ${BOT} minecraft:fire_charge`);
  });

  await step('a Sentinel Seed outside its arena is refused', async () => {
    await teleport(rcon, bot, BX + 7, PLATFORM_Y + 1, BZ + 7);
    await hold(bot, SEED, 'oak_sapling');
    const since = bot.lines.length;
    bot.activateItem();
    await waitForLine(bot, /will not take root here/, 5_000, since);
  });

  await step('inside the arena the Sentinel rises at its center and the bot is locked in Adventure', async () => {
    await teleport(rcon, bot, BX + 3, PLATFORM_Y + 1, BZ + 3);
    const opened = nextWindow(bot);
    bot.activateItem();
    await opened;
    await bot.clickWindow(11, 0, 0);
    await waitFor(async () => /Test passed/.test(await rcon.command(
      `minecraft:execute if entity @e[type=ravager,x=${BX},y=${PLATFORM_Y + 1},z=${BZ},distance=..2]`)), 'Sentinel at the center');
    await waitFor(async () => (await gameMode()) === '2', 'bot in Adventure (2)');
  });

  await step('walking out over the edge is refused', async () => {
    await bot.lookAt(bot.entity.position.offset(10, 1.6, 0));
    bot.setControlState('forward', true);
    bot.setControlState('sprint', true);
    await sleep(3_000);
    bot.setControlState('forward', false);
    bot.setControlState('sprint', false);
    await sleep(500);
    assert(inArena(bot.entity.position), `still inside the arena (at ${bot.entity.position})`);
  });

  await step('a command teleport out is refused', async () => {
    await rcon.command(`minecraft:tp ${BOT} ${BX + 40} ${PLATFORM_Y + 1} ${BZ}`);
    await sleep(1_000);
    assert(inArena(bot.entity.position), `teleport was refused (at ${bot.entity.position})`);
  });

  await step('killing the Sentinel releases the bot back to Survival', async () => {
    const sentinel = Object.values(bot.entities).find((e) => e.name === 'ravager');
    assert(sentinel, 'bot sees the Sentinel');
    await bot.attack(sentinel);
    await sleep(500);
    await rcon.command(`minecraft:kill @e[type=ravager,x=${BX},y=${PLATFORM_Y + 1},z=${BZ},distance=..10]`);
    await waitFor(async () => (await gameMode()) === '0', 'bot back in Survival (0)', 5_000);
  });

  console.log(`\nGuardian arena e2e: ${results.length} passed`);
} catch (error) {
  console.error(`FAIL after ${results.length} passed: ${error.message}`);
  process.exitCode = 1;
} finally {
  await rcon.command('minecraft:kill @e[type=ravager,distance=0..]').catch(() => {});
  await rcon.command('guardianadmin arena clear overgrown_sentinel').catch(() => {});
  await releasePlatform(rcon, BX, BZ);
  await quit(...bots);
  rcon.close();
}
