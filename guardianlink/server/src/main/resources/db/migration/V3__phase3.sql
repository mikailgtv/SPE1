-- GuardianLink Phase 3: screen time & app usage (monitoring only — no enforcement)

CREATE TABLE app_usage_daily (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id     UUID NOT NULL REFERENCES devices (id) ON DELETE CASCADE,
    package_name  TEXT NOT NULL,
    app_name      TEXT NOT NULL DEFAULT '',
    category      TEXT NOT NULL DEFAULT 'undefined',
    usage_date    DATE NOT NULL,
    usage_minutes INT NOT NULL DEFAULT 0,
    launch_count  INT NOT NULL DEFAULT 0,
    first_used_at TIMESTAMPTZ,
    last_used_at  TIMESTAMPTZ,
    icon_ver      INT NOT NULL DEFAULT 0,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (device_id, package_name, usage_date)
);
CREATE INDEX ix_usage_device_date ON app_usage_daily (device_id, usage_date);

CREATE TABLE app_icons (
    device_id    UUID NOT NULL REFERENCES devices (id) ON DELETE CASCADE,
    package_name TEXT NOT NULL,
    version_code INT NOT NULL DEFAULT 0,
    icon_png     BYTEA NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (device_id, package_name)
);

CREATE TABLE screen_time_limits (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id          UUID NOT NULL UNIQUE REFERENCES devices (id) ON DELETE CASCADE,
    daily_limit_minutes INT NOT NULL DEFAULT 240,
    near_pct           INT NOT NULL DEFAULT 80,
    enabled            BOOLEAN NOT NULL DEFAULT true,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE screen_time_schedules (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id  UUID NOT NULL REFERENCES devices (id) ON DELETE CASCADE,
    name       TEXT NOT NULL,
    start_time TEXT NOT NULL,           -- HH:MM
    end_time   TEXT NOT NULL,           -- HH:MM (بازه شبانه مجاز است؛ start==end رد می‌شود)
    days_of_week INT NOT NULL DEFAULT 127, -- bitmask: bit0=Monday ... bit6=Sunday
    enabled    BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_schedules_device ON screen_time_schedules (device_id);

-- وضعیت روزانه limit/schedule برای dedupe رویدادها
CREATE TABLE screen_time_state (
    device_id     UUID NOT NULL REFERENCES devices (id) ON DELETE CASCADE,
    day           DATE NOT NULL,
    limit_state   TEXT NOT NULL DEFAULT 'under',   -- under | near | reached
    schedule_keys TEXT NOT NULL DEFAULT '',        -- کلیدهای پنجره‌های فعال جاری
    PRIMARY KEY (device_id, day)
);
