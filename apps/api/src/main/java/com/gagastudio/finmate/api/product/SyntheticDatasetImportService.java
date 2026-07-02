package com.gagastudio.finmate.api.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gagastudio.finmate.api.dto.ApiDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class SyntheticDatasetImportService {
    private static final String USER_ID_PREFIX = "synthetic-";
    private static final String DEFAULT_PRIVACY_VERSION = "synthetic-privacy-v1.6";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;
    private final MissionEvaluationService missionEvaluationService;

    public SyntheticDatasetImportService(JdbcTemplate jdbc, ObjectMapper objectMapper, PasswordEncoder passwordEncoder, MissionEvaluationService missionEvaluationService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.passwordEncoder = passwordEncoder;
        this.missionEvaluationService = missionEvaluationService;
    }

    @Transactional
    public DevSyntheticImportResponse importDataset(DevSyntheticImportRequest request) {
        if (Boolean.TRUE.equals(request.resetSynthetic())) {
            resetSyntheticUsers();
        }

        for (DevSyntheticUserPayload user : request.users()) {
            upsertBaseUser(user);
            clearImportedRuntime(userId(user.personaId()));
        }

        int snapshots = 0;
        int dailyRecords = 0;
        int transactions = 0;
        int missions = 0;
        for (DevSyntheticUserPayload user : request.users()) {
            upsertProfileAndConsent(user, request.importVersion());
            upsertSnapshot(user);
            snapshots += 1;
            dailyRecords += upsertDailyRecords(user);
            transactions += upsertTransactions(user);
            missions += upsertMissions(user);
            missionEvaluationService.evaluateUserMissions(userId(user.personaId()));
        }

        int friendships = 0;
        int feedItems = 0;
        int birthdayFunds = 0;
        for (DevSyntheticUserPayload user : request.users()) {
            friendships += upsertFriendships(user);
            feedItems += upsertFeedItems(user);
            if (user.birthdayFund() != null) {
                upsertBirthdayFund(user.birthdayFund());
                birthdayFunds += 1;
            }
        }

        return new DevSyntheticImportResponse(
                "IMPORTED",
                request.importVersion(),
                request.users().size(),
                request.users().size(),
                snapshots,
                dailyRecords,
                transactions,
                missions,
                friendships,
                feedItems,
                birthdayFunds
        );
    }

    private void resetSyntheticUsers() {
        jdbc.update("DELETE FROM birthday_fund_contributions WHERE fund_id IN (SELECT id FROM birthday_funds WHERE owner_user_id LIKE ?)", USER_ID_PREFIX + "%");
        jdbc.update("DELETE FROM birthday_funds WHERE owner_user_id LIKE ?", USER_ID_PREFIX + "%");
        jdbc.update("DELETE FROM users WHERE id LIKE ?", USER_ID_PREFIX + "%");
    }

    private void upsertBaseUser(DevSyntheticUserPayload user) {
        jdbc.update("""
                INSERT INTO users (id, email, password_hash, display_name, onboarding_completed)
                VALUES (?, ?, ?, ?, TRUE)
                ON CONFLICT (id) DO UPDATE SET
                  email = EXCLUDED.email,
                  password_hash = EXCLUDED.password_hash,
                  display_name = EXCLUDED.display_name,
                  onboarding_completed = TRUE,
                  updated_at = now()
                """, userId(user.personaId()), user.email(), passwordEncoder.encode(user.password()), user.displayName());
        jdbc.update("""
                INSERT INTO point_wallets (user_id, point_balance, virtual_money_balance)
                VALUES (?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE SET
                  point_balance = EXCLUDED.point_balance,
                  virtual_money_balance = EXCLUDED.virtual_money_balance,
                  updated_at = now()
                """, userId(user.personaId()), user.wallet().pointBalance(), user.wallet().virtualMoneyBalance());
    }

    private void clearImportedRuntime(String userId) {
        jdbc.update("DELETE FROM birthday_fund_contributions WHERE contributor_user_id = ?", userId);
        jdbc.update("DELETE FROM birthday_fund_contributions WHERE fund_id IN (SELECT id FROM birthday_funds WHERE owner_user_id = ?)", userId);
        jdbc.update("DELETE FROM birthday_funds WHERE owner_user_id = ?", userId);
        jdbc.update("DELETE FROM point_transactions WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM feed_items WHERE user_id = ? OR actor_user_id = ?", userId, userId);
        jdbc.update("DELETE FROM friendships WHERE follower_id = ? OR followee_id = ?", userId, userId);
        jdbc.update("DELETE FROM daily_records WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM financial_transactions WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM mission_events WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM missions WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM coach_results WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM financial_snapshots WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM consent_events WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM mydata_connections WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM onboarding_responses WHERE user_id = ?", userId);
    }

    private void upsertProfileAndConsent(DevSyntheticUserPayload user, String importVersion) {
        String userId = userId(user.personaId());
        DevSyntheticProfilePayload profile = user.profile();
        DevSyntheticPrivacyPayload privacy = user.privacy();
        DevSyntheticMyDataPayload mydata = user.mydata();
        jdbc.update("""
                INSERT INTO user_profiles (user_id, age_band, income_band, job_category, household_type, money_style, area, goal_type)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE SET
                  age_band = EXCLUDED.age_band,
                  income_band = EXCLUDED.income_band,
                  job_category = EXCLUDED.job_category,
                  household_type = EXCLUDED.household_type,
                  money_style = EXCLUDED.money_style,
                  area = EXCLUDED.area,
                  goal_type = EXCLUDED.goal_type,
                  updated_at = now()
                """, userId, profile.ageBand(), profile.incomeBand(), profile.jobCategory(), profile.householdType(),
                profile.moneyStyle(), profile.area(), profile.goalType());
        jdbc.update("""
                INSERT INTO privacy_settings (user_id, anonymous_portfolio_opt_in, friend_share_default, exposed_fields)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE SET
                  anonymous_portfolio_opt_in = EXCLUDED.anonymous_portfolio_opt_in,
                  friend_share_default = EXCLUDED.friend_share_default,
                  exposed_fields = EXCLUDED.exposed_fields,
                  updated_at = now()
                """, userId, privacy.anonymousPortfolioOptIn(), privacy.friendShareDefault(), String.join(",", privacy.exposedFields()));
        jdbc.update("""
                INSERT INTO onboarding_responses (
                  id, user_id, age_band, income_band, job_category, household_type,
                  money_style, area, goal_type, pain_point
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE SET
                  age_band = EXCLUDED.age_band,
                  income_band = EXCLUDED.income_band,
                  job_category = EXCLUDED.job_category,
                  household_type = EXCLUDED.household_type,
                  money_style = EXCLUDED.money_style,
                  area = EXCLUDED.area,
                  goal_type = EXCLUDED.goal_type,
                  pain_point = EXCLUDED.pain_point,
                  updated_at = now()
                """, "onboarding-" + userId, userId, profile.ageBand(), profile.incomeBand(), profile.jobCategory(),
                profile.householdType(), profile.moneyStyle(), profile.area(), profile.goalType(), profile.painPoint());
        jdbc.update("""
                INSERT INTO mydata_connections (id, user_id, connection_status, data_mode, consent_version, scopes)
                VALUES (?, ?, 'CONNECTED', 'SYNTHETIC_MYDATA', ?, ?)
                ON CONFLICT (user_id) DO UPDATE SET
                  connection_status = EXCLUDED.connection_status,
                  data_mode = EXCLUDED.data_mode,
                  consent_version = EXCLUDED.consent_version,
                  scopes = EXCLUDED.scopes,
                  updated_at = now()
                """, "mydata-" + userId, userId, mydata.consentVersion(), String.join(",", mydata.scopes()));
        insertConsentEvent(userId, "PRIVACY_SETTINGS", DEFAULT_PRIVACY_VERSION, "AGREED", "합성 사용자 공개 범위 import");
        insertConsentEvent(userId, "MYDATA_SYNTHETIC", mydata.consentVersion(), "AGREED", "합성 MyData " + importVersion + " import");
    }

    private void upsertSnapshot(DevSyntheticUserPayload user) {
        String userId = userId(user.personaId());
        DevSyntheticSnapshotPayload snapshot = user.snapshot();
        jdbc.update("""
                INSERT INTO financial_snapshots (
                  id, user_id, month, monthly_income, monthly_spending, monthly_saving,
                  investment_value, cash_like_assets, emergency_fund_months, categories_json, lifestyle_tags
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (user_id, month) DO UPDATE SET
                  monthly_income = EXCLUDED.monthly_income,
                  monthly_spending = EXCLUDED.monthly_spending,
                  monthly_saving = EXCLUDED.monthly_saving,
                  investment_value = EXCLUDED.investment_value,
                  cash_like_assets = EXCLUDED.cash_like_assets,
                  emergency_fund_months = EXCLUDED.emergency_fund_months,
                  categories_json = EXCLUDED.categories_json,
                  lifestyle_tags = EXCLUDED.lifestyle_tags
                """, "snapshot-" + userId, userId, snapshot.month(), snapshot.monthlyIncome(), snapshot.monthlySpending(),
                snapshot.monthlySaving(), snapshot.investmentValue(), snapshot.cashLikeAssets(), snapshot.emergencyFundMonths(),
                json(snapshot.spendingCategories()), String.join(",", snapshot.lifestyleTags()));
    }

    private int upsertDailyRecords(DevSyntheticUserPayload user) {
        String userId = userId(user.personaId());
        int count = 0;
        for (DevSyntheticDailyRecordPayload record : user.dailyRecords()) {
            jdbc.update("""
                    INSERT INTO daily_records (id, user_id, record_date, budget, spent, category_spending_json, mission_status, point_delta)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (user_id, record_date) DO UPDATE SET
                      budget = EXCLUDED.budget,
                      spent = EXCLUDED.spent,
                      category_spending_json = EXCLUDED.category_spending_json,
                      mission_status = EXCLUDED.mission_status,
                      point_delta = EXCLUDED.point_delta
                    """, "record-" + userId + "-" + record.recordDate(), userId, LocalDate.parse(record.recordDate()),
                    record.budget(), record.spent(), json(record.categorySpending()), record.missionStatus(), record.pointDelta());
            count += 1;
        }
        return count;
    }

    private int upsertTransactions(DevSyntheticUserPayload user) {
        String userId = userId(user.personaId());
        int count = 0;
        for (DevSyntheticTransactionPayload transaction : nullSafe(user.transactions())) {
            jdbc.update("""
                    INSERT INTO financial_transactions (
                      id, user_id, source_transaction_id, transaction_date, transaction_time,
                      transaction_type, category, subcategory, description, amount_krw,
                      cashflow_bucket, account_ref, api_ref, raw_json
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (user_id, source_transaction_id) DO UPDATE SET
                      transaction_date = EXCLUDED.transaction_date,
                      transaction_time = EXCLUDED.transaction_time,
                      transaction_type = EXCLUDED.transaction_type,
                      category = EXCLUDED.category,
                      subcategory = EXCLUDED.subcategory,
                      description = EXCLUDED.description,
                      amount_krw = EXCLUDED.amount_krw,
                      cashflow_bucket = EXCLUDED.cashflow_bucket,
                      account_ref = EXCLUDED.account_ref,
                      api_ref = EXCLUDED.api_ref,
                      raw_json = EXCLUDED.raw_json
                    """,
                    "txn-" + userId + "-" + transaction.transactionId(),
                    userId,
                    transaction.transactionId(),
                    LocalDate.parse(transaction.transactionDate()),
                    transaction.transactionTime(),
                    transaction.transactionType(),
                    transaction.category(),
                    transaction.subcategory(),
                    transaction.description(),
                    transaction.amountKrw(),
                    transaction.cashflowBucket(),
                    transaction.accountRef(),
                    transaction.apiRef(),
                    json(transaction));
            count += 1;
        }
        return count;
    }

    private int upsertMissions(DevSyntheticUserPayload user) {
        String userId = userId(user.personaId());
        int count = 0;
        for (DevSyntheticMissionPayload mission : user.missions()) {
            jdbc.update("""
                    INSERT INTO missions (id, user_id, title, description, status, difficulty, reward_points, progress, due_date, source)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, DATE '2026-06-30', ?)
                    ON CONFLICT (id) DO UPDATE SET
                      title = EXCLUDED.title,
                      description = EXCLUDED.description,
                      status = EXCLUDED.status,
                      difficulty = EXCLUDED.difficulty,
                      reward_points = EXCLUDED.reward_points,
                      progress = EXCLUDED.progress,
                      source = EXCLUDED.source
                    """, missionDbId(userId, mission.missionId()), userId, mission.title(), mission.description(), mission.status(),
                    mission.difficulty(), mission.rewardPoints(), mission.progress(), mission.source());
            count += 1;
        }
        return count;
    }

    private int upsertFriendships(DevSyntheticUserPayload user) {
        String userId = userId(user.personaId());
        int count = 0;
        for (String targetPersonaId : nullSafe(user.follows())) {
            String targetUserId = userId(targetPersonaId);
            if (!existsUser(targetUserId) || userId.equals(targetUserId)) {
                continue;
            }
            jdbc.update("""
                    INSERT INTO friendships (id, follower_id, followee_id, status)
                    VALUES (?, ?, ?, 'ACTIVE')
                    ON CONFLICT (follower_id, followee_id) DO NOTHING
                    """, "friendship-" + userId + "-" + targetUserId, userId, targetUserId);
            count += 1;
        }
        return count;
    }

    private int upsertFeedItems(DevSyntheticUserPayload user) {
        String userId = userId(user.personaId());
        int count = 0;
        for (DevSyntheticFeedPayload feed : nullSafe(user.feedItems())) {
            String actorUserId = userId(feed.actorPersonaId());
            if (!existsUser(actorUserId)) {
                continue;
            }
            jdbc.update("""
                    INSERT INTO feed_items (id, user_id, actor_user_id, kind, title, body, amount)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                      title = EXCLUDED.title,
                      body = EXCLUDED.body,
                      amount = EXCLUDED.amount
                    """, feed.feedId(), userId, actorUserId, feed.kind(), feed.title(), feed.body(), feed.amount());
            count += 1;
        }
        return count;
    }

    private void upsertBirthdayFund(DevSyntheticBirthdayFundPayload fund) {
        String ownerUserId = userId(fund.ownerPersonaId());
        if (!existsUser(ownerUserId)) {
            return;
        }
        jdbc.update("""
                INSERT INTO birthday_funds (id, owner_user_id, title, target_amount, current_amount, due_date, status, share_code)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                  owner_user_id = EXCLUDED.owner_user_id,
                  title = EXCLUDED.title,
                  target_amount = EXCLUDED.target_amount,
                  current_amount = EXCLUDED.current_amount,
                  due_date = EXCLUDED.due_date,
                  status = EXCLUDED.status,
                  share_code = EXCLUDED.share_code,
                  updated_at = now()
                """, fund.fundId(), ownerUserId, fund.title(), fund.targetAmount(), fund.currentAmount(),
                LocalDate.parse(fund.dueDate()), fund.status(), fund.shareCode());
    }

    private boolean existsUser(String userId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, userId);
        return count != null && count > 0;
    }

    private void insertConsentEvent(String userId, String consentItem, String consentVersion, String status, String summary) {
        jdbc.update("""
                INSERT INTO consent_events (id, user_id, consent_item, consent_version, status, summary)
                VALUES (?, ?, ?, ?, ?, ?)
                """, "consent-" + userId + "-" + consentItem.toLowerCase() + "-" + UUID.randomUUID(), userId, consentItem, consentVersion, status, summary);
    }

    private String userId(String personaId) {
        return USER_ID_PREFIX + personaId;
    }

    private String missionDbId(String userId, String missionId) {
        return userId + ":" + missionId;
    }

    private <T> List<T> nullSafe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize JSON", exception);
        }
    }
}
