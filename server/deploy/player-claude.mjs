#!/usr/bin/env node
// Wakes the Player Claude hub chat only when something happens on live: a /shenron message, a
// Dragon Balls wish, or a Market cosmetic token request. Nothing here polls the panel. The daemon
// holds the panel's console websocket open (Pterodactyl-style: a token from /servers/{id}/websocket, re-sent when Wings says it is
// expiring), and a ClaudeBridge console line is just the doorbell: the message itself is read
// from plugins/ClaudeBridge/inbox.jsonl, which only the plugin writes, so the player name and UUID
// in an event can't be faked from chat. docs/player-claude.md is the hub's runbook.
//
// Usage: node server/deploy/player-claude.mjs <subcommand>
//   daemon   hold the socket open and append events to the state dir (run it supervised)
//   wait     block until there are unread events, print one line each, then exit; also exits
//            with a "DAEMON ..." line when the daemon's heartbeat is missing or stale
//   status   daemon health and inbox cursor
// State dir: PLAYER_CLAUDE_STATE, default .for_bepy/player-claude (layout: player-claude-events.mjs).

import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { PANEL, api, serverIdentifier, filesContents, wait as sleep } from "./kinetic.mjs";
import {
  appendEvents, cosmeticFromConsole, cosmeticFromLogLine, daemonHealth, ensureStateDir, formatEvent,
  freshWishes, isClaudeDoorbell, newInboxEntries, parseConsoleLine, readJsonOrNull, takeNewEvents,
  wishFromConsole, wishFromLogLine, writeJson,
} from "./player-claude-events.mjs";

const REPO = join(dirname(fileURLToPath(import.meta.url)), "..", "..");
const STATE = process.env.PLAYER_CLAUDE_STATE ?? join(REPO, ".for_bepy", "player-claude");
const INBOX = "/plugins/ClaudeBridge/inbox.jsonl";
const files = {
  daemon: join(STATE, "daemon.json"),
  cursor: join(STATE, "inbox-cursor.json"),
  wishSeen: join(STATE, "wish-seen.json"),
  cosmeticSeen: join(STATE, "cosmetic-seen.json"),
};

const log = (msg) => console.log(`${new Date().toISOString()} ${msg}`);

// Wings sends a stats frame about once a second while connected, so 90 s of silence means the
// socket died without a close frame.
const SILENT_SOCKET_MS = 90_000;
// Long enough to ride out a live restart without bothering the hub.
const DISCONNECTED_ALERT_MS = 5 * 60_000;

async function daemon() {
  ensureStateDir(STATE);
  const running = readJsonOrNull(files.daemon);
  if (running && running.pid !== process.pid && daemonHealth(running, Date.now()).ok && isAlive(running.pid)) {
    console.error(`daemon already running as pid ${running.pid}`);
    process.exit(1);
  }
  const id = await serverIdentifier();
  const state = { connected: false, since: new Date().toISOString(), lastFrameAt: Date.now(), downSince: Date.now(), alerted: false };
  let backoff = 5_000;

  const heartbeat = () =>
    writeJson(files.daemon, { pid: process.pid, heartbeatAt: new Date().toISOString(), connected: state.connected, since: state.since });
  heartbeat();

  // One inbox read at a time; a doorbell during a read just queues one more pass.
  let inboxRun = null;
  let inboxAgain = false;
  function syncInbox() {
    if (inboxRun) {
      inboxAgain = true;
      return;
    }
    inboxRun = (async () => {
      do {
        inboxAgain = false;
        try {
          let cursorCorrupt = false;
          const cursor = readJsonOrNull(files.cursor, () => (cursorCorrupt = true));
          const result = newInboxEntries(await filesContents(id, INBOX), cursor?.lines ?? null);
          if (cursorCorrupt) result.entries.push({ type: "daemon", text: "inbox cursor was unreadable; re-seeded at the inbox end, check the tail of inbox.jsonl for anything missed" });
          if (result.reset) result.entries.push({ type: "daemon", text: "inbox.jsonl got shorter; counting from its new end" });
          // Events first, cursor second: a crash in between repeats a message, never loses one.
          appendEvents(STATE, result.entries);
          writeJson(files.cursor, { lines: result.cursor });
          if (result.seeded) log(`inbox cursor seeded at ${result.cursor} lines`);
          for (const e of result.entries) log(formatEvent(e));
        } catch (err) {
          log(`inbox read failed, retrying in 30 s: ${err.message}`);
          setTimeout(syncInbox, 30_000);
        }
      } while (inboxAgain);
    })().finally(() => {
      inboxRun = null;
    });
  }

  function emitWishes(wishes) {
    const seen = readJsonOrNull(files.wishSeen, () => log("wish-seen.json was unreadable; starting it over as a first run"));
    const { fresh, seenKeys } = freshWishes(wishes, seen ?? []);
    writeJson(files.wishSeen, seenKeys);
    if (seen === null) return; // first run: what is in the log already was handled by the old poller
    const surfaced = fresh.filter((w) => w.surface);
    appendEvents(STATE, surfaced);
    for (const w of surfaced) log(formatEvent(w));
  }

  // Cosmetic requests have no echo to filter (unlike DRAGON lines), so every fresh match surfaces.
  function emitCosmetics(cosmetics) {
    const seen = readJsonOrNull(files.cosmeticSeen, () => log("cosmetic-seen.json was unreadable; starting it over as a first run"));
    const { fresh, seenKeys } = freshWishes(cosmetics, seen ?? []);
    writeJson(files.cosmeticSeen, seenKeys);
    if (seen === null) return; // first run: requests already in the log were handled before this watcher existed
    appendEvents(STATE, fresh);
    for (const c of fresh) log(formatEvent(c));
  }

  // Lines printed while the socket was down only exist in latest.log, so each (re)connect
  // reads it once. A restart's rotation can still drop some; the inbox has no such gap.
  async function scanLogOnConnect() {
    try {
      const text = await filesContents(id, "/logs/latest.log");
      const lines = text.split("\n");
      emitWishes(lines.map(wishFromLogLine).filter(Boolean));
      emitCosmetics(lines.map(cosmeticFromLogLine).filter(Boolean));
    } catch (err) {
      log(`latest.log scan failed: ${err.message}`);
    }
  }

  let ws = null;
  async function sendAuth(socket) {
    const { data } = await api(`/servers/${id}/websocket`);
    socket.send(JSON.stringify({ event: "auth", args: [data.token] }));
    return data;
  }

  async function connect() {
    const { data } = await api(`/servers/${id}/websocket`);
    const socket = new WebSocket(data.socket, { headers: { Origin: PANEL } });
    ws = socket;
    state.lastFrameAt = Date.now();
    // Wings answers every token refresh with another "auth success"; only a new socket can have
    // missed anything, so only its first one catches up.
    let caughtUp = false;
    socket.onopen = () => socket.send(JSON.stringify({ event: "auth", args: [data.token] }));
    socket.onmessage = (frame) => {
      state.lastFrameAt = Date.now();
      let msg;
      try {
        msg = JSON.parse(frame.data);
      } catch {
        return;
      }
      switch (msg.event) {
        case "auth success":
          if (caughtUp) break;
          caughtUp = true;
          log("socket authenticated");
          state.connected = true;
          state.downSince = null;
          state.alerted = false;
          backoff = 5_000;
          syncInbox();
          scanLogOnConnect();
          break;
        case "token expiring":
          sendAuth(socket).then(() => log("token refreshed"), (err) => log(`token refresh failed: ${err.message}`));
          break;
        case "token expired":
        case "jwt error":
          log(`socket says ${msg.event}; reconnecting`);
          socket.close();
          break;
        case "console output": {
          const wishes = [];
          const cosmetics = [];
          for (const raw of String(msg.args?.[0] ?? "").split("\n")) {
            const parsed = parseConsoleLine(raw);
            if (isClaudeDoorbell(parsed)) syncInbox();
            const wish = wishFromConsole(parsed);
            if (wish) wishes.push(wish);
            const cosmetic = cosmeticFromConsole(parsed);
            if (cosmetic) cosmetics.push(cosmetic);
          }
          if (wishes.length) emitWishes(wishes);
          if (cosmetics.length) emitCosmetics(cosmetics);
          break;
        }
      }
    };
    socket.onerror = (e) => log(`socket error: ${e.message ?? e.type}`);
    socket.onclose = (e) => {
      if (ws !== socket) return;
      ws = null;
      state.connected = false;
      state.downSince ??= Date.now();
      log(`socket closed (${e.code}); reconnecting in ${backoff / 1000} s`);
      setTimeout(reconnect, backoff);
      backoff = Math.min(backoff * 2, 60_000);
    };
  }

  async function reconnect() {
    try {
      await connect();
    } catch (err) {
      log(`connect failed: ${err.message}; retrying in ${backoff / 1000} s`);
      setTimeout(reconnect, backoff);
      backoff = Math.min(backoff * 2, 60_000);
    }
  }

  setInterval(() => {
    heartbeat();
    if (ws && Date.now() - state.lastFrameAt > SILENT_SOCKET_MS) {
      log("no frame for 90 s; dropping the socket");
      ws.close();
    }
    if (!state.connected && state.downSince && !state.alerted && Date.now() - state.downSince > DISCONNECTED_ALERT_MS) {
      state.alerted = true;
      appendEvents(STATE, [{ type: "daemon", text: "console socket has been down for 5+ minutes; still retrying (check the daemon's log)" }]);
    }
  }, 15_000);

  await reconnect();
}

function isAlive(pid) {
  try {
    process.kill(pid, 0);
    return true;
  } catch {
    return false;
  }
}

async function wait() {
  ensureStateDir(STATE);
  for (;;) {
    const events = takeNewEvents(STATE);
    if (events.length) {
      for (const e of events) console.log(formatEvent(e));
      return;
    }
    const health = daemonHealth(readJsonOrNull(files.daemon), Date.now());
    if (!health.ok) {
      console.log(`DAEMON ${health.reason}`);
      return;
    }
    // A local file check, not a panel request: the daemon is what talks to live.
    await sleep(1000);
  }
}

function status() {
  ensureStateDir(STATE);
  const d = readJsonOrNull(files.daemon);
  const health = daemonHealth(d, Date.now());
  console.log(`state dir: ${STATE}`);
  console.log(health.ok ? `daemon: pid ${d.pid}, socket ${health.connected ? "connected" : "DOWN"}, up since ${d.since}` : `daemon: ${health.reason}`);
  console.log(`inbox cursor: ${readJsonOrNull(files.cursor)?.lines ?? "not seeded"} lines`);
}

const sub = process.argv[2];
try {
  if (sub === "daemon") await daemon();
  else if (sub === "wait") await wait();
  else if (sub === "status") status();
  else {
    console.error("usage: node server/deploy/player-claude.mjs daemon|wait|status");
    process.exit(1);
  }
} catch (err) {
  console.error(err.message);
  process.exit(1);
}
