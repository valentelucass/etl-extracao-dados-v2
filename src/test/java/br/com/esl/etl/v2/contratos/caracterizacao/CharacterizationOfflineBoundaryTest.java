package br.com.esl.etl.v2.contratos.caracterizacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class CharacterizationOfflineBoundaryTest {

    private static final Path IMPLEMENTATION =
            Path.of("src/test/java/br/com/esl/etl/v2/contratos/caracterizacao");
    private static final Path RESOURCES = Path.of("src/test/resources/contracts/v2-012");
    private static final Path STATES = Path.of("STATES.md");
    private static final Path CHAT_TRAIL = Path.of("docs/runbooks/trilha-de-chats-gpt-5-6.md");

    @Test
    void keepsTheFoundationInTestScopeAndOutsideLegacyRemoteHarnesses() throws IOException {
        assertFalse(
                Files.exists(Path.of("src/main/java/br/com/esl/etl/v2/contratos/caracterizacao")));
        final List<String> forbiddenTokens =
                List.of(
                        "java.net",
                        "java.sql",
                        "System.getenv",
                        "System.getProperty",
                        "ContractRemoteExecution",
                        "ContractTestConfiguration",
                        "DataExportTemplate",
                        "HttpClient",
                        "ProcessBuilder",
                        "jdbc:",
                        "http://",
                        "https://");
        try (Stream<Path> files = Files.list(IMPLEMENTATION)) {
            for (final Path file :
                    files.filter(Files::isRegularFile)
                            .filter(path -> !path.getFileName().toString().endsWith("Test.java"))
                            .toList()) {
                final String content = Files.readString(file);
                for (final String forbidden : forbiddenTokens) {
                    assertFalse(
                            content.contains(forbidden),
                            file.getFileName() + " contém boundary proibida: " + forbidden);
                }
            }
        }
    }

    @Test
    void containsExactlyFourProfilesAndOneSyntheticFixtureBundlePerProfile() throws IOException {
        assertEquals(
                Set.of(
                        "coletas-6908.profile.json",
                        "manifestos-6399.profile.json",
                        "cotacoes-6906.profile.json",
                        "usuarios-individual.profile.json"),
                names(RESOURCES.resolve("profiles")));
        assertEquals(
                Set.of(
                        "coletas-6908.synthetic.json",
                        "manifestos-6399.synthetic.json",
                        "cotacoes-6906.synthetic.json",
                        "usuarios-individual.synthetic.json"),
                names(RESOURCES.resolve("fixtures")));
    }

    @Test
    void fixturesAreExplicitlySyntheticAndContainNoNetworkCoordinates() throws IOException {
        try (Stream<Path> files = Files.list(RESOURCES.resolve("fixtures"))) {
            for (final Path file : files.filter(Files::isRegularFile).toList()) {
                final String content = Files.readString(file);
                assertTrue(content.contains("\"fixtureMarker\": \"SYNTH_"));
                assertTrue(content.contains("\"evidenceClassification\": \"SYNTHETIC_FIXTURE\""));
                assertFalse(content.contains("://"));
                assertFalse(content.contains("Bearer "));
            }
        }
    }

    @Test
    void leavesEveryEntityQ01AndEveryRealCharacterizationStageOpen() throws IOException {
        final String trail = Files.readString(CHAT_TRAIL, StandardCharsets.UTF_8);
        final Matcher entityQ01 =
                Pattern.compile(
                                "(?m)^- \\[ \\] STATUS=(?:CANDIDATO|EXTERNAL_HOLD) \\| ROTA=Q-[A-Z]{3}-01 \\|")
                        .matcher(trail);
        int openEntityQ01Count = 0;
        while (entityQ01.find()) {
            openEntityQ01Count++;
        }
        assertEquals(10, openEntityQ01Count);
        assertFalse(
                Pattern.compile(
                                "(?m)^- \\[x\\].*\\| ROTA=Q-(?!(?:FND|BST)-01(?: |$))[A-Z]{3}-01 \\|")
                        .matcher(trail)
                        .find());
        assertTrue(
                trail.contains(
                        "- [x] STATUS=CONCLUIDO | ROTA=Q-FND-01 | BLOCO=39 | TAREFA=V2-012/FUNDACAO_LOCAL |"));
        assertTrue(trail.contains("- [ ] STATUS=EXTERNAL_HOLD | ROTA=Q-MAN-01 |"));

        final String states = Files.readString(STATES, StandardCharsets.UTF_8);
        assertTrue(states.contains("- [ ] **V2-012 —"));
        assertTrue(states.contains("  - [ ] **V2-012a —"));
        assertTrue(states.contains("  - [ ] **V2-012b —"));
        assertTrue(states.contains("  - [ ] **V2-012c —"));
    }

    private static Set<String> names(final Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toUnmodifiableSet());
        }
    }
}
