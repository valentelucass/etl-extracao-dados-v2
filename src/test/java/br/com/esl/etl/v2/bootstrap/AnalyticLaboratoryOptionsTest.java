package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlContract;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalyticLaboratoryOptionsTest {
    @Test
    void everyClosedContractAndFourFailuresHaveAnExplicitPackagedCommand() {
        for (final var contract : AnalyticSqlContract.values()) {
            final var parsed =
                    AnalyticLaboratoryOptions.parse(
                            new String[] {
                                "query",
                                "--synthetic-analytic-lab",
                                "--contract=" + contract.id(),
                                "--roots=16",
                                "--limit=4096"
                            });
            assertEquals(contract, parsed.contract());
            assertEquals(16, parsed.roots());
        }
        for (final var fault : AnalyticScenarioRuntime.Fault.values()) {
            assertEquals(
                    fault,
                    AnalyticLaboratoryOptions.parse(
                                    new String[] {
                                        "status",
                                        "--synthetic-analytic-lab",
                                        "--fault=" + fault.name()
                                    })
                            .fault());
        }
    }

    @Test
    void pathsRealSourcesPermitsModesDuplicateOptionsAndUnboundedBudgetsAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        AnalyticLaboratoryOptions.parse(
                                new String[] {null, "--synthetic-analytic-lab"}));
        for (final var forbidden :
                List.of(
                        "--path=C:/real",
                        "--url=https://synthetic.invalid",
                        "--source=GRAPHQL",
                        "--permit=synthetic",
                        "--mode=SWEEP",
                        "--roots=481",
                        "--roots=1",
                        "--roots=-1",
                        "--roots=0002",
                        "--page-size=17",
                        "--limit=4097",
                        "--contract=SQL-20",
                        "--fault=UNKNOWN")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            AnalyticLaboratoryOptions.parse(
                                    new String[] {
                                        "scenario", "--synthetic-analytic-lab", forbidden
                                    }),
                    forbidden);
        }
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        AnalyticLaboratoryOptions.parse(
                                new String[] {"query", "--synthetic-analytic-lab"}));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        AnalyticLaboratoryOptions.parse(
                                new String[] {
                                    "scenario", "--synthetic-analytic-lab", "--roots=2", "--roots=2"
                                }));
        assertThrows(
                IllegalArgumentException.class,
                () -> AnalyticLaboratoryOptions.parse(new String[0]));
    }
}
