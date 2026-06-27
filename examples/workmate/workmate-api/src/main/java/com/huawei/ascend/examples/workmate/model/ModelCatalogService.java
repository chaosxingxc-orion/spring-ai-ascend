package com.huawei.ascend.examples.workmate.model;

import com.huawei.ascend.examples.workmate.config.LlmModelDefinition;
import com.huawei.ascend.examples.workmate.config.WorkmateLlmProperties;
import com.huawei.ascend.examples.workmate.model.dto.ModelCatalogResponse;
import com.huawei.ascend.examples.workmate.model.dto.ModelCatalogResponse.EffortOption;
import com.huawei.ascend.examples.workmate.model.dto.ModelCatalogResponse.ModelOption;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ModelCatalogService {

    private final WorkmateLlmProperties llm;

    public ModelCatalogService(WorkmateLlmProperties llm) {
        this.llm = llm;
    }

    public ModelCatalogResponse catalog() {
        List<ModelOption> models = effectiveCatalog().stream()
                .map(def -> new ModelOption(
                        def.id(),
                        def.displayName(),
                        def.provider(),
                        def.modelName(),
                        def.capabilities()))
                .toList();
        List<EffortOption> efforts = List.of(
                new EffortOption(ModelEffort.AUTO.name(), ModelEffort.AUTO.label()),
                new EffortOption(ModelEffort.MINIMAL.name(), ModelEffort.MINIMAL.label()),
                new EffortOption(ModelEffort.LOW.name(), ModelEffort.LOW.label()),
                new EffortOption(ModelEffort.MEDIUM.name(), ModelEffort.MEDIUM.label()),
                new EffortOption(ModelEffort.HIGH.name(), ModelEffort.HIGH.label()),
                new EffortOption(ModelEffort.MAX.name(), ModelEffort.MAX.label()));
        return new ModelCatalogResponse(llm.defaultModelId(), models, efforts);
    }

    public ResolvedModel resolve(String modelId) {
        String normalized = normalizeModelId(modelId);
        LlmModelDefinition definition = effectiveCatalog().stream()
                .filter(item -> item.id().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new UnknownModelException(normalized));
        String provider = coalesce(definition.provider(), llm.modelProvider());
        String apiBase = coalesce(definition.apiBase(), llm.apiBase());
        String apiKey = coalesce(definition.apiKey(), llm.apiKey());
        String modelName = coalesce(definition.modelName(), definition.id());
        return new ResolvedModel(
                definition.id(),
                definition.displayName(),
                provider,
                apiKey,
                apiBase,
                modelName,
                llm.sslVerify());
    }

    public ResolvedModel resolveDefault() {
        return resolve(llm.defaultModelId());
    }

    public String normalizeModelId(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return llm.defaultModelId();
        }
        return modelId.trim();
    }

    private List<LlmModelDefinition> effectiveCatalog() {
        if (!llm.catalog().isEmpty()) {
            return llm.catalog();
        }
        String id = llm.defaultModelId();
        String displayName = humanize(id);
        return List.of(new LlmModelDefinition(
                id,
                displayName,
                llm.modelProvider(),
                llm.apiBase(),
                llm.apiKey(),
                llm.modelName(),
                List.of("chat")));
    }

    private static String coalesce(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback;
    }

    private static String humanize(String id) {
        if (id == null || id.isBlank()) {
            return "Default";
        }
        return id.replace('-', ' ').replace('_', ' ');
    }

    public record ResolvedModel(
            String id,
            String displayName,
            String provider,
            String apiKey,
            String apiBase,
            String modelName,
            boolean sslVerify) {}

    public static final class UnknownModelException extends IllegalArgumentException {
        public UnknownModelException(String modelId) {
            super("Unknown model: " + modelId);
        }
    }
}
