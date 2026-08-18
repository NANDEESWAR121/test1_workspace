package com.scaloz.superadmin.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class JwtUtils {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(JwtUtils.class);

    @Value("${scaloz.app.jwtSecret}")
    private String jwtSecret;

    @Value("${scaloz.app.encryptionKey}")
    private String encryptionKey;

    @Value("${scaloz.app.jwtExpirationMs}")
    private int jwtExpirationMs;

    @Value("${scaloz.app.jwtIssuer:scaloz-iam}")
    private String jwtIssuer;

    @Value("${scaloz.app.jwtAudience:scaloz-api}")
    private String jwtAudience;

    @jakarta.annotation.PostConstruct
    public void validateSecrets() {
        if (this.jwtSecret == null || this.jwtSecret.trim().isEmpty() ||
            this.jwtSecret.equals("${JWT_SECRET}") || this.jwtSecret.length() < 32) {
            throw new IllegalStateException("CRITICAL SECURITY ERROR: scaloz.app.jwtSecret is not configured, is set to the default placeholder, or is under 32 characters in length!");
        }

        if (this.encryptionKey == null || this.encryptionKey.trim().isEmpty() ||
            this.encryptionKey.equals("${ENCRYPTION_KEY}") || this.encryptionKey.length() < 16) {
            throw new IllegalStateException("CRITICAL SECURITY ERROR: scaloz.app.encryptionKey is not configured, is set to the default placeholder, or is under 16 characters in length!");
        }

        logger.info("[SECURITY] JWT Secret and Encryption Key successfully validated for production strength.");
    }

    private Key getSigningKey() {
        byte[] keyBytes = this.jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // ── Basic token (Super Admin) ─────────────────────────────────────
    public String generateToken(String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setIssuer(jwtIssuer)
                .setSubject(username)
                .setIssuedAt(java.util.Date.from(now))
                .setExpiration(java.util.Date.from(now.plusMillis(jwtExpirationMs)))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // ── Enriched SSO token (Tenant users) ────────────────────────────
    // Includes: tenant, role, apps, employeeId for HRMS to consume
    public String generateToken(String username, Map<String, Object> extraClaims) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setIssuer(jwtIssuer)
                .setSubject(username)
                .addClaims(extraClaims)
                .setIssuedAt(java.util.Date.from(now))
                .setExpiration(java.util.Date.from(now.plusMillis(jwtExpirationMs)))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String getUsernameFromToken(String token) {
        return getClaims(token).getSubject();
    }

    public Instant getExpirationDateFromToken(String token) {
        return java.util.Optional.ofNullable(getClaims(token).getExpiration())
                .map(java.util.Date::toInstant)
                .orElse(null);
    }

    // ── Extract any claim by key ──────────────────────────────────────
    public Object extractClaim(String token, String claimKey) {
        return getClaims(token).get(claimKey);
    }

    public String extractStringClaim(String token, String claimKey) {
        Object val = getClaims(token).get(claimKey);
        return val != null ? val.toString() : null;
    }

    @SuppressWarnings("unchecked")
    public List<String> extractListClaim(String token, String claimKey) {
        Object val = getClaims(token).get(claimKey);
        if (val instanceof List)
            return (List<String>) val;
        return List.of();
    }

    // ── Validate issuer matches scaloz-iam ───────────────────────────
    public boolean validateIssuer(String token) {
        try {
            String issuer = getClaims(token).getIssuer();
            return jwtIssuer.equals(issuer);
        } catch (Exception e) {
            return false;
        }
    }

    // ── Validate audience matches scaloz-api ─────────────────────────
    public boolean validateAudience(String token) {
        try {
            Object audObj = getClaims(token).get("aud");
            if (audObj == null) {
                return false;
            }
            if (audObj instanceof String audStr) {
                return jwtAudience.equals(audStr);
            }
            if (audObj instanceof List<?> audList) {
                return audList.contains(jwtAudience);
            }
            return jwtAudience.equals(audObj.toString());
        } catch (Exception e) {
            return false;
        }
    }

    // ── Centralized Role Normalization ─────────────────────────────────
    public String normalizeRole(String role) {
        if (role == null || role.trim().isEmpty()) {
            return "";
        }
        String cleanRole = role.trim().toUpperCase();
        if (cleanRole.startsWith("ROLE_")) {
            cleanRole = cleanRole.substring(5);
        }
        return cleanRole;
    }

    public boolean isSystemRole(String role) {
        return "SYSTEM".equals(normalizeRole(role));
    }

    // ── Validate complete SYSTEM service token ────────────────────────
    public boolean validateSystemToken(String token) {
        boolean validSig = validateToken(token);
        boolean validIss = validateIssuer(token);
        boolean validAud = validateAudience(token);
        String role = extractStringClaim(token, "role");
        String sub = getUsernameFromToken(token);
        Instant exp = getExpirationDateFromToken(token);
        boolean sysRole = isSystemRole(role);

        logger.info("[Scaloz Auth Debug SystemToken] sub: {}, rawRole: {}, normalizedRole: {}, validSig: {}, validIss: {}, validAud: {}, isSystemRole: {}, exp: {}",
                sub, role, normalizeRole(role), validSig, validIss, validAud, sysRole, exp);

        if (!validSig) {
            logger.warn("[Scaloz Auth Debug SystemToken] Rejecting token: Signature or Expiration failure!");
            return false;
        }
        if (!validIss) {
            logger.warn("[Scaloz Auth Debug SystemToken] Rejecting token: Issuer mismatch! Expected: {}", jwtIssuer);
            return false;
        }
        if (!validAud) {
            logger.warn("[Scaloz Auth Debug SystemToken] Rejecting token: Audience mismatch! Expected: {}", jwtAudience);
            return false;
        }
        if (!sysRole) {
            logger.warn("[Scaloz Auth Debug SystemToken] Rejecting token: Role mismatch! Expected: SYSTEM / ROLE_SYSTEM, got: {}", role);
            return false;
        }

        return true;
    }

    private Key getHexSigningKey() {
        try {
            if (this.jwtSecret != null && this.jwtSecret.length() % 2 == 0 && this.jwtSecret.matches("^[0-9a-fA-F]+$")) {
                int len = this.jwtSecret.length();
                byte[] data = new byte[len / 2];
                for (int i = 0; i < len; i += 2) {
                    data[i / 2] = (byte) ((Character.digit(this.jwtSecret.charAt(i), 16) << 4)
                            + Character.digit(this.jwtSecret.charAt(i + 1), 16));
                }
                return Keys.hmacShaKeyFor(data);
            }
        } catch (Exception ignored) {
        }
        return getSigningKey();
    }

    public boolean validateToken(String authToken) {
        try {
            Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(authToken);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            try {
                Jwts.parserBuilder().setSigningKey(getHexSigningKey()).build().parseClaimsJws(authToken);
                return true;
            } catch (Exception ex) {
                logger.error("Invalid JWT signature/token: {}", e.getMessage());
            }
        }
        return false;
    }

    private Claims getClaims(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            return Jwts.parserBuilder()
                    .setSigningKey(getHexSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        }
    }

    // ── Dynamic custom secret token methods (Option 2) ────────────────
    private Key getSigningKey(String customSecret) {
        byte[] keyBytes = customSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] paddedBytes = new byte[32];
            System.arraycopy(keyBytes, 0, paddedBytes, 0, keyBytes.length);
            for (int i = keyBytes.length; i < 32; i++) {
                paddedBytes[i] = (byte) 0;
            }
            keyBytes = paddedBytes;
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(String username, Map<String, Object> extraClaims, String customSecret) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setIssuer(jwtIssuer)
                .setSubject(username)
                .addClaims(extraClaims)
                .setIssuedAt(java.util.Date.from(now))
                .setExpiration(java.util.Date.from(now.plusMillis(300000))) // 5 minutes expiration
                .signWith(getSigningKey(customSecret), SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean validateToken(String authToken, String customSecret) {
        try {
            Jwts.parserBuilder().setSigningKey(getSigningKey(customSecret)).build().parseClaimsJws(authToken);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            logger.error("Invalid JWT signature/token with custom secret: {}", e.getMessage());
        }
        return false;
    }

    public Claims getClaims(String token, String customSecret) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey(customSecret))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
