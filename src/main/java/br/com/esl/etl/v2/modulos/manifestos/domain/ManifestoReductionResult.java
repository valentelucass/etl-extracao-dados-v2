package br.com.esl.etl.v2.modulos.manifestos.domain;

import br.com.esl.etl.v2.plataforma.identidade.ScopedSourceIdentity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Resultado determinístico de MAN-01, MAN-02, MAN-04 e MAN-07 para uma raiz/coorte. */
public final class ManifestoReductionResult {

    private final ScopedSourceIdentity.SourceKey sourceKey;
    private final Instant freshnessAtUtc;
    private final ManifestoFreshnessOrigin freshnessOrigin;
    private final Map<String, ManifestoFieldValue> rootFields;
    private final Map<ManifestoMetric, ManifestoMetricValue> metrics;
    private final ManifestoFieldValue competence;
    private final List<ScopedSourceIdentity.SourceKey> pickCandidates;
    private final List<ManifestoMdfeObservation> mdfeCandidates;
    private final List<String> childQuarantineReasons;
    private final String rootQuarantineReason;

    ManifestoReductionResult(
            final ScopedSourceIdentity.SourceKey sourceKey,
            final Instant freshnessAtUtc,
            final ManifestoFreshnessOrigin freshnessOrigin,
            final Map<String, ManifestoFieldValue> rootFields,
            final Map<ManifestoMetric, ManifestoMetricValue> metrics,
            final ManifestoFieldValue competence,
            final List<ScopedSourceIdentity.SourceKey> pickCandidates,
            final List<ManifestoMdfeObservation> mdfeCandidates,
            final List<String> childQuarantineReasons,
            final String rootQuarantineReason) {
        this.sourceKey = Objects.requireNonNull(sourceKey, "A chave raiz é obrigatória.");
        this.freshnessAtUtc = Objects.requireNonNull(freshnessAtUtc, "O frescor é obrigatório.");
        this.freshnessOrigin = Objects.requireNonNull(freshnessOrigin, "A origem é obrigatória.");
        this.rootFields = Map.copyOf(rootFields);
        this.metrics = Map.copyOf(metrics);
        this.competence = Objects.requireNonNull(competence, "A competência é obrigatória.");
        this.pickCandidates = List.copyOf(pickCandidates);
        this.mdfeCandidates = List.copyOf(mdfeCandidates);
        this.childQuarantineReasons = List.copyOf(childQuarantineReasons);
        this.rootQuarantineReason = rootQuarantineReason;
    }

    public ScopedSourceIdentity.SourceKey sourceKey() {
        return sourceKey;
    }

    public Instant freshnessAtUtc() {
        return freshnessAtUtc;
    }

    public ManifestoFreshnessOrigin freshnessOrigin() {
        return freshnessOrigin;
    }

    public Map<String, ManifestoFieldValue> rootFields() {
        return rootFields;
    }

    public Map<ManifestoMetric, ManifestoMetricValue> metrics() {
        return metrics;
    }

    public ManifestoFieldValue competence() {
        return competence;
    }

    public List<ScopedSourceIdentity.SourceKey> pickCandidates() {
        return pickCandidates;
    }

    public List<ManifestoMdfeObservation> mdfeCandidates() {
        return mdfeCandidates;
    }

    public List<String> childQuarantineReasons() {
        return childQuarantineReasons;
    }

    public Optional<String> rootQuarantineReason() {
        return Optional.ofNullable(rootQuarantineReason);
    }

    public boolean promotable() {
        return rootQuarantineReason == null;
    }
}
