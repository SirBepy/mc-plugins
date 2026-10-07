import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  Rcon, assert, buildPlatform, createBot, nbtText, quit, randomOrigin, releasePlatform, sleep, teleport,
  waitFor, waitForLine,
} from './lib.mjs';

// IT's tracker compass. TrkA is IT; TrkB (2 blocks, within hitting reach) and TrkC (8 blocks) are
// trackable, TrkD stands closest but is opted out and must never show up. AFK is shortened (auto-pass off) so TrkB can go
// AFK mid-run while TrkA and TrkC keep fidgeting.
const here = path.dirname(fileURLToPath(import.meta.url));
const configPath = path.join(here, '..', 'testserver', 'run-e2e', 'plugins', 'Tag', 'config.yml');
const THRESHOLD = 4;

const PLATFORM_Y = 200;
const { bx: BX, bz: BZ } = randomOrigin();
const rcon = await Rcon.connect();
const bots = {};
const original = fs.readFileSync(configPath, 'utf8');

const results = [];
async function step(name, fn) {
  await fn();
  results.push(name);
  console.log(`PASS ${name}`);
}

async function whoIsIt() {
  return (await rcon.command('tag who')).replace(/§./g, '').trim();
}

function withShortAfk(text) {
  assert(/^afk:/m.test(text), 'Tag config has an afk: section (delete a stale run-e2e Tag config.yml and restart)');
  const set = (t, key, value) => t.replace(new RegExp(`^(  ${key}:).*$`, 'm'), `$1 ${value}`);
  let out = set(text, 'enabled', 'true');
  out = set(out, 'threshold-seconds', THRESHOLD);
  out = set(out, 'auto-pass', 'false');
  out = set(out, 'check-interval-seconds', 1);
  return out;
}

// The nether round trip below is a command teleport right next to IT, which Tag's teleport block
// refuses. A config.yml older than that section uses the code default (on), hence the append.
function withoutTeleportBlock(text) {
  if (/^teleport-block:/m.test(text)) {
    return text.replace(/^(teleport-block:\r?\n(?:(?:  #.*)?\r?\n)*?  enabled:).*$/m, '$1 false');
  }
  return `${text.trimEnd()}\n\nteleport-block:\n  enabled: false\n`;
}

function compassSlot(bot) {
  return bot.inventory.slots.findIndex((item) => item?.name === 'compass');
}

/** Moves the compass into the hotbar if needed and selects it. */
async function holdCompass(bot) {
  await waitFor(() => compassSlot(bot) !== -1, `${bot.username} has the tracker compass`);
  const slot = compassSlot(bot);
  // Window slots 36-44 are the hotbar.
  if (slot >= 36 && slot <= 44) {
    bot.setQuickBarSlot(slot - 36);
  } else {
    await bot.equip(bot.inventory.slots[slot], 'hand');
  }
  await sleep(300);
}

// Distinct from bot.lines: only the action bar, so chat can't satisfy a readout check.
function actionBars(bot) {
  return bot.actionBars;
}

async function waitForActionBar(bot, pattern, timeoutMs = 8_000) {
  const mark = bot.actionBars.length;
  await waitFor(() => bot.actionBars.slice(mark).some((l) => pattern.test(l)),
    `${bot.username} action bar ${pattern} (last: ${bot.actionBars.slice(-3).join(' | ')})`, timeoutMs);
}

// A lodestone tracker with no target is what makes the compass spin.
function spinning(bot) {
  const item = bot.inventory.slots[compassSlot(bot)];
  return (item?.components ?? []).some((c) => c.type === 'lodestone_tracker');
}

async function rightClick(bot) {
  bot.activateItem();
  await sleep(150);
  bot.deactivateItem();
  await sleep(400);
}

const fidgeters = new Set();
let yaw = 0;
const fidget = setInterval(() => {
  yaw = (yaw + 0.6) % (Math.PI * 2);
  for (const bot of fidgeters) bot.look(yaw, 0, true).catch(() => {});
}, 400);

try {
  fs.writeFileSync(configPath, withoutTeleportBlock(withShortAfk(original)));
  await rcon.command('tag reload');
  await buildPlatform(rcon, BX, BZ, PLATFORM_Y);
  await rcon.command('tag clear');

  for (const [name, dx] of [['TrkA', 0], ['TrkD', -1], ['TrkB', 2], ['TrkC', 8]]) {
    const bot = await createBot(name);
    bot.actionBars = [];
    // Mineflayer's own actionBar event never fires for the NBT-encoded action_bar packet ViaBackwards
    // delivers, so read the packet directly.
    bot._client.on('action_bar', (packet) => bot.actionBars.push(nbtText(packet.text).trim()));
    bot._client.on('system_chat', (packet) => {
      if (packet.isActionBar) bot.actionBars.push(nbtText(packet.content).trim());
    });
    bots[name] = bot;
    await rcon.command(`minecraft:gamemode survival ${name}`);
    await rcon.command(`minecraft:clear ${name}`);
    await teleport(rcon, bot, BX + dx, PLATFORM_Y + 1, BZ);
    bot.chat('/tag optin');
    fidgeters.add(bot);
  }
  const { TrkA, TrkB, TrkC, TrkD } = bots;
  TrkD.chat('/tag optout');
  await waitForLine(TrkD, /opted out of Tag/);

  await rcon.command('tag set TrkA');

  await step('IT gets a tracker compass that points at the nearest opted-in player', async () => {
    await holdCompass(TrkA);
    await waitForActionBar(TrkA, /^TrkB - \d+m$/);
    // The compass points via the client-side spawn position, so that is what must sit on TrkB.
    await waitFor(() => TrkA.spawnPoint?.distanceTo(TrkB.entity.position) < 2, 'compass target is TrkB', 5_000);
    assert(!spinning(TrkA), 'a compass with a target in this world does not spin');
  });

  await step('right-click cycles to the next player, skipping the opted-out one, and wraps', async () => {
    await rightClick(TrkA);
    await waitForActionBar(TrkA, /^TrkC - \d+m$/);
    await rightClick(TrkA);
    await waitForActionBar(TrkA, /^TrkB - \d+m$/);
    assert(!actionBars(TrkA).some((l) => l.includes('TrkD')), 'the opted-out player is never shown');
  });

  await step('the compass cannot be dropped', async () => {
    const slot = compassSlot(TrkA);
    await TrkA.tossStack(TrkA.inventory.slots[slot]).catch(() => {});
    await sleep(1_000);
    assert(compassSlot(TrkA) !== -1, 'compass is still in the inventory');
    await holdCompass(TrkA);
  });

  await step('a player in another dimension reads "Not in this realm"', async () => {
    // Namespaced: EssentialsX shadows a bare `tp` even inside execute and lands it in the overworld.
    await rcon.command(`minecraft:execute in minecraft:the_nether run minecraft:tp TrkC ${BX} 120 ${BZ}`);
    await sleep(1_500);
    await rightClick(TrkA);
    await waitForActionBar(TrkA, /^TrkC - Not in this realm$/);
    await waitFor(() => spinning(TrkA), 'compass spins for a target in another realm', 5_000);
    await teleport(rcon, TrkC, BX + 8, PLATFORM_Y + 1, BZ);
  });

  await step('an AFK player is labelled and cannot be tagged', async () => {
    fidgeters.delete(TrkB);
    await sleep((THRESHOLD + 2) * 1000);
    await waitFor(async () => {
      await rightClick(TrkA);
      return actionBars(TrkA).slice(-2).some((l) => /^TrkB - AFK, can't be tagged$/.test(l));
    }, 'TrkA cycled onto AFK TrkB', 15_000);
    const target = TrkA.players.TrkB?.entity;
    assert(target, 'TrkA can see TrkB');
    const mark = TrkA.lines.length;
    await TrkA.lookAt(target.position.offset(0, 1.6, 0), true);
    TrkA.attack(target);
    await waitForLine(TrkA, /TrkB is AFK and can't be tagged/, 5_000, mark);
    assert((await whoIsIt()).includes('TrkA is IT'), 'TrkA is still IT');
  });

  await step('tagging passes the compass to the new IT', async () => {
    await teleport(rcon, TrkC, BX + 1, PLATFORM_Y + 1, BZ + 1);
    const target = TrkA.players.TrkC?.entity;
    assert(target, 'TrkA can see TrkC');
    const mark = TrkA.lines.length;
    await TrkA.lookAt(target.position.offset(0, 1.6, 0), true);
    TrkA.attack(target);
    await waitForLine(TrkA, /TrkA tagged TrkC/, 5_000, mark);
    await waitFor(() => compassSlot(TrkA) === -1, 'old IT lost the compass', 5_000);
    await waitFor(() => compassSlot(TrkC) !== -1, 'new IT got the compass', 5_000);
  });

  console.log(`\nTracker e2e: ${results.length} passed`);
} catch (error) {
  console.error(`FAIL after ${results.length} passed: ${error.message}`);
  process.exitCode = 1;
} finally {
  clearInterval(fidget);
  fs.writeFileSync(configPath, original);
  await rcon.command('tag reload').catch(() => {});
  await rcon.command('tag clear').catch(() => {});
  bots.TrkD?.chat('/tag optin');
  await sleep(500);
  await releasePlatform(rcon, BX, BZ);
  await quit(...Object.values(bots));
  rcon.close();
}
