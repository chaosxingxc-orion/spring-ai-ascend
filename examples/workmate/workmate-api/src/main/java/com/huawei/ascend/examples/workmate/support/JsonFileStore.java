package com.huawei.ascend.examples.workmate.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.slf4j.Logger;

/**
 * Thread-safe JSON file store: load once at startup, {@link ReentrantReadWriteLock} on reads/writes,
 * atomic persist via {@link JsonStores#writeAtomic}.
 */
public final class JsonFileStore<T> {

    private final Path storeFile;
    private final ObjectMapper objectMapper;
    private final Logger log;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private T data;

    public JsonFileStore(
            Path storeFile,
            ObjectMapper objectMapper,
            Class<T> type,
            Supplier<T> emptySupplier,
            Logger log) {
        this.storeFile = storeFile;
        this.objectMapper = objectMapper;
        this.log = log;
        this.data = loadInitial(type, emptySupplier);
    }

    public JsonFileStore(
            Path storeFile,
            ObjectMapper objectMapper,
            TypeReference<T> typeReference,
            Supplier<T> emptySupplier,
            Logger log) {
        this.storeFile = storeFile;
        this.objectMapper = objectMapper;
        this.log = log;
        this.data = loadInitial(typeReference, emptySupplier);
    }

    public <R> R read(Function<T, R> action) {
        lock.readLock().lock();
        try {
            return action.apply(data);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void write(Consumer<T> action) {
        lock.writeLock().lock();
        try {
            action.accept(data);
            persistUnlocked();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void replace(T replacement) {
        lock.writeLock().lock();
        try {
            data = replacement;
            persistUnlocked();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private T loadInitial(Class<T> type, Supplier<T> emptySupplier) {
        if (!Files.isRegularFile(storeFile)) {
            return emptySupplier.get();
        }
        try {
            T loaded = objectMapper.readValue(storeFile.toFile(), type);
            return loaded != null ? loaded : emptySupplier.get();
        } catch (IOException ex) {
            log.warn("Failed to read {}: {}", storeFile, ex.getMessage());
            return emptySupplier.get();
        }
    }

    private T loadInitial(TypeReference<T> typeReference, Supplier<T> emptySupplier) {
        if (!Files.isRegularFile(storeFile)) {
            return emptySupplier.get();
        }
        try {
            T loaded = objectMapper.readValue(storeFile.toFile(), typeReference);
            return loaded != null ? loaded : emptySupplier.get();
        } catch (IOException ex) {
            log.warn("Failed to read {}: {}", storeFile, ex.getMessage());
            return emptySupplier.get();
        }
    }

    private void persistUnlocked() {
        try {
            JsonStores.writeAtomic(objectMapper, storeFile, data);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write " + storeFile, ex);
        }
    }
}
