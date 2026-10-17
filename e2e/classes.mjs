import { Vec3 } from 'vec3';
import {
  Rcon, assert, buildPlatform, clickCombo, createBot, nbtText, quit, randomOrigin, releasePlatform, sleep, teleport, waitFor, waitForLine,
} from './lib.mjs';

// RPG classes (GutGr1nder's wish, classes plugin): with no class a player hits exactly as before and
// earns no class XP; the first pick is free and another class then waits 7 days; a Warrior earns class
// XP for a hostile mob killed in melee and sees it in the tab footer; Weapon Master at level 25 adds 10%
// melee damage; the R-L-R click combo with a sword fires Battle Cry (less damage taken), which then
// waits out its cooldown; the menu picks a class in two clicks and the Arcanist's Frail Body takes a heart that
// leaving gives back; Mend heals; going back to the class you left is free; Fleet Foot gives Speed I,
// Overclock Haste I, and an ore mined without Silk Touch earns Artificer XP.
const PY = 180;
const { bx: BX, bz: BZ } = randomOrigin();
const A = `Cls${Date.now() % 100000}`;
const B = 'ClsMenu';
const C = 'ClsPlain';

const rcon = await Rcon.connect();
const bots = [];
const results = [];
async function step(name, fn) {
  await fn();
  results.push(name);
  console.log(`PASS ${name}`);
}

const plain = (s) => s.replace(/§./g, '');
const admin = async (args) => plain(await rcon.command(`class admin ${args}`));
const titleOf = (window) => (typeof window?.title === 'string' ? window.title
  : `${nbtText(window?.title)} ${JSON.stringify(window?.title)}`);
function nextWindow(bot, timeoutMs = 5_000) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(`${bot.username}: no window opened`)), timeoutMs);
    bot.once('windowOpen', (window) => {
      clearTimeout(timer);
      resolve(window);
    });
  });
}
async function say(bot, text, pattern) {
  const since = bot.lines.length;
  bot.chat(text);
  return waitForLine(bot, pattern, 10_000, since);
}
async function number(command) {
  const reply = await rcon.command(command);
  const match = /(-?\d+(?:\.\d+)?)[a-z]?\s*$/i.exec(reply.trim());
  if (!match) throw new Error(`no number in ${command}: ${reply}`);
  return Number(match[1]);
}

/** Damage one fully charged bare-hand punch deals to a fresh 100-health pig. */
async function punch(bot) {
  await rcon.command('minecraft:kill @e[type=pig,tag=cls_e2e]');
  // The last punch's pig lingers in bot.entities for its death animation; hitting it does nothing.
  await waitFor(() => !Object.values(bot.entities).some((e) => e.name === 'pig'
    && e.position.distanceTo({ x: BX + 2.5, y: PY + 1, z: BZ + 0.5 }) < 4), 'the old pig is gone', 10_000);
  await rcon.command(`minecraft:summon minecraft:pig ${BX + 2.5} ${PY + 1} ${BZ + 0.5} {NoAI:1b,Health:100f,Tags:["cls_e2e"],attributes:[{id:"minecraft:max_health",base:100}]}`);
  let pig;
  await waitFor(() => (pig = Object.values(bot.entities).find((e) => e.name === 'pig'
    && e.position.distanceTo({ x: BX + 2.5, y: PY + 1, z: BZ + 0.5 }) < 1.5)), `${bot.username} sees the pig`, 10_000);
  await bot.lookAt(pig.position.offset(0, 0.5, 0), true);
  await sleep(1200);
  bot.attack(pig);
  await sleep(600);
  return 100 - await number('minecraft:data get entity @e[type=pig,tag=cls_e2e,limit=1] Health');
}

// AuraSkills scales the hearts a client sees, so bot.health is not the server's health; read Health.
const health = (name) => number(`minecraft:data get entity ${name} Health`);

/** Health lost to a 10-point mob hit from a summoned zombie (scaled by difficulty like any mob hit). */
async function mobHit(bot) {
  const name = bot.username;
  await rcon.command(`minecraft:effect clear ${name}`);
  await rcon.command(`minecraft:effect give ${name} minecraft:instant_health 1 5 true`);
  await waitFor(async () => (await health(name)) >= 20, `${name} healed`, 5_000);
  await rcon.command(`minecraft:summon minecraft:zombie ${BX - 3} ${PY + 1} ${BZ - 3} {NoAI:1b,Silent:1b,Tags:["cls_e2e","cls_hitter"]}`);
  await sleep(300);
  await rcon.command(`minecraft:damage ${name} 10 minecraft:mob_attack by @e[tag=cls_hitter,limit=1]`);
  const left = await health(name);
  await rcon.command('minecraft:kill @e[tag=cls_hitter]');
  return 20 - left;
}

try {
  await buildPlatform(rcon, BX, BZ, PY);
  await rcon.command('minecraft:time set 1000');
  // Saturation healing would refill part of a measured hit within half a second.
  await rcon.command('minecraft:gamerule natural_regeneration false');
  for (const name of [A, B, C]) {
    const bot = await createBot(name);
    bots.push(bot);
    await rcon.command(`minecraft:gamemode survival ${name}`);
    await rcon.command(`minecraft:clear ${name}`);
    // Bots keep their health between runs, and an earlier run's Mend step leaves one hurt.
    await rcon.command(`minecraft:effect give ${name} minecraft:instant_health 1 5 true`);
    await rcon.command(`minecraft:effect give ${name} minecraft:saturation 1 5 true`);
    await admin(`set ${name} none`);
    await admin(`unlock ${name}`);
    await admin(`today ${name}`);
    for (const c of ['warrior', 'ranger', 'arcanist', 'artificer']) await admin(`setxp ${name} ${c} 0`);
  }
  const [a, b, c] = bots;
  await teleport(rcon, a, BX, PY + 1, BZ);
  await teleport(rcon, b, BX - 4, PY + 1, BZ + 4);
  await teleport(rcon, c, BX + 4, PY + 1, BZ - 4);

  let baseline;
  await step('with no class a player hits as before and earns no class XP', async () => {
    await say(a, '/class info', /You have no class/);
    baseline = await punch(a);
    assert(baseline > 0.5 && baseline < 1.5, `a bare-hand punch does about 1 (${baseline})`);
    assert(/Active: none, today 0\/300/.test(await admin(`info ${A}`)), 'no class, nothing earned');
  });

  await step('the first pick is free, another class then waits 7 days', async () => {
    await say(a, '/class choose warrior', /You are now a Warrior \(level 1\)/);
    await say(a, '/class choose ranger', /You can change class again in 6d 23h/);
    assert(/Active: Warrior/.test(await admin(`info ${A}`)), 'still a Warrior');
  });

  await step('a hostile mob killed in melee earns Warrior XP, shown in the tab footer', async () => {
    await rcon.command(`minecraft:summon minecraft:zombie ${BX + 2.5} ${PY + 1} ${BZ + 0.5} {NoAI:1b,Health:1f,Tags:["cls_e2e","cls_prey"],equipment:{head:{id:"minecraft:leather_helmet",count:1}}}`);
    let zombie;
    await waitFor(() => (zombie = Object.values(a.entities).find((e) => e.name === 'zombie'
      && e.position.distanceTo({ x: BX + 2.5, y: PY + 1, z: BZ + 0.5 }) < 1.5)), 'A sees the zombie');
    for (let i = 0; i < 5 && a.entities[zombie.id]; i++) {
      await a.lookAt(zombie.position.offset(0, 1, 0), true);
      a.attack(zombie);
      await sleep(700);
    }
    await waitFor(async () => /today 5\/300/.test(await admin(`info ${A}`)), 'Warrior earned 5 XP', 10_000);
    assert(/Warrior: 5 XP/.test(await admin(`info ${A}`)), 'the XP went to the Warrior');
    await waitFor(() => /Warrior · Level 1 \(5\/50 XP\) · today 5\/300 XP/.test(a.tablist?.footer?.toString() ?? ''),
      `footer shows the class (${a.tablist?.footer?.toString()})`, 5_000);
  });

  await step('Weapon Master at level 25 adds 10% melee damage', async () => {
    assert(/Level 25/.test(await admin(`setxp ${A} warrior 3960`)), 'Warrior set to 25');
    const boosted = await punch(a);
    const ratio = boosted / baseline;
    assert(Math.abs(ratio - 1.1) < 0.02, `level-25 punch is 10% harder (${baseline} -> ${boosted}, x${ratio.toFixed(3)})`);
  });

  await step('the R-L-R combo with a sword fires Battle Cry, which cuts mob damage and then waits out its cooldown', async () => {
    const plainHit = await mobHit(c);
    await admin(`ready ${A}`);
    await rcon.command(`minecraft:item replace entity ${A} weapon.mainhand with minecraft:iron_sword`);
    await waitFor(() => a.heldItem?.name === 'iron_sword', 'A holds the sword');
    // Sneak + swap hands used to cast; now it must be a plain swap.
    a.setControlState('sneak', true);
    await sleep(300);
    const swapSince = a.lines.length;
    a._client.write('block_dig', { status: 6, location: { x: 0, y: 0, z: 0 }, face: 0, sequence: 0 });
    await sleep(1_000);
    assert(!a.lines.slice(swapSince).some((l) => /^Battle Cry:/.test(l)), 'sneak + F no longer casts');
    a.setControlState('sneak', false);
    await rcon.command(`minecraft:item replace entity ${A} weapon.offhand with minecraft:air`);
    await rcon.command(`minecraft:item replace entity ${A} weapon.mainhand with minecraft:iron_sword`);
    await waitFor(() => a.heldItem?.name === 'iron_sword', 'A holds the sword again');
    await sleep(1_200); // past the combo gap, so nothing above counts
    const since = a.lines.length;
    await clickCombo(a, 'RLR');
    await waitForLine(a, /Battle Cry: take 20\.0% less damage for 10 s \(every 60 s\)/, 5_000, since);
    await rcon.command(`minecraft:item replace entity ${A} weapon.mainhand with minecraft:air`);
    const cut = await mobHit(a);
    assert(plainHit > 0, `a mob hit hurts a classless player (${plainHit})`);
    assert(Math.abs(cut / plainHit - 0.8) < 0.06, `Battle Cry takes 20% off (${plainHit} -> ${cut})`);
    await say(a, '/class ability', /Battle Cry is ready in \d+[sm]/);
  });

  await step('the menu picks a class in two clicks; Frail Body takes a heart', async () => {
    const opened = nextWindow(b);
    b.chat('/class');
    const menu = await opened;
    assert(/Classes/.test(titleOf(menu)), `the class menu opened (${titleOf(menu)})`);
    assert(menu.slots[12]?.name === 'amethyst_shard', `Arcanist in slot 12 (${menu.slots[12]?.name})`);
    await b.clickWindow(12, 0, 0);
    await sleep(500);
    assert(/Active: none/.test(await admin(`info ${B}`)), 'one click only asks to confirm');
    const since = b.lines.length;
    await b.clickWindow(12, 0, 0);
    await waitForLine(b, /You are now an Arcanist \(level 1\)/, 5_000, since);
    b.closeWindow(menu);
    const max = await number(`minecraft:attribute ${B} minecraft:max_health get`);
    assert(max === 18, `Frail Body: 9 hearts (${max})`);
  });

  await step('Mend heals; leaving gives the heart back and going back is free', async () => {
    await rcon.command(`minecraft:damage ${B} 6 minecraft:generic`);
    const hurt = await health(B);
    assert(hurt <= 12.01, `B hurt (${hurt})`);
    await say(b, '/class ability', /Mend: heal 1 heart and cure poison/);
    const mended = await health(B);
    assert(Math.abs(mended - hurt - 2) < 0.01, `Mend healed 2 (${hurt} -> ${mended})`);
    await say(b, '/class leave', /You left the Arcanist class/);
    assert((await number(`minecraft:attribute ${B} minecraft:max_health get`)) === 20, 'the heart is back');
    await say(b, '/class choose warrior', /You can change class again in/);
    await say(b, '/class choose arcanist', /You are now an Arcanist/);
  });

  await step('Fleet Foot gives Speed I and Overclock Haste I', async () => {
    await admin(`set ${A} ranger`);
    await say(a, '/class ability', /Fleet Foot: Speed I for 8 s/);
    const speed = await rcon.command(`minecraft:data get entity ${A} active_effects`);
    // Amplifier 0 (level I) is left out of the NBT.
    assert(/minecraft:speed/.test(speed) && !/amplifier: [1-9]/.test(speed), `Speed I (${speed})`);
    await admin(`set ${B} artificer`);
    await say(b, '/class ability', /Overclock: Haste I for 10 s/);
    const haste = await rcon.command(`minecraft:data get entity ${B} active_effects`);
    assert(/minecraft:haste/.test(haste) && !/amplifier: [1-9]/.test(haste), `Haste I (${haste})`);
  });

  await step('an ore mined without Silk Touch earns Artificer XP', async () => {
    await rcon.command(`minecraft:item replace entity ${B} weapon.mainhand with minecraft:diamond_pickaxe`);
    await waitFor(() => b.heldItem?.name === 'diamond_pickaxe', 'B holds a pickaxe', 5_000);
    const ore = new Vec3(BX - 4, PY + 1, BZ + 6);
    await rcon.command(`minecraft:setblock ${ore.x} ${ore.y} ${ore.z} minecraft:iron_ore`);
    await sleep(500);
    const block = b.blockAt(ore);
    assert(block?.name === 'iron_ore', `B sees the ore (${block?.name})`);
    await b.dig(block, true);
    await waitFor(async () => /Artificer: 2 XP/.test(await admin(`info ${B}`)), 'Artificer earned 2 XP', 10_000);
  });

  console.log(`\nAll ${results.length} classes steps passed`);
} catch (error) {
  console.error(`FAIL after ${results.length} passed steps: ${error.stack ?? error}`);
  process.exitCode = 1;
} finally {
  for (const name of [A, B, C]) await admin(`set ${name} none`).catch(() => {});
  await rcon.command('minecraft:gamerule natural_regeneration true').catch(() => {});
  await rcon.command('minecraft:kill @e[tag=cls_e2e]').catch(() => {});
  await releasePlatform(rcon, BX, BZ);
  await quit(...bots);
  rcon.close();
}
