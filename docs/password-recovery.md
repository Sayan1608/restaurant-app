# Email OTP password recovery

Password recovery uses the email address already attached to a customer account. The flow requires SMTP configuration in the backend environment (`SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, and optionally `SMTP_FROM`). The code is never returned by the API.

## Request a code

`POST /api/auth/forgot-password`

```json
{ "email": "customer@example.com" }
```

The response is the same whether the account exists or not:

```json
{ "message": "If an account exists for that email, a verification code has been sent." }
```

An existing account is sent a six-digit code. The stored code is a BCrypt hash, expires after 10 minutes, and can be requested again after a 60-second cooldown.

## Set a new password

`POST /api/auth/reset-password`

```json
{
  "email": "customer@example.com",
  "otp": "123456",
  "newPassword": "a new password with at least 12 chars"
}
```

Codes are accepted once, and five incorrect attempts invalidate the challenge. A successful reset stores a BCrypt password hash. Both POST routes require the normal CSRF header used by the browser API client.

SMS recovery is not available because the account model does not yet store verified phone numbers and no SMS provider is configured.
