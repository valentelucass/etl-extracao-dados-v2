package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.bootstrap.AnalyticQuotesFixtures;
import br.com.esl.etl.v2.bootstrap.AnalyticScenarioFixtures;
import br.com.esl.etl.v2.bootstrap.ExpansionDependencyFixtures;
import br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryFixtures;
import br.com.esl.etl.v2.plataforma.contrato.ContractCompatibilityPolicy;
import br.com.esl.etl.v2.plataforma.contrato.ContractExecutionBinding;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.contrato.ContractSourceKind;
import br.com.esl.etl.v2.plataforma.contrato.ContractTestSupport;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.fonte.SyntheticCaptureObserver;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SyntheticSourceReleaseTest {
    @Test
    void declaresClosedDataExportRootsKeysAndSyntheticProvenance() {
        final var quotes = AnalyticQuotesSyntheticSource.release();
        assertEquals(ContractSourceKind.DATA_EXPORT, quotes.sourceKind());
        assertEquals("/sequence_code", quotes.response().keyPath());
        assertTrue(
                quotes.response().fields().stream()
                        .anyMatch(field -> field.path().equals("/synthetic_fixture")));

        for (final var template :
                new DataExportTemplate[] {
                    DataExportTemplate.COLETAS,
                    DataExportTemplate.FRETES,
                    DataExportTemplate.MANIFESTOS
                }) {
            final var release = RelationalSyntheticSource.release(template);
            assertEquals("/" + template.paginationEntityField(), release.response().keyPath());
            assertEquals(ContractSourceKind.DATA_EXPORT, release.sourceKind());
            assertTrue(release.metadata().elements().size() > 5);
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> RelationalSyntheticSource.release(DataExportTemplate.COTACOES));
    }

    @Test
    void expansionReleaseRejectsUnlistedTemplatesAndKeepsEntityKey() {
        for (final var template :
                new DataExportTemplate[] {
                    DataExportTemplate.FRETES, DataExportTemplate.LOCALIZACAO_CARGAS
                }) {
            final var release = ExpansionDependencySource.release(template);
            assertEquals("/" + template.paginationEntityField(), release.response().keyPath());
            assertEquals("expansion-dependency-v1", release.contractVersion());
            assertTrue(release.response().fields().size() > 5);
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> ExpansionDependencySource.release(DataExportTemplate.COLETAS));
    }

    @Test
    void localPageTransportCountsOnlyValidatedSyntheticRows() {
        final var template = DataExportTemplate.CONTAS_A_PAGAR;
        final var row =
                ExpansionLaboratoryFixtures.envelope(
                        template, ExpansionLaboratoryFixtures.data(template), 1, 1, 1);
        final var source = new ExpansionPageSource(page -> page == 1 ? "[" + row + "]" : "[]");
        final var release = source.contract(template);
        final var configuration = new ImmutableFingerprint("synthetic-source", "d".repeat(64));
        final var policy =
                ContractCompatibilityPolicy.create(
                        "synthetic-policy", release.contractFingerprint(), List.of());
        final var binding =
                ContractExecutionBinding.create(UUID.randomUUID(), release, policy, configuration);
        final var guard =
                new ContractRunGuard(
                        binding,
                        release,
                        policy,
                        ContractTestSupport.controlPlaneStart(binding),
                        ignored -> {});
        final var gateway = source.gateway(template, guard, release, configuration);
        final var date = LocalDate.of(2036, 4, 1);
        final var request =
                DataExportPageRequest.forTemplate(
                        template, new BusinessDateRange(date, date), null, 1);
        assertEquals(1, gateway.fetch(request).recordCount());
        assertEquals(1, source.metrics().fetchedPages());
        assertTrue(source.metrics().bytes() > 0);

        final var invalid = row.deepCopy();
        invalid.remove("provenance");
        final var rejected = new ExpansionPageSource(page -> "[" + invalid + "]");
        final var rejectedGuard =
                new ContractRunGuard(
                        binding,
                        release,
                        policy,
                        ContractTestSupport.controlPlaneStart(binding),
                        ignored -> {});
        final var rejectedGateway =
                rejected.gateway(template, rejectedGuard, release, configuration);
        assertEquals(
                "EXP_LAB_SYNTHETIC_MARKER_REQUIRED",
                assertThrows(IllegalArgumentException.class, () -> rejectedGateway.fetch(request))
                        .getMessage());
        assertEquals(0, rejected.metrics().fetchedPages());
    }

    @Test
    void quotationSourceWithDisabledTelemetryStillEnforcesSyntheticPageBoundary() {
        final var template = DataExportTemplate.COTACOES;
        final var source =
                AnalyticQuotesFixtures.source(1, 1, 1).observed(SyntheticCaptureObserver.NONE);
        final var release = source.contractRelease();
        final var configuration = new ImmutableFingerprint("synthetic-source", "d".repeat(64));
        final var policy =
                ContractCompatibilityPolicy.create(
                        "synthetic-policy", release.contractFingerprint(), List.of());
        final var binding =
                ContractExecutionBinding.create(UUID.randomUUID(), release, policy, configuration);
        final var guard =
                new ContractRunGuard(
                        binding,
                        release,
                        policy,
                        ContractTestSupport.controlPlaneStart(binding),
                        ignored -> {});
        final var date = LocalDate.of(2036, 4, 1);
        final var request =
                DataExportPageRequest.forTemplate(
                        template, new BusinessDateRange(date, date), null, 1);
        assertEquals(
                1, source.bundle(guard, configuration).dataGateway().fetch(request).recordCount());
        assertEquals(1, source.metrics().fetchedPages());
        assertTrue(source.metrics().bytes() > 0);

        final var invalid = AnalyticQuotesFixtures.data();
        invalid.remove("synthetic_fixture");
        final var rejected =
                new AnalyticQuotesSyntheticSource(page -> "[" + invalid + "]")
                        .observed(SyntheticCaptureObserver.NONE);
        final var rejectedGuard =
                new ContractRunGuard(
                        binding,
                        release,
                        policy,
                        ContractTestSupport.controlPlaneStart(binding),
                        ignored -> {});
        assertEquals(
                "ANA_QUOTE_SYNTHETIC_MARKER_REQUIRED",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        rejected.bundle(rejectedGuard, configuration)
                                                .dataGateway()
                                                .fetch(request))
                        .getMessage());
        assertEquals(0, rejected.metrics().fetchedPages());
    }

    @Test
    void bootstrapFixturesKeepScopedEntityPagesAndCorrectionValues() {
        final var date = LocalDate.of(2036, 4, 1);
        final var configuration = new ImmutableFingerprint("synthetic-source", "d".repeat(64));
        final var template = DataExportTemplate.FRETES;
        final var expansion = ExpansionDependencyFixtures.source(template, 2, 2, 2);
        final var expansionRelease = expansion.contractRelease(template);
        final var expansionPolicy =
                ContractCompatibilityPolicy.create(
                        "synthetic-policy", expansionRelease.contractFingerprint(), List.of());
        final var expansionBinding =
                ContractExecutionBinding.create(
                        UUID.randomUUID(), expansionRelease, expansionPolicy, configuration);
        final var expansionGuard =
                new ContractRunGuard(
                        expansionBinding,
                        expansionRelease,
                        expansionPolicy,
                        ContractTestSupport.controlPlaneStart(expansionBinding),
                        ignored -> {});
        final var expansionGateway =
                expansion.gateway(template, expansionGuard, expansionRelease, configuration);
        final var request =
                new DataExportPageRequest(
                        template,
                        new BusinessDateRange(date, date),
                        java.util.Optional.empty(),
                        1,
                        2,
                        template.defaultOrderBy());
        final var firstExpansion = expansionGateway.fetch(request);
        assertEquals(2, firstExpansion.recordCount());
        assertEquals(300002, firstExpansion.records().get(0).path("id").asInt());
        final var secondExpansion = expansionGateway.fetch(request.withPage(2));
        assertEquals(2, secondExpansion.recordCount());
        assertEquals(300003, secondExpansion.records().get(0).path("id").asInt());
        assertEquals(0, expansionGateway.fetch(request.withPage(3)).recordCount());
        assertEquals(3, expansion.metrics().fetchedPages());

        final var manifestTemplate = DataExportTemplate.MANIFESTOS;
        final var manifest = AnalyticScenarioFixtures.manifests(2, 1, 2, 2, true);
        final var manifestRelease = manifest.contractRelease(manifestTemplate);
        final var manifestPolicy =
                ContractCompatibilityPolicy.create(
                        "synthetic-policy", manifestRelease.contractFingerprint(), List.of());
        final var manifestBinding =
                ContractExecutionBinding.create(
                        UUID.randomUUID(), manifestRelease, manifestPolicy, configuration);
        final var manifestGuard =
                new ContractRunGuard(
                        manifestBinding,
                        manifestRelease,
                        manifestPolicy,
                        ContractTestSupport.controlPlaneStart(manifestBinding),
                        ignored -> {});
        final var manifestGateway =
                manifest.gateway(manifestTemplate, manifestGuard, manifestRelease, configuration);
        final var manifestRequest =
                new DataExportPageRequest(
                        manifestTemplate,
                        new BusinessDateRange(date, date),
                        java.util.Optional.empty(),
                        1,
                        2,
                        manifestTemplate.defaultOrderBy());
        final var first = manifestGateway.fetch(manifestRequest);
        assertEquals(2, first.recordCount());
        assertEquals(
                "SYNTHETIC BRANCH B", first.records().get(0).path("mft_crn_psn_nickname").asText());
        assertEquals(
                "2036-04-02T12:00:00.000000002Z",
                first.records().get(0).path("finished_at").asText());
        assertEquals(1, manifestGateway.fetch(manifestRequest.withPage(2)).recordCount());
        assertEquals(0, manifestGateway.fetch(manifestRequest.withPage(3)).recordCount());
    }
}
