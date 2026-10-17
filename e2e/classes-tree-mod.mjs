import {
  Rcon, assert, createBot, quit, sleep, waitFor,
} from './lib.mjs';

// The tree mod's plugin channel (classes:tree, TreeChannel in the Classes plugin) on a real server.
// A bot that registers the channel the way the Fabric mod does (minecraft:register) stands in for
// the mod: /class tree must send it a JSON snapshot instead of opening the chest, learn/refund
// requests must go through the same rules as a chest click, and a bot without the channel must
// still get the chest. The mod's screen itself can't be rendered by a bot; that check is Joe's.
const CHANNEL = 'classes:tree';

const rcon = await Rcon.connect();
const bots = [];
const results = [];
async function step(name, fn) {
  await fn();
  results.push(name);
  console.log(`PASS ${name}`);
}
const plain = (s) => s.replace(/§./g, '');
const admin = async (args) => plain(await rcon.command(`class admin ${args}`));

/** A bot that announces classes:tree and records every snapshot the server sends on it. */
async function moddedBot(name) {
  const bot = await createBot(name, {
    // Paper sends its minecraft:register before the bot spawns.
    beforeJoin: (b) => {
      b.snapshots = [];
      b.serverChannels = new Set();
      b._client.on('custom_payload', (packet) => {
        if (packet.channel === CHANNEL) b.snapshots.push(JSON.parse(packet.data.toString('utf8')));
        if (packet.channel === 'minecraft:register') {
          // minecraft-protocol hands this payload over already parsed into an array of channel names;
          // Array#toString comma-joins it, so the Buffer split only works for a lone channel.
          const names = Array.isArray(packet.data) ? packet.data.map(String) : packet.data.toString('utf8').split('\0');
          names.forEach((c) => b.serverChannels.add(c));
        }
      });
    },
  });
  bot._client.write('custom_payload', { channel: 'minecraft:register', data: Buffer.from(CHANNEL, 'utf8') });
  await sleep(500);
  return bot;
}
const send = (bot, request) => bot._client.write('custom_payload', {
  channel: CHANNEL, data: Buffer.from(JSON.stringify(request), 'utf8'),
});
async function nextSnapshot(bot, since, what) {
  await waitFor(async () => bot.snapshots.length > since, what, 5_000);
  return bot.snapshots[bot.snapshots.length - 1];
}
const node = (tree, id) => tree.nodes.find((n) => n.id === id);

try {
  const modded = await moddedBot(`TmMod${Date.now() % 100000}`);
  const vanilla = await createBot(`TmVan${Date.now() % 100000}`);
  bots.push(modded, vanilla);
  for (const bot of bots) {
    await admin(`set ${bot.username} none`);
    await admin(`unlock ${bot.username}`);
    await admin(`set ${bot.username} warrior`);
    await admin(`setxp ${bot.username} warrior 260`); // level 5: 4 free talent points
  }

  await step('the server tells clients it accepts classes:tree (the mod only sends once it sees this)', async () => {
    await waitFor(async () => modded.serverChannels.has(CHANNEL), 'minecraft:register naming classes:tree', 5_000);
  });

  await step('/class tree sends the modded client a snapshot instead of opening the chest', async () => {
    let opened = false;
    const onOpen = () => { opened = true; };
    modded.on('windowOpen', onOpen);
    const since = modded.snapshots.length;
    modded.chat('/class tree');
    const tree = await nextSnapshot(modded, since, 'a tree snapshot');
    await sleep(500);
    modded.off('windowOpen', onOpen);
    assert(!opened, 'no chest window opened for the modded client');
    assert(tree.type === 'tree' && tree.v === 1 && tree.open === true, `an opening snapshot (${tree.type} v${tree.v})`);
    assert(tree.class === 'Warrior' && tree.own === true && tree.free === 4, `Warrior, own tree, 4 free (${tree.class} ${tree.free})`);
    assert(tree.nodes.length === 33, `33 nodes (${tree.nodes.length})`);
    assert(node(tree, 'frenzy').state === 'TIER_LOCKED', `frenzy starts tier-locked (${node(tree, 'frenzy').state})`);
  });

  await step('a learn request spends a point and the server answers with an updated snapshot', async () => {
    const since = modded.snapshots.length;
    send(modded, { op: 'learn', id: 'bloodlust' });
    const tree = await nextSnapshot(modded, since, 'an update after learning');
    assert(tree.open === false, 'an update, not a new screen');
    assert(node(tree, 'bloodlust').rank === 1 && tree.free === 3, `bloodlust 1, 3 free (${node(tree, 'bloodlust').rank} ${tree.free})`);
    assert(/bloodlust 1\/\d/.test(await admin(`tree ${modded.username} warrior`)), 'the server saved the rank');
  });

  await step('the server refuses a tier-locked learn, the same as a chest click', async () => {
    const since = modded.snapshots.length;
    send(modded, { op: 'learn', id: 'frenzy' });
    const tree = await nextSnapshot(modded, since, 'an update after the refused learn');
    assert(node(tree, 'frenzy').rank === 0, `frenzy still 0 (${node(tree, 'frenzy').rank})`);
    assert(!/frenzy/.test(await admin(`tree ${modded.username} warrior`)), 'nothing saved for frenzy');
  });

  await step('a refund request takes the rank back', async () => {
    const since = modded.snapshots.length;
    send(modded, { op: 'refund', id: 'bloodlust' });
    const tree = await nextSnapshot(modded, since, 'an update after refunding');
    assert(node(tree, 'bloodlust').rank === 0 && tree.free === 4, `bloodlust 0, 4 free (${node(tree, 'bloodlust').rank} ${tree.free})`);
  });

  await step('hello v4 gets a "Browse classes" row on the hub; an older hello does not', async () => {
    // This bot never said hello before now, so without it /class falls back to the chest the same
    // as an unmodded client - say hello v2 (the hub's own minimum) first to get a baseline hub.
    send(modded, { op: 'hello', v: 2 });
    await sleep(200);
    let since = modded.snapshots.length;
    modded.chat('/class');
    const hubOld = await nextSnapshot(modded, since, 'a hub snapshot');
    assert(hubOld.type === 'hub', `a hub snapshot (${hubOld.type})`);
    assert(!hubOld.entries.some((e) => e.id === 'browse'), 'hello v2: no browse row yet');

    send(modded, { op: 'hello', v: 4 });
    await sleep(200);
    since = modded.snapshots.length;
    modded.chat('/class');
    const hub = await nextSnapshot(modded, since, 'a hub snapshot after hello v4');
    assert(hub.entries.some((e) => e.id === 'browse'), 'a browse row once the client says hello v4');
  });

  await step('hub-browse sends every class with its own passive/ability/weakness at the player\'s level', async () => {
    const since = modded.snapshots.length;
    send(modded, { op: 'hub-browse' });
    const list = await nextSnapshot(modded, since, 'a classlist snapshot');
    assert(list.type === 'classlist' && list.v === 1 && list.open === true, `an opening classlist (${list.type} v${list.v})`);
    assert(list.entries.length === 7, `all 7 classes (${list.entries.length})`);
    const warrior = list.entries.find((e) => e.id === 'warrior');
    assert(warrior.own === true && warrior.locked === false, `own Warrior, not locked (${warrior.own} ${warrior.locked})`);
    const ranger = list.entries.find((e) => e.id === 'ranger');
    assert(ranger.own === false && ranger.locked === true, `Ranger not own, locked by the Warrior pick (${ranger.own} ${ranger.locked})`);
    assert(ranger.lines.some((l) => l.includes('You can change class in')), 'the lock duration rides along as a line');
  });

  await step('browse-pick on a locked class is refused, same as the chest\'s double click', async () => {
    const since = modded.snapshots.length;
    send(modded, { op: 'browse-pick', id: 'ranger' });
    const list = await nextSnapshot(modded, since, 'a classlist refresh after the refused pick');
    assert(list.open === false, 'a refresh, not a reopened screen');
    assert(list.entries.find((e) => e.id === 'warrior').own === true, 'still Warrior: the pick was refused');
  });

  await step('a client without the mod still gets the chest menu', async () => {
    const opened = new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('no chest window opened')), 5_000);
      vanilla.once('windowOpen', (w) => { clearTimeout(timer); resolve(w); });
    });
    vanilla.chat('/class tree');
    const window = await opened;
    assert(window.slots.length >= 54, `a double chest (${window.slots.length} slots)`);
    vanilla.closeWindow(window);
  });

  console.log(`\nclasses-tree-mod: ${results.length}/${results.length} passed`);
} catch (err) {
  console.error(`FAIL after ${results.length} passing steps: ${err.stack ?? err}`);
  process.exitCode = 1;
} finally {
  for (const bot of bots) {
    if (bot) await admin(`set ${bot.username} none`).catch(() => {});
  }
  await quit(...bots);
  rcon.close();
}
