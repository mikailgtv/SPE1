-- GuardianLink Phase 4: Location sharing / history / health (monitoring only)

CREATE TABLE locations (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id  UUID NOT NULL REFERENCES devices (id) ON DELETE CASCADE,
    latitude   DOUBLE PRECISION NOT NULL,
    longitude  DOUBLE PRECISION NOT NULL,
    accuracy_m REAL NOT NULL,
    client_ts  TIMESTAMPTZ NOT NULL,      -- زمان ثبت روی دستگاه (منبع dedupe)
    server_ts  TIMESTAMPTZ NOT NULL DEFAULT now(),
    source     TEXT NOT NULL DEFAULT 'fused', -- fused | gps | network | last_known
    -- sync تکراری/صف آفلاین ⇒ رکورد تکراری ساخته نمی‌شود
    UNIQUE (device_id, client_ts)
);
CREATE INDEX ix_locations_device_ts ON locations (device_id, client_ts DESC);

CREATE TABLE location_settings (
    device_id       UUID PRIMARY KEY REFERENCES devices (id) ON DELETE CASCADE,
    sharing_enabled BOOLEAN NOT NULL DEFAULT true,
    retention_days  INT NOT NULL DEFAULT 30,   -- 0 = فقط آخرین موقعیت (بدون تاریخچه)
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE location_status (
    device_id            UUID PRIMARY KEY REFERENCES devices (id) ON DELETE CASCADE,
    permission_status    TEXT NOT NULL DEFAULT 'unknown', -- granted | background_granted | denied | unknown
    provider_status      TEXT NOT NULL DEFAULT 'unknown', -- enabled | disabled | unknown
    last_fix_ts          TIMESTAMPTZ,                     -- آخرین موقعیت معتبر ذخیره‌شده
    last_report_ts       TIMESTAMPTZ,                     -- آخرین گزارش وضعیت از دستگاه
    health_state         TEXT NOT NULL DEFAULT 'unavailable', -- healthy | delayed | unavailable
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
