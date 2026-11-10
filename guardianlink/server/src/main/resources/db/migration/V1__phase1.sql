-- GuardianLink Phase 1 schema
-- هیچ password / token خامی ذخیره نمی‌شود؛ فقط hash.

CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_users_email ON users (lower(email));

CREATE TABLE families (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE family_members (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id  UUID NOT NULL REFERENCES families (id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role       TEXT NOT NULL DEFAULT 'parent',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id)
);
CREATE INDEX ix_family_members_family ON family_members (family_id);

CREATE TABLE devices (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id          UUID NOT NULL REFERENCES families (id) ON DELETE CASCADE,
    device_name        TEXT NOT NULL,
    manufacturer       TEXT NOT NULL DEFAULT '',
    model              TEXT NOT NULL DEFAULT '',
    android_version    TEXT NOT NULL DEFAULT '',
    app_version        TEXT NOT NULL DEFAULT '',
    battery_level      INT,
    last_seen          TIMESTAMPTZ,
    status             TEXT NOT NULL DEFAULT 'active',
    install_id         TEXT,
    device_secret_hash TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_devices_family ON devices (family_id);
CREATE UNIQUE INDEX ux_devices_install_active ON devices (install_id)
    WHERE install_id IS NOT NULL AND status = 'active';

CREATE TABLE pairing_sessions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id           UUID NOT NULL REFERENCES families (id) ON DELETE CASCADE,
    parent_id           UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code_hash           TEXT NOT NULL,
    status              TEXT NOT NULL DEFAULT 'active', -- active | exchanging | used
    expires_at          TIMESTAMPTZ NOT NULL,
    exchange_token_hash TEXT,
    exchange_expires_at TIMESTAMPTZ,
    used_at             TIMESTAMPTZ,
    device_id           UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_pairing_code ON pairing_sessions (code_hash);
CREATE INDEX ix_pairing_family ON pairing_sessions (family_id);

CREATE TABLE user_sessions (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    refresh_hash TEXT NOT NULL UNIQUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at   TIMESTAMPTZ NOT NULL,
    revoked_at   TIMESTAMPTZ
);
CREATE INDEX ix_user_sessions_user ON user_sessions (user_id);

CREATE TABLE device_sessions (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id    UUID NOT NULL REFERENCES devices (id) ON DELETE CASCADE,
    refresh_hash TEXT NOT NULL UNIQUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at   TIMESTAMPTZ NOT NULL,
    revoked_at   TIMESTAMPTZ
);
CREATE INDEX ix_device_sessions_device ON device_sessions (device_id);
