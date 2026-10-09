import { Vec3 } from 'vec3';
import {
  Rcon, assert, createBot, quit, randomOrigin, releasePlatform, sleep, teleport, waitFor, waitForLine,
} from './lib.mjs';

// The Merfolk (ItsLogiks' wish, merfolk plugin): only the configured owner turns a villager standing in
// water into a merfolk with a nautilus shell (a stranger's shell does nothing); merfolk wear their
// variant's type and fins (Kelp: swamp + merfolk_head, Coral: jungle + merfolk_head_coral, the server
// pack's merfolk bodies), never lose air, and sink to the sea floor where an ordinary villager floats; an
// ordinary swamp or jungle villager is moved to plains robes; a Kelp Bed goes
// into two water blocks on the floor and stays water, and hitting it gives the item back; two merfolk fed
// fish with a free Kelp Bed make a merfolk baby; at night a merfolk walks home to its bed; an unemployed
// merfolk near an underwater barrel / cartography table becomes a Fisher / Treasurer with the wish's
// trades; buying the trident starts its 12 h kingdom-wide wait; and /merfolk kingdom revert turns a
// Treasurer back into an ordinary cartographer. Everything happens in a glass pool built each run.
const PY = 200;
const { bx: BX, bz: BZ } = randomOrigin();
const OWNER = `Merf${Date.now() % 100000}`;
const STRANGER = 'MerfStranger';
const LIVE_OWNER = '3e0a4456-e2a3-4ea8-9bfa-8e38ee4c863d';
const MERFOLK_NBT = '{BukkitValues:{"merfolk:merfolk":1b}}';

const rcon = await Rcon.connect();
const bots = [];
const results = [];
async function step(name, fn) {
  await fn();
  results.push(name);
  console.log(`PASS ${name}`);
}

const get = (selector, path) => rcon.command(`minecraft:data get entity ${selector} ${path}`);
async function number(selector, path) {
  const reply = await get(selector, path);
  const match = /: (-?[\d.]+)[a-z]?$/i.exec(reply.trim());
  if (!match) throw new Error(`no number at ${selector} ${path}: ${reply}`);
  return Number(match[1]);
}
const one = (tag) => `@e[type=villager,tag=${tag},limit=1]`;
async function summon(tag, x, y, z, extra = '') {
  await rcon.command(`minecraft:summon villager ${x} ${y} ${z} {Tags:["${tag}","mf_e2e"]${extra}}`);
  await waitFor(async () => /has the following/.test(await get(one(tag), 'Pos')), `${tag} spawned`, 5_000);
}
const summonMerfolk = (tag, x, y, z, extra = '') => summon(tag, x, y, z, `,BukkitValues:{"merfolk:merfolk":1b}${extra}`);
async function status() {
  return rcon.command('merfolk');
}
/** A UUID string as the int array NBT selectors match on. */
function uuidArray(uuid) {
  const hex = uuid.replace(/-/g, '');
  const ints = [0, 8, 16, 24].map((i) => (parseInt(hex.slice(i, i + 8), 16) | 0));
  return `[I;${ints.join(',')}]`;
}
function villagerAt(bot, x, y, z) {
  return Object.values(bot.entities).find((e) => e.name === 'villager' && e.position.distanceTo(new Vec3(x, y, z)) < 1.2);
}

try {
  // A 13x13 glass pool, 4 water deep (y 201-204) with walls 2 blocks above the water so nothing walks
  // out, on a stone floor at y 200. For the ritual the bots stand on a stone pillar in the middle (top at
  // 204, feet at 205) next to a step (top at 202) where the villager stands in water; both become water
  // after it, or merfolk would climb onto them.
  await rcon.command(`minecraft:forceload add ${BX - 16} ${BZ - 16} ${BX + 16} ${BZ + 16}`);
  await rcon.command(`minecraft:fill ${BX - 8} ${PY} ${BZ - 8} ${BX + 8} ${PY} ${BZ + 8} minecraft:stone`);
  await rcon.command(`minecraft:fill ${BX - 8} ${PY + 1} ${BZ - 8} ${BX + 8} ${PY + 8} ${BZ + 8} minecraft:air`);
  await rcon.command(`minecraft:fill ${BX - 6} ${PY + 1} ${BZ - 6} ${BX + 6} ${PY + 6} ${BZ + 6} minecraft:glass`);
  await rcon.command(`minecraft:fill ${BX - 5} ${PY + 1} ${BZ - 5} ${BX + 5} ${PY + 4} ${BZ + 5} minecraft:water`);
  await rcon.command(`minecraft:fill ${BX - 5} ${PY + 5} ${BZ - 5} ${BX + 5} ${PY + 6} ${BZ + 5} minecraft:air`);
  await rcon.command(`minecraft:fill ${BX} ${PY + 1} ${BZ} ${BX} ${PY + 4} ${BZ} minecraft:stone`);
  await rcon.command(`minecraft:fill ${BX + 1} ${PY + 1} ${BZ} ${BX + 1} ${PY + 2} ${BZ} minecraft:stone`);
  await rcon.command('minecraft:time set 1000');
  await rcon.command('minecraft:gamerule advance_time false');

  const owner = await createBot(OWNER);
  const stranger = await createBot(STRANGER);
  bots.push(owner, stranger);
  for (const bot of bots) {
    await rcon.command(`minecraft:gamemode survival ${bot.username}`);
    await rcon.command(`minecraft:clear ${bot.username}`);
  }
  const reply = await rcon.command(`merfolk owner ${OWNER}`);
  assert(/owner is now/.test(reply), `owner set (${reply})`);

  await step("a stranger's shell does nothing, the owner's shell makes a merfolk and is spent", async () => {
    await summon('mf_conv', BX + 1.5, PY + 3, BZ + 0.5, ',NoAI:1b');
    await teleport(rcon, stranger, BX, PY + 5, BZ);
    await rcon.command(`minecraft:item replace entity ${STRANGER} weapon.mainhand with minecraft:nautilus_shell 2`);
    await waitFor(() => stranger.heldItem?.name === 'nautilus_shell', 'stranger holds shells', 5_000);
    const target = villagerAt(stranger, BX + 1.5, PY + 3, BZ + 0.5);
    assert(target, 'stranger sees the villager');
    await stranger.activateEntity(target);
    await sleep(1000);
    assert(!/merfolk:merfolk/.test(await get(one('mf_conv'), 'BukkitValues')), 'still an ordinary villager');
    assert(stranger.heldItem?.count === 2, `stranger kept both shells (${stranger.heldItem?.count})`);
    await teleport(rcon, stranger, BX + 7, PY + 1, BZ + 7);

    await teleport(rcon, owner, BX, PY + 5, BZ);
    await rcon.command(`minecraft:item replace entity ${OWNER} weapon.mainhand with minecraft:nautilus_shell 2`);
    await waitFor(() => owner.heldItem?.name === 'nautilus_shell', 'owner holds shells', 5_000);
    await owner.activateEntity(villagerAt(owner, BX + 1.5, PY + 3, BZ + 0.5));
    await waitFor(async () => /merfolk:merfolk/.test(await get(one('mf_conv'), 'BukkitValues')), 'villager became a merfolk', 5_000);
    await waitFor(() => owner.heldItem?.count === 1, 'one shell spent', 5_000);
    const type = await get(one('mf_conv'), 'VillagerData.type');
    const head = await get(one('mf_conv'), 'equipment.head');
    assert(/minecraft:(swamp|jungle)/.test(type), `merfolk wear a merfolk type (${type})`);
    const fins = /minecraft:jungle/.test(type) ? /claudesmp:merfolk_head_coral/ : /claudesmp:merfolk_head(?!_)/;
    assert(fins.test(head), `merfolk wear their variant's fins (${type}, ${head})`);
    await teleport(rcon, owner, BX + 7, PY + 1, BZ + 7);
    await rcon.command(`minecraft:fill ${BX} ${PY + 1} ${BZ} ${BX + 1} ${PY + 4} ${BZ} minecraft:water`);
  });

  await step('a merfolk sinks to the floor and keeps its air; an ordinary villager floats and a submerged one loses air', async () => {
    await summonMerfolk('mf_sink', BX - 3.5, PY + 4, BZ - 3.5);
    await summon('mf_float', BX + 3.5, PY + 4, BZ + 3.5);
    await summon('mf_held', BX + 3.5, PY + 1, BZ - 3.5, ',NoAI:1b');
    await waitFor(async () => (await number(one('mf_sink'), 'Pos[1]')) < PY + 1.6, 'merfolk on the sea floor', 10_000);
    await sleep(8000);
    const sinkY = await number(one('mf_sink'), 'Pos[1]');
    const floatY = await number(one('mf_float'), 'Pos[1]');
    assert(sinkY < PY + 1.6, `merfolk still on the floor after 8 s (y ${sinkY})`);
    assert(floatY > PY + 2.5, `ordinary villager floats (y ${floatY})`);
    assert((await number(one('mf_sink'), 'Air')) === 300, 'merfolk air stays full');
    assert((await number(one('mf_held'), 'Air')) < 300, 'a submerged ordinary villager loses air');
  });

  await step('ordinary swamp and jungle villagers are moved to plains robes', async () => {
    await summon('mf_swampy', BX + 7.5, PY + 1, BZ + 7.5, ',VillagerData:{type:"minecraft:swamp",profession:"minecraft:none",level:1}');
    await summon('mf_jungly', BX + 7.5, PY + 1, BZ + 6.5, ',VillagerData:{type:"minecraft:jungle",profession:"minecraft:none",level:1}');
    assert(/minecraft:plains/.test(await get(one('mf_swampy'), 'VillagerData.type')), 'swamp to plains robes');
    assert(/minecraft:plains/.test(await get(one('mf_jungly'), 'VillagerData.type')), 'jungle to plains robes');
  });

  await step('a Kelp Bed lies in two water blocks on the floor, stays water, and hitting it gives it back', async () => {
    const give = await rcon.command(`merfolk give ${OWNER} 1`);
    assert(/Gave 1/.test(give), give);
    await waitFor(() => owner.inventory.items().some((i) => i.name === 'paper'), 'owner has the bed', 5_000);
    await owner.equip(owner.inventory.items().find((i) => i.name === 'paper'), 'hand');
    // North of the spot, so looking at it faces south: foot (BX-3, 201, BZ+2), head (BX-3, 201, BZ+3).
    await teleport(rcon, owner, BX - 4, PY + 2, BZ);
    await owner.activateBlock(owner.blockAt(new Vec3(BX - 3, PY, BZ + 2)), new Vec3(0, 1, 0));
    await waitFor(async () => (await rcon.command('merfolk beds')).includes(`${BX - 3},${PY + 1},${BZ + 2}`),
      'bed registered at the clicked spot', 5_000);
    const line = (await rcon.command('merfolk beds')).split('\n').find((l) => l.includes(`${BX - 3},${PY + 1},${BZ + 2}`));
    assert(/SOUTH/.test(line), `bed faces south (${line})`);
    for (const z of [BZ + 2, BZ + 3]) {
      const block = await rcon.command(`minecraft:execute if block ${BX - 3} ${PY + 1} ${z} minecraft:water`);
      assert(/passed/i.test(block), `bed block ${z} is still water (${block})`);
    }
    const parts = await rcon.command(`minecraft:execute positioned ${BX - 3} ${PY + 1} ${BZ + 3} if entity @e[type=minecraft:interaction,distance=..2]`);
    assert(/Count: 2|passed/i.test(parts), `two hitboxes (${parts})`);
    const hitbox = Object.values(owner.entities).find((e) => e.name === 'interaction' && e.position.distanceTo(new Vec3(BX - 2.5, PY + 1, BZ + 2.5)) < 0.8);
    assert(hitbox, 'owner sees the foot hitbox');
    await owner.attack(hitbox);
    await waitFor(async () => !(await rcon.command('merfolk beds')).includes(`${BX - 3},${PY + 1},${BZ + 2}`), 'bed picked up', 5_000);
    const drop = await rcon.command(`minecraft:execute positioned ${BX - 3} ${PY + 2} ${BZ + 3} if entity @e[type=minecraft:item,distance=..4]`);
    assert(/passed/i.test(drop), `the Kelp Bed item dropped (${drop})`);
  });

  async function placeBed(x, z) {
    await rcon.command(`minecraft:kill @e[type=minecraft:item,x=${BX},y=${PY},z=${BZ},distance=..12]`);
    await rcon.command(`merfolk give ${OWNER} 1`);
    await waitFor(() => owner.inventory.items().some((i) => i.name === 'paper'), 'owner has a bed', 5_000);
    await owner.equip(owner.inventory.items().find((i) => i.name === 'paper'), 'hand');
    await teleport(rcon, owner, x, PY + 2, z - 2);
    await owner.activateBlock(owner.blockAt(new Vec3(x, PY, z)), new Vec3(0, 1, 0));
    await waitFor(async () => (await rcon.command('merfolk beds')).includes(`${x},${PY + 1},${z}`), `bed at ${x},${z}`, 5_000);
  }

  await step('two merfolk fed fish with a free Kelp Bed make a merfolk baby', async () => {
    // The earlier steps' merfolk would claim beds too; only the pair and their baby should.
    await rcon.command('minecraft:kill @e[type=villager,tag=mf_conv]');
    await rcon.command('minecraft:kill @e[type=villager,tag=mf_sink]');
    for (const x of [BX - 4, BX - 2, BX + 2, BX + 4]) await placeBed(x, BZ - 4);
    await teleport(rcon, owner, BX + 7, PY + 1, BZ + 7);
    const loaded = async () => Number(/Merfolk loaded: (\d+)/.exec(await status())[1]);
    // 3 blocks apart, so each eats only its own fish (a merfolk takes food until it's full) and they
    // have to swim together to breed. Everyone homeless claims a bed first, leaving 2 free.
    await summonMerfolk('mf_ma', BX - 1.5, PY + 1, BZ + 3.5);
    await summonMerfolk('mf_pa', BX - 4.5, PY + 1, BZ + 3.5);
    await sleep(4000);
    const before = await loaded();
    for (const tag of ['mf_ma', 'mf_pa']) {
      const x = await number(one(tag), 'Pos[0]');
      const z = await number(one(tag), 'Pos[2]');
      await rcon.command(`minecraft:summon item ${x} ${PY + 1.2} ${z} {Item:{id:"minecraft:cod",count:4},PickupDelay:32767}`);
      // The second one's meal can be spent on the baby in the very tick it eats, so a breeding
      // cooldown counts as having eaten too.
      await waitFor(async () => (await number(one(tag), 'BukkitValues."merfolk:food"').catch(() => 0)) >= 12
        || (await number(one(tag), 'Age')) > 0, `${tag} ate the cod`, 10_000);
    }
    await waitFor(async () => (await loaded()) === before + 1, 'a baby merfolk', 45_000);
    assert((await number(one('mf_ma'), 'Age')) > 0, 'the mother is on the breeding cooldown');
    assert((await number(one('mf_pa'), 'Age')) > 0, 'the father is on the breeding cooldown');
  });

  await step('at night a merfolk goes back to its Kelp Bed', async () => {
    const home = (await rcon.command('merfolk beds')).split('\n').find((l) => / home of /.test(l));
    const [, bx, , bz] = /(-?\d+),(-?\d+),(-?\d+)/.exec(home).map(Number);
    const uuid = /home of (\S+)/.exec(home)[1];
    const away = { x: BX + 3.5, z: BZ + 3.5 };
    await rcon.command('minecraft:time set 13000');
    const distance = async () => {
      const x = await number(`@e[type=villager,limit=1,nbt={UUID:${uuidArray(uuid)}}]`, 'Pos[0]');
      const z = await number(`@e[type=villager,limit=1,nbt={UUID:${uuidArray(uuid)}}]`, 'Pos[2]');
      return Math.hypot(x - (bx + 0.5), z - (bz + 1));
    };
    await rcon.command(`minecraft:tp @e[type=villager,limit=1,nbt={UUID:${uuidArray(uuid)}}] ${away.x} ${PY + 1} ${away.z}`);
    const start = await distance();
    await waitFor(async () => (await distance()) < 2.5, `the resident swam home (started ${start.toFixed(1)} away)`, 40_000);
    await rcon.command('minecraft:time set 1000');
  });

  await step('an unemployed merfolk at an underwater barrel / cartography table becomes a Fisher / Treasurer', async () => {
    await rcon.command(`minecraft:setblock ${BX + 4} ${PY + 1} ${BZ + 4} minecraft:barrel`);
    await rcon.command(`minecraft:setblock ${BX + 4} ${PY + 1} ${BZ + 2} minecraft:cartography_table`);
    // A few blocks off, so they have to walk the floor to claim them (SeaSteering).
    await summonMerfolk('mf_job1', BX + 0.5, PY + 1, BZ + 4.5);
    await summonMerfolk('mf_job2', BX + 0.5, PY + 1, BZ + 1.5);
    const fisher = '@e[type=villager,limit=1,nbt={BukkitValues:{"merfolk:specialist":"fisher"}}]';
    const treasurer = '@e[type=villager,limit=1,nbt={BukkitValues:{"merfolk:specialist":"treasurer"}}]';
    await waitFor(async () => /has the following/.test(await get(fisher, 'Offers.Recipes[0].sell.id'))
      && /has the following/.test(await get(treasurer, 'Offers.Recipes[0].sell.id')), 'a Fisher and a Treasurer with trades', 90_000);
    assert(/minecraft:cod/.test(await get(fisher, 'Offers.Recipes[0].sell.id')), 'Fisher sells cod first');
    assert(/minecraft:trident/.test(await get(treasurer, 'Offers.Recipes[0].sell.id')), 'Treasurer sells a trident');
    assert((await number(treasurer, 'Offers.Recipes[0].buy.count')) === 4, 'for 4 diamonds');
    assert(/minecraft:diamond/.test(await get(treasurer, 'Offers.Recipes[0].buy.id')), 'diamonds');
    assert(/minecraft:heart_of_the_sea/.test(await get(treasurer, 'Offers.Recipes[2].sell.id')), 'and a heart of the sea');
    assert(/Treasurer/.test(await get(treasurer, 'CustomName')), 'named Treasurer');
  });

  await step('buying the trident starts the kingdom-wide 12 h trident wait', async () => {
    const treasurer = '@e[type=villager,limit=1,nbt={BukkitValues:{"merfolk:specialist":"treasurer"}}]';
    const x = await number(treasurer, 'Pos[0]');
    const z = await number(treasurer, 'Pos[2]');
    await rcon.command(`minecraft:clear ${OWNER}`);
    await rcon.command(`minecraft:give ${OWNER} minecraft:diamond 8`);
    await teleport(rcon, owner, Math.floor(x) - 1, PY + 1, Math.floor(z));
    const entity = Object.values(owner.entities).find((e) => e.name === 'villager' && e.position.distanceTo(new Vec3(x, PY + 1, z)) < 1.5);
    assert(entity, 'owner sees the Treasurer');
    const window = await owner.openVillager(entity);
    await waitFor(() => window.trades?.length >= 3, 'Treasurer trades listed', 5_000);
    assert(window.trades[0].outputItem.name === 'trident', `first trade is the trident (${window.trades[0].outputItem.name})`);
    await owner.trade(window, 0, 1);
    window.close();
    await waitFor(() => owner.inventory.items().some((i) => i.name === 'trident'), 'owner got the trident', 5_000);
    assert(/trident=7[12]\dm/.test(await status()), `trident waits ~12 h (${await status()})`);
  });

  await step('/merfolk kingdom revert turns the Treasurer into an ordinary novice cartographer', async () => {
    const treasurer = '@e[type=villager,limit=1,nbt={BukkitValues:{"merfolk:specialist":"treasurer"}}]';
    const uuid = (await get(treasurer, 'UUID')).replace(/^.*: /, '').trim();
    const reverted = await rcon.command('merfolk kingdom revert');
    assert(/reverted/i.test(reverted), reverted);
    const target = `@e[type=villager,limit=1,nbt={UUID:${uuid}}]`;
    await waitFor(async () => !/merfolk:specialist/.test(await get(target, 'BukkitValues')), 'specialist tag gone', 5_000);
    const offers = await get(target, 'Offers.Recipes');
    assert(!/trident|heart_of_the_sea|sponge/.test(offers), `no rares left (${offers.slice(0, 200)})`);
    assert(/minecraft:cartographer/.test(await get(target, 'VillagerData.profession')), 'still a cartographer');
    assert(/seagrass/.test(await get(target, 'Offers.Recipes[-1].sell.id')), 'with the cartographer sea trade');
    const reopened = await rcon.command('merfolk kingdom pending');
    assert(/pending/i.test(reopened), reopened);
  });

  console.log(`PASS merfolk: ${results.length} steps`);
} finally {
  await rcon.command(`merfolk owner ${LIVE_OWNER}`).catch(() => {});
  await rcon.command('merfolk kingdom pending').catch(() => {});
  await rcon.command('merfolk treasury reset').catch(() => {});
  await rcon.command('minecraft:gamerule advance_time true').catch(() => {});
  await rcon.command('minecraft:kill @e[type=villager,tag=mf_e2e]').catch(() => {});
  await rcon.command(`minecraft:kill @e[type=villager,x=${BX},y=${PY},z=${BZ},distance=..12]`).catch(() => {});
  await quit(...bots);
  await releasePlatform(rcon, BX, BZ).catch(() => {});
  rcon.close();
}
