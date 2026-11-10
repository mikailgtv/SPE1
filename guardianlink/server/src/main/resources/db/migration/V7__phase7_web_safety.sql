-- Phase 7: transparent web rules and status. No URL visits, page bodies, queries, credentials,
-- cookies or browser history are stored. Enforcement is browser/enterprise-policy scoped.
-- ALLOW_ALL is retained as a compatibility alias for BLOCK_LISTED (default allow + explicit block rules).

CREATE TABLE web_protection_settings (
    device_id          UUID PRIMARY KEY REFERENCES devices(id) ON DELETE CASCADE,
    enabled            BOOLEAN NOT NULL DEFAULT false,
    default_policy     TEXT NOT NULL DEFAULT 'ALLOW_ALL'
                       CHECK (default_policy IN ('ALLOW_ALL','BLOCK_LISTED','ALLOW_LISTED_ONLY')),
    requested_mode     TEXT NOT NULL DEFAULT 'AUTO'
                       CHECK (requested_mode IN ('AUTO','VPN_BASED','DNS_BASED','MANAGED_DEVICE')),
    alerts_enabled     BOOLEAN NOT NULL DEFAULT true,
    rule_version       BIGINT NOT NULL DEFAULT 0,
    reported_mode      TEXT NOT NULL DEFAULT 'UNKNOWN',
    reported_status    TEXT NOT NULL DEFAULT 'UNKNOWN',
    reported_reason    TEXT NOT NULL DEFAULT '',
    dns_status         TEXT NOT NULL DEFAULT 'UNSUPPORTED',
    vpn_status         TEXT NOT NULL DEFAULT 'UNSUPPORTED',
    timezone_id        TEXT NOT NULL DEFAULT 'UTC',
    last_reported_at   TIMESTAMPTZ,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE web_rules (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id    UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    rule_type    TEXT NOT NULL CHECK (rule_type IN ('DOMAIN','CATEGORY')),
    domain       TEXT,
    scope        TEXT NOT NULL DEFAULT 'DOMAIN_AND_SUBDOMAINS'
                 CHECK (scope IN ('EXACT_HOST','DOMAIN_AND_SUBDOMAINS')),
    action       TEXT NOT NULL CHECK (action IN ('ALLOW','BLOCK')),
    category     TEXT,
    enabled      BOOLEAN NOT NULL DEFAULT true,
    source       TEXT NOT NULL DEFAULT 'PARENT' CHECK (source IN ('PARENT','PROVIDER')),
    priority     INT NOT NULL DEFAULT 0,
    created_by   UUID NOT NULL REFERENCES users(id),
    version      BIGINT NOT NULL DEFAULT 1,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((rule_type='DOMAIN' AND domain IS NOT NULL AND category IS NULL)
        OR (rule_type='CATEGORY' AND domain IS NULL AND category IS NOT NULL))
);
CREATE UNIQUE INDEX ux_web_domain_rule ON web_rules(device_id,domain,scope,action)
    WHERE rule_type='DOMAIN';
CREATE UNIQUE INDEX ux_web_category_rule ON web_rules(device_id,category,action)
    WHERE rule_type='CATEGORY';
CREATE INDEX ix_web_rules_device_enabled ON web_rules(device_id,enabled,rule_type,priority DESC);

CREATE TABLE web_rule_schedules (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id       UUID NOT NULL REFERENCES web_rules(id) ON DELETE CASCADE,
    day_of_week   SMALLINT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    start_time    TIME NOT NULL,
    end_time      TIME NOT NULL,
    action        TEXT NOT NULL CHECK (action IN ('ALLOW','BLOCK')),
    timezone_id   TEXT,
    enabled       BOOLEAN NOT NULL DEFAULT true,
    CHECK (start_time <> end_time),
    UNIQUE(rule_id,day_of_week,start_time,end_time)
);
CREATE INDEX ix_web_schedule_rule ON web_rule_schedules(rule_id,enabled);

-- Category codes are policy labels, not an assertion that a classifier exists.
CREATE TABLE web_categories (
    category_code TEXT PRIMARY KEY,
    provider_id   TEXT NOT NULL DEFAULT 'unconfigured',
    available     BOOLEAN NOT NULL DEFAULT false,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE web_overrides (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id    UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    rule_id      UUID REFERENCES web_rules(id) ON DELETE SET NULL,
    domain       TEXT NOT NULL,
    scope        TEXT NOT NULL CHECK (scope IN ('EXACT_HOST','DOMAIN_AND_SUBDOMAINS')),
    action       TEXT NOT NULL CHECK (action IN ('ALLOW','BLOCK')),
    expires_at   TIMESTAMPTZ NOT NULL,
    created_by   UUID NOT NULL REFERENCES users(id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at   TIMESTAMPTZ
);
CREATE INDEX ix_web_overrides_active ON web_overrides(device_id,expires_at) WHERE revoked_at IS NULL;

CREATE TABLE web_filter_events (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id   UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    rule_id     UUID REFERENCES web_rules(id) ON DELETE SET NULL,
    event_type  TEXT NOT NULL,
    ts          TIMESTAMPTZ NOT NULL DEFAULT now(),
    metadata    TEXT NOT NULL DEFAULT '{}',
    dedupe_key  TEXT NOT NULL UNIQUE,
    source      TEXT NOT NULL DEFAULT 'server' CHECK (source IN ('server','device'))
);
CREATE INDEX ix_web_events_device_ts ON web_filter_events(device_id,ts DESC);

-- No unique visits/log of sites that the user actually browsed. Rule configuration and
-- capability/error status events only; category enforcement stays false until a provider is configured.
