import {
  Rcon, assert, buildPlatform, createBot, quit, randomOrigin, releasePlatform, sleep, teleport,
  textDisplays, waitFor, waitForLine,
} from './lib.mjs';

// The in-world leaderboard: LbA (temporarily op) places it where it stands, LbB looks at it.
// Names' floating player labels are text_displays too, so the board is found by its header text.
const PLATFORM_Y = 200;
const { bx: BX, bz: BZ } = randomOrigin();
const HEADER = /Tag leaderboard/;

const rcon = await Rcon.connect();
const bots = {};
const results = [];
async function step(name, fn) {
  await fn();
  results.push(name);
  console.log(`PASS ${name}`);
}

const plain = (s) => s.replace(/§./g, '');
const board = (bot) => textDisplays(bot).find((d) => HEADER.test(d.text));

try {
  await buildPlatform(rcon, BX, BZ, PLATFORM_Y);
  await rcon.command('tag clear');
  for (const [name, dx] of [['LbA', 0], ['LbB', 3]]) {
    const bot = await createBot(name);
    bots[name] = bot;
    await rcon.command(`minecraft:gamemode survival ${name}`);
    await teleport(rcon, bot, BX + dx, PLATFORM_Y + 1, BZ);
  }
  const { LbA, LbB } = bots;
  await rcon.command('op LbA');
  await sleep(500);

  await step('console cannot place the board (no location to use)', async () => {
    const reply = plain(await rcon.command('tag leaderboard place'));
    assert(/in-game/i.test(reply), `console rejected: ${reply}`);
  });

  await step('an admin places the board and other players see it with the top player', async () => {
    const mark = LbA.lines.length;
    LbA.chat('/tag leaderboard place');
    await waitForLine(LbA, /Leaderboard placed/, 5_000, mark);
    await waitFor(() => board(LbB), 'LbB sees the leaderboard text_display', 8_000);
    const found = board(LbB);
    assert(found.entity.position.distanceTo(LbA.entity.position) < 3, 'board sits where LbA stood');
    const top = plain(await rcon.command('tag top')).split('\n')
      .map((l) => /^\s*1\. (\S+)/.exec(l)?.[1]).find(Boolean);
    assert(top, 'tag top has a first place');
    assert(found.text.includes(top), `board text has the top player ${top}: ${found.text}`);
  });

  await step('the board updates when IT changes', async () => {
    await rcon.command('tag set LbB');
    await waitFor(() => /IT:.*LbB/.test(board(LbB)?.text ?? ''), 'footer names LbB as IT', 8_000);
  });

  await step('remove takes the board down', async () => {
    const mark = LbA.lines.length;
    LbA.chat('/tag leaderboard remove');
    await waitForLine(LbA, /Leaderboard removed/, 5_000, mark);
    await waitFor(() => !board(LbB), 'board gone', 8_000);
  });

  console.log(`\nLeaderboard e2e: ${results.length} passed`);
} catch (error) {
  console.error(`FAIL after ${results.length} passed: ${error.message}`);
  process.exitCode = 1;
} finally {
  await rcon.command('deop LbA').catch(() => {});
  await rcon.command('tag clear').catch(() => {});
  await releasePlatform(rcon, BX, BZ);
  await quit(...Object.values(bots));
  rcon.close();
}
