import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { Vec3 } from 'vec3';
import {
  Rcon, assert, createBot, quit, sleep, waitFor, waitForLine,
} from './lib.mjs';

// EndFight's arena (docs/endfight.md): the plugin builds the cult's towers, cages and fortress
// around the End's pillars by itself the first time the End loads, and no explosion or non-admin
// player can break them. Coordinates come from the pillars the plugin saved in state.yml and the
// fortress geometry in FortressStructures (inner radius 14, 3 thick, 8 tall, gates on the axes).
const END = 'execute in minecraft:the_end run';
const STATE_YML = fileURLToPath(new URL('../testserver/run-e2e/plugins/EndFight/state.yml', import.meta.url));
const RUN = String(Math.floor(Math.random() * 10_000)).padStart(4, '0');
const GRIEFER = `EfGrief${RUN}`;

const rcon = await Rcon.connect();
const bots = [];
const results = [];
async function step(name, fn) {
  await fn();
  results.push(name);
  console.log(`PASS ${name}`);
}

const plain = (s) => s.replace(/§./g, '');

/** cx/cz/radius/topY/surfaceY per pillar, read from the plugin's own state.yml. */
function pillars() {
  const text = readFileSync(STATE_YML, 'utf8');
  const out = [];
  for (const block of text.split(/\r?\n\s*-\s+/).slice(1)) {
    const num = (key) => Number(block.match(new RegExp(`${key}:\\s*(-?\\d+)`))?.[1]);
    out.push({ cx: num('cx'), cz: num('cz'), radius: num('radius'), topY: num('topY'), surfaceY: num('surfaceY') });
  }
  return out;
}

async function isBlock(x, y, z, block) {
  return /passed/i.test(await rcon.command(`${END} execute if block ${x} ${y} ${z} ${block}`));
}

try {
  let surfaceY;
  let wall;

  await step('the arena builds itself when the End loads', async () => {
    await waitFor(async () => /Built: true/.test(plain(await rcon.command('endfight status'))), 'arena built', 60_000);
    const status = plain(await rcon.command('endfight status'));
    assert(/pillars: 10\b/.test(status), `10 pillars found (got ${status.trim()})`);
    const blocks = Number(status.match(/arena blocks: (\d+)/)?.[1]);
    assert(blocks > 5_000, `thousands of arena blocks placed (got ${blocks})`);
    const found = pillars();
    assert(found.length === 10, `state.yml lists 10 pillars (got ${found.length})`);
    // The fortress stands on the ground the first build read (state.yml's terrain).
    const fortressY = readFileSync(STATE_YML, 'utf8').match(/fortressY:\s*(-?\d+)/)?.[1];
    surfaceY = fortressY !== undefined ? Number(fortressY)
      : Math.round(found.reduce((sum, p) => sum + p.surfaceY, 0) / found.length);
    wall = { x: 16, y: surfaceY + 2, z: 5 };
    await rcon.command(`${END} forceload add -64 -64 64 64`);
    // A freshly force-loaded chunk answers block queries at once but only ticks entities a moment
    // later, and a TNT summoned before then never goes off.
    await sleep(3_000);
  });

  await step('the 5 shortest pillars are caged and the fortress wall stands', async () => {
    const shortest = pillars().sort((a, b) => a.topY - b.topY).slice(0, 5);
    for (const p of shortest) {
      assert(await isBlock(p.cx + 2, p.topY + 1, p.cz, 'minecraft:iron_bars'),
        `cage bars beside the crystal on the pillar at ${p.cx},${p.cz} (top ${p.topY})`);
    }
    assert(await isBlock(wall.x, wall.y, wall.z, 'minecraft:polished_blackstone_bricks'),
      `fortress wall at ${wall.x},${wall.y},${wall.z}`);
  });

  await step('an explosion against the fortress breaks the same brick outside the arena but not the wall', async () => {
    // The control is the wall's own block, placed outside the arena and closer to the TNT, so the
    // blast demonstrably breaks that material and only the arena's protection keeps the wall up.
    // An air pocket outside the wall, so uneven ground can't shield the control from the blast.
    await rcon.command(`${END} fill ${wall.x + 1} ${surfaceY + 1} ${wall.z - 2} ${wall.x + 6} ${surfaceY + 5} ${wall.z + 3} minecraft:air`);
    const control = { x: wall.x + 4, y: wall.y, z: wall.z + 1 };
    await rcon.command(`${END} setblock ${control.x} ${control.y} ${control.z} minecraft:polished_blackstone_bricks`);
    await rcon.command(`${END} summon minecraft:tnt ${wall.x + 2}.5 ${surfaceY + 1} ${wall.z}.5 {fuse:0}`);
    await waitFor(async () => !(await isBlock(control.x, control.y, control.z, 'minecraft:polished_blackstone_bricks')),
      'the blast broke the control brick', 10_000);
    assert(await isBlock(wall.x, wall.y, wall.z, 'minecraft:polished_blackstone_bricks'), 'the wall block survived the blast');
  });

  await step('a player without endfight.admin cannot break the arena', async () => {
    const bot = await createBot(GRIEFER);
    bots.push(bot);
    await rcon.command(`minecraft:gamemode creative ${GRIEFER}`);
    await rcon.command(`${END} minecraft:tp ${GRIEFER} ${wall.x + 2}.5 ${surfaceY + 1} ${wall.z}.5`);
    await waitFor(() => bot.game.dimension?.includes('end') && Math.abs(bot.entity.position.x - (wall.x + 2.5)) < 2,
      'griefer in the End beside the wall', 20_000);
    await bot.waitForChunksToLoad().catch(() => {});
    await sleep(1_000);
    const block = bot.blockAt(new Vec3(wall.x, wall.y, wall.z));
    assert(block?.name === 'polished_blackstone_bricks', `griefer sees the wall (got ${block?.name})`);
    await bot.dig(block, true).catch(() => {});
    await sleep(1_000);
    assert(await isBlock(wall.x, wall.y, wall.z, 'minecraft:polished_blackstone_bricks'), 'the wall block is still there');
    // The same dig on a non-arena block works, so the refusal above is the arena's, not the bot's.
    const control = { x: wall.x + 3, y: wall.y, z: wall.z };
    await rcon.command(`${END} setblock ${control.x} ${control.y} ${control.z} minecraft:polished_blackstone_bricks`);
    await sleep(500);
    await bot.dig(bot.blockAt(new Vec3(control.x, control.y, control.z)), true).catch(() => {});
    await waitFor(async () => isBlock(control.x, control.y, control.z, 'minecraft:air'), 'griefer broke the control brick', 5_000);
  });

  await step('endfight clear removes the arena and endfight build puts it back', async () => {
    await rcon.command('endfight clear');
    await waitFor(async () => isBlock(wall.x, wall.y, wall.z, 'minecraft:air'), 'wall cleared', 20_000);
    assert(/Built: false/.test(plain(await rcon.command('endfight status'))), 'status says not built');
    await rcon.command('endfight build');
    await waitFor(async () => isBlock(wall.x, wall.y, wall.z, 'minecraft:polished_blackstone_bricks'), 'wall rebuilt', 30_000);
    assert(/Built: true/.test(plain(await rcon.command('endfight status'))), 'status says built again');
  });

  // The fight itself, on a dragon of the suite's own (summoned over the portal, so a rerun never
  // depends on the End's natural dragon still being alive).
  const fighter = bots[0];
  const dragon = '@e[type=minecraft:ender_dragon,tag=ef_e2e,limit=1]';
  const cultStatus = async () => plain(await rcon.command('endfight cult status'));
  const cultAlive = async () => Number((await cultStatus()).match(/Cult alive: (\d+)/)?.[1]);
  const waveMobs = async () => Number((await cultStatus()).match(/wave mobs: (\d+)/)?.[1]);
  /** Live cult mobs of one type (pillager, piglin, paratrooper, phantom...), from the status line. */
  const cultOfType = async (type) => Number((await cultStatus()).match(new RegExp(`[{ ]${type}=(\\d+)`))?.[1] ?? 0);
  const dragonNbt = async (path) => plain(await rcon.command(`${END} data get entity ${dragon} ${path}`));
  const dragonHealth = async () => Number((await dragonNbt('Health')).match(/(-?[\d.]+)f?\s*$/)?.[1]);
  async function hurtDragon(amount) {
    await rcon.command(`${END} damage ${dragon} ${amount} minecraft:player_attack by ${GRIEFER}`);
    // the dragon shrugs off hits for half a second after each one
    await sleep(700);
  }
  /** Hits the dragon until its health is at or under `fraction` of `max`. */
  async function hurtDragonTo(fraction, max) {
    for (let i = 0; i < 80 && (await dragonHealth()) > max * fraction; i++) {
      await hurtDragon(Math.max(5, Math.min(40, Math.ceil((await dragonHealth()) - max * fraction) + 1)));
    }
    assert((await dragonHealth()) <= max * fraction, `the dragon is at or under ${fraction * 100}% (${await dragonHealth()} of ${max})`);
  }
  const bossBars = new Map();
  fighter.on('bossBarCreated', (bar) => bossBars.set(bar.entityUUID, bar));
  fighter.on('bossBarUpdated', (bar) => bossBars.set(bar.entityUUID, bar));
  fighter.on('bossBarDeleted', (bar) => bossBars.delete(bar.entityUUID));
  const armyBar = () => [...bossBars.values()].map((bar) => plain(bar.title?.toString() ?? '')).find((t) => /Dragon's army/.test(t));

  const tierStatus = async () => plain(await rcon.command('endfight tier status'));
  const firstKill = async () => /first kill ever: true/.test(await tierStatus());
  const healthByTier = { Fledgeling: 250, Powerful: 350, Cruel: 500, Immortal: 750, Ferocious: 850, Primordial: 1000, Ascended: 1250 };

  await step('the altar by the portal opens the tier menu and sets the next dragon', async () => {
    await rcon.command(`${END} minecraft:tp ${GRIEFER} 0.5 ${surfaceY + 1} -6.5`);
    await sleep(1_500);
    const altar = fighter.blockAt(new Vec3(0, surfaceY + 1, -9));
    assert(altar?.name === 'lodestone', `the altar's lodestone is there (got ${altar?.name})`);
    const opened = new Promise((resolve) => fighter.once('windowOpen', resolve));
    await fighter.activateBlock(altar);
    const menu = await Promise.race([opened, sleep(5_000).then(() => null)]);
    assert(menu, 'right-clicking the altar opens a menu');
    assert(/Dragon Altar/.test(JSON.stringify(menu.title)), `the menu is the Dragon Altar (${JSON.stringify(menu.title)})`);
    if (await firstKill()) {
      // the server's first dragon is always Ascended (Joe, 2026-10-09); the menu can't change it
      await fighter.clickWindow(12, 0, 0);
      await sleep(500);
      assert(/Next: Ascended|current fight: Ascended/.test(await tierStatus()), `first kill stays Ascended (${await tierStatus()})`);
    } else {
      await fighter.clickWindow(12, 0, 0); // Cruel
      await waitFor(async () => /Next: Cruel/.test(await tierStatus()), 'the next dragon is Cruel', 5_000);
    }
    fighter.closeWindow(menu);
  });

  await step('guards take every post, piglins on the Nether pillars, and a boss bar counts them', async () => {
    await rcon.command(`${END} minecraft:kill @e[type=minecraft:ender_dragon]`);
    await rcon.command('endfight cult clear');
    // A survival fighter (waves only scale with and chase survival players) who can't be hurt.
    await rcon.command(`minecraft:gamemode survival ${GRIEFER}`);
    await rcon.command(`minecraft:effect give ${GRIEFER} minecraft:resistance infinite 4 true`);
    await rcon.command(`minecraft:effect give ${GRIEFER} minecraft:saturation infinite 4 true`);
    await rcon.command(`${END} summon minecraft:ender_dragon 0 100 0 {DragonPhase:0,Tags:["ef_e2e"]}`);
    await rcon.command(`${END} minecraft:tp ${GRIEFER} 0.5 ${surfaceY + 1} 0.5`);
    const posts = Number(plain(await rcon.command('endfight status')).match(/guard posts: (\d+)/)?.[1]);
    await waitFor(async () => (await cultAlive()) >= posts, `at least ${posts} cultists (one per post)`, 20_000);
    assert((await cultOfType('piglin')) >= 15, `piglins hold the Nether pillars (${await cultStatus()})`);
    assert((await cultOfType('piglin_brute')) >= 5, `brutes on the Nether outposts (${await cultStatus()})`);
    assert((await cultOfType('vindicator')) >= 5, `vindicators on the illager outposts (${await cultStatus()})`);
    assert(/passed/i.test(await rcon.command(`${END} execute if entity @e[type=minecraft:piglin,nbt={IsImmuneToZombification:1b}]`)),
      'the piglins never zombify');
    await waitFor(() => armyBar(), "a Dragon's army boss bar", 10_000);
    assert(/Dragon's army: \d+ left \(/.test(armyBar()), `the bar names the army (${armyBar()})`);
  });

  await step("the new dragon gets its tier's health", async () => {
    const tier = (await tierStatus()).match(/current fight: (\w+)/)?.[1];
    const expected = healthByTier[tier];
    assert(expected, `a known tier is locked for this fight (${await tierStatus()})`);
    const maxHealth = async () => Number(plain(await rcon.command(`${END} attribute ${dragon} minecraft:max_health get`)).match(/([\d.]+)\s*$/)?.[1]);
    await waitFor(async () => (await maxHealth()) === expected, `the dragon's max health is ${expected} (${tier})`, 10_000);
  });

  await step('every standing crystal gets 2 phantoms circling its tower', async () => {
    // Fresh crystals on every pillar top, where vanilla puts them, so a rerun starts like a real fight.
    await rcon.command(`${END} minecraft:kill @e[type=minecraft:end_crystal]`);
    await rcon.command(`${END} minecraft:kill @e[type=minecraft:phantom]`);
    for (const p of pillars()) {
      await rcon.command(`${END} summon minecraft:end_crystal ${p.cx}.5 ${p.topY + 1} ${p.cz}.5`);
    }
    // The wardens are topped up every 30 s.
    await waitFor(async () => (await cultOfType('phantom')) >= 20, `20 tower phantoms (${await cultStatus()})`, 40_000);
    const p = pillars()[0];
    assert(/passed/i.test(await rcon.command(`${END} execute if entity @e[type=minecraft:phantom,x=${p.cx},y=${p.topY},z=${p.cz},distance=..30]`)),
      `a phantom circles the tower at ${p.cx},${p.cz}`);
  });

  await step('the dragon cannot start landing while a pillar crystal survives, and can once they are gone', async () => {
    await rcon.command(`${END} data merge entity ${dragon} {DragonPhase:0}`);
    const crystals = plain(await rcon.command(`${END} execute if entity @e[type=minecraft:end_crystal]`));
    assert(/Count: 10\b/.test(crystals), `all 10 pillar crystals stand before the landing test (${crystals.trim()})`);
    await rcon.command(`${END} data merge entity ${dragon} {DragonPhase:2}`);
    await sleep(500);
    assert(!/: [23]\s*$/.test(await dragonNbt('DragonPhase')), `the landing approach was refused (${await dragonNbt('DragonPhase')})`);
    // The fighter breaks one pillar crystal (the +5% roll bonus), the rest are cleared.
    const p = pillars()[0];
    await rcon.command(`${END} damage @e[type=minecraft:end_crystal,x=${p.cx}.5,y=${p.topY + 1},z=${p.cz}.5,distance=..2,limit=1] 1 minecraft:player_attack by ${GRIEFER}`);
    await rcon.command(`${END} minecraft:kill @e[type=minecraft:end_crystal]`);
    await rcon.command(`${END} minecraft:kill @e[type=minecraft:phantom]`);
    await sleep(500);
    await rcon.command(`${END} data merge entity ${dragon} {DragonPhase:2}`);
    await sleep(500);
    assert(/: [23]\s*$/.test(await dragonNbt('DragonPhase')), `with no crystals left it may land (${await dragonNbt('DragonPhase')})`);
    await rcon.command(`${END} data merge entity ${dragon} {DragonPhase:0}`);
  });

  let max;
  await step('the first hit confirms the fighter, and 90% sends scouts out of the gates', async () => {
    const since = fighter.lines.length;
    max = await dragonHealth();
    await hurtDragon(5);
    await waitForLine(fighter, /You hurt the dragon!/, 5_000, since);
    await hurtDragonTo(0.89, max);
    // Wave mobs, not the total: guards on the pillars die to the dragon's wings all fight long.
    await waitFor(async () => (await waveMobs()) > 0, `scouts out of the gates (${await cultStatus()})`, 10_000);
    assert(/crossed: \[[^\]]*0/.test(await cultStatus()), 'the 90% threshold is recorded');
    await waitForLine(fighter, /Cult scouts pour out/, 5_000, since);
  });

  await step('75% drops piglins off the dragon, floating down with slow falling', async () => {
    // Counted while they float (a few seconds): guard piglins on the pillars can die to the
    // dragon's wings meanwhile, so the total isn't a reliable measure.
    const floating = '@e[type=minecraft:piglin,nbt={active_effects:[{id:"minecraft:slow_falling"}]}]';
    await hurtDragonTo(0.74, max);
    // They come out over 2.5 s and float for a few seconds more.
    let seen = 0;
    for (let i = 0; i < 80 && seen < 2; i++) {
      const out = plain(await rcon.command(`${END} execute if entity ${floating}`));
      seen = Math.max(seen, Number(out.match(/Count: (\d+)/)?.[1] ?? 0));
      await sleep(100);
    }
    assert(seen >= 2, `2 piglins floated down off the dragon (saw ${seen}; ${await cultStatus()})`);
  });

  await step('50% sends 3 phantoms after the top damage dealer', async () => {
    const phantoms = await cultOfType('phantom');
    await hurtDragonTo(0.49, max);
    await waitFor(async () => (await cultOfType('phantom')) >= phantoms + 3, `3 more phantoms (${await cultStatus()})`, 10_000);
  });

  await step('35% brings evokers and ravagers', async () => {
    const evokers = await cultOfType('evoker');
    const ravagers = await cultOfType('ravager');
    await hurtDragonTo(0.34, max);
    await waitFor(async () => (await cultOfType('ravager')) > ravagers && (await cultOfType('evoker')) > evokers,
      `one more ravager and evoker than before (${await cultStatus()})`, 10_000);
  });

  await step('15% drops gliding paratroopers whose elytras never drop', async () => {
    await hurtDragonTo(0.14, max);
    await waitFor(async () => (await cultOfType('paratrooper')) >= 4, `4 paratroopers (${await cultStatus()})`, 10_000);
    const stats = plain(await rcon.command('endfight cult stats'));
    assert(new RegExp(`${GRIEFER}=\\d+`).test(stats), `the fighter's damage is tallied (${stats})`);
  });

  await step('the cult dies with the dragon, and the final blow gets XP, the hoard and the Dragon Head Helm in its centre', async () => {
    const levels = async () => Number(plain(await rcon.command(`minecraft:xp query ${GRIEFER} levels`)).match(/(\d+) experience levels?/)?.[1]);
    // Leftover orbs would soak up the dragon's: vanilla merges new XP into an orb already lying there.
    await rcon.command(`${END} minecraft:kill @e[type=minecraft:experience_orb]`);
    await rcon.command(`minecraft:xp set ${GRIEFER} 0 levels`);
    await rcon.command(`minecraft:xp set ${GRIEFER} 0 points`);
    for (let i = 0; i < 80 && /passed/i.test(await rcon.command(`${END} execute if entity ${dragon}`)) && (await dragonHealth()) > 0; i++) {
      await hurtDragon(40);
    }
    await waitFor(async () => (await cultAlive()) === 0, 'every cultist gone with the dragon', 30_000);
    await waitFor(() => !armyBar(), "the Dragon's army bar is gone", 10_000);
    // The dying dragon flies to the portal, then its 10 s death animation drops the orbs. The sweep
    // removes each one within a tick, so a check can rarely catch one mid-tick, but none may still be
    // there half a second later.
    let lingering = 0;
    let seenLast = false;
    for (let i = 0; i < 50; i++) {
      const seen = /passed/i.test(await rcon.command(`${END} execute if entity @e[type=minecraft:experience_orb,x=0,y=80,z=0,distance=..48]`));
      if (seen && seenLast) {
        lingering++;
      }
      seenLast = seen;
      await sleep(500);
    }
    await waitFor(() => fighter.inventory.items().some((item) => item.name === 'black_shulker_box'), 'a Dragon Hoard arrives', 20_000);
    await waitFor(async () => (await levels()) > 0, 'XP arrives', 10_000);
    assert(lingering === 0, `no dragon XP orbs stay on the ground (${lingering} lingered)`);
    const hoard = `${GRIEFER} Inventory[{id:"minecraft:black_shulker_box"}].components."minecraft:container"`;
    const centre = plain(await rcon.command(`data get entity ${hoard}[{slot:13}].item.components."minecraft:custom_model_data"`));
    assert(/dragon_head/.test(centre), `the Dragon Head Helm sits in the centre slot (${centre})`);
    const inHoard = async (match) => /passed/i.test(await rcon.command(`execute if data entity ${hoard}[{item:${match}}]`));
    for (const [id, slot] of [['netherite_chestplate', 'chest'], ['netherite_leggings', 'legs'], ['netherite_boots', 'feet']]) {
      if (await inHoard(`{id:"minecraft:${id}"}`)) {
        assert(await inHoard(`{id:"minecraft:${id}",components:{"minecraft:equippable":{slot:"${slot}"}}}`),
          `a rolled ${id} equips to ${slot}`);
      }
    }
    assert(await inHoard('{id:"minecraft:iron_ingot"}'), 'the hoard holds the iron filler');
    assert(await inHoard('{id:"minecraft:golden_carrot"}'), 'and the golden carrots');
  });

  await step('summoning a new dragon locks players onto the platform and restores the whole island', async () => {
    // Something built in the courtyard and a hole in the fortress wall: the restore undoes both.
    const built = { x: 5, y: surfaceY + 6, z: 5 };
    await rcon.command(`${END} setblock ${built.x} ${built.y} ${built.z} minecraft:dirt`);
    await rcon.command(`${END} setblock ${wall.x} ${wall.y} ${wall.z} minecraft:air`);
    await rcon.command(`${END} minecraft:tp ${GRIEFER} 0.5 ${surfaceY + 1} 3.5`);
    await sleep(1_000);
    const since = fighter.lines.length;
    await rcon.command('endfight respawn');
    await waitFor(() => Math.abs(fighter.entity.position.x - 100.5) < 3 && Math.abs(fighter.entity.position.z - 0.5) < 3,
      `the fighter is pulled onto the platform (at ${fighter.entity.position})`, 30_000);
    await waitForLine(fighter, /The arena is ready/, 120_000, since);
    assert(await isBlock(wall.x, wall.y, wall.z, 'minecraft:polished_blackstone_bricks'), 'the wall hole is filled back');
    assert(await isBlock(built.x, built.y, built.z, 'minecraft:air'), 'the courtyard build is gone');
    const posts = Number(plain(await rcon.command('endfight status')).match(/guard posts: (\d+)/)?.[1]);
    await rcon.command(`${END} minecraft:tp ${GRIEFER} 0.5 ${surfaceY + 1} 0.5`);
    await waitFor(async () => (await cultAlive()) >= posts, `the guards are back (${await cultStatus()})`, 30_000);
  });
} finally {
  await quit(...bots);
  await rcon.command(`${END} forceload remove -64 -64 64 64`).catch(() => {});
  rcon.close?.();
  console.log(`endfight: ${results.length} steps passed`);
}
