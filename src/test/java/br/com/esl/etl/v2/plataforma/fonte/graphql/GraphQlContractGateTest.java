package br.com.esl.etl.v2.plataforma.fonte.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.contrato.ContractDriftException;
import br.com.esl.etl.v2.plataforma.contrato.ContractExecutionContext;
import br.com.esl.etl.v2.plataforma.contrato.ContractMetadata;
import br.com.esl.etl.v2.plataforma.contrato.SourceDataEffect;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GraphQlContractGateTest {

    @Test
    void staticMetadataContainsApprovedDocumentArgumentsAndEveryGovernedSelection() {
        for (final GraphQlReadOperation operation : GraphQlReadOperation.values()) {
            final ContractMetadata metadata = GraphQlContractAdapter.metadata(operation);
            assertEquals(operation.selectionCount() + 3, metadata.elements().size());
            assertEquals(operation.approvedDocument(), metadata.approvedDocument().orElseThrow());
            assertTrue(
                    metadata.elements().stream()
                            .filter(
                                    element ->
                                            element.kind()
                                                    == ContractMetadata.ElementKind
                                                            .GRAPHQL_ARGUMENT)
                            .allMatch(
                                    element ->
                                            element.path()
                                                    .startsWith(
                                                            "/"
                                                                    + operation.connectionName()
                                                                    + "/")));
            assertFalse(metadata.toString().contains(operation.documentText()));
        }
    }

    @Test
    void bindingMismatchFailsBeforeDelegateIo() {
        final GraphQlTestSupport.Fixture fixture = GraphQlTestSupport.usersFixture();
        final AtomicInteger calls = new AtomicInteger();
        final GraphQlContractObservationConfiguration wrong =
                new GraphQlContractObservationConfiguration(
                        GraphQlReadOperation.USERS_SNAPSHOT,
                        GraphQlTestSupport.LIMITS,
                        fixture.configuration().responsePathBoundary(),
                        new ImmutableFingerprint("wrong-runtime", "b".repeat(64)));

        final ContractDriftException mismatch =
                assertThrows(
                        ContractDriftException.class,
                        () ->
                                GraphQlContractGate.enforce(
                                        GraphQlTestSupport.bound(
                                                CancellationToken.none(),
                                                request -> {
                                                    calls.incrementAndGet();
                                                    throw new AssertionError();
                                                }),
                                        wrong,
                                        fixture.guard(),
                                        CancellationToken.none()));
        assertEquals(ContractDriftException.Reason.EXECUTION_BINDING_MISMATCH, mismatch.reason());
        assertEquals(0, calls.get());
    }

    @Test
    void missingObservationWrongOperationAndNullResponseFailClosed() {
        final GraphQlTestSupport.Fixture missingFixture = GraphQlTestSupport.usersFixture();
        final GraphQlGateway missing =
                GraphQlContractGate.enforce(
                        GraphQlTestSupport.bound(
                                CancellationToken.none(),
                                request ->
                                        GraphQlTestSupport.usersPage(
                                                false, null, "{\"id\":930001,\"name\":\"N\"}")),
                        missingFixture.configuration(),
                        missingFixture.guard(),
                        CancellationToken.none());
        final ContractDriftException absent =
                assertThrows(
                        ContractDriftException.class,
                        () ->
                                missing.fetch(
                                        GraphQlTestSupport.request(
                                                GraphQlReadOperation.USERS_SNAPSHOT)));
        assertEquals(ContractDriftException.Reason.RESPONSE_EVIDENCE_REQUIRED, absent.reason());

        final GraphQlTestSupport.Fixture wrongFixture = GraphQlTestSupport.usersFixture();
        final AtomicInteger calls = new AtomicInteger();
        final GraphQlGateway wrong =
                GraphQlContractGate.enforce(
                        GraphQlTestSupport.bound(
                                CancellationToken.none(),
                                request -> {
                                    calls.incrementAndGet();
                                    throw new AssertionError();
                                }),
                        wrongFixture.configuration(),
                        wrongFixture.guard(),
                        CancellationToken.none());
        assertThrows(
                ContractDriftException.class,
                () ->
                        wrong.fetch(
                                GraphQlTestSupport.request(
                                        GraphQlReadOperation.PICKS_TRANSITIONAL_SIDECAR)));
        assertEquals(0, calls.get());

        final GraphQlTestSupport.Fixture nullFixture = GraphQlTestSupport.usersFixture();
        final GraphQlGateway nullGateway =
                GraphQlContractGate.enforce(
                        GraphQlTestSupport.bound(CancellationToken.none(), request -> null),
                        nullFixture.configuration(),
                        nullFixture.guard(),
                        CancellationToken.none());
        assertThrows(
                NullPointerException.class,
                () ->
                        nullGateway.fetch(
                                GraphQlTestSupport.request(GraphQlReadOperation.USERS_SNAPSHOT)));
        assertThrows(ContractDriftException.class, nullFixture.guard()::complete);
    }

    @Test
    void terminalAndSuccessfulCompletionAuditAreIndependentMandatoryEvidence() {
        final GraphQlTestSupport.Fixture noTerminal = GraphQlTestSupport.usersFixture();
        fetchOneObservedPage(noTerminal);
        assertReason(
                ContractDriftException.Reason.TRAVERSAL_TERMINAL_EVIDENCE_REQUIRED,
                noTerminal.guard()::complete);

        final GraphQlTestSupport.Fixture noAudit = GraphQlTestSupport.usersFixture();
        fetchOneObservedPage(noAudit);
        final ContractExecutionContext noAuditContext = noAudit.guard().executionContext();
        noAuditContext.graphQlTraversalCompleted();
        assertReason(
                ContractDriftException.Reason.TRAVERSAL_TERMINAL_EVIDENCE_REQUIRED,
                noAudit.guard()::complete);

        final GraphQlTestSupport.Fixture complete = GraphQlTestSupport.usersFixture();
        fetchOneObservedPage(complete);
        final ContractExecutionContext context = complete.guard().executionContext();
        context.graphQlTraversalCompleted();
        context.graphQlCompletionAuditSucceeded();
        assertEquals(SourceDataEffect.SHADOW_UPSERT, complete.guard().complete().dataEffect());
        assertEquals(1, complete.guard().responsePages());
    }

    @Test
    void consumerSideFailureInvalidatesAllPartialEvidence() {
        final GraphQlTestSupport.Fixture fixture = GraphQlTestSupport.usersFixture();
        fetchOneObservedPage(fixture);
        fixture.guard().executionContext().traversalFailed();

        assertThrows(ContractDriftException.class, fixture.guard()::complete);
        assertThrows(
                ContractDriftException.class,
                () -> fixture.guard().observeGraphQlResponse(fixture.release().response()));
    }

    private static void fetchOneObservedPage(final GraphQlTestSupport.Fixture fixture) {
        final GraphQlGateway secured =
                GraphQlContractGate.enforce(
                        GraphQlTestSupport.bound(
                                CancellationToken.none(),
                                request ->
                                        GraphQlTestSupport.observed(
                                                fixture,
                                                GraphQlTestSupport.usersPage(
                                                        false,
                                                        null,
                                                        "{\"id\":930001,\"name\":\"N\"}"))),
                        fixture.configuration(),
                        fixture.guard(),
                        CancellationToken.none());
        secured.fetch(GraphQlTestSupport.request(GraphQlReadOperation.USERS_SNAPSHOT));
    }

    private static void assertReason(
            final ContractDriftException.Reason reason, final Runnable action) {
        assertEquals(reason, assertThrows(ContractDriftException.class, action::run).reason());
    }
}
