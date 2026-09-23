package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlContract;
import java.util.HashSet;
import java.util.Set;

/** Closed options are validated before any connection or fixture read. */
record AnalyticLaboratoryOptions(
        String command,
        int roots,
        int pageSize,
        int limit,
        AnalyticSqlContract contract,
        AnalyticScenarioRuntime.Fault fault) {
    static AnalyticLaboratoryOptions parse(final String[] arguments) {
        if (arguments == null
                || arguments.length < 2
                || arguments.length > 7
                || arguments[0] == null
                || !Set.of("scenario", "recompose", "replay", "status", "query")
                        .contains(arguments[0])
                || !"--synthetic-analytic-lab".equals(arguments[1])) {
            throw new IllegalArgumentException("ANA_LAB_FLAGS");
        }
        int roots = 2, pageSize = 16, limit = 4096;
        AnalyticSqlContract contract = null;
        var fault = AnalyticScenarioRuntime.Fault.NONE;
        final var seen = new HashSet<String>();
        for (int index = 2; index < arguments.length; index++) {
            if (arguments[index] == null || arguments[index].length() > 80) {
                throw new IllegalArgumentException("ANA_LAB_OPTION_BOUND");
            }
            final String[] option = arguments[index].split("=", -1);
            if (option.length != 2 || !seen.add(option[0])) {
                throw new IllegalArgumentException("ANA_LAB_OPTION_DUPLICATE");
            }
            switch (option[0]) {
                case "--contract" -> {
                    if (!option[1].matches("SQL-(0[1-9]|1[0-9])")) {
                        throw new IllegalArgumentException("ANA_LAB_CONTRACT");
                    }
                    contract = AnalyticSqlContract.valueOf(option[1].replace('-', '_'));
                }
                case "--fault" -> fault = AnalyticScenarioRuntime.Fault.valueOf(option[1]);
                case "--roots" -> roots = number(option[1]);
                case "--page-size" -> pageSize = number(option[1]);
                case "--limit" -> limit = number(option[1]);
                default -> throw new IllegalArgumentException("ANA_LAB_UNKNOWN_OPTION");
            }
        }
        if (roots < 2
                || roots > 480
                || pageSize < 1
                || pageSize > 16
                || limit < 1
                || limit > 4096
                || arguments[0].equals("query") != (contract != null)
                || fault != AnalyticScenarioRuntime.Fault.NONE
                        && !Set.of("scenario", "status").contains(arguments[0])) {
            throw new IllegalArgumentException("ANA_LAB_SCOPE");
        }
        return new AnalyticLaboratoryOptions(arguments[0], roots, pageSize, limit, contract, fault);
    }

    private static int number(final String text) {
        if (!text.matches("0|[1-9][0-9]{0,6}")) {
            throw new IllegalArgumentException("ANA_LAB_NUMBER");
        }
        return Integer.parseInt(text);
    }
}
