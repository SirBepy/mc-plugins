#!/usr/bin/env node
// Talks to the Kinetic Panel (Pterodactyl-compatible) client API for the "Minecraft Server"
// instance. Reads the API key from server/secrets.properties (gitignored) at runtime so it
// never lands in argv, shell history, or this file. No npm deps: Node 18+'s built-in fetch.
//
// Usage: node server/deploy/kinetic.mjs <subcommand> [args...]
//   info                              server state, allocations, image, startup vars
//   ssh-key-add <pubkey-path> <name>  add an SSH public key to the panel account
//   alloc-add                         auto-assign a new allocation, prints its id and ip:port
//   alloc-note <allocation-id> <note> set a note on an allocation
//   var <ENV_NAME> <value>            set a startup variable
//   power <start|stop|restart|kill>   send a power action
//   restart "<reason>"                full live restart: 60/30/10s chat countdown, save-all
//                                      flush, power restart, then waits for the new boot's
//                                      "Done (" and prints which plugins enabled
//   cmd "<console command>"           send a console command
//   log [n]                           last n lines of logs/latest.log (default 100)
//   ls <dir>                          list files in a server directory
//   prop <key> [value]                get/set one line in /server.properties (most keys need a restart)
//   replace <file> <old> <new>        swap one exact, unique piece of text in a server file (e.g. a plugin config line)
//   upload <local-file> <remote-dir>  upload one file into a server directory (overwrites)
//   decompress <dir> <file>           decompress an archive already on the server
//   delete <file...>                  delete files/dirs from server root (kept, unused today)

import { readFileSync } from "node:fs";
import { fileURLToPath, pathToFileURL } from "node:url";
import { basename, dirname, join } from "node:path";

const __dirname = dirname(fileURLToPath(import.meta.url));
const PANEL = "https://kineticpanel.net";

function readSecret(key) {
  const path = join(__dirname, "..", "secrets.properties");
  const text = readFileSync(path, "utf8");
  for (const line of text.split(/\r?\n/)) {
    const eq = line.indexOf("=");
    if (eq === -1) continue;
    if (line.slice(0, eq).trim() === key) return line.slice(eq + 1).trim();
  }
  throw new Error(`${key} not found in server/secrets.properties`);
}

const apiKey = readSecret("kinetic.api.key");

async function api(path, { method = "GET", body } = {}) {
  const res = await fetch(`${PANEL}/api/client${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${apiKey}`,
      Accept: "application/json",
      ...(body ? { "Content-Type": "application/json" } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  if (!res.ok) {
    throw new Error(`${method} ${path} -> ${res.status} ${res.statusText}: ${text.slice(0, 500)}`);
  }
  return text ? JSON.parse(text) : null;
}

// Not secret, but keep output terse: this is resolved once per invocation, never cached to disk.
async function serverIdentifier() {
  const list = await api("");
  const server = list.data?.[0];
  if (!server) throw new Error("no server found under this account");
  return server.attributes.identifier;
}

async function cmdInfo() {
  const id = await serverIdentifier();
  const [details, resources, startup] = await Promise.all([
    api(`/servers/${id}`),
    api(`/servers/${id}/resources`),
    api(`/servers/${id}/startup`),
  ]);
  const a = details.attributes;
  console.log(`identifier: ${id}`);
  console.log(`name: ${a.name}`);
  console.log(`state: ${resources.attributes.current_state}`);
  console.log(`docker_image: ${a.docker_image ?? a.relationships?.egg?.attributes?.docker_image ?? "?"}`);
  console.log("allocations:");
  for (const alloc of a.relationships?.allocations?.data ?? []) {
    const al = alloc.attributes;
    console.log(`  [${al.id}] ${al.ip}:${al.port}${al.ip_alias ? ` (${al.ip_alias})` : ""}${al.is_default ? " default" : ""}${al.notes ? ` notes="${al.notes}"` : ""}`);
  }
  console.log("startup variables:");
  for (const v of startup.attributes?.data ?? startup.data ?? []) {
    const va = v.attributes ?? v;
    console.log(`  ${va.env_variable} = ${va.server_value}`);
  }
}

async function cmdSshKeyAdd(pubkeyPath, name) {
  const publicKey = readFileSync(pubkeyPath, "utf8").trim();
  try {
    const result = await api("/account/ssh-keys", {
      method: "POST",
      body: { name, public_key: publicKey },
    });
    console.log(`ssh key added: ${result?.attributes?.name ?? name} (fingerprint ${result?.attributes?.fingerprint ?? "?"})`);
  } catch (err) {
    console.error(`ssh-key-add failed: ${err.message}`);
    throw err;
  }
}

// Kinetic's panel is a Pterodactyl-compatible fork whose allocation routes drop the "/network"
// segment vanilla Pterodactyl uses (confirmed against the live API 2026-10-03): plain
// /servers/{id}/allocations, not /servers/{id}/network/allocations.
async function cmdAllocAdd() {
  const id = await serverIdentifier();
  const result = await api(`/servers/${id}/allocations`, { method: "POST" });
  const a = result.attributes;
  console.log(`new allocation [${a.id}]: ${a.ip}:${a.port}`);
}

async function cmdAllocNote(allocationId, note) {
  const id = await serverIdentifier();
  await api(`/servers/${id}/allocations/${allocationId}`, {
    method: "PATCH",
    body: { notes: note },
  });
  console.log(`allocation ${allocationId} noted: ${note}`);
}

async function cmdVar(envName, value) {
  const id = await serverIdentifier();
  const result = await api(`/servers/${id}/startup/variable`, {
    method: "PUT",
    body: { key: envName, value },
  });
  console.log(`${envName} = ${result?.attributes?.server_value ?? value}`);
}

async function cmdPower(action) {
  if (!["start", "stop", "restart", "kill"].includes(action)) {
    throw new Error(`power action must be one of start|stop|restart|kill, got "${action}"`);
  }
  const id = await serverIdentifier();
  await api(`/servers/${id}/power`, { method: "POST", body: { signal: action } });
  console.log(`power: ${action} sent`);
}

const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

// Exported for offline testing. Paper rotates logs/latest.log to a timestamped .log.gz on every
// boot and starts a brand new latest.log, so a fresh boot's first line is never a continuation of
// the pre-restart file's first line (same file handle would repeat it verbatim). Comparing just
// the first line (not the whole text) keeps this cheap and avoids false negatives from log lines
// that get appended between poll ticks during a slow shutdown.
export function isFreshBootLog(preRestartFirstLine, currentLogText) {
  const currentFirstLine = currentLogText.split(/\r?\n/, 1)[0];
  return currentFirstLine !== preRestartFirstLine;
}

// Exported for offline testing. Scans one boot's full log text for the "Done (" ready line and
// collects the "Enabling <Plugin>" and ERROR lines so cmdRestart can print a concise summary
// instead of the whole log.
//
// Line shape is `[HH:MM:SS] [<thread>/<LEVEL>]: <message>`. The main ready line is
// `...]: Done (16.220s)! For help, type "help"` with nothing between "]: " and "Done ("; a plugin
// can print its own early "Done (...)" (Geyser does, e.g. "...]: [Geyser-Spigot] Done (1.373s)!
// Run /geyser help for help!") before every plugin has finished enabling, so matching "]: Done ("
// specifically (not "Done (" anywhere) avoids declaring the boot complete too early. ERROR lines
// have "ERROR" directly after the thread name's "/", e.g. "[Server thread/ERROR]:" or
// "[DiscordSRV - Initialization/ERROR]:", so "/ERROR]:" catches both without anchoring to a
// specific thread name.
export function parseBootLog(logText) {
  const lines = logText.split(/\r?\n/);
  const done = lines.some((line) => line.includes("]: Done ("));
  const enabled = lines
    .map((line) => line.match(/\bEnabling (\S+)/))
    .filter(Boolean)
    .map((match) => match[1]);
  const errors = lines.filter((line) => line.includes("/ERROR]:"));
  return { done, enabled, errors };
}

async function cmdRestart(reason) {
  if (!reason) throw new Error('restart requires a reason, e.g. `restart "cartel phone fix"`');
  const id = await serverIdentifier();
  const say = (text) => cmdConsole(`minecraft:tellraw @a ${JSON.stringify({ text, color: "gold" })}`);

  await say(`[Server] Restarting in 60 seconds: ${reason}.`);
  await wait(30_000);
  await say(`[Server] Restarting in 30 seconds: ${reason}.`);
  await wait(20_000);
  await say(`[Server] Restarting in 10 seconds. Rejoin in about a minute and accept the resource pack.`);
  await wait(10_000);

  // Captured before the restart so the poll loop below can tell a freshly booted log apart from
  // the one the server was still writing when we sent the restart.
  const preRestartLog = await filesContents(id, "/logs/latest.log");
  const preRestartFirstLine = preRestartLog.split(/\r?\n/, 1)[0];

  await cmdConsole("save-all flush");
  await wait(2_000);
  await cmdPower("restart");
  console.log("restart sent, waiting for the server to come back up...");

  const timeoutMs = 5 * 60_000;
  const pollMs = 5_000;
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    await wait(pollMs);
    let text;
    try {
      text = await filesContents(id, "/logs/latest.log");
    } catch {
      continue; // panel/daemon briefly unreachable mid-restart; keep polling
    }
    if (!isFreshBootLog(preRestartFirstLine, text)) continue;
    const { done, enabled, errors } = parseBootLog(text);
    if (!done) continue;
    console.log(`server is back up (${enabled.length} plugin${enabled.length === 1 ? "" : "s"} enabled):`);
    for (const name of enabled) console.log(`  Enabling ${name}`);
    if (errors.length) {
      console.log("errors during boot:");
      for (const line of errors) console.log(`  ${line}`);
    }
    return;
  }
  throw new Error(`timed out after ${timeoutMs / 1000}s waiting for "Done (" in a fresh boot log`);
}

// Exported so claude-reply.mjs can send its one tellraw through the same panel console API,
// without duplicating serverIdentifier()/api()'s auth and error handling; the CLI switch below
// still calls this the same way it always has.
export async function cmdConsole(command) {
  const id = await serverIdentifier();
  await api(`/servers/${id}/command`, { method: "POST", body: { command } });
  console.log(`command sent: ${command}`);
}

// /files/contents returns the raw file as the response body (not JSON), so this bypasses
// the api() helper's JSON.parse.
async function filesContents(id, file) {
  const res = await fetch(`${PANEL}/api/client/servers/${id}/files/contents?file=${encodeURIComponent(file)}`, {
    headers: { Authorization: `Bearer ${apiKey}`, Accept: "text/plain" },
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`read ${file} -> ${res.status} ${res.statusText}: ${text.slice(0, 500)}`);
  return text;
}

// /files/write takes the new file body as the raw request body, not a JSON envelope.
async function filesWrite(id, file, content) {
  const res = await fetch(`${PANEL}/api/client/servers/${id}/files/write?file=${encodeURIComponent(file)}`, {
    method: "POST",
    headers: { Authorization: `Bearer ${apiKey}`, "Content-Type": "text/plain" },
    body: content,
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`write ${file} -> ${res.status} ${res.statusText}: ${text.slice(0, 500)}`);
}

async function cmdLog(n = 100) {
  const id = await serverIdentifier();
  const text = await filesContents(id, "/logs/latest.log");
  const lines = text.split(/\r?\n/);
  console.log(lines.slice(-Number(n)).join("\n"));
}

async function cmdLs(dir) {
  const id = await serverIdentifier();
  const result = await api(`/servers/${id}/files/list?directory=${encodeURIComponent(dir)}`);
  for (const entry of result.data ?? []) {
    const e = entry.attributes;
    console.log(`${e.is_file ? "f" : "d"} ${e.size ?? ""}\t${e.name}`);
  }
}

// Splits keeping the line terminators as their own array elements (even indices are line
// content, odd indices are the terminator that followed), so the exact mix of \r\n/\n and the
// presence or absence of a trailing newline survive an untouched rejoin.
function splitKeepingLineEndings(text) {
  return text.split(/(\r\n|\n)/);
}

async function cmdProp(key, value) {
  if (!key) throw new Error("prop requires a key, e.g. `prop motd`");
  const id = await serverIdentifier();
  const file = "/server.properties";
  const parts = splitKeepingLineEndings(await filesContents(id, file));
  let lineIndex = -1;
  for (let i = 0; i < parts.length; i += 2) {
    const eq = parts[i].indexOf("=");
    if (eq !== -1 && parts[i].slice(0, eq) === key) {
      lineIndex = i;
      break;
    }
  }
  if (lineIndex === -1) throw new Error(`key "${key}" not found in ${file}, refusing to append`);
  if (value === undefined) {
    console.log(parts[lineIndex]);
    return;
  }
  const eq = parts[lineIndex].indexOf("=");
  parts[lineIndex] = parts[lineIndex].slice(0, eq + 1) + value;
  await filesWrite(id, file, parts.join(""));
  console.log(`${key}=${value}`);
}

async function cmdReplace(file, oldText, newText) {
  if (!file || !oldText || newText === undefined) throw new Error("replace requires <file> <old> <new>");
  const id = await serverIdentifier();
  const text = await filesContents(id, file);
  const count = text.split(oldText).length - 1;
  if (count !== 1) throw new Error(`"${oldText}" occurs ${count} times in ${file}, refusing to replace`);
  await filesWrite(id, file, text.replace(oldText, () => newText));
  console.log(`${file}: replaced 1 occurrence`);
}

// Pterodactyl uploads go to a short-lived signed Wings URL as multipart field "files", not
// through the panel API itself.
async function cmdUpload(localPath, dir) {
  if (!localPath || !dir) throw new Error("upload requires <local-file> <remote-dir>, e.g. `upload x.jar plugins`");
  const id = await serverIdentifier();
  const signed = await api(`/servers/${id}/files/upload`);
  const name = basename(localPath);
  const form = new FormData();
  form.append("files", new Blob([readFileSync(localPath)]), name);
  const res = await fetch(`${signed.attributes.url}&directory=${encodeURIComponent(dir)}`, { method: "POST", body: form });
  const text = await res.text();
  if (!res.ok) throw new Error(`upload ${name} -> ${res.status} ${res.statusText}: ${text.slice(0, 500)}`);
  console.log(`uploaded ${name} to ${dir}`);
}

async function cmdDecompress(dir, file) {
  const id = await serverIdentifier();
  await api(`/servers/${id}/files/decompress`, {
    method: "POST",
    body: { root: dir, file },
  });
  console.log(`decompressed ${dir}/${file}`);
}

async function cmdDelete(files) {
  const id = await serverIdentifier();
  await api(`/servers/${id}/files/delete`, {
    method: "POST",
    body: { root: "/", files },
  });
  console.log(`deleted: ${files.join(", ")}`);
}

// Guarded so `import { cmdConsole } from "./kinetic.mjs"` (claude-reply.mjs does this) never runs
// this CLI dispatch against the importer's own process.argv; only a direct `node kinetic.mjs ...`
// invocation reaches here, same as before this guard existed.
if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  const [, , sub, ...args] = process.argv;

  try {
    switch (sub) {
      case "info":
        await cmdInfo();
        break;
      case "ssh-key-add":
        await cmdSshKeyAdd(args[0], args[1]);
        break;
      case "alloc-add":
        await cmdAllocAdd();
        break;
      case "alloc-note":
        await cmdAllocNote(args[0], args.slice(1).join(" "));
        break;
      case "var":
        await cmdVar(args[0], args[1]);
        break;
      case "power":
        await cmdPower(args[0]);
        break;
      case "restart":
        await cmdRestart(args.join(" "));
        break;
      case "cmd":
        await cmdConsole(args.join(" "));
        break;
      case "log":
        await cmdLog(args[0] ?? 100);
        break;
      case "ls":
        await cmdLs(args[0] ?? "/");
        break;
      case "prop":
        await cmdProp(args[0], args.length > 1 ? args.slice(1).join(" ") : undefined);
        break;
      case "replace":
        await cmdReplace(args[0], args[1], args[2]);
        break;
      case "upload":
        await cmdUpload(args[0], args[1]);
        break;
      case "decompress":
        await cmdDecompress(args[0], args[1]);
        break;
      case "delete":
        await cmdDelete(args);
        break;
      default:
        console.error("unknown subcommand. See the header comment in this file for usage.");
        process.exit(1);
    }
  } catch (err) {
    console.error(err.message);
    process.exit(1);
  }
}
