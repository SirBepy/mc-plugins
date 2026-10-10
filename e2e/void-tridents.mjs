import {
  Rcon, assert, createBot, quit, sleep, waitFor,
} from './lib.mjs';

// Tweaks' LoyaltyTridentListener: Loyalty tridents always come back.
// 1. A bot on a one-block pillar far out in the End throws a named, enchanted, damaged Loyalty
//    trident; it drops below y 0 (the End's min height), flies back and lands in the bot's
//    inventory with every component intact.
// 2. A Loyalty trident whose owner is in another world can't fly home, so the void discards it and
//    the exact item goes straight into the owner's inventory.
// 3. A trident without Loyalty is lost to the void like vanilla.
// 4. A Loyalty trident lodged in the End while its owner is in the Overworld goes into the owner's
//    inventory once its 3 s grace is over (vanilla never returns across worlds).
// 5. A Loyalty trident hovering in a chunk only a forceload keeps ticking is left to vanilla while it
//    ticks, and handed over once the forceload goes (live's case: ~160 blocks from every player).
// 6. One whose owner is offline is taken back and handed over when they join again.
const RUN = String(Math.floor(Math.random() * 10_000)).padStart(4, '0');
const BOT = `VtBot${RUN}`;
// In the empty ring between the main island (and its dragon fight) and the outer islands, which
// start about 1000 blocks out: at -3000 3000 the trident landed on an outer island at y 32.
const X = -500;
const Y = 60;
const Z = -500;
const END = 'execute in minecraft:the_end run';
const NAME = "Porunga's Trident";
const COMPONENTS = `custom_name="${NAME}",enchantments={"minecraft:loyalty":3,"minecraft:impaling":5},damage=42`;
// Throwing costs the trident one point of durability, so the one that comes back carries damage 43.
const THROWN_COMPONENTS = COMPONENTS.replace('damage=42', 'damage=43');
const NBT_COMPONENTS = `{"minecraft:custom_name":"${NAME}","minecraft:enchantments":{"minecraft:loyalty":3,"minecraft:impaling":5},"minecraft:damage":42}`;

const rcon = await Rcon.connect();
let bot;

const passed = async (command) => /passed/i.test(await rcon.command(command));
const holds = (components) => passed(`execute if items entity ${BOT} container.* minecraft:trident[${components}]`);
const holdsAnyTrident = () => passed(`execute if items entity ${BOT} container.* minecraft:trident`);

async function tridentY(connection = rcon) {
  const reply = await connection.command(`${END} data get entity @e[type=minecraft:trident,x=${X},y=${Y},z=${Z},distance=..300,limit=1] Pos[1]`);
  const match = reply.match(/(-?\d+(?:\.\d+)?)d/);
  return match ? Number(match[1]) : undefined;
}

async function uuidArray(name) {
  const reply = await rcon.command(`data get entity ${name} UUID`);
  const match = reply.match(/\[I;\s*(-?\d+),\s*(-?\d+),\s*(-?\d+),\s*(-?\d+)\]/);
  assert(match, `${name}'s UUID readable (got ${reply.trim()})`);
  return `[I;${match.slice(1).join(',')}]`;
}

// Out in the same empty ring as the pillar, 25 chunks from it: past every bot's view distance, so
// only a forceload keeps it loaded and ticking.
const FX = X;
const FZ = Z + 400;

// NoGravity and no motion keep it hovering without ever landing, so vanilla Loyalty never starts
// and only the plugin can bring it back.
async function summonHovering(owner) {
  await rcon.command(`${END} summon minecraft:trident ${FX + 0.5} ${Y} ${FZ + 0.5} {Owner:${owner},pickup:1b,NoGravity:1b,`
    + `item:{id:"minecraft:trident",count:1,components:${NBT_COMPONENTS}},Motion:[0d,0d,0d]}`);
}

async function hoveringExists() {
  return /passed/i.test(await rcon.command(`${END} execute if entity @e[type=minecraft:trident,x=${FX},y=${Y},z=${FZ},distance=..8]`));
}

async function onPillar() {
  await rcon.command(`${END} minecraft:tp ${BOT} ${X + 0.5} ${Y + 1} ${Z + 0.5}`);
  await waitFor(() => bot.game.dimension?.includes('the_end')
    && bot.entity.position.distanceTo({ x: X + 0.5, y: Y + 1, z: Z + 0.5 }) < 2, `${BOT} on the End pillar`);
}

async function summonFalling(owner, components) {
  await rcon.command(`${END} summon minecraft:trident ${X + 0.5} -10 ${Z + 0.5} {Owner:${owner},pickup:1b,`
    + `item:{id:"minecraft:trident",count:1,components:${components}},Motion:[0d,-1d,0d]}`);
}

try {
  await rcon.command(`${END} forceload add ${X - 96} ${Z - 96} ${X + 96} ${Z + 96}`);
  await rcon.command(`${END} fill ${X - 2} ${Y - 2} ${Z - 2} ${X + 2} ${Y + 4} ${Z + 2} minecraft:air`);
  await rcon.command(`${END} setblock ${X} ${Y} ${Z} minecraft:obsidian`);
  await rcon.command(`${END} minecraft:kill @e[type=minecraft:trident,x=${X},y=${Y},z=${Z},distance=..300]`);

  bot = await createBot(BOT);
  await rcon.command(`minecraft:gamemode survival ${BOT}`);
  await rcon.command(`minecraft:effect give ${BOT} minecraft:resistance infinite 255 true`);
  await rcon.command(`minecraft:clear ${BOT}`);
  await onPillar();
  await bot.waitForChunksToLoad().catch(() => {});
  await sleep(1000);

  // 1. Throw it off the pillar into the void.
  await rcon.command(`minecraft:item replace entity ${BOT} hotbar.0 with minecraft:trident[${COMPONENTS}]`);
  await waitFor(() => bot.inventory.slots[36]?.name === 'trident', `${BOT} holds the trident`);
  bot.setQuickBarSlot(0);
  await bot.look(0, -0.6, true);
  // The trident spends only a few ticks below y 0 before it turns back, so its height is sampled
  // back to back on its own loop and RCON connection (one connection answers one command at a time).
  let lowest = Infinity;
  let sampling = true;
  const sampleRcon = await Rcon.connect();
  const sampler = (async () => {
    while (sampling) {
      const y = await tridentY(sampleRcon);
      if (y !== undefined) lowest = Math.min(lowest, y);
    }
  })();
  bot.activateItem();
  // A trident only throws after 10 ticks of charging.
  await sleep(1200);
  bot.deactivateItem();
  try {
    await waitFor(async () => !(await holdsAnyTrident()), `${BOT} threw the trident`, 5_000);
    await waitFor(() => holds(THROWN_COMPONENTS), 'the trident came back with its name, enchantments and damage', 45_000);
  } finally {
    sampling = false;
    await sampler;
    sampleRcon.close();
  }
  assert(lowest < 0, `the trident really fell below the End's floor at y 0 (lowest seen ${lowest})`);
  console.log(`PASS a Loyalty trident thrown into the void flies back (fell to y ${lowest.toFixed(1)})`);

  // 2. Owner in the Overworld: the void discards it, the item goes to the inventory.
  await rcon.command(`minecraft:clear ${BOT}`);
  await rcon.command(`execute in minecraft:overworld run minecraft:tp ${BOT} 0 200 0`);
  await waitFor(() => bot.game.dimension?.includes('overworld'), `${BOT} in the Overworld`);
  await summonFalling(await uuidArray(BOT), NBT_COMPONENTS);
  await waitFor(() => holds(COMPONENTS), 'the void-discarded trident was handed to its owner in another world', 20_000);
  console.log('PASS a Loyalty trident the void still takes goes into its owner\'s inventory');

  // 3. No Loyalty: lost like vanilla.
  await rcon.command(`minecraft:clear ${BOT}`);
  await summonFalling(await uuidArray(BOT), `{"minecraft:custom_name":"${NAME}"}`);
  await waitFor(async () => (await tridentY()) !== undefined, 'the plain trident is falling', 5_000);
  await waitFor(async () => (await tridentY()) === undefined, 'the void took the plain trident', 20_000);
  await sleep(1000);
  assert(!(await holdsAnyTrident()), 'a trident without Loyalty does not come back');
  console.log('PASS a trident without Loyalty is lost to the void like vanilla');

  // 4. Lodged on the pillar in the End while the owner is in the Overworld.
  await rcon.command(`minecraft:clear ${BOT}`);
  const owner = await uuidArray(BOT);
  await rcon.command(`${END} summon minecraft:trident ${X + 0.5} ${Y + 1.5} ${Z + 0.5} {Owner:${owner},pickup:1b,`
    + `item:{id:"minecraft:trident",count:1,components:${NBT_COMPONENTS}}}`);
  await sleep(1000);
  assert(!(await holdsAnyTrident()), 'vanilla gets the first few seconds');
  await waitFor(() => holds(COMPONENTS), 'the trident lodged in another world was handed over', 15_000);
  assert((await tridentY()) === undefined, 'no trident left on the pillar');
  console.log('PASS a Loyalty trident in another world from its owner goes into their inventory');

  // 5. Hovering in a forceloaded chunk far out: left alone while it ticks, handed over once it can't.
  await rcon.command(`minecraft:clear ${BOT}`);
  await onPillar();
  await rcon.command(`${END} forceload add ${FX} ${FZ}`);
  await summonHovering(owner);
  await waitFor(hoveringExists, 'the hovering trident spawned', 5_000);
  await sleep(5000);
  assert(await hoveringExists(), 'a ticking trident with its owner in the same world is left to vanilla');
  assert(!(await holdsAnyTrident()), 'nothing handed over while it ticks');
  await rcon.command(`${END} forceload remove ${FX} ${FZ}`);
  await waitFor(() => holds(COMPONENTS), 'the trident was handed over once its chunk stopped ticking', 30_000);
  console.log("PASS a Loyalty trident out of ticking range goes into its owner's inventory");

  // 6. Owner offline: taken back now, handed over on the next join.
  await rcon.command(`minecraft:clear ${BOT}`);
  await rcon.command(`${END} forceload add ${FX} ${FZ}`);
  await summonHovering(owner);
  await waitFor(hoveringExists, 'the hovering trident spawned', 5_000);
  await quit(bot);
  await waitFor(async () => !(await hoveringExists()), "the offline owner's trident was taken back", 15_000);
  bot = await createBot(BOT);
  await waitFor(() => holds(COMPONENTS), 'the trident was handed over on join', 15_000);
  console.log('PASS an offline owner gets their Loyalty trident when they join');
} finally {
  await rcon.command(`${END} minecraft:kill @e[type=minecraft:trident,x=${X},y=${Y},z=${Z},distance=..300]`).catch(() => {});
  await quit(bot);
  await rcon.command(`${END} forceload remove ${X - 96} ${Z - 96} ${X + 96} ${Z + 96}`).catch(() => {});
  await rcon.command(`${END} forceload add ${FX} ${FZ}`).catch(() => {});
  await rcon.command(`${END} minecraft:kill @e[type=minecraft:trident,x=${FX},y=${Y},z=${FZ},distance=..8]`).catch(() => {});
  await rcon.command(`${END} forceload remove ${FX} ${FZ}`).catch(() => {});
  rcon.close();
  await sleep(200);
}
