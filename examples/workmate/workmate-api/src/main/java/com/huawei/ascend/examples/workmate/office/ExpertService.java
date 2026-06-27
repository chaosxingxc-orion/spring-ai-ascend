package com.huawei.ascend.examples.workmate.office;

import com.huawei.ascend.examples.workmate.office.dto.ExpertSummaryResponse;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ExpertService {

    private final ExpertRegistry registry;

    public ExpertService(ExpertRegistry registry) {
        this.registry = registry;
    }

    public List<ExpertSummaryResponse> listExperts() {
        return registry.listExperts().stream().map(ExpertSummaryResponse::from).toList();
    }

    public ExpertSummaryResponse getExpert(String expertId) {
        return ExpertSummaryResponse.from(registry.requireExpert(expertId));
    }

    public ExpertDefinition requireExpertDefinition(String expertId) {
        return registry.requireExpert(expertId);
    }

    public Optional<ExpertDefinition> findExpertDefinition(String expertId) {
        return registry.findExpert(expertId);
    }
}
