package com.scaloz.superadmin.service;

import com.scaloz.superadmin.config.DeviceRiskProperties;
import com.scaloz.superadmin.model.LoginHistory;
import com.scaloz.superadmin.model.UserDevice;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class RiskEngineService {

    private final DeviceRiskProperties properties;
    private final CryptoMetadataService cryptoService;

    @Autowired
    public RiskEngineService(DeviceRiskProperties properties, CryptoMetadataService cryptoService) {
        this.properties = properties;
        this.cryptoService = cryptoService;
    }

    public int calculateRiskScore(
            UserDevice matchedDevice,
            boolean cookieMatched,
            boolean fingerprintMatched,
            String currentIp,
            HttpServletRequest request,
            LoginHistory lastLogin,
            List<UserDevice> knownDevices) {

        int score = 0;

        // 1. Cookie Match
        if (cookieMatched) {
            score += properties.getCookieMatch(); // usually -80
        }

        // 2. Fingerprint Match
        if (fingerprintMatched) {
            score += properties.getFingerprintMatch(); // usually -20
        }

        // 3. Unknown Device Check
        if (!cookieMatched && !fingerprintMatched) {
            score += properties.getUnknownDevice(); // usually +50
        }

        // 4. IP Subnet Match
        boolean ipSubnetMatched = false;
        if (currentIp != null) {
            for (UserDevice dev : knownDevices) {
                // Check if current IP matches or is similar to the matched device last IP
                if (matchedDevice != null) {
                    String decryptedLastIp = matchedDevice.getLastIpEncrypted() != null ? 
                            cryptoService.decrypt(matchedDevice.getLastIpEncrypted()) : null;
                    if (isIpSimilar(decryptedLastIp, currentIp)) {
                        ipSubnetMatched = true;
                        break;
                    }
                }
            }
        }

        if (ipSubnetMatched) {
            score += properties.getIpSubnetMatch(); // usually -10
        } else {
            score += properties.getNewNetwork(); // usually +50
        }

        // 5. VPN / TOR checks (Checks standard headers set by proxy/security gateways)
        if ("true".equalsIgnoreCase(request.getHeader("X-VPN-Active")) || request.getHeader("X-Proxy-Header") != null) {
            score += properties.getVpnActive(); // usually +30
        }
        if ("true".equalsIgnoreCase(request.getHeader("X-TOR-Exit")) || request.getHeader("X-Tor-Header") != null) {
            score += properties.getTorActive(); // usually +80
        }

        // 6. Impossible Travel Check
        String currentCountry = getCountryFromHeaders(request);
        if (lastLogin != null && lastLogin.getCountry() != null && !lastLogin.getCountry().equalsIgnoreCase("Unknown")
                && currentCountry != null && !currentCountry.equalsIgnoreCase("Unknown")) {
            if (!lastLogin.getCountry().equalsIgnoreCase(currentCountry)) {
                long minutes = Duration.between(lastLogin.getTimestamp(), LocalDateTime.now()).toMinutes();
                // If login locations are in different countries and time difference is less than 3 hours (180 mins)
                if (minutes >= 0 && minutes < 180) {
                    score += properties.getImpossibleTravel(); // usually +100
                }
            }
        }

        // Bound the score at a minimum of 0
        return Math.max(0, score);
    }

    public String getCountryFromHeaders(HttpServletRequest request) {
        String country = request.getHeader("CF-IPCountry");
        if (country == null || country.trim().isEmpty()) {
            country = request.getHeader("X-Country-Code");
        }
        if (country == null || country.trim().isEmpty()) {
            country = request.getHeader("X-AppEngine-Country");
        }
        if (country != null && !country.trim().isEmpty()) {
            return country.trim().toUpperCase();
        }
        return "Unknown";
    }

    public String getCityFromHeaders(HttpServletRequest request) {
        String city = request.getHeader("CF-IPCity");
        if (city == null || city.trim().isEmpty()) {
            city = request.getHeader("X-City-Name");
        }
        if (city != null && !city.trim().isEmpty()) {
            return city.trim();
        }
        return "Unknown";
    }

    private boolean isIpSimilar(String ip1, String ip2) {
        if (ip1 == null || ip2 == null) return false;
        String ip1Clean = ip1.trim();
        String ip2Clean = ip2.trim();
        if (ip1Clean.isEmpty() || ip2Clean.isEmpty()) return false;
        if (ip1Clean.equals(ip2Clean)) return true;

        if (isLocalIp(ip1Clean) && isLocalIp(ip2Clean)) return true;

        if (ip1Clean.contains(".") && ip2Clean.contains(".")) {
            String[] parts1 = ip1Clean.split("\\.");
            String[] parts2 = ip2Clean.split("\\.");
            if (parts1.length >= 3 && parts2.length >= 3) {
                return parts1[0].equals(parts2[0]) && parts1[1].equals(parts2[1]) && parts1[2].equals(parts2[2]);
            }
        }
        return false;
    }

    private boolean isLocalIp(String ip) {
        if (ip == null) return false;
        String cleanIp = ip.trim();
        return "127.0.0.1".equals(cleanIp) || "0:0:0:0:0:0:0:1".equals(cleanIp) || "::1".equals(cleanIp)
                || "localhost".equalsIgnoreCase(cleanIp);
    }
}
