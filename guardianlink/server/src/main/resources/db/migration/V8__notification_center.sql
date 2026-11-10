CREATE TABLE IF NOT EXISTS family_notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL REFERENCES families(id) ON DELETE CASCADE,
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    event_id UUID NOT NULL UNIQUE REFERENCES device_events(id) ON DELETE CASCADE,
    event_type TEXT NOT NULL,
    category TEXT NOT NULL,
    severity TEXT NOT NULL,
    title_key TEXT NOT NULL,
    body_key TEXT NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL,
    status TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','ACKNOWLEDGED','RESOLVED')),
    acknowledged_at TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_family_notifications_feed
    ON family_notifications(family_id, occurred_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_family_notifications_unread
    ON family_notifications(family_id, occurred_at DESC) WHERE acknowledged_at IS NULL;

CREATE TABLE IF NOT EXISTS notification_user_state (
    notification_id UUID NOT NULL REFERENCES family_notifications(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    read_at TIMESTAMPTZ,
    PRIMARY KEY(notification_id,user_id)
);
CREATE INDEX IF NOT EXISTS idx_notification_user_state_user_read
    ON notification_user_state(user_id,read_at,notification_id);

CREATE TABLE IF NOT EXISTS notification_preferences (
    family_id UUID PRIMARY KEY REFERENCES families(id) ON DELETE CASCADE,
    enabled_categories JSONB NOT NULL DEFAULT '{"BATTERY":true,"CONNECTIVITY":true,"LOCATION":true,"GEOFENCE":true,"APP_USAGE":true,"WEB_SAFETY":true,"PERMISSION":true,"SECURITY":true}'::jsonb,
    quiet_hours_enabled BOOLEAN NOT NULL DEFAULT false,
    quiet_start TIME NOT NULL DEFAULT '22:00',
    quiet_end TIME NOT NULL DEFAULT '07:00',
    timezone TEXT NOT NULL DEFAULT 'UTC',
    critical_during_quiet_hours BOOLEAN NOT NULL DEFAULT true,
    daily_summary_enabled BOOLEAN NOT NULL DEFAULT false,
    cooldown_minutes INTEGER NOT NULL DEFAULT 10 CHECK (cooldown_minutes BETWEEN 0 AND 240),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Preserve history by projecting existing actionable events. Non-alert administrative
-- changes remain in device_events only and are not presented as notifications.
INSERT INTO family_notifications(family_id,device_id,event_id,event_type,category,severity,title_key,body_key,metadata,occurred_at)
SELECT d.family_id,e.device_id,e.id,e.event_type,m.category,m.severity,m.title_key,m.body_key,
       COALESCE(NULLIF(e.metadata,'')::jsonb,'{}'::jsonb),e.ts
FROM device_events e JOIN devices d ON d.id=e.device_id
CROSS JOIN LATERAL (
  SELECT CASE
    WHEN e.event_type LIKE 'BATTERY_%' THEN 'BATTERY'
    WHEN e.event_type IN ('DEVICE_OFFLINE','DEVICE_ONLINE','SYNC_DELAYED','SYNC_RECOVERED','DEVICE_CONNECTED','DEVICE_DISCONNECTED') THEN 'CONNECTIVITY'
    WHEN e.event_type LIKE 'LOCATION_%' THEN 'LOCATION'
    WHEN e.event_type LIKE 'GEOFENCE_%' THEN 'GEOFENCE'
    WHEN e.event_type LIKE 'APP_USAGE_%' OR e.event_type LIKE 'SCREEN_TIME_%' OR e.event_type LIKE 'SCHEDULE_%' OR e.event_type IN ('USAGE_ACCESS_REVOKED','USAGE_REVOKED') THEN 'APP_USAGE'
    WHEN e.event_type LIKE 'WEB_%' THEN 'WEB_SAFETY'
    WHEN e.event_type LIKE 'PERMISSION_%' THEN 'PERMISSION'
    ELSE 'SECURITY' END AS category,
  CASE WHEN e.event_type IN ('BATTERY_CRITICAL','DEVICE_OFFLINE','WEB_PROTECTION_DISABLED','APP_RESTRICTION_FAILED') THEN 'CRITICAL'
       WHEN e.event_type IN ('BATTERY_LOW','SYNC_DELAYED','LOCATION_PERMISSION_REVOKED','LOCATION_DISABLED','LOCATION_SYNC_DELAYED','GEOFENCE_ERROR','WEB_FILTERING_ERROR','APP_USAGE_LIMIT_CRITICAL','APP_USAGE_LIMIT_REACHED','SCREEN_TIME_LIMIT_REACHED','USAGE_ACCESS_REVOKED','USAGE_REVOKED') THEN 'WARNING'
       ELSE 'INFO' END AS severity,
  lower(e.event_type) || '.title' AS title_key,
  lower(e.event_type) || '.body' AS body_key
) m
WHERE e.event_type IN (
 'BATTERY_LOW','BATTERY_CRITICAL','BATTERY_RECOVERED','DEVICE_OFFLINE','DEVICE_ONLINE','SYNC_DELAYED','SYNC_RECOVERED',
 'DEVICE_CONNECTED','DEVICE_DISCONNECTED','LOCATION_PERMISSION_REVOKED','LOCATION_DISABLED','LOCATION_UNAVAILABLE',
 'LOCATION_PERMISSION_GRANTED','LOCATION_SYNC_DELAYED','GEOFENCE_ENTER','GEOFENCE_EXIT','GEOFENCE_ERROR','APP_USAGE_LIMIT_NEAR','APP_USAGE_LIMIT_CRITICAL','APP_USAGE_LIMIT_REACHED',
 'SCREEN_TIME_LIMIT_NEAR','SCREEN_TIME_LIMIT_REACHED','USAGE_ACCESS_REVOKED','USAGE_REVOKED','SCHEDULE_STARTED','SCHEDULE_ENDED','APP_RESTRICTION_ENFORCED','APP_RESTRICTION_FAILED','APP_RESTRICTION_UNSUPPORTED','WEB_PROTECTION_DISABLED','WEB_FILTERING_ERROR',
 'WEB_FILTERING_UNAVAILABLE','PERMISSION_CHANGED'
)
AND (e.event_type NOT IN ('GEOFENCE_ENTER','GEOFENCE_EXIT') OR COALESCE(e.metadata::jsonb->>'notify','false')='true')
ON CONFLICT(event_id) DO NOTHING;
