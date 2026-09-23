package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.modulos.faturasporcliente.domain.FaturaClienteRules.FiscalPolicy;
import br.com.esl.etl.v2.plataforma.fonte.SyntheticSourceScope;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionArtifact;
import br.com.esl.etl.v2.plataforma.fonte.raster.RasterArtifact;
import br.com.esl.etl.v2.plataforma.qualificacao.PinnedLocalJson;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.relacional.RelationalCaptureContracts;
import br.com.esl.etl.v2.plataforma.relacional.RelationalLaboratoryPolicy;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Complete local input. Admission walks every pinned file before any SQL effect. */
public final class DeclaredIntegralInputs {
    private static final List<String> FAMILIES = List.of("COL", "FRE", "MAN", "COT", "LOC", "USER");
    private final List<PinnedLocalJson> pins = new ArrayList<>();
    private final Map<String, DeclaredCapturePages> captures = new LinkedHashMap<>();
    private final LocalDate start;
    private final LocalDate end;
    private final Clock clock;
    private final SyntheticSourceScope scope;
    private final int revision;
    private final int roots;
    private final int pageSize;
    private final FiscalPolicy fiscalPolicy;
    private final ArtifactExpansionSources expansions;
    private final RasterArtifact raster;
    private final DeclaredExpansionRelations relations;
    private final DeclaredAnalyticReferences references;
    private final DeclaredAnalyticSupport support;
    private final CollectionSweepArtifact sweep;

    public DeclaredIntegralInputs(final PinnedLocalJson input, final CancellationToken token)
            throws IOException {
        pins.add(input);
        final var root = input.read();
        QualificationJson.fields(
                root,
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
                "sources",
                "expansions",
                "raster",
                "relations",
                "references",
                "supplements",
                "sweep");
        if (!"local-artifact-scenario-v2".equals(QualificationJson.text(root, "version", 40))
                || !"LOCAL_ARTIFACT_ROLLBACK".equals(QualificationJson.text(root, "mode", 40))
                || !"localhost/ETL_SISTEMA_V2_SHADOW"
                        .equals(QualificationJson.text(root, "target", 64))
                || !"EXPLICIT_INTEGRAL_INPUTS_V1"
                        .equals(QualificationJson.text(root, "support", 40))) {
            throw new IllegalArgumentException("INTEGRAL_INPUT_SCOPE");
        }
        scope =
                new SyntheticSourceScope(
                        DeclaredCapturePages.scope(QualificationJson.text(root, "source", 40)),
                        DeclaredCapturePages.scope(QualificationJson.text(root, "tenant", 40)));
        start = LocalDate.parse(QualificationJson.text(root, "windowStart", 10));
        end = LocalDate.parse(QualificationJson.text(root, "windowEndExclusive", 10));
        final var zone = ZoneId.of(QualificationJson.text(root, "zone", 40));
        if (!start.isBefore(end)
                || end.isAfter(start.plusDays(31))
                || !zone.equals(AnalyticScenarioRuntime.ZONE)) {
            throw new IllegalArgumentException("INTEGRAL_INPUT_WINDOW");
        }
        clock = Clock.fixed(Instant.parse(QualificationJson.text(root, "logicalClock", 40)), zone);
        revision = QualificationJson.number(root, "revision", 1, 1000);
        roots = QualificationJson.number(root, "roots", 2, 32);
        pageSize = QualificationJson.number(root, "pageSize", 1, 16);
        fiscalPolicy = FiscalPolicy.valueOf(QualificationJson.text(root, "fiscalPolicy", 40));
        QualificationJson.fields(root.path("sources"), FAMILIES.toArray(String[]::new));
        for (final var family : FAMILIES) {
            final var capture =
                    new DeclaredCapturePages(
                            pin(input, root.path("sources").path(family), 65536), token);
            if (!family.equals(capture.family())
                    || !start.equals(capture.date())
                    || revision != capture.revision()
                    || !scope.source().equals(capture.source())
                    || !scope.tenant().equals(capture.tenant())
                    || capture.pageSize() != (family.equals("USER") ? 20 : pageSize)) {
                throw new IllegalArgumentException("INTEGRAL_INPUT_CAPTURE_SELECTION");
            }
            captures.put(family, capture);
        }
        QualificationJson.array(root.path("expansions"), 4, 4);
        final var artifacts = new ArrayList<ExpansionArtifact>(4);
        for (final var entry : root.path("expansions")) {
            final var artifact = ExpansionArtifact.read(pin(input, entry, 262144));
            if (!start.equals(artifact.date())
                    || pageSize != artifact.pageSize()
                    || !scope.source().equals(artifact.sourceScope().source())
                    || !scope.tenant().equals(artifact.sourceScope().tenant())) {
                throw new IllegalArgumentException("INTEGRAL_INPUT_EXPANSION_SELECTION");
            }
            artifacts.add(artifact);
        }
        expansions = new ArtifactExpansionSources(artifacts, revision, token);
        final var rasterPin = pin(input, root.path("raster"), 131072);
        raster = new RasterArtifact(rasterPin);
        final var rasterRoot = rasterPin.read();
        if (!start.equals(raster.window().start())
                || !end.equals(raster.window().endExclusive())
                || !zone.equals(raster.zone())
                || revision != raster.revision()
                || !scope.source().equals(QualificationJson.text(rasterRoot, "source", 40))
                || !scope.tenant().equals(QualificationJson.text(rasterRoot, "tenant", 40))) {
            throw new IllegalArgumentException("INTEGRAL_INPUT_RASTER_SELECTION");
        }
        relations =
                new DeclaredExpansionRelations(pin(input, root.path("relations"), 16384), token);
        references =
                new DeclaredAnalyticReferences(
                        pin(input, root.path("references"), 16384), start, end, token);
        support =
                new DeclaredAnalyticSupport(
                        pin(input, root.path("supplements"), 16384), start, end, revision, token);
        sweep = new CollectionSweepArtifact(pin(input, root.path("sweep"), 524288), token);
        sweep.requireIntegralScope(scope, start, revision, pageSize, roots);
        verifyFiles(token);
    }

    private PinnedLocalJson pin(
            final PinnedLocalJson parent,
            final com.fasterxml.jackson.databind.JsonNode entry,
            final int maximum)
            throws IOException {
        final var result =
                PinnedLocalJson.reference(parent.file().toPath().getParent(), entry, maximum);
        pins.add(result);
        return result;
    }

    public void verifyFiles(final CancellationToken token) throws IOException {
        for (final var pin : pins) {
            token.throwIfCancellationRequested();
            pin.verify();
        }
        for (final var capture : captures.values()) {
            capture.verifyFiles(token);
        }
        expansions.verifyFiles(token);
        relations.verifyFiles(token);
        references.verifyFiles(token);
        support.verifyFiles(token);
        sweep.verifyFiles(token);
        try {
            if (!raster.characterize(token).executable()) {
                throw new IllegalArgumentException("INTEGRAL_INPUT_RASTER_INCOMPLETE");
            }
        } catch (final java.sql.SQLException failure) {
            throw new IOException("INTEGRAL_INPUT_RASTER_INVALID", failure);
        }
    }

    public DeclaredCapturePages capture(final String family) {
        final var result = captures.get(family);
        if (result == null) {
            throw new IllegalArgumentException("INTEGRAL_INPUT_FAMILY_MISSING");
        }
        return result;
    }

    public RelationalCaptureContracts contracts() {
        return new RelationalCaptureContracts(
                DeclaredCapturePages.release("MAN").contractFingerprint().sha256(),
                DeclaredCapturePages.release("COL").contractFingerprint().sha256(),
                DeclaredCapturePages.release("FRE").contractFingerprint().sha256());
    }

    public RelationalLaboratoryPolicy policy() {
        return new RelationalLaboratoryPolicy(
                start, end.minusDays(1), 100000, 64, 3, 60, 2, 0, pageSize, 1000);
    }

    public LocalDate start() {
        return start;
    }

    public LocalDate end() {
        return end;
    }

    public Clock clock() {
        return clock;
    }

    public SyntheticSourceScope scope() {
        return scope;
    }

    public int revision() {
        return revision;
    }

    public int roots() {
        return roots;
    }

    public int pageSize() {
        return pageSize;
    }

    public FiscalPolicy fiscalPolicy() {
        return fiscalPolicy;
    }

    public ArtifactExpansionSources expansions() {
        return expansions;
    }

    public RasterArtifact raster() {
        return raster;
    }

    public DeclaredExpansionRelations relations() {
        return relations;
    }

    public DeclaredAnalyticReferences references() {
        return references;
    }

    public DeclaredAnalyticSupport support() {
        return support;
    }

    public CollectionSweepArtifact sweep() {
        return sweep;
    }
}
