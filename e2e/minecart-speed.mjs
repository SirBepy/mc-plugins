import { Rcon, assert, randomOrigin, sleep, waitFor } from './lib.mjs';

// Tweaks doubles minecart max speed (0.4 -> 0.8 blocks/tick). An empty cart on a long straight
// powered-rail line accelerates to its cap, so its measured speed shows whether the cap moved:
// vanilla tops out at 8 blocks/s, the tweak at 16. RCON only, no bot: forceloaded chunks tick
// entities without a player nearby.
const LENGTH = 320;
const Y = 250;
const TAG = 'e2e_speedtest';

const rcon = await Rcon.connect();
const { bx, bz } = randomOrigin();
const x0 = bx;
const x1 = bx + LENGTH;

async function cartX() {
  const reply = await rcon.command(`minecraft:data get entity @e[type=minecraft:minecart,tag=${TAG},limit=1] Pos[0]`);
  const match = reply.match(/(-?\d+(?:\.\d+)?)d/);
  return match ? Number(match[1]) : undefined;
}

try {
  await rcon.command(`minecraft:forceload add ${x0 - 16} ${bz - 16} ${x1 + 16} ${bz + 16}`);
  // /fill caps at 32768 blocks per call, far above one 320-long row.
  await rcon.command(`minecraft:fill ${x0} ${Y} ${bz} ${x1} ${Y} ${bz} minecraft:redstone_block`);
  await rcon.command(`minecraft:fill ${x0} ${Y + 1} ${bz} ${x1} ${Y + 1} ${bz} minecraft:powered_rail[shape=east_west,powered=true]`);
  await rcon.command(`minecraft:kill @e[type=minecraft:minecart,tag=${TAG}]`);
  await rcon.command(`minecraft:summon minecraft:minecart ${x0 + 1.5} ${Y + 1} ${bz + 0.5} {Tags:["${TAG}"],Motion:[0.4d,0d,0d]}`);
  await waitFor(async () => (await cartX()) !== undefined, 'test minecart spawned');

  // Powered rails take an empty cart from 0.4 to its cap within a couple of seconds.
  await sleep(3000);
  const startX = await cartX();
  const startAt = Date.now();
  await sleep(2000);
  const endX = await cartX();
  const seconds = (Date.now() - startAt) / 1000;
  assert(startX !== undefined && endX !== undefined, 'minecart still exists while measuring');
  const speed = (endX - startX) / seconds;
  console.log(`measured ${speed.toFixed(1)} blocks/s over ${seconds.toFixed(1)}s (vanilla cap 8, tweak cap 16)`);

  // RCON round trips blur the window a little, so the bounds leave room on both sides.
  assert(speed > 12, `cart faster than vanilla's 8 blocks/s (got ${speed.toFixed(1)})`);
  assert(speed < 19, `cart not past the 2x cap (got ${speed.toFixed(1)})`);
  console.log('PASS minecart-speed: carts run at about 2x vanilla');
} finally {
  await rcon.command(`minecraft:kill @e[type=minecraft:minecart,tag=${TAG}]`).catch(() => {});
  await rcon.command(`minecraft:fill ${x0} ${Y} ${bz} ${x1} ${Y + 1} ${bz} minecraft:air`).catch(() => {});
  await rcon.command(`minecraft:forceload remove ${x0 - 16} ${bz - 16} ${x1 + 16} ${bz + 16}`).catch(() => {});
  rcon.close();
  await sleep(200);
}
