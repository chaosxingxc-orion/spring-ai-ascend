package com.huawei.ascend.examples.workmate.config;

import com.huawei.ascend.examples.workmate.cloud.CloudAccessGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Enforces {@code workmate.cloud.enabled} on every {@code /api/v1/cloud/**} request.
 */
public class CloudGuardInterceptor implements HandlerInterceptor {

    private final CloudAccessGuard accessGuard;

    public CloudGuardInterceptor(CloudAccessGuard accessGuard) {
        this.accessGuard = accessGuard;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        accessGuard.requireEnabled();
        return true;
    }
}
