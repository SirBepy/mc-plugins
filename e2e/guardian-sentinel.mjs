import {
  Rcon, assert, buildPlatform, createBot, quit, randomOrigin, releasePlatform, sleep, teleport, waitFor,
} from './lib.mjs';

// The rebuilt Overgrown Sentinel, fought by one bot inside a registered arena through
// /guardianadmin test: the body is the rig's item displays (20 parts on Normal, 29 on Mythic) on an
// invisible base Ravager, the Bedrock stand-in Ravager is hidden from a Java client, the attack loop
// hurts the bot within seconds of the rise (not only after half health), and killing the boss
// removes every fight entity and drops nothing because test fights pay out nothing.
const PLATFORM_Y = 200;
const { bx: BX, bz: BZ } = randomOrigin();
const BOT = `Sentinel${Math.floor(Math.random() * 1e6)}`;
// buildPlatform lays a 17x17 floor centered on (BX, BZ); the arena covers it.
const ARENA = { x1: BX - 8, z1: BZ - 8, x2: BX + 8, z2: BZ + 8 };
const NEAR = `x=${BX},y=${PLATFORM_Y + 1},z=${BZ},distance=..30`;

const rcon = await Rcon.connect();
const bots = [];
const results = [];
async function step(name, fn) {
  await fn();
  results.push(name);
  console.log(`PASS ${name}`);
}

const count = async (selector) => Number((await rcon.command(`minecraft:execute if entity ${selector}`))
  .match(/count: (\d+)/i)?.[1] ?? 0);
const displays = () => count(`@e[type=item_display,tag=minibosses.fight_temp,${NEAR}]`);
const boss = `@e[type=ravager,tag=!minibosses.fight_temp,${NEAR},limit=1]`;

async function rise(tier) {
  const reply = await rcon.command(`guardianadmin test overgrown_sentinel ${tier}`);
  await waitFor(async () => (await count(boss)) === 1, `a ${tier} Sentinel rose (${reply})`);
}

async function slay() {
  await rcon.command(`minecraft:kill ${boss}`);
  await waitFor(async () => (await count(`@e[tag=minibosses.fight_temp,${NEAR}]`)) === 0,
    'every fight entity is gone', 10_000);
}

try {
  await buildPlatform(rcon, BX, BZ, PLATFORM_Y);
  const bot = await createBot(BOT);
  bots.push(bot);
  await rcon.command(`minecraft:gamemode survival ${BOT}`);
  await rcon.command(`minecraft:clear ${BOT}`);
  // The Sentinel's Normal slam kills an unarmoured bot in about ten seconds; keep it alive (mild
  // Resistance still lets damage through for the attack-loop step).
  await rcon.command(`minecraft:effect give ${BOT} minecraft:resistance 600 1 true`);
  await rcon.command(`minecraft:effect give ${BOT} minecraft:regeneration 600 2 true`);
  await rcon.command(`guardianadmin arena set overgrown_sentinel world ${ARENA.x1} ${PLATFORM_Y} ${ARENA.z1} ${ARENA.x2} ${PLATFORM_Y + 20} ${ARENA.z2}`);
  await teleport(rcon, bot, BX + 4, PLATFORM_Y + 1, BZ + 4);

  await step('a Normal test Sentinel rises with its 20-part body on an invisible base', async () => {
    await rise('normal');
    await waitFor(async () => (await displays()) === 20, 'the Normal rig has 20 item displays');
    const effects = await rcon.command(`minecraft:data get entity ${boss} active_effects`);
    assert(/invisibility/.test(effects), `the base Ravager is invisible: ${effects}`);
  });

  await step('the Bedrock stand-in exists on the server but a Java client never sees it', async () => {
    assert((await count(`@e[type=ravager,tag=minibosses.stand_in,${NEAR}]`)) === 1, 'one stand-in Ravager');
    await sleep(1_500);
    const seen = Object.values(bot.entities).filter((e) => e.name === 'ravager');
    assert(seen.length === 1, `the bot sees only the boss Ravager, not the stand-in (saw ${seen.length})`);
  });

  await step('the attack loop hurts the bot within seconds of the rise', async () => {
    let last = bot.health;
    let hurt = false;
    bot.on('health', () => {
      if (bot.health < last) hurt = true;
      last = bot.health;
    });
    await waitFor(() => hurt, `the bot took damage (health ${bot.health})`, 20_000);
  });

  await step('killing a test Sentinel removes the body and stand-in and drops nothing', async () => {
    await slay();
    await sleep(500);
    assert((await count(`@e[type=item,${NEAR}]`)) === 0, 'a test fight drops no loot');
  });

  await step('a Mythic test Sentinel wears its 29-part body', async () => {
    await teleport(rcon, bot, BX + 4, PLATFORM_Y + 1, BZ + 4);
    await rise('mythic');
    await waitFor(async () => (await displays()) === 29, 'the Mythic rig has 29 item displays');
    await slay();
  });

  console.log(`\nGuardian Sentinel e2e: ${results.length} passed`);
} catch (error) {
  console.error(`FAIL after ${results.length} passed: ${error.message}`);
  process.exitCode = 1;
} finally {
  await rcon.command('minecraft:kill @e[type=ravager,distance=0..]').catch(() => {});
  await rcon.command('minecraft:kill @e[tag=minibosses.fight_temp]').catch(() => {});
  await rcon.command('guardianadmin arena clear overgrown_sentinel').catch(() => {});
  await releasePlatform(rcon, BX, BZ);
  await quit(...bots);
  rcon.close();
}
