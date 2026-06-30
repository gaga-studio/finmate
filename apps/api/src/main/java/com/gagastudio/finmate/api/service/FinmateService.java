package com.gagastudio.finmate.api.service;

import com.gagastudio.finmate.api.dto.ApiDtos.*;
import com.gagastudio.finmate.api.error.ApiException;
import com.gagastudio.finmate.api.error.FieldErrorDetail;
import com.gagastudio.finmate.api.store.SeedStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FinmateService {
    public static final String ONBOARDING_TOKEN = "onb-token-001";
    public static final String ACCESS_TOKEN = "demo-token";

    private static final String DEMO_USER_ID = "demo-user-001";
    private static final String DIAGNOSIS_ID = "diag-001";
    private static final String MYDATA_CONNECTION_ID = "mydata-mock-001";
    private static final String PRIVACY_SETTINGS_ID = "privacy-001";
    private static final String PEER_PORTFOLIO_ID = "peer-portfolio-023";
    private static final String OWN_PORTFOLIO_ID = "own-portfolio-001";
    private static final String COMPARISON_ID = "cmp-001";
    private static final String SIMULATION_ID = "sim-001";
    private static final String MISSION_ID = "mis-001";
    private static final String MISSION_TEMPLATE_ID = "MISSION_AUTO_TRANSFER_SMALL";
    private static final String LOCAL_DATE = "2026-06-30";

    private final SeedStore store;

    public FinmateService(SeedStore store) {
        this.store = store;
    }

    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    public OnboardingDiagnosisResponse createDiagnosis(OnboardingDiagnosisRequest request) {
        validateEquals("occupationStatus", request.occupationStatus(), "PART_TIME_STUDENT");
        validateEquals("incomeBand", request.incomeBand(), "INCOME_150_250");
        validateEquals("householdType", request.householdType(), "SINGLE");
        validateEquals("goalType", request.goalType(), "EMERGENCY_FUND");
        validateEquals("painPoint", request.painPoint(), "SAVE_CONSISTENTLY");

        Map<String, Object> diagnosis = requireSeed("onboarding-diagnoses.json", DIAGNOSIS_ID);
        Map<String, Object> session = requireSeed("onboarding-sessions.json", "onb-session-001");
        return new OnboardingDiagnosisResponse(
                diagnosis.get("id").toString(),
                session.get("onboardingToken").toString(),
                diagnosis.get("goalType").toString(),
                diagnosis.get("recommendedPersonaId").toString(),
                diagnosis.get("cohortLabel").toString(),
                session.get("expiresAt").toString()
        );
    }

    public MockConsentResponse createMockConsent(MockConsentRequest request) {
        validateEquals("diagnosisId", request.diagnosisId(), DIAGNOSIS_ID);
        validateEquals("consentVersion", request.consentVersion(), "mydata-mock-v1.0");
        Map<String, Object> connection = requireSeed("mydata-connections.json", MYDATA_CONNECTION_ID);
        return new MockConsentResponse(
                connection.get("id").toString(),
                connection.get("status").toString(),
                connection.get("dataMode").toString(),
                connection.get("agreedAt").toString()
        );
    }

    public PrivacyConsentsResponse createPrivacyConsents(PrivacyConsentsRequest request) {
        validateEquals("friendShareDefault", request.friendShareDefault(), "NONE");
        validateEquals("consentVersion", request.consentVersion(), "privacy-v1.0");
        if (!Boolean.TRUE.equals(request.anonymousPortfolioOptIn())) {
            throw validation("anonymousPortfolioOptIn", "anonymousPortfolioOptIn must be true for the P0 demo.");
        }
        validateExposedFields(request.exposedFields());
        return new PrivacyConsentsResponse(
                PRIVACY_SETTINGS_ID,
                List.of("consent-privacy-001", "consent-portfolio-001"),
                true
        );
    }

    public DemoSessionResponse createDemoSession(DemoSessionRequest request) {
        if ("QUICK_DIAGNOSIS".equals(request.mode())) {
            validateEquals("diagnosisId", request.diagnosisId(), DIAGNOSIS_ID);
            validateEquals("mydataConnectionId", request.mydataConnectionId(), MYDATA_CONNECTION_ID);
        } else if ("SAMPLE_PERSONA".equals(request.mode())) {
            validateEquals("personaId", request.personaId(), "P001");
            validateEquals("goalType", request.goalType(), "EMERGENCY_FUND");
        } else {
            throw validation("mode", "mode must be QUICK_DIAGNOSIS or SAMPLE_PERSONA.");
        }

        Map<String, Object> session = store.mutable("onboarding-sessions.json", "onb-session-001");
        session.put("status", "COMPLETED");
        session.put("completedAt", "2026-06-30T15:05:00+09:00");
        return new DemoSessionResponse(
                DEMO_USER_ID,
                ACCESS_TOKEN,
                "P001",
                "EMERGENCY_FUND",
                "2026-06-30T16:00:00+09:00"
        );
    }

    public HomeResponse getHome() {
        Map<String, Object> feature = requireFeature(DEMO_USER_ID);
        Map<String, Object> peer = requirePortfolio(PEER_PORTFOLIO_ID);
        FinancialSummary peerSummary = summary(peer);
        double emergencyFundGapMonths = Math.abs(peerSummary.emergencyFundMonths() - number(feature.get("emergencyFundMonths")));
        Map<String, Object> template = requireSeed("mission-templates.json", MISSION_TEMPLATE_ID);
        return new HomeResponse(
                DEMO_USER_ID,
                new Goal("EMERGENCY_FUND", "비상금 1개월 만들기"),
                18000,
                new SpendingSummary(420000, number(feature.get("fixedCostRatio"))),
                new AssetSummary(integer(feature.get("cashLikeAssets")), number(feature.get("emergencyFundMonths"))),
                new TodayMissionCandidate(template.get("titleTemplate").toString(), "RULE_BASED"),
                new PeerTeaser(
                        PEER_PORTFOLIO_ID,
                        peer.get("displayName").toString(),
                        0.84,
                        "비상금 준비율이 " + round1(emergencyFundGapMonths) + "개월 차이"
                )
        );
    }

    public PortfolioResponse getPortfolio(String id) {
        Map<String, Object> portfolio = store.get("portfolios.json", id);
        if (portfolio == null) {
            throw notFound();
        }
        if (!"ACTIVE".equals(portfolio.get("status")) || !"PUBLIC".equals(portfolio.get("visibility"))) {
            throw portfolioNotAvailable();
        }
        return toPortfolioResponse(portfolio);
    }

    public ComparisonResponse createComparison(ComparisonRequest request) {
        validateEquals("peerPortfolioId", request.peerPortfolioId(), PEER_PORTFOLIO_ID);
        Map<String, Object> peer = requireActivePortfolio(request.peerPortfolioId());
        Map<String, Object> own = requireActivePortfolio(OWN_PORTFOLIO_ID);
        FinancialSummary ownSummary = summary(own);
        FinancialSummary peerSummary = summary(peer);

        return new ComparisonResponse(
                COMPARISON_ID,
                PEER_PORTFOLIO_ID,
                new MainGap("EMERGENCY_FUND", "비상금 준비율", 0.47),
                0.84,
                List.of(
                        new GapItem("EMERGENCY_FUND", ownSummary.emergencyFundMonths(), peerSummary.emergencyFundMonths(), "months"),
                        new GapItem("SAVINGS_RATE", ownSummary.savingsRate(), peerSummary.savingsRate(), "ratio"),
                        new GapItem("FIXED_COST_RATIO", ownSummary.fixedCostRatio(), peerSummary.fixedCostRatio(), "ratio")
                ),
                new ComparisonNextAction("3개월 시뮬레이션 보기", "FOLLOW_PEER_ROUTINE")
        );
    }

    public SimulationResponse createSimulation(SimulationRequest request) {
        validateEquals("comparisonId", request.comparisonId(), COMPARISON_ID);
        validateEquals("scenarioType", request.scenarioType(), "FOLLOW_PEER_ROUTINE");
        if (request.monthlyAdditionalSaving() != 100000) {
            throw validation("monthlyAdditionalSaving", "monthlyAdditionalSaving must be 100000 for the P0 demo.");
        }
        if (request.periodMonths() != 3) {
            throw validation("periodMonths", "periodMonths must be 3 for the P0 demo.");
        }

        Map<String, Object> feature = requireFeature(DEMO_USER_ID);
        int beforeCash = integer(feature.get("cashLikeAssets"));
        double beforeMonths = number(feature.get("emergencyFundMonths"));
        int afterCash = beforeCash + request.monthlyAdditionalSaving() * request.periodMonths();
        double afterMonths = round1(afterCash / number(feature.get("monthlyEssentialSpending")));
        return new SimulationResponse(
                SIMULATION_ID,
                COMPARISON_ID,
                "FOLLOW_PEER_ROUTINE",
                3,
                100000,
                orderedMap("emergencyFundMonths", beforeMonths, "cashLikeAssets", beforeCash),
                orderedMap("emergencyFundMonths", afterMonths, "cashLikeAssets", afterCash),
                "3개월 동안 매월 10만 원을 먼저 떼어두면 비상금 준비율이 0.4개월에서 0.7개월로 올라갑니다.",
                new SimulationNextAction("오늘의 미션 만들기", MISSION_TEMPLATE_ID),
                "이 시뮬레이션은 합성 데이터 기반 가정이며 금융상품 권유나 수익 보장이 아닙니다."
        );
    }

    public MissionResponse createMission(MissionRequest request) {
        validateEquals("simulationId", request.simulationId(), SIMULATION_ID);
        validateEquals("missionTemplateId", request.missionTemplateId(), MISSION_TEMPLATE_ID);
        validateEquals("triggerSource", request.triggerSource(), "SIMULATION");
        validateEquals("recommendationSource", request.recommendationSource(), "RULE_BASED");
        validateEquals("difficulty", request.difficulty(), "EASY");

        String key = request.simulationId() + ":" + request.missionTemplateId() + ":" + LOCAL_DATE;
        store.rememberMission(key, MISSION_ID);
        Map<String, Object> template = requireSeed("mission-templates.json", MISSION_TEMPLATE_ID);
        return new MissionResponse(
                MISSION_ID,
                template.get("titleTemplate").toString(),
                template.get("descriptionTemplate").toString(),
                template.get("defaultDifficulty").toString(),
                template.get("defaultVerificationType").toString(),
                integer(template.get("defaultRewardPoints")),
                "CREATED",
                LOCAL_DATE,
                new PrivacySharePreview("오늘 비상금 자동이체 미션을 시작했어요.", false)
        );
    }

    public PrivacySettingsResponse getPrivacySettings() {
        return toPrivacySettingsResponse(requireSeed("privacy-settings.json", PRIVACY_SETTINGS_ID));
    }

    public PrivacySettingsResponse updatePrivacySettings(PrivacySettingsPatchRequest request) {
        if (request == null || request.isEmpty()) {
            throw validation("body", "At least one privacy setting field is required.");
        }
        Map<String, Object> settings = store.mutable("privacy-settings.json", PRIVACY_SETTINGS_ID);
        if (request.anonymousPortfolioOptIn() != null) {
            settings.put("anonymousPortfolioOptIn", request.anonymousPortfolioOptIn());
        }
        if (request.friendShareDefault() != null) {
            validateFriendShareDefault(request.friendShareDefault());
            settings.put("friendShareDefault", request.friendShareDefault());
        }
        if (request.exposedFields() != null) {
            validateExposedFields(request.exposedFields());
            settings.put("exposedFields", new ArrayList<>(request.exposedFields()));
        }
        settings.put("updatedAt", "2026-06-30T15:20:00+09:00");
        return toPrivacySettingsResponse(settings);
    }

    public PrivacyWithdrawResponse withdrawPrivacy(PrivacyWithdrawRequest request) {
        if ("DATA_CONTRIBUTION".equals(request.scope())) {
            return new PrivacyWithdrawResponse("WITHDRAWN", "2026-06-30T15:25:00+09:00", List.of());
        }
        validateEquals("scope", request.scope(), "ANONYMOUS_PORTFOLIO");
        if (request.portfolioId() == null || request.portfolioId().isBlank()) {
            throw validation("portfolioId", "portfolioId is required when scope is ANONYMOUS_PORTFOLIO.");
        }
        validateEquals("portfolioId", request.portfolioId(), OWN_PORTFOLIO_ID);
        Map<String, Object> portfolio = store.mutable("portfolios.json", OWN_PORTFOLIO_ID);
        if (portfolio == null) {
            throw notFound();
        }
        portfolio.put("status", "WITHDRAWN");
        portfolio.put("visibility", "PRIVATE");
        portfolio.put("withdrawnAt", "2026-06-30T15:25:00+09:00");
        return new PrivacyWithdrawResponse("WITHDRAWN", "2026-06-30T15:25:00+09:00", List.of(OWN_PORTFOLIO_ID));
    }

    public void requireOnboardingToken(String authorization) {
        requireBearer(authorization, ONBOARDING_TOKEN);
    }

    public void requireAccessToken(String authorization) {
        requireBearer(authorization, ACCESS_TOKEN);
    }

    private void requireBearer(String authorization, String expectedToken) {
        if (authorization == null || !authorization.equals("Bearer " + expectedToken)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required.");
        }
    }

    private PortfolioResponse toPortfolioResponse(Map<String, Object> portfolio) {
        return new PortfolioResponse(
                portfolio.get("id").toString(),
                portfolio.get("displayName").toString(),
                portfolio.get("status").toString(),
                portfolio.get("visibility").toString(),
                portfolio.get("dataMode").toString(),
                summary(portfolio),
                routineCards(portfolio),
                stringList(portfolio.get("privacyBadges"))
        );
    }

    @SuppressWarnings("unchecked")
    private FinancialSummary summary(Map<String, Object> portfolio) {
        Map<String, Object> summary = (Map<String, Object>) portfolio.get("financialSummary");
        return new FinancialSummary(
                number(summary.get("emergencyFundMonths")),
                number(summary.get("savingsRate")),
                number(summary.get("fixedCostRatio"))
        );
    }

    @SuppressWarnings("unchecked")
    private List<RoutineCard> routineCards(Map<String, Object> portfolio) {
        List<Map<String, Object>> cards = (List<Map<String, Object>>) portfolio.get("routineCards");
        return cards.stream()
                .map(card -> new RoutineCard(card.get("title").toString(), card.get("description").toString()))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private PrivacySettingsResponse toPrivacySettingsResponse(Map<String, Object> settings) {
        Map<String, Object> preview = (Map<String, Object>) settings.get("preview");
        return new PrivacySettingsResponse(
                settings.get("id").toString(),
                Boolean.TRUE.equals(settings.get("anonymousPortfolioOptIn")),
                settings.get("friendShareDefault").toString(),
                settings.get("ownPortfolioId").toString(),
                stringList(settings.get("exposedFields")),
                new PrivacyPreview(
                        preview.get("portfolioId").toString(),
                        preview.get("displayName").toString(),
                        stringList(preview.get("hiddenFields"))
                ),
                settings.get("consentVersion").toString(),
                settings.get("updatedAt").toString()
        );
    }

    private Map<String, Object> requireSeed(String collection, String id) {
        Map<String, Object> item = store.get(collection, id);
        if (item == null) {
            throw notFound();
        }
        return item;
    }

    private Map<String, Object> requirePortfolio(String id) {
        Map<String, Object> portfolio = store.get("portfolios.json", id);
        if (portfolio == null) {
            throw notFound();
        }
        return portfolio;
    }

    private Map<String, Object> requireActivePortfolio(String id) {
        Map<String, Object> portfolio = requirePortfolio(id);
        if (!"ACTIVE".equals(portfolio.get("status")) || !"PUBLIC".equals(portfolio.get("visibility"))) {
            throw portfolioNotAvailable();
        }
        return portfolio;
    }

    private Map<String, Object> requireFeature(String userId) {
        Map<String, Object> feature = store.firstByField("feature-vectors.json", "userId", userId);
        if (feature == null) {
            throw notFound();
        }
        return feature;
    }

    private void validateEquals(String field, String actual, String expected) {
        if (!expected.equals(actual)) {
            throw validation(field, field + " must be " + expected + ".");
        }
    }

    private void validateFriendShareDefault(String value) {
        if (!List.of("NONE", "MISSION_ONLY", "ACHIEVEMENT_ONLY").contains(value)) {
            throw validation("friendShareDefault", "friendShareDefault must be NONE, MISSION_ONLY, or ACHIEVEMENT_ONLY.");
        }
    }

    private void validateExposedFields(List<String> fields) {
        List<String> allowed = List.of(
                "ageBand",
                "incomeBand",
                "occupationStatus",
                "householdType",
                "goalType",
                "financialSummary",
                "routineCards"
        );
        if (fields == null || fields.isEmpty()) {
            throw validation("exposedFields", "exposedFields must contain at least one field.");
        }
        for (String field : fields) {
            if (!allowed.contains(field)) {
                throw validation("exposedFields", "Unsupported exposed field: " + field);
            }
        }
    }

    private ApiException validation(String field, String message) {
        return new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "VALIDATION_ERROR",
                "Request validation failed.",
                List.of(new FieldErrorDetail(field, message))
        );
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found.");
    }

    private ApiException portfolioNotAvailable() {
        return new ApiException(HttpStatus.GONE, "PORTFOLIO_NOT_AVAILABLE", "This portfolio is no longer available.");
    }

    private List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of();
    }

    private int integer(Object value) {
        return ((Number) value).intValue();
    }

    private double number(Object value) {
        return ((Number) value).doubleValue();
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private Map<String, Number> orderedMap(String firstKey, Number firstValue, String secondKey, Number secondValue) {
        Map<String, Number> map = new LinkedHashMap<>();
        map.put(firstKey, firstValue);
        map.put(secondKey, secondValue);
        return map;
    }
}
