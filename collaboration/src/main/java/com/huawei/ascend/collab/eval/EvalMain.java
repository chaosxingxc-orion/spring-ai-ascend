package com.huawei.ascend.collab.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.huawei.ascend.collab.eval.EvalRunner.CaseResult;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Eval CLI: generates the collaboration eval set to JSON, then loads that JSON
 * back and runs it (full round-trip), printing a pass/fail report.
 *
 * <pre>
 *   ./collaboration/eval.sh            # generate + run + report (default)
 *   ./collaboration/eval.sh generate   # only (re)generate the eval set JSON
 *   ./collaboration/eval.sh run        # only run the existing eval set JSON
 * </pre>
 */
public final class EvalMain {

    private static final Path SET_PATH =
            Path.of("collaboration/src/main/resources/eval/collaboration-eval-set.json");
    private static final Path RESULTS_PATH = Path.of("collaboration/eval-results.json");

    public static void main(String[] args) throws Exception {
        String cmd = args.length > 0 ? args[0] : "all";
        if (!cmd.equals("generate") && !cmd.equals("run") && !cmd.equals("all")) {
            // A mistyped command (e.g. "EvalMain typo") must not silently exit 0 and
            // look like a clean run — fail loudly so the harness/operator notices.
            System.err.println("unknown command: " + cmd);
            System.err.println("usage: eval.sh [generate|run|all]   (default: all)");
            System.exit(2);
        }
        ObjectMapper om = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

        if (cmd.equals("generate") || cmd.equals("all")) {
            List<EvalScenario> scenarios = EvalSetGenerator.generate();
            Files.createDirectories(SET_PATH.getParent());
            om.writeValue(SET_PATH.toFile(), scenarios);
            System.out.println("✍ 评测集已生成: " + SET_PATH + "  (" + scenarios.size() + " 个场景)");
        }

        if (cmd.equals("run") || cmd.equals("all")) {
            File f = SET_PATH.toFile();
            List<EvalScenario> scenarios = f.exists()
                    ? List.of(om.readValue(f, EvalScenario[].class))   // load the generated set back (round-trip)
                    : EvalSetGenerator.generate();
            System.out.println("▶ 运行多任务协同评测集  (" + scenarios.size() + " 个场景)\n");

            List<CaseResult> results = EvalRunner.runAll(scenarios);
            int passed = 0;
            for (CaseResult r : results) {
                System.out.printf("  %s %-26s tasks=%d completed=%d %s%n",
                        r.passed() ? "✅" : "❌", r.scenario(), r.tasks(), r.completed(),
                        r.passed() ? "" : r.failures());
                if (r.passed()) {
                    passed++;
                }
            }
            System.out.printf("%n评测结果: %d/%d 场景通过%n", passed, results.size());
            om.writeValue(RESULTS_PATH.toFile(), results);
            System.out.println("结果明细: " + RESULTS_PATH);

            if (passed < results.size()) {
                System.exit(1);
            }
        }
    }
}
