# Restaurant and menu API

The APIs are available locally at `http://localhost:8080`. Swagger UI is at `http://localhost:8080/swagger-ui.html`.

Public responses contain active restaurants, active categories, and available menu items only. Menu items are grouped by category and ordered by category display order, then item name. An inactive restaurant/category or unavailable item is hidden from public endpoints and returns `404` when requested directly by ID.

## Public endpoints

| Method | Path | Result |
|---|---|---|
| `GET` | `/api/restaurants` | Active restaurants |
| `GET` | `/api/restaurants/{restaurantId}` | Active restaurant details |
| `GET` | `/api/restaurants/{restaurantId}/menu` | Active categories and available items for the restaurant |
| `GET` | `/api/menu-items/{itemId}` | One available item in an active category and restaurant |

The MVP is scoped to one restaurant per deployment, so these lists are currently small and are not paginated.

## Admin endpoints

All write endpoints require an authenticated `ADMIN` session and a current CSRF token in the `X-CSRF-TOKEN` header. See [authentication.md](authentication.md) for the session and CSRF flow.

| Method | Path | Body |
|---|---|---|
| `POST` | `/api/admin/restaurants/{restaurantId}/categories` | `{"name":"Desserts","displayOrder":2}` |
| `PUT` | `/api/admin/categories/{categoryId}` | `{"name":"Desserts","displayOrder":2}` |
| `PATCH` | `/api/admin/categories/{categoryId}/availability` | `{"active":false}` |
| `POST` | `/api/admin/categories/{categoryId}/items` | `{"name":"Gulab Jamun","description":"Two pieces","price":89.00,"imageUrl":null}` |
| `PUT` | `/api/admin/menu-items/{itemId}` | `{"categoryId":"<uuid>","name":"Gulab Jamun","description":"Two pieces","price":99.00,"imageUrl":null}` |
| `PATCH` | `/api/admin/menu-items/{itemId}/availability` | `{"available":false}` |

Deactivation is reversible: set a category's `active` field or an item's `available` field to `false`; setting it back to `true` makes it publicly visible again. Prices must be positive and have no more than two decimal places. A menu item can only be moved to another category in the same restaurant.

Successful creation returns `201 Created`; updates return `200 OK`. Invalid request fields return `400`, missing resources return `404`, duplicate category names return `409`, and non-admin requests to admin routes return `403` when authenticated as a customer or `401` when unauthenticated.
