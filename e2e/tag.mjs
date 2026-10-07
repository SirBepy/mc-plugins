import { Vec3 } from 'vec3';
import {
  Rcon, assert, buildPlatform, createBot, quit, randomOrigin, releasePlatform, sleep, teleport,
  waitFor, waitForLine,
} from './lib.mjs';

// Three bots stand in a row on a fresh platform. PvPManager gives new players spawn PvP protection,
// so every tag here also proves tagging works with PvP off.
const PLATFORM_Y = 200;
const { bx: BX, bz: BZ } = randomOrigin();
const HEAD_SLOT = 5;
const PREFIX = '☣';

const rcon = await Rcon.connect();
const bots = {};

const results = [];
async function step(name, fn) {
  await fn();
  results.push(name);
  console.log(`PASS ${name}`);
}

function headItem(bot) {
  return bot.inventory.slots[HEAD_SLOT];
}

async function whoIsIt() {
  const reply = await rcon.command('tag who');
  return reply.replace(/§./g, '').trim();
}

async function stats(name) {
  const text = (await rcon.command(`tag stats ${name}`)).replace(/§./g, '');
  const num = (label) => Number(new RegExp(`${label}: ([0-9.]+)`).exec(text)?.[1] ?? 'NaN');
  return { points: num('points'), tagged: num('tagged'), made: num('tags made'), text };
}

// A blocked tag (tagback, opted out) is a real PvP hit wherever PvP is on (Towny wilderness), so
// PvPManager can hold both players in combat for 15s and swallow their commands: retry until heard.
async function chatUntil(bot, command, pattern) {
  const mark = bot.lines.length;
  await waitFor(async () => {
    bot.chat(command);
    await sleep(1_000);
    return bot.lines.slice(mark).some((l) => pattern.test(l));
  }, `${bot.username} got ${pattern} for ${command}`, 25_000);
}

async function hit(attacker, victim) {
  const target = attacker.players[victim.username]?.entity;
  assert(target, `${attacker.username} can see ${victim.username}`);
  await attacker.lookAt(target.position.offset(0, 1.6, 0), true);
  attacker.attack(target);
  await sleep(600);
}

try {
  await buildPlatform(rcon, BX, BZ, PLATFORM_Y);
  await rcon.command('tag clear');

  for (const [name, dx] of [['TagA', 0], ['TagB', 2], ['TagC', 4]]) {
    const bot = await createBot(name);
    bots[name] = bot;
    await rcon.command(`minecraft:gamemode survival ${name}`);
    await rcon.command(`minecraft:clear ${name}`);
    await rcon.command(`minecraft:effect clear ${name}`);
    await teleport(rcon, bot, BX + dx, PLATFORM_Y + 1, BZ);
    bot.chat('/tag optin');
  }
  const { TagA, TagB, TagC } = bots;
  await sleep(1000);
  // Stats persist across runs, so assertions compare against this baseline.
  const before = { TagB: await stats('TagB') };

  await step('admin makes TagA IT; everyone is told', async () => {
    const mark = TagB.lines.length;
    await rcon.command('tag set TagA');
    await waitForLine(TagB, /made TagA IT/, 5_000, mark);
    assert((await whoIsIt()).includes('TagA is IT'), 'tag who reports TagA');
  });

  await step('IT wears the head item', async () => {
    await waitFor(() => headItem(TagA)?.name === 'player_head', 'TagA head slot is a player head');
  });

  await step('IT prefix shows in chat and the tab list', async () => {
    const mark = TagB.lines.length;
    TagA.chat('hello from IT');
    const line = await waitForLine(TagB, /hello from IT/, 5_000, mark);
    assert(line.includes(PREFIX) && line.includes('TagA'), `chat line has prefix: ${line}`);
    await waitFor(() => TagB.players.TagA?.displayName?.toString().includes(PREFIX), 'tab entry has prefix');
  });

  await step('floating nametag label exists above IT for other players', async () => {
    await waitFor(() => Object.values(TagB.entities).some((e) => e.name === 'text_display'
      && e.position.distanceTo(TagA.entity.position) < 4), 'TagB sees a text_display near TagA', 8_000);
  });

  await step('crouching hides the floating label', async () => {
    TagA.setControlState('sneak', true);
    await waitFor(() => !Object.values(TagB.entities).some((e) => e.name === 'text_display'
      && e.position.distanceTo(TagA.entity.position) < 4), 'label gone while sneaking', 5_000);
    TagA.setControlState('sneak', false);
    await waitFor(() => Object.values(TagB.entities).some((e) => e.name === 'text_display'
      && e.position.distanceTo(TagA.entity.position) < 4), 'label back after standing', 5_000);
  });

  await rcon.command('minecraft:give TagB minecraft:iron_helmet 1');
  await waitFor(() => TagB.inventory.items().some((i) => i.name === 'iron_helmet'), 'TagB got a helmet');
  await TagB.equip(TagB.inventory.items().find((i) => i.name === 'iron_helmet'), 'head');
  await waitFor(() => headItem(TagB)?.name === 'iron_helmet', 'TagB wears iron helmet');

  await step('IT tags TagB with PvP protection on, dealing no damage', async () => {
    const mark = TagC.lines.length;
    const healthBefore = TagB.health;
    await hit(TagA, TagB);
    await waitForLine(TagC, /TagA tagged TagB/, 5_000, mark);
    assert((await whoIsIt()).includes('TagB is IT'), 'TagB is IT now');
    await sleep(500);
    assert(TagB.health === healthBefore, `no damage (${healthBefore} -> ${TagB.health})`);
  });

  await step('head moves to TagB, helmet goes to inventory, TagA head removed', async () => {
    await waitFor(() => headItem(TagB)?.name === 'player_head', 'TagB wears the head');
    try {
      await waitFor(() => TagB.inventory.items().some((i) => i.name === 'iron_helmet'), 'iron helmet in inventory');
    } catch (error) {
      // This step has been seen to flake; dump where the helmet actually ended up (ender chest,
      // dropped on the ground, or never marked at all) so the next failure carries evidence
      // instead of a bare timeout.
      const safe = (cmd) => rcon.command(cmd).catch((e) => `(${e.message})`);
      const dump = [
        await safe('data get entity TagB Inventory'),
        await safe('data get entity TagB EnderItems'),
        await safe('tag who'),
      ];
      console.error(`  bot view: head=${headItem(TagB)?.name} items=${TagB.inventory.items().map((i) => i.name)}`);
      console.error(`Diagnostics for failed helmet relocation:\n  Inventory: ${dump[0]}\n  EnderItems: ${dump[1]}\n  whoIsIt: ${dump[2]}`);
      throw error;
    }
    await waitFor(() => headItem(TagA) == null, 'TagA head slot empty');
    assert(!TagA.inventory.items().some((i) => i.name === 'player_head'), 'TagA has no stray head');
  });

  await step('prefix moved from TagA to TagB', async () => {
    await waitFor(() => TagC.players.TagB?.displayName?.toString().includes(PREFIX), 'TagB tab prefix');
    await waitFor(() => !TagC.players.TagA?.displayName?.toString().includes(PREFIX), 'TagA tab prefix gone');
  });

  await step('no tagback: TagB cannot tag TagA right away, and is told how long is left', async () => {
    const mark = TagB.lines.length;
    await hit(TagB, TagA);
    await waitForLine(TagB, /can't tag TagA back for another \d+s/, 5_000, mark);
    assert((await whoIsIt()).includes('TagB is IT'), 'TagB still IT');
  });

  await step('the head cannot be taken off', async () => {
    await TagB.clickWindow(HEAD_SLOT, 0, 0).catch(() => {});
    await sleep(800);
    if (TagB.inventory.selectedItem || TagB.currentWindow) TagB.closeWindow(TagB.currentWindow ?? TagB.inventory);
    await sleep(500);
    assert(headItem(TagB)?.name === 'player_head', 'head still worn');
    const heads = TagB.inventory.items().filter((i) => i.name === 'player_head');
    assert(heads.length === 0, `no head copied into inventory (found ${heads.length})`);
  });

  await step('but TagB can tag someone else immediately', async () => {
    const mark = TagA.lines.length;
    await hit(TagB, TagC);
    await waitForLine(TagA, /TagB tagged TagC/, 5_000, mark);
  });

  await step('old helmet goes back on when TagB stops being IT', async () => {
    await waitFor(() => headItem(TagB)?.name === 'iron_helmet', 'iron helmet re-equipped');
    assert(!TagB.inventory.items().some((i) => i.name === 'player_head'), 'no head left on TagB');
  });

  await step('opted-out players cannot be tagged', async () => {
    await chatUntil(TagA, '/tag optout', /opted out of Tag/);
    await hit(TagC, TagA);
    await sleep(500);
    assert((await whoIsIt()).includes('TagC is IT'), 'TagC still IT');
    await chatUntil(TagA, '/tag optin', /opted back into Tag/);
  });

  await step('IT cannot opt out', async () => {
    await chatUntil(TagC, '/tag optout', /You are IT and cannot opt out/);
  });

  // Vanilla /tp is a COMMAND-cause teleport, the same cause /home, /spawn and /tpa use.
  await step('players near IT cannot command-teleport away', async () => {
    const mark = TagA.lines.length;
    const start = TagA.entity.position.clone();
    await rcon.command(`minecraft:tp TagA ${BX + 6} ${PLATFORM_Y + 1} ${BZ + 6}`);
    await waitForLine(TagA, /within 100 blocks of IT \(TagC\), so you can't teleport away/, 5_000, mark);
    await sleep(500);
    assert(TagA.entity.position.distanceTo(start) < 1, `TagA stayed put (${start} -> ${TagA.entity.position})`);
  });

  await step('opted-out players near IT can teleport', async () => {
    await chatUntil(TagA, '/tag optout', /opted out of Tag/);
    await teleport(rcon, TagA, BX + 6, PLATFORM_Y + 1, BZ + 6);
    await teleport(rcon, TagA, BX, PLATFORM_Y + 1, BZ);
    await chatUntil(TagA, '/tag optin', /opted back into Tag/);
  });

  await step('stats count tags and the +10 tag penalty', async () => {
    const after = await stats('TagB');
    assert(after.tagged === before.TagB.tagged + 1, `tagged +1: ${after.text}`);
    assert(after.made === before.TagB.made + 1, `tags made +1: ${after.text}`);
    assert(after.points >= before.TagB.points + 10, `points +10: ${after.text}`);
  });

  await step('sidebar board toggles on', async () => {
    const mark = TagC.lines.length;
    TagC.chat('/tag board');
    await waitForLine(TagC, /sidebar enabled/, 5_000, mark);
    await waitFor(() => TagC.scoreboard?.sidebar != null, 'sidebar objective shown');
    TagC.chat('/tag board');
  });

  await step('IT gains points every minute while others are online', async () => {
    const points = async () => (await stats('TagC')).points;
    const start = await points();
    await waitFor(async () => (await points()) > start, 'TagC points increased', 75_000);
  });

  console.log(`\nTag e2e: ${results.length} passed`);
} catch (error) {
  console.error(`FAIL after ${results.length} passed: ${error.message}`);
  process.exitCode = 1;
} finally {
  await releasePlatform(rcon, BX, BZ);
  await quit(...Object.values(bots));
  rcon.close();
}
