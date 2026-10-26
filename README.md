# Restaurant Food Ordering MVP

A single-restaurant food ordering MVP built as a Spring Boot API, a Next.js web app, and PostgreSQL. The current customer slice includes live menu browsing, a persistent cart, session-based registration/login, checkout with server-calculated totals, and a server-side AI menu and ordering FAQ assistant.

## Requirements

- Java 21
- Node.js 20.9 or newer and npm
- Docker with Docker Compose

## Local setup

1. Start PostgreSQL from the repository root:

   ```powershell
   docker compose up -d postgres
   docker compose ps
   ```

   Compose uses local-development defaults and publishes PostgreSQL on port `5433` to avoid common local port conflicts. Set `POSTGRES_PASSWORD` in the root `.env` (see `.env.example`). The database persists in the named `restaurant_postgres_data` volume.

2. Start the backend in a PowerShell terminal:

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

   The Maven Wrapper downloads its Maven distribution on first use. The backend reads the root `.env` server-side plus process environment variables. Set `DATABASE_PASSWORD` to the same local value as `POSTGRES_PASSWORD`; the URL, database name, and username defaults match Compose.

3. Start the frontend in another terminal:

   ```powershell
   cd frontend
   Copy-Item .env.example .env.local
   npm run dev
   ```

   Open <http://localhost:3000>. The frontend API URL is configurable with `NEXT_PUBLIC_API_BASE_URL`.

4. Check backend and database health at <http://localhost:8080/actuator/health>. Swagger UI is available at <http://localhost:8080/swagger-ui.html>.

The repository may already have a root `.env` containing the OpenRouter key. Do not overwrite it when copying examples; add only the missing database settings. Spring Boot imports this file on the server, and Compose uses it for variable substitution. `.env.example` files contain placeholders, not credentials.

## Development sample data

The initial Flyway migration creates the schema without inserting application data. To load a small sample restaurant and menu locally, set `SPRING_PROFILES_ACTIVE=development` in the backend terminal before starting Spring Boot. The profile is opt-in and creates no accounts or passwords. The seed is safe to run again; it skips the sample restaurant if it already exists. Do not activate this profile in production.

## Environment variables

| Variable | Purpose |
|---|---|
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_PORT` | Local Compose database configuration |
| `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | Backend datasource when run outside Docker |
| `NEXT_PUBLIC_API_BASE_URL` | Browser-visible backend base URL |
| `APP_CORS_ALLOWED_ORIGIN` | The single permitted browser origin for credentialed API requests (defaults to `http://localhost:3000`) |
| `APP_SESSION_COOKIE_SECURE` | Set to `true` behind HTTPS; keep `false` for local HTTP |
| `OPENROUTER_API_KEY`, `OPENROUTER_MODEL` | Server-side assistant provider credentials/model; never expose the key through `NEXT_PUBLIC_*` or frontend code |
| `OPENROUTER_BASE_URL` | Optional server-side OpenRouter-compatible API base URL; defaults to `https://openrouter.ai/api/v1` |

Use a strong unique password outside local development. The OpenRouter key is read only by the backend and is never sent to the browser.

## Current baseline

- Spring Boot 3.x API scaffold with PostgreSQL, Flyway, Spring Security, Actuator, validation, and OpenAPI dependencies.
- Next.js App Router scaffold with TypeScript, Tailwind CSS, and npm.
- PostgreSQL Compose service with a named data volume and health check.
- Customer registration, login, logout, current-user endpoint, server-side BCrypt password hashing, and role-based route protection.
- Public active restaurant and menu browsing, plus admin-only category and menu-item management APIs.
- Browser authentication uses an HttpOnly session cookie with CSRF protection; API error responses use a consistent safe JSON shape.
- Initial Flyway schema and JPA model for users, restaurants, categories, menu items, orders, and immutable order-item price/name snapshots.
- Responsive customer storefront with live menu loading, category/item cards, cart quantity controls, local cart persistence, and accessible loading/empty/error states.
- Customer registration and login through the existing HttpOnly session and CSRF flow.
- Public `/api/chat/messages` FAQ endpoint grounded in the selected restaurant's current public menu, with input/output bounds, provider timeout, per-client request limiting, and a safe unavailable response.
- Customer checkout at `POST /api/orders`: current server-side availability and price validation, transactional order/item snapshots, and customer-scoped idempotent retries. Tax and delivery fees are currently zero.

The cart subtotal is a convenience estimate; checkout calculates the final total from current database prices. Payment processing and order history/status management are later project steps. The assistant can answer menu and general ordering FAQ questions only; it cannot access customer/order data or place and change orders. Set `OPENROUTER_API_KEY` in the backend environment to enable it.

See [plan.md](plan.md) for the incremental build sequence and [AGENTS.md](AGENTS.md) for repository implementation standards.
See [docs/data-model.md](docs/data-model.md) for the schema diagram and modeling decisions.
See [docs/authentication.md](docs/authentication.md) for the auth endpoints, cookie/CSRF behavior, and browser request flow.
See [docs/restaurant-menu-api.md](docs/restaurant-menu-api.md) for restaurant browsing and admin menu management endpoints.
See [docs/chat-api.md](docs/chat-api.md) for the FAQ assistant contract and limits.
See [docs/checkout-api.md](docs/checkout-api.md) for checkout validation, pricing, snapshots, and idempotency.
