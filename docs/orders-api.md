# Order history and status API

All routes require the existing cookie session. Mutating requests require a CSRF token from `GET /api/auth/csrf`. Customer history and detail routes are owner-scoped; an admin can view order details. Admin list and status routes require the `ADMIN` role.

## Customer routes

`GET /api/orders/my?page=0&size=10` returns only the current customer's orders, newest first. Page size must be between 1 and 50. The response contains `content`, `page`, `size`, `totalElements`, and `totalPages`; each order includes its item snapshots, status, totals, and creation time.

`GET /api/orders/{orderId}` returns the order details and delivery contact information to its owner or an admin. A different customer receives `404 ORDER_NOT_FOUND` to avoid revealing another customer's order IDs.

## Admin routes

`GET /api/admin/orders?page=0&size=20&status=PLACED` lists incoming orders newest first. `status` is optional and may be `PLACED`, `CONFIRMED`, `PREPARING`, `READY`, `COMPLETED`, or `CANCELLED`. Page size is limited to 50. Admin results include customer and delivery contact details needed to process orders.

`PATCH /api/admin/orders/{orderId}/status` accepts `{ "status": "CONFIRMED" }`. Allowed transitions are `PLACED -> CONFIRMED -> PREPARING -> READY -> COMPLETED`. Admins may cancel an order only from `PLACED` or `CONFIRMED`, before preparation starts. Completed and cancelled orders are terminal. Skipped, backward, and terminal-state transitions return `409 INVALID_ORDER_STATUS_TRANSITION`.
