const path = require('node:path');

const operations = path.resolve(__dirname, '..');

module.exports = {
  apps: [{
    name: 'ETL-V2-ATUALIZACAO',
    cwd: path.resolve(operations, '..', '..'),
    script: process.execPath,
    args: [path.join(__dirname, 'iniciar-supervisor.cjs'), '-Continuo'],
    env: { COMPUTERNAME: require('node:os').hostname() },
    interpreter: 'none',
    exec_mode: 'fork',
    instances: 1,
    // O supervisor controla o intervalo e encerra quando o operador pede uma pausa.
    autorestart: false,
    watch: false
  }]
};
