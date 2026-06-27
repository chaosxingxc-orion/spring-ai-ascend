package com.huawei.ascend.examples.workmate.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.examples.workmate.config.WorkmateDataProperties;
import com.huawei.ascend.examples.workmate.support.JsonFileStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MemorySettingsStore {

    private static final Logger LOG = LoggerFactory.getLogger(MemorySettingsStore.class);
    private static final String SETTINGS_FILE = "settings.json";

    private final JsonFileStore<MemorySettings> backing;

    public MemorySettingsStore(WorkmateDataProperties dataProperties, ObjectMapper objectMapper) {
        this.backing = new JsonFileStore<>(
                dataProperties.resolvedPath().resolve("memory").resolve(SETTINGS_FILE),
                objectMapper,
                MemorySettings.class,
                MemorySettings::disabled,
                LOG);
    }

    public MemorySettings read() {
        return backing.read(settings -> settings);
    }

    public MemorySettings save(MemorySettings settings) {
        backing.replace(settings);
        return settings;
    }

    public record MemorySettings(boolean enabled, boolean autoCapture) {
        public static MemorySettings disabled() {
            return new MemorySettings(false, false);
        }
    }
}
