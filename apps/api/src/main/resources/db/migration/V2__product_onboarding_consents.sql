CREATE TABLE onboarding_responses (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    age_band TEXT NOT NULL,
    income_band TEXT NOT NULL,
    job_category TEXT NOT NULL,
    household_type TEXT NOT NULL,
    money_style TEXT NOT NULL,
    area TEXT NOT NULL,
    goal_type TEXT NOT NULL,
    pain_point TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id)
);

CREATE TABLE consent_events (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    consent_item TEXT NOT NULL,
    consent_version TEXT NOT NULL,
    status TEXT NOT NULL,
    summary TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE mydata_connections (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    connection_status TEXT NOT NULL,
    data_mode TEXT NOT NULL,
    consent_version TEXT NOT NULL,
    scopes TEXT NOT NULL,
    connected_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id)
);

CREATE INDEX idx_onboarding_responses_user ON onboarding_responses(user_id);
CREATE INDEX idx_consent_events_user_item ON consent_events(user_id, consent_item);
CREATE INDEX idx_mydata_connections_user ON mydata_connections(user_id);
