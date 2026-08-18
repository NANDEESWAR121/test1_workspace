package com.scaloz.superadmin.security;

import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenBlacklistService {

    private final ConcurrentHashMap<String, Long> blacklistedTokens = new ConcurrentHashMap<>();

    /**
     * Blacklists a token until its expiration time.
     *
     * @param token            the JWT token
     * @param expirationTimeMs the token's expiration timestamp in milliseconds
     */
    public void blacklistToken(String token, long expirationTimeMs) {
        if (token != null) {
            blacklistedTokens.put(token, expirationTimeMs);
        }
    }

    /**
     * Checks if a token is blacklisted.
     *
     * @param token the JWT token
     * @return true if blacklisted and not yet expired; false otherwise
     */
    public boolean isBlacklisted(String token) {
        if (token == null) {
            return false;
        }
        Long exp = blacklistedTokens.get(token);
        if (exp == null) {
            return false;
        }
        if (System.currentTimeMillis() > exp) {
            blacklistedTokens.remove(token);
            return false;
        }
        return true;
    }
}
