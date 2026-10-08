import { readFileSync } from 'node:fs';
import { Vec3 } from 'vec3';
import { Rcon, assert, createBot, quit, sleep, teleport, waitFor, waitForLine } from './lib.mjs';

// Market: the plugin writes a dyed, glowing Essentials [Sell] sign on the Farmer stall's first spot
// (coordinates from market/src/main/resources/stalls.yml), a bot selling to it gets paid the
// sign's price, and the next in-game day rotates the market. The e2e world has no spawn market, so a floor and a back wall are built around the spot.
const SIGN = { x: 420, y: 116, z: -882 };
// stalls.yml's price-lectern: the plugin places it and its price book itself.
const LECTERN = { x: 415, y: 115, z: -865 };
const SELLER = 'MarketSeller';
// stalls.yml's head-collector spot: the plugin keeps a Mannequin (Java) and a villager (Bedrock) on it.
const COLLECTOR = { x: 430, y: 115, z: -856 };
// heads.yml, for the texture of whichever mob the Head collector wants today.
const HEADS = readFileSync(new URL('../market/src/main/resources/heads.yml', import.meta.url), 'utf8');

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
  await rcon.command(`minecraft:forceload add ${LECTERN.x} ${LECTERN.z}`);
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

  await step('the Guide stall lectern holds the price book', async () => {
    await waitFor(async () => /passed/i.test(plain(await rcon.command(
      `minecraft:execute if block ${LECTERN.x} ${LECTERN.y} ${LECTERN.z} minecraft:lectern[has_book=true]`))),
    'a lectern with a book at the price-lectern spot', 15_000);
    const title = plain(await rcon.command(`minecraft:data get block ${LECTERN.x} ${LECTERN.y} ${LECTERN.z} Book.components."minecraft:written_book_content".title`));
    assert(/Market prices/.test(title), `the book is the price book (got ${title.trim()})`);
    // Command feedback cuts long NBT short, so read the book one text part at a time: a page is
    // [heading, "\n", name, "\n", price, "\n", name, ...].
    const part = async (page, i) => plain(await rcon.command(`minecraft:data get block ${LECTERN.x} ${LECTERN.y} ${LECTERN.z} Book.components."minecraft:written_book_content".pages[${page}].raw.extra[${i}]`))
      .replace(/^.*has the following block data: /, '').trim();
    let found;
    for (let page = 1; page < 12 && !found; page++) {
      if (!/Lumberjack/.test(await part(page, 0))) continue;
      for (let i = 2; i < 28 && !found; i += 4) {
        if ((await part(page, i)) === '"Dark oak log"') found = await part(page, i + 2);
      }
    }
    assert(/text: "  64 for \$14\.40"/.test(found ?? ''),`the book lists 64 dark oak logs at $14.40, half a normal log (got ${found})`);
  });

  await step('/market lists today\'s buyers and /market prices opens the book', async () => {
    const [bot] = bots;
    const since = bot.lines.length;
    bot.chat('/market');
    await waitForLine(bot, /Today the spawn market buys:/, 5_000, since);
    // One block per stall: its name on a line of its own, then one line per item.
    await waitForLine(bot, /^Lumberjack$/, 5_000, since);
    await waitForLine(bot, /^ - 64 [a-z ]+ \$\d+\.\d\d$/, 5_000, since);
    await waitForLine(bot, /^Head collector$/, 5_000, since);
    const opened = new Promise((resolve) => bot._client.once('open_book', resolve));
    bot.chat('/market prices');
    await Promise.race([opened, sleep(5_000).then(() => { throw new Error('/market prices opened no book'); })]);
  });

  await step('/suggested-price and /price quote the held item', async () => {
    const [bot] = bots;
    await rcon.command(`minecraft:clear ${SELLER}`);
    await rcon.command(`minecraft:give ${SELLER} minecraft:diamond 1`);
    await waitFor(() => bot.inventory.items().some((i) => i.name === 'diamond'), `${SELLER} holds a diamond`, 5_000);
    await bot.equip(bot.inventory.items().find((i) => i.name === 'diamond'), 'hand');
    let since = bot.lines.length;
    bot.chat('/suggested-price');
    // prices.yml (e301e4c, 2026-10-06): diamond's base went $16 -> $20, so 2x stall base is $20/$1280.
    await waitForLine(bot, /Suggested price for Diamond: \$20\.00 each, \$1280\.00 for a stack of 64/, 5_000, since);
    since = bot.lines.length;
    bot.chat('/price');
    await waitForLine(bot, /Suggested price for Diamond: \$20\.00/, 5_000, since);
  });

  await step('/suggested-price prices an enchanted book\'s enchantments and breaks them down', async () => {
    const [bot] = bots;
    await rcon.command(`minecraft:clear ${SELLER}`);
    await rcon.command(`minecraft:give ${SELLER} minecraft:enchanted_book[stored_enchantments={"minecraft:sharpness":4,"minecraft:mending":1,"minecraft:vanishing_curse":1}] 1`);
    await waitFor(() => bot.inventory.items().some((i) => i.name === 'enchanted_book'), `${SELLER} holds an enchanted book`, 5_000);
    await bot.equip(bot.inventory.items().find((i) => i.name === 'enchanted_book'), 'hand');
    const since = bot.lines.length;
    bot.chat('/suggested-price');
    // prices.yml: book $0.50, Sharpness V $20 so IV $10, Mending $100, then Vanishing cuts 25%: $110.50 x 0.75.
    await waitForLine(bot, /Suggested price for Enchanted Book: \$82\.88\./, 5_000, since);
    await waitForLine(bot, /^ - Enchanted book \$0\.50$/, 5_000, since);
    await waitForLine(bot, /^ - Sharpness IV \+\$10\.00$/, 5_000, since);
    await waitForLine(bot, /^ - Mending \+\$100\.00$/, 5_000, since);
    await waitForLine(bot, /^ - Curse of Vanishing -25%$/, 5_000, since);
  });

  await step('the Head collector buys a wanted mob head from a non-op and refuses a player\'s head', async () => {
    const [bot] = bots;
    await rcon.command(`deop ${SELLER}`);
    await rcon.command(`minecraft:forceload add ${COLLECTOR.x} ${COLLECTOR.z}`);
    await rcon.command(`minecraft:fill ${COLLECTOR.x - 2} ${COLLECTOR.y - 1} ${COLLECTOR.z - 3} ${COLLECTOR.x + 2} ${COLLECTOR.y - 1} ${COLLECTOR.z + 1} minecraft:stone`);
    await rcon.command(`minecraft:fill ${COLLECTOR.x - 2} ${COLLECTOR.y} ${COLLECTOR.z - 3} ${COLLECTOR.x + 2} ${COLLECTOR.y + 2} ${COLLECTOR.z + 1} minecraft:air`);
    await teleport(rcon, bot, COLLECTOR.x + 0.5, COLLECTOR.y, COLLECTOR.z - 2.5);
    // The plugin's guard tick (every 10 s) puts the bodies on the spot once its chunk is loaded.
    const spot = new Vec3(COLLECTOR.x + 0.5, COLLECTOR.y, COLLECTOR.z + 0.5);
    const mannequin = () => Object.values(bot.entities).find((e) => e.name === 'mannequin' && e.position.distanceTo(spot) < 1);
    await waitFor(() => mannequin(), 'the collector mannequin', 25_000);
    const collector = mannequin();

    let since = bot.lines.length;
    bot.chat('/market');
    await waitForLine(bot, /^Head collector$/, 5_000, since);
    const heading = bot.lines.findIndex((l, i) => i >= since && /^Head collector$/.test(l));
    const line = await waitForLine(bot, /^ - (.+) heads? \$(\d+\.\d\d)/, 5_000, heading);
    const [, mobName, each] = line.match(/^ - (.+) heads? \$(\d+\.\d\d)/);
    const block = HEADS.split(/\r?\n {2}(?=[a-z_]+:\r?\n)/).find((b) => b.includes(`name: "${mobName}"`));
    assert(block, `heads.yml has ${mobName}`);
    const texture = block.match(/texture: (\w+)/)[1];
    const value = Buffer.from(JSON.stringify({ textures: { SKIN: { url: `http://textures.minecraft.net/texture/${texture}` } } })).toString('base64');
    console.log(`  wanted today: ${mobName} from $${each} a head`);

    await rcon.command(`minecraft:clear ${SELLER}`);
    await rcon.command(`eco set ${SELLER} 0`);
    await rcon.command(`minecraft:give ${SELLER} minecraft:player_head[minecraft:profile={properties:[{name:"textures",value:"${value}"}]}] 2`);
    await waitFor(() => bot.inventory.items().some((i) => i.name === 'player_head'), `${SELLER} holds the mob heads`, 5_000);
    await bot.equip(bot.inventory.items().find((i) => i.name === 'player_head'), 'hand');
    await bot.lookAt(collector.position.offset(0, 1.6, 0), true);
    since = bot.lines.length;
    await bot.activateEntity(collector);
    await waitForLine(bot, /^Sold 2 /, 5_000, since);
    const paid = await balance();
    // A mob with several looks lists its cheapest; heads.yml's first texture may cost more, so
    // each head paid at least the listed price.
    assert(paid >= 2 * Number(each) - 0.001, `paid at least 2 x $${each} (got $${paid})`);
    await sleep(300);
    assert(!bot.inventory.items().some((i) => i.name === 'player_head'), 'the heads were taken');

    // A real player's head (its owner named) with the very same skin is refused, and kept.
    await rcon.command(`minecraft:give ${SELLER} minecraft:player_head[minecraft:profile={name:"MarketSeller",properties:[{name:"textures",value:"${value}"}]}] 1`);
    await waitFor(() => bot.inventory.items().some((i) => i.name === 'player_head'), `${SELLER} holds a player head`, 5_000);
    await bot.equip(bot.inventory.items().find((i) => i.name === 'player_head'), 'hand');
    since = bot.lines.length;
    await bot.activateEntity(collector);
    await waitForLine(bot, /never a player's/, 5_000, since);
    assert(Math.abs((await balance()) - paid) < 0.001, 'nothing paid for a player head');
    assert(bot.inventory.items().some((i) => i.name === 'player_head'), 'the player head was kept');
  });

  console.log(`\nMarket e2e: ${results.length} passed`);
} catch (error) {
  console.error(`FAIL after ${results.length} passed: ${error.message}`);
  process.exitCode = 1;
} finally {
  await rcon.command(`deop ${SELLER}`).catch(() => {});
  await rcon.command(`minecraft:forceload remove ${SIGN.x - 8} ${SIGN.z - 8} ${SIGN.x + 8} ${SIGN.z + 8}`).catch(() => {});
  await rcon.command(`minecraft:forceload remove ${LECTERN.x} ${LECTERN.z}`).catch(() => {});
  await rcon.command(`minecraft:forceload remove ${COLLECTOR.x} ${COLLECTOR.z}`).catch(() => {});
  await quit(...bots);
  rcon.close();
}
