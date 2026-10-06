package com.carryon.users;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("users")
@Getter @Setter @NoArgsConstructor
public class User {
    public enum Role { USER, ADMIN }
    public enum KycStatus { NONE, VERIFIED, REJECTED }

    @Id private UUID id = UUID.randomUUID();
    private String email;           // always stored lower-case; unique index
    private String passwordHash;
    private String fullName;
    private String phone;
    private Role role = Role.USER;
    private KycStatus kycStatus = KycStatus.NONE;
    private String kycReference;    // opaque provider reference; never raw Aadhaar/PAN
    private boolean enabled = true;
    private Instant createdAt = Instant.now();
}
