-- PostgreSQL is the single source of truth. Money is stored in paise, weight in grams.

CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    phone         VARCHAR(20),
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
    kyc_status    VARCHAR(20)  NOT NULL DEFAULT 'NONE',
    kyc_reference VARCHAR(64),            -- opaque provider reference; never raw Aadhaar/PAN
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE trips (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    traveller_id        UUID         NOT NULL REFERENCES users (id),
    origin_city         VARCHAR(80)  NOT NULL,
    destination_city    VARCHAR(80)  NOT NULL,
    departure_date      DATE         NOT NULL,
    total_capacity_g    INT          NOT NULL,
    personal_luggage_g  INT          NOT NULL,
    booked_g            INT          NOT NULL DEFAULT 0,
    status              VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Last line of defence against overbooking, enforced by the database itself.
    CONSTRAINT trips_capacity_positive CHECK (total_capacity_g > 0 AND personal_luggage_g >= 0 AND booked_g >= 0),
    CONSTRAINT trips_never_overbooked  CHECK (personal_luggage_g + booked_g <= total_capacity_g)
);
CREATE INDEX idx_trips_route ON trips (lower(origin_city), lower(destination_city), departure_date) WHERE status = 'OPEN';
CREATE INDEX idx_trips_traveller ON trips (traveller_id);

CREATE TABLE parcels (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_id            UUID         NOT NULL REFERENCES users (id),
    trip_id              UUID         REFERENCES trips (id),
    description          VARCHAR(300) NOT NULL,
    weight_g             INT          NOT NULL CHECK (weight_g > 0),
    declared_value_paise BIGINT       NOT NULL CHECK (declared_value_paise >= 0),
    reward_paise         BIGINT       NOT NULL CHECK (reward_paise > 0),
    origin_city          VARCHAR(80)  NOT NULL,
    destination_city     VARCHAR(80)  NOT NULL,
    recipient_name       VARCHAR(120) NOT NULL,
    recipient_phone      VARCHAR(20)  NOT NULL,
    status               VARCHAR(20)  NOT NULL DEFAULT 'CREATED',
    pickup_token         VARCHAR(64)  NOT NULL,   -- QR shown by sender to traveller
    delivery_token       VARCHAR(64)  NOT NULL,   -- QR shown by recipient to traveller
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    delivered_at         TIMESTAMPTZ
);
CREATE INDEX idx_parcels_sender ON parcels (sender_id);
CREATE INDEX idx_parcels_trip ON parcels (trip_id);
CREATE INDEX idx_parcels_status ON parcels (status);

-- Append-only chain of custody / audit trail.
CREATE TABLE parcel_events (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parcel_id  UUID         NOT NULL REFERENCES parcels (id),
    type       VARCHAR(40)  NOT NULL,
    actor_id   UUID,
    note       VARCHAR(300),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_parcel_events_parcel ON parcel_events (parcel_id, created_at);

-- One payment per parcel (UNIQUE) makes acceptance idempotent at the database level.
CREATE TABLE payments (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parcel_id             UUID        NOT NULL UNIQUE REFERENCES parcels (id),
    payer_id              UUID        NOT NULL REFERENCES users (id),
    amount_paise          BIGINT      NOT NULL CHECK (amount_paise > 0),
    platform_fee_paise    BIGINT      NOT NULL CHECK (platform_fee_paise >= 0),
    status                VARCHAR(20) NOT NULL DEFAULT 'HELD',   -- HELD (escrow) | RELEASED | REFUNDED
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    settled_at            TIMESTAMPTZ
);

CREATE TABLE ratings (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parcel_id  UUID        NOT NULL REFERENCES parcels (id),
    rater_id   UUID        NOT NULL REFERENCES users (id),
    ratee_id   UUID        NOT NULL REFERENCES users (id),
    score      INT         NOT NULL CHECK (score BETWEEN 1 AND 5),
    comment    VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (parcel_id, rater_id)
);
CREATE INDEX idx_ratings_ratee ON ratings (ratee_id);

CREATE TABLE disputes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parcel_id       UUID          NOT NULL REFERENCES parcels (id),
    opened_by       UUID          NOT NULL REFERENCES users (id),
    kind            VARCHAR(20)   NOT NULL,                 -- LOST | DAMAGED | NOT_DELIVERED | OTHER
    description     VARCHAR(1000) NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'OPEN',  -- OPEN | REFUNDED | RELEASED
    resolution_note VARCHAR(500),
    resolved_by     UUID REFERENCES users (id),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    resolved_at     TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_dispute_open_per_parcel ON disputes (parcel_id) WHERE status = 'OPEN';

CREATE TABLE notifications (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES users (id),
    parcel_id  UUID,
    message    VARCHAR(300) NOT NULL,
    is_read    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_user ON notifications (user_id, created_at DESC);
