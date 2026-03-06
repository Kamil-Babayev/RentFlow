# RentFlow — Implementation Sequence & Project Scope

> This document defines the full implementation plan for RentFlow, a property rental platform
> built as a microservices portfolio project. It serves as both a development roadmap and
> a scope boundary — if a feature is not described here, it is out of scope.

---

## Technology Stack Reference

| Concern | Technology |
|---|---|
| API Gateway | Go |
| User & Booking Services | Java 21 + Spring Boot 3 |
| Listing, Payment, Notification Services | Go + Gin |
| REST | Gin (Go), Spring MVC (Java) |
| Interservice communication | gRPC |
| Message broker | Apache Kafka |
| Primary database | PostgreSQL (per service, own schema) |
| Cache / ephemeral store | Redis |
| Document store | MongoDB (NotificationService) |
| Object storage | Minio (listing photos) |
| Auth provider | Keycloak (OAuth2 / OpenID Connect) |
| DB migrations | golang-migrate (Go), Flyway (Java) |
| Observability | Prometheus + Grafana + Loki + Jaeger (OpenTelemetry) |
| Resilience | Resilience4j (Java), manual retry/timeout (Go) |
| Containerisation | Docker + Docker Compose |
| Orchestration | Kubernetes (local via kind) |
| CI/CD | GitHub Actions |
| Testing | Testcontainers, Testify/Gomock, JUnit5/Mockito, Pact |

---

## Implementation Order

```
US0 → US1 → US2 → US4 → US3 → US5 → US6
```

US4 (PaymentService) is built before US3 (Booking flow) intentionally —
BookingService depends on a real PaymentService contract, not a stub of a stub.

---

## US0 — Foundation

> **Goal:** Every piece of shared infrastructure is running locally and every
> service skeleton compiles and is reachable before any feature work begins.
> This is the definition of done for "I can start building features."

### US0.1 — Local Infrastructure Stack

Set up a `docker-compose.infra.yml` that brings up all backing services independently
of application code. This file is used during active development so engineers can run
services locally against real infrastructure without starting the full application stack.

**Services to configure:**
- PostgreSQL — one instance, separate databases per service (`users_db`, `listings_db`, `bookings_db`, `payments_db`)
- Redis — single instance, used by ListingService for caching
- Apache Kafka + Zookeeper — single broker sufficient for local development
- Keycloak — with a persistent volume so realm config survives restarts
- Minio — S3-compatible object storage for listing photos
- MongoDB — for NotificationService history

**Acceptance criteria:**
- `make infra-up` starts all backing services
- `make infra-down` stops and removes containers but preserves volumes
- All services pass their own health checks within 60 seconds of startup
- Each Postgres database is created automatically on first boot via init scripts

---

### US0.2 — API Gateway Skeleton

Create the API Gateway as a Go service. At this stage it is a dumb reverse proxy with
no business logic — routing configuration only. It will be evolved throughout later stories.

**Routing table (initial):**
- `/api/v1/users/**` → UserService
- `/api/v1/listings/**` → ListingService
- `/api/v1/bookings/**` → BookingService
- `/api/v1/payments/**` → PaymentService

**Acceptance criteria:**
- Gateway starts and proxies requests to correct upstream
- Returns `502 Bad Gateway` with structured JSON error if upstream is unreachable
- Health endpoint at `GET /health` returns `200 OK`
- Configurable upstream URLs via environment variables

---

### US0.3 — Keycloak Realm Configuration

Configure the `rentflow` realm in Keycloak. This should be exportable as a JSON file
committed to the repository so the realm is recreated automatically on fresh environments.

**Configuration:**
- Realm name: `rentflow`
- Client: `rentflow-gateway` (confidential client, used by API Gateway for token introspection)
- Roles: `owner`, `renter`, `admin` (realm-level roles)
- Token lifespan: access token 15 minutes, refresh token 7 days
- SMTP configured to a local Mailhog container for development email testing
- Password policy: minimum 8 characters, at least one number

**Acceptance criteria:**
- Fresh `make infra-up` recreates realm from exported JSON automatically
- Can obtain a JWT via Keycloak's `/token` endpoint manually (Postman/curl)
- Token contains `roles` claim correctly

---

### US0.4 — Shared Proto Definitions

Create a `/proto` directory at the repository root containing all `.proto` files shared
across services. Both Go and Java services import from this single source of truth.

**Proto files to define (interfaces only at this stage, implementations come later):**
- `listing_service.proto` — `GetListing`, `LockAvailability`, `ReleaseAvailability`
- `user_service.proto` — `GetUserProfile`
- `payment_service.proto` — `InitiatePayment`, `GetPaymentStatus`

**Makefile targets:**
- `make proto-go` — compiles all protos for Go services using `protoc-gen-go-grpc`
- `make proto-java` — compiles all protos for Java services using `protoc-gen-grpc-java`
- `make proto` — runs both

**Acceptance criteria:**
- All proto files compile without errors for both languages
- Generated code is gitignored and regenerated on each build
- Proto files are versioned with a `v1` package namespace

---

### US0.5 — CI Pipeline Skeleton

Set up GitHub Actions with a pipeline that runs on every pull request and on pushes
to `main`. At this stage the pipeline only lints and runs placeholder test stages.
It will be extended as features are added.

**Pipeline stages:**
- `lint-go` — `golangci-lint` across all Go services
- `lint-java` — Checkstyle + SpotBugs on Java services
- `proto-check` — verifies generated proto code is up to date
- `test-unit` — placeholder, exits 0 until real tests exist
- `build-images` — `docker build` for each service, verifies images compile

**Acceptance criteria:**
- Pipeline runs and passes on a clean repository
- Failed lint blocks merge
- Each service has its own `Dockerfile` that builds successfully

---

### US0.6 — Project Makefile

A root-level `Makefile` that provides a single interface for all common developer tasks.

**Targets:**
```
make infra-up          Start all backing infrastructure containers
make infra-down        Stop infrastructure containers
make up                Start full application stack (infra + all services)
make down              Stop everything
make proto             Compile all proto files
make lint              Run all linters across all services
make test-unit         Run unit tests across all services
make test-integration  Run integration tests (requires Docker)
make build             Build all Docker images
make migrate           Run DB migrations for all services
make k8s-deploy        Apply all Kubernetes manifests
make dev-gateway       Run API Gateway locally (outside Docker)
make dev-listing       Run ListingService locally
make dev-booking       Run BookingService locally
```

---

## US1 — Identity & Access

> **Goal:** A user can create an account, authenticate, manage their profile,
> and the API Gateway enforces authentication on all protected routes.
> All downstream services receive user identity via trusted headers — they
> never validate JWTs themselves.

### US1.1 — User Registration

**Service:** UserService (Spring Boot)

A new user submits their details. UserService creates a Keycloak account via the
Keycloak Admin REST API, then creates a profile record in its own Postgres database.
These two writes must be treated as a saga — if Postgres write fails after Keycloak
account creation, the Keycloak account must be rolled back.

**Request:** `POST /api/v1/users/register`
```json
{
  "email": "user@example.com",
  "password": "securepassword123",
  "firstName": "Jane",
  "lastName": "Doe",
  "role": "renter"
}
```

**Response:** `201 Created` with user profile (no password fields)

**Business rules:**
- Email must be unique across the system
- Role can only be `owner` or `renter` at registration — `admin` is assigned manually
- Password is never stored in UserService — Keycloak owns it entirely
- A user can hold both `owner` and `renter` roles simultaneously (assigned later via profile update)

**Acceptance criteria:**
- Keycloak account and Postgres profile are both created or neither is
- Duplicate email returns `409 Conflict` with descriptive error
- Weak password returns `400 Bad Request` referencing password policy
- Unit tests cover saga rollback scenario

---

### US1.2 — User Login

**Service:** API Gateway (Go)

Login is not handled by any application service. The Gateway exposes a convenience
endpoint that proxies credentials to Keycloak's token endpoint and returns the JWT
to the client. This keeps Keycloak's URL internal and gives a consistent API surface.

**Request:** `POST /api/v1/auth/login`
```json
{
  "email": "user@example.com",
  "password": "securepassword123"
}
```

**Response:** `200 OK`
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "expiresIn": 900
}
```

**Acceptance criteria:**
- Invalid credentials return `401 Unauthorized`
- Successful login returns valid JWT verifiable against Keycloak's public key
- Refresh token endpoint also exposed: `POST /api/v1/auth/refresh`

---

### US1.3 — JWT Validation & Header Injection

**Service:** API Gateway (Go)

Every request to a protected route passes through JWT validation middleware in the
Gateway. On success the Gateway injects trusted headers before forwarding. Downstream
services read these headers and trust them completely — they perform no JWT validation.

**Injected headers:**
- `X-User-Id` — UUID of the authenticated user
- `X-User-Roles` — comma-separated list of roles e.g. `owner,renter`
- `X-User-Email` — email address from token claims

**Protected routes:** everything under `/api/v1/**` except `/api/v1/auth/**`
and `GET /api/v1/listings/**` (public browsing requires no auth)

**Acceptance criteria:**
- Missing or expired token on protected route returns `401 Unauthorized`
- Invalid signature returns `401 Unauthorized`
- Valid token results in correct headers on forwarded request
- Public listing routes accessible without token

---

### US1.4 — View & Update Profile

**Service:** UserService (Spring Boot)

**Endpoints:**
- `GET /api/v1/users/me` — returns own profile using `X-User-Id` header
- `PUT /api/v1/users/me` — updates own profile fields

**Updatable fields:** `firstName`, `lastName`, `phoneNumber`, `bio`, `profilePhotoUrl`

**Business rules:**
- Email is not updatable via this endpoint (Keycloak owns it)
- Password is not updatable via this endpoint (see US1.5)
- Users cannot update other users' profiles

**Acceptance criteria:**
- Returns `404 Not Found` if profile does not exist (should not happen post-registration)
- Returns `403 Forbidden` if `X-User-Id` does not match resource owner

---

### US1.5 — Change Password

**Service:** UserService (Spring Boot) → delegates to Keycloak Admin API

**Request:** `PUT /api/v1/users/me/password`
```json
{
  "currentPassword": "oldpassword123",
  "newPassword": "newpassword456"
}
```

**Business rules:**
- Current password must be verified before update is accepted
- New password must meet Keycloak realm password policy

**Acceptance criteria:**
- Wrong current password returns `400 Bad Request`
- Weak new password returns `400 Bad Request` with policy description
- On success all existing refresh tokens for the user are invalidated (Keycloak logout)

---

### US1.6 — Forgot Password

**Service:** API Gateway (Go) → delegates entirely to Keycloak

Keycloak handles the full forgot password flow natively. The Gateway exposes a
single trigger endpoint; the reset email is sent by Keycloak to the configured
SMTP (Mailhog in development).

**Request:** `POST /api/v1/auth/forgot-password`
```json
{ "email": "user@example.com" }
```

**Response:** Always `200 OK` regardless of whether email exists (prevent enumeration)

**Acceptance criteria:**
- Email is sent to Mailhog container in development environment
- Reset link expires after 1 hour (configured in Keycloak realm)
- Always returns 200 regardless of whether the email is registered

---

### US1.7 — Deactivate Account

**Service:** UserService (Spring Boot)

Soft-delete pattern — account is disabled, not removed. Data is preserved for
booking history integrity.

**Request:** `DELETE /api/v1/users/me`

**Actions performed:**
1. Disable account in Keycloak (user can no longer log in)
2. Set `status = DEACTIVATED` and `deactivatedAt` timestamp in UserService Postgres
3. Publish `user.deactivated` event to Kafka (NotificationService and other consumers react)
4. Invalidate all active sessions in Keycloak

**Business rules:**
- Users with active (`CONFIRMED` or `ACTIVE`) bookings cannot deactivate —
  must cancel bookings first
- Deactivated users' listings are automatically deactivated (via Kafka event consumed by ListingService)

**Acceptance criteria:**
- Attempting to log in after deactivation returns `401 Unauthorized`
- Deactivation blocked if active bookings exist — returns `409 Conflict` with list of booking IDs
- `user.deactivated` Kafka event is published reliably (outbox pattern)

---

### US1.8 — Admin: Hard Delete User

**Service:** UserService (Spring Boot)

Available only to users with the `admin` role. Permanently removes all user data.
Intended for GDPR compliance scenarios.

**Request:** `DELETE /api/v1/users/{userId}/hard`

**Actions performed:**
1. Hard-delete profile from Postgres
2. Delete account from Keycloak
3. Publish `user.hard-deleted` event to Kafka

**Acceptance criteria:**
- Returns `403 Forbidden` for non-admin callers
- All related profile data is removed from UserService Postgres
- Downstream services receive Kafka event and handle cleanup

---

## US2 — Listings

> **Goal:** Property owners can manage their listings and availability.
> Any user (authenticated or not) can search and browse listings.
> ListingService is the single source of truth for availability.

### US2.1 — Create Listing

**Service:** ListingService (Go + Gin)

**Request:** `POST /api/v1/listings`

**Fields:** `title`, `description`, `address`, `city`, `country`, `pricePerNight`,
`maxGuests`, `amenities[]`, `houseRules`

**Business rules:**
- Caller must have `owner` role (checked via `X-User-Roles` header)
- `ownerId` is set from `X-User-Id` header — never from request body
- New listings are created in `DRAFT` status, not immediately visible in search

**Acceptance criteria:**
- Returns `403 Forbidden` if caller is not an owner
- Returns `201 Created` with full listing object including generated `listingId`
- Listing is not returned in search results while in `DRAFT` status

---

### US2.2 — Upload Listing Photos

**Service:** ListingService (Go + Gin) → stores files in Minio

**Request:** `POST /api/v1/listings/{listingId}/photos` (multipart/form-data)

**Business rules:**
- Only the listing owner can upload photos
- Maximum 10 photos per listing
- Accepted formats: JPEG, PNG, WebP
- Maximum file size: 5MB per photo
- ListingService generates a presigned Minio URL and returns it —
  the URL is stored as `photoUrl` in the listing record

**Acceptance criteria:**
- Non-owner returns `403 Forbidden`
- Exceeding photo limit returns `400 Bad Request`
- Invalid format or oversized file returns `400 Bad Request`
- Uploaded photo URL is accessible via browser (Minio serves it)

---

### US2.3 — Publish Listing

**Service:** ListingService (Go + Gin)

Transitions a listing from `DRAFT` to `ACTIVE`. Separate from creation to allow
owners to prepare listings before they go live.

**Request:** `PUT /api/v1/listings/{listingId}/publish`

**Business rules:**
- Listing must have at least one photo before it can be published
- Listing must have title, description, address, and price set
- Publishes `listing.published` Kafka event on success

**Acceptance criteria:**
- Missing required fields returns `422 Unprocessable Entity` listing which fields are missing
- Successfully published listing appears in search results
- `listing.published` event is received by NotificationService

---

### US2.4 — Update Listing

**Service:** ListingService (Go + Gin)

**Request:** `PUT /api/v1/listings/{listingId}`

**Business rules:**
- Only listing owner can update
- All fields from US2.1 are updatable except `ownerId`
- Updates to `pricePerNight` do not affect already-confirmed bookings

**Acceptance criteria:**
- Non-owner returns `403 Forbidden`
- Price update on listing with confirmed future bookings returns `200 OK`
  (price change is prospective only — existing bookings are unaffected)

---

### US2.5 — Deactivate / Reactivate Listing

**Service:** ListingService (Go + Gin)

**Requests:**
- `PUT /api/v1/listings/{listingId}/deactivate`
- `PUT /api/v1/listings/{listingId}/reactivate`

**Business rules:**
- Deactivated listings are hidden from search
- Deactivation does not cancel existing confirmed bookings —
  BookingService receives `listing.deactivated` Kafka event and notifies affected renters
- Only the listing owner can deactivate/reactivate

**Acceptance criteria:**
- Deactivated listing returns `404 Not Found` on public detail endpoint
- `listing.deactivated` event published to Kafka on deactivation
- Owner can still view their own deactivated listings via owner-specific endpoint

---

### US2.6 — Search Listings

**Service:** ListingService (Go + Gin)

**Request:** `GET /api/v1/listings?city=London&checkIn=2025-06-01&checkOut=2025-06-07&guests=2&minPrice=50&maxPrice=200`

**Implementation notes:**
- This endpoint is public — no authentication required
- Use goroutines to run Postgres query and Redis cache check in parallel
- Cache search results in Redis with TTL of 5 minutes (key includes all query params)
- Availability filtering: exclude listings with conflicting bookings for requested date range

**Response:** Paginated list of listing summaries (no photo URLs in list view, only thumbnail)

**Acceptance criteria:**
- Returns only `ACTIVE` listings
- Date range filtering correctly excludes unavailable listings
- Cache hit returns results measurably faster (verify with integration test timing)
- Empty results return `200 OK` with empty array, not `404`

---

### US2.7 — View Listing Detail

**Service:** ListingService (Go + Gin)

**Request:** `GET /api/v1/listings/{listingId}`

**Response:** Full listing object including all photos, amenities, availability calendar,
and owner display name (fetched from UserService via gRPC, cached in Redis)

**Business rules:**
- Public endpoint — no authentication required
- Owner name is fetched via `GetUserProfile` gRPC call to UserService
- Cache full listing detail in Redis (TTL 10 minutes, invalidated on listing update)

**Acceptance criteria:**
- Returns `404 Not Found` for deactivated or non-existent listings
- gRPC call failure for owner profile does not fail the endpoint —
  returns listing with `ownerName: "Unknown"` (graceful degradation)
- Cache invalidation occurs within 1 second of listing update

---

### US2.8 — Availability Calendar Management

**Service:** ListingService (Go + Gin)

Owners can manually block dates (maintenance, personal use). The system automatically
blocks dates when a booking is confirmed and unblocks them when cancelled.
This is the only service that writes to the availability table.

**Endpoints:**
- `GET /api/v1/listings/{listingId}/availability?month=2025-06` — returns available/blocked dates
- `POST /api/v1/listings/{listingId}/availability/block` — owner blocks dates manually
- `DELETE /api/v1/listings/{listingId}/availability/block` — owner unblocks dates

**gRPC methods exposed (called by BookingService):**
- `LockAvailability(listingId, checkIn, checkOut, bookingId)` — optimistic lock during payment
- `ConfirmAvailabilityLock(bookingId)` — hard lock after payment confirmed
- `ReleaseAvailabilityLock(bookingId)` — release lock on payment failure or cancellation

**Acceptance criteria:**
- Double-booking is impossible — concurrent `LockAvailability` calls for overlapping
  dates must result in exactly one success and one failure
- Lock automatically expires after 15 minutes if not confirmed (handles payment timeouts)
- Owner cannot manually unblock dates that are locked by a confirmed booking

---

### US2.9 — Owner: View Own Listings

**Service:** ListingService (Go + Gin)

**Request:** `GET /api/v1/listings/mine`

Returns all listings owned by the caller including `DRAFT` and `DEACTIVATED` ones.
Standard search only returns `ACTIVE` listings.

**Acceptance criteria:**
- Returns `403 Forbidden` for non-owners
- Includes listing status and booking count per listing

---

## US3 — Booking Flow

> **Goal:** A renter can book an available listing for a date range.
> The booking flow is transactional across three services (Booking, Listing, Payment)
> and is coordinated via a combination of gRPC calls and Kafka events.
> The booking state machine is the most complex component in the system.

### Booking State Machine

```
                    ┌─────────────┐
                    │   PENDING   │ ← created on booking request
                    └──────┬──────┘
                           │ availability locked (gRPC)
                           │ payment initiated (gRPC)
                           ▼
                  ┌──────────────────┐
                  │ AWAITING_PAYMENT │ ← waiting for Kafka payment event
                  └────────┬─────────┘
              ┌────────────┴──────────────┐
              │ payment.completed          │ payment.failed / payment.timeout
              ▼                            ▼
        ┌───────────┐              ┌─────────────┐
        │ CONFIRMED │              │  CANCELLED  │
        └─────┬─────┘              └─────────────┘
              │ check-in date reached
              ▼
         ┌────────┐
         │ ACTIVE │
         └───┬────┘
             │ check-out date reached
             ▼
       ┌───────────┐
       │ COMPLETED │
       └───────────┘
```

Transitions to `CANCELLED` can also occur from `CONFIRMED` (renter cancels manually).

---

### US3.1 — Initiate Booking

**Service:** BookingService (Spring Boot)

**Request:** `POST /api/v1/bookings`
```json
{
  "listingId": "uuid",
  "checkIn": "2025-06-01",
  "checkOut": "2025-06-07",
  "guestCount": 2,
  "cardNumber": "4111111111111111",
  "cardExpiry": "12/26",
  "cardCvv": "123"
}
```

**Steps performed synchronously:**
1. Validate request fields
2. Call ListingService via gRPC `LockAvailability` — if fails, return `409 Conflict`
3. Create booking record in Postgres with status `PENDING`
4. Call PaymentService via gRPC `InitiatePayment` — if fails, release availability lock and return `502`
5. Transition booking to `AWAITING_PAYMENT`
6. Return `202 Accepted` with `bookingId` and status

**Business rules:**
- Caller must have `renter` role
- `checkOut` must be after `checkIn`
- `guestCount` must not exceed listing's `maxGuests`
- Card details are passed through to PaymentService and never stored in BookingService

**Acceptance criteria:**
- Unavailable dates return `409 Conflict`
- Invalid date range returns `400 Bad Request`
- gRPC call to ListingService failing returns `503 Service Unavailable`
  (Resilience4j circuit breaker applied)
- Card details are not logged anywhere

---

### US3.2 — Payment Event Handling

**Service:** BookingService (Spring Boot) — Kafka consumer

BookingService subscribes to `payment.completed`, `payment.failed`, and `payment.timeout`
events. On each event it transitions the booking state machine accordingly.

**On `payment.completed`:**
1. Call ListingService gRPC `ConfirmAvailabilityLock`
2. Transition booking to `CONFIRMED`
3. Publish `booking.confirmed` to Kafka

**On `payment.failed`:**
1. Call ListingService gRPC `ReleaseAvailabilityLock`
2. Transition booking to `CANCELLED` with reason `PAYMENT_FAILED`
3. Publish `booking.cancelled` to Kafka

**On `payment.timeout`:**
1. Call ListingService gRPC `ReleaseAvailabilityLock`
2. Transition booking to `CANCELLED` with reason `PAYMENT_TIMEOUT`
3. Publish `booking.cancelled` to Kafka
4. Mark booking for manual review (set `requiresReview = true`)

**Idempotency:** each payment event includes `bookingId` and `paymentId`.
If the event has already been processed (check `processedEventIds` in Redis),
discard it silently.

**Acceptance criteria:**
- Each state transition is idempotent — processing the same event twice does not
  produce duplicate state changes or Kafka events
- All three payment outcomes are covered by integration tests using Testcontainers

---

### US3.3 — Cancel Booking

**Service:** BookingService (Spring Boot)

**Request:** `DELETE /api/v1/bookings/{bookingId}`

**Business rules:**
- Only the renter who owns the booking can cancel
- Cancellation is only allowed when booking is in `CONFIRMED` status
- Cancellation within 24 hours of check-in is not permitted
- On cancellation, BookingService publishes `booking.cancelled` and PaymentService
  receives the event and initiates refund flow

**Acceptance criteria:**
- `ACTIVE` or `COMPLETED` bookings cannot be cancelled — returns `409 Conflict`
- Within 24 hours of check-in returns `422 Unprocessable Entity` with reason
- Cancellation publishes `booking.cancelled` event reliably (outbox pattern)

---

### US3.4 — View My Bookings

**Service:** BookingService (Spring Boot)

**Endpoints:**
- `GET /api/v1/bookings/mine` — renter views their bookings
- `GET /api/v1/bookings/listing/{listingId}` — owner views bookings on their listing

**Response:** Paginated list with booking status, listing summary, dates, and total price

**Business rules:**
- Renter sees only their own bookings
- Owner can see bookings on their listings but not card or personal details of renters

**Acceptance criteria:**
- Renter calling owner endpoint returns `403 Forbidden`
- Bookings are ordered by `checkIn` descending by default

---

### US3.5 — Admin: View All Bookings & Manual Review

**Service:** BookingService (Spring Boot)

**Endpoint:** `GET /api/v1/admin/bookings?requiresReview=true`

Allows admin to view and resolve bookings stuck in `AWAITING_PAYMENT` due to
payment timeouts.

**Request:** `PUT /api/v1/admin/bookings/{bookingId}/resolve`
```json
{ "action": "CONFIRM" | "CANCEL" }
```

**Acceptance criteria:**
- Returns `403 Forbidden` for non-admin callers
- `CONFIRM` action triggers availability confirmation and `booking.confirmed` event
- `CANCEL` action triggers availability release and `booking.cancelled` event

---

## US4 — Payment Service

> **Goal:** A mock payment service that behaves like a real payment provider —
> it accepts payment requests, validates cards, simulates processing delay,
> and communicates outcomes asynchronously via Kafka.
> Configurable failure modes enable deterministic testing.

### US4.1 — gRPC Server Setup

**Service:** PaymentService (Go)

Implement the `payment_service.proto` gRPC server.

**Methods:**
- `InitiatePayment(listingId, bookingId, amount, currency, cardDetails, idempotencyKey)`
  → returns `{ paymentId, status: PROCESSING }`
- `GetPaymentStatus(paymentId)`
  → returns current status of a payment

All `InitiatePayment` calls return `PROCESSING` immediately. The actual outcome
is communicated later via Kafka. This mirrors how real payment providers work.

**Acceptance criteria:**
- Duplicate `idempotencyKey` returns the existing `paymentId` without creating a new record
- gRPC server is reachable from BookingService container
- All gRPC methods have corresponding unit tests with mocked dependencies

---

### US4.2 — Card Validation

**Service:** PaymentService (Go)

Performed synchronously before returning `PROCESSING`. Invalid cards are rejected
immediately with a gRPC error — no Kafka event is published.

**Validations:**
- Luhn algorithm check on card number
- Card number length: 13–19 digits
- Expiry date is in the future
- CVV is 3–4 digits

**Acceptance criteria:**
- Invalid card number (fails Luhn) returns gRPC `INVALID_ARGUMENT` error
- Expired card returns gRPC `INVALID_ARGUMENT` error with message "Card expired"
- Valid card proceeds to payment processing
- Luhn algorithm has 100% unit test coverage with known valid/invalid card numbers

---

### US4.3 — Simulated Payment Processing

**Service:** PaymentService (Go)

After returning `PROCESSING`, PaymentService processes the payment asynchronously
in a goroutine. The outcome is determined by configurable environment variables.

**Environment variables:**
```
PAYMENT_FAILURE_RATE=0.15       # 15% of payments fail
PAYMENT_TIMEOUT_RATE=0.05       # 5% of payments time out (no response)
PAYMENT_MIN_DELAY_MS=100        # minimum processing delay
PAYMENT_MAX_DELAY_MS=800        # maximum processing delay
```

**Outcomes:**
- `SUCCESS` → publishes `payment.completed` to Kafka
- `FAILED` → publishes `payment.failed` with a reason code
- `TIMEOUT` → publishes `payment.timeout` after a long delay

**Acceptance criteria:**
- Setting `PAYMENT_FAILURE_RATE=1.0` causes all payments to fail — used in tests
- Setting `PAYMENT_FAILURE_RATE=0.0` and `PAYMENT_TIMEOUT_RATE=0.0` causes all to succeed
- Processing is non-blocking — PaymentService can handle concurrent payment goroutines

---

### US4.4 — Payment Records

**Service:** PaymentService (Go)

Every payment attempt is persisted in Postgres regardless of outcome.

**Schema fields:** `paymentId`, `bookingId`, `listingId`, `amount`, `currency`,
`cardLastFour`, `status`, `failureReason`, `idempotencyKey`, `createdAt`, `resolvedAt`

**Business rules:**
- Card number is never stored — only last 4 digits
- CVV is never stored
- Records are append-only — status is updated, no records are deleted

**Acceptance criteria:**
- Card number is not present anywhere in the database or logs
- `GetPaymentStatus` returns the current status from Postgres

---

### US4.5 — Refund Flow

**Service:** PaymentService (Go) — Kafka consumer

PaymentService subscribes to `booking.cancelled` events. When a booking is cancelled
it checks if a successful payment exists for that booking and, if so, initiates a refund.

**Actions:**
1. Consume `booking.cancelled` event
2. Look up payment record by `bookingId`
3. If payment status is `COMPLETED`, create a refund record and publish `payment.refunded`
4. If payment status is not `COMPLETED`, do nothing (payment never succeeded)

**Acceptance criteria:**
- Cancellation of a booking with no successful payment produces no refund event
- `payment.refunded` event is published exactly once per eligible cancellation (idempotent)
- Refund record is stored in Postgres

---

## US5 — Notifications

> **Goal:** Users receive timely notifications for all significant events.
> NotificationService is entirely event-driven — it exposes no REST endpoints
> and is never called directly. All concurrency patterns are in the service layer.

### US5.1 — Service Setup & Consumer Infrastructure

**Service:** NotificationService (Go)

Set up Kafka consumer groups and the worker pool pattern for processing notifications
concurrently. Each notification type is handled by a dedicated goroutine pool.

**Topics consumed:**
- `booking.confirmed`
- `booking.cancelled`
- `payment.failed`
- `payment.refunded`
- `listing.deactivated`
- `user.deactivated`

**Concurrency pattern:**
- Main goroutine reads from Kafka and dispatches to typed channels
- Worker pool per notification type (configurable pool size via env var)
- Each worker processes one notification: fetches user details via gRPC → sends notification → stores history

**Acceptance criteria:**
- Service recovers from individual notification failures without stopping the consumer
- Failed notifications are retried up to 3 times with exponential backoff
- After 3 failures the event is written to a dead-letter collection in MongoDB

---

### US5.2 — Email Notifications

All emails are sent via a pluggable email provider interface. In development,
the provider is a Mailhog SMTP client. In production it would be replaced with
Mailgun/SendGrid without changing consumer logic.

**Notification types and recipients:**

| Event | Recipients | Subject |
|---|---|---|
| `booking.confirmed` | Renter + Owner | "Your booking is confirmed" / "New booking received" |
| `booking.cancelled` | Renter + Owner | "Booking cancelled" |
| `payment.failed` | Renter | "Payment failed for your booking" |
| `payment.refunded` | Renter | "Your refund is on the way" |
| `listing.deactivated` | Renter (if active booking exists) | "Important: your upcoming booking" |

**Acceptance criteria:**
- All email templates render correctly with dynamic fields
- Emails appear in Mailhog UI in development environment
- gRPC call failure to UserService for recipient details causes notification
  to be retried, not silently dropped

---

### US5.3 — Notification History

**Service:** NotificationService (Go) — stores to MongoDB

Every dispatched notification is stored as a document in MongoDB regardless of
delivery success.

**Document schema:**
```json
{
  "notificationId": "uuid",
  "userId": "uuid",
  "type": "BOOKING_CONFIRMED",
  "channel": "EMAIL",
  "subject": "Your booking is confirmed",
  "status": "DELIVERED | FAILED | PENDING_RETRY",
  "retryCount": 0,
  "sourceEvent": "booking.confirmed",
  "sourceEventId": "uuid",
  "createdAt": "2025-06-01T10:00:00Z",
  "deliveredAt": "2025-06-01T10:00:01Z"
}
```

**Acceptance criteria:**
- Every notification attempt creates a record
- Failed deliveries are stored with `status: FAILED` and `retryCount`
- Dead-lettered notifications have a separate MongoDB collection

---

## US6 — Hardening & Observability

> **Goal:** The system is production-observable, resilient to partial failures,
> deployable to Kubernetes, and has comprehensive automated test coverage.
> This story is worked in parallel with US3-US5 where possible.

### US6.1 — Resilience Patterns

**Service:** BookingService (Spring Boot) — Resilience4j

Apply circuit breakers and retry policies to all outbound gRPC calls.

**Configuration per gRPC call:**
- `ListingService.LockAvailability` — circuit breaker: 50% failure threshold, 30s wait
- `PaymentService.InitiatePayment` — circuit breaker: 50% failure threshold, 60s wait, no retry (idempotency risk)
- `ListingService.ConfirmAvailabilityLock` — retry 3 times with backoff (safe to retry)
- `UserService.GetUserProfile` — retry 2 times, fallback to partial response

**Acceptance criteria:**
- Circuit breaker opens after threshold failures and returns `503` without calling downstream
- Closed circuit logs warning but does not block requests
- All circuit breaker state changes are emitted as metrics to Prometheus

---

### US6.2 — Health Checks

Every service exposes standardised health endpoints:
- `GET /health/live` — liveness: returns `200` if the process is running
- `GET /health/ready` — readiness: returns `200` only if DB, Kafka, and Redis connections are healthy

These are used by Kubernetes liveness and readiness probes.

**Acceptance criteria:**
- Readiness endpoint returns `503` if Postgres is unreachable
- Kubernetes does not route traffic to pods that fail readiness checks

---

### US6.3 — Prometheus Metrics

Each service exposes `GET /metrics` in Prometheus format.

**Custom metrics to instrument:**

| Service | Metric |
|---|---|
| BookingService | `bookings_initiated_total`, `bookings_confirmed_total`, `bookings_cancelled_total` |
| PaymentService | `payments_initiated_total`, `payments_succeeded_total`, `payments_failed_total` |
| ListingService | `listing_search_cache_hits_total`, `listing_search_cache_misses_total` |
| NotificationService | `notifications_dispatched_total`, `notifications_failed_total` |
| API Gateway | `gateway_requests_total{service, status_code}`, `gateway_request_duration_seconds` |

**Acceptance criteria:**
- All metrics are visible in Prometheus UI
- Custom metrics update correctly during integration test runs

---

### US6.4 — Grafana Dashboards

Three dashboards:

**System Overview:** request rate per service, error rate per service, p95 latency per service

**Booking Funnel:** bookings initiated → awaiting payment → confirmed → active → completed,
drop-off rate at each stage, payment failure rate

**Infrastructure:** Kafka consumer lag per topic, Postgres connection pool utilisation,
Redis hit/miss ratio

**Acceptance criteria:**
- All dashboards render correctly with no "No Data" panels when system is under load
- Dashboard JSON is committed to the repository and imported automatically on Grafana startup

---

### US6.5 — Distributed Tracing

Instrument all services with OpenTelemetry SDK. Export traces to Jaeger.

**Trace must span:**
`Gateway → BookingService → (gRPC) ListingService → (gRPC) PaymentService → (Kafka) → NotificationService`

**Acceptance criteria:**
- A single booking request produces a trace viewable end-to-end in Jaeger UI
- Kafka message propagation carries trace context in message headers
- gRPC calls propagate trace context via metadata

---

### US6.6 — Kubernetes Manifests

Write Kubernetes manifests for all services and infrastructure.

**Per application service:**
- `Deployment` with resource requests/limits and liveness/readiness probes
- `Service` (ClusterIP)
- `ConfigMap` for non-sensitive configuration
- `Secret` for credentials (referenced, not committed — use placeholder values)
- `HorizontalPodAutoscaler` — scale on CPU > 70%

**Infrastructure:**
- Postgres, Redis, Kafka, Keycloak, Minio, MongoDB — each as a `StatefulSet` with `PersistentVolumeClaim`

**Ingress:**
- NGINX ingress controller routes external traffic to API Gateway only

**Acceptance criteria:**
- `make k8s-deploy` applies all manifests to a local `kind` cluster successfully
- All pods reach `Running` state within 120 seconds
- Services are accessible via ingress from host machine

---

### US6.7 — Test Coverage

**Unit tests (no Docker required):**
- Luhn algorithm — PaymentService
- Booking state machine transitions — BookingService
- Availability conflict detection — ListingService
- JWT header injection logic — API Gateway
- Kafka event routing logic — NotificationService

**Integration tests (Testcontainers):**
- Full registration + login flow — UserService + Keycloak
- Listing create + search + cache hit — ListingService + Postgres + Redis
- Booking flow: success path — BookingService + ListingService + PaymentService + Kafka
- Booking flow: payment failure path
- Booking flow: payment timeout path
- Notification delivery on booking confirmed — NotificationService + Kafka + MongoDB

**Contract tests (Pact):**
- BookingService (consumer) ↔ ListingService (provider) for `LockAvailability`
- BookingService (consumer) ↔ PaymentService (provider) for `InitiatePayment`

**End-to-end tests:**
- Register user → create listing → initiate booking → confirm payment → verify notification
  (hits all services through the real Gateway against Testcontainers infrastructure)

**Acceptance criteria:**
- Unit tests run in < 30 seconds with no external dependencies
- Integration tests run in CI against Testcontainers
- E2E test suite passes on a clean environment with `make test-e2e`

---

### US6.8 — CI/CD Final Pipeline

Final GitHub Actions pipeline stages:

```
lint → test-unit → test-integration → build-images → scan → test-contract → test-e2e → deploy-kind
```

**Stages:**
- `lint` — golangci-lint + Checkstyle + SpotBugs
- `test-unit` — fast, no containers, fails build on failure
- `test-integration` — Testcontainers, runs on PR
- `build-images` — builds and pushes to GHCR on merge to `main`
- `scan` — Trivy vulnerability scan on all images, fails on CRITICAL CVEs
- `test-contract` — Pact contract verification
- `test-e2e` — full stack on `kind` cluster
- `deploy-kind` — applies k8s manifests, smoke test

**Acceptance criteria:**
- Full pipeline completes in under 15 minutes
- Any stage failure blocks merge
- Image tags use git SHA for traceability

---

## Out of Scope

The following are explicitly out of scope for this project:

- Real payment processing (no Stripe, PayPal, or similar)
- Mobile clients
- Real SMS notifications (email only, via Mailhog in development)
- Multi-region deployment
- Listing reviews or ratings
- Dynamic pricing
- Real-time availability updates via WebSocket
- Multi-currency support

---

## Appendix — Kafka Topic Reference

| Topic | Producer | Consumers | Payload key |
|---|---|---|---|
| `user.deactivated` | UserService | ListingService, NotificationService | `userId` |
| `user.hard-deleted` | UserService | All services | `userId` |
| `listing.published` | ListingService | NotificationService | `listingId` |
| `listing.deactivated` | ListingService | BookingService, NotificationService | `listingId` |
| `booking.confirmed` | BookingService | NotificationService | `bookingId` |
| `booking.cancelled` | BookingService | PaymentService, NotificationService | `bookingId` |
| `payment.completed` | PaymentService | BookingService | `bookingId` |
| `payment.failed` | PaymentService | BookingService, NotificationService | `bookingId` |
| `payment.timeout` | PaymentService | BookingService, NotificationService | `bookingId` |
| `payment.refunded` | PaymentService | NotificationService | `bookingId` |

---

## Appendix — gRPC Method Reference

| Proto file | Method | Caller | Provider |
|---|---|---|---|
| `listing_service.proto` | `GetListing` | BookingService | ListingService |
| `listing_service.proto` | `LockAvailability` | BookingService | ListingService |
| `listing_service.proto` | `ConfirmAvailabilityLock` | BookingService | ListingService |
| `listing_service.proto` | `ReleaseAvailabilityLock` | BookingService | ListingService |
| `user_service.proto` | `GetUserProfile` | ListingService, NotificationService | UserService |
| `payment_service.proto` | `InitiatePayment` | BookingService | PaymentService |
| `payment_service.proto` | `GetPaymentStatus` | BookingService | PaymentService |