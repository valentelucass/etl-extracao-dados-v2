package br.com.esl.etl.v2.contratos.caracterizacao.qfnd02;

import static br.com.esl.etl.v2.contratos.caracterizacao.qfnd02.Qfnd02Vocabulary.Outcome;
import static br.com.esl.etl.v2.contratos.caracterizacao.qfnd02.Qfnd02Vocabulary.Presence;
import static br.com.esl.etl.v2.contratos.caracterizacao.qfnd02.Qfnd02Vocabulary.Reason;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Avaliador puro e fail-closed de fixtures sintéticas. */
public final class Qfnd02Evaluator {

    private final Map<String, ResourceBinding> resourceBindings;

    Qfnd02Evaluator(final Map<String, ResourceBinding> resourceBindings) {
        this.resourceBindings = Map.copyOf(Objects.requireNonNull(resourceBindings));
    }

    public Qfnd02Result evaluate(final Qfnd02Profile profile, final Qfnd02Observation observation) {
        final ResourceBinding resourceBinding = resourceBindings.get(profile.profileId());
        if (resourceBinding == null) {
            throw new IllegalArgumentException("Perfil sem binding bruto Q-FND-02.");
        }
        final EnumSet<Reason> reasons = EnumSet.noneOf(Reason.class);
        if (!profile.profileId().equals(observation.profileId())) {
            reasons.add(Reason.PROFILE_BINDING_MISMATCH);
        }
        if (observation.providerEvidence() != profile.providerEvidence()) {
            reasons.add(Reason.PROVIDER_EVIDENCE_DRIFT);
        }
        final Map<String, Qfnd02Observation.ChannelObservation> observations =
                observation.channels().stream()
                        .collect(
                                Collectors.toUnmodifiableMap(
                                        Qfnd02Observation.ChannelObservation::channelId,
                                        Function.identity()));
        if (!observations
                .keySet()
                .equals(
                        profile.channels().stream()
                                .map(Qfnd02Profile.Channel::channelId)
                                .collect(Collectors.toUnmodifiableSet()))) {
            reasons.add(Reason.CHANNEL_SET_DRIFT);
        }
        for (final Qfnd02Profile.Channel channel : profile.channels()) {
            final Qfnd02Observation.ChannelObservation observed =
                    observations.get(channel.channelId());
            if (observed == null) {
                continue;
            }
            if (!channel.expectedPaths().equals(observed.observedPaths())) {
                reasons.add(Reason.PATH_DRIFT);
            }
            if (!channel.fieldTypes().equals(observed.observedTypes())) {
                reasons.add(Reason.FIELD_TYPE_DRIFT);
            }
            if (!channel.presenceByPath().equals(observed.presenceByPath())
                    || observed.presenceByPath().values().stream()
                            .anyMatch(states -> !states.equals(EnumSet.allOf(Presence.class)))) {
                reasons.add(Reason.PRESENCE_MODEL_INCOMPLETE);
            }
            if (!channel.sourceKey().path().equals(observed.sourceKeyPath())
                    || channel.sourceKey().typeTagged() != observed.sourceKeyTypeTagged()) {
                reasons.add(Reason.SOURCE_KEY_DRIFT);
            }
            if (observed.shortPageIsTerminal()) {
                reasons.add(Reason.PAGINATION_DRIFT);
            }
            if (observed.completenessProven()
                    || observed.snapshotProven()
                    || observed.relationshipEnabled()
                    || observed.publicationEnabled()
                    || observed.sweepEnabled()) {
                reasons.add(Reason.UNSAFE_CAPABILITY);
            }
            if (channel.rootOrFreshnessAuthority() != observed.rootOrFreshnessAuthority()) {
                reasons.add(Reason.AUTHORITY_DRIFT);
            }
        }
        return new Qfnd02Result(
                profile.profileId(),
                resourceBinding.profileSha256(),
                resourceBinding.fixtureSha256(),
                reasons.isEmpty() ? Outcome.SYNTHETIC_STRUCTURE_ACCEPTED : Outcome.FAIL_CLOSED,
                profile.profileStatus(),
                profile.gateStatus(),
                profile.providerEvidence(),
                false,
                reasons);
    }

    record ResourceBinding(String profileSha256, String fixtureSha256) {
        private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

        ResourceBinding {
            if (profileSha256 == null
                    || fixtureSha256 == null
                    || !SHA256.matcher(profileSha256).matches()
                    || !SHA256.matcher(fixtureSha256).matches()) {
                throw new IllegalArgumentException("Binding bruto Q-FND-02 inválido.");
            }
        }
    }
}
