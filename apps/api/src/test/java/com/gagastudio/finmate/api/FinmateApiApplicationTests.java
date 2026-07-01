package com.gagastudio.finmate.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gagastudio.finmate.api.auth.AuthService;
import com.gagastudio.finmate.api.auth.JwtService;
import com.gagastudio.finmate.api.store.SeedStore;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class FinmateApiApplicationTests {
    private static final String ONBOARDING_AUTH = "Bearer onb-token-001";
    private static final String ACCESS_AUTH = "Bearer demo-token";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("finmate_test")
            .withUsername("finmate")
            .withPassword("finmate");

    @DynamicPropertySource
    static void registerDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("finmate.dev-tools.enabled", () -> "true");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SeedStore seedStore;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtService jwtService;

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
    void productMvpAuthOnboardingCreatesStarterUserState() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@finmate.local";
        MvcResult signup = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "password123!",
                                  "displayName": "새사용자"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.onboardingCompleted").value(false))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(header().exists("Set-Cookie"))
                .andReturn();

        JsonNode signupBody = objectMapper.readTree(signup.getResponse().getContentAsString());
        String userId = signupBody.get("user").get("userId").asText();
        String accessToken = signupBody.get("accessToken").asText();
        Cookie refreshCookie = refreshCookie(signup);

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("새사용자"))
                .andExpect(jsonPath("$.pointBalance").value(0));

        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM missions WHERE user_id = ?", Integer.class, userId));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM daily_records WHERE user_id = ?", Integer.class, userId));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM financial_snapshots WHERE user_id = ?", Integer.class, userId));

        mockMvc.perform(post("/api/users/me/onboarding")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ageBand": "20대 후반",
                                  "incomeBand": "3,000만원 ~ 4,000만원",
                                  "jobCategory": "IT/개발",
                                  "householdType": "1인가구",
                                  "moneyStyle": "안정 추구형",
                                  "area": "서울 강남권",
                                  "goalType": "EMERGENCY_FUND",
                                  "painPoint": "SAVE_CONSISTENTLY",
                                  "privacyConsent": {
                                    "anonymousPortfolioOptIn": true,
                                    "friendShareDefault": "MISSION_ONLY",
                                    "exposedFields": ["ageBand", "goalType", "financialSummary", "missionStatus"],
                                    "privacyConsentVersion": "privacy-v1.4"
                                  },
                                  "mydataConsent": {
                                    "mydataConsentVersion": "synthetic-mydata-v1.4",
                                    "mydataScopes": ["ACCOUNT_SUMMARY", "CARD_SPENDING", "INVESTMENT_SUMMARY"]
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingCompleted").value(true));

        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM onboarding_responses WHERE user_id = ?", Integer.class, userId));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM mydata_connections WHERE user_id = ? AND data_mode = 'SYNTHETIC_MYDATA'", Integer.class, userId));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM consent_events WHERE user_id = ?", Integer.class, userId));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM financial_snapshots WHERE user_id = ?", Integer.class, userId));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM daily_records WHERE user_id = ?", Integer.class, userId));
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM missions WHERE user_id = ?", Integer.class, userId));

        mockMvc.perform(get("/api/app/home").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screenId").value("home"))
                .andExpect(jsonPath("$.sections[0].title").value("새사용자님, 좋은 아침이에요!"))
                .andExpect(jsonPath("$.sections[1].id").value("mission-hero"))
                .andExpect(jsonPath("$.sections[2].id").value("budget"))
                .andExpect(jsonPath("$.sections[3].id").value("spending"))
                .andExpect(jsonPath("$.sections[4].id").value("asset"))
                .andExpect(jsonPath("$.sections[?(@.id == 'birthday-alert')]").doesNotExist());

        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM friendships WHERE follower_id = ?", Integer.class, userId));

        mockMvc.perform(post("/api/ai/coach-results/fallback").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("RULE_BASED_FALLBACK"))
                .andExpect(jsonPath("$.recommendations", hasSize(3)));

        mockMvc.perform(get("/api/app/missions").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screenId").value("missions"))
                .andExpect(jsonPath("$.sections[0].id").value("mission-hero"))
                .andExpect(jsonPath("$.sections[1].items", hasSize(3)));

        mockMvc.perform(get("/api/app/records?month=2026-06").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screenId").value("records:2026-06"))
                .andExpect(jsonPath("$.sections[1].id").value("budget"));

        mockMvc.perform(get("/api/app/compare").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screenId").value("compare"))
                .andExpect(jsonPath("$.sections[1].id").value("score"));

        mockMvc.perform(get("/api/app/missions/not-a-mission").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MISSION_NOT_FOUND"));

        mockMvc.perform(post("/api/app/missions/mission-food/feedback")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "DONE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rewardPoints").value(120));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM missions WHERE user_id = ? AND status = 'COMPLETED'", Integer.class, userId));

        mockMvc.perform(post("/api/users/me/onboarding")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ageBand": "20대 후반",
                                  "incomeBand": "3,000만원 ~ 4,000만원",
                                  "jobCategory": "IT/개발",
                                  "householdType": "1인가구",
                                  "moneyStyle": "안정 추구형",
                                  "area": "서울 강남권",
                                  "goalType": "EMERGENCY_FUND",
                                  "painPoint": "SAVE_CONSISTENTLY",
                                  "privacyConsent": {
                                    "anonymousPortfolioOptIn": true,
                                    "friendShareDefault": "MISSION_ONLY",
                                    "exposedFields": ["ageBand", "goalType", "financialSummary", "missionStatus"],
                                    "privacyConsentVersion": "privacy-v1.4"
                                  },
                                  "mydataConsent": {
                                    "mydataConsentVersion": "synthetic-mydata-v1.4",
                                    "mydataScopes": ["ACCOUNT_SUMMARY", "CARD_SPENDING", "INVESTMENT_SUMMARY"]
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingCompleted").value(true));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM missions WHERE user_id = ? AND id = ? AND status = 'COMPLETED'", Integer.class, userId, userId + ":mission-food"));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM point_transactions WHERE user_id = ? AND reference_id = 'mission-food'", Integer.class, userId));

        mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(header().exists("Set-Cookie"));

        mockMvc.perform(post("/api/auth/logout").cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOGGED_OUT"));
    }

    @Test
    void productOnboardingRequiresSurveyAndConsentPayloads() throws Exception {
        String email = "invalid-onboarding-" + UUID.randomUUID() + "@finmate.local";
        MvcResult signup = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "password123!",
                                  "displayName": "새사용자"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode signupBody = objectMapper.readTree(signup.getResponse().getContentAsString());
        String accessToken = signupBody.get("accessToken").asText();

        mockMvc.perform(post("/api/users/me/onboarding")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ageBand": "20대 후반",
                                  "incomeBand": "3,000만원 ~ 4,000만원",
                                  "jobCategory": "IT/개발",
                                  "householdType": "1인가구",
                                  "moneyStyle": "안정 추구형",
                                  "area": "서울 강남권",
                                  "goalType": "EMERGENCY_FUND",
                                  "painPoint": "SAVE_CONSISTENTLY"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void developmentResetClearsRuntimeStateAndKeepsBootstrapWorking() throws Exception {
        mockMvc.perform(post("/api/dev/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESET"));

        mockMvc.perform(get("/api/app/home").header("Authorization", ACCESS_AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screenId").value("home"))
                .andExpect(jsonPath("$.sections[0].title").value("jinn님, 좋은 아침이에요!"));
    }

    @Test
    void developmentBootstrapTestAccountSeedsReferenceDemoData() throws Exception {
        MvcResult bootstrap = mockMvc.perform(post("/api/dev/bootstrap-test-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "qa-birthday@finmate.local",
                                  "password": "password123!",
                                  "displayName": "테스트 사용자",
                                  "includeBirthdayEvent": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("qa-birthday@finmate.local"))
                .andExpect(jsonPath("$.user.onboardingCompleted").value(true))
                .andExpect(jsonPath("$.user.pointBalance").value(2450))
                .andReturn();

        String accessToken = objectMapper.readTree(bootstrap.getResponse().getContentAsString()).get("accessToken").asText();

        mockMvc.perform(get("/api/app/home").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections[1].id").value("mission-hero"))
                .andExpect(jsonPath("$.sections[2].id").value("budget"))
                .andExpect(jsonPath("$.sections[3].id").value("birthday-alert"))
                .andExpect(jsonPath("$.sections[4].id").value("spending"))
                .andExpect(jsonPath("$.sections[5].id").value("asset"));
    }

    @Test
    void developmentSyntheticImportSeedsExternalDatasetStyleUsers() throws Exception {
        String importPayload = """
                {
                  "importVersion": "2026-06",
                  "sourceRepository": "https://github.com/gaga-studio/financial-sns-mydata-202606",
                  "sourceCommit": "test-commit",
                  "resetSynthetic": true,
                  "users": [
                    {
                      "personaId": "P001",
                      "email": "p001@synthetic.finmate.local",
                      "password": "password123!",
                      "displayName": "가상청년 P001",
                      "profile": {
                        "ageBand": "20대 초반",
                        "incomeBand": "150~250만원",
                        "jobCategory": "휴학생/단기알바",
                        "householdType": "1인가구",
                        "moneyStyle": "안정추구형",
                        "area": "인천 부평구",
                        "goalType": "EMERGENCY_FUND",
                        "painPoint": "SAVE_CONSISTENTLY"
                      },
                      "privacy": {
                        "anonymousPortfolioOptIn": true,
                        "friendShareDefault": "MISSION_ONLY",
                        "exposedFields": ["ageBand", "goalType", "financialSummary", "missionStatus"]
                      },
                      "mydata": {
                        "consentVersion": "synthetic-mydata-v1.6",
                        "scopes": ["ACCOUNT_SUMMARY", "CARD_SPENDING", "INVESTMENT_SUMMARY"]
                      },
                      "wallet": {
                        "pointBalance": 537,
                        "virtualMoneyBalance": 100000
                      },
                      "snapshot": {
                        "month": "2026-06",
                        "monthlyIncome": 1700000,
                        "monthlySpending": 2115200,
                        "monthlySaving": 150000,
                        "investmentValue": 76100,
                        "cashLikeAssets": 354000,
                        "emergencyFundMonths": 0.17,
                        "spendingCategories": {
                          "식비": 183000,
                          "교통비": 201000,
                          "카페/간식": 36700,
                          "기타": 1694500
                        },
                        "lifestyleTags": ["가성비", "예산관리", "캠퍼스생활"]
                      },
                      "dailyRecords": [
                        {
                          "recordDate": "2026-06-12",
                          "budget": 10000,
                          "spent": 7800,
                          "categorySpending": {
                            "식비": 6200,
                            "교통비": 1200,
                            "카페/간식": 400,
                            "기타": 0
                          },
                          "missionStatus": "IN_PROGRESS",
                          "pointDelta": 0
                        }
                      ],
                      "missions": [
                        {
                          "missionId": "mission-food",
                          "title": "내일 식비 10,000원 이하 사용하기",
                          "description": "하루 식비를 낮춰 남는 금액을 비상금으로 옮겨요.",
                          "status": "ACTIVE",
                          "difficulty": "EASY",
                          "rewardPoints": 120,
                          "progress": 78,
                          "source": "SYNTHETIC_MYDATA_IMPORT"
                        }
                      ],
                      "follows": ["P002"],
                      "feedItems": [
                        {
                          "feedId": "feed-P001-birthday",
                          "actorPersonaId": "P002",
                          "kind": "BIRTHDAY",
                          "title": "가상청년 P002님의 생일 펀드가 열렸어요",
                          "body": "친구들이 함께 모으는 생일 축하 펀드",
                          "amount": 72000
                        }
                      ],
                      "birthdayFund": {
                        "fundId": "fund-jiwoo",
                        "ownerPersonaId": "P002",
                        "title": "가상청년 P002님의 생일 펀드",
                        "targetAmount": 100000,
                        "currentAmount": 72000,
                        "dueDate": "2026-06-15",
                        "status": "OPEN",
                        "shareCode": "SYNTH-BDAY-2026"
                      }
                    },
                    {
                      "personaId": "P002",
                      "email": "p002@synthetic.finmate.local",
                      "password": "password123!",
                      "displayName": "가상청년 P002",
                      "profile": {
                        "ageBand": "20대 후반",
                        "incomeBand": "250~350만원",
                        "jobCategory": "IT/개발",
                        "householdType": "1인가구",
                        "moneyStyle": "중립형",
                        "area": "서울 성동구",
                        "goalType": "HOUSING",
                        "painPoint": "BUILD_ROUTINE"
                      },
                      "privacy": {
                        "anonymousPortfolioOptIn": true,
                        "friendShareDefault": "MISSION_ONLY",
                        "exposedFields": ["ageBand", "goalType", "financialSummary"]
                      },
                      "mydata": {
                        "consentVersion": "synthetic-mydata-v1.6",
                        "scopes": ["ACCOUNT_SUMMARY", "CARD_SPENDING", "INVESTMENT_SUMMARY"]
                      },
                      "wallet": {
                        "pointBalance": 574,
                        "virtualMoneyBalance": 100000
                      },
                      "snapshot": {
                        "month": "2026-06",
                        "monthlyIncome": 3160000,
                        "monthlySpending": 3211300,
                        "monthlySaving": 1100000,
                        "investmentValue": 193200,
                        "cashLikeAssets": 1479200,
                        "emergencyFundMonths": 0.46,
                        "spendingCategories": {
                          "식비": 451400,
                          "교통비": 282000,
                          "카페/간식": 117500,
                          "기타": 2360400
                        },
                        "lifestyleTags": ["구독서비스", "예산관리", "출퇴근"]
                      },
                      "dailyRecords": [
                        {
                          "recordDate": "2026-06-12",
                          "budget": 18000,
                          "spent": 14500,
                          "categorySpending": {
                            "식비": 10000,
                            "교통비": 0,
                            "카페/간식": 4500,
                            "기타": 0
                          },
                          "missionStatus": "IN_PROGRESS",
                          "pointDelta": 0
                        }
                      ],
                      "missions": [
                        {
                          "missionId": "mission-food",
                          "title": "내일 식비 10,000원 이하 사용하기",
                          "description": "하루 식비를 낮춰 남는 금액을 비상금으로 옮겨요.",
                          "status": "ACTIVE",
                          "difficulty": "EASY",
                          "rewardPoints": 120,
                          "progress": 42,
                          "source": "SYNTHETIC_MYDATA_IMPORT"
                        }
                      ],
                      "follows": ["P001"],
                      "feedItems": [],
                      "birthdayFund": null
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/dev/import-synthetic-dataset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(importPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IMPORTED"))
                .andExpect(jsonPath("$.importedUsers").value(2))
                .andExpect(jsonPath("$.snapshots").value(2))
                .andExpect(jsonPath("$.dailyRecords").value(2))
                .andExpect(jsonPath("$.birthdayFunds").value(1));

        mockMvc.perform(post("/api/dev/import-synthetic-dataset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(importPayload.replace("\"resetSynthetic\": true", "\"resetSynthetic\": false")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedUsers").value(2));

        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM users WHERE id LIKE 'synthetic-P%'", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM financial_snapshots WHERE user_id LIKE 'synthetic-P%'", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM birthday_funds WHERE id = 'fund-jiwoo'", Integer.class));

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "p001@synthetic.finmate.local",
                                  "password": "password123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("p001@synthetic.finmate.local"))
                .andReturn();
        String accessToken = objectMapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();

        mockMvc.perform(get("/api/app/home").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections[1].id").value("mission-hero"))
                .andExpect(jsonPath("$.sections[2].id").value("budget"))
                .andExpect(jsonPath("$.sections[3].id").value("birthday-alert"))
                .andExpect(jsonPath("$.sections[5].id").value("asset"));

        mockMvc.perform(get("/api/app/compare").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections[1].id").value("score"));

        mockMvc.perform(get("/api/app/records?month=2026-06").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections[1].id").value("budget"));
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
                .andExpect(jsonPath("$.sections[1].id").value("mission-hero"))
                .andExpect(jsonPath("$.sections[2].id").value("budget"))
                .andExpect(jsonPath("$.sections[3].id").value("birthday-alert"));

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
    void rejectsAccessTokenForUserMissingFromDatabase() throws Exception {
        String accessToken = jwtService.issue("user-missing-from-db").token();

        mockMvc.perform(get("/api/app/home").header("Authorization", "Bearer " + accessToken))
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

    private Cookie refreshCookie(MvcResult result) {
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        String prefix = AuthService.REFRESH_COOKIE + "=";
        if (setCookie == null || !setCookie.startsWith(prefix)) {
            throw new AssertionError("Missing refresh cookie");
        }
        String value = setCookie.substring(prefix.length(), setCookie.indexOf(';'));
        return new Cookie(AuthService.REFRESH_COOKIE, value);
    }
}
