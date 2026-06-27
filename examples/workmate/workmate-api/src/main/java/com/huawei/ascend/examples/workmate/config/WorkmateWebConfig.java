package com.huawei.ascend.examples.workmate.config;

import com.huawei.ascend.examples.workmate.cloud.CloudAccessGuard;
import com.huawei.ascend.examples.workmate.office.StudioAccessGuard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WorkmateWebConfig implements WebMvcConfigurer {

    private final StudioAccessGuard studioAccessGuard;
    private final CloudAccessGuard cloudAccessGuard;
    private final String[] allowedOrigins;

    public WorkmateWebConfig(
            StudioAccessGuard studioAccessGuard,
            CloudAccessGuard cloudAccessGuard,
            @Value("${workmate.web.allowed-origins:http://localhost:5174,http://127.0.0.1:5174}")
                    String allowedOrigins) {
        this.studioAccessGuard = studioAccessGuard;
        this.cloudAccessGuard = cloudAccessGuard;
        this.allowedOrigins = allowedOrigins.split("\\s*,\\s*");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Restrict to the dev UI origin(s) only. allowCredentials + wildcard ports was an
        // open door for any localhost service; pin exact origins instead.
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new StudioGuardInterceptor(studioAccessGuard))
                .addPathPatterns("/api/v1/studio/**");
        registry.addInterceptor(new CloudGuardInterceptor(cloudAccessGuard))
                .addPathPatterns("/api/v1/cloud/**");
    }
}
