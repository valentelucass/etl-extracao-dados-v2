package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RelationalSyntheticSource;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWindowStrategy;
import br.com.esl.etl.v2.plataforma.persistencia.relacional.JdbcRelationalLaboratory;
import br.com.esl.etl.v2.plataforma.relacional.RelationalBinding;
import br.com.esl.etl.v2.plataforma.relacional.RelationalCaptureContracts;
import br.com.esl.etl.v2.plataforma.relacional.RelationalLaboratoryPolicy;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RelationalLaboratoryContractTest {
    private static final LocalDate DATE = LocalDate.of(2036, 4, 1);

    @Test
    void relationalCaptureMapsOnlyTheThreeScopedSourceFamiliesBeforeJdbc() {
        assertEquals("manifestos", LocalRelationalRuntime.entity(DataExportTemplate.MANIFESTOS));
        assertEquals("coletas", LocalRelationalRuntime.entity(DataExportTemplate.COLETAS));
        assertEquals("fretes", LocalRelationalRuntime.entity(DataExportTemplate.FRETES));
        assertEquals(
                "REL_LAB_ENTITY_DENIED",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> LocalRelationalRuntime.entity(DataExportTemplate.COTACOES))
                        .getMessage());
    }

    @Test
    void relationalCaptureAdmissionRejectsDatesModesAndCancellationBeforeJdbc() {
        final var bounds = policy(new int[] {10000, 4, 3, 20, 2, 1, 17, 200});
        final var execution = UUID.randomUUID();
        final var replay = UUID.randomUUID();
        final var day =
                LaboratoryCaptureWindow.day(DATE, ZoneOffset.UTC, RuntimeWindowStrategy.FULL);
        LocalRelationalRuntime.requireCaptureAdmission(
                execution,
                bounds,
                DATE,
                ExecutionMode.BOOTSTRAP,
                null,
                CancellationToken.none(),
                day);
        LocalRelationalRuntime.requireCaptureAdmission(
                execution,
                bounds,
                DATE,
                ExecutionMode.REPLAY,
                replay,
                CancellationToken.none(),
                day);

        final var otherDay =
                LaboratoryCaptureWindow.day(
                        DATE.plusDays(1), ZoneOffset.UTC, RuntimeWindowStrategy.FULL);
        assertEquals(
                "REL_LAB_SINGLE_SOURCE_DAY_REQUIRED",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        LocalRelationalRuntime.requireCaptureAdmission(
                                                execution,
                                                bounds,
                                                DATE,
                                                ExecutionMode.BOOTSTRAP,
                                                null,
                                                CancellationToken.none(),
                                                otherDay))
                        .getMessage());
        final var outside = DATE.minusDays(2);
        assertEquals(
                "REL_LAB_WINDOW_BOUND",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        LocalRelationalRuntime.requireCaptureAdmission(
                                                execution,
                                                bounds,
                                                outside,
                                                ExecutionMode.BOOTSTRAP,
                                                null,
                                                CancellationToken.none(),
                                                LaboratoryCaptureWindow.day(
                                                        outside,
                                                        ZoneOffset.UTC,
                                                        RuntimeWindowStrategy.FULL)))
                        .getMessage());
        for (final var mode : List.of(ExecutionMode.SWEEP, ExecutionMode.REPLAY)) {
            assertEquals(
                    "REL_LAB_MODE_DENIED",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            LocalRelationalRuntime.requireCaptureAdmission(
                                                    execution,
                                                    bounds,
                                                    DATE,
                                                    mode,
                                                    null,
                                                    CancellationToken.none(),
                                                    day))
                            .getMessage());
        }
        assertEquals(
                "REL_LAB_MODE_DENIED",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        LocalRelationalRuntime.requireCaptureAdmission(
                                                execution,
                                                bounds,
                                                DATE,
                                                ExecutionMode.BACKFILL,
                                                replay,
                                                CancellationToken.none(),
                                                day))
                        .getMessage());
        assertThrows(
                ResilienceCancelledException.class,
                () ->
                        LocalRelationalRuntime.requireCaptureAdmission(
                                execution,
                                bounds,
                                DATE,
                                ExecutionMode.BOOTSTRAP,
                                null,
                                () -> true,
                                day));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "scenario",
                "prod --synthetic-relational-lab",
                "scenario --prod",
                "scenario --synthetic-relational-lab --path=C:/real",
                "scenario --synthetic-relational-lab --roots=33",
                "scenario --synthetic-relational-lab --page-size=101",
                "scenario --synthetic-relational-lab --days=4",
                "scenario --synthetic-relational-lab --roots=1 --roots=2",
                "scenario --synthetic-relational-lab --roots=0",
                "scenario --synthetic-relational-lab --source=GRAPHQL",
                "scenario --synthetic-relational-lab --database=ETL_SISTEMA",
                "scenario --synthetic-relational-lab --roots=-1",
                "scenario --synthetic-relational-lab --days=1=2"
            })
    void rejectsUnapprovedSurfaceBeforeAnyConnection(final String command) {
        final var bytes = new ByteArrayOutputStream();
        assertEquals(
                RuntimeExitCategory.CONFIG_AUTH,
                RelationalLaboratoryMain.run(
                        command.isEmpty() ? new String[0] : command.split(" "),
                        new PrintStream(bytes, true, StandardCharsets.UTF_8)));
        assertEquals("REL_LAB_CONFIG_REJECTED", bytes.toString(StandardCharsets.UTF_8).trim());
    }

    @Test
    void validCommandsExposeFiniteFixtureBudgets() {
        for (final String command : List.of("scenario", "hydrate", "replay", "status")) {
            final var option =
                    RelationalLaboratoryMain.Options.parse(
                            new String[] {
                                command,
                                "--synthetic-relational-lab",
                                "--roots=32",
                                "--page-size=100",
                                "--days=3"
                            });
            assertEquals(command, option.command());
            assertEquals(32, option.roots());
            assertEquals(100, option.pageSize());
            assertEquals(3, option.days());
        }
    }

    @Test
    void typedKeysPreserveZeroAndDoNotCollapseTextIntoNumbers() {
        assertEquals("INTEGER:0", RelationalBinding.Key.integer(0).storage());
        assertEquals("ROOT", RelationalBinding.Key.root().storage());
        assertNotEquals(
                RelationalBinding.Key.integer(0),
                new RelationalBinding.Key(RelationalBinding.WireType.STRING, "0"));
        assertThrows(IllegalArgumentException.class, () -> RelationalBinding.Key.integer(-1));
        for (final String invalid : List.of("", "00", "1.0", "-1", " 1", "1\n", "1".repeat(121))) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new RelationalBinding.Key(RelationalBinding.WireType.INTEGER, invalid));
        }
        for (final String invalid :
                List.of("", "x\u007f", "a\nb", "x".repeat(121), "item ", " item")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new RelationalBinding.Key(RelationalBinding.WireType.STRING, invalid));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> new RelationalBinding.Key(RelationalBinding.WireType.ROOT, "other"));
    }

    @Test
    void policyBudgetChangesAreBoundIntoConfigurationAndWindowExpansionIsFinite() {
        final var normal = policy(new int[] {10000, 4, 3, 20, 2, 1, 17, 200});
        normal.validateDate(DATE.minusDays(1));
        normal.validateDate(DATE.plusDays(3));
        assertThrows(IllegalArgumentException.class, () -> normal.validateDate(DATE.minusDays(2)));
        assertThrows(IllegalArgumentException.class, () -> normal.validateDate(DATE.plusDays(4)));
        assertEquals(64, normal.fingerprint().length());
        for (int changed = 0; changed < 8; changed++) {
            final int[] budgets = {10000, 4, 3, 20, 2, 1, 17, 200};
            budgets[changed]++;
            assertNotEquals(normal.fingerprint(), policy(budgets).fingerprint());
            budgets[changed] = -1;
            assertThrows(IllegalArgumentException.class, () -> policy(budgets));
            budgets[changed] = Integer.MAX_VALUE;
            assertThrows(IllegalArgumentException.class, () -> policy(budgets));
        }
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RelationalLaboratoryPolicy(
                                DATE, DATE.minusDays(1), 1, 1, 1, 1, 1, 0, 1, 2));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RelationalLaboratoryPolicy(
                                DATE, DATE.plusDays(367), 1, 1, 1, 1, 1, 0, 1, 2));
    }

    @Test
    void releasesAreIndependentAndCaptureConservationCannotHideMissingRows() {
        final var release = RelationalSyntheticSource.contracts();
        assertNotEquals(release.manifestos(), release.coletas());
        assertNotEquals(release.coletas(), release.fretes());
        assertEquals(release.fingerprint(), RelationalSyntheticSource.contracts().fingerprint());
        assertNotEquals(
                release.fingerprint(),
                new RelationalCaptureContracts(
                                release.coletas(), release.manifestos(), release.fretes())
                        .fingerprint());
        assertThrows(
                IllegalArgumentException.class,
                () -> new RelationalCaptureContracts("0", release.coletas(), release.fretes()));
        assertEquals(0, new JdbcRelationalLaboratory.CaptureReceipt(0, 0, 0, 0).considered());
        assertThrows(
                IllegalArgumentException.class,
                () -> new JdbcRelationalLaboratory.CaptureReceipt(3, 1, 1, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new JdbcRelationalLaboratory.CaptureReceipt(-1, -1, 0, 0));
    }

    @Test
    void bindingRequiresDeclaredProvenanceAndChildGrain() {
        final var valid = RelationalLaboratoryFixtures.bindingBatch(DATE, 1, 1).get(0);
        assertEquals(RelationalBinding.Relation.MC, valid.relation());
        assertTrue(valid.evidenceId().startsWith("synthetic-"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RelationalBinding(
                                "provider-assumed",
                                valid.relation(),
                                valid.origin(),
                                valid.originComponent(),
                                valid.target(),
                                valid.targetComponent(),
                                DATE,
                                1,
                                valid.cardinality()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RelationalBinding(
                                valid.evidenceId(),
                                RelationalBinding.Relation.CF,
                                valid.origin(),
                                valid.originComponent(),
                                valid.target(),
                                RelationalBinding.Key.root(),
                                DATE,
                                1,
                                valid.cardinality()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RelationalBinding(
                                valid.evidenceId(),
                                valid.relation(),
                                valid.origin(),
                                RelationalBinding.Key.root(),
                                valid.target(),
                                valid.targetComponent(),
                                DATE,
                                1,
                                valid.cardinality()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RelationalBinding(
                                valid.evidenceId(),
                                valid.relation(),
                                valid.origin(),
                                valid.originComponent(),
                                valid.target(),
                                valid.targetComponent(),
                                DATE,
                                0,
                                valid.cardinality()));
    }

    private static RelationalLaboratoryPolicy policy(final int[] values) {
        return new RelationalLaboratoryPolicy(
                DATE,
                DATE.plusDays(2),
                values[0],
                values[1],
                values[2],
                values[3],
                values[4],
                values[5],
                values[6],
                values[7]);
    }
}
