package br.com.esl.etl.v2.plataforma.persistencia.controle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The reviewed physical exact_time schema has stage_record_id and no execution_id. */
class C78Sample12OwnerExactTimeSchemaTest {
    @Test
    void baselineReadbackAndRollbackUseTheOwnedParentForExactTimeCounts() throws Exception {
        var fixture =
                new C78Sample12OwnerTest.Fixture(1) {
                    @Override
                    Object statement(String sql) {
                        if (sql != null && sql.contains("coleta_exact_time")) {
                            assertFalse(sql.contains("coleta_exact_time WHERE execution_id"));
                            assertTrue(sql.contains("JOIN stg.coleta_record r"));
                            assertTrue(sql.contains("r.stage_record_id=t.stage_record_id"));
                            assertTrue(sql.contains("r.execution_id="));
                        }
                        return super.statement(sql);
                    }
                };
        var baseline =
                C78Sample12Owner.baseline(fixture.connection(), fixture.selection, fixture::before);
        var finish = C78Sample12OwnerTest.finish(fixture.selection, 1);
        assertTrue(
                C78Sample12Owner.compare(
                        fixture.connection(), fixture.selection, finish, fixture::before));
        var result =
                C78Sample12Owner.rollbackAndClose(
                        fixture.connection(),
                        fixture.selection,
                        baseline,
                        finish,
                        true,
                        fixture::before,
                        () -> fixture.charges);
        assertTrue(result.matches(finish));
        assertEquals(6, fixture.sql.size());
        assertEquals(2, fixture.rollbacks);
        assertEquals(0, fixture.transaction);
        assertTrue(fixture.closed);
        assertEquals(java.util.List.of(10, 10, 10, 10, 10, 10), fixture.timeouts);
    }
}
