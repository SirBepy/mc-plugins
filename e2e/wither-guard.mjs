import {
  Rcon, assert, buildPlatform, createBot, quit, randomOrigin, releasePlatform, sleep, teleport, waitFor, waitForLine,
} from './lib.mjs';

// Tweaks' WitherGuardListener (Joe, 2026-10-09): Wither anti-cheese.
// 1. A Wither built in the wilderness spawns.
// 2. One built with bedrock overhead is refused: no Wither, the soul sand and skulls stay, nearby players are told.
// 3. One built inside a Towny town is refused the same way.
// 4. A respawn anchor blast, TNT and an End Crystal don't hurt it; a player's hit does.
// 5. A Wither stuck in bedrock appears above the nearest survival player at once.
// 6. A Wither shut in an obsidian box (no player in sight) appears above the nearest player within ~10 s.
// The last skull goes in from a dispenser: /setblock never runs the skull's summon check, a dispenser does.
const RUN = String(Math.floor(Math.random() * 10_000)).padStart(4, '0');
const FIGHTER = `WgFighter${RUN}`;
const MAYOR = `WgMayor${RUN}`;
const TOWN = `wgtown${RUN}`;
const Y = 200;
const { bx: BX, bz: BZ } = randomOrigin();

const rcon = await Rcon.connect();
let fighter;
let mayor;

const passed = async (command) => /passed|test passed/i.test(await rcon.command(command));
const isBlock = (x, y, z, block) => passed(`execute if block ${x} ${y} ${z} minecraft:${block}`);
const witherNear = (x, y, z, r) => passed(`execute if entity @e[type=minecraft:wither,x=${x},y=${y},z=${z},distance=..${r}]`);

async function health(tag) {
  const reply = await rcon.command(`data get entity @e[type=minecraft:wither,tag=${tag},limit=1] Health`);
  const match = reply.match(/(-?\d+(?:\.\d+)?)f?\s*$/);
  assert(match, `Wither ${tag} health readable (got ${reply.trim()})`);
  return Number(match[1]);
}

async function killWithers() {
  await rcon.command(`minecraft:kill @e[type=minecraft:wither,x=${BX},y=${Y},z=${BZ},distance=..200]`);
  await rcon.command(`minecraft:kill @e[type=minecraft:wither_skull,x=${BX},y=${Y},z=${BZ},distance=..200]`);
}

/** The T of soul sand at (x, y + 1..2, z) along x, two skulls set by hand, and a dispenser loaded with the third. */
async function buildWither(x, z) {
  await rcon.command(`minecraft:fill ${x - 2} ${Y + 1} ${z - 2} ${x + 2} ${Y + 9} ${z + 2} minecraft:air`);
  await rcon.command(`minecraft:setblock ${x} ${Y + 1} ${z} minecraft:soul_sand`);
  await rcon.command(`minecraft:fill ${x - 1} ${Y + 2} ${z} ${x + 1} ${Y + 2} ${z} minecraft:soul_sand`);
  await rcon.command(`minecraft:setblock ${x - 1} ${Y + 3} ${z} minecraft:wither_skeleton_skull`);
  await rcon.command(`minecraft:setblock ${x} ${Y + 3} ${z} minecraft:wither_skeleton_skull`);
  await rcon.command(`minecraft:setblock ${x + 1} ${Y + 3} ${z + 1} minecraft:dispenser[facing=north]`);
  await rcon.command(`minecraft:item replace block ${x + 1} ${Y + 3} ${z + 1} container.0 with minecraft:wither_skeleton_skull`);
}

async function fire(x, z) {
  await rcon.command(`minecraft:setblock ${x + 1} ${Y + 4} ${z + 1} minecraft:redstone_block`);
  await sleep(1500);
}

async function clearBuild(x, z) {
  await rcon.command(`minecraft:fill ${x - 2} ${Y + 1} ${z - 2} ${x + 2} ${Y + 9} ${z + 2} minecraft:air`);
}

try {
  await buildPlatform(rcon, BX, BZ, Y);
  await rcon.command(`minecraft:forceload add ${BX - 64} ${BZ - 64} ${BX + 64} ${BZ + 64}`);
  // A floor under every step, the town's chunk (BX + 32..47) included, so no bot falls off.
  await rcon.command(`minecraft:fill ${BX - 40} ${Y} ${BZ - 8} ${BX + 47} ${Y} ${BZ + 8} minecraft:stone`);
  fighter = await createBot(FIGHTER);
  await rcon.command(`minecraft:gamemode survival ${FIGHTER}`);
  await rcon.command(`minecraft:effect give ${FIGHTER} minecraft:resistance infinite 255 true`);
  await rcon.command(`minecraft:effect give ${FIGHTER} minecraft:regeneration infinite 255 true`);
  await rcon.command(`minecraft:effect give ${FIGHTER} minecraft:fire_resistance infinite 0 true`);
  await rcon.command(`minecraft:clear ${FIGHTER}`);
  await teleport(rcon, fighter, BX, Y + 1, BZ);

  // 1. Wilderness.
  const AX = BX - 10;
  await buildWither(AX, BZ);
  await fire(AX, BZ);
  await waitFor(() => witherNear(AX, Y + 2, BZ, 4), 'a Wither built in the wilderness spawned', 5_000);
  assert(await isBlock(AX, Y + 1, BZ, 'air'), 'its soul sand was used up');
  await killWithers();
  await clearBuild(AX, BZ);
  console.log('PASS a Wither built in the wilderness spawns');

  // 2. Bedrock overhead.
  const BXB = BX + 10;
  await buildWither(BXB, BZ);
  await rcon.command(`minecraft:setblock ${BXB} ${Y + 7} ${BZ} minecraft:bedrock`);
  const since = fighter.lines.length;
  await fire(BXB, BZ);
  assert(!(await witherNear(BXB, Y + 2, BZ, 8)), 'no Wither spawned under bedrock');
  assert(await isBlock(BXB, Y + 1, BZ, 'soul_sand') && await isBlock(BXB + 1, Y + 2, BZ, 'soul_sand'), 'the soul sand stayed');
  assert(await isBlock(BXB - 1, Y + 3, BZ, 'wither_skeleton_skull')
    && await isBlock(BXB + 1, Y + 3, BZ, 'wither_skeleton_skull'), 'the skulls stayed, the dispensed one included');
  await waitForLine(fighter, /bedrock/i, 5_000, since);
  await clearBuild(BXB, BZ);
  console.log('PASS a Wither built under bedrock is refused, the build stays and nearby players are told');

  // 3. Inside a town: the town claims the mayor's chunk.
  const TX = BX + 32 + 8;
  const TZ = BZ;
  mayor = await createBot(MAYOR);
  await rcon.command(`minecraft:gamemode survival ${MAYOR}`);
  await rcon.command(`minecraft:effect give ${MAYOR} minecraft:resistance infinite 255 true`);
  await teleport(rcon, mayor, TX + 3, Y + 1, TZ + 4);
  const created = await rcon.command(`townyadmin town new ${TOWN} ${MAYOR}`);
  await sleep(1500);
  const toggled = await rcon.command(`townyadmin town ${TOWN} toggle pvp off`);
  assert(!/doesn't exist|not found|invalid/i.test(toggled), `town created (${created.trim()} / ${toggled.trim()})`);
  await buildWither(TX, TZ);
  const mayorSince = mayor.lines.length;
  await fire(TX, TZ);
  assert(!(await witherNear(TX, Y + 2, TZ, 8)), 'no Wither spawned in the town');
  assert(await isBlock(TX, Y + 1, TZ, 'soul_sand'), 'the soul sand stayed in the town');
  assert(await isBlock(TX + 1, Y + 3, TZ, 'wither_skeleton_skull'), 'the dispenser placed the last skull in the town');
  await waitForLine(mayor, /town/i, 5_000, mayorSince);
  await clearBuild(TX, TZ);
  await quit(mayor);
  mayor = null;
  console.log('PASS a Wither built inside a town is refused');

  // 4. Blasts. A still Wither (NoAI) out of its summoning phase, 4 blocks from the fighter.
  const DX = BX - 30;
  await teleport(rcon, fighter, DX - 3, Y + 1, BZ);
  await rcon.command(`minecraft:summon minecraft:wither ${DX + 0.5} ${Y + 1} ${BZ + 0.5} {NoAI:1b,Invul:0,Tags:["wgD"]}`);
  await waitFor(() => witherNear(DX, Y + 1, BZ, 2), 'the blast-test Wither is there', 5_000);
  const full = await health('wgD');
  await rcon.command(`minecraft:setblock ${DX - 1} ${Y + 1} ${BZ} minecraft:respawn_anchor[charges=4]`);
  await sleep(300);
  const anchor = fighter.blockAt(fighter.entity.position.offset(2, 0, 0).floored());
  assert(anchor && anchor.name === 'respawn_anchor', `the fighter sees the anchor (got ${anchor?.name})`);
  await fighter.activateBlock(anchor).catch(() => {});
  await waitFor(async () => !(await isBlock(DX - 1, Y + 1, BZ, 'respawn_anchor')), 'the anchor went off', 5_000);
  await sleep(500);
  assert(await health('wgD') === full, `the anchor blast left the Wither at ${full}`);
  await rcon.command(`minecraft:summon minecraft:tnt ${DX + 0.5} ${Y + 1} ${BZ + 1.5} {fuse:0}`);
  await sleep(1000);
  assert(await health('wgD') === full, `TNT left the Wither at ${full}`);
  await rcon.command(`minecraft:summon minecraft:end_crystal ${DX + 0.5} ${Y + 1} ${BZ - 1.5} {ShowBottom:0b,Tags:["wgC"]}`);
  await sleep(300);
  await rcon.command(`minecraft:damage @e[type=minecraft:end_crystal,tag=wgC,limit=1] 1 minecraft:player_attack by ${FIGHTER}`);
  await waitFor(async () => !(await passed('execute if entity @e[type=minecraft:end_crystal,tag=wgC]')), 'the crystal blew up', 5_000);
  await sleep(500);
  assert(await health('wgD') === full, `the End Crystal blast left the Wither at ${full}`);
  await rcon.command(`minecraft:damage @e[type=minecraft:wither,tag=wgD,limit=1] 10 minecraft:player_attack by ${FIGHTER}`);
  await sleep(300);
  const hit = await health('wgD');
  assert(hit < full, `a player's hit still hurts it (${full} -> ${hit})`);
  await killWithers();
  console.log(`PASS anchors, TNT and End Crystals don't hurt the Wither; a player's hit does (${full} -> ${hit})`);

  // 5. Stuck in bedrock: its head inside a bedrock block, 30 blocks up and over from the fighter.
  await teleport(rcon, fighter, BX, Y + 1, BZ);
  const EX = BX + 20;
  const EY = Y + 25;
  await rcon.command(`minecraft:setblock ${EX} ${EY + 2} ${BZ} minecraft:bedrock`);
  await rcon.command(`minecraft:summon minecraft:wither ${EX + 0.5} ${EY} ${BZ + 0.5} {Invul:0,Tags:["wgE"]}`);
  await waitFor(() => witherNear(BX, Y + 4, BZ, 3), 'the bedrock-stuck Wither appeared over the fighter', 5_000);
  await killWithers();
  await rcon.command(`minecraft:setblock ${EX} ${EY + 2} ${BZ} minecraft:air`);
  console.log('PASS a Wither stuck in bedrock appears over the nearest player at once');

  // 6. Out of sight: a sealed obsidian box; it never breaks out because nothing hurts it.
  const FX = BX - 20;
  const FY = Y + 20;
  await rcon.command(`minecraft:fill ${FX - 2} ${FY - 1} ${BZ - 2} ${FX + 2} ${FY + 5} ${BZ + 2} minecraft:obsidian hollow`);
  await rcon.command(`minecraft:summon minecraft:wither ${FX + 0.5} ${FY} ${BZ + 0.5} {Invul:0,Tags:["wgF"]}`);
  const boxed = Date.now();
  await sleep(3000);
  assert(await witherNear(FX, FY + 1, BZ, 3), 'the boxed Wither is still in its box after 3 s');
  await waitFor(() => witherNear(BX, Y + 4, BZ, 3), 'the boxed Wither appeared over the fighter', 15_000);
  const took = Math.round((Date.now() - boxed) / 1000);
  assert(took >= 9, `it waited about 10 s first (${took} s)`);
  console.log(`PASS a Wither with no player in sight appears over the nearest player after ${took} s`);
} finally {
  await killWithers().catch(() => {});
  await rcon.command(`townyadmin town ${TOWN} delete`).catch(() => {});
  await sleep(500);
  await rcon.command('confirm').catch(() => {});
  await quit(fighter, mayor);
  await rcon.command(`minecraft:fill ${BX - 22} ${Y + 19} ${BZ - 2} ${BX - 18} ${Y + 25} ${BZ + 2} minecraft:air`).catch(() => {});
  await rcon.command(`minecraft:forceload remove ${BX - 64} ${BZ - 64} ${BX + 64} ${BZ + 64}`).catch(() => {});
  await releasePlatform(rcon, BX, BZ);
  rcon.close();
  await sleep(200);
}
