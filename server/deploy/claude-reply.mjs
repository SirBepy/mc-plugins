#!/usr/bin/env node
// Sends one Claude reply into chat as a tellraw, via the same Kinetic Panel console API
// kinetic.mjs's own `cmd` subcommand uses (see cmdConsole, exported from kinetic.mjs for this).
// This script can only ever build and send that one tellraw; it takes no other console command.
//
// Usage: node server/deploy/claude-reply.mjs <player> <message...>
//        node server/deploy/claude-reply.mjs --all <message...>   (sends to @a instead of a player)
//        node server/deploy/claude-reply.mjs [--all] [--dry-run] <player|message...>
//   <player>     a Java name or a Floodgate (Bedrock) name, which starts with '.'
//   <message...> 1-400 characters, joined with spaces; may contain quotes/backslashes/newlines
//   --all        broadcast to @a instead of a single player (no <player> argument then)
//   --dry-run    print the exact console command instead of sending it

import { cmdConsole } from "./kinetic.mjs";

const PLAYER_PATTERN = /^\.?[A-Za-z0-9_]{1,17}$/;
const MAX_MESSAGE_LENGTH = 400;

function buildTellrawCommand(target, message) {
  const json = JSON.stringify([
    "",
    { text: "[Claude] ", color: "light_purple", bold: true },
    { text: message, color: "white" },
  ]);
  return `tellraw ${target} ${json}`;
}

function parseArgs(argv) {
  const all = argv.includes("--all");
  const dryRun = argv.includes("--dry-run");
  const positional = argv.filter((arg) => arg !== "--all" && arg !== "--dry-run");

  let target;
  let messageWords;
  if (all) {
    target = "@a";
    messageWords = positional;
  } else {
    [target, ...messageWords] = positional;
  }

  if (!all) {
    if (!target) {
      throw new Error("missing <player> (or pass --all to broadcast)");
    }
    if (!PLAYER_PATTERN.test(target)) {
      throw new Error(`invalid player name "${target}": must match ${PLAYER_PATTERN}`);
    }
  }

  const message = messageWords.join(" ");
  if (message.length < 1 || message.length > MAX_MESSAGE_LENGTH) {
    throw new Error(`message must be 1-${MAX_MESSAGE_LENGTH} characters, got ${message.length}`);
  }

  return { target, message, dryRun };
}

async function main() {
  const { target, message, dryRun } = parseArgs(process.argv.slice(2));
  const command = buildTellrawCommand(target, message);
  if (dryRun) {
    console.log(command);
    return;
  }
  await cmdConsole(command);
}

try {
  await main();
} catch (err) {
  console.error(err.message);
  process.exit(1);
}
