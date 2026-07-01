package com.gagastudio.finmate.api.product;

import com.gagastudio.finmate.api.dto.ApiDtos.FinancialSnapshotV1;

public interface FinancialDataProvider {
    FinancialSnapshotV1 snapshotFor(String userId);
}
