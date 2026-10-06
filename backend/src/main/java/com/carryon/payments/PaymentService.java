package com.carryon.payments;

import com.carryon.common.ApiException;
import com.carryon.config.AppProperties;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * MOCK escrow ledger. State transitions (HELD -> RELEASED | REFUNDED) are real and enforced; only the
 * money movement is simulated. Swapping in Razorpay/Stripe means calling the gateway inside hold/release/refund
 * with an idempotency key derived from the parcel id (the unique index on parcelId already guarantees one payment
 * per parcel). Callers invoke these inside a transaction (see ParcelService / Tx).
 */
@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentRepository payments;
    private final AppProperties props;

    public Payment hold(UUID parcelId, UUID payerId, long amountPaise) {
        Payment p = new Payment();
        p.setParcelId(parcelId);
        p.setPayerId(payerId);
        p.setAmountPaise(amountPaise);
        p.setPlatformFeePaise(amountPaise * props.platformFeePercent() / 100);
        return payments.insert(p);   // insert (not save): a duplicate parcelId fails on the unique index
    }

    public void release(UUID parcelId) { settle(parcelId, Payment.Status.RELEASED); }

    public void refund(UUID parcelId) { settle(parcelId, Payment.Status.REFUNDED); }

    private void settle(UUID parcelId, Payment.Status target) {
        Payment p = payments.findByParcelId(parcelId).orElseThrow(() -> ApiException.notFound("Payment"));
        if (p.getStatus() != Payment.Status.HELD) {
            throw ApiException.conflict("Payment already " + p.getStatus());
        }
        p.setStatus(target);
        p.setSettledAt(Instant.now());
        payments.save(p);
    }

    public Payment forParcel(UUID parcelId) {
        return payments.findByParcelId(parcelId).orElse(null);
    }
}
