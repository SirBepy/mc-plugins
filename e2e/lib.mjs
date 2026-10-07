import mineflayer from 'mineflayer';
import net from 'node:net';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const runDir = path.join(here, '..', 'testserver', 'run-e2e');

export const HOST = '127.0.0.1';
export const PORT = 25565;
// Mineflayer tops out at 26.1; ViaBackwards on the 26.2 e2e server bridges the gap.
export const BOT_VERSION = '26.1';

export const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function serverProperty(key, dir) {
  const text = fs.readFileSync(path.join(dir, 'server.properties'), 'utf8');
  const line = text.split(/\r?\n/).find((l) => l.startsWith(key + '='));
  return line ? line.slice(key.length + 1).trim() : undefined;
}

/** Minimal Source RCON client: auth once, then one request per command. */
export class Rcon {
  // dir: folder holding server.properties to read rcon.port/rcon.password from. Defaults to the
  // e2e test server; server-check.mjs passes server/local instead.
  static async connect(dir = runDir) {
    const rcon = new Rcon();
    await rcon.#open(Number(serverProperty('rcon.port', dir) ?? 25575), serverProperty('rcon.password', dir));
    return rcon;
  }

  #socket;
  #nextId = 1;
  #pending = new Map();
  #buffer = Buffer.alloc(0);

  #open(port, password) {
    return new Promise((resolve, reject) => {
      this.#socket = net.connect(port, HOST, async () => {
        try {
          await this.#request(3, password);
          resolve();
        } catch (e) {
          reject(e);
        }
      });
      this.#socket.on('error', reject);
      this.#socket.on('data', (chunk) => this.#onData(chunk));
    });
  }

  #onData(chunk) {
    this.#buffer = Buffer.concat([this.#buffer, chunk]);
    while (this.#buffer.length >= 4) {
      const length = this.#buffer.readInt32LE(0);
      if (this.#buffer.length < 4 + length) return;
      const id = this.#buffer.readInt32LE(4);
      const body = this.#buffer.toString('utf8', 12, 4 + length - 2);
      this.#buffer = this.#buffer.subarray(4 + length);
      if (id === -1) {
        for (const { reject } of this.#pending.values()) reject(new Error('RCON auth failed'));
        this.#pending.clear();
        continue;
      }
      const waiter = this.#pending.get(id);
      if (waiter) {
        this.#pending.delete(id);
        waiter.resolve(body);
      }
    }
  }

  #request(type, body) {
    const id = this.#nextId++;
    const payload = Buffer.from(body, 'utf8');
    const packet = Buffer.alloc(14 + payload.length);
    packet.writeInt32LE(10 + payload.length, 0);
    packet.writeInt32LE(id, 4);
    packet.writeInt32LE(type, 8);
    payload.copy(packet, 12);
    return new Promise((resolve, reject) => {
      this.#pending.set(id, { resolve, reject });
      this.#socket.write(packet);
      setTimeout(() => {
        if (this.#pending.delete(id)) reject(new Error(`RCON timeout: ${body}`));
      }, 10_000);
    });
  }

  command(cmd) {
    return this.#request(2, cmd);
  }

  close() {
    this.#socket?.end();
  }
}

/** Bots record every chat/system line they receive as plain text in `bot.lines`. */
export function createBot(username) {
  return new Promise((resolve, reject) => {
    const bot = mineflayer.createBot({ host: HOST, port: PORT, username, version: BOT_VERSION, auth: 'offline' });
    bot.lines = [];
    bot.clicks = [];
    bot.on('messagestr', (text) => bot.lines.push(text));
    bot.on('message', (message) => collectClickCommands(message.json, bot.clicks));
    // Mineflayer renders player chat from plainMessage; a real client shows the server-rendered
    // unsignedChatContent (where Names puts the prefix and colored name), so record that too.
    bot._client.on('player_chat', (packet) => {
      if (packet.unsignedChatContent) bot.lines.push(nbtText(packet.unsignedChatContent));
    });
    // A rejected createBot() must not leave a connected bot behind: nobody is driving it from here
    // on, so it just stands wherever it spawned (seen: repeatedly killed by a zombie for 15+
    // minutes), and its still-open socket keeps the whole suite process alive long after the
    // suite's own try/catch has already moved on and printed its FAIL line.
    const fail = (error) => {
      bot.quit();
      reject(error);
    };
    const timer = setTimeout(() => fail(new Error(`${username} did not spawn`)), 60_000);
    bot.once('spawn', async () => {
      clearTimeout(timer);
      try {
        // Teleporting a bot before its chunks arrive gets it kicked for an invalid move packet, and
        // every later block lookup needs real chunk data, so this failure can't just be swallowed.
        // This handler isn't awaited by anything, though, so an uncaught rejection here would crash
        // the whole suite process instead of surfacing as this call's own rejection.
        await bot.waitForChunksToLoad();
        await sleep(500);
        resolve(bot);
      } catch (error) {
        fail(new Error(`${username}: chunks never loaded after spawn: ${error.message}`));
      }
    });
    bot.on('kicked', (reason) => console.error(`[${username}] kicked: ${JSON.stringify(reason)}`));
    bot.on('kicked', (reason) => fail(new Error(`${username} kicked: ${JSON.stringify(reason)}`)));
    // A chunk-load timeout above can itself get the bot kicked, which can emit 'error' more than
    // once; `once` would leave the second occurrence with no listener, and Node kills the process
    // on an unhandled 'error' event. fail() on an already-settled promise is a harmless no-op.
    bot.on('error', fail);
  });
}

/** Plain text of an NBT-encoded chat component ({type, value} wrappers, "" key for bare strings). */
export function nbtText(node) {
  if (node == null) return '';
  if (typeof node !== 'object') return String(node);
  if ('type' in node && 'value' in node && Object.keys(node).length === 2) {
    if (node.type === 'list') return (node.value.value ?? []).map(nbtText).join('');
    return nbtText(node.value);
  }
  if (Array.isArray(node)) return node.map(nbtText).join('');
  return nbtText(node.text) + nbtText(node['']) + nbtText(node.extra);
}

/** Every text_display the bot can see, with its plain text (all text-like metadata joined). */
export function textDisplays(bot) {
  return Object.values(bot.entities).filter((e) => e.name === 'text_display').map((e) => ({
    entity: e,
    text: e.metadata.map((value) => (value && typeof value === 'object' ? nbtText(value) : '')).join(' '),
  }));
}

// Chat JSON spells click events as clickEvent (older) or click_event (1.21.5+).
function collectClickCommands(node, out) {
  if (!node || typeof node !== 'object') return;
  if (Array.isArray(node)) {
    node.forEach((child) => collectClickCommands(child, out));
    return;
  }
  const click = node.clickEvent ?? node.click_event;
  if (click && click.action === 'run_command') {
    out.push(click.value ?? click.command);
  }
  for (const value of Object.values(node)) {
    if (value && typeof value === 'object') collectClickCommands(value, out);
  }
}

export async function waitForClick(bot, pattern, timeoutMs = 10_000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    const hit = bot.clicks.find((c) => pattern.test(c));
    if (hit) return hit;
    await sleep(100);
  }
  throw new Error(`${bot.username} never got a clickable ${pattern}. Clicks: ${JSON.stringify(bot.clicks)}`);
}

/** Right-clicks a container block; resolves true if a window opened, false if the server refused. */
export async function tryOpen(bot, block, timeoutMs = 3_000) {
  return new Promise((resolve) => {
    const onOpen = (window) => {
      clearTimeout(timer);
      bot.closeWindow(window);
      resolve(true);
    };
    const timer = setTimeout(() => {
      bot.removeListener('windowOpen', onOpen);
      resolve(false);
    }, timeoutMs);
    bot.once('windowOpen', onOpen);
    bot.activateBlock(block).catch(() => {});
  });
}

export async function waitForLine(bot, pattern, timeoutMs = 10_000, since = 0) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    const hit = bot.lines.slice(since).find((l) => pattern.test(l));
    if (hit) return hit;
    await sleep(100);
  }
  throw new Error(`${bot.username} never saw ${pattern}. Last lines:\n  ${bot.lines.slice(-8).join('\n  ')}`);
}

// 20s default: a busy test server (fresh chunk generation) can stall the main thread for several seconds.
export async function waitFor(check, description, timeoutMs = 20_000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    if (await check()) return;
    await sleep(100);
  }
  throw new Error(`Timed out waiting for: ${description}`);
}

export function assert(condition, message) {
  if (!condition) throw new Error(`Assertion failed: ${message}`);
}

/** Vanilla /tp (EssentialsX shadows the plain name), then waits for the destination chunks. */
export async function teleport(rcon, bot, x, y, z) {
  await rcon.command(`minecraft:tp ${bot.username} ${x} ${y} ${z}`);
  await waitFor(() => bot.entity.position.distanceTo({ x: x + 0.5, y, z: z + 0.5 }) < 2
    || bot.entity.position.distanceTo({ x, y, z }) < 2, `${bot.username} arrived at ${x} ${y} ${z}`);
  // Far chunks of freshly generated terrain can take longer; the forceloaded platform is what matters.
  await bot.waitForChunksToLoad().catch(() => {});
  await sleep(500);
}

export async function quit(...bots) {
  for (const bot of bots) {
    if (bot) bot.quit();
  }
  await sleep(500);
}

/** A fresh spot every run, so protections and grants left by an earlier run can't leak in. */
export function randomOrigin() {
  return { bx: 16 * Math.floor(Math.random() * 40 - 20), bz: 16 * Math.floor(Math.random() * 40 - 20) };
}

/** Forceloads around (bx, bz) and carves a 16x16 stone floor at y with 4 blocks of air above it. */
export async function buildPlatform(rcon, bx, bz, y) {
  // /fill silently does nothing in unloaded chunks.
  await rcon.command(`minecraft:forceload add ${bx - 16} ${bz - 16} ${bx + 16} ${bz + 16}`);
  await rcon.command(`minecraft:fill ${bx - 8} ${y} ${bz - 8} ${bx + 8} ${y} ${bz + 8} minecraft:stone`);
  await rcon.command(`minecraft:fill ${bx - 8} ${y + 1} ${bz - 8} ${bx + 8} ${y + 4} ${bz + 8} minecraft:air`);
}

/** Releases the forceload ticket added by buildPlatform. Never throws: always called during cleanup. */
export async function releasePlatform(rcon, bx, bz) {
  await rcon.command(`minecraft:forceload remove ${bx - 16} ${bz - 16} ${bx + 16} ${bz + 16}`).catch(() => {});
}
