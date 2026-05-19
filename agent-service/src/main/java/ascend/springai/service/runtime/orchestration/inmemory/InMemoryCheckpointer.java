package ascend.springai.service.runtime.orchestration.inmemory;

import ascend.springai.engine.orchestration.spi.Checkpointer;
import ascend.springai.service.runtime.posture.AppPostureGate;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dev-posture Checkpointer backed by a ConcurrentHashMap.
 * Not durable across JVM restarts. W2 replaces with a Postgres-backed impl.
 *
 * <p>Enforces the §4 #13 inline payload cap of {@value #MAX_INLINE_PAYLOAD_BYTES} bytes.
 * In research/prod posture (APP_POSTURE=research|prod), oversized payloads throw
 * {@link IllegalStateException}. In dev posture they emit a WARNING to stderr
 * (in-memory is non-durable anyway; warn and continue).
 */
public final class InMemoryCheckpointer implements Checkpointer {

    /** §4 #13 inline payload cap — checkpoints larger than this must use PayloadStore (W2). */
    static final int MAX_INLINE_PAYLOAD_BYTES = 16 * 1024;

    private final Map<String, byte[]> store = new ConcurrentHashMap<>();
    private final boolean failOnOversize;

    public InMemoryCheckpointer() {
        AppPostureGate.requireDevForInMemoryComponent("InMemoryCheckpointer");
        this.failOnOversize = false; // dev posture only (research/prod throws above)
    }

    /** Package-private constructor for testing posture-specific behaviour without env-var manipulation. */
    InMemoryCheckpointer(boolean failOnOversize) {
        this.failOnOversize = failOnOversize;
    }

    @Override
    public void save(UUID runId, String nodeKey, byte[] payload) {
        if (payload.length > MAX_INLINE_PAYLOAD_BYTES) {
            String msg = "InMemoryCheckpointer: payload for run=" + runId + " key='" + nodeKey +
                    "' is " + payload.length + " bytes, exceeding the §4 #13 inline cap of 16 KiB" +
                    " (" + MAX_INLINE_PAYLOAD_BYTES + " bytes). Register a PayloadStore for large " +
                    "payloads (payload_store_spi, W2).";
            if (failOnOversize) {
                throw new IllegalStateException(msg);
            }
            System.err.println("[WARN] springai-ascend " + msg);
        }
        store.put(key(runId, nodeKey), Arrays.copyOf(payload, payload.length));
    }

    @Override
    public Optional<byte[]> load(UUID runId, String nodeKey) {
        byte[] value = store.get(key(runId, nodeKey));
        return value == null ? Optional.empty()
                             : Optional.of(Arrays.copyOf(value, value.length));
    }

    private static String key(UUID runId, String nodeKey) {
        return runId + ":" + nodeKey;
    }
}
