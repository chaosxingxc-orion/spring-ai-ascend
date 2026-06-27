package com.huawei.ascend.examples.workmate.capability;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/capabilities")
public class IdleCapabilityController {

    private final IdleCapabilityService idleCapabilityService;

    public IdleCapabilityController(IdleCapabilityService idleCapabilityService) {
        this.idleCapabilityService = idleCapabilityService;
    }

    @GetMapping("/idle")
    public List<IdleCapabilityItem> listIdle(@RequestParam(defaultValue = "30") long idleDays) {
        return idleCapabilityService.listIdle(idleDays);
    }

    @PostMapping("/idle/disable")
    public void disable(@RequestParam String type, @RequestParam String id) {
        idleCapabilityService.disable(type, id);
    }
}
