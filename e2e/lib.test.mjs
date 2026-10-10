// node --test e2e/lib.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { geyserBedrockPort } from './lib.mjs';

const config = ['bedrock:', '  address: 0.0.0.0', '  port: 25572', 'remote:', '  port: 25565', ''];

test('reads the bedrock block port from an LF config', () => {
  assert.equal(geyserBedrockPort(config.join('\n')), 25572);
});

// A config rewritten on Windows is CRLF; the old per-script regex fell back to 19132 there.
test('reads the bedrock block port from a CRLF config', () => {
  assert.equal(geyserBedrockPort(config.join('\r\n')), 25572);
});

test("never takes the remote block's port", () => {
  assert.equal(geyserBedrockPort(['remote:', '  port: 25565', 'bedrock:', '  port: 25572', ''].join('\n')), 25572);
});

// Geyser's config-version 9 (preview build 3090 on) writes a blank line between commented keys.
test('reads the bedrock block port past blank lines inside the block', () => {
  const v9 = ['bedrock:', '  # bind address', '  address: 0.0.0.0', '', '  # listen port', '  port: 25593', '', 'java:', '  auth-type: floodgate', ''];
  assert.equal(geyserBedrockPort(v9.join('\n')), 25593);
  assert.equal(geyserBedrockPort(v9.join('\r\n')), 25593);
});

test("falls back to Geyser's default without a bedrock block", () => {
  assert.equal(geyserBedrockPort('remote:\n  port: 25565\n'), 19132);
});
