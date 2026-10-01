package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.qualificacao.QualificationConfiguration;
import java.util.ArrayList;
import java.util.Map;

/** Both independent local shadow gates must be explicit before any SQL evidence is read. */
final class QualificationSqlOptIn {
    private QualificationSqlOptIn() {}

    static void require(final boolean enabled, final boolean profileActive) {
        if (!enabled || !profileActive) {
            throw new IllegalArgumentException("QUAL_SQL_OPT_IN_REQUIRED");
        }
    }

    static void projectWorkerEnvironment(
            final Map<String, String> environment,
            final QualificationConfiguration configuration,
            final String validatedUrl) {
        final String target = configuration.validatedJdbcUrl(validatedUrl);
        for (final String name : new ArrayList<>(environment.keySet())) {
            if (name.regionMatches(true, 0, "V2_", 0, 3)) {
                environment.remove(name);
            }
        }
        environment.put("V2_SHADOW_JDBC_URL", target);
    }
}
