package com.carryon.auth;

import com.carryon.config.AppProperties;
import com.carryon.users.User;
import com.carryon.users.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates the first admin from environment configuration. Override ADMIN_PASSWORD in any real deployment. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminSeeder implements ApplicationRunner {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AppProperties props;

    @Override
    public void run(ApplicationArguments args) {
        if (users.existsByEmail(props.adminEmail().toLowerCase())) return;
        User admin = new User();
        admin.setEmail(props.adminEmail().toLowerCase());
        admin.setPasswordHash(encoder.encode(props.adminPassword()));
        admin.setFullName("Platform Admin");
        admin.setRole(User.Role.ADMIN);
        admin.setKycStatus(User.KycStatus.VERIFIED);
        users.insert(admin);
        log.info("Seeded admin account {}", props.adminEmail());
    }
}
