package com.carryon.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String jwtSecret,
        int jwtTtlMinutes,
        String corsOrigin,
        String adminEmail,
        String adminPassword,
        int authRateLimitPerMinute,
        int platformFeePercent,
        int autoReleaseHours,
        long maxDeclaredValuePaise) {}
