package br.com.esl.etl.v2.plataforma.fonte.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.bootstrap.AnalyticUsersFixtures;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalyticUsersFixturesGatewayTest {
    @Test
    void syntheticUsersKeepOpaqueIdentityAndNullAcrossTwentyOneBoundedRows() {
        final var run = UUID.fromString("00000000-0000-4000-8000-000000000021");
        final var fixture = GraphQlTestSupport.usersFixture();
        final var gateway =
                AnalyticUsersFixtures.source(run, 21, true)
                        .bind(fixture.configuration(), fixture.guard(), CancellationToken.none());
        final var first =
                gateway.fetch(GraphQlTestSupport.request(GraphQlReadOperation.USERS_SNAPSHOT));
        final var nodes = new ArrayList<JsonNode>();
        first.forEachNode(nodes::add);
        assertEquals(20, nodes.size());
        assertEquals(AnalyticUsersFixtures.identifier(run, 0), nodes.get(0).path("id").asText());
        assertTrue(nodes.get(0).path("name").isNull());
        assertTrue(first.hasNextPage());
        assertEquals("synthetic-page-1", first.endCursor().orElseThrow().value());

        final var second =
                gateway.fetch(
                        GraphQlTestSupport.request(GraphQlReadOperation.USERS_SNAPSHOT)
                                .next(first.endCursor().orElseThrow()));
        nodes.clear();
        second.forEachNode(nodes::add);
        assertEquals(1, nodes.size());
        assertEquals(AnalyticUsersFixtures.identifier(run, 20), nodes.get(0).path("id").asText());
        assertFalse(second.hasNextPage());
        assertTrue(second.endCursor().isEmpty());
    }

    @Test
    void identifierAndPageBudgetsRejectOutOfScopeInput() {
        final var run = UUID.fromString("00000000-0000-4000-8000-000000000022");
        for (final int ordinal : new int[] {-1, 256}) {
            assertEquals(
                    "ANA_USER_FIXTURE_ORDINAL",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> AnalyticUsersFixtures.identifier(run, ordinal))
                            .getMessage());
        }
        for (final int count : new int[] {0, 257}) {
            assertEquals(
                    "ANA_USER_FIXTURE_COUNT",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> AnalyticUsersFixtures.source(run, count, false))
                            .getMessage());
        }
    }
}
