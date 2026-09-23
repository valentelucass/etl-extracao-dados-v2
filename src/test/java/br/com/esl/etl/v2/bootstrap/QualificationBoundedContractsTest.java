package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlContract;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationCampaign;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationComparator;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationGate;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationOracles;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationPlanner;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationTopology;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationWireOracle;
import br.com.esl.etl.v2.plataforma.qualificacao.QualifiedPackage;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class QualificationBoundedContractsTest {
    @Test
    void directConstructionCannotBypassParsedCollectionCeilings() throws Exception {
        final var original = QualificationCampaign.parse(QualificationContractTest.campaign());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new QualificationCampaign(
                                original.id(),
                                original.pins(),
                                2,
                                2,
                                300,
                                Collections.nCopies(65, original.cases().get(0))));
        final var item = original.cases().get(0);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new QualificationCampaign.Case(
                                item.id(),
                                item.wave(),
                                Collections.nCopies(36, "scope"),
                                item.action(),
                                item.mode(),
                                item.fault(),
                                item.barrier(),
                                item.outputs(),
                                item.tick(),
                                item.start(),
                                item.endExclusive(),
                                item.zone(),
                                item.lookbackSeconds(),
                                item.deadlineSeconds(),
                                item.maximumCatchUp(),
                                item.blackouts(),
                                item.expected()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new QualificationPlanner.Plan(
                                Collections.nCopies(4, null),
                                false,
                                QualificationGate.State.PASS_LOCAL,
                                "DUE"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new QualificationTopology.Node(
                                "test",
                                QualificationTopology.Kind.INPUT,
                                Collections.nCopies(36, "scope")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new QualificationOracles.Output(
                                AnalyticSqlContract.SQL_01,
                                QualificationOracles.Count.ONE_PER_ROOT,
                                List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new QualificationComparator.Result(
                                "SQL-01",
                                1,
                                1,
                                25,
                                Collections.nCopies(25, null),
                                QualificationGate.State.FAILED));
        assertEquals(1024, QualifiedPackage.MAX_MEMBERS);
        final var members = new HashMap<String, QualifiedPackage.Member>();
        for (int index = 0; index < QualifiedPackage.MAX_MEMBERS + 1; index++) {
            members.put("member-" + index, null);
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> new QualifiedPackage(null, null, null, members));
        final var node = JsonNodeFactory.instance.objectNode();
        for (int index = 0; index < 129; index++) {
            node.put("field-" + index, index);
        }
        assertThrows(IllegalArgumentException.class, () -> QualificationWireOracle.names(node));
    }

    @Test
    void boundedReceiptsCannotBeConstructedWithAnUnboundedRunUniverse() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new QualificationMetrics.Snapshot(0, 0, 0, 0, 0, 0, 0, 0, 0, List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new QualificationScenarioVerifier.Result(
                                java.util.Map.of(), List.of(), null, 0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new QualificationWindowExecutor.Result(
                                null, Collections.nCopies(16, null), null, null, null));
        final var tables = new HashMap<String, Long>();
        for (int index = 0; index < 2049; index++) {
            tables.put("table-" + index, 0L);
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> new QualificationSqlEvidence.Snapshot(tables, "a".repeat(64)));
    }
}
