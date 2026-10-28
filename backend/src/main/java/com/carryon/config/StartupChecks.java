package com.carryon.config;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/** Refuses to boot in the "prod" profile with the well-known development secrets. */
@Component
public class StartupChecks {
    public StartupChecks(Environment env, AppProperties props) {
        if (!env.acceptsProfiles(Profiles.of("prod"))) return;
        if (props.jwtSecret().startsWith("dev-only")) {
            throw new IllegalStateException("JWT_SECRET must be set to a private random value in production");
        }
        if ("admin12345".equals(props.adminPassword())) {
            throw new IllegalStateException("ADMIN_PASSWORD must be changed in production");
        }
    }
}
