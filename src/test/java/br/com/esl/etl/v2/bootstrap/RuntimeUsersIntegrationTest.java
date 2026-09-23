package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.autorizacao.DurableAuthorizationException;
import br.com.esl.etl.v2.plataforma.autorizacao.RuntimeAction;
import br.com.esl.etl.v2.plataforma.autorizacao.RuntimeAuthorizationScope;
import br.com.esl.etl.v2.plataforma.autorizacao.RuntimeUsersAuthorityFixture;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfiguration;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportContractObservationConfiguration;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportHttpGatewayBundle;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RuntimeUsersJdbc;
import br.com.esl.etl.v2.plataforma.fonte.graphql.Bloco58GraphQlAccess;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlContractObservationConfiguration;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlGateway;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlPageRequest;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlReadOperation;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationSignal;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RuntimeUsersIntegrationTest {
    @TempDir Path directory;

    @Test
    void replayUsesNewExecutionAndStartsWithoutAnyPriorCursor() throws Exception {
        final var harness = new Harness();
        assertEquals(RuntimeExitCategory.SUCCESS, harness.run(RuntimeAction.RUN));
        final String origin = harness.document.get("executionId").asText();
        harness.document
                .put("mode", "REPLAY")
                .put("replayOf", origin)
                .put("executionId", UUID.randomUUID().toString())
                .put("cycleId", UUID.randomUUID().toString())
                .put("idempotencyKey", UUID.randomUUID().toString());
        harness.requests.clear();
        assertEquals(RuntimeExitCategory.SUCCESS, harness.run(RuntimeAction.REPLAY));
        assertEquals(2, harness.requests.size());
        assertTrue(harness.requests.get(0).after().isEmpty());
        final var starts =
                harness.jdbc.calls().stream()
                        .filter(
                                call ->
                                        call.operation()
                                                .equals("ctl.usp_control_plane_start_execution"))
                        .toList();
        assertEquals(2, starts.size());
        assertEquals(origin, starts.get(1).parameters().get(16));
        assertEquals("REPLAY", starts.get(1).parameters().get(7));
        assertFalse(starts.get(0).parameters().get(1).equals(starts.get(1).parameters().get(1)));
        assertEquals(2, harness.jdbc.appliedOccurrences());
    }

    @Test
    void ungatedParserResponseCannotFabricateCompletion() throws Exception {
        final var harness = new Harness();
        harness.secured = false;
        assertFalse(harness.run(RuntimeAction.RUN) == RuntimeExitCategory.SUCCESS);
        assertEquals(0, harness.seals());
        assertEquals(
                0,
                harness.jdbc.operations().stream()
                        .filter(op -> op.startsWith("core.usp_") || op.startsWith("stg.usp_"))
                        .count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"fetch", "between-pages", "before-promotion"})
    void operationalCancellationStopsWithoutSealOrPromotion(final String phase) throws Exception {
        final var harness = new Harness();
        try (var input = new java.io.PipedInputStream();
                var output = new java.io.PipedOutputStream(input)) {
            harness.controls = input;
            final Runnable cancel =
                    () -> {
                        try {
                            output.write('!');
                            output.flush();
                        } catch (final java.io.IOException failure) {
                            throw new AssertionError(failure);
                        }
                        final long deadline =
                                System.nanoTime() + java.time.Duration.ofSeconds(2).toNanos();
                        while (!harness.activeCancellation.isCancellationRequested()) {
                            if (System.nanoTime() >= deadline) {
                                throw new AssertionError("CONTROL_NOT_CONSUMED");
                            }
                            Thread.yield();
                        }
                    };
            if (phase.equals("fetch")) {
                harness.beforeFetch = ignored -> cancel.run();
            } else {
                harness.jdbc.afterBatch(
                        () -> {
                            if (harness.requests.size()
                                    == (phase.equals("between-pages") ? 1 : 2)) {
                                cancel.run();
                            }
                        });
            }
            assertEquals(RuntimeExitCategory.CANCELLED, harness.run(RuntimeAction.RUN));
        }
        assertEquals(0, harness.seals());
        assertEquals(
                0,
                harness.jdbc.operations().stream()
                        .filter(op -> op.startsWith("core.usp_"))
                        .count());
        assertEquals(0, harness.jdbc.appliedOccurrences());
        assertEquals(phase.equals("before-promotion") ? 2 : 1, harness.requests.size());
    }

    @Test
    void statusForAbsentOccurrenceDoesNotEvenBuildSourceGateway() throws Exception {
        final var harness = new Harness();
        assertEquals(RuntimeExitCategory.DEGRADED, harness.run(RuntimeAction.STATUS));
        assertEquals(0, harness.factories);
        assertTrue(harness.requests.isEmpty());
        assertEquals(List.of("ctl.usp_runtime_status"), harness.jdbc.operations());
    }

    @ParameterizedTest
    @ValueSource(strings = {"page", "lease", "quality"})
    void persistenceFailuresCannotSealOrApply(final String phase) throws Exception {
        final var harness = new Harness();
        harness.jdbc.lose(phase);
        assertFalse(harness.run(RuntimeAction.RUN) == RuntimeExitCategory.SUCCESS);
        assertEquals(0, harness.seals());
        assertEquals(0, harness.jdbc.appliedOccurrences());
        if (phase.equals("lease")) {
            assertEquals(0, harness.factories);
        }
        if (phase.equals("page")) {
            assertEquals(
                    0,
                    harness.jdbc.operations().stream()
                            .filter(op -> op.startsWith("stg.usp_"))
                            .count());
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "start",
                "endExclusive",
                "mode",
                "qualityFingerprint",
                "compatibilityVersion",
                "maximumPages"
            })
    void changedFrozenScopeIsRefusedBeforeBusinessComposition(final String field) throws Exception {
        final var harness = new Harness();
        switch (field) {
            case "start" ->
                    harness.document.put(
                            field,
                            RuntimeUsersOperationalRequestTest.NOW.plusSeconds(1).toString());
            case "endExclusive" ->
                    harness.document.put(
                            field,
                            RuntimeUsersOperationalRequestTest.NOW.plusSeconds(7200).toString());
            case "mode" ->
                    harness.document
                            .put(field, "REPLAY")
                            .put("replayOf", UUID.randomUUID().toString());
            case "qualityFingerprint" -> harness.document.put(field, "d".repeat(64));
            case "compatibilityVersion" -> harness.document.put(field, "other-1");
            case "maximumPages" -> harness.document.put(field, "3");
            default -> throw new AssertionError(field);
        }
        Files.writeString(harness.file, harness.document.toString());
        final Class<? extends RuntimeException> expected =
                field.equals("mode")
                        ? IllegalArgumentException.class
                        : DurableAuthorizationException.class;
        assertThrows(
                expected,
                () -> harness.root.executeOperationalRequest(harness.file, RuntimeAction.RUN));
        assertEquals(0, harness.compositions);
        assertTrue(harness.jdbc.operations().isEmpty());
    }

    @Test
    void recoveryMustRejectNullAggregateEvenWhenJdbcWouldConvertItToZero() throws Exception {
        final var harness = new Harness();
        assertEquals(RuntimeExitCategory.SUCCESS, harness.run(RuntimeAction.RUN));
        harness.jdbc.tamper("count-null");
        assertThrows(
                br.com.esl.etl.v2.plataforma.orquestracao.RuntimeRecoveryException.class,
                () -> harness.run(RuntimeAction.STATUS));
        assertEquals(2, harness.requests.size());
        assertEquals(1, harness.jdbc.appliedOccurrences());
    }

    @Test
    void oneTerminalPopulatedPageIsEnoughForLocalTraversalAndNeverEmptySnapshot() throws Exception {
        final var harness = new Harness();
        harness.pages.clear();
        harness.pages.add(page(false, null, "{\"id\":1}"));
        assertEquals(RuntimeExitCategory.SUCCESS, harness.run(RuntimeAction.RUN));
        assertEquals(1, harness.requests.size());
        assertEquals(1, harness.jdbc.appliedOccurrences());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "missing",
                "repeated",
                "cyclic",
                "page-cap",
                "node-cap",
                "oversized",
                "empty",
                "contract",
                "partial"
            })
    void invalidTraversalCannotSealPrepareOrApply(final String scenario) throws Exception {
        final var harness = new Harness();
        switch (scenario) {
            case "missing" -> harness.pages.set(0, page(true, null, "{\"id\":1}"));
            case "repeated" -> harness.pages.set(1, page(true, "synthetic-a", "{\"id\":2}"));
            case "cyclic" -> {
                harness.pages.set(1, page(true, "synthetic-b", "{\"id\":2}"));
                harness.pages.add(page(true, "synthetic-a", "{\"id\":3}"));
            }
            case "page-cap" -> harness.document.put("maximumPages", "1");
            case "node-cap" -> harness.document.put("maximumNodes", "1");
            case "oversized" ->
                    harness.pages.set(
                            0,
                            page(
                                    false,
                                    null,
                                    java.util.stream.IntStream.range(1, 22)
                                            .mapToObj(i -> "{\"id\":" + i + "}")
                                            .collect(java.util.stream.Collectors.joining(","))));
            case "empty" ->
                    harness.pages.set(
                            0,
                            "{\"data\":{\"individual\":{\"edges\":[],\"pageInfo\":{\"hasNextPage\":false,\"endCursor\":null}}}}");
            case "contract" ->
                    harness.pages.set(
                            0, page(false, null, "{\"id\":1,\"updatedAt\":\"2036-01-01\"}"));
            case "partial" -> harness.jdbc.lose("partial");
            default -> throw new AssertionError(scenario);
        }
        harness.freeze(RuntimeAction.RUN);
        assertFalse(harness.run(RuntimeAction.RUN) == RuntimeExitCategory.SUCCESS, scenario);
        assertEquals(0, harness.seals(), scenario);
        assertEquals(
                0,
                harness.jdbc.operations().stream().filter(op -> op.startsWith("core.usp_")).count(),
                scenario);
        assertEquals(0, harness.jdbc.appliedOccurrences(), scenario);
        assertEquals(0, harness.jdbc.openConnections(), scenario);
        final int fetches = harness.requests.size();
        assertFalse(harness.run(RuntimeAction.FORCE_RUN) == RuntimeExitCategory.SUCCESS, scenario);
        assertEquals(fetches, harness.requests.size(), scenario);
    }

    @ParameterizedTest
    @ValueSource(strings = {"seal", "apply", "receipt", "foreign"})
    void validDurableReceiptRecoversUncertainOutcomeWithoutRefetchOrDuplicateApply(
            final String phase) throws Exception {
        final var harness = new Harness();
        harness.jdbc.lose(phase);
        assertFalse(harness.run(RuntimeAction.RUN) == RuntimeExitCategory.SUCCESS);
        assertTrue(harness.diagnostics.contains("RUNTIME_RESULT status=RECOVERY_REQUIRED"));
        final int fetches = harness.requests.size();
        assertEquals(RuntimeExitCategory.SUCCESS, harness.run(RuntimeAction.FORCE_RUN));
        assertEquals(fetches, harness.requests.size());
        assertEquals(1, harness.jdbc.appliedOccurrences());
        assertEquals(
                1,
                harness.jdbc.operations().stream()
                        .filter(op -> op.equals("core.usp_apply_reconcile_publish_usuarios"))
                        .count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"start", "prepare", "transition"})
    void uncertainAttemptWithoutSealRequiresReadbackAndCannotRepeatEffects(final String phase)
            throws Exception {
        final var harness = new Harness();
        harness.jdbc.lose(phase);
        assertFalse(harness.run(RuntimeAction.RUN) == RuntimeExitCategory.SUCCESS);
        assertTrue(harness.diagnostics.contains("RUNTIME_RESULT status=RECOVERY_REQUIRED"));
        final int calls = harness.jdbc.operations().size();
        final int fetches = harness.requests.size();
        assertFalse(harness.run(RuntimeAction.FORCE_RUN) == RuntimeExitCategory.SUCCESS);
        assertEquals(fetches, harness.requests.size());
        assertTrue(
                harness.jdbc.operations().subList(calls, harness.jdbc.operations().size()).stream()
                        .allMatch(op -> op.equals("ctl.usp_runtime_recovery")));
        assertEquals(0, harness.seals());
        assertEquals(0, harness.jdbc.appliedOccurrences());
    }

    @ParameterizedTest
    @ValueSource(strings = {"typed", "execution", "seal"})
    void recoveryRejectsTamperedReceiptWithoutSourceOrApply(final String kind) throws Exception {
        final var harness = new Harness();
        assertEquals(RuntimeExitCategory.SUCCESS, harness.run(RuntimeAction.RUN));
        harness.jdbc.tamper(kind);
        assertFalse(harness.run(RuntimeAction.FORCE_RUN) == RuntimeExitCategory.SUCCESS);
        assertEquals(2, harness.requests.size());
        assertEquals(1, harness.jdbc.appliedOccurrences());
    }

    @Test
    void rejectedQualityCannotSealOrApplyUsers() throws Exception {
        final var harness = new Harness();
        harness.jdbc.lose("quality");
        assertEquals(RuntimeExitCategory.SOURCE_DQ, harness.run(RuntimeAction.RUN));
        assertEquals(
                1,
                harness.jdbc.operations().stream()
                        .filter(op -> op.startsWith("recon.usp_evaluate"))
                        .count());
        assertEquals(
                0,
                harness.jdbc.calls().stream()
                        .filter(
                                call ->
                                        call.operation().equals("ctl.usp_runtime_recovery")
                                                && "SEAL".equals(call.parameters().get(2)))
                        .count());
        assertEquals(0, harness.jdbc.appliedOccurrences());
    }

    @Test
    void rootRunsRealUsersPipelineAfterUniqueConsumptionAndStatusDoesNotComposeSource()
            throws Exception {
        final var harness = new Harness();
        assertEquals(
                RuntimeExitCategory.SUCCESS,
                harness.run(RuntimeAction.RUN),
                () -> harness.jdbc.operations().toString() + " fetches=" + harness.requests.size());
        assertTrue(harness.authority.consumed());
        assertEquals(2, harness.authority.connections());
        assertEquals(1, harness.compositions);
        assertEquals(2, harness.requests.size());
        assertTrue(harness.requests.get(0).after().isEmpty());
        assertTrue(harness.requests.get(1).after().isPresent());
        harness.requests.forEach(
                page -> {
                    assertEquals(GraphQlReadOperation.USERS_SNAPSHOT, page.operation());
                    assertEquals("enabled=true", page.operation().fixedParametersContract());
                    assertEquals(20, page.pageSize());
                });
        final var staged =
                harness.jdbc.calls().stream()
                        .filter(call -> call.operation().equals("stg.usp_stage_usuario_record"))
                        .toList();
        assertEquals(3, staged.size());
        assertEquals("INTEGER", staged.get(0).parameters().get(5));
        assertEquals("STRING", staged.get(1).parameters().get(5));
        assertEquals("VALUE", staged.get(0).parameters().get(6));
        assertEquals("NULL", staged.get(1).parameters().get(6));
        assertEquals("ABSENT", staged.get(2).parameters().get(6));
        assertFalse(staged.get(0).parameters().get(4).equals(staged.get(1).parameters().get(4)));
        harness.jdbc.calls().stream()
                .filter(call -> call.operation().startsWith("core.usp_"))
                .forEach(
                        call -> {
                            assertEquals(
                                    harness.document.get("executionId").asText(),
                                    call.parameters().get(1));
                            assertEquals(
                                    harness.request.binding.contractFingerprint().sha256(),
                                    call.parameters().get(3));
                            assertEquals(
                                    harness.request.binding.configurationFingerprint().sha256(),
                                    call.parameters().get(5));
                        });
        assertEquals(1, harness.jdbc.appliedOccurrences());
        assertThrows(
                DurableAuthorizationException.class,
                () -> harness.root.executeOperationalRequest(harness.file, RuntimeAction.RUN));
        assertEquals(1, harness.compositions);
        final int before = harness.requests.size();
        assertEquals(RuntimeExitCategory.SUCCESS, harness.run(RuntimeAction.STATUS));
        assertEquals(RuntimeExitCategory.SUCCESS, harness.run(RuntimeAction.FORCE_RUN));
        assertEquals(before, harness.requests.size());
        assertEquals(1, harness.jdbc.appliedOccurrences());
        assertEquals(0, harness.jdbc.openConnections());
    }

    @Test
    void authorizationCounterexamplesRefuseBeforeAnyBusinessComposition() throws Exception {
        for (final String reason :
                List.of(
                        "denied", "hash", "policy", "mapping", "time", "target", "context",
                        "ack")) {
            final var harness = new Harness();
            harness.authority.reject(reason);
            assertThrows(
                    DurableAuthorizationException.class,
                    () -> harness.root.executeOperationalRequest(harness.file, RuntimeAction.RUN),
                    reason);
            assertEquals(0, harness.compositions, reason);
            assertTrue(harness.requests.isEmpty(), reason);
            assertTrue(harness.jdbc.operations().isEmpty(), reason);
        }
    }

    final class Harness {
        final RuntimeConfiguration configuration =
                RuntimeUsersOperationalRequestTest.configuration();
        final com.fasterxml.jackson.databind.node.ObjectNode document =
                RuntimeUsersOperationalRequestTest.document();
        final RuntimeUsersJdbc jdbc = new RuntimeUsersJdbc(RuntimeUsersOperationalRequestTest.NOW);
        final List<GraphQlPageRequest> requests = new ArrayList<>();
        final List<String> diagnostics = new ArrayList<>();
        final List<String> pages =
                new ArrayList<>(
                        List.of(
                                page(
                                        true,
                                        "synthetic-a",
                                        "{\"id\":1,\"name\":\"Synthetic\"},{\"id\":\"1\",\"name\":null}"),
                                page(false, null, "{\"id\":2}")));
        RuntimeOperationalRequest request;
        RuntimeUsersAuthorityFixture authority;
        RuntimeCompositionRoot root;
        final Path file = directory.resolve(UUID.randomUUID() + ".json");
        int compositions;
        int factories;
        boolean secured = true;
        java.io.InputStream controls;
        CancellationToken activeCancellation;
        java.util.function.IntConsumer beforeFetch = ignored -> {};

        Harness() throws Exception {
            freeze(RuntimeAction.RUN);
        }

        void freeze(final RuntimeAction action) throws Exception {
            Files.writeString(file, document.toString());
            request = RuntimeOperationalRequest.read(configuration, file);
            authority =
                    new RuntimeUsersAuthorityFixture(
                            RuntimeAuthorizationScope.from(
                                    request.invocation, action, request.plan));
            root =
                    new RuntimeCompositionRoot(
                            configuration,
                            authority::authority,
                            (config, req, act, diagnostic, controls) -> {
                                assertTrue(authority.consumed());
                                compositions++;
                                return RuntimeOperationalExecution.execute(
                                        config,
                                        req,
                                        act,
                                        jdbc.dataSource(),
                                        gateways(),
                                        diagnostics::add,
                                        controls);
                            });
        }

        RuntimeExitCategory run(final RuntimeAction action) throws Exception {
            if (compositions > 0) {
                document.put("invocationId", UUID.randomUUID().toString());
                freeze(action);
            } else if (action != RuntimeAction.RUN) {
                freeze(action);
            }
            return root.executeOperationalRequest(file, action, diagnostics::add, controls);
        }

        RuntimeOperationalExecution.SourceGateways gateways() {
            return new RuntimeOperationalExecution.SourceGateways() {
                @Override
                public DataExportHttpGatewayBundle create(
                        final RuntimeConfiguration config,
                        final RuntimeOperationalRequest req,
                        final CancellationSignal cancel,
                        final DataExportContractObservationConfiguration observation) {
                    throw new AssertionError("USERS_CANNOT_COMPOSE_DATA_EXPORT");
                }

                @Override
                public GraphQlGateway createUsers(
                        final RuntimeConfiguration config,
                        final RuntimeOperationalRequest req,
                        final CancellationToken cancel,
                        final GraphQlContractObservationConfiguration observation,
                        final ContractRunGuard guard) {
                    factories++;
                    activeCancellation = cancel;
                    final GraphQlGateway raw =
                            new GraphQlGateway() {
                                @Override
                                public br.com.esl.etl.v2.plataforma.fonte.graphql
                                                .GraphQlPageResponse
                                        fetch(final GraphQlPageRequest page) {
                                    requests.add(page);
                                    beforeFetch.accept(requests.size());
                                    return Bloco58GraphQlAccess.parse(
                                            pages.get(requests.size() - 1), page, observation);
                                }

                                @Override
                                public void verifyCancellationToken(final CancellationToken token) {
                                    if (token != cancel) {
                                        throw new AssertionError("TOKEN_CHANGED");
                                    }
                                }
                            };
                    return secured
                            ? Bloco58GraphQlAccess.enforce(raw, observation, guard, cancel)
                            : raw;
                }
            };
        }

        long seals() {
            return jdbc.calls().stream()
                    .filter(
                            call ->
                                    call.operation().equals("ctl.usp_runtime_recovery")
                                            && "SEAL".equals(call.parameters().get(2)))
                    .count();
        }
    }

    static String page(final boolean more, final String cursor, final String nodes) {
        final String edges =
                java.util.Arrays.stream(nodes.split("(?<=}),"))
                        .map(node -> "{\"node\":" + node + "}")
                        .collect(java.util.stream.Collectors.joining(","));
        return "{\"data\":{\"individual\":{\"edges\":["
                + edges
                + "],\"pageInfo\":{\"hasNextPage\":"
                + more
                + ",\"endCursor\":"
                + (cursor == null ? "null" : "\"" + cursor + "\"")
                + "}}}}";
    }
}
