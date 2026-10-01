package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.analitico.SyntheticCollectionSnapshot;
import br.com.esl.etl.v2.plataforma.contrato.ContractCompatibilityPolicy;
import br.com.esl.etl.v2.plataforma.contrato.ContractExecutionBinding;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.contrato.ContractTestSupport;
import br.com.esl.etl.v2.plataforma.contrato.SourceContractRelease;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageRequest;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RelationalCaptureSource;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnalyticCollectionSweepFixturesTest {
    @TempDir Path folder;

    @Test
    void declaredArtifactReplaysFourPinnedObservationsForTheSameSyntheticSnapshot()
            throws Exception {
        final var date = LocalDate.of(2036, 4, 2);
        QualificationSweepArtifactFixtures.write(folder, date, 3);
        final var artifact =
                new CollectionSweepArtifact(
                        folder.resolve("phase-1.json"), CancellationToken.none());
        final var run = UUID.fromString("00000000-0000-4000-8000-000000000001");
        final var snapshot = artifact.snapshot(run);
        assertEquals(6, snapshot.expectedRows());
        final var inputs = artifact.inputs(run, CancellationToken.none());
        assertEquals(4, inputs.size());
        for (final var input : inputs) {
            input.validate(snapshot);
            final var records = firstPage(input.source(), date);
            assertEquals(2, records.size());
            assertEquals(200002, records.get(0).path("id").intValue());
        }
        final var foreign =
                artifact.snapshot(UUID.fromString("00000000-0000-4000-8000-000000000002"));
        assertEquals(
                "ANA_SWEEP_INPUT_SCOPE",
                assertThrows(IllegalArgumentException.class, () -> inputs.get(0).validate(foreign))
                        .getMessage());
    }

    @Test
    void fourIndependentCapturesRetainTheSameSnapshotScopeAndOmission() throws Exception {
        final var date =
                LocalDate.parse(AnalyticCollectionsFixtures.data().path("request_date").asText());
        final var run = UUID.randomUUID();
        final var snapshot = new SyntheticCollectionSnapshot(run, date, 3, true);
        final var inputs =
                AnalyticCollectionSweepFixtures.inputs(snapshot, 2, AnalyticScenarioObserver.NONE);
        assertEquals(4, inputs.size());
        for (final var input : inputs) {
            input.validate(snapshot);
            final var rows = firstPage(input.source(), date);
            assertEquals(2, rows.size());
            assertEquals(200002, rows.get(0).path("id").intValue());
            assertTrue(rows.stream().allMatch(row -> row.path("synthetic_fixture").asBoolean()));
        }
        final var foreign = new SyntheticCollectionSnapshot(UUID.randomUUID(), date, 3, true);
        assertEquals(
                "ANA_SWEEP_INPUT_SCOPE",
                assertThrows(IllegalArgumentException.class, () -> inputs.get(0).validate(foreign))
                        .getMessage());
        assertEquals(
                "ANA_COLLECTION_SWEEP_FIXTURE_SCOPE",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        AnalyticCollectionSweepFixtures.inputs(
                                                new SyntheticCollectionSnapshot(
                                                        run, date.plusDays(1), 3, true),
                                                2,
                                                AnalyticScenarioObserver.NONE))
                        .getMessage());
    }

    @Test
    void fullSnapshotBeginsAtFirstRootAndRejectsUnboundedSource() throws Exception {
        final var date =
                LocalDate.parse(AnalyticCollectionsFixtures.data().path("request_date").asText());
        final var snapshot = new SyntheticCollectionSnapshot(UUID.randomUUID(), date, 3, false);
        final var source =
                AnalyticCollectionSweepFixtures.inputs(snapshot, 2, AnalyticScenarioObserver.NONE)
                        .get(0)
                        .source();
        assertEquals(200001, firstPage(source, date).get(0).path("id").intValue());
        assertThrows(
                IllegalArgumentException.class, () -> AnalyticCollectionsFixtures.source(0, 3, 2));
        assertThrows(
                IllegalArgumentException.class, () -> AnalyticCollectionsFixtures.source(1, 3, 17));
    }

    private static List<com.fasterxml.jackson.databind.JsonNode> firstPage(
            final RelationalCaptureSource source, final LocalDate date) throws Exception {
        final var template = DataExportTemplate.COLETAS;
        final var release = source.contractRelease(template);
        return source.gateway(template, guard(release), release, configuration())
                .fetch(
                        new DataExportPageRequest(
                                template,
                                new BusinessDateRange(date, date),
                                Optional.empty(),
                                1,
                                2,
                                template.defaultOrderBy()))
                .records();
    }

    private static ImmutableFingerprint configuration() {
        return new ImmutableFingerprint("synthetic-sweep", "d".repeat(64));
    }

    private static ContractRunGuard guard(final SourceContractRelease release) {
        final var policy =
                ContractCompatibilityPolicy.create(
                        "synthetic-sweep-policy", release.contractFingerprint(), List.of());
        final var binding =
                ContractExecutionBinding.create(
                        UUID.randomUUID(), release, policy, configuration());
        return new ContractRunGuard(
                binding,
                release,
                policy,
                ContractTestSupport.controlPlaneStart(binding),
                ignored -> {});
    }
}
