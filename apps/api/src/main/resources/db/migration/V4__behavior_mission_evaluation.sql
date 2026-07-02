CREATE TABLE financial_transactions (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source_transaction_id TEXT NOT NULL,
    transaction_date DATE NOT NULL,
    transaction_time TEXT,
    transaction_type TEXT NOT NULL,
    category TEXT NOT NULL,
    subcategory TEXT,
    description TEXT,
    amount_krw INTEGER NOT NULL,
    cashflow_bucket TEXT,
    account_ref TEXT,
    api_ref TEXT,
    raw_json TEXT NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, source_transaction_id)
);

ALTER TABLE missions
    ADD COLUMN template_id TEXT,
    ADD COLUMN verification_type TEXT,
    ADD COLUMN evaluation_rule_json TEXT NOT NULL DEFAULT '{}',
    ADD COLUMN evaluation_period_start DATE,
    ADD COLUMN evaluation_period_end DATE,
    ADD COLUMN evaluated_at TIMESTAMPTZ,
    ADD COLUMN evaluation_status TEXT NOT NULL DEFAULT 'NOT_EVALUATED';

ALTER TABLE mission_events
    ADD COLUMN source TEXT NOT NULL DEFAULT 'SYSTEM',
    ADD COLUMN evaluation_result TEXT,
    ADD COLUMN evidence_json TEXT NOT NULL DEFAULT '{}';

CREATE INDEX idx_financial_transactions_user_date ON financial_transactions(user_id, transaction_date);
CREATE INDEX idx_financial_transactions_user_category ON financial_transactions(user_id, category);
CREATE UNIQUE INDEX idx_mission_events_done_once
    ON mission_events(mission_id, event_type)
    WHERE event_type = 'DONE';
