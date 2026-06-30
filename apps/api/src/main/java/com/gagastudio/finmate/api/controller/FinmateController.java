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
}
