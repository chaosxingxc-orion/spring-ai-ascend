package com.huawei.ascend.examples.workmate.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.DockerClientFactory;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * W17: validates the real Flyway migration chain (V1..) applies cleanly on a genuine Postgres,
 * and that Hibernate {@code ddl-auto=validate} agrees with the resulting schema.
 *
 * <p>This is the regression guard for DRIFT-4: prior tests used {@code create-drop} with Flyway
 * disabled, so an entity/migration mismatch could pass CI yet break a real (migrated) database.
 *
 * <p>Requires a Docker daemon. When Docker is unavailable the Testcontainers JUnit extension
 * disables the container startup and the class is skipped, so it never breaks Docker-less sandboxes.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIf("dockerAvailable")
class FlywayMigrationIntegrationTest {

    static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("workmate")
                    .withUsername("workmate")
                    .withPassword("workmate");

    private static Path workspaceBase;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        workspaceBase = Files.createTempDirectory("workmate-w17-test-");
        registry.add("workmate.workspace.base-path", () -> workspaceBase.toString());
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        // Real production posture: Flyway owns schema, Hibernate validates.
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Test
    void flywayChainAppliesAndHibernateValidates() throws Exception {
        try (var connection = dataSource.getConnection();
                var rs = connection.getMetaData().getTables(null, null, "run_events", null)) {
            assertThat(rs.next()).as("run_events table must exist after Flyway V1..V7").isTrue();
        }
    }

    @Test
    void runEventsTableIsAppendOnly() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        try (var connection = dataSource.getConnection();
                var ps = connection.prepareStatement(
                        "INSERT INTO run_events (id, session_id, run_id, seq, event_name, payload_json, created_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?, NOW())")) {
            ps.setObject(1, eventId);
            ps.setObject(2, sessionId);
            ps.setString(3, "worm-test");
            ps.setInt(4, 1);
            ps.setString(5, "test.event");
            ps.setString(6, "{\"ok\":true}");
            ps.executeUpdate();
        }

        try (var connection = dataSource.getConnection();
                var ps = connection.prepareStatement("UPDATE run_events SET event_name = 'mutated' WHERE id = ?")) {
            ps.setObject(1, eventId);
            org.assertj.core.api.Assertions.assertThatThrownBy(ps::executeUpdate)
                    .hasMessageContaining("append-only");
        }
    }

    @Test
    void persistenceWorksEndToEndOnPostgres() throws Exception {
        String body = mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"PG migration check\"}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String sessionId = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/messages"))
                .andExpect(status().isOk());
    }
}
