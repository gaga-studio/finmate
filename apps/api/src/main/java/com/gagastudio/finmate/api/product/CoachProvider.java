package com.gagastudio.finmate.api.product;

import com.gagastudio.finmate.api.dto.ApiDtos.CoachResultV1;
import com.gagastudio.finmate.api.dto.ApiDtos.FinancialSnapshotV1;

public interface CoachProvider {
    CoachResultV1 coach(FinancialSnapshotV1 snapshot);
}
