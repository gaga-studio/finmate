CREATE TABLE compare_groups (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  title TEXT NOT NULL,
  filters_json TEXT NOT NULL DEFAULT '{}',
  member_count INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE compare_group_members (
  group_id TEXT NOT NULL REFERENCES compare_groups(id) ON DELETE CASCADE,
  member_user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  rank_order INTEGER NOT NULL,
  PRIMARY KEY (group_id, member_user_id)
);

CREATE INDEX idx_compare_groups_user_created ON compare_groups(user_id, created_at DESC);
CREATE INDEX idx_compare_group_members_group_rank ON compare_group_members(group_id, rank_order);
