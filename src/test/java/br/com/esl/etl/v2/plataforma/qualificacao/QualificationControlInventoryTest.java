package br.com.esl.etl.v2.plataforma.qualificacao;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlContract;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QualificationControlInventoryTest {
    @TempDir Path directory;

    @Test
    void acceptsOnlyDeclaredRootAndCaseFilesAndEmptyReservations() throws Exception {
        final var files = files();
        final var caseRoot = files.attempt("first", true);
        Files.writeString(files.root().resolve("campaign.json"), "{}");
        Files.writeString(caseRoot.resolve("receipt.json"), "{}");
        Files.createFile(caseRoot.resolve("process.json.reserved"));
        assertDoesNotThrow(() -> QualificationControlInventory.verify(files, campaign()));

        Files.writeString(caseRoot.resolve("process.json.reserved"), "nonempty");
        assertThrows(
                IllegalArgumentException.class,
                () -> QualificationControlInventory.verify(files, campaign()));
        Files.delete(caseRoot.resolve("process.json.reserved"));
        Files.writeString(caseRoot.resolve("undeclared.txt"), "x");
        assertThrows(
                IllegalArgumentException.class,
                () -> QualificationControlInventory.verify(files, campaign()));
    }

    @Test
    void rejectsUndeclaredRootAndCaseDirectory() throws Exception {
        final var files = files();
        Files.createDirectory(files.root().resolve("case-other"));
        assertThrows(
                IllegalArgumentException.class,
                () -> QualificationControlInventory.verify(files, campaign()));
    }

    private QualificationControlFiles files() throws Exception {
        final var target = Files.createDirectories(directory.resolve("target"));
        final var payload = Files.createDirectory(target.resolve("payload"));
        return new QualificationControlFiles(payload, target.resolve("control"), true);
    }

    private static QualificationCampaign campaign() {
        final var item =
                new QualificationCampaign.Case(
                        "first",
                        "one",
                        List.of(),
                        QualificationCampaign.Action.SCENARIO,
                        ExecutionMode.BOOTSTRAP,
                        QualificationCampaign.Fault.NONE,
                        QualificationCampaign.Barrier.NONE,
                        List.of(AnalyticSqlContract.SQL_01),
                        Instant.EPOCH,
                        null,
                        null,
                        null,
                        0,
                        1,
                        0,
                        List.of(),
                        QualificationGate.State.PASS_LOCAL);
        return new QualificationCampaign("synthetic", null, 1, 1, 1, List.of(item));
    }
}
