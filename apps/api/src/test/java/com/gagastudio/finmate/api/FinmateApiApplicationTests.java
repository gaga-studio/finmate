package com.gagastudio.finmate.api;

import com.gagastudio.finmate.api.store.SeedStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FinmateApiApplicationTests {
    private static final String ONBOARDING_AUTH = "Bearer onb-token-001";
    private static final String ACCESS_AUTH = "Bearer demo-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SeedStore seedStore;

    @BeforeEach
    void resetSeedState() {
        seedStore.reset();
    }

    @Test
    void healthReturnsOk() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void p0HappyPathAndPrivacyWithdrawWork() throws Exception {
        mockMvc.perform(post("/api/onboarding/diagnosis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "occupationStatus": "PART_TIME_STUDENT",
                                  "incomeBand": "INCOME_150_250",
                                  "householdType": "SINGLE",
                                  "goalType": "EMERGENCY_FUND",
                                  "painPoint": "SAVE_CONSISTENTLY"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diagnosisId").value("diag-001"))
                .andExpect(jsonPath("$.onboardingToken").value("onb-token-001"));

        mockMvc.perform(post("/api/mydata/mock-consent")
                        .header("Authorization", ONBOARDING_AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "diagnosisId": "diag-001",
                                  "consentVersion": "mydata-mock-v1.0",
                                  "scopes": ["ACCOUNT_SUMMARY", "CARD_SPENDING", "INVESTMENT_SUMMARY"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mydataConnectionId").value("mydata-mock-001"))
                .andExpect(jsonPath("$.status").value("CONNECTED"));

        mockMvc.perform(post("/api/privacy/consents")
                        .header("Authorization", ONBOARDING_AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "anonymousPortfolioOptIn": true,
                                  "friendShareDefault": "NONE",
                                  "exposedFields": ["ageBand", "incomeBand", "goalType", "financialSummary", "routineCards"],
                                  "consentVersion": "privacy-v1.0"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.privacySettingsId").value("privacy-001"))
                .andExpect(jsonPath("$.previewAvailable").value(true));

        mockMvc.perform(post("/api/demo/session")
                        .header("Authorization", ONBOARDING_AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mode": "QUICK_DIAGNOSIS",
                                  "diagnosisId": "diag-001",
                                  "mydataConnectionId": "mydata-mock-001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("demo-user-001"))
                .andExpect(jsonPath("$.accessToken").value("demo-token"));

        mockMvc.perform(get("/api/home").header("Authorization", ACCESS_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.peerTeaser.portfolioId").value("peer-portfolio-023"))
                .andExpect(jsonPath("$.peerTeaser.mainDifference").value("비상금 준비율이 1.4개월 차이"))
                .andExpect(jsonPath("$.todayMissionCandidate.recommendationSource").value("RULE_BASED"));

        mockMvc.perform(get("/api/explore/portfolios/peer-portfolio-023").header("Authorization", ACCESS_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portfolioId").value("peer-portfolio-023"))
                .andExpect(jsonPath("$.routineCards[0].title").value("월급 다음 날 10만 원 자동이체"));

        mockMvc.perform(post("/api/comparisons")
                        .header("Authorization", ACCESS_AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "peerPortfolioId": "peer-portfolio-023"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comparisonId").value("cmp-001"))
                .andExpect(jsonPath("$.mainGap.type").value("EMERGENCY_FUND"))
                .andExpect(jsonPath("$.mainGap.normalizedGap").value(0.47))
                .andExpect(jsonPath("$.similarityScore").value(0.84));

        mockMvc.perform(post("/api/simulations")
                        .header("Authorization", ACCESS_AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "comparisonId": "cmp-001",
                                  "scenarioType": "FOLLOW_PEER_ROUTINE",
                                  "monthlyAdditionalSaving": 100000,
                                  "periodMonths": 3
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulationId").value("sim-001"))
                .andExpect(jsonPath("$.after.cashLikeAssets").value(700000))
                .andExpect(jsonPath("$.after.emergencyFundMonths").value(0.7));

        mockMvc.perform(post("/api/missions")
                        .header("Authorization", ACCESS_AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "simulationId": "sim-001",
                                  "missionTemplateId": "MISSION_AUTO_TRANSFER_SMALL",
                                  "triggerSource": "SIMULATION",
                                  "recommendationSource": "RULE_BASED",
                                  "difficulty": "EASY"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missionId").value("mis-001"))
                .andExpect(jsonPath("$.title").value("이번 달 비상금 자동이체 10만 원 설정하기"));

        mockMvc.perform(get("/api/privacy/settings").header("Authorization", ACCESS_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownPortfolioId").value("own-portfolio-001"))
                .andExpect(jsonPath("$.exposedFields", hasItem("financialSummary")));

        mockMvc.perform(patch("/api/privacy/settings")
                        .header("Authorization", ACCESS_AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "friendShareDefault": "MISSION_ONLY",
                                  "exposedFields": ["ageBand", "goalType", "financialSummary"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.friendShareDefault").value("MISSION_ONLY"))
                .andExpect(jsonPath("$.anonymousPortfolioOptIn").value(true));

        mockMvc.perform(post("/api/privacy/withdraw")
                        .header("Authorization", ACCESS_AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scope": "ANONYMOUS_PORTFOLIO",
                                  "portfolioId": "own-portfolio-001",
                                  "reason": "DEMO_PRIVACY_CHECK"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.affectedPortfolioIds[0]").value("own-portfolio-001"));

        mockMvc.perform(get("/api/explore/portfolios/own-portfolio-001").header("Authorization", ACCESS_AUTH))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("PORTFOLIO_NOT_AVAILABLE"));

        mockMvc.perform(get("/api/explore/portfolios/peer-portfolio-023").header("Authorization", ACCESS_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portfolioId").value("peer-portfolio-023"));
    }

    @Test
    void p1AppExperienceHappyPathWorks() throws Exception {
        mockMvc.perform(get("/api/app/home").header("Authorization", ACCESS_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screenId").value("home"))
                .andExpect(jsonPath("$.sections[0].kind").value("greeting"))
                .andExpect(jsonPath("$.sections[1].id").value("birthday-alert"));

        mockMvc.perform(get("/api/app/home/budget").header("Authorization", ACCESS_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screenId").value("home:budget"))
                .andExpect(jsonPath("$.sections[0].metrics[2].value").value("₩2,200"));

        mockMvc.perform(get("/api/app/compare/filter").header("Authorization", ACCESS_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screenId").value("compare:filter"));

        mockMvc.perform(post("/api/app/compare/filter/search")
                        .header("Authorization", ACCESS_AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ageBand": "20대",
                                  "incomeBand": "3,000만원 ~ 4,000만원",
                                  "jobCategory": "IT/개발",
                                  "moneyStyle": "안정 추구형",
                                  "area": "서울 강남권"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screenId").value("compare:filter-results"))
                .andExpect(jsonPath("$.sections[1].items", hasSize(3)));

        mockMvc.perform(get("/api/app/compare/results/cmp-001").header("Authorization", ACCESS_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screenId").value("compare:cmp-001"));

        mockMvc.perform(get("/api/app/compare/cmp-001/coach-flow").header("Authorization", ACCESS_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screenId").value("compare:coach-flow"))
                .andExpect(jsonPath("$.heroAsset").value("/assets/characters/finmate-coach.png"));

        mockMvc.perform(get("/api/app/missions/mission-food").header("Authorization", ACCESS_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screenId").value("missions:mission-food"));

        mockMvc.perform(get("/api/app/missions/mission-fixed-cost").header("Authorization", ACCESS_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screenId").value("missions:mission-fixed-cost"));

        mockMvc.perform(post("/api/app/missions/mission-food/feedback")
                        .header("Authorization", ACCESS_AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "DONE",
                                  "note": "오늘 목표 완료"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECORDED"))
                .andExpect(jsonPath("$.nextPath").value("/missions/mission-food/feedback"));

        mockMvc.perform(post("/api/app/missions/mission-fixed-cost/feedback")
                        .header("Authorization", ACCESS_AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "DONE",
                                  "note": "구독을 정리했어요"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECORDED"))
                .andExpect(jsonPath("$.nextPath").value("/missions/mission-fixed-cost/feedback"));

        mockMvc.perform(get("/api/app/records?month=2026-06").header("Authorization", ACCESS_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screenId").value("records:2026-06"));

        mockMvc.perform(get("/api/app/records/2026-06-12").header("Authorization", ACCESS_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screenId").value("records:2026-06-12"));

        mockMvc.perform(get("/api/app/profile/sections/followers").header("Authorization", ACCESS_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screenId").value("profile:followers"));

        mockMvc.perform(get("/api/app/birthdays/bday-jiwoo/flow").header("Authorization", ACCESS_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screenId").value("birthdays:bday-jiwoo"));

        mockMvc.perform(post("/api/app/birthday-funds/fund-jiwoo/contributions")
                        .header("Authorization", ACCESS_AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 10000,
                                  "message": "지우야 생일 축하해!",
                                  "anonymous": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.nextPath").value("/birthday-funds/fund-jiwoo/complete"));

        mockMvc.perform(get("/api/app/birthday-funds/fund-jiwoo/complete").header("Authorization", ACCESS_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screenId").value("birthday-funds:fund-jiwoo:status"));

        mockMvc.perform(get("/api/app/birthday-funds/me/open").header("Authorization", ACCESS_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screenId").value("birthday-funds:me:open"));

        mockMvc.perform(post("/api/app/birthday-funds/me/open").header("Authorization", ACCESS_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPENED"));

        mockMvc.perform(post("/api/app/birthday-funds/me/share").header("Authorization", ACCESS_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHARED"));

        mockMvc.perform(get("/api/app/birthday-funds/me/status").header("Authorization", ACCESS_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screenId").value("birthday-funds:me:status"));
    }

    @Test
    void rejectsMissingAccessToken() throws Exception {
        mockMvc.perform(get("/api/home"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void rejectsWrongTokens() throws Exception {
        mockMvc.perform(post("/api/mydata/mock-consent")
                        .header("Authorization", "Bearer wrong-onboarding-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "diagnosisId": "diag-001",
                                  "consentVersion": "mydata-mock-v1.0",
                                  "scopes": ["ACCOUNT_SUMMARY"]
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/home").header("Authorization", "Bearer wrong-access-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void rejectsEmptyPrivacyPatch() throws Exception {
        mockMvc.perform(patch("/api/privacy/settings")
                        .header("Authorization", ACCESS_AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsInvalidExposedFields() throws Exception {
        mockMvc.perform(patch("/api/privacy/settings")
                        .header("Authorization", ACCESS_AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "exposedFields": ["ageBand", "merchantName"]
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsPeerPortfolioWithdrawTarget() throws Exception {
        mockMvc.perform(post("/api/privacy/withdraw")
                        .header("Authorization", ACCESS_AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scope": "ANONYMOUS_PORTFOLIO",
                                  "portfolioId": "peer-portfolio-023"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void dataContributionWithdrawDoesNotChangePortfolioAvailability() throws Exception {
        mockMvc.perform(post("/api/privacy/withdraw")
                        .header("Authorization", ACCESS_AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scope": "DATA_CONTRIBUTION",
                                  "reason": "USER_REQUEST"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.affectedPortfolioIds", hasSize(0)));

        mockMvc.perform(get("/api/explore/portfolios/own-portfolio-001").header("Authorization", ACCESS_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portfolioId").value("own-portfolio-001"));
    }

    @Test
    void corsAllowsLocalFrontendOrigins() throws Exception {
        for (String origin : List.of(
                "http://localhost:3000",
                "http://localhost:5173",
                "http://127.0.0.1:3000",
                "http://127.0.0.1:5173")) {
            mockMvc.perform(options("/api/home")
                            .header("Origin", origin)
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", origin));

            mockMvc.perform(options("/health")
                            .header("Origin", origin)
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", origin));
        }
    }
}
