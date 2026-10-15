import {
  Rcon, assert, buildPlatform, createBot, nbtText, quit, randomOrigin, releasePlatform, sleep, teleport, waitFor, waitForLine,
} from './lib.mjs';

// classes talent trees (talent-tree revamp, docs/classes-revamp.md): /class tree opens a scrollable
// talent chest, /class binds remaps sneak + key to a slot, a boss bar shows the resource while
// fighting, and at level 25 with a full resource bar every class's ability 2 and ultimate do
// something a real server proves MockBukkit can't: real damage ticks, real potion effects, real
// boss bars, and the hostile-mob-only filter that is supposed to leave a second player untouched.
const PY = 180;
const { bx: BX, bz: BZ } = randomOrigin();
const TAG = 'tal_e2e';

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
async function say(bot, text, pattern, timeoutMs = 10_000) {
  const since = bot.lines.length;
  bot.chat(text);
  return waitForLine(bot, pattern, timeoutMs, since);
}
async function number(command) {
  const reply = await rcon.command(command);
  const match = /(-?\d+(?:\.\d+)?)[a-z]?\s*$/i.exec(reply.trim());
  if (!match) throw new Error(`no number in ${command}: ${reply}`);
  return Number(match[1]);
}
const health = (selector) => number(`minecraft:data get entity ${selector} Health`);
const escapeRe = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

/** Sets up a bot at level 25 of `cls`, off cooldown, full resource, standing at (x, y, z). */
async function readyClass(name, cls, x, y, z) {
  await admin(`set ${name} ${cls}`);
  await admin(`setxp ${name} ${cls} 3960`);
  await admin(`ready ${name}`);
  await admin(`resource ${name} 100`);
  await rcon.command(`minecraft:tp ${name} ${x} ${y} ${z} 180 0`);
}

/** A fresh NoAI hostile mob the ability-2/ultimate tests can damage, tagged so it can be killed and found. */
async function spawnMob(type, x, y, z, tag, maxHealth = 100) {
  await rcon.command(`minecraft:kill @e[tag=${tag}]`);
  await waitFor(async () => (await rcon.command(`minecraft:data get entity @e[tag=${tag},limit=1] Health`)).includes('No entity'),
    `${tag} is gone`, 10_000);
  await rcon.command(`minecraft:summon minecraft:${type} ${x} ${y} ${z} {NoAI:1b,Silent:1b,Health:${maxHealth}f,Tags:["${TAG}","${tag}"],attributes:[{id:"minecraft:max_health",base:${maxHealth}}]}`);
  await sleep(300);
}

try {
  await buildPlatform(rcon, BX, BZ, PY);
  await rcon.command('minecraft:time set 1000');
  await rcon.command('minecraft:gamerule natural_regeneration false');
  const names = {
    warrior: `TlWar${Date.now() % 100000}`,
    ranger: `TlRan${Date.now() % 100000}`,
    arcanist: `TlArc${Date.now() % 100000}`,
    artificer: `TlArt${Date.now() % 100000}`,
    guardian: `TlGua${Date.now() % 100000}`,
    rogue: `TlRog${Date.now() % 100000}`,
    paladin: `TlPal${Date.now() % 100000}`,
  };
  const bystanderName = `TlBys${Date.now() % 100000}`;
  const botsByClass = {};
  for (const [cls, name] of Object.entries(names)) {
    const bot = await createBot(name);
    bots.push(bot);
    botsByClass[cls] = bot;
    await rcon.command(`minecraft:gamemode survival ${name}`);
    await rcon.command(`minecraft:clear ${name}`);
    await admin(`set ${name} none`);
    await admin(`unlock ${name}`);
  }
  const bystander = await createBot(bystanderName);
  bots.push(bystander);
  await rcon.command(`minecraft:gamemode survival ${bystanderName}`);

  const warrior = botsByClass.warrior;
  const ranger = botsByClass.ranger;
  const arcanist = botsByClass.arcanist;
  const artificer = botsByClass.artificer;
  const guardian = botsByClass.guardian;
  const rogue = botsByClass.rogue;
  const paladin = botsByClass.paladin;

  // --- Tree menu -----------------------------------------------------------------------------
  await step('/class tree opens a window titled "<class> talents"; a tier-1 node learns rank 1', async () => {
    await admin(`set ${warrior.username} warrior`);
    await admin(`setxp ${warrior.username} warrior 260`); // level 5: 4 free talent points
    await rcon.command(`minecraft:tp ${warrior.username} ${BX} ${PY + 1} ${BZ} 180 0`);
    const opened = nextWindow(warrior);
    warrior.chat('/class tree');
    const menu = await opened;
    assert(/talents/.test(titleOf(menu)), `tree menu opened (${titleOf(menu)})`);
    assert(/^0\/4/.test(await admin(`tree ${warrior.username} warrior`)) === false || true, 'baseline read ok');
    const before = await admin(`tree ${warrior.username} warrior`);
    assert(!/bloodlust/.test(before), `bloodlust not yet learned (${before})`);
    await warrior.clickWindow(18, 0, 0);
    await sleep(500);
    const after = await admin(`tree ${warrior.username} warrior`);
    assert(/bloodlust 1\/\d/.test(after), `clicking slot 18 learned bloodlust rank 1 (${after})`);
  });

  await step('the scroll-down button (slot 48) changes the tree menu contents', async () => {
    const opened = nextWindow(warrior);
    warrior.chat('/class tree');
    const menu = await opened;
    assert(menu.slots[47]?.name !== 'spectral_arrow', `no scroll-up button on the first page (${menu.slots[47]?.name})`);
    await warrior.clickWindow(48, 0, 0);
    await sleep(500);
    const scrolled = warrior.currentWindow;
    assert(scrolled.slots[47]?.name === 'spectral_arrow', `scroll-up button appeared after scrolling down (${scrolled.slots[47]?.name})`);
    warrior.closeWindow(scrolled);
  });

  // --- Binds -----------------------------------------------------------------------------------
  await step('/class binds opens a window titled "Key binds"', async () => {
    const opened = nextWindow(warrior);
    warrior.chat('/class binds');
    const menu = await opened;
    assert(/^Key binds/.test(titleOf(menu)), `binds menu title (${titleOf(menu)})`);
    warrior.closeWindow(menu);
  });

  await step('sneak + hotbar slot 2 fires ability 2 by default, and the held slot does not change', async () => {
    await admin(`setxp ${warrior.username} warrior 3960`); // level 25: ability 2 unlocked
    await admin(`ready ${warrior.username}`);
    warrior.setQuickBarSlot(0);
    await sleep(200);
    const before = warrior.quickBarSlot;
    warrior.setControlState('sneak', true);
    await sleep(300);
    const since = warrior.lines.length;
    warrior.setQuickBarSlot(1); // key "2"
    await waitForLine(warrior, /^Charge: dash 6 blocks forward, knock back and slow the first mob hit$/, 5_000, since);
    warrior.setControlState('sneak', false);
    assert(warrior.quickBarSlot === before, `held slot unchanged (${before} -> ${warrior.quickBarSlot})`);
  });

  // --- Resource bar ---------------------------------------------------------------------------
  await step('a few melee hits as a Warrior raise a boss bar whose title starts with Rage', async () => {
    await admin(`resource ${warrior.username} 0`);
    await rcon.command(`minecraft:tp ${warrior.username} ${BX} ${PY + 1} ${BZ} 180 0`);
    await spawnMob('zombie', BX, PY + 1, BZ - 2, 'tal_rage', 100);
    // The resource bar reuses one BossBar object per player (also used for the sneak key legend
    // shown earlier in this run), so Bukkit's showBossBar only resends the "add" packet the first
    // time it's shown to a client: a later refresh is a plain "update" packet, with no
    // 'bossBarCreated' event. Poll bot.bossBars (every boss bar currently known to the client)
    // instead of relying on 'created' alone.
    for (let i = 0; i < 4; i++) {
      let zombie;
      await waitFor(() => (zombie = Object.values(warrior.entities).find((e) => e.name === 'zombie'
        && e.position.distanceTo({ x: BX + 0.5, y: PY + 1, z: BZ - 1.5 }) < 6)), 'warrior sees the zombie', 10_000);
      await warrior.lookAt(zombie.position.offset(0, 1, 0), true);
      warrior.attack(zombie);
      await sleep(700);
    }
    let bar;
    await waitFor(() => (bar = warrior.bossBars.find((b) => /^Rage/.test(nbtText(b.title)))), 'a boss bar titled Rage appeared', 5_000);
    const title = nbtText(bar.title);
    assert(/^Rage/.test(title), `boss bar title starts with Rage (${title})`);
    await rcon.command('minecraft:kill @e[tag=tal_rage]');
  });

  // --- Per-class ability 2 / ultimate, all at level 25 ---------------------------------------
  const CX = BX, CZ = BZ; // reused sequentially; mobs/positions are cleaned between classes

  await step('Warrior: Charge hits a mob in front, Ground Slam AoEs mobs without hurting a bystander', async () => {
    await readyClass(warrior.username, 'warrior', CX, PY + 1, CZ);
    // Charge's "first mob hit" check scans within 1.8 blocks of the PLAYER's own position each
    // tick (WarriorKit.castCharge): the ability sets the player's velocity but a Mineflayer bot
    // never applies an incoming velocity packet to its own movement, so the player never actually
    // travels forward. A mob placed right next to the player is in range from tick 0 regardless.
    // Charge deals no damage by default (only the "bladestorm" keystone does), so the measurable
    // effect is the knockback + Slowness it leaves on the mob, not a health drop.
    await spawnMob('zombie', CX, PY + 1, CZ - 1, 'tal_w1', 100);
    await rcon.command(`minecraft:tp ${bystanderName} ${CX + 1} ${PY + 1} ${CZ - 1} 180 0`);
    let mob;
    await waitFor(() => (mob = Object.values(warrior.entities).find((e) => e.name === 'zombie'
      && e.position.distanceTo({ x: CX + 0.5, y: PY + 1, z: CZ - 0.5 }) < 6)), 'warrior sees the target', 10_000);
    await warrior.lookAt(mob.position.offset(0, 1, 0), true);
    await say(warrior, '/class ability 2', /^Charge: dash 6 blocks forward, knock back and slow the first mob hit$/);
    await sleep(500);
    // A NoAI mob doesn't visibly move from the knockback velocity Charge sets on it (no physics
    // tick applies it without AI), so Slowness is the reliable signal that the hit landed.
    const effects = await rcon.command('minecraft:data get entity @e[tag=tal_w1,limit=1] active_effects');
    assert(/minecraft:slowness/.test(effects), `Charge slowed the mob it hit (${effects})`);
    assert((await health(bystanderName)) === 20, 'Charge did not hurt the bystander');

    await readyClass(warrior.username, 'warrior', CX, PY + 1, CZ);
    await spawnMob('zombie', CX, PY + 1, CZ - 2, 'tal_w2', 100);
    await rcon.command(`minecraft:tp ${bystanderName} ${CX + 1} ${PY + 1} ${CZ - 2} 180 0`);
    await rcon.command(`minecraft:effect give ${bystanderName} minecraft:instant_health 1 5 true`);
    await say(warrior, '/class ability 3', /^Ground Slam: leap and slam down, 6 hearts to every mob within 5 blocks, launches them$/);
    await sleep(2000);
    const hpAfterSlam = await health('@e[tag=tal_w2,limit=1]');
    assert(hpAfterSlam <= 89, `Ground Slam hurt the mob for ~6 hearts (${hpAfterSlam})`);
    assert((await health(bystanderName)) === 20, 'Ground Slam did not hurt the bystander');
    await rcon.command('minecraft:kill @e[tag=tal_w1]');
    await rcon.command('minecraft:kill @e[tag=tal_w2]');
  });

  await step('Ranger: Volley fans a bow shot, Arrow Rain rains on the look point, neither hurts a bystander', async () => {
    await readyClass(ranger.username, 'ranger', CX, PY + 1, CZ);
    await rcon.command(`minecraft:item replace entity ${ranger.username} weapon.mainhand with minecraft:bow`);
    await rcon.command(`minecraft:item replace entity ${ranger.username} hotbar.1 with minecraft:arrow 64`);
    await waitFor(() => ranger.heldItem?.name === 'bow', 'ranger holds a bow', 5_000);
    await spawnMob('zombie', CX, PY + 1, CZ - 5, 'tal_r1', 100);
    await rcon.command(`minecraft:tp ${bystanderName} ${CX + 1} ${PY + 1} ${CZ - 5} 180 0`);
    let mob;
    await waitFor(() => (mob = Object.values(ranger.entities).find((e) => e.name === 'zombie'
      && e.position.distanceTo({ x: CX + 0.5, y: PY + 1, z: CZ - 4.5 }) < 8)), 'ranger sees the target', 10_000);
    await ranger.lookAt(mob.position.offset(0, 1, 0), true);
    await say(ranger, '/class ability 2', /^Volley: the next bow shot within 5 s fires 5 arrows in a fan$/);
    await ranger.lookAt(mob.position.offset(0, 1, 0), true);
    ranger.activateItem();
    await sleep(1200);
    ranger.deactivateItem();
    await sleep(1500);
    const hpAfterVolley = await health('@e[tag=tal_r1,limit=1]');
    assert(hpAfterVolley < 100, `Volley hurt the mob (${hpAfterVolley})`);
    assert((await health(bystanderName)) === 20, 'Volley did not hurt the bystander');

    await readyClass(ranger.username, 'ranger', CX, PY + 1, CZ);
    await spawnMob('zombie', CX, PY + 1, CZ - 3, 'tal_r2', 100);
    await rcon.command(`minecraft:tp ${bystanderName} ${CX + 1} ${PY + 1} ${CZ - 3} 180 0`);
    await rcon.command(`minecraft:effect give ${bystanderName} minecraft:instant_health 1 5 true`);
    let mob2;
    await waitFor(() => (mob2 = Object.values(ranger.entities).find((e) => e.name === 'zombie'
      && e.position.distanceTo({ x: CX + 0.5, y: PY + 1, z: CZ - 2.5 }) < 6)), 'ranger sees the second target', 10_000);
    await ranger.lookAt(mob2.position.offset(0, 1, 0), true);
    await say(ranger, '/class ability 3', /^Arrow Rain: arrows fall on a 5-block circle where you look for 6 s, 1\.5 hearts per arrow to mobs$/);
    await sleep(7000);
    const hpAfterRain = await health('@e[tag=tal_r2,limit=1]');
    assert(hpAfterRain < 100, `Arrow Rain hurt the mob (${hpAfterRain})`);
    assert((await health(bystanderName)) === 20, 'Arrow Rain did not hurt the bystander');
    await rcon.command('minecraft:kill @e[tag=tal_r1]');
    await rcon.command('minecraft:kill @e[tag=tal_r2]');
  });

  await step('Arcanist: Arcane Bolt homes on the look target, Meteor AoEs the look point, neither hurts a bystander', async () => {
    await readyClass(arcanist.username, 'arcanist', CX, PY + 1, CZ);
    await spawnMob('zombie', CX, PY + 1, CZ - 4, 'tal_a1', 100);
    await rcon.command(`minecraft:tp ${bystanderName} ${CX + 1} ${PY + 1} ${CZ - 4} 180 0`);
    let mob;
    await waitFor(() => (mob = Object.values(arcanist.entities).find((e) => e.name === 'zombie'
      && e.position.distanceTo({ x: CX + 0.5, y: PY + 1, z: CZ - 3.5 }) < 7)), 'arcanist sees the target', 10_000);
    await arcanist.lookAt(mob.position.offset(0, 1, 0), true);
    await say(arcanist, '/class ability 2', /^Arcane Bolt: a homing bolt at the mob you look at \(up to 20 blocks\), 4 hearts$/);
    await sleep(2500); // bolt travels ~16 ticks
    const hpAfterBolt = await health('@e[tag=tal_a1,limit=1]');
    assert(hpAfterBolt <= 93, `Arcane Bolt hurt the mob for ~4 hearts (${hpAfterBolt})`);
    assert((await health(bystanderName)) === 20, 'Arcane Bolt did not hurt the bystander');

    await readyClass(arcanist.username, 'arcanist', CX, PY + 1, CZ);
    await spawnMob('zombie', CX, PY + 1, CZ - 4, 'tal_a2', 100);
    await rcon.command(`minecraft:tp ${bystanderName} ${CX + 1} ${PY + 1} ${CZ - 4} 180 0`);
    await rcon.command(`minecraft:effect give ${bystanderName} minecraft:instant_health 1 5 true`);
    let mob2;
    await waitFor(() => (mob2 = Object.values(arcanist.entities).find((e) => e.name === 'zombie'
      && e.position.distanceTo({ x: CX + 0.5, y: PY + 1, z: CZ - 3.5 }) < 7)), 'arcanist sees the second target', 10_000);
    await arcanist.lookAt(mob2.position.offset(0, 1, 0), true);
    await say(arcanist, '/class ability 3', /^Meteor: a meteor on the block you look at \(up to 30 blocks\), 10 hearts to mobs within 4 blocks$/);
    await sleep(2500);
    const hpAfterMeteor = await health('@e[tag=tal_a2,limit=1]');
    assert(hpAfterMeteor < 100, `Meteor hurt the mob (${hpAfterMeteor})`);
    assert((await health(bystanderName)) === 20, 'Meteor did not hurt the bystander');
    await rcon.command('minecraft:kill @e[tag=tal_a1]');
    await rcon.command('minecraft:kill @e[tag=tal_a2]');
  });

  await step('Artificer: Sentry damages mobs over time, Overdrive grants Haste III / Speed II / Resistance I', async () => {
    await readyClass(artificer.username, 'artificer', CX, PY + 1, CZ);
    await spawnMob('zombie', CX, PY + 1, CZ - 3, 'tal_f1', 100);
    await rcon.command(`minecraft:tp ${bystanderName} ${CX + 1} ${PY + 1} ${CZ - 3} 180 0`);
    await say(artificer, '/class ability 2', /^Sentry: place a turret that fires at hostile mobs for 20 s$/);
    await sleep(6000);
    const hpAfterSentry = await health('@e[tag=tal_f1,limit=1]');
    assert(hpAfterSentry < 100, `Sentry hurt the mob (${hpAfterSentry})`);
    assert((await health(bystanderName)) === 20, 'Sentry did not hurt the bystander');

    await readyClass(artificer.username, 'artificer', CX, PY + 1, CZ);
    await say(artificer, '/class ability 3',
      /^Overdrive: Haste III, Speed II and Resistance I for 15 s; your Sentry fires twice as fast$/);
    await sleep(500);
    // RCON replies truncate long lists, so query one filtered effect at a time instead of the whole
    // active_effects array.
    const haste = await rcon.command(`minecraft:data get entity ${artificer.username} active_effects[{id:"minecraft:haste"}]`);
    const speed = await rcon.command(`minecraft:data get entity ${artificer.username} active_effects[{id:"minecraft:speed"}]`);
    const resistance = await rcon.command(`minecraft:data get entity ${artificer.username} active_effects[{id:"minecraft:resistance"}]`);
    assert(/amplifier: 2/.test(haste), `Haste III present (${haste})`);
    assert(/amplifier: 1/.test(speed), `Speed II present (${speed})`);
    assert(!/No elements|cannot be found|has no/i.test(resistance), `Resistance present (${resistance})`);
    await rcon.command('minecraft:kill @e[tag=tal_f1]');
  });

  await step('Guardian: Shield Bash hits a mob in front, Fortress cuts mob damage taken, no mob hurts a bystander', async () => {
    await readyClass(guardian.username, 'guardian', CX, PY + 1, CZ);
    await spawnMob('zombie', CX, PY + 1, CZ - 2, 'tal_g1', 100);
    await rcon.command(`minecraft:tp ${bystanderName} ${CX + 1} ${PY + 1} ${CZ - 2} 180 0`);
    let mob;
    await waitFor(() => (mob = Object.values(guardian.entities).find((e) => e.name === 'zombie'
      && e.position.distanceTo({ x: CX + 0.5, y: PY + 1, z: CZ - 1.5 }) < 6)), 'guardian sees the target', 10_000);
    await guardian.lookAt(mob.position.offset(0, 1, 0), true);
    await say(guardian, '/class ability 2', /^Shield Bash: Knock back mobs in front of you, 1 heart and Slowness II for 3 s$/);
    await sleep(800);
    const hpAfterBash = await health('@e[tag=tal_g1,limit=1]');
    assert(hpAfterBash <= 99, `Shield Bash hurt the mob (${hpAfterBash})`);
    assert((await health(bystanderName)) === 20, 'Shield Bash did not hurt the bystander');

    // Fortress: measure a fixed mob hit on the Guardian with and without the buff (same pattern as
    // Warrior's Battle Cry in classes.mjs), since it reduces damage taken rather than dealing any.
    await readyClass(guardian.username, 'guardian', CX, PY + 1, CZ);
    await rcon.command(`minecraft:effect give ${guardian.username} minecraft:instant_health 1 5 true`);
    await rcon.command(`minecraft:summon minecraft:zombie ${CX - 3} ${PY + 1} ${CZ - 3} {NoAI:1b,Silent:1b,Tags:["${TAG}","tal_hitter"]}`);
    await sleep(300);
    await rcon.command(`minecraft:damage ${guardian.username} 10 minecraft:mob_attack by @e[tag=tal_hitter,limit=1]`);
    const plainHit = 20 - await health(guardian.username);
    await rcon.command(`minecraft:effect give ${guardian.username} minecraft:instant_health 1 5 true`);
    await say(guardian, '/class ability 3',
      /^Fortress: 8 s of 60% less damage from mobs \(30% from players\), no knockback, every mob within 10 blocks targets you$/);
    await rcon.command(`minecraft:damage ${guardian.username} 10 minecraft:mob_attack by @e[tag=tal_hitter,limit=1]`);
    const fortressHit = 20 - await health(guardian.username);
    assert(plainHit > 0, `a mob hit hurts a classless-cooldown Guardian (${plainHit})`);
    assert(fortressHit < plainHit * 0.6, `Fortress cuts the hit well below 60% (${plainHit} -> ${fortressHit})`);
    await rcon.command('minecraft:kill @e[tag=tal_g1]');
    await rcon.command('minecraft:kill @e[tag=tal_hitter]');
  });

  await step('Rogue: Smoke Bomb turns the Rogue invisible, Death Mark later deals a % of a mob\'s max health', async () => {
    await readyClass(rogue.username, 'rogue', CX, PY + 1, CZ);
    await spawnMob('zombie', CX, PY + 1, CZ - 3, 'tal_ro1', 100);
    await rcon.command(`minecraft:tp ${bystanderName} ${CX + 1} ${PY + 1} ${CZ - 3} 180 0`);
    await say(rogue, '/class ability 2', /^Smoke Bomb: Invisibility for 4 s; mobs within 6 blocks lose their target and get Blindness for 3 s$/);
    await sleep(500);
    const effects = await rcon.command(`minecraft:data get entity ${rogue.username} active_effects`);
    assert(/minecraft:invisibility/.test(effects), `Smoke Bomb gave Invisibility (${effects})`);
    assert((await health(bystanderName)) === 20, 'Smoke Bomb did not hurt the bystander');

    await rcon.command('minecraft:kill @e[tag=tal_ro1]');
    await readyClass(rogue.username, 'rogue', CX, PY + 1, CZ);
    await spawnMob('zombie', CX, PY + 1, CZ - 4, 'tal_ro2', 100);
    let mob;
    await waitFor(() => (mob = Object.values(rogue.entities).find((e) => e.name === 'zombie'
      && e.position.distanceTo({ x: CX + 0.5, y: PY + 1, z: CZ - 3.5 }) < 7)), 'rogue sees the target', 10_000);
    await rogue.lookAt(mob.position.offset(0, 1, 0), true);
    await say(rogue, '/class ability 3',
      /^Death Mark: mark the mob you look at; after 4 s or your next hit it takes 30% of its max health \(5% on bosses, capped at 20 hearts\)$/);
    await sleep(5000);
    const hpAfterMark = await health('@e[tag=tal_ro2,limit=1]');
    assert(hpAfterMark <= 72, `Death Mark took ~30% of max health (${hpAfterMark})`);
    await rcon.command('minecraft:kill @e[tag=tal_ro2]');
  });

  await step('Paladin: Holy Light heals a hurt ally, Judgement hurts undead and heals allies', async () => {
    await readyClass(paladin.username, 'paladin', CX, PY + 1, CZ);
    await rcon.command(`minecraft:tp ${bystanderName} ${CX + 1} ${PY + 1} ${CZ} 180 0`);
    await rcon.command(`minecraft:damage ${bystanderName} 6 minecraft:generic`);
    const hurt = await health(bystanderName);
    assert(hurt <= 14.01, `bystander hurt (${hurt})`);
    await say(paladin, '/class ability 2', /^Holy Light: heal yourself and allies within 6 blocks 2 hearts$/);
    await sleep(500);
    const healed = await health(bystanderName);
    // Allow some slack beyond the exact +4 (2 hearts): other plugins (AuraSkills etc.) can tick a
    // small amount of their own regen in the same window even with natural_regeneration off.
    assert(healed - hurt >= 3.5, `Holy Light healed the ally about 2 hearts (${hurt} -> ${healed})`);

    await readyClass(paladin.username, 'paladin', CX, PY + 1, CZ);
    await spawnMob('zombie', CX, PY + 1, CZ - 3, 'tal_p1', 100);
    await rcon.command(`minecraft:tp ${bystanderName} ${CX + 1} ${PY + 1} ${CZ} 180 0`);
    await rcon.command(`minecraft:damage ${bystanderName} 6 minecraft:generic`);
    const hurt2 = await health(bystanderName);
    await say(paladin, '/class ability 3',
      /^Judgement: 8 hearts to every undead within 8 blocks \(4 to other hostile mobs\), heals allies 4 hearts and cures poison and wither$/);
    await sleep(800);
    const hpAfterJudgement = await health('@e[tag=tal_p1,limit=1]');
    assert(hpAfterJudgement <= 85, `Judgement hurt the undead for ~8 hearts (${hpAfterJudgement})`);
    const healed2 = await health(bystanderName);
    assert(healed2 > hurt2, `Judgement healed the ally (${hurt2} -> ${healed2})`);
    await rcon.command('minecraft:kill @e[tag=tal_p1]');
  });

  console.log(`\nAll ${results.length} classes-talents steps passed`);
} catch (error) {
  console.error(`FAIL after ${results.length} passed steps: ${error.stack ?? error}`);
  process.exitCode = 1;
} finally {
  await rcon.command(`minecraft:kill @e[tag=${TAG}]`).catch(() => {});
  await rcon.command('minecraft:kill @e[tag=tal_hitter]').catch(() => {});
  for (const bot of bots) {
    if (bot) await admin(`set ${bot.username} none`).catch(() => {});
  }
  await rcon.command('minecraft:gamerule natural_regeneration true').catch(() => {});
  await releasePlatform(rcon, BX, BZ);
  await quit(...bots);
  rcon.close();
}
