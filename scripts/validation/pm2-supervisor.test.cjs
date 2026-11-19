'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { spawn } = require('node:child_process');
const fs = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');
const app = require('../operations/pm2/ecosystem.config.cjs').apps[0];

test('o PM2 executa o supervisor no Windows e recebe sua saída e código de falha', {
  skip: process.platform !== 'win32', timeout: 30000
}, async () => {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), 'etl pm2 '));
  try {
    const folder = path.join(root, 'pm2');
    await fs.mkdir(folder);
    const [launcher, mode] = app.args;
    const copy = path.join(folder, path.basename(launcher));
    await fs.copyFile(launcher, copy);
    await fs.writeFile(path.join(root, 'Start-ProductionHistory.ps1'), [
      'param([switch]$Continuo, [switch]$Verify)',
      'if ($Verify) { Write-Output "verificado"; exit 0 }',
      'if ($Continuo) { [Console]::Error.WriteLine("falha de teste"); exit 19 }',
      'exit 23'
    ].join('\r\n'));
    assert.equal(app.env.COMPUTERNAME.toLowerCase(), os.hostname().toLowerCase());
    assert.equal(mode, '-Continuo');
    for (const [flag, expectedCode, marker] of [['-Verify', 0, 'verificado'], [mode, 19, 'falha de teste']]) {
      const child = spawn(app.script, [copy, flag], {
        cwd: root, detached: true, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe', 'ipc']
      });
      let output = '';
      child.stdout.on('data', data => { output += data; });
      child.stderr.on('data', data => { output += data; });
      const code = await new Promise((resolve, reject) => {
        child.once('error', reject);
        child.once('close', resolve);
      });
      assert.equal(code, expectedCode);
      assert.ok(output.includes(marker));
    }
  } finally {
    await fs.rm(root, { recursive: true, force: true });
  }
});
