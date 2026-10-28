# Carryon: peer-to-peer parcel delivery with travellers

Senders post parcels; verified travellers with spare luggage capacity carry them on trips they are already making.
Chain of custody uses QR tokens; payment is held in (mock) escrow until delivery is confirmed.

## Stack (deliberately boring)

| Layer | Choice | Why |
|---|---|---|
| API | Spring Boot 4 (Java 21), **modular monolith** | One deployable, clean module boundaries |
| DB | MongoDB (replica set, transactions) | Source of truth for all transactional state |
| Auth | Spring Security + JWT (HS256, stateless) | No extra infra. A managed identity service is a drop-in swap later |
| Frontend | React + TypeScript + Vite + Tailwind | Simple SPA, static hosting |
| Hosting | Free tier: MongoDB Atlas (DB), Render (API), Cloudflare Pages (web) | See docs/DEPLOY-FREE.md |

**Not used (on purpose):** Redis, Kafka, a search engine, Kubernetes, microservices, WebSockets, queues. See docs/ARCHITECTURE.md for the reasoning and the measurable triggers for adding each.

## Run locally (Windows)

Prerequisites: Docker Desktop (running), Java 21+, Node 20+.

```powershell
.\start-dev.ps1
```

It starts MongoDB, the API and the website, then opens http://localhost:5173.

| Account | Login | Password |
|---|---|---|
| Traveller (verified, has trips) | `traveller@carryon.demo` | `demo12345` |
| Sender | `sender@carryon.demo` | `demo12345` |
| Admin | `admin@carryon.local` | `admin12345` |

Demo accounts exist only when `SEED_DEMO=true` (the script sets it). Change `ADMIN_PASSWORD` and set `JWT_SECRET` outside local development. With `SPRING_PROFILES_ACTIVE=prod` the app refuses to start on the default secrets.

Manual start: `docker compose up -d`, then `cd backend; .\mvnw spring-boot:run`, then `cd frontend; npm install; npm run dev`.

## Demo flow

1. Log in as the **sender**, create a Delhi → Mumbai parcel, click **Request** on the matching trip.
2. Log in as the **traveller** (private window), **Accept** the request.
3. Sender: copy the pickup code under the QR. Traveller: paste it and confirm pickup. Repeat with the delivery code.
4. Sender: **Confirm delivery** (payment released), then rate. Or report a problem and resolve it as admin.

## Test

```powershell
cd backend
.\mvnw test       # needs Docker running (Testcontainers starts a real MongoDB replica set)
```

18 tests, including login/security over HTTP and a concurrency test: 10 parcels race for 8 kg of spare capacity and exactly 5 are accepted.

## Modules (`com.carryon.*`)

`auth`, `users` (mock KYC), `trips`, `parcels` (state machine + custody events), `matching`, `payments` (mock escrow), `notifications`, `disputes`, `ratings`, `admin`.

## Deploy free

docs/DEPLOY-FREE.md (Atlas + Render + Cloudflare Pages). CI: `.github/workflows/ci.yml`.

## Honest scope

KYC and payments are simulated (the state machines are real). Parcel photo upload and real-time push are not built. Notifications are polled every 20 s.
