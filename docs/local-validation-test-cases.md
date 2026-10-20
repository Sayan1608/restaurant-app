# Local validation test cases

Run date: 2026-10-20 (local development environment)

## Results

| ID | Test case | Expected result | Result |
|---|---|---|---|
| TC-01 | Restart PostgreSQL through Docker Compose | Container returns to healthy state; existing schema remains available | PASS — healthy after restart; Flyway reports schema version 1 up to date |
| TC-02 | Run backend automated tests | All backend tests pass | PASS — `./mvnw test`: 12 tests, 0 failures/errors/skips |
| TC-03 | Check backend health endpoint | `GET /actuator/health` returns 200 and `UP` | PASS |
| TC-04 | Check OpenAPI endpoint | `GET /v3/api-docs` returns 200 | PASS |
| TC-05 | Check frontend server | `GET http://localhost:3000` returns 200 | PASS |
| TC-06 | Fetch CSRF token | `GET /api/auth/csrf` returns a token and header name and establishes a session | PASS |
| TC-07 | Register a customer while attempting to submit ADMIN role | Registration returns 201; server assigns CUSTOMER; password hash is not returned | PASS |
| TC-08 | Register the same email twice | Duplicate registration returns 409 | PASS |
| TC-09 | Submit invalid registration fields | Registration returns 400 | PASS |
| TC-10 | Submit registration without CSRF token | Request returns 403 | PASS |
| TC-11 | Log in with an incorrect password | Request returns 401 | PASS |
| TC-12 | Log in with the registered customer | Login succeeds and rotates the session ID | PASS |
| TC-13 | Fetch current user after login | `/api/auth/me` returns the signed-in customer | PASS |
| TC-14 | Request admin orders as a customer | Request returns 403 | PASS |
| TC-15 | Log out with a CSRF token, then fetch current user | Logout returns 204; current-user request returns 401 | PASS |
| TC-16 | Send CORS preflight from configured frontend origin | Returns 200 with the matching allowed origin and credentials enabled | PASS |
| TC-17 | Send CORS preflight from an untrusted origin | Request is rejected with 403 and no allow-origin header | PASS |
| TC-18 | Check the stored password representation | Newly registered account uses a BCrypt-style `$2...` hash | PASS |

The temporary customer accounts used by the live checks were removed after confirming neither had orders. No existing user or order data was changed.

## Notes

- The frontend was smoke-tested for HTTP availability only; no browser-driven UI interaction suite is configured/run here.
- This validation covers the currently implemented authentication slice and service availability. Restaurant/menu browsing and order placement endpoints are not implemented yet.
- The Maven run emitted a warning that optional Flyway Maven metadata could not be fetched from GitHub Packages (HTTP 401); the build and all tests still succeeded.
