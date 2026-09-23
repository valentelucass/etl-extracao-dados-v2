package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlCatalog;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlContract;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlValue;
import br.com.esl.etl.v2.plataforma.analitico.SyntheticCollectionSnapshot;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticQueries;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationComparator;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationGate;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationLineageEvidence;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationLocationOracle;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationOracles;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class QualificationOutputIT {
    @Test
    @Timeout(240)
    void exactValuesLineageAndAbsenceAcrossCandidateConfirmationAndReappearance() throws Exception {
        final var oracle =
                QualificationOracles.read(
                        Path.of(
                                "src/main/resources/qualification-laboratory/outputs.synthetic.json"));
        final Instant started = Instant.now();
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var runtime = new AnalyticScenarioRuntime(session, Clock.systemUTC());
            final var run = runtime.start(2, 2);
            assertTrue(
                    runtime.capture(
                                    run,
                                    ExecutionMode.BOOTSTRAP,
                                    1,
                                    false,
                                    null,
                                    CancellationToken.none())
                            .status()
                            .complete());
            final var evidence =
                    new Evidence(
                            new QualificationLineageEvidence(
                                    session, run.id(), run.expansion(), 2, 1, false),
                            new QualificationLocationOracle());
            final var results = new ArrayList<QualificationComparator.Result>();
            final var context = context(run.id(), started, 0, null, evidence);
            for (final var contract : AnalyticSqlContract.values()) {
                if (contract != AnalyticSqlContract.SQL_10) {
                    results.add(compare(session, oracle, context, contract));
                }
            }
            assertTrue(
                    results.stream().allMatch(r -> r.gate() == QualificationGate.State.PASS_LOCAL),
                    () ->
                            results.stream()
                                    .filter(r -> r.gate() != QualificationGate.State.PASS_LOCAL)
                                    .toList()
                                    .toString());
            final var sweep =
                    new LocalAnalyticCollectionSweep(
                            session,
                            run.id(),
                            run.relational(),
                            AnalyticScenarioRuntime.policy(2),
                            AnalyticScenarioRuntime.LOGICAL_CLOCK,
                            Clock.systemUTC());
            final var absent =
                    new SyntheticCollectionSnapshot(
                            run.id(), AnalyticScenarioRuntime.START, 2, true);
            final var first = sweep.observe(absent, UUID.randomUUID(), CancellationToken.none());
            assertEquals(1, first.result().candidates());
            final var candidate =
                    compare(
                            session,
                            oracle,
                            context(run.id(), started, 1, null, evidence),
                            AnalyticSqlContract.SQL_04);
            assertEquals(
                    QualificationGate.State.PASS_LOCAL, candidate.gate(), candidate.toString());
            final UUID confirmation = UUID.randomUUID();
            final var second = sweep.observe(absent, confirmation, CancellationToken.none());
            assertEquals(1, second.result().confirmations());
            final var confirmed =
                    compare(
                            session,
                            oracle,
                            context(run.id(), started, 2, confirmation, evidence),
                            AnalyticSqlContract.SQL_04);
            assertEquals(1, confirmed.observedRows());
            assertEquals(
                    QualificationGate.State.PASS_LOCAL, confirmed.gate(), confirmed.toString());
            final var reappeared =
                    sweep.observe(
                            new SyntheticCollectionSnapshot(
                                    run.id(), AnalyticScenarioRuntime.START, 2, false),
                            UUID.randomUUID(),
                            CancellationToken.none());
            assertEquals(1, reappeared.result().reactivated());
            final var clear =
                    compare(
                            session,
                            oracle,
                            context(run.id(), started, 0, null, evidence),
                            AnalyticSqlContract.SQL_04);
            assertEquals(QualificationGate.State.PASS_LOCAL, clear.gate(), clear.toString());
        }
    }

    private static QualificationOracles.Context context(
            final UUID run,
            final Instant started,
            final int stage,
            final UUID confirmation,
            final Evidence evidence) {
        return new QualificationOracles.Context(
                run, 2, 1, false, stage, confirmation, started, Instant.now(), evidence);
    }

    private static QualificationComparator.Result compare(
            final ColetaTemporalLaboratorySession session,
            final QualificationOracles oracle,
            final QualificationOracles.Context context,
            final AnalyticSqlContract contract)
            throws Exception {
        final String hash =
                QualificationJson.sha256(
                        Path.of(
                                "src/main/resources/qualification-laboratory/outputs.synthetic.json"));
        final var binding =
                new QualificationComparator.Binding(
                        hash, hash, hash, "INDEPENDENT_SYNTHETIC_RULES_V1");
        final var comparator =
                new QualificationComparator(
                        contract,
                        binding,
                        binding,
                        AnalyticSqlCatalog.columns(contract),
                        oracle.expected(contract, context));
        new JdbcAnalyticQueries(session)
                .read(
                        context.run(),
                        contract,
                        2,
                        4096,
                        CancellationToken.none(),
                        comparator::metadata,
                        comparator);
        return comparator.finish();
    }

    private record Evidence(
            QualificationLineageEvidence lineage, QualificationLocationOracle location)
            implements QualificationOracles.Evidence {
        @Override
        public List<AnalyticSqlValue> monitor(final long row) {
            throw new IllegalArgumentException("MONITOR_HAS_SEPARATE_PROOF");
        }

        @Override
        public int monitorCount() {
            return 0;
        }

        @Override
        public boolean lineage(
                final String entity, final int root, final int component, final JsonNode actual) {
            return lineage.compare(entity, root, component, actual);
        }

        @Override
        public String locationHash(final int root) {
            return location.hash(root);
        }
    }
}
