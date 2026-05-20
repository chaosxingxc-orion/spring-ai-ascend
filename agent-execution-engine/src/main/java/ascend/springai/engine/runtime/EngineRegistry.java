package ascend.springai.engine.runtime;

import ascend.springai.engine.spi.EngineMatchingException;
import ascend.springai.engine.spi.ExecutorAdapter;
import ascend.springai.engine.orchestration.spi.ExecutorDefinition;
import ascend.springai.middleware.HookDispatcher;
import ascend.springai.middleware.spi.RuntimeMiddleware;
import ascend.springai.bus.spi.s2c.S2cCallbackTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Central authority for engine dispatch (Rule 43, Rule 44, ADR-0072).
 *
 * <p>The runtime never pattern-matches on {@code ExecutorDefinition} subtypes
 * outside of this class — every dispatch goes through
 * {@link #resolve(EngineEnvelope)}. The registry is constructed at boot:
 *
 * <ol>
 *   <li>One {@code register(engineType, adapter)} call per supported engine.</li>
 *   <li>Optional {@code validateAgainstSchema()} call at boot to ensure
 *   the registered set matches {@code docs/contracts/engine-envelope.v1.yaml}
 *   {@code known_engines} declaration (Phase 5 R2 pilot — runtime self-validation).</li>
 * </ol>
 *
 * <p>Strict matching: a {@link #resolve(EngineEnvelope)} for an unknown
 * {@code engineType} raises {@link EngineMatchingException}; the calling
 * Run transitions to {@code RunStatus.FAILED} with reason
 * {@code engine_mismatch}. No fallback policy.
 *
 * <p>Authority: ADR-0072; CLAUDE.md Rules 43, 44.
 */
public final class EngineRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(EngineRegistry.class);

    private static final String DEFAULT_SCHEMA_PATH = "docs/contracts/engine-envelope.v1.yaml";

    private final Map<String, ExecutorAdapter> adaptersByEngineType = new LinkedHashMap<>();
    private final Map<Class<? extends ExecutorDefinition>, ExecutorAdapter> adaptersByPayloadType = new LinkedHashMap<>();
    private final java.util.List<RuntimeMiddleware> middlewares = new java.util.ArrayList<>();
    private S2cCallbackTransport s2cCallbackTransport;   // null until registered

    /**
     * Register an adapter under its {@link ExecutorAdapter#engineType()}.
     * Duplicate registration for the same engineType is a hard error — Rule 6
     * forbids inline fallbacks; the application owner must explicitly choose
     * which adapter wins.
     */
    public synchronized EngineRegistry register(ExecutorAdapter adapter) {
        Objects.requireNonNull(adapter, "adapter is required");
        String engineType = adapter.engineType();
        if (engineType == null || engineType.isBlank()) {
            throw new IllegalArgumentException("ExecutorAdapter.engineType() must be non-blank");
        }
        Class<? extends ExecutorDefinition> payloadType = adapter.payloadType();
        if (payloadType == null) {
            throw new IllegalArgumentException("ExecutorAdapter.payloadType() must be non-null");
        }
        ExecutorAdapter previousByEngine = adaptersByEngineType.get(engineType);
        ExecutorAdapter previousByPayload = adaptersByPayloadType.get(payloadType);
        if (previousByEngine != null || previousByPayload != null) {
            // Rule 6: refuse the silent override.
            throw new IllegalStateException("Duplicate adapter registration for engineType=" + engineType
                    + " / payloadType=" + payloadType.getSimpleName()
                    + " — explicit deregistration required first");
        }
        adaptersByEngineType.put(engineType, adapter);
        adaptersByPayloadType.put(payloadType, adapter);
        LOG.info("EngineRegistry: registered adapter for engineType={} payloadType={}",
                engineType, payloadType.getSimpleName());
        return this;
    }

    /**
     * Resolve the adapter that handles the given payload's concrete class.
     * Used by orchestrators that hold a raw {@link ExecutorDefinition} (legacy
     * call path) — the registry encapsulates the class-to-engineType mapping
     * so dispatchers never pattern-match on payload subtypes (Rule 43).
     */
    public ExecutorAdapter resolveByPayload(ExecutorDefinition def) {
        Objects.requireNonNull(def, "def is required");
        ExecutorAdapter adapter = adaptersByPayloadType.get(def.getClass());
        if (adapter == null) {
            throw new EngineMatchingException(
                    null,
                    def.getClass().getSimpleName(),
                    "No ExecutorAdapter registered for payload class=" + def.getClass().getSimpleName()
                            + " (registered payloadTypes: " + adaptersByPayloadTypeKeyNames() + ") — engine_mismatch");
        }
        return adapter;
    }

    private Set<String> adaptersByPayloadTypeKeyNames() {
        Set<String> names = new LinkedHashSet<>();
        for (Class<?> c : adaptersByPayloadType.keySet()) {
            names.add(c.getSimpleName());
        }
        return names;
    }

    /**
     * Strict-matching resolve. Returns the adapter registered under
     * {@code envelope.engineType()} or raises {@link EngineMatchingException}.
     */
    public ExecutorAdapter resolve(EngineEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope is required");
        ExecutorAdapter adapter = adaptersByEngineType.get(envelope.engineType());
        if (adapter == null) {
            throw new EngineMatchingException(
                    envelope.engineType(),
                    envelope.payload().getClass().getSimpleName(),
                    "No ExecutorAdapter registered for engineType=" + envelope.engineType()
                            + " (registered: " + adaptersByEngineType.keySet() + ") — engine_mismatch");
        }
        return adapter;
    }

    /**
     * Convenience for the legacy dispatch site that has only an engine_type
     * string and a payload. Equivalent to building a minimal envelope and
     * resolving it.
     */
    public ExecutorAdapter resolveByEngineType(String engineType) {
        Objects.requireNonNull(engineType, "engineType is required");
        ExecutorAdapter adapter = adaptersByEngineType.get(engineType);
        if (adapter == null) {
            throw new EngineMatchingException(
                    engineType,
                    null,
                    "No ExecutorAdapter registered for engineType=" + engineType
                            + " (registered: " + adaptersByEngineType.keySet() + ") — engine_mismatch");
        }
        return adapter;
    }

    /**
     * Phase 5 R2 pilot — boot-time self-validation against the YAML schema.
     *
     * <p>Asserts that:
     * <ul>
     *   <li>every {@code known_engines[].id} from the YAML has a registered adapter</li>
     *   <li>every registered adapter's {@code engineType()} appears in {@code known_engines}</li>
     * </ul>
     *
     * <p>Throws {@link IllegalStateException} on any mismatch (Rule 9
     * ship-gate posture).
     */
    public void validateAgainstSchema() {
        validateAgainstSchema(DEFAULT_SCHEMA_PATH);
    }

    public void validateAgainstSchema(String yamlPath) {
        Set<String> declared = readKnownEngines(yamlPath);
        Set<String> registered = new LinkedHashSet<>(adaptersByEngineType.keySet());

        Set<String> missingAdapters = new LinkedHashSet<>(declared);
        missingAdapters.removeAll(registered);

        Set<String> unknownAdapters = new LinkedHashSet<>(registered);
        unknownAdapters.removeAll(declared);

        if (!missingAdapters.isEmpty() || !unknownAdapters.isEmpty()) {
            throw new IllegalStateException(
                    "EngineRegistry self-validation failed against " + yamlPath
                            + ": missing adapters for known_engines=" + missingAdapters
                            + "; unknown registered adapters=" + unknownAdapters);
        }
        LOG.info("EngineRegistry: self-validation OK ({} engines: {})", registered.size(), registered);
    }

    /** Read the {@code known_engines[].id} set from the YAML schema. Package-private for tests. */
    @SuppressWarnings("unchecked")
    Set<String> readKnownEngines(String yamlPath) {
        Object root = readYaml(yamlPath);
        if (!(root instanceof Map<?, ?> map)) {
            throw new IllegalStateException("engine-envelope.v1.yaml malformed at " + yamlPath);
        }
        Object known = map.get("known_engines");
        if (!(known instanceof List<?> rows)) {
            throw new IllegalStateException("engine-envelope.v1.yaml: known_engines: list missing or wrong shape");
        }
        Set<String> ids = new LinkedHashSet<>();
        for (Object row : rows) {
            if (row instanceof Map<?, ?> r) {
                Object id = r.get("id");
                if (id != null) {
                    ids.add(id.toString());
                }
            }
        }
        if (ids.isEmpty()) {
            throw new IllegalStateException("engine-envelope.v1.yaml: known_engines is empty");
        }
        return ids;
    }

    /** Visible for tests. */
    public Set<String> registeredEngineTypes() {
        return new LinkedHashSet<>(adaptersByEngineType.keySet());
    }

    /**
     * Register a cross-cutting {@link RuntimeMiddleware} that listens on
     * {@link ascend.springai.middleware.spi.HookPoint} events.
     * Order matters — middlewares fire in registration order for before_*
     * and on_* hooks; in reverse order for after_* hooks. ADR-0073.
     */
    public synchronized EngineRegistry registerMiddleware(RuntimeMiddleware middleware) {
        Objects.requireNonNull(middleware, "middleware is required");
        middlewares.add(middleware);
        LOG.info("EngineRegistry: registered middleware {}", middleware.getClass().getName());
        return this;
    }

    /**
     * Return the hook dispatcher backing the registered middlewares. Always
     * non-null; the empty case returns a dispatcher that proceeds without
     * firing. The orchestrator calls this once at construction.
     */
    public HookDispatcher hookDispatcher() {
        return new HookDispatcher(middlewares);
    }

    /**
     * Register the S2C callback transport (ADR-0074, W2.x Phase 3). At most one
     * transport may be registered; duplicate registration is rejected per Rule 6
     * (single construction path). The orchestrator pulls this once at construction
     * via {@link #s2cCallbackTransport()}; absence is OK and means S2C
     * suspensions raise {@code s2c_transport_unavailable}.
     */
    public synchronized EngineRegistry registerS2cCallbackTransport(S2cCallbackTransport transport) {
        Objects.requireNonNull(transport, "transport is required");
        if (this.s2cCallbackTransport != null) {
            throw new IllegalStateException("S2cCallbackTransport already registered ("
                    + this.s2cCallbackTransport.getClass().getName()
                    + ") -- explicit deregistration required first per Rule 6");
        }
        this.s2cCallbackTransport = transport;
        LOG.info("EngineRegistry: registered S2cCallbackTransport {}", transport.getClass().getName());
        return this;
    }

    /**
     * Returns the registered S2C callback transport, or {@code null} when no
     * transport has been registered. The orchestrator interprets null as
     * "S2C suspension is not supported in this deployment" and fails any S2C
     * suspension with reason {@code s2c_transport_unavailable}.
     */
    public S2cCallbackTransport s2cCallbackTransport() {
        return s2cCallbackTransport;
    }

    private Object readYaml(String yamlPath) {
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
}
