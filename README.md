# Carryon: peer-to-peer parcel delivery with travellers

Senders post parcels; verified travellers with spare luggage capacity carry them on trips they are already making.
Chain of custody uses QR tokens; payment is held in (mock) escrow until delivery is confirmed.

## Stack (deliberately boring)

| Layer | Choice | Why |
|---|---|---|
| API | Spring Boot 4 (Java 21), **modular monolith** | One deployable, clean module boundaries |
| DB | MongoDB (replica set, transactions) | Source of truth for all transactional state |
| Auth | Spring Security + JWT (HS256, stateless) | No extra infra. Cognito is a drop-in swap later |
| Frontend | React + TypeScript + Vite + Tailwind | Simple SPA, static hosting |
| Hosting | Free tier: MongoDB Atlas (DB), Render (API), Cloudflare Pages (web) | See docs/DEPLOY-FREE.md |

**Deploy free:** docs/DEPLOY-FREE.md (Atlas + Render + Cloudflare Pages). CI is in .github/workflows/ci.yml.

**Not used (on purpose):** Redis, Kafka, OpenSearch, Kubernetes, microservices, WebSockets, SQS. See docs/ARCHITECTURE.md for the reasoning and the measurable triggers for adding each.

## Run locally

```bash
docker compose up -d            # MongoDB (single-node replica set)
cd backend && ./mvnw spring-boot:run
```

API on http://localhost:8080. Seeded admin: `admin@carryon.local` / `admin12345` (override with `ADMIN_EMAIL` / `ADMIN_PASSWORD`; set `JWT_SECRET` outside dev).

## Test

```bash
cd backend && ./mvnw test       # needs Docker running (Testcontainers starts a real MongoDB replica set)
```

Includes a concurrency test: 10 parcels race for 8 kg of spare capacity, and exactly 5 are accepted.

## Modules (`com.carryon.*`)

`auth`, `users` (mock KYC), `trips`, `parcels` (state machine + custody events), `matching`, `payments` (mock escrow), `notifications`, `disputes`, `ratings`, `admin`.
