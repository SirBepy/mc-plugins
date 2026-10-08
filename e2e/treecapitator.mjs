import {
  Rcon, assert, buildPlatform, createBot, quit, randomOrigin, releasePlatform, sleep, teleport, waitFor,
} from './lib.mjs';

// Live's AuraSkills Treecapitator fells 4x the per-wood cap (server/config/plugins/AuraSkills/
// mana_abilities.yml max_blocks_multiplier: 4, copied into the e2e server by prepareE2eServer).
// Oak has no max_blocks in AuraSkills' sources/foraging.yml, so its cap is 100 x the multiplier:
// measured 403 felled at 4 and 106 at 1 (2026-10-08). AuraSkills 2.4.0 only follows logs up to
// +6 x, +6 z and +31 y from the broken one, so the "tree" is a solid 7x7x20 oak cube (980 logs)
// broken at its low corner, big enough that the cap, not the cube, decides how many fall.
const Y = 120, W = 7, H = 20, TOTAL = W * W * H;
const { bx: BX, bz: BZ } = randomOrigin();
const box = `${BX} ${Y + 1} ${BZ} ${BX + W - 1} ${Y + H} ${BZ + W - 1}`;
const rcon = await Rcon.connect();
let bot;

/** Logs still standing: clone counts only the oak_log blocks it copies into a scratch spot. */
async function standing() {
  const res = await rcon.command(`minecraft:clone ${box} ${BX - 8} ${Y + 1} ${BZ - 8} filtered minecraft:oak_log force`);
  return Number(res.match(/cloned (\d+)/)?.[1] ?? 0);
}

try {
  await buildPlatform(rcon, BX, BZ, Y);
  await rcon.command(`minecraft:fill ${BX - 8} ${Y + 1} ${BZ - 8} ${BX + 8} ${Y + H + 1} ${BZ + 8} minecraft:air`);
  await rcon.command(`minecraft:fill ${box} minecraft:oak_log`);
  assert(await standing() === TOTAL, `the ${TOTAL}-log cube was built`);

  // A fresh name per run: Treecapitator's cooldown (195 s at level 1) and spent mana stick to a player.
  bot = await createBot(`Tc${Math.floor(Math.random() * 1e5)}`);
  await rcon.command(`minecraft:gamemode survival ${bot.username}`);
  await rcon.command(`minecraft:clear ${bot.username}`);
  // Level 6 unlocks Treecapitator (mana_abilities.yml unlock: 6).
  await rcon.command(`skills skill setlevel ${bot.username} foraging 6`);
  await rcon.command(`minecraft:give ${bot.username} minecraft:diamond_axe`);
  await waitFor(() => bot.inventory.items().some((i) => i.name === 'diamond_axe'), 'axe in inventory');
  await bot.equip(bot.inventory.items().find((i) => i.name === 'diamond_axe'), 'hand');
  await teleport(rcon, bot, BX - 0.5, Y + 1, BZ + 0.5);
  const corner = () => bot.blockAt(bot.entity.position.offset(1, 0, 0).floored());
  await waitFor(() => corner()?.name === 'oak_log', 'the cube corner loaded client-side');

  // Right-click readies Treecapitator; the next log broken within a few seconds triggers it.
  bot.activateItem();
  await sleep(300);
  bot.deactivateItem();
  await sleep(500);
  await bot.dig(corner(), true);

  // It fells one step per 50 ms; done once the count holds still for 3 s.
  let last = -1, still = 0;
  await waitFor(async () => {
    const now = await standing();
    still = now === last ? still + 1 : 0;
    last = now;
    await sleep(1000);
    return still >= 3;
  }, 'Treecapitator to finish', 90_000);
  const felled = TOTAL - last;
  console.log(`one swing felled ${felled} of ${TOTAL} logs`);
  assert(felled >= 350 && felled < TOTAL, `Treecapitator fells ~400 logs (cap 100 x multiplier 4), got ${felled}`);
  console.log('PASS treecapitator: the multiplier of 4 is in effect');
} finally {
  await rcon.command(`minecraft:fill ${BX - 8} ${Y + 1} ${BZ - 8} ${BX + 8} ${Y + H + 1} ${BZ + 8} minecraft:air`).catch(() => {});
  await rcon.command(`minecraft:kill @e[type=minecraft:item,x=${BX},y=${Y},z=${BZ},distance=..40]`).catch(() => {});
  await releasePlatform(rcon, BX, BZ);
  await quit(bot);
  rcon.close();
  await sleep(200);
}
