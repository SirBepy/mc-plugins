import {
  Rcon, assert, buildPlatform, createBot, quit, randomOrigin, releasePlatform, sleep, teleport, waitFor,
} from './lib.mjs';

// Risk under test: the real server ships Vanilla Tweaks' graves datapack, which scoops every item
// dropped on death into a grave entity. Tag already strips its IT sheep head from death drops
// (HeadProtectionListener#onDeath) before Bukkit ever spawns the dropped-item entities, so graves
// should never see it; the player's other items (including the PDC-marked displaced helmet) should
// still land in the grave normally, and the head should come back on after respawn via the watchdog.
const PLATFORM_Y = 200;
const { bx: BX, bz: BZ } = randomOrigin();
const HEAD_SLOT = 5;

const rcon = await Rcon.connect();
let bot;

const results = [];
async function step(name, fn) {
  await fn();
  results.push(name);
  console.log(`PASS ${name}`);
}

function headItem() {
  return bot.inventory.slots[HEAD_SLOT];
}

function graveSelector(pos) {
  return `@e[type=minecraft:item_display,tag=graves.grave,x=${pos.x},y=${pos.y},z=${pos.z},distance=..20,sort=nearest,limit=1]`;
}

/** Short, truncation-safe grave checks: data get entity on a real selector match returns a short
 * string (empty only on a failed `if`), unlike dumping the whole bundle_contents array, which the
 * server truncates past ~256 chars (a known vanilla RCON limit, confirmed experimentally here). */
async function bundleContains(pos, id) {
  const reply = await rcon.command(
    `execute positioned ${pos.x} ${pos.y} ${pos.z} as ${graveSelector(pos)} `
    + `if data entity @s item.components."minecraft:bundle_contents"[{id:"${id}"}] run data get entity @s Tags`,
  );
  return reply.trim().length > 0;
}

async function graveExists(pos) {
  const reply = await rcon.command(`execute positioned ${pos.x} ${pos.y} ${pos.z} run data get entity ${graveSelector(pos)} Tags`);
  return !reply.includes('No entity was found');
}

// Only GraveA's own items count: a mob's arrow or rotten flesh landing near the random death spot
// is not a grave leak and used to fail this check intermittently.
const CARRIED_ITEMS = ['minecraft:player_head', 'minecraft:iron_helmet', 'minecraft:diamond'];

async function droppedItemDump(pos, id) {
  return rcon.command(`execute positioned ${pos.x} ${pos.y} ${pos.z} run data get entity `
    + `@e[type=minecraft:item,distance=..20,nbt={Item:{id:"${id}"}},sort=nearest,limit=1]`);
}

try {
  await buildPlatform(rcon, BX, BZ, PLATFORM_Y);
  await rcon.command('tag clear');

  bot = await createBot('GraveA');
  await rcon.command('minecraft:gamemode survival GraveA');
  await rcon.command('minecraft:clear GraveA');
  await teleport(rcon, bot, BX, PLATFORM_Y + 1, BZ);

  await rcon.command('minecraft:give GraveA minecraft:iron_helmet 1');
  await waitFor(() => bot.inventory.items().some((i) => i.name === 'iron_helmet'), 'GraveA got a helmet');
  await bot.equip(bot.inventory.items().find((i) => i.name === 'iron_helmet'), 'head');
  await waitFor(() => headItem()?.name === 'iron_helmet', 'GraveA wears the iron helmet');
  await rcon.command('minecraft:give GraveA minecraft:diamond 3');
  await waitFor(() => bot.inventory.items().some((i) => i.name === 'diamond' && i.count === 3), 'GraveA got 3 diamonds');
  await sleep(500);

  await step('becoming IT moves the worn helmet to inventory and marks it', async () => {
    await rcon.command('tag set GraveA');
    await waitFor(() => headItem()?.name === 'player_head', 'GraveA wears the sheep head');
    await waitFor(() => bot.inventory.items().some((i) => i.name === 'iron_helmet'), 'iron helmet moved to inventory');
  });

  const deathPos = {
    x: Math.floor(bot.entity.position.x),
    y: Math.floor(bot.entity.position.y),
    z: Math.floor(bot.entity.position.z),
  };

  await rcon.command('minecraft:kill GraveA');
  // Let the death advancement chain run (same-tick pickup; compatibility_mode is off by default) and
  // the grave entity finish initializing before inspecting it.
  await sleep(2000);

  await step('the sheep head is never dropped and never enters the grave', async () => {
    for (const id of CARRIED_ITEMS) {
      const dump = await droppedItemDump(deathPos, id);
      assert(dump.includes('No entity was found'), `no stray dropped ${id} near death: ${dump}`);
    }
    assert(!(await bundleContains(deathPos, 'minecraft:player_head')), 'grave bundle has no player_head');
  });

  await step('the displaced helmet and other items are in the grave', async () => {
    assert(await graveExists(deathPos), 'a grave entity exists at the death location');
    assert(await bundleContains(deathPos, 'minecraft:iron_helmet'), 'grave bundle has the marked iron helmet');
    assert(await bundleContains(deathPos, 'minecraft:diamond'), 'grave bundle has the diamonds');
  });

  await step('after respawn, GraveA is still IT and wears the sheep head again', async () => {
    await waitFor(() => headItem()?.name === 'player_head', 'sheep head re-equipped after respawn', 15_000);
    const who = (await rcon.command('tag who')).replace(/§./g, '');
    assert(who.includes('GraveA is IT'), who);
  });

  await step('retrieving the grave returns the items with no duplicate head', async () => {
    // Respawn drops the bot at world spawn; walk it back by teleport to interact with the grave.
    await teleport(rcon, bot, deathPos.x, deathPos.y, deathPos.z);
    await sleep(500);
    const interactionEntity = Object.values(bot.entities).find((e) => e.name === 'interaction'
      && e.position.distanceTo(bot.entity.position) < 8);
    assert(interactionEntity, 'bot can see the grave interaction entity');
    await bot.activateEntity(interactionEntity);
    await sleep(1500);

    assert(bot.inventory.items().some((i) => i.name === 'iron_helmet'), 'helmet retrieved from the grave');
    assert(bot.inventory.items().some((i) => i.name === 'diamond' && i.count === 3), 'diamonds retrieved from the grave');
    assert(headItem()?.name === 'player_head', 'sheep head still worn, undisturbed by the retrieval');
    const strayHeads = bot.inventory.items().filter((i) => i.name === 'player_head');
    assert(strayHeads.length === 0, `no duplicate head in inventory (found ${strayHeads.length})`);
    assert(!(await graveExists(deathPos)), 'grave destroyed after being emptied');
  });

  console.log(`\nGraves e2e: ${results.length} passed`);
} catch (error) {
  console.error(`FAIL after ${results.length} passed: ${error.message}`);
  process.exitCode = 1;
} finally {
  await releasePlatform(rcon, BX, BZ);
  await quit(bot);
  rcon.close();
}
