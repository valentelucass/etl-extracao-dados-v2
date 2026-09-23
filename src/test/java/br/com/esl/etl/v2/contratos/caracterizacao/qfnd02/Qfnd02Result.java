package br.com.esl.etl.v2.contratos.caracterizacao.qfnd02;

import static br.com.esl.etl.v2.contratos.caracterizacao.qfnd02.Qfnd02Vocabulary.GateStatus;
import static br.com.esl.etl.v2.contratos.caracterizacao.qfnd02.Qfnd02Vocabulary.Outcome;
import static br.com.esl.etl.v2.contratos.caracterizacao.qfnd02.Qfnd02Vocabulary.ProfileStatus;
import static br.com.esl.etl.v2.contratos.caracterizacao.qfnd02.Qfnd02Vocabulary.ProviderEvidence;
import static br.com.esl.etl.v2.contratos.caracterizacao.qfnd02.Qfnd02Vocabulary.Reason;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Resultado sem payload, escopo, cursor ou identificador de negócio. */
public record Qfnd02Result(
        String profileId,
        String profileFingerprint,
        String fixtureFingerprint,
        Outcome outcome,
        ProfileStatus profileStatus,
        GateStatus gateStatus,
        ProviderEvidence providerEvidence,
        boolean providerPass,
        Set<Reason> reasons) {

    private static final Set<String> PROFILE_IDS =
            Set.of("V2_012_FRETES_6389", "V2_012_LOCALIZACAO_8656_DATA_EXPORT");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    public Qfnd02Result {
        profileId = Objects.requireNonNull(profileId);
        profileFingerprint = Objects.requireNonNull(profileFingerprint);
        fixtureFingerprint = Objects.requireNonNull(fixtureFingerprint);
        outcome = Objects.requireNonNull(outcome);
        profileStatus = Objects.requireNonNull(profileStatus);
        gateStatus = Objects.requireNonNull(gateStatus);
        providerEvidence = Objects.requireNonNull(providerEvidence);
        reasons = Set.copyOf(Objects.requireNonNull(reasons));
        if (!PROFILE_IDS.contains(profileId)
                || !SHA256.matcher(profileFingerprint).matches()
                || !SHA256.matcher(fixtureFingerprint).matches()
                || providerPass
                || profileStatus != ProfileStatus.PREPARED_NOT_EXECUTED
                || gateStatus != GateStatus.ORACLE_REQUIRED
                || providerEvidence != ProviderEvidence.NOT_EXECUTED
                || outcome == Outcome.SYNTHETIC_STRUCTURE_ACCEPTED != reasons.isEmpty()) {
            throw new IllegalArgumentException("Resultado Q-FND-02 incoerente.");
        }
    }
}
