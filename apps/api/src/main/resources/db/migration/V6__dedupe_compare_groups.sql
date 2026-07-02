WITH ranked_groups AS (
  SELECT
    id,
    ROW_NUMBER() OVER (
      PARTITION BY user_id, filters_json
      ORDER BY updated_at DESC, created_at DESC, id DESC
    ) AS duplicate_rank
  FROM compare_groups
)
DELETE FROM compare_groups
WHERE id IN (
  SELECT id
  FROM ranked_groups
  WHERE duplicate_rank > 1
);

CREATE UNIQUE INDEX uq_compare_groups_user_filters
  ON compare_groups(user_id, filters_json);
