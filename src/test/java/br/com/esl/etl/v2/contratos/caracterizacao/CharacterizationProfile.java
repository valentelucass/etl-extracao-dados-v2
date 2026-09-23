package br.com.esl.etl.v2.contratos.caracterizacao;

import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.AbsencePolicy;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.AuthorizationRequirement;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.CharacterizationCheck;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.ChildKeyDomain;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.ChildKind;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.ChildPolicy;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.Entity;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.ExecutionValueRequirement;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.ExpansionPolicy;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.FilterRole;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.GateStatus;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.LogicalGrain;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.OracleKind;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.OrderingRole;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.PaginationKind;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.PerSemantics;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.Presence;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.ProfileStatus;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.RootCardinality;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.SourceKeyDomain;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.SourceKind;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.Stage;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.StatusMode;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.TemporalTranslation;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.TerminalCondition;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.TimezoneRequirement;
import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.WireType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Contrato esperado, imutável e provider-neutral, de uma futura caracterização. */
public record CharacterizationProfile(
        String schemaVersion,
        String profileId,
        String fixtureResource,
        String fixtureFingerprint,
        Set<Stage> stages,
        Entity entity,
        SourceKind sourceKind,
        OracleKind oracleKind,
        AuthorizationRequirement authorizationRequirement,
        ContractReference contract,
        SourceKeyContract sourceKey,
        List<BusinessAliasContract> businessAliases,
        List<ChildIdentityContract> childIdentities,
        Set<String> relationshipCandidatePaths,
        ScopeContract scope,
        RootGrainContract rootGrain,
        PathScopeContract pathScopes,
        AbsencePolicy absencePolicy,
        List<FilterContract> filters,
        PaginationContract pagination,
        OrderingContract ordering,
        TemporalContract temporal,
        StatusContract status,
        CharacterizationLimits limits,
        List<FieldContract> fieldContracts,
        Set<String> expectedPaths,
        Set<CharacterizationCheck> requiredChecks,
        ProfileStatus profileStatus,
        GateStatus gateStatus) {

    private static final Pattern PROFILE_ID = Pattern.compile("[A-Z][A-Z0-9_]{2,95}");
    private static final Pattern RESOURCE =
            Pattern.compile("/contracts/v2-012/fixtures/[a-z0-9-]+\\.synthetic\\.json");

    public CharacterizationProfile {
        if (!"V2_012_PROFILE_V1".equals(schemaVersion)) {
            throw new IllegalArgumentException("A versão do perfil V2-012 é inválida.");
        }
        if (profileId == null || !PROFILE_ID.matcher(profileId).matches()) {
            throw new IllegalArgumentException("O identificador do perfil é inválido.");
        }
        if (fixtureResource == null || !RESOURCE.matcher(fixtureResource).matches()) {
            throw new IllegalArgumentException("O recurso de fixture do perfil é inválido.");
        }
        if (fixtureFingerprint == null
                || !ContractReference.SHA256.matcher(fixtureFingerprint).matches()) {
            throw new IllegalArgumentException("O fingerprint da fixture do perfil é inválido.");
        }
        stages = immutableEnumSet(stages, Stage.class, "Os estágios V2-012 são obrigatórios.");
        if (!stages.equals(EnumSet.allOf(Stage.class))) {
            throw new IllegalArgumentException("O perfil deve modelar V2-012a, V2-012b e V2-012c.");
        }
        entity = Objects.requireNonNull(entity, "A entidade do perfil é obrigatória.");
        sourceKind = Objects.requireNonNull(sourceKind, "A origem do perfil é obrigatória.");
        oracleKind = Objects.requireNonNull(oracleKind, "O oráculo do perfil é obrigatório.");
        authorizationRequirement =
                Objects.requireNonNull(
                        authorizationRequirement, "A autorização futura é obrigatória.");
        contract = Objects.requireNonNull(contract, "O binding de contrato é obrigatório.");
        sourceKey = Objects.requireNonNull(sourceKey, "A source key é obrigatória.");
        businessAliases = sortedAliases(businessAliases);
        childIdentities = sortedChildren(childIdentities);
        relationshipCandidatePaths =
                sortedStringsAllowEmpty(
                        relationshipCandidatePaths,
                        "Os paths candidatos de relação são inválidos.");
        scope = Objects.requireNonNull(scope, "O escopo do perfil é obrigatório.");
        rootGrain = Objects.requireNonNull(rootGrain, "A raiz e o grão são obrigatórios.");
        pathScopes = Objects.requireNonNull(pathScopes, "Os escopos de path são obrigatórios.");
        absencePolicy =
                Objects.requireNonNull(absencePolicy, "A política de ausência é obrigatória.");
        filters = sortedFilters(filters);
        pagination = Objects.requireNonNull(pagination, "A paginação é obrigatória.");
        ordering = Objects.requireNonNull(ordering, "A ordenação é obrigatória.");
        temporal = Objects.requireNonNull(temporal, "O contrato temporal é obrigatório.");
        status = Objects.requireNonNull(status, "O contrato de status é obrigatório.");
        limits = Objects.requireNonNull(limits, "Os limites são obrigatórios.");
        fieldContracts = sortedFieldContracts(fieldContracts);
        expectedPaths = sortedStrings(expectedPaths, "Os paths esperados são obrigatórios.");
        requiredChecks =
                immutableEnumSet(
                        requiredChecks,
                        CharacterizationCheck.class,
                        "Os checks obrigatórios são necessários.");
        profileStatus = Objects.requireNonNull(profileStatus, "O status do perfil é obrigatório.");
        gateStatus = Objects.requireNonNull(gateStatus, "O gate do perfil é obrigatório.");
        if (profileStatus != ProfileStatus.PREPARED_NOT_EXECUTED
                || gateStatus != GateStatus.ORACLE_REQUIRED) {
            throw new IllegalArgumentException("A fundação não pode declarar perfil executado.");
        }
        if (sourceKind == SourceKind.DATA_EXPORT && oracleKind != OracleKind.DATA_EXPORT_PROVIDER
                || sourceKind == SourceKind.GRAPHQL && oracleKind != OracleKind.GRAPHQL_PROVIDER) {
            throw new IllegalArgumentException("O oráculo não corresponde ao source kind.");
        }
        if (!expectedPaths.contains(sourceKey.path())) {
            throw new IllegalArgumentException("A source key deve integrar os paths esperados.");
        }
        if (!fieldContracts.stream()
                .map(FieldContract::path)
                .collect(java.util.stream.Collectors.toUnmodifiableSet())
                .equals(expectedPaths)) {
            throw new IllegalArgumentException(
                    "Os contratos de campo devem cobrir exatamente os paths esperados.");
        }
        final TreeSet<String> scopedPaths = new TreeSet<>(pathScopes.recordPaths());
        scopedPaths.addAll(pathScopes.connectionPaths());
        if (!Collections.disjoint(pathScopes.recordPaths(), pathScopes.connectionPaths())
                || !scopedPaths.equals(expectedPaths)) {
            throw new IllegalArgumentException(
                    "Os escopos de path devem ser disjuntos e cobrir exatamente o contrato.");
        }
        if (status.mode() != StatusMode.NOT_APPLICABLE && !expectedPaths.contains(status.path())) {
            throw new IllegalArgumentException("O path de status deve integrar o contrato.");
        }
        for (final ChildIdentityContract child : childIdentities) {
            if (!expectedPaths.contains(child.path())
                    || !expectedPaths.containsAll(child.attributePaths())) {
                throw new IllegalArgumentException(
                        "As identidades de filho devem integrar os paths esperados.");
            }
        }
        if (!expectedPaths.containsAll(rootGrain.rootScalarPaths())
                || !expectedPaths.containsAll(temporal.timestampPaths())
                || !expectedPaths.containsAll(relationshipCandidatePaths)) {
            throw new IllegalArgumentException(
                    "Os paths de raiz e temporais devem integrar os paths esperados.");
        }
    }

    public String profileFingerprint() {
        return CharacterizationFingerprint.sha256(this);
    }

    private static List<BusinessAliasContract> sortedAliases(
            final List<BusinessAliasContract> values) {
        final List<BusinessAliasContract> aliases =
                new ArrayList<>(Objects.requireNonNull(values, "Os aliases são obrigatórios."));
        aliases.sort(Comparator.comparing(BusinessAliasContract::path));
        if (aliases.stream().map(BusinessAliasContract::path).distinct().count()
                != aliases.size()) {
            throw new IllegalArgumentException("Há alias de negócio duplicado.");
        }
        return List.copyOf(aliases);
    }

    private static List<FilterContract> sortedFilters(final List<FilterContract> values) {
        final List<FilterContract> result =
                new ArrayList<>(Objects.requireNonNull(values, "Os filtros são obrigatórios."));
        result.sort(Comparator.comparing(FilterContract::path));
        if (result.stream().map(FilterContract::path).distinct().count() != result.size()) {
            throw new IllegalArgumentException("Há filtro duplicado no perfil.");
        }
        return List.copyOf(result);
    }

    private static List<ChildIdentityContract> sortedChildren(
            final List<ChildIdentityContract> values) {
        final List<ChildIdentityContract> result =
                new ArrayList<>(
                        Objects.requireNonNull(
                                values, "As identidades de filho são obrigatórias."));
        result.sort(Comparator.comparing(ChildIdentityContract::kind));
        if (result.stream().map(ChildIdentityContract::kind).distinct().count() != result.size()) {
            throw new IllegalArgumentException("Há identidade de filho duplicada no perfil.");
        }
        return List.copyOf(result);
    }

    private static List<FieldContract> sortedFieldContracts(final List<FieldContract> values) {
        final List<FieldContract> result =
                new ArrayList<>(
                        Objects.requireNonNull(values, "Os contratos de campo são obrigatórios."));
        result.sort(Comparator.comparing(FieldContract::path));
        if (result.isEmpty()
                || result.stream().anyMatch(Objects::isNull)
                || result.stream().map(FieldContract::path).distinct().count() != result.size()) {
            throw new IllegalArgumentException("Os contratos de campo são inválidos.");
        }
        return List.copyOf(result);
    }

    private static Set<String> sortedStrings(final Set<String> values, final String message) {
        final TreeSet<String> sorted = new TreeSet<>(Objects.requireNonNull(values, message));
        if (sorted.isEmpty()
                || sorted.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException(message);
        }
        return Collections.unmodifiableSet(sorted);
    }

    private static Set<String> sortedStringsAllowEmpty(
            final Set<String> values, final String message) {
        final TreeSet<String> sorted = new TreeSet<>(Objects.requireNonNull(values, message));
        if (sorted.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException(message);
        }
        return Collections.unmodifiableSet(sorted);
    }

    private static <E extends Enum<E>> Set<E> immutableEnumSet(
            final Set<E> values, final Class<E> type, final String message) {
        Objects.requireNonNull(values, message);
        if (values.isEmpty() || values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(message);
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(values));
    }

    public record ContractReference(
            String contractId,
            String contractVersion,
            String documentReference,
            String contractFingerprint,
            String identityFingerprint) {

        private static final Pattern TECHNICAL = Pattern.compile("[a-z0-9][a-z0-9._-]{2,127}");
        private static final Pattern VERSION =
                Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}\\.[a-z0-9.-]+");
        private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

        public ContractReference {
            if (contractId == null
                    || !TECHNICAL.matcher(contractId).matches()
                    || documentReference == null
                    || !TECHNICAL.matcher(documentReference).matches()
                    || contractVersion == null
                    || !VERSION.matcher(contractVersion).matches()
                    || contractFingerprint == null
                    || !SHA256.matcher(contractFingerprint).matches()
                    || identityFingerprint == null
                    || !SHA256.matcher(identityFingerprint).matches()) {
                throw new IllegalArgumentException("O binding canônico do contrato é inválido.");
            }
        }
    }

    public record SourceKeyContract(
            String path, Set<WireType> wireTypes, boolean typeTagged, SourceKeyDomain domain) {

        public SourceKeyContract {
            path = technicalPath(path);
            wireTypes =
                    immutableEnumSet(wireTypes, WireType.class, "Os wire types são obrigatórios.");
            domain = Objects.requireNonNull(domain, "O domínio da source key é obrigatório.");
            if (!typeTagged) {
                throw new IllegalArgumentException(
                        "Toda source key da fundação deve ser type-tagged.");
            }
            if ((domain == SourceKeyDomain.CANONICAL_INTEGER
                                    || domain == SourceKeyDomain.POSITIVE_INTEGER)
                            && !wireTypes.equals(Set.of(WireType.INTEGER))
                    || domain == SourceKeyDomain.CANONICAL_INTEGER_OR_NON_BLANK_STRING
                            && !wireTypes.equals(Set.of(WireType.INTEGER, WireType.STRING))) {
                throw new IllegalArgumentException(
                        "O domínio e os wire types da source key divergem.");
            }
        }
    }

    public record ChildIdentityContract(
            ChildKind kind,
            String path,
            Set<WireType> wireTypes,
            boolean typeTagged,
            ChildKeyDomain domain,
            Set<String> attributePaths) {

        public ChildIdentityContract {
            kind = Objects.requireNonNull(kind, "O tipo de filho é obrigatório.");
            path = technicalPath(path);
            wireTypes =
                    immutableEnumSet(
                            wireTypes, WireType.class, "Os wire types do filho são obrigatórios.");
            domain = Objects.requireNonNull(domain, "O domínio da chave de filho é obrigatório.");
            attributePaths =
                    sortedStringsAllowEmpty(attributePaths, "Os atributos de filho são inválidos.");
            if (!typeTagged || attributePaths.contains(path)) {
                throw new IllegalArgumentException("A identidade de filho é inválida.");
            }
            if (domain == ChildKeyDomain.POSITIVE_INTEGER
                            && !wireTypes.equals(Set.of(WireType.INTEGER))
                    || domain == ChildKeyDomain.FIXED_44_DIGIT_STRING
                            && !wireTypes.equals(Set.of(WireType.STRING))) {
                throw new IllegalArgumentException("O domínio e os wire types do filho divergem.");
            }
        }
    }

    /** Tipo e presença esperados por path, sem transportar qualquer valor de negócio. */
    public record FieldContract(
            String path, Set<WireType> wireTypes, Set<Presence> presenceStates) {

        public FieldContract {
            path = technicalPath(path);
            wireTypes =
                    immutableEnumSet(
                            wireTypes, WireType.class, "Os wire types do campo são obrigatórios.");
            presenceStates =
                    immutableEnumSet(
                            presenceStates,
                            Presence.class,
                            "Os estados de presença do campo são obrigatórios.");
        }
    }

    public record BusinessAliasContract(String path, String name, String role) {

        private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_]{0,63}");

        public BusinessAliasContract {
            path = technicalPath(path);
            if (name == null || !NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("O nome do alias é inválido.");
            }
            if (!"BUSINESS_ALIAS_NEVER_SOURCE_KEY".equals(role)) {
                throw new IllegalArgumentException("O papel do alias é inválido.");
            }
        }
    }

    public record ScopeContract(
            ExecutionValueRequirement sourceInstance,
            ExecutionValueRequirement tenantScope,
            Set<String> forbiddenValues) {

        public ScopeContract {
            sourceInstance =
                    Objects.requireNonNull(sourceInstance, "source_instance é obrigatório.");
            tenantScope = Objects.requireNonNull(tenantScope, "tenant_scope é obrigatório.");
            forbiddenValues =
                    sortedStrings(forbiddenValues, "Os sentinels de escopo são obrigatórios.");
            if (sourceInstance != ExecutionValueRequirement.REQUIRED_AT_EXECUTION
                    || tenantScope != ExecutionValueRequirement.REQUIRED_AT_EXECUTION
                    || !forbiddenValues.equals(Set.of("DEFAULT", "GLOBAL", "SINGLETON"))) {
                throw new IllegalArgumentException("O contrato de escopo explícito diverge.");
            }
        }
    }

    public record PathScopeContract(Set<String> recordPaths, Set<String> connectionPaths) {

        public PathScopeContract {
            recordPaths =
                    sortedStringsAllowEmpty(recordPaths, "Os paths de registro são inválidos.");
            connectionPaths =
                    sortedStringsAllowEmpty(connectionPaths, "Os paths de conexão são inválidos.");
            if (recordPaths.isEmpty()) {
                throw new IllegalArgumentException("O perfil deve possuir paths de registro.");
            }
        }
    }

    public record RootGrainContract(
            String recordRoot,
            Set<String> acceptedRecordRoots,
            LogicalGrain logicalGrain,
            RootCardinality cardinality,
            ExpansionPolicy expansionPolicy,
            ChildPolicy childPolicy,
            Set<String> rootScalarPaths) {

        public RootGrainContract {
            recordRoot = technicalPath(recordRoot);
            acceptedRecordRoots =
                    sortedStrings(acceptedRecordRoots, "As raízes aceitas são obrigatórias.");
            for (final String acceptedRoot : acceptedRecordRoots) {
                technicalPath(acceptedRoot);
            }
            if (!acceptedRecordRoots.contains(recordRoot)) {
                throw new IllegalArgumentException(
                        "A raiz canônica deve integrar as raízes aceitas.");
            }
            logicalGrain = Objects.requireNonNull(logicalGrain, "O grão lógico é obrigatório.");
            cardinality = Objects.requireNonNull(cardinality, "A cardinalidade é obrigatória.");
            expansionPolicy = Objects.requireNonNull(expansionPolicy, "A expansão é obrigatória.");
            childPolicy =
                    Objects.requireNonNull(childPolicy, "A política de filhos é obrigatória.");
            rootScalarPaths =
                    sortedStringsAllowEmpty(rootScalarPaths, "Os escalares da raiz são inválidos.");
        }
    }

    public record FilterContract(
            String path, FilterRole role, boolean watermark, Boolean requiredBooleanValue) {

        public FilterContract {
            if (path == null || path.isBlank() || !path.matches("[a-z][a-z0-9_.]{1,127}")) {
                throw new IllegalArgumentException("O filtro técnico é inválido.");
            }
            role = Objects.requireNonNull(role, "O papel do filtro é obrigatório.");
            if (watermark) {
                throw new IllegalArgumentException("A fundação não promove filtros a watermark.");
            }
        }
    }

    public record PaginationContract(
            PaginationKind kind,
            int maximumPageSize,
            TerminalCondition terminalCondition,
            PerSemantics perSemantics,
            boolean shortPageIsTerminal,
            boolean completenessProven,
            boolean snapshotProven) {

        public PaginationContract {
            kind = Objects.requireNonNull(kind, "O tipo de paginação é obrigatório.");
            terminalCondition =
                    Objects.requireNonNull(terminalCondition, "A condição terminal é obrigatória.");
            perSemantics =
                    Objects.requireNonNull(perSemantics, "A semântica de per é obrigatória.");
            if (maximumPageSize <= 0
                    || shortPageIsTerminal
                    || completenessProven
                    || snapshotProven) {
                throw new IllegalArgumentException(
                        "A paginação da fundação contém prova indevida.");
            }
        }
    }

    public record OrderingContract(String expression, OrderingRole role) {

        public OrderingContract {
            role = Objects.requireNonNull(role, "O papel da ordenação é obrigatório.");
            if (role == OrderingRole.ABSENT) {
                if (expression != null) {
                    throw new IllegalArgumentException("Ordenação ausente não aceita expressão.");
                }
            } else if (expression == null
                    || !expression.toLowerCase(Locale.ROOT).matches("[a-z_]+ asc")) {
                throw new IllegalArgumentException("A expressão de ordenação é inválida.");
            }
        }
    }

    public record TemporalContract(
            Set<String> timestampPaths,
            List<String> precedence,
            String timezone,
            TimezoneRequirement timezoneRequirement,
            TemporalTranslation translation) {

        public TemporalContract {
            timestampPaths =
                    sortedStringsAllowEmpty(timestampPaths, "Os paths temporais são inválidos.");
            precedence =
                    List.copyOf(
                            Objects.requireNonNull(
                                    precedence, "A precedência temporal é obrigatória."));
            if (precedence.stream().anyMatch(value -> value == null || value.isBlank())
                    || precedence.stream().distinct().count() != precedence.size()
                    || !timestampPaths.containsAll(precedence)) {
                throw new IllegalArgumentException("A precedência temporal é inválida.");
            }
            timezoneRequirement =
                    Objects.requireNonNull(
                            timezoneRequirement, "A exigência de timezone é obrigatória.");
            translation = Objects.requireNonNull(translation, "A tradução temporal é obrigatória.");
            if (timezoneRequirement == TimezoneRequirement.NOT_APPLICABLE) {
                if (timezone != null
                        || translation != TemporalTranslation.ABSENT_NO_TEMPORAL_FIELD
                        || !timestampPaths.isEmpty()
                        || !precedence.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Timezone não aplicável contém semântica temporal.");
                }
            } else if (!"America/Sao_Paulo".equals(timezone)
                    || timestampPaths.isEmpty()
                    || precedence.isEmpty()) {
                throw new IllegalArgumentException("O timezone explícito do perfil é inválido.");
            }
        }
    }

    public record StatusContract(
            String path,
            StatusMode mode,
            String reference,
            String policyFingerprint,
            boolean unknownValuesAllowed) {

        private static final Pattern REFERENCE = Pattern.compile("[a-z0-9][a-z0-9._-]{2,127}");

        public StatusContract {
            mode = Objects.requireNonNull(mode, "O modo de status é obrigatório.");
            if (mode == StatusMode.NOT_APPLICABLE) {
                if (path != null
                        || reference != null
                        || policyFingerprint != null
                        || unknownValuesAllowed) {
                    throw new IllegalArgumentException(
                            "O status não aplicável possui configuração.");
                }
            } else {
                path = technicalPath(path);
                if (reference == null
                        || !REFERENCE.matcher(reference).matches()
                        || policyFingerprint == null
                        || !ContractReference.SHA256.matcher(policyFingerprint).matches()) {
                    throw new IllegalArgumentException("A referência de status é inválida.");
                }
                if ((mode == StatusMode.CLOSED_CATALOG && unknownValuesAllowed)
                        || (mode == StatusMode.KNOWN_PRECEDENCE_UNKNOWN_PRESERVED
                                && !unknownValuesAllowed)) {
                    throw new IllegalArgumentException("A política de status é inconsistente.");
                }
            }
        }
    }

    public record CharacterizationLimits(
            long maximumBytes,
            int maximumRows,
            int maximumPages,
            int maximumDepth,
            int maximumPaths,
            int maximumNodes) {

        public CharacterizationLimits {
            if (maximumBytes <= 0
                    || maximumRows <= 0
                    || maximumPages <= 0
                    || maximumDepth <= 0
                    || maximumPaths <= 0
                    || maximumNodes <= 0) {
                throw new IllegalArgumentException(
                        "Todo limite de caracterização deve ser positivo.");
            }
        }
    }

    private static String technicalPath(final String value) {
        if (value == null
                || !value.matches(
                        "/(?:[A-Za-z_][A-Za-z0-9_]*|\\*)(?:/(?:[A-Za-z_][A-Za-z0-9_]*|\\*))*")) {
            throw new IllegalArgumentException("O path técnico é inválido.");
        }
        return value;
    }
}
