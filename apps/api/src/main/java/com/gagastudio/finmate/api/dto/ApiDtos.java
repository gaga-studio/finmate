package com.gagastudio.finmate.api.dto;

import jakarta.validation.Valid;
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

    public record DevSyntheticImportRequest(
            @NotBlank String importVersion,
            @NotBlank String sourceRepository,
            @NotBlank String sourceCommit,
            Boolean resetSynthetic,
            @Valid @NotEmpty List<DevSyntheticUserPayload> users
    ) {
    }

    public record DevSyntheticImportResponse(
            String status,
            String importVersion,
            int requestedUsers,
            int importedUsers,
            int snapshots,
            int dailyRecords,
            int transactions,
            int missions,
            int friendships,
            int feedItems,
            int birthdayFunds
    ) {
    }

    public record DevSyntheticUserPayload(
            @NotBlank String personaId,
            @Email @NotBlank String email,
            @Size(min = 8, max = 72) String password,
            @NotBlank String displayName,
            @Valid @NotNull DevSyntheticProfilePayload profile,
            @Valid @NotNull DevSyntheticPrivacyPayload privacy,
            @Valid @NotNull DevSyntheticMyDataPayload mydata,
            @Valid @NotNull DevSyntheticWalletPayload wallet,
            @Valid @NotNull DevSyntheticSnapshotPayload snapshot,
            @Valid @NotEmpty List<DevSyntheticDailyRecordPayload> dailyRecords,
            @Valid List<DevSyntheticTransactionPayload> transactions,
            @Valid @NotEmpty List<DevSyntheticMissionPayload> missions,
            List<@NotBlank String> follows,
            @Valid List<DevSyntheticFeedPayload> feedItems,
            @Valid DevSyntheticBirthdayFundPayload birthdayFund
    ) {
    }

    public record DevSyntheticProfilePayload(
            @NotBlank String ageBand,
            @NotBlank String incomeBand,
            @NotBlank String jobCategory,
            @NotBlank String householdType,
            @NotBlank String moneyStyle,
            @NotBlank String area,
            @NotBlank String goalType,
            @NotBlank String painPoint
    ) {
    }

    public record DevSyntheticPrivacyPayload(
            @NotNull Boolean anonymousPortfolioOptIn,
            @NotBlank String friendShareDefault,
            @NotEmpty List<@NotBlank String> exposedFields
    ) {
    }

    public record DevSyntheticMyDataPayload(
            @NotBlank String consentVersion,
            @NotEmpty List<@NotBlank String> scopes
    ) {
    }

    public record DevSyntheticWalletPayload(
            @NotNull Integer pointBalance,
            @NotNull Integer virtualMoneyBalance
    ) {
    }

    public record DevSyntheticSnapshotPayload(
            @NotBlank String month,
            @NotNull Integer monthlyIncome,
            @NotNull Integer monthlySpending,
            @NotNull Integer monthlySaving,
            @NotNull Integer investmentValue,
            @NotNull Integer cashLikeAssets,
            @NotNull Double emergencyFundMonths,
            @NotNull Map<String, Integer> spendingCategories,
            @NotEmpty List<@NotBlank String> lifestyleTags
    ) {
    }

    public record DevSyntheticDailyRecordPayload(
            @NotBlank String recordDate,
            @NotNull Integer budget,
            @NotNull Integer spent,
            @NotNull Map<String, Integer> categorySpending,
            @NotBlank String missionStatus,
            @NotNull Integer pointDelta
    ) {
    }

    public record DevSyntheticTransactionPayload(
            @NotBlank String transactionId,
            @NotBlank String transactionDate,
            String transactionTime,
            @NotBlank String transactionType,
            @NotBlank String category,
            String subcategory,
            String description,
            @NotNull Integer amountKrw,
            String cashflowBucket,
            String accountRef,
            String apiRef
    ) {
    }

    public record DevSyntheticMissionPayload(
            @NotBlank String missionId,
            @NotBlank String title,
            @NotBlank String description,
            @NotBlank String status,
            @NotBlank String difficulty,
            @NotNull Integer rewardPoints,
            @NotNull Integer progress,
            @NotBlank String source
    ) {
    }

    public record DevSyntheticFeedPayload(
            @NotBlank String feedId,
            @NotBlank String actorPersonaId,
            @NotBlank String kind,
            @NotBlank String title,
            @NotBlank String body,
            Integer amount
    ) {
    }

    public record DevSyntheticBirthdayFundPayload(
            @NotBlank String fundId,
            @NotBlank String ownerPersonaId,
            @NotBlank String title,
            @NotNull Integer targetAmount,
            @NotNull Integer currentAmount,
            @NotBlank String dueDate,
            @NotBlank String status,
            @NotBlank String shareCode
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
            @NotBlank String ageBand,
            @NotBlank String incomeBand,
            @NotBlank String jobCategory,
            @NotBlank String householdType,
            @NotBlank String moneyStyle,
            @NotBlank String area,
            @NotBlank String goalType,
            @NotBlank String painPoint,
            @Valid @NotNull ProductPrivacyConsentRequest privacyConsent,
            @Valid @NotNull ProductMyDataConsentRequest mydataConsent
    ) {
    }

    public record ProductPrivacyConsentRequest(
            @NotNull Boolean anonymousPortfolioOptIn,
            @NotBlank String friendShareDefault,
            @NotEmpty List<@NotBlank String> exposedFields,
            @NotBlank String privacyConsentVersion
    ) {
    }

    public record ProductMyDataConsentRequest(
            @NotBlank String mydataConsentVersion,
            @NotEmpty List<@NotBlank String> mydataScopes
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
