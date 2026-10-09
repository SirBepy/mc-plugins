import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { Vec3 } from 'vec3';
import {
  Rcon, assert, createBot, quit, sleep, waitFor,
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
    surfaceY = Math.round(found.reduce((sum, p) => sum + p.surfaceY, 0) / found.length);
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
  const cultAlive = async () => Number(plain(await rcon.command('endfight cult status')).match(/Cult alive: (\d+)/)?.[1]);
  const dragonNbt = async (path) => plain(await rcon.command(`${END} data get entity ${dragon} ${path}`));
  const dragonHealth = async () => Number((await dragonNbt('Health')).match(/(-?[\d.]+)f?\s*$/)?.[1]);
  async function hurtDragon(amount) {
    await rcon.command(`${END} damage ${dragon} ${amount} minecraft:player_attack by ${GRIEFER}`);
    // the dragon shrugs off hits for half a second after each one
    await sleep(700);
  }

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
      // the server's first dragon is always Ferocious; the menu can't change it
      await fighter.clickWindow(12, 0, 0);
      await sleep(500);
      assert(/Next: Ferocious|current fight: Ferocious/.test(await tierStatus()), 'first kill stays Ferocious');
    } else {
      await fighter.clickWindow(12, 0, 0); // Cruel
      await waitFor(async () => /Next: Cruel/.test(await tierStatus()), 'the next dragon is Cruel', 5_000);
    }
    fighter.closeWindow(menu);
  });

  await step('guards take every post once a dragon is alive and a player is near', async () => {
    await rcon.command(`${END} minecraft:kill @e[type=minecraft:ender_dragon]`);
    await rcon.command('endfight cult clear');
    await rcon.command(`${END} summon minecraft:ender_dragon 0 100 0 {DragonPhase:0,Tags:["ef_e2e"]}`);
    await rcon.command(`${END} minecraft:tp ${GRIEFER} 0.5 ${surfaceY + 1} 0.5`);
    const posts = Number(plain(await rcon.command('endfight status')).match(/guard posts: (\d+)/)?.[1]);
    await waitFor(async () => (await cultAlive()) >= posts, `at least ${posts} cultists (one per post)`, 20_000);
  });

  await step("the new dragon gets its tier's health", async () => {
    const tier = (await tierStatus()).match(/current fight: (\w+)/)?.[1];
    const expected = healthByTier[tier];
    assert(expected, `a known tier is locked for this fight (${await tierStatus()})`);
    const maxHealth = async () => Number(plain(await rcon.command(`${END} attribute ${dragon} minecraft:max_health get`)).match(/([\d.]+)\s*$/)?.[1]);
    await waitFor(async () => (await maxHealth()) === expected, `the dragon's max health is ${expected} (${tier})`, 10_000);
  });

  await step('the dragon cannot start landing while a pillar crystal survives, and can once they are gone', async () => {
    // Fresh crystals on every pillar top, where vanilla puts them, so a rerun starts like a real fight.
    // Back to circling first: with no crystals left from the last run the dragon may already be
    // landing, and asking for the phase it is already in fires no phase change at all.
    await rcon.command(`${END} data merge entity ${dragon} {DragonPhase:0}`);
    await rcon.command(`${END} minecraft:kill @e[type=minecraft:end_crystal]`);
    for (const p of pillars()) {
      await rcon.command(`${END} summon minecraft:end_crystal ${p.cx}.5 ${p.topY + 1} ${p.cz}.5`);
    }
    await sleep(1_000);
    const crystals = plain(await rcon.command(`${END} execute if entity @e[type=minecraft:end_crystal]`));
    assert(/Count: 10\b/.test(crystals), `all 10 pillar crystals stand before the landing test (${crystals.trim()})`);
    await rcon.command(`${END} data merge entity ${dragon} {DragonPhase:2}`);
    await sleep(500);
    assert(!/: [23]\s*$/.test(await dragonNbt('DragonPhase')), `the landing approach was refused (${await dragonNbt('DragonPhase')})`);
    await rcon.command(`${END} minecraft:kill @e[type=minecraft:end_crystal]`);
    await sleep(500);
    await rcon.command(`${END} data merge entity ${dragon} {DragonPhase:2}`);
    await sleep(500);
    assert(/: [23]\s*$/.test(await dragonNbt('DragonPhase')), `with no crystals left it may land (${await dragonNbt('DragonPhase')})`);
    await rcon.command(`${END} data merge entity ${dragon} {DragonPhase:0}`);
  });

  await step('dropping below 75% health sends a wave out of the gates', async () => {
    const before = await cultAlive();
    const max = await dragonHealth();
    for (let i = 0; i < 40 && (await dragonHealth()) > max * 0.7; i++) {
      await hurtDragon(15);
    }
    assert((await dragonHealth()) <= max * 0.75, `the dragon is below 75% (${await dragonHealth()} of ${max})`);
    await waitFor(async () => (await cultAlive()) > before, `more cultists than the ${before} guards`, 10_000);
    assert(/crossed: \[[^\]]*0/.test(plain(await rcon.command('endfight cult status'))), 'the 75% threshold is recorded');
  });

  await step('the cult dies with the dragon, and its fighter gets XP and the reward', async () => {
    const levels = async () => Number(plain(await rcon.command(`minecraft:xp query ${GRIEFER} levels`)).match(/(\d+) experience levels?/)?.[1]);
    // Leftover orbs would soak up the dragon's: vanilla merges new XP into an orb already lying there.
    await rcon.command(`${END} minecraft:kill @e[type=minecraft:experience_orb]`);
    await rcon.command(`minecraft:xp set ${GRIEFER} 0 levels`);
    await rcon.command(`minecraft:xp set ${GRIEFER} 0 points`);
    for (let i = 0; i < 60 && /passed/i.test(await rcon.command(`${END} execute if entity ${dragon}`)) && (await dragonHealth()) > 0; i++) {
      await hurtDragon(40);
    }
    await waitFor(async () => (await cultAlive()) === 0, 'every cultist gone with the dragon', 30_000);
    // The dying dragon flies to the portal, then its 10 s death animation drops the orbs. The sweep
    // removes each one within a tick, so a check can rarely catch one mid-tick, but none may still be
    // there half a second later.
    let lingering = 0;
    let seenLast = false;
    const sightings = [];
    for (let i = 0; i < 50; i++) {
      const seen = /passed/i.test(await rcon.command(`${END} execute if entity @e[type=minecraft:experience_orb,x=0,y=80,z=0,distance=..48]`));
      if (seen) {
        const orb = '@e[type=minecraft:experience_orb,x=0,y=80,z=0,distance=..48,limit=1]';
        sightings.push(`t=${i / 2}s ${plain(await rcon.command(`${END} data get entity ${orb} Pos`)).replace(/.*data: /, '')}`);
        if (seenLast) {
          lingering++;
        }
      }
      seenLast = seen;
      await sleep(500);
    }
    if (sightings.length) {
      console.log(`orbs seen: ${sightings.join('; ')}`);
    }
    await waitFor(() => fighter.inventory.items().some((item) => item.name === 'black_shulker_box'), 'a Dragon Hoard arrives', 20_000);
    await waitFor(async () => (await levels()) > 0, 'XP arrives', 10_000);
    assert(lingering === 0, `no dragon XP orbs stay on the ground (${lingering} lingered)`);
  });
} finally {
  await quit(...bots);
  await rcon.command(`${END} forceload remove -64 -64 64 64`).catch(() => {});
  rcon.close?.();
  console.log(`endfight: ${results.length} steps passed`);
}
