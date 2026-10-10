import bedrock from 'bedrock-protocol';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { Rcon, readGeyserBedrockPort, sleep } from './lib.mjs';

// Backpack paging (server/datapacks/eclipse-backpacks) against server/local, which runs the real
// 26.3 datapack set; Mineflayer can't join 26.3, so a headless Bedrock client through Geyser is the
// player. A click on a menu arrow is simulated the way the server sees it: the arrow leaves the menu
// slot and sits on the player's cursor, then the pack's own tick logic handles it.
//
// Regression for ImMijo's netherite backpack on live (2026-10-10): page 2's layout ("go back" at 18,
// locked slots, no "next page") had been saved over page 1, so it opened on a "page 2" and never got
// back to page 1. The suite saves a page-2 display over page 1 the same way (page score 0 while page
// 2 is shown, then a click saves it), checks the stored data matches what live had, and expects the
// backpack to open on a proper page 1 again.
// Usage: node backpack-pages.mjs   (server/local booted with RCON up; its Geyser login check is off)

const here = path.dirname(fileURLToPath(import.meta.url));
const localDir = path.join(here, '..', 'server', 'local');
// Floodgate gives every offline Bedrock login the zero UUID, so clear its player data for a fresh join.
const ZERO_UUID = '00000000-0000-0000-0000-000000000000';
for (const f of [`${ZERO_UUID}.dat`, `${ZERO_UUID}.dat_old`]) fs.rmSync(path.join(localDir, 'world', 'playerdata', f), { force: true });

const P = '.BedrockE2E';
const M = '@e[tag=backpacks.backpack_menu_ready,limit=1]';
const PAGES = 'SelectedItem.components."minecraft:custom_data".backpacks.contents.pages';
const NETHERITE = 'minecraft:structure_block[max_stack_size=1,custom_data={backpacks:{real_backpack:1b,tier:3,contents:{slots:40}}}]';

let failures = 0;
function report(name, pass, detail) {
  console.log(`${pass ? 'PASS' : 'FAIL'} ${name}${detail ? ` - ${detail}` : ''}`);
  if (!pass) failures++;
}

const client = bedrock.createClient({ host: '127.0.0.1', port: readGeyserBedrockPort(localDir), username: 'BedrockE2E', offline: true });
client.on('error', (e) => console.error('[bedrock client]', e.message));
await new Promise((resolve, reject) => {
  const timer = setTimeout(() => reject(new Error('Bedrock client did not spawn in 30 s')), 30_000);
  client.once('spawn', () => { clearTimeout(timer); resolve(); });
});
await sleep(3000);
const rcon = await Rcon.connect(localDir);
const cmd = (c) => rcon.command(c);

// Essentials shadows /item and /kill, so every vanilla command here is namespaced.
async function slot(s) {
  const id = await cmd(`data get entity ${M} Items[{Slot:${s}b}].id`);
  if (id.includes('Found no elements')) return '.';
  const data = await cmd(`data get entity ${M} Items[{Slot:${s}b}].components."minecraft:custom_data"`);
  if (data.includes('go_back')) return '<';
  if (data.includes('next_page')) return '>';
  if (data.includes('empty: 1b')) return '#';
  return id.match(/"minecraft:(\w)/)?.[1] ?? '?';
}
async function menu() {
  const page = Number((await cmd(`scoreboard players get ${M} backpacks.pages`)).match(/has (-?\d+)/)?.[1]);
  let slots = '';
  for (let s = 0; s < 27; s++) slots += await slot(s);
  return { page, slots };
}
async function storedArrows(index) {
  const has = async (flag) => /passed/.test(await cmd(`execute if data entity ${P} ${PAGES}[${index}][{components:{"minecraft:custom_data":{menu_backpack:{${flag}}}}}]`));
  return { next: await has('next_page:1b'), back: await has('go_back:1b') };
}
async function click(s) {
  await cmd(`minecraft:item replace entity ${P} player.cursor from entity ${M} container.${s}`);
  await cmd(`minecraft:item replace entity ${M} container.${s} with minecraft:air`);
  await sleep(700);
}

try {
  await cmd(`minecraft:gamemode survival ${P}`);
  await cmd('minecraft:forceload add -16 -16 16 16');
  await cmd('minecraft:fill -4 199 -4 4 199 4 minecraft:stone');
  await cmd('minecraft:fill -4 200 -4 4 203 4 minecraft:air');
  await cmd(`minecraft:tp ${P} 0.5 200 0.5`);
  await cmd(`minecraft:clear ${P}`);
  await sleep(1000);
  await cmd(`minecraft:item replace entity ${P} hotbar.0 with ${NETHERITE}`);
  await sleep(2000);

  let m = await menu();
  report('a held 40-slot backpack shows page 1 with a next-page arrow', m.page === 0 && m.slots[26] === '>' && !m.slots.includes('<'), m.slots);

  await cmd(`minecraft:item replace entity ${M} container.0 with minecraft:stone 5`);
  await sleep(700);
  await click(26);
  m = await menu();
  report('next page shows page 2 (go-back at 18, no next)', m.page === 1 && m.slots[18] === '<' && !m.slots.includes('>'), m.slots);
  await cmd(`minecraft:item replace entity ${M} container.0 with minecraft:dirt 3`);
  await sleep(700);
  await click(18);
  m = await menu();
  report('go back returns to page 1 with its stone', m.page === 0 && m.slots[0] === 's' && m.slots[26] === '>', m.slots);

  // Save page 2's display over page 1: page score 0 while page 2 is shown, then a click saves it.
  await click(26);
  await cmd(`scoreboard players set ${M} backpacks.pages 0`);
  await click(18);
  const broken = await storedArrows(0);
  report('stored page 1 now looks like live had it (go-back, no next)', broken.back && !broken.next, JSON.stringify(broken));

  // Opening (the advancement the right-click grants) resets to page 1 and loads it.
  await cmd(`minecraft:advancement grant ${P} only backpacks:open`);
  await sleep(1000);
  m = await menu();
  report('the backpack opens on a proper page 1 (next at 26, no go-back, no locked slots)', m.page === 0 && m.slots[26] === '>' && !m.slots.includes('<') && !m.slots.includes('#'), m.slots);
  report('the item saved on that page is still there', m.slots[0] === 'd', m.slots);

  await click(26);
  m = await menu();
  report('next page reaches page 2 again', m.page === 1 && m.slots[18] === '<', m.slots);
  const repaired = await storedArrows(0);
  report('the repaired page 1 was saved (next, no go-back)', repaired.next && !repaired.back, JSON.stringify(repaired));
  await click(18);
  m = await menu();
  report('go back reaches page 1 again', m.page === 0 && m.slots[26] === '>', m.slots);
} finally {
  await cmd(`minecraft:clear ${P}`).catch(() => {});
  await cmd('minecraft:forceload remove -16 -16 16 16').catch(() => {});
  client.disconnect();
  await sleep(500);
}

console.log(failures ? `\n${failures} check(s) failed` : '\nAll backpack page checks passed');
process.exit(failures ? 1 : 0);
