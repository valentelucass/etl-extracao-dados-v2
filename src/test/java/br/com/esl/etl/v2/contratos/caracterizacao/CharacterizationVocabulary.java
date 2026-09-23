package br.com.esl.etl.v2.contratos.caracterizacao;

/** Vocabulários fechados da fundação offline V2-012. */
public final class CharacterizationVocabulary {

    private CharacterizationVocabulary() {}

    public enum Stage {
        V2_012A,
        V2_012B,
        V2_012C
    }

    public enum Entity {
        COLETAS,
        MANIFESTOS,
        COTACOES,
        USUARIOS
    }

    public enum SourceKind {
        DATA_EXPORT,
        GRAPHQL
    }

    public enum OracleKind {
        DATA_EXPORT_PROVIDER,
        GRAPHQL_PROVIDER
    }

    public enum OracleAvailability {
        SYNTHETIC_IN_MEMORY_ONLY,
        MISSING
    }

    public enum AuthorizationRequirement {
        EXPLICIT_OWNER_AUTHORIZATION
    }

    public enum AuthorizationState {
        SYNTHETIC_TEST_ONLY,
        MISSING
    }

    public enum ExecutionValueRequirement {
        REQUIRED_AT_EXECUTION
    }

    public enum WireType {
        INTEGER,
        STRING,
        BOOLEAN,
        DECIMAL,
        OBJECT,
        ARRAY
    }

    public enum SourceKeyDomain {
        CANONICAL_INTEGER,
        POSITIVE_INTEGER,
        CANONICAL_INTEGER_OR_NON_BLANK_STRING
    }

    public enum ChildKind {
        PICK,
        MDFE
    }

    public enum ChildKeyDomain {
        POSITIVE_INTEGER,
        FIXED_44_DIGIT_STRING
    }

    public enum Presence {
        ABSENT,
        NULL,
        VALUE
    }

    public enum FilterRole {
        REQUIRED,
        COMPLEMENTARY
    }

    public enum PaginationKind {
        NUMERIC_PAGE,
        RELAY_CURSOR
    }

    public enum TerminalCondition {
        EMPTY_PAGE_LOCAL_UNVERIFIED,
        PAGE_INFO_NO_NEXT_PAGE_LOCAL_UNVERIFIED
    }

    public enum PerSemantics {
        DISTINCT_SOURCE_KEYS_WITH_PHYSICAL_EXPANSION,
        UNPROVEN,
        MAXIMUM_FIRST_ARGUMENT
    }

    public enum OrderingRole {
        PARITY_ONLY_NOT_IDENTITY_OR_CURSOR,
        ABSENT
    }

    public enum TemporalTranslation {
        BOUNDARIES_UNPROVEN,
        SOURCE_CIVIL_DATE_UNPROVEN,
        ABSENT_NO_TEMPORAL_FIELD
    }

    public enum TimezoneRequirement {
        EXPLICIT_REQUIRED,
        ORACLE_CONFIRMATION_REQUIRED,
        NOT_APPLICABLE
    }

    public enum TimestampState {
        VALID,
        INVALID,
        NOT_APPLICABLE
    }

    public enum TimezoneState {
        EXPLICIT_CONFIRMED,
        UNCONFIRMED,
        NOT_APPLICABLE
    }

    public enum RootCardinality {
        ARRAY_ELEMENTS,
        GRAPHQL_EDGE_NODES
    }

    public enum LogicalGrain {
        DISTINCT_SCOPED_ID,
        DISTINCT_SCOPED_SEQUENCE_CODE,
        ONE_SCOPED_NODE_PER_EDGE
    }

    public enum ChildPolicy {
        PHYSICAL_EXPANSION_NEVER_CREATES_A_NEW_LOGICAL_ROOT,
        PICK_AND_MDFE_KEYS_ARE_CHILD_LOCAL_WITHOUT_RELATION_INFERENCE,
        NO_CHILD_IDENTITY_OBSERVED,
        NO_DATA_EXPORT_OR_INCREMENTAL_INFERENCE
    }

    public enum ExpansionPolicy {
        PHYSICAL_ROWS_MAY_EXCEED_LOGICAL_ROOTS,
        PHYSICAL_ROWS_PRESERVE_DISTINCT_CHILDREN,
        NO_CHILD_OR_EXPANSION_PROVEN,
        ONE_NODE_PER_EDGE
    }

    public enum EvidenceClassification {
        SYNTHETIC_FIXTURE
    }

    public enum AbsencePolicy {
        OBSERVATION_ONLY_NO_LIFECYCLE_INFERENCE,
        NO_DEACTIVATION_BY_ABSENCE
    }

    public enum StatusMode {
        CLOSED_CATALOG,
        KNOWN_PRECEDENCE_UNKNOWN_PRESERVED,
        NOT_APPLICABLE
    }

    public enum CharacterizationCheck {
        SYNTHETIC_ORACLE_AVAILABLE,
        SYNTHETIC_AUTHORIZATION_CONTEXT,
        EXPLICIT_SCOPES,
        ROOT_MATCH,
        PATH_SET_MATCH,
        FIELD_TYPES_MATCH,
        FILTERS_MATCH,
        RELATIONSHIPS_PRESERVED,
        ABSENCE_POLICY_PRESERVED,
        SOURCE_KEY_VALID,
        PRESENCE_TRI_STATE_TRACKED,
        TERMINAL_CONDITION_MET,
        LIMITS_RESPECTED,
        ORDERING_PARITY_VALID,
        TEMPORAL_EVIDENCE_VALID,
        STATUS_SEMANTICS_VALID,
        CARDINALITY_MATCH,
        ENTITY_RULES_MATCH,
        SYNTHETIC_EVIDENCE_ONLY
    }

    public enum ProfileStatus {
        PREPARED_NOT_EXECUTED
    }

    public enum ObservationStatus {
        SYNTHETIC_STRUCTURE_ACCEPTED,
        FAIL_CLOSED
    }

    public enum GateStatus {
        ORACLE_REQUIRED
    }

    public enum ScenarioOutcome {
        SYNTHETIC_STRUCTURE_ACCEPTED,
        FAIL_CLOSED
    }

    public enum FailClosedReason {
        PROFILE_BINDING_MISMATCH,
        ORACLE_MISSING,
        AUTHORIZATION_MISSING,
        SOURCE_INSTANCE_MISSING,
        TENANT_SCOPE_MISSING,
        RESERVED_SCOPE_VALUE,
        NON_SYNTHETIC_SCOPE_VALUE,
        ROOT_DRIFT,
        PATH_DRIFT,
        FIELD_SCOPE_MISMATCH,
        ROOT_SCALAR_ROLE_DRIFT,
        FIELD_TYPE_MISMATCH,
        FILTER_CONTRACT_DRIFT,
        RELATIONSHIP_INFERENCE_DRIFT,
        ABSENCE_POLICY_DRIFT,
        SOURCE_KEY_PATH_MISMATCH,
        SOURCE_KEY_TAGGING_MISSING,
        SOURCE_KEY_MISSING,
        SOURCE_KEY_NULL,
        SOURCE_KEY_TYPE_MISMATCH,
        SOURCE_KEY_VALUE_INVALID,
        SOURCE_KEY_COLLISION,
        PRESENCE_MODEL_INCOMPLETE,
        PAGINATION_SEMANTICS_DRIFT,
        TERMINALITY_INCOMPLETE,
        BYTE_LIMIT_EXCEEDED,
        ROW_LIMIT_EXCEEDED,
        PAGE_LIMIT_EXCEEDED,
        PAGE_SIZE_EXCEEDED,
        DEPTH_LIMIT_EXCEEDED,
        PATH_LIMIT_EXCEEDED,
        NODE_LIMIT_EXCEEDED,
        INVALID_TIMESTAMP,
        TEMPORAL_STATE_MISMATCH,
        TIMEZONE_UNCONFIRMED,
        TIMEZONE_MISMATCH,
        TEMPORAL_PRECEDENCE_DRIFT,
        STATUS_SEMANTICS_DRIFT,
        CARDINALITY_DRIFT,
        OBSERVATION_COUNT_MISMATCH,
        LOGICAL_ENTITY_LIMIT_EXCEEDED,
        EXPANSION_SEMANTICS_DRIFT,
        CHILD_IDENTITY_CONFLICT,
        CHILD_KEY_PATH_MISMATCH,
        CHILD_KEY_TAGGING_MISSING,
        CHILD_ATTRIBUTE_ASYMMETRY,
        ORDER_PARITY_VIOLATION,
        GRAPHQL_PAGE_SIZE_EXCEEDED,
        GRAPHQL_CURSOR_MISSING,
        GRAPHQL_CURSOR_REPEATED,
        GRAPHQL_CURSOR_CYCLE,
        UNSUPPORTED_EVIDENCE_CLASSIFICATION
    }

    public enum FoundationOutcome {
        FOUNDATION_OFFLINE_COMPLETE_Q01_PENDING_ORACLES,
        FOUNDATION_FAIL_CLOSED
    }
}
