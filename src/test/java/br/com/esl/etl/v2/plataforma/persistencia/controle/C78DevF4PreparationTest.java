package br.com.esl.etl.v2.plataforma.persistencia.controle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.bootstrap.RuntimeDevEngineBankBinding;
import br.com.esl.etl.v2.bootstrap.RuntimeDevEngineBankBinding.MissingGate;
import br.com.esl.etl.v2.bootstrap.RuntimeDevEngineBankBinding.Refusal;
import br.com.esl.etl.v2.plataforma.configuracao.C78LocalSqlTarget;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Real component guards/metadata only: no synthetic backend is integrated or made an authority. */
class C78DevF4PreparationTest {
    @Test
    void producerIsConcreteAndMissingRootPrecedesSourceAndPhysicalOpen() throws Exception {
        final var scope = C78DevEngineBankAdapterTest.scope();
        final var sourceCalls = new AtomicInteger();
        final RuntimeDevEngineBankBinding.Admission source =
                new C78DevEngineBankAdapterTest.SyntheticAdmission() {
                    @Override
                    public void verify() {
                        sourceCalls.incrementAndGet();
                        throw new AssertionError(
                                "source must not run without actual Root provenance");
                    }
                };
        try (var producer = C78DevEngineAdmissionProducer.inspect(scope, null, null, source)) {
            final Refusal refused = assertThrows(Refusal.class, producer::verify);
            assertEquals(MissingGate.ADMISSION, refused.gate());
            assertEquals("ROOT_INSPECTION_DISPATCH_NOT_SUPPLIED", refused.getCause().getMessage());
            assertEquals(0, sourceCalls.get());
            assertEquals(MissingGate.ADMISSION, assertThrows(Refusal.class, producer::open).gate());
        }
    }

    @Test
    void actualAdapterBindsProducerAndRejectsEngineOrCatalogDescriptorAsInspection()
            throws Exception {
        final var f = new C78DevEngineBankAdapterTest.Fixture(C78DevEngineBankAdapterTest.scope());
        try (var producer = C78DevEngineAdmissionProducer.inspect(f.scope, f.path, f.sha(), null);
                var prepared =
                        new C78DevEngineBankAdapter(f.path, f.sha(), producer).prepare(f.scope)) {
            assertEquals(
                    MissingGate.ADMISSION, assertThrows(Refusal.class, prepared::verify).gate());
            assertEquals(MissingGate.ADMISSION, assertThrows(Refusal.class, prepared::open).gate());
            assertFalse(java.nio.file.Files.exists(f.claim()));
        }
    }

    @Test
    void coverageModeHas12StaticSelectsAndDefaultQueriesAreUnchanged() {
        final var target = C78LocalSqlTarget.approved(C78LocalSqlTarget.APPROVAL_REFERENCE);
        final var original = C78DevCatalogPlan.prepare(UUID.randomUUID(), target);
        final var coverage = C78DevCatalogPlan.prepareCoverage(UUID.randomUUID(), target);
        assertEquals(12, original.queries().size());
        assertEquals(12, coverage.queries().size());
        assertTrue(original.queries().contains(C78DevCatalogPlan.Query.DQ_METADATA));
        assertFalse(original.queries().contains(C78DevCatalogPlan.Query.MODULE_CONTRACTS));
        assertTrue(coverage.queries().contains(C78DevCatalogPlan.Query.MODULE_CONTRACTS));
        for (final var query : original.queries()) {
            assertEquals(query.sql(), original.sql(query));
        }
        for (final var query : coverage.queries()) {
            final String sql = coverage.sql(query).stripLeading();
            assertTrue(sql.startsWith("SELECT") || sql.startsWith("WITH"));
            assertFalse(
                    sql.contains("CREATE ")
                            || sql.contains("ALTER ")
                            || sql.contains("INSERT ")
                            || sql.contains("UPDATE ")
                            || sql.contains("DELETE ")
                            || sql.contains("EXEC "));
        }
    }

    @Test
    void missingFiveAndDefaultedRecoveryBodiesAreExplicitlyCoveredWithoutClaimingObserved() {
        final var plan =
                C78DevCatalogPlan.prepareCoverage(
                        UUID.randomUUID(),
                        C78LocalSqlTarget.approved(C78LocalSqlTarget.APPROVAL_REFERENCE));
        for (final String name :
                List.of(
                        "usp_runtime_temporal_plan",
                        "usp_runtime_temporal_gaps",
                        "usp_record_execution_metric",
                        "usp_raise_observability_alert",
                        "usp_observe_platform_health")) {
            assertTrue(plan.sql(C78DevCatalogPlan.Query.PROCEDURE_PRESENCE).contains(name));
            assertTrue(plan.sql(C78DevCatalogPlan.Query.PARAMETERS).contains(name));
            assertTrue(plan.sql(C78DevCatalogPlan.Query.MODULE_CONTRACTS).contains(name));
        }
        for (final String name : List.of("usp_runtime_recovery", "usp_runtime_status")) {
            assertTrue(plan.sql(C78DevCatalogPlan.Query.MODULE_CONTRACTS).contains(name));
        }
        for (final String name :
                List.of(
                        "execution_record",
                        "source_protocol_binding",
                        "execution_source_protocol",
                        "execution_publication_event",
                        "execution_promotion_result")) {
            assertTrue(plan.sql(C78DevCatalogPlan.Query.COLUMNS).contains(name));
        }
        assertTrue(
                plan.sql(C78DevCatalogPlan.Query.MODULE_CONTRACTS)
                        .contains("CONVERT(varbinary(max),m.definition)"));
    }

    @Test
    void moduleMetadataAcceptsBoundedHexAndOriginalLimitsRemain256And65536() {
        final String row =
                "ctl|usp_runtime_recovery|0|256|" + "A".repeat(64) + "|" + "B".repeat(512);
        final var evidence =
                new C78DevCatalogInspector.Evidence(
                        C78DevCatalogPlan.Query.MODULE_CONTRACTS, List.of(row));
        assertEquals(1, evidence.rows().size());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new C78DevCatalogInspector.Evidence(
                                C78DevCatalogPlan.Query.MODULE_CONTRACTS, List.of("!")));
        assertEquals(256, C78DevCatalogPlan.cellCharacters(C78DevCatalogPlan.Query.COLUMNS));
        assertEquals(65536, C78DevCatalogPlan.queryCharacters(C78DevCatalogPlan.Query.COLUMNS));
        assertEquals(
                512, C78DevCatalogPlan.cellCharacters(C78DevCatalogPlan.Query.MODULE_CONTRACTS));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new C78DevCatalogInspector.Evidence(
                                C78DevCatalogPlan.Query.MODULE_CONTRACTS,
                                List.of("A".repeat(3078))));
    }

    @Test
    void captureOnlyReceiptCannotRemoveOrdinaryMainAuditContractGates() throws Exception {
        final var f = new C78DevEngineBankAdapterTest.Fixture(C78DevEngineBankAdapterTest.scope());
        final var contracts = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        contracts.put("schema", "BANCO_C78_MAIN_COLETAS_CAPTURE_SQL_SURFACE_V1");
        contracts.put("planSHA256", f.scope.planSHA256());
        contracts.put("requestSHA256", f.scope.requestSHA256());
        contracts.putArray("columns").addObject().put("status", "CONFIRMED");
        contracts
                .putArray("javaCalls")
                .addObject()
                .put("reportCovered", true)
                .put("arityMatches", true);
        f.reference("physicalContract", contracts);
        try (var controls = f.control()) {
            final var refusal = assertThrows(Refusal.class, controls::verify);
            assertEquals(MissingGate.PHYSICAL_CONTRACT, refusal.gate());
        }
        assertEquals(29, C78DevEngineSession.TABLES.size());
        assertTrue(C78DevEngineSession.TABLES.contains("ctl.execution_audit"));
        assertTrue(C78DevEngineSession.TABLES.contains("ctl.page_audit"));
    }

    @Test
    void rawCallShapesRejectWrongArityAndMalformedBindListsBeforeRawPrepare() throws Exception {
        final var f = new C78DevEngineBankAdapterTest.Fixture(C78DevEngineBankAdapterTest.scope());
        try (var controls = f.control()) {
            assertTrue(
                    controls.allowedCall(
                            "{call stg.usp_stage_coleta_record(" + "?,".repeat(22) + "?)}"));
            assertFalse(controls.allowedCall("{call stg.usp_stage_coleta_record(?)}"));
            assertTrue(controls.allowedCall("{call recon.usp_runtime_raise_alert(?,?,?,?,?,?,?)}"));
            assertFalse(
                    controls.allowedCall(
                            "{call recon.usp_raise_observability_alert(?,?,?,?,?,?,?)}"));
            assertFalse(controls.allowedCall("{call ctl.usp_runtime_temporal_plan(?,?,?,?,?,?)}"));
            assertFalse(
                    controls.allowedCall("{call ctl.usp_control_plane_register_source(?,?,?,)}"));
            assertFalse(controls.allowedCall("{call ctl.usp_control_plane_register_source(,?,?)}"));
            assertFalse(
                    controls.allowedCall("{call core.usp_prepare_staged_execution(?,?,?,?,?)}"));
        }
    }
}
