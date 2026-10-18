# AGENTS.md — Restaurant Food Ordering App MVP

These instructions apply to the entire repository unless a more specific `AGENTS.md` exists in a subdirectory. Follow them whenever implementing, reviewing, testing, or documenting this project.

## 1. Mission and delivery approach

Build a usable, secure, end-to-end restaurant food ordering MVP. The primary journey is:

1. Browse the restaurant menu and ask the AI assistant menu or ordering questions.
2. Add available food items to a cart.
3. Register or log in.
4. Submit checkout details and place an order.
5. View the order and its current status.
6. As restaurant staff/admin, manage menu availability and process incoming orders.

The initial MVP supports one restaurant per deployment. Keep the restaurant domain in the model, but defer multi-restaurant marketplace onboarding and cross-restaurant administration unless the user changes this scope.

Work in small, reviewable increments. Read `plan.md` and the existing code before changing anything. Implement the next incomplete step from the plan rather than generating the entire application in one pass. Keep the application runnable after each meaningful change. If the repository differs from the suggested structure, follow its established conventions unless they conflict with these instructions.

Before coding:
- Inspect the repository structure, README, build files, migrations, tests, and Git status.
- Identify what already works and avoid replacing working code without a reason.
- State the intended change briefly, then implement it.
- If a decision is low-risk, choose a sensible default and document it. Ask only when a missing requirement blocks safe progress.

After coding:
- Run the narrowest relevant tests, then broader checks where practical.
- Fix failures caused by the change. Do not claim tests passed unless they were run and passed.
- Summarize files changed, behavior delivered, commands run, and any known limitations or next step.

## 2. Approved stack

### Backend
- Java 21 LTS.
- Spring Boot 3.x compatible stable release unless the repository already uses a newer compatible release.
- Maven and the Maven Wrapper (`./mvnw` / `mvnw.cmd`).
- Spring Web, Spring Security, Spring Data JPA, Bean Validation, PostgreSQL JDBC driver, Flyway, Actuator, and Spring Boot Test.
- Springdoc OpenAPI / Swagger UI for API documentation.
- JUnit 5, Mockito, and Testcontainers with PostgreSQL for integration tests where practical.

Do not upgrade major framework versions, change build systems, or add dependencies without checking compatibility and explaining the reason.

### Frontend
- Next.js App Router, React, TypeScript with strict mode, and Tailwind CSS.
- Use the package manager already selected by the lockfile (npm, pnpm, or yarn). Do not introduce a second package manager.
- Prefer server components by default; use client components only where interactivity or browser APIs require them.
- Use a small, consistent component system and typed API client. Avoid adding a large UI/state-management library unless the need is clear.

### Database and local environment
- PostgreSQL as the source of truth for application data.
- Docker Compose for local dependencies, with a named database volume and health check.
- Environment variables for local configuration; commit `.env.example`, never real `.env` files or secrets.

## 3. Architecture boundaries

Use a **modular monolith**: one Spring Boot deployment, one PostgreSQL database, and a separately run Next.js frontend. Keep domain boundaries clear without introducing distributed-system complexity.

Backend domains should be organised by feature, for example:

- `user` / `auth`
- `restaurant`
- `menu`
- `order`
- `payment` only to the extent required by the selected MVP payment approach
- `common` for genuinely shared infrastructure such as errors and API configuration

Within each feature, use clear controller → service → repository responsibilities:

- **Controller:** HTTP mapping, authentication context, DTO validation, status codes. No business logic or direct persistence logic.
- **Service:** business rules, authorization-sensitive decisions, transactions, orchestration.
- **Repository:** persistence queries only.
- **DTOs:** explicit request and response contracts. Never serialize JPA entities directly.
- **Entities:** persistence model only; avoid returning entities or leaking lazy-loaded relationships through the API.
- Keep AI chat in a small backend feature/service. Call OpenRouter from the server only, using environment configuration such as `OPENROUTER_API_KEY` and `OPENROUTER_MODEL`; never put the key in frontend code, `NEXT_PUBLIC_*` variables, source control, or logs.
- Ground assistant responses in current public menu/FAQ data assembled by the backend. Do not send credentials, checkout details, or private order/customer data to the model. The assistant must not create or modify orders or admin data.
- Bound user input and model output, configure provider timeouts, handle provider failures safely, and keep external AI calls outside database transactions. Do not claim prices or availability unless supported by current server-provided data.

Do not create interfaces, generic frameworks, factories, base classes, event buses, or abstractions without a real current use case. Prefer simple, testable code over speculative extensibility.

## 4. MVP scope and non-goals

### Required for MVP
- Customer registration and login.
- Roles `CUSTOMER` and `ADMIN` with server-enforced access control.
- Public restaurant listing, restaurant detail, menu categories, and available menu items.
- A clearly labelled AI chat assistant for menu and ordering FAQs, grounded in current public restaurant/menu data.
- Responsive customer UI and a cart that supports add, remove, and quantity changes.
- Checkout that validates current menu availability and calculates all prices on the backend.
- Persistent orders and immutable item-name/unit-price snapshots at purchase time.
- Customer order history and order details restricted to the owner or an admin.
- Admin menu availability/management and order status management.
- Database migrations, useful validation/error messages, automated tests, API docs, local setup documentation, and a deployable configuration.

### Explicitly defer unless the user asks
- Microservices, Kafka/RabbitMQ, Kubernetes, Redis, distributed caching, and event-driven architecture.
- Delivery-partner apps, live GPS tracking, coupons, loyalty, reviews, recommendations, and advanced analytics.
- Real payment processing. Start with a clearly labelled MVP payment method such as cash on delivery or a test-only payment option. Never pretend a payment succeeded.
- AI-powered order taking, order changes, private order lookup, personalized recommendations, or actions beyond menu/ordering FAQs.
- Complex multi-tenant restaurant onboarding or marketplace commissions.

Keep future extension points clean, but do not implement deferred features pre-emptively.

## 5. Backend coding standards

- Use constructor injection; do not use field injection.
- Use descriptive names and small methods with one responsibility.
- Use Bean Validation on inbound DTOs (`@NotBlank`, `@Email`, `@Positive`, `@Size`, etc. as appropriate). Validate business invariants again in services.
- Use `BigDecimal` for money. Never use `float` or `double` for currency. Define a consistent currency (initially INR unless product requirements say otherwise) and scale/rounding policy.
- Use `Instant` or `OffsetDateTime` for timestamps; store timestamps consistently and document timezone behavior.
- Use enums for fixed values such as roles and order/payment statuses.
- Mark multi-step business operations with appropriate `@Transactional` boundaries. Keep transactions short and do not make network calls inside database transactions.
- Add database constraints and indexes for important invariants and query patterns, not just Java validation.
- Avoid N+1 query patterns; use focused repository queries, projections, entity graphs, or fetch joins when needed. Do not indiscriminately eager-load relationships.
- Use pagination for potentially growing lists, especially admin order lists.
- Avoid Lombok unless it is already established in the project; if used, do not generate unsafe entity `toString`, `equals`, or `hashCode` methods over relationships.
- Never expose stack traces, SQL, secrets, password hashes, tokens, or internal exception messages in API responses.

### Authentication and authorization
- Hash passwords with a suitable adaptive password encoder such as BCrypt; never store or log plain-text passwords.
- Enforce authorization in Spring Security and, where relevant, verify resource ownership in the service layer. Frontend route guards are only a user-experience feature, not security.
- A customer may only view their own orders. Admin access must be checked on every protected backend endpoint.
- Do not return whether an email exists in a way that unnecessarily enables account enumeration.
- Choose and document the session/token strategy before implementation. If JWT is used, validate signature, expiry, and claims server-side and handle secrets safely. Do not store long-lived credentials in insecure browser storage by default.
- Configure CORS for the known frontend origin(s), not `*` with credentials. Make CSRF decisions based on the actual authentication mechanism; do not disable protections blindly.
- Never create a publicly accessible default admin account with a committed password. Use a documented, local-only bootstrap method or controlled environment configuration.

### Order and pricing invariants
- The client may submit item IDs and quantities, but **must not be trusted for prices, discounts, taxes, fees, or the final total**.
- Re-read current menu items from PostgreSQL at checkout and reject nonexistent, inactive, unavailable, or invalid items.
- Validate quantities and reject duplicate/invalid lines or consolidate them consistently.
- Calculate all totals on the backend using `BigDecimal` and a documented rounding rule.
- Create the order and its order-item snapshots in one database transaction. Save item name and unit price at purchase time so later menu edits do not rewrite order history.
- Protect against accidental duplicate order submission where feasible; do not claim idempotency unless it is implemented and tested.
- Define and enforce valid status transitions. Initial flow: `PLACED → CONFIRMED → PREPARING → READY → COMPLETED`; cancellation is allowed only under explicitly defined rules.
- Keep order status separate from payment status if payment state exists. Never infer payment success from a client-side redirect or a request body flag.

## 6. REST API conventions

- Prefix application APIs with `/api`.
- Use resource-oriented URLs and correct HTTP methods/status codes.
- Use JSON request/response bodies and DTOs.
- Return `201 Created` for successful resource creation where appropriate, `400` for invalid input, `401` for unauthenticated access, `403` for forbidden access, `404` for missing resources, and `409` for state conflicts where appropriate.
- Use a consistent error response with a safe message, stable error code, optional field validation details, and a trace/correlation ID if available. Never include stack traces.
- Keep OpenAPI docs aligned with actual endpoint behavior.
- Avoid breaking API changes without updating frontend types, tests, and documentation in the same change.

Expected initial API surface (adjust consistently to the implemented model):

| Capability | Method and route | Access |
|---|---|---|
| Register | `POST /api/auth/register` | Public |
| Login | `POST /api/auth/login` | Public |
| Current user | `GET /api/auth/me` | Authenticated |
| Restaurant list | `GET /api/restaurants` | Public |
| Restaurant details | `GET /api/restaurants/{restaurantId}` | Public |
| Restaurant menu | `GET /api/restaurants/{restaurantId}/menu` | Public |
| Menu/ordering FAQ chat | `POST /api/chat/messages` | Public, rate-limited |
| Create order | `POST /api/orders` | Customer |
| My orders | `GET /api/orders/my` | Customer |
| Order details | `GET /api/orders/{orderId}` | Owner or admin |
| Admin order list | `GET /api/admin/orders` | Admin |
| Update order status | `PATCH /api/admin/orders/{orderId}/status` | Admin |
| Admin menu operations | `/api/admin/**` menu routes | Admin |

Do not implement every possible endpoint in advance. Deliver only endpoints needed by the current vertical slice.

## 7. Database and migrations

- Use Flyway as the schema-change source of truth. Every schema change must have a new, ordered migration such as `V1__create_users.sql`; do not edit a migration that may already have run in shared or deployed environments.
- Do not rely on Hibernate schema auto-update in production. Use validation or a similarly safe schema mode outside disposable local development.
- Keep database credentials and URLs configurable by environment.
- Add foreign keys, unique constraints, `NOT NULL` constraints, and check constraints where they encode real invariants.
- Use stable ID types consistently across the application. Do not mix ID types casually.
- Seed demo data only through an explicit development/demo profile or documented seed script. Never seed real credentials or production test data automatically.
- Use PostgreSQL in integration tests when behavior depends on PostgreSQL. H2 is not a full substitute for PostgreSQL-specific behavior.

## 8. Frontend standards

- Keep TypeScript strict; avoid `any`. Use explicit types for API request/response payloads and UI state.
- Put API access in a small shared module (for example `frontend/lib/api.ts`) rather than duplicating raw `fetch` calls throughout components.
- Keep backend base URL configurable via a public environment variable such as `NEXT_PUBLIC_API_BASE_URL`; validate required environment configuration and document it.
- Handle loading, empty, success, validation, and network-error states for every primary screen.
- Make forms accessible: associated labels, keyboard navigation, visible focus, sensible validation feedback, and semantic HTML.
- Ensure the main flows work on mobile and desktop. Avoid hard-coded viewport assumptions.
- Keep cart state client-side for the MVP if convenient, but treat it only as a convenience. The backend remains authoritative for availability and price.
- Never put secrets in `NEXT_PUBLIC_*` variables; these are exposed to the browser.
- The OpenRouter key is a backend secret. The frontend may call the app's chat endpoint, but must never call OpenRouter directly.
- Avoid excessive global state. Use local component state or a small shared store only when necessary.
- Do not add mock/fake successful API responses as a substitute for incomplete backend functionality. Clearly isolate any demo fixtures and label them as such.

## 9. Testing requirements

Add tests alongside each feature, not as a final afterthought.

### Backend
- Unit-test service rules, especially pricing, validation, order status transitions, and authorization-sensitive behavior.
- Test controller validation and expected HTTP status/error payloads where useful.
- Integration-test persistence and transactional order creation against PostgreSQL, preferably with Testcontainers.
- Cover registration/login, admin-only routes, customer order ownership, unavailable items, invalid quantities, recalculated totals, and failure/rollback behavior.
- Test chatbot context construction, input/output limits, and provider error handling with a mocked client; automated tests must not require a live OpenRouter key or call the provider.

### Frontend
- Run linting and TypeScript checks.
- Add focused tests for cart calculations and important interaction flows when the project has a test setup.
- Verify loading, empty, and error states, not only the happy path.

### Minimum verification commands
Use the project's existing scripts where present. Typical commands are:

```bash
# Backend (from backend/)
./mvnw test
./mvnw verify

# Windows backend
mvnw.cmd test

# Frontend (from frontend/)
npm run lint
npx tsc --noEmit
npm run build
```

Do not silently skip failing tests, weaken assertions, or change tests merely to make incorrect behavior pass. If a command cannot run because a dependency/service is unavailable, report that limitation explicitly.

## 10. Security, privacy, and operational safety

- Do not commit secrets, access tokens, private keys, production data, or `.env` files.
- Update `.gitignore` when adding generated files, local environment files, or build outputs.
- Never log passwords, authorization headers, tokens, payment secrets, or unnecessary personal/delivery details.
- Validate and constrain all user input; do not trust client-supplied IDs, role names, prices, totals, or status transitions.
- Use parameterized repository queries. Do not concatenate user input into SQL.
- Avoid exposing unnecessary personal information in admin/customer API responses.
- Configure production HTTPS, CORS, database credentials, health checks, backups, and safe migration execution before deployment.
- Do not run destructive database commands, delete user files, force-push, rotate credentials, or alter production resources without explicit authorization.

## 11. Performance and scalability guardrails

Design for clarity and safe evolution, not hypothetical high traffic.

- Add indexes for foreign keys and measured/expected filter/sort patterns.
- Paginate growing collections and avoid loading full tables into memory.
- Keep controller responses and database queries focused.
- Keep application stateless where practical so additional instances can be deployed later.
- Use connection pooling defaults sensibly and make configuration external.
- Add caching, queues, asynchronous processing, service extraction, read replicas, or orchestration only when measured requirements justify them. Document the bottleneck and trade-off before adding such infrastructure.
- Prefer backward-compatible changes and explicit API/database evolution.

## 12. Suggested implementation sequence

Follow `plan.md` as the detailed checklist. Implement in this order, completing and verifying each slice before moving on:

1. Inspect repo, initialise Spring Boot/Next.js, configure Docker Compose PostgreSQL, environment examples, health check, and README.
2. Add initial Flyway migrations and domain model for users, restaurants, menu categories/items, orders, and order-item snapshots.
3. Implement authentication and role-based authorization.
4. Implement public restaurant/menu APIs and protected admin menu operations.
5. Build the customer browsing UI, cart, and scoped FAQ chat assistant; route chat through the backend's OpenRouter integration.
6. Implement server-validated transactional checkout and order snapshots.
7. Implement customer order history and admin order status workflow.
8. Finish admin screens and the chosen MVP payment method (prefer cash on delivery or test mode initially).
9. Harden and document the completed slices; add focused tests alongside each feature, then run broader security and regression checks.
10. Prepare deployment, CI checks, health monitoring, backups, and an end-to-end smoke test after local MVP acceptance. Deployment is optional for MVP completion unless explicitly required.

Each step should include a clear acceptance check and a focused commit when Git is available. Do not start deployment or add optional infrastructure while core order placement is broken.

## 13. Definition of done for a change

A task is done only when all applicable items are true:

- [ ] The implementation solves the requested problem and stays within MVP scope.
- [ ] AI chat, when in scope, is server-side, grounded in current public data, labelled to users, and does not expose secrets or private data.
- [ ] Existing conventions and architecture are respected.
- [ ] Input validation, error handling, authorization, and edge cases are addressed.
- [ ] Relevant automated tests are added or updated and pass.
- [ ] API types/docs and frontend usage remain consistent where applicable.
- [ ] Configuration and schema changes are documented and use environment variables/migrations.
- [ ] No secrets, debug code, fake success paths, or unrelated refactors are introduced.
- [ ] README or relevant docs are updated when setup or behavior changes.
- [ ] Deployment is not treated as a prerequisite for the locally runnable MVP unless the task explicitly requires a hosted release.
- [ ] The final response states what changed, verification performed, and remaining limitations.

## 14. Communication style for coding agents

Be practical and transparent. Prefer a working, end-to-end slice over broad scaffolding. Explain important design choices briefly, especially security, transaction, schema, and dependency decisions. Do not claim completion when only files were generated or when tests were not run. If blocked, describe the blocker and the smallest decision or action needed to proceed.
