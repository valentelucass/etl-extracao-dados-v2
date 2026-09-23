package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionArtifact;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QualificationArtifactInputTest {
    @TempDir Path directory;

    @Test
    void captureBoundaryDoesNotRequireTheConcreteFixtureGenerator() {
        assertFalse(
                Arrays.stream(LocalExpansionRuntime.class.getMethods())
                        .filter(method -> method.getName().equals("capture"))
                        .flatMap(method -> Arrays.stream(method.getParameterTypes()))
                        .anyMatch(type -> type.getSimpleName().equals("ExpansionSyntheticSource")));
    }

    @Test
    void allFourTypedProfilesAcceptExplicitArtifactsWithoutAuthenticatingTheirSource()
            throws Exception {
        for (final var family : new String[] {"CAP", "FAT", "INV", "SIN"}) {
            final var template = template(family);
            final var path =
                    QualificationArtifactFixtures.write(
                            directory.resolve(family), template, family, row -> {});
            final var input = ExpansionArtifact.read(path);
            final var result = ExpansionCharacterizer.inspect(input, CancellationToken.none());
            assertTrue(result.executable(), family);
            assertEquals(1, result.validRows());
            assertEquals(2, result.traversal().pages());
            assertEquals("STRUCTURALLY_VALID_UNVERIFIED", result.traversal().providerEvidence());
        }
    }

    @Test
    void missingBindingAndInvalidTypedValueRemainBlockedBeforeSql() throws Exception {
        final var path =
                QualificationArtifactFixtures.write(
                        directory,
                        DataExportTemplate.CONTAS_A_PAGAR,
                        "CAP",
                        row -> {
                            row.remove("binding");
                            ((ObjectNode) row.path("data"))
                                    .put("value", "invalid-synthetic-decimal");
                        });
        final var result =
                ExpansionCharacterizer.inspect(
                        ExpansionArtifact.read(path), CancellationToken.none());
        assertFalse(result.executable());
        assertEquals(1, result.invalidRows());
        assertEquals(1, result.traversal().absentBindings());
        final var output = new ByteArrayOutputStream();
        assertEquals(
                RuntimeExitCategory.SOURCE_DQ,
                LocalDataLaboratoryMain.run(
                        new String[] {"capture", "--artifact", path.toString()},
                        new PrintStream(output)));
        assertTrue(output.toString().contains("BLOCKED_BEFORE_SQL"));
        assertFalse(output.toString().contains("invalid-synthetic-decimal"));
    }

    @Test
    void swappedContractAndEscapingPageAreRejected() throws Exception {
        final var path =
                QualificationArtifactFixtures.write(
                        directory, DataExportTemplate.CONTAS_A_PAGAR, "CAP", row -> {});
        QualificationArtifactFixtures.manifest(
                path, root -> root.put("contractSha256", "0".repeat(64)));
        assertThrows(IllegalArgumentException.class, () -> ExpansionArtifact.read(path));
        final var second =
                QualificationArtifactFixtures.write(
                        directory.resolve("second"),
                        DataExportTemplate.SINISTROS,
                        "SIN",
                        row -> {});
        QualificationArtifactFixtures.manifest(
                second,
                root -> ((ObjectNode) root.path("pages").get(0)).put("file", "../escape.json"));
        assertThrows(IllegalArgumentException.class, () -> ExpansionArtifact.read(second));
    }

    @Test
    void changedPageAndUnprovenTerminalNeverReceiveAnExecutableReceipt() throws Exception {
        final var path =
                QualificationArtifactFixtures.write(
                        directory, DataExportTemplate.INVENTARIO, "INV", row -> {});
        final var artifact = ExpansionArtifact.read(path);
        Files.writeString(directory.resolve("page-one.json"), "[]");
        assertThrows(
                IllegalArgumentException.class,
                () -> ExpansionCharacterizer.inspect(artifact, CancellationToken.none()));
        final var second =
                QualificationArtifactFixtures.write(
                        directory.resolve("second"),
                        DataExportTemplate.INVENTARIO,
                        "INV",
                        row -> {});
        QualificationArtifactFixtures.manifest(second, root -> root.put("complete", false));
        assertFalse(
                ExpansionCharacterizer.inspect(
                                ExpansionArtifact.read(second), CancellationToken.none())
                        .executable());
    }

    @Test
    void characterizationIsDeterministicAndUnsupportedCommandsDoNotOpenSql() throws Exception {
        final var path =
                QualificationArtifactFixtures.write(
                        directory, DataExportTemplate.FATURAS_POR_CLIENTE, "FAT", row -> {});
        final var first = new ByteArrayOutputStream();
        final var second = new ByteArrayOutputStream();
        final String[] args = {"characterize", "--artifact", path.toString()};
        assertEquals(
                RuntimeExitCategory.SUCCESS,
                LocalDataLaboratoryMain.run(args, new PrintStream(first)));
        assertEquals(
                RuntimeExitCategory.SUCCESS,
                LocalDataLaboratoryMain.run(args, new PrintStream(second)));
        assertEquals(first.toString(), second.toString());
        assertEquals(
                RuntimeExitCategory.CONFIG_AUTH,
                LocalDataLaboratoryMain.run(
                        new String[] {"sweep-apply", "--artifact", path.toString()},
                        new PrintStream(second)));
    }

    private static DataExportTemplate template(final String family) {
        return switch (family) {
            case "CAP" -> DataExportTemplate.CONTAS_A_PAGAR;
            case "FAT" -> DataExportTemplate.FATURAS_POR_CLIENTE;
            case "INV" -> DataExportTemplate.INVENTARIO;
            case "SIN" -> DataExportTemplate.SINISTROS;
            default -> throw new IllegalArgumentException();
        };
    }
}
