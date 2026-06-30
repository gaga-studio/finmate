package com.gagastudio.finmate.api.error;

import java.util.List;

public record ErrorResponse(String code, String message, List<FieldErrorDetail> fieldErrors) {
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, List.of());
    }
}
