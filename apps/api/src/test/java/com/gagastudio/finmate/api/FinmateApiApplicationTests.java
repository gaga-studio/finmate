package com.gagastudio.finmate.api;

import com.gagastudio.finmate.api.store.SeedStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    void rejectsMissingAccessToken() throws Exception {
        mockMvc.perform(get("/api/home"))
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
}
