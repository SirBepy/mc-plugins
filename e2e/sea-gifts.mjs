import {
  Rcon, assert, buildPlatform, createBot, quit, randomOrigin, releasePlatform, sleep, teleport, waitFor, waitForLine,
} from './lib.mjs';

// ItsLogiks' sea gifts (Tweaks): the owner holds infinite Dolphin's Grace and Conduit Power, switches each
// one on his own at most once an hour (/seagifts dolphin|conduit, refused with the time left), milk and
// /effect clear don't take a gift that is on, death switches both off until he calls them back (not
// blocked by the hour), and a stranger neither has them nor can run the command. The owner is moved to a
// fresh bot each run (sea-gifts.yml keeps every past owner's cooldowns) and back to ItsLogiks after.
const PLATFORM_Y = 200;
const { bx: BX, bz: BZ } = randomOrigin();
const OWNER = `Sea${Date.now() % 100000}`;
const STRANGER = 'SeaStranger';
const ITSLOGIKS = '3e0a4456-e2a3-4ea8-9bfa-8e38ee4c863d';
const DOLPHIN = 'minecraft:dolphins_grace';
const CONDUIT = 'minecraft:conduit_power';

const rcon = await Rcon.connect();
const bots = [];
const results = [];
async function step(name, fn) {
  await fn();
  results.push(name);
  console.log(`PASS ${name}`);
}

// RCON cuts a long reply off with "...", so ask for one effect's duration at a time.
const duration = (name, effect) => rcon.command(`minecraft:data get entity ${name} active_effects[{id:"${effect}"}].duration`);
async function has(name, effect) {
  return /has the following entity data/.test(await duration(name, effect));
}
async function command(bot, text, pattern) {
  const since = bot.lines.length;
  bot.chat(text);
  return waitForLine(bot, pattern, 5_000, since);
}

try {
  await buildPlatform(rcon, BX, BZ, PLATFORM_Y);
  const owner = await createBot(OWNER);
  const stranger = await createBot(STRANGER);
  bots.push(owner, stranger);
  for (const bot of bots) {
    await rcon.command(`minecraft:gamemode survival ${bot.username}`);
    await rcon.command(`minecraft:effect clear ${bot.username}`);
    await teleport(rcon, bot, BX, PLATFORM_Y + 1, BZ);
  }
  await rcon.command(`seagifts owner ${OWNER}`);

  await step('the owner gets both gifts as infinite effects, the stranger neither', async () => {
    await waitFor(async () => (await has(OWNER, DOLPHIN)) && (await has(OWNER, CONDUIT)), 'owner has both gifts', 5_000);
    for (const effect of [DOLPHIN, CONDUIT]) {
      const reply = await duration(OWNER, effect);
      assert(/: -1$/.test(reply.trim()), `${effect} is infinite (${reply})`);
    }
    await sleep(1500);
    assert(!(await has(STRANGER, DOLPHIN)) && !(await has(STRANGER, CONDUIT)), 'stranger has no gift');
  });

  await step('a gift switches off on its own and the hour refuses a second flip', async () => {
    await command(owner, '/seagifts dolphin', /Dolphin's Grace is switched off/);
    await sleep(1500);
    assert(!(await has(OWNER, DOLPHIN)), 'Dolphin\'s Grace gone');
    assert(await has(OWNER, CONDUIT), 'Conduit Power untouched');
    await command(owner, '/seagifts dolphin', /switch it again in 59m/);
    assert(!(await has(OWNER, DOLPHIN)), 'still off after the refusal');
  });

  await step('milk and /effect clear do not take a gift that is on', async () => {
    await rcon.command(`minecraft:item replace entity ${OWNER} hotbar.0 with minecraft:milk_bucket`);
    await waitFor(() => owner.inventory.slots[36]?.name === 'milk_bucket', 'owner holds milk', 5_000);
    owner.setQuickBarSlot(0);
    await owner.consume();
    await waitFor(() => owner.inventory.slots[36]?.name === 'bucket', 'milk drunk', 5_000);
    assert(await has(OWNER, CONDUIT), 'Conduit Power survives milk');
    await rcon.command(`minecraft:effect clear ${OWNER}`);
    await waitFor(() => has(OWNER, CONDUIT), 'Conduit Power back after effect clear', 3_000);
  });

  await step('death switches the gifts off until he calls them back, without waiting the hour', async () => {
    const since = owner.lines.length;
    await rcon.command(`minecraft:kill ${OWNER}`);
    await waitForLine(owner, /left you when you died/, 5_000, since);
    await waitFor(() => owner.health > 0, 'owner respawned', 10_000);
    await sleep(2000);
    assert(!(await has(OWNER, CONDUIT)), 'Conduit Power stays off after death');
    await command(owner, '/seagifts conduit', /Conduit Power is yours again/);
    await waitFor(() => has(OWNER, CONDUIT), 'Conduit Power back', 3_000);
    await command(owner, '/seagifts', /Conduit Power: on, can be switched again in 59m/);
  });

  await step('a stranger cannot use /seagifts', async () => {
    const since = stranger.lines.length;
    stranger.chat('/seagifts conduit');
    await waitForLine(stranger, /Unknown or incomplete command|Unknown command/i, 5_000, since);
    await sleep(1500);
    assert(!(await has(STRANGER, CONDUIT)), 'stranger has no gift');
  });

  console.log(`PASS sea-gifts: ${results.length} steps`);
} finally {
  await rcon.command(`seagifts owner ${ITSLOGIKS}`).catch(() => {});
  for (const bot of bots) await rcon.command(`minecraft:effect clear ${bot.username}`).catch(() => {});
  await quit(...bots);
  await releasePlatform(rcon, BX, BZ).catch(() => {});
  rcon.close();
}
