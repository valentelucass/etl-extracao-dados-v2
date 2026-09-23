package br.com.esl.etl.v2.contratos.caracterizacao;

import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.Entity;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.FoundationOutcome;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CharacterizationDeterminismTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-05T12:00:00Z"), ZoneOffset.UTC);

    @TempDir Path temporaryDirectory;

    @Test
    void fingerprintsAreCanonicalAndStableAcrossFreshLoads() {
        final CharacterizationProfileRegistry first = CharacterizationProfileRegistry.loadDefault();
        final CharacterizationProfileRegistry second =
                CharacterizationProfileRegistry.loadDefault();
        final CharacterizationFixtureLoader loader = new CharacterizationFixtureLoader();

        for (final CharacterizationProfile profile : first.profiles()) {
            final CharacterizationProfile reloaded = second.profile(profile.entity());
            assertEquals(profile.profileFingerprint(), reloaded.profileFingerprint());
            assertEquals(
                    loader.loadResource(profile.fixtureResource()).fixtureFingerprint(),
                    loader.loadResource(reloaded.fixtureResource()).fixtureFingerprint());
        }

        final Map<String, Integer> forward = new LinkedHashMap<>();
        forward.put("syntheticA", 1);
        forward.put("syntheticB", 2);
        final Map<String, Integer> reverse = new LinkedHashMap<>();
        reverse.put("syntheticB", 2);
        reverse.put("syntheticA", 1);
        assertEquals(
                CharacterizationFingerprint.sha256(forward),
                CharacterizationFingerprint.sha256(reverse));
    }

    @Test
    void writesTheSameAllowlistedReceiptUnderTargetForAnyInputOrder() throws Exception {
        final CharacterizationProfileRegistry registry =
                CharacterizationProfileRegistry.loadDefault();
        final CharacterizationFixtureLoader loader = new CharacterizationFixtureLoader();
        final List<CharacterizationResult> results = new ArrayList<>();
        final List<CharacterizationReceiptWriter.ReceiptBinding> bindings = new ArrayList<>();
        for (final CharacterizationProfile profile : registry.profiles()) {
            final CharacterizationFixture fixture = loader.loadResource(profile.fixtureResource());
            final CharacterizationFixture.Scenario scenario = acceptedScenario(fixture);
            results.add(
                    registry.evaluator(profile.entity()).evaluate(profile, scenario.observation()));
            bindings.add(CharacterizationReceiptWriter.ReceiptBinding.from(profile, fixture));
        }
        final CharacterizationSummary firstSummary = CharacterizationSummary.from(results);
        Collections.reverse(results);
        Collections.reverse(bindings);
        final CharacterizationSummary secondSummary = CharacterizationSummary.from(results);
        final Path target = temporaryDirectory.resolve("target");
        final CharacterizationReceiptWriter writer =
                CharacterizationReceiptWriter.forTargetDirectory(
                        target, FIXED_CLOCK, () -> "SYNTH_FOUNDATION_RECEIPT");

        final Path firstReceipt = writer.write(firstSummary, bindings);
        final byte[] firstBytes = Files.readAllBytes(firstReceipt);
        final Path secondReceipt = writer.write(secondSummary, bindings);
        final byte[] secondBytes = Files.readAllBytes(secondReceipt);
        final String content = Files.readString(secondReceipt);

        assertArrayEquals(firstBytes, secondBytes);
        assertEquals(
                target.resolve("v2-012-characterization/SYNTH_FOUNDATION_RECEIPT/receipt.json")
                        .toAbsolutePath()
                        .normalize(),
                secondReceipt);
        assertTrue(content.contains("FOUNDATION_OFFLINE_COMPLETE_Q01_PENDING_ORACLES"));
        assertTrue(content.contains("PREPARED_NOT_EXECUTED"));
        assertTrue(content.contains("ORACLE_REQUIRED"));
        assertFalse(content.contains("sourceInstance"));
        assertFalse(content.contains("tenantScope"));
        assertFalse(content.contains("observedPaths"));
        assertFalse(content.contains("rawCursor"));
        assertEquals(
                FoundationOutcome.FOUNDATION_OFFLINE_COMPLETE_Q01_PENDING_ORACLES,
                firstSummary.outcome());
    }

    @Test
    void rejectsUnsafeReceiptDestinationsBindingsAndReportFields() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        CharacterizationReceiptWriter.forTargetDirectory(
                                temporaryDirectory, FIXED_CLOCK, () -> "SYNTH_RECEIPT"));
        final CharacterizationReceiptWriter writer =
                CharacterizationReceiptWriter.forTargetDirectory(
                        temporaryDirectory.resolve("target"), FIXED_CLOCK, () -> "../escape");
        assertThrows(
                IllegalArgumentException.class,
                () -> writer.write(acceptedSummary(), acceptedBindings()));

        final ObjectMapper json = new ObjectMapper();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        CharacterizationReportSanitizer.requireSanitized(
                                json.readTree("{\"payload\":\"SYNTH_VALUE\"}")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        CharacterizationReportSanitizer.requireSanitized(
                                json.readTree("{\"source_instance\":\"SYNTH_VALUE\"}")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        CharacterizationReportSanitizer.requireSanitized(
                                json.readTree("{\"business_id_hash\":\"SYNTH_VALUE\"}")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        CharacterizationReportSanitizer.requireSanitized(
                                json.readTree("{\"authorizationHeader\":\"SYNTH_VALUE\"}")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        CharacterizationReportSanitizer.requireSanitized(
                                json.readTree("{\"customerBusinessId\":\"SYNTH_VALUE\"}")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        CharacterizationReportSanitizer.requireSanitized(
                                json.readTree("{\"record_content_hash\":\"SYNTH_VALUE\"}")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        CharacterizationReportSanitizer.requireSanitized(
                                json.readTree("{\"value\":\"https://synthetic.invalid\"}")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        CharacterizationReportSanitizer.requireSanitized(
                                json.readTree(
                                        "{\"value\":\""
                                                + String.join("", "Bea", "rer")
                                                + " SYNTH_TOKEN\"}")));

        final CharacterizationProfileRegistry registry =
                CharacterizationProfileRegistry.loadDefault();
        final CharacterizationFixtureLoader loader = new CharacterizationFixtureLoader();
        final CharacterizationProfile coletas = registry.profile(Entity.COLETAS);
        final CharacterizationFixture coletasFixture =
                loader.loadResource(coletas.fixtureResource());
        final CharacterizationFixture foreignBundle =
                new CharacterizationFixture(
                        "V2_012_FIXTURE_V1",
                        "SYNTH_FOREIGN_OBSERVATION_BUNDLE",
                        coletas.profileId(),
                        List.of(
                                coletasFixture.scenarios().stream()
                                        .filter(
                                                scenario ->
                                                        "SYNTH_ORACLE_MISSING"
                                                                .equals(scenario.scenarioId()))
                                        .findFirst()
                                        .orElseThrow()));
        assertThrows(
                IllegalArgumentException.class,
                () -> CharacterizationReceiptWriter.ReceiptBinding.from(coletas, foreignBundle));

        final CharacterizationSummary accepted = acceptedSummary();
        final CharacterizationResult acceptedColetas =
                accepted.results().stream()
                        .filter(result -> result.entity() == Entity.COLETAS)
                        .findFirst()
                        .orElseThrow();
        final CharacterizationObservation rejectedObservation =
                coletasFixture.scenarios().stream()
                        .filter(scenario -> "SYNTH_ORACLE_MISSING".equals(scenario.scenarioId()))
                        .findFirst()
                        .orElseThrow()
                        .observation();
        final CharacterizationResult forgedAccepted =
                new CharacterizationResult(
                        acceptedColetas.profileId(),
                        acceptedColetas.entity(),
                        acceptedColetas.profileFingerprint(),
                        acceptedColetas.contractFingerprint(),
                        rejectedObservation.observationFingerprint(),
                        acceptedColetas.observationStatus(),
                        acceptedColetas.profileStatus(),
                        acceptedColetas.gateStatus(),
                        acceptedColetas.checks(),
                        acceptedColetas.failClosedReasons());
        final List<CharacterizationResult> forgedResults =
                accepted.results().stream()
                        .map(result -> result.entity() == Entity.COLETAS ? forgedAccepted : result)
                        .toList();
        final CharacterizationReceiptWriter boundWriter =
                CharacterizationReceiptWriter.forTargetDirectory(
                        temporaryDirectory.resolve("binding-check/target"),
                        FIXED_CLOCK,
                        () -> "SYNTH_BOUND_RECEIPT");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        boundWriter.write(
                                CharacterizationSummary.from(forgedResults), acceptedBindings()));
    }

    @Test
    void refusesAReparsePointInsideTargetWhenThePlatformSupportsLinks() throws Exception {
        final Path target = temporaryDirectory.resolve("link-check/target");
        final Path outside = temporaryDirectory.resolve("outside-receipts");
        Files.createDirectories(target);
        Files.createDirectories(outside);
        try {
            Files.createSymbolicLink(target.resolve("v2-012-characterization"), outside);
        } catch (final UnsupportedOperationException | java.io.IOException exception) {
            Assumptions.assumeTrue(false, "O filesystem não permite symlink de teste.");
        }
        final CharacterizationReceiptWriter writer =
                CharacterizationReceiptWriter.forTargetDirectory(
                        target, FIXED_CLOCK, () -> "SYNTH_LINK_RECEIPT");
        assertThrows(
                IllegalArgumentException.class,
                () -> writer.write(acceptedSummary(), acceptedBindings()));
        assertFalse(Files.exists(outside.resolve("SYNTH_LINK_RECEIPT/receipt.json")));
    }

    @Test
    void replacesAHardLinkedReceiptWithoutMutatingTheExternalInode() throws Exception {
        final Path target = temporaryDirectory.resolve("hardlink-check/target");
        final Path receiptDirectory =
                target.resolve("v2-012-characterization/SYNTH_HARDLINK_RECEIPT");
        final Path externalFile = temporaryDirectory.resolve("external-sentinel.json");
        final Path destination = receiptDirectory.resolve("receipt.json");
        Files.createDirectories(receiptDirectory);
        Files.writeString(externalFile, "SYNTH_EXTERNAL_SENTINEL");
        try {
            Files.createLink(destination, externalFile);
        } catch (final UnsupportedOperationException | java.io.IOException exception) {
            Assumptions.assumeTrue(false, "O filesystem não permite hardlink de teste.");
        }

        final CharacterizationReceiptWriter writer =
                CharacterizationReceiptWriter.forTargetDirectory(
                        target, FIXED_CLOCK, () -> "SYNTH_HARDLINK_RECEIPT");
        final Path written = writer.write(acceptedSummary(), acceptedBindings());

        assertEquals("SYNTH_EXTERNAL_SENTINEL", Files.readString(externalFile));
        assertFalse(Files.isSameFile(externalFile, written));
        assertTrue(
                Files.readString(written)
                        .contains("FOUNDATION_OFFLINE_COMPLETE_Q01_PENDING_ORACLES"));
    }

    private CharacterizationSummary acceptedSummary() {
        final CharacterizationProfileRegistry registry =
                CharacterizationProfileRegistry.loadDefault();
        final CharacterizationFixtureLoader loader = new CharacterizationFixtureLoader();
        final List<CharacterizationResult> results = new ArrayList<>();
        for (final CharacterizationProfile profile : registry.profiles()) {
            results.add(
                    registry.evaluator(profile.entity())
                            .evaluate(
                                    profile,
                                    acceptedScenario(loader.loadResource(profile.fixtureResource()))
                                            .observation()));
        }
        return CharacterizationSummary.from(results);
    }

    private List<CharacterizationReceiptWriter.ReceiptBinding> acceptedBindings() {
        final CharacterizationProfileRegistry registry =
                CharacterizationProfileRegistry.loadDefault();
        final CharacterizationFixtureLoader loader = new CharacterizationFixtureLoader();
        return registry.profiles().stream()
                .map(
                        profile ->
                                CharacterizationReceiptWriter.ReceiptBinding.from(
                                        profile, loader.loadResource(profile.fixtureResource())))
                .toList();
    }

    private static CharacterizationFixture.Scenario acceptedScenario(
            final CharacterizationFixture fixture) {
        return fixture.scenarios().stream()
                .filter(scenario -> "SYNTH_ACCEPTED_BASELINE".equals(scenario.scenarioId()))
                .findFirst()
                .orElseThrow();
    }
}
