package br.com.esl.etl.v2.contratos.caracterizacao.qfnd02;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Registro fechado de dois perfis de entidade e três canais de fonte. */
public final class Qfnd02Registry {

    private static final String ROOT = "/contracts/v2-012/extensions/q-fnd-02/";
    private static final Map<String, String> RESOURCE_HASHES =
            Map.of(
                    "profiles/fretes-6389.profile.json",
                    "e58300114443f94673545a12d8c8ece983725bfaa99be37c855df90b99451ea9",
                    "profiles/localizacao-8656.profile.json",
                    "d557f2bbfaea0e130aded1f991b7a6c96e9850162160b83832e4b3e32b10a37f",
                    "fixtures/fretes-6389.synthetic.json",
                    "a1ce0672197da66075e8bdce4ba659438f304aa41c5b3bd067f3d1c36a227647",
                    "fixtures/localizacao-8656.synthetic.json",
                    "1162b138240086e0b1b9eecfcda7d6a80d1e65a82c7a28dea8427161d06402f3");
    private static final Map<String, Qfnd02Evaluator.ResourceBinding> PROFILE_BINDINGS =
            Map.of(
                    "V2_012_FRETES_6389",
                    new Qfnd02Evaluator.ResourceBinding(
                            RESOURCE_HASHES.get("profiles/fretes-6389.profile.json"),
                            RESOURCE_HASHES.get("fixtures/fretes-6389.synthetic.json")),
                    "V2_012_LOCALIZACAO_8656_DATA_EXPORT",
                    new Qfnd02Evaluator.ResourceBinding(
                            RESOURCE_HASHES.get("profiles/localizacao-8656.profile.json"),
                            RESOURCE_HASHES.get("fixtures/localizacao-8656.synthetic.json")));
    private final Map<String, Qfnd02Profile> profiles;
    private final Map<String, Qfnd02Observation> observations;
    private final Qfnd02Evaluator evaluator;

    private Qfnd02Registry(
            final List<Qfnd02Profile> profiles,
            final List<Qfnd02Observation> observations,
            final Qfnd02Evaluator evaluator) {
        this.profiles = byProfileId(profiles, Qfnd02Profile::profileId);
        this.observations = byProfileId(observations, Qfnd02Observation::profileId);
        this.evaluator = evaluator;
        if (this.profiles.size() != 2
                || this.observations.size() != 2
                || !this.profiles.keySet().equals(this.observations.keySet())
                || this.profiles.values().stream()
                                .mapToInt(profile -> profile.channels().size())
                                .sum()
                        != 3) {
            throw new IllegalArgumentException("Registro Q-FND-02 incompleto.");
        }
    }

    public static Qfnd02Registry loadDefault() {
        RESOURCE_HASHES.forEach(Qfnd02Registry::verifyResourceHash);
        final Qfnd02Loader loader = new Qfnd02Loader();
        return new Qfnd02Registry(
                List.of(
                        loader.loadProfileResource(ROOT + "profiles/fretes-6389.profile.json"),
                        loader.loadProfileResource(
                                ROOT + "profiles/localizacao-8656.profile.json")),
                List.of(
                        loader.loadFixtureResource(ROOT + "fixtures/fretes-6389.synthetic.json"),
                        loader.loadFixtureResource(
                                ROOT + "fixtures/localizacao-8656.synthetic.json")),
                new Qfnd02Evaluator(PROFILE_BINDINGS));
    }

    private static void verifyResourceHash(final String relative, final String expected) {
        try (InputStream stream = Qfnd02Registry.class.getResourceAsStream(ROOT + relative)) {
            if (stream == null) {
                throw new IllegalArgumentException("Recurso Q-FND-02 ausente.");
            }
            final String actual =
                    HexFormat.of()
                            .formatHex(
                                    MessageDigest.getInstance("SHA-256")
                                            .digest(stream.readAllBytes()));
            if (!actual.equals(expected)) {
                throw new IllegalArgumentException("Binding criptográfico Q-FND-02 divergente.");
            }
        } catch (final IOException | NoSuchAlgorithmException exception) {
            throw new IllegalArgumentException("Falha ao verificar binding Q-FND-02.", exception);
        }
    }

    public List<Qfnd02Profile> profiles() {
        return profiles.values().stream()
                .sorted(Comparator.comparing(Qfnd02Profile::profileId))
                .toList();
    }

    public Qfnd02Profile profile(final String profileId) {
        final Qfnd02Profile profile = profiles.get(profileId);
        if (profile == null) {
            throw new IllegalArgumentException("Perfil Q-FND-02 desconhecido.");
        }
        return profile;
    }

    public Qfnd02Observation observation(final String profileId) {
        final Qfnd02Observation observation = observations.get(profileId);
        if (observation == null) {
            throw new IllegalArgumentException("Fixture Q-FND-02 desconhecida.");
        }
        return observation;
    }

    public Qfnd02Evaluator evaluator(final String profileId) {
        profile(profileId);
        return evaluator;
    }

    public String profileSha256(final String profileId) {
        profile(profileId);
        return PROFILE_BINDINGS.get(profileId).profileSha256();
    }

    public String fixtureSha256(final String profileId) {
        profile(profileId);
        return PROFILE_BINDINGS.get(profileId).fixtureSha256();
    }

    private static <T> Map<String, T> byProfileId(
            final List<T> values, final Function<T, String> id) {
        final LinkedHashMap<String, T> result =
                values.stream()
                        .sorted(Comparator.comparing(id))
                        .collect(
                                Collectors.toMap(
                                        id,
                                        Function.identity(),
                                        (left, right) -> {
                                            throw new IllegalArgumentException(
                                                    "Binding Q-FND-02 duplicado.");
                                        },
                                        LinkedHashMap::new));
        return Map.copyOf(result);
    }
}
