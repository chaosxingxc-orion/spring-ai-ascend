package ascend.springai.service.platform.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enforcer for plan §11 row E17: no source under
 * the platform-side persistence packages (post-Phase-C:
 * {@code agent-service/src/main/.../platform/persistence/..}; pre-Phase-C was
 * {@code agent-platform/persistence/..} per ADR-0078) (or any package using
 * {@code JdbcClient}/{@code JdbcTemplate}) may construct SQL via String
 * concatenation. JPQL / SQL must live in {@code @Query} annotations or in
 * static text blocks; parameter substitution flows through named/positional
 * placeholders, never through string interpolation.
 *
 * <p>This is a deliberately crude heuristic: it flags any line containing
 * SQL keywords ({@code SELECT}, {@code INSERT}, {@code UPDATE}, {@code DELETE},
 * {@code WHERE}) together with the {@code +} concatenation operator inside a
 * string literal. False positives are addressed by extracting the offending
 * string into a constant.
 *
 * <p>Enforcer row: docs/governance/enforcers.yaml#E17.
 */
class NoStringConcatSqlTest {

    private static final Pattern SQL_PLUS = Pattern.compile(
            "\"[^\"]*\\b(SELECT|INSERT|UPDATE|DELETE|WHERE|FROM)\\b[^\"]*\"\\s*\\+");

    @Test
    void no_source_in_persistence_or_idempotency_concatenates_sql_strings() throws IOException {
        Path main = Path.of("src/main/java");
        if (!Files.isDirectory(main)) {
            return; // running outside the platform module (pre-Phase-C: agent-platform)
        }
        List<String> violations = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(main)) {
            stream.filter(p -> p.toString().endsWith(".java"))
                    .filter(NoStringConcatSqlTest::touchesPersistence)
                    .forEach(p -> scan(p, violations));
        }
        assertThat(violations)
                .as("SQL must not be assembled via String + concatenation; enforcer row E17.")
                .isEmpty();
    }

    private static boolean touchesPersistence(Path p) {
        String s = p.toString().replace('\\', '/');
        return s.contains("/persistence/")
                || s.contains("/idempotency/jdbc/")
                || s.contains("/idempotency/JdbcIdempotencyStore");
    }

    private static void scan(Path p, List<String> violations) {
        try {
            String body = Files.readString(p, StandardCharsets.UTF_8);
            Matcher m = SQL_PLUS.matcher(body);
            while (m.find()) {
                int line = countLinesUpTo(body, m.start());
                violations.add(p + ":" + line + " :: " + m.group());
            }
        } catch (IOException e) {
            violations.add(p + " :: read failed: " + e.getMessage());
        }
    }

    private static int countLinesUpTo(String body, int offset) {
        int count = 1;
        for (int i = 0; i < offset && i < body.length(); i++) {
            if (body.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }
}
