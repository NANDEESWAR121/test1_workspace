package com.scaloz.superadmin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "scaloz.security.risk")
public class DeviceRiskProperties {
    private int cookieMatch = -80;
    private int fingerprintMatch = -20;
    private int ipSubnetMatch = -10;
    private int newNetwork = 50;
    private int vpnActive = 30;
    private int torActive = 80;
    private int impossibleTravel = 100;
    private int unknownDevice = 50;
    
    private int thresholdAllow = 20;
    private int thresholdNotify = 70;
    private int thresholdMfa = 120;

    public DeviceRiskProperties() {}

    public int getCookieMatch() { return cookieMatch; }
    public void setCookieMatch(int cookieMatch) { this.cookieMatch = cookieMatch; }

    public int getFingerprintMatch() { return fingerprintMatch; }
    public void setFingerprintMatch(int fingerprintMatch) { this.fingerprintMatch = fingerprintMatch; }

    public int getIpSubnetMatch() { return ipSubnetMatch; }
    public void setIpSubnetMatch(int ipSubnetMatch) { this.ipSubnetMatch = ipSubnetMatch; }

    public int getNewNetwork() { return newNetwork; }
    public void setNewNetwork(int newNetwork) { this.newNetwork = newNetwork; }

    public int getVpnActive() { return vpnActive; }
    public void setVpnActive(int vpnActive) { this.vpnActive = vpnActive; }

    public int getTorActive() { return torActive; }
    public void setTorActive(int torActive) { this.torActive = torActive; }

    public int getImpossibleTravel() { return impossibleTravel; }
    public void setImpossibleTravel(int impossibleTravel) { this.impossibleTravel = impossibleTravel; }

    public int getUnknownDevice() { return unknownDevice; }
    public void setUnknownDevice(int unknownDevice) { this.unknownDevice = unknownDevice; }

    public int getThresholdAllow() { return thresholdAllow; }
    public void setThresholdAllow(int thresholdAllow) { this.thresholdAllow = thresholdAllow; }

    public int getThresholdNotify() { return thresholdNotify; }
    public void setThresholdNotify(int thresholdNotify) { this.thresholdNotify = thresholdNotify; }

    public int getThresholdMfa() { return thresholdMfa; }
    public void setThresholdMfa(int thresholdMfa) { this.thresholdMfa = thresholdMfa; }
}
