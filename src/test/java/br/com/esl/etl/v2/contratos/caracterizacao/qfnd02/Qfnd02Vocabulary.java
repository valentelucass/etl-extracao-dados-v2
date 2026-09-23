package br.com.esl.etl.v2.contratos.caracterizacao.qfnd02;

/** Vocabulário fechado da extensão offline Q-FND-02. */
public final class Qfnd02Vocabulary {

    private Qfnd02Vocabulary() {}

    public enum Entity {
        FRETES,
        LOCALIZACAO_CARGAS
    }

    public enum SourceProfile {
        DATA_EXPORT,
        GRAPHQL_SIDECAR
    }

    public enum ProviderType {
        INTEGER,
        UNVERIFIED_ORACLE_REQUIRED
    }

    public enum Presence {
        ABSENT,
        NULL,
        VALUE
    }

    public enum ProfileStatus {
        PREPARED_NOT_EXECUTED
    }

    public enum GateStatus {
        ORACLE_REQUIRED
    }

    public enum ProviderEvidence {
        NOT_EXECUTED
    }

    public enum EvidenceClassification {
        SYNTHETIC_FIXTURE
    }

    public enum Outcome {
        SYNTHETIC_STRUCTURE_ACCEPTED,
        FAIL_CLOSED
    }

    public enum Reason {
        PROFILE_BINDING_MISMATCH,
        CHANNEL_SET_DRIFT,
        PATH_DRIFT,
        FIELD_TYPE_DRIFT,
        PRESENCE_MODEL_INCOMPLETE,
        SOURCE_KEY_DRIFT,
        PROVIDER_EVIDENCE_DRIFT,
        UNSAFE_CAPABILITY,
        PAGINATION_DRIFT,
        AUTHORITY_DRIFT
    }
}
