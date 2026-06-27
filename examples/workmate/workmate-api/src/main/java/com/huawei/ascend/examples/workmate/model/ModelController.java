package com.huawei.ascend.examples.workmate.model;

import com.huawei.ascend.examples.workmate.model.dto.ModelCatalogResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/models")
public class ModelController {

    private final ModelCatalogService catalogService;

    public ModelController(ModelCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public ModelCatalogResponse listModels() {
        return catalogService.catalog();
    }
}
