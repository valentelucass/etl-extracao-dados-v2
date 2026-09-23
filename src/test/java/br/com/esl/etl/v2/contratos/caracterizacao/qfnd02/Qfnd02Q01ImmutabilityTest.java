package br.com.esl.etl.v2.contratos.caracterizacao.qfnd02;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class Qfnd02Q01ImmutabilityTest {

    private static final String EXPECTED_AGGREGATE =
            "ab99feb5e413deb44bf0dfc26f737453fa802cd6b293805ba82aaa1c56b9029a";
    private static final List<String> LOCKED_PATHS =
            """
            docs/adr/0026-harness-caracterizacao-provider-neutral-fail-closed.md
            docs/catalogos/caracterizacao-v2-012/manifesto.json
            docs/catalogos/caracterizacao-v2-012/README.md
            docs/runbooks/v2-012-fundacao-caracterizacao-sol.md
            scripts/validation/Test-V2012CharacterizationFoundation.ps1
            src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CharacterizationAdapterRegistry.java
            src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CharacterizationClosedSchemaTest.java
            src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CharacterizationDeterminismTest.java
            src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CharacterizationEvaluator.java
            src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CharacterizationFingerprint.java
            src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CharacterizationFixture.java
            src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CharacterizationFixtureLoader.java
            src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CharacterizationFoundationTest.java
            src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CharacterizationObservation.java
            src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CharacterizationOfflineBoundaryTest.java
            src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CharacterizationOracleAdapter.java
            src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CharacterizationProfile.java
            src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CharacterizationProfileLoader.java
            src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CharacterizationProfileRegistry.java
            src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CharacterizationReceipt.java
            src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CharacterizationReceiptWriter.java
            src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CharacterizationReportSanitizer.java
            src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CharacterizationResult.java
            src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CharacterizationRule.java
            src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CharacterizationRuleRegistry.java
            src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CharacterizationSummary.java
            src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CharacterizationVocabulary.java
            src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/ColetasCharacterizationRule.java
            src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CotacoesCharacterizationRule.java
            src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/InMemoryDataExportCharacterizationAdapter.java
            src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/InMemoryGraphQlCharacterizationAdapter.java
            src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/ManifestosCharacterizationRule.java
            src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/StrictUtf8JsonLoader.java
            src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/StrictUtf8JsonLoaderTest.java
            src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/UsuariosCharacterizationRule.java
            src/test/resources/contracts/v2-012/fixtures/coletas-6908.synthetic.json
            src/test/resources/contracts/v2-012/fixtures/cotacoes-6906.synthetic.json
            src/test/resources/contracts/v2-012/fixtures/manifestos-6399.synthetic.json
            src/test/resources/contracts/v2-012/fixtures/usuarios-individual.synthetic.json
            src/test/resources/contracts/v2-012/profiles/coletas-6908.profile.json
            src/test/resources/contracts/v2-012/profiles/cotacoes-6906.profile.json
            src/test/resources/contracts/v2-012/profiles/manifestos-6399.profile.json
            src/test/resources/contracts/v2-012/profiles/usuarios-individual.profile.json
            """
                    .lines()
                    .map(String::strip)
                    .filter(value -> !value.isEmpty())
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();

    @Test
    void keepsAllFortyThreeQ01FilesByteIdentical() throws Exception {
        assertEquals(43, LOCKED_PATHS.size());
        final StringBuilder lockMaterial = new StringBuilder();
        for (final String relative : LOCKED_PATHS) {
            final byte[] bytes = Files.readAllBytes(Path.of(relative));
            lockMaterial.append(sha256(bytes)).append("  ").append(relative).append('\n');
        }
        assertEquals(
                EXPECTED_AGGREGATE,
                sha256(lockMaterial.toString().getBytes(StandardCharsets.UTF_8)));
        assertClosedDirectDirectories();
    }

    private static void assertClosedDirectDirectories() throws IOException {
        final long directJava;
        try (var stream =
                Files.list(Path.of("src/test/java/br/com/esl/etl/v2/contratos/caracterizacao"))) {
            directJava =
                    stream.filter(Files::isRegularFile)
                            .filter(path -> path.getFileName().toString().endsWith(".java"))
                            .count();
        }
        assertEquals(30, directJava);
        try (var profiles = Files.list(Path.of("src/test/resources/contracts/v2-012/profiles"));
                var fixtures =
                        Files.list(Path.of("src/test/resources/contracts/v2-012/fixtures"))) {
            assertEquals(4, profiles.filter(Files::isRegularFile).count());
            assertEquals(4, fixtures.filter(Files::isRegularFile).count());
        }
    }

    private static String sha256(final byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
