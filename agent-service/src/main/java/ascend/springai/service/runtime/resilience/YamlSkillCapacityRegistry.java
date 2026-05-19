package ascend.springai.service.runtime.resilience;

import ascend.springai.service.runtime.resilience.spi.SkillCapacityRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Loads {@code docs/governance/skill-capacity.yaml} on application startup and tracks
 * in-flight skill invocations per {@code (tenant, skill)} and per skill globally.
 *
 * <p>Per Layer-0 principle P-K (Skill-Dimensional Resource Arbitration) the matrix
 * is 2D: a per-tenant quota plus a global skill capacity. Acquire is a CAS loop
 * against both axes; either cap-hit short-circuits with {@code false}.
 *
 * <p>The YAML location is configurable via {@code app.resilience.skill-capacity-path};
 * the default {@code docs/governance/skill-capacity.yaml} is correct for unit tests
 * launched from the repo root and for the packaged jar (the file is shipped under
 * the same path).
 *
 * <p>Authority: ADR-0070; CLAUDE.md Rule R-K + 41.b.
 */
public class YamlSkillCapacityRegistry implements SkillCapacityRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(YamlSkillCapacityRegistry.class);

    private final String yamlPath;
    private final Map<String, Integer> perTenantCap = new HashMap<>();
    private final Map<String, Integer> globalCap = new HashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> perTenantInFlight = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> globalInFlight = new ConcurrentHashMap<>();

    public YamlSkillCapacityRegistry(String yamlPath) {
        this.yamlPath = yamlPath;
        load();
    }

    public YamlSkillCapacityRegistry() {
        this("docs/governance/skill-capacity.yaml");
    }

    @SuppressWarnings("unchecked")
    private void load() {
        Object root = readYaml();
        if (!(root instanceof Map<?, ?> map)) {
            LOG.warn("skill-capacity.yaml absent or malformed at {} — registry empty", yamlPath);
            return;
        }
        Object skills = map.get("skills");
        if (!(skills instanceof List<?> rows)) {
            LOG.warn("skill-capacity.yaml at {} declares no skills: list — registry empty", yamlPath);
            return;
        }
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> r)) {
                continue;
            }
            String id = stringOf(r.get("id"));
            Integer perTenant = intOf(r.get("capacity_per_tenant"));
            Integer global = intOf(r.get("global_capacity"));
            if (id == null || perTenant == null || global == null) {
                LOG.warn("skill-capacity.yaml row malformed (id/per/global missing): {}", r);
                continue;
            }
            perTenantCap.put(id, perTenant);
            globalCap.put(id, global);
        }
        LOG.info("Loaded skill-capacity matrix: {} skills from {}", perTenantCap.size(), yamlPath);
    }

    @Override
    public boolean tryAcquire(String tenant, String skill) {
        Integer perTenant = perTenantCap.get(skill);
        Integer global = globalCap.get(skill);
        if (perTenant == null || global == null) {
            // Unknown skill — fail-closed by default (Rule 10 research/prod posture).
            return false;
        }
        String tenantKey = tenant + ":" + skill;
        AtomicInteger tenantSlot = perTenantInFlight.computeIfAbsent(tenantKey, k -> new AtomicInteger());
        AtomicInteger globalSlot = globalInFlight.computeIfAbsent(skill, k -> new AtomicInteger());

        for (;;) {
            int t = tenantSlot.get();
            int g = globalSlot.get();
            if (t >= perTenant || g >= global) {
                return false;
            }
            if (!tenantSlot.compareAndSet(t, t + 1)) {
                continue;
            }
            if (!globalSlot.compareAndSet(g, g + 1)) {
                tenantSlot.decrementAndGet();
                continue;
            }
            return true;
        }
    }

    @Override
    public void release(String tenant, String skill) {
        AtomicInteger tenantSlot = perTenantInFlight.get(tenant + ":" + skill);
        AtomicInteger globalSlot = globalInFlight.get(skill);
        if (tenantSlot != null && tenantSlot.get() > 0) {
            tenantSlot.decrementAndGet();
        }
        if (globalSlot != null && globalSlot.get() > 0) {
            globalSlot.decrementAndGet();
        }
    }

    private Object readYaml() {
        Path p = Path.of(yamlPath);
        try {
            if (Files.isRegularFile(p)) {
                try (InputStream in = Files.newInputStream(p)) {
                    return new Yaml().load(in);
                }
            }
        } catch (IOException e) {
            LOG.warn("Failed reading {}: {}", yamlPath, e.getMessage());
        }
        // Fallback: classpath resource for jar-packaged deployments.
        try (InputStream in = getClass().getResourceAsStream("/" + yamlPath)) {
            if (in != null) {
                return new Yaml().load(in);
            }
        } catch (IOException e) {
            LOG.warn("Failed reading classpath {}: {}", yamlPath, e.getMessage());
        }
        return null;
    }

    private static String stringOf(Object value) {
        return value == null ? null : value.toString();
    }

    private static Integer intOf(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.valueOf(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
