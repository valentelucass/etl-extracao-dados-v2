package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.DATE;
import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.NONE;
import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionFreightTerms;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExpansionLaboratoryFreightTermsIT {
    @Test
    void lateralInputsAreCapturedAndRevisedWithoutMutatingEslData() throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var runtime = ExpansionLaboratoryDependenciesIT.runtime(session, run);
            runtime.capture(
                    DataExportTemplate.FRETES,
                    DATE,
                    ExecutionMode.BOOTSTRAP,
                    null,
                    ExpansionDependencyFixtures.source(DataExportTemplate.FRETES, 1, 3)
                            .withFinancialBindings(ExpansionDependencyFixtures::financialTerms),
                    NONE);
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.ufn_expansion_freight_terms(?) WHERE "
                                    + "terms_state='READY' AND billing_reference_date='20360405'",
                            run));
            assertEquals(
                    2,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM stg.expansion_lab_freight_terms t JOIN "
                                    + "ctl.expansion_lab_dependency_capture c ON c.execution_id=t.execution_id JOIN "
                                    + "stg.frete_record f ON f.execution_id=t.execution_id WHERE c.run_id=? AND "
                                    + "f.stage_record_id=(SELECT MIN(x.stage_record_id) FROM stg.frete_record x WHERE "
                                    + "x.execution_id=f.execution_id) AND JSON_VALUE(f.financial_json,"
                                    + "'$.arithmetic')='FORBIDDEN' AND JSON_VALUE(f.payload_json,"
                                    + "'$.billingReferenceDate') IS NULL",
                            run));
            final var correction =
                    runtime.capture(
                            DataExportTemplate.FRETES,
                            DATE,
                            ExecutionMode.INCREMENTAL,
                            null,
                            ExpansionDependencyFixtures.source(DataExportTemplate.FRETES, 1, 3)
                                    .withFinancialBindings(
                                            key -> terms(key, 2, LocalDate.of(2036, 4, 6))),
                            NONE);
            assertEquals(1, correction.receipt().noops());
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.ufn_expansion_freight_terms(?) WHERE "
                                    + "terms_state='READY' AND billing_reference_date='20360406' AND revision=2",
                            run));
        }
    }

    @Test
    void divergentSameRevisionIsUnresolvedUntilExplicitNewRevision() throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var runtime = ExpansionLaboratoryDependenciesIT.runtime(session, run);
            runtime.capture(
                    DataExportTemplate.FRETES,
                    DATE,
                    ExecutionMode.BOOTSTRAP,
                    null,
                    ExpansionDependencyFixtures.source(DataExportTemplate.FRETES, 1, 3)
                            .withFinancialBindings(ExpansionDependencyFixtures::financialTerms),
                    NONE);
            runtime.capture(
                    DataExportTemplate.FRETES,
                    DATE,
                    ExecutionMode.INCREMENTAL,
                    null,
                    ExpansionDependencyFixtures.source(DataExportTemplate.FRETES, 1, 3)
                            .withFinancialBindings(key -> terms(key, 1, LocalDate.of(2036, 4, 6))),
                    NONE);
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.ufn_expansion_freight_terms(?) WHERE "
                                    + "terms_state='CONFLICT' AND term_id IS NULL",
                            run));
            runtime.capture(
                    DataExportTemplate.FRETES,
                    DATE,
                    ExecutionMode.INCREMENTAL,
                    null,
                    ExpansionDependencyFixtures.source(DataExportTemplate.FRETES, 1, 3)
                            .withFinancialBindings(key -> terms(key, 2, LocalDate.of(2036, 4, 6))),
                    NONE);
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.ufn_expansion_freight_terms(?) WHERE terms_state='READY'",
                            run));
        }
    }

    @Test
    void freightAliasCannotReplaceSourceKeyOfFinancialBinding() throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var runtime = ExpansionLaboratoryDependenciesIT.runtime(session, run);
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            runtime.capture(
                                    DataExportTemplate.FRETES,
                                    DATE,
                                    ExecutionMode.BOOTSTRAP,
                                    null,
                                    ExpansionDependencyFixtures.source(
                                                    DataExportTemplate.FRETES, 1, 3)
                                            .withFinancialBindings(
                                                    key -> terms("INTEGER:600001", 1, DATE)),
                                    NONE));
            assertEquals(
                    0,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.expansion_lab_dependency WHERE run_id=?",
                            run));
        }
    }

    static ExpansionFreightTerms terms(final String key, final int revision, final LocalDate date) {
        final var original = ExpansionDependencyFixtures.financialTerms(key);
        return new ExpansionFreightTerms(
                key,
                revision,
                date,
                original.classification(),
                original.courtesy(),
                original.eligible(),
                original.fallbackVolumes(),
                original.payerToken(),
                original.currency(),
                original.unit(),
                original.active(),
                original.evidence());
    }
}
