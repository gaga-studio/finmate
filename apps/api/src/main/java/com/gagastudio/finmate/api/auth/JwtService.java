package com.gagastudio.finmate.api.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gagastudio.finmate.api.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class JwtService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String issuer;
    private final String secret;
    private final long accessTokenMinutes;

    public JwtService(
            ObjectMapper objectMapper,
            @Value("${finmate.auth.issuer}") String issuer,
            @Value("${finmate.auth.jwt-secret}") String secret,
            @Value("${finmate.auth.access-token-minutes}") long accessTokenMinutes
    ) {
        this.objectMapper = objectMapper;
        this.clock = Clock.systemUTC();
        this.issuer = issuer;
        this.secret = secret;
        this.accessTokenMinutes = accessTokenMinutes;
    }

    public IssuedAccessToken issue(String userId) {
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plusSeconds(accessTokenMinutes * 60);
        Map<String, Object> header = orderedMap("alg", "HS256", "typ", "JWT");
        Map<String, Object> payload = orderedMap(
                "iss", issuer,
                "sub", userId,
                "iat", now.getEpochSecond(),
                "exp", expiresAt.getEpochSecond()
        );
        String unsigned = encodeJson(header) + "." + encodeJson(payload);
        String token = unsigned + "." + sign(unsigned);
        return new IssuedAccessToken(token, expiresAt.toString());
    }

    public String requireSubject(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw unauthorized();
        }
        String token = authorization.substring("Bearer ".length());
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw unauthorized();
        }
        String unsigned = parts[0] + "." + parts[1];
        if (!constantTimeEquals(sign(unsigned), parts[2])) {
            throw unauthorized();
        }
        Map<String, Object> payload = decodeJson(parts[1]);
        if (!issuer.equals(payload.get("iss"))) {
            throw unauthorized();
        }
        long exp = number(payload.get("exp"));
        if (Instant.now(clock).getEpochSecond() >= exp) {
            throw unauthorized();
        }
        Object subject = payload.get("sub");
        if (subject == null || subject.toString().isBlank()) {
            throw unauthorized();
        }
        return subject.toString();
    }

    private String encodeJson(Map<String, Object> value) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(value);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to encode JWT JSON", exception);
        }
    }

    private Map<String, Object> decodeJson(String value) {
        try {
            byte[] json = Base64.getUrlDecoder().decode(value);
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception exception) {
            throw unauthorized();
        }
    }

    private String sign(String unsigned) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(unsigned.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to sign JWT", exception);
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigestSupport.equals(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    private long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw unauthorized();
    }

    private ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required.");
    }

    private Map<String, Object> orderedMap(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            map.put(values[index].toString(), values[index + 1]);
        }
        return map;
    }

    public record IssuedAccessToken(String token, String expiresAt) {
    }

    private static final class MessageDigestSupport {
        private MessageDigestSupport() {
        }

        static boolean equals(byte[] left, byte[] right) {
            if (left.length != right.length) {
                return false;
            }
            int result = 0;
            for (int index = 0; index < left.length; index++) {
                result |= left[index] ^ right[index];
            }
            return result == 0;
        }
    }
}
