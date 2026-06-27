package com.huawei.ascend.examples.workmate.prompt;

import com.huawei.ascend.examples.workmate.prompt.dto.EnhancePromptRequest;
import com.huawei.ascend.examples.workmate.prompt.dto.EnhancePromptResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/prompt")
public class EnhancePromptController {

    private final PromptEnhanceService promptEnhanceService;

    public EnhancePromptController(PromptEnhanceService promptEnhanceService) {
        this.promptEnhanceService = promptEnhanceService;
    }

    @PostMapping("/enhance")
    public EnhancePromptResponse enhance(@Valid @RequestBody EnhancePromptRequest request) {
        String text = promptEnhanceService.enhance(request.text(), request.expertId());
        return new EnhancePromptResponse(text);
    }
}
