import {
  Rcon, assert, buildPlatform, createBot, nbtText, quit, randomOrigin, releasePlatform, sleep, teleport, waitFor,
  waitForLine,
} from './lib.mjs';

// The Adventurer's Guild (ImMijo's Shenron wish): a stranger registers from /guild for the 500-coin fee
// (into the Treasury), claims a finished daily (paid in full, with a 5% commission minted on top for
// the Treasury), works a Guildmaster commission by killing a mob, and the Guildmaster withdraws from
// his office and announces. The suite makes its own bot the Guildmaster (`guildadmin guildmaster`).
const PLATFORM_Y = 200;
const { bx: BX, bz: BZ } = randomOrigin();
// Fresh names each run: the e2e server keeps guild.yml, so a reused name would already be a member
// and its Guildmaster would already have spent this week's commissions.
const RUN = String(Math.floor(Math.random() * 10_000)).padStart(4, '0');
const GM = `GuildGM${RUN}`;
const HERO = `GuildHero${RUN}`;

// Hand-in contracts an E-Rank board can draw, and what to give the bot to finish them.
const HAND_INS = {
  e_logs: ['oak_log 64'],
  e_bread: ['bread 16'],
  e_torches: ['torch 64'],
  e_wool: ['white_wool 24'],
};

const rcon = await Rcon.connect();
const bots = [];
const results = [];
async function step(name, fn) {
  await fn();
  results.push(name);
  console.log(`PASS ${name}`);
}

const plain = (s) => s.replace(/§./g, '');
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

async function balance(name) {
  const out = plain(await rcon.command(`balance ${name}`));
  const m = out.match(/\$([\d,]+(?:\.\d+)?)/);
  assert(m, `balance readable (got ${out.trim()})`);
  return Number(m[1].replace(/,/g, ''));
}

async function treasury() {
  const out = await rcon.command('guildadmin status');
  const m = out.match(/treasury (-?\d+)/);
  assert(m, `treasury readable (got ${out})`);
  return Number(m[1]);
}

async function member(name) {
  return plain(await rcon.command(`guildadmin member ${name}`));
}

try {
  await buildPlatform(rcon, BX, BZ, PLATFORM_Y);
  const gm = await createBot(GM);
  const hero = await createBot(HERO);
  bots.push(gm, hero);
  for (const bot of bots) {
    await rcon.command(`minecraft:gamemode survival ${bot.username}`);
    await rcon.command(`minecraft:clear ${bot.username}`);
  }
  await rcon.command(`guildadmin guildmaster ${GM}`);
  await teleport(rcon, gm, BX, PLATFORM_Y + 1, BZ);
  await teleport(rcon, hero, BX + 3, PLATFORM_Y + 1, BZ);
  await rcon.command(`eco set ${HERO} 600`);
  await rcon.command(`eco set ${GM} 1000`);

  let menu;
  await step('a stranger opens /guild and sees the registration desk', async () => {
    const opened = nextWindow(hero);
    hero.chat('/guild');
    menu = await opened;
    assert(/Adventurer's Guild/.test(titleOf(menu)), `title (got ${titleOf(menu)})`);
    assert(menu.slots[22]?.name === 'name_tag', `register button at slot 22 (got ${menu.slots[22]?.name})`);
  });

  let treasuryAfterFee;
  await step('registering takes 500 coins into the Treasury and starts at E-Rank', async () => {
    const before = await treasury();
    await hero.clickWindow(22, 0, 0);
    await waitFor(async () => (await balance(HERO)) === 100, 'hero paid 500', 10_000);
    treasuryAfterFee = await treasury();
    assert(treasuryAfterFee === before + 500, `treasury +500 (was ${before}, now ${treasuryAfterFee})`);
    assert(/: E,/.test(await member(HERO)), 'hero is E-Rank');
    hero.closeWindow(menu);
  });

  await step('a finished daily pays its advertised coins and mints the commission on top', async () => {
    const info = await member(HERO);
    const ids = [...info.matchAll(/Board [^:]*:(.*?)\|/g)][0][1].trim().split(' ').map((s) => s.split('[')[0]);
    assert(ids.length === 3, `three dailies (got ${ids})`);
    const slot = 1;
    for (const give of HAND_INS[ids[slot]] ?? []) {
      await rcon.command(`minecraft:give ${HERO} minecraft:${give}`);
    }
    await rcon.command(`guildadmin complete ${HERO} ${slot}`);
    const before = await balance(HERO);
    const treasuryBefore = await treasury();
    const opened = nextWindow(hero);
    hero.chat('/guild');
    menu = await opened;
    const since = hero.lines.length;
    await hero.clickWindow(21, 0, 0);
    const paidLine = await waitForLine(hero, /Contract complete/, 10_000, since);
    const coins = Number(paidLine.match(/Paid: (\d+) coins/)[1]);
    await waitFor(async () => (await balance(HERO)) === before + coins, `hero paid ${coins}`, 10_000);
    const cut = Math.max(1, Math.round(coins * 0.05));
    assert((await treasury()) === treasuryBefore + cut, `treasury got the ${cut}-coin commission`);
    hero.closeWindow(menu);
  });

  await step('a Guildmaster commission is escrowed, worked by a kill and paid from the Treasury', async () => {
    const before = await treasury();
    const since = gm.lines.length;
    gm.chat('/guild commission hunt zombie 1 50 E');
    await waitForLine(gm, /Commission posted/, 10_000, since);
    assert((await treasury()) === before - 50, 'reward escrowed');

    await rcon.command(`minecraft:summon minecraft:zombie ${BX + 5} ${PLATFORM_Y + 1} ${BZ} {NoAI:1b,Health:1f,PersistenceRequired:1b,Tags:["guild_e2e"],equipment:{head:{id:"minecraft:leather_helmet",count:1}}}`);
    let zombie;
    // At night the world is full of zombies; take the one summoned on the platform.
    const spot = { x: BX + 5.5, y: PLATFORM_Y + 1, z: BZ + 0.5 };
    await waitFor(() => (zombie = Object.values(hero.entities)
      .find((e) => e.name === 'zombie' && e.position.distanceTo(spot) < 1.5)), 'hero sees the zombie');
    const heroSince = hero.lines.length;
    // A helmet keeps it from burning in daylight, which would leave no killer to credit.
    for (let i = 0; i < 5 && hero.entities[zombie.id]; i++) {
      await hero.lookAt(zombie.position.offset(0, 1, 0));
      hero.attack(zombie);
      await sleep(700);
    }
    await waitForLine(hero, /commission/i, 10_000, heroSince);

    const heroBefore = await balance(HERO);
    const opened = nextWindow(hero);
    hero.chat('/guild commissions');
    menu = await opened;
    const slot = menu.slots.findIndex((s, i) => i < 54 && s?.name === 'filled_map');
    assert(slot >= 0, 'the commission is on the board');
    await hero.clickWindow(slot, 0, 0);
    await waitFor(async () => (await balance(HERO)) === heroBefore + 50, 'hero paid 50 from escrow', 10_000);
    assert((await treasury()) === before - 50, 'nothing minted for a commission');
    hero.closeWindow(menu);
  });

  // Farm rules (docs/guild.md): a mob that took most of its damage from a fall, like a grinder's drop,
  // earns nothing even when the player lands the last hit; a fresh mob killed by the player still counts.
  await step('a zombie softened by a fall earns nothing, a fought one counts', async () => {
    const since = gm.lines.length;
    gm.chat('/guild commission hunt zombie 1 50 E');
    await waitForLine(gm, /Commission posted/, 10_000, since);

    // One at a time on the commission step's spot, within the hero's reach.
    const summon = async (tag, health) => {
      await rcon.command(`minecraft:summon minecraft:zombie ${BX + 5} ${PLATFORM_Y + 1} ${BZ} {NoAI:1b,Health:${health}f,PersistenceRequired:1b,Tags:["guild_e2e","${tag}"],equipment:{head:{id:"minecraft:leather_helmet",count:1}}}`);
      const spot = { x: BX + 5.5, y: PLATFORM_Y + 1, z: BZ + 0.5 };
      let zombie;
      await waitFor(() => (zombie = Object.values(hero.entities)
        .find((e) => e.name === 'zombie' && e.position.distanceTo(spot) < 1.5)), 'hero sees the zombie');
      return zombie;
    };
    const slay = async (zombie) => {
      for (let i = 0; i < 10 && hero.entities[zombie.id]; i++) {
        await hero.lookAt(zombie.position.offset(0, 1, 0));
        hero.attack(zombie);
        await sleep(700);
      }
      assert(!hero.entities[zombie.id], 'the zombie died');
    };

    const grinder = await summon('guild_e2e_grinder', 20);
    const hurt = await rcon.command(`minecraft:damage @e[tag=guild_e2e_grinder,limit=1] 19 minecraft:fall`);
    assert(/Applied 19/.test(hurt), `fall damage applied (${hurt.trim()})`);
    let heroSince = hero.lines.length;
    await slay(grinder);
    await sleep(3_000);
    assert(!hero.lines.slice(heroSince).some((l) => /commission/i.test(l)), 'the grinder kill made no commission progress');

    const fought = await summon('guild_e2e_fought', 1);
    heroSince = hero.lines.length;
    await slay(fought);
    await waitForLine(hero, /commission/i, 10_000, heroSince);
  });

  await step('an op rerolls the Grand Quest and the server is told', async () => {
    const since = hero.lines.length;
    const out = plain(await rcon.command('guildadmin grandquest reroll'));
    assert(/Voided Grand Quest .*; this week's is now gq_/.test(out), `reroll reply (got ${out.trim()})`);
    await waitForLine(hero, /The Guild has set a new Grand Quest for this week/, 5_000, since);
  });

  await step('the Guildmaster withdraws from his office', async () => {
    const before = await balance(GM);
    const treasuryBefore = await treasury();
    const opened = nextWindow(gm);
    gm.chat('/guild office');
    menu = await opened;
    assert(/Guildmaster's Office/.test(titleOf(menu)), `office title (got ${titleOf(menu)})`);
    await gm.clickWindow(20, 0, 0);
    await waitFor(async () => (await balance(GM)) === before + 100, 'GM drew 100', 10_000);
    assert((await treasury()) === treasuryBefore - 100, 'treasury -100');
    gm.closeWindow(menu);
  });

  await step('a member has no office and nobody else can announce', async () => {
    const since = hero.lines.length;
    hero.chat('/guild office');
    await waitForLine(hero, /Only the Guildmaster has an office/, 5_000, since);
    hero.chat('/guild announce hello');
    await waitForLine(hero, /Only the Guildmaster makes Guild announcements/, 5_000, since);
  });

  await step('the Guildmaster announces to the server', async () => {
    await rcon.command('guildadmin resetannounce'); // the cooldown is server-wide and an earlier run may have used it
    const since = hero.lines.length;
    gm.chat('/guild announce The Guild is open for business');
    await waitForLine(hero, new RegExp(`Guildmaster ${GM}: The Guild is open for business`), 5_000, since);
  });

  await step('the board shows the server bounty and Grand Quest, and the Hall of Fame opens', async () => {
    let opened = nextWindow(hero);
    hero.chat('/guild');
    menu = await opened;
    assert(menu.slots[11] && menu.slots[13], 'bounty and Grand Quest icons on the board');
    hero.closeWindow(menu);
    opened = nextWindow(hero);
    hero.chat('/guild halloffame');
    menu = await opened;
    assert(/Hall of Fame/.test(titleOf(menu)), `hall of fame title (got ${titleOf(menu)})`);
    hero.closeWindow(menu);
  });

  // The staff stand wherever ImMijo asks in the hall he builds; the suite puts Lyra on its platform.
  const LYRA = { x: BX + 0.5, y: PLATFORM_Y + 1, z: BZ + 4.5 };
  await step('the receptionist is placed by console command at a given spot', async () => {
    await rcon.command(`guildadmin staff place receptionist world ${LYRA.x} ${LYRA.y} ${LYRA.z} 180`);
    await waitFor(async () => /receptionist \(Lyra Fennwick, Guild Receptionist\): present/
      .test(await rcon.command('guildadmin staff list')), 'Lyra present', 20_000);
  });

  await step('right-clicking the receptionist gets a word from Lyra and the Guild board', async () => {
    await teleport(rcon, hero, BX, PLATFORM_Y + 1, BZ + 2);
    let lyra;
    await waitFor(() => (lyra = Object.values(hero.entities)
      .find((e) => e.name === 'villager' && e.position.distanceTo(LYRA) < 1)), 'hero sees Lyra');
    const since = hero.lines.length;
    const opened = nextWindow(hero);
    await hero.activateEntity(lyra);
    menu = await opened;
    assert(/Adventurer's Guild/.test(titleOf(menu)), `board title (got ${titleOf(menu)})`);
    await waitForLine(hero, /^Lyra: /, 5_000, since);
    hero.closeWindow(menu);
  });

  await step('inside a guarded hall box a member cannot break blocks', async () => {
    await rcon.command(`guildadmin hall guard world ${BX - 8} ${PLATFORM_Y - 2} ${BZ - 8} ${BX + 8} ${PLATFORM_Y + 10} ${BZ + 8}`);
    const floor = hero.blockAt(hero.entity.position.offset(0, -1, 0));
    assert(floor && floor.name !== 'air', `standing on the platform (got ${floor?.name})`);
    await Promise.race([hero.dig(floor, true).catch(() => {}), sleep(6_000)]);
    await sleep(500);
    const after = await rcon.command(`minecraft:execute if block ${floor.position.x} ${floor.position.y} ${floor.position.z} minecraft:${floor.name}`);
    assert(/Test passed/.test(after), `floor block still there (${after})`);
  });

  console.log(`\nGuild e2e: ${results.length} passed`);
} catch (error) {
  console.error(`FAIL after ${results.length} passed: ${error.message}`);
  process.exitCode = 1;
} finally {
  await rcon.command('minecraft:kill @e[tag=guild_e2e]').catch(() => {});
  await rcon.command('guildadmin staff remove receptionist').catch(() => {});
  await rcon.command('guildadmin hall guard off').catch(() => {});
  await releasePlatform(rcon, BX, BZ);
  await quit(...bots);
  await sleep(200);
  rcon.close();
}
