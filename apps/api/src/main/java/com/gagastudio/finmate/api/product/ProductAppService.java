package com.gagastudio.finmate.api.product;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gagastudio.finmate.api.dto.ApiDtos.*;
import com.gagastudio.finmate.api.error.ApiException;
import com.gagastudio.finmate.api.error.FieldErrorDetail;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ProductAppService implements FinancialDataProvider {
    private static final TypeReference<Map<String, Integer>> CATEGORY_MAP = new TypeReference<>() {
    };
    private static final TypeReference<List<CoachInsightV1>> INSIGHT_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<CoachRecommendationV1>> RECOMMENDATION_LIST = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final CoachProvider coachProvider;

    public ProductAppService(JdbcTemplate jdbc, ObjectMapper objectMapper, CoachProvider coachProvider) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.coachProvider = coachProvider;
    }

    @Transactional
    public void bootstrapUser(String userId, String displayName) {
        jdbc.update("""
                INSERT INTO users (id, email, password_hash, display_name, onboarding_completed)
                VALUES (?, ?, 'seed-user', ?, TRUE)
                ON CONFLICT (id) DO NOTHING
                """, userId, userId + "@finmate.local", displayName);
        insertSeedFriend("friend-jiwoo", "jiwoo@finmate.local", "지우");
        insertSeedFriend("friend-minsu", "minsu@finmate.local", "민수");
        insertSeedFriend("friend-seoyeon", "seoyeon@finmate.local", "서연");
        insertSeedFriend("friend-jaehun", "jaehun@finmate.local", "재훈");

        jdbc.update("""
                INSERT INTO user_profiles (user_id, age_band, income_band, job_category, household_type, money_style, area, goal_type)
                VALUES (?, '20대', '3,000만원 ~ 4,000만원', 'IT/개발', '1인가구', '안정 추구형', '서울 강남권', 'EMERGENCY_FUND')
                ON CONFLICT (user_id) DO NOTHING
                """, userId);
        jdbc.update("""
                INSERT INTO privacy_settings (user_id, anonymous_portfolio_opt_in, friend_share_default, exposed_fields)
                VALUES (?, TRUE, 'MISSION_ONLY', 'ageBand,goalType,financialSummary')
                ON CONFLICT (user_id) DO NOTHING
                """, userId);
        jdbc.update("""
                INSERT INTO point_wallets (user_id, point_balance, virtual_money_balance)
                VALUES (?, 2450, 100000)
                ON CONFLICT (user_id) DO NOTHING
                """, userId);
        jdbc.update("""
                INSERT INTO financial_snapshots (
                  id, user_id, month, monthly_income, monthly_spending, monthly_saving,
                  investment_value, cash_like_assets, emergency_fund_months, categories_json, lifestyle_tags
                )
                VALUES (?, ?, '2026-06', 3200000, 1680000, 620000, 1200000, 400000, 0.4, ?, '식비절약,비상금,또래비교')
                ON CONFLICT (user_id, month) DO NOTHING
                """, "snapshot-" + userId, userId, json(Map.of("식비", 6200, "교통비", 1200, "카페/간식", 600, "기타", 1800)));

        upsertMission(userId, "mission-food", "내일 식비 10,000원 이하 사용하기", "하루 식비를 낮춰 남는 금액을 비상금으로 옮겨요.", "ACTIVE", "EASY", 120, 78, "RULE_BASED_FALLBACK");
        upsertMission(userId, "mission-cafe", "카페 지출 줄이기", "이번 주 카페 2회 이하 이용", "ACTIVE", "EASY", 100, 50, "RULE_BASED_FALLBACK");
        upsertMission(userId, "mission-fixed-cost", "고정 지출 5% 줄이기", "구독과 자동결제를 점검해 반복 지출을 낮춰요.", "ACTIVE", "NORMAL", 180, 45, "RULE_BASED_FALLBACK");
        upsertMission(userId, "mission-saving", "저축하기 습관 만들기", "3일 연속 저축 성공", "ACTIVE", "EASY", 200, 66, "RULE_BASED_FALLBACK");

        jdbc.update("""
                INSERT INTO daily_records (id, user_id, record_date, budget, spent, category_spending_json, mission_status, point_delta)
                VALUES (?, ?, DATE '2026-06-12', 10000, 7800, ?, 'IN_PROGRESS', 0)
                ON CONFLICT (user_id, record_date) DO NOTHING
                """, "record-" + userId + "-2026-06-12", userId, json(Map.of("식비", 6200, "교통비", 1200, "카페/간식", 600, "기타", 1800)));

        upsertFriendship(userId, "friend-jiwoo");
        upsertFriendship(userId, "friend-minsu");
        upsertFriendship(userId, "friend-seoyeon");
        upsertFriendship(userId, "friend-jaehun");
        upsertFeed(userId, "feed-" + userId + "-1", "friend-minsu", "MISSION", "민수가 카페 지출 줄이기를 완료했어요", "이번 주 카페 2회 이하 미션 성공", null);
        upsertFeed(userId, "feed-" + userId + "-2", "friend-seoyeon", "SAVING", "서연이 비상금 목표를 70% 채웠어요", "비슷한 또래의 저축 루틴이 올라왔어요", null);
        upsertFeed(userId, "feed-" + userId + "-3", "friend-jiwoo", "BIRTHDAY", "지우님의 생일 펀드가 열렸어요", "친구들이 함께 모으는 생일 축하 펀드", 72000);

        jdbc.update("""
                INSERT INTO birthday_funds (id, owner_user_id, title, target_amount, current_amount, due_date, status, share_code)
                VALUES ('fund-jiwoo', 'friend-jiwoo', '지우님의 생일 펀드', 100000, 72000, DATE '2026-06-15', 'OPEN', 'JIWOO-2026')
                ON CONFLICT (id) DO NOTHING
                """);
    }

    @Transactional
    public UserMeResponse completeOnboarding(String userId, ProductOnboardingRequest request) {
        bootstrapUser(userId, displayName(userId));
        jdbc.update("""
                UPDATE user_profiles
                SET goal_type = ?, money_style = ?, household_type = ?, area = ?, updated_at = now()
                WHERE user_id = ?
                """, request.goalType(), request.moneyStyle(), request.householdType(), request.area(), userId);
        jdbc.update("UPDATE users SET onboarding_completed = TRUE, updated_at = now() WHERE id = ?", userId);
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

    @Override
    public FinancialSnapshotV1 snapshotFor(String userId) {
        bootstrapUser(userId, displayName(userId));
        SnapshotRow snapshot = snapshot(userId);
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
        DailyRecordRow record = dailyRecord(userId);
        WalletRow wallet = wallet(userId);
        SnapshotRow snapshot = snapshot(userId);
        FundRow fund = fund("fund-jiwoo");
        return screen("home", "", "home", List.of(
                section("greeting", "greeting", user.displayName() + "님, 좋은 아침이에요!", "오늘도 똑똑한 금융 습관을 만들어가요.", null, null, null, null, null, null),
                section("birthday-alert", "actionCard", "지우님의 생일이 다가오고 있어요", "친구들이 함께 모으는 생일 펀드가 열렸어요.", "/birthdays/bday-jiwoo", "/assets/characters/finmate-birthday.png",
                        metrics(metric("현재 모금", won(fund.currentAmount()), "목표 " + won(fund.targetAmount()), "green", percent(fund.currentAmount(), fund.targetAmount())),
                                metric("가상머니", won(wallet.virtualMoneyBalance()), "참여 가능", "purple", null)),
                        null, actions(action("축하 펀드 참여하기", "/birthday-funds/fund-jiwoo/contribute", "GET", "primary", null)), null),
                missionHero(mission(userId, "mission-food")),
                budgetSection(record),
                spendingSection(record),
                assetSection(snapshot),
                section("points", "points", "포인트 지갑", null, null, null,
                        metrics(metric("보유 포인트", user.pointBalance() + "P", "가상머니 " + won(user.virtualMoneyBalance()), "purple", 62)),
                        null, null, null),
                followingSection(userId)
        ), Map.of("version", "product-mvp"));
    }

    public AppScreenResponse getHomeDetail(String userId, String detail) {
        return switch (detail) {
            case "budget" -> screen("home:budget", "오늘의 예산", "home", List.of(budgetSection(dailyRecord(userId))), Map.of());
            case "spending" -> screen("home:spending", "오늘의 지출 요약", "home", List.of(spendingSection(dailyRecord(userId))), Map.of());
            case "assets" -> screen("home:assets", "자산 현황", "home", List.of(assetSection(snapshot(userId))), Map.of());
            case "following" -> screen("home:following", "팔로잉 금융 근황", "home", List.of(followingSection(userId), feedSection(userId)), Map.of());
            case "mission" -> getMission(userId, "mission-food");
            default -> throw validation("detail", "Unsupported home detail.");
        };
    }

    public AppScreenResponse getCompare(String userId) {
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
        CoachResultV1 coach = latestOrFallbackCoach(userId);
        return screen("compare:cmp-001", "비교 결과", "compare", List.of(
                section("result", "coach", userName(userId) + "님, 또래와 비교해봤어요!", coach.summary(), null, "/assets/characters/finmate-main.png",
                        metrics(metric("나의 종합 점수", coach.score() + "점", "상위 40%", "purple", coach.score())), null,
                        actions(action("AI 코치의 분석 보기", "/compare/coach", "GET", "primary", null)), null),
                compareBarsSection(coach)
        ), Map.of("comparisonId", comparisonId));
    }

    public AppScreenResponse getCoachFlow(String userId, String comparisonId) {
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
        List<MissionRow> missions = missions(userId);
        return screen("missions", "미션", "mission", List.of(
                missionHero(mission(userId, "mission-food")),
                section("active", "list", "진행 중인 미션", null, null, null, null,
                        missions.stream().filter(m -> !"COMPLETED".equals(m.status())).map(this::missionItem).toList(),
                        null, null),
                section("points", "points", "나의 포인트", null, null, null,
                        metrics(metric("보유 포인트", wallet(userId).pointBalance() + "P", "실천 완료 시 자동 적립", "purple", 62)), null, null, null)
        ), Map.of());
    }

    public AppScreenResponse getMission(String userId, String missionId) {
        if ("mission-invest".equals(missionId)) {
            return screen("missions:mission-invest", "나의 계획 세우기", "mission", List.of(
                    section("plan", "choiceList", "AI 코치의 제안을 내 계획으로 만들어볼까요?", "이번 달 나의 핵심 목표는?", null, null, null,
                            items(item("mission-food", "식비 절약 시작하기", "선택됨", null, null, "check", "purple", "/missions/mission-food"),
                                    item("mission-saving", "비상금 3개월치 모으기", "자동 저축 유지", null, null, "saving", "green", "/missions/mission-saving"),
                                    item("mission-fixed-cost", "고정 지출 5% 줄이기", "구독 점검", null, null, "spend", "orange", "/missions/mission-fixed-cost")),
                            actions(action("계획 저장하기", "/missions/mission-food", "GET", "primary", null)), null)
            ), Map.of("missionId", missionId));
        }
        MissionRow mission = mission(userId, missionId);
        return screen("missions:" + missionId, mission.title(), "mission", List.of(
                missionHero(mission),
                section("guide", "checkList", "오늘의 행동 가이드", null, null, null, null,
                        items(item("step1", "오늘 쓴 금액을 확인해요.", "기록 탭에서 예산 대비 사용액을 봐요.", null, null, "check", "green", null),
                                item("step2", "작은 행동 하나를 먼저 실천해요.", mission.description(), null, null, "check", "purple", null),
                                item("step3", "완료 후 포인트를 기록해요.", "실천 기록은 피드백과 포인트로 남아요.", null, null, "saving", "green", null)),
                        null, null)
        ), Map.of("missionId", missionId));
    }

    @Transactional
    public AppActionResultResponse submitMissionFeedback(String userId, String missionId, AppMissionFeedbackRequest request) {
        MissionRow mission = mission(userId, missionId);
        if (!"DONE".equals(request.status())) {
            throw validation("status", "Only DONE feedback is supported.");
        }
        int reward = "COMPLETED".equals(mission.status()) ? 0 : mission.rewardPoints();
        if (reward > 0) {
            jdbc.update("UPDATE missions SET status = 'COMPLETED', progress = 100, completed_at = now() WHERE id = ?", mission.dbId());
            addPoints(userId, reward, "MISSION", missionId, mission.title() + " 완료");
            jdbc.update("""
                    UPDATE daily_records
                    SET mission_status = 'SUCCESS', point_delta = point_delta + ?
                    WHERE user_id = ? AND record_date = DATE '2026-06-12'
                    """, reward, userId);
            upsertFeed(userId, "feed-" + userId + "-mission-" + missionId, userId, "MISSION", mission.title() + " 완료", "오늘의 금융 루틴을 실천했어요.", null);
        }
        jdbc.update("""
                INSERT INTO mission_events (id, mission_id, user_id, event_type, note, reward_points)
                VALUES (?, ?, ?, 'DONE', ?, ?)
                """, "event-" + UUID.randomUUID(), mission.dbId(), userId, request.note(), reward);
        return new AppActionResultResponse("RECORDED", "오늘 실천을 기록했어요", reward + "P가 포인트 지갑에 반영됐습니다.", "/missions/" + missionId + "/feedback", Map.of("rewardPoints", reward, "pointBalance", wallet(userId).pointBalance()));
    }

    public AppScreenResponse getRecords(String userId, String month) {
        DailyRecordRow record = dailyRecord(userId);
        return screen("records:2026-06", "기록", "records", List.of(
                section("calendar", "calendar", "2026년 6월", "날짜별 지출과 미션 성공 기록", "/records/2026-06-12", null, null,
                        items(item("2026-06-12", "12", won(record.spent()), null, record.missionStatus(), "calendar", "green", "/records/2026-06-12"),
                                item("history", "월간 히스토리", "실천 기록 보기", null, null, "records", "purple", "/records/history"),
                                item("stats", "포인트 통계", wallet(userId).pointBalance() + "P", null, null, "wallet", "green", "/records/stats")),
                        null, null),
                budgetSection(record)
        ), Map.of("month", month == null ? "2026-06" : month));
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
        DailyRecordRow record = dailyRecord(userId);
        return screen("records:" + date, "날짜별 기록", "records", List.of(budgetSection(record), spendingSection(record), feedSection(userId)), Map.of("date", date));
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
            case "privacy" -> privacyScreen(userId);
            default -> throw validation("section", "Unsupported profile section.");
        };
    }

    public AppScreenResponse getBirthdays(String userId) {
        bootstrapUser(userId, displayName(userId));
        FundRow fund = fund("fund-jiwoo");
        return screen("birthdays", "생일 펀드", "home", List.of(
                section("fund", "birthday", "지우님의 생일 펀드", "친구의 생일을 함께 축하하며 모아주는 특별한 선물이에요.", "/birthdays/bday-jiwoo", "/assets/characters/finmate-birthday.png",
                        metrics(metric("모금 금액", won(fund.currentAmount()), "목표 " + won(fund.targetAmount()), "green", percent(fund.currentAmount(), fund.targetAmount())),
                                metric("남은 기간", "D-7", fund.dueDate().toString(), "purple", null)),
                        null, actions(action("참여하기", "/birthday-funds/fund-jiwoo/contribute", "GET", "primary", null), action("내 생일 펀드 열기", "/birthday-funds/me/open", "GET", "secondary", null)), null)
        ), Map.of());
    }

    public AppScreenResponse getBirthdayFlow(String userId, String birthdayId) {
        if (!"bday-jiwoo".equals(birthdayId)) {
            throw validation("birthdayId", "Unsupported birthdayId.");
        }
        FundRow fund = fund("fund-jiwoo");
        return screen("birthdays:bday-jiwoo", "지우님의 생일", "home", List.of(
                section("fund", "birthday", "생일 축하 펀드란?", "친구의 생일을 함께 축하하며 모아주는 특별한 선물이에요.", null, "/assets/characters/finmate-birthday.png",
                        metrics(metric("모금 금액", won(fund.currentAmount()), "목표 " + won(fund.targetAmount()), "green", percent(fund.currentAmount(), fund.targetAmount())),
                                metric("남은 기간", "D-7", fund.dueDate().toString(), "purple", null)),
                        null, actions(action("참여하기", "/birthday-funds/fund-jiwoo/contribute", "GET", "primary", null)), null),
                section("participants", "list", "실시간 참여 현황", null, null, null, null,
                        items(item("p1", "나", "참여 가능", won(10000), null, "profile", "purple", null),
                                item("p2", "민수", "축하 메시지와 함께 참여", won(10000), null, "profile", "green", null),
                                item("p3", "서연", "따뜻한 메시지", won(5000), null, "profile", "purple", null)),
                        null, null)
        ), Map.of("birthdayId", birthdayId));
    }

    @Transactional
    public AppActionResultResponse contributeBirthdayFund(String userId, String fundId, AppBirthdayContributionRequest request) {
        bootstrapUser(userId, displayName(userId));
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
        FundRow fund = fund(fundId);
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

    private void insertSeedFriend(String id, String email, String displayName) {
        jdbc.update("""
                INSERT INTO users (id, email, password_hash, display_name, onboarding_completed)
                VALUES (?, ?, 'seed-user', ?, TRUE)
                ON CONFLICT (id) DO NOTHING
                """, id, email, displayName);
        jdbc.update("INSERT INTO point_wallets (user_id, point_balance, virtual_money_balance) VALUES (?, 1800, 100000) ON CONFLICT (user_id) DO NOTHING", id);
    }

    private void upsertMission(String userId, String missionId, String title, String description, String status, String difficulty, int rewardPoints, int progress, String source) {
        jdbc.update("""
                INSERT INTO missions (id, user_id, title, description, status, difficulty, reward_points, progress, due_date, source)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, DATE '2026-06-30', ?)
                ON CONFLICT (id) DO NOTHING
                """, missionDbId(userId, missionId), userId, title, description, status, difficulty, rewardPoints, progress, source);
    }

    private void upsertFriendship(String userId, String friendId) {
        jdbc.update("""
                INSERT INTO friendships (id, follower_id, followee_id, status)
                VALUES (?, ?, ?, 'ACTIVE')
                ON CONFLICT (follower_id, followee_id) DO NOTHING
                """, "friendship-" + userId + "-" + friendId, userId, friendId);
    }

    private void upsertFeed(String userId, String id, String actorUserId, String kind, String title, String body, Integer amount) {
        jdbc.update("""
                INSERT INTO feed_items (id, user_id, actor_user_id, kind, title, body, amount)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """, id, userId, actorUserId, kind, title, body, amount);
    }

    private void addPoints(String userId, int points, String referenceType, String referenceId, String description) {
        jdbc.update("UPDATE point_wallets SET point_balance = point_balance + ?, updated_at = now() WHERE user_id = ?", points, userId);
        addPointTransaction(userId, "POINT", points, wallet(userId).pointBalance(), referenceType, referenceId, description);
    }

    private void addPointTransaction(String userId, String type, int amount, int balanceAfter, String referenceType, String referenceId, String description) {
        jdbc.update("""
                INSERT INTO point_transactions (id, user_id, type, amount, balance_after, reference_type, reference_id, description)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, "ptx-" + UUID.randomUUID(), userId, type, amount, balanceAfter, referenceType, referenceId, description);
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
        bootstrapUser(userId, displayName(userId));
        return jdbc.queryForObject("""
                SELECT * FROM financial_snapshots WHERE user_id = ? AND month = '2026-06'
                """, (rs, rowNum) -> new SnapshotRow(
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
    }

    private DailyRecordRow dailyRecord(String userId) {
        bootstrapUser(userId, displayName(userId));
        return jdbc.queryForObject("SELECT * FROM daily_records WHERE user_id = ? AND record_date = DATE '2026-06-12'",
                (rs, rowNum) -> new DailyRecordRow(
                        rs.getDate("record_date").toLocalDate(),
                        rs.getInt("budget"),
                        rs.getInt("spent"),
                        rs.getString("category_spending_json"),
                        rs.getString("mission_status"),
                        rs.getInt("point_delta")
                ), userId);
    }

    private MissionRow mission(String userId, String missionId) {
        bootstrapUser(userId, displayName(userId));
        return jdbc.queryForObject("SELECT * FROM missions WHERE id = ?",
                (rs, rowNum) -> missionRow(rs, missionId), missionDbId(userId, missionId));
    }

    private List<MissionRow> missions(String userId) {
        bootstrapUser(userId, displayName(userId));
        return jdbc.query("SELECT * FROM missions WHERE user_id = ? ORDER BY created_at",
                (rs, rowNum) -> missionRow(rs, rs.getString("id").substring((userId + ":").length())), userId);
    }

    private MissionRow missionRow(ResultSet rs, String routeId) throws java.sql.SQLException {
        return new MissionRow(
                rs.getString("id"),
                routeId,
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("status"),
                rs.getString("difficulty"),
                rs.getInt("reward_points"),
                rs.getInt("progress")
        );
    }

    private WalletRow wallet(String userId) {
        bootstrapUser(userId, displayName(userId));
        return jdbc.queryForObject("SELECT point_balance, virtual_money_balance FROM point_wallets WHERE user_id = ?",
                (rs, rowNum) -> new WalletRow(rs.getInt("point_balance"), rs.getInt("virtual_money_balance")), userId);
    }

    private FundRow fund(String fundId) {
        return jdbc.queryForObject("SELECT * FROM birthday_funds WHERE id = ?",
                (rs, rowNum) -> new FundRow(
                        rs.getString("id"),
                        rs.getString("title"),
                        rs.getInt("target_amount"),
                        rs.getInt("current_amount"),
                        rs.getDate("due_date").toLocalDate(),
                        rs.getString("status")
                ), fundId);
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
        return section("feed", "list", "친구 피드", null, null, null, null, feed, null, null);
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
        return section("mission-hero", "missionHero", mission.title(), "오늘의 미션", "/missions/" + mission.routeId(), null,
                metrics(metric("진행률", mission.progress() + "%", mission.rewardPoints() + "P 보상", "purple", mission.progress())),
                null, actions(action("오늘 실천 기록하기", "/missions/" + mission.routeId() + "/feedback", "POST", "primary", "mission-feedback")), null);
    }

    private AppItem missionItem(MissionRow mission) {
        return item(mission.routeId(), mission.title(), mission.description(), mission.progress() + "%", "+" + mission.rewardPoints() + "P", "target", "purple", "/missions/" + mission.routeId());
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
            case "교통비" -> "bus";
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

    private record SnapshotRow(String id, String month, int monthlyIncome, int monthlySpending, int monthlySaving, int investmentValue, int cashLikeAssets, double emergencyFundMonths, String categoriesJson, String lifestyleTags) {
    }

    private record DailyRecordRow(LocalDate date, int budget, int spent, String categorySpendingJson, String missionStatus, int pointDelta) {
    }

    private record MissionRow(String dbId, String routeId, String title, String description, String status, String difficulty, int rewardPoints, int progress) {
    }

    private record WalletRow(int pointBalance, int virtualMoneyBalance) {
    }

    private record FundRow(String id, String title, int targetAmount, int currentAmount, LocalDate dueDate, String status) {
    }

    private record PrivacyRow(String exposedFields) {
    }
}
