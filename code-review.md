# Code review — restaurant_app

Date: 2026-10-30 (findings) · 2026-10-31 (high-severity fixes applied) · Reviewer: Claude Opus 5
Scope: entire repository at `6f84111` plus uncommitted `plan.md` / `CLAUDE.md`

> **Status:** findings 1–4 (all High) are **fixed and verified** — see the
> [Resolution log](#resolution-log) at the end. Findings 5–36 remain open.

## How this review was done

Every Java, TypeScript, SQL and configuration file under `backend/src`, `frontend/src`, `docs/` and the repo
root was read in full (~3,000 LOC backend main, ~1,200 LOC backend test, ~1,050 LOC frontend). Findings were
then verified by running the actual toolchain rather than by inspection alone.

**Baseline verification (all run during this review):**

| Check | Command | Result |
|---|---|---|
| Backend tests | `./mvnw.cmd test` | **46 passed**, 0 failures, 0 errors (**48** after the two regression tests added 10-31) |
| Frontend tests | `npm run test` | **7 passed** (3 files) |
| Typecheck | `npx tsc --noEmit` | clean, exit 0 |
| Lint | `npm run lint` | clean, exit 0 |
| Database | `docker compose ps` | `restaurant-app-postgres` up, healthy, schema at Flyway version 3 |

The codebase is in good shape. The architecture holds to `AGENTS.md`: feature-sliced packages, records as DTOs,
no entity crossing the HTTP boundary, pricing recomputed server-side, invariants mirrored as SQL `CHECK`
constraints, and a deny-by-default security allowlist. Test coverage is genuinely good for an MVP — 46 backend
integration tests covering ownership, CSRF, role separation, idempotency replay, rollback, and OTP lockout.

The findings below are therefore mostly about **robustness under concurrency and failure**, **operability**,
and one **confirmed user-visible rendering bug**. Nothing here suggests the design is wrong.

---

## Severity summary

| # | Severity | Finding | Location |
|---|---|---|---|
| 1 | **High** — **FIXED** | Checkout idempotency race returns 500 instead of replaying | `OrderService.java:60-98` |
| 2 | **High** — **FIXED** | Six characters corrupted to `?` in the live UI | `storefront.tsx:164` |
| 3 | **High** — **FIXED** | Password reset does not invalidate existing sessions | `PasswordResetService.java:99-121` |
| 4 | **High** — **FIXED** | Zero logging in the backend; exceptions silently swallowed | whole of `backend/src/main` |
| 5 | **Medium** | Swagger / OpenAPI publicly exposed on every profile | `SecurityConfiguration.java:108` |
| 6 | **Medium** | `requestReset` is not transactional and races to a 500 | `PasswordResetService.java:64-95` |
| 7 | **Medium** | Account enumeration via forgot-password response timing | `PasswordResetService.java:64-95` |
| 8 | **Medium** | Chat rate limiter keyed on `getRemoteAddr()` | `ChatController.java:25` |
| 9 | **Medium** | App-level case-insensitive uniqueness vs case-sensitive DB constraint | `AdminMenuService.java:31,47` |
| 10 | **Medium** | `fieldErrors` and error `code` discarded by the frontend client | `api.ts:74-75` |
| 11 | **Medium** | `password_reset_otps` grows unbounded; no cleanup | `V3`, `PasswordResetOtp.java` |
| 12 | **Medium** | No catch-all handler, so 500s break the `ApiError` contract | `ApiExceptionHandler.java` |
| 13 | **Medium** | Money handled as JS `number` on the frontend | `api.ts:11-62` |
| 14 | **Medium** | Currency formatted inconsistently between screens | `storefront.tsx:11` vs others |
| 15 | **Medium** | No timeout on any frontend request | `api.ts:71-79` |
| 16 | **Medium** | `imageUrl` accepts any string, no scheme allowlist | `CreateMenuItemRequest.java` |
| 17 | **Medium** | In-memory servlet sessions, contrary to `CLAUDE.md` | `application.properties`, `pom.xml` |
| 18 | **Medium** | Cart rehydrated from `localStorage` without validation | `storefront.tsx:57` |
| 19 | **Medium** | Cart quantity unbounded client-side vs `@Max(99)` server-side | `storefront.tsx:71` |
| 20 | **Medium** | `storefront.tsx` has no tests | `frontend/src/app/` |
| 21 | **Low** | Tailwind shipped but entirely unused | `globals.css:1`, `package.json` |
| 22–35 | **Low** | Cleanups — see [Low severity](#low-severity) | various |
| 36 | **Medium** | Test suite permanently pollutes the development database | `OrderCheckoutControllerTests:148` |

---

## High severity

*All four are fixed. Each section keeps its original analysis; the fix is recorded in the [Resolution log](#resolution-log).*

### 1. Checkout idempotency has a race that returns 500 instead of replaying

`OrderService.create` reads for a prior order by `(customer, idempotencyKey)` at line 64, and if absent inserts
a new one at line 98. Two concurrent requests carrying the same `Idempotency-Key` both miss the read, both
insert, and the second violates the `uq_orders_customer_idempotency_key` unique index added in `V2`. There is
no `DataIntegrityViolationException` handling anywhere in the `orders` package.

**Failure scenario.** A customer double-clicks "Place order", or the browser retries on a flaky connection. Two
requests arrive in parallel with the same key. One order is created; the other request fails with an unhandled
`DataIntegrityViolationException` surfacing as a 500 whose body is *not* the documented `ApiError` shape
(see finding 12). The customer sees "We couldn't place your order", and `checkout-form.tsx` retains the
idempotency key so a manual retry then correctly replays — but the first impression is a hard failure of the
exact guarantee idempotency exists to provide.

This is notable because the codebase already establishes the right pattern twice elsewhere —
`UserAccountService.java:43` and `AdminMenuService.java:38,56` both catch `DataIntegrityViolationException`
and convert it to a domain response. `OrderService` is the one place that omits it.

**Recommendation.** Wrap the insert in a `try/catch (DataIntegrityViolationException)`; on catch, re-read by
`(customer, idempotencyKey)` and return the existing order as a replay, applying the same fingerprint
comparison used at line 66. Because the method is `@Transactional`, the retry needs a separate transaction
(`REQUIRES_NEW` on a small helper, or catching in the controller) — the existing transaction is marked
rollback-only once the constraint fires. A test is hard to write under the suite's `@Transactional` model;
assert the recovery path with a `@MockitoSpyBean` repository that throws on the first `save`.

### 2. Six characters are corrupted to literal `?` in the shipped UI

`frontend/src/app/storefront.tsx:164` contains six ASCII `0x3F` bytes where non-ASCII glyphs were intended.
This was confirmed at byte level, not read off a terminal:

```
storefront.tsx    "fee are ?0 for now"       ->  f e e   a r e   ? 0         (0x3F)
checkout-form.tsx "tax are ₹0 for this MVP"  ->  t a x   a r e  342 202 271  (U+20B9 ₹)
```

The same file holds correct UTF-8 elsewhere (`303 227` = `×` in the auth modal), so this is localized damage to
one line, not a file-wide encoding problem. No other frontend file is affected — I checked all of them.

**What the user sees**, all inside the cart drawer:

| Intended | Renders as | Element |
|---|---|---|
| `₹` | `?` | "Tax and delivery fee are **?**0 for now." |
| `×` | `?` | cart drawer close button |
| `−` | `?` | the decrement button, `aria-label="Remove one …"` |
| `✓`/`✳` | `?` | order confirmation mark |
| `→` | `?` | "Continue to checkout **?**" |
| `✳` | `?` | empty-bag glyph |

The decrement button is the worst of these: the control that removes an item from the cart is labelled `?`.
Note this is invisible to the whole toolchain — `tsc`, `eslint` and the tests all pass, because `?` is a
perfectly valid string.

**Recommendation.** Restore the six characters. Since the enclosing line is 2,771 characters long (finding 28),
edit it with care. Worth adding a CI grep for `>?<` and `are ?0` to stop a recurrence, as no existing check
catches it.

### 3. A password reset leaves existing sessions signed in

`PasswordResetService.resetPassword` changes `users.password_hash` and deletes the OTP challenge. It does
nothing to existing sessions, and I confirmed there is no `invalidate`, `SessionRegistry`, `maximumSessions`
or equivalent anywhere in `backend/src/main`.

**Failure scenario.** An attacker signs in with a stolen password. The owner notices, uses "Forgot password"
and sets a new one. The attacker's session cookie keeps working until it idles out
(`server.servlet.session.timeout=30m`, refreshed by any request) — so an attacker who keeps the session warm
retains access indefinitely. Account recovery is precisely the moment a user believes they have revoked access,
so the gap between belief and reality is the whole risk.

This compounds with finding 17: because the `SecurityContext` is serialized into the session with a snapshot
`AuthenticatedUser`, nothing re-reads the user row per request, so the stale session cannot notice the change.

**Recommendation.** Register sessions (`HttpSessionEventPublisher` + `SessionRegistry`, or Spring Session with
`findByPrincipalName`) and expire all of a user's sessions on successful reset. Adding Spring Session JDBC
(finding 17) makes this straightforward and is the natural pairing. `docs/password-recovery.md` should state
the chosen behaviour either way.

### 4. The backend has no logging at all, and swallows exceptions silently

`grep -rn "Logger|LoggerFactory|log\."` over `backend/src/main/java` returns **nothing**. There is no logger in
any of the 60 main-source classes. Two places actively discard a diagnosis:

- `ChatService.java:64` — `catch (RestClientException exception) { throw unavailable(); }`. The provider's
  cause is dropped.
- `PasswordResetService.java:89` — `catch (RuntimeException exception) { otps.delete(challenge); return
  GENERIC_MESSAGE; }`. Every SMTP failure is discarded.

**Failure scenario.** Users report that reset emails never arrive. The API returns 200 with the generic message
whether SMTP succeeded, failed to authenticate, timed out, or was never configured — and the exception that
would tell you which is gone. There is no way to distinguish these from the server side, because nothing was
recorded. The same applies to chat: an expired OpenRouter key, a 429, and a network timeout are
indistinguishable, all surfacing as "unavailable right now".

**Recommendation.** Add an SLF4J logger to each service and log at `warn`/`error` with the caught exception as
the cause at both sites, plus the admin-promotion and order-status transitions once Step 8 lands. Never log the
OTP, the password, the API key, or `Idempotency-Key` values. This is the single highest-value operability change
in the review and is a precondition for diagnosing anything else here in production.

---

## Medium severity

### 5. Swagger and OpenAPI are publicly exposed on every profile

`SecurityConfiguration.java:108` `permitAll()`s `/v3/api-docs/**`, `/swagger-ui/**` and `/swagger-ui.html` with
no profile condition. The application warns about this itself on every boot — visible in the test run output:

```
WARN  SpringDoc /v3/api-docs endpoint is enabled by default. To disable it in production,
      set the property 'springdoc.api-docs.enabled=false'
```

Anonymous users get a complete map of every endpoint, including `/api/admin/**`, with request and response
schemas. It does not grant access, but it removes any need to guess at the attack surface.

**Recommendation.** Set `springdoc.api-docs.enabled=false` and `springdoc.swagger-ui.enabled=false` in the
default properties and re-enable them in a `development` profile block, or move the matcher behind
`hasRole("ADMIN")`.

### 6. `requestReset` is not transactional and races to a 500

`PasswordResetService.requestReset` (line 64) carries no `@Transactional`, and the class has none, so
`otps.save` (79) and `otps.delete` (93) each commit in their own transaction.

Two problems follow. First, two concurrent requests for the same *new* email both see `findByEmail` empty, both
construct a `PasswordResetOtp`, and the second violates the `UNIQUE` constraint on `password_reset_otps.email`
from `V3` — an unhandled 500. That 500 is itself an **enumeration oracle**: a registered email can be made to
return 500, while an unregistered one always returns the generic 200 at line 66, defeating the purpose of
`GENERIC_MESSAGE`. Second, if the process dies between the commit at 79 and the compensating delete at 93, a
challenge survives whose code was never delivered, and the 60-second cooldown at line 70 then blocks the user
from requesting a working one.

**Recommendation.** Make the method `@Transactional` so the save and the compensating delete are atomic, and
catch `DataIntegrityViolationException` around the save, re-reading and treating it as a concurrent request
(return the generic message). Prefer an upsert keyed on email.

### 7. Forgot-password leaks account existence through response timing

Still in `requestReset`: an unregistered email returns at line 66 after one indexed `SELECT`. A registered one
runs `passwordEncoder.encode(code)` — BCrypt at **strength 12**, deliberately ~200–400 ms — and then a
synchronous SMTP send. The difference is hundreds of milliseconds to seconds, trivially measurable, and far
outside normal network jitter.

The generic message and the 60-second cooldown show enumeration resistance was an explicit goal here
(`docs/password-recovery.md` and `plan.md` §9 both say so), so the timing channel undercuts a stated property.

**Recommendation.** Do the work off the request thread — hand the hash-and-send to a `TaskExecutor` and return
immediately in both branches. That fixes the timing channel and the SMTP-latency problem together. A constant
floor on response time is a weaker alternative.

### 8. The chat rate limiter keys on `getRemoteAddr()`

`ChatController.java:25` uses `httpRequest.getRemoteAddr()` as the limiter key, and no
`X-Forwarded-For`/`Forwarded` handling or `ForwardedHeaderFilter` is configured.

**Failure scenario.** Behind any reverse proxy, load balancer or CDN — i.e. any realistic deployment — every
client presents the proxy's address. All users collapse into a single 20-requests-per-minute bucket, so one
enthusiastic user denies the assistant to everyone, and a deliberate attacker does so with a trivial loop.
Conversely, with no proxy, an attacker rotating source addresses bypasses the limit entirely.

That second direction costs real money: `/api/chat/messages` is `permitAll()` and every accepted call spends
OpenRouter tokens, with `max_tokens: 500` per reply. An unauthenticated, per-IP-limited, paid endpoint is an
open invitation to bill-running.

**Recommendation.** Configure `ForwardedHeaderFilter` and resolve the client from a trusted-proxy-aware
`X-Forwarded-For`. Add a global ceiling alongside the per-client one so total spend is bounded regardless of
key distribution, and consider requiring authentication, or a cheap proof-of-work/captcha, for anonymous use.
`docs/chat-api.md` should record whichever limit is authoritative.

### 9. App-level case-insensitive uniqueness contradicts the database constraint

`AdminMenuService` checks duplicates with `existsByRestaurant_IdAndNameIgnoreCase` (lines 31 and 47), but the
backing constraint from `V1` is `CONSTRAINT uq_menu_categories_restaurant_name UNIQUE (restaurant_id, name)` —
byte-exact and case-**sensitive** in PostgreSQL.

The two disagree in both directions. The `catch (DataIntegrityViolationException)` at lines 38 and 56 is dead
for case variants: under concurrency, "Mains" and "mains" both insert successfully, leaving data the
application considers duplicate and will refuse to let an admin rename. Sequentially the app rejects "mains"
as a duplicate of "Mains" even though the database would accept it, so the effective rule depends on timing.

`restaurants.name` has the same shape (`existsByNameIgnoreCase` in `RestaurantRepository` against
`UNIQUE (name)`), currently reachable only from `DevelopmentDataSeeder`. For contrast, `users.email` gets this
right: `UserAccount` lowercases on construction and `V1` adds `CHECK (email = LOWER(email))`, making the unique
index genuinely case-insensitive.

**Recommendation.** Pick one rule and enforce it in the database. Either add a functional unique index
(`CREATE UNIQUE INDEX ... ON menu_categories (restaurant_id, LOWER(name))`) in a new `V4`, matching the app's
intent, or drop `IgnoreCase` from the queries. The migration must come with the code change — per `CLAUDE.md`,
`ddl-auto=validate` means they cannot drift.

### 10. The frontend discards `fieldErrors` and the error `code`

`api.ts:74-75` parses the error body and then throws `new Error(error?.message ?? …)`, keeping only the
message. Both other fields are lost.

The backend goes to real trouble to produce them. `ApiExceptionHandler.handleInvalidRequest` builds a
per-field map, `ApiException` carries one, and `RegisterRequest` has hand-written per-field messages. None of
it can ever reach the user: a registration rejected for a short password shows the generic "Check the supplied
fields." with no indication of which field. The `ApiError` type at `api.ts:29` declares `fieldErrors?` and
nothing ever reads it.

The lost `code` matters just as much, because the UI cannot branch on outcomes it should handle distinctly —
`MENU_ITEM_UNAVAILABLE` (refresh the menu), `IDEMPOTENCY_KEY_REUSED`, `INVALID_ORDER_STATUS_TRANSITION`
(reload the desk), `CHAT_RATE_LIMITED` (back off) all arrive as an opaque string. `order-center.tsx` and
`checkout-form.tsx` consequently render every failure into one `form-error` paragraph.

**Recommendation.** Define `class ApiRequestError extends Error` carrying `code` and `fieldErrors`, throw that
from `request()`, and surface field errors next to their inputs. This is a prerequisite for the form validation
the backend already supports.

### 11. `password_reset_otps` grows without bound

Nothing deletes expired challenges. Rows are removed only on successful reset (`PasswordResetService:120`), on
an expired-but-attempted lookup (105), or on mail failure (93). An OTP that is requested and simply abandoned —
the common case for a user who then remembers their password — persists forever. No scheduled cleanup exists
anywhere, and `docs/password-recovery.md` does not mention one.

Two pieces of related dead code point at an intention that was never finished: `PasswordResetOtp.expireAt(…)`
is never called from anywhere, and `PasswordResetOtpRepository.deleteByEmail` is never used (and as written
would need `@Modifying` plus a transaction to work).

A further wrinkle: once `failedAttempts` reaches 5, line 107 throws *before* the expiry delete at 105 can ever
run, so a locked-out challenge is never cleaned up by any path.

**Recommendation.** Add a `@Scheduled` sweep deleting `expires_at < now()` (hourly is ample), with an index on
`expires_at` in the same migration. Delete `expireAt` and `deleteByEmail`, or use them.

### 12. No catch-all handler, so unexpected failures break the documented error shape

`ApiExceptionHandler` handles exactly four types: `ApiException`, `MethodArgumentNotValidException`,
`HttpMessageNotReadableException` and `AuthenticationException`. Anything else — including the
`DataIntegrityViolationException` of findings 1 and 6 — falls through to Spring Boot's default error handling
and returns `{"timestamp","status","error","path"}` instead of the `{"code","message","fieldErrors"}` contract
that `AGENTS.md` mandates and `api.ts` expects.

`request()` at `api.ts:74` reads `error?.message`, which is `undefined` for that shape, so the user gets
"Something went wrong. Please try again." — losing even the status.

**Recommendation.** Add `@ExceptionHandler(Exception.class)` returning a 500 `ApiError` with a fixed
`INTERNAL_ERROR` code and a non-revealing message, logging the exception (finding 4). Keep
`server.error.include-message` and `include-stacktrace` at their safe defaults.

### 13. Money crosses into the frontend as a floating-point `number`

`api.ts` types every monetary field as `number` (`price`, `subtotal`, `deliveryFee`, `tax`, `total`,
`unitPrice`, `lineTotal`), so server-side `BigDecimal` scale-2 values become IEEE-754 doubles on parse. Client
arithmetic then compounds them: `storefront.tsx:68` sums `line.item.price * line.quantity` across the cart and
`checkout-form.tsx:33` does the same.

`AGENTS.md` and `CLAUDE.md` are explicit that money is `BigDecimal` scale 2 server-side and that the cart total
is only a display estimate with the backend authoritative — so the blast radius is limited to display, and
with INR at two decimals and realistic basket sizes, visible error is unlikely. It is still the wrong
representation for currency, and the risk grows the moment the deliberately-zero `tax`/`deliveryFee` become
percentages (Step 8).

**Recommendation.** For now, keep the estimate but compute it in integer paise (multiply on parse, divide only
to format) so client arithmetic is exact. If fees ever become non-zero, carry money as strings over the wire
and format from the string.

### 14. The same price is formatted differently on different screens

Three `money` helpers disagree:

| File | `maximumFractionDigits` | A ₹249.50 item renders as |
|---|---|---|
| `storefront.tsx:11` | `0` | ₹250 |
| `checkout-form.tsx:22` | `2` | ₹249.50 |
| `order-center.tsx:24` | `2` | ₹249.50 |

The menu and cart round to whole rupees; checkout and order history show paise. A customer adding a ₹249.50
item sees "₹250" in the bag, then "₹249.50" one screen later. For a multi-item cart the rounded subtotal
estimate can differ from the checkout figure by several rupees, which reads as a pricing error at exactly the
moment trust matters. `MenuItem.price` permits two decimals (`scale() > 2` is the only rejection), so this is
reachable with ordinary data, not a contrived edge case.

**Recommendation.** Move one `money` (and `dateTime`) into `src/lib/format.ts` and import it everywhere —
this also resolves finding 25. Use two fraction digits consistently.

### 15. No request has a timeout

`request()` at `api.ts:71` calls `fetch` with no `AbortSignal`. Browsers will wait out their own default,
typically minutes.

The chat path makes this concrete: `ChatConfiguration` gives the server a 15-second read timeout to OpenRouter,
but if the backend itself stalls, `storefront.tsx` leaves `chatBusy` true and the user watches "Let me have a
little look…" indefinitely with no way to cancel. The same applies to `submitting` in `checkout-form.tsx`,
where the submit button stays disabled.

**Recommendation.** Add `AbortSignal.timeout(…)` in `request()` — roughly 10 s for reads, 30 s for the chat
call which is legitimately slow — and map `AbortError` to a retryable message.

### 16. `imageUrl` accepts any string with no scheme restriction

`CreateMenuItemRequest` and `UpdateMenuItemRequest` constrain `imageUrl` with `@Size(max = 2048)` only;
`AdminMenuService.normalizeImageUrl` just trims. Any string is persisted, including `javascript:…`,
`data:text/html,…` or an attacker-controlled host.

The value is admin-supplied, which lowers severity, and no component currently renders it (the storefront draws
CSS illustrations instead) — so this is latent rather than live. It becomes exploitable the moment a template
does `<img src={item.imageUrl}>`: a `data:` URI is a stored-content vector, and an external URL silently leaks
viewer IPs to a third party. Since the admin menu UI is the next thing to be built (Step 8), the field is about
to be used.

**Recommendation.** Validate with `@Pattern` or a custom validator restricting to absolute `http`/`https`
URLs, and reject everything else at the boundary. Record the rule in `docs/restaurant-menu-api.md`.

### 17. Sessions are in-memory Tomcat sessions, not Spring Session

`CLAUDE.md:46` states "Browser auth is a Spring Session cookie". There is **no** `spring-session-*` dependency
in `backend/pom.xml`, and `application.properties` configures plain `server.servlet.session.cookie.*`. Auth is
therefore a standard in-memory `JSESSIONID`.

Consequences: every user is signed out on each restart or redeploy; the app cannot be scaled to more than one
instance without sticky sessions; and sessions cannot be enumerated or revoked, which is what blocks finding 3.

A related detail: `AuthenticatedUser` holds `passwordHash` and is `Serializable`, but does not implement
`CredentialsContainer`, so `ProviderManager`'s default `eraseCredentials` cannot clear it. The BCrypt hash is
retained in the authenticated principal for the session's lifetime — only in heap today, but it would be
written to the store the moment session persistence is added.

**Recommendation.** Either add `spring-session-jdbc` (which also enables finding 3, at the cost of a migration
for `SPRING_SESSION`) or correct `CLAUDE.md` to say "servlet session" and note the restart/scaling limits.
Independently, implement `CredentialsContainer` on `AuthenticatedUser` to null the hash on erase. Since
`CLAUDE.md` is new and still uncommitted, fixing the sentence now costs nothing.

### 18. The cart is rehydrated from `localStorage` with no validation

`storefront.tsx:57` does `setCart(JSON.parse(raw) as CartLine[])`. The `as` is an unchecked assertion: the
parsed value is never verified to be an array, nor its entries to have `item.price`, `item.id` or a positive
integer `quantity`.

**Failure scenario.** A stale entry written by an earlier shape of the app, hand-edited storage, or a partial
write leaves `price` undefined. `money(line.item.price * line.quantity)` then renders "₹NaN" in the bag and the
subtotal, and the user cannot clear it from the UI — the empty-cart reset is unreachable while `cart.length` is
truthy. A negative `quantity` sails past the decrement filter at line 71 and reaches checkout, where the
backend correctly rejects it with a generic message the user cannot act on.

The `try/catch` around the read only covers a `JSON.parse` throw, not a structurally valid but wrong payload.
Note the key is already versioned (`restaurant-cart-v1`), so the intent to guard against shape drift exists.

**Recommendation.** Validate after parsing — a small type guard per line, dropping anything malformed — and
clear the key when validation fails. Reconcile against the freshly loaded menu (the flatMap at lines 48–52
already does this well) and take `price` from the server copy rather than from storage.

### 19. Cart quantity is unbounded client-side but capped at 99 server-side

`OrderLineRequest` enforces `@Min(1) @Max(99)`. `changeQuantity` at `storefront.tsx:71` applies no ceiling, and
`add` increments without one. A customer can raise a line to 100+, see a plausible subtotal, fill in delivery
details, and only then have the whole order rejected — surfaced, thanks to finding 10, as "Check the supplied
fields." with no mention of which item or what the limit is.

**Recommendation.** Clamp at 99 in `changeQuantity`/`add` and disable the increment button at the ceiling with
a short note. Mirror the constant so the two limits are visibly the same number.

### 20. `storefront.tsx` has no tests

`checkout-form.tsx`, `order-center.tsx` and the password-recovery flow each have a co-located Vitest file
(7 tests, all passing). `storefront.tsx` — the largest and by far the most stateful component, owning menu
load, cart, `localStorage` persistence, the auth modal, the four-way auth mode machine, and chat — has none.

Findings 2, 14, 18 and 19 all live in this file, which is consistent with it being the untested one. `CLAUDE.md`
notes the callback-injection design exists specifically to make components testable; `storefront` is where that
pattern is applied (passing `api.*` down) but never exercised.

**Recommendation.** Add `storefront.test.tsx` covering, at minimum: cart add/increment/decrement and
`localStorage` round-trip; rehydration of a malformed cart (finding 18); the quantity ceiling (19); and the
menu-reconciliation flatMap that drops items no longer on the menu. Stub `api` the way the sibling tests do.

---

## Low severity

### 21. Tailwind is a shipped dependency with zero usage

`globals.css:1` is `@import "tailwindcss";`, and `tailwindcss` + `@tailwindcss/turbopack` are devDependencies.
Across every `.tsx` file, utility classes (`flex`, `px-*`, `text-sm`, `bg-*`, `grid`) appear **zero** times —
all 174 lines of `globals.css` are hand-written, and components use semantic names like `site-shell` and
`food-card`. Every visitor downloads Tailwind's preflight and generated CSS for nothing.

**Recommendation.** Drop the `@import` and both packages, or adopt Tailwind deliberately. Removing it is a
one-line change plus `npm uninstall`.

### 22. The BCrypt 72-byte limit is encoded in three places

`UserAccountService.BCRYPT_MAX_PASSWORD_BYTES = 72` (line 19), `PasswordResetService.BCRYPT_MAX_PASSWORD_BYTES
= 72` (line 30), and a bare literal `72` inline at `AuthController.java:107`, each with its own near-identical
validation and message. `RegisterRequest`/`LoginRequest` separately declare `@Size(max = 72)` — in *characters*,
not bytes, which is why the byte checks exist at all. Extract one shared constant and validator.

### 23. `HttpStatus` is imported twice in `AuthController`

Lines 10 and 22 both `import org.springframework.http.HttpStatus;`. Legal Java, but noise that suggests a merge
was not tidied. The build does not flag it; consider adding a Checkstyle or SpotBugs pass.

### 24. Dead arithmetic in the order total

`OrderService.java:93`: `BigDecimal total = subtotal.add(ZERO_MONEY).add(ZERO_MONEY);`. The two adds are
no-ops standing in for the deliberately-zero delivery fee and tax. Harmless, but it reads as though fees are
being applied. Replace with named `deliveryFee`/`tax` locals (both `ZERO_MONEY`) and add those, so Step 8 has
an obvious seam.

### 25. `money` and `dateTime` are duplicated across components

Three copies of `money` (finding 14) and two of `dateTime` (`order-center.tsx:27`). Consolidate into
`src/lib/format.ts`.

### 26. The frontend re-implements the server's status transition rules

`order-center.tsx:16-23` hard-codes a `transitions` map mirroring `OrderService.isAllowedTransition`, which is
in turn mirrored by `ck_orders_status` in `V1`. Three copies of one invariant. Reasonable for UX today, but it
silently drifts. Consider returning the allowed next states on the order payload.

### 27. `ChatService.endpoint` should be `static final`

`ChatService.java:27` declares `private final String endpoint = "/chat/completions";` — a per-instance copy of
a constant. Make it `private static final`.

### 28. `storefront.tsx` line density defeats review and diffs

`file` reports a longest line of 2,771 characters; line 164 holds the entire cart drawer — overlay, close
button, confirmation panel, the `CheckoutForm` branch, cart lines, summary and empty state — in one expression
with nested ternaries. Finding 2's six corrupted characters sit inside it, which is a fair illustration of the
cost: nothing about that line is reviewable at a glance, and any change to it produces an unreadable diff.
Extract the drawer, the auth modal and the chat panel into sibling components (the pattern already used for
`CheckoutForm` and `OrderCenter`) and let the formatter wrap the result.

### 29. `BackendApplicationTests` omits `@ActiveProfiles("test")`

Every other `@SpringBootTest` sets it; `BackendApplicationTests.java:6` does not, so `contextLoads` runs on the
default profile. Harmless today, but it means the one test that asserts the context boots does so under
different configuration from the rest of the suite. Add the annotation for consistency.

### 30. Compose publishes PostgreSQL on all interfaces

`docker-compose.yml` maps `"${POSTGRES_PORT:-5433}:5432"`, which binds `0.0.0.0` (confirmed:
`0.0.0.0:5433->5432/tcp`), exposing the dev database to the local network. Bind to loopback:
`"127.0.0.1:${POSTGRES_PORT:-5433}:5432"`.

### 31. One `detailLoading` flag disables every "View details" button

`order-center.tsx` uses a single `detailLoading` for the whole list, so opening one order disables all the
buttons and relabels each to "Opening…". Key the pending state by order id.

### 32. UI styling branches on a server message string

`storefront.tsx` picks `form-success` vs `form-error` via `authMessage.startsWith("Your password has")`,
matching `PasswordResetService`'s copy verbatim. Rewording the server message silently restyles the UI as an
error. Return a status flag, or set the style where the call succeeds.

### 33. Raw UUIDs are shown to users as order numbers

`order-center.tsx` renders `#{order.id}` and builds labels like `View details for 7f3a…`, and
`storefront.tsx` shows the full UUID on the confirmation panel. Unreadable, unspeakable over the phone, and
verbose for screen readers. Consider a short human-facing order number (a `V4` sequence) or display a prefix.

### 34. The admin order desk never refreshes on its own

`OrderCenter` loads once per `page`/`statusFilter`/`reload` change. For a desk whose purpose is watching
incoming orders, new orders appear only if the admin interacts. Add a poll (10–30 s) while the admin view is
open, or a manual refresh control.

### 35. Smaller inconsistencies

- **Validation messages.** `RegisterRequest`/`LoginRequest` supply per-field messages;
  `PasswordResetRequest`/`PasswordResetConfirmation` and the whole `menu` package rely on Bean Validation
  defaults, so clients get a mix of curated and framework text.
- **Pagination defaults.** `OrderController.myOrders` defaults to `size=10`, `AdminOrderController` to `20`,
  `api.ts` mirrors both, and `order-center.tsx` passes `10` for both — so the admin default is dead.
- **`setTimeout(…, 0)`.** `order-center.tsx:63` wraps `refresh()` in a zero-delay timer with no comment. It
  reads as a workaround for an effect-ordering or test `act` warning; either document why or remove it.
- **`this` vs `api`.** `api.updateOrderStatus` calls `api.csrf()` while every sibling uses `this.csrf()`.
- **A CSRF round-trip per mutation.** Every mutating call fetches `/api/auth/csrf` first, doubling request
  count. With `HttpSessionCsrfTokenRepository` the token is stable for the session, so cache it and re-fetch
  only on 403.
- **Chat has no memory.** The UI presents a conversation, but `api.ask` sends only the latest message and
  `ChatService` builds a fresh two-message prompt, so follow-ups like "is that one spicy?" cannot work. Either
  send recent turns or set the expectation in the UI copy.
- **`crypto.randomUUID()`** (`checkout-form.tsx:46`) requires a secure context. It works on `localhost` and
  HTTPS but throws on plain HTTP over a LAN address — a staging-deployment trap. Guard with a fallback.
- **No phone-number validation** on either side beyond `@Size(max = 32)`.
- **Arithmetic overflow at the extreme.** 50 lines × 99 × a near-maximum `NUMERIC(12,2)` price can exceed the
  column, failing at insert as a 500 rather than a validation error. Unreachable with realistic prices.
- **`OrderSummary`** is a bare alias of `OrderResponse` (`api.ts:51`).

### 36. The test suite permanently pollutes the development database

Found on 2026-10-31 while bringing the stack up, not during the original read-through.

`OrderCheckoutControllerTests` is `@Transactional` at class level, so each test rolls back — except
`rollsBackTheOrderWhenSavingItsItemsFails` (line 148), which is annotated
`@Transactional(propagation = Propagation.NOT_SUPPORTED)` so it can observe a real rollback. That
opt-out also applies to `@BeforeEach setUp()`, so the restaurant, category, menu item and user it
creates are **committed** and never removed.

**Observed, not inferred.** The database held 9 restaurants and 11 users, 8 of each named
`Checkout <uuid>` / `checkout-<uuid>@example.test`. Running the suite once took restaurants from 13
to 14 — one new row set per run, accumulating forever.

This is not cosmetic. `GET /api/restaurants` sorts by name, `"Checkout …"` sorts before
`"Demo Kitchen"`, and `storefront.tsx` hardcodes `restaurants[0]`. The storefront therefore loads a
one-item test fixture instead of the seeded demo menu — the development environment is visibly
broken by running the tests.

**Recommendation.** Give the class an `@AfterEach` that deletes the fixtures when they were
committed, as the new `concurrentCheckoutWithTheSameKeyReplaysTheWinningOrder` test does via
`deleteCommittedFixtures()`. Clean the existing rows with
`UPDATE restaurants SET active = false WHERE name LIKE 'Checkout %'` (reversible) or a delete.
The deeper fix is finding 4 of `plan.md` §9.1: Testcontainers would give each run its own database.

---

## Documentation accuracy

Checked `docs/`, `AGENTS.md`, `plan.md`, `CLAUDE.md` and `README.md` against the code:

| Claim | Where | Reality |
|---|---|---|
| "Spring Session cookie" | `CLAUDE.md:46` | No `spring-session` dependency; plain servlet session (finding 17) |
| Testcontainers for integration tests | `AGENTS.md`, `plan.md` §1 | Not in `pom.xml`; tests use the Compose database (already `plan.md` §9.1 item 4) |
| "order placement endpoints are not implemented yet" | `docs/local-validation-test-cases.md` | Stale, dated 2026-10-20, schema v1, 12 tests — now `V3` and 46 tests (already §9.1 item 3) |
| Enumeration-resistant recovery | `docs/password-recovery.md` | Undercut by the timing channel and race-500 (findings 6, 7) |
| OTP lifecycle | `docs/password-recovery.md` | No cleanup of abandoned challenges documented or implemented (finding 11) |

`plan.md` §9.1 items 1, 2 and 5 (no ADMIN bootstrap, no admin menu UI, no payment schema) remain accurate and
are the agreed scope of Step 8. One gap §9.1 missed: `AdminMenuController` is write-only, so a menu-management
UI has no endpoint that can show inactive categories or unavailable items. Relatedly, `Restaurant` has no admin
CRUD at all and no setter for `active`, so outside the `development` seeder there is no way to create a
restaurant or take one offline.

---

## Suggested order of work

1. **Finding 2** — restore the six characters. Minutes, and it is a visible defect in the shipping UI.
2. **Finding 4** — add logging. Everything else here is harder to diagnose without it.
3. **Findings 1 and 6** — close both unique-constraint races using the pattern already in `UserAccountService`.
4. **Findings 3, 5, 7, 8** — the security set: session revocation on reset, gate Swagger, move hashing and
   SMTP off the request thread, fix the rate-limit key.
5. **Findings 10, 12** — make errors usable end to end (catch-all handler plus a typed client error). These
   pair naturally and improve every other failure path.
6. **Findings 9, 11, 16** — data-integrity and hygiene; 9 needs a migration, so fold it into the Step 8 `V4`.
7. **Findings 13, 14, 15, 18, 19, 20** — the frontend correctness set, ideally with the `storefront` tests
   first so the rest land against a safety net.
8. **Findings 21–35** — cleanups, worth batching into one pass.

Findings 1, 3, 9, 11 and 20 are the ones that would be hardest to retrofit later, because they touch the
checkout transaction, the session model, the schema and the test baseline respectively.

---

## Resolution log

Applied 2026-10-31. Verification: **48 backend tests pass** (46 original, unchanged, plus 2 new
regression tests), frontend **7 tests / `tsc` / `eslint`** all clean, and the character fix confirmed
in the running app's DOM.

### Finding 1 — checkout idempotency race

`OrderService.create` now runs the insert through a `TransactionTemplate` and catches
`DataIntegrityViolationException`. The losing request re-reads the winner's order in a **fresh**
transaction (the one that hit the constraint is already rolled back) and returns it as a normal
replay. `TransactionTemplate` is used rather than a second `@Transactional` method because a
self-invocation would bypass the proxy; it is the pattern already used in `DevelopmentDataSeeder`.
The fingerprint comparison was extracted to `replayOf(...)` and is shared with the existing
pre-insert path, so the "same key, different payload" rule still applies to the recovered order. If
the violation came from some other constraint, the original exception is rethrown rather than
disguised.

New test `OrderCheckoutControllerTests.concurrentCheckoutWithTheSameKeyReplaysTheWinningOrder`
forces the lookup to miss once so the insert hits the unique index for real. **Confirmed it fails
without the fix**, with exactly the predicted error:

```
DataIntegrityViolationException: duplicate key value violates unique constraint
"uq_orders_customer_idempotency_key"
```

It runs `NOT_SUPPORTED` (the aborted insert poisons an enclosing transaction) and therefore cleans up
its own committed rows — unlike the test described in finding 36.

### Finding 2 — corrupted characters

All six restored in `storefront.tsx`: `×` (close), `−` (decrement, now pairing with `+`), `₹` (the fee
sentence), `↗` (checkout), and `✳` for the confirmation and empty-bag marks. The two `✳` choices are a
judgement call — the original bytes are unrecoverable — based on `.confirmation-mark` and
`.empty-cart>span` sharing identical styling (orange, 38px) with the brand's sparkle.

Git reports **one line changed**. Verified in the live DOM rather than by eye: the drawer now
contains **zero** `?` characters, and reads `−` / `+`, "fee are ₹0 for now.", "Sign in to checkout ↗".

A CRLF-to-LF conversion slipped in during the edit and was reverted; the file keeps its original
line endings.

### Finding 3 — sessions surviving a password reset

A `SessionRegistry` and `HttpSessionEventPublisher` were added, `RegisterSessionAuthenticationStrategy`
appended to the existing composite strategy (last, so it records the id *after* session-fixation
protection changes it), and `maximumSessions(-1)` set so `ConcurrentSessionFilter` is installed and
actually enforces `expireNow()`. Logins stay unlimited. An expired session now gets a `401` with code
`SESSION_EXPIRED` in the standard `ApiError` shape instead of the framework's default page.

`PasswordResetService.resetPassword` expires every session held by the account. This required
`equals`/`hashCode` on `AuthenticatedUser` (by id), because `SessionRegistry` keys sessions by
principal and each login builds a fresh instance — without it the lookup silently returned nothing.

New test `AuthControllerTests.passwordResetSignsOutSessionsOpenedWithTheOldPassword` signs in,
confirms the session works, resets the password via the real OTP flow, and asserts the original
session is then rejected.

**Still open:** sessions remain in-memory (finding 17), so this works within one instance only.

### Finding 4 — no logging

SLF4J loggers added to `ChatService`, `PasswordResetService` and `OrderService`. The two sites that
discarded an exception now log it with its cause: the OpenRouter failure and the SMTP failure. A
missing API key and a provider returning no usable reply are now distinguishable from a transport
failure, which was the actual complaint. No OTP, password, email address, API key or idempotency key
is written to the log.

This is the narrow fix for the two swallow sites. Broader request/audit logging is still absent.
