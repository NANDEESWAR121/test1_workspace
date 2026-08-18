package com.scaloz.superadmin.model;

import jakarta.persistence.*;

@Entity
@Table(name = "tenants")
public class Tenant {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Tenant.class);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_name", length = 100, nullable = false)
    private String name;

    @Column(name = "tenant_code", length = 50, nullable = false, unique = true)
    private String code;

    @Column(name = "company_email", length = 50, nullable = false)
    private String email;

    @Column(name = "company_phone", length = 15)
    private String phone;

    @Column(name = "country_code", length = 10)
    private String countryCode;

    @Column(name = "company_landline", length = 15)
    private String landline;

    @Column(name = "company_website", length = 100)
    private String website;

    @Column(name = "company_size")
    private String companySize;

    @Column(name = "company_address", length = 255)
    private String address;

    @Lob
    @Column(name = "company_logo")
    @org.hibernate.annotations.JdbcType(org.hibernate.type.descriptor.jdbc.VarbinaryJdbcType.class)
    private byte[] companyLogo;

    // Admin Details
    @Column(name = "admin_email", length = 50)
    private String adminEmail;

    @Column(name = "selected_products", columnDefinition = "TEXT")
    private String selectedProducts;

    @Transient
    private String selectedModules;

    @Transient
    private String subscriptionPlan;

    @Column(name = "status")
    private String status = "Active";

    public Tenant() {
        // Empty constructor for JPA purposes
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getLandline() {
        return landline;
    }

    public void setLandline(String landline) {
        this.landline = landline;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }



    public String getCompanySize() {
        return companySize;
    }

    public void setCompanySize(String companySize) {
        this.companySize = companySize;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    @Transient
    public String getLogo() {
        if (this.companyLogo == null || this.companyLogo.length == 0) {
            return null;
        }
        if (isLegacyFilename(this.companyLogo)) {
            return null;
        }
        byte[] processedLogo = resizeImageIfLarge(this.companyLogo, 256);
        String base64 = java.util.Base64.getEncoder().encodeToString(processedLogo);
        return "data:image/png;base64," + base64;
    }

    private byte[] resizeImageIfLarge(byte[] imageData, int maxDimension) {
        if (imageData == null || imageData.length <= 50 * 1024) { // Only resize if larger than 50KB
            return imageData;
        }
        try {
            java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(imageData);
            java.awt.image.BufferedImage originalImage = javax.imageio.ImageIO.read(bais);
            if (originalImage == null) {
                return imageData;
            }
            int originWidth = originalImage.getWidth();
            int originHeight = originalImage.getHeight();
            if (originWidth <= maxDimension && originHeight <= maxDimension) {
                return imageData;
            }

            int newWidth;
            int newHeight;
            if (originWidth > originHeight) {
                newWidth = maxDimension;
                newHeight = (originHeight * maxDimension) / originWidth;
            } else {
                newHeight = maxDimension;
                newWidth = (originWidth * maxDimension) / originHeight;
            }

            java.awt.image.BufferedImage resizedImage = new java.awt.image.BufferedImage(newWidth, newHeight,
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = resizedImage.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
            g.dispose();

            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(resizedImage, "png", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            logger.warn("[Scaloz] Warning: Failed to resize large tenant logo dynamically: {}", e.getMessage());
            return imageData;
        }
    }

    private boolean isLegacyFilename(byte[] data) {
        if (data == null || data.length == 0 || data.length > 500) {
            return false;
        }
        try {
            String str = new String(data, java.nio.charset.StandardCharsets.UTF_8).trim();
            return str.toLowerCase().endsWith(".png") ||
                   str.toLowerCase().endsWith(".jpg") ||
                   str.toLowerCase().endsWith(".jpeg") ||
                   str.toLowerCase().endsWith(".gif") ||
                   str.toLowerCase().endsWith(".svg") ||
                   str.contains("/") ||
                   str.contains("\\");
        } catch (Exception e) {
            return false;
        }
    }

    public void setLogo(String logoStr) {
        if (logoStr == null || logoStr.trim().isEmpty()) {
            this.companyLogo = null;
            return;
        }
        if (logoStr.startsWith("data:image/")) {
            try {
                int commaIndex = logoStr.indexOf(',');
                if (commaIndex != -1) {
                    String base64Data = logoStr.substring(commaIndex + 1);
                    byte[] decoded = java.util.Base64.getDecoder().decode(base64Data.trim());
                    this.companyLogo = resizeImageIfLarge(decoded, 256);
                }
            } catch (Exception e) {
                logger.error("Failed to decode base64 logo: {}", e.getMessage());
            }
        } else {
            // In case it's raw base64 or legacy value that doesn't start with data:image
            try {
                byte[] decoded = java.util.Base64.getDecoder().decode(logoStr.trim());
                this.companyLogo = resizeImageIfLarge(decoded, 256);
            } catch (Exception e) {
                // If it fails, treat it as null
                this.companyLogo = null;
            }
        }
    }



    public String getAdminEmail() {
        return adminEmail;
    }

    public void setAdminEmail(String adminEmail) {
        this.adminEmail = adminEmail;
    }



    public String getSelectedProducts() {
        return selectedProducts;
    }

    public void setSelectedProducts(String selectedProducts) {
        this.selectedProducts = selectedProducts;
    }

    public String getSelectedModules() {
        return selectedModules;
    }

    public void setSelectedModules(String selectedModules) {
        this.selectedModules = selectedModules;
    }

    public String getSubscriptionPlan() {
        return subscriptionPlan;
    }

    public void setSubscriptionPlan(String subscriptionPlan) {
        this.subscriptionPlan = subscriptionPlan;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }


}
