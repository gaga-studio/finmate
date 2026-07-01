ALTER TABLE mission_events
    ADD COLUMN event_date DATE;

UPDATE mission_events
SET event_date = CAST(created_at AS DATE)
WHERE event_date IS NULL;

ALTER TABLE mission_events
    ALTER COLUMN event_date SET DEFAULT DATE '2026-06-12',
    ALTER COLUMN event_date SET NOT NULL;

CREATE INDEX idx_mission_events_user_date ON mission_events(user_id, event_date);
