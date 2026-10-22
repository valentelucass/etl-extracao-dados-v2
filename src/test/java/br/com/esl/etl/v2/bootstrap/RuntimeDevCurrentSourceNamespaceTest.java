package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.autorizacao.RuntimeAction;
import br.com.esl.etl.v2.plataforma.autorizacao.RuntimeAuthorizationScope;
import br.com.esl.etl.v2.plataforma.configuracao.C78LocalSqlTarget;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.CurrentSourceInfoEvidence;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.persistencia.controle.C78DevEngineBankAdapter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class RuntimeDevCurrentSourceNamespaceTest {
    private static final String CURRENT = "user20261016-continuar-v2";
    private static final String LEGACY = "hermesc54d6a25880d";

    @Test
    void newCurrentReaderRevalidatesTheSameLiveObservationWithoutOpeningPhysicalAdmission()
            throws Exception {
        final var calls = new AtomicInteger();
        try (var diagnostic = silent();
                var reader =
                        reader(
                                CURRENT,
                                diagnostic,
                                () -> {
                                    calls.incrementAndGet();
                                    return observation(CURRENT, "a");
                                })) {
            reader.verify();
            reader.verify();
            assertEquals(2, calls.get());
            assertThrows(RuntimeDevEngineBankBinding.Refusal.class, reader::open);
            assertEquals(null, reader.gateDetailCode());
        }
    }

    @Test
    void legacyCurrentReaderKeepsItsExactC54Pair() throws Exception {
        try (var diagnostic = silent();
                var reader = reader(LEGACY, diagnostic, () -> observation(LEGACY, "a"))) {
            reader.verify();
            assertEquals(null, reader.gateDetailCode());
        }
    }

    @Test
    void neitherAcceptedRequestCanCrossIntoTheOtherUnit() throws Exception {
        for (final String unit : List.of(CURRENT, LEGACY)) {
            final String other = unit.equals(CURRENT) ? LEGACY : CURRENT;
            try (var diagnostic = silent();
                    var reader = reader(unit, diagnostic, () -> observation(other, "a"))) {
                final var refused =
                        assertThrows(RuntimeDevEngineBankBinding.Refusal.class, reader::verify);
                assertEquals(RuntimeDevEngineBankBinding.MissingGate.SOURCE_QUOTA, refused.gate());
                assertEquals(
                        "STOP429_CURRENT_INFO_OR_LIVE_INPUT_BINDING_NOT_PROVED",
                        reader.gateDetailCode());
            }
        }
    }

    @Test
    void unknownUnitRefusesBeforeCallingTheLiveReader() throws Exception {
        final var calls = new AtomicInteger();
        try (var diagnostic = silent();
                var reader =
                        reader(
                                "user20261016-continuar-v2-copy",
                                diagnostic,
                                () -> {
                                    calls.incrementAndGet();
                                    return observation(CURRENT, "a");
                                })) {
            assertThrows(RuntimeDevEngineBankBinding.Refusal.class, reader::verify);
            assertEquals(0, calls.get());
        }
    }

    @Test
    void aClosedInspectionCannotRevalidateOrReuseAcceptedEvidence() throws Exception {
        final var calls = new AtomicInteger();
        try (var diagnostic = silent()) {
            final var reader =
                    reader(
                            CURRENT,
                            diagnostic,
                            () -> {
                                calls.incrementAndGet();
                                return observation(CURRENT, "a");
                            });
            reader.verify();
            reader.close();
            assertThrows(RuntimeDevEngineBankBinding.Refusal.class, reader::verify);
            assertEquals(1, calls.get());
        }
    }

    @Test
    void holderClosureAfterAcceptanceRefusesWithoutPublishingItsPrivateCause() throws Exception {
        final var holderClosed = new AtomicBoolean();
        final var output = new ByteArrayOutputStream();
        try (var diagnostic = new PrintStream(output, false, StandardCharsets.UTF_8);
                var reader =
                        reader(
                                CURRENT,
                                diagnostic,
                                () -> {
                                    if (holderClosed.get()) {
                                        throw new SQLException("fixture-private-holder-cause");
                                    }
                                    return observation(CURRENT, "a");
                                })) {
            reader.verify();
            holderClosed.set(true);
            final var refused =
                    assertThrows(RuntimeDevEngineBankBinding.Refusal.class, reader::verify);
            assertEquals(RuntimeDevEngineBankBinding.MissingGate.SOURCE_QUOTA, refused.gate());
            assertEquals(null, refused.getCause());
            assertFalse(output.toString(StandardCharsets.UTF_8).contains("fixture-private"));
        }
    }

    @Test
    void changingTheAcceptedObservationCannotRenewTheSameReader() throws Exception {
        final var current = new AtomicReference<>(observation(CURRENT, "a"));
        try (var diagnostic = silent();
                var reader = reader(CURRENT, diagnostic, current::get)) {
            reader.verify();
            current.set(observation(CURRENT, "b"));
            assertThrows(RuntimeDevEngineBankBinding.Refusal.class, reader::verify);
        }
    }

    @Test
    void knownCurrentUnitWithoutALiveEvidenceImplementationRemainsHeld() throws Exception {
        try (var diagnostic = silent();
                var reader =
                        new RuntimeDevSourceEvidenceInspection(
                                Path.of(CURRENT), DataExportTemplate.COLETAS, diagnostic)) {
            assertThrows(RuntimeDevEngineBankBinding.Refusal.class, reader::verify);
            assertThrows(RuntimeDevEngineBankBinding.Refusal.class, reader::open);
        }
    }

    @Test
    void configurationRequiresTheExactCurrentUnitAndSampleOptInWhilePreservingF4()
            throws Exception {
        for (final String unit : List.of(CURRENT, LEGACY)) {
            RuntimeDevSourceConfiguration.validateCurrentUnit(
                    unit, true, new Properties(), Map.of());
            assertEquals(
                    "DEV_SOURCE_METADATA_CURRENT_UNIT_REQUIRED",
                    assertThrows(
                                    IOException.class,
                                    () ->
                                            RuntimeDevSourceConfiguration.validateCurrentUnit(
                                                    unit, false, new Properties(), Map.of()))
                            .getMessage());
        }
        for (final boolean sample : List.of(false, true)) {
            RuntimeDevSourceConfiguration.validateCurrentUnit(
                    "hermesf4eda62e83d1", sample, new Properties(), Map.of());
        }
        assertThrows(
                IOException.class,
                () ->
                        RuntimeDevSourceConfiguration.validateCurrentUnit(
                                CURRENT.toUpperCase(java.util.Locale.ROOT),
                                true,
                                new Properties(),
                                Map.of()));
    }

    @Test
    void configurationRejectsAmbiguousNativeOverridesInEitherCurrentNamespace() throws Exception {
        for (final String unit : List.of(CURRENT, LEGACY)) {
            for (final String key : Set.of("V2_DATAEXPORT_BASE_URL", "V2_DATAEXPORT_TOKEN")) {
                final var system = new Properties();
                system.setProperty(key, "");
                assertEquals(
                        "DEV_SOURCE_NATIVE_OVERRIDE_AMBIGUOUS",
                        assertThrows(
                                        IOException.class,
                                        () ->
                                                RuntimeDevSourceConfiguration.validateCurrentUnit(
                                                        unit, true, system, Map.of()))
                                .getMessage());
                assertEquals(
                        "DEV_SOURCE_NATIVE_OVERRIDE_AMBIGUOUS",
                        assertThrows(
                                        IOException.class,
                                        () ->
                                                RuntimeDevSourceConfiguration.validateCurrentUnit(
                                                        unit,
                                                        true,
                                                        new Properties(),
                                                        Map.of(key, "")))
                                .getMessage());
            }
        }
    }

    @Test
    void engineSelectsCurrentProducerOnlyForExactNamespaceAndBothOptIns() {
        for (final String unit : List.of(CURRENT, LEGACY)) {
            assertTrue(RuntimeDevEngineExecution.usesCurrentSource(Path.of(unit), true, true));
            assertFalse(RuntimeDevEngineExecution.usesCurrentSource(Path.of(unit), false, true));
            assertFalse(RuntimeDevEngineExecution.usesCurrentSource(Path.of(unit), true, false));
        }
        assertFalse(RuntimeDevEngineExecution.usesCurrentSource(null, true, true));
        assertFalse(
                RuntimeDevEngineExecution.usesCurrentSource(
                        Path.of(CURRENT + "-copy"), true, true));
        assertFalse(
                RuntimeDevEngineExecution.usesCurrentSource(
                        Path.of("hermesf4eda62e83d1"), true, true));
    }

    @Test
    void newConcreteFactoryStillRefusesMissingRootBeforeSourceOrPhysicalOpen() throws Exception {
        final var config = RuntimeDevSampleExecutionTest.configuration();
        final var request = RuntimeDevSampleExecutionTest.request(config, 1, 1);
        final var scope =
                new RuntimeDevEngineBankBinding.Scope(
                        config,
                        RuntimeAuthorizationScope.from(
                                request.invocation, RuntimeAction.RUN, request.plan),
                        C78LocalSqlTarget.approved(C78LocalSqlTarget.APPROVAL_REFERENCE),
                        "a".repeat(64),
                        "b".repeat(64),
                        "c".repeat(64),
                        request.plan.fingerprint().sha256(),
                        request.template,
                        request.pageSize,
                        request.limits);
        final var output = new ByteArrayOutputStream();
        try (var diagnostic = new PrintStream(output, false, StandardCharsets.UTF_8)) {
            final var composition =
                    RuntimeDevEngineExecution.currentSourceProducer(
                            scope, request, Path.of(CURRENT), null, null, diagnostic);
            final var adapter = new C78DevEngineBankAdapter(null, null, composition.producer());
            try (var reader = composition.reader();
                    var admission = adapter.prepare(scope)) {
                final var refused =
                        assertThrows(RuntimeDevEngineBankBinding.Refusal.class, admission::verify);
                assertEquals(RuntimeDevEngineBankBinding.MissingGate.ADMISSION, refused.gate());
                assertThrows(RuntimeDevEngineBankBinding.Refusal.class, admission::open);
                assertThrows(RuntimeDevEngineBankBinding.Refusal.class, reader::verify);
            }
            assertEquals(
                    "DEV_CURRENT_SOURCE_NAMESPACE_REQUIRED",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            RuntimeDevEngineExecution.currentSourceProducer(
                                                    scope,
                                                    request,
                                                    Path.of(CURRENT + "-copy"),
                                                    null,
                                                    null,
                                                    diagnostic))
                            .getMessage());
        }
        assertEquals(0, output.size());
    }

    private static RuntimeDevSourceEvidenceInspection reader(
            final String unit,
            final PrintStream diagnostic,
            final CurrentSourceInfoEvidence evidence) {
        return new RuntimeDevSourceEvidenceInspection(
                Path.of(unit), DataExportTemplate.COLETAS, diagnostic, evidence);
    }

    private static CurrentSourceInfoEvidence.AcceptedInfo observation(
            final String unit, final String binding) {
        return new CurrentSourceInfoEvidence.AcceptedInfo(
                CurrentSourceInfoEvidence.requestForUnit(unit),
                binding.repeat(32),
                6908,
                200,
                4820,
                31,
                1,
                Instant.parse("2026-10-16T15:00:00Z"));
    }

    private static PrintStream silent() {
        return new PrintStream(new ByteArrayOutputStream(), false, StandardCharsets.UTF_8);
    }
}
