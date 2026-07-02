package com.gagastudio.finmate.api.auth;

import com.gagastudio.finmate.api.dto.ApiDtos.AuthLoginRequest;
import com.gagastudio.finmate.api.dto.ApiDtos.AuthResponse;
import com.gagastudio.finmate.api.dto.ApiDtos.AuthSignupRequest;
import com.gagastudio.finmate.api.dto.ApiDtos.UserMeResponse;
import com.gagastudio.finmate.api.error.ApiException;
import com.gagastudio.finmate.api.error.FieldErrorDetail;
import com.gagastudio.finmate.api.product.ProductAppService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {
    public static final String REFRESH_COOKIE = "finmate_refresh";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ProductAppService productAppService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final long refreshTokenDays;

    public AuthService(
            JdbcTemplate jdbc,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            ProductAppService productAppService,
            @Value("${finmate.auth.refresh-token-days}") long refreshTokenDays
    ) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.productAppService = productAppService;
        this.refreshTokenDays = refreshTokenDays;
    }

    @Transactional
    public AuthResult signup(AuthSignupRequest request) {
        String email = normalizeEmail(request.email());
        if (existsByEmail(email)) {
            throw validation("email", "이미 가입된 이메일입니다.");
        }
        String userId = "user-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users (id, email, password_hash, display_name, onboarding_completed)
                VALUES (?, ?, ?, ?, FALSE)
                """, userId, email, passwordEncoder.encode(request.password()), request.displayName().trim());
        productAppService.bootstrapUser(userId, request.displayName().trim());
        return issue(userId);
    }

    @Transactional
    public AuthResult login(AuthLoginRequest request) {
        String email = normalizeEmail(request.email());
        UserPassword row = userPassword(email);
        if (row == null || !passwordEncoder.matches(request.password(), row.passwordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다.");
        }
        productAppService.bootstrapUser(row.userId(), row.displayName());
        return issue(row.userId());
    }

    @Transactional
    public AuthResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw unauthorized();
        }
        RefreshTokenRow row = refreshToken(hash(rawRefreshToken));
        if (row == null || row.revokedAt() != null || row.expiresAt().isBefore(OffsetDateTime.now())) {
            throw unauthorized();
        }
        return issue(row.userId());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        jdbc.update("UPDATE refresh_tokens SET revoked_at = now() WHERE token_hash = ?", hash(rawRefreshToken));
    }

    public String requireUserId(String authorization) {
        String userId = jwtService.requireSubject(authorization);
        String displayName = displayName(userId);
        if (displayName == null) {
            throw unauthorized();
        }
        productAppService.bootstrapUser(userId, displayName);
        return userId;
    }

    public UserMeResponse me(String authorization) {
        return productAppService.userMe(requireUserId(authorization));
    }

    private AuthResult issue(String userId) {
        JwtService.IssuedAccessToken accessToken = jwtService.issue(userId);
        String rawRefreshToken = randomToken();
        OffsetDateTime refreshExpiresAt = OffsetDateTime.now().plusDays(refreshTokenDays);
        jdbc.update("""
                INSERT INTO refresh_tokens (id, user_id, token_hash, expires_at)
                VALUES (?, ?, ?, ?)
                """, "rt-" + UUID.randomUUID(), userId, hash(rawRefreshToken), refreshExpiresAt);
        return new AuthResult(new AuthResponse(productAppService.userMe(userId), accessToken.token(), accessToken.expiresAt()), rawRefreshToken, refreshExpiresAt);
    }

    private UserPassword userPassword(String email) {
        List<UserPassword> rows = jdbc.query("""
                SELECT id, password_hash, display_name FROM users WHERE email = ?
                """, (rs, rowNum) -> new UserPassword(rs.getString("id"), rs.getString("password_hash"), rs.getString("display_name")), email);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private RefreshTokenRow refreshToken(String tokenHash) {
        List<RefreshTokenRow> rows = jdbc.query("""
                SELECT user_id, expires_at, revoked_at FROM refresh_tokens WHERE token_hash = ?
                """, (rs, rowNum) -> new RefreshTokenRow(
                rs.getString("user_id"),
                rs.getObject("expires_at", OffsetDateTime.class),
                rs.getObject("revoked_at", OffsetDateTime.class)
        ), tokenHash);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private boolean existsByEmail(String email) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email);
        return count != null && count > 0;
    }

    private String displayName(String userId) {
        List<String> rows = jdbc.query("SELECT display_name FROM users WHERE id = ?", (rs, rowNum) -> rs.getString("display_name"), userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to hash refresh token", exception);
        }
    }

    private ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required.");
    }

    private ApiException validation(String field, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Request validation failed.", List.of(new FieldErrorDetail(field, message)));
    }

    public record AuthResult(AuthResponse response, String refreshToken, OffsetDateTime refreshExpiresAt) {
    }

    private record UserPassword(String userId, String passwordHash, String displayName) {
    }

    private record RefreshTokenRow(String userId, OffsetDateTime expiresAt, OffsetDateTime revokedAt) {
    }
}
