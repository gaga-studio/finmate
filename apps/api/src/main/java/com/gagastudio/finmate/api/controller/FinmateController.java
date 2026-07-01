package com.gagastudio.finmate.api.controller;

import com.gagastudio.finmate.api.dto.ApiDtos.*;
import com.gagastudio.finmate.api.service.FinmateService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class FinmateController {
    private final FinmateService service;

    public FinmateController(FinmateService service) {
        this.service = service;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return service.health();
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
        service.requireAccessToken(authorization);
        return service.getAppHome();
    }

    @GetMapping("/api/app/home/{detail}")
    public AppScreenResponse getAppHomeDetail(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String detail
    ) {
        service.requireAccessToken(authorization);
        return service.getAppHomeDetail(detail);
    }

    @GetMapping("/api/app/compare")
    public AppScreenResponse getAppCompare(@RequestHeader(value = "Authorization", required = false) String authorization) {
        service.requireAccessToken(authorization);
        return service.getAppCompare();
    }

    @GetMapping("/api/app/compare/filter")
    public AppScreenResponse getAppCompareFilter(@RequestHeader(value = "Authorization", required = false) String authorization) {
        service.requireAccessToken(authorization);
        return service.getAppCompareFilter();
    }

    @PostMapping("/api/app/compare/filter/search")
    public AppScreenResponse searchAppCompareFilter(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody AppCompareSearchRequest request
    ) {
        service.requireAccessToken(authorization);
        return service.searchAppCompareFilter(request);
    }

    @GetMapping("/api/app/compare/results/{comparisonId}")
    public AppScreenResponse getAppCompareResult(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String comparisonId
    ) {
        service.requireAccessToken(authorization);
        return service.getAppCompareResult(comparisonId);
    }

    @GetMapping("/api/app/compare/{comparisonId}/coach-flow")
    public AppScreenResponse getAppCoachFlow(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String comparisonId
    ) {
        service.requireAccessToken(authorization);
        return service.getAppCoachFlow(comparisonId);
    }

    @GetMapping("/api/app/missions")
    public AppScreenResponse getAppMissions(@RequestHeader(value = "Authorization", required = false) String authorization) {
        service.requireAccessToken(authorization);
        return service.getAppMissions();
    }

    @GetMapping("/api/app/missions/{missionId}")
    public AppScreenResponse getAppMission(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String missionId
    ) {
        service.requireAccessToken(authorization);
        return service.getAppMission(missionId);
    }

    @PostMapping("/api/app/missions/{missionId}/feedback")
    public AppActionResultResponse submitAppMissionFeedback(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String missionId,
            @Valid @RequestBody AppMissionFeedbackRequest request
    ) {
        service.requireAccessToken(authorization);
        return service.submitAppMissionFeedback(missionId, request);
    }

    @GetMapping("/api/app/records")
    public AppScreenResponse getAppRecords(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "month", required = false) String month
    ) {
        service.requireAccessToken(authorization);
        return service.getAppRecords(month);
    }

    @GetMapping("/api/app/records/{date}")
    public AppScreenResponse getAppRecordDetail(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String date
    ) {
        service.requireAccessToken(authorization);
        return service.getAppRecordDetail(date);
    }

    @GetMapping("/api/app/profile")
    public AppScreenResponse getAppProfile(@RequestHeader(value = "Authorization", required = false) String authorization) {
        service.requireAccessToken(authorization);
        return service.getAppProfile();
    }

    @GetMapping("/api/app/profile/sections/{section}")
    public AppScreenResponse getAppProfileSection(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String section
    ) {
        service.requireAccessToken(authorization);
        return service.getAppProfileSection(section);
    }

    @GetMapping("/api/app/birthdays")
    public AppScreenResponse getAppBirthdays(@RequestHeader(value = "Authorization", required = false) String authorization) {
        service.requireAccessToken(authorization);
        return service.getAppBirthdays();
    }

    @GetMapping("/api/app/birthdays/{birthdayId}/flow")
    public AppScreenResponse getAppBirthdayFlow(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String birthdayId
    ) {
        service.requireAccessToken(authorization);
        return service.getAppBirthdayFlow(birthdayId);
    }

    @PostMapping("/api/app/birthday-funds/{fundId}/contributions")
    public AppActionResultResponse contributeBirthdayFund(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String fundId,
            @Valid @RequestBody AppBirthdayContributionRequest request
    ) {
        service.requireAccessToken(authorization);
        return service.contributeBirthdayFund(fundId, request);
    }

    @GetMapping("/api/app/birthday-funds/{fundId}/complete")
    public AppScreenResponse getBirthdayContributionComplete(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String fundId
    ) {
        service.requireAccessToken(authorization);
        return service.getBirthdayContributionComplete(fundId);
    }

    @GetMapping("/api/app/birthday-funds/me/open")
    public AppScreenResponse getMyBirthdayFundOpenScreen(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        service.requireAccessToken(authorization);
        return service.getMyBirthdayFundOpenScreen();
    }

    @PostMapping("/api/app/birthday-funds/me/open")
    public AppActionResultResponse openMyBirthdayFund(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        service.requireAccessToken(authorization);
        return service.openMyBirthdayFund();
    }

    @GetMapping("/api/app/birthday-funds/me/share")
    public AppScreenResponse getMyBirthdayFundShareScreen(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        service.requireAccessToken(authorization);
        return service.getMyBirthdayFundShareScreen();
    }

    @PostMapping("/api/app/birthday-funds/me/share")
    public AppActionResultResponse shareMyBirthdayFund(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        service.requireAccessToken(authorization);
        return service.shareMyBirthdayFund();
    }

    @GetMapping("/api/app/birthday-funds/me/status")
    public AppScreenResponse getMyBirthdayFundStatus(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        service.requireAccessToken(authorization);
        return service.getMyBirthdayFundStatus();
    }
}
