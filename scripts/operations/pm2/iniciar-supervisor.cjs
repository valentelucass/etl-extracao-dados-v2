'use strict';

const { spawn } = require('node:child_process');
const path = require('node:path');

const mode = process.argv[2];
if (process.argv.length !== 3 || !['-Continuo', '-Verify'].includes(mode)) {
  console.error('Informe -Continuo ou -Verify.');
  process.exit(2);
}

// No Windows, o PowerShell 5.1 pode sair sem executar o script quando o PM2 o inicia diretamente em modo detached.
const powershell = path.join(process.env.SystemRoot, 'System32', 'WindowsPowerShell', 'v1.0', 'powershell.exe');
const script = path.resolve(__dirname, '..', 'Start-ProductionHistory.ps1');
const worker = spawn(powershell, ['-NoLogo', '-NoProfile', '-NonInteractive', '-File', script, mode], {
  detached: false,
  windowsHide: true,
  stdio: ['ignore', 'pipe', 'pipe']
});
worker.stdout.pipe(process.stdout);
worker.stderr.pipe(process.stderr);
worker.once('error', error => {
  console.error('Falha ao iniciar o supervisor do ETL:', error.code);
  process.exitCode = 1;
});
worker.once('close', code => { process.exitCode = code ?? 1; });
