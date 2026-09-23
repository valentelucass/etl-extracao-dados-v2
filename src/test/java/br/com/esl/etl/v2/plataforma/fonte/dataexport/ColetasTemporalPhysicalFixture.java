package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.bootstrap.LocalColetasTemporalRuntime;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaTemporalIdentityBinding;
import br.com.esl.etl.v2.plataforma.contrato.ContractExecutionBinding;
import br.com.esl.etl.v2.plataforma.contrato.ContractObservationLimits;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponse;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.contrato.ContractSourceKind;
import br.com.esl.etl.v2.plataforma.contrato.ContractTestSupport;
import br.com.esl.etl.v2.plataforma.contrato.SourceContractRelease;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.fonte.graphql.ColetasTemporalGraphQlFixture;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlExtractionLimits;
import br.com.esl.etl.v2.plataforma.identidade.FirstWaveIdentityContract;
import br.com.esl.etl.v2.plataforma.identidade.ScopedSourceIdentity;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.JdbcColetaTemporalLaboratory;
import br.com.esl.etl.v2.plataforma.persistencia.sombra.JdbcDataExportExtractionAudit;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

final class ColetasTemporalPhysicalFixture {
    static final LocalDate DATE = LocalDate.of(2036, 1, 20);
    static final Clock CLOCK = Clock.fixed(Instant.parse("2036-01-21T12:00:00Z"), ZoneOffset.UTC);
    static final ImmutableFingerprint EVIDENCE =
            new ImmutableFingerprint("synthetic-coletas-temporal-v1", "a".repeat(64));
    private static final AtomicInteger PARTITION = new AtomicInteger();
    final ContractExecutionBinding binding;
    final ContractRunGuard guard;
    final ColetasTemporalGraphQlFixture graph;
    final DataExportPageStreamer streamer;
    final ColetaTemporalLaboratorySession session;
    final CancellationToken cancellation;
    IntConsumer beforeDataFetch = ignored -> {};

    ColetasTemporalPhysicalFixture(
            final ColetaTemporalLaboratorySession session,
            final List<String> dataPages,
            final List<String> referencePages,
            final CancellationToken cancellation)
            throws SQLException {
        this.session = session;
        this.cancellation = cancellation;
        final var limits = ContractObservationLimits.runtimeDefaults();
        final var fields =
                new ArrayList<>(DataExportColetasContractCatalog.release().response().fields());
        fields.add(
                new ContractResponse.Field(
                        "/status_updated_at",
                        ContractResponse.Cardinality.SCALAR,
                        ContractResponse.Presence.OPTIONAL,
                        true,
                        List.of(ContractResponse.JsonType.STRING)));
        fields.add(
                new ContractResponse.Field(
                        "/synthetic_fixture",
                        ContractResponse.Cardinality.SCALAR,
                        ContractResponse.Presence.REQUIRED,
                        false,
                        List.of(ContractResponse.JsonType.BOOLEAN)));
        final var release =
                SourceContractRelease.create(
                        ContractSourceKind.DATA_EXPORT,
                        DataExportContractAdapter.documentReference(DataExportTemplate.COLETAS),
                        "synthetic-coletas-temporal-v1",
                        DataExportColetasContractCatalog.release().metadata(),
                        new ContractResponse(
                                "$",
                                ContractResponse.Cardinality.ARRAY,
                                ContractResponse.ObservationState.POPULATED,
                                "/id",
                                fields));
        final var policy = ContractTestSupport.policy(release);
        binding =
                ContractExecutionBinding.create(
                        UUID.randomUUID(), release, policy, EVIDENCE, limits);
        guard =
                new ContractRunGuard(
                        binding,
                        release,
                        policy,
                        ContractTestSupport.controlPlaneStart(binding),
                        ignored -> {});
        final var config =
                DataExportContractObservationConfiguration.forRelease(
                        DataExportTemplate.COLETAS,
                        release,
                        limits,
                        guard.responsePathBoundary(),
                        EVIDENCE);
        final var adapter = new DataExportContractAdapter(limits, guard.responsePathBoundary());
        final var bundle =
                DataExportHttpGatewayBundle.contractBound(
                        request -> {
                            beforeDataFetch.accept(request.page());
                            if (request.page() > dataPages.size()) {
                                throw new IllegalStateException("SYNTHETIC_DATA_INTERRUPTED");
                            }
                            final var tree = parse(dataPages.get(request.page() - 1));
                            final var normalized =
                                    new DataExportResponseNormalizer().normalize(tree);
                            DataExportPageEntityLimitValidator.validate(request, normalized);
                            return normalized.withObservation(
                                    adapter.response(
                                            tree, DataExportResponseForm.ROOT_ARRAY, "/id"),
                                    limits,
                                    guard.responsePathBoundary());
                        },
                        ignored -> {
                            throw new AssertionError(
                                    "No source metadata request is needed for frozen fixtures.");
                        },
                        config);
        final var secured =
                DataExportContractGate.enforce(bundle, DataExportTemplate.COLETAS, guard);
        guard.validateMetadata(release.metadata());
        streamer =
                new DataExportPageStreamer(
                        secured.dataGateway(), new JdbcDataExportExtractionAudit(session), CLOCK);
        graph = ColetasTemporalGraphQlFixture.create(referencePages, CLOCK, cancellation);
        provision();
    }

    private static JsonNode parse(final String page) {
        try {
            return DataExportStrictJsonParser.readTree(page);
        } catch (final JsonProcessingException failure) {
            throw new IllegalArgumentException("SYNTHETIC_DATA_JSON_INVALID", failure);
        }
    }

    private void provision() throws SQLException {
        try (var connection = session.getConnection();
                var sql =
                        connection.prepareStatement(
                                """
                DECLARE @now DATETIME2(3)=SYSUTCDATETIME(),@cycle UNIQUEIDENTIFIER=NEWID(),
                    @idem NVARCHAR(128)=CONVERT(nvarchar(36),NEWID()),
                    @end DATETIME2(3)=DATEADD(SECOND,?,CONVERT(DATETIME2(3),'20360121',112));
                EXEC ctl.usp_control_plane_register_source N'SYNTHETIC_COLETAS_TEMPORAL_LAB',N'DATA_EXPORT',@now;
                EXEC ctl.usp_control_plane_start_cycle @cycle,N'synthetic-coletas-temporal-v1',?,@now;
                EXEC ctl.usp_control_plane_start_execution ?,@cycle,N'LOCAL_SHADOW',N'SYNTHETIC_COLETAS_TEMPORAL_LAB',
                    N'SYNTHETIC_COLETAS_TENANT',N'coletas',N'BACKFILL','2036-01-20',@end,
                    N'DATA_EXPORT_RESTART_FROM_BEGINNING',?,?,?, ?,@idem,NULL,3600,@now;
                """)) {
            sql.setInt(1, PARTITION.incrementAndGet());
            sql.setString(2, EVIDENCE.sha256());
            sql.setString(3, binding.executionId().toString());
            sql.setString(4, binding.contractVersion());
            sql.setString(5, binding.contractFingerprint().sha256());
            sql.setString(6, binding.configurationFingerprint().version());
            sql.setString(7, binding.configurationFingerprint().sha256());
            sql.setQueryTimeout(10);
            sql.executeUpdate();
        }
    }

    JdbcColetaTemporalLaboratory.Result run() {
        return new LocalColetasTemporalRuntime(session, streamer, graph.streamer(), CLOCK, true)
                .execute(
                        input(),
                        List.of(identityBinding("INTEGER:17", "STRING:synthetic-17")),
                        cancellation);
    }

    LocalColetasTemporalRuntime.Input input() {
        return new LocalColetasTemporalRuntime.Input(
                guard,
                request(),
                new DataExportExtractionLimits(4, 100, 100),
                graph.guard().executionContext(),
                new GraphQlExtractionLimits(4, 80));
    }

    static DataExportPageRequest request() {
        return new DataExportPageRequest(
                DataExportTemplate.COLETAS,
                new BusinessDateRange(DATE, DATE),
                Optional.empty(),
                1,
                2,
                DataExportTemplate.COLETAS.defaultOrderBy());
    }

    ColetaTemporalIdentityBinding identityBinding(final String key, final String referenceKey) {
        return new ColetaTemporalIdentityBinding(
                binding.executionId(),
                graph.guard().executionContext().executionId(),
                identity(ScopedSourceIdentity.WireType.INTEGER, key),
                identity(ScopedSourceIdentity.WireType.STRING, referenceKey),
                DATE,
                EVIDENCE);
    }

    private static ScopedSourceIdentity identity(
            final ScopedSourceIdentity.WireType type, final String key) {
        return new ScopedSourceIdentity(
                LocalColetasTemporalRuntime.SOURCE,
                LocalColetasTemporalRuntime.TENANT,
                FirstWaveIdentityContract.Entity.COLETAS,
                new ScopedSourceIdentity.SourceKey(type, key));
    }

    static String data(final String status, final String nativeRaw) {
        final var row =
                JsonNodeFactory.instance
                        .objectNode()
                        .put("id", 17)
                        .put("sequence_code", 17)
                        .put("status", status)
                        .put("request_date", DATE.toString())
                        .put("synthetic_fixture", true);
        if (nativeRaw != null) {
            if (nativeRaw.equals("null")) {
                row.putNull("status_updated_at");
            } else {
                row.put("status_updated_at", nativeRaw);
            }
        }
        return row.toString();
    }

    static String reference(final String status, final String raw) {
        final var row =
                JsonNodeFactory.instance
                        .objectNode()
                        .put("id", "synthetic-17")
                        .put("status", status)
                        .put("requestDate", DATE.toString());
        if (raw != null) {
            if (raw.equals("null")) {
                row.putNull("statusUpdatedAt");
            } else {
                row.put("statusUpdatedAt", raw);
            }
        }
        return row.toString();
    }

    static String graphPage(final boolean more, final String... nodes) {
        final var edges = new ArrayList<String>();
        for (final String node : nodes) {
            edges.add("{\"node\":" + node + "}");
        }
        return "{\"data\":{\"pick\":{\"edges\":["
                + String.join(",", edges)
                + "],\"pageInfo\":{\"hasNextPage\":"
                + more
                + ",\"endCursor\":"
                + (more ? "\"synthetic-next\"" : "null")
                + "}}}}";
    }
}
