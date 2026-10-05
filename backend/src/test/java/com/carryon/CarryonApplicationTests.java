package com.carryon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carryon.auth.AuthController.RegisterRequest;
import com.carryon.auth.AuthService;
import com.carryon.common.ApiException;
import com.carryon.parcels.Parcel;
import com.carryon.parcels.ParcelController.CreateParcelRequest;
import com.carryon.parcels.ParcelService;
import com.carryon.payments.Payment;
import com.carryon.payments.PaymentService;
import com.carryon.trips.Trip;
import com.carryon.trips.TripController.CreateTripRequest;
import com.carryon.trips.TripRepository;
import com.carryon.trips.TripService;
import com.carryon.users.User;
import com.carryon.users.UserRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CarryonApplicationTests {

    @Autowired AuthService auth;
    @Autowired UserRepository users;
    @Autowired TripService tripService;
    @Autowired TripRepository trips;
    @Autowired ParcelService parcels;
    @Autowired PaymentService payments;

    private UUID newUser(String name) {
        String email = name + "-" + UUID.randomUUID() + "@test.local";
        auth.register(new RegisterRequest(email, "password123", name, "+911234567890"));
        User u = users.findByEmailIgnoreCase(email).orElseThrow();
        u.setKycStatus(User.KycStatus.VERIFIED);
        users.save(u);
        return u.getId();
    }

    private Trip newTrip(UUID traveller, int total, int personal) {
        return tripService.create(traveller, new CreateTripRequest("Delhi", "Mumbai",
                LocalDate.now().plusDays(3), total, personal));
    }

    private Parcel newParcel(UUID sender, int grams) {
        return parcels.create(sender, new CreateParcelRequest("Laptop charger", grams, 800000, 50000,
                "Delhi", "Mumbai", "Ravi", "+919999999999", true));
    }

    @Test
    void fullHappyPathMovesEscrowAndCapacity() {
        UUID sender = newUser("sender"), traveller = newUser("traveller");
        Trip trip = newTrip(traveller, 20000, 12000);
        Parcel p = newParcel(sender, 1200);

        parcels.requestTrip(sender, p.getId(), trip.getId());
        parcels.accept(traveller, p.getId());
        assertThat(trips.findById(trip.getId()).orElseThrow().getBookedG()).isEqualTo(1200);
        assertThat(payments.forParcel(p.getId()).getStatus()).isEqualTo(Payment.Status.HELD);

        assertThatThrownBy(() -> parcels.pickup(traveller, p.getId(), "wrong"))
                .isInstanceOf(ApiException.class);
        Parcel fresh = parcels.visible(p.getId(), sender, false);
        parcels.pickup(traveller, p.getId(), fresh.getPickupToken());
        parcels.deliver(traveller, p.getId(), fresh.getDeliveryToken());
        parcels.confirmDelivery(sender, p.getId());

        assertThat(parcels.visible(p.getId(), sender, false).getStatus()).isEqualTo(Parcel.Status.COMPLETED);
        assertThat(payments.forParcel(p.getId()).getStatus()).isEqualTo(Payment.Status.RELEASED);
        assertThat(parcels.timeline(p.getId(), sender, false)).extracting("type")
                .containsExactly("CREATED", "REQUESTED", "ACCEPTED", "PICKED_UP", "DELIVERED", "COMPLETED");
    }

    /** 8 kg spare; ten 1.5 kg parcels compete at once. Only 5 fit (7.5 kg); the 6th would make 9 kg. */
    @Test
    void concurrentAcceptsNeverOverbookTheTrip() throws Exception {
        UUID traveller = newUser("traveller");
        Trip trip = newTrip(traveller, 20000, 12000);      // 8000 g spare
        int contenders = 10;
        List<UUID> parcelIds = new ArrayList<>();
        for (int i = 0; i < contenders; i++) {
            UUID sender = newUser("s" + i);
            Parcel p = newParcel(sender, 1500);
            parcels.requestTrip(sender, p.getId(), trip.getId());
            parcelIds.add(p.getId());
        }

        ExecutorService pool = Executors.newFixedThreadPool(contenders);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (UUID id : parcelIds) {
            results.add(pool.submit(() -> {
                go.await();
                try { parcels.accept(traveller, id); return true; }
                catch (ApiException e) { return false; }
            }));
        }
        go.countDown();
        int accepted = 0;
        for (Future<Boolean> f : results) if (f.get()) accepted++;
        pool.shutdown();

        Trip after = trips.findById(trip.getId()).orElseThrow();
        assertThat(accepted).isEqualTo(5);
        assertThat(after.getBookedG()).isEqualTo(7500);
        assertThat(after.availableG()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void doubleAcceptOfSameParcelIsRejected() throws Exception {
        UUID sender = newUser("sender"), traveller = newUser("traveller");
        Trip trip = newTrip(traveller, 20000, 12000);
        Parcel p = newParcel(sender, 1000);
        parcels.requestTrip(sender, p.getId(), trip.getId());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> r = new ArrayList<>();
        for (int i = 0; i < 2; i++) r.add(pool.submit(() -> {
            go.await();
            try { parcels.accept(traveller, p.getId()); return true; } catch (ApiException e) { return false; }
        }));
        go.countDown();
        int ok = 0;
        for (Future<Boolean> f : r) if (f.get()) ok++;
        pool.shutdown();

        assertThat(ok).isEqualTo(1);
        assertThat(trips.findById(trip.getId()).orElseThrow().getBookedG()).isEqualTo(1000);
    }

    @Test
    void cancellingAcceptedParcelReleasesCapacityAndRefunds() {
        UUID sender = newUser("sender"), traveller = newUser("traveller");
        Trip trip = newTrip(traveller, 20000, 12000);
        Parcel p = newParcel(sender, 2000);
        parcels.requestTrip(sender, p.getId(), trip.getId());
        parcels.accept(traveller, p.getId());
        parcels.cancel(sender, p.getId());

        assertThat(trips.findById(trip.getId()).orElseThrow().getBookedG()).isZero();
        assertThat(payments.forParcel(p.getId()).getStatus()).isEqualTo(Payment.Status.REFUNDED);
    }

    @Test
    void strangersCannotSeeOrTouchAParcel() {
        UUID sender = newUser("sender"), traveller = newUser("traveller"), stranger = newUser("stranger");
        Trip trip = newTrip(traveller, 20000, 12000);
        Parcel p = newParcel(sender, 1000);
        parcels.requestTrip(sender, p.getId(), trip.getId());

        assertThatThrownBy(() -> parcels.visible(p.getId(), stranger, false)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> parcels.accept(stranger, p.getId())).isInstanceOf(ApiException.class);
    }

    @Test
    void tripRequiresKyc() {
        User u = new User();
        u.setEmail("nokyc-" + UUID.randomUUID() + "@test.local");
        u.setPasswordHash("x");
        u.setFullName("No Kyc");
        UUID id = users.save(u).getId();
        assertThatThrownBy(() -> newTrip(id, 20000, 12000)).isInstanceOf(ApiException.class);
    }
}
