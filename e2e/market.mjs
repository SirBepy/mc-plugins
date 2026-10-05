import { Vec3 } from 'vec3';
import { Rcon, assert, createBot, quit, sleep, teleport, waitFor, waitForLine } from './lib.mjs';

// Market: the plugin writes a dyed, glowing Essentials [Sell] sign on the Farmer stall's first spot
// (coordinates from market/src/main/resources/stalls.yml), a bot selling to it gets paid the
// sign's price, and the next in-game day rotates the market. The e2e world has no spawn market, so a floor and a back wall are built around the spot.
const SIGN = { x: 420, y: 116, z: -882 };
const SELLER = 'MarketSeller';

const rcon = await Rcon.connect();
const bots = [];
const results = [];
async function step(name, fn) {
  await fn();
  results.push(name);
  console.log(`PASS ${name}`);
}

const plain = (s) => s.replace(/§./g, '');

// One path per call: the server shortens long NBT in command feedback ("...").
async function signData(path) {
  const out = plain(await rcon.command(`minecraft:data get block ${SIGN.x} ${SIGN.y} ${SIGN.z} front_text.${path}`));
  return out.replace(/^.*has the following block data: /, '').trim();
}

async function balance() {
  const out = plain(await rcon.command(`balance ${SELLER}`));
  const match = out.match(/\$([\d,]+(?:\.\d+)?)/);
  assert(match, `balance readable (got ${out.trim()})`);
  return Number(match[1].replace(/,/g, ''));
}

try {
  await rcon.command(`minecraft:forceload add ${SIGN.x - 8} ${SIGN.z - 8} ${SIGN.x + 8} ${SIGN.z + 8}`);
  await rcon.command(`minecraft:fill ${SIGN.x - 3} ${SIGN.y - 2} ${SIGN.z + 1} ${SIGN.x + 3} ${SIGN.y - 2} ${SIGN.z + 5} minecraft:stone`);
  await rcon.command(`minecraft:fill ${SIGN.x - 3} ${SIGN.y - 1} ${SIGN.z} ${SIGN.x + 3} ${SIGN.y + 2} ${SIGN.z + 5} minecraft:air`);
  await rcon.command(`minecraft:fill ${SIGN.x - 3} ${SIGN.y - 1} ${SIGN.z - 1} ${SIGN.x + 3} ${SIGN.y + 2} ${SIGN.z - 1} minecraft:stone`);

  let item;
  let amount;
  let price;
  await step('market rotate writes a dyed, glowing [Sell] sign on the stall spot', async () => {
    await waitFor(async () => /picked new buyers/.test(plain(await rcon.command('market rotate'))), 'market rotate', 10_000);
    const color = await signData('color');
    assert(color === '"yellow"', `sign dyed yellow (got ${color})`);
    const glow = await signData('has_glowing_text');
    assert(glow === '1b', `sign glows (got ${glow})`);
    const first = await signData('messages[0]');
    assert(/color: "dark_blue"/.test(first) && /text: "\[Sell\]"/.test(first), `line 1 is a dark blue [Sell] (got ${first})`);
    const unquote = (s) => s.replace(/^"|"$/g, '');
    amount = Number(unquote(await signData('messages[1]')));
    item = unquote(await signData('messages[2]'));
    price = Number(unquote(await signData('messages[3]')).replace('$', ''));
    assert(amount > 0 && /^[a-z_]+$/.test(item) && price > 0, `sign reads amount/item/price (got ${amount} ${item} ${price})`);
    console.log(`  sign: ${amount} ${item} for $${price}`);
  });

  await step('selling to the sign pays its price and takes the items', async () => {
    const bot = await createBot(SELLER);
    bots.push(bot);
    await rcon.command(`op ${SELLER}`);
    await rcon.command(`minecraft:gamemode survival ${SELLER}`);
    await rcon.command(`minecraft:clear ${SELLER}`);
    await rcon.command(`eco set ${SELLER} 0`);
    await rcon.command(`minecraft:give ${SELLER} minecraft:${item} ${amount}`);
    await teleport(rcon, bot, SIGN.x, SIGN.y - 1, SIGN.z + 2);
    await waitFor(() => bot.inventory.items().some((i) => i.name === item), `${SELLER} holds the ${item}`, 5_000);
    const held = bot.inventory.items().find((i) => i.name === item);
    await bot.equip(held, 'hand');
    const sign = bot.blockAt(new Vec3(SIGN.x, SIGN.y, SIGN.z));
    assert(sign && sign.name.endsWith('wall_sign'), `bot sees the sign (got ${sign?.name})`);
    await bot.lookAt(sign.position.offset(0.5, 0.5, 0.9), true);
    await bot.activateBlock(sign).catch(() => {});
    await waitFor(async () => (await balance()) > 0, `${SELLER} got paid`, 5_000);
    const paid = await balance();
    assert(Math.abs(paid - price) < 0.001, `paid the sign's $${price} (got $${paid})`);
    await sleep(300);
    assert(!bot.inventory.items().some((i) => i.name === item), `the ${amount} ${item} were taken`);
  });

  await step('the next in-game sunrise brings new buyers and tells players', async () => {
    const [bot] = bots;
    const since = bot.lines.length;
    await rcon.command('minecraft:time add 24000');
    // The plugin checks every 10 s.
    // Any rotation announcement: a run dir keeps whatever Market config.yml it first got.
    await waitForLine(bot, /spawn market/i, 15_000, since);
    assert((await signData('color')) === '"yellow"', 'the stall spot still holds a dyed sign after the rotation');
  });

  console.log(`\nMarket e2e: ${results.length} passed`);
} catch (error) {
  console.error(`FAIL after ${results.length} passed: ${error.message}`);
  process.exitCode = 1;
} finally {
  await rcon.command(`deop ${SELLER}`).catch(() => {});
  await rcon.command(`minecraft:forceload remove ${SIGN.x - 8} ${SIGN.z - 8} ${SIGN.x + 8} ${SIGN.z + 8}`).catch(() => {});
  await quit(...bots);
  rcon.close();
}
