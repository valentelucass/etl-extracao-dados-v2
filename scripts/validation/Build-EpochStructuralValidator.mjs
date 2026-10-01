import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

const root = path.resolve(import.meta.dirname, '../..');
const manifestPath = path.join(root, 'database/manifest/epoch-v104-v105-inventory.json');
const templatePath = path.join(root, 'scripts/validation/epoch-validator-template.sql');
const validatorPath = path.join(root, 'database/validation/063_validate_epoch_v104_v105.sql');
const check = process.argv.includes('--check');
const bytes = (file) => fs.readFileSync(file);
const sha = (value) => crypto.createHash('sha256').update(value).digest('hex').toUpperCase();
const manifest = JSON.parse(bytes(manifestPath).toString('utf8'));
if (manifest.epoch !== 105 || manifest.migrations.length !== 105 ||
    manifest.baselineSha256 !== sha(bytes(path.join(root, manifest.baseline)))) {
  throw new Error('EPOCH_MANIFEST_BASELINE_DRIFT');
}
for (const entry of manifest.migrations) {
  if (entry.sha256 !== sha(bytes(path.join(root, 'database/migrations', entry.file)))) {
    throw new Error(`EPOCH_MIGRATION_DRIFT ${entry.file}`);
  }
}
const quoted = (value) => `N'${value.replaceAll("'", "''")}'`;
function insertRows(target, columns, rows) {
  if (!rows.length) throw new Error(`EMPTY_INVENTORY ${target}`);
  const lines = [];
  for (let i = 0; i < rows.length; i += 900) {
    lines.push(`INSERT ${target} (${columns.join(', ')}) VALUES`);
    lines.push(rows.slice(i, i + 900).map((row) => `    (${row.map(quoted).join(', ')})`).join(',\n') + ';');
  }
  return lines.join('\n');
}
const inserts = [
  insertRows('@objects', ['schema_name', 'object_name', 'object_type'],
    manifest.objects.map((item) => [item.schema, item.name, item.type])),
  insertRows('@types', ['schema_name', 'type_name'],
    manifest.tableTypes.map((item) => [item.schema, item.name])),
  insertRows('@constraints', ['schema_name', 'table_name', 'constraint_name', 'constraint_type'],
    manifest.constraints.map((item) => [item.schema, item.table, item.name, item.type])),
  insertRows('@unnamed', ['schema_name', 'table_name', 'constraint_type', 'columns_text', 'reference_name'],
    manifest.unnamedConstraints.map((item) =>
      [item.schema, item.table, item.type, item.columns, item.reference])),
  insertRows('@indexes', ['schema_name', 'table_name', 'index_name'],
    manifest.indexes.map((item) => [item.schema, item.table, item.name])),
].join('\n\n');
const template = bytes(templatePath).toString('utf8');
if (template.split('__INVENTORY_INSERTS__').length !== 2) throw new Error('EPOCH_TEMPLATE_MARKER');
const generated = template.replace('__INVENTORY_INSERTS__',
  `-- Generated from epoch-v104-v105-inventory.json SHA-256 ${sha(bytes(manifestPath))}.\n${inserts}`);
if (check) {
  if (!fs.existsSync(validatorPath) || bytes(validatorPath).toString('utf8') !== generated) {
    throw new Error('EPOCH_VALIDATOR_DRIFT');
  }
} else fs.writeFileSync(validatorPath, generated, 'utf8');
console.log(`EPOCH_VALIDATOR_${check ? 'CHECK' : 'BUILT'} objects=${manifest.objects.length} types=${manifest.tableTypes.length} constraints=${manifest.constraints.length} unnamed=${manifest.unnamedConstraints.length} indexes=${manifest.indexes.length}`);
