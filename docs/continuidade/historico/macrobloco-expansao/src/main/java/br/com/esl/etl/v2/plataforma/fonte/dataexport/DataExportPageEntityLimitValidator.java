package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Valida o limite {@code per} pela entidade lógica do template, não pelas linhas físicas do
 * relatório.
 *
 * <p>Um mesmo {@code id} pode gerar mais de uma linha quando o relatório expande relações. Essa
 * expansão não autoriza valores ausentes, nulos ou estruturados para a entidade de paginação.
 */
public final class DataExportPageEntityLimitValidator {

    private DataExportPageEntityLimitValidator() {}

    /**
     * Garante que cada linha tenha a entidade escalar declarada pelo template e que a quantidade de
     * entidades distintas não exceda o {@code per} pedido.
     */
    public static void validate(
            final DataExportPageRequest request, final DataExportPageResponse response) {
        countDistinctEntities(request, response);
    }

    /**
     * Valida o contrato de entidade da página e devolve apenas sua contagem distinta sanitizada.
     */
    public static int countDistinctEntities(
            final DataExportPageRequest request, final DataExportPageResponse response) {
        Objects.requireNonNull(request, "A requisição Data Export é obrigatória.");
        Objects.requireNonNull(response, "A resposta Data Export é obrigatória.");

        final String entityField = request.template().paginationEntityField();
        final Set<JsonNode> entityValues = new HashSet<>();
        for (final JsonNode record : response.records()) {
            entityValues.add(requiredScalarEntityValue(request, record, entityField));
        }

        final int entityCount = entityValues.size();
        if (entityCount > request.pageSize()) {
            throw new IllegalStateException(
                    "O template "
                            + request.template().templateId()
                            + " retornou "
                            + entityCount
                            + " entidades distintas na página "
                            + request.page()
                            + ", acima do per solicitado de "
                            + request.pageSize()
                            + ".");
        }
        return entityCount;
    }

    private static JsonNode requiredScalarEntityValue(
            final DataExportPageRequest request, final JsonNode record, final String entityField) {
        final JsonNode entityValue = record == null ? null : record.get(entityField);
        if (entityValue == null || entityValue.isNull() || !entityValue.isValueNode()) {
            throw new IllegalStateException(
                    "O template "
                            + request.template().templateId()
                            + " retornou o campo de entidade de paginação obrigatório '"
                            + entityField
                            + "' ausente, nulo ou não escalar na página "
                            + request.page()
                            + ".");
        }
        return entityValue;
    }
}
