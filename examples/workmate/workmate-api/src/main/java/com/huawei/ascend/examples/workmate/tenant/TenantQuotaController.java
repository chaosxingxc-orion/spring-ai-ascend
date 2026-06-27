package com.huawei.ascend.examples.workmate.tenant;

import com.huawei.ascend.examples.workmate.tenant.dto.TenantQuotaResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant")
public class TenantQuotaController {

    private final TenantQuotaService tenantQuotaService;

    public TenantQuotaController(TenantQuotaService tenantQuotaService) {
        this.tenantQuotaService = tenantQuotaService;
    }

    @GetMapping("/quota")
    public TenantQuotaResponse quota() {
        return tenantQuotaService.currentQuota();
    }
}
