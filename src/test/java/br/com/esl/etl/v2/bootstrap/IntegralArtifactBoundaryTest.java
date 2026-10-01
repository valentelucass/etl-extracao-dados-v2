package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.qualificacao.PinnedLocalJson;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IntegralArtifactBoundaryTest {
    @TempDir Path folder;

    @Test
    void strictIntegralAdmissionRejectsUnsupportedContextBeforeAnySessionExists() throws Exception {
        final var file = IntegralArtifactFixtures.write(folder, false, 2);
        final var original = (ObjectNode) IntegralArtifactFixtures.read(file);
        for (final String mutation :
                List.of(
                        "version",
                        "mode",
                        "target",
                        "support",
                        "source",
                        "tenant",
                        "windowStart",
                        "windowEndExclusive",
                        "zone",
                        "logicalClock",
                        "revision",
                        "roots",
                        "pageSize",
                        "fiscalPolicy",
                        "extra",
                        "wrongType",
                        "path")) {
            final var candidate = original.deepCopy();
            switch (mutation) {
                case "version", "mode", "target", "support", "source", "tenant", "fiscalPolicy" ->
                        candidate.put(mutation, "UNDECLARED");
                case "windowStart" -> candidate.put(mutation, "2037-08-14");
                case "windowEndExclusive" -> candidate.put(mutation, "2037-09-12");
                case "zone" -> candidate.put(mutation, "UTC");
                case "logicalClock" -> candidate.put(mutation, "2037-08-11");
                case "revision" -> candidate.put(mutation, 0);
                case "roots" -> candidate.put(mutation, 33);
                case "pageSize" -> candidate.put(mutation, 17);
                case "wrongType" -> candidate.put("revision", "3");
                case "extra" -> candidate.put("fallback", true);
                case "path" ->
                        ((ObjectNode) candidate.path("sources").path("COL"))
                                .put("file", "../outside.json");
                default -> throw new IllegalArgumentException(mutation);
            }
            IntegralArtifactFixtures.save(file, candidate);
            assertThrows(
                    RuntimeException.class, () -> load(file, CancellationToken.none()), mutation);
        }
        IntegralArtifactFixtures.save(file, original);
        assertEquals(2, load(file, CancellationToken.none()).roots());
        assertThrows(ResilienceCancelledException.class, () -> load(file, () -> true));
    }

    @Test
    void declaredReferencesRequireCoverageOfTheWholeWindow() throws Exception {
        final var file = IntegralArtifactFixtures.write(folder, false, 2);
        final var input = IntegralArtifactFixtures.read(file);
        final var refFile = folder.resolve(input.path("references").path("file").asText());
        final var original = (ObjectNode) IntegralArtifactFixtures.read(refFile);
        for (final String field : List.of("validFrom", "validToExclusive")) {
            final var candidate = original.deepCopy();
            candidate.put(field, field.equals("validFrom") ? "2037-08-12" : "2037-08-13");
            IntegralArtifactFixtures.save(refFile, candidate);
            final var failure =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    new DeclaredAnalyticReferences(
                                            PinnedLocalJson.open(refFile, 65536),
                                            java.time.LocalDate.parse("2037-08-11"),
                                            java.time.LocalDate.parse("2037-08-14"),
                                            CancellationToken.none()));
            assertEquals("INTEGRAL_REFERENCES_VALIDITY", failure.getMessage());
        }
    }

    @Test
    void completeInputDoesNotTrustAFileChangedAfterPreflight() throws Exception {
        final var file = IntegralArtifactFixtures.write(folder, false, 2);
        final var declared = load(file, CancellationToken.none());
        final var page = folder.resolve("sources/fre/page-1.json");
        final var values = IntegralArtifactFixtures.read(page);
        ((ObjectNode) values.path(0)).put("total", "999.99000000");
        IntegralArtifactFixtures.save(page, values);
        assertEquals(
                "LOCAL_PIN_BYTES",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> declared.verifyFiles(CancellationToken.none()))
                        .getMessage());
    }

    @Test
    void completeInputBindsCaptureContractsAndPolicyToTheDeclaredWindow() throws Exception {
        final var file = IntegralArtifactFixtures.write(folder, false, 2);
        final var input = load(file, CancellationToken.none());
        final var contracts = input.contracts();
        assertEquals(
                input.capture("MAN").contractRelease().contractFingerprint().sha256(),
                contracts.manifestos());
        assertEquals(
                input.capture("COL").contractRelease().contractFingerprint().sha256(),
                contracts.coletas());
        assertEquals(
                input.capture("FRE").contractRelease().contractFingerprint().sha256(),
                contracts.fretes());
        assertEquals(input.start(), input.policy().start());
        assertEquals(input.end().minusDays(1), input.policy().end());
        assertEquals(input.pageSize(), input.policy().pageSize());
        assertThrows(
                IllegalArgumentException.class,
                () -> input.policy().validateDate(input.start().minusDays(1)));
        assertEquals(
                "INTEGRAL_INPUT_FAMILY_MISSING",
                assertThrows(IllegalArgumentException.class, () -> input.capture("UNDECLARED"))
                        .getMessage());
        input.verifyFiles(CancellationToken.none());
    }

    @Test
    void declaredCaptureFamiliesExposeOnlyTheirOwnSyntheticAdapters() throws Exception {
        final var input =
                load(IntegralArtifactFixtures.write(folder, false, 2), CancellationToken.none());
        assertNotNull(input.capture("MAN").relational(AnalyticScenarioObserver.NONE));
        assertNotNull(input.capture("COL").relational(AnalyticScenarioObserver.NONE));
        assertNotNull(input.capture("FRE").dependency(AnalyticScenarioObserver.NONE));
        assertNotNull(input.capture("LOC").dependency(AnalyticScenarioObserver.NONE));
        assertNotNull(input.capture("COT").quotes());
        assertNotNull(input.capture("USER").users());
        assertEquals(
                "INTEGRAL_RELATIONAL_FAMILY",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        input.capture("USER")
                                                .relational(AnalyticScenarioObserver.NONE))
                        .getMessage());
        assertEquals(
                "INTEGRAL_DEPENDENCY_FAMILY",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        input.capture("COL")
                                                .dependency(AnalyticScenarioObserver.NONE))
                        .getMessage());
        assertEquals(
                "INTEGRAL_QUOTES_FAMILY",
                assertThrows(IllegalArgumentException.class, () -> input.capture("MAN").quotes())
                        .getMessage());
        assertEquals(
                "INTEGRAL_USERS_FAMILY",
                assertThrows(IllegalArgumentException.class, () -> input.capture("FRE").users())
                        .getMessage());
    }

    private static DeclaredIntegralInputs load(final Path file, final CancellationToken token)
            throws Exception {
        return new DeclaredIntegralInputs(PinnedLocalJson.open(file, 131072), token);
    }
}
