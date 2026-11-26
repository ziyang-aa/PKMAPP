package com.example.pkmapp.server.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private static final String HMAC_ALG = "HmacSHA256";
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private final ObjectMapper objectMapper;
    private final byte[] secretBytes;
    private final long accessTtlSeconds;
    private final long refreshTtlSeconds;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);
    private final SecureRandom secureRandom = new SecureRandom();

    public JwtService(
            ObjectMapper objectMapper,
            @Value("${pkm.jwt.secret}") String secret,
            @Value("${pkm.jwt.access-ttl-seconds:7200}") long accessTtlSeconds,
            @Value("${pkm.jwt.refresh-ttl-seconds:2592000}") long refreshTtlSeconds
    ) {
        this.objectMapper = objectMapper;
        this.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.accessTtlSeconds = accessTtlSeconds;
        this.refreshTtlSeconds = refreshTtlSeconds;
    }

    public String hashPassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    public boolean verifyPassword(String rawPassword, String passwordHash) {
        return passwordHash != null && !passwordHash.isBlank() && passwordEncoder.matches(rawPassword, passwordHash);
    }

    public String issueAccessToken(String userId) {
        try {
            long nowSec = System.currentTimeMillis() / 1000L;
            Map<String, Object> header = new LinkedHashMap<>();
            header.put("alg", "HS256");
            header.put("typ", "JWT");

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sub", userId);
            payload.put("iat", nowSec);
            payload.put("exp", nowSec + accessTtlSeconds);

            String encodedHeader = URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(header));
            String encodedPayload = URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(payload));
            String signingInput = encodedHeader + "." + encodedPayload;
            String signature = URL_ENCODER.encodeToString(hmacSha256(signingInput));
            return signingInput + "." + signature;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign JWT", ex);
        }
    }

    public String verifyAccessTokenAndGetUserId(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return null;
        }
        try {
            String signingInput = parts[0] + "." + parts[1];
            byte[] expectedSig = hmacSha256(signingInput);
            byte[] actualSig = URL_DECODER.decode(parts[2]);
            if (!MessageDigest.isEqual(expectedSig, actualSig)) {
                return null;
            }
            byte[] payloadBytes = URL_DECODER.decode(parts[1]);
            Map<String, Object> claims = objectMapper.readValue(payloadBytes, new TypeReference<>() {});
            Object expObj = claims.get("exp");
            Object subObj = claims.get("sub");
            if (!(expObj instanceof Number expNum) || !(subObj instanceof String userId) || userId.isBlank()) {
                return null;
            }
            long nowSec = System.currentTimeMillis() / 1000L;
            if (expNum.longValue() < nowSec) {
                return null;
            }
            return userId;
        } catch (Exception ex) {
            return null;
        }
    }

    public String generateRefreshToken() {
        byte[] bytes = new byte[36];
        secureRandom.nextBytes(bytes);
        return "rt_" + URL_ENCODER.encodeToString(bytes);
    }

    public long refreshExpiresAtMillis(long nowMillis) {
        return nowMillis + refreshTtlSeconds * 1000L;
    }

    public String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private byte[] hmacSha256(String data) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALG);
        mac.init(new SecretKeySpec(secretBytes, HMAC_ALG));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }
}
