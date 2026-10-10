import { createHash } from 'node:crypto';
import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { Vec3 } from 'vec3';
import { Rcon, assert, createBot, quit, sleep, teleport, waitFor, waitForLine } from './lib.mjs';

// Market: the plugin writes a dyed, glowing Essentials [Sell] sign on the Farmer stall's first spot
// (coordinates from market/src/main/resources/stalls.yml), a bot selling to it gets paid the
// sign's price, and the next in-game day rotates the market. The e2e world has no spawn market, so a floor and a back wall are built around each spot tested.
const SIGN = { x: 420, y: 116, z: -882 };
// stalls.yml's price-lectern: the plugin places it and its price book itself.
const LECTERN = { x: 415, y: 115, z: -865 };
const SELLER = 'MarketSeller';
// The plugin-owned spots come straight from the generated stalls.yml, so moving a stall in the
// generator never leaves this suite testing an empty spot.
const STALLS = readFileSync(new URL('../market/src/main/resources/stalls.yml', import.meta.url), 'utf8');
const spot = (text) => {
  const m = text.match(/\{x: (-?\d+), y: (-?\d+), z: (-?\d+), facing: (\w+)\}/);
  return { x: Number(m[1]), y: Number(m[2]), z: Number(m[3]), facing: m[4] };
};
// Every {x,y,z,facing} on one line, in file order: a shop's `signs:` list has one per home/token.
const spots = (text) => [...text.matchAll(/\{x: (-?\d+), y: (-?\d+), z: (-?\d+), facing: (\w+)\}/g)]
  .map(([, x, y, z, facing]) => ({ x: Number(x), y: Number(y), z: Number(z), facing }));
const section = (key) => STALLS.split(`\n${key}:`)[1].split(/\n\S/)[0];
// stalls.yml's head-stall: 4 signs, the eye-level 3 first (the Head collector NPC was removed 2026-10-09).
const HEAD_STALL = section('head-stall').split('\n').filter((l) => l.includes('{x:')).map(spot);
const HEAD_STALL_CENTER = HEAD_STALL[1];
// stalls.yml's wanted-board: a sign and a player_wall_head.
const WANTED_SIGN = spot(section('wanted-board').split('sign:')[1]);
const WANTED_HEAD = spot(section('wanted-board').split('head:')[1]);
// stalls.yml's shops: each shop's own line (never the whole shops: block, or Homes' 3 signs would
// swallow Cosmetics' too), `signs:` a list (Joe, 2026-10-09: Homes gets 3, one per home).
const shopLine = (id) => section('shops').split('\n').find((l) => l.trim().startsWith(`${id}:`));
const HOMES_SIGNS = spots(shopLine('homes'));
const COSMETICS_SIGN = spots(shopLine('cosmetics'))[0];
// heads.yml, for the texture of whichever mob the Heads stall wants today.
const HEADS = readFileSync(new URL('../market/src/main/resources/heads.yml', import.meta.url), 'utf8');
// The e2e server's own log, to check /cosmetic's exact log line (Player Claude's listener tails this).
const LOG_PATH = fileURLToPath(new URL('../testserver/run-e2e/logs/latest.log', import.meta.url));
const MARKET_CONFIG = fileURLToPath(new URL('../testserver/run-e2e/plugins/Market/config.yml', import.meta.url));
let originalMarketConfig;

const rcon = await Rcon.connect();
const bots = [];
const results = [];
async function step(name, fn) {
  await fn();
  results.push(name);
  console.log(`PASS ${name}`);
}

const plain = (s) => s.replace(/§./g, '');
const unquote = (s) => s.replace(/^"|"$/g, '');

// One path per call: the server shortens long NBT in command feedback ("...").
async function dataGet(loc, path) {
  const out = plain(await rcon.command(`minecraft:data get block ${loc.x} ${loc.y} ${loc.z} ${path}`));
  return out.replace(/^.*has the following block data: /, '').trim();
}

async function signData(path) {
  return dataGet(SIGN, `front_text.${path}`);
}

async function signLine(loc, i) {
  return unquote(await dataGet(loc, `front_text.messages[${i}]`));
}

async function balance(name = SELLER) {
  const out = plain(await rcon.command(`balance ${name}`));
  const match = out.match(/\$([\d,]+(?:\.\d+)?)/);
  assert(match, `balance readable (got ${out.trim()})`);
  return Number(match[1].replace(/,/g, ''));
}

// A sign's facing is the direction it reads from: "south" means stand south of it looking north.
// Each case's fill mirrors the farmer stall's own floor+backing wall, just rotated to the facing.
const APPROACH = {
  south: {
    tp: (l) => [l.x, l.y - 1, l.z + 2],
    look: (p) => p.offset(0.5, 0.5, 0.9),
    build: async (x, y, z) => {
      await rcon.command(`minecraft:fill ${x - 3} ${y - 2} ${z + 1} ${x + 3} ${y - 2} ${z + 5} minecraft:stone`);
      await rcon.command(`minecraft:fill ${x - 3} ${y - 1} ${z} ${x + 3} ${y + 2} ${z + 5} minecraft:air`);
      await rcon.command(`minecraft:fill ${x - 3} ${y - 1} ${z - 1} ${x + 3} ${y + 2} ${z - 1} minecraft:stone`);
    },
  },
  north: {
    tp: (l) => [l.x, l.y - 1, l.z - 2],
    look: (p) => p.offset(0.5, 0.5, 0.1),
    build: async (x, y, z) => {
      await rcon.command(`minecraft:fill ${x - 3} ${y - 2} ${z - 5} ${x + 3} ${y - 2} ${z - 1} minecraft:stone`);
      await rcon.command(`minecraft:fill ${x - 3} ${y - 1} ${z - 5} ${x + 3} ${y + 2} ${z} minecraft:air`);
      await rcon.command(`minecraft:fill ${x - 3} ${y - 1} ${z + 1} ${x + 3} ${y + 2} ${z + 1} minecraft:stone`);
    },
  },
  west: {
    tp: (l) => [l.x - 2, l.y - 1, l.z],
    look: (p) => p.offset(0.1, 0.5, 0.5),
    build: async (x, y, z) => {
      await rcon.command(`minecraft:fill ${x - 5} ${y - 2} ${z - 3} ${x - 1} ${y - 2} ${z + 3} minecraft:stone`);
      await rcon.command(`minecraft:fill ${x - 5} ${y - 1} ${z - 3} ${x} ${y + 2} ${z + 3} minecraft:air`);
      await rcon.command(`minecraft:fill ${x + 1} ${y - 1} ${z - 3} ${x + 1} ${y + 2} ${z + 3} minecraft:stone`);
    },
  },
  east: {
    tp: (l) => [l.x + 2, l.y - 1, l.z],
    look: (p) => p.offset(0.9, 0.5, 0.5),
    build: async (x, y, z) => {
      await rcon.command(`minecraft:fill ${x + 1} ${y - 2} ${z - 3} ${x + 5} ${y - 2} ${z + 3} minecraft:stone`);
      await rcon.command(`minecraft:fill ${x} ${y - 1} ${z - 3} ${x + 5} ${y + 2} ${z + 3} minecraft:air`);
      await rcon.command(`minecraft:fill ${x - 1} ${y - 1} ${z - 3} ${x - 1} ${y + 2} ${z + 3} minecraft:stone`);
    },
  },
};

async function buildApproach(loc, facing) {
  await rcon.command(`minecraft:forceload add ${loc.x - 8} ${loc.z - 8} ${loc.x + 8} ${loc.z + 8}`);
  await APPROACH[facing].build(loc.x, loc.y, loc.z);
}

async function approach(bot, loc, facing) {
  const [x, y, z] = APPROACH[facing].tp(loc);
  await teleport(rcon, bot, x, y, z);
}

async function clickSignAt(bot, loc, facing) {
  const block = bot.blockAt(new Vec3(loc.x, loc.y, loc.z));
  assert(block && block.name.endsWith('wall_sign'), `bot sees the sign at ${loc.x},${loc.y},${loc.z} (got ${block?.name})`);
  await bot.lookAt(APPROACH[facing].look(block.position), true);
  await bot.activateBlock(block).catch(() => {});
}

// The server is offline-mode, so every player's uuid is Java's own name-derived (version 3, MD5)
// uuid: UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(UTF_8)).
function offlineUuidBytes(name) {
  const md5 = createHash('md5').update(`OfflinePlayer:${name}`, 'utf8').digest();
  md5[6] = (md5[6] & 0x0f) | 0x30;
  md5[8] = (md5[8] & 0x3f) | 0x80;
  return md5;
}

// The profile component's `id` is the uuid as 4 signed big-endian ints (Mojang's UUIDUtil.uuidToIntArray).
function uuidIntArray(bytes) {
  const ints = [];
  for (let i = 0; i < 16; i += 4) ints.push(bytes.readInt32BE(i));
  return ints;
}

/** A player_head item's NBT for /give: a profile naming the exact player (by uuid, offline-derived
 * from their name) and, if given, a sirbepy:killed_at PDC long stamp (KillStamp). */
function playerHeadNbt(name, killedAtMillis) {
  const ids = uuidIntArray(offlineUuidBytes(name)).join(',');
  const stamp = killedAtMillis == null ? '' : `,custom_data={PublicBukkitValues:{"sirbepy:killed_at":${killedAtMillis}L}}`;
  return `minecraft:profile={id:[I;${ids}],name:"${name}"}${stamp}`;
}

try {
  await rcon.command(`minecraft:forceload add ${SIGN.x - 8} ${SIGN.z - 8} ${SIGN.x + 8} ${SIGN.z + 8}`);
  await rcon.command(`minecraft:forceload add ${LECTERN.x} ${LECTERN.z}`);
  await rcon.command(`minecraft:fill ${SIGN.x - 3} ${SIGN.y - 2} ${SIGN.z + 1} ${SIGN.x + 3} ${SIGN.y - 2} ${SIGN.z + 5} minecraft:stone`);
  await rcon.command(`minecraft:fill ${SIGN.x - 3} ${SIGN.y - 1} ${SIGN.z} ${SIGN.x + 3} ${SIGN.y + 2} ${SIGN.z + 5} minecraft:air`);
  await rcon.command(`minecraft:fill ${SIGN.x - 3} ${SIGN.y - 1} ${SIGN.z - 1} ${SIGN.x + 3} ${SIGN.y + 2} ${SIGN.z - 1} minecraft:stone`);
  // Built up front so the very first /market rotate (below) paints every one of these at once.
  await buildApproach(HEAD_STALL_CENTER, 'north');
  await buildApproach(WANTED_SIGN, 'west');
  // The middle sign: its approach box reaches 3 blocks either side, covering all 3 Homes signs
  // (one block apart) in a single build.
  await buildApproach(HOMES_SIGNS[1], 'west');
  await buildApproach(COSMETICS_SIGN, 'east');

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
    const unq = (s) => s.replace(/^"|"$/g, '');
    amount = Number(unq(await signData('messages[1]')));
    item = unq(await signData('messages[2]'));
    price = Number(unq(await signData('messages[3]')).replace('$', ''));
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
    await waitForLine(bot, /^Heads$/, 5_000, since);
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
    // prices.yml (regenerated alongside this market rework): diamond is $22, so a stack of 64 is $1408.
    await waitForLine(bot, /Suggested price for Diamond: \$22\.00 each, \$1408\.00 for a stack of 64/, 5_000, since);
    since = bot.lines.length;
    bot.chat('/price');
    await waitForLine(bot, /Suggested price for Diamond: \$22\.00/, 5_000, since);
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

  await step('the Heads stall writes one sign per wanted mob', async () => {
    for (const slot of HEAD_STALL) {
      const color = await dataGet(slot, 'front_text.color');
      assert(color === '"red"', `head-stall sign dyed red at ${slot.x},${slot.y},${slot.z} (got ${color})`);
      const glow = await dataGet(slot, 'front_text.has_glowing_text');
      assert(glow === '1b', `head-stall sign glows at ${slot.x},${slot.y},${slot.z} (got ${glow})`);
      const name = await signLine(slot, 0);
      assert(name.length > 0, `head-stall sign names a wanted mob at ${slot.x},${slot.y},${slot.z}`);
      const headWord = await signLine(slot, 1);
      assert(headWord === 'head', `line 2 reads "head" (got ${headWord})`);
      const priceLine = await signLine(slot, 2);
      assert(/^\$/.test(priceLine), `line 3 is a price (got ${priceLine})`);
      const rightClick = await signLine(slot, 3);
      assert(rightClick === 'Right-click', `line 4 prompts Right-click (got ${rightClick})`);
    }
  });

  let mobName;
  let headEach;
  await step('the Heads stall buys a wanted mob head from a non-op', async () => {
    const [bot] = bots;
    await rcon.command(`deop ${SELLER}`);
    await approach(bot, HEAD_STALL_CENTER, 'north');

    let since = bot.lines.length;
    bot.chat('/market');
    await waitForLine(bot, /^Heads$/, 5_000, since);
    const heading = bot.lines.findIndex((l, i) => i >= since && /^Heads$/.test(l));
    const line = await waitForLine(bot, /^ - (.+) heads? \$/, 5_000, heading);
    const match = line.match(/^ - (.+?) heads? (\$[\d.]+(?: to \$[\d.]+)?)$/);
    assert(match, `/market's Heads block lists a mob (got ${line})`);
    [, mobName] = match;
    const nums = match[2].match(/[\d.]+/g).map(Number);
    headEach = Math.min(...nums);
    const block = HEADS.split(/\r?\n {2}(?=[a-z_]+:\r?\n)/).find((b) => b.includes(`name: "${mobName}"`));
    assert(block, `heads.yml has ${mobName}`);
    const texture = block.match(/texture: (\w+)/)[1];
    const value = Buffer.from(JSON.stringify({ textures: { SKIN: { url: `http://textures.minecraft.net/texture/${texture}` } } })).toString('base64');
    console.log(`  wanted today: ${mobName} from $${headEach} a head`);

    await rcon.command(`minecraft:clear ${SELLER}`);
    await rcon.command(`eco set ${SELLER} 0`);
    await rcon.command(`minecraft:give ${SELLER} minecraft:player_head[minecraft:profile={properties:[{name:"textures",value:"${value}"}]}] 2`);
    await waitFor(() => bot.inventory.items().some((i) => i.name === 'player_head'), `${SELLER} holds the mob heads`, 5_000);
    await bot.equip(bot.inventory.items().find((i) => i.name === 'player_head'), 'hand');
    since = bot.lines.length;
    await clickSignAt(bot, HEAD_STALL_CENTER, 'north');
    await waitForLine(bot, /^Sold 2 /, 5_000, since);
    const paid = await balance();
    // A mob with several looks lists its cheapest; heads.yml's first texture may cost more, so
    // each head paid at least the listed price.
    assert(paid >= 2 * headEach - 0.001, `paid at least 2 x $${headEach} (got $${paid})`);
    await sleep(300);
    assert(!bot.inventory.items().some((i) => i.name === 'player_head'), 'the heads were taken');
  });

  await step('the Heads stall refuses a real player\'s head, even wearing a wanted skin', async () => {
    const [bot] = bots;
    const block = HEADS.split(/\r?\n {2}(?=[a-z_]+:\r?\n)/).find((b) => b.includes(`name: "${mobName}"`));
    const texture = block.match(/texture: (\w+)/)[1];
    const value = Buffer.from(JSON.stringify({ textures: { SKIN: { url: `http://textures.minecraft.net/texture/${texture}` } } })).toString('base64');
    const before = await balance();
    await rcon.command(`minecraft:give ${SELLER} minecraft:player_head[minecraft:profile={name:"MarketSeller",properties:[{name:"textures",value:"${value}"}]}] 1`);
    await waitFor(() => bot.inventory.items().some((i) => i.name === 'player_head'), `${SELLER} holds a player head`, 5_000);
    await bot.equip(bot.inventory.items().find((i) => i.name === 'player_head'), 'hand');
    const since = bot.lines.length;
    await clickSignAt(bot, HEAD_STALL_CENTER, 'north');
    await waitForLine(bot, /never a player's/, 5_000, since);
    assert(Math.abs((await balance()) - before) < 0.001, 'nothing paid for a player head');
    assert(bot.inventory.items().some((i) => i.name === 'player_head'), 'the player head was kept');
  });

  // Player of the Day (the Wanted board). Two bots stay online so the admin rotate below can land
  // on either one as the target; whichever it picks, the e2e server is offline-mode so its uuid is
  // the same deterministic, name-derived one `playerHeadNbt` computes, letting a head of the exact
  // target be given to any bot without needing to know which one got picked ahead of time.
  const target2 = await createBot('MarketTarget');
  bots.push(target2);
  await rcon.command('eco set MarketTarget 0');
  // The Heads stall step above left a refused player_head in SELLER's hand (kept, unsold): clear it
  // so a later `.find(i => i.name === 'player_head')` here can only ever see the head this section
  // itself just gave, never a stray leftover from an earlier step.
  await rcon.command(`minecraft:clear ${SELLER} minecraft:player_head`);
  await rcon.command('minecraft:clear MarketTarget minecraft:player_head');

  let wantedTargetName;
  const wantedBotFor = (name) => bots.find((b) => b.username === name);

  // wanted.min-online (4 on live) posts no bounty with fewer players online, and this suite has two
  // bots; drop it to 1 for the run, restored in finally.
  originalMarketConfig = readFileSync(MARKET_CONFIG, 'utf8');
  assert(/^  min-online:/m.test(originalMarketConfig),
    "the run dir's Market config.yml has wanted.min-online (delete a stale run-e2e Market config.yml and restart)");
  writeFileSync(MARKET_CONFIG, originalMarketConfig.replace(/^(  min-online:).*$/m, '$1 1'));
  await rcon.command('market reload');

  // pick() takes an online player 80% of the time and otherwise "everyone who's ever joined"
  // (WantedBoard, wanted.online-chance), and that pool only ever holds a player once Bukkit has seen
  // them disconnect at least once - neither bot has, this session, so that branch picks nobody ("No
  // bounty") or a stranger. Retry the rotate until it lands on one of our online bots.
  async function rotateWantedBoard() {
    for (let i = 0; i < 20; i++) {
      await waitFor(async () => /picked new buyers/.test(plain(await rcon.command('market rotate'))), 'market rotate (wanted)', 10_000);
      wantedTargetName = await signLine(WANTED_SIGN, 1);
      if (wantedBotFor(wantedTargetName)) return;
    }
    throw new Error(`the Wanted board never landed on one of our bots after 20 rotates (last: ${wantedTargetName})`);
  }

  await step('the Wanted board picks a target and refuses that player\'s own head', async () => {
    await approach(target2, WANTED_SIGN, 'west');
    await rotateWantedBoard();
    const targetBot = wantedBotFor(wantedTargetName);
    await approach(targetBot, WANTED_SIGN, 'west');
    await rcon.command(`minecraft:give ${wantedTargetName} minecraft:player_head[${playerHeadNbt(wantedTargetName)}] 1`);
    await waitFor(() => targetBot.inventory.items().some((i) => i.name === 'player_head'), `${wantedTargetName} holds their own head`, 5_000);
    await targetBot.equip(targetBot.inventory.items().find((i) => i.name === 'player_head'), 'hand');
    const since = targetBot.lines.length;
    await clickSignAt(targetBot, WANTED_SIGN, 'west');
    await waitForLine(targetBot, /can't turn in your own head/, 5_000, since);
    assert(targetBot.inventory.items().some((i) => i.name === 'player_head'), 'the own head was kept, not sold');
  });

  await step('an unstamped head pays the old-head share, claims the board, and a repeat sale is refused', async () => {
    const targetBot = wantedBotFor(wantedTargetName);
    const sellerBot = bots.find((b) => b.username !== wantedTargetName && (b.username === SELLER || b.username === 'MarketTarget'));
    const oldLine = await signLine(WANTED_SIGN, 3);
    const oldPrice = Number(oldLine.match(/\$(\d+)/)[1]);
    const before = await balance(sellerBot.username);

    await approach(sellerBot, WANTED_SIGN, 'west');
    await rcon.command(`minecraft:give ${sellerBot.username} minecraft:player_head[${playerHeadNbt(wantedTargetName)}] 1`);
    await waitFor(() => sellerBot.inventory.items().some((i) => i.name === 'player_head'), `${sellerBot.username} holds ${wantedTargetName}'s head`, 5_000);
    await sellerBot.equip(sellerBot.inventory.items().find((i) => i.name === 'player_head'), 'hand');
    let since = sellerBot.lines.length;
    await clickSignAt(sellerBot, WANTED_SIGN, 'west');
    await waitForLine(sellerBot, /brought in/, 5_000, since);
    const after = await balance(sellerBot.username);
    assert(Math.abs(after - before - oldPrice) < 0.001, `paid the old-head price $${oldPrice} (before $${before}, after $${after})`);
    assert(!sellerBot.inventory.items().some((i) => i.name === 'player_head'), 'the head was taken');

    assert((await signLine(WANTED_SIGN, 2)) === 'Claimed by', 'the sign now shows Claimed by');
    assert((await signLine(WANTED_SIGN, 3)) === sellerBot.username, 'the sign names the seller');

    // A second head of the same target, same period: refused, nothing paid again.
    await rcon.command(`minecraft:give ${sellerBot.username} minecraft:player_head[${playerHeadNbt(wantedTargetName)}] 1`);
    await waitFor(() => sellerBot.inventory.items().some((i) => i.name === 'player_head'), `${sellerBot.username} holds a second head`, 5_000);
    await sellerBot.equip(sellerBot.inventory.items().find((i) => i.name === 'player_head'), 'hand');
    since = sellerBot.lines.length;
    await clickSignAt(sellerBot, WANTED_SIGN, 'west');
    await waitForLine(sellerBot, /already brought in/, 5_000, since);
    assert(Math.abs((await balance(sellerBot.username)) - after) < 0.001, 'the second attempt paid nothing');
    assert(sellerBot.inventory.items().some((i) => i.name === 'player_head'), 'the second head was kept, unsold');
  });

  await step('a kill stamped after the bounty was posted pays the fresh, full price', async () => {
    await rotateWantedBoard();
    const sellerBot = bots.find((b) => b.username !== wantedTargetName);
    const freshLine = await signLine(WANTED_SIGN, 2);
    const freshPrice = Number(freshLine.match(/\$(\d+)/)[1]);
    const before = await balance(sellerBot.username);

    await approach(sellerBot, WANTED_SIGN, 'west');
    // sellerBot may be the same bot that was kept holding the previous step's refused repeat head:
    // clear it first so the equip below can only find the fresh, stamped head given next.
    await rcon.command(`minecraft:clear ${sellerBot.username} minecraft:player_head`);
    // Stamped a few seconds in the future: always at-or-after whatever postedAt the rotate above
    // just set (clock.get() on the server), with no need to read the server's own clock.
    const killedAt = Date.now() + 10_000;
    await rcon.command(`minecraft:give ${sellerBot.username} minecraft:player_head[${playerHeadNbt(wantedTargetName, killedAt)}] 1`);
    await waitFor(() => sellerBot.inventory.items().some((i) => i.name === 'player_head'), `${sellerBot.username} holds a fresh head`, 5_000);
    await sellerBot.equip(sellerBot.inventory.items().find((i) => i.name === 'player_head'), 'hand');
    const since = sellerBot.lines.length;
    await clickSignAt(sellerBot, WANTED_SIGN, 'west');
    await waitForLine(sellerBot, /fresh kill/, 5_000, since);
    const after = await balance(sellerBot.username);
    // The bounty grows by about $1 every 2.4 minutes the target plays, so it may tick over between
    // reading the sign and the sale.
    assert(Math.abs(after - before - freshPrice) <= 1, `paid the fresh, full price $${freshPrice} (before $${before}, after $${after})`);
  });

  await step('a target who stays alive until the next bounty is paid half its value', async () => {
    await rotateWantedBoard();
    const targetBot = wantedBotFor(wantedTargetName);
    const value = Number((await signLine(WANTED_SIGN, 2)).match(/\$(\d+)/)[1]);
    const before = await balance(wantedTargetName);
    const since = targetBot.lines.length;
    await rcon.command('minecraft:time add 24000');
    const line = await waitForLine(targetBot, /survived the bounty and earned \$\d+/, 20_000, since);
    const earned = Number(line.match(/earned \$(\d+)/)[1]);
    assert(Math.abs(earned - Math.round(value / 2)) <= 1, `earned half the $${value} bounty (got $${earned})`);
    const after = await balance(wantedTargetName);
    assert(Math.abs(after - before - earned) < 0.001, `the target was paid $${earned} (before $${before}, after $${after})`);
  });

  await step('a target who died since the bounty went up gets no survivor reward', async () => {
    await rotateWantedBoard();
    const targetBot = wantedBotFor(wantedTargetName);
    await rcon.command(`minecraft:kill ${wantedTargetName}`);
    await waitFor(() => targetBot.health > 0, `${wantedTargetName} respawned`, 10_000);
    const before = await balance(wantedTargetName);
    const since = targetBot.lines.length;
    await rcon.command('minecraft:time add 24000');
    await waitForLine(targetBot, /spawn market/i, 15_000, since);
    // The survivor line would come in the same 10 s check as the rotation line; give it a beat.
    await sleep(1_000);
    assert(!targetBot.lines.slice(since).some((l) => /survived the bounty/.test(l)), 'no survivor broadcast');
    assert(Math.abs((await balance(wantedTargetName)) - before) < 0.001, 'nothing paid to a target who died');
  });

  await step('the Homes shop sells the 4th home via its own sign and charges its price', async () => {
    const [bot] = bots;
    await rcon.command(`minecraft:clear ${SELLER}`);
    await rcon.command(`eco set ${SELLER} 2000`);
    const before = await balance();
    await approach(bot, HOMES_SIGNS[0], 'west');
    const since = bot.lines.length;
    await clickSignAt(bot, HOMES_SIGNS[0], 'west');
    await waitForLine(bot, /You can now set 4 homes/, 5_000, since);
    const after = await balance();
    assert(Math.abs(before - after - 500) < 0.001, `charged $500 for the 4th home (before $${before}, after $${after})`);
    // LuckPerms answers `permission check`/`info` only through its own async messaging, which the
    // RCON response never carries (confirmed live: both the reply and the log stay empty for a
    // `check`, even though a `set` logs a line) - so read ConsoleHomeGrant's own
    // `lp ... permission set huskhomes.max_homes.4 true` back from the server log instead, the one
    // channel LuckPerms reliably logs a grant to.
    await waitFor(() => new RegExp(`Set huskhomes\\.max_homes\\.4 to true for ${SELLER}`, 'i').test(readFileSync(LOG_PATH, 'utf8')),
      `LuckPerms logs granting huskhomes.max_homes.4 to ${SELLER}`, 10_000);
  });

  await step('clicking the 6th-home sign before the 5th refuses with no charge', async () => {
    const [bot] = bots;
    const before = await balance();
    await approach(bot, HOMES_SIGNS[2], 'west');
    const since = bot.lines.length;
    await clickSignAt(bot, HOMES_SIGNS[2], 'west');
    await waitForLine(bot, /Buy your 5th home first/, 5_000, since);
    const after = await balance();
    assert(Math.abs(before - after) < 0.001, `the refusal charged nothing (before $${before}, after $${after})`);
  });

  await step('the Cosmetics shop sells a token, and /cosmetic spends it to stamp the held item', async () => {
    const [bot] = bots;
    await rcon.command(`minecraft:clear ${SELLER}`);
    await rcon.command(`eco set ${SELLER} 3000`);
    const before = await balance();
    await approach(bot, COSMETICS_SIGN, 'east');
    const since = bot.lines.length;
    await clickSignAt(bot, COSMETICS_SIGN, 'east');
    await waitForLine(bot, /bought a Cosmetic Token/, 5_000, since);
    const after = await balance();
    assert(Math.abs(before - after - 2500) < 0.001, `charged $2500 for the token (before $${before}, after $${after})`);
    await waitFor(() => bot.inventory.items().some((i) => i.name === 'nether_star'), `${SELLER} holds a Cosmetic Token`, 5_000);

    await rcon.command(`minecraft:give ${SELLER} minecraft:diamond 1`);
    await waitFor(() => bot.inventory.items().filter((i) => i.name === 'diamond').length > 0, `${SELLER} holds a diamond`, 5_000);
    await bot.equip(bot.inventory.items().find((i) => i.name === 'diamond'), 'hand');

    const since2 = bot.lines.length;
    bot.chat('/cosmetic a shiny purple glow');
    await waitForLine(bot, /Token used! Request #(\d+)/, 5_000, since2);
    assert(!bot.inventory.items().some((i) => i.name === 'nether_star'), 'the Cosmetic Token was spent');

    const cmd = plain(await rcon.command(`minecraft:data get entity ${SELLER} SelectedItem.components."minecraft:custom_model_data".strings[0]`));
    assert(/cosmetic_\d+/.test(cmd), `the held item carries a cosmetic_<id> custom_model_data string (got ${cmd})`);

    await sleep(500); // the plugin's logger.info() call to flush to disk
    const log = readFileSync(LOG_PATH, 'utf8');
    assert(new RegExp(`Cosmetic request #\\d+ from ${SELLER} \\([0-9a-fA-F-]+\\): diamond - a shiny purple glow`).test(log),
      'the exact log line Player Claude\'s listener tails is in the server log');
  });

  console.log(`\nMarket e2e: ${results.length} passed`);
} catch (error) {
  console.error(`FAIL after ${results.length} passed: ${error.message}`);
  process.exitCode = 1;
} finally {
  if (originalMarketConfig !== undefined) {
    writeFileSync(MARKET_CONFIG, originalMarketConfig);
    await rcon.command('market reload').catch(() => {});
  }
  await rcon.command(`deop ${SELLER}`).catch(() => {});
  await rcon.command(`minecraft:forceload remove ${SIGN.x - 8} ${SIGN.z - 8} ${SIGN.x + 8} ${SIGN.z + 8}`).catch(() => {});
  await rcon.command(`minecraft:forceload remove ${LECTERN.x} ${LECTERN.z}`).catch(() => {});
  await rcon.command(`minecraft:forceload remove ${HEAD_STALL_CENTER.x - 8} ${HEAD_STALL_CENTER.z - 8} ${HEAD_STALL_CENTER.x + 8} ${HEAD_STALL_CENTER.z + 8}`).catch(() => {});
  await rcon.command(`minecraft:forceload remove ${WANTED_SIGN.x - 8} ${WANTED_SIGN.z - 8} ${WANTED_SIGN.x + 8} ${WANTED_SIGN.z + 8}`).catch(() => {});
  await rcon.command(`minecraft:forceload remove ${HOMES_SIGNS[1].x - 8} ${HOMES_SIGNS[1].z - 8} ${HOMES_SIGNS[1].x + 8} ${HOMES_SIGNS[1].z + 8}`).catch(() => {});
  await rcon.command(`minecraft:forceload remove ${COSMETICS_SIGN.x - 8} ${COSMETICS_SIGN.z - 8} ${COSMETICS_SIGN.x + 8} ${COSMETICS_SIGN.z + 8}`).catch(() => {});
  await quit(...bots);
  rcon.close();
}
