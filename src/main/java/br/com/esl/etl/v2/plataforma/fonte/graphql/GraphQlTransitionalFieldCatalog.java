package br.com.esl.etl.v2.plataforma.fonte.graphql;

import java.util.HashSet;
import java.util.Set;

/** Verifica igualdade bidirecional entre cada documento ativo e seu ledger transitório. */
public final class GraphQlTransitionalFieldCatalog {

    private GraphQlTransitionalFieldCatalog() {}

    public static void validate() {
        final Set<String> identities = new HashSet<>();
        for (final GraphQlTransitionalField field : GraphQlTransitionalField.values()) {
            final String identity = field.operation().name() + ':' + field.path();
            if (!identities.add(identity)
                    || !field.operation().declaresSelection(field.path())
                    || !field.publicationBlocked()) {
                throw new IllegalStateException("O ledger GraphQL transitório é inconsistente.");
            }
        }
        for (final GraphQlReadOperation operation : GraphQlReadOperation.values()) {
            int catalogFields = 0;
            for (final GraphQlTransitionalField field : GraphQlTransitionalField.values()) {
                if (field.operation() == operation) {
                    catalogFields++;
                }
            }
            if (catalogFields != operation.selectionCount()) {
                throw new IllegalStateException(
                        "O documento GraphQL possui seleção sem ledger transitório exato.");
            }
        }
    }

    static boolean shadowUpsertBlocked(final GraphQlReadOperation operation) {
        boolean found = false;
        for (final GraphQlTransitionalField field : GraphQlTransitionalField.values()) {
            if (field.operation() == operation) {
                found = true;
                if (field.promotionPolicy()
                        == GraphQlTransitionalField.PromotionPolicy.OBSERVATION_ONLY) {
                    return true;
                }
            }
        }
        if (!found) {
            throw new IllegalStateException("A operação GraphQL não possui ledger transitório.");
        }
        return false;
    }
}
