import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

const rootArgument = process.argv.find((arg) => /^--root=/.test(arg))?.slice('--root='.length);
const root = rootArgument ? path.resolve(rootArgument) : path.resolve(import.meta.dirname, '../..');
const baselinePath = path.join(root, 'database/baseline/001_schema_foundation_baseline.sql');
const manifestPath = path.join(root, 'database/manifest/epoch-v104-v105-inventory.json');
const through = Number(process.argv.find((arg) => /^--through=/.test(arg))?.split('=')[1] ?? 105);
const check = process.argv.includes('--check');
const printOnly = process.argv.includes('--print-only');
const outputArgument = process.argv.find((arg) => /^--output=/.test(arg))?.slice('--output='.length);
const outputPath = outputArgument ? path.resolve(root, outputArgument) : manifestPath;
if (![17, 104, 105].includes(through)) throw new Error('EPOCH_VERSION_REQUIRED');

function masked(source) {
  const out = [...source];
  let state = 'normal';
  for (let i = 0; i < source.length; i++) {
    const a = source[i], b = source[i + 1];
    if (state === 'normal') {
      if (a === '-' && b === '-') { out[i] = out[++i] = ' '; state = 'line'; }
      else if (a === '/' && b === '*') { out[i] = out[++i] = ' '; state = 'block'; }
      else if (a === "'") { out[i] = ' '; state = 'string'; }
    } else if (state === 'line') {
      if (a === '\n') state = 'normal'; else out[i] = ' ';
    } else if (state === 'block') {
      if (a === '*' && b === '/') { out[i] = out[++i] = ' '; state = 'normal'; }
      else if (a !== '\n') out[i] = ' ';
    } else if (state === 'string') {
      if (a === "'" && b === "'") { out[i] = out[++i] = ' '; }
      else if (a === "'") { out[i] = ' '; state = 'normal'; }
      else if (a !== '\n') out[i] = ' ';
    }
  }
  if (state === 'block' || state === 'string') throw new Error('UNCLOSED_SQL_LEXEME');
  return out.join('');
}

const namePart = '(?:\\[[^\\]]+\\]|[A-Za-z_][A-Za-z_0-9]*)';
const qname = `(${namePart})\\s*\\.\\s*(${namePart})`;
const objectKind = '(TABLE|VIEW|PROCEDURE|PROC|FUNCTION|TRIGGER|TYPE)';
function clean(identifier) { return identifier.replace(/^\[|\]$/g, ''); }
function key(schema, name) { return `${schema}.${name}`; }
function sha(bytes) { return crypto.createHash('sha256').update(bytes).digest('hex').toUpperCase(); }
function lineOf(source, offset) { return source.slice(0, offset).split('\n').length; }
function mapKey(item) {
  return item.table && item.index ? `${item.schema}.${item.table}.${item.name}`
    : `${item.schema}.${item.name}`;
}
function add(map, item) {
  const id = mapKey(item);
  if (map.has(id)) throw new Error(`DUPLICATE_CREATE ${id} ${item.source}`);
  map.set(id, item);
}
function remove(map, schema, name, source) {
  const id = key(schema, name);
  if (!map.delete(id)) throw new Error(`DROP_UNKNOWN ${id} ${source}`);
}
function balancedClose(source, open) {
  let depth = 0;
  for (let i = open; i < source.length; i++) {
    if (source[i] === '(') depth++;
    else if (source[i] === ')' && --depth === 0) return i;
  }
  throw new Error(`UNCLOSED_PAREN ${open}`);
}
function topLevelSegments(body) {
  const parts = [];
  let depth = 0, start = 0;
  for (let i = 0; i < body.length; i++) {
    if (body[i] === '(') depth++;
    else if (body[i] === ')') depth--;
    else if (body[i] === ',' && depth === 0) {
      parts.push({text: body.slice(start, i), offset: start});
      start = i + 1;
    }
  }
  parts.push({text: body.slice(start), offset: start});
  return parts;
}
function keyColumns(segment, keyword, fallback) {
  const at = segment.search(keyword);
  if (at < 0) return fallback;
  const open = segment.indexOf('(', at);
  if (open < 0) return fallback;
  const close = balancedClose(segment, open);
  return segment.slice(open + 1, close).split(',').map((value) =>
    clean(value.trim().split(/\s+/)[0])).join(',');
}
function unnamedInSegment(segment, schema, table, origin) {
  const content = segment.trim();
  if (!content) return [];
  const tableLevel = /^(?:PRIMARY\s+KEY|FOREIGN\s+KEY|UNIQUE|CHECK|CONSTRAINT)\b/i.test(content);
  const first = content.match(new RegExp(`^(${namePart})`));
  const column = tableLevel ? '' : clean(first?.[1] ?? '');
  const named = new Set([...content.matchAll(new RegExp(`\\bCONSTRAINT\\s+${namePart}\\s+(PRIMARY\\s+KEY|FOREIGN\\s+KEY|REFERENCES|UNIQUE|CHECK|DEFAULT)`, 'gi'))]
    .map((match) => constraintType(match[1])));
  const found = [];
  const push = (type, columns, reference = '') => found.push({schema, table, type,
    columns, reference, ...origin});
  if (/\bPRIMARY\s+KEY\b/i.test(content) && !named.has('PK')) {
    push('PK', tableLevel ? keyColumns(content, /\bPRIMARY\s+KEY\b/i, '') : column);
  }
  if (/\bUNIQUE\b/i.test(content) && !named.has('UQ')) {
    push('UQ', tableLevel ? keyColumns(content, /\bUNIQUE\b/i, '') : column);
  }
  if (/\bREFERENCES\b/i.test(content) && !named.has('F')) {
    const ref = content.match(new RegExp(`\\bREFERENCES\\s+${qname}`, 'i'));
    if (!ref) throw new Error(`UNQUALIFIED_UNNAMED_REFERENCE ${schema}.${table} ${origin.source}:${origin.line}`);
    push('F', /\bFOREIGN\s+KEY\b/i.test(content)
      ? keyColumns(content, /\bFOREIGN\s+KEY\b/i, '') : column,
      `${clean(ref[1]).toLowerCase()}.${clean(ref[2])}`);
  }
  if (/\bCHECK\s*\(/i.test(content) && !named.has('C')) push('C', column);
  if (/\bDEFAULT\b/i.test(content) && !named.has('D')) push('D', column);
  for (const item of found) if (!item.columns && item.type !== 'C') {
    throw new Error(`UNNAMED_COLUMN_UNKNOWN ${schema}.${table} ${item.type} ${origin.source}:${origin.line}`);
  }
  return found;
}
function constraintType(fragment) {
  const head = fragment.trimStart().toUpperCase();
  if (head.startsWith('PRIMARY KEY')) return 'PK';
  if (head.startsWith('FOREIGN KEY') || head.startsWith('REFERENCES')) return 'F';
  if (head.startsWith('UNIQUE')) return 'UQ';
  if (head.startsWith('CHECK')) return 'C';
  if (head.startsWith('DEFAULT')) return 'D';
  throw new Error(`UNCLASSIFIED_CONSTRAINT ${head.slice(0, 60)}`);
}

const baseline = fs.readFileSync(baselinePath, 'utf8');
const includes = [...baseline.matchAll(/^:r\s+"\.\.\\migrations\\(V(\d{3})__[^"\r\n]+\.sql)"\s*$/gm)]
  .map((match) => ({file: match[1], version: Number(match[2])}));
if (includes.length !== 105 || includes.some((entry, i) => entry.version !== i + 1)) {
  throw new Error('BASELINE_NOT_V001_V105_CONTIGUOUS');
}
const files = includes.filter((entry) => entry.version <= through);
const appSchemas = new Set(['ctl', 'stg', 'core', 'ref', 'mart', 'pub', 'recon']);
const objects = new Map(), types = new Map(), constraints = new Map(), indexes = new Map();
const unnamedConstraints = [];
const migrationHashes = [];

for (const entry of files) {
  const file = path.join(root, 'database/migrations', entry.file);
  const bytes = fs.readFileSync(file);
  const sql = bytes.toString('utf8');
  const source = masked(sql);
  migrationHashes.push({version: entry.version, file: entry.file, sha256: sha(bytes)});
  const events = [];
  const objectPattern = new RegExp(`\\b(CREATE(?:\\s+OR\\s+ALTER)?|DROP)\\s+${objectKind}\\s+${qname}`, 'gi');
  for (const match of source.matchAll(objectPattern)) {
    const verb = match[1].toUpperCase().replace(/\s+/g, ' ');
    const kind = match[2].toUpperCase();
    const schema = clean(match[3]).toLowerCase(), name = clean(match[4]);
    if (!appSchemas.has(schema)) continue;
    const origin = {source: entry.file, line: lineOf(sql, match.index)};
    let type = {TABLE: 'U', VIEW: 'V', PROCEDURE: 'P', PROC: 'P', TRIGGER: 'TR', TYPE: 'TT'}[kind];
    if (kind === 'FUNCTION') {
      const rest = source.slice(match.index + match[0].length).split(/^GO\s*$/im)[0];
      type = /\bRETURNS\s+@\w+\s+TABLE\b/i.test(rest) ? 'TF'
        : /\bRETURNS\s+TABLE\b/i.test(rest) ? 'IF' : 'FN';
    }
    events.push({at: match.index, action: verb.startsWith('DROP') ? 'drop' : 'create',
      replace: verb === 'CREATE OR ALTER', kind, schema, name, type, origin});
  }
  const broadDdl = /\b(?:CREATE(?:\s+OR\s+ALTER)?|DROP)\s+(?:TABLE|VIEW|PROCEDURE|PROC|FUNCTION|TRIGGER|TYPE)\s+/gi;
  for (const match of source.matchAll(broadDdl)) {
    const lineStart = source.lastIndexOf('\n', match.index) + 1;
    const prefix = source.slice(lineStart, match.index).trim().toUpperCase();
    if (/^(?:GRANT|DENY|REVOKE)\b/.test(prefix)) continue;
    const next = source.slice(match.index + match[0].length).trimStart();
    if (next.startsWith('#') || next.startsWith('@')) continue;
    if (events.some((event) => event.at === match.index)) continue;
    if (new RegExp(`^${qname}`).test(next) && /^\[?dbo\]?\s*\./i.test(next)) continue;
    throw new Error(`UNMODELLED_DDL ${entry.file}:${lineOf(sql, match.index)} ${match[0].trim()}`);
  }
  const indexPattern = new RegExp(`\\b(CREATE|DROP)\\s+(?:UNIQUE\\s+)?(?:CLUSTERED\\s+|NONCLUSTERED\\s+)?INDEX\\s+(${namePart})\\s+ON\\s+${qname}`, 'gi');
  for (const match of source.matchAll(indexPattern)) {
    const schema = clean(match[3]).toLowerCase(), table = clean(match[4]);
    if (!appSchemas.has(schema)) continue;
    events.push({at: match.index, action: match[1].toUpperCase() === 'DROP' ? 'dropIndex' : 'createIndex',
      schema, name: clean(match[2]), table, origin: {source: entry.file, line: lineOf(sql, match.index)}});
  }
  const tablePattern = new RegExp(`\\bCREATE\\s+TABLE\\s+${qname}\\s*\\(`, 'gi');
  for (const match of source.matchAll(tablePattern)) {
    const schema = clean(match[1]).toLowerCase(), table = clean(match[2]);
    if (!appSchemas.has(schema)) continue;
    const open = source.indexOf('(', match.index + match[0].length - 1);
    const close = balancedClose(source, open);
    const body = source.slice(open + 1, close);
    const named = new RegExp(`\\bCONSTRAINT\\s+(${namePart})\\s+`, 'gi');
    for (const constraint of body.matchAll(named)) {
      const name = clean(constraint[1]);
      events.push({at: open + 1 + constraint.index, action: 'createConstraint', schema, table, name,
        type: constraintType(body.slice(constraint.index + constraint[0].length)),
        origin: {source: entry.file, line: lineOf(sql, open + 1 + constraint.index)}});
    }
    for (const segment of topLevelSegments(body)) {
      const origin = {source: entry.file, line: lineOf(sql, open + 1 + segment.offset)};
      for (const item of unnamedInSegment(segment.text, schema, table, origin)) {
        events.push({at: open + 1 + segment.offset, action: 'createUnnamedConstraint', ...item});
      }
    }
  }
  const alterPattern = new RegExp(`\\bALTER\\s+TABLE\\s+${qname}\\s+(?:WITH\\s+(?:NO)?CHECK\\s+)?(ADD|DROP)\\s+CONSTRAINT\\s+(${namePart})\\s*`, 'gi');
  for (const match of source.matchAll(alterPattern)) {
    const schema = clean(match[1]).toLowerCase(), table = clean(match[2]);
    if (!appSchemas.has(schema)) continue;
    const action = match[3].toUpperCase() === 'ADD' ? 'createConstraint' : 'dropConstraint';
    events.push({at: match.index, action, schema, table, name: clean(match[4]),
      type: action === 'createConstraint' ? constraintType(source.slice(match.index + match[0].length)) : undefined,
      origin: {source: entry.file, line: lineOf(sql, match.index)}});
  }
  const addColumnPattern = new RegExp(`\\bALTER\\s+TABLE\\s+${qname}\\s+ADD\\s+(?!CONSTRAINT\\b)([^;]+);`, 'gi');
  for (const match of source.matchAll(addColumnPattern)) {
    const schema = clean(match[1]).toLowerCase(), table = clean(match[2]);
    if (!appSchemas.has(schema)) continue;
    for (const segment of topLevelSegments(match[3])) {
      const origin = {source: entry.file, line: lineOf(sql, match.index + segment.offset)};
      for (const item of unnamedInSegment(segment.text, schema, table, origin)) {
        events.push({at: match.index + segment.offset, action: 'createUnnamedConstraint', ...item});
      }
    }
  }
  events.sort((a, b) => a.at - b.at);
  for (const event of events) {
    if (event.action === 'create') {
      const map = event.kind === 'TYPE' ? types : objects;
      if (event.replace && map.has(key(event.schema, event.name))) continue;
      add(map, {schema: event.schema, name: event.name, type: event.type, ...event.origin});
    } else if (event.action === 'drop') {
      remove(event.kind === 'TYPE' ? types : objects, event.schema, event.name, entry.file);
      if (event.kind === 'TABLE') {
        for (const [id, value] of constraints) if (value.schema === event.schema && value.table === event.name) constraints.delete(id);
        for (const [id, value] of indexes) if (value.schema === event.schema && value.table === event.name) indexes.delete(id);
        for (let i = unnamedConstraints.length - 1; i >= 0; i--) {
          if (unnamedConstraints[i].schema === event.schema && unnamedConstraints[i].table === event.name) {
            unnamedConstraints.splice(i, 1);
          }
        }
      }
    } else if (event.action === 'createConstraint') {
      add(constraints, {schema: event.schema, table: event.table, name: event.name,
        type: event.type, ...event.origin});
    } else if (event.action === 'dropConstraint') {
      remove(constraints, event.schema, event.name, entry.file);
    } else if (event.action === 'createUnnamedConstraint') {
      unnamedConstraints.push({schema: event.schema, table: event.table, type: event.type,
        columns: event.columns, reference: event.reference, source: event.source, line: event.line});
    } else if (event.action === 'createIndex') {
      add(indexes, {schema: event.schema, table: event.table, index: true,
        name: event.name, ...event.origin});
    } else if (event.action === 'dropIndex') {
      if (!indexes.delete(`${event.schema}.${event.table}.${event.name}`)) {
        throw new Error(`DROP_UNKNOWN_INDEX ${event.schema}.${event.table}.${event.name} ${entry.file}`);
      }
    }
  }
  if (entry.version === 105) {
    for (const item of [
      {schema: 'ctl', table: 'execution_audit', name: 'CK_ctl_execution_audit_status', variable: 'status'},
      {schema: 'ref', table: 'expansion_lab_label', name: 'CK_exp_label', variable: 'label'},
    ]) {
      if (!sql.includes(`QUOTENAME(N'${item.name}')`) ||
          !sql.includes(`(SELECT ${item.variable}_definition FROM #v105_before)`) ||
          !sql.includes(`EXEC sys.sp_executesql @recreate_${item.variable};`)) {
        throw new Error(`V105_DYNAMIC_CHECK_REBUILD_NOT_PROVED ${item.name}`);
      }
      add(constraints, {schema: item.schema, table: item.table, name: item.name,
        type: 'C', source: entry.file, line: lineOf(sql, sql.indexOf(`QUOTENAME(N'${item.name}')`))});
    }
  }
}
const sorted = (map) => [...map.values()].sort((a, b) =>
  `${a.schema}.${a.name}`.localeCompare(`${b.schema}.${b.name}`, 'en'));
const result = {
  epoch: through,
  baseline: path.relative(root, baselinePath).replaceAll('\\', '/'),
  baselineSha256: sha(fs.readFileSync(baselinePath)),
  migrations: migrationHashes,
  excludedInfrastructure: [{schema: 'ctl', name: 'flyway_schema_history', type: 'U'}],
  objects: sorted(objects), tableTypes: sorted(types),
  constraints: sorted(constraints), unnamedConstraints: unnamedConstraints.sort((a, b) =>
    `${a.schema}.${a.table}.${a.type}.${a.columns}.${a.reference}`.localeCompare(
      `${b.schema}.${b.table}.${b.type}.${b.columns}.${b.reference}`, 'en')),
  indexes: sorted(indexes),
};
const output = JSON.stringify(result, null, 2) + '\n';
if (printOnly) {
  // Use this to compare adjacent epochs without modifying reviewed manifests.
} else if (check) {
  if (!fs.existsSync(outputPath) || fs.readFileSync(outputPath, 'utf8') !== output) {
    throw new Error('EPOCH_INVENTORY_DRIFT');
  }
} else fs.writeFileSync(outputPath, output, 'utf8');
console.log(`EPOCH_INVENTORY_${check ? 'CHECK' : 'BUILT'} V${through} objects=${objects.size} types=${types.size} constraints=${constraints.size} unnamed=${unnamedConstraints.length} indexes=${indexes.size}`);
