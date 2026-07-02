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
import java.time.temporal.ChronoUnit;
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
        jdbc.update("DELETE FROM compare_group_members WHERE group_id IN (SELECT id FROM compare_groups WHERE user_id = ?)", userId);
        jdbc.update("DELETE FROM compare_groups WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM compare_group_members WHERE member_user_id = ?", userId);
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
                  compare_group_members,
                  compare_groups,
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
        missionEvaluationService.evaluateUserMissions(userId);
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
                            metric("남은 기간", dDayLabel(birthdayFund.dueDate()), birthdayFund.dueDate().toString(), "purple", null)),
                    null, actions(action("축하 펀드 참여하기", "/birthday-funds/" + birthdayFund.id() + "/contribute", "GET", "primary", null)), null));
        }
        sections.add(record == null
                ? emptyActionSection("spending-empty", "오늘의 지출 요약", "오늘 지출 기록이 아직 없어요.", "지출이 기록되면 식비, 교통비, 카페/간식 비중을 바로 볼 수 있어요.", "/records")
                : spendingSection(record));
        sections.add(snapshot == null
                ? emptyActionSection("asset-empty", "자산 현황", "연결된 자산 데이터가 없어요.", "마이데이터 연결 또는 샘플 데이터가 준비되면 자산 흐름을 보여드릴게요.", "/profile")
                : assetSection(userId, snapshot));
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
        bootstrapUser(userId, displayName(userId));
        if (findSnapshot(userId) == null) {
            return screen("compare", "그룹 비교", "compare", List.of(
                    section("lead", "lead", "비교할 금융 데이터가 아직 없어요", "가계부나 마이데이터 요약이 준비되면 비슷한 또래와 비교할 수 있어요.", null, null, null, null, null, null),
                    emptyActionSection("compare-empty", "또래 비교 준비 중", "아직 비교 기준이 부족해요.", "기록과 자산 요약이 쌓이면 금융 점수와 항목별 차이를 보여드릴게요.", "/home")
            ), Map.of("comparisonId", "empty"));
        }
        List<AppItem> savedGroups = savedCompareGroupItems(userId);
        return screen("compare", "그룹 비교", "compare", List.of(
                section("compare-prompt", "comparePrompt", "비교하고 싶은 그룹을 선택해보세요", "나와 비슷한 사람들의 공개 금융 데이터를 기준으로 평균을 계산해요.", "/compare/filter", null, null, null, null, null),
                section("recommended", "compareGroupRail", "AI 추천 그룹", "나이, 직업, 소득, 생활권이 가까운 합성 사용자를 묶었어요.", null, null, null,
                        recommendedGroupItems(userId), null, null),
                section("my-groups", "savedCompareGroups", "내 그룹 비교", savedGroups.isEmpty() ? "직접 만든 비교 그룹이 아직 없어요." : "저장한 그룹은 다시 비교할 수 있어요.", null, null, null,
                        savedGroups.isEmpty()
                                ? items(item("compare-create-empty", "직접 만들기", "필터를 골라 나만의 비교 그룹을 저장해보세요.", null, null, "spark", "purple", "/compare/filter"))
                                : savedGroups,
                        actions(action("+ 직접 만들기", "/compare/filter", "GET", "secondary", null)), Map.of("empty", savedGroups.isEmpty()))
        ), Map.of("savedGroupCount", savedGroups.size()));
    }

    public AppScreenResponse getCompareFilter(String userId) {
        bootstrapUser(userId, displayName(userId));
        AppCompareSearchRequest request = defaultCompareRequest(userId);
        return compareSearchScreen(userId, request, "compare:filter", "필터링 조회");
    }

    public AppScreenResponse searchCompareFilter(String userId, AppCompareSearchRequest request) {
        return compareSearchScreen(userId, normalizeCompareRequest(userId, request), "compare:filter-results", "필터링 조회");
    }

    @Transactional
    public AppActionResultResponse createCompareGroup(String userId, AppCompareSearchRequest request) {
        bootstrapUser(userId, displayName(userId));
        AppCompareSearchRequest normalized = normalizeCompareRequest(userId, request);
        List<CandidateRow> members = compareCandidates(userId, normalized, 50);
        if (members.isEmpty()) {
            return new AppActionResultResponse("NO_MATCH", "조건에 맞는 그룹이 없어요", "필터를 조금 넓히면 비교할 수 있는 사람이 늘어납니다.", "/compare/filter", Map.of("resultCount", 0));
        }
        String title = compareGroupTitle(normalized);
        String filtersJson = json(compareRequestMap(normalized));
        String existingGroupId = findCompareGroupIdByFilters(userId, filtersJson);
        boolean reused = existingGroupId != null;
        String comparisonId = reused ? existingGroupId : "cmp-" + UUID.randomUUID();
        if (reused) {
            jdbc.update("""
                    UPDATE compare_groups
                    SET title = ?, filters_json = ?, member_count = ?, updated_at = now()
                    WHERE id = ? AND user_id = ?
                    """, title, filtersJson, members.size(), comparisonId, userId);
            jdbc.update("DELETE FROM compare_group_members WHERE group_id = ?", comparisonId);
        } else {
            jdbc.update("""
                    INSERT INTO compare_groups (id, user_id, title, filters_json, member_count)
                    VALUES (?, ?, ?, ?, ?)
                    """, comparisonId, userId, title, filtersJson, members.size());
        }
        int order = 1;
        for (CandidateRow member : members) {
            jdbc.update("""
                    INSERT INTO compare_group_members (group_id, member_user_id, rank_order)
                    VALUES (?, ?, ?)
                    """, comparisonId, member.userId(), order++);
        }
        return new AppActionResultResponse("CREATED", reused ? "비교 그룹을 갱신했어요" : "비교 그룹을 만들었어요", members.size() + "명의 공개 금융 데이터 평균과 비교합니다.", "/compare/results/" + comparisonId, Map.of("comparisonId", comparisonId, "memberCount", members.size(), "reused", reused));
    }

    public AppScreenResponse getCompareResult(String userId, String comparisonId) {
        SnapshotRow mySnapshot = findSnapshot(userId);
        if (mySnapshot == null) {
            return screen("compare:" + comparisonId, "비교 결과", "compare", List.of(
                    emptyActionSection("compare-result-empty", "비교 결과가 아직 없어요", "금융 데이터가 준비되면 또래와의 차이를 보여드릴게요.", "지금은 홈에서 연결 상태를 먼저 확인해주세요.", "/home")
            ), Map.of("comparisonId", comparisonId));
        }
        CompareGroupRow group = findCompareGroup(userId, comparisonId);
        List<CandidateRow> members = group == null
                ? compareCandidates(userId, defaultCompareRequest(userId), 50)
                : compareGroupMembers(group.id());
        if (members.isEmpty()) {
            return screen("compare:" + comparisonId, "비교 결과", "compare", List.of(
                    emptyActionSection("compare-result-empty", "비교할 그룹이 비어 있어요", "필터 조건을 다시 선택하면 비교 가능한 그룹을 만들 수 있어요.", "지금은 조건을 조금 넓혀보세요.", "/compare/filter")
            ), Map.of("comparisonId", comparisonId));
        }
        CompareStats mine = compareStats(userId, mySnapshot);
        CompareStats groupStats = averageStats(members);
        String title = group == null ? compareGroupTitle(defaultCompareRequest(userId)) : group.title();
        return screen("compare:" + comparisonId, "그룹 비교", "compare", List.of(
                section("lead", "lead", "비슷한 사람들과 비교해보세요", title + " " + members.size() + "명의 평균과 비교합니다.", null, null, null, null, null, null),
                section("score", "scoreGrid", "금융 점수", null, null, null,
                        metrics(metric("나의 금융 점수", mine.score() + "점", "내 데이터 기준", "purple", mine.score()),
                                metric("비교 그룹 평균", groupStats.score() + "점", members.size() + "명 평균", "green", groupStats.score())),
                        null, null, null),
                compareBarsSection(mine, groupStats),
                section("compare-members", "compareGroupMembers", "그룹에 포함된 사용자", members.size() + "명의 공개 금융 프로필입니다.", null, null, null,
                        members.stream().map(member -> candidateItem(userId, member)).toList(), null,
                        Map.of("initialVisible", 5, "pageSize", 5, "total", members.size()))
        ), Map.of("comparisonId", comparisonId, "memberCount", members.size()));
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
        List<AppItem> activeMissionItems = missions.stream()
                .filter(mission -> !"COMPLETED".equals(mission.status()))
                .filter(mission -> todayMission == null || !mission.dbId().equals(todayMission.dbId()))
                .map(this::missionItem)
                .toList();
        if (activeMissionItems.isEmpty()) {
            activeMissionItems = items(item("active-empty", "진행 중인 미션을 더 추가해보세요", "추천 미션을 선택하면 이 영역에서 상태를 계속 확인할 수 있어요.", null, "추천 보기", "target", "purple", "/missions/add"));
        }
        sections.add(section("active", "list", "진행 중인 미션", null, null, null, null,
                activeMissionItems,
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
                                item("period", "평가 기간", mission.periodSummary(), null, null, "calendar", "purple", null),
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
                        null, null)
        ), Map.of("recommendationCount", recommendations.size()));
    }

    @Transactional
    public AppActionResultResponse addMissionFromTemplate(String userId, String templateId) {
        MissionTemplate template = missionTemplate(templateId);
        String missionDbId = missionDbId(userId, template.missionId());
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM missions WHERE id = ?", Integer.class, missionDbId);
        if (exists != null && exists > 0) {
            return new AppActionResultResponse("ALREADY_ADDED", "이미 추가된 미션이에요", "미션 상세에서 상태와 근거를 확인할 수 있어요.", "/missions/" + template.missionId(), Map.of("missionId", template.missionId()));
        }
        jdbc.update("""
                INSERT INTO missions (
                  id, user_id, title, description, status, difficulty, reward_points, progress,
                  due_date, source, template_id, verification_type, evaluation_period_start, evaluation_period_end
                )
                VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, 0, ?, 'USER_ADDED_TEMPLATE', ?, 'BEHAVIOR_DATA', ?, ?)
                """, missionDbId, userId, template.title(), template.description(), template.difficulty(),
                template.rewardPoints(), APP_TODAY.plusDays(7), template.id(), APP_TODAY, APP_TODAY.plusDays(7));
        jdbc.update("""
                INSERT INTO mission_events (id, mission_id, user_id, event_type, note, reward_points, event_date, source)
                VALUES (?, ?, ?, 'ADDED', ?, 0, ?, 'USER_ACTION')
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
        int following = followingCount(userId);
        int followers = followerCount(userId);
        return screen("profile", "팔로잉 금융 현황", "profile", List.of(
                section("profile-tabs", "profileSegmented", "팔로잉", "팔로워", null, null, null,
                        items(item("following-tab", "팔로잉", following + "명", null, "선택됨", "profile", "purple", null),
                                item("followers-tab", "팔로워", followers + "명", null, null, "profile", "muted", "/profile/followers")),
                        null, Map.of("active", "following")),
                section("profile-following-hero", "profileFollowingHero", "내 팔로잉 " + following + "명의 최근 금융 활동이에요", "최근 30일 기준으로 공개된 금융 루틴만 집계합니다.", "/profile/following", null,
                        metrics(metric("팔로잉", following + "명", "내가 보는 금융 생활", "purple", null),
                                metric("팔로워", followers + "명", "나를 보는 사람", "green", null)),
                        null, null, null),
                followingSection(userId),
                followingDistributionSection(userId),
                followingTopActivitiesSection(userId),
                section("profile-settings", "actionCard", "계정 관리", user.displayName() + "님의 공개 범위와 세션을 관리합니다.", null, null,
                        metrics(metric("포인트", user.pointBalance() + "P", "가상머니 " + won(user.virtualMoneyBalance()), "purple", null)),
                        null, actions(action("공개 범위 확인", "/settings/privacy", "GET", "secondary", null), action("로그아웃", "/login", "POST", "danger", "logout")), null)
        ), Map.of("followingCount", following, "followerCount", followers));
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
                                metric("남은 기간", dDayLabel(fund.dueDate()), fund.dueDate().toString(), "purple", null)),
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
                                metric("남은 기간", dDayLabel(fund.dueDate()), fund.dueDate().toString(), "purple", null)),
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
                .orElse(null);
    }

    private List<MissionEventRow> missionEvents(String userId, YearMonth month) {
        LocalDate from = month.atDay(1);
        LocalDate to = month.plusMonths(1).atDay(1);
        return jdbc.query("""
                        SELECT e.id, e.event_type, e.note, e.reward_points, e.event_date, e.source, e.evaluation_result, m.title
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
                        rs.getDate("event_date").toLocalDate(),
                        rs.getString("source"),
                        rs.getString("evaluation_result")
                ), userId, from, to);
    }

    private List<MissionEventRow> missionEvents(String userId, LocalDate date) {
        return jdbc.query("""
                        SELECT e.id, e.event_type, e.note, e.reward_points, e.event_date, e.source, e.evaluation_result, m.title
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
                        rs.getDate("event_date").toLocalDate(),
                        rs.getString("source"),
                        rs.getString("evaluation_result")
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
            if (event.isDataEvaluationSuccess()) {
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
        List<MissionEventRow> successEvents = events.stream()
                .filter(MissionEventRow::isDataEvaluationSuccess)
                .toList();
        if (successEvents.isEmpty()) {
            return section(id, "list", title, null, null, null, null,
                    items(item("mission-events-empty", "아직 성공한 미션 기록이 없어요", "행동 데이터가 조건을 만족하면 성공 기록과 포인트가 여기에 쌓입니다.", null, null, "target", "purple", "/missions")),
                    null, null);
        }
        List<AppItem> items = successEvents.stream()
                .map(event -> item(
                        event.id(),
                        event.title(),
                        "성공",
                        event.rewardPoints() > 0 ? "+" + event.rewardPoints() + "P" : null,
                        event.eventDate().toString(),
                        "check",
                        "green",
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
        java.sql.Date periodStart = rs.getDate("evaluation_period_start");
        java.sql.Date periodEnd = rs.getDate("evaluation_period_end");
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
                evidenceSummary(rs.getString("evaluation_rule_json")),
                periodSummary(periodStart, periodEnd)
        );
    }

    private String periodSummary(java.sql.Date periodStart, java.sql.Date periodEnd) {
        if (periodStart == null && periodEnd == null) {
            return "기존 행동 데이터 기준";
        }
        String start = periodStart == null ? "시작일 미정" : periodStart.toLocalDate().toString();
        String end = periodEnd == null ? "종료일 미정" : periodEnd.toLocalDate().toString();
        return start + " ~ " + end;
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

    private int followerCount(String userId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM friendships WHERE followee_id = ? AND status = 'ACTIVE'", Integer.class, userId);
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
        List<String> followeeIds = followeeIds(userId);
        int count = followeeIds.size();
        if (count == 0) {
            return emptyActionSection("following-empty", "팔로잉 금융 근황", "아직 팔로잉한 친구가 없어요.", "친구 금융 활동이 생기면 지출, 저축, 투자 근황을 한눈에 볼 수 있어요.", "/profile");
        }
        int stockCount = countFolloweesWithSignal(userId, followeeIds, "investment_value", "주식", "투자", "ETF");
        int savingCount = countFolloweesWithSignal(userId, followeeIds, "monthly_saving", "저축", "적금", "비상금", "자동이체");
        int fundCount = countFolloweesWithSignal(userId, followeeIds, null, "펀드");
        int pensionCount = countFolloweesWithSignal(userId, followeeIds, null, "연금", "IRP");
        return section("following", "signalGrid", "팔로잉 금융 근황", "친구 " + count + "명의 공개 금융 활동 기준", "/profile/following", null,
                metrics(followingMetric("주식 투자", stockCount, count, "purple"),
                        followingMetric("적금 가입", savingCount, count, "green"),
                        followingMetric("펀드 투자", fundCount, count, "purple"),
                        followingMetric("연금 준비", pensionCount, count, "green")),
                null, null, null);
    }

    private AppSection followingDistributionSection(String userId) {
        List<String> followeeIds = followeeIds(userId);
        int count = followeeIds.size();
        if (count == 0) {
            return section("following-distribution", "distribution", "금융 생활 분포", "팔로잉 데이터가 쌓이면 분포를 보여드릴게요.", null, null, null,
                    items(item("distribution-empty", "아직 분포 데이터가 없어요", "팔로잉한 친구의 공개 금융 활동이 필요합니다.", null, null, "chart", "purple", null, Map.of("mine", 0, "group", 0))),
                    null, null);
        }
        int stockCount = countFolloweesWithSignal(userId, followeeIds, "investment_value", "주식", "투자", "ETF");
        int savingCount = countFolloweesWithSignal(userId, followeeIds, "monthly_saving", "저축", "적금", "비상금", "자동이체");
        int fundCount = countFolloweesWithSignal(userId, followeeIds, null, "펀드");
        int pensionCount = countFolloweesWithSignal(userId, followeeIds, null, "연금", "IRP");
        int studyCount = countFolloweesWithSignal(userId, followeeIds, null, "재테크", "공부", "금융 공부");
        return section("following-distribution", "distribution", "금융 생활 분포", "팔로잉 " + count + "명의 공개 신호 기준입니다.", null, null, null,
                items(distributionItem("dist-stock", "주식 투자", stockCount, count, "stocks", "purple"),
                        distributionItem("dist-saving", "적금 가입", savingCount, count, "saving", "green"),
                        distributionItem("dist-fund", "펀드 투자", fundCount, count, "fund", "purple"),
                        distributionItem("dist-pension", "연금 준비", pensionCount, count, "pension", "green"),
                        distributionItem("dist-study", "재테크 공부", studyCount, count, "study", "purple")),
                null, null);
    }

    private AppItem distributionItem(String id, String title, int value, int total, String icon, String tone) {
        int progress = percent(value, total);
        return item(id, title, value + "명", progress + "%", null, icon, tone, null, Map.of("mine", progress, "group", 0));
    }

    private AppSection followingTopActivitiesSection(String userId) {
        List<AppItem> items = jdbc.query("""
                        SELECT f.id, u.display_name, f.title, f.amount, f.kind
                        FROM feed_items f
                        JOIN users u ON u.id = f.actor_user_id
                        WHERE f.user_id = ? AND f.kind <> 'BIRTHDAY'
                        ORDER BY f.created_at DESC
                        LIMIT 5
                        """,
                (rs, rowNum) -> item(
                        rs.getString("id"),
                        rs.getString("display_name"),
                        rs.getString("title"),
                        rs.getObject("amount") == null ? null : signedWon(rs.getInt("amount")),
                        rs.getString("kind"),
                        iconForActivity(rs.getString("kind")),
                        rowNum % 2 == 0 ? "purple" : "green",
                        null
                ), userId);
        if (items.isEmpty()) {
            items = derivedFollowingActivities(userId);
        }
        if (items.isEmpty()) {
            items = items(item("top-empty", "아직 금융 활동이 없어요", "팔로잉한 친구의 공개 활동이 생기면 TOP 5로 보여드려요.", null, null, "feed", "purple", null));
        }
        return section("following-top", "rankList", "팔로잉 TOP 5 금융 활동", null, null, null, null, items, null, null);
    }

    private List<AppItem> derivedFollowingActivities(String userId) {
        List<AppItem> items = new ArrayList<>();
        for (String followeeId : followeeIds(userId)) {
            SnapshotRow snapshot = findSnapshot(followeeId);
            if (snapshot == null) {
                continue;
            }
            String name = displayName(followeeId);
            if (snapshot.monthlySaving() > 0 && items.size() < 5) {
                items.add(item("derived-saving-" + followeeId, name, "적금·저축", signedWon(snapshot.monthlySaving()), "월 저축", "saving", "green", null));
            }
            if (snapshot.investmentValue() > 0 && items.size() < 5) {
                items.add(item("derived-invest-" + followeeId, name, "투자 보유", won(snapshot.investmentValue()), "자산 요약", "stocks", "purple", null));
            }
            if (items.size() >= 5) {
                break;
            }
        }
        return items;
    }

    private String iconForActivity(String kind) {
        if (kind == null) {
            return "feed";
        }
        if (kind.contains("INVEST") || kind.contains("STOCK")) {
            return "stocks";
        }
        if (kind.contains("SAVE") || kind.contains("MISSION")) {
            return "saving";
        }
        return "feed";
    }

    private List<String> followeeIds(String userId) {
        return jdbc.query("""
                        SELECT followee_id
                        FROM friendships
                        WHERE follower_id = ? AND status = 'ACTIVE'
                        ORDER BY created_at
                        """,
                (rs, rowNum) -> rs.getString("followee_id"), userId);
    }

    private AppMetric followingMetric(String label, int value, int total, String tone) {
        int progress = percent(value, total);
        return metric(label, value + "명", progress + "%", tone, progress);
    }

    private int countFolloweesWithSignal(String viewerId, List<String> followeeIds, String positiveSnapshotColumn, String... keywords) {
        int count = 0;
        for (String followeeId : followeeIds) {
            if (hasPositiveSnapshotValue(followeeId, positiveSnapshotColumn) || hasTextSignal(viewerId, followeeId, keywords)) {
                count += 1;
            }
        }
        return count;
    }

    private boolean hasPositiveSnapshotValue(String userId, String column) {
        if (column == null) {
            return false;
        }
        if (!Set.of("investment_value", "monthly_saving").contains(column)) {
            return false;
        }
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM financial_snapshots
                WHERE user_id = ? AND month = '2026-06' AND %s > 0
                """.formatted(column), Integer.class, userId);
        return count != null && count > 0;
    }

    private boolean hasTextSignal(String viewerId, String followeeId, String... keywords) {
        for (String keyword : keywords) {
            String like = "%" + keyword + "%";
            Integer feedMatches = jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM feed_items
                    WHERE user_id = ? AND actor_user_id = ?
                      AND kind <> 'BIRTHDAY'
                      AND (kind LIKE ? OR title LIKE ? OR body LIKE ?)
                    """, Integer.class, viewerId, followeeId, like, like, like);
            if (feedMatches != null && feedMatches > 0) {
                return true;
            }
            Integer transactionMatches = jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM financial_transactions
                    WHERE user_id = ?
                      AND (category LIKE ? OR COALESCE(subcategory, '') LIKE ? OR COALESCE(description, '') LIKE ? OR COALESCE(cashflow_bucket, '') LIKE ?)
                    """, Integer.class, followeeId, like, like, like, like);
            if (transactionMatches != null && transactionMatches > 0) {
                return true;
            }
        }
        return false;
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
        return assetSection(userId, snapshot);
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

    private AppSection assetSection(String userId, SnapshotRow snapshot) {
        int totalAsset = snapshot.cashLikeAssets() + snapshot.investmentValue();
        List<Integer> sparkline = assetSparkline(userId, snapshot, totalAsset);
        return section("asset", "asset", "자산 현황", assetSubtitle(snapshot, totalAsset), "/home/assets", null,
                metrics(metric("총 자산", won(totalAsset), "비상금 " + formatMonths(snapshot.emergencyFundMonths()) + "개월", "purple", emergencyFundProgress(snapshot))),
                null, null, Map.of("sparkline", sparkline));
    }

    private String assetSubtitle(SnapshotRow snapshot, int totalAsset) {
        int previousAsset = totalAsset - snapshot.monthlySaving();
        if (previousAsset <= 0) {
            return "이번 달 " + signedWon(snapshot.monthlySaving());
        }
        double percent = snapshot.monthlySaving() * 100.0 / previousAsset;
        return "이번 달 " + signedWon(snapshot.monthlySaving()) + " (" + signedDecimal(percent) + "%)";
    }

    private int emergencyFundProgress(SnapshotRow snapshot) {
        int target = snapshot.monthlySpending() * 3;
        if (target > 0) {
            return percent(snapshot.cashLikeAssets(), target);
        }
        return Math.max(0, Math.min(100, (int) Math.round(snapshot.emergencyFundMonths() / 3.0 * 100)));
    }

    private List<Integer> assetSparkline(String userId, SnapshotRow snapshot, int currentAsset) {
        YearMonth month = parseMonth(snapshot.month());
        LocalDate start = month.atDay(1);
        LocalDate end = APP_TODAY.isAfter(month.atEndOfMonth()) ? month.atEndOfMonth() : APP_TODAY;
        List<FinancialTransactionMovement> transactions = jdbc.query("""
                        SELECT transaction_date, amount_krw, transaction_type, cashflow_bucket
                        FROM financial_transactions
                        WHERE user_id = ? AND transaction_date >= ? AND transaction_date <= ?
                        ORDER BY transaction_date
                        """,
                (rs, rowNum) -> new FinancialTransactionMovement(
                        rs.getDate("transaction_date").toLocalDate(),
                        rs.getInt("amount_krw"),
                        rs.getString("transaction_type"),
                        rs.getString("cashflow_bucket")
                ), userId, start, end);
        Map<LocalDate, Integer> movementByDate = new LinkedHashMap<>();
        int netMovement = 0;
        for (FinancialTransactionMovement transaction : transactions) {
            int movement = assetMovement(transaction);
            if (movement == 0) {
                continue;
            }
            movementByDate.merge(transaction.date(), movement, Integer::sum);
            netMovement += movement;
        }
        if (movementByDate.isEmpty()) {
            return List.of();
        }
        int runningAsset = currentAsset - netMovement;
        List<Integer> values = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            runningAsset += movementByDate.getOrDefault(date, 0);
            values.add(Math.max(0, runningAsset));
        }
        return values;
    }

    private int assetMovement(FinancialTransactionMovement transaction) {
        String bucket = transaction.cashflowBucket() == null ? "" : transaction.cashflowBucket();
        String type = transaction.transactionType() == null ? "" : transaction.transactionType();
        int amount = transaction.amount();
        if (bucket.contains("수입") || type.contains("수입")) {
            return Math.abs(amount);
        }
        if (bucket.contains("소비") || type.contains("지출")) {
            return amount < 0 ? amount : -amount;
        }
        if (bucket.contains("저축") || bucket.contains("투자") || type.contains("저축") || type.contains("투자")) {
            return 0;
        }
        return amount;
    }

    private AppScreenResponse compareSearchScreen(String userId, AppCompareSearchRequest request, String screenId, String title) {
        List<CandidateRow> matches = compareCandidates(userId, request, 200);
        List<AppItem> resultItems = matches.stream()
                .limit(20)
                .map(candidate -> candidateItem(userId, candidate))
                .toList();
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("filters", compareRequestMap(request));
        meta.put("filterOptions", filterOptions());
        meta.put("resultCount", matches.size());
        return screen(screenId, title, "compare", List.of(
                section("profiles", "compareProfileList", "검색 결과 " + matches.size() + "명", "선택한 조건과 가까운 익명 금융 프로필입니다.", null, null, null,
                        resultItems.isEmpty()
                                ? items(item("profile-empty", "조건에 맞는 사람이 없어요", "필터를 전체로 바꾸거나 조건을 넓혀보세요.", null, null, "search", "purple", null))
                                : resultItems,
                        null, Map.of("resultCount", matches.size()))
        ), meta);
    }

    private List<AppItem> recommendedGroupItems(String userId) {
        ProfileRow profile = profileRow(userId);
        List<AppItem> groups = new ArrayList<>();
        AppCompareSearchRequest ageJob = new AppCompareSearchRequest(decade(profile.ageBand()), "전체", profile.jobCategory(), "전체", "전체", "전체", "전체");
        addRecommendedGroup(groups, "recommend-age-job", decade(profile.ageBand()) + " " + shortJob(profile.jobCategory()), ageJob, userId, "profile");
        AppCompareSearchRequest job = new AppCompareSearchRequest("전체", "전체", profile.jobCategory(), profile.moneyStyle(), "전체", "전체", "전체");
        addRecommendedGroup(groups, "recommend-job-style", shortJob(profile.jobCategory()) + " · " + valueOr(profile.moneyStyle(), "금융 성향"), job, userId, "spark");
        AppCompareSearchRequest area = new AppCompareSearchRequest("전체", "전체", "전체", "전체", profile.area(), profile.householdType(), "전체");
        addRecommendedGroup(groups, "recommend-area-home", valueOr(profile.area(), "생활권") + " " + valueOr(profile.householdType(), "가구"), area, userId, "home");
        AppCompareSearchRequest income = new AppCompareSearchRequest("전체", profile.incomeBand(), "전체", "전체", "전체", "전체", "전체");
        addRecommendedGroup(groups, "recommend-income", valueOr(profile.incomeBand(), "비슷한 소득"), income, userId, "wallet");
        return groups;
    }

    private void addRecommendedGroup(List<AppItem> groups, String id, String title, AppCompareSearchRequest request, String userId, String icon) {
        int count = compareCandidates(userId, normalizeCompareRequest(userId, request), 200).size();
        if (count == 0) {
            return;
        }
        groups.add(item(id, title, count + "명", null, "AI 추천", icon, "purple", "/compare/filter", compareRequestMap(normalizeCompareRequest(userId, request))));
    }

    private List<AppItem> savedCompareGroupItems(String userId) {
        return jdbc.query("""
                        SELECT id, title, member_count, updated_at
                        FROM compare_groups
                        WHERE user_id = ?
                        ORDER BY updated_at DESC
                        LIMIT 10
                        """,
                (rs, rowNum) -> item(
                        rs.getString("id"),
                        rs.getString("title"),
                        "마지막 비교 " + rs.getObject("updated_at", OffsetDateTime.class).toLocalDate(),
                        null,
                        rs.getInt("member_count") + "명",
                        "profile",
                        "purple",
                        "/compare/results/" + rs.getString("id")
                ), userId);
    }

    private String findCompareGroupIdByFilters(String userId, String filtersJson) {
        List<String> ids = jdbc.queryForList("""
                        SELECT id
                        FROM compare_groups
                        WHERE user_id = ? AND filters_json = ?
                        ORDER BY updated_at DESC, created_at DESC
                        LIMIT 1
                        """, String.class, userId, filtersJson);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private AppItem candidateItem(String viewerId, CandidateRow candidate) {
        String subtitle = String.join(" · ",
                compact(valueOr(candidate.ageBand(), "나이 미공개"), shortJob(candidate.jobCategory()), "연소득 " + valueOr(candidate.incomeBand(), "미공개")));
        String caption = financialSignalCaption(viewerId, candidate);
        return item(candidate.userId(), candidate.displayName(), subtitle, candidate.score() + "점", caption, "profile", "purple", null,
                Map.of(
                        "memberUserId", candidate.userId(),
                        "ageBand", valueOr(candidate.ageBand(), ""),
                        "jobCategory", valueOr(candidate.jobCategory(), ""),
                        "incomeBand", valueOr(candidate.incomeBand(), ""),
                        "area", valueOr(candidate.area(), ""),
                        "moneyStyle", valueOr(candidate.moneyStyle(), ""),
                        "score", candidate.score(),
                        "stockSignal", candidate.investmentValue() > 0,
                        "savingSignal", candidate.monthlySaving() > 0,
                        "pensionSignal", hasTextSignal(viewerId, candidate.userId(), "연금", "IRP")
                ));
    }

    private List<CandidateRow> compareCandidates(String userId, AppCompareSearchRequest request, int limit) {
        AppCompareSearchRequest normalized = normalizeCompareRequest(userId, request);
        ProfileRow viewerProfile = profileRow(userId);
        Comparator<CandidateRow> candidateOrder = Comparator
                .comparingInt((CandidateRow candidate) -> similarityScore(viewerProfile, candidate)).reversed()
                .thenComparing(Comparator.comparingInt(CandidateRow::score).reversed())
                .thenComparing(CandidateRow::displayName)
                .thenComparing(CandidateRow::userId);
        return allCompareCandidates(userId).stream()
                .filter(candidate -> matchesFilter(candidate, normalized))
                .sorted(candidateOrder)
                .limit(limit)
                .toList();
    }

    private List<CandidateRow> allCompareCandidates(String userId) {
        return jdbc.query("""
                        SELECT u.id, u.display_name,
                               COALESCE(p.age_band, '') AS age_band,
                               COALESCE(p.income_band, '') AS income_band,
                               COALESCE(p.job_category, '') AS job_category,
                               COALESCE(p.household_type, '') AS household_type,
                               COALESCE(p.money_style, '') AS money_style,
                               COALESCE(p.area, '') AS area,
                               s.monthly_income, s.monthly_spending, s.monthly_saving,
                               s.investment_value, s.cash_like_assets, s.emergency_fund_months,
                               s.categories_json, COALESCE(s.lifestyle_tags, '') AS lifestyle_tags
                        FROM users u
                        JOIN user_profiles p ON p.user_id = u.id
                        JOIN financial_snapshots s ON s.user_id = u.id AND s.month = '2026-06'
                        LEFT JOIN privacy_settings ps ON ps.user_id = u.id
                        WHERE u.id <> ? AND COALESCE(ps.anonymous_portfolio_opt_in, TRUE) = TRUE
                        ORDER BY u.display_name
                        """,
                (rs, rowNum) -> {
                    CandidateRow candidate = new CandidateRow(
                            rs.getString("id"),
                            rs.getString("display_name"),
                            rs.getString("age_band"),
                            rs.getString("income_band"),
                            rs.getString("job_category"),
                            rs.getString("household_type"),
                            rs.getString("money_style"),
                            rs.getString("area"),
                            rs.getInt("monthly_income"),
                            rs.getInt("monthly_spending"),
                            rs.getInt("monthly_saving"),
                            rs.getInt("investment_value"),
                            rs.getInt("cash_like_assets"),
                            number(rs.getObject("emergency_fund_months")),
                            rs.getString("categories_json"),
                            rs.getString("lifestyle_tags"),
                            0
                    );
                    return candidate.withScore(financialScore(candidate.monthlyIncome(), candidate.monthlySpending(), candidate.monthlySaving(), candidate.investmentValue(), candidate.cashLikeAssets(), candidate.emergencyFundMonths()));
                }, userId);
    }

    private boolean matchesFilter(CandidateRow candidate, AppCompareSearchRequest request) {
        return matchesText(candidate.ageBand(), request.ageBand())
                && matchesText(candidate.incomeBand(), request.incomeBand())
                && matchesText(candidate.jobCategory(), request.jobCategory())
                && matchesText(candidate.moneyStyle(), request.moneyStyle())
                && matchesText(candidate.area(), request.area())
                && matchesText(candidate.householdType(), request.householdType())
                && matchesAssetRange(candidate.totalAsset(), request.assetRange());
    }

    private boolean matchesText(String value, String filter) {
        if (filter == null || filter.isBlank() || "전체".equals(filter)) {
            return true;
        }
        String safeValue = value == null ? "" : value;
        return safeValue.equals(filter) || safeValue.contains(filter) || filter.contains(safeValue);
    }

    private boolean matchesAssetRange(int totalAsset, String range) {
        if (range == null || range.isBlank() || "전체".equals(range)) {
            return true;
        }
        if (range.contains("500만원 미만")) {
            return totalAsset < 5_000_000;
        }
        if (range.contains("500만원~1,000만원")) {
            return totalAsset >= 5_000_000 && totalAsset < 10_000_000;
        }
        if (range.contains("1,000만원 이상")) {
            return totalAsset >= 10_000_000;
        }
        return true;
    }

    private int similarityScore(ProfileRow profile, CandidateRow candidate) {
        int score = 0;
        if (matchesText(candidate.ageBand(), decade(profile.ageBand()))) {
            score += 25;
        }
        if (matchesText(candidate.jobCategory(), profile.jobCategory())) {
            score += 25;
        }
        if (matchesText(candidate.incomeBand(), profile.incomeBand())) {
            score += 20;
        }
        if (matchesText(candidate.moneyStyle(), profile.moneyStyle())) {
            score += 15;
        }
        if (matchesText(candidate.area(), profile.area())) {
            score += 10;
        }
        if (matchesText(candidate.householdType(), profile.householdType())) {
            score += 5;
        }
        return score;
    }

    private AppCompareSearchRequest defaultCompareRequest(String userId) {
        return allCompareRequest();
    }

    private AppCompareSearchRequest allCompareRequest() {
        return new AppCompareSearchRequest(
                "전체",
                "전체",
                "전체",
                "전체",
                "전체",
                "전체",
                "전체"
        );
    }

    private AppCompareSearchRequest normalizeCompareRequest(String userId, AppCompareSearchRequest request) {
        AppCompareSearchRequest defaults = allCompareRequest();
        if (request == null) {
            return defaults;
        }
        return new AppCompareSearchRequest(
                valueOr(request.ageBand(), defaults.ageBand()),
                valueOr(request.incomeBand(), defaults.incomeBand()),
                valueOr(request.jobCategory(), defaults.jobCategory()),
                valueOr(request.moneyStyle(), defaults.moneyStyle()),
                valueOr(request.area(), defaults.area()),
                valueOr(request.householdType(), defaults.householdType()),
                valueOr(request.assetRange(), "전체")
        );
    }

    private Map<String, Object> compareRequestMap(AppCompareSearchRequest request) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ageBand", valueOr(request.ageBand(), "전체"));
        map.put("incomeBand", valueOr(request.incomeBand(), "전체"));
        map.put("jobCategory", valueOr(request.jobCategory(), "전체"));
        map.put("moneyStyle", valueOr(request.moneyStyle(), "전체"));
        map.put("area", valueOr(request.area(), "전체"));
        map.put("householdType", valueOr(request.householdType(), "전체"));
        map.put("assetRange", valueOr(request.assetRange(), "전체"));
        return map;
    }

    private Map<String, Object> filterOptions() {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("ageBand", distinctAgeOptions());
        options.put("incomeBand", distinctProfileValues("income_band"));
        options.put("jobCategory", distinctProfileValues("job_category"));
        options.put("moneyStyle", distinctProfileValues("money_style"));
        options.put("area", distinctProfileValues("area"));
        options.put("householdType", distinctProfileValues("household_type"));
        options.put("assetRange", List.of("전체", "500만원 미만", "500만원~1,000만원", "1,000만원 이상"));
        return options;
    }

    private List<String> distinctAgeOptions() {
        List<String> raw = distinctProfileValues("age_band").stream()
                .map(this::decade)
                .filter(value -> !"전체".equals(value))
                .distinct()
                .toList();
        List<String> values = new ArrayList<>();
        values.add("전체");
        values.addAll(raw);
        return values;
    }

    private List<String> distinctProfileValues(String column) {
        String safeColumn = switch (column) {
            case "age_band", "income_band", "job_category", "money_style", "area", "household_type" -> column;
            default -> throw validation("filter", "Unsupported filter.");
        };
        List<String> values = jdbc.query("""
                        SELECT DISTINCT %s AS value
                        FROM user_profiles
                        WHERE %s IS NOT NULL AND %s <> ''
                        """.formatted(safeColumn, safeColumn, safeColumn, safeColumn),
                (rs, rowNum) -> rs.getString("value"));
        List<String> orderedValues = values.stream()
                .map(value -> normalizeFilterOption(safeColumn, value))
                .filter(value -> value != null && !value.isBlank() && !"전체".equals(value))
                .distinct()
                .sorted(filterOptionComparator(safeColumn))
                .toList();
        List<String> result = new ArrayList<>();
        result.add("전체");
        result.addAll(orderedValues);
        return result;
    }

    private String normalizeFilterOption(String column, String value) {
        String trimmed = value == null ? "" : value.trim();
        if ("money_style".equals(column)) {
            return trimmed.replaceAll("\\s+", "");
        }
        return trimmed;
    }

    private Comparator<String> filterOptionComparator(String column) {
        return switch (column) {
            case "income_band" -> Comparator.comparingInt(this::incomeBandRank).thenComparing(value -> value);
            case "money_style" -> Comparator.comparingInt(this::moneyStyleRank).thenComparing(value -> value);
            case "age_band" -> Comparator.comparingInt(this::ageBandRank).thenComparing(value -> value);
            default -> Comparator.naturalOrder();
        };
    }

    private int incomeBandRank(String value) {
        String normalized = value == null ? "" : value.replace(",", "");
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\d+").matcher(normalized);
        if (!matcher.find()) {
            return Integer.MAX_VALUE;
        }
        int firstNumber = Integer.parseInt(matcher.group());
        if (normalized.contains("미만")) {
            return Math.max(0, firstNumber - 1);
        }
        return firstNumber;
    }

    private int moneyStyleRank(String value) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", "");
        return switch (normalized) {
            case "원금보전형" -> 10;
            case "안정추구형" -> 20;
            case "중립형" -> 30;
            case "성장추구형" -> 40;
            case "공격투자형" -> 50;
            default -> 100;
        };
    }

    private int ageBandRank(String value) {
        String normalized = value == null ? "" : value;
        if (normalized.contains("20대")) {
            return 20;
        }
        if (normalized.contains("30대")) {
            return 30;
        }
        return 100;
    }

    private String compareGroupTitle(AppCompareSearchRequest request) {
        List<String> labels = new ArrayList<>();
        if (hasFilter(request.ageBand())) {
            labels.add(request.ageBand());
        }
        if (hasFilter(request.jobCategory())) {
            labels.add(shortJob(request.jobCategory()));
        }
        if (hasFilter(request.incomeBand()) && labels.size() < 2) {
            labels.add(request.incomeBand());
        }
        if (hasFilter(request.area()) && labels.size() < 2) {
            labels.add(request.area());
        }
        return labels.isEmpty() ? "나 vs 직접 만든 그룹 평균" : "나 vs " + String.join(" ", labels) + " 평균";
    }

    private boolean hasFilter(String value) {
        return value != null && !value.isBlank() && !"전체".equals(value);
    }

    private CompareGroupRow findCompareGroup(String userId, String comparisonId) {
        List<CompareGroupRow> groups = jdbc.query("""
                        SELECT id, title, filters_json, member_count
                        FROM compare_groups
                        WHERE user_id = ? AND id = ?
                        """,
                (rs, rowNum) -> new CompareGroupRow(rs.getString("id"), rs.getString("title"), rs.getString("filters_json"), rs.getInt("member_count")),
                userId, comparisonId);
        return groups.isEmpty() ? null : groups.get(0);
    }

    private List<CandidateRow> compareGroupMembers(String groupId) {
        return jdbc.query("""
                        SELECT u.id, u.display_name,
                               COALESCE(p.age_band, '') AS age_band,
                               COALESCE(p.income_band, '') AS income_band,
                               COALESCE(p.job_category, '') AS job_category,
                               COALESCE(p.household_type, '') AS household_type,
                               COALESCE(p.money_style, '') AS money_style,
                               COALESCE(p.area, '') AS area,
                               s.monthly_income, s.monthly_spending, s.monthly_saving,
                               s.investment_value, s.cash_like_assets, s.emergency_fund_months,
                               s.categories_json, COALESCE(s.lifestyle_tags, '') AS lifestyle_tags
                        FROM compare_group_members gm
                        JOIN users u ON u.id = gm.member_user_id
                        JOIN user_profiles p ON p.user_id = u.id
                        JOIN financial_snapshots s ON s.user_id = u.id AND s.month = '2026-06'
                        WHERE gm.group_id = ?
                        ORDER BY gm.rank_order
                        """,
                (rs, rowNum) -> {
                    CandidateRow candidate = new CandidateRow(
                            rs.getString("id"),
                            rs.getString("display_name"),
                            rs.getString("age_band"),
                            rs.getString("income_band"),
                            rs.getString("job_category"),
                            rs.getString("household_type"),
                            rs.getString("money_style"),
                            rs.getString("area"),
                            rs.getInt("monthly_income"),
                            rs.getInt("monthly_spending"),
                            rs.getInt("monthly_saving"),
                            rs.getInt("investment_value"),
                            rs.getInt("cash_like_assets"),
                            number(rs.getObject("emergency_fund_months")),
                            rs.getString("categories_json"),
                            rs.getString("lifestyle_tags"),
                            0
                    );
                    return candidate.withScore(financialScore(candidate.monthlyIncome(), candidate.monthlySpending(), candidate.monthlySaving(), candidate.investmentValue(), candidate.cashLikeAssets(), candidate.emergencyFundMonths()));
                }, groupId);
    }

    private CompareStats compareStats(String userId, SnapshotRow snapshot) {
        return new CompareStats(
                snapshot.monthlySaving(),
                snapshot.monthlySpending(),
                ratePercent(snapshot.monthlySaving(), snapshot.monthlyIncome()),
                ratePercent(snapshot.monthlySpending(), snapshot.monthlyIncome()),
                ratePercent(snapshot.investmentValue(), snapshot.cashLikeAssets() + snapshot.investmentValue()),
                debtRatio(userId),
                financialScore(snapshot.monthlyIncome(), snapshot.monthlySpending(), snapshot.monthlySaving(), snapshot.investmentValue(), snapshot.cashLikeAssets(), snapshot.emergencyFundMonths())
        );
    }

    private CompareStats averageStats(List<CandidateRow> members) {
        int size = Math.max(1, members.size());
        int saving = members.stream().mapToInt(CandidateRow::monthlySaving).sum() / size;
        int spending = members.stream().mapToInt(CandidateRow::monthlySpending).sum() / size;
        int savingRate = (int) Math.round(members.stream().mapToInt(candidate -> ratePercent(candidate.monthlySaving(), candidate.monthlyIncome())).average().orElse(0));
        int spendingRate = (int) Math.round(members.stream().mapToInt(candidate -> ratePercent(candidate.monthlySpending(), candidate.monthlyIncome())).average().orElse(0));
        int investmentRatio = (int) Math.round(members.stream().mapToInt(candidate -> ratePercent(candidate.investmentValue(), candidate.totalAsset())).average().orElse(0));
        int debtRatio = (int) Math.round(members.stream().mapToInt(candidate -> debtRatio(candidate.userId())).average().orElse(0));
        int score = (int) Math.round(members.stream().mapToInt(CandidateRow::score).average().orElse(0));
        return new CompareStats(saving, spending, savingRate, spendingRate, investmentRatio, debtRatio, score);
    }

    private AppSection compareBarsSection(CompareStats mine, CompareStats group) {
        return section("comparison-bars", "compareBars", "항목별 비교", "가운데는 그룹 평균, 보라색 위치가 나의 현재 수준입니다.", null, null, null,
                items(item("saving", "저축", "월 평균 저축액", won(mine.monthlySaving()), "그룹 " + won(group.monthlySaving()), "saving", "purple", null,
                                compareGaugeData(mine.savingRate(), group.savingRate(), mine.monthlySaving(), group.monthlySaving(), "amount", "lowerHigher")),
                        item("spending", "소비", "월 평균 소비액", won(mine.monthlySpending()), "그룹 " + won(group.monthlySpending()), "spend", "green", null,
                                compareGaugeData(mine.spendingRate(), group.spendingRate(), mine.monthlySpending(), group.monthlySpending(), "amount", "spending")),
                        item("investment", "투자", "총자산 대비 투자 비율", mine.investmentRatio() + "%", "그룹 " + group.investmentRatio() + "%", "stocks", "purple", null,
                                compareGaugeData(mine.investmentRatio(), group.investmentRatio(), mine.investmentRatio(), group.investmentRatio(), "percentPoint", "lowerHigher")),
                        item("debt", "부채", "거래 내역의 대출/부채 신호", mine.debtRatio() + "%", "그룹 " + group.debtRatio() + "%", "debt", "orange", null,
                                compareGaugeData(mine.debtRatio(), group.debtRatio(), mine.debtRatio(), group.debtRatio(), "percentPoint", "lowerHigher"))),
                null, null);
    }

    private Map<String, Object> compareGaugeData(int mineRate, int groupRate, int mineRaw, int groupRaw, String unit, String mode) {
        int diff = mineRaw - groupRaw;
        return Map.of(
                "mine", mineRate,
                "group", groupRate,
                "groupPosition", 50,
                "minePosition", compareGaugePosition(mineRate, groupRate, mineRaw, groupRaw, unit),
                "deltaLabel", compareDeltaLabel(diff, unit, mode),
                "deltaDirection", compareDeltaDirection(diff)
        );
    }

    private int compareGaugePosition(int mineRate, int groupRate, int mineRaw, int groupRaw, String unit) {
        if ("amount".equals(unit)) {
            if (groupRaw == 0) {
                return mineRaw == 0 ? 50 : 92;
            }
            double relativeDiff = (mineRaw - groupRaw) * 100.0 / Math.abs(groupRaw);
            return clamp(50 + (int) Math.round(relativeDiff * 0.5), 8, 92);
        }
        return clamp(50 + (mineRate - groupRate) * 2, 8, 92);
    }

    private String compareDeltaLabel(int diff, String unit, String mode) {
        if (diff == 0) {
            return "그룹과 비슷해요";
        }
        if ("spending".equals(mode)) {
            return "그룹보다 월 " + won(Math.abs(diff)) + (diff > 0 ? " 더 써요" : " 적게 써요");
        }
        String formatted = "percentPoint".equals(unit) ? Math.abs(diff) + "%p" : "월 " + won(Math.abs(diff));
        return "그룹보다 " + formatted + (diff > 0 ? " 높아요" : " 낮아요");
    }

    private String compareDeltaDirection(int diff) {
        if (diff == 0) {
            return "same";
        }
        return diff > 0 ? "above" : "below";
    }

    private int financialScore(int income, int spending, int saving, int investment, int cashLikeAssets, double emergencyFundMonths) {
        int savingScore = Math.min(28, (int) Math.round(ratePercent(saving, income) * 0.9));
        int spendingScore = Math.max(0, 24 - Math.max(0, ratePercent(spending, income) - 55) / 3);
        int emergencyScore = Math.min(24, (int) Math.round(emergencyFundMonths * 10));
        int investmentScore = Math.min(18, (int) Math.round(ratePercent(investment, investment + cashLikeAssets) * 0.45));
        return Math.max(0, Math.min(100, 30 + savingScore + spendingScore + emergencyScore + investmentScore));
    }

    private int debtRatio(String userId) {
        Integer debt = jdbc.queryForObject("""
                SELECT COALESCE(SUM(ABS(amount_krw)), 0)
                FROM financial_transactions
                WHERE user_id = ?
                  AND (
                    category LIKE '%대출%' OR category LIKE '%부채%'
                    OR COALESCE(subcategory, '') LIKE '%대출%' OR COALESCE(subcategory, '') LIKE '%부채%'
                    OR COALESCE(description, '') LIKE '%대출%' OR COALESCE(description, '') LIKE '%부채%'
                  )
                """, Integer.class, userId);
        SnapshotRow snapshot = findSnapshot(userId);
        int denominator = snapshot == null ? 0 : snapshot.monthlyIncome();
        return ratePercent(debt == null ? 0 : debt, denominator);
    }

    private int ratePercent(int value, int total) {
        if (total <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(100, (int) Math.round(value * 100.0 / total)));
    }

    private String financialSignalCaption(String viewerId, CandidateRow candidate) {
        List<String> labels = new ArrayList<>();
        if (candidate.investmentValue() > 0) {
            labels.add("주식");
        }
        if (candidate.monthlySaving() > 0) {
            labels.add("적금");
        }
        if (hasTextSignal(viewerId, candidate.userId(), "연금", "IRP")) {
            labels.add("연금");
        }
        if (labels.isEmpty()) {
            labels.add("공개 요약");
        }
        return String.join(" · ", labels);
    }

    private ProfileRow profileRow(String userId) {
        List<ProfileRow> rows = jdbc.query("""
                        SELECT COALESCE(age_band, '') AS age_band,
                               COALESCE(income_band, '') AS income_band,
                               COALESCE(job_category, '') AS job_category,
                               COALESCE(household_type, '') AS household_type,
                               COALESCE(money_style, '') AS money_style,
                               COALESCE(area, '') AS area
                        FROM user_profiles
                        WHERE user_id = ?
                        """,
                (rs, rowNum) -> new ProfileRow(
                        rs.getString("age_band"),
                        rs.getString("income_band"),
                        rs.getString("job_category"),
                        rs.getString("household_type"),
                        rs.getString("money_style"),
                        rs.getString("area")
                ), userId);
        return rows.isEmpty() ? new ProfileRow("", "", "", "", "", "") : rows.get(0);
    }

    private String decade(String ageBand) {
        if (ageBand == null || ageBand.isBlank()) {
            return "전체";
        }
        if (ageBand.contains("10대")) {
            return "10대";
        }
        if (ageBand.contains("20대")) {
            return "20대";
        }
        if (ageBand.contains("30대")) {
            return "30대";
        }
        return ageBand;
    }

    private String shortJob(String jobCategory) {
        if (jobCategory == null || jobCategory.isBlank() || "전체".equals(jobCategory)) {
            return "직업";
        }
        return jobCategory.replace("IT/", "IT ");
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private List<String> compact(String... values) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(value);
            }
        }
        return result;
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

    private String signedWon(int amount) {
        String sign = amount >= 0 ? "+" : "-";
        return sign + won(Math.abs(amount));
    }

    private String signedDecimal(double value) {
        String sign = value >= 0 ? "+" : "-";
        return sign + String.format("%.1f", Math.abs(value));
    }

    private String formatMonths(double months) {
        return String.format("%.1f", months);
    }

    private String dDayLabel(LocalDate dueDate) {
        long days = ChronoUnit.DAYS.between(APP_TODAY, dueDate);
        if (days == 0) {
            return "D-day";
        }
        if (days > 0) {
            return "D-" + days;
        }
        return "종료";
    }

    private int percent(int value, int total) {
        if (total == 0) {
            return 0;
        }
        return Math.max(0, Math.min(100, (int) Math.round(value * 100.0 / total)));
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
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

    private record MissionRow(String dbId, String routeId, String title, String description, String status, String difficulty, int rewardPoints, int progress, String evaluationStatus, String evaluatedAt, String evidenceSummary, String periodSummary) {
    }

    private record MissionEventRow(String id, String eventType, String title, String note, int rewardPoints, LocalDate eventDate, String source, String evaluationResult) {
        private boolean isDataEvaluationSuccess() {
            return "DONE".equals(eventType) && "DATA_EVALUATION".equals(source) && "SUCCESS".equals(evaluationResult);
        }
    }

    private record MissionTemplate(String id, String missionId, String title, String description, String difficulty, String difficultyLabel, int rewardPoints, String icon, String tone, boolean active) {
    }

    private record WalletRow(int pointBalance, int virtualMoneyBalance) {
    }

    private record FundRow(String id, String title, int targetAmount, int currentAmount, LocalDate dueDate, String status) {
    }

    private record FinancialTransactionMovement(LocalDate date, int amount, String transactionType, String cashflowBucket) {
    }

    private record PrivacyRow(String exposedFields) {
    }

    private record ProfileRow(String ageBand, String incomeBand, String jobCategory, String householdType, String moneyStyle, String area) {
    }

    private record CandidateRow(
            String userId,
            String displayName,
            String ageBand,
            String incomeBand,
            String jobCategory,
            String householdType,
            String moneyStyle,
            String area,
            int monthlyIncome,
            int monthlySpending,
            int monthlySaving,
            int investmentValue,
            int cashLikeAssets,
            double emergencyFundMonths,
            String categoriesJson,
            String lifestyleTags,
            int score
    ) {
        private int totalAsset() {
            return cashLikeAssets + investmentValue;
        }

        private CandidateRow withScore(int nextScore) {
            return new CandidateRow(userId, displayName, ageBand, incomeBand, jobCategory, householdType, moneyStyle, area,
                    monthlyIncome, monthlySpending, monthlySaving, investmentValue, cashLikeAssets, emergencyFundMonths,
                    categoriesJson, lifestyleTags, nextScore);
        }
    }

    private record CompareGroupRow(String id, String title, String filtersJson, int memberCount) {
    }

    private record CompareStats(
            int monthlySaving,
            int monthlySpending,
            int savingRate,
            int spendingRate,
            int investmentRatio,
            int debtRatio,
            int score
    ) {
    }
}
