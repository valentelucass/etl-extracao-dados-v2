package br.com.esl.etl.v2.plataforma.qualificacao;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/** No generic extension/directory whitelist: each control member has a declared owner and role. */
public final class QualificationControlInventory {
    private static final Set<String> ROOT =
            Set.of(
                    "campaign.json",
                    "configuration.json",
                    "campaign-result.json",
                    "controller.lock");
    private static final Set<String> CASE =
            Set.of(
                    "intent.json",
                    "process.json",
                    "receipt.json",
                    "barrier.json",
                    "cancel.json",
                    "stdout.log",
                    "stderr.log",
                    "owner.json",
                    "baseline.json",
                    "reconciliation.json");

    private QualificationControlInventory() {}

    public static void verify(
            final QualificationControlFiles files, final QualificationCampaign campaign)
            throws IOException {
        QualificationControlFiles.directory(files.root());
        final var cases = new HashSet<String>();
        campaign.cases().forEach(item -> cases.add("case-" + item.id()));
        try (var paths = Files.list(files.root())) {
            final var iterator = paths.iterator();
            int visited = 0;
            while (iterator.hasNext()) {
                final var path = iterator.next();
                if (++visited > 75) {
                    throw new IllegalArgumentException("QUAL_CONTROL_ROOT_LIMIT");
                }
                final String name = path.getFileName().toString();
                if (name.equals("journal")) {
                    QualificationControlFiles.directory(path);
                } else if (cases.contains(name)) {
                    attempt(path);
                } else {
                    file(path, ROOT);
                }
            }
        }
    }

    private static void attempt(final Path directory) throws IOException {
        QualificationControlFiles.directory(directory);
        try (var paths = Files.list(directory)) {
            final var iterator = paths.iterator();
            int visited = 0;
            while (iterator.hasNext()) {
                if (++visited > 20) {
                    throw new IllegalArgumentException("QUAL_CONTROL_CASE_LIMIT");
                }
                file(iterator.next(), CASE);
            }
        }
    }

    private static void file(final Path path, final Set<String> allowed) throws IOException {
        QualificationJson.regular(path);
        final String name = path.getFileName().toString();
        if (name.endsWith(".json.reserved")
                && allowed.contains(name.substring(0, name.length() - 9))) {
            if (Files.size(path) != 0) {
                throw new IllegalArgumentException("QUAL_CONTROL_RESERVATION_CONTENT");
            }
        } else if (!allowed.contains(name) || Files.size(path) > 1048576) {
            throw new IllegalArgumentException("QUAL_CONTROL_UNDECLARED_OR_TRUNCATED_FILE");
        }
    }
}
