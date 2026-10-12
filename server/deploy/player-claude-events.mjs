// The parsing and state-file half of player-claude.mjs, kept free of any panel access so
// player-claude-events.test.mjs runs without server/secrets.properties.
//
// State directory layout (all plain files, so the hub chat, the daemon and `wait` can each be
// restarted on their own without losing anything):
//   daemon.json        the daemon's pid and a heartbeat written every 15 s
//   inbox-cursor.json  how many complete lines of the live inbox.jsonl were already turned into events
//   wish-seen.json     recent Dragon Balls line keys, so a reconnect's log scan doesn't repeat one
//   hub-events.jsonl   every event for the hub chat, appended by the daemon
//   hub-events.read    how many of those lines `wait` already printed
//   players/<uuid>.md  one notes file per player, the player agents' memory between hub sessions

import { appendFileSync, existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from "node:fs";
import { join } from "node:path";

const ANSI = /\x1b\[[0-9;?]*[A-Za-z]/g;
// Wings console frames look like "\r>....\x1b[K[11:51:49 INFO]: \x1b[33mtext": a prompt redraw,
// then the console's own "[time LEVEL]: " prefix. exec() takes the FIRST such prefix, so a chat
// message that quotes one further along can't move where the real message starts.
const CONSOLE_PREFIX = /\[(\d\d:\d\d:\d\d) ([A-Z]+)\]: (.*)$/;

/** One console line from the panel websocket -> {time, level, message}, or null for prompt noise. */
export function parseConsoleLine(raw) {
  const clean = raw.replace(ANSI, "").replace(/\r/g, "");
  const m = CONSOLE_PREFIX.exec(clean);
  return m ? { time: m[1], level: m[2], message: m[3] } : null;
}

/**
 * A ClaudeBridge line only says "inbox.jsonl grew, go read it": the player's words are always
 * taken from that file, which only the plugin writes. So a chat line that fakes this prefix
 * costs one extra inbox read and can never inject a message or a sender UUID.
 */
export function isClaudeDoorbell(parsed) {
  return parsed !== null && parsed.message.startsWith("[ClaudeBridge] ");
}

// Paper's plugin logger and SummonSessionManager.wishLogLine each add "[DragonBalls] ", hence
// the repeat. Player chat reaches the console as "Name: text" or "[Not Secure] ... Name: text",
// never starting with "[DragonBalls]", so a player can't fake a wish line by typing one.
const WISH_MESSAGE = /^(?:\[DragonBalls\] )+(WISH|DRAGON|GRANTED) (.*)$/;
// DRAGON lines Claude writes itself (`shenron say`) echo back; only the plugin's scripted ones matter.
const PLUGIN_DRAGON = /I am the Eternal Dragon|weary|considering/i;
// latest.log carries the thread name the websocket drops; anchoring on Server thread keeps the
// same spoof guarantee for the reconnect scan.
const LOG_LINE = /^\[(\d\d:\d\d:\d\d)\] \[Server thread\/INFO\]: (.*)$/;

// Market's plugin.yml name is "Market", so Paper's logger prefixes this with "[Market] " itself
// (CosmeticRequests.java logs it once, no doubled prefix like DragonBalls). Player chat reaches
// the console as "Name: text" or "[Not Secure] ... Name: text", never starting with "[Market]",
// so a player can't fake a request by typing one. materialId never contains " - " (it is a plain
// material key), so splitting the tail on the first " - " safely separates it from the free-text
// idea even if the idea itself contains " - ".
const COSMETIC_MESSAGE = /^\[Market\] Cosmetic request #(\d+) from (.+) \(([0-9a-fA-F-]+)\): (.+)$/;

function cosmeticFromMessage(time, message) {
  const m = COSMETIC_MESSAGE.exec(message);
  if (!m) return null;
  const [, id, player, uuid, rest] = m;
  const sep = rest.indexOf(" - ");
  if (sep === -1) return null;
  return {
    type: "cosmetic", time, id: Number(id), player, uuid,
    material: rest.slice(0, sep), idea: rest.slice(sep + 3), key: `cosmetic|${id}`,
  };
}

/** A websocket console line -> a cosmetic request event, or null. */
export function cosmeticFromConsole(parsed) {
  return parsed === null ? null : cosmeticFromMessage(parsed.time, parsed.message);
}

/** A latest.log line -> the same cosmetic request event shape, or null. */
export function cosmeticFromLogLine(line) {
  const m = LOG_LINE.exec(line.replace(/\r$/, ""));
  return m ? cosmeticFromMessage(m[1], m[2]) : null;
}

function wishFromMessage(time, message) {
  const m = WISH_MESSAGE.exec(message);
  if (!m) return null;
  const [, kind, text] = m;
  const surface = kind === "WISH" || (kind === "DRAGON" && PLUGIN_DRAGON.test(text));
  return { type: "wish", time, kind, text, key: `${time}|${kind}|${text}`, surface };
}

/** A websocket console line -> a wish event (with `surface` false for lines the hub ignores), or null. */
export function wishFromConsole(parsed) {
  return parsed === null ? null : wishFromMessage(parsed.time, parsed.message);
}

/** A latest.log line -> the same wish event shape, or null. */
export function wishFromLogLine(line) {
  const m = LOG_LINE.exec(line.replace(/\r$/, ""));
  return m ? wishFromMessage(m[1], m[2]) : null;
}

/** The lines of an append-only file that end in "\n"; a trailing partial line is still being written. */
function completeLines(text) {
  const end = text.lastIndexOf("\n");
  return end === -1 ? [] : text.slice(0, end).split("\n");
}

/**
 * Splits off the inbox.jsonl lines past `cursor` (a count of complete lines). A trailing line
 * without its "\n" is still being written, so it waits for the next read. `cursor` null means
 * first run: everything already there is history the old listener answered, so it is only
 * counted. A file shorter than the cursor was replaced; restart counting from its current end.
 */
export function newInboxEntries(text, cursor) {
  const lines = completeLines(text);
  if (cursor === null) return { entries: [], cursor: lines.length, seeded: true };
  if (lines.length < cursor) return { entries: [], cursor: lines.length, reset: true };
  const entries = lines.slice(cursor).map((line) => {
    try {
      const e = JSON.parse(line);
      return { type: "claude", time: e.time, player: e.player, uuid: e.uuid, message: e.message };
    } catch {
      return { type: "daemon", text: `unreadable inbox.jsonl line: ${line.slice(0, 200)}` };
    }
  });
  return { entries, cursor: lines.length };
}

/** Keeps the wishes whose key isn't in `seenKeys`; returns them plus the updated, capped key list. */
export function freshWishes(wishes, seenKeys, cap = 500) {
  const seen = new Set(seenKeys);
  const fresh = [];
  for (const w of wishes) {
    if (seen.has(w.key)) continue;
    seen.add(w.key);
    fresh.push(w);
  }
  return { fresh, seenKeys: [...seen].slice(-cap) };
}

/** The one line `wait` prints per event; the hub chat reads these. */
export function formatEvent(e) {
  switch (e.type) {
    case "claude":
      return `CLAUDE [${e.time}] ${e.player} (${e.uuid}): ${e.message}`;
    case "wish":
      return `DRAGONBALLS ${e.kind} [${e.time}] ${e.text}`;
    case "cosmetic":
      return `COSMETIC #${e.id} ${e.player} (${e.uuid}): ${e.material} - ${e.idea}`;
    default:
      return `DAEMON ${e.text}`;
  }
}

export const HEARTBEAT_STALE_MS = 2 * 60 * 1000;

/** Whether the daemon behind `daemon.json` is alive, judged by its heartbeat alone. */
export function daemonHealth(daemonState, now) {
  if (!daemonState) return { ok: false, reason: "not running (no daemon.json): start it, see docs/player-claude.md" };
  const age = now - Date.parse(daemonState.heartbeatAt);
  if (!(age <= HEARTBEAT_STALE_MS)) {
    return { ok: false, reason: `not running (last heartbeat ${daemonState.heartbeatAt}): start it, see docs/player-claude.md` };
  }
  return { ok: true, connected: daemonState.connected };
}

// A reader can catch a file mid-write: `wait` died on "Unexpected end of JSON input" reading
// daemon.json during a heartbeat (2026-10-10 19:09 UTC). Writes go through a rename, and a reader
// still retries briefly for a daemon that predates that.
export function readJson(file, fallback = null) {
  for (let attempt = 0; ; attempt++) {
    if (!existsSync(file)) return fallback;
    try {
      return JSON.parse(readFileSync(file, "utf8"));
    } catch (e) {
      if (!(e instanceof SyntaxError) || attempt >= 4) throw e;
      Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, 50);
    }
  }
}

// A hard power loss can leave a state file as NUL bytes (daemon.json after the 2026-10-11 ~02:15 UTC
// reboot), which no retry fixes. Callers that can carry on treat it as missing; onCorrupt lets them
// say so out loud instead of silently starting over.
export function readJsonOrNull(file, onCorrupt) {
  try {
    return readJson(file);
  } catch (e) {
    if (!(e instanceof SyntaxError)) throw e;
    onCorrupt?.(e);
    return null;
  }
}

export function writeJson(file, value) {
  writeAtomic(file, JSON.stringify(value));
}

function writeAtomic(file, text) {
  writeFileSync(file + ".tmp", text);
  renameSync(file + ".tmp", file);
}

export function ensureStateDir(stateDir) {
  mkdirSync(join(stateDir, "players"), { recursive: true });
}

/** Appends events for the hub. Only these fields are written: wish keys and flags stay in the daemon. */
export function appendEvents(stateDir, events) {
  if (events.length === 0) return;
  const lines = events.map(({ key, surface, ...e }) => JSON.stringify(e) + "\n").join("");
  appendFileSync(join(stateDir, "hub-events.jsonl"), lines);
}

/** Returns the events `wait` hasn't printed yet and marks them read. */
export function takeNewEvents(stateDir) {
  const file = join(stateDir, "hub-events.jsonl");
  const readFile = join(stateDir, "hub-events.read");
  if (!existsSync(file)) return [];
  const lines = completeLines(readFileSync(file, "utf8"));
  const raw = existsSync(readFile) ? readFileSync(readFile, "utf8").trim() : "0";
  const read = /^\d+$/.test(raw) ? Number(raw) : null;
  // An unreadable marker skips to the end: replaying the whole file would re-raise every old wish.
  if (read === null) {
    writeAtomic(readFile, String(lines.length));
    return [{ type: "daemon", text: "hub-events.read was unreadable; skipped to the end, check the tail of hub-events.jsonl for anything missed" }];
  }
  if (lines.length <= read) return [];
  writeAtomic(readFile, String(lines.length));
  return lines.slice(read).map((line) => JSON.parse(line));
}
