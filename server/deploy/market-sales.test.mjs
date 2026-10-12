// node --test server/deploy/market-sales.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";
import { parseSellLine, parseTradeLog, splitCsvLine, summarize } from "./market-sales.mjs";

// Captured 2026-10-12 from e2e/market.mjs: a bot selling 64 wheat for $5.14 at the Farmer stall
// on a server/local assembled with economy-log-enabled: true (server/config/plugins/Essentials/
// config.yml). The date field is itself comma-separated inside its own quotes.
const REAL_SELL_LINE =
  'Sign,Sell,Interact,"Monday, October 12, 2026, 2:05:26 PM Central European Summer Time","MarketSeller",64,WHEAT,,"MarketSeller",5.14,money,$,"world",420,116,-882,5.14,';

test("splitCsvLine keeps a quoted field's internal commas as one field", () => {
  const fields = splitCsvLine(REAL_SELL_LINE);
  assert.equal(fields[3], "Monday, October 12, 2026, 2:05:26 PM Central European Summer Time");
  assert.equal(fields.length, 18);
});

test("parseSellLine reads player, item, count and money off a real [Sell] sign line", () => {
  const sale = parseSellLine(REAL_SELL_LINE);
  assert.deepEqual(sale, {
    time: "Monday, October 12, 2026, 2:05:26 PM Central European Summer Time",
    player: "MarketSeller",
    item: "WHEAT",
    count: 64,
    money: 5.14,
  });
});

test("parseSellLine ignores blank lines and non-Sell-sign lines", () => {
  assert.equal(parseSellLine(""), null);
  assert.equal(parseSellLine("   "), null);
  // Synthetic non-Sell line: real column layout differs per subtype, but the filter only checks
  // fields[1], so any subtype other than "Sell" must be rejected regardless of the rest.
  assert.equal(parseSellLine('Sign,Buy,Interact,"<date>","p",5,money,$,"p",64,DIRT,,"world",1,2,3,10,'), null);
});

test("parseTradeLog pulls every [Sell] sale out of a multi-line file and skips the rest", () => {
  const text = [
    REAL_SELL_LINE,
    // Synthetic non-Sell line, same reasoning as above - must be skipped by subtype alone.
    'Sign,Buy,Interact,"<date>","p",5,money,$,"p",64,DIRT,,"world",1,2,3,10,',
    "",
    'Sign,Sell,Interact,"<date>","MarketSeller",10,CARROT,,"MarketSeller",2.50,money,$,"world",1,2,3,7.64,',
  ].join("\n");
  const sales = parseTradeLog(text);
  assert.equal(sales.length, 2);
  assert.equal(sales[1].item, "CARROT");
});

test("summarize totals by item and by player", () => {
  const sales = [
    { time: "t1", player: "Alice", item: "WHEAT", count: 64, money: 5.14 },
    { time: "t2", player: "Bob", item: "WHEAT", count: 10, money: 0.8 },
    { time: "t3", player: "Alice", item: "CARROT", count: 5, money: 12 },
  ];
  const { byItem, byPlayer } = summarize(sales);
  assert.deepEqual(byItem.get("WHEAT"), { count: 74, money: 5.14 + 0.8 });
  assert.deepEqual(byItem.get("CARROT"), { count: 5, money: 12 });
  assert.deepEqual(byPlayer.get("Alice"), { sales: 2, money: 5.14 + 12 });
  assert.deepEqual(byPlayer.get("Bob"), { sales: 1, money: 0.8 });
});
