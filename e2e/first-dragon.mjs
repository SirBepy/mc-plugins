import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import {
  Rcon, assert, createBot, quit, sleep, waitFor, waitForLine,
} from './lib.mjs';

// The Guild's one-time First Dragon event: the first Ender Dragon to die in the End pays every Guild
// member who hurt it 300 coins + 50 reputation (no contract count) and the secret Dragonslayer title,
// lists them in the Hall of Fame and announces them. Fighters: HERO swings a sword at it, BOMBER sets
// off a bed beside it, AWAY hurts it (/damage ... by) and logs off before it dies, STRANGER hurts it
// without being a member, IDLE is a member who only stands in the End. A second dragon pays nobody.
//
// The dragons are summoned hovering in place (DragonPhase 10) over an obsidian platform far out in the
// End, away from the main island's own fight, so the bots can reach them; the death handler only asks
// that a dragon die in the End and not to an op's /kill (the cleanup's own /kill checks that). The suite clears the record first (guildadmin firstdragon reset), so it can rerun on one world.
const RUN = String(Math.floor(Math.random() * 10_000)).padStart(4, '0');
const HERO = `FdHero${RUN}`;
const BOMBER = `FdBomb${RUN}`;
const AWAY = `FdAway${RUN}`;
const IDLE = `FdIdle${RUN}`;
const STRANGER = `FdStr${RUN}`;
// Well clear of the main island (the natural fight tracks players within ~192 blocks of 0 0).
const X = 3000;
const Y = 100;
const Z = 3000;
const END = 'execute in minecraft:the_end run';
const GUILD_YML = fileURLToPath(new URL('../testserver/run-e2e/plugins/Guild/guild.yml', import.meta.url));

const rcon = await Rcon.connect();
const bots = {};
const results = [];
async function step(name, fn) {
  await fn();
  results.push(name);
  console.log(`PASS ${name}`);
}

const plain = (s) => s.replace(/§./g, '');

function nextWindow(bot, timeoutMs = 5_000) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(`${bot.username}: no window opened`)), timeoutMs);
    bot.once('windowOpen', (window) => {
      clearTimeout(timer);
      resolve(window);
    });
  });
}

async function balance(name) {
  const out = plain(await rcon.command(`balance ${name}`));
  const m = out.match(/\$([\d,]+(?:\.\d+)?)/);
  assert(m, `balance readable (got ${out.trim()})`);
  return Number(m[1].replace(/,/g, ''));
}

async function treasury() {
  const m = (await rcon.command('guildadmin status')).match(/treasury (-?\d+)/);
  assert(m, 'treasury readable');
  return Number(m[1]);
}

async function member(name) {
  return plain(await rcon.command(`guildadmin member ${name}`));
}

async function register(bot) {
  await rcon.command(`eco set ${bot.username} 500`);
  const opened = nextWindow(bot);
  bot.chat('/guild');
  const menu = await opened;
  await bot.clickWindow(22, 0, 0);
  await waitFor(async () => /: E,/.test(await member(bot.username)), `${bot.username} registered`, 10_000);
  bot.closeWindow(menu);
}

async function toEnd(bot, dx, dz) {
  await rcon.command(`${END} minecraft:tp ${bot.username} ${X + dx} ${Y + 1} ${Z + dz}`);
  await waitFor(() => bot.game.dimension?.includes('end') && Math.abs(bot.entity.position.x - (X + dx)) < 2,
    `${bot.username} in the End`, 20_000);
  await bot.waitForChunksToLoad().catch(() => {});
  await sleep(500);
}

async function summonDragon(tag) {
  await rcon.command(`${END} summon minecraft:ender_dragon ${X} ${Y + 3} ${Z} {DragonPhase:10,Tags:["fd_e2e","${tag}"]}`);
  await waitFor(async () => /passed/.test(await rcon.command(`${END} execute if entity @e[tag=${tag}]`)), `${tag} summoned`);
}

const dragon = (tag) => `@e[type=minecraft:ender_dragon,tag=${tag},limit=1]`;

async function fighters(tag) {
  return rcon.command(`${END} data get entity ${dragon(tag)} BukkitValues."guild:dragon-fighters"`);
}

try {
  await rcon.command(`${END} forceload add ${X - 32} ${Z - 32} ${X + 32} ${Z + 32}`);
  await rcon.command(`${END} fill ${X - 12} ${Y} ${Z - 12} ${X + 12} ${Y} ${Z + 12} minecraft:obsidian`);
  await rcon.command(`${END} fill ${X - 12} ${Y + 1} ${Z - 12} ${X + 12} ${Y + 12} ${Z + 12} minecraft:air`);
  await rcon.command(`${END} minecraft:kill @e[tag=fd_e2e]`);
  await rcon.command('guildadmin firstdragon reset');

  for (const name of [HERO, BOMBER, AWAY, IDLE, STRANGER]) {
    bots[name] = await createBot(name);
    await rcon.command(`minecraft:gamemode creative ${name}`);
    await rcon.command(`minecraft:clear ${name}`);
  }
  for (const name of [HERO, BOMBER, AWAY, IDLE]) {
    await register(bots[name]);
  }
  await rcon.command(`eco set ${STRANGER} 0`);

  await step('the event is armed before any dragon dies', async () => {
    assert(/armed/.test(await rcon.command('guildadmin firstdragon')), 'status says armed');
  });

  await step("an op's /kill of a dragon in the End does not spend the event", async () => {
    await summonDragon('fd_killed');
    await rcon.command(`${END} minecraft:kill ${dragon('fd_killed')}`);
    await sleep(1_000);
    assert(/armed/.test(await rcon.command('guildadmin firstdragon')), 'still armed');
  });

  await step('a sword hit, a bed blast and a /damage by a player each make a fighter', async () => {
    await toEnd(bots[IDLE], -10, -10);
    await toEnd(bots[STRANGER], 10, -10);
    await toEnd(bots[AWAY], -10, 10);
    await toEnd(bots[HERO], 0, 5);
    await summonDragon('fd_first');

    const hero = bots[HERO];
    const target = Object.values(hero.entities).find((e) => e.name === 'ender_dragon');
    assert(target, 'HERO sees the dragon');
    await hero.lookAt(target.position.offset(0, 2, 0));
    hero.attack(target);
    await waitFor(async () => /[0-9a-f]{8}-/.test(await fighters('fd_first')), 'HERO recorded on the dragon', 5_000);

    // The dragon shrugs off hits for half a second after each one (vanilla hurt cooldown), so space them.
    await sleep(1_000);
    await toEnd(bots[BOMBER], 6, 0);
    await rcon.command(`${END} setblock ${X + 4} ${Y + 1} ${Z} minecraft:red_bed[facing=west,part=head]`);
    await rcon.command(`${END} setblock ${X + 5} ${Y + 1} ${Z} minecraft:red_bed[facing=west,part=foot]`);
    await sleep(500);
    await bots[BOMBER].activateBlock(bots[BOMBER].blockAt(bots[BOMBER].entity.position.clone().set(X + 5, Y + 1, Z)));

    await sleep(1_000);
    await rcon.command(`${END} damage ${dragon('fd_first')} 2 minecraft:arrow by ${AWAY}`);
    await sleep(1_000);
    await rcon.command(`${END} damage ${dragon('fd_first')} 2 minecraft:arrow by ${STRANGER}`);
    await sleep(1_000);

    const tally = await fighters('fd_first');
    console.log(`  tally: ${tally.trim()}`);
    const ids = [...tally.matchAll(/[0-9a-f]{8}-[0-9a-f-]{27}/g)].map((m) => m[0]);
    const who = ids.map((id) => Object.values(bots).find((b) => b.player?.uuid === id)?.username ?? id);
    assert(JSON.stringify(who) === JSON.stringify([HERO, BOMBER, AWAY, STRANGER]),
      `HERO, BOMBER, AWAY, STRANGER recorded in that order, IDLE not (got ${who})`);
  });

  await step('AWAY logs off before the kill', async () => {
    bots[AWAY].quit();
    delete bots[AWAY];
    await sleep(1_000);
  });

  const before = {};
  for (const name of [HERO, BOMBER, AWAY, IDLE, STRANGER]) before[name] = await balance(name);
  const treasuryBefore = await treasury();

  await step('the first dragon dies: its Guild fighters are announced, nobody else', async () => {
    const since = bots[IDLE].lines.length;
    await rcon.command(`${END} damage ${dragon('fd_first')} 1000 minecraft:player_attack by ${HERO}`);
    const line = await waitForLine(bots[IDLE], /first Ender Dragon has fallen/, 40_000, since);
    console.log(`  ${line}`);
    for (const name of [HERO, BOMBER, AWAY]) assert(line.includes(name), `${name} named`);
    for (const name of [IDLE, STRANGER]) assert(!line.includes(name), `${name} not named`);
  });

  await step('each fighter is paid 300 coins outside the caps, with the commission minted on top', async () => {
    await waitFor(async () => (await balance(HERO)) === before[HERO] + 300, 'HERO paid');
    assert((await balance(BOMBER)) === before[BOMBER] + 300, 'BOMBER paid');
    assert((await balance(AWAY)) === before[AWAY] + 300, 'AWAY paid while offline');
    assert((await balance(IDLE)) === before[IDLE], 'IDLE not paid');
    assert((await balance(STRANGER)) === before[STRANGER], 'STRANGER not paid');
    assert((await treasury()) === treasuryBefore + 45, `treasury +3 x 15 commission (was ${treasuryBefore})`);
  });

  await step('+50 reputation, contracts unchanged, the secret title, Hall of Fame record', async () => {
    const hero = await member(HERO);
    assert(/rep 50, contracts 0/.test(hero), `HERO rep 50, contracts 0 (got ${hero})`);
    assert(/rep 0,/.test(await member(IDLE)), 'IDLE rep 0');
    const status = await rcon.command('guildadmin firstdragon');
    console.log(`  ${status.trim()}`);
    assert(status.includes(`party ${HERO}, ${BOMBER}, ${AWAY}`), 'party in the order they first hurt it');
    await waitForLine(bots[HERO], /Hidden achievement: "Dragonslayer"/, 5_000, 0);
    const yml = readFileSync(GUILD_YML, 'utf8');
    const heroBlock = yml.slice(yml.indexOf(`name: ${HERO}`));
    assert(/dragonslayer/.test(heroBlock.slice(0, heroBlock.indexOf('name: ', 10))), 'title saved for HERO');

    const opened = nextWindow(bots[HERO]);
    bots[HERO].chat('/guild halloffame');
    const hall = await opened;
    const icon = hall.slots[22];
    assert(icon?.name === 'dragon_head', `Hall of Fame slot 22 is a dragon head (got ${icon?.name})`);
    const lore = JSON.stringify(icon.components ?? icon.nbt ?? {});
    for (const name of [HERO, BOMBER, AWAY]) assert(lore.includes(name), `${name} in the Hall of Fame lore`);
    bots[HERO].closeWindow(hall);
  });

  await step('AWAY is told on the next join', async () => {
    bots[AWAY] = await createBot(AWAY);
    await waitForLine(bots[AWAY], /While you were away/, 10_000, 0);
  });

  await step('a second dragon pays nobody', async () => {
    await summonDragon('fd_second');
    const idle = await balance(IDLE);
    const since = bots[HERO].lines.length;
    await rcon.command(`${END} damage ${dragon('fd_second')} 5 minecraft:player_attack by ${IDLE}`);
    await rcon.command(`${END} damage ${dragon('fd_second')} 1000 minecraft:player_attack by ${IDLE}`);
    await waitFor(async () => /failed/.test(await rcon.command(`${END} execute if entity ${dragon('fd_second')}`)),
      'the second dragon is gone', 40_000);
    await sleep(1_000);
    assert(!bots[HERO].lines.slice(since).some((l) => /first Ender Dragon/.test(l)), 'no second announcement');
    assert((await balance(IDLE)) === idle, 'IDLE not paid for the second dragon');
    assert(/rep 0,/.test(await member(IDLE)), 'IDLE still rep 0');
  });

  console.log(`\nfirst-dragon: ${results.length}/${results.length} passed`);
} catch (error) {
  console.error(`FAIL after ${results.length} steps: ${error.stack ?? error}`);
  process.exitCode = 1;
} finally {
  await rcon.command(`${END} minecraft:kill @e[tag=fd_e2e]`).catch(() => {});
  await rcon.command('guildadmin firstdragon reset').catch(() => {});
  await rcon.command(`${END} forceload remove ${X - 32} ${Z - 32} ${X + 32} ${Z + 32}`).catch(() => {});
  await quit(...Object.values(bots));
  await sleep(200);
  rcon.close();
}
