import mineflayer from 'mineflayer';
import { Rcon, HOST, BOT_VERSION, assert, buildPlatform, quit, randomOrigin, releasePlatform, sleep, waitFor, teleport } from './lib.mjs';

// Gameplay smoke test for the ported "Backpacks!" datapack (server/datapacks/eclipse-backpacks).
// Not wired into run-all.mjs: that suite targets testserver (26.2, port 25565 via lib.mjs'
// createBot); this targets server/local (26.3, port 25566), so it opens its own bot connection
// instead of reusing lib.mjs's hardcoded-port createBot.
// Usage: node eclipse-backpacks.mjs   (server/local must already be booted with RCON up)
//
// KNOWN BLOCKER (2026-10-04, see PORTED.md's "Known limitation" section and todo filed for this):
// a Mineflayer bot currently can't stay connected to server/local long enough to run this. It
// reaches the configuration phase but hangs there (server/local pushes a server-resource-pack
// that lib.mjs's createBot never acks) and, once that's worked around, gets kicked almost
// immediately with multiplayer.disconnect.invalid_player_movement - a declare_recipes parsing
// desync (this server's huge combined recipe book overruns node-minecraft-protocol's 26.1
// parser once ViaBackwards has translated it down from 26.3) corrupts every packet after it,
// including movement. Same class of issue already on record in
// .claude/todos/10-handoff-real-server-setup.md for why the bot e2e suite uses a separate 26.2
// testserver instead of a 26.3 one. This script is written and believed correct for when that
// gap closes (newer Mineflayer, or a custom minimal client); it has not been run to completion.

function createLocalBot(username) {
  return new Promise((resolve, reject) => {
    const bot = mineflayer.createBot({ host: HOST, port: 25566, username, version: BOT_VERSION, auth: 'offline' });
    const timer = setTimeout(() => reject(new Error(`${username} did not spawn`)), 60_000);
    bot.once('spawn', async () => {
      clearTimeout(timer);
      await bot.waitForChunksToLoad();
      await sleep(500);
      resolve(bot);
    });
    bot.on('kicked', (reason) => console.error(`[${username}] kicked: ${JSON.stringify(reason)}`));
    bot.once('kicked', (reason) => reject(new Error(`${username} kicked: ${JSON.stringify(reason)}`)));
    bot.once('error', reject);
  });
}

const PLATFORM_Y = 200;
// The small backpack's exact result item, taken verbatim from
// data/backpacks/recipe/crafting_table/small.json's "result" (component-for-component).
const SMALL_BACKPACK_ITEM = 'minecraft:command_block[' +
  'item_model="backpacks:backpacks/normal/1/brown",' +
  'item_name={"translate":"item.backpacks.normal.1"},' +
  'max_stack_size=1,' +
  'custom_data={backpacks:{real_backpack:1b,tier:0,contents:{slots:3}}},' +
  'rarity="uncommon",' +
  'lore=[{text:"Backpacks!",color:"blue",italic:true}]' +
  ']';

const rcon = await Rcon.connect('../server/local');
const bots = {};
const results = [];
async function step(name, fn) {
  await fn();
  results.push(name);
  console.log(`PASS ${name}`);
}

function parseCount(dataGetResponse) {
  // "<entity> has the following entity data: 5" or "... : [...]" etc. Pull the trailing int if present.
  const m = dataGetResponse.match(/:\s*(-?\d+)\s*$/);
  return m ? Number(m[1]) : null;
}

try {
  const { bx: BX, bz: BZ } = randomOrigin();
  await buildPlatform(rcon, BX, BZ, PLATFORM_Y);
  await rcon.command('gamerule doMobSpawning false');
  const bot = await createLocalBot('BPTester');
  bots.BPTester = bot;
  await rcon.command('minecraft:gamemode survival BPTester');
  await teleport(rcon, bot, BX, PLATFORM_Y + 1, BZ);
  await rcon.command('op BPTester');

  // --- a. craft/give + recipe registration ------------------------------------------------------
  await step('recipe backpacks:crafting_table/small is registered', async () => {
    const res = await rcon.command('recipe give BPTester backpacks:crafting_table/small');
    assert(/Gave player \d+ recipe/.test(res) || /already know/i.test(res), res);
  });

  await step('give the exact small-backpack result item', async () => {
    const res = await rcon.command(`give BPTester ${SMALL_BACKPACK_ITEM} 1`);
    assert(/Gave/.test(res), res);
    await waitFor(() => bot.inventory.items().some((i) => i.name === 'command_block'), 'backpack in inventory');
  });

  // --- b. hold it, menu auto-summons every tick while held & not sneaking ----------------------
  let menuEntity;
  await step('holding the backpack auto-summons the menu minecart', async () => {
    const backpack = bot.inventory.items().find((i) => i.name === 'command_block');
    await bot.equip(backpack, 'hand');
    await waitFor(async () => {
      const res = await rcon.command('execute if entity @e[tag=backpacks.backpack_menu_ready]');
      return /^Test passed/.test(res);
    }, 'backpacks.backpack_menu_ready tag appears', 10_000);
  });

  let windowSlotCount;
  await step('interacting with the menu minecart opens a window', async () => {
    await sleep(500); // let the entity position sync to the bot
    menuEntity = Object.values(bot.entities).find((e) => e.name === 'chest_minecart'
      && e.position.distanceTo(bot.entity.position) < 5);
    assert(menuEntity, 'menu minecart entity visible to bot');
    const windowOpened = new Promise((resolve) => bot.once('windowOpen', resolve));
    await bot.activateEntity(menuEntity);
    const win = await windowOpened;
    windowSlotCount = win.slots.length - 36; // minus the player inventory overlay
    assert(windowSlotCount > 0, `container slot count = ${windowSlotCount}`);
    console.log(`menu container slot count: ${windowSlotCount}`);
  });

  // --- c. put diamonds into an unlocked slot (small backpack declares contents.slots:3, so slot 0 persists) ---
  await rcon.command('give BPTester minecraft:diamond 5');
  await waitFor(() => bot.inventory.items().some((i) => i.name === 'diamond' && i.count === 5), 'bot has 5 diamonds');

  await step('placing 5 diamonds into container slot 0 persists into the backpack item', async () => {
    const win = bot.currentWindow;
    const diamondSlot = win.slots.findIndex((s) => s && s.name === 'diamond');
    assert(diamondSlot === -1, 'diamonds start outside the container window (sanity)');
    // Mineflayer windows: container slots first, then player inv/hotbar. Click diamond stack in
    // player inventory, then click container slot 0 to deposit it there.
    const invDiamondSlot = win.slots.findIndex((s, idx) => idx >= windowSlotCount && s && s.name === 'diamond');
    assert(invDiamondSlot !== -1, 'diamonds visible in the window inventory section');
    await bot.clickWindow(invDiamondSlot, 0, 0); // pick up
    await sleep(200);
    await bot.clickWindow(0, 0, 0); // place into container slot 0
    await sleep(200);
    await bot.closeWindow(win);
    await sleep(1_500); // let bp/main tick & save the container contents into item NBT

    const invData = await rcon.command('data get entity BPTester SelectedItem.components."minecraft:custom_data".backpacks.contents.pages[0][0]');
    assert(/diamond/.test(invData), `backpack item stores diamonds: ${invData}`);
    assert(!bot.inventory.items().some((i) => i.name === 'diamond'), 'bot no longer holds the diamonds (no dupe)');
  });

  // --- d. reopen and take them back out ----------------------------------------------------------
  await step('reopening and withdrawing the diamonds restores bot inventory, backpack empties', async () => {
    await waitFor(async () => {
      const res = await rcon.command('execute if entity @e[tag=backpacks.backpack_menu_ready]');
      return /^Test passed/.test(res);
    }, 'menu re-ready', 10_000);
    menuEntity = Object.values(bot.entities).find((e) => e.name === 'chest_minecart'
      && e.position.distanceTo(bot.entity.position) < 5);
    const windowOpened = new Promise((resolve) => bot.once('windowOpen', resolve));
    await bot.activateEntity(menuEntity);
    const win = await windowOpened;
    const diamondSlot = win.slots.findIndex((s) => s && s.name === 'diamond');
    assert(diamondSlot !== -1, 'diamonds visible in the reopened container');
    await bot.clickWindow(diamondSlot, 0, 0);
    await sleep(200);
    const emptyPlayerSlot = win.slots.findIndex((s, idx) => idx >= windowSlotCount && !s);
    await bot.clickWindow(emptyPlayerSlot, 0, 0);
    await sleep(200);
    await bot.closeWindow(win);
    await sleep(1_500);

    assert(bot.inventory.items().some((i) => i.name === 'diamond' && i.count === 5), '5 diamonds back on bot');
    const after = await rcon.command('data get entity BPTester SelectedItem.components."minecraft:custom_data".backpacks.contents.pages[0]');
    assert(/\[\]|\[0b,0b,0b\]|has the following entity data: \[\]/.test(after) || !/diamond/.test(after), `backpack emptied: ${after}`);
  });

  // --- e. dupe probes: drop + hand-swap while menu open -------------------------------------------
  await step('dupe probe: drop and hand-swap while the menu is open does not duplicate', async () => {
    await rcon.command('give BPTester minecraft:diamond 5');
    await waitFor(() => bot.inventory.items().some((i) => i.name === 'diamond' && i.count === 5), '5 diamonds again');
    const backpack = bot.inventory.items().find((i) => i.name === 'command_block');
    await bot.equip(backpack, 'hand');
    await waitFor(async () => /^Test passed/.test(await rcon.command('execute if entity @e[tag=backpacks.backpack_menu_ready]')), 'menu ready', 10_000);
    await sleep(500);
    menuEntity = Object.values(bot.entities).find((e) => e.name === 'chest_minecart'
      && e.position.distanceTo(bot.entity.position) < 5);
    const windowOpened = new Promise((resolve) => bot.once('windowOpen', resolve));
    await bot.activateEntity(menuEntity);
    await windowOpened;

    await bot.tossStack(backpack).catch(() => {}); // try to drop the backpack while menu is open
    await sleep(300);
    bot.swapHands?.();
    await sleep(300);
    try { await bot.closeWindow(bot.currentWindow); } catch { /* window may already be gone */ }
    await sleep(1_500);

    const invDiamonds = bot.inventory.items().filter((i) => i.name === 'diamond').reduce((a, i) => a + i.count, 0);
    const danglingBackpackData = await rcon.command(`data get entity @e[type=item,x=${BX},y=${PLATFORM_Y},z=${BZ},distance=..10,limit=1,sort=nearest] Item.components."minecraft:custom_data"`);
    let danglingDiamonds = 0;
    const m = danglingBackpackData.match(/diamond.*?Count:(\d+)b/);
    if (m) danglingDiamonds = Number(m[1]);
    console.log(`dupe probe totals: bot=${invDiamonds} dangling=${danglingDiamonds}`);
    assert(invDiamonds + danglingDiamonds <= 5, `no diamond duplication: bot=${invDiamonds} dangling=${danglingDiamonds}`);
  });

  // --- f. death with items in the backpack --------------------------------------------------------
  await step('death drops the backpack with its contents, no duplication', async () => {
    // Clear any leftover dropped items near the platform before killing, so the post-death check
    // below can assume the only item entity nearby is the one death just dropped.
    await rcon.command(`kill @e[type=item,x=${BX},y=${PLATFORM_Y},z=${BZ},distance=..20]`);
    await rcon.command('give BPTester minecraft:diamond 3');
    await waitFor(() => bot.inventory.items().some((i) => i.name === 'diamond' && i.count === 3), '3 diamonds before death');
    const backpack = bot.inventory.items().find((i) => i.name === 'command_block');
    await bot.equip(backpack, 'hand');
    await waitFor(async () => /^Test passed/.test(await rcon.command('execute if entity @e[tag=backpacks.backpack_menu_ready]')), 'menu ready', 10_000);
    await sleep(500);
    menuEntity = Object.values(bot.entities).find((e) => e.name === 'chest_minecart'
      && e.position.distanceTo(bot.entity.position) < 5);
    const windowOpened = new Promise((resolve) => bot.once('windowOpen', resolve));
    await bot.activateEntity(menuEntity);
    const win = await windowOpened;
    const invDiamondSlot = win.slots.findIndex((s, idx) => idx >= windowSlotCount && s && s.name === 'diamond');
    await bot.clickWindow(invDiamondSlot, 0, 0);
    await sleep(200);
    await bot.clickWindow(0, 0, 0);
    await sleep(200);
    await bot.closeWindow(win);
    await sleep(1_500);

    await rcon.command('kill BPTester');
    await sleep(1_500);
    const dropped = await rcon.command(`data get entity @e[type=item,x=${BX},y=${PLATFORM_Y},z=${BZ},distance=..10,limit=1,sort=nearest] Item`);
    assert(/command_block/.test(dropped), `dropped item is the backpack: ${dropped}`);
    assert(/diamond/.test(dropped), `dropped backpack still carries its diamonds: ${dropped}`);
  });

  // --- g. world minecart disguises use the new 26.3 DisplayState format with no serialization errors ---
  await step('world minecarts get DisplayState disguised with zero serialization errors', async () => {
    await rcon.command(`forceload add ${BX - 32} ${BZ - 32} ${BX + 32} ${BZ + 32}`);
    await rcon.command(`summon chest_minecart ${BX + 10} ${PLATFORM_Y} ${BZ} {Tags:["e2e_probe_chest"]}`);
    await rcon.command(`summon minecart ${BX + 12} ${PLATFORM_Y} ${BZ} {Tags:["e2e_probe_plain"]}`);
    await sleep(3_000);
    const chestState = await rcon.command('data get entity @e[tag=e2e_probe_chest,limit=1] DisplayState');
    const plainState = await rcon.command('data get entity @e[tag=e2e_probe_plain,limit=1] DisplayState');
    assert(/id: "minecraft:command_block"/.test(chestState), `chest minecart disguised: ${chestState}`);
    assert(/id: "minecraft:command_block"/.test(plainState), `plain minecart disguised: ${plainState}`);
    await rcon.command('kill @e[tag=e2e_probe_chest]');
    await rcon.command('kill @e[tag=e2e_probe_plain]');
    await releasePlatform(rcon, BX, BZ);
    await rcon.command(`forceload remove ${BX - 32} ${BZ - 32} ${BX + 32} ${BZ + 32}`);
  });

  // --- h. upgrade recipes registered (best effort) -------------------------------------------------
  await step('smithing upgrade recipes are registered', async () => {
    const r2 = await rcon.command('recipe give BPTester backpacks:smithing/upgrade/2');
    const r3 = await rcon.command('recipe give BPTester backpacks:smithing/upgrade/3');
    const r4 = await rcon.command('recipe give BPTester backpacks:smithing/upgrade/4');
    for (const [name, res] of [['2', r2], ['3', r3], ['4', r4]]) {
      assert(!/Unknown recipe/i.test(res), `recipe backpacks:smithing/upgrade/${name} exists: ${res}`);
    }
  });

  console.log(`\nEclipse Backpacks e2e: ${results.length} passed`);
} catch (error) {
  console.error(`FAIL after ${results.length} passed: ${error.message}\n${error.stack}`);
  process.exitCode = 1;
} finally {
  await quit(...Object.values(bots));
  rcon.close();
}
