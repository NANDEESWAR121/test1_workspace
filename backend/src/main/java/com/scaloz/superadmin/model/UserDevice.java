package com.scaloz.superadmin.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_devices", indexes = {
    @Index(name = "idx_user_devices_lookup", columnList = "user_id, user_type"),
    @Index(name = "idx_device_id_lookup", columnList = "device_id")
})
public class UserDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, unique = true, length = 36)
    private String deviceId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_type", nullable = false, length = 20)
    private String userType;

    @Column(name = "device_secret_hash", nullable = false, length = 64)
    private String deviceSecretHash;

    @Column(name = "fingerprint_hash", nullable = false, length = 64)
    private String fingerprintHash;

    @Column(name = "browser", nullable = false)
    private String browser;

    @Column(name = "os", nullable = false)
    private String os;

    @Column(name = "device_name", nullable = false)
    private String deviceName;

    @Column(name = "last_ip_encrypted", length = 128)
    private String lastIpEncrypted;

    @Column(name = "last_country_encrypted", length = 128)
    private String lastCountryEncrypted;

    @Column(name = "last_seen", nullable = false)
    private LocalDateTime lastSeen;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "login_count", nullable = false)
    private Integer loginCount = 1;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "revocation_reason")
    private String revocationReason;

    public UserDevice() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUserType() { return userType; }
    public void setUserType(String userType) { this.userType = userType; }

    public String getDeviceSecretHash() { return deviceSecretHash; }
    public void setDeviceSecretHash(String deviceSecretHash) { this.deviceSecretHash = deviceSecretHash; }

    public String getFingerprintHash() { return fingerprintHash; }
    public void setFingerprintHash(String fingerprintHash) { this.fingerprintHash = fingerprintHash; }

    public String getBrowser() { return browser; }
    public void setBrowser(String browser) { this.browser = browser; }

    public String getOs() { return os; }
    public void setOs(String os) { this.os = os; }

    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }

    public String getLastIpEncrypted() { return lastIpEncrypted; }
    public void setLastIpEncrypted(String lastIpEncrypted) { this.lastIpEncrypted = lastIpEncrypted; }

    public String getLastCountryEncrypted() { return lastCountryEncrypted; }
    public void setLastCountryEncrypted(String lastCountryEncrypted) { this.lastCountryEncrypted = lastCountryEncrypted; }

    public LocalDateTime getLastSeen() { return lastSeen; }
    public void setLastSeen(LocalDateTime lastSeen) { this.lastSeen = lastSeen; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public Integer getLoginCount() { return loginCount; }
    public void setLoginCount(Integer loginCount) { this.loginCount = loginCount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRevocationReason() { return revocationReason; }
    public void setRevocationReason(String revocationReason) { this.revocationReason = revocationReason; }
}
