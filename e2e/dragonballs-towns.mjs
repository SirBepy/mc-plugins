import { sleep, waitFor, waitForLine } from './lib.mjs';
import { HUNTER, PLATFORM_Y, openSuite, placeBallOn } from './dragonballs-lib.mjs';

// No Dragon Ball stays inside a Towny town (Joe, 2026-10-08): DBHunter founds a town on the
// platform's own chunk (console-created, so no money is involved), can't place a ball in it but
// can just past its edge, and a ball tossed into the town moves itself to the nearest wilderness
// spot open to the sky within a few seconds, telling DBHunter where it went.
const {
  rcon, BX, BZ, step, plain, status, tossTowardCentre, finish, hunter: bot,
} = await openSuite('DragonBalls towns');
const TOWN = `dbtown${Date.now() % 100000}`;
const ROOF = { x1: BX - 8, x2: BX - 1, z1: BZ - 8, z2: BZ + 8, y: PLATFORM_Y + 3 };

let error;
try {
  await rcon.command('dragonballs wake');
  await sleep(1000);

  // The platform runs BX-8..BX+8; the town is the chunk DBHunter stands in, BX..BX+15.
  await step('a town is founded on the platform', async () => {
    await rcon.command(`townyadmin town new ${TOWN} ${HUNTER}`);
    await waitFor(async () => plain(await rcon.command(`townyadmin town ${TOWN} toggle pvp off`)).includes('PVP'),
      'the town exists', 10_000);
  });

  await step('a ball cannot be placed inside the town', async () => {
    await rcon.command(`dragonballs give ${HUNTER} 3`);
    await waitFor(() => bot.inventory.items().some((i) => i.name === 'player_head'), 'DBHunter holds the 3-star');
    let floated = true;
    try {
      await placeBallOn(bot, BX + 1, PLATFORM_Y, BZ + 1);
    } catch {
      floated = false;
    }
    if (floated) throw new Error('a ball floated inside the town');
    const s = (await status()).overworld[3];
    if (s.state !== 'held' || s.holder !== HUNTER) throw new Error(`the 3-star should still be held (${JSON.stringify(s)})`);
  });

  await step('a ball can still be placed just past the town edge', async () => {
    await placeBallOn(bot, BX - 3, PLATFORM_Y, BZ + 1);
    const s = (await status()).overworld[3];
    if (s.state !== 'placed' || s.x !== BX - 3) throw new Error(`placed in the wilderness (${JSON.stringify(s)})`);
    await rcon.command(`dragonballs give ${HUNTER} 3`);
    await waitFor(async () => (await status()).overworld[3].state === 'held', 'the 3-star is back in hand');
  });

  // A roof over the wilderness half of the platform makes a covered gap at the platform's height
  // (Joe, 2026-10-09: an evicted ball landed 8 blocks underground): the ball must land on the roof.
  await step('a ball tossed into the town rolls out to the nearest wilderness, above ground', async () => {
    await rcon.command(`minecraft:fill ${ROOF.x1} ${ROOF.y} ${ROOF.z1} ${ROOF.x2} ${ROOF.y} ${ROOF.z2} minecraft:stone`);
    const since = bot.lines.length;
    await tossTowardCentre(bot);
    await waitFor(async () => (await status()).overworld[3].state === 'dropped', 'the 3-star lies in the town', 5_000);
    await waitFor(async () => {
      const s = (await status()).overworld[3];
      return s.state === 'placed' && s.x < BX;
    }, 'the 3-star was moved out of the town', 15_000);
    const s = (await status()).overworld[3];
    if (s.y !== ROOF.y + 1 || s.x < ROOF.x1) throw new Error(`on the roof just west of the town (${JSON.stringify(s)})`);
    const sky = plain(await rcon.command(`minecraft:execute positioned ${s.x} ${s.y} ${s.z} if predicate `
      + `{condition:'minecraft:location_check',predicate:{can_see_sky:true}}`));
    if (!/passed/i.test(sky)) throw new Error(`the ball's spot can't see the sky (${sky})`);
    await waitForLine(bot, /3-Star Dragon Ball can't stay inside a town, so it rolled out to/, 5_000, since);
    const items = plain(await rcon.command(
      `minecraft:execute if entity @e[type=minecraft:item,x=${BX},y=${PLATFORM_Y},z=${BZ},dx=15,dy=4,dz=15]`));
    if (/passed/i.test(items)) throw new Error('an item entity is still lying in the town');
  });
} catch (e) {
  error = e;
} finally {
  await rcon.command(`minecraft:fill ${ROOF.x1} ${ROOF.y} ${ROOF.z1} ${ROOF.x2} ${ROOF.y} ${ROOF.z2} minecraft:air`)
    .catch(() => {});
  await rcon.command(`townyadmin town ${TOWN} delete`).catch(() => {});
  await rcon.command('confirm').catch(() => {});
  await rcon.command(`dragonballs give ${HUNTER} 3`).catch(() => {});
  await finish(error);
}
