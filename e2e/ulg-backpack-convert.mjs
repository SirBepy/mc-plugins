// Prototype verification for the legacy-ulg-backpack converter (server/datapacks/ulg-backpack-converter). Not
// wired into run-all.mjs yet - this proves the converter against server/local directly, the same
// way e2e/bedrock.mjs does: a headless Bedrock client is the only way to get a "real player" entity
// on 26.3 (Mineflayer gets kicked, see PORTED.md's "Known limitation"), then everything else is
// driven over RCON.
import bedrock from 'bedrock-protocol';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { Rcon, readGeyserBedrockPort } from './lib.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const localDir = path.join(here, '..', 'server', 'local');

let failures = 0;
function report(name, pass, detail) {
  console.log(`${pass ? 'PASS' : 'FAIL'} ${name}${detail ? ` - ${detail}` : ''}`);
  if (!pass) failures++;
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const USERNAME = 'UlgConvertE2E';
const FLOODGATE_NAME = `.${USERNAME}`;

const bedrockPort = readGeyserBedrockPort(localDir);
const client = bedrock.createClient({ host: '127.0.0.1', port: bedrockPort, username: USERNAME, offline: true });
let kickReason = null;
client.on('kick', (reason) => { kickReason = reason; });
client.on('error', (err) => console.error('[bedrock client error]', err.message));

async function waitForSpawn() {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('bot did not spawn within 30s')), 30_000);
    client.once('spawn', () => { clearTimeout(timer); resolve(); });
    client.once('close', () => { clearTimeout(timer); reject(new Error(`connection closed before spawn (kick: ${JSON.stringify(kickReason)})`)); });
  });
}

try {
  await waitForSpawn();
  report('bot joins and spawns', true);
} catch (e) {
  report('bot joins and spawns', false, e.message);
  console.log(`\n${failures} check(s) failed`);
  process.exitCode = 1;
  process.exit(1);
}

const rcon = await Rcon.connect(localDir);
const P = FLOODGATE_NAME;

// --- small SNBT builders for old ulg Inventory entries (test fixtures only - the converter itself
// never builds these, it only reads them) ---
let slotCounter = 0;
function nextSlot() { return slotCounter++; }
function plainItem(id, count, extra = '') {
  return `{Slot:${nextSlot()}b,id:"${id}",count:${count}${extra ? `,components:{${extra}}` : ''}}`;
}
function enchantedSword() {
  return `{Slot:${nextSlot()}b,id:"minecraft:diamond_sword",count:1,components:{"minecraft:enchantments":{"minecraft:sharpness":5,"minecraft:unbreaking":3}}}`;
}
function namedDiamond() {
  return `{Slot:${nextSlot()}b,id:"minecraft:diamond",count:1,components:{"minecraft:custom_name":'{"text":"Heirloom"}'}}`;
}
function damagedPick() {
  return `{Slot:${nextSlot()}b,id:"minecraft:iron_pickaxe",count:1,components:{"minecraft:damage":100}}`;
}
function bundleWithSticks() {
  return `{Slot:${nextSlot()}b,id:"minecraft:bundle",count:1,components:{"minecraft:bundle_contents":[{id:"minecraft:stick",count:3}]}}`;
}
function nonEmptyShulker() {
  return `{Slot:${nextSlot()}b,id:"minecraft:shulker_box",count:1,components:{"minecraft:container":[{slot:0,item:{id:"minecraft:emerald",count:5}}]}}`;
}

// "item"/"give"/"clear" are shadowed by a plugin over RCON (confirmed empirically this dispatch,
// matches the project's existing give/clear finding) - minecraft:item/minecraft:clear are the
// unshadowed forms. Old backpacks are seeded into the player's OFFHAND: the ulg pack's own
// documented canonical carry location (FORMAT.md), and the one nbt/item_slot_arg pairing confirmed
// simple and correct (equipment.offhand / weapon.offhand - no hotbar-vs-rest index translation
// unlike main inventory, see ulg-backpack-converter/README.md).
async function giveOldBackpack(items, itemSlotArg = 'weapon.offhand') {
  slotCounter = 0;
  const resolved = items.map((f) => f());
  await rcon.command(`minecraft:clear ${P}`);
  const inv = `[${resolved.join(',')}]`;
  const cmd = `minecraft:item replace entity ${P} ${itemSlotArg} with minecraft:carrot_on_a_stick[item_model="ulg:backpack/chest_normal",custom_data={bp:{Inventory:${inv},Opened:false,newV:26}}]`;
  const res = await rcon.command(cmd);
  return { res, count: resolved.length };
}

async function leaf(pathExpr) {
  return rcon.command(`data get entity ${P} ${pathExpr}`);
}

function numFromReply(res) {
  // "Found no elements..." / error replies can still contain digits (e.g. an echoed NBT path like
  // "Slot:0b"), so only parse a number out of a reply that looks like an actual data/score result.
  // Two known reply shapes carry the value differently: "<entity> has the following entity data: N"
  // (data get on a scalar) and "<holder> has N [<objective>]" (scoreboard players get) - match
  // either, preferring the data-get colon form first since it's unambiguous.
  if (/found no elements|error|unknown/i.test(res)) return null;
  const dataGet = res.match(/entity data:\s*(-?\d+)/);
  if (dataGet) return Number(dataGet[1]);
  const score = res.match(/\bhas\s+(-?\d+)\s*\[/);
  if (score) return Number(score[1]);
  return null;
}
function strFromReply(res) {
  if (/found no elements|error|unknown/i.test(res)) return null;
  const m = res.match(/"([^"]*)"/);
  return m ? m[1] : null;
}

// execute store result score <x> <obj> run data get <path> stores the list/compound's element
// count into the score, but the command's own RCON feedback text still echoes the full (possibly
// truncated) data get value, not the number - so the only reliable way to read the count back is a
// second, separate scoreboard query.
async function countOf(getCmd) {
  await rcon.command(`execute store result score #ulgconvert_test ulgconvert.scratch run ${getCmd}`);
  const res = await rcon.command('scoreboard players get #ulgconvert_test ulgconvert.scratch');
  return numFromReply(res);
}

const OFFHAND = { nbt: 'equipment.offhand', arg: 'weapon.offhand' };

async function runCase(name, items, expectTier, expectRealCount, expectEjectedCount, { nbt, arg } = OFFHAND) {
  console.log(`\n--- ${name} ---`);
  const { count, res: giveRes } = await giveOldBackpack(items, arg);
  report(`${name}: old backpack seeded (${count} old slots)`, /Replaced/.test(giveRes), giveRes);

  const convertRes = await rcon.command(
    `execute as ${P} run function ulgconvert:convert/item {nbt:"${nbt}",item_slot_arg:"${arg}",source_desc:"test:${name.replace(/\s+/g, '_')}"}`
  );
  report(`${name}: convert/item ran with no error`, !/error|unknown/i.test(convertRes), convertRes);

  const tierRes = await leaf(`${nbt}.components."minecraft:custom_data".backpacks.tier`);
  report(`${name}: tier == ${expectTier}`, numFromReply(tierRes) === expectTier, tierRes);

  const slotsRes = await leaf(`${nbt}.components."minecraft:custom_data".backpacks.contents.slots`);
  const realBackpackCapacity = [3, 9, 18, 40][expectTier];
  report(`${name}: contents.slots == ${realBackpackCapacity}`, numFromReply(slotsRes) === realBackpackCapacity, slotsRes);

  const initIdRes = await leaf(`${nbt}.components."minecraft:custom_data".backpacks.init.id`);
  const initId = numFromReply(initIdRes);
  report(`${name}: init.id is a number`, Number.isInteger(initId), initIdRes);

  const initSlotsRes = await leaf(`${nbt}.components."minecraft:custom_data".backpacks.init.slots`);
  report(`${name}: init.slots == contents.slots`, numFromReply(initSlotsRes) === realBackpackCapacity, initSlotsRes);

  const page0Count = await countOf(`data get entity ${P} ${nbt}.components."minecraft:custom_data".backpacks.contents.pages[0]`);
  report(`${name}: page 0 has entries (sparse list, length=${page0Count})`, Number.isInteger(page0Count) && page0Count > 0, String(page0Count));

  if (expectTier === 3) {
    const page1Count = await countOf(`data get entity ${P} ${nbt}.components."minecraft:custom_data".backpacks.contents.pages[1]`);
    report(`${name}: page 1 exists (2-page tier)`, Number.isInteger(page1Count) && page1Count > 0, String(page1Count));
  }

  // Backup record: ulgconvert:backup backups.<initId>.backup should have exactly `count` entries
  // (every original old item, ejected or not).
  const backupCount = await countOf(`data get storage ulgconvert:backup backups.${initId}.backup`);
  report(`${name}: backup.length == ${count} (every original item backed up)`, backupCount === count, String(backupCount));

  const ejectedCount = await countOf(`data get storage ulgconvert:backup backups.${initId}.ejected`);
  report(`${name}: ejected.length == ${expectEjectedCount}`, ejectedCount === expectEjectedCount, String(ejectedCount));

  const realCountRes = await rcon.command('scoreboard players get #realcount ulgconvert.scratch');
  report(`${name}: #realcount == ${expectRealCount} (post-eject real item count)`, numFromReply(realCountRes) === expectRealCount, realCountRes);

  return { initId };
}

// --- tier 0: 2 real items, fits in 3 ---
await runCase('tier0-small', [() => plainItem('minecraft:cobblestone', 37), enchantedSword], 0, 2, 0);

// --- tier 1: 5 real items, fits in 9 but not 3 ---
await runCase('tier1-medium', [
  () => plainItem('minecraft:cobblestone', 37), enchantedSword, namedDiamond, damagedPick,
  () => plainItem('minecraft:oak_log', 64),
], 1, 5, 0);

// --- tier 2: 12 real items, fits in 18 but not 9 ---
const tier2Items = [enchantedSword, namedDiamond, damagedPick];
for (let i = 0; i < 9; i++) tier2Items.push(() => plainItem('minecraft:stone', i + 1));
await runCase('tier2-large', tier2Items, 2, 12, 0);

// --- tier 3: 20 real items, fits in 40 but not 18 (forces page 1 + page 0 arrow) ---
const tier3Items = [enchantedSword, namedDiamond, damagedPick];
for (let i = 0; i < 17; i++) tier3Items.push(() => plainItem('minecraft:dirt', i + 1));
await runCase('tier3-xlarge', tier3Items, 3, 20, 0);

// --- bundle/shulker eject case: 3 real items + 1 bundle + 1 non-empty shulker -> tier 0, 2 ejected ---
const { initId: ejectCaseId } = await runCase('eject-bundle-shulker', [
  () => plainItem('minecraft:cobblestone', 10), namedDiamond, damagedPick, bundleWithSticks, nonEmptyShulker,
], 0, 3, 2);

// Ejected items land in the player's main inventory (first free "inventory.N" slots, which are raw
// NBT Slot 9-35 - see README.md). Offhand itself now holds the converted backpack, untouched.
// "inventory.N" is only a valid /item slot ARGUMENT, not a /data get NBT path, so check the real
// backing list directly (truncation-safe: just a substring match on raw Inventory text, not a full
// per-item parse).
const wholeInvRes = await rcon.command(`data get entity ${P} Inventory`);
const ejectedFound = (wholeInvRes.match(/minecraft:bundle/g) || []).length + (wholeInvRes.match(/minecraft:shulker_box/g) || []).length;
report('eject case: both bundle and shulker landed in player inventory', ejectedFound === 2, `found ${ejectedFound} in Inventory`);

// --- a second slot pairing: a backpack sitting in hotbar slot 0 (raw NBT Slot 0b) instead of
// offhand, proving convert/item's general per-slot mechanism (not just the offhand shortcut). ---
await rcon.command(`minecraft:clear ${P}`);
await runCase(
  'hotbar-slot-pairing',
  [() => plainItem('minecraft:cobblestone', 5), namedDiamond],
  0, 2, 0,
  { nbt: 'Inventory[{Slot:0b}]', arg: 'hotbar.0' }
);

// --- restore path: pull backup entry 0 of the eject case back into inventory slot arg 20 and compare ---
await rcon.command(`minecraft:clear ${P}`);
const restoreRes = await rcon.command(`execute as ${P} run function ulgconvert:restore/item {id:${ejectCaseId},index:0,slot:20}`);
report('restore/item ran with no error', !/error|unknown/i.test(restoreRes), restoreRes);
// slot:20 (inventory.20 argument index) -> raw NBT Slot 29.
const restoredIdRes = await leaf('Inventory[{Slot:29b}].id');
report('restore/item placed the backed-up item id (cobblestone)', strFromReply(restoredIdRes) === 'minecraft:cobblestone', restoredIdRes);
const restoredCountRes = await leaf('Inventory[{Slot:29b}].count');
report('restore/item placed the backed-up count (10)', numFromReply(restoredCountRes) === 10, restoredCountRes);

client.close?.();
rcon.close();

console.log(`\n${failures} check(s) failed`);
process.exitCode = failures ? 1 : 0;
