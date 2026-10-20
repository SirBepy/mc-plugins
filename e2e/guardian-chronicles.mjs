import {
  Rcon, assert, buildPlatform, createBot, nbtText, quit, randomOrigin, releasePlatform, sleep, teleport, waitFor,
  waitForLine,
} from './lib.mjs';

// The Guardian Chronicles and the Guardian tier picker, played by one bot: Thessa stands where she is
// placed, talking starts the story, a Sentinel Seed opens the Normal/Heroic/Mythic picker (Heroic locked),
// picking Normal wakes the Sentinel, killing it hands out the chapter's fragment, a second seed hits the
// cooldown, the chapter is told, its discovery biome advances the story, and the finale hands over the book.
// Needs the server's plugins/Minibosses/chronicles.key (prepareE2eServer and assembleLocalServer copy
// it from server/secrets.properties); without it the Chronicles stay locked and the first step fails. The story's words are encrypted in the repo, so nothing here quotes
// them: steps check stages (/chronicle admin status), items and window icons instead.
const PLATFORM_Y = 200;
const { bx: BX, bz: BZ } = randomOrigin();
// A fresh name each run: Guardian kills are permanent per player, so a reused bot would start with Heroic open.
const BOT = `Chron${Math.floor(Math.random() * 1e6)}`;
const SPOT = { x: BX + 0.5, y: PLATFORM_Y + 1, z: BZ + 0.5 };
const AT_SPOT = `x=${SPOT.x},y=${SPOT.y},z=${SPOT.z},distance=..1`;
const NEAR_BOT = `x=${BX},y=${PLATFORM_Y + 1},z=${BZ + 3}`;
const SEED = 'minecraft:oak_sapling[minecraft:custom_data={PublicBukkitValues:{"minibosses:sentinel_seed":1b}}]';

const rcon = await Rcon.connect();
const bots = [];
const results = [];
async function step(name, fn) {
  await fn();
  results.push(name);
  console.log(`PASS ${name}`);
}

const titleOf = (window) => (typeof window?.title === 'string' ? window.title
  : `${nbtText(window?.title)} ${JSON.stringify(window?.title)}`);
const chronicler = (bot) => Object.values(bot.entities)
  .find((e) => e.name === 'mannequin' && e.position.distanceTo(bot.entity.position) < 6);
const stageOf = async () => Number((await rcon.command(`chronicle admin status ${BOT}`)).match(/stage (\d+)/)?.[1]);

async function count(item) {
  const out = await rcon.command(`minecraft:clear ${BOT} ${item} 0`);
  const match = out.match(/Found (\d+)/);
  return match ? Number(match[1]) : 0;
}

function nextWindow(bot, timeoutMs = 5_000) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(`${bot.username}: no window opened`)), timeoutMs);
    bot.once('windowOpen', (window) => {
      clearTimeout(timer);
      resolve(window);
    });
  });
}

async function holdSeed(bot) {
  await rcon.command(`minecraft:give ${BOT} ${SEED}`);
  await waitFor(() => bot.inventory.items().find((i) => i.name === 'oak_sapling'), 'bot holds a seed');
  await bot.equip(bot.inventory.items().find((i) => i.name === 'oak_sapling'), 'hand');
}

async function talk(bot) {
  await waitFor(() => chronicler(bot), 'bot sees the Chronicler');
  await bot.activateEntity(chronicler(bot));
}

try {
  await buildPlatform(rcon, BX, BZ, PLATFORM_Y);
  const bot = await createBot(BOT);
  bots.push(bot);
  await rcon.command(`minecraft:gamemode creative ${BOT}`);
  await rcon.command(`minecraft:clear ${BOT}`);
  await rcon.command(`chronicle admin reset ${BOT}`);
  await rcon.command(`chronicle admin place world ${SPOT.x} ${SPOT.y} ${SPOT.z} 0`);
  await teleport(rcon, bot, BX, PLATFORM_Y + 1, BZ + 3);

  await step('the Chronicler stands at her spot as an AI-less, invulnerable villager plus a Mannequin', async () => {
    await waitFor(async () => /Test passed/.test(await rcon.command(`minecraft:execute if entity @e[type=villager,${AT_SPOT}]`)),
      'villager at the spot', 10_000);
    assert(/1b/.test(await rcon.command(`minecraft:data get entity @e[type=villager,${AT_SPOT},limit=1] NoAI`)), 'NoAI');
    assert(/1b/.test(await rcon.command(`minecraft:data get entity @e[type=villager,${AT_SPOT},limit=1] Invulnerable`)),
      'Invulnerable');
    assert(/Test passed/.test(await rcon.command(`minecraft:execute if entity @e[type=mannequin,${AT_SPOT}]`)), 'mannequin');
  });

  await step('talking to her starts the story (stage 1) with her lines in chat', async () => {
    const since = bot.lines.length;
    await talk(bot);
    await waitForLine(bot, /^\S+: /, 5_000, since);
    await waitFor(async () => (await stageOf()) === 1, 'stage 1');
  });

  await step('a Sentinel Seed opens the tier picker with Heroic and Mythic locked', async () => {
    await holdSeed(bot);
    const opened = nextWindow(bot);
    bot.activateItem();
    const picker = await opened;
    assert(/Wake/.test(titleOf(picker)), `picker title (got ${titleOf(picker)})`);
    assert(picker.slots[11]?.name === 'iron_sword', `Normal is open (got ${picker.slots[11]?.name})`);
    assert(picker.slots[13]?.name === 'barrier', `Heroic is locked (got ${picker.slots[13]?.name})`);
    assert(picker.slots[15]?.name === 'barrier', `Mythic is locked (got ${picker.slots[15]?.name})`);
    assert((await count('minecraft:oak_sapling')) === 1, 'opening the picker spends nothing');
  });

  await step('picking Normal spends the seed and wakes the Sentinel', async () => {
    await bot.clickWindow(11, 0, 0);
    await waitFor(async () => /Test passed/.test(await rcon.command(
      `minecraft:execute if entity @e[type=ravager,${NEAR_BOT},distance=..6]`)), 'a Sentinel near the bot');
    assert((await count('minecraft:oak_sapling')) === 0, 'the seed was spent');
  });

  await step('killing it drops this chapter\'s fragment, and a second seed right away hits the cooldown', async () => {
    const sentinel = Object.values(bot.entities).find((e) => e.name === 'ravager');
    assert(sentinel, 'bot sees the Sentinel');
    await bot.attack(sentinel);
    await sleep(500);
    await rcon.command(`minecraft:kill @e[type=ravager,${NEAR_BOT},distance=..10]`);
    await waitFor(async () => (await count('minecraft:paper')) === 1, 'the fragment arrived', 5_000);
    // Heroic unlocking after this kill is covered by SentinelFightTest; the picker can't be reopened
    // here because the summon cooldown (30 min) refuses before it opens.
    await holdSeed(bot);
    const since = bot.lines.length;
    bot.activateItem();
    await waitForLine(bot, /still resting/, 5_000, since);
    await rcon.command(`minecraft:clear ${BOT} minecraft:oak_sapling`);
  });

  await step('talking again tells the chapter (stage 2)', async () => {
    await sleep(15_000); // the prologue is still being spoken; a click mid-speech is ignored by design
    await talk(bot);
    await waitFor(async () => (await stageOf()) === 2, 'stage 2', 10_000);
  });

  await step('standing in the chapter\'s discovery biome advances the story (stage 3)', async () => {
    await rcon.command(`minecraft:fillbiome ${BX - 4} ${PLATFORM_Y - 2} ${BZ - 4} ${BX + 4} ${PLATFORM_Y + 6} ${BZ + 8} minecraft:lush_caves`);
    // The biome watcher skips a player while the chapter is still being spoken.
    await waitFor(async () => (await stageOf()) === 3, 'stage 3', 25_000);
  });

  await step('the finale hands over the finished book', async () => {
    await sleep(10_000); // the discovery echo is still being spoken
    await rcon.command(`chronicle admin stage ${BOT} 7`);
    await talk(bot);
    await waitFor(async () => (await count('minecraft:written_book')) === 1, 'the book arrived', 60_000);
    assert((await stageOf()) === 8, 'stage 8: finished');
  });

  console.log(`\nGuardian Chronicles e2e: ${results.length} passed`);
} catch (error) {
  console.error(`FAIL after ${results.length} passed: ${error.message}`);
  process.exitCode = 1;
} finally {
  await rcon.command('chronicle admin remove').catch(() => {});
  await rcon.command(`chronicle admin reset ${BOT}`).catch(() => {});
  await releasePlatform(rcon, BX, BZ);
  await quit(...bots);
  rcon.close();
}
