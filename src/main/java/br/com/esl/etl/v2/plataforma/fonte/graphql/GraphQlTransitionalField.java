package br.com.esl.etl.v2.plataforma.fonte.graphql;

import br.com.esl.etl.v2.plataforma.contrato.ContractClassification;
import java.util.Objects;

/** Ledger fechado: toda folha selecionada possui owner-papel, gate de prazo e saída objetiva. */
public enum GraphQlTransitionalField {
    GQL_0178(
            GraphQlReadOperation.USERS_SNAPSHOT,
            "/individual/pageInfo/hasNextPage",
            Purpose.PAGINATION,
            "SEGURANCA_E_OPERACAO",
            "V2-033",
            "Remover com o documento GraphQL após o cutover de Usuários.",
            "V2-040",
            PromotionPolicy.SHADOW_UPSERT_ONLY,
            EvidenceLevel.IMPLEMENTED_IN_SHADOW),
    GQL_0179(
            GraphQlReadOperation.USERS_SNAPSHOT,
            "/individual/pageInfo/endCursor",
            Purpose.PAGINATION,
            "SEGURANCA_E_OPERACAO",
            "V2-033",
            "Remover com o documento GraphQL após o cutover de Usuários.",
            "V2-040",
            PromotionPolicy.SHADOW_UPSERT_ONLY,
            EvidenceLevel.IMPLEMENTED_IN_SHADOW),
    GQL_0180(
            GraphQlReadOperation.USERS_SNAPSHOT,
            "/individual/edges/node/id",
            Purpose.USER_SOURCE,
            "SEGURANCA_E_OPERACAO",
            "V2-033",
            "Remover somente quando uma fonte substituta de user_id estiver contratada e reconciliada.",
            "V2-040",
            PromotionPolicy.SHADOW_UPSERT_ONLY,
            EvidenceLevel.IMPLEMENTED_IN_SHADOW),
    GQL_0181(
            GraphQlReadOperation.USERS_SNAPSHOT,
            "/individual/edges/node/name",
            Purpose.USER_SOURCE,
            "SEGURANCA_E_OPERACAO",
            "V2-033",
            "Remover somente quando uma fonte substituta de nome estiver contratada e reconciliada.",
            "V2-040",
            PromotionPolicy.SHADOW_UPSERT_ONLY,
            EvidenceLevel.IMPLEMENTED_IN_SHADOW),
    GQL_0002(
            GraphQlReadOperation.PICKS_TRANSITIONAL_SIDECAR,
            "/pick/edges/node/id",
            Purpose.SIDECAR,
            "OPERACAO_E_PLATAFORMA",
            "V2-010",
            "Remover após equivalência Data Export e paridade da chave de Coletas.",
            "V2-040"),
    GQL_0006(
            GraphQlReadOperation.PICKS_TRANSITIONAL_SIDECAR,
            "/pick/edges/node/sequenceCode",
            Purpose.DUAL_RUN,
            "OPERACAO_E_PLATAFORMA",
            "V2-010",
            "Remover após identidade e paridade de Coletas aprovadas.",
            "V2-040"),
    GQL_0034(
            GraphQlReadOperation.PICKS_TRANSITIONAL_SIDECAR,
            "/pick/edges/node/pickItems/id",
            Purpose.SIDECAR,
            "OPERACAO_E_PLATAFORMA",
            "V2-010",
            "Remover após o vínculo Coleta-Frete ser provado e materializado set-based.",
            "V2-040"),
    GQL_0050(
            GraphQlReadOperation.PICKS_TRANSITIONAL_SIDECAR,
            "/pick/pageInfo/hasNextPage",
            Purpose.PAGINATION,
            "OPERACAO_E_PLATAFORMA",
            "V2-010",
            "Remover com o sidecar de Coletas após o cutover.",
            "V2-040"),
    GQL_0051(
            GraphQlReadOperation.PICKS_TRANSITIONAL_SIDECAR,
            "/pick/pageInfo/endCursor",
            Purpose.PAGINATION,
            "OPERACAO_E_PLATAFORMA",
            "V2-010",
            "Remover com o sidecar de Coletas após o cutover.",
            "V2-040"),
    GQL_0052(
            GraphQlReadOperation.FREIGHTS_TRANSITIONAL_SIDECAR,
            "/freight/edges/node/id",
            Purpose.SIDECAR,
            "OPERACAO_FISCAL_E_PLATAFORMA",
            "V2-011",
            "Remover após equivalência Data Export e paridade da chave de Fretes.",
            "V2-040"),
    GQL_0053(
            GraphQlReadOperation.FREIGHTS_TRANSITIONAL_SIDECAR,
            "/freight/edges/node/accountingCreditId",
            Purpose.SIDECAR,
            "OPERACAO_FISCAL_E_PLATAFORMA",
            "V2-011",
            "Remover após equivalente financeiro Data Export provado.",
            "V2-040"),
    GQL_0054(
            GraphQlReadOperation.FREIGHTS_TRANSITIONAL_SIDECAR,
            "/freight/edges/node/accountingCreditInstallmentId",
            Purpose.SIDECAR,
            "OPERACAO_FISCAL_E_PLATAFORMA",
            "V2-011",
            "Remover após equivalente de parcela Data Export provado.",
            "V2-040"),
    GQL_0055(
            GraphQlReadOperation.FREIGHTS_TRANSITIONAL_SIDECAR,
            "/freight/edges/node/referenceNumber",
            Purpose.DUAL_RUN,
            "OPERACAO_FISCAL_E_PLATAFORMA",
            "V2-011",
            "Remover quando cobertura e paridade do campo Data Export forem aprovadas.",
            "V2-040"),
    GQL_0060(
            GraphQlReadOperation.FREIGHTS_TRANSITIONAL_SIDECAR,
            "/freight/edges/node/cte/key",
            Purpose.DUAL_RUN,
            "OPERACAO_FISCAL_E_PLATAFORMA",
            "V2-011",
            "Remover quando a chave CT-e do Data Export estiver contratada e reconciliada.",
            "V2-040"),
    GQL_0066(
            GraphQlReadOperation.FREIGHTS_TRANSITIONAL_SIDECAR,
            "/freight/edges/node/total",
            Purpose.DUAL_RUN,
            "OPERACAO_FISCAL_E_PLATAFORMA",
            "V2-011",
            "Remover quando escala, moeda e paridade do total Data Export forem aprovadas.",
            "V2-040"),
    GQL_0106(
            GraphQlReadOperation.FREIGHTS_TRANSITIONAL_SIDECAR,
            "/freight/edges/node/corporationSequenceNumber",
            Purpose.DUAL_RUN,
            "OPERACAO_FISCAL_E_PLATAFORMA",
            "V2-011",
            "Remover após identidade e paridade da sequência de Fretes aprovadas.",
            "V2-040"),
    GQL_0107(
            GraphQlReadOperation.FREIGHTS_TRANSITIONAL_SIDECAR,
            "/freight/edges/node/pickItemId",
            Purpose.SIDECAR,
            "OPERACAO_FISCAL_E_PLATAFORMA",
            "V2-011",
            "Remover após o vínculo Coleta-Frete ser provado sem inferência.",
            "V2-040"),
    GQL_0157(
            GraphQlReadOperation.FREIGHTS_TRANSITIONAL_SIDECAR,
            "/freight/pageInfo/hasNextPage",
            Purpose.PAGINATION,
            "OPERACAO_FISCAL_E_PLATAFORMA",
            "V2-011",
            "Remover com o sidecar de Fretes após o cutover.",
            "V2-040"),
    GQL_0158(
            GraphQlReadOperation.FREIGHTS_TRANSITIONAL_SIDECAR,
            "/freight/pageInfo/endCursor",
            Purpose.PAGINATION,
            "OPERACAO_FISCAL_E_PLATAFORMA",
            "V2-011",
            "Remover com o sidecar de Fretes após o cutover.",
            "V2-040");

    private final GraphQlReadOperation operation;
    private final String path;
    private final Purpose purpose;
    private final String ownerRole;
    private final String deadlineGate;
    private final String exitCriteria;
    private final String removalTask;
    private final PromotionPolicy promotionPolicy;
    private final EvidenceLevel evidenceLevel;

    GraphQlTransitionalField(
            final GraphQlReadOperation operation,
            final String path,
            final Purpose purpose,
            final String ownerRole,
            final String deadlineGate,
            final String exitCriteria,
            final String removalTask) {
        this(
                operation,
                path,
                purpose,
                ownerRole,
                deadlineGate,
                exitCriteria,
                removalTask,
                PromotionPolicy.OBSERVATION_ONLY,
                EvidenceLevel.SYNTHETIC_ONLY);
    }

    GraphQlTransitionalField(
            final GraphQlReadOperation operation,
            final String path,
            final Purpose purpose,
            final String ownerRole,
            final String deadlineGate,
            final String exitCriteria,
            final String removalTask,
            final PromotionPolicy promotionPolicy,
            final EvidenceLevel evidenceLevel) {
        this.operation = Objects.requireNonNull(operation, "A operação é obrigatória.");
        this.path = requireText(path);
        this.purpose = Objects.requireNonNull(purpose, "A finalidade é obrigatória.");
        this.ownerRole = requireText(ownerRole);
        this.deadlineGate = requireText(deadlineGate);
        this.exitCriteria = requireText(exitCriteria);
        this.removalTask = requireText(removalTask);
        this.promotionPolicy =
                Objects.requireNonNull(promotionPolicy, "A política de promoção é obrigatória.");
        this.evidenceLevel =
                Objects.requireNonNull(evidenceLevel, "O nível de evidência é obrigatório.");
    }

    public GraphQlReadOperation operation() {
        return operation;
    }

    public String path() {
        return path;
    }

    public Purpose purpose() {
        return purpose;
    }

    public String ownerRole() {
        return ownerRole;
    }

    public String deadlineGate() {
        return deadlineGate;
    }

    public String exitCriteria() {
        return exitCriteria;
    }

    public String removalTask() {
        return removalTask;
    }

    public boolean publicationBlocked() {
        return true;
    }

    /**
     * Controla somente o efeito shadow; publicação externa continua bloqueada para todo o ledger.
     */
    public PromotionPolicy promotionPolicy() {
        return promotionPolicy;
    }

    public ContractClassification classification() {
        return ContractClassification.TRANSITIONAL;
    }

    public EvidenceLevel evidenceLevel() {
        return evidenceLevel;
    }

    private static String requireText(final String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("O ledger GraphQL exige texto não vazio.");
        }
        return value;
    }

    public enum Purpose {
        USER_SOURCE,
        DUAL_RUN,
        SIDECAR,
        PAGINATION
    }

    public enum EvidenceLevel {
        SYNTHETIC_ONLY,
        IMPLEMENTED_IN_SHADOW
    }

    public enum PromotionPolicy {
        OBSERVATION_ONLY,
        SHADOW_UPSERT_ONLY
    }
}
