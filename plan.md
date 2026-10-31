# Restaurant Food Ordering App — MVP Build Plan

## 1. Project Goal

Build a working restaurant food ordering web application with a customer experience and a small restaurant-admin experience.

**Backend:** Java 21, Spring Boot, Spring Security, Spring Data JPA, Hibernate, PostgreSQL, Flyway  
**Frontend:** Next.js (React) with TypeScript and Tailwind CSS  
**Testing:** JUnit 5, Mockito, Spring Boot Test, Testcontainers (PostgreSQL)  
**Local development:** Git, Docker Compose, Maven, npm/pnpm  
**API documentation:** Springdoc OpenAPI / Swagger UI

The first release should demonstrate the complete workflow: browse a restaurant menu, ask the AI assistant menu and ordering questions, add items to cart, place an order, view order status, and manage the order as restaurant staff.

## 2. MVP Scope

### Must have
- Customer registration and login.
- Role-based access for `CUSTOMER` and `ADMIN`.
- Restaurant and menu browsing.
- Menu categories, food items, prices, and availability.
- Client-side cart with server-side price validation at checkout.
- Order creation, order history, and order status updates.
- Basic admin menu management and incoming-order management.
- A clearly labelled AI chat assistant for grounded menu and ordering FAQs.
- Responsive UI, useful validation/error messages, and automated tests.

**Initial product scope:** one restaurant per deployment. Keep the restaurant entity in the data model, but defer multi-restaurant marketplace browsing, onboarding, and cross-restaurant administration.

### Keep out of the first release
- Delivery-partner application and live GPS tracking.
- Coupons, loyalty points, personalized recommendations, and advanced analytics. The scoped FAQ assistant above is included; it must not act as an order-taking agent.
- Microservices, Kafka, Kubernetes, and distributed caching.
- Real payment processing unless required for the initial demo. Start with cash on delivery or a clearly labelled test-mode payment integration.

## 3. Architecture Principles

- Start with a **modular monolith**: one Spring Boot application and one PostgreSQL database.
- Keep frontend and backend as separate applications in one repository or two repositories; a single repository is convenient for an MVP.
- Organise backend code by domain: `user`, `restaurant`, `menu`, `order`, and `payment`.
- Keep AI chat in a small `chat` feature/service. Call OpenRouter only from the backend; load `OPENROUTER_API_KEY` and the selected model from environment configuration. Never expose provider credentials to the browser or logs.
- Ground assistant replies in current menu and restaurant FAQ data supplied by the backend. Do not let the model set prices, assert availability without current data, access private order/customer data, or execute order or admin actions.
- Bound chat input/output, configure provider timeouts, handle provider failures safely, and keep external calls outside database transactions.
- Use controller → service → repository layers. Controllers handle HTTP, services enforce business rules, repositories handle persistence.
- Use request/response DTOs; do not expose JPA entities directly from REST endpoints.
- Keep secrets in environment variables or a secret manager, never in source control.
- Use Flyway migrations for every schema change.
- Make order creation transactional and calculate prices on the server.
- Preserve an order-item snapshot of the item name and unit price at the time of purchase.
- Add complexity only when a real requirement justifies it.

## 4. Suggested Repository Layout

```text
restaurant-ordering-app/
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/example/foodordering/
│       │   ├── config/
│       │   ├── common/exception/
│       │   ├── user/
│       │   ├── restaurant/
│       │   ├── menu/
│       │   ├── order/
│       │   └── payment/
│       └── main/resources/db/migration/
├── frontend/
│   ├── package.json
│   ├── app/
│   ├── components/
│   ├── lib/
│   └── types/
├── docker-compose.yml
├── .env.example
├── .gitignore
└── README.md
```

Adjust package names and folders to match the generated project. Avoid creating empty abstractions before they are needed.

## 5. Ten Executable Steps

## Step 1 — Initialise the repository and local environment

**Tasks**
1. Create a Git repository and add a README, `.gitignore`, and `.env.example`.
2. Create `backend/` using Spring Initializr with Java 21, Maven, and a compatible stable Spring Boot release.
3. Add dependencies: Spring Web, Spring Security, Spring Data JPA, Validation, PostgreSQL Driver, Flyway, Actuator, and Spring Boot Test. Add Springdoc OpenAPI for API documentation.
4. Create `frontend/` using Next.js with TypeScript and Tailwind CSS.
5. Add a Docker Compose service for PostgreSQL with a named volume and a health check.
6. Configure local environment variables for database URL, username, password, backend URL, frontend URL, and server-only OpenRouter settings (`OPENROUTER_API_KEY`, `OPENROUTER_MODEL`). Document variable names in `.env.example`; never put real values there.
7. Add a simple health endpoint and confirm that the backend can connect to PostgreSQL.

**Acceptance criteria**
- Backend starts successfully.
- Frontend starts successfully.
- PostgreSQL is healthy and accessible from the backend.
- No credentials are committed to Git.

**Commit suggestion:** `chore: initialise backend frontend and local database`

## Step 2 — Model the domain and database

**Initial tables**
- `users`: id, name, email (unique), password_hash, role, created_at.
- `restaurants`: id, name, description, address, active, created_at.
- `menu_categories`: id, restaurant_id, name, display_order, active.
- `menu_items`: id, category_id, name, description, price, image_url, available, created_at, updated_at.
- `orders`: id, customer_id, restaurant_id, status, subtotal, delivery_fee, tax, total, delivery/contact details, created_at, updated_at.
- `order_items`: id, order_id, menu_item_id (nullable if needed for deleted items), item_name_snapshot, unit_price_snapshot, quantity, line_total.
- `payments` (only if needed for the chosen checkout): id, order_id, method, status, provider_reference, created_at.

**Tasks**
1. Draw a simple ER diagram and decide required/optional fields.
2. Use PostgreSQL `NUMERIC`/Java `BigDecimal` for money; never use floating-point types for prices.
3. Add foreign keys, unique constraints, check constraints for positive prices and quantities, and indexes for common lookups.
4. Create the first Flyway migration, such as `V1__create_initial_schema.sql`.
5. Add JPA entities and repositories.
6. Add a small development-only seed dataset for a restaurant, categories, and menu items.

**Acceptance criteria**
- A fresh database can be built entirely from Flyway migrations.
- Relationships and constraints match the domain model.
- Money and historical order-item prices are stored accurately.

**Commit suggestion:** `feat: add initial domain model and database schema`

## Step 3 — Add authentication and access control

**Tasks**
1. Implement registration and login endpoints.
2. Hash passwords with Spring Security's supported `PasswordEncoder` (for example, BCrypt).
3. Implement role-based authorization for `CUSTOMER` and `ADMIN`.
4. Choose a browser session strategy deliberately. Prefer secure, HttpOnly cookies for browser authentication where practical; if using JWT, define expiry, storage, refresh, and revocation behavior instead of storing long-lived tokens in local storage.
5. Protect admin endpoints at the backend, not just by hiding UI links.
6. Configure CORS narrowly for the frontend origin and use CSRF protection appropriate to the chosen cookie/session design.
7. Add validation and consistent error responses for duplicate emails, invalid credentials, and forbidden access.

**Suggested endpoints**
- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/logout` (if applicable to the chosen session strategy)
- `GET /api/auth/me`

**Acceptance criteria**
- Customers can register and log in.
- Passwords are never stored in plain text.
- Admin-only operations reject customer requests.
- Authentication errors do not reveal sensitive account details.

**Commit suggestion:** `feat: implement authentication and role-based access`

## Step 4 — Build restaurant and menu APIs

**Tasks**
1. Implement public restaurant listing and restaurant detail endpoints.
2. Implement menu category and available menu-item listing.
3. Implement admin create/update/deactivate operations for menu items and categories.
4. Use DTOs, validation, pagination where appropriate, and predictable HTTP status codes.
5. Add sorting/filtering only for MVP needs, such as category and availability.
6. Publish endpoint documentation through Swagger UI.

**Suggested endpoints**
- `GET /api/restaurants`
- `GET /api/restaurants/{restaurantId}`
- `GET /api/restaurants/{restaurantId}/menu`
- `GET /api/menu-items/{itemId}`
- `POST /api/admin/restaurants/{restaurantId}/categories`
- `POST /api/admin/categories/{categoryId}/items`
- `PUT /api/admin/menu-items/{itemId}`
- `PATCH /api/admin/menu-items/{itemId}/availability`

**Acceptance criteria**
- Public APIs expose only active restaurants and available items where appropriate.
- Invalid data is rejected with clear validation errors.
- Admin write endpoints require the correct role.

**Commit suggestion:** `feat: implement restaurant and menu management APIs`

## Step 5 — Build the customer-facing frontend

**Pages/components**
- Home / restaurant listing.
- Restaurant detail and menu page.
- Food-item card/detail view.
- Cart page or cart drawer.
- Registration, login, and account state.
- Shared header, loading state, empty state, and error message components.
- A clearly labelled menu and ordering FAQ chat panel.

**Tasks**
1. Set up typed API helpers in `frontend/lib/` and shared response types in `frontend/types/`.
2. Configure the backend base URL via environment variables.
3. Build responsive pages using reusable components.
4. Add cart quantity controls, remove-item action, and subtotal display.
5. Keep the cart in client state or local storage for the MVP; do not treat client prices as authoritative.
6. Handle loading, empty, and error states accessibly.
7. Verify layout on mobile and desktop widths.
8. Add the chatbot backend endpoint/service and typed frontend client. Send only the current restaurant's public menu/FAQ context to OpenRouter; do not send credentials, checkout details, or private order data.
9. Set request and response size limits, provider timeout, safe error/fallback UI, and basic abuse/rate controls appropriate to the deployment.
10. Add tests for prompt/context construction and provider errors using a mocked OpenRouter client; do not require a live API key in automated tests.

**Acceptance criteria**
- A visitor can browse restaurant menus without logging in if that is the selected product behavior.
- Items can be added, removed, and repriced in the visible cart.
- UI works on common mobile and desktop sizes.
- Chat answers menu/ordering FAQs from supplied current data, is labelled as AI, and fails gracefully when the provider is unavailable.
- Chat cannot create/change orders, reveal private data, or invent prices or availability as authoritative facts.

**Commit suggestion:** `feat: build restaurant browsing and shopping cart UI`

## Step 6 — Implement checkout and transactional order creation

**Tasks**
1. Define the checkout request DTO with item IDs, quantities, and required contact/delivery information.
2. Validate that quantities are positive and within reasonable limits.
3. Load the current menu items from PostgreSQL; reject missing or unavailable items.
4. Recalculate each line total, subtotal, fees, tax, and final total on the backend.
5. Create the order and order-item snapshots in a single service-layer transaction.
6. Define an idempotency approach or duplicate-submission protection for checkout retries.
7. Return the created order ID and a concise order summary.
8. Never accept a final total supplied by the browser as authoritative.

**Suggested endpoint**
- `POST /api/orders`

**Acceptance criteria**
- A valid checkout creates one consistent order.
- Invalid or unavailable items cannot be ordered.
- Changing a browser-submitted price does not change the server-calculated total.
- A failed transaction does not leave a partial order.

**Commit suggestion:** `feat: implement transactional order checkout`

## Step 7 — Add order history and order-status management

**Tasks**
1. Add a customer endpoint to list the authenticated user's orders.
2. Add a detail endpoint that verifies the order belongs to the authenticated user, unless the caller is an authorised admin.
3. Add an admin view of incoming orders, with pagination if needed.
4. Enforce allowed status transitions in the service layer.
5. Define cancellation rules explicitly; do not allow arbitrary status changes.
6. Show order ID, items, totals, creation time, and current status in the UI.

**Suggested endpoints**
- `GET /api/orders/my`
- `GET /api/orders/{orderId}`
- `GET /api/admin/orders`
- `PATCH /api/admin/orders/{orderId}/status`

**Initial status flow**
`PLACED → CONFIRMED → PREPARING → READY → COMPLETED`

Add `CANCELLED` only with clear transition rules. Adapt statuses to the restaurant's real workflow.

**Acceptance criteria**
- Customers can only see their own orders.
- Admins can view and update orders.
- Invalid status transitions are rejected.

**Commit suggestion:** `feat: add order history and order administration`

## Step 8 — Complete checkout and the admin dashboard

**Tasks**
1. Decide whether the MVP uses cash on delivery or a payment provider's test mode.
2. Represent payment method and payment status separately from order status where payments are implemented.
3. If using a gateway, create payment attempts on the server and verify webhook signatures server-side. Never trust a browser redirect as proof of payment.
4. Make payment callbacks idempotent and handle delayed/failing callbacks.
5. Build a minimal admin dashboard for menu availability and incoming orders.
6. Display actionable success/failure feedback to the customer.

**Acceptance criteria**
- The chosen payment path is unambiguous to users.
- Payment status cannot be forged by changing client-side state.
- Admins can operate the basic restaurant workflow without direct database edits.

**Commit suggestion:** `feat: complete checkout and admin operations`

## Step 9 — Harden and document the completed slices

**Tasks**
1. Ensure focused tests were added alongside each feature; add any missing unit and integration coverage for pricing, validation, order transitions, persistence, auth, ownership, and admin access.
2. Cover invalid quantities, unavailable menu items, duplicate checkout requests, payment failure paths, chatbot grounding, provider errors, and input/output limits.
3. Add a global exception handler and consistent error payloads.
4. Add structured logs and Actuator health/readiness endpoints without exposing secrets.
5. Review SQL indexes, N+1 query risks, input validation, CORS, CSRF, AI prompt/context boundaries, provider timeouts, rate controls, and secret handling.
6. Document local setup, environment variables (including server-only OpenRouter configuration), migrations, test commands, API docs, chatbot limitations, and known limitations in `README.md`.

**Acceptance criteria**
- Critical customer and admin journeys are covered by automated tests.
- Tests can run from a clean checkout with documented prerequisites.
- No credentials or sensitive data are written to logs.

**Commit suggestion:** `test: cover critical ordering workflows and harden app`

## Step 10 — Prepare an optional deployment

This step follows acceptance of the locally runnable MVP; deployment is not a prerequisite for declaring the core MVP complete unless a hosted demo is explicitly required.

**Tasks**
1. Build production frontend and backend configurations.
2. Deploy the Next.js frontend, Spring Boot backend, and managed PostgreSQL instance to suitable hosting.
3. Configure HTTPS, allowed origins, environment secrets, and database connection settings.
4. Run Flyway migrations through a controlled deployment step.
5. Configure database backups and test a restore procedure.
6. Add CI to build the backend, run tests, and build the frontend on pull requests.
7. Seed demo data through a controlled development/demo process; do not expose default admin credentials.
8. Run a production smoke test from registration through order completion.
9. Collect feedback from a few test users and prioritise usability fixes.

**Acceptance criteria**
- The app is reachable over HTTPS.
- A complete order can be placed and processed in the deployed environment.
- Health checks, backups, and basic operational documentation are in place.

**Commit suggestion:** `chore: deploy MVP and document release process`

## 6. API Contract Checklist

Use a consistent JSON format and standard HTTP status codes. Keep exact fields aligned with the implementation.

| Capability | Method and route | Access |
|---|---|---|
| Register | `POST /api/auth/register` | Public |
| Login | `POST /api/auth/login` | Public |
| Current user | `GET /api/auth/me` | Authenticated |
| Restaurant list | `GET /api/restaurants` | Public |
| Restaurant menu | `GET /api/restaurants/{id}/menu` | Public |
| Create order | `POST /api/orders` | Customer |
| Ask menu/ordering FAQ assistant | `POST /api/chat/messages` | Public, rate-limited |
| My orders | `GET /api/orders/my` | Customer |
| Order details | `GET /api/orders/{id}` | Owner or admin |
| Manage menu | `/api/admin/menu-items/**` | Admin |
| List orders | `GET /api/admin/orders` | Admin |
| Update order status | `PATCH /api/admin/orders/{id}/status` | Admin |

## 7. Definition of Done

- [ ] Backend and frontend run locally from the README instructions.
- [ ] Database can be recreated using Flyway migrations.
- [ ] Customer can register/login, browse a menu, and manage a cart.
- [ ] Server validates current prices and availability at checkout.
- [ ] Order and item snapshots persist transactionally.
- [ ] Customer can view only their own order history.
- [ ] Admin can manage menu availability and process orders.
- [ ] AI chat answers only scoped menu/ordering FAQs using current public data and does not expose provider keys or private customer/order data.
- [ ] Critical authorization and ordering rules have automated tests.
- [ ] Local setup, health checks, API docs, and release instructions are documented.

## 8. Future Enhancements — Not MVP Blockers

Only prioritise these after the core flow is stable and users have validated the product.

1. Multi-restaurant marketplace browsing, restaurant onboarding, and restaurant-scoped staff administration.
2. Real online payments, refunds, and reconciliation.
3. Email/SMS/push notifications and live order updates.
4. Delivery partner assignment, delivery zones, and location tracking.
5. Search, filters, ratings, reviews, offers, loyalty, and personalized AI recommendations.
6. Redis caching for measured hot reads.
7. Outbox pattern and message broker for reliable asynchronous work.
8. Extract services only where independent deployment/scaling or team ownership provides a clear benefit.
9. Metrics, distributed tracing, alerting, and load testing.
10. Read replicas, partitioning, autoscaling, and orchestration if measured traffic requires them.

## 8.1 Ad hoc feature — Email OTP password recovery

Requested after Step 7 as an additional authentication feature.

**Tasks**
1. Add a generic `POST /api/auth/forgot-password` endpoint that sends a six-digit OTP to the account email without revealing whether the account exists.
2. Persist only a BCrypt hash of the OTP, expire it after 10 minutes, limit guesses to five, and apply a 60-second resend cooldown.
3. Add `POST /api/auth/reset-password` to validate the OTP and replace the password with a BCrypt hash; invalidate the OTP after use.
4. Send mail through configurable SMTP settings and keep credentials in environment configuration.
5. Add sign-in UI for requesting a code and setting a replacement password.
6. Add unit and controller tests for generic responses, OTP validation, expiry, attempt limits, one-time use, password hashing, and delivery behavior.

**Acceptance criteria**
- Existing and unknown emails receive the same public response.
- A valid email OTP can reset a password once; expired, malformed, reused, or repeatedly guessed codes are rejected.
- OTPs and passwords are never stored in plaintext.
- SMTP is configured outside source control before email delivery is enabled in a deployment.

**Status:** Implemented. Email is supported because accounts currently have email identities; SMS recovery remains out of scope until phone numbers and an SMS provider are added.

## 9. Current implementation status

Reviewed against the repository on 2026-10-29. Steps 1-5 are implemented: local backend/frontend/database setup, initial schema and entities, session authentication, public restaurant/menu APIs, admin menu operations, and the customer storefront with FAQ assistant.

Step 6 is implemented: authenticated customers can submit checkout details from the cart. The backend re-reads current menu items and prices, rejects unavailable or invalid lines, recalculates totals, and creates orders with immutable item snapshots in a transaction. A customer-scoped UUID idempotency key safely replays an unchanged checkout and rejects reuse with changed details. Tax and delivery fees are both zero for now, per product direction; payment remains deferred to Step 8. Backend integration tests cover price tampering, snapshots, unavailable items, quantity and duplicate-line validation, rollback, idempotency, and access control. Frontend tests cover the checkout payload, retry key, errors, and returning to the cart.

Step 7 is implemented: customers can view paginated order history and owner-only details; admins can view and filter a paginated incoming-order queue, inspect delivery details, and update status through the defined workflow. The storefront includes customer order history/detail and an admin order desk. Cancellation is allowed only from PLACED or CONFIRMED, before preparation starts.

Ad hoc email OTP password recovery is implemented alongside authentication: users can request a code and set a new password from the sign-in modal. OTPs are BCrypt hashed, expire after 10 minutes, allow five failed attempts, and have a 60-second resend cooldown. The public request message is generic to reduce account enumeration. SMTP settings are environment-configured; mobile/SMS recovery is deferred because accounts do not have phone numbers.

Next: Step 8 - Complete the admin dashboard and choose the MVP payment method. Steps 8-10 remain future work in the sequence above. Settle the open items in section 9.1 first; items 1-3 there block useful admin-dashboard work.

## 9.1 Open items to settle before Step 8

Recorded 2026-10-30 after reviewing the repository against this plan and `docs/`. These are gaps between what the plan/docs claim and what the code does. Clear or consciously accept each one before starting Step 8, because the first three block any realistic admin-dashboard work.

### 1. No way to create an ADMIN account (blocker for Step 8)

`UserAccountService.register` always assigns `UserRole.CUSTOMER`, the `development` seeder creates no accounts, and no migration inserts one. The admin order desk and every `/api/admin/**` endpoint are therefore unreachable without manually updating `users.role` in SQL. `AGENTS.md` section 5 forbids a publicly accessible default admin with a committed password, so Step 8 needs a documented, local-only bootstrap - for example an environment-configured admin promotion on startup under the `development` profile, or a documented one-off SQL/CLI step in the README. Decide the mechanism before building admin screens.

### 2. Admin menu management has no UI

The admin category and menu-item APIs are complete and documented in `docs/restaurant-menu-api.md`, but the frontend only has the admin order desk in `order-center.tsx`. Step 8's "minimal admin dashboard for menu availability and incoming orders" is unbuilt on the menu side. The ordering half already exists and should be reused, not rewritten.

### 3. `docs/local-validation-test-cases.md` is stale

That file is dated 2026-10-20, records schema version 1 and 12 backend tests, and still states that "Restaurant/menu browsing and order placement endpoints are not implemented yet". It predates the menu, checkout, order-workflow, and OTP slices (now `V1`-`V3`). TC-01 to TC-18 cover the authentication slice only. Re-run and extend it, or mark it explicitly as an authentication-slice snapshot, so it stops reading as current end-to-end validation.

### 4. Testcontainers is specified but not used

Section 1 of this plan and `AGENTS.md` sections 2 and 9 name Testcontainers with PostgreSQL for integration tests. `backend/pom.xml` has no Testcontainers dependency and there is no `backend/src/test/resources`, so the `@SpringBootTest @ActiveProfiles("test")` suites run against the datasource from the root `.env` - the Compose database on port 5433, which must be running before `mvnw.cmd test`. Either add Testcontainers or amend the stated standard; until then, document the running-database prerequisite wherever test commands are listed.

### 5. Payment state has no schema yet

`docs/data-model.md` defers the `payments` table until a payment method needs persistent state, and checkout currently hard-codes tax and delivery fee to `0.00`. Step 8 task 1 (cash on delivery versus a provider test mode) determines whether a `V4` migration for payment method/status is required and whether the zero-fee assumption stays. Settle the payment decision before writing the migration, and keep payment status separate from order status per `AGENTS.md` section 5.
