import { Vec3 } from 'vec3';
import {
  Rcon, assert, buildPlatform, createBot, quit, randomOrigin, releasePlatform, sleep, teleport, waitFor,
} from './lib.mjs';

// CoffiebagSupreme's three Nether wishes (Tweaks): a ripe crop he breaks replants from a seed in his
// inventory (nobody else's does, and with no seed nothing is planted); a zombie leaves him alone until he
// hits it, while it still goes for anyone else; food he crafts, takes out of a smoker or cooks on a campfire
// is marked and fills twice as much (eaten here), as does each slice of his cake for anyone, while the same
// food made by anyone else is plain. The wishes are moved
// to a fresh bot each run and back to CoffiebagSupreme after.
const PLATFORM_Y = 200;
const { bx: BX, bz: BZ } = randomOrigin();
const OWNER = `Nw${Date.now() % 100000}`;
const STRANGER = 'NwStranger';
const COFFIE = '7dca8f8e-2981-4925-a7d7-128395ca11b9';
const ZOMBIE = '@e[type=minecraft:zombie,tag=e2e_nw]';
// Far enough that a zombie on the platform loses a player there (zombie follow range is 35).
const AWAY = { x: BX + 200, z: BZ };

const rcon = await Rcon.connect();
const bots = [];
const results = [];
async function step(name, fn) {
  await fn();
  results.push(name);
  console.log(`PASS ${name}`);
}

const passes = async (condition) => /Test passed/.test(await rcon.command(`minecraft:execute ${condition}`));
const seeds = (bot) => bot.inventory.items().filter((i) => i.name === 'wheat_seeds').reduce((n, i) => n + i.count, 0);
// RCON cuts a long reply off with "...", so ask for the one number.
const nutrition = (name, item) => rcon.command(
  `minecraft:data get entity ${name} Inventory[{id:"minecraft:${item}"}].components."minecraft:food".nutrition`);

async function ripeWheat(x, z) {
  await rcon.command(`minecraft:setblock ${x} ${PLATFORM_Y} ${z} minecraft:farmland`);
  await rcon.command(`minecraft:setblock ${x} ${PLATFORM_Y + 1} ${z} minecraft:wheat[age=7]`);
  await sleep(300);
}

async function harvest(bot, x, z) {
  await bot.dig(bot.blockAt(new Vec3(x, PLATFORM_Y + 1, z)));
  // The crop's own drops can't be picked up for 10 ticks; clear them so the seed count shows only the replant.
  await rcon.command(`minecraft:kill @e[type=minecraft:item,x=${x},y=${PLATFORM_Y + 1},z=${z},distance=..3]`);
  await sleep(1000);
}

async function prepare(bot, x, z) {
  await rcon.command(`minecraft:clear ${bot.username}`);
  await rcon.command(`minecraft:effect clear ${bot.username}`);
  await teleport(rcon, bot, x, PLATFORM_Y + 1, z);
}

const difficulty = (await rcon.command('minecraft:difficulty')).match(/difficulty is (\w+)/i)?.[1]?.toLowerCase() ?? 'normal';
try {
  await buildPlatform(rcon, BX, BZ, PLATFORM_Y);
  await rcon.command(`minecraft:forceload add ${AWAY.x - 8} ${AWAY.z - 8} ${AWAY.x + 8} ${AWAY.z + 8}`);
  await rcon.command(`minecraft:fill ${AWAY.x - 2} ${PLATFORM_Y} ${AWAY.z - 2} ${AWAY.x + 2} ${PLATFORM_Y} ${AWAY.z + 2} minecraft:stone`);
  const owner = await createBot(OWNER);
  const stranger = await createBot(STRANGER);
  bots.push(owner, stranger);
  for (const bot of bots) await rcon.command(`minecraft:gamemode survival ${bot.username}`);
  await rcon.command(`netherwishes owner ${OWNER}`);
  await prepare(owner, BX, BZ);
  await prepare(stranger, BX, BZ + 4);

  await step('a ripe crop he breaks replants from his seeds, using one', async () => {
    await rcon.command(`minecraft:give ${OWNER} minecraft:wheat_seeds 3`);
    await waitFor(() => seeds(owner) === 3, 'owner has 3 seeds', 5_000);
    await ripeWheat(BX + 1, BZ);
    await harvest(owner, BX + 1, BZ);
    assert(await passes(`if block ${BX + 1} ${PLATFORM_Y + 1} ${BZ} minecraft:wheat[age=0]`), 'the wheat is replanted');
    assert(seeds(owner) === 2, `one seed used (has ${seeds(owner)})`);
  });

  await step('with no seed, or for anyone else, nothing is replanted', async () => {
    await rcon.command(`minecraft:clear ${OWNER}`);
    await ripeWheat(BX - 1, BZ);
    await harvest(owner, BX - 1, BZ);
    assert(await passes(`if block ${BX - 1} ${PLATFORM_Y + 1} ${BZ} minecraft:air`), 'no seed: the spot stays empty');
    await rcon.command(`minecraft:give ${STRANGER} minecraft:wheat_seeds 3`);
    await ripeWheat(BX + 1, BZ + 4);
    await harvest(stranger, BX + 1, BZ + 4);
    assert(await passes(`if block ${BX + 1} ${PLATFORM_Y + 1} ${BZ + 4} minecraft:air`), 'the stranger\'s harvest stays empty');
    assert(seeds(stranger) === 3, 'the stranger kept his seeds');
  });

  await step('a zombie leaves him alone, goes for someone else, and fights him once he hits it', async () => {
    await rcon.command('minecraft:difficulty hard');
    await teleport(rcon, stranger, AWAY.x, PLATFORM_Y + 1, AWAY.z);
    await rcon.command(`minecraft:summon minecraft:zombie ${BX + 2} ${PLATFORM_Y + 1} ${BZ} {Tags:["e2e_nw"],PersistenceRequired:1b,IsBaby:0b}`);
    // Daylight at y 201 would burn it before the test is over.
    await rcon.command(`minecraft:effect give ${ZOMBIE} minecraft:fire_resistance infinite 0 true`);
    await rcon.command(`minecraft:effect give ${OWNER} minecraft:instant_health 1 10 true`);
    await sleep(1000);
    const full = owner.health;
    await sleep(6000);
    assert(owner.health >= full, `six seconds next to the zombie and no hit (health ${full} -> ${owner.health})`);

    await teleport(rcon, stranger, BX + 3, PLATFORM_Y + 1, BZ + 1);
    const strangerFull = stranger.health;
    await waitFor(() => stranger.health < strangerFull, 'the zombie hits the stranger', 15_000);
    assert(owner.health >= full, `it still never hit the owner (health ${owner.health})`);
    await teleport(rcon, stranger, AWAY.x, PLATFORM_Y + 1, AWAY.z);
    await sleep(2000);

    const zombie = owner.nearestEntity((e) => e.name === 'zombie');
    assert(zombie, 'the owner sees the zombie');
    await owner.lookAt(zombie.position.offset(0, 1.6, 0));
    owner.attack(zombie);
    await waitFor(() => owner.health < full, 'the zombie he hit fights back', 15_000);
  });

  await step('food he crafts or takes out of a smoker is his and fills twice as much; others\' is plain', async () => {
    await rcon.command(`minecraft:kill ${ZOMBIE}`);
    await rcon.command(`minecraft:difficulty ${difficulty}`);
    await prepare(owner, BX, BZ);
    await prepare(stranger, BX, BZ + 4);
    for (const bot of bots) {
      await rcon.command(`minecraft:give ${bot.username} minecraft:pumpkin 1`);
      await rcon.command(`minecraft:give ${bot.username} minecraft:sugar 1`);
      await rcon.command(`minecraft:give ${bot.username} minecraft:egg 1`);
    }
    await sleep(1000);
    const pie = owner.registry.itemsByName.pumpkin_pie.id;
    for (const bot of bots) {
      const recipe = bot.recipesFor(pie, null, 1, null)[0];
      assert(recipe, `${bot.username} has a pumpkin pie recipe in the 2x2 grid`);
      await bot.craft(recipe, 1, null);
    }
    await waitFor(() => owner.inventory.items().some((i) => i.name === 'pumpkin_pie'), 'owner crafted a pie', 5_000);
    const ownerPie = await nutrition(OWNER, 'pumpkin_pie');
    assert(/: 16$/.test(ownerPie.trim()), `his pie has nutrition 16 (${ownerPie})`);
    const strangerPie = await nutrition(STRANGER, 'pumpkin_pie');
    assert(!/: \d+$/.test(strangerPie.trim()), `the stranger's pie keeps vanilla food (${strangerPie})`);

    for (const [bot, x] of [[owner, BX + 1], [stranger, BX + 1]]) {
      const z = bot === owner ? BZ : BZ + 4;
      await rcon.command(`minecraft:setblock ${x} ${PLATFORM_Y + 1} ${z} minecraft:smoker{Items:[{Slot:2b,id:"minecraft:cooked_beef",count:4}]}`);
      await sleep(300);
      const smoker = await bot.openFurnace(bot.blockAt(new Vec3(x, PLATFORM_Y + 1, z)));
      await smoker.takeOutput();
      smoker.close();
    }
    await sleep(500);
    const ownerBeef = await nutrition(OWNER, 'cooked_beef');
    assert(/: 16$/.test(ownerBeef.trim()), `his smoker beef has nutrition 16 (${ownerBeef})`);
    const strangerBeef = await nutrition(STRANGER, 'cooked_beef');
    assert(!/: \d+$/.test(strangerBeef.trim()), `the stranger's smoker beef is plain (${strangerBeef})`);

    // Hunger 255 (1.28 exhaustion a tick) empties saturation and the bar in a few seconds; then the marked pie
    // (vanilla 8 / 4.8) fills 16 / 9.6.
    await rcon.command(`minecraft:effect give ${OWNER} minecraft:hunger 30 255 true`);
    await waitFor(() => owner.food === 0, `owner starving (food ${owner.food})`, 25_000);
    await rcon.command(`minecraft:effect clear ${OWNER}`);
    await sleep(500);
    owner.setQuickBarSlot(0);
    await owner.equip(owner.inventory.items().find((i) => i.name === 'pumpkin_pie'), 'hand');
    await owner.consume();
    await waitFor(() => owner.food === 16, `the pie fills 16 (food ${owner.food})`, 5_000);
    const saturation = await rcon.command(`minecraft:data get entity ${OWNER} foodSaturationLevel`);
    assert(/: 9\.6\d*f?$/.test(saturation.trim()), `and 9.6 saturation (${saturation})`);
  });

  await step('campfire food he put on is his, and a slice of his cake fills a stranger twice as much', async () => {
    await prepare(owner, BX, BZ);
    await prepare(stranger, BX, BZ + 4);
    for (const [bot, z] of [[owner, BZ], [stranger, BZ + 4]]) {
      await rcon.command(`minecraft:setblock ${BX + 1} ${PLATFORM_Y + 1} ${z} minecraft:campfire[lit=true]`);
      await rcon.command(`minecraft:give ${bot.username} minecraft:beef 1`);
      await waitFor(() => bot.inventory.items().some((i) => i.name === 'beef'), `${bot.username} has beef`, 5_000);
      await bot.equip(bot.inventory.items().find((i) => i.name === 'beef'), 'hand');
      await bot.activateBlock(bot.blockAt(new Vec3(BX + 1, PLATFORM_Y + 1, z)));
    }
    await sleep(1000);
    assert(owner.inventory.items().every((i) => i.name !== 'beef'), 'the owner\'s beef is on the fire');
    // A campfire cooks in 30 s and pops the food out on top, where the bot beside it picks it up.
    await waitFor(() => owner.inventory.items().some((i) => i.name === 'cooked_beef')
      && stranger.inventory.items().some((i) => i.name === 'cooked_beef'), 'both campfires cooked', 45_000);
    const ownerBeef = await nutrition(OWNER, 'cooked_beef');
    assert(/: 16$/.test(ownerBeef.trim()), `his campfire beef has nutrition 16 (${ownerBeef})`);
    const strangerBeef = await nutrition(STRANGER, 'cooked_beef');
    assert(!/: \d+$/.test(strangerBeef.trim()), `the stranger's campfire beef is plain (${strangerBeef})`);

    // A cake he crafted, as the plugin marks it (PDC lives in custom_data.PublicBukkitValues).
    await rcon.command(`minecraft:give ${OWNER} minecraft:cake[custom_data={PublicBukkitValues:{"tweaks:hearty_food":"${COFFIE}"}}] 1`);
    await waitFor(() => owner.inventory.items().some((i) => i.name === 'cake'), 'owner has the cake', 5_000);
    await owner.equip(owner.inventory.items().find((i) => i.name === 'cake'), 'hand');
    await owner.placeBlock(owner.blockAt(new Vec3(BX - 1, PLATFORM_Y, BZ + 2)), new Vec3(0, 1, 0));
    await waitFor(() => passes(`if block ${BX - 1} ${PLATFORM_Y + 1} ${BZ + 2} minecraft:cake`), 'the cake is placed', 5_000);
    await rcon.command(`minecraft:effect give ${STRANGER} minecraft:hunger 30 255 true`);
    await waitFor(() => stranger.food === 0, `stranger starving (food ${stranger.food})`, 25_000);
    await rcon.command(`minecraft:effect clear ${STRANGER}`);
    await sleep(500);
    await stranger.activateBlock(stranger.blockAt(new Vec3(BX - 1, PLATFORM_Y + 1, BZ + 2)));
    await waitFor(() => stranger.food === 4, `a slice fills 4 instead of 2 (food ${stranger.food})`, 5_000);
  });

  console.log(`PASS nether-wishes: ${results.length} steps`);
} finally {
  await rcon.command(`netherwishes owner ${COFFIE}`).catch(() => {});
  await rcon.command(`minecraft:kill ${ZOMBIE}`).catch(() => {});
  await rcon.command(`minecraft:effect clear ${STRANGER}`).catch(() => {});
  await rcon.command(`minecraft:difficulty ${difficulty}`).catch(() => {});
  for (const bot of bots) await rcon.command(`minecraft:clear ${bot.username}`).catch(() => {});
  await quit(...bots);
  await rcon.command(`minecraft:forceload remove ${AWAY.x - 8} ${AWAY.z - 8} ${AWAY.x + 8} ${AWAY.z + 8}`).catch(() => {});
  await releasePlatform(rcon, BX, BZ).catch(() => {});
  rcon.close();
}
