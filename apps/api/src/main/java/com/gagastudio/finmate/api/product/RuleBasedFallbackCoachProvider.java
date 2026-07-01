package com.gagastudio.finmate.api.product;

import com.gagastudio.finmate.api.dto.ApiDtos.CoachInsightV1;
import com.gagastudio.finmate.api.dto.ApiDtos.CoachRecommendationV1;
import com.gagastudio.finmate.api.dto.ApiDtos.CoachResultV1;
import com.gagastudio.finmate.api.dto.ApiDtos.FinancialSnapshotV1;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class RuleBasedFallbackCoachProvider implements CoachProvider {
    @Override
    public CoachResultV1 coach(FinancialSnapshotV1 snapshot) {
        int savingRate = snapshot.monthlyIncome() == 0 ? 0 : (int) Math.round((snapshot.monthlySaving() * 100.0) / snapshot.monthlyIncome());
        int score = Math.max(48, Math.min(88, 58 + savingRate / 2 + (int) Math.round(snapshot.emergencyFundMonths() * 8)));
        return new CoachResultV1(
                "coach-" + UUID.randomUUID(),
                snapshot.snapshotId(),
                "RULE_BASED_FALLBACK",
                score,
                0.72,
                "저축 습관은 안정적이지만 비상금과 고정 지출 관리 여지가 있어요.",
                List.of(
                        new CoachInsightV1("SAVING", "저축 루틴이 만들어지고 있어요.", "수입 대비 저축 흐름이 꾸준해 미션 기반 성장에 적합합니다.", "green"),
                        new CoachInsightV1("EMERGENCY_FUND", "비상금은 아직 짧아요.", "현재 비상금은 약 " + snapshot.emergencyFundMonths() + "개월 수준이라 1개월 목표가 먼저입니다.", "purple"),
                        new CoachInsightV1("SPENDING", "식비와 고정비를 먼저 보면 좋아요.", "이번 달 소비에서 생활비 항목이 가장 큰 개선 후보입니다.", "orange")
                ),
                List.of(
                        new CoachRecommendationV1("mission-food", "내일 식비 10,000원 이하 사용하기", "하루 식비를 먼저 낮춰 남는 금액을 비상금으로 옮겨요.", "EASY", 120),
                        new CoachRecommendationV1("mission-fixed-cost", "고정 지출 5% 줄이기", "구독과 자동결제를 점검해 반복 지출을 낮춰요.", "NORMAL", 180),
                        new CoachRecommendationV1("mission-saving", "3일 연속 저축 성공하기", "작은 자동저축을 이어가며 비상금 루틴을 완성해요.", "EASY", 200)
                )
        );
    }
}
