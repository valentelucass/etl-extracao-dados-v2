package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticCollectionRegions;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticFleetReferences;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticQuoteTariffs;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticReferences;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionReferences;
import br.com.esl.etl.v2.plataforma.qualificacao.PinnedLocalJson;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Five explicit reference releases, retaining existing typed importers and SQL validation. */
public final class DeclaredAnalyticReferences {
    private static final List<String> FAMILIES =
            List.of("expansion", "dimensions", "fleet", "regions", "tariffs");
    private final PinnedLocalJson manifest;
    private final Map<String, PinnedLocalJson> files;
    private final int revision;
    private final LocalDate from;
    private final LocalDate toExclusive;
    private final JdbcAnalyticReferences.Policies policies;

    public DeclaredAnalyticReferences(
            final PinnedLocalJson manifest,
            final LocalDate start,
            final LocalDate end,
            final CancellationToken token)
            throws IOException {
        this.manifest = manifest;
        final var root = manifest.read();
        QualificationJson.fields(
                root,
                "version",
                "origin",
                "revision",
                "validFrom",
                "validToExclusive",
                "policies",
                "files");
        if (!"local-analytic-references-v1".equals(QualificationJson.text(root, "version", 40))
                || !"LOCAL_SYNTHETIC_ARTIFACT_V1"
                        .equals(QualificationJson.text(root, "origin", 40))) {
            throw new IllegalArgumentException("INTEGRAL_REFERENCES_SCOPE");
        }
        revision = QualificationJson.number(root, "revision", 1, 1000);
        from = LocalDate.parse(QualificationJson.text(root, "validFrom", 10));
        toExclusive = LocalDate.parse(QualificationJson.text(root, "validToExclusive", 10));
        if (from.isAfter(start)
                || toExclusive.isBefore(end)
                || !from.isBefore(toExclusive)
                || toExclusive.isAfter(from.plusDays(62))) {
            throw new IllegalArgumentException("INTEGRAL_REFERENCES_VALIDITY");
        }
        final var policy = root.path("policies");
        QualificationJson.fields(policy, "fiscal", "branch", "driver");
        policies =
                new JdbcAnalyticReferences.Policies(
                        JdbcAnalyticReferences.Fiscal.valueOf(
                                QualificationJson.text(policy, "fiscal", 40)),
                        JdbcAnalyticReferences.Branch.valueOf(
                                QualificationJson.text(policy, "branch", 40)),
                        JdbcAnalyticReferences.Driver.valueOf(
                                QualificationJson.text(policy, "driver", 40)));
        QualificationJson.fields(root.path("files"), FAMILIES.toArray(String[]::new));
        final var pins = new LinkedHashMap<String, PinnedLocalJson>();
        for (final var family : FAMILIES) {
            pins.put(
                    family,
                    PinnedLocalJson.reference(
                            manifest.file().toPath().getParent(),
                            root.path("files").path(family),
                            family.equals("expansion") ? 16384 : 32768));
        }
        files = Map.copyOf(pins);
        verifyFiles(token);
    }

    public void verifyFiles(final CancellationToken token) throws IOException {
        manifest.verify();
        for (final var family : FAMILIES) {
            token.throwIfCancellationRequested();
            final var data = files.get(family).read();
            if (!"FIXTURE_SINTETICA_EXPLICITA"
                    .equals(QualificationJson.text(data, "provenance", 40))) {
                throw new IllegalArgumentException("INTEGRAL_REFERENCES_PROVENANCE");
            }
            switch (family) {
                case "expansion" ->
                        QualificationJson.fields(
                                data,
                                "provenance",
                                "version",
                                "branchCode",
                                "branchLabel",
                                "payerReference",
                                "labels");
                case "dimensions" ->
                        QualificationJson.fields(
                                data, "provenance", "version", "labels", "registry", "exclusions");
                case "fleet" ->
                        QualificationJson.fields(
                                data,
                                "provenance",
                                "version",
                                "tokenScheme",
                                "normalization",
                                "documents",
                                "aliases",
                                "matrix",
                                "exceptions");
                case "regions" ->
                        QualificationJson.fields(
                                data, "provenance", "version", "normalizationVersion", "rows");
                case "tariffs" ->
                        QualificationJson.fields(data, "provenance", "version", "origin", "rows");
                default -> throw new IllegalArgumentException("INTEGRAL_REFERENCES_FAMILY");
            }
            verifyStructure(family, data);
        }
    }

    private static void verifyStructure(
            final String family, final com.fasterxml.jackson.databind.JsonNode data) {
        final String version =
                switch (family) {
                    case "expansion" -> "expansion-references-v1";
                    case "dimensions" -> "analytic-references-v1";
                    case "fleet" -> "analytic-fleet-references-v1";
                    case "regions" -> "synthetic-analytic-collection-region-v1";
                    case "tariffs" -> "synthetic-analytic-quote-tariff-v1";
                    default -> throw new IllegalArgumentException("INTEGRAL_REFERENCES_FAMILY");
                };
        if (!version.equals(QualificationJson.text(data, "version", 64))) {
            throw new IllegalArgumentException("INTEGRAL_REFERENCES_VERSION");
        }
        switch (family) {
            case "expansion" -> {
                for (final var field : List.of("branchCode", "branchLabel", "payerReference")) {
                    QualificationJson.text(data, field, 256);
                }
                rows(data, "labels", "category", "raw", "label");
            }
            case "dimensions" -> {
                rows(data, "labels", "category", "raw", "label");
                rows(
                        data,
                        "registry",
                        "kind",
                        "key",
                        "name",
                        "document",
                        "plate",
                        "branch",
                        "capacity",
                        "unit",
                        "classification",
                        "active",
                        "sourcePath",
                        "vehicleType",
                        "vehicleOwner");
                rows(data, "exclusions", "kind", "value");
            }
            case "fleet" -> {
                rows(data, "documents", "documentToken", "reason");
                rows(data, "aliases", "scope", "aliasScope", "raw", "classification", "reason");
                rows(data, "matrix", "vehicle", "driver", "classification", "reason");
                rows(
                        data,
                        "exceptions",
                        "scope",
                        "subjectToken",
                        "requiredVehicle",
                        "classification",
                        "priority",
                        "reason");
            }
            case "regions" -> rows(data, "rows", "kind", "key1", "key2", "region", "priority");
            case "tariffs" ->
                    rows(
                            data,
                            "rows",
                            "origin",
                            "destination",
                            "amount",
                            "currency",
                            "unit",
                            "weightUnit",
                            "rounding");
            default -> throw new IllegalArgumentException("INTEGRAL_REFERENCES_FAMILY");
        }
    }

    private static void rows(
            final com.fasterxml.jackson.databind.JsonNode data,
            final String field,
            final String... allowed) {
        final var array = data.path(field);
        QualificationJson.array(array, 0, 100);
        final var names = Set.of(allowed);
        for (final var row : array) {
            if (!row.isObject()) {
                throw new IllegalArgumentException("INTEGRAL_REFERENCE_ROW");
            }
            final var fields = row.fieldNames();
            while (fields.hasNext()) {
                if (!names.contains(fields.next())) {
                    throw new IllegalArgumentException("INTEGRAL_REFERENCE_UNKNOWN_FIELD");
                }
            }
        }
        // Required fields, optional absence/null and business types retain the typed importer's
        // contract. This admission check closes unknown members without manufacturing defaults.
    }

    public long importInto(
            final ColetaTemporalLaboratorySession session,
            final UUID run,
            final UUID expansion,
            final Clock clock,
            final CancellationToken token)
            throws IOException, SQLException {
        verifyFiles(token);
        files.get("expansion")
                .consume(
                        bytes ->
                                new JdbcExpansionReferences(session, clock)
                                        .importFixture(
                                                expansion, revision, from, toExclusive, bytes));
        token.throwIfCancellationRequested();
        files.get("dimensions")
                .consume(
                        bytes ->
                                new JdbcAnalyticReferences(session, clock)
                                        .importFixture(
                                                run, revision, from, toExclusive, policies, bytes));
        token.throwIfCancellationRequested();
        files.get("fleet")
                .consume(
                        bytes ->
                                new JdbcAnalyticFleetReferences(session, clock)
                                        .importFixture(run, revision, from, toExclusive, bytes));
        token.throwIfCancellationRequested();
        files.get("regions")
                .consume(
                        bytes ->
                                new JdbcAnalyticCollectionRegions(session)
                                        .importFixture(run, revision, from, toExclusive, bytes));
        token.throwIfCancellationRequested();
        return files.get("tariffs")
                .consume(
                        bytes ->
                                new JdbcAnalyticQuoteTariffs(session)
                                        .importFixture(run, revision, from, toExclusive, bytes))
                .release();
    }

    public int revision() {
        return revision;
    }
}
