package com.gagastudio.finmate.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public final class ApiDtos {
    private ApiDtos() {
    }

    public record OnboardingDiagnosisRequest(
            @NotBlank String occupationStatus,
            @NotBlank String incomeBand,
            @NotBlank String householdType,
            @NotBlank String goalType,
            @NotBlank String painPoint
    ) {
    }

    public record OnboardingDiagnosisResponse(
            String diagnosisId,
            String onboardingToken,
            String goalType,
            String recommendedPersonaId,
            String cohortLabel,
            String expiresAt
    ) {
    }

    public record MockConsentRequest(
            @NotBlank String diagnosisId,
            @NotBlank String consentVersion,
            @NotEmpty List<String> scopes
    ) {
    }

    public record MockConsentResponse(
            String mydataConnectionId,
            String status,
            String dataMode,
            String agreedAt
    ) {
    }

    public record PrivacyConsentsRequest(
            @NotNull Boolean anonymousPortfolioOptIn,
            @NotBlank String friendShareDefault,
            @NotEmpty List<String> exposedFields,
            @NotBlank String consentVersion
    ) {
    }

    public record PrivacyConsentsResponse(
            String privacySettingsId,
            List<String> consentHistoryIds,
            boolean previewAvailable
    ) {
    }

    public record DemoSessionRequest(
            @NotBlank String mode,
            String diagnosisId,
            String mydataConnectionId,
            String personaId,
            String goalType
    ) {
    }

    public record DemoSessionResponse(
            String userId,
            String accessToken,
            String selectedPersonaId,
            String goalType,
            String expiresAt
    ) {
    }

    public record HomeResponse(
            String userId,
            Goal goal,
            int todayBudget,
            SpendingSummary spendingSummary,
            AssetSummary assetSummary,
            TodayMissionCandidate todayMissionCandidate,
            PeerTeaser peerTeaser
    ) {
    }

    public record Goal(String goalType, String label) {
    }

    public record SpendingSummary(int monthlySpent, double fixedCostRatio) {
    }

    public record AssetSummary(int cashLikeAssets, double emergencyFundMonths) {
    }

    public record TodayMissionCandidate(String title, String recommendationSource) {
    }

    public record PeerTeaser(String portfolioId, String title, double similarityScore, String mainDifference) {
    }

    public record PortfolioResponse(
            String portfolioId,
            String displayName,
            String status,
            String visibility,
            String dataMode,
            FinancialSummary financialSummary,
            List<RoutineCard> routineCards,
            List<String> privacyBadges
    ) {
    }

    public record FinancialSummary(double emergencyFundMonths, double savingsRate, double fixedCostRatio) {
    }

    public record RoutineCard(String title, String description) {
    }

    public record ComparisonRequest(@NotBlank String peerPortfolioId) {
    }

    public record ComparisonResponse(
            String comparisonId,
            String peerPortfolioId,
            MainGap mainGap,
            double similarityScore,
            List<GapItem> gapItems,
            ComparisonNextAction nextAction
    ) {
    }

    public record MainGap(String type, String label, double normalizedGap) {
    }

    public record GapItem(String type, double userValue, double peerValue, String unit) {
    }

    public record ComparisonNextAction(String label, String scenarioType) {
    }

    public record SimulationRequest(
            @NotBlank String comparisonId,
            @NotBlank String scenarioType,
            @NotNull Integer monthlyAdditionalSaving,
            @NotNull Integer periodMonths
    ) {
    }

    public record SimulationResponse(
            String simulationId,
            String comparisonId,
            String scenarioType,
            int periodMonths,
            int monthlyAdditionalSaving,
            Map<String, Number> before,
            Map<String, Number> after,
            String insight,
            SimulationNextAction nextAction,
            String disclaimer
    ) {
    }

    public record SimulationNextAction(String label, String missionTemplateId) {
    }

    public record MissionRequest(
            @NotBlank String simulationId,
            @NotBlank String missionTemplateId,
            @NotBlank String triggerSource,
            @NotBlank String recommendationSource,
            @NotBlank String difficulty
    ) {
    }

    public record MissionResponse(
            String missionId,
            String title,
            String description,
            String difficulty,
            String verificationType,
            int rewardPoints,
            String status,
            String localDate,
            PrivacySharePreview privacySharePreview
    ) {
    }

    public record PrivacySharePreview(String shareableText, boolean containsAmount) {
    }

    public record PrivacySettingsPatchRequest(
            Boolean anonymousPortfolioOptIn,
            String friendShareDefault,
            List<String> exposedFields
    ) {
        public boolean isEmpty() {
            return anonymousPortfolioOptIn == null && friendShareDefault == null && exposedFields == null;
        }
    }

    public record PrivacySettingsResponse(
            String privacySettingsId,
            boolean anonymousPortfolioOptIn,
            String friendShareDefault,
            String ownPortfolioId,
            List<String> exposedFields,
            PrivacyPreview preview,
            String consentVersion,
            String updatedAt
    ) {
    }

    public record PrivacyPreview(String portfolioId, String displayName, List<String> hiddenFields) {
    }

    public record PrivacyWithdrawRequest(
            @NotBlank String scope,
            String portfolioId,
            String reason
    ) {
    }

    public record PrivacyWithdrawResponse(
            String status,
            String withdrawnAt,
            List<String> affectedPortfolioIds
    ) {
    }

    public record AppScreenResponse(
            String screenId,
            String title,
            String tab,
            String statusBarTime,
            String heroAsset,
            List<AppSection> sections,
            Map<String, Object> meta
    ) {
    }

    public record AppSection(
            String id,
            String kind,
            String title,
            String subtitle,
            String detailPath,
            String heroAsset,
            List<AppMetric> metrics,
            List<AppItem> items,
            List<AppAction> actions,
            Map<String, Object> data
    ) {
    }

    public record AppMetric(
            String label,
            String value,
            String caption,
            String tone,
            Integer progress
    ) {
    }

    public record AppItem(
            String id,
            String title,
            String subtitle,
            String value,
            String caption,
            String icon,
            String tone,
            String detailPath,
            Map<String, Object> data
    ) {
    }

    public record AppAction(
            String label,
            String path,
            String method,
            String tone,
            String intent
    ) {
    }

    public record AppCompareSearchRequest(
            String ageBand,
            String incomeBand,
            String jobCategory,
            String moneyStyle,
            String area
    ) {
    }

    public record AppMissionFeedbackRequest(
            @NotBlank String status,
            String note
    ) {
    }

    public record AppBirthdayContributionRequest(
            @NotNull Integer amount,
            String message,
            Boolean anonymous
    ) {
    }

    public record AppActionResultResponse(
            String status,
            String title,
            String message,
            String nextPath,
            Map<String, Object> data
    ) {
    }

    public record AuthSignupRequest(
            @Email @NotBlank String email,
            @Size(min = 8, max = 72) String password,
            @NotBlank String displayName
    ) {
    }

    public record AuthLoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password
    ) {
    }

    public record AuthResponse(
            UserMeResponse user,
            String accessToken,
            String expiresAt
    ) {
    }

    public record UserMeResponse(
            String userId,
            String email,
            String displayName,
            boolean onboardingCompleted,
            int pointBalance,
            int virtualMoneyBalance
    ) {
    }

    public record ProductOnboardingRequest(
            @NotBlank String goalType,
            @NotBlank String moneyStyle,
            @NotBlank String householdType,
            @NotBlank String area
    ) {
    }

    public record FinancialSnapshotV1(
            String snapshotId,
            String userId,
            String month,
            int monthlyIncome,
            int monthlySpending,
            int monthlySaving,
            int investmentValue,
            int cashLikeAssets,
            double emergencyFundMonths,
            Map<String, Integer> spendingCategories,
            List<String> lifestyleTags
    ) {
    }

    public record CoachResultV1(
            String resultId,
            String snapshotId,
            String source,
            int score,
            double confidence,
            String summary,
            List<CoachInsightV1> insights,
            List<CoachRecommendationV1> recommendations
    ) {
    }

    public record CoachInsightV1(
            String type,
            String title,
            String body,
            String tone
    ) {
    }

    public record CoachRecommendationV1(
            String missionTemplateId,
            String title,
            String body,
            String difficulty,
            int rewardPoints
    ) {
    }
}
