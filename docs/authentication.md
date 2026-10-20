# Authentication and browser sessions

The API uses server-side HTTP sessions. A successful login returns a `JSESSIONID` cookie configured as HttpOnly and SameSite=Lax; browser code should not read or store session identifiers. The session expires after 30 minutes of inactivity. Set `APP_SESSION_COOKIE_SECURE=true` when running behind HTTPS. Local HTTP uses the default `false`.

Cookie-based authentication requires CSRF protection. Call `GET /api/auth/csrf` to get a token from the server-side session. For each unsafe request (`POST`, `PUT`, `PATCH`, or `DELETE`), send that token in the `X-CSRF-TOKEN` header and include credentials. Spring Security rotates the session ID after authentication and clears the previous CSRF token on login/logout, so fetch a fresh CSRF token after either operation.

The frontend origin is restricted to `APP_CORS_ALLOWED_ORIGIN` (default `http://localhost:3000`) and credentialed CORS is enabled for that exact origin. Configure the deployed origin explicitly.

## Endpoints

| Method | Route | Access | Result |
|---|---|---|---|
| `GET` | `/api/auth/csrf` | Public | Sets an `XSRF-TOKEN` cookie and returns the header name |
| `POST` | `/api/auth/register` | Public + CSRF token | Creates a `CUSTOMER` account; clients cannot choose a role |
| `POST` | `/api/auth/login` | Public + CSRF token | Authenticates and starts a session |
| `GET` | `/api/auth/me` | Authenticated | Returns the current user without password data |
| `POST` | `/api/auth/logout` | Authenticated + CSRF token | Invalidates the session |

Registration accepts `name`, `email`, and `password`. Passwords must contain at least 12 characters and at most 72 UTF-8 bytes. Emails are trimmed and normalized to lowercase. Login failures use the same message regardless of whether an account exists. Registration conflicts return a generic message.

Only health and API documentation, the CSRF endpoint, and registration/login are public. `/api/admin/**` requires `ADMIN`; `/api/customer/**` requires `CUSTOMER`. Other application routes remain denied until a later step adds their access rules. No default or publicly seeded admin account is created.

## Browser request flow

Use `credentials: "include"` for API requests. First call `GET /api/auth/csrf`, then send the returned `token` as the `X-CSRF-TOKEN` header with registration, login, and logout requests. After login or logout, call the CSRF endpoint again before the next unsafe request.

The frontend auth client will implement this flow when the customer UI is added. Do not send the session ID or OpenRouter key in application JSON or local storage.
