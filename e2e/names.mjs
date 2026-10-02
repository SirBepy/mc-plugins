import {
  Rcon, assert, createBot, nbtText, quit, sleep, teleport, waitFor, waitForLine,
} from './lib.mjs';

// NameA picks colors and nicknames; NameB watches chat, the tab list and the floating label.
const PLATFORM_Y = 200;
const BX = 16 * Math.floor(Math.random() * 250 - 125);
const BZ = 16 * Math.floor(Math.random() * 250 - 125);

const rcon = await Rcon.connect();
const bots = {};
const results = [];
async function step(name, fn) {
  await fn();
  results.push(name);
  console.log(`PASS ${name}`);
}

function tabJson(viewer, name) {
  return JSON.stringify(viewer.players[name]?.displayName?.json ?? {});
}

function labelText(viewer, target) {
  const label = Object.values(viewer.entities).find((e) => e.name === 'text_display'
    && e.position.distanceTo(target.entity.position) < 4);
  if (!label) return undefined;
  return label.metadata.map((value) => (value && typeof value === 'object' ? nbtText(value) : ''))
    .join(' ');
}

async function openWindow(bot, command) {
  const opened = new Promise((resolve) => bot.once('windowOpen', resolve));
  bot.chat(command);
  return Promise.race([opened, sleep(5_000).then(() => { throw new Error(`${command} opened no window`); })]);
}

try {
  await rcon.command(`minecraft:forceload add ${BX - 16} ${BZ - 16} ${BX + 16} ${BZ + 16}`);
  await rcon.command(`minecraft:fill ${BX - 8} ${PLATFORM_Y} ${BZ - 8} ${BX + 8} ${PLATFORM_Y} ${BZ + 8} minecraft:stone`);
  await rcon.command(`minecraft:fill ${BX - 8} ${PLATFORM_Y + 1} ${BZ - 8} ${BX + 8} ${PLATFORM_Y + 4} ${BZ + 8} minecraft:air`);
  for (const [name, dx] of [['NameA', 0], ['NameB', 3]]) {
    const bot = await createBot(name);
    bots[name] = bot;
    await rcon.command(`minecraft:gamemode survival ${name}`);
    await rcon.command(`minecraft:deop ${name}`);
    await teleport(rcon, bot, BX + dx, PLATFORM_Y + 1, BZ);
  }
  const { NameA, NameB } = bots;
  await rcon.command('nick NameA reset').catch(() => {});
  NameA.chat('/nick reset');
  await sleep(500);

  await step('/color opens a GUI with colors', async () => {
    const window = await openWindow(NameA, '/color');
    const items = window.slots.slice(0, window.inventoryStart).filter(Boolean);
    assert(items.length >= 16, `at least the 16 named colors (got ${items.length})`);
    NameA.closeWindow(window);
  });

  await step('clicking a color applies it to chat and the tab list', async () => {
    const window = await openWindow(NameA, '/color');
    const index = window.slots.slice(0, window.inventoryStart)
      .findIndex((item) => item && /aqua/i.test(JSON.stringify(item.nbt ?? item.components ?? item.customName ?? '') + item.name));
    const slot = index >= 0 ? index : 11;
    await NameA.clickWindow(slot, 0, 0);
    await waitForLine(NameA, /Color applied/);
    if (NameA.currentWindow) NameA.closeWindow(NameA.currentWindow);
    await waitFor(() => /color/.test(tabJson(NameB, 'NameA')), 'NameA tab entry is colored');
  });

  await step('players without permission cannot nickname', async () => {
    const mark = NameA.lines.length;
    NameA.chat('/nick Sneaky');
    await waitForLine(NameA, /permission|Unknown or incomplete command|Unknown command/, 5_000, mark);
  });

  await rcon.command('minecraft:op NameA');
  await sleep(500);

  await step('/nick <name> changes chat, tab and the floating label', async () => {
    const mark = NameB.lines.length;
    NameA.chat('/nick Captain_A');
    await waitForLine(NameA, /Nickname set to Captain_A/);
    NameA.chat('ahoy');
    const line = await waitForLine(NameB, /Captain_A.*ahoy/, 5_000, mark);
    assert(!line.includes('NameA:'), `chat shows the nick, not the real name: ${line}`);
    await waitFor(() => NameB.players.NameA?.displayName?.toString().includes('Captain_A'), 'tab shows nick');
    await waitFor(() => labelText(NameB, NameA)?.includes('Captain_A'), 'floating label shows nick', 5_000);
  });

  await step('nicknames cannot impersonate a real player', async () => {
    const mark = NameA.lines.length;
    NameA.chat('/nick NameB');
    await waitForLine(NameA, /already a player's real name/, 5_000, mark);
  });

  await step('nicknames reject bad characters and strip MiniMessage tags', async () => {
    let mark = NameA.lines.length;
    NameA.chat('/nick Bad!Name');
    await waitForLine(NameA, /may only contain/, 5_000, mark);
    mark = NameA.lines.length;
    NameA.chat('/nick <red>Evil');
    await waitForLine(NameA, /Nickname set to Evil\./, 5_000, mark);
  });

  await step('the anvil GUI sets a nickname', async () => {
    const window = await openWindow(NameA, '/nick');
    assert(window.type?.includes('anvil') || /anvil/i.test(String(window.type)), `anvil window (${window.type})`);
    NameA._client.write('name_item', { name: 'Anvil_Name' });
    await sleep(500);
    await NameA.clickWindow(2, 0, 0).catch(() => {});
    await waitForLine(NameA, /Nickname set to Anvil_Name/, 5_000);
    if (NameA.currentWindow) NameA.closeWindow(NameA.currentWindow);
  });

  await step('/nick reset restores the real name', async () => {
    NameA.chat('/nick reset');
    await waitForLine(NameA, /Nickname reset/);
    await waitFor(() => NameB.players.NameA?.displayName?.toString().includes('NameA'), 'tab back to real name');
  });

  console.log(`\nNames e2e: ${results.length} passed`);
} catch (error) {
  console.error(`FAIL after ${results.length} passed: ${error.message}`);
  process.exitCode = 1;
} finally {
  await rcon.command('minecraft:deop NameA').catch(() => {});
  await rcon.command(`minecraft:forceload remove ${BX - 16} ${BZ - 16} ${BX + 16} ${BZ + 16}`).catch(() => {});
  await quit(...Object.values(bots));
  rcon.close();
}
