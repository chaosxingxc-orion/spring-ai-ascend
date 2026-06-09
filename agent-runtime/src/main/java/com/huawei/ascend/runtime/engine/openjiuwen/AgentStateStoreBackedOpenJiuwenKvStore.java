package com.huawei.ascend.runtime.engine.openjiuwen;

import com.huawei.ascend.runtime.common.Guards;
import com.huawei.ascend.runtime.engine.service.AgentStateStore;
import com.openjiuwen.spi.store.BaseKVStore;
import com.openjiuwen.spi.store.KVStorePipeline;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Adapts OpenJiuwen's KV checkpointer storage contract to the runtime-owned
 * {@link AgentStateStore}.
 *
 * <p>OpenJiuwen's {@code PersistenceCheckpointer} owns checkpoint key layout and
 * value shape. This adapter only wraps each value in a small map because
 * {@code AgentStateStore} stores map values.
 */
public class AgentStateStoreBackedOpenJiuwenKvStore extends BaseKVStore {

    private static final String DEFAULT_PREFIX = "openjiuwen.checkpoint:";
    private static final String INDEX_KEY = "__index";
    private static final String VALUE_KEY = "value";
    private static final String NULL_VALUE_KEY = "nullValue";

    private final AgentStateStore stateStore;
    private final String prefix;

    public AgentStateStoreBackedOpenJiuwenKvStore(AgentStateStore stateStore) {
        this(stateStore, DEFAULT_PREFIX);
    }

    public AgentStateStoreBackedOpenJiuwenKvStore(AgentStateStore stateStore, String prefix) {
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
        this.prefix = Guards.requireNonBlank(prefix, "prefix");
    }

    @Override
    public synchronized void set(String key, Object value) {
        String rawKey = Guards.requireNonBlank(key, "key");
        stateStore.save(storageKey(rawKey), wrap(value));
        addIndex(rawKey);
    }

    @Override
    public synchronized boolean exclusiveSet(String key, Object value, Integer timeout) {
        if (exists(key)) {
            return false;
        }
        set(key, value);
        return true;
    }

    @Override
    public synchronized Object get(String key) {
        return stateStore.load(storageKey(Guards.requireNonBlank(key, "key")))
                .map(AgentStateStoreBackedOpenJiuwenKvStore::unwrap)
                .orElse(null);
    }

    @Override
    public synchronized boolean exists(String key) {
        return stateStore.load(storageKey(Guards.requireNonBlank(key, "key"))).isPresent();
    }

    @Override
    public synchronized void delete(String key) {
        String rawKey = Guards.requireNonBlank(key, "key");
        stateStore.delete(storageKey(rawKey));
        removeIndex(rawKey);
    }

    @Override
    public synchronized Map<String, Object> getByPrefix(String keyPrefix) {
        String rawPrefix = Guards.requireNonBlank(keyPrefix, "keyPrefix");
        Map<String, Object> values = new LinkedHashMap<>();
        for (String key : loadIndex().keySet()) {
            if (key.startsWith(rawPrefix)) {
                Object value = get(key);
                if (value != null) {
                    values.put(key, value);
                }
            }
        }
        return values;
    }

    @Override
    public synchronized void deleteByPrefix(String keyPrefix, Integer limit) {
        int max = limit == null ? Integer.MAX_VALUE : Math.max(limit, 0);
        List<String> keys = new ArrayList<>();
        for (String key : loadIndex().keySet()) {
            if (key.startsWith(keyPrefix) && keys.size() < max) {
                keys.add(key);
            }
        }
        keys.forEach(this::delete);
    }

    @Override
    public synchronized List<Object> mget(List<String> keys) {
        Objects.requireNonNull(keys, "keys");
        return keys.stream().map(this::get).toList();
    }

    @Override
    public synchronized int batchDelete(List<String> keys, Integer limit) {
        Objects.requireNonNull(keys, "keys");
        int max = limit == null ? keys.size() : Math.max(limit, 0);
        int deleted = 0;
        for (String key : keys) {
            if (deleted >= max) {
                break;
            }
            if (exists(key)) {
                delete(key);
                deleted++;
            }
        }
        return deleted;
    }

    @Override
    public KVStorePipeline pipeline() {
        return new KVStorePipeline(operations -> operations.stream().map(this::executeOperation).toList());
    }

    private Object executeOperation(Object[] operation) {
        String name = String.valueOf(operation[0]);
        if ("set".equals(name)) {
            set(String.valueOf(operation[1]), operation[2]);
            return Boolean.TRUE;
        }
        if ("get".equals(name)) {
            return get(String.valueOf(operation[1]));
        }
        if ("exists".equals(name)) {
            return exists(String.valueOf(operation[1]));
        }
        throw new IllegalArgumentException("Unsupported OpenJiuwen KV pipeline operation: " + name);
    }

    private String storageKey(String rawKey) {
        return prefix + rawKey;
    }

    private String indexStorageKey() {
        return storageKey(INDEX_KEY);
    }

    private void addIndex(String rawKey) {
        Map<String, Object> index = loadIndex();
        index.put(rawKey, Boolean.TRUE);
        stateStore.save(indexStorageKey(), index);
    }

    private void removeIndex(String rawKey) {
        Map<String, Object> index = loadIndex();
        index.remove(rawKey);
        stateStore.save(indexStorageKey(), index);
    }

    private Map<String, Object> loadIndex() {
        return new LinkedHashMap<>(stateStore.load(indexStorageKey()).orElseGet(Map::of));
    }

    private static Map<String, Object> wrap(Object value) {
        Map<String, Object> wrapped = new LinkedHashMap<>();
        if (value == null) {
            wrapped.put(NULL_VALUE_KEY, Boolean.TRUE);
        } else {
            wrapped.put(VALUE_KEY, value);
        }
        return wrapped;
    }

    private static Object unwrap(Map<String, Object> wrapped) {
        if (Boolean.TRUE.equals(wrapped.get(NULL_VALUE_KEY))) {
            return null;
        }
        return wrapped.get(VALUE_KEY);
    }
}
