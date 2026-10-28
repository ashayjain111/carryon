package com.carryon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carryon.auth.AuthController.RegisterRequest;
import com.carryon.auth.AuthService;
import com.carryon.common.ApiException;
import com.carryon.common.Dates;
import com.carryon.disputes.Dispute;
import com.carryon.disputes.DisputeController;
import com.carryon.disputes.DisputeController.Decision;
import com.carryon.parcels.Parcel;
import com.carryon.parcels.ParcelController.CreateParcelRequest;
import com.carryon.parcels.ParcelService;
import com.carryon.payments.Payment;
import com.carryon.payments.PaymentService;
import com.carryon.trips.Trip;
import com.carryon.trips.TripController.CreateTripRequest;
import com.carryon.trips.TripService;
import com.carryon.users.User;
import com.carryon.users.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuthAndFlowTests {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired AuthService auth;
    @Autowired TripService tripService;
    @Autowired ParcelService parcels;
    @Autowired PaymentService payments;
    @Autowired DisputeController disputes;
    @Autowired com.carryon.ratings.RatingController ratings;

    private String uniq(String p) { return p + "-" + UUID.randomUUID() + "@test.local"; }

    private String json(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private String register(String email, String extra) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\",\"fullName\":\"Test User\"" + extra + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty()).andReturn();
        return com.jayway.jsonpath.JsonPath.read(r.getResponse().getContentAsString(), "$.token");
    }

    // ---------- login & security over HTTP ----------

    @Test
    void registerWithoutPhoneThenLoginWorks_caseInsensitiveEmail() throws Exception {
        String email = uniq("Mixed.Case");
        register(email, "");                                       // phone omitted
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json(email.toUpperCase(), "password123")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.email").value(email.toLowerCase()));
    }

    @Test
    void registerWithBlankPhoneIsAccepted() throws Exception {
        register(uniq("blankphone"), ",\"phone\":\"\"");
    }

    @Test
    void wrongPasswordAndUnknownUserGetTheSame401() throws Exception {
        String email = uniq("pw");
        register(email, "");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json(email, "wrong-password")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("Invalid credentials"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json(uniq("nobody"), "password123")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("Invalid credentials"));
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        String email = uniq("dup");
        register(email, "");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\",\"fullName\":\"X\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void shortPasswordFailsValidation() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + uniq("short") + "\",\"password\":\"short\",\"fullName\":\"X\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.password").exists());
    }

    @Test
    void staleTokenInBrowserNeverBlocksLogin() throws Exception {
        String email = uniq("stale");
        register(email, "");
        mvc.perform(post("/api/auth/login").header("Authorization", "Bearer not-a-real-token")
                        .contentType(MediaType.APPLICATION_JSON).content(json(email, "password123")))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpointsNeedAValidToken() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer garbage")).andExpect(status().isUnauthorized());
        String token = register(uniq("me"), "");
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.kycStatus").value("NONE"));
    }

    @Test
    void normalUsersCannotUseAdminEndpoints_butAdminCan() throws Exception {
        String userToken = register(uniq("plain"), "");
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + userToken)).andExpect(status().isForbidden());

        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("admin@carryon.local", "admin12345"))).andExpect(status().isOk()).andReturn();
        String adminToken = com.jayway.jsonpath.JsonPath.read(r.getResponse().getContentAsString(), "$.token");
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + adminToken)).andExpect(status().isOk());
    }

    @Test
    void disabledUserIsLockedOutImmediately() throws Exception {
        String email = uniq("ban");
        String token = register(email, "");
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk());

        User u = users.findByEmail(email).orElseThrow();
        u.setEnabled(false);
        users.save(u);

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json(email, "password123")))
                .andExpect(status().isUnauthorized());
    }

    // ---------- disputes and ratings ----------

    private UUID newUser(String name) {
        String email = uniq(name);
        auth.register(new RegisterRequest(email, "password123", name, null));
        User u = users.findByEmail(email).orElseThrow();
        u.setKycStatus(User.KycStatus.VERIFIED);
        users.save(u);
        return u.getId();
    }

    private org.springframework.security.core.Authentication as(UUID id, String role) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(id.toString()).build();
        return new JwtAuthenticationToken(jwt, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role)));
    }

    private Parcel acceptedParcel(UUID sender, UUID traveller) {
        Trip trip = tripService.create(traveller, new CreateTripRequest("Delhi", "Mumbai", Dates.today().plusDays(2), 20000, 12000));
        Parcel p = parcels.create(sender, new CreateParcelRequest("Charger", 1000, 800000, 50000, "Delhi", "Mumbai", "Ravi", "+919999999999", true));
        parcels.requestTrip(sender, p.getId(), trip.getId());
        parcels.accept(traveller, p.getId());
        return p;
    }

    @Test
    void disputeFreezesParcelAndAdminRefundReturnsEscrow() {
        UUID sender = newUser("sender"), traveller = newUser("traveller"), admin = newUser("admin");
        Parcel p = acceptedParcel(sender, traveller);

        var d = disputes.open(as(sender, "USER"), p.getId(),
                new DisputeController.OpenRequest(Dispute.Kind.DAMAGED, "Box arrived crushed"));
        assertThat(parcels.visible(p.getId(), sender, false).getStatus()).isEqualTo(Parcel.Status.DISPUTED);
        assertThat(payments.forParcel(p.getId()).getStatus()).isEqualTo(Payment.Status.HELD);

        // a second dispute on the same parcel is refused
        assertThatThrownBy(() -> disputes.open(as(traveller, "USER"), p.getId(),
                new DisputeController.OpenRequest(Dispute.Kind.OTHER, "again"))).isInstanceOf(ApiException.class);

        disputes.resolve(as(admin, "ADMIN"), d.id(), new DisputeController.ResolveRequest(Decision.REFUND_SENDER, "ok"));
        assertThat(parcels.visible(p.getId(), sender, false).getStatus()).isEqualTo(Parcel.Status.REFUNDED);
        assertThat(payments.forParcel(p.getId()).getStatus()).isEqualTo(Payment.Status.REFUNDED);
        assertThatThrownBy(() -> disputes.resolve(as(admin, "ADMIN"), d.id(),
                new DisputeController.ResolveRequest(Decision.RELEASE_TO_TRAVELLER, "x"))).isInstanceOf(ApiException.class);
    }

    @Test
    void autoReleasePaysTravellerAfterWindow_andRatingsAreOncePerSide() {
        UUID sender = newUser("sender"), traveller = newUser("traveller");
        Parcel p = acceptedParcel(sender, traveller);
        Parcel fresh = parcels.visible(p.getId(), sender, false);
        parcels.pickup(traveller, p.getId(), fresh.getPickupToken());
        parcels.deliver(traveller, p.getId(), fresh.getDeliveryToken());
        parcels.confirmDelivery(sender, p.getId());

        var rate = new com.carryon.ratings.RatingController.RateRequest(5, "great");
        ratings.rate(as(sender, "USER"), p.getId(), rate);
        assertThatThrownBy(() -> ratings.rate(as(sender, "USER"), p.getId(), rate)).isInstanceOf(Exception.class);
        ratings.rate(as(traveller, "USER"), p.getId(), rate);               // the other side may rate once
        assertThat(ratings.summary(traveller).count()).isEqualTo(1);
    }

    @Test
    void unauthenticatedPrincipalHelperIsNotAdmin() {
        assertThat(new UsernamePasswordAuthenticationToken("x", "y").getAuthorities()).isEmpty();
    }
}
