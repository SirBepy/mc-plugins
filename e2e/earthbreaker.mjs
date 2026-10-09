import {
  Rcon, assert, buildPlatform, createBot, quit, randomOrigin, releasePlatform, sleep, teleport, waitFor,
} from './lib.mjs';

// Tweaks' EarthbreakerListener (dvolic21's Shenron wish, 2026-10-09): Shenron's Earthbreaker, any item tagged
// claudesmp:earthbreaker, mines a 5x5 face of natural stone and ores.
// 1. Area mode digs at a quarter of the speed (block_break_speed 0.25), sneaking lifts it.
// 2. Digging a stone wall face-on breaks the upright 5x5: ores drop their item (Fortune applies), ancient
//    debris and dirt stay, the block one past the 5x5 stays, and the pickaxe wears 1 per block broken.
// 3. Sneaking mines one block.
// 4. Towny: the part of the 5x5 inside someone else's town is skipped, the wilderness part breaks.
const RUN = String(Math.floor(Math.random() * 10_000)).padStart(4, '0');
const MINER = `EbMiner${RUN}`;
const MAYOR = `EbMayor${RUN}`;
const TOWN = `ebtown${RUN}`;
const Y = 200;
// Chunk-aligned, so x = BX is the first column of the town's chunk in step 4.
const { bx: BX, bz: BZ } = randomOrigin();
const TAG = 'custom_data={PublicBukkitValues:{"claudesmp:earthbreaker":1b}}';
// No Unbreaking here, so the wear is exactly one point per block.
const PICK = `minecraft:netherite_pickaxe[${TAG},enchantments={"minecraft:efficiency":5,"minecraft:fortune":3}]`;

const rcon = await Rcon.connect();
let miner;
let mayor;

const passed = async (command) => /passed|test passed/i.test(await rcon.command(command));
const isBlock = (x, y, z, block) => passed(`execute if block ${x} ${y} ${z} minecraft:${block}`);

/**
 * A 7-wide, 7-tall stone wall in the plane z = wz, centred on (cx, Y + 4), on a glass floor that runs past
 * both sides of it, so drops that pop out behind the wall don't fall to the ground far below.
 */
async function wall(cx, wz) {
  await rcon.command(`minecraft:fill ${cx - 4} ${Y} ${wz - 4} ${cx + 4} ${Y} ${wz + 4} minecraft:glass`);
  await rcon.command(`minecraft:fill ${cx - 4} ${Y + 1} ${wz - 4} ${cx + 4} ${Y + 8} ${wz - 1} minecraft:air`);
  await rcon.command(`minecraft:fill ${cx - 3} ${Y + 1} ${wz} ${cx + 3} ${Y + 7} ${wz} minecraft:stone`);
}

async function brokenIn5x5(cx, wz) {
  let broken = 0;
  for (let x = cx - 2; x <= cx + 2; x++) {
    for (let y = Y + 2; y <= Y + 6; y++) if (await isBlock(x, y, wz, 'air')) broken++;
  }
  return broken;
}

async function digCentre(cx, wz) {
  await teleport(rcon, miner, cx, Y + 1, wz - 2);
  const centre = miner.blockAt(miner.entity.position.offset(0, 3, 2).floored());
  assert(centre && centre.name === 'stone', `the wall centre is stone for the bot (got ${centre?.name})`);
  // Mineflayer's dig times itself without the block_break_speed attribute, so it stops too early for area
  // mode and the server keeps the block. A real client reads the attribute and holds on until the slowed
  // dig is done; this holds for 1.5 s, past the 6 ticks stone takes at x0.25. Face 2 is north, towards the bot.
  await miner.lookAt(centre.position.offset(0.5, 0.5, 0), true);
  miner._client.write('block_dig', { status: 0, location: centre.position, face: 2 });
  for (let i = 0; i < 5; i++) {
    miner.swingArm();
    await sleep(300);
  }
  miner._client.write('block_dig', { status: 2, location: centre.position, face: 2 });
  await waitFor(() => isBlock(cx, Y + 4, wz, 'air'), 'the centre block broke', 10_000);
  await sleep(1500);
}

async function damage() {
  const reply = await rcon.command(`data get entity ${MINER} SelectedItem.components."minecraft:damage"`);
  const match = reply.match(/(\d+)\s*$/);
  return match ? Number(match[1]) : 0;
}

async function breakSpeed() {
  const reply = await rcon.command(`attribute ${MINER} minecraft:block_break_speed get`);
  const match = reply.match(/(-?\d+(?:\.\d+)?)\s*$/);
  assert(match, `block_break_speed readable (got ${reply.trim()})`);
  return Number(match[1]);
}

try {
  await buildPlatform(rcon, BX, BZ, Y);
  await rcon.command(`minecraft:forceload add ${BX - 64} ${BZ - 16} ${BX + 32} ${BZ + 32}`);
  miner = await createBot(MINER);
  await rcon.command(`minecraft:gamemode survival ${MINER}`);
  await rcon.command(`minecraft:effect give ${MINER} minecraft:resistance infinite 255 true`);
  await rcon.command(`minecraft:clear ${MINER}`);
  await rcon.command(`minecraft:item replace entity ${MINER} hotbar.0 with ${PICK}`);
  await waitFor(() => miner.inventory.slots[36]?.name === 'netherite_pickaxe', `${MINER} holds the Earthbreaker`);
  miner.setQuickBarSlot(0);

  // 1. Dig speed.
  await waitFor(async () => (await breakSpeed()) === 0.25, 'area mode digs at a quarter of the speed', 5_000);
  miner.setControlState('sneak', true);
  await waitFor(async () => (await breakSpeed()) === 1, 'sneaking digs at full speed', 5_000);
  miner.setControlState('sneak', false);
  await waitFor(async () => (await breakSpeed()) === 0.25, 'standing up slows it again', 5_000);
  console.log('PASS area mode digs at x0.25, sneaking at x1');

  // 2. The upright 5x5.
  const AX = BX - 40;
  const AZ = BZ + 3;
  await wall(AX, AZ);
  await rcon.command(`minecraft:setblock ${AX - 1} ${Y + 5} ${AZ} minecraft:diamond_ore`);
  await rcon.command(`minecraft:setblock ${AX + 1} ${Y + 3} ${AZ} minecraft:ancient_debris`);
  await rcon.command(`minecraft:setblock ${AX + 2} ${Y + 6} ${AZ} minecraft:dirt`);
  await digCentre(AX, AZ);
  assert(await isBlock(AX + 1, Y + 3, AZ, 'ancient_debris'), 'ancient debris is never area-mined');
  assert(await isBlock(AX + 2, Y + 6, AZ, 'dirt'), 'dirt is never area-mined');
  assert(await isBlock(AX - 1, Y + 5, AZ, 'air'), 'the diamond ore was area-mined');
  assert(await isBlock(AX + 3, Y + 4, AZ, 'stone') && await isBlock(AX, Y + 7, AZ, 'stone')
    && await isBlock(AX, Y + 1, AZ, 'stone'), 'nothing past the 5x5 breaks');
  const broken = await brokenIn5x5(AX, AZ);
  assert(broken === 23, `23 of the 25 blocks broke (debris and dirt kept), got ${broken}`);
  // The drops land around the wall's foot; a few roll into the bot's pickup reach, so look in both places.
  const dropped = async (id) => await passed(`execute if entity @e[type=minecraft:item,x=${AX},y=${Y + 4},z=${AZ},`
    + `distance=..8,nbt={Item:{id:"minecraft:${id}"}}]`)
    || passed(`execute if items entity ${MINER} container.* minecraft:${id}`);
  await waitFor(() => dropped('diamond'), 'the diamond ore dropped diamonds, not the ore', 10_000);
  assert(!(await dropped('diamond_ore')), 'no ore block dropped');
  assert(await dropped('cobblestone'), 'the stone dropped cobblestone');
  const worn = await damage();
  assert(worn === 23, `the pickaxe wore 1 per block broken (23), got ${worn}`);
  console.log('PASS digging a stone wall breaks the upright 5x5: ores drop items, debris and dirt stay, 1 wear per block');

  // 3. Sneaking.
  const SX = BX - 20;
  const SZ = BZ + 3;
  await wall(SX, SZ);
  miner.setControlState('sneak', true);
  await digCentre(SX, SZ);
  miner.setControlState('sneak', false);
  const sneakBroken = await brokenIn5x5(SX, SZ);
  assert(sneakBroken === 1, `sneaking broke one block, got ${sneakBroken}`);
  console.log('PASS sneaking mines one block');

  // 4. Towny: the town owns the chunk from x = BX; the 5x5 centred on BX - 1 has 2 columns inside it.
  const TX = BX - 1;
  const TZ = BZ + 3;
  await wall(TX, TZ);
  mayor = await createBot(MAYOR);
  await rcon.command(`minecraft:gamemode survival ${MAYOR}`);
  await teleport(rcon, mayor, BX + 8, Y + 1, BZ + 8);
  const created = await rcon.command(`townyadmin town new ${TOWN} ${MAYOR}`);
  await sleep(1500);
  const claimed = await rcon.command(`townyadmin town ${TOWN} toggle pvp off`);
  assert(!/doesn't exist|not found|invalid/i.test(claimed), `town created (${created.trim()} / ${claimed.trim()})`);
  let messages = 0;
  const count = () => { messages++; };
  miner.on('messagestr', count);
  await digCentre(TX, TZ);
  miner.removeListener('messagestr', count);
  for (let x = BX; x <= BX + 1; x++) {
    for (let y = Y + 2; y <= Y + 6; y++) assert(await isBlock(x, y, TZ, 'stone'), `town block ${x} ${y} kept`);
  }
  const wildBroken = await brokenIn5x5(TX, TZ);
  assert(wildBroken === 15, `the 15 wilderness blocks broke, got ${wildBroken}`);
  console.log(`PASS the part of the 5x5 inside someone else's town is skipped (${messages} chat lines to the miner)`);
} finally {
  await rcon.command(`townyadmin town ${TOWN} delete`).catch(() => {});
  await sleep(500);
  await rcon.command('confirm').catch(() => {});
  await quit(miner, mayor);
  await rcon.command(`minecraft:forceload remove ${BX - 64} ${BZ - 16} ${BX + 32} ${BZ + 32}`).catch(() => {});
  await releasePlatform(rcon, BX, BZ);
  rcon.close();
  await sleep(200);
}
