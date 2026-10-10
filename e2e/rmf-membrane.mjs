import bedrock from 'bedrock-protocol';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { Rcon, readGeyserBedrockPort, sleep } from './lib.mjs';

// Plain Phantom Membranes against server/local, which runs the real 26.3 datapack set (Reg's More
// Foods plus server/datapacks/rmf-membrane-fix); Mineflayer can't join 26.3, so a headless Bedrock
// client through Geyser is the player More Foods' tick function acts on.
//
// Regression for Joe on live (2026-10-11): membranes from End loot chests turned into Coffee Beans
// with a "Replaced Item rmf:coffee_beans" action bar as soon as they reached his inventory. Expects a
// plain membrane to become More Foods' own membrane (count kept, no Coffee Beans) and Herbs, which
// share the phantom_membrane id, to be left alone.
// Usage: node rmf-membrane.mjs   (server/local booted with RCON up; its Geyser login check is off)

const here = path.dirname(fileURLToPath(import.meta.url));
const localDir = path.join(here, '..', 'server', 'local');
// Floodgate gives every offline Bedrock login the zero UUID, so clear its player data for a fresh join.
const ZERO_UUID = '00000000-0000-0000-0000-000000000000';
for (const f of [`${ZERO_UUID}.dat`, `${ZERO_UUID}.dat_old`]) fs.rmSync(path.join(localDir, 'world', 'playerdata', f), { force: true });

const P = '.BedrockE2E';
const PLAIN = 'minecraft:phantom_membrane[item_model="phantom_membrane",lore=[]]';
const RMF_MEMBRANE = 'minecraft:phantom_membrane[item_model="rmf:phantom_membrane"]';
const COFFEE = '*[custom_data~{item_id:"rmf:coffee_beans"}]';
const HERBS = '*[custom_data~{item_id:"rmf:herbs"}]';

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
// `clear <p> <item> 0` removes nothing and reports how many matching items the player holds.
async function count(item) {
  const reply = await cmd(`minecraft:clear ${P} ${item} 0`);
  return Number(reply.match(/Found (\d+)/)?.[1] ?? 0);
}

try {
  await cmd(`minecraft:gamemode survival ${P}`);
  await cmd(`minecraft:clear ${P}`);
  await sleep(500);

  // A loot chest's membrane: the vanilla item, no components.
  await cmd(`minecraft:item replace entity ${P} hotbar.0 with minecraft:phantom_membrane 5`);
  await sleep(1500);
  report('the plain membrane is gone', (await count(PLAIN)) === 0);
  report('no Coffee Beans were given', (await count(COFFEE)) === 0);
  const converted = await count(RMF_MEMBRANE);
  report('5 More Foods membranes replace it', converted === 5, `found ${converted}`);
  const name = await cmd(`data get entity ${P} Inventory[{id:"minecraft:phantom_membrane"}].components."minecraft:item_name"`);
  report('it carries More Foods\' membrane name', name.includes('item.rmf.phantom_membrane'), name);

  await cmd(`minecraft:clear ${P}`);
  await cmd(`minecraft:function rmf:give_item/herbs {player:"${P}",count:3}`);
  await sleep(1500);
  const herbs = await count(HERBS);
  report('Herbs are left alone', herbs === 3 && (await count(COFFEE)) === 0, `found ${herbs} herbs`);
} finally {
  await cmd(`minecraft:clear ${P}`).catch(() => {});
  client.disconnect();
  await sleep(500);
}

console.log(failures ? `\n${failures} check(s) failed` : '\nAll phantom membrane checks passed');
process.exit(failures ? 1 : 0);
