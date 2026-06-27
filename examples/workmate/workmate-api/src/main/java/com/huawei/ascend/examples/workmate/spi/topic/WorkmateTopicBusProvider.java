package com.huawei.ascend.examples.workmate.spi.topic;

import com.huawei.ascend.examples.workmate.config.WorkmateTopicBusProperties;
import com.huawei.ascend.examples.workmate.spi.topic.ascend.AscendRuntimeTopicBusProvider;
import com.huawei.ascend.examples.workmate.spi.topic.local.LocalInMemoryTopicBusProvider;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Selects topic-bus backend via {@code workmate.topic-bus.provider}.
 */
@Component
public class WorkmateTopicBusProvider implements TopicBusProvider {

    private final Map<String, TopicBusProvider> providersById;
    private final WorkmateTopicBusProperties properties;

    public WorkmateTopicBusProvider(
            List<TopicBusProvider> providers,
            WorkmateTopicBusProperties properties) {
        this.providersById = providers.stream()
                .filter(p -> p.getClass() != WorkmateTopicBusProvider.class)
                .collect(Collectors.toMap(TopicBusProvider::providerId, Function.identity(), (a, b) -> a));
        this.properties = properties;
    }

    @Override
    public String providerId() {
        return resolveDelegate().providerId();
    }

    @Override
    public TopicBusSpi open(TopicBusScope scope) {
        return resolveDelegate().open(scope);
    }

    private TopicBusProvider resolveDelegate() {
        String configured = properties.provider();
        TopicBusProvider delegate = providersById.get(configured);
        if (delegate != null) {
            return delegate;
        }
        return providersById.getOrDefault(
                LocalInMemoryTopicBusProvider.PROVIDER_ID,
                providersById.values().stream().findFirst().orElseThrow());
    }
}
