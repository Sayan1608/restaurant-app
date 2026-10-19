# Restaurant Food Ordering MVP

A single-restaurant food ordering MVP built as a Spring Boot API, a Next.js web app, and PostgreSQL. The AI menu and ordering FAQ assistant is planned for a later slice; its server-only environment variables are listed for local setup, but chat is not implemented yet.

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
| `OPENROUTER_API_KEY`, `OPENROUTER_MODEL` | Reserved for the planned server-side chatbot integration; never expose the key through `NEXT_PUBLIC_*` or frontend code |

Use a strong unique password outside local development. The OpenRouter key is read only by the backend and is never sent to the browser.

## Current baseline

- Spring Boot 3.x API scaffold with PostgreSQL, Flyway, Spring Security, Actuator, validation, and OpenAPI dependencies.
- Next.js App Router scaffold with TypeScript, Tailwind CSS, and npm.
- PostgreSQL Compose service with a named data volume and health check.
- Only the health and OpenAPI routes are publicly accessible in the backend scaffold. Business APIs and authentication are added in later plan steps.
- Initial Flyway schema and JPA model for users, restaurants, categories, menu items, orders, and immutable order-item price/name snapshots.

See [plan.md](plan.md) for the incremental build sequence and [AGENTS.md](AGENTS.md) for repository implementation standards.
See [docs/data-model.md](docs/data-model.md) for the schema diagram and modeling decisions.
