package br.com.esl.etl.v2.plataforma.fonte.graphql;

import java.util.Objects;

/** Caps explícitos de uma travessia GraphQL na mesma execução. */
public record GraphQlExtractionLimits(int maxPages, long maxNodes) {

    public static final int ABSOLUTE_MAXIMUM_PAGES = 100_000;
    public static final long ABSOLUTE_MAXIMUM_NODES = 10_000_000L;

    public GraphQlExtractionLimits {
        if (maxPages < 1 || maxPages > ABSOLUTE_MAXIMUM_PAGES) {
            throw new IllegalArgumentException("O máximo de páginas GraphQL é inválido.");
        }
        if (maxNodes < 1 || maxNodes > ABSOLUTE_MAXIMUM_NODES) {
            throw new IllegalArgumentException("O máximo de nodes GraphQL é inválido.");
        }
    }

    public void validate(final GraphQlPageRequest request) {
        Objects.requireNonNull(request, "A requisição GraphQL é obrigatória.");
        if (request.after().isPresent()) {
            throw new IllegalArgumentException(
                    "A travessia GraphQL deve iniciar sem cursor persistido.");
        }
    }
}
