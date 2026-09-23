package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionArtifact;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionCaptureSource;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.util.EnumMap;
import java.util.List;

/** Exactly four prevalidated local inputs; an absent family or revision never falls back. */
public final class ArtifactExpansionSources implements AnalyticExpansionSources {
    private final EnumMap<DataExportTemplate, ExpansionArtifact> inputs =
            new EnumMap<>(DataExportTemplate.class);
    private final int revision;

    public ArtifactExpansionSources(
            final List<ExpansionArtifact> artifacts,
            final int revision,
            final CancellationToken token) {
        if (artifacts.size() != 4 || revision < 1 || revision > 1000) {
            throw new IllegalArgumentException("EXP_ARTIFACT_SELECTION_BOUND");
        }
        this.revision = revision;
        for (final var artifact : artifacts) {
            if (inputs.putIfAbsent(artifact.template(), artifact) != null
                    || !ExpansionCharacterizer.inspect(artifact, token).executable()) {
                throw new IllegalArgumentException("EXP_ARTIFACT_SELECTION_INVALID");
            }
        }
    }

    @Override
    public ExpansionCaptureSource open(
            final DataExportTemplate template,
            final int roots,
            final int pageSize,
            final int sourceRevision,
            final ExpansionCaptureSource.Observer observer) {
        final var artifact = inputs.get(template);
        if (artifact == null || sourceRevision != revision || pageSize != artifact.pageSize()) {
            throw new IllegalArgumentException("EXP_ARTIFACT_SELECTION_BINDING");
        }
        return artifact.source(observer);
    }

    public void verifyFiles(final CancellationToken token) {
        for (final var input : inputs.values()) {
            if (!ExpansionCharacterizer.inspect(input, token).executable()) {
                throw new IllegalArgumentException("EXP_ARTIFACT_SELECTION_INVALID");
            }
        }
    }
}
