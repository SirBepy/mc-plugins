import {
  Rcon, assert, createBot, nbtText, quit, randomOrigin, releasePlatform, sleep, teleport, waitFor, waitForLine,
} from './lib.mjs';

// The cartel flip phone: the owner's right-click opens the phone menu, Guard Mode brings two armed
// cousins who kill a nearby monster and leave a bystander alone, the phone menu calls them off, and
// Assassinate Mode (target list + confirm) broadcasts the warning, spawns them ~10 blocks from the
// target and kills them, after which a second hit is refused for a day. A stranger's phone is dead.
// Runs inside a Towny town with mobs off, since Towny would otherwise delete the cousins within 5 s.
const PLATFORM_Y = 200;
const { bx: BX, bz: BZ } = randomOrigin();
const OWNER = 'CartelGus';
const TARGET = 'CartelMark';
const TOWN = `cartel${Date.now() % 100000}`;
const COUSINS = '@e[tag=cartel.cousin]';

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
const containerSize = (window) => window.slots.length - 36;
const cousinCount = async () => {
  const out = await rcon.command(`minecraft:execute if entity ${COUSINS}`);
  const match = out.match(/count: (\d+)/i);
  return match ? Number(match[1]) : 0;
};

function nextWindow(bot, timeoutMs = 5_000) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(`${bot.username}: no window opened`)), timeoutMs);
    bot.once('windowOpen', (window) => {
      clearTimeout(timer);
      resolve(window);
    });
  });
}

async function openPhone(bot) {
  const phone = bot.inventory.items().find((i) => i.name === 'clock');
  assert(phone, `${bot.username} holds the phone`);
  await bot.equip(phone, 'hand');
  const opened = nextWindow(bot);
  bot.activateItem();
  const window = await opened;
  bot.deactivateItem();
  return window;
}

async function position(selector) {
  const out = await rcon.command(`minecraft:data get entity ${selector} Pos`);
  const nums = [...out.matchAll(/(-?\d+(?:\.\d+)?)d/g)].map((m) => Number(m[1]));
  return { x: nums[0], y: nums[1], z: nums[2] };
}

try {
  await rcon.command(`minecraft:forceload add ${BX - 32} ${BZ - 32} ${BX + 32} ${BZ + 32}`);
  await rcon.command(`minecraft:fill ${BX - 24} ${PLATFORM_Y} ${BZ - 24} ${BX + 24} ${PLATFORM_Y} ${BZ + 24} minecraft:stone`);
  await rcon.command(`minecraft:fill ${BX - 24} ${PLATFORM_Y + 1} ${BZ - 24} ${BX + 24} ${PLATFORM_Y + 5} ${BZ + 24} minecraft:air`);
  const owner = await createBot(OWNER);
  const target = await createBot(TARGET);
  bots.push(owner, target);
  for (const bot of bots) {
    await rcon.command(`minecraft:gamemode survival ${bot.username}`);
    await rcon.command(`minecraft:clear ${bot.username}`);
    await rcon.command(`minecraft:effect clear ${bot.username}`);
  }
  await rcon.command('cartel dismiss');
  await rcon.command(`cartel owner ${OWNER}`);
  await rcon.command('cartel reset');
  // Mid-chunk, so the town's home block (this chunk) holds the owner and the guards around them.
  await teleport(rcon, owner, BX + 8, PLATFORM_Y + 1, BZ + 8);
  await teleport(rcon, target, BX + 12, PLATFORM_Y + 1, BZ + 8);
  await rcon.command(`townyadmin town new ${TOWN} ${OWNER}`);
  await sleep(1000);
  const townMobs = await rcon.command(`townyadmin town ${TOWN} toggle mobs off`);
  console.log(`town mobs: ${townMobs.replace(/§./g, '').trim()}`);

  await step('the owner gets a phone and right-clicking it opens the phone menu', async () => {
    await rcon.command(`cartel give ${OWNER}`);
    await waitFor(() => owner.inventory.items().some((i) => i.name === 'clock'), 'owner has the phone', 5_000);
    const menu = await openPhone(owner);
    assert(/Cartel Phone/.test(titleOf(menu)), `title (got ${titleOf(menu)})`);
    assert(containerSize(menu) === 27, `three rows (got ${containerSize(menu)})`);
    assert(menu.slots[11]?.name === 'shield' && menu.slots[15]?.name === 'netherite_sword', 'guard and hit buttons');
    owner.closeWindow(menu);
  });

  await step('Guard Mode brings two cousins in iron armor with Sharpness X netherite swords', async () => {
    const menu = await openPhone(owner);
    const since = owner.lines.length;
    await owner.clickWindow(11, 0, 0);
    await waitForLine(owner, /Leonel and Marco are here/, 5_000, since);
    const count = await cousinCount();
    assert(count === 2, `two cousins (got ${count}: ${await rcon.command(`minecraft:execute if entity ${COUSINS}`)}; ${await rcon.command('cartel status')})`);
    const one = `${COUSINS.slice(0, -1)},limit=1]`;
    const slots = { head: 'iron_helmet', chest: 'iron_chestplate', legs: 'iron_leggings', feet: 'iron_boots', mainhand: 'netherite_sword' };
    for (const [slot, item] of Object.entries(slots)) {
      const id = await rcon.command(`minecraft:data get entity ${one} equipment.${slot}.id`);
      assert(id.includes(`minecraft:${item}`), `${item} in ${slot} (got ${id})`);
    }
    for (const slot of ['head', 'chest', 'legs', 'feet']) {
      const look = await rcon.command(`minecraft:data get entity ${one} equipment.${slot}.components."minecraft:equippable".asset_id`);
      assert(look.includes('claudesmp:salamanca'), `Salamanca look on the ${slot} armor (got ${look})`);
    }
    const sharpness = await rcon.command(`minecraft:data get entity ${one} equipment.mainhand.components."minecraft:enchantments"."minecraft:sharpness"`);
    assert(/: 10$/.test(sharpness.trim()), `Sharpness X (got ${sharpness})`);
    const health = await rcon.command(`minecraft:data get entity ${one} Health`);
    assert(/40(\.0)?f/.test(health), `40 health (got ${health})`);
    void menu;
  });

  await step('the guards survive Towny mob removal in a town with mobs off', async () => {
    await sleep(7_000);
    assert((await cousinCount()) === 2, 'both cousins still there after Towny\'s 5 s sweep');
  });

  await step('the guards kill a monster near the owner and leave a bystander alone', async () => {
    const healthBefore = target.health;
    await rcon.command(`minecraft:summon minecraft:husk ${BX + 3} ${PLATFORM_Y + 1} ${BZ + 8} {Tags:["cartel.probe"],PersistenceRequired:1b}`);
    await waitFor(async () => !/count/i.test(await rcon.command('minecraft:execute if entity @e[tag=cartel.probe]')),
      'the husk is dead', 20_000);
    assert(target.health >= healthBefore, `bystander unhurt (${healthBefore} -> ${target.health})`);
    assert(owner.health === 20, `owner unhurt (${owner.health})`);
  });

  await step('the phone calls the cousins off', async () => {
    const menu = await openPhone(owner);
    assert(menu.slots[13]?.name === 'barrier', 'call-off button while active');
    await owner.clickWindow(13, 0, 0);
    await waitFor(async () => (await cousinCount()) === 0, 'cousins gone', 5_000);
  });

  await step('Assassinate Mode warns the server, spawns the cousins 10 blocks off and kills the target', async () => {
    await rcon.command('cartel reset');
    let menu = await openPhone(owner);
    let opened = nextWindow(owner);
    await owner.clickWindow(15, 0, 0);
    menu = await opened;
    assert(/crossed the cartel/.test(titleOf(menu)), `target list (got ${titleOf(menu)})`);
    const slot = menu.slots.findIndex((s, i) => i < containerSize(menu) && s?.name === 'player_head'
      && JSON.stringify(s.customName ?? s.nbt ?? s).includes(TARGET));
    assert(slot >= 0, 'the target is listed');
    opened = nextWindow(owner);
    await owner.clickWindow(slot, 0, 0);
    menu = await opened;
    assert(/Order the hit on CartelMark/.test(titleOf(menu)), `confirm (got ${titleOf(menu)})`);
    const since = target.lines.length;
    let died = false;
    target.once('death', () => { died = true; });
    await owner.clickWindow(11, 0, 0);
    await waitForLine(target, /CartelMark, you have made enemies of the cartel\./, 5_000, since);
    const markAt = target.entity.position;
    const spot = await position(`${COUSINS.slice(0, -1)},limit=1]`);
    const d = Math.hypot(spot.x - markAt.x, spot.z - markAt.z);
    assert(d >= 8.5 && d <= 11.5, `cousin appeared ${d.toFixed(1)} blocks from the target`);
    await waitFor(() => died, 'the target is killed', 30_000);
    await waitForLine(owner, /The job is done/, 5_000);
    await waitFor(async () => (await cousinCount()) === 0, 'cousins leave after the job', 5_000);
  });

  await step('a second hit is refused for a day', async () => {
    await waitFor(() => target.health > 0, 'target respawned', 10_000);
    await sleep(1000);
    let menu = await openPhone(owner);
    let opened = nextWindow(owner);
    await owner.clickWindow(15, 0, 0);
    menu = await opened;
    const slot = menu.slots.findIndex((s, i) => i < containerSize(menu) && s?.name === 'player_head');
    opened = nextWindow(owner);
    await owner.clickWindow(slot, 0, 0);
    await opened;
    const since = owner.lines.length;
    await owner.clickWindow(11, 0, 0);
    await waitForLine(owner, /Assassinate Mode is ready again in 23h 59m/, 5_000, since);
    assert((await cousinCount()) === 0, 'no cousins');
  });

  await step('anyone else holding a phone gets a dead line', async () => {
    await rcon.command(`cartel give ${TARGET}`);
    await waitFor(() => target.inventory.items().some((i) => i.name === 'clock'), 'target has a phone', 5_000);
    const phone = target.inventory.items().find((i) => i.name === 'clock');
    await target.equip(phone, 'hand');
    const since = target.lines.length;
    target.activateItem();
    target.deactivateItem();
    await waitForLine(target, /The line is dead/, 5_000, since);
  });

  console.log(`\nCartel e2e: ${results.length} passed`);
} catch (error) {
  console.error(`FAIL after ${results.length} passed: ${error.message}`);
  process.exitCode = 1;
} finally {
  await rcon.command('cartel dismiss').catch(() => {});
  await rcon.command('cartel reset').catch(() => {});
  await rcon.command(`townyadmin town ${TOWN} delete`).catch(() => {});
  await rcon.command('confirm').catch(() => {});
  await rcon.command('minecraft:kill @e[tag=cartel.probe]').catch(() => {});
  await releasePlatform(rcon, BX, BZ);
  await rcon.command(`minecraft:forceload remove ${BX - 32} ${BZ - 32} ${BX + 32} ${BZ + 32}`).catch(() => {});
  await quit(...bots);
  rcon.close();
}
