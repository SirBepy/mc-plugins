import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { Rcon } from './lib.mjs';

// One-shot driver for pre-generating server/pregen's world via Chunky over RCON, run manually
// after :server:assemblePregenServer + booting server/pregen through the supervisor. Automates the
// same commands docs/server.md lists under "Pre-generate terrain with Chunky", against the
// disposable pregen folder instead of the real server, so the world can be uploaded once already
// generated.

const here = path.dirname(fileURLToPath(import.meta.url));
const pregenDir = path.join(here, '..', 'server', 'pregen');

// Half of the server-rules datapack's border diameters (10000 overworld / 2500 nether, see
// server/datapacks/server-rules/data/server_rules/function/load.mcfunction), since Chunky's radius
// is measured from the center, not a diameter.
const OVERWORLD_RADIUS = 5000;
const NETHER_RADIUS = 1250;

// Chunky's square shape covers a (radius*2 blocks)-per-side square, 16 blocks per chunk; a radius
// not itself a multiple of 16 (the nether's 1250) still needs a whole extra chunk at the edge.
function chunksFor(radius) {
  const side = Math.ceil((radius * 2) / 16);
  return side * side;
}

async function selectWorld(rcon, world) {
  const res = await rcon.command(`chunky world ${world}`);
  if (!res.startsWith('[Chunky] World changed to')) {
    throw new Error(`chunky world ${world} was rejected: ${res}`);
  }
}

async function queueWorld(rcon, world, radius) {
  await selectWorld(rcon, world);
  // Explicit even though square is Chunky's default: matches the vanilla world border's shape
  // (docs/server.md's Chunky commands rely on the same default, confirmed against the plugin here).
  await rcon.command('chunky shape square');
  // server-rules centers both borders on 0,0 (see its load.mcfunction).
  await rcon.command('chunky center 0 0');
  await rcon.command(`chunky radius ${radius}`);
  const startRes = await rcon.command('chunky start');
  console.log(`[${world}] queued radius ${radius} (${chunksFor(radius).toLocaleString()} chunks): ${startRes}`);
}

async function cmdStart() {
  const rcon = await Rcon.connect(pregenDir);
  try {
    const overworldBorder = await rcon.command('worldborder get');
    const netherBorder = await rcon.command('execute in minecraft:the_nether run worldborder get');
    console.log(`overworld worldborder: ${overworldBorder}`);
    console.log(`nether worldborder: ${netherBorder}`);
    if (!/\b10000\b/.test(overworldBorder)) {
      throw new Error(`expected overworld worldborder 10000, got: ${overworldBorder}`);
    }
    if (!/\b2500\b/.test(netherBorder)) {
      throw new Error(`expected nether worldborder 2500, got: ${netherBorder}`);
    }

    // Chunky queues one task per world and runs them in order (confirmed live: queuing the nether
    // while the overworld task is still running is accepted rather than rejected or overwritten).
    await queueWorld(rcon, 'world', OVERWORLD_RADIUS);
    await queueWorld(rcon, 'world_nether', NETHER_RADIUS);
  } finally {
    rcon.close();
  }
}

// Confirmed live: "chunky progress" (no world argument) replies with one line per RUNNING task
// across all worlds, not just the currently selected one, so a single call covers both.
async function cmdStatus() {
  const rcon = await Rcon.connect(pregenDir);
  try {
    console.log(await rcon.command('chunky progress'));
  } finally {
    rcon.close();
  }
}

const sub = process.argv[2];
if (sub === 'start') await cmdStart();
else if (sub === 'status') await cmdStatus();
else {
  console.error('usage: node pregen.mjs <start|status>');
  process.exitCode = 1;
}
