package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.contrato.ContractCompatibilityPolicy;
import br.com.esl.etl.v2.plataforma.contrato.ContractExecutionBinding;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.contrato.ContractTestSupport;
import br.com.esl.etl.v2.plataforma.contrato.SourceContractRelease;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageRequest;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.persistencia.relacional.JdbcRelationalLaboratory;
import br.com.esl.etl.v2.plataforma.relacional.RelationalBinding;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BootstrapFixturePagingTest {
    private static final LocalDate DATE = LocalDate.of(2036, 4, 1);

    @Test
    void relationalFixtureKeepsRootMultiplicityAndSeparateMcCfBindings() throws Exception {
        for (final var template :
                List.of(
                        DataExportTemplate.MANIFESTOS,
                        DataExportTemplate.COLETAS,
                        DataExportTemplate.FRETES)) {
            final var source = RelationalLaboratoryFixtures.source(template, DATE, 7, 2, 3, true);
            final var gateway =
                    source.gateway(
                            template,
                            guard(source.contractRelease(template)),
                            source.contractRelease(template),
                            configuration());
            final var first = gateway.fetch(request(template, 1, 3));
            final var second = gateway.fetch(request(template, 2, 3));
            assertEquals(3, first.recordCount());
            assertEquals(1, second.recordCount());
            assertTrue(
                    first.records().stream()
                            .allMatch(row -> row.path("synthetic_fixture").asBoolean()));
        }
        final var bindings = RelationalLaboratoryFixtures.bindingBatch(DATE, 7, 2);
        assertEquals(4, bindings.size());
        assertEquals(RelationalBinding.Relation.MC, bindings.get(0).relation());
        assertEquals(RelationalBinding.Relation.CF, bindings.get(1).relation());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        RelationalLaboratoryFixtures.source(
                                DataExportTemplate.FRETES, DATE, 1, 4097, 3, false));
        assertThrows(
                IllegalArgumentException.class,
                () -> RelationalLaboratoryFixtures.bindingBatch(DATE, 1, 51));
    }

    @Test
    void expansionFixturePreservesTwoPhysicalRowsPerRootAndRejectsForeignTargets()
            throws Exception {
        for (final var template :
                List.of(DataExportTemplate.FRETES, DataExportTemplate.LOCALIZACAO_CARGAS)) {
            final var source = ExpansionDependencyFixtures.source(template, 7, 2, 2);
            final var release = source.contractRelease(template);
            final var gateway = source.gateway(template, guard(release), release, configuration());
            assertEquals(2, gateway.fetch(request(template, 1, 2)).recordCount());
            assertEquals(2, gateway.fetch(request(template, 2, 2)).recordCount());
            assertEquals(0, gateway.fetch(request(template, 3, 2)).recordCount());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ExpansionDependencyFixtures.hydration(template, "FOREIGN:7"));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> ExpansionDependencyFixtures.data(DataExportTemplate.FRETES, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> ExpansionDependencyFixtures.data(DataExportTemplate.COLETAS, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> ExpansionDependencyFixtures.source(DataExportTemplate.FRETES, 1, 2, 0));
        assertEquals(
                "INTEGER:300007",
                ExpansionDependencyFixtures.financialTerms("INTEGER:300007").sourceKey());
    }

    @Test
    void relationalHydrationUsesClaimTargetAndRejectsUnscopedTarget() throws Exception {
        final var claim =
                new JdbcRelationalLaboratory.Claim(
                        1,
                        1,
                        RelationalBinding.Relation.MC,
                        "INTEGER:200007",
                        "ROOT",
                        DATE,
                        Instant.parse("2036-04-01T12:00:00Z"));
        final var source = RelationalLaboratoryFixtures.hydration(claim, 2);
        final var template = DataExportTemplate.COLETAS;
        final var release = source.contractRelease(template);
        final var gateway = source.gateway(template, guard(release), release, configuration());
        assertEquals(1, gateway.fetch(request(template, 1, 2)).recordCount());
        assertEquals(0, gateway.fetch(request(template, 2, 2)).recordCount());
        final var foreign =
                new JdbcRelationalLaboratory.Claim(
                        1,
                        1,
                        RelationalBinding.Relation.MC,
                        "INTEGER:7",
                        "ROOT",
                        DATE,
                        Instant.parse("2036-04-01T12:00:00Z"));
        assertThrows(
                IllegalArgumentException.class,
                () -> RelationalLaboratoryFixtures.hydration(foreign, 2));
    }

    private static DataExportPageRequest request(
            final DataExportTemplate template, final int page, final int size) {
        return new DataExportPageRequest(
                template,
                new BusinessDateRange(DATE, DATE),
                Optional.empty(),
                page,
                size,
                template.defaultOrderBy());
    }

    private static ImmutableFingerprint configuration() {
        return new ImmutableFingerprint("synthetic-bootstrap", "d".repeat(64));
    }

    private static ContractRunGuard guard(final SourceContractRelease release) {
        final var policy =
                ContractCompatibilityPolicy.create(
                        "synthetic-bootstrap-policy", release.contractFingerprint(), List.of());
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
