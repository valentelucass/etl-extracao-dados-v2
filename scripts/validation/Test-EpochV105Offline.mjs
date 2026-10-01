import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import {execFileSync} from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

const root = path.resolve(import.meta.dirname, '../..');
const read = (relative) => fs.readFileSync(path.join(root, relative), 'utf8');
const migration = read('database/migrations/V105__align_audit_and_expansion_label_bin2.sql');
const v042 = read('database/migrations/V042__consume_expansion_references.sql');
const v025 = read('database/migrations/V025__restore_coletas_extraction_audit.sql');
const v024 = read('database/migrations/V024__bind_source_protocols_and_users_runtime.sql');
const manifest = JSON.parse(read('database/manifest/epoch-v104-v105-inventory.json'));
const repair = JSON.parse(read('database/manifest/epoch-v105-collation-repair.json'));
const snapshot = read('database/validation/064_snapshot_epoch_v105.sql');

execFileSync(process.execPath, [path.join(root, 'scripts/validation/Build-EpochSchemaInventory.mjs'),
  '--through=105', '--check'], {cwd: root});
execFileSync(process.execPath, [path.join(root, 'scripts/validation/Build-EpochStructuralValidator.mjs'),
  '--check'], {cwd: root});

const procedure = (sql, name) => {
  const escaped = name.replaceAll('.', '\\.');
  const match = sql.match(new RegExp(`CREATE\\s+PROCEDURE\\s+${escaped}[\\s\\S]*?\\r?\\nGO`, 'i'));
  assert.ok(match, `missing procedure ${name}`);
  return match[0].replaceAll('\r\n', '\n');
};
const originalImport = procedure(v042, 'ref.usp_import_expansion_references');
assert.equal(procedure(migration, 'ref.usp_import_expansion_references'), originalImport,
  'V105 procedure body, signature and four-column receipt must equal V042');
const utf16Digest = (source) => crypto.createHash('sha256').update(Buffer.from(source, 'utf16le'))
  .digest('hex').toUpperCase();
const procedureBody = originalImport.replace(/\nGO$/, '');
assert.equal(Buffer.byteLength(procedureBody, 'utf16le'),
  repair.reviewedDefinitions.procedureV042Utf16Bytes);
assert.equal(utf16Digest(procedureBody), repair.reviewedDefinitions.procedureV042Sha256Utf16Le);
assert.ok(migration.includes(`0x${repair.reviewedDefinitions.procedureV042Sha256Utf16Le}`));
assert.match(v025, /CK_ctl_execution_audit_status CHECK \(status IN \(N'STARTED', N'COMPLETED', N'FAILED'\)\)/);
assert.match(v042, /CK_exp_label CHECK\(family_code=N'EXPANSION_LABELS'/);
assert.match(v042, /LEN\(raw_value\)>0 AND DATALENGTH\(raw_value\)=DATALENGTH\(LTRIM\(RTRIM\(raw_value\)\)\) AND LEN\(label\)>0/);
const canonicalChecks = [
  ['statusCheck', "([status]=N'FAILED' OR [status]=N'COMPLETED' OR [status]=N'STARTED')"],
  ['labelCheck', "([family_code]=N'EXPANSION_LABELS' AND ([category]='FAT_CTE' OR [category]='CAP_CLASS' OR [category]='CAP_TYPE') AND len([raw_value])>(0) AND datalength([raw_value])=datalength(ltrim(rtrim([raw_value]))) AND len([label])>(0))"],
];
for (const [name, value] of canonicalChecks) {
  assert.equal(Buffer.byteLength(value, 'utf16le'), repair.reviewedDefinitions[`${name}SqlServerUtf16Bytes`]);
  assert.equal(utf16Digest(value), repair.reviewedDefinitions[`${name}SqlServerSha256Utf16Le`]);
  assert.ok(migration.includes(`0x${repair.reviewedDefinitions[`${name}SqlServerSha256Utf16Le`]}`));
}
const reviewedGuardTokens = [
  `0x${repair.reviewedDefinitions.procedureV042Sha256Utf16Le}`,
  `0x${repair.reviewedDefinitions.statusCheckSqlServerSha256Utf16Le}`,
  `0x${repair.reviewedDefinitions.labelCheckSqlServerSha256Utf16Le}`,
  'suppress_dup_key_messages = 0', 'data_compression = 0',
];
const reviewedGuardFaults = (source) => reviewedGuardTokens.filter((token) => !source.includes(token));
assert.deepEqual(reviewedGuardFaults(migration), []);
for (const token of reviewedGuardTokens) {
  assert.deepEqual(reviewedGuardFaults(migration.replaceAll(token, 'REVIEWED_GUARD_MUTANT')), [token]);
}
assert.equal(repair.procedure.parameterCount, 10);
assert.equal(repair.procedure.tableTypeParameterOrdinal, 6);
assert.deepEqual(repair.procedure.receiptColumnsInOrder,
  ['label_release', 'calendar_release', 'branch_release', 'payer_release']);

function compact(text) { return text.replace(/\s+/g, ''); }
function successorFaults(source) {
  const match = source.match(/CREATE\s+OR\s+ALTER\s+PROCEDURE\s+ctl\.usp_control_plane_register_source[\s\S]*?\r?\nGO/i);
  if (!match) return ['PROCEDURE_MISSING'];
  const body = compact(match[0]);
  const terms = [
    'DATALENGTH(@source_instance)>256',
    'SET@source_instance=LTRIM(RTRIM(@source_instance));',
    'FROMctl.source_catalogWITH(UPDLOCK,HOLDLOCK)',
    'DECLARE@nowDATETIME2(3)=SYSUTCDATETIME();',
    'VALUES(@source_instance,@source_kind,1,@now)',
  ];
  const at = terms.map((term) => body.indexOf(term));
  const faults = [];
  if (at.some((position) => position < 0) || at.some((position, i) => i > 0 && position <= at[i - 1])) {
    faults.push('ORDER');
  }
  if (!body.includes('v.protocolCOLLATELatin1_General_100_BIN2=@source_kindCOLLATELatin1_General_100_BIN2')) {
    faults.push('BIN2');
  }
  return faults;
}
assert.deepEqual(successorFaults(v024), []);
const lengthLine = 'IF DATALENGTH(@source_instance)>256 OR DATALENGTH(@source_kind)>128';
const trimLine = 'SET @source_instance=LTRIM(RTRIM(@source_instance));';
assert.ok(v024.includes(lengthLine) && v024.includes(trimLine));
const lengthMutant = v024.replace(trimLine, '').replace(lengthLine, `${trimLine}\n    ${lengthLine}`);
assert.ok(successorFaults(lengthMutant).includes('ORDER'));
const clockLine = 'DECLARE @now DATETIME2(3)=SYSUTCDATETIME();';
assert.ok(v024.includes(clockLine));
const clockMutant = v024.replace(clockLine, '').replace(
  'SELECT @existing_kind=source_kind,@active=active FROM ctl.source_catalog WITH(UPDLOCK,HOLDLOCK)',
  `${clockLine}\n        SELECT @existing_kind=source_kind,@active=active FROM ctl.source_catalog WITH(UPDLOCK,HOLDLOCK)`);
assert.ok(successorFaults(clockMutant).includes('ORDER'));
const bin2Mutant = v024.replace('v.protocol COLLATE Latin1_General_100_BIN2=@source_kind COLLATE Latin1_General_100_BIN2',
  'v.protocol=@source_kind');
assert.ok(successorFaults(bin2Mutant).includes('BIN2'));

function migrationCollationFaults(source) {
  const fields = [
    /ALTER TABLE ctl\.execution_audit\s+ALTER COLUMN status NVARCHAR\(20\) COLLATE Latin1_General_100_BIN2 NOT NULL;/,
    /ALTER TABLE ctl\.execution_audit\s+ALTER COLUMN traversal_verification NVARCHAR\(64\) COLLATE Latin1_General_100_BIN2 NULL;/,
    /ALTER TABLE ctl\.execution_audit\s+ALTER COLUMN failure_category NVARCHAR\(100\) COLLATE Latin1_General_100_BIN2 NULL;/,
    /ALTER TABLE ref\.expansion_lab_label\s+ALTER COLUMN label NVARCHAR\(128\) COLLATE Latin1_General_100_BIN2 NOT NULL;/,
    /CREATE TYPE ref\.expansion_lab_label_batch AS TABLE \([\s\S]*?label NVARCHAR\(128\) COLLATE Latin1_General_100_BIN2 NOT NULL,[\s\S]*?PRIMARY KEY \(category, raw_value\)/,
  ];
  return fields.flatMap((pattern, index) => pattern.test(source) ? [] : [index]);
}
assert.deepEqual(migrationCollationFaults(migration), []);
for (const [from, to] of [
  ['ALTER COLUMN status NVARCHAR(20) COLLATE Latin1_General_100_BIN2', 'ALTER COLUMN status NVARCHAR(20) COLLATE Latin1_General_100_CI_AS_SC'],
  ['ALTER COLUMN traversal_verification NVARCHAR(64) COLLATE Latin1_General_100_BIN2', 'ALTER COLUMN traversal_verification NVARCHAR(64) COLLATE Latin1_General_100_CI_AS_SC'],
  ['ALTER COLUMN failure_category NVARCHAR(100) COLLATE Latin1_General_100_BIN2', 'ALTER COLUMN failure_category NVARCHAR(100) COLLATE Latin1_General_100_CI_AS_SC'],
  ['ALTER COLUMN label NVARCHAR(128) COLLATE Latin1_General_100_BIN2', 'ALTER COLUMN label NVARCHAR(128) COLLATE Latin1_General_100_CI_AS_SC'],
  ['label NVARCHAR(128) COLLATE Latin1_General_100_BIN2 NOT NULL,\n    PRIMARY KEY',
    'label NVARCHAR(128) COLLATE Latin1_General_100_CI_AS_SC NOT NULL,\n    PRIMARY KEY'],
]) {
  assert.ok(migration.includes(from), `mutation anchor absent: ${from}`);
  assert.equal(migrationCollationFaults(migration.replace(from, to)).length, 1);
}

const requiredGuardTokens = [
  'V105_EXACT_SHADOW_REQUIRED', 'V105_EXISTING_STATUS_NOT_BIN2_CANONICAL',
  'V105_TVP_PROCEDURE_OR_PRIVILEGE_DRIFT', 'V105_AGGREGATE_DATA_DRIFT',
  'DROP PROCEDURE ref.usp_import_expansion_references;',
  'DROP TYPE ref.expansion_lab_label_batch;',
  'CREATE INDEX IX_ctl_execution_audit_status_started_at',
  'EXEC sys.sp_executesql @recreate_status;', 'EXEC sys.sp_executesql @recreate_label;',
  'COMMIT TRANSACTION;',
];
for (const token of requiredGuardTokens) assert.ok(migration.includes(token), `V105 guard missing: ${token}`);
const beforeDrop = migration.slice(0,
  migration.indexOf('DROP PROCEDURE ref.usp_import_expansion_references;'));
function dependencyFaults(sql) {
  return [
    'sys.stats_columns', 'V105_UNREVIEWED_COLUMN_STATISTICS',
    'sys.sql_expression_dependencies AS d', 'd.referenced_class = 6',
    'sys.foreign_key_columns', 'sys.fulltext_index_columns',
    'sys.computed_columns', 'sys.default_constraints',
  ].filter((token) => !sql.includes(token));
}
assert.deepEqual(dependencyFaults(beforeDrop), []);
for (const token of ['sys.stats_columns', 'd.referenced_class = 6',
  'sys.foreign_key_columns', 'sys.fulltext_index_columns']) {
  assert.deepEqual(dependencyFaults(beforeDrop.replace(token, 'REMOVED_BY_MUTANT')), [token]);
}
for (const token of ['V105_STATISTICS_BLOCKERS', 'V105_TYPE_DEPENDENCIES',
  'V105_COLUMN_DEPENDENCIES', 'auto_created_stats', 'other_expression_references',
  'other_schema_bound_references', 'V105_REVIEWED_DEFINITIONS',
  'procedure_v042_bytes_match', 'status_index_v025_options_match']) {
  assert.ok(snapshot.includes(token), `readback misses ${token}`);
}
assert.ok(!/\b(?:BACKUP|RESTORE|CREATE|ALTER|DROP|DELETE|UPDATE|MERGE|TRUNCATE)\b\s+(?:DATABASE|TABLE|PROCEDURE|TYPE|INDEX|STATISTICS|FROM|INTO)/i.test(snapshot),
  'readback must remain read-only');
assert.equal(manifest.objects.length, 569);
assert.equal(manifest.tableTypes.length, 23);
assert.equal(manifest.constraints.length, 1019);
assert.equal(manifest.unnamedConstraints.length, 224);
for (const [schema, table, type, columns] of [
  ['core', 'analytic_raster_stop', 'F', 'last_observation_id'],
  ['core', 'analytic_raster_trip', 'F', 'last_observation_id'],
  ['core', 'expansion_lab_root', 'D', 'amount_state'],
]) {
  assert.ok(manifest.unnamedConstraints.some((entry) => entry.schema === schema &&
    entry.table === table && entry.type === type && entry.columns === columns),
  `migration-derived unnamed constraint missing: ${schema}.${table}.${columns}`);
}
assert.equal(manifest.indexes.length, 100);

// Rebuild in a private fixture. The mutants change DDL, not a manifest hash:
// each must alter the independently derived inventory instead of passing by
// copying the deployed catalog into an allowlist.
const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'p08-v105-inventory-'));
try {
  fs.mkdirSync(path.join(temp, 'database/migrations'), {recursive: true});
  fs.mkdirSync(path.join(temp, 'database/baseline'), {recursive: true});
  fs.mkdirSync(path.join(temp, 'database/manifest'), {recursive: true});
  fs.copyFileSync(path.join(root, 'database/baseline/001_schema_foundation_baseline.sql'),
    path.join(temp, 'database/baseline/001_schema_foundation_baseline.sql'));
  for (const entry of manifest.migrations) {
    fs.copyFileSync(path.join(root, 'database/migrations', entry.file),
      path.join(temp, 'database/migrations', entry.file));
  }
  const subject = path.join(temp, 'database/migrations/V105__align_audit_and_expansion_label_bin2.sql');
  const output = path.join(temp, 'database/manifest/epoch-v104-v105-inventory.json');
  const build = () => {
    execFileSync(process.execPath, [path.join(root, 'scripts/validation/Build-EpochSchemaInventory.mjs'),
      `--root=${temp}`, '--through=105'], {cwd: root});
    return JSON.parse(fs.readFileSync(output, 'utf8'));
  };
  const initial = build();
  assert.deepEqual([initial.objects.length, initial.constraints.length,
    initial.unnamedConstraints.length, initial.indexes.length], [569, 1019, 224, 100]);
  const earlier = (version) => {
    const privatePath = path.join(temp, `database/manifest/epoch-v${version}.private.json`);
    execFileSync(process.execPath, [path.join(root, 'scripts/validation/Build-EpochSchemaInventory.mjs'),
      `--root=${temp}`, `--through=${version}`, `--output=${privatePath}`], {cwd: root});
    return JSON.parse(fs.readFileSync(privatePath, 'utf8'));
  };
  const v104 = earlier(104);
  for (const property of ['objects', 'tableTypes', 'constraints', 'unnamedConstraints', 'indexes']) {
    const identity = (item) => [item.schema, item.table ?? '', item.name ?? '',
      item.type ?? '', item.columns ?? '', item.reference ?? ''].join('|');
    assert.deepEqual(initial[property].map(identity).sort(), v104[property].map(identity).sort(),
      `V105 must rebuild without changing the closed ${property} inventory`);
  }
  const v17 = earlier(17);
  assert.equal(initial.objects.length - v17.objects.length, 334,
    'V104 objects added after V017 must reconcile the original 005 object failures');
  fs.writeFileSync(subject, migration + '\nCREATE VIEW ref.epoch_mutant AS SELECT 1 AS probe;\nGO\n');
  assert.equal(build().objects.length, 570);
  fs.writeFileSync(subject, migration + '\nALTER TABLE ctl.execution_audit ADD CONSTRAINT CK_epoch_mutant CHECK (pages_fetched >= 0);\n');
  assert.equal(build().constraints.length, 1020);
  fs.writeFileSync(subject, migration + '\nCREATE TABLE ctl.epoch_mutant (id INT NOT NULL PRIMARY KEY);\n');
  assert.equal(build().unnamedConstraints.length, 225);
  fs.writeFileSync(subject, migration.replace(
    'CREATE INDEX IX_ctl_execution_audit_status_started_at\n    ON ctl.execution_audit (status, started_at DESC);', ''));
  assert.equal(build().indexes.length, 99);
} finally {
  const allowed = path.resolve(os.tmpdir()) + path.sep;
  assert.ok(path.resolve(temp).startsWith(allowed) &&
    path.basename(temp).startsWith('p08-v105-inventory-'), 'unsafe private fixture cleanup');
  fs.rmSync(temp, {recursive: true, force: true});
}
console.log('EPOCH_V105_OFFLINE_PASS: source V024 order/BIN2 mutants=3, collation mutants=5, dependency mutants=4, inventory mutants=4, procedure V042 exact, baseline/manifest/validator checks');
