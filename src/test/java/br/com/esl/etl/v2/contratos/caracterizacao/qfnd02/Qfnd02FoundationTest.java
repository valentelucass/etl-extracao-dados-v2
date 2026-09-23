package br.com.esl.etl.v2.contratos.caracterizacao.qfnd02;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class Qfnd02FoundationTest {

    @Test
    void loadsTwoEntityProfilesAndThreeIsolatedChannelsWithoutExecutingAnOracle() {
        final Qfnd02Registry registry = Qfnd02Registry.loadDefault();

        assertEquals(2, registry.profiles().size());
        assertEquals(
                Set.of("V2_012_FRETES_6389", "V2_012_LOCALIZACAO_8656_DATA_EXPORT"),
                registry.profiles().stream()
                        .map(Qfnd02Profile::profileId)
                        .collect(Collectors.toUnmodifiableSet()));
        assertEquals(
                3,
                registry.profiles().stream().mapToInt(profile -> profile.channels().size()).sum());

        for (final Qfnd02Profile profile : registry.profiles()) {
            final Qfnd02Result result =
                    registry.evaluator(profile.profileId())
                            .evaluate(profile, registry.observation(profile.profileId()));
            assertEquals(Qfnd02Vocabulary.Outcome.SYNTHETIC_STRUCTURE_ACCEPTED, result.outcome());
            assertEquals(
                    Qfnd02Vocabulary.ProfileStatus.PREPARED_NOT_EXECUTED, result.profileStatus());
            assertEquals(Qfnd02Vocabulary.GateStatus.ORACLE_REQUIRED, result.gateStatus());
            assertEquals(Qfnd02Vocabulary.ProviderEvidence.NOT_EXECUTED, result.providerEvidence());
            assertFalse(result.providerPass());
            assertTrue(result.reasons().isEmpty());
        }
    }

    @Test
    void keepsFreightAndLocationContractsLiteralAndIndependent() {
        final Qfnd02Registry registry = Qfnd02Registry.loadDefault();
        final Qfnd02Profile freight = registry.profile("V2_012_FRETES_6389");
        final Qfnd02Profile location = registry.profile("V2_012_LOCALIZACAO_8656_DATA_EXPORT");

        final Qfnd02Profile.Channel dataExport = freight.channel("FRETES_DATA_EXPORT_6389");
        final Qfnd02Profile.Channel sidecar = freight.channel("FRETES_GRAPHQL_SIDECAR");
        assertEquals("/id", dataExport.sourceKey().path());
        assertEquals(7, dataExport.expectedPaths().size());
        assertEquals(
                Set.of(
                        "/id",
                        "/updated_at",
                        "/reference_number",
                        "/fit_p_m_pck_sequence_code",
                        "/corporation_sequence_number",
                        "/finished_at",
                        "/fit_dpn_performance_finished_at"),
                dataExport.expectedPaths());
        assertEquals(10, sidecar.expectedPaths().size());
        assertEquals(
                Set.of(
                        "/freight/edges/node/id",
                        "/freight/edges/node/accountingCreditId",
                        "/freight/edges/node/accountingCreditInstallmentId",
                        "/freight/edges/node/referenceNumber",
                        "/freight/edges/node/cte/key",
                        "/freight/edges/node/total",
                        "/freight/edges/node/corporationSequenceNumber",
                        "/freight/edges/node/pickItemId",
                        "/freight/pageInfo/hasNextPage",
                        "/freight/pageInfo/endCursor"),
                sidecar.expectedPaths());
        assertTrue(sidecar.sourceKey().path().isEmpty());
        assertFalse(sidecar.rootOrFreshnessAuthority());
        assertTrue(sidecar.publicationBlocked());
        assertEquals(Qfnd02Vocabulary.SourceProfile.GRAPHQL_SIDECAR, sidecar.sourceProfile());
        assertEquals("/corporation_sequence_number", location.sourceKey().path());
        assertEquals(17, location.expectedPaths().size());
        assertEquals(
                Set.of(
                        "/corporation_sequence_number",
                        "/type",
                        "/service_at",
                        "/invoices_volumes",
                        "/taxed_weight",
                        "/invoices_value",
                        "/total",
                        "/service_type",
                        "/fit_crn_psn_nickname",
                        "/fit_dpn_delivery_prediction_at",
                        "/fit_dyn_name",
                        "/fit_dyn_drt_nickname",
                        "/fit_fsn_name",
                        "/fit_fln_status",
                        "/fit_fln_cln_nickname",
                        "/fit_o_n_name",
                        "/fit_o_n_drt_nickname"),
                location.expectedPaths());
        assertFalse(location.expectedPaths().contains("/sequence_number"));
        assertEquals("ABSENT_UNSOURCED_LEGACY", location.statusBranchNicknameState());
        assertEquals(
                Set.of("FRE-01", "FRE-02", "FRE-03", "FRE-04", "FRE-05", "FRE-06", "FRE-07"),
                freight.decisionPolicies().keySet());
        assertEquals(
                Set.of("LOC-01", "LOC-02", "LOC-03", "LOC-04", "LOC-05", "LOC-06", "LOC-07"),
                location.decisionPolicies().keySet());
        assertEquals(4, location.numericPolicies().size());
        assertEquals(38, location.numericPolicies().get("/total").precision());
        assertEquals(9, location.numericPolicies().get("/total").scale());
        for (final Qfnd02Profile profile : registry.profiles()) {
            for (final Qfnd02Profile.Channel channel : profile.channels()) {
                assertEquals(channel.expectedPaths(), channel.presenceByPath().keySet());
                assertTrue(
                        channel.presenceByPath().values().stream()
                                .allMatch(
                                        states ->
                                                states.equals(
                                                        Set.of(
                                                                Qfnd02Vocabulary.Presence.ABSENT,
                                                                Qfnd02Vocabulary.Presence.NULL,
                                                                Qfnd02Vocabulary.Presence.VALUE))));
            }
        }
    }
}
