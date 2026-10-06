package com.carryon.auth;

import com.carryon.common.ApiException;
import com.carryon.config.AppProperties;
import com.carryon.users.User;
import com.carryon.users.UserController;
import com.carryon.users.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtEncoder jwtEncoder;
    private final AppProperties props;

    public AuthController.AuthResponse register(AuthController.RegisterRequest r) {
        String email = r.email().trim().toLowerCase();
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("Email already registered");
        }
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(r.password()));
        u.setFullName(r.fullName().trim());
        u.setPhone(r.phone());
        try {
            users.insert(u);                       // unique index on email closes the check-then-insert race
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("Email already registered");
        }
        return respond(u);
    }

    public AuthController.AuthResponse login(AuthController.LoginRequest r) {
        User u = users.findByEmail(r.email().trim().toLowerCase()).orElse(null);
        boolean ok = u != null && encoder.matches(r.password(), u.getPasswordHash());
        if (!ok || !u.isEnabled()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        return respond(u);
    }

    private AuthController.AuthResponse respond(User u) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .subject(u.getId().toString())
                .claim("role", u.getRole().name())
                .issuedAt(now)
                .expiresAt(now.plus(props.jwtTtlMinutes(), ChronoUnit.MINUTES))
                .build();
        String token = jwtEncoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new AuthController.AuthResponse(token, UserController.UserView.of(u));
    }
}
