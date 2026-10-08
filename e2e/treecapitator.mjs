import {
  Rcon, assert, buildPlatform, createBot, quit, randomOrigin, releasePlatform, sleep, teleport, waitFor,
} from './lib.mjs';

// Live's AuraSkills Treecapitator, with live's configs (copied into the e2e server by prepareE2eServer):
// 1. It fells 4x the per-wood cap (mana_abilities.yml max_blocks_multiplier: 4). Oak has no max_blocks
//    in sources/foraging.yml, so its cap is 100 x the multiplier: measured 403 felled at 4 and 106 at 1
//    (2026-10-08). AuraSkills 2.4.0 only follows logs up to +6 x, +6 z and +31 y from the broken one,
//    so the "tree" is a solid 7x7x20 oak cube (980 logs) broken at its low corner, big enough that the
//    cap, not the cube, decides how many fall.
// 2. It follows logs only (sources/foraging.yml treecapitator_applicable has no '*_leaves'), so leaves
//    touching a neighbouring tree no longer carry it, and the cap, into that tree.
const Y = 120, W = 7, H = 20, TOTAL = W * W * H;
const rcon = await Rcon.connect();
const origins = [];
const bots = [];

/** A fresh bot per swing: Treecapitator's cooldown (195 s at level 1) and spent mana stick to a player. */
async function lumberjack(x, z) {
  const bot = await createBot(`Tc${Math.floor(Math.random() * 1e5)}`);
  bots.push(bot);
  await rcon.command(`minecraft:gamemode survival ${bot.username}`);
  await rcon.command(`minecraft:clear ${bot.username}`);
  // Level 6 unlocks Treecapitator (mana_abilities.yml unlock: 6).
  await rcon.command(`skills skill setlevel ${bot.username} foraging 6`);
  await rcon.command(`minecraft:give ${bot.username} minecraft:diamond_axe`);
  await waitFor(() => bot.inventory.items().some((i) => i.name === 'diamond_axe'), 'axe in inventory');
  await bot.equip(bot.inventory.items().find((i) => i.name === 'diamond_axe'), 'hand');
  await teleport(rcon, bot, x, Y + 1, z);
  return bot;
}

/** Readies Treecapitator with a right-click, then breaks the log at +1 x from the bot's feet. */
async function swing(bot) {
  const log = () => bot.blockAt(bot.entity.position.offset(1, 0, 0).floored());
  await waitFor(() => log()?.name === 'oak_log', 'the log to cut loaded client-side');
  bot.activateItem();
  await sleep(300);
  bot.deactivateItem();
  await sleep(500);
  await bot.dig(log(), true);
}

/** Polls count() until it holds still for 3 s (Treecapitator fells one step per 50 ms). */
async function settle(count) {
  let last = -1, still = 0;
  await waitFor(async () => {
    const now = await count();
    still = now === last ? still + 1 : 0;
    last = now;
    await sleep(1000);
    return still >= 3;
  }, 'Treecapitator to finish', 90_000);
  return last;
}

async function columnLogs(x, z, from, to) {
  let n = 0;
  for (let y = from; y <= to; y++) {
    if (/passed/i.test(await rcon.command(`minecraft:execute if block ${x} ${y} ${z} minecraft:oak_log`))) n++;
  }
  return n;
}

async function capScenario() {
  const { bx: BX, bz: BZ } = randomOrigin();
  origins.push({ bx: BX, bz: BZ });
  await buildPlatform(rcon, BX, BZ, Y);
  await rcon.command(`minecraft:fill ${BX - 8} ${Y + 1} ${BZ - 8} ${BX + 8} ${Y + H + 1} ${BZ + 8} minecraft:air`);
  const box = `${BX} ${Y + 1} ${BZ} ${BX + W - 1} ${Y + H} ${BZ + W - 1}`;
  // Clone counts only the oak_log blocks it copies into a scratch spot: the logs still standing.
  const standing = async () => Number((await rcon.command(
    `minecraft:clone ${box} ${BX - 8} ${Y + 1} ${BZ - 8} filtered minecraft:oak_log force`,
  )).match(/cloned (\d+)/)?.[1] ?? 0);
  await rcon.command(`minecraft:fill ${box} minecraft:oak_log`);
  assert(await standing() === TOTAL, `the ${TOTAL}-log cube was built`);

  await swing(await lumberjack(BX - 0.5, BZ + 0.5));
  const felled = TOTAL - await settle(standing);
  console.log(`one swing felled ${felled} of ${TOTAL} logs`);
  assert(felled >= 350 && felled < TOTAL, `Treecapitator fells ~400 logs (cap 100 x multiplier 4), got ${felled}`);
  console.log('PASS treecapitator: the multiplier of 4 is in effect');
}

async function neighbourScenario() {
  const { bx: BX, bz: BZ } = randomOrigin();
  origins.push({ bx: BX, bz: BZ });
  await buildPlatform(rcon, BX, BZ, Y);
  await rcon.command(`minecraft:fill ${BX - 8} ${Y + 1} ${BZ - 8} ${BX + 8} ${Y + 10} ${BZ + 8} minecraft:air`);
  // Two 6-log oaks 4 blocks apart whose crowns touch: a 3-wide band of leaves spans the gap.
  const top = Y + 6, nx = BX + 4;
  await rcon.command(`minecraft:fill ${BX - 1} ${top - 2} ${BZ - 1} ${nx + 1} ${top + 1} ${BZ + 1} minecraft:oak_leaves[persistent=true]`);
  await rcon.command(`minecraft:fill ${BX} ${Y + 1} ${BZ} ${BX} ${top} ${BZ} minecraft:oak_log`);
  await rcon.command(`minecraft:fill ${nx} ${Y + 1} ${BZ} ${nx} ${top} ${BZ} minecraft:oak_log`);
  assert(await columnLogs(BX, BZ, Y + 1, top) === 6 && await columnLogs(nx, BZ, Y + 1, top) === 6, 'both trees were built');

  await swing(await lumberjack(BX - 0.5, BZ + 0.5));
  const cutLeft = await settle(() => columnLogs(BX, BZ, Y + 1, top));
  const neighbourLeft = await columnLogs(nx, BZ, Y + 1, top);
  console.log(`cut tree: ${6 - cutLeft} of 6 felled; neighbour: ${neighbourLeft} of 6 standing`);
  assert(cutLeft === 0, `the cut tree is felled whole, ${cutLeft} logs left`);
  assert(neighbourLeft === 6, `the neighbouring tree its leaves touch stays standing, ${neighbourLeft} of 6 left`);
  console.log('PASS treecapitator: logs only, the leaves do not carry it into the next tree');
}

try {
  await capScenario();
  await neighbourScenario();
} finally {
  for (const { bx, bz } of origins) {
    await rcon.command(`minecraft:fill ${bx - 8} ${Y + 1} ${bz - 8} ${bx + 8} ${Y + H + 1} ${bz + 8} minecraft:air`).catch(() => {});
    await rcon.command(`minecraft:kill @e[type=minecraft:item,x=${bx},y=${Y},z=${bz},distance=..40]`).catch(() => {});
    await releasePlatform(rcon, bx, bz);
  }
  await quit(...bots);
  rcon.close();
  await sleep(200);
}
