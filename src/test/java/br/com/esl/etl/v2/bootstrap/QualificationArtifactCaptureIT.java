package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.faturasporcliente.domain.FaturaClienteRules.FiscalPolicy;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionPolicy;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionProjection;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionArtifact;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionCaptureSource;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionLaboratory;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionQueries;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class QualificationArtifactCaptureIT {
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2036-04-15T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate DATE = LocalDate.of(2036, 4, 1);
    @TempDir Path directory;

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"CONTAS_A_PAGAR", "FATURAS_POR_CLIENTE", "INVENTARIO", "SINISTROS"})
    void twoIndependentAmountsTraverseArtifactMapperJdbcReaderAndReplay(
            final DataExportTemplate template) throws Exception {
        final String family = JdbcExpansionLaboratory.vertical(template);
        for (final String amount : new String[] {"37.25", "91.75"}) {
            final var path =
                    QualificationArtifactFixtures.write(
                            directory.resolve(amount),
                            template,
                            family,
                            row ->
                                    ((ObjectNode) row.path("data"))
                                            .put(amountField(template), amount));
            final var artifact = ExpansionArtifact.read(path);
            assertTrue(
                    ExpansionCharacterizer.inspect(artifact, CancellationToken.none())
                            .executable());
            try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
                session.controlStatements(30, 100000);
                final UUID run = UUID.randomUUID();
                final var runtime = runtime(session, run);
                final var captured =
                        runtime.capture(
                                template,
                                DATE,
                                ExecutionMode.BOOTSTRAP,
                                null,
                                artifact.source(ExpansionCaptureSource.Observer.NONE),
                                CancellationToken.none());
                assertEquals(1, captured.receipt().inserts());
                assertEquals(0, captured.receipt().quarantine());
                final var rows =
                        new JdbcExpansionQueries(session)
                                .detailPage(
                                        run,
                                        JdbcExpansionQueries.Vertical.valueOf(family),
                                        1,
                                        0,
                                        2);
                assertEquals(1, rows.size());
                assertEquals("independent-root-alpha", rows.get(0).lineage().root().value());
                assertEquals(0, new BigDecimal(amount).compareTo(amount(rows.get(0))));
                final var replay =
                        runtime.capture(
                                template,
                                DATE,
                                ExecutionMode.REPLAY,
                                captured.executionId(),
                                artifact.source(ExpansionCaptureSource.Observer.NONE),
                                CancellationToken.none());
                assertEquals(1, replay.receipt().noops());
                assertEquals(0, replay.receipt().updates());
            }
        }
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"CONTAS_A_PAGAR", "FATURAS_POR_CLIENTE", "INVENTARIO", "SINISTROS"})
    void cancellationAndMismatchedWindowDoNotStartCapture(final DataExportTemplate template)
            throws Exception {
        final var path =
                QualificationArtifactFixtures.write(
                        directory, template, JdbcExpansionLaboratory.vertical(template), row -> {});
        final var artifact = ExpansionArtifact.read(path);
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var runtime = runtime(session, run);
            final long statements = session.preparedStatements();
            assertThrows(
                    ResilienceCancelledException.class,
                    () ->
                            runtime.capture(
                                    template,
                                    DATE,
                                    ExecutionMode.BOOTSTRAP,
                                    null,
                                    artifact.source(ExpansionCaptureSource.Observer.NONE),
                                    () -> true));
            assertEquals(statements, session.preparedStatements());
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            runtime.capture(
                                    template,
                                    DATE.plusDays(1),
                                    ExecutionMode.BOOTSTRAP,
                                    null,
                                    artifact.source(ExpansionCaptureSource.Observer.NONE),
                                    CancellationToken.none()));
            assertEquals(statements, session.preparedStatements());
        }
    }

    private static LocalExpansionRuntime runtime(
            final ColetaTemporalLaboratorySession session, final UUID run) throws Exception {
        final var policy =
                new ExpansionPolicy(
                        DATE, DATE.plusDays(3), DATE, 2, 100, 1000, FiscalPolicy.SYNTHETIC_CTE);
        new JdbcExpansionLaboratory(session, CLOCK).start(run, policy);
        return new LocalExpansionRuntime(session, run, policy, CLOCK, Clock.systemUTC());
    }

    private static String amountField(final DataExportTemplate template) {
        return switch (template) {
            case CONTAS_A_PAGAR -> "value";
            case FATURAS_POR_CLIENTE -> "fit_ant_value";
            case INVENTARIO -> "cnr_c_s_fit_invoices_value";
            case SINISTROS -> "insurance_claim_total";
            default -> throw new IllegalArgumentException();
        };
    }

    private static BigDecimal amount(final ExpansionProjection projection) {
        if (projection instanceof ExpansionProjection.Payable payable) {
            return payable.rootAmount();
        }
        if (projection instanceof ExpansionProjection.InvoiceCustomer invoice) {
            return invoice.titleValue();
        }
        if (projection instanceof ExpansionProjection.Inventory inventory) {
            return inventory.invoiceValue();
        }
        return ((ExpansionProjection.InsuranceClaim) projection).claimTotal();
    }
}
