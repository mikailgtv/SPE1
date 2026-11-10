-- GuardianLink Phase 2 schema: monitoring, events, per-family settings

CREATE TABLE family_settings (
    family_id               UUID PRIMARY KEY REFERENCES families (id) ON DELETE CASCADE,
    battery_low_pct         INT NOT NULL DEFAULT 30,
    battery_critical_pct    INT NOT NULL DEFAULT 15,
    sync_delayed_sec        INT NOT NULL DEFAULT 1800,
    offline_sec             INT NOT NULL DEFAULT 3600,
    heartbeat_interval_sec  INT NOT NULL DEFAULT 900,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE device_status (
    device_id        UUID PRIMARY KEY REFERENCES devices (id) ON DELETE CASCADE,
    battery_level    INT,
    battery_band     TEXT NOT NULL DEFAULT 'unknown', -- normal | low | critical | unknown
    connection_state TEXT NOT NULL DEFAULT 'unknown', -- wifi | cellular | ethernet | none | unknown
    health_state     TEXT NOT NULL DEFAULT 'unknown', -- healthy | delayed | offline | unknown
    last_heartbeat   TIMESTAMPTZ,
    last_sync        TIMESTAMPTZ,
    app_version      TEXT NOT NULL DEFAULT '',
    android_version  TEXT NOT NULL DEFAULT '',
    model            TEXT NOT NULL DEFAULT '',
    manufacturer     TEXT NOT NULL DEFAULT '',
    permissions      TEXT NOT NULL DEFAULT '{}',      -- JSON map key->state
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE device_events (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id  UUID NOT NULL REFERENCES devices (id) ON DELETE CASCADE,
    event_type TEXT NOT NULL,
    ts         TIMESTAMPTZ NOT NULL DEFAULT now(),
    metadata   TEXT NOT NULL DEFAULT '{}',
    dedupe_key TEXT UNIQUE
);
CREATE INDEX ix_device_events_device_ts ON device_events (device_id, ts DESC);
