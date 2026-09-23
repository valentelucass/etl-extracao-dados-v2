package br.com.esl.etl.v2.contratos.bloco58;

import br.com.esl.etl.v2.contratos.bloco58.LocalCharacterization.Layer;
import br.com.esl.etl.v2.contratos.bloco58.LocalCharacterization.Pages;
import br.com.esl.etl.v2.contratos.bloco58.LocalCharacterization.Report;
import br.com.esl.etl.v2.modulos.usuarios.aplicacao.ExtrairUsuariosGraphQl;
import br.com.esl.etl.v2.modulos.usuarios.aplicacao.UsuarioGraphQlNodeMapper;
import br.com.esl.etl.v2.plataforma.contrato.ContractDriftException;
import br.com.esl.etl.v2.plataforma.contrato.ContractExecutionBinding;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.contrato.ContractTestSupport;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.fonte.graphql.Bloco58GraphQlAccess;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlContractObservationConfiguration;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlExtractionAudit;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlExtractionLimits;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlFirstWaveContractCatalog;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlPageStreamer;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlReadOperation;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationSignal;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.BiFunction;

/** Bounded local users consumer. Domain comparisons observe the actual staged records. */
public final class UsuariosCharacterization {
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2036-03-20T12:00:00Z"), ZoneOffset.UTC);

    private UsuariosCharacterization() {}

    public enum Fault {
        NONE,
        CANCEL_BEFORE,
        CANCEL_AFTER_FETCH,
        CANCEL_AFTER_STAGE,
        STAGE_FIRST,
        STAGE_SECOND
    }

    public static Report mapper(
            final Pages pages, final BiFunction<Integer, Integer, JsonNode> expected) {
        final var report = new Report();
        try {
            final String document;
            try (var input = pages.open(1)) {
                document = LocalCharacterization.read(input);
            }
            report.enter(Layer.PARSER);
            final var envelope = Bloco58GraphQlAccess.strict(document);
            final var edges = envelope.at("/data/individual/edges");
            if (!edges.isArray() || edges.size() > 20) {
                throw new IllegalArgumentException("INVALID_EDGES");
            }
            report.page();
            for (int row = 0; row < edges.size(); row++) {
                report.enter(Layer.MAPPER);
                final var mapped =
                        new UsuarioGraphQlNodeMapper().map(row + 1, edges.get(row).path("node"));
                report.mapped();
                report.compare(expected.apply(1, row), UsuariosProjection.observe(mapped));
            }
        } catch (IOException failure) {
            report.refuse(Layer.INPUT, "READ_FAILURE");
        } catch (RuntimeException failure) {
            report.failed(failure);
        }
        return report;
    }

    public static Report pipeline(
            final Pages pages,
            final int maximumPages,
            final int maximumRows,
            final Fault fault,
            final BiFunction<Integer, Integer, JsonNode> expected) {
        final var report = new Report();
        if (maximumPages < 1 || maximumPages > 100 || maximumRows < 1 || maximumRows > 1000) {
            report.refuse(Layer.INPUT, "CASE_LIMIT");
            return report;
        }
        final var operation = GraphQlReadOperation.USERS_SNAPSHOT;
        final var release = GraphQlFirstWaveContractCatalog.release(operation);
        final var policy = ContractTestSupport.policy(release);
        final var fingerprint = new ImmutableFingerprint("bloco58-synthetic-v1", "b".repeat(64));
        final var binding =
                ContractExecutionBinding.create(
                        UUID.randomUUID(),
                        release,
                        policy,
                        fingerprint,
                        LocalCharacterization.LIMITS);
        final var guard =
                new ContractRunGuard(
                        binding,
                        release,
                        policy,
                        ContractTestSupport.controlPlaneStart(binding),
                        ignored -> {});
        final var cancellation = new CancellationSignal();
        if (fault == Fault.CANCEL_BEFORE) {
            cancellation.cancel();
        }
        final int[] pageNumber = {0};
        try {
            final var configuration =
                    new GraphQlContractObservationConfiguration(
                            operation,
                            LocalCharacterization.LIMITS,
                            guard.responsePathBoundary(),
                            fingerprint);
            final var gateway =
                    Bloco58GraphQlAccess.enforce(
                            request -> {
                                // Only page-local protocol state is retained. No cursor is copied
                                // into a report.
                                report.enter(Layer.INPUT);
                                final String document;
                                try (var input = pages.open(++pageNumber[0])) {
                                    document = LocalCharacterization.read(input);
                                } catch (IOException failure) {
                                    throw new IllegalStateException("READ_FAILURE");
                                }
                                report.page();
                                report.enter(Layer.PARSER);
                                final var response =
                                        Bloco58GraphQlAccess.parse(
                                                document, request, configuration);
                                if (fault == Fault.CANCEL_AFTER_FETCH) {
                                    cancellation.cancel();
                                }
                                report.enter(Layer.TRAVERSAL);
                                return response;
                            },
                            configuration,
                            guard,
                            cancellation);
            final var streamer =
                    new GraphQlPageStreamer(gateway, GraphQlExtractionAudit.noop(), CLOCK);
            final var result =
                    new ExtrairUsuariosGraphQl(
                                    streamer,
                                    batch -> {
                                        report.enter(Layer.STAGING);
                                        for (int row = 0; row < batch.size(); row++) {
                                            report.mapped();
                                        }
                                        if (fault == Fault.STAGE_FIRST
                                                || fault == Fault.STAGE_SECOND
                                                        && batch.batchNumber() == 2) {
                                            throw new IllegalStateException("STAGING_FAILURE");
                                        }
                                        for (int row = 0; row < batch.size(); row++) {
                                            report.staged();
                                            report.compare(
                                                    expected.apply(pageNumber[0], row),
                                                    UsuariosProjection.observe(
                                                            batch.recordAt(row)));
                                        }
                                        if (fault == Fault.CANCEL_AFTER_STAGE) {
                                            cancellation.cancel();
                                        }
                                        report.enter(Layer.TRAVERSAL);
                                    },
                                    new UsuarioGraphQlNodeMapper(),
                                    CLOCK)
                            .execute(
                                    guard.executionContext(),
                                    new GraphQlExtractionLimits(maximumPages, maximumRows),
                                    cancellation);
            if (result.traversalVerification().provesCoverageOrSnapshot()) {
                report.difference("/flow/snapshot");
            }
            guard.complete();
            report.terminal();
        } catch (RuntimeException failure) {
            report.failed(failure);
            try {
                guard.complete();
                report.difference("/flow/incompletePermit");
            } catch (ContractDriftException expectedFailure) {
                /* Incomplete may not authorize apply. */
            }
        }
        return report;
    }
}
