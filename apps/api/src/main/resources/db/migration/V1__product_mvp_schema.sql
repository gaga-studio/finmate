CREATE TABLE users (
    id TEXT PRIMARY KEY,
    email TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    display_name TEXT NOT NULL,
    onboarding_completed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE refresh_tokens (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_profiles (
    user_id TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    age_band TEXT NOT NULL DEFAULT '20대',
    income_band TEXT NOT NULL DEFAULT '3,000만원 ~ 4,000만원',
    job_category TEXT NOT NULL DEFAULT 'IT/개발',
    household_type TEXT NOT NULL DEFAULT '1인가구',
    money_style TEXT NOT NULL DEFAULT '안정 추구형',
    area TEXT NOT NULL DEFAULT '서울 강남권',
    goal_type TEXT NOT NULL DEFAULT 'EMERGENCY_FUND',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE privacy_settings (
    user_id TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    anonymous_portfolio_opt_in BOOLEAN NOT NULL DEFAULT TRUE,
    friend_share_default TEXT NOT NULL DEFAULT 'MISSION_ONLY',
    exposed_fields TEXT NOT NULL DEFAULT 'ageBand,goalType,financialSummary',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE financial_snapshots (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    month TEXT NOT NULL,
    monthly_income INTEGER NOT NULL,
    monthly_spending INTEGER NOT NULL,
    monthly_saving INTEGER NOT NULL,
    investment_value INTEGER NOT NULL,
    cash_like_assets INTEGER NOT NULL,
    emergency_fund_months NUMERIC(6,2) NOT NULL,
    categories_json TEXT NOT NULL,
    lifestyle_tags TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, month)
);

CREATE TABLE coach_results (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    snapshot_id TEXT NOT NULL REFERENCES financial_snapshots(id) ON DELETE CASCADE,
    source TEXT NOT NULL,
    score INTEGER NOT NULL,
    confidence NUMERIC(5,2) NOT NULL,
    summary TEXT NOT NULL,
    insights_json TEXT NOT NULL,
    recommendations_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE missions (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    status TEXT NOT NULL,
    difficulty TEXT NOT NULL,
    reward_points INTEGER NOT NULL,
    progress INTEGER NOT NULL DEFAULT 0,
    due_date DATE,
    source TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ
);

CREATE TABLE mission_events (
    id TEXT PRIMARY KEY,
    mission_id TEXT NOT NULL REFERENCES missions(id) ON DELETE CASCADE,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_type TEXT NOT NULL,
    note TEXT,
    reward_points INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE daily_records (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    record_date DATE NOT NULL,
    budget INTEGER NOT NULL,
    spent INTEGER NOT NULL,
    category_spending_json TEXT NOT NULL,
    mission_status TEXT NOT NULL,
    point_delta INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, record_date)
);

CREATE TABLE friendships (
    id TEXT PRIMARY KEY,
    follower_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    followee_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (follower_id, followee_id)
);

CREATE TABLE feed_items (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    actor_user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kind TEXT NOT NULL,
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    amount INTEGER,
    privacy_level TEXT NOT NULL DEFAULT 'SUMMARY',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE point_wallets (
    user_id TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    point_balance INTEGER NOT NULL DEFAULT 0,
    virtual_money_balance INTEGER NOT NULL DEFAULT 100000,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE point_transactions (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type TEXT NOT NULL,
    amount INTEGER NOT NULL,
    balance_after INTEGER NOT NULL,
    reference_type TEXT NOT NULL,
    reference_id TEXT NOT NULL,
    description TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE birthday_funds (
    id TEXT PRIMARY KEY,
    owner_user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    target_amount INTEGER NOT NULL,
    current_amount INTEGER NOT NULL DEFAULT 0,
    due_date DATE NOT NULL,
    status TEXT NOT NULL,
    share_code TEXT NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE birthday_fund_contributions (
    id TEXT PRIMARY KEY,
    fund_id TEXT NOT NULL REFERENCES birthday_funds(id) ON DELETE CASCADE,
    contributor_user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    amount INTEGER NOT NULL,
    message TEXT NOT NULL,
    anonymous BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX idx_financial_snapshots_user ON financial_snapshots(user_id);
CREATE INDEX idx_coach_results_user ON coach_results(user_id);
CREATE INDEX idx_missions_user ON missions(user_id);
CREATE INDEX idx_daily_records_user_date ON daily_records(user_id, record_date);
CREATE INDEX idx_feed_items_user_created ON feed_items(user_id, created_at DESC);
CREATE INDEX idx_birthday_funds_owner ON birthday_funds(owner_user_id);
