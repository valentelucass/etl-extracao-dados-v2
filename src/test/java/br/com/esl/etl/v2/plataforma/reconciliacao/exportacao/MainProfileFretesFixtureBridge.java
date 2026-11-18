package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;

/** Test-only access to the existing authored fixture; no retained business input. */
public final class MainProfileFretesFixtureBridge {
    private MainProfileFretesFixtureBridge() {}

    public static ObjectNode pair(final Path directory, final boolean difference) throws Exception {
        final var fixture = new ExportPairFixture(directory);
        if (difference) {
            fixture.uiBook = ExportPairFixture.book(false, false, false, "1.100000000000001");
        }
        return fixture.manifest();
    }
}
