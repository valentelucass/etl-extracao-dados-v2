const path = require('node:path');

const operations = path.resolve(__dirname, '..');

module.exports = {
  apps: [{
    name: 'ETL-V2-ATUALIZACAO',
    cwd: path.resolve(operations, '..', '..'),
    script: path.join(process.env.SystemRoot, 'System32', 'WindowsPowerShell', 'v1.0', 'powershell.exe'),
    args: ['-NoLogo', '-NoProfile', '-NonInteractive', '-File',
      path.join(operations, 'Start-ProductionHistory.ps1'), '-Continuo'],
    interpreter: 'none',
    exec_mode: 'fork',
    instances: 1,
    // O supervisor controla o intervalo e encerra quando o operador pede uma pausa.
    autorestart: false,
    watch: false
  }]
};
