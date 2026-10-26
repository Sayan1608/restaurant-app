# Checkout API

Create an order with `POST /api/orders`. The endpoint requires an authenticated `CUSTOMER` session, a valid CSRF token, and a UUID `Idempotency-Key` header. The browser should call `GET /api/auth/csrf` first and send its returned token in the configured CSRF header.

Request body:

```json
{
  "restaurantId": "<restaurant-uuid>",
  "items": [{ "menuItemId": "<menu-item-uuid>", "quantity": 2 }],
  "contactName": "Asha Customer",
  "contactPhone": "+91 98765 43210",
  "deliveryAddress": "22 Test Street, Bengaluru",
  "deliveryNotes": "Ring the bell"
}
```

Each item ID may appear once; quantities must be positive and at most 99, and at most 50 distinct lines are accepted. Contact name, phone, and address are required. Client prices and totals are ignored. The backend reloads active, available items belonging to the active restaurant and calculates line totals from current database prices. Orders preserve item name and unit price snapshots. Tax and delivery fees are zero for the current MVP.

A successful new order returns `201 Created`, a `Location` header, and the order summary. Repeating the same customer, key, and normalized request returns `200 OK` with the original order. Reusing that key for changed request details returns `409 IDEMPOTENCY_KEY_REUSED`; unavailable items return `409 MENU_ITEM_UNAVAILABLE`. Invalid requests return `400`.
