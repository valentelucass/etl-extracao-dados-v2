package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.modulos.fretes.aplicacao.FreightAnalyticAttributesMapper;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticDimensionBinding.Entity;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticFiscalAttribute;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticFreightRelationBinding;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticFreightRelationBinding.Kind;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticManifestState;
import br.com.esl.etl.v2.plataforma.analitico.FreightSupplementObservation;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticCollectionSupplements;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticFiscalAttributes;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticFixtureBindings;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticFreightRelations;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticManifestCompositions;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticManifestState;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcFreightAnalyticAttributes;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Explicit fixture crosswalks and attributes, batched against the actual captured provenance. */
public final class AnalyticScenarioEnrichment {
    private final ColetaTemporalLaboratorySession session;
    private final JdbcAnalyticFixtureBindings sources;

    public AnalyticScenarioEnrichment(final ColetaTemporalLaboratorySession session) {
        this.session = Objects.requireNonNull(session);
        sources = new JdbcAnalyticFixtureBindings(session);
    }

    public void apply(
            final UUID run,
            final UUID expansion,
            final UUID manifest,
            final UUID relationalFreight,
            final int revision,
            final boolean correction,
            final CancellationToken token)
            throws SQLException {
        freight(run, manifest, relationalFreight, revision, correction, token);
        collections(run, revision, token);
        long after = 0;
        while (true) {
            token.throwIfCancellationRequested();
            final var batch = sources.fiscalSourcesPage(expansion, after, 64);
            if (batch.isEmpty()) {
                break;
            }
            final var attributes = new ArrayList<AnalyticFiscalAttribute>(64);
            for (final var source : batch) {
                attributes.add(
                        new AnalyticFiscalAttribute(
                                source.component(),
                                source.execution(),
                                revision,
                                "SERIE-SINTETICA"));
            }
            new JdbcAnalyticFiscalAttributes(session).bindBatch(run, attributes, token);
            after = batch.get(batch.size() - 1).component();
        }
    }

    private void freight(
            final UUID run,
            final UUID manifest,
            final UUID relationalFreight,
            final int revision,
            final boolean correction,
            final CancellationToken token)
            throws SQLException {
        final var attributes =
                new FreightAnalyticAttributesMapper()
                        .map(AnalyticFreightScenarioData.load(revision, correction));
        String after = "";
        while (true) {
            token.throwIfCancellationRequested();
            final var batch = sources.sourcesPage(run, Entity.FRETE, after, 6);
            if (batch.isEmpty()) {
                break;
            }
            // At most six source rows/executions exist in this batch; no universe is retained.
            final Map<UUID, List<FreightSupplementObservation>> grouped = new LinkedHashMap<>();
            final var relations = new ArrayList<AnalyticFreightRelationBinding>(12);
            final var states = new ArrayList<AnalyticManifestState>(6);
            final var seals = new ArrayList<JdbcAnalyticManifestCompositions.Declaration>(6);
            for (final var source : batch) {
                final long root = Long.parseLong(source.key().substring(8)) - 300000;
                if (root < 1 || root > 1024) {
                    throw new SQLException("ANA_SCENARIO_FREIGHT_OUTSIDE_DECLARED_FIXTURE");
                }
                final String manifestKey = "INTEGER:" + root;
                grouped.computeIfAbsent(source.execution(), ignored -> new ArrayList<>(6))
                        .add(
                                new FreightSupplementObservation(
                                        source.key(),
                                        revision,
                                        attributes,
                                        "synthetic-composed-freight-v1"));
                relations.add(
                        new AnalyticFreightRelationBinding(
                                Kind.DIRECT,
                                manifestKey,
                                manifest,
                                source.key(),
                                source.execution(),
                                revision,
                                true,
                                null));
                relations.add(
                        new AnalyticFreightRelationBinding(
                                Kind.CROSSWALK,
                                source.key(),
                                relationalFreight,
                                source.key(),
                                source.execution(),
                                revision,
                                true,
                                null));
                states.add(new AnalyticManifestState(manifestKey, manifest, revision, true, false));
                seals.add(
                        new JdbcAnalyticManifestCompositions.Declaration(
                                manifestKey, manifest, revision, 1));
            }
            for (final var group : grouped.entrySet()) {
                new JdbcFreightAnalyticAttributes(session)
                        .captureBatch(run, group.getKey(), group.getValue(), token);
            }
            new JdbcAnalyticManifestState(session).bindBatch(run, states, token);
            new JdbcAnalyticFreightRelations(session).bindBatch(run, relations, token);
            new JdbcAnalyticManifestCompositions(session).seal(run, seals, token);
            after = batch.get(batch.size() - 1).key();
        }
    }

    private void collections(final UUID run, final int revision, final CancellationToken token)
            throws SQLException {
        String after = "";
        final var attributes = AnalyticCollectionsFixtures.supplement();
        final String user = "STRING:" + AnalyticUsersFixtures.identifier(run, 0);
        while (true) {
            token.throwIfCancellationRequested();
            final var batch = sources.sourcesPage(run, Entity.COL, after, 6);
            if (batch.isEmpty()) {
                break;
            }
            final var bindings = new ArrayList<JdbcAnalyticCollectionSupplements.Binding>(6);
            for (final var source : batch) {
                bindings.add(
                        new JdbcAnalyticCollectionSupplements.Binding(
                                source.key(),
                                source.execution(),
                                revision,
                                attributes,
                                user,
                                user));
            }
            new JdbcAnalyticCollectionSupplements(session).bind(run, bindings, token);
            after = batch.get(batch.size() - 1).key();
        }
    }
}
