package com.huawei.ascend.runtime.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the Run save discipline: production code persists a Run only through
 * the two DFA-safe construction paths — {@code Run.create(...)} for the initial
 * PENDING record and {@code <run>.withStatus(...)} (which validates the
 * {@code RunStateMachine} edge) for every transition. Saving a hand-built
 * {@code new Run(...)} from outside the run package would bypass the DFA and is
 * the defect family this guard exists for.
 *
 * <p>The guard scans every {@code .save(} call in production files that import
 * {@code RunRepository}, with whitespace normalised so multi-line calls are
 * seen. It also asserts it actually FOUND the kernel's known save sites — a
 * guard that silently matches nothing is worse than no guard.
 */
class RunRepositorySaveGuardTest {

    private static final String RUN_REPOSITORY_IMPORT =
            "import com.huawei.ascend.runtime.run.RunRepository;";

    /** A receiver followed by .save( — e.g. {@code runRepository.save(}. */
    private static final Pattern SAVE_CALL = Pattern.compile("[A-Za-z_$][\\w$]*\\.save\\(");

    /** DFA-safe arguments: Run.create(...) or <expr>.withStatus(...). */
    private static final Pattern ALLOWED_ARGUMENT =
            Pattern.compile("^\\s*(Run\\.create\\(|[\\w$]+\\.withStatus\\()");

    @Test
    void productionRunSavesGoThroughDfaValidatedConstructionOnly() throws IOException {
        Path mainJava = Path.of("src/main/java").toAbsolutePath();
        List<String> violations = new ArrayList<>();
        List<String> scannedSaveSites = new ArrayList<>();

        try (var files = Files.walk(mainJava)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> scan(mainJava, path, violations, scannedSaveSites));
        }

        assertThat(violations).isEmpty();
        // Anti-vacuity: the A2A executor is the kernel's Run-persisting seam. If
        // these sites move, point the guard at the new seam — never let it pass
        // while seeing zero save calls.
        assertThat(scannedSaveSites)
                .as("save sites the guard actually saw — empty means the guard went blind")
                .anyMatch(site -> site.contains("A2aAgentExecutor.java"));
    }

    private static void scan(Path mainJava, Path path, List<String> violations,
                             List<String> scannedSaveSites) {
        String relative = mainJava.relativize(path).toString().replace('\\', '/');
        // The run package owns the repository implementations; their internal
        // saves (version bumps etc.) are the mechanism, not a bypass of it.
        if (relative.startsWith("com/huawei/ascend/runtime/run/")) {
            return;
        }
        String source;
        try {
            source = Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        if (!source.contains(RUN_REPOSITORY_IMPORT)) {
            return;
        }
        String normalized = source.replaceAll("\\s+", " ");
        Matcher matcher = SAVE_CALL.matcher(normalized);
        while (matcher.find()) {
            String argument = normalized.substring(matcher.end(),
                    Math.min(normalized.length(), matcher.end() + 120));
            String site = relative + " → " + matcher.group() + snippet(argument);
            scannedSaveSites.add(site);
            if (!ALLOWED_ARGUMENT.matcher(argument).find()) {
                violations.add(site);
            }
        }
    }

    private static String snippet(String argument) {
        return argument.substring(0, Math.min(argument.length(), 60));
    }
}
