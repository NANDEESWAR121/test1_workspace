package com.scaloz.superadmin.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

@Service
public class CookieService {

    private static final String COOKIE_NAME = "trusted_device";
    private static final String COOKIE_VERSION = "v2";

    public static class DeviceCookieData {
        private final String version;
        private final String deviceId;
        private final String rawSecret;

        public DeviceCookieData(String version, String deviceId, String rawSecret) {
            this.version = version;
            this.deviceId = deviceId;
            this.rawSecret = rawSecret;
        }

        public String getVersion() { return version; }
        public String getDeviceId() { return deviceId; }
        public String getRawSecret() { return rawSecret; }
    }

    public DeviceCookieData parseCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                String value = cookie.getValue();
                if (value != null && value.startsWith(COOKIE_VERSION + ".")) {
                    String[] parts = value.split("\\.", 3);
                    if (parts.length == 3) {
                        return new DeviceCookieData(parts[0], parts[1], parts[2]);
                    }
                }
            }
        }
        return null;
    }

    public void setCookie(HttpServletRequest request, HttpServletResponse response, String deviceId, String rawSecret) {
        String cookieValue = String.join(".", COOKIE_VERSION, deviceId, rawSecret);
        boolean isSecure = request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
        String secureFlag = isSecure ? "; Secure" : "";

        // Expiry of 1 year: 31536000 seconds
        String cookieHeader = String.format("%s=%s; Path=/; Max-Age=31536000; HttpOnly%s; SameSite=Lax",
                COOKIE_NAME, cookieValue, secureFlag);

        response.addHeader("Set-Cookie", cookieHeader);
    }

    public void deleteCookie(HttpServletResponse response) {
        response.addHeader("Set-Cookie", COOKIE_NAME + "=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax");
    }
}
