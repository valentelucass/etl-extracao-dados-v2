package br.com.esl.etl.v2.arquitetura;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.bootstrap.Main;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.BaseStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

public class ArchitectureRulesTest {

    private static final Pattern REPOSITORY_ROLE =
            Pattern.compile(
                    "(?:Repository|Repositorio|DAO|Dao|Store|DataStore|"
                            + "Persistence(?:Adapter|Port|Gateway))$");
    private static final Pattern PERSISTENCE_PACKAGE =
            Pattern.compile(
                    "(?i)(?:^|\\.)(?:repository|repositories|repositorio|repositorios|"
                            + "persistencia|persistence)(?:\\.|$)");
    private static final Pattern BOUNDED_REPOSITORY_METHOD =
            Pattern.compile("(?i).*(?:Page|Pagina|Batch|Lote|Chunk|Slice|Limited|Limitado|Top).*?");
    private static final Pattern TRAVERSAL_OR_FALLBACK_FILE =
            Pattern.compile(
                    "(?i)(streamer|traversal|traverser|travessia|paginator|pagination|paginador|"
                            + "fallback)");
    private static final Pattern FALLBACK_METHOD =
            Pattern.compile("(?i)\\b[A-Za-z0-9_]*fallback[A-Za-z0-9_]*\\s*\\(");
    private static final Pattern MUTABLE_CONTAINER_DECLARATION =
            Pattern.compile(
                    "\\b([A-Za-z_$][A-Za-z0-9_$]*)\\s*=\\s*new\\s+(?:java\\.util\\.)?"
                            + "(?:ArrayList|LinkedList|Vector|Stack|ArrayDeque|PriorityQueue|"
                            + "HashMap|LinkedHashMap|TreeMap|ConcurrentHashMap|HashSet|"
                            + "LinkedHashSet|TreeSet)\\s*[<(]");
    private static final Pattern COLLECTION_VARIABLE_DECLARATION =
            Pattern.compile(
                    "\\b(?:java\\.util\\.)?(?:Collection|List|Set|Map|Deque|Queue|"
                            + "ArrayList|LinkedList|Vector|Stack|ArrayDeque|PriorityQueue|"
                            + "HashMap|LinkedHashMap|TreeMap|ConcurrentHashMap|HashSet|"
                            + "LinkedHashSet|TreeSet)(?:\\s*<[^;=(){}]+>)?\\s+"
                            + "([A-Za-z_$][A-Za-z0-9_$]*)\\s*(?:=|;)");
    private static final Pattern LOOP_START = Pattern.compile("\\b(?:for|while)\\s*\\(");
    private static final Pattern DO_LOOP_START = Pattern.compile("\\bdo\\s*\\{");
    private static final Pattern FOR_EACH_START = Pattern.compile("\\.forEach\\s*\\(");
    private static final Pattern EXPLICIT_FINITE_LOOP =
            Pattern.compile("(?s)(?:List|Set)\\.of\\s*\\(|\\.values\\s*\\(\\)");
    private static final Pattern BOUND_PARAMETER_NAME =
            Pattern.compile(
                    "(?i)(?:limit|max|maximum|pageSize|batchSize|chunkSize|sliceSize|per|top|count)");

    private static final Map<String, String> BOUNDED_COLLECTION_APIS =
            Map.ofEntries(
                    Map.entry(
                            "br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageRequest#filters()",
                            "Filtros tipados de uma única requisição."),
                    Map.entry(
                            "br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageRequest#orderBy()",
                            "Ordenação fixa de uma única requisição."),
                    Map.entry(
                            "br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageResponse#records()",
                            "Uma única resposta limitada por bytes e validada pelo page size."),
                    Map.entry(
                            "br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPayloadFieldProfile#jsonTypes()",
                            "Tipos distintos de um campo dentro de uma única página limitada."),
                    Map.entry(
                            "br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPayloadFieldProfile#textualFormats()",
                            "Formatos distintos de um campo dentro de uma única página limitada."),
                    Map.entry(
                            "br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPayloadProfile#fields()",
                            "Perfil sanitizado de uma única página limitada."),
                    Map.entry(
                            "br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate#defaultOrderBy()",
                            "Configuração imutável e finita do template."),
                    Map.entry(
                            "br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplateInfo#fields()",
                            "Metadados de um único documento /info limitado por bytes."),
                    Map.entry(
                            "br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplateInfo#filters()",
                            "Metadados de um único documento /info limitado por bytes."),
                    Map.entry(
                            "br.com.esl.etl.v2.plataforma.fonte.dataexport."
                                    + "DataExportTemplateInfoParser$ParsedTemplateInfo#fields()",
                            "Campos parseados de um único documento /info limitado por bytes."),
                    Map.entry(
                            "br.com.esl.etl.v2.plataforma.fonte.dataexport."
                                    + "DataExportTemplateInfoParser$ParsedTemplateInfo#filters()",
                            "Filtros parseados de um único documento /info limitado por bytes."),
                    Map.entry(
                            "br.com.esl.etl.v2.plataforma.contrato.ContractMetadata#elements()",
                            "Elementos sanitizados de um documento limitado a 4.096 paths."),
                    Map.entry(
                            "br.com.esl.etl.v2.plataforma.contrato.ContractResponse#fields()",
                            "Shape sanitizado de uma única resposta limitado a 4.096 paths."),
                    Map.entry(
                            "br.com.esl.etl.v2.plataforma.contrato.ContractResponse$Field#jsonTypes()",
                            "Enumeração fechada de tipos JSON de um único path."),
                    Map.entry(
                            "br.com.esl.etl.v2.plataforma.contrato.ContractDiff#changes()",
                            "Diff classificado limitado a 1.024 mudanças."),
                    Map.entry(
                            "br.com.esl.etl.v2.plataforma.contrato."
                                    + "ContractCompatibilityPolicy#allowances()",
                            "Allowlist exata limitada a 128 regras."),
                    Map.entry(
                            "br.com.esl.etl.v2.plataforma.contrato."
                                    + "ContractCompatibilityPolicy#opaquePaths()",
                            "Containers opacos explícitos limitados a 128 paths."),
                    Map.entry(
                            "br.com.esl.etl.v2.plataforma.persistencia.staging.StagingBatch#records()",
                            "Um único lote de staging com limite explícito e teto absoluto."));

    @Test
    void productiveCollectionApisMustHaveAnExplicitBoundedContract() throws Exception {
        final Set<String> collectionApis = productiveCollectionApis(productionClasses());
        final List<String> violations =
                collectionApis.stream()
                        .filter(api -> !BOUNDED_COLLECTION_APIS.containsKey(api))
                        .sorted()
                        .toList();
        final List<String> staleAllowlistEntries =
                BOUNDED_COLLECTION_APIS.keySet().stream()
                        .filter(api -> !collectionApis.contains(api))
                        .sorted()
                        .toList();

        assertTrue(
                violations.isEmpty(),
                () -> "APIs produtivas com coleção sem limite explícito: " + violations);
        assertTrue(
                staleAllowlistEntries.isEmpty(),
                () -> "Allowlist arquitetural obsoleta: " + staleAllowlistEntries);
        assertTrue(
                BOUNDED_COLLECTION_APIS.values().stream().noneMatch(String::isBlank),
                "Todo limite permitido precisa de justificativa revisável.");
    }

    @Test
    void persistenceAdaptersMustNotMaterializeAKeyUniverse() throws Exception {
        final List<String> violations = findRepositoryMaterialization(productionClasses());

        assertTrue(
                violations.isEmpty(),
                () -> "Persistência materializa coleção/universo de chaves: " + violations);
    }

    @Test
    void identityPolicyMustRemainSingleObservationWithoutInMemoryRegistryOrJdbc() throws Exception {
        final Pattern forbidden =
                Pattern.compile(
                        "(?s)(?:new\\s+(?:ArrayList|HashMap|HashSet|TreeMap|TreeSet)\\s*[<(]"
                                + "|java\\.util\\.(?:Map|Set|Collection)"
                                + "|import\\s+java\\.util\\.(?:Map|Set|Collection)"
                                + "|java\\.sql\\.|javax\\.sql\\.|JdbcTemplate|DataSource)");
        final List<String> violations =
                mainJavaSources().stream()
                        .filter(source -> source.name().contains("/plataforma/identidade/"))
                        .filter(source -> forbidden.matcher(source.content()).find())
                        .map(SourceFile::name)
                        .sorted()
                        .toList();

        assertTrue(
                violations.isEmpty(),
                () ->
                        "Identidade/crosswalk contém registry em memória ou acesso JDBC: "
                                + violations);
    }

    @Test
    void traversalAndFallbackCodeMustNotAccumulateTheWholeExecution() throws Exception {
        final List<String> violations = findSourceAccumulatorViolations(mainJavaSources());

        assertTrue(
                violations.isEmpty(),
                () -> "Travessia/fallback contém acumulador de vida da execução: " + violations);
    }

    @Test
    void productiveLoggingMustUseTheSingleStructuredAdapter() throws Exception {
        final List<String> violations =
                mainJavaSources().stream()
                        .filter(source -> source.content().contains("LoggerFactory"))
                        .filter(source -> !source.name().endsWith("/Slf4jStructuredLogSink.java"))
                        .map(SourceFile::name)
                        .sorted()
                        .toList();

        assertTrue(
                violations.isEmpty(),
                () -> "LoggerFactory fora do adapter estruturado: " + violations);

        final List<String> unboundedAdapters =
                mainJavaSources().stream()
                        .filter(source -> source.content().contains("new Slf4jStructuredLogSink"))
                        .filter(source -> !source.content().contains("new BoundedStructuredLogger"))
                        .map(SourceFile::name)
                        .sorted()
                        .toList();
        assertTrue(
                unboundedAdapters.isEmpty(),
                () ->
                        "Adapter estruturado produtivo sem budget de eventos/bytes: "
                                + unboundedAdapters);
    }

    @Test
    void observabilityAndDataQualityMustNotUseOpenCardinalityMaps() throws Exception {
        final List<String> violations =
                mainJavaSources().stream()
                        .filter(
                                source ->
                                        source.name().contains("Observability")
                                                || source.name().contains("DataQuality")
                                                || source.name().contains("Metrics")
                                                || source.name().contains("Alert"))
                        .filter(
                                source ->
                                        source.content().contains("java.util.Map")
                                                || source.content().contains("ConcurrentHashMap"))
                        .map(SourceFile::name)
                        .sorted()
                        .toList();

        assertTrue(
                violations.isEmpty(),
                () -> "Observabilidade/DQ contém mapa de cardinalidade aberta: " + violations);
    }

    @Test
    void ruleDetectsDirectWrappedAndArrayCollectionApis() {
        final Set<String> apis =
                productiveCollectionApis(
                        List.of(
                                UnboundedApiFixture.class,
                                WrappedUnboundedApiFixture.class,
                                ArrayUnboundedApiFixture.class,
                                JsonArrayUnboundedApiFixture.class));

        assertEquals(4, apis.size());
    }

    @Test
    void ruleTerminatesForARecursiveGenericBoundThatIsNotACollection() {
        assertTrue(productiveCollectionApis(List.of(RecursiveGenericApiFixture.class)).isEmpty());
    }

    @Test
    void ruleIgnoresAnApiWhoseDeclaringTypeIsNotExternallyVisible() {
        assertTrue(productiveCollectionApis(List.of(HiddenCollectionApiFixture.class)).isEmpty());
    }

    @Test
    void ruleDetectsARepositoryThatKeepsAndReturnsAllKeys() {
        final List<String> violations =
                findRepositoryMaterialization(List.of(UniverseKeyRepository.class));

        assertEquals(2, violations.size());
    }

    @Test
    void ruleAllowsAnExplicitlyBoundedRepositoryBatch() {
        assertTrue(
                findRepositoryMaterialization(List.of(BoundedBatchPersistenceAdapterFixture.class))
                        .isEmpty());
    }

    @Test
    void ruleRejectsABatchNameWithoutAnExplicitLimit() {
        for (final Class<?> fixture :
                List.of(UnboundedBatchRepository.class, MisleadingNumericBatchRepository.class)) {
            assertEquals(1, findRepositoryMaterialization(List.of(fixture)).size());
        }
    }

    @Test
    void ruleDoesNotClassifyARestoreUtilityAsARepository() {
        assertTrue(findRepositoryMaterialization(List.of(BackupRestore.class)).isEmpty());
    }

    @Test
    void ruleDetectsAWholeTraversalAccumulatorIncludingArrayDeque() {
        final List<SourceFile> fixtures =
                List.of(
                        new SourceFile(
                                "WholeTraversalFallback.java",
                                "final class WholeTraversalFallback { void run() { "
                                        + "var allRecords = new java.util.ArrayDeque<String>(); "
                                        + "while (hasNext()) { allRecords.add(next()); } } }"),
                        new SourceFile(
                                "FieldTraversal.java",
                                "final class FieldTraversal { private final java.util.Deque<String> "
                                        + "allRecords = new java.util.ArrayDeque<>(); void run() { "
                                        + "while (hasNext()) { allRecords.addLast(next()); } } }"),
                        new SourceFile(
                                "NestedTraversal.java",
                                "final class NestedTraversal { void run() { var allRecords = "
                                        + "new java.util.ArrayList<String>(); try { while (hasNext()) "
                                        + "{ allRecords.add(next()); } } finally { close(); } } }"),
                        new SourceFile(
                                "ForEachTraversal.java",
                                "final class ForEachTraversal { private java.util.List<String> "
                                        + "allRecords; void run(java.util.stream.Stream<String> "
                                        + "records) { records.forEach(allRecords::add); } }"),
                        new SourceFile(
                                "LambdaTraversal.java",
                                "final class LambdaTraversal { private java.util.List<String> "
                                        + "allRecords; void run(java.util.stream.Stream<String> "
                                        + "records) { records.forEach(record -> "
                                        + "allRecords.add(record)); } }"),
                        new SourceFile(
                                "MillionPageTraversal.java",
                                "final class MillionPageTraversal { void run() { var allRecords = "
                                        + "new java.util.ArrayList<String>(); for (int page = 0; "
                                        + "hasNext() && page < 1000000; page++) { "
                                        + "allRecords.addAll(readPage(page)); } } }"));

        for (final SourceFile fixture : fixtures) {
            assertEquals(
                    1, findSourceAccumulatorViolations(List.of(fixture)).size(), fixture.name());
        }
    }

    @Test
    void ruleAllowsAFallbackContainerInsideAnExplicitlyFiniteLoop() {
        final SourceFile fixture =
                new SourceFile(
                        "FiniteFallback.java",
                        "final class FiniteFallback { void order() { "
                                + "var transports = new java.util.ArrayList<String>(); "
                                + "for (var transport : java.util.List.of(\"GET\", \"POST\")) { "
                                + "transports.add(transport); } } }");

        assertTrue(findSourceAccumulatorViolations(List.of(fixture)).isEmpty());
    }

    private static Set<String> productiveCollectionApis(final Collection<Class<?>> classes) {
        final java.util.LinkedHashSet<String> apis = new java.util.LinkedHashSet<>();
        for (final Class<?> type : classes) {
            for (final Method method : type.getDeclaredMethods()) {
                if (isProductiveApi(method) && isCollectionLike(method.getGenericReturnType())) {
                    apis.add(methodKey(method));
                }
            }
        }
        return Set.copyOf(apis);
    }

    private static List<String> findRepositoryMaterialization(final Collection<Class<?>> classes) {
        final List<String> violations = new ArrayList<>();
        for (final Class<?> type : classes) {
            if (!isRepositoryLike(type)) {
                continue;
            }
            for (final Field field : type.getDeclaredFields()) {
                if (!field.isSynthetic() && isCollectionLike(field.getGenericType())) {
                    violations.add(type.getName() + "#field:" + field.getName());
                }
            }
            for (final Method method : type.getDeclaredMethods()) {
                if (!method.isSynthetic()
                        && isCollectionLike(method.getGenericReturnType())
                        && (!BOUNDED_REPOSITORY_METHOD.matcher(method.getName()).matches()
                                || !hasExplicitBoundParameter(method))) {
                    violations.add(methodKey(method));
                }
            }
        }
        return violations.stream().sorted().toList();
    }

    private static boolean hasExplicitBoundParameter(final Method method) {
        return Stream.of(method.getParameters())
                .anyMatch(
                        parameter ->
                                isNumericBoundType(parameter.getType())
                                                && parameter.isNamePresent()
                                                && BOUND_PARAMETER_NAME
                                                        .matcher(parameter.getName())
                                                        .matches()
                                        || Pattern.compile(
                                                        "(?i).*(?:Limit|PageRequest|Pageable|"
                                                                + "BatchSize|PageSize).*")
                                                .matcher(parameter.getType().getSimpleName())
                                                .matches());
    }

    private static boolean isNumericBoundType(final Class<?> parameter) {
        return parameter == byte.class
                || parameter == short.class
                || parameter == int.class
                || parameter == long.class
                || parameter == Byte.class
                || parameter == Short.class
                || parameter == Integer.class
                || parameter == Long.class;
    }

    private static List<String> findSourceAccumulatorViolations(
            final Collection<SourceFile> sources) {
        final List<String> violations = new ArrayList<>();
        for (final SourceFile source : sources) {
            final boolean traversalOrFallback =
                    TRAVERSAL_OR_FALLBACK_FILE.matcher(source.name()).find()
                            || FALLBACK_METHOD.matcher(source.content()).find();
            if (!traversalOrFallback) {
                continue;
            }
            final List<MutableContainer> containers = mutableContainers(source.content());
            for (final LoopRegion loop : loopRegions(source.content())) {
                final String loopBody =
                        source.content().substring(loop.bodyStart(), loop.bodyEnd());
                for (final MutableContainer container : containers) {
                    if (container.declarationOffset() < loop.start()
                            && scopeContains(
                                    source.content(), container.declarationOffset(), loop.start())
                            && !loop.explicitlyFinite()
                            && (mutationOf(container.variable()).matcher(loopBody).find()
                                    || replacementOf(container.variable())
                                            .matcher(loopBody)
                                            .find())) {
                        violations.add(source.name() + ":loop-accumulator:" + container.variable());
                    }
                }
            }
            for (final ForEachRegion forEach : forEachRegions(source.content())) {
                final String body =
                        source.content().substring(forEach.bodyStart(), forEach.bodyEnd());
                for (final MutableContainer container : containers) {
                    if (container.declarationOffset() < forEach.start()
                            && scopeContains(
                                    source.content(),
                                    container.declarationOffset(),
                                    forEach.start())
                            && !isExplicitlyFiniteStatement(source.content(), forEach.start())
                            && (mutationOf(container.variable()).matcher(body).find()
                                    || methodReferenceMutationOf(container.variable())
                                            .matcher(body)
                                            .find())) {
                        violations.add(
                                source.name() + ":for-each-accumulator:" + container.variable());
                    }
                }
            }
        }
        return violations.stream().distinct().sorted().toList();
    }

    private static List<MutableContainer> mutableContainers(final String source) {
        final List<MutableContainer> containers = new ArrayList<>();
        final Matcher matcher = MUTABLE_CONTAINER_DECLARATION.matcher(source);
        while (matcher.find()) {
            containers.add(new MutableContainer(matcher.group(1), matcher.start()));
        }
        final Matcher declaredCollection = COLLECTION_VARIABLE_DECLARATION.matcher(source);
        while (declaredCollection.find()) {
            containers.add(
                    new MutableContainer(declaredCollection.group(1), declaredCollection.start()));
        }
        return List.copyOf(containers);
    }

    private static Pattern mutationOf(final String variable) {
        return Pattern.compile(
                "\\b"
                        + Pattern.quote(variable)
                        + "\\s*\\.\\s*(?:add|addAll|addFirst|addLast|put|putAll|offer|"
                        + "offerFirst|offerLast|push|compute|computeIfAbsent|merge)\\s*\\(");
    }

    private static Pattern replacementOf(final String variable) {
        return Pattern.compile(
                "(?s)\\b"
                        + Pattern.quote(variable)
                        + "\\s*=.*?(?:\\.toList\\s*\\(|\\.collect\\s*\\(|"
                        + "(?:List|Set|Map)\\.copyOf\\s*\\()");
    }

    private static Pattern methodReferenceMutationOf(final String variable) {
        return Pattern.compile(
                "\\b"
                        + Pattern.quote(variable)
                        + "::(?:add|addAll|addFirst|addLast|put|offer|offerLast)\\b");
    }

    private static List<LoopRegion> loopRegions(final String source) {
        final List<LoopRegion> loops = new ArrayList<>();
        final Matcher matcher = LOOP_START.matcher(source);
        while (matcher.find()) {
            final int openingParenthesis = source.indexOf('(', matcher.start());
            final int closingParenthesis = closingDelimiter(source, openingParenthesis, '(', ')');
            if (closingParenthesis < 0) {
                continue;
            }
            int bodyStart = closingParenthesis + 1;
            while (bodyStart < source.length()
                    && Character.isWhitespace(source.charAt(bodyStart))) {
                bodyStart++;
            }
            if (bodyStart >= source.length()) {
                continue;
            }
            final int bodyEnd;
            if (source.charAt(bodyStart) == '{') {
                final int closingBrace = closingDelimiter(source, bodyStart, '{', '}');
                if (closingBrace < 0) {
                    continue;
                }
                bodyEnd = closingBrace + 1;
            } else {
                final int semicolon = source.indexOf(';', bodyStart);
                if (semicolon < 0) {
                    continue;
                }
                bodyEnd = semicolon + 1;
            }
            final String header = source.substring(matcher.start(), bodyStart);
            loops.add(
                    new LoopRegion(
                            matcher.start(),
                            bodyStart,
                            bodyEnd,
                            EXPLICIT_FINITE_LOOP.matcher(header).find()));
        }
        final Matcher doLoop = DO_LOOP_START.matcher(source);
        while (doLoop.find()) {
            final int openingBrace = source.indexOf('{', doLoop.start());
            final int closingBrace = closingDelimiter(source, openingBrace, '{', '}');
            if (closingBrace >= 0) {
                loops.add(new LoopRegion(doLoop.start(), openingBrace, closingBrace + 1, false));
            }
        }
        return List.copyOf(loops);
    }

    private static List<ForEachRegion> forEachRegions(final String source) {
        final List<ForEachRegion> regions = new ArrayList<>();
        final Matcher matcher = FOR_EACH_START.matcher(source);
        while (matcher.find()) {
            final int openingParenthesis = source.indexOf('(', matcher.start());
            final int closingParenthesis = closingDelimiter(source, openingParenthesis, '(', ')');
            if (closingParenthesis >= 0) {
                regions.add(
                        new ForEachRegion(
                                matcher.start(), openingParenthesis + 1, closingParenthesis));
            }
        }
        return List.copyOf(regions);
    }

    private static boolean isExplicitlyFiniteStatement(final String source, final int offset) {
        final int statementStart = Math.max(source.lastIndexOf(';', offset) + 1, 0);
        return EXPLICIT_FINITE_LOOP.matcher(source.substring(statementStart, offset)).find();
    }

    private static int closingDelimiter(
            final String source, final int openingOffset, final char opening, final char closing) {
        int depth = 0;
        for (int offset = openingOffset; offset < source.length(); offset++) {
            if (source.charAt(offset) == opening) {
                depth++;
            } else if (source.charAt(offset) == closing && --depth == 0) {
                return offset;
            }
        }
        return -1;
    }

    private static boolean scopeContains(
            final String source, final int declarationOffset, final int useOffset) {
        final List<Integer> declarationBlocks = enclosingBlocks(source, declarationOffset);
        final List<Integer> useBlocks = enclosingBlocks(source, useOffset);
        if (declarationBlocks.size() > useBlocks.size()) {
            return false;
        }
        for (int index = 0; index < declarationBlocks.size(); index++) {
            if (!declarationBlocks.get(index).equals(useBlocks.get(index))) {
                return false;
            }
        }
        return true;
    }

    private static List<Integer> enclosingBlocks(final String source, final int offset) {
        final List<Integer> openings = new ArrayList<>();
        for (int index = 0; index < offset; index++) {
            if (source.charAt(index) == '{') {
                openings.add(index);
            } else if (source.charAt(index) == '}' && !openings.isEmpty()) {
                openings.remove(openings.size() - 1);
            }
        }
        return List.copyOf(openings);
    }

    private static boolean isProductiveApi(final Method method) {
        final int modifiers = method.getModifiers();
        if (method.isSynthetic()
                || isFiniteEnumValuesMethod(method)
                || (!Modifier.isPublic(modifiers) && !Modifier.isProtected(modifiers))) {
            return false;
        }
        Class<?> declaringType = method.getDeclaringClass();
        while (declaringType != null) {
            final int typeModifiers = declaringType.getModifiers();
            if (declaringType.getEnclosingClass() == null) {
                if (!Modifier.isPublic(typeModifiers)) {
                    return false;
                }
            } else if (!Modifier.isPublic(typeModifiers) && !Modifier.isProtected(typeModifiers)) {
                return false;
            }
            declaringType = declaringType.getEnclosingClass();
        }
        return true;
    }

    private static boolean isFiniteEnumValuesMethod(final Method method) {
        return method.getDeclaringClass().isEnum()
                && method.getName().equals("values")
                && method.getParameterCount() == 0;
    }

    private static boolean isCollectionLike(final Type type) {
        return isCollectionLike(
                type, Collections.newSetFromMap(new IdentityHashMap<Type, Boolean>()));
    }

    private static boolean isCollectionLike(final Type type, final Set<Type> visited) {
        if (!visited.add(type)) {
            return false;
        }
        if (type instanceof Class<?> rawClass) {
            if (ArrayNode.class.isAssignableFrom(rawClass)) {
                return true;
            }
            return !JsonNode.class.isAssignableFrom(rawClass)
                    && (rawClass.isArray()
                            || Collection.class.isAssignableFrom(rawClass)
                            || Map.class.isAssignableFrom(rawClass)
                            || Iterable.class.isAssignableFrom(rawClass)
                            || Iterator.class.isAssignableFrom(rawClass)
                            || Spliterator.class.isAssignableFrom(rawClass)
                            || BaseStream.class.isAssignableFrom(rawClass));
        }
        if (type instanceof ParameterizedType parameterizedType) {
            if (isCollectionLike(parameterizedType.getRawType(), visited)) {
                return true;
            }
            for (final Type argument : parameterizedType.getActualTypeArguments()) {
                if (isCollectionLike(argument, visited)) {
                    return true;
                }
            }
            return false;
        }
        if (type instanceof GenericArrayType) {
            return true;
        }
        if (type instanceof WildcardType wildcardType) {
            for (final Type upperBound : wildcardType.getUpperBounds()) {
                if (isCollectionLike(upperBound, visited)) {
                    return true;
                }
            }
            for (final Type lowerBound : wildcardType.getLowerBounds()) {
                if (isCollectionLike(lowerBound, visited)) {
                    return true;
                }
            }
            return false;
        }
        if (type instanceof TypeVariable<?> typeVariable) {
            for (final Type bound : typeVariable.getBounds()) {
                if (isCollectionLike(bound, visited)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isRepositoryLike(final Class<?> type) {
        return !type.isEnum()
                && !type.isRecord()
                && (REPOSITORY_ROLE.matcher(type.getSimpleName()).find()
                        || PERSISTENCE_PACKAGE.matcher(type.getPackageName()).find());
    }

    private static String methodKey(final Method method) {
        final String parameters =
                Stream.of(method.getParameterTypes())
                        .map(Class::getTypeName)
                        .reduce((left, right) -> left + "," + right)
                        .orElse("");
        return method.getDeclaringClass().getName()
                + "#"
                + method.getName()
                + "("
                + parameters
                + ")";
    }

    private static List<Class<?>> productionClasses()
            throws IOException, URISyntaxException, ClassNotFoundException {
        final Path classRoot =
                Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        final Path sourceRoot =
                Path.of(System.getProperty("user.dir"), "src", "main", "java").toAbsolutePath();
        final List<Class<?>> classes = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(classRoot)) {
            for (final Path classFile : paths.filter(isProductionClassFile()).toList()) {
                final String relativeName =
                        classRoot.relativize(classFile).toString().replace('\\', '/');
                final String binaryName =
                        relativeName.substring(0, relativeName.length() - ".class".length());
                final String topLevelName =
                        binaryName.contains("$")
                                ? binaryName.substring(0, binaryName.indexOf('$'))
                                : binaryName;
                final Path sourceFile = sourceRoot.resolve(topLevelName + ".java");
                if (!isCurrentBinaryName(sourceFile, binaryName, topLevelName)) {
                    continue;
                }
                classes.add(
                        Class.forName(
                                binaryName.replace('/', '.'),
                                false,
                                Thread.currentThread().getContextClassLoader()));
            }
        }
        return classes;
    }

    private static boolean isCurrentBinaryName(
            final Path sourceFile, final String binaryName, final String topLevelName)
            throws IOException {
        if (!Files.isRegularFile(sourceFile)) {
            return false;
        }
        if (binaryName.equals(topLevelName)) {
            return true;
        }
        final String source = Files.readString(sourceFile, StandardCharsets.UTF_8);
        final String nestedPath = binaryName.substring(topLevelName.length() + 1);
        for (final String binarySegment : nestedPath.split("\\$")) {
            final String declaredName = binarySegment.replaceFirst("^\\d+", "");
            if (declaredName.isEmpty()) {
                continue;
            }
            final Pattern declaration =
                    Pattern.compile(
                            "\\b(?:class|interface|record|enum)\\s+"
                                    + Pattern.quote(declaredName)
                                    + "\\b");
            if (!declaration.matcher(source).find()) {
                return false;
            }
        }
        return true;
    }

    private static Predicate<Path> isProductionClassFile() {
        return path -> {
            final String name = path.getFileName().toString();
            return name.endsWith(".class")
                    && !name.equals("module-info.class")
                    && !name.equals("package-info.class");
        };
    }

    private static List<SourceFile> mainJavaSources() throws IOException {
        final Path sourceRoot =
                Path.of(System.getProperty("user.dir"), "src", "main", "java").toAbsolutePath();
        final List<SourceFile> sources = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            for (final Path path :
                    paths.filter(file -> file.toString().endsWith(".java")).toList()) {
                sources.add(
                        new SourceFile(
                                sourceRoot.relativize(path).toString().replace('\\', '/'),
                                Files.readString(path, StandardCharsets.UTF_8)));
            }
        }
        return sources;
    }

    public interface UnboundedApiFixture {
        List<String> findEverything();
    }

    public interface WrappedUnboundedApiFixture {
        Optional<List<String>> findEverything();
    }

    public interface ArrayUnboundedApiFixture {
        String[] findEverything();
    }

    public interface JsonArrayUnboundedApiFixture {
        ArrayNode findEverything();
    }

    public interface RecursiveGenericApiFixture<T extends Comparable<T>> {
        T value();
    }

    private interface HiddenCollectionApiFixture {
        List<String> findEverything();
    }

    private static final class UniverseKeyRepository {
        private final Set<String> keys = Set.of();

        Set<String> loadAllKeys() {
            return keys;
        }
    }

    private static final class BoundedBatchPersistenceAdapterFixture {
        List<String> loadBatch(final int limit) {
            return List.of("one").stream().limit(limit).toList();
        }
    }

    private static final class UnboundedBatchRepository {
        List<String> loadBatch() {
            return List.of();
        }
    }

    private static final class MisleadingNumericBatchRepository {
        List<String> loadBatch(final long tenantId) {
            return List.of(Long.toString(tenantId));
        }
    }

    private static final class BackupRestore {
        List<String> loadAllKeys() {
            return List.of();
        }
    }

    private record MutableContainer(String variable, int declarationOffset) {}

    private record LoopRegion(int start, int bodyStart, int bodyEnd, boolean explicitlyFinite) {}

    private record ForEachRegion(int start, int bodyStart, int bodyEnd) {}

    private record SourceFile(String name, String content) {}
}
