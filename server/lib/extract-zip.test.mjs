import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { WINDOWS_TAR, extractZip } from './extract-zip.mjs';

function makeZip(root) {
  const src = join(root, 'src');
  mkdirSync(join(src, 'data', 'a'), { recursive: true });
  mkdirSync(join(src, 'assets'), { recursive: true });
  writeFileSync(join(src, 'data', 'a', 'one.json'), '{"one":1}');
  writeFileSync(join(src, 'assets', 'two.json'), '{"two":2}');
  const zip = join(root, 'pack.zip');
  const made = process.platform === 'win32'
    ? spawnSync(WINDOWS_TAR, ['-a', '-cf', zip, '-C', src, 'data', 'assets'])
    : spawnSync('zip', ['-qr', zip, 'data', 'assets'], { cwd: src });
  assert.equal(made.status, 0, `could not build the test zip: ${made.stderr}`);
  return zip;
}

test('extracts the whole archive', () => {
  const root = mkdtempSync(join(tmpdir(), 'extract-zip-'));
  try {
    const out = join(root, 'out');
    mkdirSync(out);
    extractZip(makeZip(root), out);
    assert.equal(readFileSync(join(out, 'data', 'a', 'one.json'), 'utf8'), '{"one":1}');
    assert.equal(readFileSync(join(out, 'assets', 'two.json'), 'utf8'), '{"two":2}');
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('extracts only the named members', () => {
  const root = mkdtempSync(join(tmpdir(), 'extract-zip-'));
  try {
    const out = join(root, 'out');
    mkdirSync(out);
    extractZip(makeZip(root), out, ['data/a/*']);
    assert.ok(existsSync(join(out, 'data', 'a', 'one.json')));
    assert.ok(!existsSync(join(out, 'assets', 'two.json')));
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('throws with the archive name when it cannot be read', () => {
  const root = mkdtempSync(join(tmpdir(), 'extract-zip-'));
  try {
    assert.throws(() => extractZip(join(root, 'missing.zip'), root), /could not read .*missing\.zip/);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
