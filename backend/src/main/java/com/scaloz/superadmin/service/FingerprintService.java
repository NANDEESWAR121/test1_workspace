package com.scaloz.superadmin.service;

import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class FingerprintService {

    public String parseBrowser(String userAgent) {
        if (userAgent == null) return "Unknown";
        String ua = userAgent.toLowerCase();
        if (ua.contains("edg/")) return "Edge";
        if (ua.contains("chrome/") && !ua.contains("chromium/")) return "Chrome";
        if (ua.contains("safari/") && !ua.contains("chrome/") && !ua.contains("chromium/")) return "Safari";
        if (ua.contains("firefox/")) return "Firefox";
        if (ua.contains("opr/") || ua.contains("opera/")) return "Opera";
        return "Other Browser";
    }

    public String parseOS(String userAgent) {
        if (userAgent == null) return "Unknown";
        String ua = userAgent.toLowerCase();
        if (ua.contains("windows")) return "Windows";
        if (ua.contains("macintosh") || ua.contains("mac os x")) return "macOS";
        if (ua.contains("iphone") || ua.contains("ipad")) return "iOS";
        if (ua.contains("android")) return "Android";
        if (ua.contains("linux")) return "Linux";
        return "Other OS";
    }

    public String parsePlatform(String userAgent) {
        if (userAgent == null) return "Desktop";
        String ua = userAgent.toLowerCase();
        if (ua.contains("mobile") || ua.contains("iphone") || (ua.contains("android") && ua.contains("mobile"))) {
            return "Mobile";
        }
        if (ua.contains("ipad") || ua.contains("tablet") || (ua.contains("android") && !ua.contains("mobile"))) {
            return "Tablet";
        }
        return "Desktop";
    }

    public String parseArchitecture(String userAgent) {
        if (userAgent == null) return "Unknown";
        String ua = userAgent.toLowerCase();
        if (ua.contains("x86_64") || ua.contains("amd64") || ua.contains("win64") || ua.contains("wow64")) {
            return "x64";
        }
        if (ua.contains("arm64") || ua.contains("aarch64")) {
            return "ARM64";
        }
        if (ua.contains("i686") || ua.contains("i386") || ua.contains("x86")) {
            return "x86";
        }
        return "Unknown Architecture";
    }

    public String getDeviceName(String userAgent) {
        String os = parseOS(userAgent);
        String platform = parsePlatform(userAgent);
        if ("macOS".equals(os)) return "Macbook / macOS Desktop";
        if ("Windows".equals(os)) return "Windows PC / Laptop";
        if ("iOS".equals(os)) return "iPhone / iPad";
        if ("Android".equals(os)) return "Android Device";
        if ("Linux".equals(os)) return "Linux System";
        return platform + " System (" + os + ")";
    }

    public String generateFingerprintHash(String userAgent, String acceptLanguage) {
        String browser = parseBrowser(userAgent);
        String os = parseOS(userAgent);
        String platform = parsePlatform(userAgent);
        String architecture = parseArchitecture(userAgent);
        String language = acceptLanguage != null ? acceptLanguage.trim() : "Unknown Language";

        String rawString = String.join("|", browser, os, platform, architecture, language);
        return sha256(rawString);
    }

    public String sha256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 Algorithm not found", e);
        }
    }
}
