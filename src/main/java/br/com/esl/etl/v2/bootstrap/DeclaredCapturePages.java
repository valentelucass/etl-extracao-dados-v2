package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.contrato.ContractMetadata;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponse;
import br.com.esl.etl.v2.plataforma.contrato.SourceContractRelease;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.AnalyticQuotesCaptureSource;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.AnalyticQuotesSyntheticSource;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionDependencySource;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RelationalCaptureSource;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RelationalSyntheticSource;
import br.com.esl.etl.v2.plataforma.fonte.graphql.AnalyticUsersCaptureSource;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlFirstWaveContractCatalog;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlReadOperation;
import br.com.esl.etl.v2.plataforma.qualificacao.PinnedLocalJson;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Declared wire pages for the six original families, read again and pinned at consumption. */
public final class DeclaredCapturePages {
    private final PinnedLocalJson manifest;
    private final List<PinnedLocalJson> pages;
    private final String family;
    private final String source;
    private final String tenant;
    private final LocalDate date;
    private final int revision;
    private final int pageSize;
    private final int maximumRows;
    private final int expectedRows;
    private final SourceContractRelease release;

    public DeclaredCapturePages(final PinnedLocalJson manifest, final CancellationToken token)
            throws IOException {
        this.manifest = manifest;
        final var root = manifest.read();
        QualificationJson.fields(
                root,
                "version",
                "origin",
                "family",
                "source",
                "tenant",
                "date",
                "revision",
                "contractSha256",
                "pageSize",
                "maximumPages",
                "maximumRows",
                "expectedRows",
                "complete",
                "pages");
        if (!"local-capture-pages-v1".equals(QualificationJson.text(root, "version", 40))
                || !"LOCAL_SYNTHETIC_ARTIFACT_V1".equals(QualificationJson.text(root, "origin", 40))
                || !QualificationJson.flag(root, "complete")) {
            throw new IllegalArgumentException("INTEGRAL_CAPTURE_SCOPE_OR_INCOMPLETE");
        }
        family = QualificationJson.text(root, "family", 4);
        release = release(family);
        if (!release.contractFingerprint()
                .sha256()
                .equals(QualificationJson.digest(root, "contractSha256"))) {
            throw new IllegalArgumentException("INTEGRAL_CAPTURE_CONTRACT");
        }
        source = scope(QualificationJson.text(root, "source", 64));
        tenant = scope(QualificationJson.text(root, "tenant", 64));
        date = LocalDate.parse(QualificationJson.text(root, "date", 10));
        revision = QualificationJson.number(root, "revision", 1, 1000);
        pageSize = QualificationJson.number(root, "pageSize", 1, family.equals("USER") ? 20 : 16);
        if (family.equals("USER") && pageSize != 20) {
            throw new IllegalArgumentException("INTEGRAL_USERS_PAGE_SIZE");
        }
        final int maximumPages = QualificationJson.number(root, "maximumPages", 1, 256);
        maximumRows = QualificationJson.number(root, "maximumRows", 1, 10000);
        expectedRows = QualificationJson.number(root, "expectedRows", 0, maximumRows);
        QualificationJson.array(root.path("pages"), 1, maximumPages);
        final var declared = new ArrayList<PinnedLocalJson>();
        for (final var page : root.path("pages")) {
            declared.add(
                    PinnedLocalJson.reference(manifest.file().toPath().getParent(), page, 65536));
        }
        pages = List.copyOf(declared);
        verifyFiles(token);
    }

    public static String scope(final String value) {
        if (!value.matches("SYNTHETIC_[A-Z0-9_]{1,30}")) {
            throw new IllegalArgumentException("INTEGRAL_SYNTHETIC_SCOPE");
        }
        return value;
    }

    public void verifyFiles(final CancellationToken token) throws IOException {
        manifest.verify();
        long rows = 0;
        for (int ordinal = 1; ordinal <= pages.size(); ordinal++) {
            token.throwIfCancellationRequested();
            final var tree =
                    QualificationJson.parse(page(ordinal).getBytes(StandardCharsets.UTF_8), 65536);
            final var records = family.equals("USER") ? tree.at("/data/individual/edges") : tree;
            QualificationJson.array(records, 0, family.equals("USER") ? 20 : 1000);
            if (!family.equals("USER")) {
                if (records.isEmpty() != (ordinal == pages.size())) {
                    throw new IllegalArgumentException("INTEGRAL_CAPTURE_TERMINAL");
                }
                final var identities = new HashSet<String>();
                for (final var row : records) {
                    final var key = row.at(release.response().keyPath());
                    if (!key.isIntegralNumber() && !key.isTextual() && !key.isBoolean()) {
                        throw new IllegalArgumentException("INTEGRAL_CAPTURE_ENTITY_KEY");
                    }
                    identities.add(key.getNodeType() + ":" + key.toString());
                }
                if (identities.size() > pageSize) {
                    throw new IllegalArgumentException("INTEGRAL_CAPTURE_ENTITY_BOUND");
                }
            } else {
                final var info = tree.at("/data/individual/pageInfo");
                if (!info.path("hasNextPage").isBoolean()
                        || info.path("hasNextPage").booleanValue() != (ordinal < pages.size())) {
                    throw new IllegalArgumentException("INTEGRAL_USERS_TERMINAL");
                }
            }
            rows += records.size();
            if (rows > maximumRows) {
                throw new IllegalArgumentException("INTEGRAL_CAPTURE_ROW_BOUND");
            }
        }
        if (rows != expectedRows) {
            throw new IllegalArgumentException("INTEGRAL_CAPTURE_CARDINALITY");
        }
    }

    private String page(final int ordinal) {
        if (ordinal < 1 || ordinal > pages.size()) {
            throw new IllegalArgumentException("INTEGRAL_CAPTURE_PAGE_MISSING");
        }
        try {
            manifest.verify();
            return pages.get(ordinal - 1)
                    .consume(
                            bytes -> {
                                QualificationJson.parse(bytes, 65536);
                                return new String(bytes, StandardCharsets.UTF_8);
                            });
        } catch (final IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    public RelationalCaptureSource relational(final AnalyticScenarioObserver observer) {
        if (!Set.of("COL", "FRE", "MAN").contains(family)) {
            throw new IllegalArgumentException("INTEGRAL_RELATIONAL_FAMILY");
        }
        return new RelationalSyntheticSource(this::page).withContract(release).observed(observer);
    }

    public ExpansionDependencySource dependency(final AnalyticScenarioObserver observer) {
        if (!Set.of("FRE", "LOC").contains(family)) {
            throw new IllegalArgumentException("INTEGRAL_DEPENDENCY_FAMILY");
        }
        return new ExpansionDependencySource(this::page).withContract(release).observed(observer);
    }

    public AnalyticQuotesCaptureSource quotes() {
        if (!family.equals("COT")) {
            throw new IllegalArgumentException("INTEGRAL_QUOTES_FAMILY");
        }
        return new AnalyticQuotesSyntheticSource(this::page);
    }

    public AnalyticUsersCaptureSource users() {
        if (!family.equals("USER")) {
            throw new IllegalArgumentException("INTEGRAL_USERS_FAMILY");
        }
        return br.com.esl.etl.v2.plataforma.fonte.graphql.AnalyticUsersSyntheticSource.declared(
                this::page);
    }

    /** Both freight consumers receive this same declared union and the same pinned wire pages. */
    public static SourceContractRelease release(final String family) {
        return switch (family) {
            case "COL" ->
                    new RelationalSyntheticSource(ignored -> "[]")
                            .withAnalyticCollectionDetails()
                            .contractRelease(DataExportTemplate.COLETAS);
            case "MAN" ->
                    new RelationalSyntheticSource(ignored -> "[]")
                            .withAnalyticManifestDetails()
                            .contractRelease(DataExportTemplate.MANIFESTOS);
            case "FRE" -> freightRelease();
            case "LOC" -> ExpansionDependencySource.release(DataExportTemplate.LOCALIZACAO_CARGAS);
            case "COT" -> AnalyticQuotesSyntheticSource.release();
            case "USER" ->
                    GraphQlFirstWaveContractCatalog.release(GraphQlReadOperation.USERS_SNAPSHOT);
            default -> throw new IllegalArgumentException("INTEGRAL_CAPTURE_FAMILY");
        };
    }

    private static SourceContractRelease freightRelease() {
        final var relational = RelationalSyntheticSource.release(DataExportTemplate.FRETES);
        final var dependency =
                new ExpansionDependencySource(ignored -> "[]")
                        .withAnalyticFreightPerformance()
                        .contractRelease(DataExportTemplate.FRETES);
        final var fields = new LinkedHashMap<String, ContractResponse.Field>();
        final var metadata = new LinkedHashMap<String, ContractMetadata.Element>();
        for (final var contract : List.of(relational, dependency)) {
            for (final var field : contract.response().fields()) {
                final var existing = fields.putIfAbsent(field.path(), field);
                if (existing != null && !existing.equals(field)) {
                    throw new IllegalArgumentException("INTEGRAL_FREIGHT_CONTRACT_CONFLICT");
                }
            }
            for (final var element : contract.metadata().elements()) {
                metadata.putIfAbsent(element.kind() + ":" + element.path(), element);
            }
        }
        return SourceContractRelease.create(
                relational.sourceKind(),
                relational.documentReference(),
                "integral-freight-pages-v1",
                new ContractMetadata(List.copyOf(metadata.values()), Optional.empty()),
                new ContractResponse(
                        "$",
                        ContractResponse.Cardinality.ARRAY,
                        ContractResponse.ObservationState.POPULATED,
                        "/id",
                        List.copyOf(fields.values())));
    }

    public String family() {
        return family;
    }

    public String source() {
        return source;
    }

    public String tenant() {
        return tenant;
    }

    public LocalDate date() {
        return date;
    }

    public int revision() {
        return revision;
    }

    public int pageSize() {
        return pageSize;
    }

    public String fingerprint() {
        return manifest.sha256();
    }

    public SourceContractRelease contractRelease() {
        return release;
    }
}
