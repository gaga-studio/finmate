package com.gagastudio.finmate.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class FinmateApiApplicationTests {
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
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetProductState() throws Exception {
        mockMvc.perform(post("/api/dev/reset"))
                .andExpect(status().isOk());
    }

    @Test
    void healthReturnsOk() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void demoTokenAndLegacyRuntimeAreNotProductEntryPoints() throws Exception {
        mockMvc.perform(get("/api/app/home").header("Authorization", "Bearer demo-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(post("/api/demo/session").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/home"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/mydata/mock-consent").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/dev/bootstrap-test-account").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/app/missions/mission-food/feedback")
                        .header("Authorization", "Bearer " + signupAndToken("legacy-check"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DONE\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void onboardingCreatesStarterStateWithoutFriendsOrBirthdayFund() throws Exception {
        String accessToken = signupAndToken("starter");
        String userId = userIdFromToken(accessToken);

        assertEquals(0, count("missions", userId));
        assertEquals(0, count("daily_records", userId));
        assertEquals(0, count("financial_snapshots", userId));

        completeOnboarding(accessToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingCompleted").value(true));

        assertEquals(1, count("financial_snapshots", userId));
        assertEquals(1, count("daily_records", userId));
        assertEquals(3, count("missions", userId));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM friendships WHERE follower_id = ?", Integer.class, userId));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM birthday_funds WHERE owner_user_id = ?", Integer.class, userId));

        mockMvc.perform(get("/api/app/home").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screenId").value("home"))
                .andExpect(jsonPath("$.sections[?(@.id == 'mission-hero')]").exists())
                .andExpect(jsonPath("$.sections[?(@.id == 'budget')]").exists())
                .andExpect(jsonPath("$.sections[?(@.id == 'spending')]").exists())
                .andExpect(jsonPath("$.sections[?(@.id == 'asset')]").exists())
                .andExpect(jsonPath("$.sections[?(@.id == 'birthday-alert')]").doesNotExist());

        mockMvc.perform(get("/api/app/missions/mission-fixed-cost").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections[1].title").value("완료 조건과 근거"))
                .andExpect(content().string(containsString("행동 데이터")));
    }

    @Test
    void syntheticImportCreatesP001AndMissionEvaluationUsesBehaviorDataOnce() throws Exception {
        mockMvc.perform(post("/api/dev/import-synthetic-dataset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(syntheticImportPayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedUsers").value(2))
                .andExpect(jsonPath("$.transactions").value(4))
                .andExpect(jsonPath("$.birthdayFunds").value(1));

        String accessToken = login("p001@synthetic.finmate.local", "password123!");
        mockMvc.perform(get("/api/app/home").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("fund-p002-birthday")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("fund-jiwoo"))));

        mockMvc.perform(get("/api/app/missions").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screenId").value("missions"));

        String userId = "synthetic-P001";
        assertEquals(2, jdbc.queryForObject("""
                SELECT count(*) FROM missions
                WHERE user_id = ? AND status = 'COMPLETED'
                """, Integer.class, userId));
        assertEquals(2, jdbc.queryForObject("""
                SELECT count(*) FROM mission_events
                WHERE user_id = ? AND event_type = 'DONE' AND source = 'DATA_EVALUATION'
                """, Integer.class, userId));
        assertTrue(jdbc.queryForObject("""
                SELECT point_balance FROM point_wallets WHERE user_id = ?
                """, Integer.class, userId) >= 320);

        int pointTransactions = jdbc.queryForObject("""
                SELECT count(*) FROM point_transactions
                WHERE user_id = ? AND reference_type = 'MISSION'
                """, Integer.class, userId);
        mockMvc.perform(get("/api/app/missions").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
        assertEquals(pointTransactions, jdbc.queryForObject("""
                SELECT count(*) FROM point_transactions
                WHERE user_id = ? AND reference_type = 'MISSION'
                """, Integer.class, userId));

        mockMvc.perform(get("/api/app/missions/mission-fixed-cost").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("데이터가 더 필요")))
                .andExpect(jsonPath("$.sections[0].metrics[0].progress", greaterThanOrEqualTo(0)));
    }

    private ResultActions completeOnboarding(String accessToken) throws Exception {
        return mockMvc.perform(post("/api/users/me/onboarding")
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
                            "privacyConsentVersion": "privacy-v1.7"
                          },
                          "mydataConsent": {
                            "mydataConsentVersion": "synthetic-mydata-v1.7",
                            "mydataScopes": ["ACCOUNT_SUMMARY", "CARD_SPENDING", "INVESTMENT_SUMMARY"]
                          }
                        }
                        """));
    }

    private String signupAndToken(String label) throws Exception {
        String email = label + "-" + UUID.randomUUID() + "@finmate.local";
        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "password123!",
                                  "displayName": "테스트사용자"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.onboardingCompleted").value(false))
                .andExpect(header().exists("Set-Cookie"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String userIdFromToken(String accessToken) throws Exception {
        String[] parts = accessToken.split("\\.");
        JsonNode payload = objectMapper.readTree(java.util.Base64.getUrlDecoder().decode(parts[1]));
        return payload.get("sub").asText();
    }

    private int count(String table, String userId) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE user_id = ?", Integer.class, userId);
    }

    private String syntheticImportPayload() {
        return """
                {
                  "importVersion": "test-import",
                  "sourceRepository": "https://github.com/gaga-studio/financial-sns-mydata-202606",
                  "sourceCommit": "test",
                  "resetSynthetic": true,
                  "users": [
                    {
                      "personaId": "P001",
                      "email": "p001@synthetic.finmate.local",
                      "password": "password123!",
                      "displayName": "하민",
                      "profile": {
                        "ageBand": "20대 후반",
                        "incomeBand": "3,000만원 ~ 4,000만원",
                        "jobCategory": "IT/개발",
                        "householdType": "1인가구",
                        "moneyStyle": "안정 추구형",
                        "area": "서울",
                        "goalType": "EMERGENCY_FUND",
                        "painPoint": "SAVE_CONSISTENTLY"
                      },
                      "privacy": {
                        "anonymousPortfolioOptIn": true,
                        "friendShareDefault": "MISSION_ONLY",
                        "exposedFields": ["ageBand", "goalType", "financialSummary", "missionStatus"]
                      },
                      "mydata": {
                        "consentVersion": "synthetic-mydata-test",
                        "scopes": ["ACCOUNT_SUMMARY", "CARD_SPENDING", "INVESTMENT_SUMMARY"]
                      },
                      "wallet": { "pointBalance": 0, "virtualMoneyBalance": 100000 },
                      "snapshot": {
                        "month": "2026-06",
                        "monthlyIncome": 2900000,
                        "monthlySpending": 1600000,
                        "monthlySaving": 180000,
                        "investmentValue": 250000,
                        "cashLikeAssets": 500000,
                        "emergencyFundMonths": 0.31,
                        "spendingCategories": { "식비": 240000, "교통비": 90000, "카페/간식": 50000, "기타": 1220000 },
                        "lifestyleTags": ["비상금", "루틴"]
                      },
                      "dailyRecords": [
                        {
                          "recordDate": "2026-06-12",
                          "budget": 10000,
                          "spent": 7800,
                          "categorySpending": { "식비": 7800, "교통비": 0, "카페/간식": 0, "기타": 0 },
                          "missionStatus": "IN_PROGRESS",
                          "pointDelta": 0
                        }
                      ],
                      "transactions": [
                        {
                          "transactionId": "P001-T001",
                          "transactionDate": "2026-06-12",
                          "transactionTime": "12:10",
                          "transactionType": "지출",
                          "category": "식비",
                          "subcategory": "점심",
                          "description": "점심",
                          "amountKrw": -7800,
                          "cashflowBucket": "소비",
                          "accountRef": "bank:P001",
                          "apiRef": "card:P001"
                        },
                        {
                          "transactionId": "P001-T002",
                          "transactionDate": "2026-06-03",
                          "transactionTime": "09:00",
                          "transactionType": "저축",
                          "category": "저축",
                          "subcategory": "비상금",
                          "description": "비상금 자동이체",
                          "amountKrw": -120000,
                          "cashflowBucket": "저축",
                          "accountRef": "bank:P001",
                          "apiRef": "bank:P001"
                        }
                      ],
                      "missions": [
                        {
                          "missionId": "mission-food",
                          "title": "내일 식비 10,000원 이하 사용하기",
                          "description": "하루 식비 10,000원 이하",
                          "status": "ACTIVE",
                          "difficulty": "EASY",
                          "rewardPoints": 120,
                          "progress": 40,
                          "source": "SYNTHETIC_MYDATA_IMPORT"
                        },
                        {
                          "missionId": "mission-saving",
                          "title": "저축하기 습관 만들기",
                          "description": "비상금 100,000원 이상 저축",
                          "status": "ACTIVE",
                          "difficulty": "EASY",
                          "rewardPoints": 200,
                          "progress": 40,
                          "source": "SYNTHETIC_MYDATA_IMPORT"
                        },
                        {
                          "missionId": "mission-fixed-cost",
                          "title": "고정 지출 5% 줄이기",
                          "description": "구독과 반복 결제 점검",
                          "status": "ACTIVE",
                          "difficulty": "NORMAL",
                          "rewardPoints": 180,
                          "progress": 20,
                          "source": "SYNTHETIC_MYDATA_IMPORT"
                        }
                      ],
                      "follows": ["P002"],
                      "feedItems": [
                        {
                          "feedId": "feed-P001-birthday",
                          "actorPersonaId": "P002",
                          "kind": "BIRTHDAY",
                          "title": "서연님의 생일 펀드가 열렸어요",
                          "body": "친구들이 함께 모으는 생일 축하 펀드",
                          "amount": 72000
                        }
                      ],
                      "birthdayFund": null
                    },
                    {
                      "personaId": "P002",
                      "email": "p002@synthetic.finmate.local",
                      "password": "password123!",
                      "displayName": "서연",
                      "profile": {
                        "ageBand": "20대 후반",
                        "incomeBand": "3,000만원 ~ 4,000만원",
                        "jobCategory": "디자인",
                        "householdType": "1인가구",
                        "moneyStyle": "안정 추구형",
                        "area": "서울",
                        "goalType": "SAVING",
                        "painPoint": "SAVE_CONSISTENTLY"
                      },
                      "privacy": {
                        "anonymousPortfolioOptIn": true,
                        "friendShareDefault": "MISSION_ONLY",
                        "exposedFields": ["ageBand", "goalType", "financialSummary", "missionStatus"]
                      },
                      "mydata": {
                        "consentVersion": "synthetic-mydata-test",
                        "scopes": ["ACCOUNT_SUMMARY", "CARD_SPENDING", "INVESTMENT_SUMMARY"]
                      },
                      "wallet": { "pointBalance": 0, "virtualMoneyBalance": 100000 },
                      "snapshot": {
                        "month": "2026-06",
                        "monthlyIncome": 2800000,
                        "monthlySpending": 1500000,
                        "monthlySaving": 140000,
                        "investmentValue": 100000,
                        "cashLikeAssets": 420000,
                        "emergencyFundMonths": 0.28,
                        "spendingCategories": { "식비": 200000, "교통비": 70000, "카페/간식": 40000, "기타": 1190000 },
                        "lifestyleTags": ["저축"]
                      },
                      "dailyRecords": [
                        {
                          "recordDate": "2026-06-12",
                          "budget": 10000,
                          "spent": 9000,
                          "categorySpending": { "식비": 9000, "교통비": 0, "카페/간식": 0, "기타": 0 },
                          "missionStatus": "IN_PROGRESS",
                          "pointDelta": 0
                        }
                      ],
                      "transactions": [
                        {
                          "transactionId": "P002-T001",
                          "transactionDate": "2026-06-12",
                          "transactionTime": "12:10",
                          "transactionType": "지출",
                          "category": "식비",
                          "subcategory": "점심",
                          "description": "점심",
                          "amountKrw": -9000,
                          "cashflowBucket": "소비",
                          "accountRef": "bank:P002",
                          "apiRef": "card:P002"
                        },
                        {
                          "transactionId": "P002-T002",
                          "transactionDate": "2026-06-03",
                          "transactionTime": "09:00",
                          "transactionType": "저축",
                          "category": "저축",
                          "subcategory": "비상금",
                          "description": "비상금 자동이체",
                          "amountKrw": -100000,
                          "cashflowBucket": "저축",
                          "accountRef": "bank:P002",
                          "apiRef": "bank:P002"
                        }
                      ],
                      "missions": [
                        {
                          "missionId": "mission-food",
                          "title": "내일 식비 10,000원 이하 사용하기",
                          "description": "하루 식비 10,000원 이하",
                          "status": "ACTIVE",
                          "difficulty": "EASY",
                          "rewardPoints": 120,
                          "progress": 40,
                          "source": "SYNTHETIC_MYDATA_IMPORT"
                        }
                      ],
                      "follows": [],
                      "feedItems": [],
                      "birthdayFund": {
                        "fundId": "fund-p002-birthday",
                        "ownerPersonaId": "P002",
                        "title": "서연님의 생일 펀드",
                        "targetAmount": 100000,
                        "currentAmount": 72000,
                        "dueDate": "2026-06-15",
                        "status": "OPEN",
                        "shareCode": "SYNTH-BDAY-TEST"
                      }
                    }
                  ]
                }
                """;
    }
}
