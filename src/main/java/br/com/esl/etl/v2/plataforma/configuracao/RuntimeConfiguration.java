package br.com.esl.etl.v2.plataforma.configuracao;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;

/** Grafo de configuração já validado que pode ser entregue ao único composition root. */
public record RuntimeConfiguration(
        RuntimeEnvironment environment,
        ZoneId businessZone,
        Clock clock,
        Optional<DataExportSourceConfiguration> dataExport,
        Optional<GraphQlSourceConfiguration> graphQl,
        ShadowStorageProperties shadowStorage) {

    public RuntimeConfiguration {
        environment = Objects.requireNonNull(environment, "O ambiente é obrigatório.");
        businessZone = validateIanaZone(businessZone);
        clock = Objects.requireNonNull(clock, "O relógio é obrigatório.");
        dataExport =
                Objects.requireNonNull(dataExport, "A configuração Data Export é obrigatória.");
        graphQl = Objects.requireNonNull(graphQl, "A configuração GraphQL é obrigatória.");
        shadowStorage =
                Objects.requireNonNull(shadowStorage, "A configuração de sombra é obrigatória.");
    }

    private static ZoneId validateIanaZone(final ZoneId value) {
        Objects.requireNonNull(value, "O timezone de negócio é obrigatório.");
        if (!ZoneId.getAvailableZoneIds().contains(value.getId())) {
            throw new IllegalArgumentException(
                    "O timezone de negócio deve usar um identificador IANA.");
        }
        return value;
    }
}
