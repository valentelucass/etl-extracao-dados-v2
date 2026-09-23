package br.com.esl.etl.v2.plataforma.reconciliacao.sweep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SweepPreviewBoundaryTest {
    private static final Path PRODUCT_ROOT =
            Path.of("src/main/java/br/com/esl/etl/v2/plataforma/reconciliacao/sweep");

    @Test
    void productPackageIsExactlySixPurePreviewTypes() throws Exception {
        final Set<String> files;
        try (var paths = Files.walk(PRODUCT_ROOT)) {
            files =
                    paths.filter(Files::isRegularFile)
                            .map(PRODUCT_ROOT::relativize)
                            .map(Path::toString)
                            .map(value -> value.replace('\\', '/'))
                            .collect(Collectors.toSet());
        }
        assertEquals(
                Set.of(
                        "SweepApplicability.java",
                        "SweepScope.java",
                        "SweepPreviewBlockReason.java",
                        "SweepPreviewEvidence.java",
                        "SweepPreviewAssessment.java",
                        "FailClosedSweepPreviewKernel.java"),
                files);

        for (final String file : files) {
            final String source =
                    Files.readString(PRODUCT_ROOT.resolve(file), StandardCharsets.UTF_8)
                            .toLowerCase(Locale.ROOT);
            for (final String forbidden : FORBIDDEN_SOURCE_TOKENS) {
                assertFalse(source.contains(forbidden), file + " contains " + forbidden);
            }
        }
    }

    @Test
    void kernelIsNotWiredIntoRuntimeCompositionAuthorizationOrMain() throws Exception {
        for (final Path source :
                List.of(
                        Path.of("src/main/java/br/com/esl/etl/v2/bootstrap/Main.java"),
                        Path.of(
                                "src/main/java/br/com/esl/etl/v2/bootstrap/RuntimeCompositionRoot.java"),
                        Path.of(
                                "src/main/java/br/com/esl/etl/v2/plataforma/autorizacao/RuntimeAction.java"),
                        Path.of(
                                "src/main/java/br/com/esl/etl/v2/plataforma/autorizacao/RuntimeAuthorizationPolicy.java"))) {
            final String content = Files.readString(source, StandardCharsets.UTF_8);
            assertFalse(content.contains("reconciliacao.sweep"));
            assertFalse(content.contains("FailClosedSweepPreviewKernel"));
        }
        assertEquals(
                Set.of("RUN", "REPLAY", "SWEEP_PREVIEW", "SWEEP_APPLY", "FORCE_RUN", "STATUS"),
                java.util.Arrays.stream(
                                br.com.esl.etl.v2.plataforma.autorizacao.RuntimeAction.values())
                        .map(Enum::name)
                        .collect(Collectors.toSet()));
    }

    private static final Set<String> FORBIDDEN_SOURCE_TOKENS =
            Set.of(
                    "java.sql",
                    "java.net",
                    "java.io",
                    "java.nio.file",
                    "runtimeaction",
                    "runtimerole",
                    "sourcedataeffect",
                    "contractpromotionpermit",
                    "runtimecompositionroot",
                    "jdbc",
                    "http",
                    "socket",
                    " runnable",
                    " callable",
                    " consumer",
                    " callback");
}
