package com.huawei.ascend.examples.workmate.office;

import com.huawei.ascend.examples.workmate.office.dto.PlaybookResponse;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PlaybookService {

    private final PlaybookRegistry registry;

    public PlaybookService(PlaybookRegistry registry) {
        this.registry = registry;
    }

    public List<PlaybookResponse> listPlaybooks(String placement) {
        if (placement == null || placement.isBlank()) {
            return registry.listPlaybooks().stream().map(PlaybookResponse::from).toList();
        }
        return registry.listByPlacement(placement).stream().map(PlaybookResponse::from).toList();
    }
}
