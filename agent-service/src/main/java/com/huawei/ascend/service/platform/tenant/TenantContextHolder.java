package com.huawei.ascend.service.platform.tenant;

// ThreadLocal cleared in TenantContextFilter.doFilterInternal finally{} per Rule 5.
public final class TenantContextHolder {
    private static final ThreadLocal<TenantContext> HOLDER = new ThreadLocal<>();
    private TenantContextHolder() {}

    /**
     * Returns the current request's tenant context, or {@code null} if called outside an HTTP
     * request (timer-driven resumes, async orchestration, test code without filter setup).
     * Production code under {@code com.huawei.ascend.service.runtime.*} MUST NOT call this — source
     * tenant from {@code RunContext.tenantId()} instead (Rule 21, ADR-0023).
     */
    public static TenantContext get() { return HOLDER.get(); }
    public static void set(TenantContext ctx) { HOLDER.set(ctx); }
    public static void clear() { HOLDER.remove(); }
}
