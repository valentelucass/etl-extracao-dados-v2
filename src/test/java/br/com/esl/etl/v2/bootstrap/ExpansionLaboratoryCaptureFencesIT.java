package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.DATE;
import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.NONE;
import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.controle.ControlPlaneSource;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.controle.JdbcSqlServerControlPlane;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionSyntheticSource;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ExpansionLaboratoryCaptureFencesIT {
    enum Mutation {
        SOURCE,
        TENANT,
        CONTRACT,
        PAGE_COUNT
    }

    static Stream<Arguments> mutations() {
        final var cases = new ArrayList<Arguments>();
        for (final var template :
                List.of(
                        DataExportTemplate.CONTAS_A_PAGAR,
                        DataExportTemplate.FATURAS_POR_CLIENTE,
                        DataExportTemplate.INVENTARIO,
                        DataExportTemplate.SINISTROS)) {
            for (final var mutation : Mutation.values()) {
                cases.add(Arguments.of(template, mutation));
            }
        }
        return cases.stream();
    }

    @ParameterizedTest
    @MethodSource("mutations")
    void corruptedScopeOrCaptureCannotSealOrPromote(
            final DataExportTemplate template, final Mutation mutation) throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var runtime = ExpansionLaboratoryLocalIntegrationIT.runtime(session, run, 3, 100);
            if (mutation == Mutation.SOURCE) {
                new JdbcSqlServerControlPlane(session)
                        .registerSource(
                                new ControlPlaneSource(
                                        "SYNTHETIC_SCOPE_TEST", "DATA_EXPORT", Instant.now()));
            }
            final var row =
                    ExpansionLaboratoryFixtures.envelope(
                            template, ExpansionLaboratoryFixtures.data(template), 1, 1, 1);
            final var mutated = new java.util.concurrent.atomic.AtomicBoolean();
            final var source =
                    new ExpansionSyntheticSource(
                            page -> {
                                if (page == 1) {
                                    return "[" + row + "]";
                                }
                                final String sql =
                                        switch (mutation) {
                                            case CONTRACT ->
                                                    "UPDATE ctl.expansion_lab_capture SET contract_fingerprint=REPLICATE('0',"
                                                            + "64) WHERE run_id=?";
                                            case SOURCE ->
                                                    "UPDATE p SET source_instance=N'SYNTHETIC_SCOPE_TEST' FROM "
                                                            + "ctl.execution_partition p JOIN ctl.execution_attempt e ON "
                                                            + "e.partition_id=p.partition_id JOIN ctl.expansion_lab_capture c ON "
                                                            + "c.execution_id=e.execution_id WHERE c.run_id=?";
                                            case TENANT ->
                                                    "UPDATE p SET tenant_scope=N'SYNTHETIC_OTHER' FROM ctl.execution_partition p JOIN "
                                                            + "ctl.execution_attempt e ON e.partition_id=p.partition_id JOIN "
                                                            + "ctl.expansion_lab_capture c ON c.execution_id=e.execution_id "
                                                            + "WHERE c.run_id=?";
                                            case PAGE_COUNT ->
                                                    "UPDATE a SET record_count=2 FROM ctl.page_audit a JOIN ctl.expansion_lab_capture "
                                                            + "c ON c.execution_id=a.execution_id WHERE c.run_id=? AND a.page_number=1";
                                        };
                                try (var connection = session.getConnection();
                                        var command = connection.prepareStatement(sql)) {
                                    command.setQueryTimeout(10);
                                    command.setString(1, run.toString());
                                    assertEquals(1, command.executeUpdate());
                                    mutated.set(true);
                                } catch (final SQLException failure) {
                                    throw new IllegalStateException(
                                            "NEGATIVE_FIXTURE_MUTATION_FAILED", failure);
                                }
                                return "[]";
                            });
            assertThrows(
                    Exception.class,
                    () ->
                            runtime.capture(
                                    template, DATE, ExecutionMode.BOOTSTRAP, null, source, NONE));
            assertTrue(mutated.get(), "The negative fixture reached the intended SQL mutation.");
            assertEquals(
                    0,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.expansion_lab_root WHERE run_id=?",
                            run));
            assertEquals(
                    0,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM ctl.expansion_lab_capture WHERE run_id=? AND state='COMPLETE'",
                            run));
        }
    }
}
