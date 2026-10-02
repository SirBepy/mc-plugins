import mineflayer from 'mineflayer';
import net from 'node:net';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const runDir = path.join(here, '..', 'testserver', 'run');

export const HOST = '127.0.0.1';
export const PORT = 25565;
// Mineflayer tops out below the server version; ViaBackwards on the test server bridges the gap.
export const BOT_VERSION = '26.1';

export const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function serverProperty(key) {
  const text = fs.readFileSync(path.join(runDir, 'server.properties'), 'utf8');
  const line = text.split(/\r?\n/).find((l) => l.startsWith(key + '='));
  return line ? line.slice(key.length + 1).trim() : undefined;
}

/** Minimal Source RCON client: auth once, then one request per command. */
export class Rcon {
  static async connect() {
    const rcon = new Rcon();
    await rcon.#open(Number(serverProperty('rcon.port') ?? 25575), serverProperty('rcon.password'));
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
    bot.on('messagestr', (text) => bot.lines.push(text));
    const timer = setTimeout(() => reject(new Error(`${username} did not spawn`)), 60_000);
    bot.once('spawn', () => {
      clearTimeout(timer);
      resolve(bot);
    });
    bot.once('kicked', (reason) => reject(new Error(`${username} kicked: ${JSON.stringify(reason)}`)));
    bot.once('error', reject);
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

export async function waitFor(check, description, timeoutMs = 10_000) {
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

export async function quit(...bots) {
  for (const bot of bots) {
    if (bot) bot.quit();
  }
  await sleep(500);
}
