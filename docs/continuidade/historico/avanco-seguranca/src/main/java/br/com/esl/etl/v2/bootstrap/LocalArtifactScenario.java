package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticScenarioVariant;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlContract;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionArtifact;
import br.com.esl.etl.v2.plataforma.fonte.raster.RasterArtifact;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.qualificacao.DeclaredWireRows;
import br.com.esl.etl.v2.plataforma.qualificacao.PinnedLocalJson;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationComparator;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationOracles;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The existing eleven-input/five-fact runtime with explicitly supplied expansion and oracle files.
 */
public final class LocalArtifactScenario {
    private final DeclaredIntegralInputs integral;
    private final DeclaredSqlOracles integralOracles;
    private final int roots;
    private final int pageSize;
    private final ArtifactExpansionSources sources;
    private final RasterArtifact raster;
    private final DeclaredExpansionRelations relations;
    private final DeclaredWireRows wire;
    private final LocalFactOracle facts;
    private final List<PinnedLocalJson> manifestPins;
    private final QualificationScenarioVerifier verifier;

    public LocalArtifactScenario(
            final Path inputPath, final Path oraclePath, final CancellationToken token)
            throws IOException {
        final var pins = new ArrayList<PinnedLocalJson>();
        final var inputPin = PinnedLocalJson.open(inputPath, 16384);
        pins.add(inputPin);
        final var input = inputPin.read();
        integral =
                java.util.Set.of("local-artifact-scenario-v2", "local-artifact-scenario-v3")
                                .contains(QualificationJson.text(input, "version", 40))
                        ? new DeclaredIntegralInputs(inputPin, token)
                        : null;
        if (integral != null) {
            roots = integral.roots();
            pageSize = integral.pageSize();
            sources = integral.expansions();
            raster = integral.raster();
            relations = integral.relations();
        } else {
            QualificationJson.fields(
                    input,
                    "version",
                    "mode",
                    "target",
                    "support",
                    "windowStart",
                    "windowEndExclusive",
                    "roots",
                    "pageSize",
                    "expansions",
                    "raster",
                    "relations");
            if (!"local-artifact-scenario-v1".equals(QualificationJson.text(input, "version", 40))
                    || !"LOCAL_ARTIFACT_ROLLBACK".equals(QualificationJson.text(input, "mode", 40))
                    || !"localhost/ETL_SISTEMA_V2_SHADOW"
                            .equals(QualificationJson.text(input, "target", 64))
                    || !"PACKAGED_ANALYTIC_SUPPORT_V1"
                            .equals(QualificationJson.text(input, "support", 40))
                    || !AnalyticScenarioRuntime.START
                            .toString()
                            .equals(QualificationJson.text(input, "windowStart", 10))
                    || !AnalyticScenarioRuntime.END
                            .toString()
                            .equals(QualificationJson.text(input, "windowEndExclusive", 10))) {
                throw new IllegalArgumentException("LOCAL_SCENARIO_SCOPE");
            }
            roots = QualificationJson.number(input, "roots", 2, 32);
            pageSize = QualificationJson.number(input, "pageSize", 1, 16);
            QualificationJson.array(input.path("expansions"), 4, 4);
            final var artifacts = new ArrayList<ExpansionArtifact>(4);
            for (final var entry : input.path("expansions")) {
                token.throwIfCancellationRequested();
                final var pin =
                        PinnedLocalJson.reference(
                                inputPath.toAbsolutePath().getParent(), entry, 262144);
                pins.add(pin);
                final var artifact = ExpansionArtifact.read(pin);
                if (artifact.pageSize() != pageSize
                        || !artifact.date().equals(AnalyticScenarioRuntime.START)) {
                    throw new IllegalArgumentException("LOCAL_SCENARIO_CAPTURE_WINDOW");
                }
                artifacts.add(artifact);
            }
            sources = new ArtifactExpansionSources(artifacts, 1, token);
            final var relationPin =
                    PinnedLocalJson.reference(
                            inputPath.toAbsolutePath().getParent(), input.path("relations"), 16384);
            final var rasterPin =
                    PinnedLocalJson.reference(
                            inputPath.toAbsolutePath().getParent(), input.path("raster"), 131072);
            pins.add(relationPin);
            pins.add(rasterPin);
            relations = new DeclaredExpansionRelations(relationPin, token);
            raster = new RasterArtifact(rasterPin);
            if (!raster.window().start().equals(AnalyticScenarioRuntime.START)
                    || !raster.window().endExclusive().equals(AnalyticScenarioRuntime.END)
                    || !raster.zone().equals(AnalyticScenarioRuntime.ZONE)
                    || raster.revision() != 1) {
                throw new IllegalArgumentException("LOCAL_SCENARIO_RASTER_SCOPE");
            }
            try {
                if (!raster.characterize(token).executable()) {
                    throw new IllegalArgumentException("LOCAL_SCENARIO_RASTER_INCOMPLETE");
                }
            } catch (final java.sql.SQLException failure) {
                throw new IllegalArgumentException("LOCAL_SCENARIO_RASTER_INVALID", failure);
            }
        }
        final var oraclePin = PinnedLocalJson.open(oraclePath, 16384);
        pins.add(oraclePin);
        final var oracle = oraclePin.read();
        QualificationJson.fields(
                oracle,
                "version",
                "origin",
                "inputSha256",
                "schemaSha256",
                "runtimeSha256",
                "outputs",
                "wire",
                "facts");
        if (!(integral == null ? "local-artifact-oracles-v1" : "local-artifact-oracles-v2")
                        .equals(QualificationJson.text(oracle, "version", 40))
                || !"INDEPENDENT_SYNTHETIC_RULES_V1"
                        .equals(QualificationJson.text(oracle, "origin", 40))
                || !inputPin.sha256().equals(QualificationJson.digest(oracle, "inputSha256"))
                || !runtimeFingerprint().equals(QualificationJson.digest(oracle, "runtimeSha256"))
                || !schemaFingerprint().equals(QualificationJson.digest(oracle, "schemaSha256"))) {
            throw new IllegalArgumentException("LOCAL_SCENARIO_ORACLE_BINDING");
        }
        final var binding =
                new QualificationComparator.Binding(
                        runtimeFingerprint(),
                        inputPin.sha256(),
                        oraclePin.sha256(),
                        "INDEPENDENT_SYNTHETIC_RULES_V1");
        final var outputsPin =
                PinnedLocalJson.reference(
                        oraclePath.toAbsolutePath().getParent(), oracle.path("outputs"), 524288);
        final var wirePin =
                PinnedLocalJson.reference(
                        oraclePath.toAbsolutePath().getParent(), oracle.path("wire"), 262144);
        final var factsPin =
                PinnedLocalJson.reference(
                        oraclePath.toAbsolutePath().getParent(), oracle.path("facts"), 16384);
        pins.add(outputsPin);
        pins.add(wirePin);
        pins.add(factsPin);
        wire = new DeclaredWireRows(wirePin);
        facts = new LocalFactOracle(factsPin);
        manifestPins = List.copyOf(pins);
        integralOracles = integral == null ? null : new DeclaredSqlOracles(outputsPin, token);
        verifier =
                integral != null
                        ? new QualificationScenarioVerifier(integralOracles, binding, wire, facts)
                        : new QualificationScenarioVerifier(
                                QualificationOracles.parse(outputsPin.read()),
                                binding,
                                binding,
                                wire,
                                facts);
    }

    public QualificationScenarioVerifier.Result execute(
            final ColetaTemporalLaboratorySession session, final CancellationToken token)
            throws Exception {
        return execute(
                session,
                token,
                AnalyticScenarioObserver.NONE,
                java.util.UUID.randomUUID(),
                () -> {});
    }

    public QualificationScenarioVerifier.Result execute(
            final ColetaTemporalLaboratorySession session,
            final CancellationToken token,
            final AnalyticScenarioObserver observer,
            final java.util.UUID runId,
            final Runnable afterPreparation)
            throws Exception {
        verifyFiles(token);
        final Instant started = Instant.now();
        new QualificationPhysicalMetadata().verify(session);
        final var runtime =
                integral != null
                        ? new AnalyticScenarioRuntime(
                                session, Clock.systemUTC(), observer, integral)
                        : new AnalyticScenarioRuntime(
                                session,
                                Clock.systemUTC(),
                                observer,
                                AnalyticScenarioVariant.BASELINE,
                                sources,
                                (count, revision) -> {
                                    if (count != roots || revision != raster.revision()) {
                                        throw new IllegalArgumentException(
                                                "LOCAL_SCENARIO_RASTER_SELECTION");
                                    }
                                    return raster.gateway();
                                },
                                relations);
        final var run = runtime.start(runId, roots, pageSize, AnalyticScenarioRuntime.Fault.NONE);
        final var cycle =
                runtime.capture(
                        run,
                        ExecutionMode.BOOTSTRAP,
                        integral == null ? 1 : integral.revision(),
                        false,
                        null,
                        token);
        afterPreparation.run();
        if (integral != null) {
            final var verified =
                    verifier.verifyIntegral(session, run, cycle, integral, started, token);
            if (verified.selected().state()
                    != br.com.esl.etl.v2.plataforma.qualificacao.QualificationGate.State
                            .PASS_LOCAL) {
                return verified;
            }
            final var sweep = integral.sweep();
            final var observation =
                    new LocalAnalyticCollectionSweep(
                                    session,
                                    run.id(),
                                    run.relational(),
                                    integral.policy(),
                                    integral.clock(),
                                    Clock.systemUTC(),
                                    observer)
                            .observe(
                                    sweep.snapshot(run.id()),
                                    java.util.UUID.randomUUID(),
                                    sweep.inputs(run.id(), token, observer),
                                    token);
            return new QualificationScenarioVerifier.Result(
                    verified.scopes(),
                    verified.outputs(),
                    verified.selected(),
                    verified.retainedTechnicalRecords(),
                    observation.preview());
        }
        return verifier.verify(
                session,
                run,
                List.of(cycle),
                started,
                List.of(AnalyticSqlContract.values()),
                token);
    }

    public int roots() {
        return roots;
    }

    DeclaredIntegralInputs integralInputs() {
        if (integral == null) {
            throw new IllegalArgumentException("SEQUENCE_REQUIRES_INTEGRAL_INPUTS");
        }
        return integral;
    }

    QualificationScenarioVerifier.Result compareSequence(
            final ColetaTemporalLaboratorySession session,
            final AnalyticScenarioRuntime.Run run,
            final List<AnalyticScenarioRuntime.Cycle> cycles,
            final Instant started,
            final CancellationToken token)
            throws Exception {
        return verifier.verifyIntegral(session, run, cycles, integralInputs(), started, token);
    }

    SequenceRecomposition.Receipt recompose(
            final ColetaTemporalLaboratorySession session,
            final AnalyticScenarioRuntime.Run run,
            final AnalyticScenarioRuntime.Cycle source,
            final CancellationToken token)
            throws Exception {
        return SequenceRecomposition.execute(
                session, run, source, integralInputs(), integralOracles.factCandidates(), token);
    }

    QualificationScenarioVerifier.Result compareRecomposition(
            final ColetaTemporalLaboratorySession session,
            final AnalyticScenarioRuntime.Run run,
            final List<AnalyticScenarioRuntime.Cycle> cycles,
            final Instant started,
            final CancellationToken token,
            final List<SequenceRecomposition.Receipt> recompositions)
            throws Exception {
        return verifier.verifyIntegral(
                session, run, cycles, integralInputs(), started, token, recompositions);
    }

    List<SweepResponsibilityPlanner.Preview> previewSequence(
            final ColetaTemporalLaboratorySession session,
            final AnalyticScenarioRuntime.Run run,
            final AnalyticScenarioObserver observer,
            final CancellationToken token)
            throws Exception {
        final var input = integralInputs();
        // The declared preview captures its own snapshot. Its observations must not replace the
        // scenario's captured cohort before the next dependent stage.
        try (var connection = session.getConnection()) {
            final var savepoint = connection.setSavepoint();
            Exception original = null;
            try {
                final var sweep = input.sweep();
                return new LocalAnalyticCollectionSweep(
                                session,
                                run.id(),
                                run.relational(),
                                input.policy(),
                                input.clock(),
                                Clock.systemUTC(),
                                observer)
                        .observe(
                                sweep.snapshot(run.id()),
                                java.util.UUID.randomUUID(),
                                sweep.inputs(run.id(), token, observer),
                                token)
                        .preview();
            } catch (final Exception failure) {
                original = failure;
                throw failure;
            } finally {
                try {
                    connection.rollback(savepoint);
                } catch (final java.sql.SQLException rollback) {
                    if (original == null) {
                        throw rollback;
                    }
                    original.addSuppressed(rollback);
                }
            }
        }
    }

    public java.time.LocalDate start() {
        return integral == null ? AnalyticScenarioRuntime.START : integral.start();
    }

    public java.time.LocalDate endExclusive() {
        return integral == null ? AnalyticScenarioRuntime.END : integral.end();
    }

    public int pageSize() {
        return pageSize;
    }

    public void verifyFiles(final CancellationToken token) throws Exception {
        if (integral != null) {
            integral.verifyFiles(token);
            integralOracles.verifyFiles(token);
        }
        for (final var pin : manifestPins) {
            token.throwIfCancellationRequested();
            pin.verify();
        }
        sources.verifyFiles(token);
        relations.verifyFiles(token);
        wire.verifyFiles(token);
        facts.verifyFiles(token);
        if (!raster.characterize(token).executable()) {
            throw new IllegalArgumentException("LOCAL_SCENARIO_RASTER_INCOMPLETE");
        }
    }

    public static String schemaFingerprint() throws IOException {
        try (var stream =
                LocalArtifactScenario.class.getResourceAsStream(
                        "/qualification-laboratory/physical-columns.v098.json")) {
            if (stream == null) {
                throw new IllegalArgumentException("LOCAL_SCENARIO_SCHEMA_RESOURCE");
            }
            final byte[] bytes = stream.readNBytes(524289);
            QualificationJson.parse(bytes, 524288);
            return QualificationJson.sha256(bytes);
        }
    }

    /**
     * Released execution binds the whole JAR. Exploded classes are only a distinct build/test
     * binding.
     */
    public static String runtimeFingerprint() throws IOException {
        final Path location;
        try {
            location =
                    Path.of(
                            LocalArtifactScenario.class
                                    .getProtectionDomain()
                                    .getCodeSource()
                                    .getLocation()
                                    .toURI());
        } catch (final java.net.URISyntaxException failure) {
            throw new IllegalArgumentException("LOCAL_SCENARIO_RUNTIME_LOCATION", failure);
        }
        if (java.nio.file.Files.isRegularFile(location)) {
            if (!location.getFileName().toString().endsWith(".jar")
                    || java.nio.file.Files.size(location) > 67108864) {
                throw new IllegalArgumentException("LOCAL_SCENARIO_RUNTIME_BOUND");
            }
            return QualificationJson.sha256(location);
        }
        final var signature = new StringBuilder("EXPLODED_LOCAL_TEST_CLASSES_V1|");
        for (final var type :
                List.of(
                        LocalArtifactScenario.class,
                        LocalExpansionRuntime.class,
                        LocalRasterRuntime.class,
                        QualificationComparator.class,
                        LocalFactOracle.class)) {
            try (var stream = type.getResourceAsStream(type.getSimpleName() + ".class")) {
                if (stream == null) {
                    throw new IllegalArgumentException("LOCAL_SCENARIO_CLASS_MISSING");
                }
                final byte[] bytes = stream.readNBytes(131073);
                if (bytes.length > 131072) {
                    throw new IllegalArgumentException("LOCAL_SCENARIO_CLASS_BOUND");
                }
                signature.append(QualificationJson.sha256(bytes)).append('|');
            }
        }
        return QualificationJson.sha256(
                signature.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
