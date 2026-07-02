package com.gagastudio.finmate.api.product;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gagastudio.finmate.api.dto.ApiDtos.*;
import com.gagastudio.finmate.api.error.ApiException;
import com.gagastudio.finmate.api.error.FieldErrorDetail;
import com.gagastudio.finmate.api.store.SeedStore;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ProductAppService implements FinancialDataProvider {
    private static final LocalDate APP_TODAY = LocalDate.of(2026, 6, 12);
    private static final YearMonth DEFAULT_RECORD_MONTH = YearMonth.of(2026, 6);
    private static final TypeReference<Map<String, Integer>> CATEGORY_MAP = new TypeReference<>() {
    };
    private static final TypeReference<List<CoachInsightV1>> INSIGHT_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<CoachRecommendationV1>> RECOMMENDATION_LIST = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final CoachProvider coachProvider;
    private final SeedStore seedStore;
    private final MissionEvaluationService missionEvaluationService;

    public ProductAppService(JdbcTemplate jdbc, ObjectMapper objectMapper, CoachProvider coachProvider, SeedStore seedStore, MissionEvaluationService missionEvaluationService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.coachProvider = coachProvider;
        this.seedStore = seedStore;
        this.missionEvaluationService = missionEvaluationService;
    }

    @Transactional
    public void bootstrapUser(String userId, String displayName) {
        jdbc.update("""
                INSERT INTO users (id, email, password_hash, display_name, onboarding_completed)
                VALUES (?, ?, 'seed-user', ?, TRUE)
                ON CONFLICT (id) DO NOTHING
                """, userId, userId + "@finmate.local", displayName);
        jdbc.update("""
                INSERT INTO user_profiles (user_id)
                VALUES (?)
                ON CONFLICT (user_id) DO NOTHING
                """, userId);
        jdbc.update("""
                INSERT INTO privacy_settings (user_id, anonymous_portfolio_opt_in, friend_share_default, exposed_fields)
                VALUES (?, TRUE, 'MISSION_ONLY', 'ageBand,goalType,financialSummary')
                ON CONFLICT (user_id) DO NOTHING
                """, userId);
        jdbc.update("""
                INSERT INTO point_wallets (user_id, point_balance, virtual_money_balance)
                VALUES (?, 0, 100000)
                ON CONFLICT (user_id) DO NOTHING
                """, userId);
    }

    private void clearUserRuntimeData(String userId) {
        jdbc.update("DELETE FROM birthday_fund_contributions WHERE contributor_user_id = ?", userId);
        jdbc.update("DELETE FROM point_transactions WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM feed_items WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM friendships WHERE follower_id = ?", userId);
        jdbc.update("DELETE FROM daily_records WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM financial_transactions WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM mission_events WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM missions WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM coach_results WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM financial_snapshots WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM birthday_funds WHERE owner_user_id = ?", userId);
    }

    @Transactional
    public void resetDevelopmentState() {
        jdbc.execute("""
                TRUNCATE TABLE
                  birthday_fund_contributions,
                  birthday_funds,
                  point_transactions,
                  point_wallets,
                  feed_items,
                  friendships,
                  financial_transactions,
                  daily_records,
                  mission_events,
                  missions,
                  coach_results,
                  financial_snapshots,
                  consent_events,
                  mydata_connections,
                  onboarding_responses,
                  privacy_settings,
                  user_profiles,
                  refresh_tokens,
                  users
                RESTART IDENTITY CASCADE
                """);
    }

    @Transactional
    public UserMeResponse completeOnboarding(String userId, ProductOnboardingRequest request) {
        bootstrapUser(userId, displayName(userId));
        String exposedFields = String.join(",", request.privacyConsent().exposedFields());
        String mydataScopes = String.join(",", request.mydataConsent().mydataScopes());

        jdbc.update("""
                UPDATE user_profiles
                SET age_band = ?,
                    income_band = ?,
                    job_category = ?,
                    household_type = ?,
                    money_style = ?,
                    area = ?,
                    goal_type = ?,
                    updated_at = now()
                WHERE user_id = ?
                """, request.ageBand(), request.incomeBand(), request.jobCategory(), request.householdType(),
                request.moneyStyle(), request.area(), request.goalType(), userId);
        jdbc.update("""
                INSERT INTO privacy_settings (user_id, anonymous_portfolio_opt_in, friend_share_default, exposed_fields)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE SET
                  anonymous_portfolio_opt_in = EXCLUDED.anonymous_portfolio_opt_in,
                  friend_share_default = EXCLUDED.friend_share_default,
                  exposed_fields = EXCLUDED.exposed_fields,
                  updated_at = now()
                """, userId, request.privacyConsent().anonymousPortfolioOptIn(),
                request.privacyConsent().friendShareDefault(), exposedFields);
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
                """, "onboarding-" + userId, userId, request.ageBand(), request.incomeBand(), request.jobCategory(),
                request.householdType(), request.moneyStyle(), request.area(), request.goalType(), request.painPoint());
        jdbc.update("""
                INSERT INTO mydata_connections (id, user_id, connection_status, data_mode, consent_version, scopes)
                VALUES (?, ?, 'CONNECTED', 'SYNTHETIC_MYDATA', ?, ?)
                ON CONFLICT (user_id) DO UPDATE SET
                  connection_status = EXCLUDED.connection_status,
                  data_mode = EXCLUDED.data_mode,
                  consent_version = EXCLUDED.consent_version,
                  scopes = EXCLUDED.scopes,
                  updated_at = now()
                """, "mydata-" + userId, userId, request.mydataConsent().mydataConsentVersion(), mydataScopes);
        insertConsentEvent(userId, "PRIVACY_SETTINGS", request.privacyConsent().privacyConsentVersion(), "AGREED",
                "익명 포트폴리오와 친구 피드 공개 범위에 동의");
        insertConsentEvent(userId, "MYDATA_SYNTHETIC", request.mydataConsent().mydataConsentVersion(), "AGREED",
                "합성/샘플 금융 데이터 기반 연결 범위에 동의");
        jdbc.update("UPDATE users SET onboarding_completed = TRUE, updated_at = now() WHERE id = ?", userId);
        upsertOnboardingStarterState(userId, request);
        return userMe(userId);
    }

    public UserMeResponse userMe(String userId) {
        bootstrapUser(userId, displayName(userId));
        return jdbc.queryForObject("""
                SELECT u.id, u.email, u.display_name, u.onboarding_completed,
                       COALESCE(w.point_balance, 0) AS point_balance,
                       COALESCE(w.virtual_money_balance, 0) AS virtual_money_balance
                FROM users u
                LEFT JOIN point_wallets w ON w.user_id = u.id
                WHERE u.id = ?
                """, (rs, rowNum) -> new UserMeResponse(
                rs.getString("id"),
                rs.getString("email"),
                rs.getString("display_name"),
                rs.getBoolean("onboarding_completed"),
                rs.getInt("point_balance"),
                rs.getInt("virtual_money_balance")
        ), userId);
    }

    private void upsertOnboardingStarterState(String userId, ProductOnboardingRequest request) {
        if (userId.startsWith("synthetic-")) {
            return;
        }

        StarterRoutine routine = starterRoutine(request);
        jdbc.update("""
                INSERT INTO financial_snapshots (
                  id, user_id, month, monthly_income, monthly_spending, monthly_saving,
                  investment_value, cash_like_assets, emergency_fund_months, categories_json, lifestyle_tags
                )
                VALUES (?, ?, '2026-06', ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (user_id, month) DO UPDATE SET
                  monthly_income = EXCLUDED.monthly_income,
                  monthly_spending = EXCLUDED.monthly_spending,
                  monthly_saving = EXCLUDED.monthly_saving,
                  investment_value = EXCLUDED.investment_value,
                  cash_like_assets = EXCLUDED.cash_like_assets,
                  emergency_fund_months = EXCLUDED.emergency_fund_months,
                  categories_json = EXCLUDED.categories_json,
                  lifestyle_tags = EXCLUDED.lifestyle_tags
                """, "snapshot-" + userId, userId, routine.monthlyIncome(), routine.monthlySpending(),
                routine.monthlySaving(), routine.investmentValue(), routine.cashLikeAssets(), routine.emergencyFundMonths(),
                json(routine.monthlyCategories()), String.join(",", routine.lifestyleTags()));

        jdbc.update("""
                INSERT INTO daily_records (id, user_id, record_date, budget, spent, category_spending_json, mission_status, point_delta)
                VALUES (?, ?, DATE '2026-06-12', ?, ?, ?, 'IN_PROGRESS', 0)
                ON CONFLICT (user_id, record_date) DO UPDATE SET
                  budget = EXCLUDED.budget,
                  spent = EXCLUDED.spent,
                  category_spending_json = EXCLUDED.category_spending_json,
                  mission_status = CASE
                    WHEN daily_records.mission_status = 'SUCCESS' THEN daily_records.mission_status
                    ELSE EXCLUDED.mission_status
                  END
                """, "record-" + userId + "-2026-06-12", userId, routine.dailyBudget(), routine.dailySpent(), json(routine.dailyCategories()));

        upsertStarterMission(userId, "mission-food", "내일 식비 10,000원 이하 사용하기", "하루 식비를 낮춰 남는 금액을 비상금으로 옮겨요.", "EASY", 120, routine.foodMissionProgress());
        upsertStarterMission(userId, "mission-saving", "저축하기 습관 만들기", "이번 주 남는 금액을 비상금으로 따로 모아봐요.", "EASY", 200, routine.savingMissionProgress());
        upsertStarterMission(userId, "mission-fixed-cost", "고정 지출 5% 줄이기", "구독과 반복 결제를 점검해 다음 달 현금흐름을 가볍게 만들어요.", "NORMAL", 180, routine.fixedCostMissionProgress());
        if ("START_INVESTING".equals(request.painPoint()) || request.moneyStyle().contains("투자")) {
            upsertStarterMission(userId, "mission-invest", "투자 비중 점검하기", "이번 달 매수 금액과 현금 비중을 함께 확인해요.", "NORMAL", 150, 15);
        }
    }

    private void upsertStarterMission(String userId, String missionId, String title, String description, String difficulty, int rewardPoints, int progress) {
        jdbc.update("""
                INSERT INTO missions (id, user_id, title, description, status, difficulty, reward_points, progress, due_date, source)
                VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, ?, DATE '2026-06-30', 'ONBOARDING_STARTER')
                ON CONFLICT (id) DO UPDATE SET
                  title = EXCLUDED.title,
                  description = EXCLUDED.description,
                  difficulty = EXCLUDED.difficulty,
                  reward_points = EXCLUDED.reward_points,
                  progress = EXCLUDED.progress,
                  source = EXCLUDED.source
                WHERE missions.status <> 'COMPLETED'
                """, missionDbId(userId, missionId), userId, title, description, difficulty, rewardPoints, progress);
    }

    private StarterRoutine starterRoutine(ProductOnboardingRequest request) {
        int monthlyIncome = starterIncome(request.incomeBand());
        double spendingRate = starterSpendingRate(request);
        int monthlySpending = roundTo((int) Math.round(monthlyIncome * spendingRate), 10000);
        int investmentValue = starterInvestmentValue(request);
        int monthlySaving = Math.max(120000, roundTo(monthlyIncome - monthlySpending - investmentValue / 8, 10000));
        int cashLikeAssets = Math.max(300000, roundTo((int) Math.round(monthlySaving * 1.35), 10000));
        double emergencyFundMonths = Math.round((cashLikeAssets * 100.0) / Math.max(monthlySpending, 1)) / 100.0;

        int dailyBudget = Math.max(12000, roundTo(monthlySpending / 60, 1000));
        int dailySpent = Math.min(dailyBudget, Math.max(6000, roundTo((int) Math.round(dailyBudget * starterDailySpendRate(request)), 100)));
        Map<String, Integer> dailyCategories = splitCategories(dailySpent, request);
        Map<String, Integer> monthlyCategories = splitCategories(monthlySpending, request);
        List<String> tags = starterTags(request);

        return new StarterRoutine(
                monthlyIncome,
                monthlySpending,
                monthlySaving,
                investmentValue,
                cashLikeAssets,
                emergencyFundMonths,
                dailyBudget,
                dailySpent,
                dailyCategories,
                monthlyCategories,
                tags,
                "CONTROL_SPENDING".equals(request.painPoint()) ? 18 : 34,
                "SAVE_CONSISTENTLY".equals(request.painPoint()) ? 42 : 24,
                "1인가구".equals(request.householdType()) ? 22 : 35
        );
    }

    private int starterIncome(String incomeBand) {
        if (incomeBand.contains("2,000")) {
            return 2100000;
        }
        if (incomeBand.contains("4,000")) {
            return 3750000;
        }
        return 2900000;
    }

    private double starterSpendingRate(ProductOnboardingRequest request) {
        double rate = 0.68;
        if ("1인가구".equals(request.householdType())) {
            rate += 0.07;
        } else if ("가족과 거주".equals(request.householdType())) {
            rate -= 0.12;
        } else if (request.householdType().contains("룸메이트")) {
            rate -= 0.04;
        }
        if ("CONTROL_SPENDING".equals(request.painPoint())) {
            rate += 0.06;
        } else if ("SAVE_CONSISTENTLY".equals(request.painPoint())) {
            rate -= 0.03;
        }
        if (request.moneyStyle().contains("투자")) {
            rate -= 0.02;
        }
        return Math.max(0.48, Math.min(0.82, rate));
    }

    private int starterInvestmentValue(ProductOnboardingRequest request) {
        if ("START_INVESTING".equals(request.painPoint()) || request.moneyStyle().contains("투자")) {
            return 520000;
        }
        if ("TRAVEL".equals(request.goalType())) {
            return 80000;
        }
        return 160000;
    }

    private double starterDailySpendRate(ProductOnboardingRequest request) {
        if ("CONTROL_SPENDING".equals(request.painPoint())) {
            return 0.92;
        }
        if ("가족과 거주".equals(request.householdType())) {
            return 0.68;
        }
        return 0.78;
    }

    private Map<String, Integer> splitCategories(int total, ProductOnboardingRequest request) {
        double foodRate = "CONTROL_SPENDING".equals(request.painPoint()) ? 0.48 : 0.42;
        double cafeRate = "CONTROL_SPENDING".equals(request.painPoint()) ? 0.20 : 0.12;
        double transportRate = request.area().contains("수도권") ? 0.24 : 0.18;
        int food = roundTo((int) Math.round(total * foodRate), 100);
        int transport = roundTo((int) Math.round(total * transportRate), 100);
        int cafe = roundTo((int) Math.round(total * cafeRate), 100);
        int other = Math.max(0, total - food - transport - cafe);
        Map<String, Integer> categories = new LinkedHashMap<>();
        categories.put("식비", food);
        categories.put("교통비", transport);
        categories.put("카페/간식", cafe);
        categories.put("기타", other);
        return categories;
    }

    private List<String> starterTags(ProductOnboardingRequest request) {
        List<String> tags = new ArrayList<>();
        tags.add(request.goalType());
        tags.add(request.moneyStyle());
        tags.add(request.painPoint());
        return tags;
    }

    private int roundTo(int value, int unit) {
        return Math.round(value / (float) unit) * unit;
    }

    @Override
    public FinancialSnapshotV1 snapshotFor(String userId) {
        bootstrapUser(userId, displayName(userId));
        SnapshotRow snapshot = findSnapshot(userId);
        if (snapshot == null) {
            return new FinancialSnapshotV1(
                    "snapshot-empty-" + userId,
                    userId,
                    "2026-06",
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    Map.of(),
                    List.of("데이터 대기")
            );
        }
        return new FinancialSnapshotV1(
                snapshot.id(),
                userId,
                snapshot.month(),
                snapshot.monthlyIncome(),
                snapshot.monthlySpending(),
                snapshot.monthlySaving(),
                snapshot.investmentValue(),
                snapshot.cashLikeAssets(),
                snapshot.emergencyFundMonths(),
                readJson(snapshot.categoriesJson(), CATEGORY_MAP),
                List.of(snapshot.lifestyleTags().split(","))
        );
    }

    @Transactional
    public CoachResultV1 fallbackCoach(String userId) {
        if (findSnapshot(userId) == null) {
            return emptyCoach(userId);
        }
        CoachResultV1 result = coachProvider.coach(snapshotFor(userId));
        storeCoachResult(userId, result);
        return result;
    }

    @Transactional
    public CoachResultV1 storeCoachResult(String userId, CoachResultV1 result) {
        jdbc.update("""
                INSERT INTO coach_results (id, user_id, snapshot_id, source, score, confidence, summary, insights_json, recommendations_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """, result.resultId(), userId, result.snapshotId(), result.source(), result.score(), result.confidence(), result.summary(), json(result.insights()), json(result.recommendations()));
        return result;
    }

    public AppScreenResponse getHome(String userId) {
        bootstrapUser(userId, displayName(userId));
        UserMeResponse user = userMe(userId);
        DailyRecordRow record = findDailyRecord(userId);
        SnapshotRow snapshot = findSnapshot(userId);
        MissionRow mission = todayMission(missions(userId));
        FundRow birthdayFund = availableBirthdayFund(userId);
        List<AppSection> sections = new ArrayList<>();
        sections.add(section("greeting", "greeting", user.displayName() + "님, 좋은 아침이에요!", "하루 30초, 나의 금융 습관을 확인하고 한 걸음 더 성장해요.", null, null, null, null, null, null));
        sections.add(mission == null
                ? emptyActionSection("mission-empty", "오늘의 미션", "아직 진행 중인 미션이 없어요.", "비교와 기록이 쌓이면 나에게 맞는 실천 목표를 만들 수 있어요.", "/missions")
                : missionHero(mission));
        sections.add(record == null
                ? emptyActionSection("budget-empty", "오늘의 예산", "오늘 예산을 아직 등록하지 않았어요.", "기록 탭에서 하루 예산과 사용 금액을 확인해보세요.", "/records")
                : budgetSection(record));
        if (birthdayFund != null) {
            sections.add(section("birthday-alert", "actionCard", birthdayFund.title() + "가 열렸어요", "친구들이 함께 모으는 생일 펀드가 열렸어요.", "/birthdays/" + birthdayFund.id(), "/assets/characters/finmate-birthday.png",
                    metrics(metric("현재 모금", won(birthdayFund.currentAmount()), "목표 " + won(birthdayFund.targetAmount()), "green", percent(birthdayFund.currentAmount(), birthdayFund.targetAmount())),
                            metric("남은 기간", "D-7", birthdayFund.dueDate().toString(), "purple", null)),
                    null, actions(action("축하 펀드 참여하기", "/birthday-funds/" + birthdayFund.id() + "/contribute", "GET", "primary", null)), null));
        }
        sections.add(record == null
                ? emptyActionSection("spending-empty", "오늘의 지출 요약", "오늘 지출 기록이 아직 없어요.", "지출이 기록되면 식비, 교통비, 카페/간식 비중을 바로 볼 수 있어요.", "/records")
                : spendingSection(record));
        sections.add(snapshot == null
                ? emptyActionSection("asset-empty", "자산 현황", "연결된 자산 데이터가 없어요.", "마이데이터 연결 또는 샘플 데이터가 준비되면 자산 흐름을 보여드릴게요.", "/profile")
                : assetSection(snapshot));
        sections.add(followingSection(userId));
        return screen("home", "", "home", sections, Map.of("version", "product-mvp"));
    }

    public AppScreenResponse getHomeDetail(String userId, String detail) {
        return switch (detail) {
            case "budget" -> screen("home:budget", "오늘의 예산", "home", List.of(recordOrEmpty(userId, "budget")), Map.of());
            case "spending" -> screen("home:spending", "오늘의 지출 요약", "home", List.of(recordOrEmpty(userId, "spending")), Map.of());
            case "assets" -> screen("home:assets", "자산 현황", "home", List.of(snapshotOrEmpty(userId)), Map.of());
            case "following" -> screen("home:following", "팔로잉 금융 근황", "home", List.of(followingSection(userId), feedSection(userId)), Map.of());
            case "mission" -> {
                MissionRow mission = todayMission(missions(userId));
                yield mission == null ? getMissions(userId) : getMission(userId, mission.routeId());
            }
            default -> throw validation("detail", "Unsupported home detail.");
        };
    }

    public AppScreenResponse getCompare(String userId) {
        if (findSnapshot(userId) == null) {
            return screen("compare", "그룹 비교", "compare", List.of(
                    section("lead", "lead", "비교할 금융 데이터가 아직 없어요", "가계부나 마이데이터 요약이 준비되면 비슷한 또래와 비교할 수 있어요.", null, null, null, null, null, null),
                    emptyActionSection("compare-empty", "또래 비교 준비 중", "아직 비교 기준이 부족해요.", "기록과 자산 요약이 쌓이면 금융 점수와 항목별 차이를 보여드릴게요.", "/home")
            ), Map.of("comparisonId", "empty"));
        }
        CoachResultV1 coach = latestOrFallbackCoach(userId);
        return screen("compare", "그룹 비교", "compare", List.of(
                section("lead", "lead", "비슷한 사람들과 비교해보세요", "나와 비슷한 금융 생활을 가진 사람들의 평균과 비교할 수 있어요.", null, null, null, null, null, null),
                section("score", "scoreGrid", "금융 점수", null, null, null,
                        metrics(metric("나의 금융 점수", coach.score() + "점", "상위 62%", "purple", coach.score()),
                                metric("비교 그룹 평균", "68점", "상위 48%", "green", 68)),
                        null, null, null),
                compareBarsSection(coach),
                section("cta", "actionCard", "비교 그룹을 바꿔볼까요?", "업종, 소득, 생활권 기준으로 더 가까운 그룹을 찾아요.", "/compare/filter", null, null, null,
                        actions(action("비교 그룹 변경하기", "/compare/filter", "GET", "primary", null)), null)
        ), Map.of("comparisonId", "cmp-001"));
    }

    public AppScreenResponse getCompareFilter(String userId) {
        bootstrapUser(userId, displayName(userId));
        return screen("compare:filter", "비교 그룹 선택", "compare", List.of(
                section("filters", "list", "필터 선택", null, null, null, null,
                        items(item("age", "나이", "20대", null, null, "profile", "purple", null),
                                item("income", "연 소득", "3,000만원 ~ 4,000만원", null, null, "wallet", "green", null),
                                item("job", "업종", "IT/개발", null, null, "work", "purple", null),
                                item("style", "금융 성향", "안정 추구형", null, null, "saving", "green", null),
                                item("area", "생활권", "서울 강남권", null, null, "home", "purple", null)),
                        actions(action("이 조건으로 비교하기", "/compare/filter/results", "POST", "primary", "compare-search")), null)
        ), Map.of());
    }

    public AppScreenResponse searchCompareFilter(String userId, AppCompareSearchRequest request) {
        return screen("compare:filter-results", "필터링 조회", "compare", List.of(
                section("summary", "lead", "이 조건에 맞는 사용자 1,246명", "선택한 조건과 가까운 익명 프로필을 골랐어요.", null, null, null, null, null, null),
                section("profiles", "list", "프로필 리스트", null, null, null, null,
                        items(item("p1", "코딩하는 제이", "27세 · IT 개발자 · 금융 점수 74점", null, "적금·연금", "profile", "purple", "/compare/result"),
                                item("p2", "미니멀 라이프", "29세 · 디자이너 · 금융 점수 70점", null, "저축·소비절제", "profile", "green", "/compare/result"),
                                item("p3", "퇴근 후 투자", "26세 · 마케터 · 금융 점수 76점", null, "주식·펀드", "profile", "orange", "/compare/result")),
                        actions(action("이 그룹으로 비교하기", "/compare/result", "GET", "primary", null)), null)
        ), Map.of("filters", request));
    }

    public AppScreenResponse getCompareResult(String userId, String comparisonId) {
        if (findSnapshot(userId) == null) {
            return screen("compare:" + comparisonId, "비교 결과", "compare", List.of(
                    emptyActionSection("compare-result-empty", "비교 결과가 아직 없어요", "금융 데이터가 준비되면 또래와의 차이를 보여드릴게요.", "지금은 홈에서 연결 상태를 먼저 확인해주세요.", "/home")
            ), Map.of("comparisonId", comparisonId));
        }
        CoachResultV1 coach = latestOrFallbackCoach(userId);
        return screen("compare:cmp-001", "비교 결과", "compare", List.of(
                section("result", "coach", userName(userId) + "님, 또래와 비교해봤어요!", coach.summary(), null, "/assets/characters/finmate-main.png",
                        metrics(metric("나의 종합 점수", coach.score() + "점", "상위 40%", "purple", coach.score())), null,
                        actions(action("AI 코치의 분석 보기", "/compare/coach", "GET", "primary", null)), null),
                compareBarsSection(coach)
        ), Map.of("comparisonId", comparisonId));
    }

    public AppScreenResponse getCoachFlow(String userId, String comparisonId) {
        if (findSnapshot(userId) == null) {
            return screen("compare:coach-flow", "AI 코치 분석", "compare", List.of(
                    emptyActionSection("coach-empty", "AI 코치가 기다리고 있어요", "아직 분석할 금융 흐름이 충분하지 않아요.", "기록과 자산 요약이 준비되면 추천 행동을 만들 수 있어요.", "/home")
            ), Map.of("comparisonId", comparisonId));
        }
        CoachResultV1 coach = latestOrFallbackCoach(userId);
        List<AppItem> insightItems = coach.insights().stream()
                .map(insight -> item(insight.type(), insight.title(), insight.body(), null, null, "check", insight.tone(), null))
                .toList();
        List<AppItem> recommendationItems = coach.recommendations().stream()
                .map(recommendation -> item(recommendation.missionTemplateId(), recommendation.title(), recommendation.body(), null, "+" + recommendation.rewardPoints() + "P", "target", "purple", "/missions/" + recommendation.missionTemplateId()))
                .toList();
        return screen("compare:coach-flow", "AI 코치 분석", "compare", "/assets/characters/finmate-coach.png", List.of(
                section("summary", "coach", userName(userId) + "님, 잘하고 있어요!", coach.summary(), null, "/assets/characters/finmate-coach.png", null, null, null, null),
                section("insights", "checkList", "핵심 요약", null, null, null, null, insightItems, null, null),
                section("actions", "actionList", "추천 행동 TOP 3", null, null, null, null, recommendationItems,
                        actions(action("계획 세우기", "/missions/mission-food", "GET", "primary", null)), null)
        ), Map.of("comparisonId", comparisonId));
    }

    public AppScreenResponse getMissions(String userId) {
        missionEvaluationService.evaluateUserMissions(userId);
        List<MissionRow> missions = missions(userId);
        List<AppSection> sections = new ArrayList<>();
        MissionRow todayMission = todayMission(missions);
        sections.add(todayMission == null
                ? emptyActionSection("mission-empty", "오늘의 미션이 아직 없어요", "비교와 기록이 쌓이면 맞춤 미션을 만들 수 있어요.", "지금은 홈에서 데이터 연결 상태를 확인해주세요.", "/home")
                : missionHero(todayMission));
        sections.add(section("active", "list", "진행 중인 미션", null, null, null, null,
                missions.stream()
                        .filter(mission -> !"COMPLETED".equals(mission.status()))
                        .filter(mission -> todayMission == null || !mission.dbId().equals(todayMission.dbId()))
                        .map(this::missionItem)
                        .toList(),
                null, null));
        sections.add(section("completed", "list", "완료된 미션", null, null, null, null,
                missions.stream().filter(mission -> "COMPLETED".equals(mission.status())).map(this::completedMissionItem).toList(),
                null, null));
        sections.add(section("add", "list", "미션 추가", "내 기록과 비슷한 또래 루틴에서 추천한 미션을 더해보세요.", null, null, null,
                items(item("mission-add", "추천 미션 보기", "아직 추가하지 않은 행동 목표를 확인해요.", null, "맞춤 추천", "target", "purple", "/missions/add")),
                actions(action("미션 추가하기", "/missions/add", "GET", "primary", null)), null));
        sections.add(section("points", "points", "나의 포인트", null, null, null,
                metrics(metric("보유 포인트", wallet(userId).pointBalance() + "P", "행동 데이터 검증 시 자동 적립", "purple", 62)), null, null, null));
        return screen("missions", "미션", "mission", sections, Map.of());
    }

    public AppScreenResponse getMission(String userId, String missionId) {
        if ("next-goals".equals(missionId)) {
            List<AppItem> nextMissionItems = missions(userId).stream()
                    .filter(mission -> !"COMPLETED".equals(mission.status()))
                    .map(this::missionItem)
                    .toList();
            return screen("missions:next-goals", "다음 목표 제안", "mission", "/assets/characters/finmate-growth.png", List.of(
                    section("coach", "coach", "다음 행동으로 이어가볼까요?", "완료한 미션 다음에 이어서 하기 좋은 목표를 골랐어요.", null, "/assets/characters/finmate-growth.png", null, null, null, null),
                    section("next", "list", "추천 다음 목표", null, null, null, null, nextMissionItems,
                            actions(action("미션 탭으로 돌아가기", "/missions", "GET", "primary", null)), null)
            ), Map.of("completedMissionId", "mission-food"));
        }
        if ("mission-invest".equals(missionId)) {
            return screen("missions:mission-invest", "나의 계획 세우기", "mission", List.of(
                    section("plan", "choiceList", "AI 코치의 제안을 내 계획으로 만들어볼까요?", "이번 달 나의 핵심 목표는?", null, null, null,
                            items(item("mission-food", "식비 절약 시작하기", "선택됨", null, null, "check", "purple", "/missions/mission-food"),
                                    item("mission-saving", "비상금 3개월치 모으기", "자동 저축 유지", null, null, "saving", "green", "/missions/mission-saving"),
                                    item("mission-fixed-cost", "고정 지출 5% 줄이기", "구독 점검", null, null, "spend", "orange", "/missions/mission-fixed-cost")),
                            actions(action("계획 저장하기", "/missions/mission-food", "GET", "primary", null)), null)
            ), Map.of("missionId", missionId));
        }
        missionEvaluationService.evaluateMission(userId, missionId);
        MissionRow mission = mission(userId, missionId);
        return screen("missions:" + missionId, mission.title(), "mission", List.of(
                missionHero(mission),
                section("evidence", "checkList", "완료 조건과 근거", "미션은 버튼이 아니라 행동 데이터가 조건을 만족할 때 자동 완료돼요.", null, null, null,
                        items(item("condition", "완료 조건", mission.description(), null, null, "check", "purple", null),
                                item("status", "현재 판정", mission.evaluationStatus(), null, mission.evaluatedAt(), "target", "green", null),
                                item("evidence", "근거 데이터", mission.evidenceSummary(), null, null, "records", "purple", null)),
                        null, null)
        ), Map.of("missionId", missionId));
    }

    public AppScreenResponse getMissionAdd(String userId) {
        List<MissionTemplate> templates = recommendedMissionTemplates(userId);
        List<AppItem> recommendations = templates.stream()
                .map(template -> item(
                        template.id(),
                        template.title(),
                        template.description(),
                        template.difficultyLabel(),
                        "+" + template.rewardPoints() + "P",
                        template.icon(),
                        template.tone(),
                        null,
                        Map.of("templateId", template.id())
                ))
                .toList();
        if (recommendations.isEmpty()) {
            return screen("missions:add", "미션 추가", "mission", List.of(
                    emptyActionSection("mission-add-empty", "추천할 미션을 모두 추가했어요", "진행 중인 목표를 먼저 실천해보세요.", "완료 기록이 쌓이면 다음 추천을 다시 만들 수 있어요.", "/missions")
            ), Map.of());
        }
        return screen("missions:add", "미션 추가", "mission", List.of(
                section("recommendations", "list", "추천 미션", "내 금융 기록에서 바로 시작하기 좋은 목표예요.", null, null, null,
                        recommendations,
                        actions(action("첫 추천 미션 추가하기", "/missions/add/" + templates.get(0).id(), "POST", "primary", "mission-add")), null)
        ), Map.of("recommendationCount", recommendations.size()));
    }

    @Transactional
    public AppActionResultResponse addMissionFromTemplate(String userId, String templateId) {
        MissionTemplate template = missionTemplate(templateId);
        String missionDbId = missionDbId(userId, template.missionId());
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM missions WHERE id = ?", Integer.class, missionDbId);
        if (exists != null && exists > 0) {
            return new AppActionResultResponse("ALREADY_ADDED", "이미 추가된 미션이에요", "진행 중인 미션 목록에서 확인할 수 있어요.", "/missions/" + template.missionId(), Map.of("missionId", template.missionId()));
        }
        jdbc.update("""
                INSERT INTO missions (id, user_id, title, description, status, difficulty, reward_points, progress, due_date, source)
                VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, 0, ?, 'USER_ADDED_TEMPLATE')
                """, missionDbId, userId, template.title(), template.description(), template.difficulty(), template.rewardPoints(), APP_TODAY.plusDays(7));
        jdbc.update("""
                INSERT INTO mission_events (id, mission_id, user_id, event_type, note, reward_points, event_date)
                VALUES (?, ?, ?, 'ADDED', ?, 0, ?)
                """, "event-" + UUID.randomUUID(), missionDbId, userId, "추천 미션 추가", APP_TODAY);
        return new AppActionResultResponse("ADDED", "미션을 추가했어요", template.title() + " 미션이 진행 중 목록에 들어갔습니다.", "/missions/" + template.missionId(), Map.of("missionId", template.missionId()));
    }

    public AppScreenResponse getRecords(String userId, String month) {
        missionEvaluationService.evaluateUserMissions(userId);
        YearMonth targetMonth = parseMonth(month);
        List<DailyRecordRow> records = dailyRecords(userId, targetMonth);
        List<MissionEventRow> events = missionEvents(userId, targetMonth);
        DailyRecordRow todayRecord = findDailyRecord(userId, APP_TODAY);
        if (records.isEmpty() && events.isEmpty()) {
            return screen("records:2026-06", "기록", "records", List.of(
                    section("calendar", "calendar", monthTitle(targetMonth), "아직 기록된 지출과 미션이 없어요.", "/records/history", null, null,
                            items(item("history", "월간 히스토리", "실천 기록 보기", null, null, "records", "purple", "/records/history"),
                                    item("stats", "포인트 통계", wallet(userId).pointBalance() + "P", null, null, "wallet", "green", "/records/stats")),
                            null, null),
                    emptyActionSection("record-empty", "오늘 기록이 비어 있어요", "예산과 지출이 기록되면 달력에서 바로 확인할 수 있어요.", "행동 데이터가 검증되면 포인트 기록도 함께 쌓입니다.", "/missions")
            ), Map.of("month", targetMonth.toString()));
        }
        List<AppSection> sections = new ArrayList<>();
        sections.add(section("calendar", "calendar", monthTitle(targetMonth), "날짜별 지출과 미션 성공 기록", "/records/" + APP_TODAY, null, null,
                calendarItems(userId, targetMonth, records, events),
                null, null));
        if (todayRecord != null) {
            sections.add(budgetSection(todayRecord));
        }
        sections.add(missionEventsSection("today-mission-events", "오늘의 미션 기록", missionEvents(userId, APP_TODAY)));
        return screen("records:" + targetMonth, "기록", "records", sections, Map.of("month", targetMonth.toString()));
    }

    public AppScreenResponse getRecordDetail(String userId, String date) {
        if ("history".equals(date)) {
            return screen("records:history", "월간 히스토리", "records", List.of(feedSection(userId)), Map.of());
        }
        if ("stats".equals(date)) {
            WalletRow wallet = wallet(userId);
            return screen("records:stats", "포인트 통계", "records", List.of(
                    section("stats", "points", "포인트와 가상머니", null, null, null,
                            metrics(metric("포인트", wallet.pointBalance() + "P", "미션 보상", "purple", 70),
                                    metric("가상머니", won(wallet.virtualMoneyBalance()), "생일펀드 참여 가능", "green", 60)),
                            null, null, null)
            ), Map.of());
        }
        LocalDate targetDate = parseDate(date);
        DailyRecordRow record = findDailyRecord(userId, targetDate);
        List<MissionEventRow> events = missionEvents(userId, targetDate);
        if (record == null && events.isEmpty()) {
            return screen("records:" + date, "날짜별 기록", "records", List.of(
                    emptyActionSection("record-detail-empty", "이 날짜의 기록이 없어요", "아직 예산, 지출, 미션 실천 내역이 기록되지 않았어요.", "기록이 쌓이면 날짜별 흐름을 보여드릴게요.", "/records")
            ), Map.of("date", date));
        }
        List<AppSection> sections = new ArrayList<>();
        if (record != null) {
            sections.add(budgetSection(record));
            sections.add(spendingSection(record));
        }
        sections.add(missionEventsSection("mission-events", "미션 실천 기록", events));
        sections.add(pointTransactionsSection(userId, targetDate));
        return screen("records:" + date, "날짜별 기록", "records", sections, Map.of("date", date));
    }

    public AppScreenResponse getProfile(String userId) {
        UserMeResponse user = userMe(userId);
        return screen("profile", "프로필", "profile", List.of(
                section("profile", "profileHero", user.displayName() + "님의 금융 생활", "실제 계정 상태로 저장되는 프로필입니다.", null, null,
                        metrics(metric("팔로잉", followingCount(userId) + "명", "최근 30일", "purple", null),
                                metric("포인트", user.pointBalance() + "P", "가상머니 " + won(user.virtualMoneyBalance()), "green", null)),
                        null, actions(action("공개 범위 확인", "/settings/privacy", "GET", "secondary", null), action("로그아웃", "/login", "POST", "danger", "logout")), null),
                followingSection(userId),
                feedSection(userId)
        ), Map.of());
    }

    public AppScreenResponse getProfileSection(String userId, String section) {
        return switch (section) {
            case "followers", "following" -> screen("profile:" + section, "팔로잉 금융 생활", "profile", List.of(followingSection(userId), feedSection(userId)), Map.of());
            case "activities" -> screen("profile:activities", "금융 활동 TOP", "profile", List.of(feedSection(userId)), Map.of());
            case "points" -> profilePointsScreen(userId);
            case "privacy" -> privacyScreen(userId);
            default -> throw validation("section", "Unsupported profile section.");
        };
    }

    private AppScreenResponse profilePointsScreen(String userId) {
        WalletRow wallet = wallet(userId);
        return screen("profile:points", "포인트 내역", "profile", List.of(
                section("wallet", "points", "포인트 지갑", "미션 보상과 생일펀드 가상머니 흐름을 확인해요.", null, null,
                        metrics(metric("보유 포인트", wallet.pointBalance() + "P", "실천 보상", "purple", 70),
                                metric("가상머니", won(wallet.virtualMoneyBalance()), "생일펀드 참여 가능", "green", 60)),
                        null, null, null),
                section("history", "list", "최근 포인트 기록", null, null, null, null, pointTransactionItems(userId), null, null)
        ), Map.of());
    }

    public AppScreenResponse getBirthdays(String userId) {
        bootstrapUser(userId, displayName(userId));
        FundRow fund = availableBirthdayFund(userId);
        if (fund == null) {
            return screen("birthdays", "생일 펀드", "home", List.of(
                    emptyActionSection("birthdays-empty", "열린 생일 펀드가 없어요", "친구 생일 이벤트가 생기면 이곳에서 바로 확인할 수 있어요.", "내 생일 펀드는 프로필에서 열 수 있습니다.", "/profile")
            ), Map.of());
        }
        return screen("birthdays", "생일 펀드", "home", List.of(
                section("fund", "birthday", fund.title(), "친구의 생일을 함께 축하하며 모아주는 특별한 선물이에요.", "/birthdays/" + fund.id(), "/assets/characters/finmate-birthday.png",
                        metrics(metric("모금 금액", won(fund.currentAmount()), "목표 " + won(fund.targetAmount()), "green", percent(fund.currentAmount(), fund.targetAmount())),
                                metric("남은 기간", "D-7", fund.dueDate().toString(), "purple", null)),
                        null, actions(action("참여하기", "/birthday-funds/" + fund.id() + "/contribute", "GET", "primary", null), action("내 생일 펀드 열기", "/birthday-funds/me/open", "GET", "secondary", null)), null)
        ), Map.of());
    }

    public AppScreenResponse getBirthdayFlow(String userId, String birthdayId) {
        FundRow fund = availableBirthdayFund(userId, birthdayId);
        if (fund == null) {
            return screen("birthdays:" + birthdayId, "생일 펀드", "home", List.of(
                    emptyActionSection("birthday-empty", "이 생일 이벤트를 찾을 수 없어요", "아직 참여 가능한 생일 펀드가 없거나 종료된 상태입니다.", "홈에서 새 이벤트가 열렸는지 확인해주세요.", "/home")
            ), Map.of("birthdayId", birthdayId));
        }
        return screen("birthdays:" + fund.id(), fund.title(), "home", List.of(
                section("fund", "birthday", "생일 축하 펀드란?", "친구의 생일을 함께 축하하며 모아주는 특별한 선물이에요.", null, "/assets/characters/finmate-birthday.png",
                        metrics(metric("모금 금액", won(fund.currentAmount()), "목표 " + won(fund.targetAmount()), "green", percent(fund.currentAmount(), fund.targetAmount())),
                                metric("남은 기간", "D-7", fund.dueDate().toString(), "purple", null)),
                        null, actions(action("참여하기", "/birthday-funds/" + fund.id() + "/contribute", "GET", "primary", null)), null),
                section("participants", "list", "실시간 참여 현황", null, null, null, null,
                        contributionItems(fund.id()).isEmpty()
                                ? items(item("participants-empty", "아직 참여 기록이 없어요", "첫 축하 메시지를 남길 수 있어요.", null, null, "profile", "purple", null))
                                : contributionItems(fund.id()),
                        null, null)
        ), Map.of("birthdayId", fund.id()));
    }

    @Transactional
    public AppActionResultResponse contributeBirthdayFund(String userId, String fundId, AppBirthdayContributionRequest request) {
        bootstrapUser(userId, displayName(userId));
        FundRow fund = findFund(fundId);
        if (fund == null) {
            throw validation("fundId", "참여 가능한 생일 펀드가 없습니다.");
        }
        int amount = request.amount();
        if (amount <= 0 || amount > 20000) {
            throw validation("amount", "amount must be between 1 and 20000.");
        }
        WalletRow wallet = wallet(userId);
        if (wallet.virtualMoneyBalance() < amount) {
            throw validation("amount", "가상머니 잔액이 부족합니다.");
        }
        jdbc.update("UPDATE birthday_funds SET current_amount = current_amount + ?, updated_at = now() WHERE id = ?", amount, fundId);
        jdbc.update("UPDATE point_wallets SET virtual_money_balance = virtual_money_balance - ?, updated_at = now() WHERE user_id = ?", amount, userId);
        jdbc.update("""
                INSERT INTO birthday_fund_contributions (id, fund_id, contributor_user_id, amount, message, anonymous)
                VALUES (?, ?, ?, ?, ?, ?)
                """, "contrib-" + UUID.randomUUID(), fundId, userId, amount, request.message() == null ? "" : request.message(), Boolean.TRUE.equals(request.anonymous()));
        addPointTransaction(userId, "VIRTUAL_MONEY", -amount, wallet(userId).virtualMoneyBalance(), "BIRTHDAY_FUND", fundId, "생일 펀드 참여");
        return new AppActionResultResponse("COMPLETED", "참여 완료!", "생일 축하 펀드에 참여했어요.", "/birthday-funds/" + fundId + "/complete", Map.of("amount", amount, "virtualMoneyBalance", wallet(userId).virtualMoneyBalance()));
    }

    public AppScreenResponse getBirthdayContributionComplete(String userId, String fundId) {
        FundRow fund = findFund(fundId);
        if (fund == null) {
            return screen("birthday-funds:" + fundId + ":status", "참여 완료", "home", List.of(
                    emptyActionSection("birthday-complete-empty", "생일 펀드 현황이 없어요", "참여 가능한 생일 펀드를 찾을 수 없습니다.", "홈으로 돌아가 새 이벤트를 확인해주세요.", "/home")
            ), Map.of());
        }
        return screen("birthday-funds:" + fundId + ":status", "참여 완료", "home", List.of(
                section("complete", "birthday", "축하가 완료되었어요!", "따뜻한 마음이 전달됐습니다.", null, "/assets/characters/finmate-birthday.png",
                        metrics(metric("현재 모금", won(fund.currentAmount()), "목표 " + won(fund.targetAmount()), "green", percent(fund.currentAmount(), fund.targetAmount()))),
                        null, actions(action("현황 보기", "/birthday-funds/" + fundId + "/complete", "GET", "secondary", null)), null)
        ), Map.of());
    }

    @Transactional
    public AppActionResultResponse openMyBirthdayFund(String userId) {
        bootstrapUser(userId, displayName(userId));
        String fundId = ownFundId(userId);
        jdbc.update("""
                INSERT INTO birthday_funds (id, owner_user_id, title, target_amount, current_amount, due_date, status, share_code)
                VALUES (?, ?, ?, 100000, 0, DATE '2026-07-15', 'OPEN', ?)
                ON CONFLICT (id) DO UPDATE SET status = 'OPEN', updated_at = now()
                """, fundId, userId, userName(userId) + "님의 생일 펀드", "ME-" + userId.substring(0, Math.min(8, userId.length())));
        return new AppActionResultResponse("OPENED", "내 생일 펀드가 열렸어요", "친구들에게 공유할 준비가 완료됐습니다.", "/birthday-funds/me/status", Map.of("fundId", fundId));
    }

    @Transactional
    public AppActionResultResponse shareMyBirthdayFund(String userId) {
        openMyBirthdayFund(userId);
        String fundId = ownFundId(userId);
        jdbc.update("UPDATE birthday_funds SET status = 'SHARED', updated_at = now() WHERE id = ?", fundId);
        upsertFeed(userId, "feed-" + userId + "-my-birthday", userId, "BIRTHDAY", "내 생일 펀드를 공유했어요", "친구들이 축하 메시지를 남길 수 있어요.", 0);
        return new AppActionResultResponse("SHARED", "공유가 완료됐어요", "내 생일 펀드가 친구 피드에 표시됩니다.", "/birthday-funds/me/status", Map.of("fundId", fundId));
    }

    public AppScreenResponse getMyBirthdayFundOpenScreen(String userId) {
        return screen("birthday-funds:me:open", "내 생일 펀드", "profile", List.of(
                section("open", "birthday", "내 생일 펀드를 오픈할까요?", "내 생일을 친구들에게 알리고 축하 펀드를 받을 수 있어요.", null, "/assets/characters/finmate-birthday.png", null, null,
                        actions(action("공유하기", "/birthday-funds/me/share", "POST", "primary", "birthday-share"), action("현황 보기", "/birthday-funds/me/status", "GET", "secondary", null)), null)
        ), Map.of());
    }

    public AppScreenResponse getMyBirthdayFundShareScreen(String userId) {
        return screen("birthday-funds:me:share", "내 펀드 공유", "profile", List.of(
                section("share", "birthday", "친구들에게 공유할 준비가 됐어요", "앱 안의 친구 피드에 공유됩니다.", null, "/assets/characters/finmate-birthday.png", null, null,
                        actions(action("실시간 모금 현황 보기", "/birthday-funds/me/status", "GET", "primary", null)), null)
        ), Map.of());
    }

    public AppScreenResponse getMyBirthdayFundStatus(String userId) {
        openMyBirthdayFund(userId);
        FundRow fund = fund(ownFundId(userId));
        return screen("birthday-funds:me:status", "실시간 모금 현황", "profile", List.of(
                section("status", "birthday", fund.title(), "친구들의 참여와 메시지가 여기에 쌓여요.", null, "/assets/characters/finmate-birthday.png",
                        metrics(metric("현재 모금", won(fund.currentAmount()), "목표 " + won(fund.targetAmount()), "green", percent(fund.currentAmount(), fund.targetAmount()))),
                        contributionItems(fund.id()), null, null)
        ), Map.of("fundId", fund.id()));
    }

    private AppSection privacySection(String userId) {
        PrivacyRow privacy = privacy(userId);
        return section("privacy", "list", "공개 범위 설정", "친구에게 보이는 정보와 숨기는 정보를 관리합니다.", null, null, null,
                items(item("visible", "공개 정보", privacy.exposedFields(), null, null, "profile", "green", null),
                        item("hidden", "숨김 정보", "카드번호, 거래시간, 정확한 거래처", null, null, "lock", "purple", null)),
                actions(action("로그아웃", "/login", "POST", "danger", "logout")), null);
    }

    private AppScreenResponse privacyScreen(String userId, boolean ignored) {
        return screen("profile:privacy", "공개 범위 설정", "profile", List.of(privacySection(userId)), Map.of());
    }

    private AppScreenResponse privacyScreen(String userId) {
        return privacyScreen(userId, true);
    }

    private CoachResultV1 latestOrFallbackCoach(String userId) {
        bootstrapUser(userId, displayName(userId));
        if (findSnapshot(userId) == null) {
            return emptyCoach(userId);
        }
        try {
            return jdbc.queryForObject("""
                    SELECT id, snapshot_id, source, score, confidence, summary, insights_json, recommendations_json
                    FROM coach_results
                    WHERE user_id = ?
                    ORDER BY created_at DESC
                    LIMIT 1
                    """, (rs, rowNum) -> new CoachResultV1(
                    rs.getString("id"),
                    rs.getString("snapshot_id"),
                    rs.getString("source"),
                    rs.getInt("score"),
                    number(rs.getObject("confidence")),
                    rs.getString("summary"),
                    readJson(rs.getString("insights_json"), INSIGHT_LIST),
                    readJson(rs.getString("recommendations_json"), RECOMMENDATION_LIST)
            ), userId);
        } catch (EmptyResultDataAccessException exception) {
            return fallbackCoach(userId);
        }
    }

    private CoachResultV1 emptyCoach(String userId) {
        return new CoachResultV1(
                "coach-empty-" + userId,
                "snapshot-empty-" + userId,
                "USER_STATE",
                0,
                0,
                "비교할 금융 데이터가 아직 충분하지 않아요. 마이데이터 연결 또는 기록이 쌓이면 또래 비교와 코칭을 시작할 수 있어요.",
                List.of(),
                List.of()
        );
    }

    private void upsertFeed(String userId, String id, String actorUserId, String kind, String title, String body, Integer amount) {
        jdbc.update("""
                INSERT INTO feed_items (id, user_id, actor_user_id, kind, title, body, amount)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """, id, userId, actorUserId, kind, title, body, amount);
    }

    private void addPointTransaction(String userId, String type, int amount, int balanceAfter, String referenceType, String referenceId, String description) {
        jdbc.update("""
                INSERT INTO point_transactions (id, user_id, type, amount, balance_after, reference_type, reference_id, description)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, "ptx-" + UUID.randomUUID(), userId, type, amount, balanceAfter, referenceType, referenceId, description);
    }

    private void insertConsentEvent(String userId, String consentItem, String consentVersion, String status, String summary) {
        jdbc.update("""
                INSERT INTO consent_events (id, user_id, consent_item, consent_version, status, summary)
                VALUES (?, ?, ?, ?, ?, ?)
                """, "consent-" + UUID.randomUUID(), userId, consentItem, consentVersion, status, summary);
    }

    private String missionDbId(String userId, String missionId) {
        return userId + ":" + missionId;
    }

    private String ownFundId(String userId) {
        return "fund-" + userId;
    }

    private String displayName(String userId) {
        try {
            return jdbc.queryForObject("SELECT display_name FROM users WHERE id = ?", String.class, userId);
        } catch (EmptyResultDataAccessException exception) {
            return "jinn";
        }
    }

    private String userName(String userId) {
        return displayName(userId);
    }

    private SnapshotRow snapshot(String userId) {
        SnapshotRow snapshot = findSnapshot(userId);
        if (snapshot == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SNAPSHOT_NOT_FOUND", "Financial snapshot not found.");
        }
        return snapshot;
    }

    private SnapshotRow findSnapshot(String userId) {
        List<SnapshotRow> rows = jdbc.query("""
                        SELECT * FROM financial_snapshots WHERE user_id = ? AND month = '2026-06'
                        """,
                (rs, rowNum) -> new SnapshotRow(
                        rs.getString("id"),
                        rs.getString("month"),
                        rs.getInt("monthly_income"),
                        rs.getInt("monthly_spending"),
                        rs.getInt("monthly_saving"),
                        rs.getInt("investment_value"),
                        rs.getInt("cash_like_assets"),
                        number(rs.getObject("emergency_fund_months")),
                        rs.getString("categories_json"),
                        rs.getString("lifestyle_tags")
                ), userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private DailyRecordRow dailyRecord(String userId) {
        DailyRecordRow record = findDailyRecord(userId);
        if (record == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RECORD_NOT_FOUND", "Daily record not found.");
        }
        return record;
    }

    private DailyRecordRow findDailyRecord(String userId) {
        return findDailyRecord(userId, APP_TODAY);
    }

    private DailyRecordRow findDailyRecord(String userId, LocalDate date) {
        List<DailyRecordRow> rows = jdbc.query("SELECT * FROM daily_records WHERE user_id = ? AND record_date = ?",
                (rs, rowNum) -> new DailyRecordRow(
                        rs.getDate("record_date").toLocalDate(),
                        rs.getInt("budget"),
                        rs.getInt("spent"),
                        rs.getString("category_spending_json"),
                        rs.getString("mission_status"),
                        rs.getInt("point_delta")
                ), userId, date);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<DailyRecordRow> dailyRecords(String userId, YearMonth month) {
        LocalDate from = month.atDay(1);
        LocalDate to = month.plusMonths(1).atDay(1);
        return jdbc.query("""
                        SELECT * FROM daily_records
                        WHERE user_id = ? AND record_date >= ? AND record_date < ?
                        ORDER BY record_date
                        """,
                (rs, rowNum) -> new DailyRecordRow(
                        rs.getDate("record_date").toLocalDate(),
                        rs.getInt("budget"),
                        rs.getInt("spent"),
                        rs.getString("category_spending_json"),
                        rs.getString("mission_status"),
                        rs.getInt("point_delta")
                ), userId, from, to);
    }

    private MissionRow mission(String userId, String missionId) {
        MissionRow mission = findMission(userId, missionId);
        if (mission == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MISSION_NOT_FOUND", "Mission not found.");
        }
        return mission;
    }

    private MissionRow findMission(String userId, String missionId) {
        List<MissionRow> rows = jdbc.query("SELECT * FROM missions WHERE id = ?",
                (rs, rowNum) -> missionRow(rs, missionId), missionDbId(userId, missionId));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<MissionRow> missions(String userId) {
        bootstrapUser(userId, displayName(userId));
        return jdbc.query("SELECT * FROM missions WHERE user_id = ? ORDER BY created_at",
                (rs, rowNum) -> missionRow(rs, rs.getString("id").substring((userId + ":").length())), userId);
    }

    private MissionRow todayMission(List<MissionRow> missions) {
        return missions.stream()
                .filter(mission -> !"COMPLETED".equals(mission.status()))
                .max(Comparator.comparingInt(MissionRow::progress))
                .orElse(missions.stream().findFirst().orElse(null));
    }

    private List<MissionEventRow> missionEvents(String userId, YearMonth month) {
        LocalDate from = month.atDay(1);
        LocalDate to = month.plusMonths(1).atDay(1);
        return jdbc.query("""
                        SELECT e.id, e.event_type, e.note, e.reward_points, e.event_date, m.title
                        FROM mission_events e
                        JOIN missions m ON m.id = e.mission_id
                        WHERE e.user_id = ? AND e.event_date >= ? AND e.event_date < ?
                        ORDER BY e.event_date DESC, e.created_at DESC
                        """,
                (rs, rowNum) -> new MissionEventRow(
                        rs.getString("id"),
                        rs.getString("event_type"),
                        rs.getString("title"),
                        rs.getString("note"),
                        rs.getInt("reward_points"),
                        rs.getDate("event_date").toLocalDate()
                ), userId, from, to);
    }

    private List<MissionEventRow> missionEvents(String userId, LocalDate date) {
        return jdbc.query("""
                        SELECT e.id, e.event_type, e.note, e.reward_points, e.event_date, m.title
                        FROM mission_events e
                        JOIN missions m ON m.id = e.mission_id
                        WHERE e.user_id = ? AND e.event_date = ?
                        ORDER BY e.created_at DESC
                        """,
                (rs, rowNum) -> new MissionEventRow(
                        rs.getString("id"),
                        rs.getString("event_type"),
                        rs.getString("title"),
                        rs.getString("note"),
                        rs.getInt("reward_points"),
                        rs.getDate("event_date").toLocalDate()
                ), userId, date);
    }

    private List<AppItem> pointTransactionItems(String userId) {
        bootstrapUser(userId, displayName(userId));
        List<AppItem> transactions = jdbc.query("""
                        SELECT id, type, amount, balance_after, description, created_at
                        FROM point_transactions
                        WHERE user_id = ?
                        ORDER BY created_at DESC
                        LIMIT 10
                        """,
                (rs, rowNum) -> {
                    int amount = rs.getInt("amount");
                    String type = rs.getString("type");
                    String value = ("VIRTUAL_MONEY".equals(type) ? won(Math.abs(amount)) : Math.abs(amount) + "P");
                    if (amount > 0) {
                        value = "+" + value;
                    } else if (amount < 0) {
                        value = "-" + value;
                    }
                    return item(
                            rs.getString("id"),
                            rs.getString("description"),
                            rs.getObject("created_at", OffsetDateTime.class).toLocalDate().toString(),
                            value,
                            "잔액 " + ("VIRTUAL_MONEY".equals(type) ? won(rs.getInt("balance_after")) : rs.getInt("balance_after") + "P"),
                            "wallet",
                            amount >= 0 ? "purple" : "green",
                            null
                    );
                }, userId);
        if (transactions.isEmpty()) {
            return List.of(item("points-empty", "아직 포인트 기록이 없어요", "행동 데이터로 미션이 검증되면 이곳에 기록돼요.", null, null, "wallet", "purple", null));
        }
        return transactions;
    }

    private AppSection pointTransactionsSection(String userId, LocalDate date) {
        List<AppItem> items = jdbc.query("""
                        SELECT id, type, amount, balance_after, description, created_at
                        FROM point_transactions
                        WHERE user_id = ? AND CAST(created_at AS DATE) = ?
                        ORDER BY created_at DESC
                        LIMIT 10
                        """,
                (rs, rowNum) -> {
                    int amount = rs.getInt("amount");
                    return item(
                            rs.getString("id"),
                            rs.getString("description"),
                            rs.getObject("created_at", OffsetDateTime.class).toLocalTime().withNano(0).toString(),
                            "+" + amount + "P",
                            "잔액 " + rs.getInt("balance_after") + "P",
                            "wallet",
                            "purple",
                            null
                    );
                }, userId, date);
        if (items.isEmpty()) {
            items = List.of(item("points-empty", "포인트 기록이 없어요", "이 날짜에는 포인트 적립 내역이 없습니다.", null, null, "wallet", "purple", null));
        }
        return section("point-transactions", "list", "포인트 기록", null, null, null, null, items, null, null);
    }

    private List<AppItem> calendarItems(String userId, YearMonth month, List<DailyRecordRow> records, List<MissionEventRow> events) {
        Map<LocalDate, DailyRecordRow> recordByDate = new LinkedHashMap<>();
        for (DailyRecordRow record : records) {
            recordByDate.put(record.date(), record);
        }
        Set<LocalDate> successDates = new HashSet<>();
        for (MissionEventRow event : events) {
            if ("DONE".equals(event.eventType())) {
                successDates.add(event.eventDate());
            }
        }
        List<AppItem> items = new ArrayList<>();
        for (int day = 1; day <= month.lengthOfMonth(); day += 1) {
            LocalDate date = month.atDay(day);
            DailyRecordRow record = recordByDate.get(date);
            boolean missionSuccess = successDates.contains(date) || (record != null && "SUCCESS".equals(record.missionStatus()));
            String tone = missionSuccess ? "success" : record != null && record.spent() > record.budget() ? "over" : record == null ? "empty" : "none";
            String value = record == null ? null : won(record.spent());
            items.add(item(date.toString(), String.valueOf(day), value, null, missionSuccess ? "SUCCESS" : null, "calendar", tone, "/records/" + date));
        }
        items.add(item("history", "월간 히스토리", "실천 기록 보기", null, null, "records", "purple", "/records/history"));
        items.add(item("stats", "포인트 통계", wallet(userId).pointBalance() + "P", null, null, "wallet", "green", "/records/stats"));
        return items;
    }

    private AppSection missionEventsSection(String id, String title, List<MissionEventRow> events) {
        if (events.isEmpty()) {
            return section(id, "list", title, null, null, null, null,
                    items(item("mission-events-empty", "아직 미션 기록이 없어요", "미션을 추가하거나 행동 데이터가 검증되면 여기에 쌓입니다.", null, null, "target", "purple", "/missions")),
                    null, null);
        }
        List<AppItem> items = events.stream()
                .map(event -> item(
                        event.id(),
                        event.title(),
                        "DONE".equals(event.eventType()) ? "성공" : "추가됨",
                        event.rewardPoints() > 0 ? "+" + event.rewardPoints() + "P" : null,
                        event.eventDate().toString(),
                        "check",
                        "DONE".equals(event.eventType()) ? "green" : "purple",
                        null
                ))
                .toList();
        return section(id, "list", title, null, null, null, null, items, null, null);
    }

    private List<MissionTemplate> recommendedMissionTemplates(String userId) {
        Set<String> existingMissionIds = new HashSet<>(missions(userId).stream().map(MissionRow::routeId).toList());
        return seedStore.all("mission-templates.json").stream()
                .map(this::missionTemplate)
                .filter(MissionTemplate::active)
                .filter(template -> !existingMissionIds.contains(template.missionId()))
                .sorted(Comparator.comparing(MissionTemplate::id))
                .toList();
    }

    private MissionTemplate missionTemplate(String templateId) {
        Map<String, Object> seed = seedStore.get("mission-templates.json", templateId);
        if (seed == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MISSION_TEMPLATE_NOT_FOUND", "Mission template not found.");
        }
        return missionTemplate(seed);
    }

    private MissionTemplate missionTemplate(Map<String, Object> seed) {
        String id = stringValue(seed.get("id"));
        String missionId = stringValue(seed.getOrDefault("missionId", id.toLowerCase().replace("_", "-")));
        String difficulty = stringValue(seed.getOrDefault("defaultDifficulty", "EASY"));
        return new MissionTemplate(
                id,
                missionId,
                stringValue(seed.getOrDefault("titleTemplate", "추천 미션")),
                stringValue(seed.getOrDefault("descriptionTemplate", "오늘 바로 실천할 수 있는 금융 루틴입니다.")),
                difficulty,
                "EASY".equals(difficulty) ? "쉬움" : "보통",
                intValue(seed.getOrDefault("defaultRewardPoints", 100)),
                stringValue(seed.getOrDefault("icon", "target")),
                stringValue(seed.getOrDefault("tone", "purple")),
                Boolean.TRUE.equals(seed.get("active"))
        );
    }

    private MissionRow missionRow(ResultSet rs, String routeId) throws java.sql.SQLException {
        OffsetDateTime evaluatedAt = rs.getObject("evaluated_at", OffsetDateTime.class);
        return new MissionRow(
                rs.getString("id"),
                routeId,
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("status"),
                rs.getString("difficulty"),
                rs.getInt("reward_points"),
                rs.getInt("progress"),
                rs.getString("evaluation_status"),
                evaluatedAt == null ? "아직 평가 전" : evaluatedAt.toLocalDate().toString(),
                evidenceSummary(rs.getString("evaluation_rule_json"))
        );
    }

    private WalletRow wallet(String userId) {
        bootstrapUser(userId, displayName(userId));
        List<WalletRow> rows = jdbc.query("SELECT point_balance, virtual_money_balance FROM point_wallets WHERE user_id = ?",
                (rs, rowNum) -> new WalletRow(rs.getInt("point_balance"), rs.getInt("virtual_money_balance")), userId);
        return rows.isEmpty() ? new WalletRow(0, 100000) : rows.get(0);
    }

    private FundRow fund(String fundId) {
        FundRow fund = findFund(fundId);
        if (fund == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BIRTHDAY_FUND_NOT_FOUND", "Birthday fund not found.");
        }
        return fund;
    }

    private FundRow findFund(String fundId) {
        List<FundRow> rows = jdbc.query("SELECT * FROM birthday_funds WHERE id = ?",
                (rs, rowNum) -> fundRow(rs), fundId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private FundRow fundRow(ResultSet rs) throws java.sql.SQLException {
        return new FundRow(
                rs.getString("id"),
                rs.getString("title"),
                rs.getInt("target_amount"),
                rs.getInt("current_amount"),
                rs.getDate("due_date").toLocalDate(),
                rs.getString("status")
        );
    }

    private PrivacyRow privacy(String userId) {
        bootstrapUser(userId, displayName(userId));
        return jdbc.queryForObject("SELECT exposed_fields FROM privacy_settings WHERE user_id = ?",
                (rs, rowNum) -> new PrivacyRow(rs.getString("exposed_fields")), userId);
    }

    private int followingCount(String userId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM friendships WHERE follower_id = ? AND status = 'ACTIVE'", Integer.class, userId);
        return count == null ? 0 : count;
    }

    private FundRow availableBirthdayFund(String userId) {
        List<FundRow> funds = jdbc.query("""
                        SELECT b.*
                        FROM birthday_funds b
                        JOIN feed_items f ON f.user_id = ? AND f.actor_user_id = b.owner_user_id AND f.kind = 'BIRTHDAY'
                        WHERE b.status = 'OPEN'
                        ORDER BY b.due_date, b.created_at
                        LIMIT 1
                        """,
                (rs, rowNum) -> fundRow(rs), userId);
        return funds.isEmpty() ? null : funds.get(0);
    }

    private FundRow availableBirthdayFund(String userId, String fundId) {
        List<FundRow> funds = jdbc.query("""
                        SELECT b.*
                        FROM birthday_funds b
                        JOIN feed_items f ON f.user_id = ? AND f.actor_user_id = b.owner_user_id AND f.kind = 'BIRTHDAY'
                        WHERE b.id = ? AND b.status = 'OPEN'
                        LIMIT 1
                        """,
                (rs, rowNum) -> fundRow(rs), userId, fundId);
        return funds.isEmpty() ? null : funds.get(0);
    }

    private List<AppItem> contributionItems(String fundId) {
        return jdbc.query("""
                SELECT u.display_name, c.message, c.amount
                FROM birthday_fund_contributions c
                JOIN users u ON u.id = c.contributor_user_id
                WHERE c.fund_id = ?
                ORDER BY c.created_at DESC
                LIMIT 10
                """, (rs, rowNum) -> item("c" + rowNum, rs.getString("display_name"), rs.getString("message"), won(rs.getInt("amount")), null, "profile", "purple", null), fundId);
    }

    private AppSection followingSection(String userId) {
        int count = followingCount(userId);
        if (count == 0) {
            return emptyActionSection("following-empty", "팔로잉 금융 근황", "아직 팔로잉한 친구가 없어요.", "친구 금융 활동이 생기면 지출, 저축, 투자 근황을 한눈에 볼 수 있어요.", "/profile");
        }
        return section("following", "signalGrid", "팔로잉 금융 근황", "친구 " + followingCount(userId) + "명의 금융 활동 요약", "/profile/following", null,
                metrics(metric("주식 투자", "18명", "36%", "purple", 36),
                        metric("적금 가입", "32명", "64%", "green", 64),
                        metric("펀드 투자", "9명", "18%", "purple", 18),
                        metric("연금 준비", "21명", "42%", "green", 42)),
                null, null, null);
    }

    private AppSection feedSection(String userId) {
        List<AppItem> feed = jdbc.query("""
                SELECT f.id, u.display_name, f.title, f.body, f.amount, f.kind
                FROM feed_items f
                JOIN users u ON u.id = f.actor_user_id
                WHERE f.user_id = ?
                ORDER BY f.created_at DESC
                LIMIT 10
                """, (rs, rowNum) -> item(rs.getString("id"), rs.getString("title"), rs.getString("body"), rs.getObject("amount") == null ? null : won(rs.getInt("amount")), rs.getString("display_name"), "feed", "purple", null), userId);
        if (feed.isEmpty()) {
            return section("feed", "list", "친구 피드", null, null, null, null,
                    items(item("feed-empty", "아직 친구 활동이 없어요", "팔로잉한 친구의 금융 루틴이 생기면 여기에 표시됩니다.", null, null, "feed", "purple", null)),
                    null, null);
        }
        return section("feed", "list", "친구 피드", null, null, null, null, feed, null, null);
    }

    private AppSection recordOrEmpty(String userId, String section) {
        DailyRecordRow record = findDailyRecord(userId);
        if (record == null) {
            return "spending".equals(section)
                    ? emptyActionSection("spending-empty", "오늘의 지출 요약", "오늘 지출 기록이 아직 없어요.", "지출이 기록되면 카테고리별 소비 비중을 보여드릴게요.", "/records")
                    : emptyActionSection("budget-empty", "오늘의 예산", "오늘 예산을 아직 등록하지 않았어요.", "기록 탭에서 예산과 사용 금액을 확인할 수 있어요.", "/records");
        }
        return "spending".equals(section) ? spendingSection(record) : budgetSection(record);
    }

    private AppSection snapshotOrEmpty(String userId) {
        SnapshotRow snapshot = findSnapshot(userId);
        if (snapshot == null) {
            return emptyActionSection("asset-empty", "자산 현황", "연결된 자산 데이터가 없어요.", "마이데이터 연결 또는 샘플 데이터가 준비되면 자산 흐름을 보여드릴게요.", "/profile");
        }
        return assetSection(snapshot);
    }

    private AppSection emptyActionSection(String id, String title, String subtitle, String body, String path) {
        return section(id, "actionCard", title, subtitle, path, null,
                metrics(metric("상태", "대기", body, "purple", null)),
                null, actions(action("확인하기", path, "GET", "secondary", null)), Map.of("empty", true));
    }

    private AppSection budgetSection(DailyRecordRow record) {
        int remaining = record.budget() - record.spent();
        return section("budget", "budget", "오늘의 예산", null, "/home/budget", null,
                metrics(metric("하루 예산", won(record.budget()), "식비 기준", "purple", null),
                        metric("사용 금액", won(record.spent()), "현재까지", "green", percent(record.spent(), record.budget())),
                        metric("남은 금액", won(remaining), remaining >= 0 ? "예산 안" : "초과", remaining >= 0 ? "green" : "orange", null)),
                null, null, Map.of("progress", percent(record.spent(), record.budget())));
    }

    private AppSection spendingSection(DailyRecordRow record) {
        Map<String, Integer> categories = readJson(record.categorySpendingJson(), CATEGORY_MAP);
        int total = categories.values().stream().mapToInt(Integer::intValue).sum();
        List<AppItem> items = categories.entrySet().stream()
                .map(entry -> item(entry.getKey(), entry.getKey(), "-" + won(entry.getValue()), null, percent(entry.getValue(), total) + "%", iconForCategory(entry.getKey()), "orange", null))
                .toList();
        return section("spending", "spendingGrid", "오늘의 지출 요약", null, "/home/spending", null, null, items, null, null);
    }

    private AppSection assetSection(SnapshotRow snapshot) {
        return section("asset", "asset", "자산 현황", "이번 달 +" + won(snapshot.monthlySaving()) + " (+2.6%)", "/home/assets", null,
                metrics(metric("총 자산", won(snapshot.cashLikeAssets() + snapshot.investmentValue()), "비상금 " + snapshot.emergencyFundMonths() + "개월", "purple", 40)),
                null, null, Map.of("sparkline", List.of(12, 18, 16, 24, 21, 31, 28, 35)));
    }

    private AppSection compareBarsSection(CoachResultV1 coach) {
        return section("comparison-bars", "compareBars", "항목별 비교", null, null, null, null,
                items(item("saving", "저축 비율", "65%", "상위 30%", null, "saving", "purple", null, Map.of("mine", 65, "group", 30)),
                        item("investment", "투자 비율", "15%", "하위 20%", null, "stocks", "orange", null, Map.of("mine", 15, "group", 20)),
                        item("spending", "소비 절제력", "72%", "상위 50%", null, "spend", "green", null, Map.of("mine", 72, "group", 50)),
                        item("score", "AI 코치 점수", coach.score() + "점", coach.source(), null, "target", "purple", null, Map.of("mine", coach.score(), "group", 68))),
                actions(action("AI 코치의 분석 보기", "/compare/coach", "GET", "primary", null)), null);
    }

    private AppSection missionHero(MissionRow mission) {
        return section("mission-hero", "missionHero", mission.title(), "오늘의 미션", "/missions/" + mission.routeId(), "/assets/characters/finmate-main.png",
                metrics(metric("진행률", mission.progress() + "%", mission.rewardPoints() + "P 보상", "purple", mission.progress())),
                null, null, null);
    }

    private AppItem missionItem(MissionRow mission) {
        return item(mission.routeId(), mission.title(), mission.description(), mission.progress() + "%", "+" + mission.rewardPoints() + "P", "target", "purple", "/missions/" + mission.routeId());
    }

    private AppItem completedMissionItem(MissionRow mission) {
        return item(mission.routeId(), mission.title(), "완료됨", "100%", "+" + mission.rewardPoints() + "P", "check", "green", "/missions/" + mission.routeId());
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return DEFAULT_RECORD_MONTH;
        }
        try {
            return YearMonth.parse(month);
        } catch (Exception exception) {
            return DEFAULT_RECORD_MONTH;
        }
    }

    private LocalDate parseDate(String date) {
        try {
            return LocalDate.parse(date);
        } catch (Exception exception) {
            return APP_TODAY;
        }
    }

    private String monthTitle(YearMonth month) {
        return month.getYear() + "년 " + month.getMonthValue() + "월";
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString());
    }

    private AppScreenResponse screen(String id, String title, String tab, List<AppSection> sections, Map<String, Object> meta) {
        return new AppScreenResponse(id, title, tab, "9:41", null, sections, meta);
    }

    private AppScreenResponse screen(String id, String title, String tab, String heroAsset, List<AppSection> sections, Map<String, Object> meta) {
        return new AppScreenResponse(id, title, tab, "9:41", heroAsset, sections, meta);
    }

    private AppSection section(String id, String kind, String title, String subtitle, String detailPath, String heroAsset, List<AppMetric> metrics, List<AppItem> items, List<AppAction> actions, Map<String, Object> data) {
        return new AppSection(id, kind, title, subtitle, detailPath, heroAsset, metrics, items, actions, data);
    }

    private AppMetric metric(String label, String value, String caption, String tone, Integer progress) {
        return new AppMetric(label, value, caption, tone, progress);
    }

    private List<AppMetric> metrics(AppMetric... values) {
        return List.of(values);
    }

    private AppItem item(String id, String title, String subtitle, String value, String caption, String icon, String tone, String detailPath) {
        return item(id, title, subtitle, value, caption, icon, tone, detailPath, null);
    }

    private AppItem item(String id, String title, String subtitle, String value, String caption, String icon, String tone, String detailPath, Map<String, Object> data) {
        return new AppItem(id, title, subtitle, value, caption, icon, tone, detailPath, data);
    }

    private List<AppItem> items(AppItem... values) {
        return List.of(values);
    }

    private AppAction action(String label, String path, String method, String tone, String intent) {
        return new AppAction(label, path, method, tone, intent);
    }

    private List<AppAction> actions(AppAction... values) {
        return List.of(values);
    }

    private String iconForCategory(String category) {
        return switch (category) {
            case "식비" -> "food";
            case "교통비" -> "transport";
            case "카페/간식" -> "cafe";
            default -> "more";
        };
    }

    private String won(int amount) {
        return "₩" + String.format("%,d", amount);
    }

    private int percent(int value, int total) {
        if (total == 0) {
            return 0;
        }
        return Math.max(0, Math.min(100, (int) Math.round(value * 100.0 / total)));
    }

    private String evidenceSummary(String evidenceJson) {
        if (evidenceJson == null || evidenceJson.isBlank() || "{}".equals(evidenceJson)) {
            return "아직 평가할 행동 데이터가 충분하지 않아요.";
        }
        Map<String, Object> evidence = readJson(evidenceJson, OBJECT_MAP);
        Object message = evidence.get("message");
        if (message != null && !message.toString().isBlank()) {
            return message.toString();
        }
        Object source = evidence.get("source");
        return source == null ? "행동 데이터 기준으로 평가 중입니다." : "근거: " + source;
    }

    private double number(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal.doubleValue();
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(value.toString());
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize JSON", exception);
        }
    }

    private <T> T readJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to parse JSON", exception);
        }
    }

    private ApiException validation(String field, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Request validation failed.", List.of(new FieldErrorDetail(field, message)));
    }

    private record StarterRoutine(
            int monthlyIncome,
            int monthlySpending,
            int monthlySaving,
            int investmentValue,
            int cashLikeAssets,
            double emergencyFundMonths,
            int dailyBudget,
            int dailySpent,
            Map<String, Integer> dailyCategories,
            Map<String, Integer> monthlyCategories,
            List<String> lifestyleTags,
            int foodMissionProgress,
            int savingMissionProgress,
            int fixedCostMissionProgress
    ) {
    }

    private record SnapshotRow(String id, String month, int monthlyIncome, int monthlySpending, int monthlySaving, int investmentValue, int cashLikeAssets, double emergencyFundMonths, String categoriesJson, String lifestyleTags) {
    }

    private record DailyRecordRow(LocalDate date, int budget, int spent, String categorySpendingJson, String missionStatus, int pointDelta) {
    }

    private record MissionRow(String dbId, String routeId, String title, String description, String status, String difficulty, int rewardPoints, int progress, String evaluationStatus, String evaluatedAt, String evidenceSummary) {
    }

    private record MissionEventRow(String id, String eventType, String title, String note, int rewardPoints, LocalDate eventDate) {
    }

    private record MissionTemplate(String id, String missionId, String title, String description, String difficulty, String difficultyLabel, int rewardPoints, String icon, String tone, boolean active) {
    }

    private record WalletRow(int pointBalance, int virtualMoneyBalance) {
    }

    private record FundRow(String id, String title, int targetAmount, int currentAmount, LocalDate dueDate, String status) {
    }

    private record PrivacyRow(String exposedFields) {
    }
}
