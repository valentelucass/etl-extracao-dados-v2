package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticMaterializationRequest;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticMaterializations;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionMaterializations;
import br.com.esl.etl.v2.plataforma.persistencia.relacional.JdbcRelationalLaboratory;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Reuses captured sources and explicit supplements; it never fabricates a capture receipt. */
final class SequenceRecomposition {
    record Receipt(Map<UUID, String> facts, Map<String, Long> candidates) {
        Receipt {
            facts = Map.copyOf(facts);
            candidates = Map.copyOf(candidates);
            if (facts.size() != 5 || candidates.size() != 5) {
                throw new IllegalArgumentException("SEQUENCE_RECOMPOSITION_RECEIPTS");
            }
        }
    }

    private SequenceRecomposition() {}

    static Receipt execute(
            final ColetaTemporalLaboratorySession session,
            final AnalyticScenarioRuntime.Run run,
            final AnalyticScenarioRuntime.Cycle source,
            final DeclaredIntegralInputs input,
            final Map<String, Long> expected,
            final CancellationToken token)
            throws Exception {
        input.verifyFiles(token);
        input.support()
                .apply(
                        session,
                        run.id(),
                        run.expansion(),
                        run.relational(),
                        source.relationalFreight(),
                        input.clock(),
                        token);
        new JdbcRelationalLaboratory(session, input.clock())
                .resolve(run.relational(), UUID.randomUUID(), token);
        final var ids = new LinkedHashMap<UUID, String>();
        final var material = new JdbcAnalyticMaterializations(session, Clock.systemUTC());
        for (final var fact : java.util.List.of("MAT01", "MAT02", "MAT03", "MAT04", "MAT05")) {
            token.throwIfCancellationRequested();
            final var id = UUID.randomUUID();
            ids.put(id, fact);
            final var request =
                    new AnalyticMaterializationRequest(
                            run.id(),
                            id,
                            input.references().revision(),
                            ExecutionMode.BACKFILL,
                            true,
                            input.start(),
                            input.end());
            switch (fact) {
                case "MAT01" -> material.freight(request, token);
                case "MAT02" -> material.collectors(request, token);
                case "MAT05" -> material.manifests(request, token);
                case "MAT03" ->
                        new JdbcExpansionMaterializations(session, input.clock())
                                .revenue(
                                        run.expansion(),
                                        id,
                                        input.references().revision(),
                                        ExecutionMode.BACKFILL,
                                        true,
                                        input.start(),
                                        input.end());
                case "MAT04" ->
                        new JdbcExpansionMaterializations(session, input.clock())
                                .invoices(
                                        run.expansion(),
                                        id,
                                        input.references().revision(),
                                        ExecutionMode.BACKFILL,
                                        true,
                                        input.start(),
                                        input.end());
                default -> throw new IllegalArgumentException("SEQUENCE_RECOMPOSITION_FACT");
            }
        }
        return new Receipt(ids, expected);
    }
}
