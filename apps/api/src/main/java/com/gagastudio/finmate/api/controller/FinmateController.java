package com.gagastudio.finmate.api.controller;

import com.gagastudio.finmate.api.auth.AuthService;
import com.gagastudio.finmate.api.dto.ApiDtos.*;
import com.gagastudio.finmate.api.error.ApiException;
import com.gagastudio.finmate.api.product.ProductAppService;
import com.gagastudio.finmate.api.product.SyntheticDatasetImportService;
import com.gagastudio.finmate.api.service.FinmateService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;

@RestController
public class FinmateController {
    private final FinmateService service;
    private final AuthService authService;
    private final ProductAppService productAppService;
    private final SyntheticDatasetImportService syntheticDatasetImportService;
    private final boolean devToolsEnabled;

    public FinmateController(
            FinmateService service,
            AuthService authService,
            ProductAppService productAppService,
            SyntheticDatasetImportService syntheticDatasetImportService,
            @Value("${finmate.dev-tools.enabled:false}") boolean devToolsEnabled
    ) {
        this.service = service;
        this.authService = authService;
        this.productAppService = productAppService;
        this.syntheticDatasetImportService = syntheticDatasetImportService;
        this.devToolsEnabled = devToolsEnabled;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return service.health();
    }

    @PostMapping("/api/dev/reset")
    public Map<String, String> resetDevelopmentState() {
        if (!devToolsEnabled) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found.");
        }
        productAppService.resetDevelopmentState();
        return Map.of("status", "RESET");
    }

    @PostMapping("/api/dev/bootstrap-test-account")
    public ResponseEntity<AuthResponse> bootstrapTestAccount(@Valid @RequestBody DevBootstrapTestAccountRequest request) {
        if (!devToolsEnabled) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found.");
        }
        AuthService.AuthResult result = authService.bootstrapTestAccount(request);
        return authResponse(result);
    }

    @PostMapping("/api/dev/import-synthetic-dataset")
    public DevSyntheticImportResponse importSyntheticDataset(@Valid @RequestBody DevSyntheticImportRequest request) {
        if (!devToolsEnabled) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found.");
        }
        return syntheticDatasetImportService.importDataset(request);
    }

    @PostMapping("/api/auth/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody AuthSignupRequest request) {
        AuthService.AuthResult result = authService.signup(request);
        return authResponse(result);
    }

    @PostMapping("/api/auth/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthLoginRequest request) {
        AuthService.AuthResult result = authService.login(request);
        return authResponse(result);
    }

    @PostMapping("/api/auth/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(value = AuthService.REFRESH_COOKIE, required = false) String refreshToken
    ) {
        AuthService.AuthResult result = authService.refresh(refreshToken);
        return authResponse(result);
    }

    @PostMapping("/api/auth/logout")
    public ResponseEntity<Map<String, String>> logout(
            @CookieValue(value = AuthService.REFRESH_COOKIE, required = false) String refreshToken
    ) {
        authService.logout(refreshToken);
        return ResponseEntity
                .ok()
                .header(HttpHeaders.SET_COOKIE, expiredRefreshCookie().toString())
                .body(Map.of("status", "LOGGED_OUT"));
    }

    @GetMapping("/api/users/me")
    public UserMeResponse me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return authService.me(authorization);
    }

    @PostMapping("/api/users/me/onboarding")
    public UserMeResponse completeProductOnboarding(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody ProductOnboardingRequest request
    ) {
        return productAppService.completeOnboarding(authService.requireUserId(authorization), request);
    }

    @GetMapping("/api/ai/financial-snapshot")
    public FinancialSnapshotV1 getFinancialSnapshot(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        return productAppService.snapshotFor(authService.requireUserId(authorization));
    }

    @PostMapping("/api/ai/coach-results/fallback")
    public CoachResultV1 fallbackCoach(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        return productAppService.fallbackCoach(authService.requireUserId(authorization));
    }

    @PostMapping("/api/ai/coach-results")
    public CoachResultV1 storeCoachResult(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody CoachResultV1 request
    ) {
        return productAppService.storeCoachResult(authService.requireUserId(authorization), request);
    }

    @PostMapping("/api/onboarding/diagnosis")
    public OnboardingDiagnosisResponse createDiagnosis(@Valid @RequestBody OnboardingDiagnosisRequest request) {
        return service.createDiagnosis(request);
    }

    @PostMapping("/api/mydata/mock-consent")
    public MockConsentResponse createMockConsent(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody MockConsentRequest request
    ) {
        service.requireOnboardingToken(authorization);
        return service.createMockConsent(request);
    }

    @PostMapping("/api/privacy/consents")
    public PrivacyConsentsResponse createPrivacyConsents(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody PrivacyConsentsRequest request
    ) {
        service.requireOnboardingToken(authorization);
        return service.createPrivacyConsents(request);
    }

    @PostMapping("/api/demo/session")
    public DemoSessionResponse createDemoSession(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody DemoSessionRequest request
    ) {
        service.requireOnboardingToken(authorization);
        return service.createDemoSession(request);
    }

    @GetMapping("/api/home")
    public HomeResponse getHome(@RequestHeader(value = "Authorization", required = false) String authorization) {
        service.requireAccessToken(authorization);
        return service.getHome();
    }

    @GetMapping("/api/explore/portfolios/{id}")
    public PortfolioResponse getPortfolio(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String id
    ) {
        service.requireAccessToken(authorization);
        return service.getPortfolio(id);
    }

    @PostMapping("/api/comparisons")
    public ComparisonResponse createComparison(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody ComparisonRequest request
    ) {
        service.requireAccessToken(authorization);
        return service.createComparison(request);
    }

    @PostMapping("/api/simulations")
    public SimulationResponse createSimulation(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody SimulationRequest request
    ) {
        service.requireAccessToken(authorization);
        return service.createSimulation(request);
    }

    @PostMapping("/api/missions")
    public MissionResponse createMission(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody MissionRequest request
    ) {
        service.requireAccessToken(authorization);
        return service.createMission(request);
    }

    @GetMapping("/api/privacy/settings")
    public PrivacySettingsResponse getPrivacySettings(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        service.requireAccessToken(authorization);
        return service.getPrivacySettings();
    }

    @PatchMapping("/api/privacy/settings")
    public PrivacySettingsResponse updatePrivacySettings(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody PrivacySettingsPatchRequest request
    ) {
        service.requireAccessToken(authorization);
        return service.updatePrivacySettings(request);
    }

    @PostMapping("/api/privacy/withdraw")
    public PrivacyWithdrawResponse withdrawPrivacy(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody PrivacyWithdrawRequest request
    ) {
        service.requireAccessToken(authorization);
        return service.withdrawPrivacy(request);
    }

    @GetMapping("/api/app/home")
    public AppScreenResponse getAppHome(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return productAppService.getHome(authService.requireUserId(authorization));
    }

    @GetMapping("/api/app/home/{detail}")
    public AppScreenResponse getAppHomeDetail(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String detail
    ) {
        return productAppService.getHomeDetail(authService.requireUserId(authorization), detail);
    }

    @GetMapping("/api/app/compare")
    public AppScreenResponse getAppCompare(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return productAppService.getCompare(authService.requireUserId(authorization));
    }

    @GetMapping("/api/app/compare/filter")
    public AppScreenResponse getAppCompareFilter(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return productAppService.getCompareFilter(authService.requireUserId(authorization));
    }

    @PostMapping("/api/app/compare/filter/search")
    public AppScreenResponse searchAppCompareFilter(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody AppCompareSearchRequest request
    ) {
        return productAppService.searchCompareFilter(authService.requireUserId(authorization), request);
    }

    @GetMapping("/api/app/compare/results/{comparisonId}")
    public AppScreenResponse getAppCompareResult(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String comparisonId
    ) {
        return productAppService.getCompareResult(authService.requireUserId(authorization), comparisonId);
    }

    @GetMapping("/api/app/compare/{comparisonId}/coach-flow")
    public AppScreenResponse getAppCoachFlow(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String comparisonId
    ) {
        return productAppService.getCoachFlow(authService.requireUserId(authorization), comparisonId);
    }

    @GetMapping("/api/app/missions")
    public AppScreenResponse getAppMissions(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return productAppService.getMissions(authService.requireUserId(authorization));
    }

    @GetMapping("/api/app/missions/{missionId}")
    public AppScreenResponse getAppMission(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String missionId
    ) {
        return productAppService.getMission(authService.requireUserId(authorization), missionId);
    }

    @GetMapping("/api/app/missions/add")
    public AppScreenResponse getAppMissionAdd(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return productAppService.getMissionAdd(authService.requireUserId(authorization));
    }

    @PostMapping("/api/app/missions/add/{templateId}")
    public AppActionResultResponse addAppMissionFromTemplate(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String templateId
    ) {
        return productAppService.addMissionFromTemplate(authService.requireUserId(authorization), templateId);
    }

    @PostMapping("/api/app/missions/{missionId}/feedback")
    public AppActionResultResponse submitAppMissionFeedback(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String missionId,
            @Valid @RequestBody AppMissionFeedbackRequest request
    ) {
        return productAppService.submitMissionFeedback(authService.requireUserId(authorization), missionId, request);
    }

    @GetMapping("/api/app/records")
    public AppScreenResponse getAppRecords(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "month", required = false) String month
    ) {
        return productAppService.getRecords(authService.requireUserId(authorization), month);
    }

    @GetMapping("/api/app/records/{date}")
    public AppScreenResponse getAppRecordDetail(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String date
    ) {
        return productAppService.getRecordDetail(authService.requireUserId(authorization), date);
    }

    @GetMapping("/api/app/profile")
    public AppScreenResponse getAppProfile(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return productAppService.getProfile(authService.requireUserId(authorization));
    }

    @GetMapping("/api/app/profile/sections/{section}")
    public AppScreenResponse getAppProfileSection(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String section
    ) {
        return productAppService.getProfileSection(authService.requireUserId(authorization), section);
    }

    @GetMapping("/api/app/birthdays")
    public AppScreenResponse getAppBirthdays(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return productAppService.getBirthdays(authService.requireUserId(authorization));
    }

    @GetMapping("/api/app/birthdays/{birthdayId}/flow")
    public AppScreenResponse getAppBirthdayFlow(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String birthdayId
    ) {
        return productAppService.getBirthdayFlow(authService.requireUserId(authorization), birthdayId);
    }

    @PostMapping("/api/app/birthday-funds/{fundId}/contributions")
    public AppActionResultResponse contributeBirthdayFund(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String fundId,
            @Valid @RequestBody AppBirthdayContributionRequest request
    ) {
        return productAppService.contributeBirthdayFund(authService.requireUserId(authorization), fundId, request);
    }

    @GetMapping("/api/app/birthday-funds/{fundId}/complete")
    public AppScreenResponse getBirthdayContributionComplete(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String fundId
    ) {
        return productAppService.getBirthdayContributionComplete(authService.requireUserId(authorization), fundId);
    }

    @GetMapping("/api/app/birthday-funds/me/open")
    public AppScreenResponse getMyBirthdayFundOpenScreen(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        return productAppService.getMyBirthdayFundOpenScreen(authService.requireUserId(authorization));
    }

    @PostMapping("/api/app/birthday-funds/me/open")
    public AppActionResultResponse openMyBirthdayFund(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        return productAppService.openMyBirthdayFund(authService.requireUserId(authorization));
    }

    @GetMapping("/api/app/birthday-funds/me/share")
    public AppScreenResponse getMyBirthdayFundShareScreen(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        return productAppService.getMyBirthdayFundShareScreen(authService.requireUserId(authorization));
    }

    @PostMapping("/api/app/birthday-funds/me/share")
    public AppActionResultResponse shareMyBirthdayFund(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        return productAppService.shareMyBirthdayFund(authService.requireUserId(authorization));
    }

    @GetMapping("/api/app/birthday-funds/me/status")
    public AppScreenResponse getMyBirthdayFundStatus(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        return productAppService.getMyBirthdayFundStatus(authService.requireUserId(authorization));
    }

    private ResponseEntity<AuthResponse> authResponse(AuthService.AuthResult result) {
        return ResponseEntity
                .ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken(), result.refreshExpiresAt()).toString())
                .body(result.response());
    }

    private ResponseCookie refreshCookie(String token, OffsetDateTime expiresAt) {
        long maxAgeSeconds = Math.max(0, Duration.between(OffsetDateTime.now(), expiresAt).toSeconds());
        return ResponseCookie.from(AuthService.REFRESH_COOKIE, token)
                .httpOnly(true)
                .secure(false)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .build();
    }

    private ResponseCookie expiredRefreshCookie() {
        return ResponseCookie.from(AuthService.REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(false)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(Duration.ZERO)
                .build();
    }
}
