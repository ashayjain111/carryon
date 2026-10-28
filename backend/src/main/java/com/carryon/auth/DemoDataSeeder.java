package com.carryon.auth;

import com.carryon.trips.Trip;
import com.carryon.trips.TripRepository;
import com.carryon.users.User;
import com.carryon.users.UserRepository;
import java.time.LocalDate;
import com.carryon.common.Dates;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Optional demo accounts and trips (SEED_DEMO=true) so the app is usable immediately. Never enable in production. */
@Slf4j
@Component
@Order(10)
@ConditionalOnProperty(name = "app.seed-demo", havingValue = "true")
@RequiredArgsConstructor
public class DemoDataSeeder implements ApplicationRunner {
    private final UserRepository users;
    private final TripRepository trips;
    private final PasswordEncoder encoder;

    @Override
    public void run(ApplicationArguments args) {
        if (users.existsByEmail("traveller@carryon.demo")) return;
        User traveller = user("traveller@carryon.demo", "Tara Traveller", true);
        user("sender@carryon.demo", "Sam Sender", false);
        trip(traveller, "Delhi", "Mumbai", 5, 20000, 12000);
        trip(traveller, "Mumbai", "Bengaluru", 9, 15000, 9000);
        log.info("Seeded demo accounts: traveller@carryon.demo / sender@carryon.demo (password demo12345)");
    }

    private User user(String email, String name, boolean verified) {
        User u = new User();
        u.setEmail(email);
        u.setFullName(name);
        u.setPasswordHash(encoder.encode("demo12345"));
        if (verified) u.setKycStatus(User.KycStatus.VERIFIED);
        return users.insert(u);
    }

    private void trip(User t, String from, String to, int inDays, int total, int personal) {
        Trip trip = new Trip();
        trip.setTravellerId(t.getId());
        trip.setOriginCity(from);
        trip.setDestinationCity(to);
        trip.setOriginKey(from.toLowerCase());
        trip.setDestinationKey(to.toLowerCase());
        trip.setDepartureDate(Dates.today().plusDays(inDays));
        trip.setTotalCapacityG(total);
        trip.setPersonalLuggageG(personal);
        trip.setAvailableG(total - personal);
        trips.insert(trip);
    }
}
