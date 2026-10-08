import {
  Rcon, assert, createBot, nbtText, quit, randomOrigin, releasePlatform, sleep, teleport, waitFor, waitForLine,
} from './lib.mjs';

// The cartel flip phone: the owner's right-click opens the phone menu, Guard Mode brings two cousins in
// full iron with crossbow-pistols who shoot a nearby monster dead and leave a bystander alone, the phone
// menu calls them off, and Assassinate Mode (target list + confirm) broadcasts the warning, spawns them
// ~10 blocks from the target and kills them, the owner gets the target's head, and a second hit is refused
// for 24 hours. A stranger's phone is dead. A target on a 4-block pillar is shot from range with bolts and
// firework rockets that deal 6-8 a hit on hard (live's difficulty), bare and in full Protection IV netherite
// alike, with no knockback; a bystander on the next pillar inside the blast radius is never hurt, and the
// bolts can't be picked up.
// Runs inside a Towny town with mobs off, since Towny would otherwise delete the cousins within 5 s.
const PLATFORM_Y = 200;
const { bx: BX, bz: BZ } = randomOrigin();
const OWNER = 'CartelGus';
const TARGET = 'CartelMark';
const BYSTANDER = 'CartelLydia';
const TOWN = `cartel${Date.now() % 100000}`;
const COUSINS = '@e[tag=cartel.cousin]';

const rcon = await Rcon.connect();
const bots = [];
const results = [];
async function step(name, fn) {
  await fn();
  results.push(name);
  console.log(`PASS ${name}`);
}

const titleOf = (window) => (typeof window?.title === 'string' ? window.title
  : `${nbtText(window?.title)} ${JSON.stringify(window?.title)}`);
const containerSize = (window) => window.slots.length - 36;
const cousinCount = async () => {
  const out = await rcon.command(`minecraft:execute if entity ${COUSINS}`);
  const match = out.match(/count: (\d+)/i);
  return match ? Number(match[1]) : 0;
};

function nextWindow(bot, timeoutMs = 5_000) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(`${bot.username}: no window opened`)), timeoutMs);
    bot.once('windowOpen', (window) => {
      clearTimeout(timer);
      resolve(window);
    });
  });
}

async function openPhone(bot) {
  const phone = bot.inventory.items().find((i) => i.name === 'clock');
  assert(phone, `${bot.username} holds the phone`);
  await bot.equip(phone, 'hand');
  const opened = nextWindow(bot);
  bot.activateItem();
  const window = await opened;
  bot.deactivateItem();
  return window;
}

/** Opens the phone, picks {@code victim} from the target list and returns the confirm screen. */
async function confirmScreen(bot, victim) {
  let menu = await openPhone(bot);
  let opened = nextWindow(bot);
  await bot.clickWindow(15, 0, 0);
  menu = await opened;
  assert(/crossed the cartel/.test(titleOf(menu)), `target list (got ${titleOf(menu)})`);
  const slot = menu.slots.findIndex((s, i) => i < containerSize(menu) && s?.name === 'player_head'
    && JSON.stringify(s.customName ?? s.nbt ?? s).includes(victim));
  assert(slot >= 0, `${victim} is listed`);
  opened = nextWindow(bot);
  await bot.clickWindow(slot, 0, 0);
  menu = await opened;
  assert(new RegExp(`Order the hit on ${victim}`).test(titleOf(menu)), `confirm (got ${titleOf(menu)})`);
  return menu;
}

async function position(selector) {
  const out = await rcon.command(`minecraft:data get entity ${selector} Pos`);
  const nums = [...out.matchAll(/(-?\d+(?:\.\d+)?)d/g)].map((m) => Number(m[1]));
  return { x: nums[0], y: nums[1], z: nums[2] };
}

try {
  await rcon.command(`minecraft:forceload add ${BX - 32} ${BZ - 32} ${BX + 32} ${BZ + 32}`);
  await rcon.command(`minecraft:fill ${BX - 24} ${PLATFORM_Y} ${BZ - 24} ${BX + 24} ${PLATFORM_Y} ${BZ + 24} minecraft:stone`);
  await rcon.command(`minecraft:fill ${BX - 24} ${PLATFORM_Y + 1} ${BZ - 24} ${BX + 24} ${PLATFORM_Y + 5} ${BZ + 24} minecraft:air`);
  const owner = await createBot(OWNER);
  const target = await createBot(TARGET);
  bots.push(owner, target);
  for (const bot of bots) {
    await rcon.command(`minecraft:gamemode survival ${bot.username}`);
    await rcon.command(`minecraft:clear ${bot.username}`);
    await rcon.command(`minecraft:effect clear ${bot.username}`);
  }
  await rcon.command('cartel dismiss');
  await rcon.command(`cartel owner ${OWNER}`);
  await rcon.command('cartel reset');
  // Mid-chunk, so the town's home block (this chunk) holds the owner and the guards around them.
  await teleport(rcon, owner, BX + 8, PLATFORM_Y + 1, BZ + 8);
  await teleport(rcon, target, BX + 12, PLATFORM_Y + 1, BZ + 8);
  await rcon.command(`townyadmin town new ${TOWN} ${OWNER}`);
  await sleep(1000);
  const townMobs = await rcon.command(`townyadmin town ${TOWN} toggle mobs off`);
  console.log(`town mobs: ${townMobs.replace(/§./g, '').trim()}`);

  await step('the owner gets a phone and right-clicking it opens the phone menu', async () => {
    await rcon.command(`cartel give ${OWNER}`);
    await waitFor(() => owner.inventory.items().some((i) => i.name === 'clock'), 'owner has the phone', 5_000);
    const menu = await openPhone(owner);
    assert(/Cartel Phone/.test(titleOf(menu)), `title (got ${titleOf(menu)})`);
    assert(containerSize(menu) === 27, `three rows (got ${containerSize(menu)})`);
    assert(menu.slots[11]?.name === 'shield' && menu.slots[15]?.name === 'crossbow', 'guard and hit buttons');
    owner.closeWindow(menu);
  });

  await step('Guard Mode brings two cousins in full iron with enchanted crossbow-pistols', async () => {
    const menu = await openPhone(owner);
    const since = owner.lines.length;
    await owner.clickWindow(11, 0, 0);
    await waitForLine(owner, /Leonel and Marco are here/, 5_000, since);
    const count = await cousinCount();
    assert(count === 2, `two cousins (got ${count}: ${await rcon.command(`minecraft:execute if entity ${COUSINS}`)}; ${await rcon.command('cartel status')})`);
    const one = `${COUSINS.slice(0, -1)},limit=1]`;
    const slots = { head: 'iron_helmet', chest: 'iron_chestplate', legs: 'iron_leggings', feet: 'iron_boots', mainhand: 'crossbow' };
    for (const [slot, item] of Object.entries(slots)) {
      const id = await rcon.command(`minecraft:data get entity ${one} equipment.${slot}.id`);
      assert(id.includes(`minecraft:${item}`), `${item} in ${slot} (got ${id})`);
    }
    for (const slot of ['head', 'chest', 'legs', 'feet']) {
      const look = await rcon.command(`minecraft:data get entity ${one} equipment.${slot}.components."minecraft:equippable".asset_id`);
      assert(look.includes('claudesmp:salamanca'), `Salamanca look on the ${slot} (got ${look})`);
    }
    for (const [enchantment, level] of Object.entries({ piercing: 4, quick_charge: 3 })) {
      const got = await rcon.command(`minecraft:data get entity ${one} equipment.mainhand.components."minecraft:enchantments"."minecraft:${enchantment}"`);
      assert(new RegExp(`: ${level}$`).test(got.trim()), `${enchantment} ${level} (got ${got})`);
    }
    const pistol = await rcon.command(`minecraft:data get entity ${one} equipment.mainhand.components."minecraft:custom_model_data".strings`);
    assert(pistol.includes('cartel_pistol'), `the pistol look (got ${pistol})`);
    const health = await rcon.command(`minecraft:data get entity ${one} Health`);
    assert(/40(\.0)?f/.test(health), `40 health (got ${health})`);
    void menu;
  });

  await step('the guards survive Towny mob removal in a town with mobs off', async () => {
    await sleep(7_000);
    assert((await cousinCount()) === 2, 'both cousins still there after Towny\'s 5 s sweep');
  });

  await step('the guards kill a monster near the owner and leave a bystander alone', async () => {
    const healthBefore = target.health;
    await rcon.command(`minecraft:summon minecraft:husk ${BX + 3} ${PLATFORM_Y + 1} ${BZ + 8} {Tags:["cartel.probe"],PersistenceRequired:1b}`);
    await waitFor(async () => !/count/i.test(await rcon.command('minecraft:execute if entity @e[tag=cartel.probe]')),
      'the husk is dead', 20_000);
    assert(target.health >= healthBefore, `bystander unhurt (${healthBefore} -> ${target.health})`);
    assert(owner.health === 20, `owner unhurt (${owner.health})`);
  });

  await step('the phone calls the cousins off', async () => {
    const menu = await openPhone(owner);
    assert(menu.slots[13]?.name === 'barrier', 'call-off button while active');
    await owner.clickWindow(13, 0, 0);
    await waitFor(async () => (await cousinCount()) === 0, 'cousins gone', 5_000);
  });

  await step('the cousins shoot a target on a pillar from range with bolts and rockets, without knockback, sparing a bystander', async () => {
    await rcon.command('cartel reset');
    const px = BX + 14;
    const pz = BZ + 8;
    // The bystander's pillar is 2 blocks off: inside a rocket blast's 5 blocks, in plain sight of it.
    const sx = px + 2;
    for (const x of [px, sx]) {
      await rcon.command(`minecraft:fill ${x} ${PLATFORM_Y + 1} ${pz} ${x} ${PLATFORM_Y + 4} ${pz} minecraft:stone`);
    }
    const bystander = await createBot(BYSTANDER);
    bots.push(bystander);
    await rcon.command(`minecraft:gamemode survival ${BYSTANDER}`);
    await teleport(rcon, target, px, PLATFORM_Y + 5, pz);
    await teleport(rcon, bystander, sx, PLATFORM_Y + 5, pz);
    // The most health there is, to take many hits in a row without dying. The client is sent health
    // scaled down (and not linearly), so the damage is read off the server's Health instead.
    await rcon.command(`minecraft:attribute ${TARGET} minecraft:max_health base set 1024`);
    await rcon.command(`minecraft:effect give ${TARGET} minecraft:instant_health 1 10 true`);
    await rcon.command(`minecraft:effect give ${BYSTANDER} minecraft:instant_health 1 10 true`);
    await sleep(1000);
    const serverHealth = async () => Number((await rcon.command(`minecraft:data get entity ${TARGET} Health`)).match(/([\d.]+)f/)?.[1]);
    let hits = [];
    let sources = [];
    // damage_event carries the damage type and the direct entity (protocol id + 1), the bolt or the rocket.
    // A rocket is often gone on the client before its blast's event arrives, so a hit whose damage type
    // isn't a bolt's is the rocket's. The packet can trail the RCON reply, so a drop waits 150 ms for it;
    // two hits in one drop are kept out of the per-kind numbers.
    let arrowType;
    const onDamage = (packet) => {
      if (packet.entityId !== target.entity.id) return;
      const name = target.entities[packet.sourceDirectId - 1]?.name;
      if (name === 'arrow') arrowType = packet.sourceTypeId;
      sources.push({ name, type: packet.sourceTypeId });
    };
    target._client.on('damage_event', onDamage);
    let polling = true;
    const poller = (async () => {
      let last = await serverHealth();
      while (polling) {
        const health = await serverHealth();
        if (health < last - 0.001) {
          await sleep(150);
          const kinds = sources.splice(0).map((d) => (d.name === 'arrow' || d.type === arrowType ? 'arrow' : 'firework_rocket'));
          hits.push({ damage: last - health, source: kinds.join('+') || 'unknown' });
        }
        last = health;
      }
    })();
    const of = (kind) => hits.filter((h) => h.source === kind).map((h) => h.damage);
    const shotFor = async (what, bolts, rockets) => {
      await waitFor(() => of('arrow').length >= bolts && of('firework_rocket').length >= rockets,
        `the target is shot (${what})`, 45_000).catch(async (error) => {
        const flying = await rcon.command('minecraft:execute if entity @e[type=#minecraft:arrows]');
        throw new Error(`${error.message} (hits ${JSON.stringify(hits)}, arrows: ${flying}, a cousin at `
          + `${JSON.stringify(await position(`${COUSINS.slice(0, -1)},limit=1]`))}, target at ${JSON.stringify(target.entity.position)})`);
      });
    };
    const fmt = (list) => list.map((h) => h.toFixed(2)).join(', ');
    // Live runs on hard, which would scale a mob's bolt or rocket by 1.5, so measure there.
    const difficulty = (await rcon.command('minecraft:difficulty')).match(/difficulty is (\w+)/i)?.[1]?.toLowerCase() ?? 'easy';
    await rcon.command('minecraft:difficulty hard');
    try {
      await confirmScreen(owner, TARGET);
      const since = target.lines.length;
      await owner.clickWindow(11, 0, 0);
      await waitForLine(target, /CartelMark, you have made enemies of the cartel\./, 5_000, since);
      await shotFor('no armor', 1, 1);
      const bare = of('arrow');
      const bareRockets = of('firework_rocket');
      // Joe's 3-4 hearts a hit, whatever the bolt's speed, the blast's distance or the difficulty.
      const fixed = (list) => list.every((h) => h >= 5.99 && h <= 8.01);
      assert(fixed(bare), `a bolt on a bare target did 6-8 (got ${fmt(bare)})`);
      assert(fixed(bareRockets), `a rocket on a bare target did 6-8 (got ${fmt(bareRockets)})`);
      const at = target.entity.position;
      assert(Math.abs(at.x - (px + 0.5)) < 0.45 && Math.abs(at.z - (pz + 0.5)) < 0.45 && at.y >= PLATFORM_Y + 4.9,
        `no knockback from bolts or rockets: still on the pillar (at ${at.x.toFixed(2)} ${at.y.toFixed(2)} ${at.z.toFixed(2)})`);
      assert(bystander.health === 20, `the bystander 2 blocks away is unhurt (${bystander.health})`);
      const nearest = await position(`@e[tag=cartel.cousin,limit=1,sort=nearest,x=${px + 0.5},y=${PLATFORM_Y + 1},z=${pz + 0.5}]`);
      const away = Math.hypot(nearest.x - (px + 0.5), nearest.z - (pz + 0.5));
      assert(away >= 2.5, `the nearest cousin shoots from range, not from the pillar's base (${away.toFixed(1)} blocks)`);

      for (const [slot, item] of Object.entries({ head: 'helmet', chest: 'chestplate', legs: 'leggings', feet: 'boots' })) {
        await rcon.command(`minecraft:item replace entity ${TARGET} armor.${slot} with minecraft:netherite_${item}[enchantments={protection:4}]`);
      }
      await rcon.command(`minecraft:effect give ${TARGET} minecraft:instant_health 1 10 true`);
      await sleep(1500);
      hits = [];
      await shotFor('Protection IV netherite', 2, 1);
      const armored = of('arrow');
      const armoredRockets = of('firework_rocket');
      assert(fixed(armored) && fixed(armoredRockets),
        `armor and Protection take nothing off (bolts ${fmt(armored)}, rockets ${fmt(armoredRockets)})`);
      assert(bystander.health === 20, `the bystander is still unhurt (${bystander.health})`);
      console.log(`  bolts on hard: ${fmt(bare)} bare, ${fmt(armored)} in full Protection IV netherite`);
      console.log(`  rockets on hard: ${fmt(bareRockets)} bare, ${fmt(armoredRockets)} in full Protection IV netherite; `
        + `nearest cousin ${away.toFixed(1)} blocks off; other drops: ${JSON.stringify(hits.filter((h) => !['arrow', 'firework_rocket'].includes(h.source)))}`);

      // A bolt that lands in a player is gone at once, so catch one in flight or in the ground.
      const arrow = `@e[type=minecraft:arrow,x=${px},y=${PLATFORM_Y},z=${pz},distance=..32,limit=1]`;
      let pickup = '';
      const until = Date.now() + 10_000;
      while (Date.now() < until && !/: \d+b$/.test(pickup.trim())) {
        pickup = await rcon.command(`minecraft:data get entity ${arrow} pickup`);
      }
      assert(/: 0b$/.test(pickup.trim()), `the bolts can't be picked up (got ${pickup})`);
    } finally {
      polling = false;
      await poller;
      target._client.off('damage_event', onDamage);
      await rcon.command('cartel dismiss');
      await rcon.command(`minecraft:difficulty ${difficulty}`);
      await rcon.command(`minecraft:clear ${TARGET}`);
      await rcon.command(`minecraft:attribute ${TARGET} minecraft:max_health base reset`);
      for (const x of [px, sx]) {
        await rcon.command(`minecraft:fill ${x} ${PLATFORM_Y + 1} ${pz} ${x} ${PLATFORM_Y + 4} ${pz} minecraft:air`);
      }
      await rcon.command('minecraft:kill @e[type=minecraft:arrow]');
      await rcon.command('minecraft:kill @e[type=minecraft:firework_rocket]');
      await teleport(rcon, bystander, BX - 16, PLATFORM_Y + 1, BZ - 16);
    }
  });

  await step('Assassinate Mode warns the server, spawns the cousins 10 blocks off and kills the target', async () => {
    await rcon.command('cartel reset');
    await waitFor(() => target.health > 0, 'target alive', 10_000);
    await rcon.command(`minecraft:effect give ${TARGET} minecraft:instant_health 1 10 true`);
    await teleport(rcon, target, BX + 12, PLATFORM_Y + 1, BZ + 8);
    await confirmScreen(owner, TARGET);
    const since = target.lines.length;
    let died = false;
    target.once('death', () => { died = true; });
    await owner.clickWindow(11, 0, 0);
    await waitForLine(target, /CartelMark, you have made enemies of the cartel\./, 5_000, since);
    const markAt = target.entity.position;
    const spot = await position(`${COUSINS.slice(0, -1)},limit=1]`);
    const d = Math.hypot(spot.x - markAt.x, spot.z - markAt.z);
    assert(d >= 8.5 && d <= 11.5, `cousin appeared ${d.toFixed(1)} blocks from the target`);
    await waitFor(() => died, 'the target is killed', 30_000);
    await waitForLine(owner, /The job is done/, 5_000);
    await waitForLine(owner, /Leonel and Marco bring you CartelMark's head\./, 5_000);
    await waitFor(async () => /Found [1-9]/.test(await rcon.command(`minecraft:clear ${OWNER} minecraft:player_head 0`)),
      "the owner holds the target's head", 5_000);
    await waitFor(async () => (await cousinCount()) === 0, 'cousins leave after the job', 5_000);
  });

  await step('a second hit is refused for 24 hours', async () => {
    await waitFor(() => target.health > 0, 'target respawned', 10_000);
    await sleep(1000);
    await confirmScreen(owner, TARGET);
    const since = owner.lines.length;
    await owner.clickWindow(11, 0, 0);
    await waitForLine(owner, /Assassinate Mode is ready again in 23h 59m/, 5_000, since);
    assert((await cousinCount()) === 0, 'no cousins');
  });

  await step('anyone else holding a phone gets a dead line', async () => {
    await rcon.command(`cartel give ${TARGET}`);
    await waitFor(() => target.inventory.items().some((i) => i.name === 'clock'), 'target has a phone', 5_000);
    const phone = target.inventory.items().find((i) => i.name === 'clock');
    await target.equip(phone, 'hand');
    const since = target.lines.length;
    target.activateItem();
    target.deactivateItem();
    await waitForLine(target, /The line is dead/, 5_000, since);
  });

  console.log(`\nCartel e2e: ${results.length} passed`);
} catch (error) {
  console.error(`FAIL after ${results.length} passed: ${error.message}`);
  process.exitCode = 1;
} finally {
  await rcon.command('cartel dismiss').catch(() => {});
  await rcon.command('cartel reset').catch(() => {});
  await rcon.command(`townyadmin town ${TOWN} delete`).catch(() => {});
  await rcon.command('confirm').catch(() => {});
  await rcon.command('minecraft:kill @e[tag=cartel.probe]').catch(() => {});
  await releasePlatform(rcon, BX, BZ);
  await rcon.command(`minecraft:forceload remove ${BX - 32} ${BZ - 32} ${BX + 32} ${BZ + 32}`).catch(() => {});
  await quit(...bots);
  rcon.close();
}
