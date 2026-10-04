import { Vec3 } from 'vec3';
import {
  Rcon, assert, buildPlatform, createBot, quit, randomOrigin, releasePlatform, sleep, teleport, waitFor,
} from './lib.mjs';

// A copper golem carries items from an (unlocked) copper chest into a chest a player placed, which
// Bolt auto-locks. Without SafeChests' CopperGolemAccessListener, Bolt refuses the golem that target.
const PLATFORM_Y = 200;
const { bx: BX, bz: BZ } = randomOrigin();
const rcon = await Rcon.connect();
let owner;

async function chestItems(x, y, z) {
  return rcon.command(`minecraft:data get block ${x} ${y} ${z} Items`);
}

try {
  await buildPlatform(rcon, BX, BZ, PLATFORM_Y);
  owner = await createBot('GolemOwner');
  await rcon.command(`minecraft:gamemode survival ${owner.username}`);
  await rcon.command(`minecraft:clear ${owner.username}`);
  await teleport(rcon, owner, BX, PLATFORM_Y + 1, BZ);

  await rcon.command(`minecraft:give ${owner.username} minecraft:chest 1`);
  await waitFor(() => owner.inventory.items().some((i) => i.name === 'chest'), 'chest in inventory');
  await owner.equip(owner.inventory.items().find((i) => i.name === 'chest'), 'hand');
  const floor = owner.blockAt(new Vec3(BX + 3, PLATFORM_Y, BZ));
  await owner.lookAt(floor.position.offset(0.5, 1, 0.5), true);
  await owner.placeBlock(floor, new Vec3(0, 1, 0));
  const target = { x: BX + 3, y: PLATFORM_Y + 1, z: BZ };
  await sleep(500);
  const lock = await rcon.command(`minecraft:data get block ${target.x} ${target.y} ${target.z}`);
  assert(/chest/.test(lock), `owner's chest placed (got ${lock.trim()})`);

  // setblock, not a player placement, so Bolt leaves the source copper chest unlocked.
  await rcon.command(`minecraft:setblock ${BX - 3} ${PLATFORM_Y + 1} ${BZ} minecraft:copper_chest{Items:[{Slot:0b,id:"minecraft:cobblestone",count:16}]}`);
  await rcon.command(`minecraft:summon minecraft:copper_golem ${BX} ${PLATFORM_Y + 1} ${BZ} {Tags:["e2e_golem"]}`);
  await quit(owner);
  owner = undefined;

  await waitFor(async () => /cobblestone/.test(await chestItems(target.x, target.y, target.z)),
    'golem deposited cobblestone into the locked chest', 90_000);
  console.log('PASS copper-golem: golem sorts into a Bolt-locked chest');
} finally {
  await rcon.command('minecraft:kill @e[type=minecraft:copper_golem,tag=e2e_golem]').catch(() => {});
  await quit(owner);
  await releasePlatform(rcon, BX, BZ);
  rcon.close();
  await sleep(200);
}
