#!/usr/bin/env node
// Summarizes EssentialsX's trade.log into per-item and per-player totals, so a "what should be
// nerfed or buffed" question can be answered from real spawn-market sales instead of guessed
// gather rates. Only meaningful once economy-log-enabled: true in
// server/config/plugins/Essentials/config.yml (live needs Joe's OK + a restart).
//
// trade.log is CSV with a quoted date field that itself contains commas ("Monday, October 12,
// 2026, ..."), so this does a quote-aware split rather than a plain String.split(","). Column
// layout is NOT fixed-width: Trade.log's Java source (EssentialsX 2.22.0) appends a charge/pay
// side's fields only when that side actually carries them (item vs money vs exp), so a [Sell]
// sign line (subtype "Sell": player sells an item, gets paid money) looks like:
//   Sign,Sell,Interact,"<date>","<player>",<itemCount>,<ITEM>,,"<player>",<money>,money,$,"<world>",<x>,<y>,<z>,<endBalance>,
// captured 2026-10-12 from a bot selling 64 wheat for $5.14 at the Farmer stall:
//   Sign,Sell,Interact,"Monday, October 12, 2026, 2:05:26 PM Central European Summer Time","MarketSeller",64,WHEAT,,"MarketSeller",5.14,money,$,"world",420,116,-882,5.14,
// A Market StarterBoost top-up (market/src/main/java/.../StarterBoost.java) pays through a
// separate Vault deposit, not through Trade.log, so it never shows up as its own line here - a
// boosted sale's logged money is only the sign's base price.
//
// Usage: node server/deploy/market-sales.mjs <path-to-trade.log>
//   Prints per-item totals (count sold, money paid) and per-player totals (sales, money paid),
//   each sorted by money descending. Buy/Trade sign lines and anything economy-log-update-enabled
//   writes with no location are ignored; only subtype "Sell" (a spawn [Sell] stall) is counted.

import { readFileSync } from "node:fs";
import { pathToFileURL } from "node:url";

/** Quote-aware CSV split: a run of chars inside "..." is one field, commas inside it don't split. */
export function splitCsvLine(line) {
  const fields = [];
  let field = "";
  let inQuotes = false;
  for (let i = 0; i < line.length; i++) {
    const c = line[i];
    if (c === '"') {
      inQuotes = !inQuotes;
      continue;
    }
    if (c === "," && !inQuotes) {
      fields.push(field);
      field = "";
      continue;
    }
    field += c;
  }
  fields.push(field);
  return fields;
}

/** One trade.log line -> a sale record, or null for anything that isn't a [Sell] sign line
 * (a Buy/Trade sign, a blank line, or a line too short to be well-formed). */
export function parseSellLine(line) {
  if (!line.trim()) return null;
  const f = splitCsvLine(line);
  if (f[0] !== "Sign" || f[1] !== "Sell" || f.length < 10) return null;
  const count = Number(f[5]);
  const item = f[6];
  const money = Number(f[9]);
  const player = f[4];
  if (!player || !item || !Number.isFinite(count) || !Number.isFinite(money)) return null;
  return { time: f[3], player, item, count, money };
}

/** Every [Sell] sign sale in a trade.log's text. */
export function parseTradeLog(text) {
  return text.split(/\r?\n/).map(parseSellLine).filter((r) => r !== null);
}

/** {byItem: Map<item, {count, money}>, byPlayer: Map<player, {sales, money}>}, both the plain
 * Map insertion order - callers sort for display. */
export function summarize(sales) {
  const byItem = new Map();
  const byPlayer = new Map();
  for (const sale of sales) {
    const item = byItem.get(sale.item) ?? { count: 0, money: 0 };
    item.count += sale.count;
    item.money += sale.money;
    byItem.set(sale.item, item);

    const player = byPlayer.get(sale.player) ?? { sales: 0, money: 0 };
    player.sales += 1;
    player.money += sale.money;
    byPlayer.set(sale.player, player);
  }
  return { byItem, byPlayer };
}

function sortedByMoney(map) {
  return [...map.entries()].sort((a, b) => b[1].money - a[1].money);
}

function printTable(title, columns, rows) {
  console.log(title);
  for (const row of rows) console.log("  " + row.join("\t"));
  if (rows.length === 0) console.log("  (no sales)");
  console.log("");
}

async function main() {
  const path = process.argv[2];
  if (!path) {
    console.error("Usage: node server/deploy/market-sales.mjs <path-to-trade.log>");
    process.exit(1);
  }
  const text = readFileSync(path, "utf8");
  const sales = parseTradeLog(text);
  const { byItem, byPlayer } = summarize(sales);

  printTable(
    `Per item, sorted by money (${sales.length} sale${sales.length === 1 ? "" : "s"} total):`,
    ["item", "count", "money"],
    sortedByMoney(byItem).map(([item, { count, money }]) => [item, count, `$${money.toFixed(2)}`]),
  );
  printTable(
    "Per player, sorted by money:",
    ["player", "sales", "money"],
    sortedByMoney(byPlayer).map(([player, { sales: n, money }]) => [player, n, `$${money.toFixed(2)}`]),
  );
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((err) => {
    console.error(err.message);
    process.exit(1);
  });
}
