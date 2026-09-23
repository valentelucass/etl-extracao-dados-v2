package br.com.esl.etl.v2.plataforma.fonte.graphql;

import br.com.esl.etl.v2.plataforma.contrato.ApprovedGraphQlDocument;
import br.com.esl.etl.v2.plataforma.contrato.ContractClassification;
import br.com.esl.etl.v2.plataforma.contrato.ContractSemanticsFingerprint;
import br.com.esl.etl.v2.plataforma.contrato.SourceCompletenessStatus;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.resiliencia.EslWorkload;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

/** Documentos GraphQL read-only fechados; texto arbitrário nunca cruza a porta produtiva. */
public enum GraphQlReadOperation {
    USERS_SNAPSHOT(
            "2026-08-31.v2-025a.1",
            "V2UsersSnapshot",
            "individual",
            "IndividualInput!",
            "enabled=true",
            20,
            EslWorkload.USUARIOS,
            "/individual/edges/node/id",
            "/individual/edges/node/name",
            "/individual/pageInfo/hasNextPage",
            "/individual/pageInfo/endCursor"),
    PICKS_TRANSITIONAL_SIDECAR(
            "2026-08-31.v2-024.1",
            "V2PicksTransitionalSidecar",
            "pick",
            "PickInput!",
            "requestDate=ISO_LOCAL_DATE",
            100,
            EslWorkload.COLETAS,
            "/pick/edges/node/id",
            "/pick/edges/node/sequenceCode",
            "/pick/edges/node/pickItems/id",
            "/pick/pageInfo/hasNextPage",
            "/pick/pageInfo/endCursor"),
    PICKS_TEMPORAL_REFERENCE(
            "2026-09-10.coletas-temporal.1",
            "V2PicksTemporalReference",
            "pick",
            "PickInput!",
            "requestDate=ISO_LOCAL_DATE",
            20,
            EslWorkload.COLETAS,
            "/pick/edges/node/id",
            "/pick/edges/node/status",
            "/pick/edges/node/statusUpdatedAt",
            "/pick/edges/node/requestDate",
            "/pick/pageInfo/hasNextPage",
            "/pick/pageInfo/endCursor"),
    FREIGHTS_TRANSITIONAL_SIDECAR(
            "2026-08-31.v2-024.1",
            "V2FreightsTransitionalSidecar",
            "freight",
            "FreightInput!",
            "serviceAt=ISO_LOCAL_DATE_INCLUSIVE_RANGE",
            100,
            EslWorkload.FRETES,
            "/freight/edges/node/id",
            "/freight/edges/node/accountingCreditId",
            "/freight/edges/node/accountingCreditInstallmentId",
            "/freight/edges/node/referenceNumber",
            "/freight/edges/node/cte/key",
            "/freight/edges/node/total",
            "/freight/edges/node/corporationSequenceNumber",
            "/freight/edges/node/pickItemId",
            "/freight/pageInfo/hasNextPage",
            "/freight/pageInfo/endCursor");

    private final String contractVersion;
    private final String operationName;
    private final String connectionName;
    private final String parametersType;
    private final String fixedParametersContract;
    private final int maximumPageSize;
    private final EslWorkload workload;
    private final String documentText;
    private final ApprovedGraphQlDocument approvedDocument;
    private final Set<String> selectionPaths;
    private final ImmutableFingerprint contractSemanticsFingerprint;

    GraphQlReadOperation(
            final String contractVersion,
            final String operationName,
            final String connectionName,
            final String parametersType,
            final String fixedParametersContract,
            final int maximumPageSize,
            final EslWorkload workload,
            final String... selectionPaths) {
        this.contractVersion = Objects.requireNonNull(contractVersion, "A versão é obrigatória.");
        this.operationName = operationName;
        this.connectionName = connectionName;
        this.parametersType = parametersType;
        this.fixedParametersContract = fixedParametersContract;
        this.maximumPageSize = maximumPageSize;
        this.workload = workload;
        this.selectionPaths = Set.of(selectionPaths);
        if (selectionPaths.length < 1
                || selectionPaths.length > 64
                || this.selectionPaths.size() != selectionPaths.length) {
            throw new IllegalArgumentException("A operação GraphQL contém seleção duplicada.");
        }
        this.documentText = renderDocument(selectionPaths);
        this.approvedDocument = ApprovedGraphQlDocument.approve(this.documentText);
        this.contractSemanticsFingerprint = createSemanticsFingerprint();
    }

    public String contractVersion() {
        return contractVersion;
    }

    public String operationName() {
        return operationName;
    }

    public String connectionName() {
        return connectionName;
    }

    public int maximumPageSize() {
        return maximumPageSize;
    }

    public String parametersType() {
        return parametersType;
    }

    public String fixedParametersContract() {
        return fixedParametersContract;
    }

    public EslWorkload workload() {
        return workload;
    }

    public ApprovedGraphQlDocument approvedDocument() {
        return approvedDocument;
    }

    public String documentReference() {
        return "graphql-" + name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public ContractClassification contractClassification() {
        return ContractClassification.TRANSITIONAL;
    }

    public SourceCompletenessStatus completenessStatus() {
        return SourceCompletenessStatus.BLOCKED_NO_COMPLETENESS_PROOF;
    }

    public ImmutableFingerprint contractSemanticsFingerprint() {
        return contractSemanticsFingerprint;
    }

    String documentText() {
        return documentText;
    }

    int selectionCount() {
        return selectionPaths.size();
    }

    boolean declaresSelection(final String path) {
        return selectionPaths.contains(Objects.requireNonNull(path, "O path é obrigatório."));
    }

    private ImmutableFingerprint createSemanticsFingerprint() {
        return ContractSemanticsFingerprint.create(
                contractVersion,
                "GRAPHQL",
                operationName,
                connectionName,
                parametersType,
                fixedParametersContract,
                Integer.toString(maximumPageSize),
                workload.name(),
                GraphQlContractAdapter.APPROVED_KEY_PATH,
                approvedDocument.fingerprint().version(),
                approvedDocument.fingerprint().sha256(),
                "RELAY_CURSOR_INTRA_RUN_ONLY",
                "PAGE_INFO_TERMINAL_LOCAL_UNVERIFIED",
                contractClassification().name(),
                completenessStatus().name());
    }

    void forEachSelection(final Consumer<String> consumer) {
        selectionPaths.stream().sorted().forEach(consumer);
    }

    private String renderDocument(final String[] paths) {
        if (!isName(operationName)
                || !isName(connectionName)
                || !parametersType.endsWith("!")
                || !isName(parametersType.substring(0, parametersType.length() - 1))) {
            throw new IllegalArgumentException("A identidade do documento GraphQL é inválida.");
        }
        final SelectionNode root = new SelectionNode();
        for (final String path : paths) {
            final String[] segments = path.split("/", -1);
            if (segments.length < 3
                    || segments.length > 9
                    || !segments[0].isEmpty()
                    || !connectionName.equals(segments[1])) {
                throw new IllegalArgumentException("O path GraphQL não pertence à conexão.");
            }
            SelectionNode current = root;
            for (int index = 2; index < segments.length; index++) {
                final String segment = segments[index];
                if (!isName(segment) || current.leaf) {
                    throw new IllegalArgumentException("A árvore de seleção GraphQL é inválida.");
                }
                current = current.children.computeIfAbsent(segment, ignored -> new SelectionNode());
            }
            if (current.leaf || !current.children.isEmpty()) {
                throw new IllegalArgumentException("A árvore de seleção GraphQL é ambígua.");
            }
            current.leaf = true;
        }
        final StringBuilder document = new StringBuilder(1_024);
        document.append("query ")
                .append(operationName)
                .append("($params: ")
                .append(parametersType)
                .append(", $after: String, $first: Int!) {\n  ")
                .append(connectionName)
                .append("(params: $params, after: $after, first: $first) {\n");
        renderSelections(document, root, 4);
        return document.append("  }\n}").toString();
    }

    private static void renderSelections(
            final StringBuilder document, final SelectionNode node, final int indentation) {
        node.children.forEach(
                (name, child) -> {
                    document.append(" ".repeat(indentation)).append(name);
                    if (child.leaf) {
                        document.append('\n');
                    } else {
                        document.append(" {\n");
                        renderSelections(document, child, indentation + 2);
                        document.append(" ".repeat(indentation)).append("}\n");
                    }
                });
    }

    private static boolean isName(final String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            final char character = value.charAt(index);
            if (!(character == '_'
                    || character >= 'A' && character <= 'Z'
                    || character >= 'a' && character <= 'z'
                    || index > 0 && character >= '0' && character <= '9')) {
                return false;
            }
        }
        return true;
    }

    private static final class SelectionNode {

        private final TreeMap<String, SelectionNode> children = new TreeMap<>();
        private boolean leaf;
    }
}
