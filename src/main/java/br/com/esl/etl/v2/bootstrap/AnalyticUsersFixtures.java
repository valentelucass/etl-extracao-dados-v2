package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.fonte.graphql.AnalyticUsersSyntheticSource;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.Objects;
import java.util.UUID;

/** Explicit opaque identifiers and bounded Relay pages for the packaged observation scenario. */
public final class AnalyticUsersFixtures {
    private AnalyticUsersFixtures() {}

    public static String identifier(final UUID run, final int ordinal) {
        Objects.requireNonNull(run);
        if (ordinal < 0 || ordinal >= 256) {
            throw new IllegalArgumentException("ANA_USER_FIXTURE_ORDINAL");
        }
        return "synthetic-analytic-" + run + "-" + ordinal;
    }

    public static AnalyticUsersSyntheticSource source(
            final UUID run, final int count, final boolean clearFirstName) {
        Objects.requireNonNull(run);
        if (count < 1 || count > 256) {
            throw new IllegalArgumentException("ANA_USER_FIXTURE_COUNT");
        }
        return new AnalyticUsersSyntheticSource(
                page -> {
                    if (page < 1 || page > (count + 19) / 20) {
                        throw new IllegalArgumentException("ANA_USER_FIXTURE_PAGE");
                    }
                    final var envelope = JsonNodeFactory.instance.objectNode();
                    final var individual = envelope.putObject("data").putObject("individual");
                    final var edges = individual.putArray("edges");
                    final int end = Math.min(count, page * 20);
                    for (int index = (page - 1) * 20; index < end; index++) {
                        final var node = edges.addObject().putObject("node");
                        node.put("id", identifier(run, index));
                        if (clearFirstName && index == 0) {
                            node.putNull("name");
                        } else {
                            node.put("name", " USUÁRIO SINTÉTICO ");
                        }
                    }
                    final var info =
                            individual.putObject("pageInfo").put("hasNextPage", end < count);
                    if (end < count) {
                        info.put("endCursor", "synthetic-page-" + page);
                    } else {
                        info.putNull("endCursor");
                    }
                    return envelope.toString();
                });
    }
}
