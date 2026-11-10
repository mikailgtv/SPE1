-- Phase 6: app inventory, per-app rules/schedules, management capability reporting.
-- No message content, credentials, keyboard, clipboard, camera, microphone, screen capture.

CREATE TABLE app_inventory (
    device_id       UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    package_name    TEXT NOT NULL,
    app_name        TEXT NOT NULL,
    version_name    TEXT NOT NULL DEFAULT '',
    version_code    BIGINT NOT NULL DEFAULT 0,
    installed_at    TIMESTAMPTZ,
    is_system_app   BOOLEAN NOT NULL DEFAULT false,
    category        TEXT NOT NULL DEFAULT 'undefined',
    installed       BOOLEAN NOT NULL DEFAULT true,
    uninstalled_at  TIMESTAMPTZ,
    first_seen_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (device_id, package_name)
);
CREATE INDEX ix_app_inventory_device_installed ON app_inventory(device_id, installed, app_name);

CREATE TABLE app_management_state (
    device_id             UUID PRIMARY KEY REFERENCES devices(id) ON DELETE CASCADE,
    rule_version          BIGINT NOT NULL DEFAULT 0,
    management_mode       TEXT NOT NULL DEFAULT 'unknown', -- normal | managed | unsupported | unknown
    usage_access_granted  BOOLEAN NOT NULL DEFAULT false,
    timezone_id           TEXT NOT NULL DEFAULT 'UTC',
    inventory_updated_at  TIMESTAMPTZ,
    last_sync_at          TIMESTAMPTZ,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE app_rules (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id             UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    package_name          TEXT NOT NULL,
    rule_type             TEXT NOT NULL CHECK (rule_type IN ('ALLOW','LIMITED','RESTRICTED','SCHEDULED')),
    daily_limit_seconds   INT,
    near_pct              INT NOT NULL DEFAULT 80,
    critical_pct          INT NOT NULL DEFAULT 90,
    enabled               BOOLEAN NOT NULL DEFAULT true,
    created_by            UUID NOT NULL REFERENCES users(id),
    version               BIGINT NOT NULL DEFAULT 1,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_enforcement_status TEXT NOT NULL DEFAULT 'unknown',
    last_enforcement_at TIMESTAMPTZ,
    UNIQUE (device_id, package_name),
    CHECK (daily_limit_seconds IS NULL OR daily_limit_seconds BETWEEN 60 AND 86400),
    CHECK (near_pct BETWEEN 1 AND 99),
    CHECK (critical_pct BETWEEN 1 AND 100),
    CHECK (critical_pct > near_pct)
);
CREATE INDEX ix_app_rules_device_enabled ON app_rules(device_id, enabled);

CREATE TABLE app_rule_schedules (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id      UUID NOT NULL REFERENCES app_rules(id) ON DELETE CASCADE,
    day_of_week  SMALLINT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7), -- ISO Monday=1 ... Sunday=7
    start_time   TIME NOT NULL,
    end_time     TIME NOT NULL,
    enabled      BOOLEAN NOT NULL DEFAULT true,
    CHECK (start_time <> end_time),
    UNIQUE (rule_id, day_of_week, start_time, end_time)
);
CREATE INDEX ix_app_rule_schedules_rule ON app_rule_schedules(rule_id, enabled);

CREATE TABLE app_rule_usage_state (
    rule_id       UUID NOT NULL REFERENCES app_rules(id) ON DELETE CASCADE,
    usage_date    DATE NOT NULL,
    last_stage    SMALLINT NOT NULL DEFAULT 0, -- 0=under, 1=near, 2=critical, 3=reached
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (rule_id, usage_date)
);

CREATE TABLE app_restriction_events (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id     UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    rule_id       UUID REFERENCES app_rules(id) ON DELETE SET NULL,
    package_name  TEXT NOT NULL,
    event_type    TEXT NOT NULL,
    ts            TIMESTAMPTZ NOT NULL DEFAULT now(),
    metadata      TEXT NOT NULL DEFAULT '{}',
    dedupe_key    TEXT NOT NULL UNIQUE,
    processed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_app_restriction_events_device_ts ON app_restriction_events(device_id, ts DESC);

-- Device-reported capability is informative, never trusted as authorization.
-- Actual enforcement is guarded locally by DevicePolicyManager.isDeviceOwnerApp().
