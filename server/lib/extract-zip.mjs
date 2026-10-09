// Unpacks a zip (or jar) for the server/ generator scripts.
import { spawnSync } from 'node:child_process';
import { join } from 'node:path';

// Windows' own bsdtar reads zips; Git Bash's GNU tar (often first on PATH) does not.
export const WINDOWS_TAR = join(process.env.SystemRoot ?? 'C:\\Windows', 'System32', 'tar.exe');

// members: optional entry paths or globs to extract instead of the whole archive.
export function extractZip(zip, dir, members = []) {
  const result = process.platform === 'win32'
    ? spawnSync(WINDOWS_TAR, ['-xf', zip, '-C', dir, ...members])
    : spawnSync('unzip', ['-q', zip, ...members, '-d', dir]);
  if (result.status !== 0) throw new Error(`could not read ${zip}: ${result.stderr}`);
}
