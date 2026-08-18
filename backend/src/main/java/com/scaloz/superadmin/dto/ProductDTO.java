package com.scaloz.superadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ProductDTO {

    private Long id;

    @Size(max = 100, message = "Product Name cannot exceed 100 characters.")
    private String name;

    @NotBlank(message = "Product Code is required.")
    @Size(max = 50, message = "Product Code cannot exceed 50 characters.")
    private String code;

    @NotBlank(message = "Base URL is required.")
    @Size(max = 100, message = "Base URL cannot exceed 100 characters.")
    @Pattern(regexp = "https?://.*", message = "Base URL must start with http:// or https://")
    private String url;

    @NotBlank(message = "Product Icon is required.")
    private String icon;

    @NotBlank(message = "Product Description is required.")
    @Size(max = 500, message = "Product Description cannot exceed 500 characters.")
    private String content;

    private String status;

    private String syncTenantUrl;

    private String syncUserUrl;

    public ProductDTO() {
        // Empty constructor for serialization purposes
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

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSyncTenantUrl() {
        return syncTenantUrl;
    }

    public void setSyncTenantUrl(String syncTenantUrl) {
        this.syncTenantUrl = syncTenantUrl;
    }

    public String getSyncUserUrl() {
        return syncUserUrl;
    }

    public void setSyncUserUrl(String syncUserUrl) {
        this.syncUserUrl = syncUserUrl;
    }
}
