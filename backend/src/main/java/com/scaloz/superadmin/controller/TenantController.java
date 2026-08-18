package com.scaloz.superadmin.controller;

import com.scaloz.superadmin.dto.TenantDTO;
import com.scaloz.superadmin.service.TenantService;
import com.scaloz.superadmin.repository.TenantRepository;
import com.scaloz.superadmin.repository.ProductRepository;
import com.scaloz.superadmin.model.Tenant;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private static final String EMAIL_REGEX = "^\\S+@\\S+\\.\\S+$";

    private static final String KEY_MESSAGE = "message";

    private final TenantService tenantService;
    private final TenantRepository tenantRepository;
    private final ProductRepository productRepository;

    public TenantController(TenantService tenantService,
                             TenantRepository tenantRepository,
                             ProductRepository productRepository) {
        this.tenantService = tenantService;
        this.tenantRepository = tenantRepository;
        this.productRepository = productRepository;
    }

    public static boolean isProductActive(String selectedProducts, String productCode) {
        if (selectedProducts == null || selectedProducts.trim().isEmpty()) {
            return false;
        }
        String[] products = selectedProducts.split(",");
        for (String p : products) {
            String trimmed = p.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.contains(":")) {
                String[] parts = trimmed.split(":", 2);
                String code = parts[0].trim();
                String status = parts[1].trim();
                if (code.equalsIgnoreCase(productCode)) {
                    return status.equalsIgnoreCase("Active");
                }
            } else {
                if (trimmed.equalsIgnoreCase(productCode)) {
                    return true;
                }
            }
        }
        return false;
    }

    @GetMapping
    public List<TenantDTO> getAllTenants() {
        return tenantService.getAllTenants();
    }

    @PostMapping
    public ResponseEntity<Object> createTenant(@RequestBody TenantDTO tenantDTO) {
        Optional<ResponseEntity<Object>> validationResult = validateTenant(tenantDTO);
        if (validationResult.isPresent()) {
            return validationResult.get();
        }

        if (tenantDTO.getName() != null && !tenantDTO.getName().trim().isEmpty()
                && tenantRepository.findByNameIgnoreCase(tenantDTO.getName().trim()).isPresent()) {
            return ResponseEntity.badRequest().body("Tenant Name is already existing.");
        }

        if (tenantRepository.findByCode(tenantDTO.getCode()).isPresent()) {
            return ResponseEntity.badRequest().body("Tenant ID is already existing.");
        }
        
        if (tenantDTO.getAdminEmail() != null && !tenantDTO.getAdminEmail().trim().isEmpty()
                && !tenantRepository.findByAdminEmail(tenantDTO.getAdminEmail().trim()).isEmpty()) {
            return ResponseEntity.badRequest().body("Admin Email is already existing.");
        }

        if (tenantDTO.getPhone() != null && !tenantDTO.getPhone().trim().isEmpty()
                && tenantRepository.findByPhone(tenantDTO.getPhone().trim()).isPresent()) {
            return ResponseEntity.badRequest().body("Mobile number is already existing.");
        }

        try {
            TenantDTO savedTenant = tenantService.createTenant(tenantDTO);
            return ResponseEntity.ok(savedTenant);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error creating tenant: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Object> updateTenant(@PathVariable Long id, @RequestBody TenantDTO updatedTenantDTO) {
        Optional<ResponseEntity<Object>> validationResult = validateTenant(updatedTenantDTO);
        if (validationResult.isPresent()) {
            return validationResult.get();
        }

        if (updatedTenantDTO.getName() != null && !updatedTenantDTO.getName().trim().isEmpty()) {
            Optional<Tenant> existingName = tenantRepository.findByNameIgnoreCase(updatedTenantDTO.getName().trim());
            if (existingName.isPresent() && !existingName.get().getId().equals(id)) {
                return ResponseEntity.badRequest().body("Tenant Name is already existing.");
            }
        }

        if (updatedTenantDTO.getPhone() != null && !updatedTenantDTO.getPhone().trim().isEmpty()) {
            Optional<Tenant> existingPhone = tenantRepository.findByPhone(updatedTenantDTO.getPhone().trim());
            if (existingPhone.isPresent() && !existingPhone.get().getId().equals(id)) {
                return ResponseEntity.badRequest().body("Mobile number is already existing.");
            }
        }

        try {
            TenantDTO savedTenant = tenantService.updateTenant(id, updatedTenantDTO);
            return ResponseEntity.ok(savedTenant);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error updating tenant: " + e.getMessage());
        }
    }

    /**
     * POST /api/tenants/upload-logo
     * Accepts a multipart image file, converts it to base64, and returns the data URL.
     */
    @PostMapping(value = "/upload-logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Object> uploadLogo(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, "No file provided."));
        }
        if (file.getSize() > 2 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, "Logo file must be smaller than 2MB."));
        }

        String contentType = file.getContentType();
        if (contentType == null || (!contentType.startsWith("image/") && !contentType.equals("image/svg+xml"))) {
            return ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, "Only image files (PNG, JPG, GIF, SVG) are allowed."));
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null) {
            String lower = originalFilename.toLowerCase();
            if (!lower.endsWith(".png") && !lower.endsWith(".jpg") && !lower.endsWith(".jpeg") && 
                !lower.endsWith(".gif") && !lower.endsWith(".svg")) {
                return ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, "Only image files with .png, .jpg, .jpeg, .gif, or .svg extensions are allowed."));
            }
        }

        try {
            byte[] bytes = file.getBytes();
            contentType = file.getContentType();
            if (contentType == null) {
                contentType = "image/png";
            }
            String base64 = java.util.Base64.getEncoder().encodeToString(bytes);
            String logoUrl = "data:" + contentType + ";base64," + base64;
            return ResponseEntity.ok(Map.of("logoUrl", logoUrl));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(KEY_MESSAGE, "Failed to process logo: " + e.getMessage()));
        }
    }

    private boolean isInvalidLength(String value, int maxLength) {
        return value != null && value.length() > maxLength;
    }

    private boolean isInvalidRegex(String value, String regex) {
        return value != null && !value.matches(regex);
    }

    private Optional<ResponseEntity<Object>> validateLengths(TenantDTO tenantDTO) {
        if (isInvalidLength(tenantDTO.getName(), 100)) {
            return Optional.of(ResponseEntity.badRequest().body("Tenant Name cannot exceed 100 characters."));
        }
        if (isInvalidLength(tenantDTO.getCode(), 50)) {
            return Optional.of(ResponseEntity.badRequest().body("Tenant Code cannot exceed 50 characters."));
        }
        if (isInvalidLength(tenantDTO.getEmail(), 50)) {
            return Optional.of(ResponseEntity.badRequest().body("Company Email cannot exceed 50 characters."));
        }
        if (isInvalidLength(tenantDTO.getWebsite(), 100)) {
            return Optional.of(ResponseEntity.badRequest().body("Company Website cannot exceed 100 characters."));
        }
        if (isInvalidLength(tenantDTO.getAddress(), 255)) {
            return Optional.of(ResponseEntity.badRequest().body("Company Address cannot exceed 255 characters."));
        }
        if (isInvalidLength(tenantDTO.getAdminEmail(), 50)) {
            return Optional.of(ResponseEntity.badRequest().body("Admin Email cannot exceed 50 characters."));
        }
        return Optional.empty();
    }

    private Optional<ResponseEntity<Object>> validateEmails(TenantDTO tenantDTO) {
        String emailVal = tenantDTO.getEmail() != null ? tenantDTO.getEmail().trim() : null;
        String adminEmailVal = tenantDTO.getAdminEmail() != null ? tenantDTO.getAdminEmail().trim() : null;

        if (isInvalidRegex(emailVal, EMAIL_REGEX)) {
            return Optional.of(ResponseEntity.badRequest().body("Company Email format is invalid."));
        }
        if (isInvalidRegex(adminEmailVal, EMAIL_REGEX)) {
            return Optional.of(ResponseEntity.badRequest().body("Personal mail id is invalid format"));
        }
        return Optional.empty();
    }

    private Optional<ResponseEntity<Object>> validatePhone(TenantDTO tenantDTO) {
        if (tenantDTO.getPhone() != null && !tenantDTO.getPhone().trim().isEmpty()) {
            String phoneVal = tenantDTO.getPhone().trim();
            if (phoneVal.length() > 15) {
                return Optional.of(ResponseEntity.badRequest().body("Company Phone cannot exceed 15 characters."));
            }
            if (!phoneVal.matches("^\\d+$")) {
                return Optional.of(ResponseEntity.badRequest().body("mobile number should be numeric"));
            }
            if (phoneVal.length() < 7) {
                return Optional.of(ResponseEntity.badRequest().body("Company Phone must be at least 7 digits."));
            }
        }
        return Optional.empty();
    }

    private Optional<ResponseEntity<Object>> validateLandline(TenantDTO tenantDTO) {
        if (tenantDTO.getLandline() != null && !tenantDTO.getLandline().trim().isEmpty()) {
            String landlineVal = tenantDTO.getLandline().trim();
            if (landlineVal.length() > 15) {
                return Optional.of(ResponseEntity.badRequest().body("Company Landline cannot exceed 15 characters."));
            }
            if (!landlineVal.matches("^\\d+$")) {
                return Optional.of(ResponseEntity.badRequest().body("Company Landline must contain numeric digits only."));
            }
            if (landlineVal.length() < 6) {
                return Optional.of(ResponseEntity.badRequest().body("Company Landline must be at least 6 digits."));
            }
        }
        return Optional.empty();
    }

    private Optional<ResponseEntity<Object>> validateProducts(TenantDTO tenantDTO) {
        if (tenantDTO.getSelectedProducts() == null || tenantDTO.getSelectedProducts().trim().isEmpty()) {
            return Optional.of(ResponseEntity.badRequest().body("At least one product platform must be selected."));
        }

        String[] products = tenantDTO.getSelectedProducts().split(",");
        for (String p : products) {
            String trimmed = p.trim();
            if (trimmed.isEmpty()) {
                return Optional.of(ResponseEntity.badRequest().body("Product platform selection cannot contain empty values."));
            }
            String pCode = trimmed;
            String status = null;
            if (trimmed.contains(":")) {
                String[] parts = trimmed.split(":", 2);
                pCode = parts[0].trim();
                status = parts[1].trim();
            }
            if (pCode.isEmpty()) {
                return Optional.of(ResponseEntity.badRequest().body("Product platform selection cannot contain empty values."));
            }
            if (productRepository.findByCode(pCode).isEmpty()) {
                return Optional.of(ResponseEntity.badRequest().body("Invalid product: " + pCode));
            }
            if (status != null && !status.equalsIgnoreCase("Active") && !status.equalsIgnoreCase("Inactive")) {
                return Optional.of(ResponseEntity.badRequest().body("Product status must be 'Active' or 'Inactive'"));
            }
        }
        return Optional.empty();
    }

    private Optional<ResponseEntity<Object>> validateTenant(TenantDTO tenantDTO) {
        Optional<ResponseEntity<Object>> error = validateLengths(tenantDTO);
        if (error.isPresent()) return error;

        error = validateEmails(tenantDTO);
        if (error.isPresent()) return error;

        error = validatePhone(tenantDTO);
        if (error.isPresent()) return error;

        error = validateLandline(tenantDTO);
        if (error.isPresent()) return error;

        return validateProducts(tenantDTO);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTenant(@PathVariable Long id) {
        try {
            tenantService.deleteTenant(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
