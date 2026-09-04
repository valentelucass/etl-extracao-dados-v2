package br.com.esl.etl.v2.contratos;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Grava somente o resumo sanitizado em diretório de build, nunca no repositório versionado. */
public final class ContractEvidenceWriter {

    private final Path evidenceRoot;
    private final ObjectMapper objectMapper;

    public ContractEvidenceWriter() {
        this(Path.of("target"), new ObjectMapper());
    }

    static ContractEvidenceWriter forTargetDirectory(final Path targetDirectory) {
        return new ContractEvidenceWriter(targetDirectory, new ObjectMapper());
    }

    private ContractEvidenceWriter(final Path targetDirectory, final ObjectMapper objectMapper) {
        Objects.requireNonNull(targetDirectory, "O diretório de build é obrigatório.");
        if (targetDirectory.getFileName() == null
                || !"target".equals(targetDirectory.getFileName().toString())) {
            throw new IllegalArgumentException(
                    "A evidência de contrato só pode ser gravada sob o diretório target.");
        }
        this.evidenceRoot =
                targetDirectory.toAbsolutePath().normalize().resolve("contract-evidence");
        this.objectMapper =
                Objects.requireNonNull(objectMapper, "O ObjectMapper é obrigatório.")
                        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    public Path write(final ContractEvidenceSummary summary) {
        Objects.requireNonNull(summary, "O resumo de evidência é obrigatório.");
        final Path runDirectory = evidenceRoot.resolve(summary.runId()).normalize();
        if (!runDirectory.startsWith(evidenceRoot)) {
            throw new IllegalArgumentException(
                    "O identificador da execução não pode sair do diretório de evidência.");
        }
        final Path summaryFile = runDirectory.resolve("summary.json");
        try {
            Files.createDirectories(runDirectory);
            objectMapper.writeValue(summaryFile.toFile(), summary);
            return summaryFile;
        } catch (final IOException exception) {
            throw new IllegalStateException(
                    "Não foi possível gravar o resumo sanitizado de contrato.");
        }
    }
}
