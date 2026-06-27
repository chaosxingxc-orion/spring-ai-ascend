package com.huawei.ascend.examples.workmate.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
public class JacksonConfiguration {

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
