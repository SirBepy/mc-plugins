import { Rcon, assert, createBot, quit, sleep, waitFor, waitForLine } from './lib.mjs';

// The End's opening (EndFight, Joe 2026-10-10): with an opening time set, End portals shut for
// everyone (ops too) and a bounced player is set down beside the frames instead of falling into
// the stronghold's lava; /warp end lands in the portal room of the stronghold nearest spawn; once
// the time passes the server announces it and the same portal takes the player to the End.

const rcon = await Rcon.connect();
const bot = await createBot('EndOpenBot');
await rcon.command(`op ${bot.username}`);
await rcon.command(`gamemode survival ${bot.username}`);
await rcon.command(`effect give ${bot.username} minecraft:fire_resistance 600 0 true`);
const results = [];
async function step(name, fn) {
  await fn();
  results.push(name);
  console.log(`PASS ${name}`);
}

function isoIn(seconds) {
  return new Date(Date.now() + seconds * 1000).toISOString().replace(/\.\d+Z$/, 'Z');
}

function framesNear(radius) {
  return bot.findBlocks({
    matching: (block) => block.name === 'end_portal_frame',
    maxDistance: radius,
    count: 12,
  });
}

let frames = [];
try {
  await step('an opening time shuts the End and says so', async () => {
    const reply = await rcon.command(`endfight opening set ${isoIn(600)}`);
    assert(/closed, 10 minutes left/.test(reply), `status after set: ${reply}`);
  });

  await step('/warp end lands beside the stronghold portal frames, not in lava', async () => {
    const since = bot.lines.length;
    bot.chat('/warp end');
    await waitForLine(bot, /stronghold's portal room/, 60_000, since);
    await sleep(1500);
    await bot.waitForChunksToLoad().catch(() => {});
    frames = framesNear(8);
    assert(frames.length >= 8, `found ${frames.length} portal frames within 8 blocks of ${bot.entity.position}`);
    const feet = bot.blockAt(bot.entity.position.floored());
    assert(feet && feet.name !== 'lava', `standing in ${feet?.name}`);
    assert(bot.game.dimension.includes('overworld'), `still in the overworld (${bot.game.dimension})`);
  });

  const cx = Math.round(frames.reduce((s, f) => s + f.x, 0) / frames.length);
  const cz = Math.round(frames.reduce((s, f) => s + f.z, 0) / frames.length);
  const fy = frames[0].y;

  await step('jumping into a filled portal while closed bounces the player out beside the frames', async () => {
    await rcon.command(`fill ${cx - 1} ${fy} ${cz - 1} ${cx + 1} ${fy} ${cz + 1} minecraft:end_portal`);
    const since = bot.lines.length;
    await rcon.command(`minecraft:tp ${bot.username} ${cx + 0.5} ${fy + 0.2} ${cz + 0.5}`);
    await waitForLine(bot, /The End is closed until/, 10_000, since);
    await sleep(2000);
    assert(bot.game.dimension.includes('overworld'), `bounced, still in the overworld (${bot.game.dimension})`);
    const p = bot.entity.position;
    const out = Math.max(Math.abs(p.x - (cx + 0.5)), Math.abs(p.z - (cz + 0.5)));
    assert(out > 2.4, `set down outside the frame ring (${p}, centre ${cx} ${cz})`);
    assert(p.y >= fy - 3, `not dropped below the portal (${p.y} vs frames at ${fy})`);
  });

  await step('when the time comes the server announces it and the portal works', async () => {
    const since = bot.lines.length;
    await rcon.command(`endfight opening set ${isoIn(-5)}`);
    await waitForLine(bot, /The End is open!/, 10_000, since);
    await rcon.command(`minecraft:tp ${bot.username} ${cx + 0.5} ${fy + 0.2} ${cz + 0.5}`);
    await waitFor(() => bot.game.dimension.includes('end'), 'the bot reached the End', 30_000);
  });
} finally {
  await rcon.command('endfight opening clear');
  if (frames.length) {
    const cx = Math.round(frames.reduce((s, f) => s + f.x, 0) / frames.length);
    const cz = Math.round(frames.reduce((s, f) => s + f.z, 0) / frames.length);
    await rcon.command(`fill ${cx - 1} ${frames[0].y} ${cz - 1} ${cx + 1} ${frames[0].y} ${cz + 1} minecraft:air replace minecraft:end_portal`);
  }
  await rcon.command(`deop ${bot.username}`);
  await quit(bot);
  rcon.close?.();
}
console.log(`end-opening: ${results.length}/4 passed`);
process.exit(results.length === 4 ? 0 : 1);
