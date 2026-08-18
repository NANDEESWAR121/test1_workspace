package com.scaloz.superadmin.service;

import com.scaloz.superadmin.config.DeviceRiskProperties;
import com.scaloz.superadmin.model.DeviceEvent;
import com.scaloz.superadmin.model.LoginHistory;
import com.scaloz.superadmin.model.UserDevice;
import com.scaloz.superadmin.model.UserSession;
import com.scaloz.superadmin.repository.DeviceEventRepository;
import com.scaloz.superadmin.repository.LoginHistoryRepository;
import com.scaloz.superadmin.repository.UserDeviceRepository;
import com.scaloz.superadmin.repository.UserSessionRepository;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DeviceSecurityService {

    private static final Logger logger = LoggerFactory.getLogger(DeviceSecurityService.class);
    private static final SecureRandom secureRandom = new SecureRandom();

    private final UserDeviceRepository userDeviceRepository;
    private final UserSessionRepository userSessionRepository;
    private final DeviceEventRepository deviceEventRepository;
    private final LoginHistoryRepository loginHistoryRepository;
    
    private final CookieService cookieService;
    private final FingerprintService fingerprintService;
    private final RiskEngineService riskEngineService;
    private final CryptoMetadataService cryptoService;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@scaloz.com}")
    private String fromEmail;

    @Autowired
    public DeviceSecurityService(
            UserDeviceRepository userDeviceRepository,
            UserSessionRepository userSessionRepository,
            DeviceEventRepository deviceEventRepository,
            LoginHistoryRepository loginHistoryRepository,
            CookieService cookieService,
            FingerprintService fingerprintService,
            RiskEngineService riskEngineService,
            CryptoMetadataService cryptoService,
            @Autowired(required = false) JavaMailSender mailSender) {
        this.userDeviceRepository = userDeviceRepository;
        this.userSessionRepository = userSessionRepository;
        this.deviceEventRepository = deviceEventRepository;
        this.loginHistoryRepository = loginHistoryRepository;
        this.cookieService = cookieService;
        this.fingerprintService = fingerprintService;
        this.riskEngineService = riskEngineService;
        this.cryptoService = cryptoService;
        this.mailSender = mailSender;
    }

    public int evaluateDeviceAndHandleSession(
            Long userId,
            String userType,
            String email,
            String jwtId,
            HttpServletRequest request,
            HttpServletResponse response) {

        String ip = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        String acceptLanguage = request.getHeader("Accept-Language");

        // 1. Parse Versioned Cookie (v2.deviceId.secret)
        CookieService.DeviceCookieData cookieData = cookieService.parseCookie(request);
        
        boolean cookieMatched = false;
        boolean fingerprintMatched = false;
        UserDevice matchedDevice = null;

        // 2. Direct Device ID + Secret Verification
        if (cookieData != null) {
            Optional<UserDevice> devOpt = userDeviceRepository.findByDeviceId(cookieData.getDeviceId());
            if (devOpt.isPresent()) {
                UserDevice dev = devOpt.get();
                // Verify ownership (Device must belong to this specific user)
                if (dev.getUserId().equals(userId) && dev.getUserType().equalsIgnoreCase(userType) 
                        && "ACTIVE".equalsIgnoreCase(dev.getStatus())) {
                    String hashedIncomingSecret = sha256(cookieData.getRawSecret());
                    if (dev.getDeviceSecretHash().equals(hashedIncomingSecret)) {
                        cookieMatched = true;
                        matchedDevice = dev;
                    } else {
                        // Potential replay / token hijacking attempt
                        logDeviceEvent(userId, userType, dev.getDeviceId(), "TOKEN_REPLAY_ATTEMPT", ip);
                        logger.warn("[SECURITY WARNING] Token replay suspected for Device ID: {}, User: {}", dev.getDeviceId(), userId);
                    }
                }
            }
        }

        // Fetch top 20 known active devices for fallback checking
        List<UserDevice> knownDevices = userDeviceRepository
                .findTop20ByUserIdAndUserTypeAndStatusOrderByLastSeenDesc(userId, userType, "ACTIVE");

        // 3. Fingerprint Fallback Check (If cookie verification failed)
        String currentFingerprint = fingerprintService.generateFingerprintHash(userAgent, acceptLanguage);
        if (!cookieMatched) {
            for (UserDevice dev : knownDevices) {
                if (currentFingerprint.equals(dev.getFingerprintHash())) {
                    fingerprintMatched = true;
                    matchedDevice = dev;
                    logDeviceEvent(userId, userType, dev.getDeviceId(), "FINGERPRINT_MATCH", ip);
                    break;
                }
            }
        }

        // Fetch last login history for location travel checks
        List<LoginHistory> history = loginHistoryRepository
                .findTop10ByUserIdAndUserTypeOrderByTimestampDesc(userId, userType);
        LoginHistory lastLogin = history.isEmpty() ? null : history.get(0);

        // 4. Calculate Risk Score via Risk Engine
        int riskScore = riskEngineService.calculateRiskScore(
                matchedDevice, cookieMatched, fingerprintMatched, ip, request, lastLogin, knownDevices);

        logger.info("[Risk Engine] Computed Risk Score: {} for User ID: {} ({})", riskScore, userId, userType);

        String deviceIdForAudit = null;
        String status = "SUCCESS";

        // 5. Take Action Based on Risk Score Thresholds
        if (riskScore >= 71) {
            // Trigger Email Alert (Only if user has at least one registered active device)
            if (!knownDevices.isEmpty()) {
                sendSecureNotificationEmail(email, fingerprintService.getDeviceName(userAgent),
                        fingerprintService.parseBrowser(userAgent), ip, riskEngineService.getCountryFromHeaders(request));
            }
        }

        // 6. Rotate Cookie & Save/Update Device Records
        if (cookieMatched && matchedDevice != null) {
            // Normal Rotate
            String newSecret = generateSecureSecret();
            matchedDevice.setDeviceSecretHash(sha256(newSecret));
            matchedDevice.setLastIpEncrypted(cryptoService.encrypt(ip));
            matchedDevice.setLastCountryEncrypted(cryptoService.encrypt(riskEngineService.getCountryFromHeaders(request)));
            matchedDevice.setLastSeen(LocalDateTime.now());
            matchedDevice.setExpiresAt(LocalDateTime.now().plusDays(180));
            matchedDevice.setLoginCount(matchedDevice.getLoginCount() + 1);
            userDeviceRepository.save(matchedDevice);

            cookieService.setCookie(request, response, matchedDevice.getDeviceId(), newSecret);
            logDeviceEvent(userId, userType, matchedDevice.getDeviceId(), "COOKIE_ROTATED", ip);
            deviceIdForAudit = matchedDevice.getDeviceId();
        } else if (fingerprintMatched && matchedDevice != null) {
            // Fingerprint matched: Issue a new rotated cookie
            String newSecret = generateSecureSecret();
            matchedDevice.setDeviceSecretHash(sha256(newSecret));
            matchedDevice.setLastIpEncrypted(cryptoService.encrypt(ip));
            matchedDevice.setLastCountryEncrypted(cryptoService.encrypt(riskEngineService.getCountryFromHeaders(request)));
            matchedDevice.setLastSeen(LocalDateTime.now());
            matchedDevice.setExpiresAt(LocalDateTime.now().plusDays(180));
            matchedDevice.setLoginCount(matchedDevice.getLoginCount() + 1);
            userDeviceRepository.save(matchedDevice);

            cookieService.setCookie(request, response, matchedDevice.getDeviceId(), newSecret);
            logDeviceEvent(userId, userType, matchedDevice.getDeviceId(), "COOKIE_ROTATED", ip);
            deviceIdForAudit = matchedDevice.getDeviceId();
        } else {
            // Completely New Device -> Register it
            String newDeviceId = UUID.randomUUID().toString();
            String newSecret = generateSecureSecret();

            UserDevice newDevice = new UserDevice();
            newDevice.setDeviceId(newDeviceId);
            newDevice.setUserId(userId);
            newDevice.setUserType(userType);
            newDevice.setDeviceSecretHash(sha256(newSecret));
            newDevice.setFingerprintHash(currentFingerprint);
            newDevice.setBrowser(fingerprintService.parseBrowser(userAgent));
            newDevice.setOs(fingerprintService.parseOS(userAgent));
            newDevice.setDeviceName(fingerprintService.getDeviceName(userAgent));
            newDevice.setLastIpEncrypted(cryptoService.encrypt(ip));
            newDevice.setLastCountryEncrypted(cryptoService.encrypt(riskEngineService.getCountryFromHeaders(request)));
            newDevice.setLastSeen(LocalDateTime.now());
            newDevice.setExpiresAt(LocalDateTime.now().plusDays(180));
            newDevice.setLoginCount(1);

            userDeviceRepository.save(newDevice);
            cookieService.setCookie(request, response, newDeviceId, newSecret);
            logDeviceEvent(userId, userType, newDeviceId, "DEVICE_REGISTERED", ip);
            deviceIdForAudit = newDeviceId;
        }

        // 7. Bind and Register User Session
        UserSession session = new UserSession();
        session.setUserId(userId);
        session.setUserType(userType);
        session.setDeviceId(deviceIdForAudit);
        session.setJwtId(jwtId);
        session.setExpiresAt(LocalDateTime.now().plusDays(1)); // JWT standard shelf life
        session.setLastActivity(LocalDateTime.now());
        userSessionRepository.save(session);

        // 8. Log audit trail in login_history
        LoginHistory log = new LoginHistory();
        log.setUserId(userId);
        log.setUserType(userType);
        if (matchedDevice != null) {
            log.setDeviceId(matchedDevice.getId());
        }
        log.setIpAddress(ip);
        log.setUserAgent(userAgent != null && userAgent.length() > 512 ? userAgent.substring(0, 512) : userAgent);
        log.setBrowser(fingerprintService.parseBrowser(userAgent));
        log.setOs(fingerprintService.parseOS(userAgent));
        log.setCountry(riskEngineService.getCountryFromHeaders(request));
        log.setCity(riskEngineService.getCityFromHeaders(request));
        log.setRiskScore(riskScore);
        log.setStatus(status);
        loginHistoryRepository.save(log);

        return riskScore;
    }

    private void logDeviceEvent(Long userId, String userType, String deviceId, String eventType, String ip) {
        DeviceEvent event = new DeviceEvent();
        event.setUserId(userId);
        event.setUserType(userType);
        event.setDeviceId(deviceId);
        event.setEventType(eventType);
        event.setIpAddress(ip);
        deviceEventRepository.save(event);
    }

    private String generateSecureSecret() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String data) {
        return fingerprintService.sha256(data);
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }

    private void sendSecureNotificationEmail(String toEmail, String deviceName, String browserName, String ip, String country) {
        new Thread(() -> {
            try {
                if (mailSender != null && toEmail != null && !toEmail.isEmpty()) {
                    MimeMessage message = mailSender.createMimeMessage();
                    MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                    helper.setFrom(fromEmail);
                    helper.setTo(toEmail);
                    helper.setSubject("Security Alert: New Sign-in Detected");

                    String formattedDate = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                            .format(java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata"))) + " IST";

                    String htmlMsg = "<div style='font-family:\"Inter\",\"Helvetica Neue\",Helvetica,Arial,sans-serif;max-width:550px;margin:30px auto;padding:40px;border:1px solid #e2e8f0;border-radius:12px;background:#ffffff;box-shadow:0 4px 6px -1px rgba(0,0,0,0.05);'>"
                            + "<div style='text-align:center;margin-bottom:30px;'>"
                            + "  <span style='background:#fffbeb;color:#d97706;padding:12px;border-radius:50%;display:inline-block;font-size:24px;width:32px;height:32px;line-height:32px;font-weight:bold;'>⚠️</span>"
                            + "</div>"
                            + "<h2 style='color:#1e293b;font-size:20px;font-weight:700;margin-top:0;margin-bottom:12px;text-align:center;'>New Sign-in Detected</h2>"
                            + "<p style='color:#475569;font-size:14px;line-height:1.6;'>Hello,</p>"
                            + "<p style='color:#475569;font-size:14px;line-height:1.6;'>A new login was detected on your Scaloz account. Below are the details:</p>"
                            + "<div style='background:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;padding:20px;margin:24px 0;'>"
                            + "  <table style='width:100%;font-size:13px;border-collapse:collapse;color:#334155;'>"
                            + "    <tr><td style='padding:6px 0;font-weight:600;width:140px;'>Device Type:</td><td style='padding:6px 0;color:#0f172a;'>"
                            + deviceName + "</td></tr>"
                            + "    <tr><td style='padding:6px 0;font-weight:600;'>Browser:</td><td style='padding:6px 0;color:#0f172a;'>"
                            + browserName + "</td></tr>"
                            + "    <tr><td style='padding:6px 0;font-weight:600;'>IP Address:</td><td style='padding:6px 0;color:#0f172a;'>"
                            + ip + "</td></tr>"
                            + "    <tr><td style='padding:6px 0;font-weight:600;'>Time:</td><td style='padding:6px 0;color:#0f172a;'>"
                            + formattedDate + "</td></tr>"
                            + "  </table>"
                            + "</div>"
                            + "<div style='background:#fffbeb;border-left:4px solid #f59e0b;padding:16px;border-radius:4px;margin-bottom:30px;'>"
                            + "  <p style='margin:0;font-size:13px;color:#b45309;line-height:1.6;'>"
                            + "    <strong>If this was you:</strong> You can safely ignore this email. A new secure cookie has been set on your browser.<br>"
                            + "    <strong>If this wasn't you:</strong> Please log in to your account, reset your password immediately, and revoke any unrecognized sessions under Security Settings."
                            + "  </p>"
                            + "</div>"
                            + "<hr style='border:none;border-top:1px solid #e2e8f0;margin:30px 0;'>"
                            + "<p style='font-size:12px;color:#94a3b8;text-align:center;margin:0;'>"
                            + "  Regards,<br>"
                            + "  <strong>Scaloz Security Team</strong>"
                            + "</p>"
                            + "</div>";

                    helper.setText(htmlMsg, true);
                    mailSender.send(message);
                    logger.info("Sent secure login alert email successfully to: {}", toEmail);
                }
            } catch (Exception e) {
                logger.error("Failed to send login alert email to {}: {}", toEmail, e.getMessage());
            }
        }).start();
    }
}
