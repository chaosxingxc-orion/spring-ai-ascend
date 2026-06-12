package com.huawei.ascend.runtime.boot;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URISyntaxException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * The jar is a plain library: it must not ship application-level artifacts that
 * inject configuration into (or shadow configuration of) a host application —
 * no classpath-root application config, no logging config, no Flyway migration
 * scripts. Asserted on the packaged classes directory (= jar content) and on a
 * minimal host's resulting Environment.
 */
class LibraryArtifactBoundaryTest {

    @Test
    void packagedClasspathRootCarriesNoApplicationLevelArtifacts() throws URISyntaxException {
        Path classes = Path.of(RuntimeAutoConfiguration.class
                .getProtectionDomain().getCodeSource().getLocation().toURI());

        assertThat(classes.resolve("application.yml")).doesNotExist();
        assertThat(classes.resolve("application.yaml")).doesNotExist();
        assertThat(classes.resolve("application.properties")).doesNotExist();
        assertThat(classes.resolve("logback-spring.xml")).doesNotExist();
        assertThat(classes.resolve("logback.xml")).doesNotExist();
        assertThat(classes.resolve("db")).doesNotExist();
    }

    @Test
    void hostEnvironmentGetsNoServerPortDatasourceOrFlywayInjection() {
        SpringApplication app = new SpringApplication(EmptyHost.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        try (ConfigurableApplicationContext ctx = app.run()) {
            assertThat(ctx.getEnvironment().getProperty("server.port")).isNull();
            assertThat(ctx.getEnvironment().getProperty("spring.flyway.enabled")).isNull();
            assertThat(ctx.getEnvironment().getProperty("spring.datasource.url")).isNull();
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class EmptyHost {
    }
}
