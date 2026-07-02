package com.gagastudio.finmate.api.product;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MissionEvaluationService {
    private static final LocalDate APP_TODAY = LocalDate.of(2026, 6, 12);
    private static final TypeReference<Map<String, Integer>> CATEGORY_MAP = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public MissionEvaluationService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void evaluateUserMissions(String userId) {
        List<MissionSeed> missions = jdbc.query("""
                        SELECT id, title, reward_points, progress
                        FROM missions
                        WHERE user_id = ? AND status <> 'COMPLETED'
                        ORDER BY created_at
                        """,
                (rs, rowNum) -> missionSeed(rs, userId), userId);
        for (MissionSeed mission : missions) {
            evaluateMission(userId, mission);
        }
    }

    @Transactional
    public void evaluateMission(String userId, String routeId) {
        List<MissionSeed> missions = jdbc.query("""
                        SELECT id, title, reward_points, progress
                        FROM missions
                        WHERE id = ? AND user_id = ? AND status <> 'COMPLETED'
                        """,
                (rs, rowNum) -> missionSeed(rs, userId), missionDbId(userId, routeId), userId);
        if (!missions.isEmpty()) {
            evaluateMission(userId, missions.get(0));
        }
    }

    private void evaluateMission(String userId, MissionSeed mission) {
        EvaluationResult result = resultFor(userId, mission);
        if (result.success()) {
            completeMission(userId, mission, result);
            return;
        }
        jdbc.update("""
                UPDATE missions
                SET progress = ?,
                    evaluated_at = now(),
                    evaluation_status = ?,
                    evaluation_rule_json = ?
                WHERE id = ? AND status <> 'COMPLETED'
                """, result.progress(), result.status(), json(result.evidence()), mission.dbId());
    }

    private EvaluationResult resultFor(String userId, MissionSeed mission) {
        String routeId = mission.routeId();
        String title = mission.title();
        if (routeId.contains("food") || title.contains("식비")) {
            return foodResult(userId);
        }
        if (routeId.contains("auto-transfer") || routeId.contains("saving") || title.contains("저축") || title.contains("비상금")) {
            return savingResult(userId);
        }
        if (routeId.contains("cafe") || title.contains("카페")) {
            return cafeResult(userId);
        }
        if (routeId.contains("transport") || title.contains("교통")) {
            return transportResult(userId);
        }
        if (routeId.contains("subscription") || routeId.contains("fixed-cost") || title.contains("구독") || title.contains("고정")) {
            return dataNeeded("데이터가 더 필요해요. 구독/고정비 미션은 반복 결제의 전월 대비 변화가 필요해요.", mission.progress());
        }
        return dataNeeded("이 미션을 자동 검증할 행동 데이터가 아직 충분하지 않아요.", mission.progress());
    }

    private EvaluationResult foodResult(String userId) {
        DailyRecord record = findDailyRecord(userId, APP_TODAY);
        if (record == null) {
            return dataNeeded("오늘 지출 기록이 아직 없어요.", 0);
        }
        int food = categoryAmount(record.categories(), "식비");
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("rule", "식비 10,000원 이하");
        evidence.put("date", APP_TODAY.toString());
        evidence.put("foodSpending", food);
        evidence.put("source", "daily_records.category_spending_json");
        return food <= 10000
                ? success("식비가 10,000원 이하로 확인됐어요.", evidence)
                : inProgress(Math.max(5, Math.min(95, 100 - (food - 10000) / 200)), "식비가 아직 목표보다 높아요.", evidence);
    }

    private EvaluationResult savingResult(String userId) {
        int monthlySaving = monthlySaving(userId);
        int transferSaving = Math.abs(transactionSum(userId, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1), "저축", null));
        int evidenceAmount = Math.max(monthlySaving, transferSaving);
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("rule", "월 저축 또는 비상금 이체 100,000원 이상");
        evidence.put("monthlySaving", monthlySaving);
        evidence.put("savingTransferAmount", transferSaving);
        evidence.put("source", "financial_snapshots + financial_transactions");
        return evidenceAmount >= 100000
                ? success("저축/비상금 행동 데이터가 100,000원 이상으로 확인됐어요.", evidence)
                : inProgress(Math.min(95, percent(evidenceAmount, 100000)), "저축 데이터가 목표 금액에 아직 부족해요.", evidence);
    }

    private EvaluationResult cafeResult(String userId) {
        LocalDate from = APP_TODAY.minusDays(4);
        LocalDate to = APP_TODAY.plusDays(3);
        int count = transactionCount(userId, from, to, "카페/간식");
        int totalTransactions = transactionCount(userId, from, to, null);
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("rule", "이번 주 카페/간식 결제 2회 이하");
        evidence.put("from", from.toString());
        evidence.put("to", to.minusDays(1).toString());
        evidence.put("cafeTransactionCount", count);
        evidence.put("source", "financial_transactions");
        if (totalTransactions == 0) {
            return dataNeeded("이번 주 거래 원장이 아직 없어 카페 횟수를 확인할 수 없어요.", 0);
        }
        return count <= 2
                ? success("이번 주 카페/간식 결제가 2회 이하로 확인됐어요.", evidence)
                : inProgress(Math.max(10, 100 - ((count - 2) * 20)), "카페/간식 결제 횟수가 아직 목표보다 많아요.", evidence);
    }

    private EvaluationResult transportResult(String userId) {
        DailyRecord record = findDailyRecord(userId, APP_TODAY);
        if (record == null) {
            return dataNeeded("오늘 교통비 기록이 아직 없어요.", 0);
        }
        int transport = categoryAmount(record.categories(), "교통비");
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("rule", "교통비 하루 3,000원 이하");
        evidence.put("date", APP_TODAY.toString());
        evidence.put("transportSpending", transport);
        evidence.put("source", "daily_records.category_spending_json");
        return transport <= 3000
                ? success("오늘 교통비가 하루 예산 안으로 확인됐어요.", evidence)
                : inProgress(Math.max(5, Math.min(95, 100 - (transport - 3000) / 100)), "교통비가 아직 목표보다 높아요.", evidence);
    }

    private EvaluationResult success(String message, Map<String, Object> evidence) {
        evidence.put("message", message);
        return new EvaluationResult(true, 100, "SUCCESS", evidence);
    }

    private EvaluationResult inProgress(int progress, String message, Map<String, Object> evidence) {
        evidence.put("message", message);
        return new EvaluationResult(false, progress, "IN_PROGRESS", evidence);
    }

    private EvaluationResult dataNeeded(String message, int progress) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("message", message);
        evidence.put("source", "insufficient_behavior_data");
        return new EvaluationResult(false, Math.max(0, Math.min(95, progress)), "DATA_NEEDED", evidence);
    }

    private void completeMission(String userId, MissionSeed mission, EvaluationResult result) {
        int updated = jdbc.update("""
                UPDATE missions
                SET status = 'COMPLETED',
                    progress = 100,
                    completed_at = COALESCE(completed_at, now()),
                    evaluated_at = now(),
                    evaluation_status = 'SUCCESS',
                    evaluation_rule_json = ?
                WHERE id = ? AND status <> 'COMPLETED'
                """, json(result.evidence()), mission.dbId());
        if (updated == 0) {
            return;
        }
        addPointsOnce(userId, mission);
        jdbc.update("""
                UPDATE daily_records
                SET mission_status = 'SUCCESS',
                    point_delta = point_delta + ?
                WHERE user_id = ? AND record_date = ?
                """, mission.rewardPoints(), userId, APP_TODAY);
        jdbc.update("""
                INSERT INTO mission_events (
                  id, mission_id, user_id, event_type, note, reward_points, event_date,
                  source, evaluation_result, evidence_json
                )
                VALUES (?, ?, ?, 'DONE', ?, ?, ?, 'DATA_EVALUATION', 'SUCCESS', ?)
                ON CONFLICT DO NOTHING
                """, "event-" + UUID.randomUUID(), mission.dbId(), userId,
                stringValue(result.evidence().get("message")), mission.rewardPoints(), APP_TODAY, json(result.evidence()));
    }

    private void addPointsOnce(String userId, MissionSeed mission) {
        Integer existing = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM point_transactions
                WHERE user_id = ? AND reference_type = 'MISSION' AND reference_id = ?
                """, Integer.class, userId, mission.routeId());
        if (existing != null && existing > 0) {
            return;
        }
        jdbc.update("UPDATE point_wallets SET point_balance = point_balance + ?, updated_at = now() WHERE user_id = ?", mission.rewardPoints(), userId);
        int balance = jdbc.queryForObject("SELECT point_balance FROM point_wallets WHERE user_id = ?", Integer.class, userId);
        jdbc.update("""
                INSERT INTO point_transactions (id, user_id, type, amount, balance_after, reference_type, reference_id, description)
                VALUES (?, ?, 'POINT', ?, ?, 'MISSION', ?, ?)
                """, "ptx-" + UUID.randomUUID(), userId, mission.rewardPoints(), balance, mission.routeId(), mission.title() + " 자동 검증 완료");
    }

    private MissionSeed missionSeed(ResultSet rs, String userId) throws java.sql.SQLException {
        String dbId = rs.getString("id");
        String prefix = userId + ":";
        String routeId = dbId.startsWith(prefix) ? dbId.substring(prefix.length()) : dbId;
        return new MissionSeed(dbId, routeId, rs.getString("title"), rs.getInt("reward_points"), rs.getInt("progress"));
    }

    private int monthlySaving(String userId) {
        List<Integer> rows = jdbc.query("""
                        SELECT monthly_saving FROM financial_snapshots
                        WHERE user_id = ? AND month = '2026-06'
                        """,
                (rs, rowNum) -> rs.getInt("monthly_saving"), userId);
        return rows.isEmpty() ? 0 : rows.get(0);
    }

    private DailyRecord findDailyRecord(String userId, LocalDate date) {
        List<DailyRecord> rows = jdbc.query("""
                        SELECT category_spending_json FROM daily_records
                        WHERE user_id = ? AND record_date = ?
                        """,
                (rs, rowNum) -> new DailyRecord(readJson(rs.getString("category_spending_json"))), userId, date);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private int transactionCount(String userId, LocalDate from, LocalDate to, String category) {
        if (category == null) {
            return count("""
                    SELECT COUNT(*) FROM financial_transactions
                    WHERE user_id = ? AND transaction_date >= ? AND transaction_date < ?
                    """, userId, from, to);
        }
        return count("""
                SELECT COUNT(*) FROM financial_transactions
                WHERE user_id = ? AND transaction_date >= ? AND transaction_date < ? AND category = ?
                """, userId, from, to, category);
    }

    private int transactionSum(String userId, LocalDate from, LocalDate to, String cashflowBucket, String category) {
        Integer value;
        if (category != null) {
            value = jdbc.queryForObject("""
                    SELECT COALESCE(SUM(amount_krw), 0) FROM financial_transactions
                    WHERE user_id = ? AND transaction_date >= ? AND transaction_date < ? AND category = ?
                    """, Integer.class, userId, from, to, category);
        } else {
            value = jdbc.queryForObject("""
                    SELECT COALESCE(SUM(amount_krw), 0) FROM financial_transactions
                    WHERE user_id = ? AND transaction_date >= ? AND transaction_date < ? AND cashflow_bucket = ?
                    """, Integer.class, userId, from, to, cashflowBucket);
        }
        return value == null ? 0 : value;
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private int categoryAmount(Map<String, Integer> categories, String category) {
        return categories.getOrDefault(category, 0);
    }

    private int percent(int value, int target) {
        if (target <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(100, (int) Math.round(value * 100.0 / target)));
    }

    private Map<String, Integer> readJson(String json) {
        try {
            return objectMapper.readValue(json, CATEGORY_MAP);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to parse category JSON", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize evidence JSON", exception);
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private String missionDbId(String userId, String routeId) {
        return userId + ":" + routeId;
    }

    private record MissionSeed(String dbId, String routeId, String title, int rewardPoints, int progress) {
    }

    private record DailyRecord(Map<String, Integer> categories) {
    }

    private record EvaluationResult(boolean success, int progress, String status, Map<String, Object> evidence) {
    }
}
