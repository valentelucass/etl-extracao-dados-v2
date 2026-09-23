package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryFreightOperationalIT.DATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlCatalog;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlContract;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlValue;
import br.com.esl.etl.v2.plataforma.analitico.SyntheticCollectionSnapshot;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticQueries;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AnalyticLaboratoryQueryReaderIT {
    @Test
    void nineteenPreparedQueriesMatchCatalogAndReturnNativeTypesWithoutRetainingUniverse()
            throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var f = AnalyticLaboratoryCollectionSupplementIT.start(session);
            AnalyticLaboratoryCollectionQueriesIT.prepare(session, f);
            final var snapshot = new SyntheticCollectionSnapshot(f.run(), DATE, 2, true);
            final var sweep = AnalyticLaboratoryCollectionSweepIT.runtime(session, f);
            sweep.observe(snapshot, UUID.randomUUID(), CancellationToken.none());
            sweep.observe(snapshot, UUID.randomUUID(), CancellationToken.none());
            final var reader = new JdbcAnalyticQueries(session);
            int fields = 0;
            for (final var contract : AnalyticSqlContract.values()) {
                fields += AnalyticSqlCatalog.columns(contract).size();
                final var seen = new AtomicInteger();
                final var receipt =
                        reader.read(
                                f.run(),
                                contract,
                                1,
                                1000,
                                CancellationToken.none(),
                                row -> {
                                    assertEquals(contract.columns(), row.values().size());
                                    seen.incrementAndGet();
                                    if (contract == AnalyticSqlContract.SQL_03) {
                                        assertEquals(
                                                200001,
                                                assertInstanceOf(
                                                                AnalyticSqlValue.IntegerValue.class,
                                                                row.values().get(0))
                                                        .value());
                                        assertEquals(
                                                DATE,
                                                assertInstanceOf(
                                                                AnalyticSqlValue.Date.class,
                                                                row.values().get(2))
                                                        .value());
                                        assertInstanceOf(
                                                AnalyticSqlValue.Time.class, row.values().get(3));
                                        assertEquals(
                                                new BigDecimal("7.12500000"),
                                                assertInstanceOf(
                                                                AnalyticSqlValue.Decimal.class,
                                                                row.values().get(9))
                                                        .value());
                                        assertInstanceOf(
                                                AnalyticSqlValue.Missing.class,
                                                row.values().get(12));
                                        assertInstanceOf(
                                                AnalyticSqlValue.CivilDateTime.class,
                                                row.values().get(35));
                                        assertInstanceOf(
                                                AnalyticSqlValue.Text.class, row.values().get(39));
                                    }
                                    if (contract == AnalyticSqlContract.SQL_04) {
                                        assertInstanceOf(
                                                AnalyticSqlValue.Identifier.class,
                                                row.values().get(11));
                                    }
                                });
                assertEquals(seen.get(), receipt.rows());
                assertEquals(contract.columns(), receipt.columns());
                assertEquals(16, receipt.fetchSize());
                if (contract == AnalyticSqlContract.SQL_03
                        || contract == AnalyticSqlContract.SQL_04) {
                    assertEquals(1, receipt.rows());
                }
            }
            assertEquals(673, fields);
        }
    }

    @Test
    void rowCapAndInvalidRequestsFailAndAnotherRunDoesNotLeakRows() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var f = AnalyticLaboratoryCollectionSupplementIT.start(session);
            AnalyticLaboratoryCollectionQueriesIT.prepare(session, f);
            final var reader = new JdbcAnalyticQueries(session);
            final var seen = new AtomicInteger();
            final var error =
                    assertThrows(
                            SQLException.class,
                            () ->
                                    reader.read(
                                            f.run(),
                                            AnalyticSqlContract.SQL_10,
                                            1,
                                            1,
                                            CancellationToken.none(),
                                            row -> seen.incrementAndGet()));
            assertEquals("ANA_QUERY_ROW_LIMIT_EXCEEDED", error.getMessage());
            assertEquals(1, seen.get());
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            reader.read(
                                    f.run(),
                                    AnalyticSqlContract.SQL_03,
                                    0,
                                    100,
                                    CancellationToken.none(),
                                    row -> {}));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            reader.read(
                                    f.run(),
                                    AnalyticSqlContract.SQL_03,
                                    1,
                                    4097,
                                    CancellationToken.none(),
                                    row -> {}));
            assertEquals(
                    0,
                    reader.read(
                                    UUID.randomUUID(),
                                    AnalyticSqlContract.SQL_03,
                                    1,
                                    100,
                                    CancellationToken.none(),
                                    row -> {})
                            .rows());
            assertEquals(
                    1,
                    reader.read(
                                    f.run(),
                                    AnalyticSqlContract.SQL_03,
                                    1,
                                    100,
                                    CancellationToken.none(),
                                    row -> {})
                            .rows());
            assertTrue(
                    reader.read(
                                            f.run(),
                                            AnalyticSqlContract.SQL_10,
                                            1,
                                            100,
                                            CancellationToken.none(),
                                            row -> {})
                                    .rows()
                            > 1);
        }
    }
}
