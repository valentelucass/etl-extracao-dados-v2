package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.modulos.fretes.aplicacao.FreightAnalyticAttributesMapper;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticDimensionBinding;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticFiscalAttribute;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticFreightRelationBinding;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticManifestState;
import br.com.esl.etl.v2.plataforma.analitico.FreightSupplementObservation;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionFreightTerms;
import br.com.esl.etl.v2.plataforma.fonte.graphql.AnalyticCollectionSupplementMapper;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticCollectionSupplements;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticDimensions;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticFiscalAttributes;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticFreightRelations;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticManifestCompositions;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticManifestState;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcFreightAnalyticAttributes;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.relacional.JdbcRelationalLaboratory;
import br.com.esl.etl.v2.plataforma.qualificacao.PinnedLocalJson;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.relacional.RelationalBinding;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Explicit lateral inputs. Source keys select captured provenance; SQL surrogates are never input
 * identities.
 */
public final class DeclaredAnalyticSupport {
    private static final int PREFLIGHT_FILTER_BITS = 1 << 20;
    private static final List<String> KINDS =
            List.of(
                    "financial",
                    "dimensions",
                    "relational",
                    "freight",
                    "collections",
                    "manifestStates",
                    "freightRelations",
                    "compositions",
                    "fiscal");
    private final PinnedLocalJson manifest;
    private final Map<String, List<PinnedLocalJson>> batches = new LinkedHashMap<>();
    private final LocalDate start;
    private final LocalDate end;
    private final int revision;

    public DeclaredAnalyticSupport(
            final PinnedLocalJson manifest,
            final LocalDate start,
            final LocalDate end,
            final int revision,
            final CancellationToken token)
            throws IOException {
        this.manifest = manifest;
        this.start = start;
        this.end = end;
        this.revision = revision;
        final var root = manifest.read();
        QualificationJson.fields(root, "version", "origin", "complete", "batches");
        if (!"local-analytic-support-v1".equals(QualificationJson.text(root, "version", 40))
                || !"LOCAL_SYNTHETIC_ARTIFACT_V1".equals(QualificationJson.text(root, "origin", 40))
                || !QualificationJson.flag(root, "complete")) {
            throw new IllegalArgumentException("INTEGRAL_SUPPORT_INCOMPLETE");
        }
        QualificationJson.fields(root.path("batches"), KINDS.toArray(String[]::new));
        for (final var kind : KINDS) {
            final var descriptors = root.path("batches").path(kind);
            QualificationJson.array(descriptors, 1, 128);
            final var files = new ArrayList<PinnedLocalJson>();
            for (final var descriptor : descriptors) {
                files.add(
                        PinnedLocalJson.reference(
                                manifest.file().toPath().getParent(), descriptor, 131072));
            }
            batches.put(kind, List.copyOf(files));
        }
        verifyFiles(token);
    }

    public void verifyFiles(final CancellationToken token) throws IOException {
        manifest.verify();
        for (final var kind : KINDS) {
            final var files = batches.get(kind);
            final var seen = new BitSet(PREFLIGHT_FILTER_BITS);
            for (int index = 0; index < files.size(); index++) {
                token.throwIfCancellationRequested();
                final var keys = new HashSet<String>();
                boolean possibleDuplicate = false;
                for (final var row : read(files.get(index))) {
                    token.throwIfCancellationRequested();
                    final String key = validate(kind, row);
                    if (!keys.add(key)) {
                        throw new IllegalArgumentException("INTEGRAL_SUPPORT_DUPLICATE");
                    }
                    possibleDuplicate |=
                            seen.get(filterSlot(key, 0))
                                    && seen.get(filterSlot(key, 1))
                                    && seen.get(filterSlot(key, 2));
                }
                // Preflight has no SQL or disk writes. Keep at most one batch of keys;
                // the fixed filter only avoids rereads. It never establishes key equality.
                for (int previous = 0; previous < index && possibleDuplicate; previous++) {
                    token.throwIfCancellationRequested();
                    for (final var row : read(files.get(previous))) {
                        token.throwIfCancellationRequested();
                        if (keys.contains(validate(kind, row))) {
                            throw new IllegalArgumentException("INTEGRAL_SUPPORT_DUPLICATE");
                        }
                    }
                }
                for (final var key : keys) {
                    seen.set(filterSlot(key, 0));
                    seen.set(filterSlot(key, 1));
                    seen.set(filterSlot(key, 2));
                }
            }
        }
    }

    private static int filterSlot(final String key, final int salt) {
        int hash = key.hashCode() ^ (0x9e3779b9 * salt);
        hash ^= hash >>> 16;
        hash *= 0x7feb352d;
        hash ^= hash >>> 15;
        hash *= 0x846ca68b;
        hash ^= hash >>> 16;
        return hash & (PREFLIGHT_FILTER_BITS - 1);
    }

    private static JsonNode read(final PinnedLocalJson pin) throws IOException {
        final var rows = pin.read();
        QualificationJson.array(rows, 0, 64);
        return rows;
    }

    private String validate(final String kind, final JsonNode row) {
        if (QualificationJson.number(row, "revision", 1, 1000) != revision) {
            throw new IllegalArgumentException("INTEGRAL_SUPPORT_REVISION");
        }
        switch (kind) {
            case "financial" -> {
                QualificationJson.fields(
                        row,
                        "sourceKey",
                        "revision",
                        "billingReferenceDate",
                        "classification",
                        "courtesy",
                        "eligible",
                        "fallbackVolumes",
                        "payerToken",
                        "currency",
                        "unit",
                        "active",
                        "evidence");
                return terms(row).sourceKey();
            }
            case "dimensions" -> {
                QualificationJson.fields(
                        row,
                        "entity",
                        "sourceKey",
                        "role",
                        "entityKey",
                        "revision",
                        "from",
                        "toExclusive",
                        "active",
                        "previousEntityKey",
                        "evidence");
                final var from = LocalDate.parse(text(row, "from"));
                final var to = LocalDate.parse(text(row, "toExclusive"));
                if (from.isAfter(start) || to.isBefore(end) || !from.isBefore(to)) {
                    throw new IllegalArgumentException("INTEGRAL_SUPPORT_DIMENSION_VALIDITY");
                }
                AnalyticDimensionBinding.Entity.valueOf(text(row, "entity"));
                AnalyticDimensionBinding.Role.valueOf(text(row, "role"));
                text(row, "entityKey");
                text(row, "evidence");
                QualificationJson.flag(row, "active");
                nullable(row, "previousEntityKey");
                return text(row, "entity") + ":" + text(row, "sourceKey") + ":" + text(row, "role");
            }
            case "relational" -> {
                QualificationJson.fields(
                        row,
                        "evidenceId",
                        "relation",
                        "origin",
                        "originComponent",
                        "target",
                        "targetComponent",
                        "targetDate",
                        "revision",
                        "cardinality");
                return relational(row).evidenceId();
            }
            case "freight" -> {
                QualificationJson.fields(row, "sourceKey", "revision", "evidence", "attributes");
                new FreightAnalyticAttributesMapper().map(row.path("attributes"));
                text(row, "evidence");
                return text(row, "sourceKey");
            }
            case "collections" -> {
                QualificationJson.fields(
                        row,
                        "sourceKey",
                        "revision",
                        "cancellationUserKey",
                        "destroyUserKey",
                        "attributes");
                QualificationJson.fields(row.path("attributes"), "version", "provenance", "data");
                if (!new AnalyticCollectionSupplementMapper().map(row.path("attributes")).valid()) {
                    throw new IllegalArgumentException("INTEGRAL_SUPPORT_COLLECTION_INVALID");
                }
                nullable(row, "cancellationUserKey");
                nullable(row, "destroyUserKey");
                return text(row, "sourceKey");
            }
            case "manifestStates" -> {
                QualificationJson.fields(row, "sourceKey", "revision", "active", "reactivate");
                QualificationJson.flag(row, "active");
                QualificationJson.flag(row, "reactivate");
                return text(row, "sourceKey");
            }
            case "freightRelations" -> {
                QualificationJson.fields(
                        row,
                        "kind",
                        "originKey",
                        "freightKey",
                        "revision",
                        "active",
                        "previousFreightKey");
                AnalyticFreightRelationBinding.Kind.valueOf(text(row, "kind"));
                QualificationJson.flag(row, "active");
                nullable(row, "previousFreightKey");
                return text(row, "kind")
                        + ":"
                        + text(row, "originKey")
                        + ":"
                        + text(row, "freightKey");
            }
            case "compositions" -> {
                QualificationJson.fields(row, "sourceKey", "revision", "expectedFreights");
                QualificationJson.number(row, "expectedFreights", 0, 10000);
                return text(row, "sourceKey");
            }
            case "fiscal" -> {
                QualificationJson.fields(
                        row, "root", "part", "component", "revision", "nfseSeries");
                nullable(row, "nfseSeries");
                // String keys may contain separators. Preserve all three tuple boundaries.
                return JsonNodeFactory.instance
                        .arrayNode()
                        .add(key(row.path("root")).storage())
                        .add(key(row.path("part")).storage())
                        .add(key(row.path("component")).storage())
                        .toString();
            }
            default -> throw new IllegalArgumentException("INTEGRAL_SUPPORT_KIND");
        }
    }

    public ExpansionFreightTerms financialTerms(final String sourceKey) {
        ExpansionFreightTerms result = null;
        try {
            manifest.verify();
            for (final var file : batches.get("financial")) {
                for (final var row : read(file)) {
                    if (text(row, "sourceKey").equals(sourceKey)) {
                        if (result != null) {
                            throw new IllegalArgumentException("INTEGRAL_FINANCIAL_DUPLICATE");
                        }
                        result = terms(row);
                    }
                }
            }
        } catch (final IOException failure) {
            throw new UncheckedIOException(failure);
        }
        if (result == null) {
            throw new IllegalArgumentException("INTEGRAL_FINANCIAL_MISSING");
        }
        return result;
    }

    public void apply(
            final ColetaTemporalLaboratorySession session,
            final UUID run,
            final UUID expansion,
            final UUID relationalRun,
            final UUID relationalFreight,
            final Clock clock,
            final CancellationToken token)
            throws SQLException, IOException {
        verifyFiles(token);
        final var counts = new LinkedHashMap<String, Integer>();
        for (final var kind : KINDS) {
            int count = 0;
            for (final var file : batches.get(kind)) {
                final var rows = read(file);
                for (int offset = 0; offset < rows.size(); offset += 16) {
                    token.throwIfCancellationRequested();
                    final var batch = new ArrayList<JsonNode>(16);
                    for (int index = offset; index < Math.min(offset + 16, rows.size()); index++) {
                        batch.add(rows.get(index));
                    }
                    applyBatch(
                            session,
                            run,
                            expansion,
                            relationalRun,
                            relationalFreight,
                            clock,
                            kind,
                            batch,
                            token);
                    count += batch.size();
                }
            }
            counts.put(kind, count);
        }
        // Exact coverage; a supplied row must resolve to one current source and every required
        // source needs its own row.
        for (final var entry :
                Map.of(
                                "financial",
                                "FRETE",
                                "freight",
                                "FRETE",
                                "collections",
                                "COL",
                                "manifestStates",
                                "MAN",
                                "compositions",
                                "MAN")
                        .entrySet()) {
            try (var connection = session.getConnection();
                    var sql =
                            connection.prepareStatement(
                                    "SELECT COUNT(DISTINCT source_key) FROM core.analytic_lab_source_current"
                                            + " WHERE run_id=? AND entity=? AND usable=1")) {
                sql.setQueryTimeout(10);
                sql.setString(1, run.toString());
                sql.setString(2, entry.getValue());
                try (var rows = sql.executeQuery()) {
                    if (!rows.next() || rows.getInt(1) != counts.get(entry.getKey())) {
                        throw new SQLException(
                                "INTEGRAL_SUPPORT_SOURCE_COVERAGE_" + entry.getKey());
                    }
                }
            }
        }
        try (var connection = session.getConnection();
                var sql =
                        connection.prepareStatement(
                                "SELECT COUNT_BIG(*) FROM core.expansion_lab_current_input WHERE run_id=? AND vertical='FAT'")) {
            sql.setQueryTimeout(10);
            sql.setString(1, expansion.toString());
            try (var rows = sql.executeQuery()) {
                if (!rows.next() || rows.getLong(1) != counts.get("fiscal")) {
                    throw new SQLException("INTEGRAL_SUPPORT_FISCAL_COVERAGE");
                }
            }
        }
    }

    private void applyBatch(
            final ColetaTemporalLaboratorySession session,
            final UUID run,
            final UUID expansion,
            final UUID relationalRun,
            final UUID relationalFreight,
            final Clock clock,
            final String kind,
            final List<JsonNode> batch,
            final CancellationToken token)
            throws SQLException {
        final var sources = sources(session, run, kind, batch);
        switch (kind) {
            case "financial" -> {
                // Financial terms have already reached the expansion adapter. The bounded lookup
                // above proves that every declared term has a captured current source.
            }
            case "dimensions" -> {
                final var bindings = new ArrayList<AnalyticDimensionBinding>(batch.size());
                for (final var row : batch) {
                    bindings.add(
                            new AnalyticDimensionBinding(
                                    AnalyticDimensionBinding.Entity.valueOf(text(row, "entity")),
                                    text(row, "sourceKey"),
                                    sources.get(
                                            new SourceIdentity(
                                                    text(row, "entity"), text(row, "sourceKey"))),
                                    AnalyticDimensionBinding.Role.valueOf(text(row, "role")),
                                    text(row, "entityKey"),
                                    revision,
                                    LocalDate.parse(text(row, "from")),
                                    LocalDate.parse(text(row, "toExclusive")),
                                    QualificationJson.flag(row, "active"),
                                    nullable(row, "previousEntityKey"),
                                    text(row, "evidence")));
                }
                new JdbcAnalyticDimensions(session).bindBatch(run, bindings, token);
            }
            case "relational" -> {
                final var bindings = new ArrayList<RelationalBinding>(batch.size());
                for (final var row : batch) {
                    bindings.add(relational(row));
                }
                new JdbcRelationalLaboratory(session, clock)
                        .bindBatch(relationalRun, bindings, token);
            }
            case "freight" -> {
                final var executions =
                        new LinkedHashMap<UUID, List<FreightSupplementObservation>>();
                for (final var row : batch) {
                    final var execution =
                            sources.get(new SourceIdentity("FRETE", text(row, "sourceKey")));
                    executions
                            .computeIfAbsent(execution, ignored -> new ArrayList<>())
                            .add(
                                    new FreightSupplementObservation(
                                            text(row, "sourceKey"),
                                            revision,
                                            new FreightAnalyticAttributesMapper()
                                                    .map(row.path("attributes")),
                                            text(row, "evidence")));
                }
                final var repository = new JdbcFreightAnalyticAttributes(session);
                for (final var execution : executions.entrySet()) {
                    token.throwIfCancellationRequested();
                    repository.captureBatch(run, execution.getKey(), execution.getValue(), token);
                }
            }
            case "collections" -> {
                final var bindings =
                        new ArrayList<JdbcAnalyticCollectionSupplements.Binding>(batch.size());
                for (final var row : batch) {
                    bindings.add(
                            new JdbcAnalyticCollectionSupplements.Binding(
                                    text(row, "sourceKey"),
                                    sources.get(new SourceIdentity("COL", text(row, "sourceKey"))),
                                    revision,
                                    new AnalyticCollectionSupplementMapper()
                                            .map(row.path("attributes")),
                                    nullable(row, "cancellationUserKey"),
                                    nullable(row, "destroyUserKey")));
                }
                new JdbcAnalyticCollectionSupplements(session).bind(run, bindings, token);
            }
            case "manifestStates" -> {
                final var states = new ArrayList<AnalyticManifestState>(batch.size());
                for (final var row : batch) {
                    states.add(
                            new AnalyticManifestState(
                                    text(row, "sourceKey"),
                                    sources.get(new SourceIdentity("MAN", text(row, "sourceKey"))),
                                    revision,
                                    QualificationJson.flag(row, "active"),
                                    QualificationJson.flag(row, "reactivate")));
                }
                new JdbcAnalyticManifestState(session).bindBatch(run, states, token);
            }
            case "freightRelations" -> {
                final var bindings = new ArrayList<AnalyticFreightRelationBinding>(batch.size());
                for (final var row : batch) {
                    final var relationKind =
                            AnalyticFreightRelationBinding.Kind.valueOf(text(row, "kind"));
                    final var origin =
                            relationKind == AnalyticFreightRelationBinding.Kind.DIRECT
                                    ? sources.get(new SourceIdentity("MAN", text(row, "originKey")))
                                    : relationalFreight;
                    bindings.add(
                            new AnalyticFreightRelationBinding(
                                    relationKind,
                                    text(row, "originKey"),
                                    origin,
                                    text(row, "freightKey"),
                                    sources.get(
                                            new SourceIdentity("FRETE", text(row, "freightKey"))),
                                    revision,
                                    QualificationJson.flag(row, "active"),
                                    nullable(row, "previousFreightKey")));
                }
                new JdbcAnalyticFreightRelations(session).bindBatch(run, bindings, token);
            }
            case "compositions" -> {
                final var declarations =
                        new ArrayList<JdbcAnalyticManifestCompositions.Declaration>(batch.size());
                for (final var row : batch) {
                    declarations.add(
                            new JdbcAnalyticManifestCompositions.Declaration(
                                    text(row, "sourceKey"),
                                    sources.get(new SourceIdentity("MAN", text(row, "sourceKey"))),
                                    revision,
                                    QualificationJson.number(row, "expectedFreights", 0, 10000)));
                }
                new JdbcAnalyticManifestCompositions(session).seal(run, declarations, token);
            }
            case "fiscal" -> applyFiscal(session, run, expansion, batch, token);
            default -> throw new IllegalArgumentException("INTEGRAL_SUPPORT_KIND");
        }
    }

    private record SourceIdentity(String entity, String key) {}

    private static Map<SourceIdentity, UUID> sources(
            final ColetaTemporalLaboratorySession session,
            final UUID run,
            final String kind,
            final List<JsonNode> batch)
            throws SQLException {
        final var requested = new java.util.LinkedHashSet<SourceIdentity>();
        for (final var row : batch) {
            switch (kind) {
                case "financial", "freight" ->
                        requested.add(new SourceIdentity("FRETE", text(row, "sourceKey")));
                case "dimensions" ->
                        requested.add(
                                new SourceIdentity(text(row, "entity"), text(row, "sourceKey")));
                case "collections" ->
                        requested.add(new SourceIdentity("COL", text(row, "sourceKey")));
                case "manifestStates", "compositions" ->
                        requested.add(new SourceIdentity("MAN", text(row, "sourceKey")));
                case "freightRelations" -> {
                    requested.add(new SourceIdentity("FRETE", text(row, "freightKey")));
                    if ("DIRECT".equals(text(row, "kind"))) {
                        requested.add(new SourceIdentity("MAN", text(row, "originKey")));
                    }
                }
                default -> {
                    // Relational bindings and fiscal attributes use their own typed identities.
                }
            }
        }
        final var result = new LinkedHashMap<SourceIdentity, UUID>();
        if (requested.isEmpty()) {
            return result;
        }
        final var json = JsonNodeFactory.instance.arrayNode();
        for (final var identity : requested) {
            json.addObject().put("entity", identity.entity()).put("sourceKey", identity.key());
        }
        try (var connection = session.getConnection();
                var sql =
                        connection.prepareStatement(
                                "SELECT DISTINCT q.entity,q.source_key,s.execution_id FROM OPENJSON(?)"
                                        + " WITH(entity varchar(16) '$.entity',source_key nvarchar(256) '$.sourceKey') q"
                                        + " LEFT JOIN core.analytic_lab_source_current s ON s.run_id=?"
                                        + " AND s.entity=q.entity AND s.source_key=q.source_key AND s.usable=1")) {
            sql.setQueryTimeout(10);
            sql.setFetchSize(16);
            sql.setMaxRows(33);
            sql.setNString(1, json.toString());
            sql.setString(2, run.toString());
            try (var rows = sql.executeQuery()) {
                while (rows.next()) {
                    final var identity = new SourceIdentity(rows.getString(1), rows.getString(2));
                    final var execution = rows.getString(3);
                    if (execution == null) {
                        throw new SQLException(
                                "INTEGRAL_SUPPORT_SOURCE_MISSING_" + identity.entity());
                    }
                    if (!requested.contains(identity)
                            || result.putIfAbsent(identity, UUID.fromString(execution)) != null) {
                        throw new SQLException("INTEGRAL_SUPPORT_SOURCE_AMBIGUOUS");
                    }
                }
            }
        }
        if (result.size() != requested.size()) {
            throw new SQLException("INTEGRAL_SUPPORT_SOURCE_COVERAGE");
        }
        return result;
    }

    private void applyFiscal(
            final ColetaTemporalLaboratorySession session,
            final UUID run,
            final UUID expansion,
            final List<JsonNode> batch,
            final CancellationToken token)
            throws SQLException {
        final var json = JsonNodeFactory.instance.arrayNode();
        for (int index = 0; index < batch.size(); index++) {
            final var row = batch.get(index);
            final var root = key(row.path("root"));
            final var part = key(row.path("part"));
            final var component = key(row.path("component"));
            json.addObject()
                    .put("ordinal", index)
                    .put("rootType", root.type().name())
                    .put("rootKey", root.value())
                    .put("partType", part.type().name())
                    .put("partKey", part.value())
                    .put("componentType", component.type().name())
                    .put("componentKey", component.value());
        }
        final var attributes = new LinkedHashMap<Integer, AnalyticFiscalAttribute>();
        try (var connection = session.getConnection();
                var sql =
                        connection.prepareStatement(
                                "SELECT q.ordinal,i.component_id,i.execution_id FROM OPENJSON(?) WITH("
                                        + "ordinal int '$.ordinal',root_type varchar(16) '$.rootType',"
                                        + "root_key nvarchar(256) '$.rootKey',part_type varchar(16) '$.partType',"
                                        + "part_key nvarchar(256) '$.partKey',component_type varchar(16) '$.componentType',"
                                        + "component_key nvarchar(256) '$.componentKey') q"
                                        + " JOIN core.expansion_lab_root r ON r.root_type=q.root_type AND r.root_key=q.root_key"
                                        + " JOIN core.expansion_lab_component c ON c.root_id=r.root_id"
                                        + " AND c.part_type=q.part_type AND c.part_key=q.part_key"
                                        + " AND c.component_type=q.component_type AND c.component_key=q.component_key"
                                        + " JOIN core.expansion_lab_current_input i ON i.component_id=c.component_id"
                                        + " AND i.run_id=? AND i.vertical='FAT'")) {
            sql.setQueryTimeout(10);
            sql.setFetchSize(16);
            sql.setMaxRows(17);
            sql.setNString(1, json.toString());
            sql.setString(2, expansion.toString());
            try (var rows = sql.executeQuery()) {
                while (rows.next()) {
                    final int ordinal = rows.getInt(1);
                    if (ordinal < 0 || ordinal >= batch.size() || attributes.containsKey(ordinal)) {
                        throw new SQLException("INTEGRAL_FISCAL_SOURCE_AMBIGUOUS");
                    }
                    attributes.put(
                            ordinal,
                            new AnalyticFiscalAttribute(
                                    rows.getLong(2),
                                    UUID.fromString(rows.getString(3)),
                                    revision,
                                    nullable(batch.get(ordinal), "nfseSeries")));
                }
            }
        }
        if (attributes.size() != batch.size()) {
            throw new SQLException("INTEGRAL_FISCAL_SOURCE_MISSING");
        }
        new JdbcAnalyticFiscalAttributes(session)
                .bindBatch(run, List.copyOf(attributes.values()), token);
    }

    private ExpansionFreightTerms terms(final JsonNode row) {
        return new ExpansionFreightTerms(
                text(row, "sourceKey"),
                revision,
                row.path("billingReferenceDate").isNull()
                        ? null
                        : LocalDate.parse(text(row, "billingReferenceDate")),
                nullable(row, "classification"),
                flagOrNull(row, "courtesy"),
                flagOrNull(row, "eligible"),
                row.path("fallbackVolumes").isNull()
                        ? null
                        : QualificationJson.number(row, "fallbackVolumes", 0, 1000000),
                QualificationJson.digest(row, "payerToken"),
                text(row, "currency"),
                text(row, "unit"),
                QualificationJson.flag(row, "active"),
                text(row, "evidence"));
    }

    private RelationalBinding relational(final JsonNode row) {
        final var date = LocalDate.parse(text(row, "targetDate"));
        if (date.isBefore(start) || !date.isBefore(end)) {
            throw new IllegalArgumentException("INTEGRAL_RELATIONAL_DATE");
        }
        return new RelationalBinding(
                text(row, "evidenceId"),
                RelationalBinding.Relation.valueOf(text(row, "relation")),
                key(row.path("origin")),
                key(row.path("originComponent")),
                key(row.path("target")),
                key(row.path("targetComponent")),
                date,
                revision,
                RelationalBinding.Cardinality.valueOf(text(row, "cardinality")));
    }

    private static RelationalBinding.Key key(final JsonNode row) {
        QualificationJson.fields(row, "type", "value");
        return new RelationalBinding.Key(
                RelationalBinding.WireType.valueOf(text(row, "type")), text(row, "value"));
    }

    private static String text(final JsonNode row, final String key) {
        return QualificationJson.text(row, key, 256);
    }

    private static String nullable(final JsonNode row, final String key) {
        return row.path(key).isNull() ? null : text(row, key);
    }

    private static Boolean flagOrNull(final JsonNode row, final String key) {
        return row.path(key).isNull() ? null : QualificationJson.flag(row, key);
    }
}
