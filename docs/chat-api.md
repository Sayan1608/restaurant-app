# Menu FAQ assistant API

The assistant is available at `POST /api/chat/messages`. It is public and intended for menu and general ordering FAQ questions. It does not read customer, checkout, or order data and cannot perform ordering or admin actions. Because the application uses cookie-based sessions, clients should first call `GET /api/auth/csrf` and send the returned token in the named header for this POST.

Request:

```json
{
  "restaurantId": "<restaurant-uuid>",
  "message": "Which mains are on the menu?"
}
```

The message must contain 1–1000 characters. The backend reads the restaurant's currently public menu from the database and sends only item names, descriptions, prices, and active categories as model context. The model output is limited to 500 generated tokens and 2000 characters. Provider connection and response timeouts are 3 and 15 seconds. Requests are limited to 20 per client IP per minute per backend instance.

Configure `OPENROUTER_API_KEY` and `OPENROUTER_MODEL` on the backend only. `OPENROUTER_BASE_URL` can override the provider-compatible API URL. With no key or an upstream failure, the endpoint returns a safe `503 CHAT_UNAVAILABLE` response. Rate-limited requests return `429 CHAT_RATE_LIMITED`. No provider key or user-private data is sent to the browser or model.

The assistant's answer is informational. It cannot confirm current stock beyond the public menu snapshot it receives and cannot place, look up, or modify orders.
