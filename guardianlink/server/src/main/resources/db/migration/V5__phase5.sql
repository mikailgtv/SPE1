-- GuardianLink Phase 5: Geofencing / Safe Zones / Entry-Exit alerts (monitoring only)
-- موتور تشخیص ورود/خروج سمت سرور است (بدون Play Services روی دستگاه کودک، بدون FGS،
-- بدون هیچ notification روی گوشی کودک) — کودک همان fixهای Phase 4 را می‌فرستد.

CREATE TABLE geofences (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id  UUID NOT NULL REFERENCES devices (id) ON DELETE CASCADE,
    name       TEXT NOT NULL,
    latitude   DOUBLE PRECISION NOT NULL,
    longitude  DOUBLE PRECISION NOT NULL,
    radius_m   INT NOT NULL,
    enabled    BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- «Duplicate Zone» تعریف مشخص دارد: نام تکراری برای یک دستگاه مجاز نیست
    UNIQUE (device_id, name)
);
CREATE INDEX ix_geofences_device ON geofences (device_id);
-- Case-insensitive duplicate-name protection (the table UNIQUE is retained for compatibility).
CREATE UNIQUE INDEX ux_geofences_device_name_lower ON geofences(device_id, lower(name));

CREATE TABLE geofence_states (
    geofence_id         UUID PRIMARY KEY REFERENCES geofences (id) ON DELETE CASCADE,
    -- بند ۱۰: بعد از ساخت، بدون اطلاعات کافی ⇒ unknown (هرگز inside/outside قطعی اعلام نمی‌شود)
    state               TEXT NOT NULL DEFAULT 'unknown', -- unknown | inside | outside | error
    last_transition_at  TIMESTAMPTZ,
    last_known_accuracy REAL,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE geofence_events (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    geofence_id  UUID NOT NULL REFERENCES geofences (id) ON DELETE CASCADE,
    device_id    UUID NOT NULL REFERENCES devices (id) ON DELETE CASCADE,
    event_type   TEXT NOT NULL, -- GEOFENCE_ENTER | GEOFENCE_EXIT | GEOFENCE_CREATED | GEOFENCE_UPDATED | GEOFENCE_ERROR
    ts           TIMESTAMPTZ NOT NULL DEFAULT now(),     -- زمان خود رویداد
    accuracy_m   REAL,                                    -- دقت fix منجر به رویداد (شفافیت)
    dwell_min    INT,                                     -- Dwell: مدت حضور داخل zone قبل از EXIT
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_geofence_events_device_ts ON geofence_events (device_id, ts DESC);
CREATE INDEX ix_geofence_events_zone_ts ON geofence_events (geofence_id, ts DESC);

CREATE TABLE geofence_notification_settings (
    geofence_id     UUID PRIMARY KEY REFERENCES geofences (id) ON DELETE CASCADE,
    notify_on_entry BOOLEAN NOT NULL DEFAULT true,
    notify_on_exit  BOOLEAN NOT NULL DEFAULT true,
    enabled         BOOLEAN NOT NULL DEFAULT true  -- کلید کلی اعلان‌های همین zone
);
