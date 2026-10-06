package com.carryon.users;

import com.carryon.common.ApiException;
import com.carryon.common.CurrentUser;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserRepository users;

    public record UserView(UUID id, String email, String fullName, String phone, String role, String kycStatus) {
        public static UserView of(User u) {
            return new UserView(u.getId(), u.getEmail(), u.getFullName(), u.getPhone(),
                    u.getRole().name(), u.getKycStatus().name());
        }
    }

    @GetMapping("/me")
    public UserView me(Authentication auth) {
        return UserView.of(users.findById(CurrentUser.id(auth)).orElseThrow(() -> ApiException.notFound("User")));
    }

    /**
     * MOCK KYC. In production this endpoint is replaced by a hosted KYC provider (e.g. DigiLocker /
     * a verification vendor) that calls us back with a verdict. We only ever store the verdict and an
     * opaque provider reference: raw Aadhaar/PAN numbers and document images are never persisted here.
     */
    @PostMapping("/me/kyc/mock-verify")
    public UserView mockKyc(Authentication auth) {
        User u = users.findById(CurrentUser.id(auth)).orElseThrow(() -> ApiException.notFound("User"));
        u.setKycStatus(User.KycStatus.VERIFIED);
        u.setKycReference("mock-" + UUID.randomUUID());
        users.save(u);
        return UserView.of(u);
    }
}
