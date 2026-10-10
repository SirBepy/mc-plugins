import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { Rcon, assert, createBot, nbtText, quit, sleep } from './lib.mjs';

// /changelog (Tweaks, commit ec66d76): any player gets a written book of what changed at each live
// restart, built fresh from plugins/Tweaks/changelog.yml on every use (Changelog.java sorts newest
// date first, entries sharing a date keeping the file's own order). Paper's Player#openBook sends the
// book as a held-slot update - on this 26.2 e2e server that's a `set_player_inventory` packet
// carrying a written_book_content component, sent right before the `open_book` packet and followed
// by a second `set_player_inventory` that restores whatever the player was holding - so this suite
// listens for that component instead of placing the book somewhere in the world, since nothing else
// exposes its page text. Verified by probing the real packets against this server: mineflayer 26.1
// via ViaBackwards does carry the component through untouched.
const CHANGELOG_FILE = fileURLToPath(new URL('../testserver/run-e2e/plugins/Tweaks/changelog.yml', import.meta.url));

const rcon = await Rcon.connect();
const bot = await createBot('ChangelogBot');
const results = [];
async function step(name, fn) {
  await fn();
  results.push(name);
  console.log(`PASS ${name}`);
}

/** Mirrors Changelog.pages()'s sort: newest date first, same-date entries keeping file order (a
 * stable sort, same as Java's Stream.sorted()). No yaml library in this project's e2e deps, so this
 * is a small regex parse of the known `entries:` block shape, same style as market.mjs's own. */
function newestTitle(yamlText) {
  const section = yamlText.split(/\nentries:/)[1] ?? '';
  const blocks = section.split(/\n {2}- /).slice(1);
  const entries = blocks.map((block, i) => ({
    date: block.match(/date:\s*"?(\S+?)"?\s*\n/)?.[1],
    title: block.match(/title:\s*(.+)/)?.[1]?.trim(),
    i,
  }));
  entries.sort((a, b) => (a.date === b.date ? a.i - b.i : a.date < b.date ? 1 : -1));
  assert(entries[0]?.title, `changelog.yml parsed at least one dated, titled entry (got ${JSON.stringify(entries)})`);
  return entries[0].title;
}

/** Waits for the `set_player_inventory` packet carrying a written_book_content component - the one
 * Paper's openBook sends just before its `open_book` packet - and returns that component's data. */
function captureBookContent(timeoutMs = 5_000) {
  return new Promise((resolve, reject) => {
    const onInv = (packet) => {
      const comp = packet.contents?.components?.find((c) => c.type === 'written_book_content');
      if (!comp) return;
      clearTimeout(timer);
      bot._client.removeListener('set_player_inventory', onInv);
      resolve(comp.data);
    };
    const timer = setTimeout(() => {
      bot._client.removeListener('set_player_inventory', onInv);
      reject(new Error(`${bot.username} got no written_book_content inventory update`));
    }, timeoutMs);
    bot._client.on('set_player_inventory', onInv);
  });
}

/** Runs /changelog and returns page one's plain text (NBT text-component, same shape chat uses). */
async function openChangelog() {
  const contentP = captureBookContent();
  const openedP = new Promise((resolve) => bot._client.once('open_book', resolve));
  bot.chat('/changelog');
  await Promise.race([openedP, sleep(5_000).then(() => { throw new Error('/changelog opened no book (no open_book packet)'); })]);
  const data = await contentP;
  assert(data.pages?.length > 0, 'the book has at least one page');
  return nbtText(data.pages[0].content);
}

let originalChangelog;
try {
  originalChangelog = readFileSync(CHANGELOG_FILE, 'utf8');

  await step('a non-op player runs /changelog and page one leads with the newest entry\'s title', async () => {
    const title = newestTitle(originalChangelog);
    const page1 = await openChangelog();
    assert(page1.startsWith(title), `page one starts with "${title}" (got ${JSON.stringify(page1.slice(0, 80))})`);
  });

  await step('editing changelog.yml on the server and running /changelog again shows the new entry, no restart', async () => {
    const uniqueTitle = `E2E probe ${Date.now()}`;
    const injected = originalChangelog.replace('entries:', `entries:\n  - date: 2099-01-01\n    title: ${uniqueTitle}\n    lines:\n      - probe line for the e2e suite\n`);
    writeFileSync(CHANGELOG_FILE, injected);
    const page1 = await openChangelog();
    assert(page1.startsWith(uniqueTitle), `page one starts with the freshly added "${uniqueTitle}" (got ${JSON.stringify(page1.slice(0, 80))})`);
  });

  await step('console changelog gets the players-only refusal instead of an error', async () => {
    const reply = (await rcon.command('changelog')).trim();
    assert(/Only players can open the changelog book\./.test(reply), `console got the players-only refusal (got ${JSON.stringify(reply)})`);
  });

  console.log(`\nChangelog e2e: ${results.length} passed`);
} catch (error) {
  console.error(`FAIL after ${results.length} passed: ${error.message}`);
  process.exitCode = 1;
} finally {
  if (originalChangelog !== undefined) writeFileSync(CHANGELOG_FILE, originalChangelog);
  await quit(bot);
  rcon.close();
}
