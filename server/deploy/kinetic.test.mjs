// node --test server/deploy/kinetic.test.mjs
// Importing kinetic.mjs reads kinetic.api.key from server/secrets.properties, so this runs on a
// machine with the secrets file; nothing here calls the panel.
import { test } from "node:test";
import assert from "node:assert/strict";
import { commandWasUnknown, hasRestartGo, isFreshBootLog, makeRestartToken, parseBootLog } from "./kinetic.mjs";

const oldLog = [
  "[11:58:01] [bootstrap/INFO]: Running Java 25 (OpenJDK 64-Bit Server VM 25+36-LTS; Eclipse Adoptium Temurin-25+36) on Linux",
  "[11:58:09] [Server thread/INFO]: [Tag] Enabling Tag v0.1.0",
  "[11:58:12] [Server thread/INFO]: Done (11.020s)! For help, type \"help\"",
  "[12:30:00] [Server thread/INFO]: Stopping server",
].join("\n");

const newBootStarting = [
  "[12:30:20] [bootstrap/INFO]: Running Java 25 (OpenJDK 64-Bit Server VM 25+36-LTS; Eclipse Adoptium Temurin-25+36) on Linux",
  "[12:30:28] [Server thread/INFO]: [LuckPerms] Enabling LuckPerms v5.5.17",
  "[12:30:29] [Server thread/INFO]: [Geyser-Spigot] Done (1.373s)! Run /geyser help for help!",
].join("\n");

const newBootDone = [
  newBootStarting,
  "[12:30:30] [Server thread/INFO]: [DragonBalls] Enabling DragonBalls v0.1.0",
  "[12:30:31] [DiscordSRV - Initialization/ERROR]: No bot token is set",
  "[12:30:36] [Server thread/INFO]: Done (16.220s)! For help, type \"help\"",
].join("\r\n");

const firstLine = (text) => text.split(/\r?\n/, 1)[0];

test("the pre-restart log still being written is not a fresh boot", () => {
  const grown = `${oldLog}\n[12:30:01] [Server thread/INFO]: Saving worlds`;
  assert.equal(isFreshBootLog(firstLine(oldLog), grown), false);
});

test("a rotated log with a new first line is a fresh boot", () => {
  assert.equal(isFreshBootLog(firstLine(oldLog), newBootStarting), true);
});

test("a plugin's own early Done line does not count as the server being up", () => {
  assert.equal(parseBootLog(newBootStarting).done, false);
});

test("a finished boot reports its plugins and ERROR lines", () => {
  const { done, enabled, errors } = parseBootLog(newBootDone);
  assert.equal(done, true);
  assert.deepEqual(enabled, ["LuckPerms", "DragonBalls"]);
  assert.deepEqual(errors, ["[12:30:31] [DiscordSRV - Initialization/ERROR]: No bot token is set"]);
});

test("an unrecognised console command is detected from the lines appended after it", () => {
  const before = oldLog;
  const after = `${oldLog}\n[12:30:01] [Server thread/INFO]: Unknown command. Type "/help" for help.`;
  assert.equal(commandWasUnknown(before, after), true);
});

test("the 'Unknown or incomplete command' form Paper actually logs on live is also detected", () => {
  const before = oldLog;
  const after = `${oldLog}\n[12:30:01] [Server thread/INFO]: System chat: Unknown or incomplete command. See below for error\nrestartcountdown status<--[HERE]`;
  assert.equal(commandWasUnknown(before, after), true);
});

test("the unknown-command line right after a log that ends in a newline is detected", () => {
  // latest.log always ends with "\n"; the first appended line is where Paper puts its message.
  const before = `${oldLog}\n`;
  const after = `${before}[02:52:25] [Server thread/INFO]: System chat: Unknown or incomplete command. See below for error\nrestartcountdown start cx3baa4j reason<--[HERE]\n`;
  assert.equal(commandWasUnknown(before, after), true);
});

test("ordinary log growth after a command is not mistaken for an unknown command", () => {
  const before = oldLog;
  const after = `${oldLog}\n[12:30:01] [Server thread/INFO]: Saving worlds`;
  assert.equal(commandWasUnknown(before, after), false);
});

test("an unrelated earlier Unknown command line does not count, only lines appended after this call", () => {
  const before = `[11:00:00] [Server thread/INFO]: Unknown command. Type "/help" for help.\n${oldLog}`;
  const after = `${before}\n[12:30:01] [Server thread/INFO]: Saving worlds`;
  assert.equal(commandWasUnknown(before, after), false);
});

test("the go marker is matched only for its own token", () => {
  const log = `${oldLog}\n[12:30:40] [Server thread/INFO]: [Tweaks] RESTART-GO abc123`;
  assert.equal(hasRestartGo(log, "abc123"), true);
  assert.equal(hasRestartGo(log, "def456"), false, "a stale marker from a different run never matches");
});

test("restart tokens are short and url/log-safe", () => {
  const token = makeRestartToken();
  assert.match(token, /^[a-z0-9]{4,}$/);
});
