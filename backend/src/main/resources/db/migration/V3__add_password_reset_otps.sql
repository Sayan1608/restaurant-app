CREATE TABLE password_reset_otps (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    otp_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    failed_attempts INTEGER NOT NULL DEFAULT 0 CHECK (failed_attempts >= 0)
);
