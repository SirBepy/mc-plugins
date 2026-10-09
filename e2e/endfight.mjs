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
} finally {
  await quit(...bots);
  await rcon.command(`${END} forceload remove -64 -64 64 64`).catch(() => {});
  rcon.close?.();
  console.log(`endfight: ${results.length} steps passed`);
}
