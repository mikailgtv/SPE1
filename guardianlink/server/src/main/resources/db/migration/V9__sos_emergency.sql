-- Phase 9: durable, family-scoped SOS sessions and event/route history.
CREATE TABLE sos_preferences (
    family_id UUID PRIMARY KEY REFERENCES families(id) ON DELETE CASCADE,
    auto_expiration_hours INTEGER NOT NULL DEFAULT 24 CHECK (auto_expiration_hours BETWEEN 1 AND 72),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE sos_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    client_request_id UUID NOT NULL,
    source TEXT NOT NULL DEFAULT 'child_ui' CHECK (source IN ('child_ui','hardware','other')),
    status TEXT NOT NULL CHECK (status IN ('ACTIVATING','ACTIVE','ACKNOWLEDGED','RESOLVED','CANCELLED','FAILED','EXPIRED')),
    activated_at TIMESTAMPTZ NOT NULL,
    acknowledged_at TIMESTAMPTZ,
    acknowledged_by UUID REFERENCES users(id) ON DELETE SET NULL,
    resolved_at TIMESTAMPTZ,
    resolved_by UUID REFERENCES users(id) ON DELETE SET NULL,
    cancelled_at TIMESTAMPTZ,
    cancelled_by_device BOOLEAN NOT NULL DEFAULT false,
    expires_at TIMESTAMPTZ,
    tracking_enabled BOOLEAN NOT NULL DEFAULT false,
    tracking_started_at TIMESTAMPTZ,
    tracking_ends_at TIMESTAMPTZ,
    tracking_interval_sec INTEGER NOT NULL DEFAULT 60 CHECK (tracking_interval_sec IN (30,60)),
    tracking_status TEXT NOT NULL DEFAULT 'LIMITED' CHECK (tracking_status IN ('ACTIVE','LIMITED','STOPPED')),
    tracking_reason TEXT,
    battery_level INTEGER CHECK (battery_level BETWEEN 0 AND 100),
    network_state TEXT NOT NULL DEFAULT 'unknown' CHECK (network_state IN ('wifi','cellular','ethernet','none','limited','unknown')),
    device_state TEXT NOT NULL DEFAULT 'unknown',
    permission_status TEXT NOT NULL DEFAULT 'unknown' CHECK (permission_status IN ('granted','background_granted','denied','unknown')),
    provider_status TEXT NOT NULL DEFAULT 'unknown' CHECK (provider_status IN ('enabled','disabled','unknown')),
    resolve_note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(device_id, client_request_id)
);
CREATE UNIQUE INDEX uq_sos_one_open_per_device ON sos_sessions(device_id)
    WHERE status IN ('ACTIVATING','ACTIVE','ACKNOWLEDGED');
CREATE INDEX ix_sos_family_active ON sos_sessions(family_id,status,activated_at DESC);
CREATE INDEX ix_sos_device_history ON sos_sessions(device_id,activated_at DESC);

CREATE TABLE sos_location_points (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES sos_sessions(id) ON DELETE CASCADE,
    latitude DOUBLE PRECISION NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude DOUBLE PRECISION NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    accuracy_m REAL CHECK (accuracy_m >= 0 AND accuracy_m <= 100000),
    client_ts TIMESTAMPTZ NOT NULL,
    server_ts TIMESTAMPTZ NOT NULL DEFAULT now(),
    provider TEXT NOT NULL CHECK (provider IN ('fused','gps','network','last_known','passive')),
    source TEXT NOT NULL DEFAULT 'tracking' CHECK (source IN ('activation','tracking','existing_location')),
    UNIQUE(session_id,client_ts)
);
CREATE INDEX ix_sos_locations_session_ts ON sos_location_points(session_id,client_ts DESC);

CREATE TABLE sos_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES sos_sessions(id) ON DELETE CASCADE,
    family_id UUID NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    event_type TEXT NOT NULL,
    actor_type TEXT NOT NULL CHECK (actor_type IN ('device','parent','system')),
    actor_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    dedupe_key TEXT UNIQUE
);
CREATE INDEX ix_sos_events_session_ts ON sos_events(session_id,occurred_at,id);

CREATE TABLE sos_notes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES sos_sessions(id) ON DELETE CASCADE,
    family_id UUID NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    author_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    note TEXT NOT NULL CHECK (char_length(note) BETWEEN 1 AND 1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_sos_notes_session ON sos_notes(session_id,created_at);

ALTER TABLE notification_preferences
    ALTER COLUMN enabled_categories SET DEFAULT '{"BATTERY":true,"CONNECTIVITY":true,"LOCATION":true,"GEOFENCE":true,"APP_USAGE":true,"WEB_SAFETY":true,"PERMISSION":true,"SECURITY":true,"EMERGENCY":true}'::jsonb;
UPDATE notification_preferences SET enabled_categories = enabled_categories || '{"EMERGENCY":true}'::jsonb;
