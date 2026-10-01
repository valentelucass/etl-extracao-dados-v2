package br.com.esl.etl.v2.bootstrap;

import java.io.PrintStream;
import java.nio.file.Path;

/** Entrada separada do Main operacional; somente valida um plano ate autorizacao nominal. */
public final class Coletas6908PilotMain {
    private Coletas6908PilotMain() {}

    public static void main(final String[] args) {
        final int status = run(args, System.out, System.err);
        if (status != 0) {
            System.exit(status);
        }
    }

    static int run(final String[] args, final PrintStream out, final PrintStream error) {
        if (args.length != 2 || !"--plan".equals(args[0])) {
            error.println("COL_PILOT_EXECUTION_NOT_AUTHORIZED");
            return 3;
        }
        try {
            final Coletas6908PilotPlan plan = Coletas6908PilotPlan.read(Path.of(args[1]));
            out.println(
                    "COL_PILOT_PLAN_VALID template=6908 firstPage=1 maxPages="
                            + plan.maxPages()
                            + " per="
                            + plan.per()
                            + " maxPhysicalRows="
                            + plan.maxPhysicalRows()
                            + " maxResponseBytes="
                            + plan.maxResponseBytes()
                            + " deadlineSeconds="
                            + plan.deadline().toSeconds()
                            + " provenance=BOUNDED_TRAVERSAL_OBSERVED_ONLY execution=DISABLED");
            return 0;
        } catch (final RuntimeException failure) {
            error.println("COL_PILOT_INVALID_PLAN");
            return 2;
        }
    }
}
