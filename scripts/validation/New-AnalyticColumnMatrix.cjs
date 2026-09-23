// Offline consolidation of observed SQL metadata and versioned legacy field inventory.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const root = path.resolve(__dirname, '../..');
const catalog = 'docs/catalogos/macrobloco-analitico';
const round = 'target/macrobloco-analitico-20260912-01';
const read = p => JSON.parse(fs.readFileSync(path.join(root, p), 'utf8').replace(/^\uFEFF/, ''));
const hash = value => crypto.createHash('sha256').update(value).digest('hex');
const definitions = read(`${round}/analytic-view-definitions-all-01.json`);
const metadataPath = `${round}/metadata-after-monitoring-01-sql-metadata.json`;
const metadata = read(metadataPath);
const initial = read(`${catalog}/contratos-colunas-inicial.json`);
const typed = read('src/main/resources/analytic-laboratory/query-contracts.synthetic.json');
const views = new Map(definitions.map(d => [`${d.schemaName}.${d.localName}`, d.definition]));

// This lexer only locates projection delimiters; SQL Server supplied types and nullability.
function delimiters(sql) {
  const tokens = [];
  let depth = 0;
  for (let i = 0; i < sql.length;) {
    if (sql.startsWith('--', i)) { const end = sql.indexOf('\n', i); i = end < 0 ? sql.length : end + 1; continue; }
    if (sql.startsWith('/*', i)) { const end = sql.indexOf('*/', i + 2); if (end < 0) throw Error('SQL_COMMENT'); i = end + 2; continue; }
    if (sql[i] === "'" || sql[i] === '[' || sql[i] === '"') {
      const close = sql[i] === '[' ? ']' : sql[i]; i++;
      while (i < sql.length) { if (sql[i++] === close) { if (sql[i] === close) i++; else break; } }
      continue;
    }
    if (sql[i] === '(') { depth++; i++; continue; }
    if (sql[i] === ')') { if (--depth < 0) throw Error('SQL_DEPTH'); i++; continue; }
    if (depth === 0 && sql[i] === ',') tokens.push({word: ',', start: i, end: i + 1});
    if (/[A-Za-z_]/.test(sql[i])) {
      const start = i++;
      while (i < sql.length && /[A-Za-z0-9_]/.test(sql[i])) i++;
      if (depth === 0) tokens.push({word: sql.slice(start, i).toUpperCase(), start, end: i});
    } else i++;
  }
  if (depth !== 0) throw Error('SQL_UNCLOSED');
  return tokens;
}
function projection(name, seen = []) {
  if (seen.includes(name) || !views.has(name)) throw Error(`VIEW_DEPENDENCY_${name}`);
  const sql = views.get(name), tokens = delimiters(sql);
  const select = tokens.find(t => t.word === 'SELECT');
  const from = tokens.find(t => t.word === 'FROM' && t.start > select.end);
  if (!from) throw Error(`VIEW_PROJECTION_${name}`);
  const commas = tokens.filter(t => t.word === ',' && t.start > select.end && t.start < from.start);
  let start = select.end;
  const expressions = [...commas, from].map(t => { const value = sql.slice(start, t.start).trim(); start = t.end; return value; });
  if (expressions.length === 1 && expressions[0] === '*') {
    const dependency = /^\s*([a-z0-9_]+\.[a-z0-9_]+)/i.exec(sql.slice(from.end));
    if (!dependency) throw Error(`VIEW_STAR_${name}`);
    const inner = projection(dependency[1], [...seen, name]);
    return {expressions: inner.expressions, definitionChain: [name, ...inner.definitionChain]};
  }
  if (expressions.some(e => /^(?:\w+\.)?\*$/.test(e))) throw Error(`UNEXPANDED_STAR_${name}`);
  return {expressions, definitionChain: [name]};
}
const responsibilities = [
 ['PUB-04/MAT-04', 'documento fiscal canônico por cliente/filial e recorte, com política explícita', 'FinancialQueries'],
 ['PUB-01/MAT-01', 'Frete canônico preparado, indicador PE como dimensão de responsabilidade', 'FreightOperational,FreightFallback,FreightAttributes'],
 ['PUB-02/COL-11', 'Coleta canônica atual, ausência candidata visível e Usuários por binding', 'CollectionQueries,CollectionSweep'],
 ['PUB-02/V2-013', 'Coleta canônica com ausência confirmada; não conta repetição da observação', 'CollectionSweep,CollectionSweepIsolation'],
 ['PUB-03', 'Cotação canônica capturada com tarifa governada', 'Quotes,ObservationModes'],
 ['PUB-04', 'componente CAP canônico, pagamento e conciliação tri-state', 'FinancialQueries'],
 ['PUB-05', 'Localização canônica atual por binding, sem fan-out', 'FreightLocationQueries'],
 ['PUB-07/MAN-01..07', 'raiz MAN canônica atual com display tipado e fatos preparados', 'Manifests,ManifestCapture,ManifestGates'],
 ['PUB-07/MAT-05', 'fato por Manifesto canônico/competência; Frete deduplicado antes da soma', 'Manifests,ManifestGates,FreightPaths'],
 ['PUB-07', 'evento técnico de captura/carga/ciclo; instantes ausentes permanecem nulos', 'Monitoring'],
 ['PUB-06', 'raiz Inventário canônica ativa; vínculo Frete unívoco e referência vigente', 'InventoryIncidentQueries'],
 ['PUB-06', 'raiz Sinistro canônica ativa; veículo/documentos vinculados', 'InventoryIncidentQueries'],
 ['PUB-08/RAS-01..06', 'viagem e parada canônicas ativas; prioridade direta/fallback temporal', 'RasterTransit,RasterProofs'],
 ['PUB-07/V2-035', 'Filial canônica usada por fonte/tenant/release/vigência', 'Dimensions'],
 ['PUB-07/V2-035', 'Cliente canônico usado por fonte/tenant/release/vigência', 'Dimensions'],
 ['PUB-07/V2-035', 'Veículo por chave declarada; placa normalizada é atributo', 'Dimensions,FleetReferences'],
 ['PUB-07/V2-035', 'Motorista por chave declarada; homônimo e filtro genérico com política explícita', 'Dimensions,FleetReferences'],
 ['PUB-07/V2-035', 'Plano de Contas por chave e classificação governadas', 'Dimensions'],
 ['PUB-07/V2-035', 'Usuário ativo capturado, nome normalizado sem merge por nome', 'Users,ObservationModes']
];
const result = [];
for (const [index, legacy] of initial.contracts.entries()) {
  const number = index + 1, name = `analytic_lab_sql_${String(number).padStart(2, '0')}`;
  const actual = metadata.filter(c => c.localName === name);
  const parsed = projection(`pub.${name}`);
  if (parsed.expressions.length !== actual.length) throw Error(`PROJECTION_COUNT_${name}_${parsed.expressions.length}_${actual.length}`);
  const contract = typed.contracts.find(c => c.id === legacy.id);
  if (!contract || contract.columns.length !== legacy.columns.length) throw Error(`TYPED_COUNT_${name}`);
  const [rule, grain, tests] = responsibilities[index];
  const columns = legacy.columns.map((column, i) => {
    const physical = actual[i], reader = contract.columns[i];
    if (column.ordinal !== i + 1 || column.name !== physical.columnName || reader.name !== column.name ||
        reader.type !== physical.sqlType || reader.precision !== physical.precision || reader.scale !== physical.scale ||
        reader.nullable !== physical.nullable) throw Error(`COLUMN_METADATA_${name}_${i}`);
    return {ordinal: i + 1, name: column.name, legacyExpression: column.legacyExpression,
      localExpression: parsed.expressions[i], physicalMetadata: physical,
      rule, lineage: parsed.definitionChain, semanticContext: `SQL-${String(number).padStart(2, '0')}:definitionContext`,
      consumer: 'JdbcAnalyticQueries + AnalyticScenarioRuntime', state: 'IMPLEMENTED_TYPED_LOCAL',
      divergencePolicy: 'ADR0050: local synthetic bindings/references; source gaps stay gated; no external compatibility acceptance'};
  });
  result.push({id: legacy.id, legacyName: legacy.legacyName, legacySource: legacy.source,
    legacySourceSha256: legacy.sourceSha256, localName: `pub.${name}`, rule, grain,
    definitionContext: 'definicoes-sql-observadas.json: full FROM/JOIN, predicates, CTE, labels and fallback expressions; ordinal projection expanded through explicit view chain',
    definitionChain: parsed.definitionChain.map(n => ({name:n, sha256:hash(views.get(n))})),
    directTests: tests.split(',').map(n => `AnalyticLaboratory${n}IT`),
    sharedTests: ['AnalyticLaboratoryQueryReaderIT', 'AnalyticScenarioRuntimeIT', 'JAR contract selection'],
    evidenceLayer: 'SQL_SERVER_METADATA_AND_TYPED_JDBC; final campaign identified in verification-summary.json',
    localColumns: actual.length, businessColumns: columns.length, columns});
}
if (result.length !== 19 || result.reduce((n,c) => n+c.columns.length,0) !== 673 || metadata.length !== 971) throw Error('MATRIX_COUNTS');
const output = `${catalog}/matriz-colunas-final.json`;
for (const p of [output, `${catalog}/definicoes-sql-observadas.json`]) {
  if (fs.existsSync(path.join(root, p))) throw Error(`MATRIX_OUTPUT_EXISTS_${p}`);
}
fs.writeFileSync(path.join(root, `${catalog}/definicoes-sql-observadas.json`), JSON.stringify({
  scope:'READ_ONLY_LOCAL_DEFINITION_SNAPSHOT_NOT_AN_INSTALLER', migrationsThrough:98,
  definitions: definitions.map(d => ({...d, sha256:hash(d.definition)}))},null,2)+'\n');
fs.writeFileSync(path.join(root, output), JSON.stringify({version:1, businessColumns:673, physicalColumns:971,
  metadataSource:metadataPath, metadataSha256:hash(fs.readFileSync(path.join(root,metadataPath))),
  identityIsSynthetic:true, externalCompatibilityAccepted:false, contracts:result},null,2)+'\n');
process.stdout.write('ANALYTIC_COLUMN_MATRIX_19_673_971\n');
