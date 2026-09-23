package br.com.esl.etl.v2.plataforma.qualificacao;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticScenarioVariant;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Bounded surrogate-ID context; business expectations always come from independent source rules.
 */
public final class QualificationLineageEvidence {
    private final QualificationWireOracle wire;
    private final Map<String, ObjectNode> technical = new HashMap<>();
    private final int roots;
    private final int revision;
    private final boolean correction;
    private final int manifestCohortCaptures;

    public QualificationLineageEvidence(
            final ColetaTemporalLaboratorySession session,
            final UUID run,
            final UUID expansion,
            final int roots,
            final int revision,
            final boolean correction)
            throws SQLException, IOException {
        this(session, run, expansion, roots, revision, correction, 1);
    }

    public QualificationLineageEvidence(
            final ColetaTemporalLaboratorySession session,
            final UUID run,
            final UUID expansion,
            final int roots,
            final int revision,
            final boolean correction,
            final int manifestCohortCaptures)
            throws SQLException, IOException {
        this(
                session,
                run,
                expansion,
                roots,
                revision,
                correction,
                manifestCohortCaptures,
                AnalyticScenarioVariant.BASELINE);
    }

    public QualificationLineageEvidence(
            final ColetaTemporalLaboratorySession session,
            final UUID run,
            final UUID expansion,
            final int roots,
            final int revision,
            final boolean correction,
            final int manifestCohortCaptures,
            final AnalyticScenarioVariant variant)
            throws SQLException, IOException {
        this(
                session,
                run,
                expansion,
                roots,
                revision,
                correction,
                manifestCohortCaptures,
                new QualificationWireOracle(variant));
    }

    public QualificationLineageEvidence(
            final ColetaTemporalLaboratorySession session,
            final UUID run,
            final UUID expansion,
            final int roots,
            final int revision,
            final boolean correction,
            final int manifestCohortCaptures,
            final QualificationWireOracle wire)
            throws SQLException, IOException {
        if (roots < 2
                || roots > 32
                || revision < 1
                || revision > 1000
                || manifestCohortCaptures < 1
                || manifestCohortCaptures > 8) {
            throw new IllegalArgumentException("QUAL_LINEAGE_CONTEXT_LIMIT");
        }
        this.roots = roots;
        this.revision = revision;
        this.correction = correction;
        this.manifestCohortCaptures = manifestCohortCaptures;
        this.wire = java.util.Objects.requireNonNull(wire);
        expansion(session, expansion);
        manifests(session, run);
        quotations(session, run);
        collections(session, run);
    }

    public int retainedTechnicalRecords() {
        return technical.size();
    }

    public String manifestDifference(final int root, final JsonNode actual) {
        final var context = technical.get(key("MAN", root, 1));
        if (context == null) {
            return "CONTEXT_MISSING";
        }
        return wire.manifestDifference(
                wire.source("MAN", root, 1, revision, correction), actual, context);
    }

    public boolean compare(
            final String entity, final int root, final int component, final JsonNode actual) {
        final var context = technical.get(key(entity, root, component));
        if (context == null && !entity.equals("FRE") && !entity.equals("LOC")) {
            return false;
        }
        return wire.compare(
                entity,
                wire.source(entity, root, component, revision, correction),
                actual,
                context == null ? JsonNodeFactory.instance.objectNode() : context);
    }

    public String componentIdentity(final String entity, final int root, final int component) {
        return wire.componentIdentity(entity, root, component);
    }

    private void expansion(final ColetaTemporalLaboratorySession session, final UUID run)
            throws SQLException {
        try (var connection = session.getConnection();
                var sql =
                        connection.prepareStatement(
                                "SELECT TOP(257) vertical,root_key,component_key,execution_id,occurrence,observation_id,revision,"
                                        + "root_type,part_type,part_key,component_type"
                                        + " FROM core.expansion_lab_current_input WHERE run_id=?"
                                        + " ORDER BY vertical,root_key,component_key")) {
            sql.setQueryTimeout(30);
            sql.setString(1, run.toString());
            int count = 0;
            try (var rows = sql.executeQuery()) {
                while (rows.next()) {
                    if (++count > roots * 8) {
                        throw new SQLException("QUAL_LINEAGE_EXPANSION_COUNT");
                    }
                    final String entity = rows.getString(1);
                    if (!java.util.Set.of("CAP", "FAT", "INV", "SIN").contains(entity)) {
                        throw new SQLException("QUAL_LINEAGE_ENTITY");
                    }
                    final String rootKey = rows.getString(2);
                    final int root = wire.expansionRootOrdinal(entity, rows.getString(8), rootKey);
                    final int component =
                            wire.expansionComponentOrdinal(
                                    entity,
                                    rows.getString(8),
                                    rootKey,
                                    rows.getString(9),
                                    rows.getString(10),
                                    rows.getString(11),
                                    rows.getString(3));
                    put(
                            key(entity, root, component),
                            JsonNodeFactory.instance
                                    .objectNode()
                                    .put("execution_id", rows.getString(4))
                                    .put("occurrence", rows.getLong(5))
                                    .put("observation_id", rows.getLong(6))
                                    .put("revision", rows.getInt(7)));
                }
            }
            if (count != roots * 8) {
                throw new SQLException("QUAL_LINEAGE_EXPANSION_COUNT");
            }
        }
    }

    private void manifests(final ColetaTemporalLaboratorySession session, final UUID run)
            throws SQLException {
        try (var connection = session.getConnection();
                var sql =
                        connection.prepareStatement(
                                "SELECT TOP(33) c.source_key,s.snapshot_id,s.execution_id FROM core.analytic_manifest_current c"
                                        + " LEFT JOIN mart.analytic_manifest_current f ON f.run_id=c.run_id AND f.source_key=c.source_key"
                                        + " LEFT JOIN mart.analytic_manifest_observation o ON o.observation_id=f.observation_id"
                                        + " JOIN core.analytic_manifest_snapshot s ON s.snapshot_id=COALESCE(o.snapshot_id,c.snapshot_id)"
                                        + " WHERE c.run_id=? ORDER BY c.source_key")) {
            sql.setQueryTimeout(30);
            sql.setString(1, run.toString());
            int count = 0;
            try (var rows = sql.executeQuery()) {
                while (rows.next()) {
                    if (++count > roots) {
                        throw new SQLException("QUAL_LINEAGE_MANIFEST_COUNT");
                    }
                    final int root = Integer.parseInt(rows.getString(1).substring(8));
                    final var input = wire.source("MAN", root, 1, revision, correction);
                    final var fresh = OffsetDateTime.parse(input.path("finished_at").asText());
                    put(
                            key("MAN", root, 1),
                            JsonNodeFactory.instance
                                    .objectNode()
                                    .put("snapshot_id", rows.getLong(2))
                                    .put("execution_id", rows.getString(3))
                                    .put("source_rows", 3 * manifestCohortCaptures)
                                    .put("fresh_second", fresh.toEpochSecond())
                                    .put("fresh_nano", fresh.getNano()));
                }
            }
            if (count != roots) {
                throw new SQLException("QUAL_LINEAGE_MANIFEST_COUNT");
            }
        }
    }

    private void quotations(final ColetaTemporalLaboratorySession session, final UUID run)
            throws SQLException {
        try (var connection = session.getConnection();
                var sql =
                        connection.prepareStatement(
                                "SELECT TOP(33) c.source_key,s.source_execution,s.stage_record_id,s.snapshot_id,s.previous_snapshot"
                                        + " FROM core.analytic_quote_current c"
                                        + " JOIN core.analytic_quote_snapshot s ON s.snapshot_id=c.snapshot_id"
                                        + " WHERE c.run_id=? ORDER BY c.source_key")) {
            sql.setQueryTimeout(30);
            sql.setString(1, run.toString());
            int count = 0;
            try (var rows = sql.executeQuery()) {
                while (rows.next()) {
                    if (++count > roots) {
                        throw new SQLException("QUAL_LINEAGE_QUOTE_COUNT");
                    }
                    final int root = Integer.parseInt(rows.getString(1).substring(8)) - 9999;
                    final var node =
                            JsonNodeFactory.instance
                                    .objectNode()
                                    .put("source_execution", rows.getString(2))
                                    .put("stage_record_id", rows.getLong(3))
                                    .put("snapshot_id", rows.getLong(4));
                    nullable(node, "previous_snapshot", rows.getObject(5));
                    put(key("COT", root, 1), node);
                }
            }
            if (count != roots) {
                throw new SQLException("QUAL_LINEAGE_QUOTE_COUNT");
            }
        }
    }

    private void collections(final ColetaTemporalLaboratorySession session, final UUID run)
            throws SQLException, IOException {
        // A constant number of set queries provides IDs, never source/consumer values or monetary
        // goldens.
        try (var connection = session.getConnection();
                var sql =
                        connection.prepareStatement(
                                "SELECT TOP(33) c.source_key,s.snapshot_id,s.previous_snapshot,s.source_execution,s.representative_stage,"
                                        + " l.request_hour_supplement_id FROM core.analytic_collection_current c"
                                        + " JOIN core.analytic_collection_snapshot s ON s.snapshot_id=c.snapshot_id"
                                        + " LEFT JOIN core.analytic_collection_supplement_effective l"
                                        + " ON l.run_id=c.run_id AND l.source_key=c.source_key"
                                        + " WHERE c.run_id=? ORDER BY c.source_key")) {
            sql.setQueryTimeout(30);
            sql.setString(1, run.toString());
            int count = 0;
            try (var rows = sql.executeQuery()) {
                while (rows.next()) {
                    if (++count > roots) {
                        throw new SQLException("QUAL_LINEAGE_COLLECTION_COUNT");
                    }
                    final int root = Integer.parseInt(rows.getString(1).substring(8)) - 200000;
                    final var node =
                            JsonNodeFactory.instance
                                    .objectNode()
                                    .put("snapshot_id", rows.getLong(2))
                                    .put("source_execution", rows.getString(4))
                                    .put("representative_stage", rows.getLong(5));
                    nullable(node, "previous_snapshot", rows.getObject(3));
                    node.set("graphqlLateral", QualificationWireOracle.lateral(rows.getLong(6)));
                    node.putArray("lineage");
                    put(key("COL", root, 1), node);
                }
            }
            if (count != roots) {
                throw new SQLException("QUAL_LINEAGE_COLLECTION_COUNT");
            }
        }
        collectionLineage(session, run);
        collectionReferences(session, run);
    }

    private void collectionReferences(final ColetaTemporalLaboratorySession session, final UUID run)
            throws SQLException {
        try (var connection = session.getConnection();
                var sql =
                        connection.prepareStatement(
                                "SELECT TOP(33) c.source_key,mc.link_id,branch.binding_id,regions.reference_release_id"
                                        + " FROM core.analytic_collection_current c"
                                        + " JOIN ctl.analytic_lab_source_group g ON g.run_id=c.run_id"
                                        + " JOIN core.relational_lab_link mc ON mc.run_id=g.relational_run"
                                        + " AND mc.relation_kind='MC' AND mc.target_key=c.source_key AND mc.active=1"
                                        + " OUTER APPLY(SELECT d.binding_id"
                                        + " FROM ref.ufn_analytic_dimension(c.run_id,2,CONVERT(DATE,'20360401')) d"
                                        + " WHERE d.entity='COL' AND d.source_key=c.source_key AND d.role='BRANCH') branch"
                                        + " OUTER APPLY ref.ufn_analytic_collection_region(c.run_id,2,CONVERT(DATE,'20360401')) regions"
                                        + " WHERE c.run_id=? ORDER BY c.source_key")) {
            sql.setQueryTimeout(30);
            sql.setString(1, run.toString());
            int count = 0;
            try (var rows = sql.executeQuery()) {
                while (rows.next()) {
                    if (++count > roots) {
                        throw new SQLException("QUAL_LINEAGE_COLLECTION_BINDING_COUNT");
                    }
                    final int root = Integer.parseInt(rows.getString(1).substring(8)) - 200000;
                    final var node = technical.get(key("COL", root, 1));
                    node.putObject("manifest")
                            .put("sourceKey", "INTEGER:" + root)
                            .put("linkId", rows.getLong(2));
                    node.put("branchBinding", rows.getLong(3))
                            .put("regionRelease", rows.getLong(4));
                    if (rows.getLong(2) < 1 || rows.getLong(3) < 1 || rows.getLong(4) < 1) {
                        throw new SQLException("QUAL_LINEAGE_REFERENCE_MISSING");
                    }
                }
            }
            if (count != roots) {
                throw new SQLException("QUAL_LINEAGE_COLLECTION_BINDING_COUNT");
            }
        }
    }

    private void collectionLineage(final ColetaTemporalLaboratorySession session, final UUID run)
            throws SQLException {
        try (var connection = session.getConnection();
                var sql =
                        connection.prepareStatement(
                                "SELECT TOP(97) c.source_key,l.stage_record_id FROM core.analytic_collection_current c"
                                        + " JOIN core.analytic_collection_lineage l ON l.snapshot_id=c.snapshot_id"
                                        + " WHERE c.run_id=? ORDER BY c.source_key,l.stage_record_id")) {
            sql.setQueryTimeout(30);
            sql.setString(1, run.toString());
            int count = 0;
            try (var rows = sql.executeQuery()) {
                while (rows.next()) {
                    if (++count > roots * 3) {
                        throw new SQLException("QUAL_LINEAGE_COHORT_LIMIT");
                    }
                    final int root = Integer.parseInt(rows.getString(1).substring(8)) - 200000;
                    final var node = technical.get(key("COL", root, 1));
                    ((com.fasterxml.jackson.databind.node.ArrayNode) node.path("lineage"))
                            .addObject()
                            .put("stage_record_id", rows.getLong(2));
                }
            }
            if (count != roots * 3) {
                throw new SQLException("QUAL_LINEAGE_COHORT_COUNT");
            }
        }
    }

    private String key(final String entity, final int root, final int component) {
        if (root < 1 || root > roots || component < 1 || component > 2) {
            throw new IllegalArgumentException("QUAL_LINEAGE_KEY");
        }
        return entity + ":" + root + ":" + component;
    }

    private void put(final String key, final ObjectNode value) {
        if (technical.size() >= 352 || technical.putIfAbsent(key, value) != null) {
            throw new IllegalArgumentException("QUAL_LINEAGE_DUPLICATE_OR_LIMIT");
        }
    }

    private static void nullable(final ObjectNode node, final String key, final Object value) {
        if (value == null) {
            node.putNull(key);
        } else {
            node.put(key, ((Number) value).longValue());
        }
    }
}
