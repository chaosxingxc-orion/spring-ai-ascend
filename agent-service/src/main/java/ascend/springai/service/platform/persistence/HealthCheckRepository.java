package ascend.springai.service.platform.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Minimal repository proving JDBC + Flyway-applied schema reach Postgres.
 *
 * <p>Pings the {@code health_check} table created by V1__init.sql. Returns
 * a non-zero monotonic value on success.</p>
 */
@Repository
public class HealthCheckRepository {

    private final JdbcTemplate jdbcTemplate;

    public HealthCheckRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long pingDb() {
        long start = System.nanoTime();
        Integer one = jdbcTemplate.queryForObject(
                "SELECT 1 FROM health_check WHERE singleton = true",
                Integer.class
        );
        if (one == null || one != 1) {
            throw new IllegalStateException("health_check table missing or corrupt");
        }
        return System.nanoTime() - start;
    }
}
