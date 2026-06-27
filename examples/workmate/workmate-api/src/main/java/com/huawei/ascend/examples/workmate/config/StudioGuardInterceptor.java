package com.huawei.ascend.examples.workmate.config;

import com.huawei.ascend.examples.workmate.office.StudioAccessGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Enforces {@code workmate.studio.enabled} on every {@code /api/v1/studio/**} request.
 *
 * <p>Previously only a subset of Studio endpoints called {@code requireStudio()} explicitly, so the
 * feature flag could not be relied on as a security boundary. Centralizing the check here closes that
 * gap for all read/write/import/rollback endpoints uniformly.</p>
 */
public class StudioGuardInterceptor implements HandlerInterceptor {

    private final StudioAccessGuard accessGuard;

    public StudioGuardInterceptor(StudioAccessGuard accessGuard) {
        this.accessGuard = accessGuard;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        accessGuard.requireEnabled();
        return true;
    }
}
