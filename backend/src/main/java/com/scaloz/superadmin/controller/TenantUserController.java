
package com.scaloz.superadmin.controller;

import com.scaloz.superadmin.model.Product;
import com.scaloz.superadmin.model.Tenant;
import com.scaloz.superadmin.model.TenantUser;
import com.scaloz.superadmin.repository.ProductRepository;
import com.scaloz.superadmin.repository.TenantRepository;
import com.scaloz.superadmin.repository.TenantUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;
import java.util.HashMap;
import java.lang.reflect.Field;
import java.util.Objects;

@RestController
@RequestMapping("/api/tenant-users")
public class TenantUserController {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(TenantUserController.class);

    private static final String ROLE_ADMIN = "Admin";
    private static final String ROLE_SUB_ADMIN = "Sub Admin";
    private static final String KEY_MESSAGE = "message";
    private static final String VAL_DOB_MISSING = "Date of Birth is missing.";
    private static final String VAL_DOB_FUTURE = "Date of Birth cannot be in the future.";
    private static final String VAL_DOB_INVALID = "Date of Birth format is invalid. Please use YYYY-MM-DD or DD/MM/YYYY.";
    private static final String AADHAR_REGEX = "^\\d{12}$";
    private static final String VAL_AADHAR_INVALID = "Aadhaar Number must be exactly 12 digits and contain only numbers.";
    private static final String PAN_REGEX = "^[A-Z]{5}\\d{4}[A-Z]$";
    private static final String VAL_PAN_INVALID = "PAN Number must be of format: 5 letters, 4 numbers, and 1 letter (e.g., ABCDE1234F).";
    private static final String EMAIL_REGEX = "^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,7}$";
    private static final String PHONE_REGEX = "^\\d{7,15}$";
    private static final String NAME_REGEX = "^[a-zA-Z0-9 .\\-]+$";
    private static final String EMP_ID_REGEX = "^[a-zA-Z0-9_\\-]+$";
    private static final String VAL_EMPLOYEE_EXISTS = "Employee ID is already existing.";
    private static final String VAL_ACTIVE = "Active";
    private static final String VAL_BEARER = "Bearer ";
    private static final String KEY_EMPLOYEE_ID = "employeeId";
    private static final String KEY_FIRST_NAME = "firstName";
    private static final String KEY_LAST_NAME = "lastName";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_WORK_LOCATION = "workLocation";
    private static final String KEY_GENDER = "gender";
    private static final String KEY_DATE_OF_BIRTH = "dateOfBirth";
    private static final String KEY_AADHAR_NO = "aadharNo";
    private static final String KEY_PAN_NO = "panNo";
    private static final String KEY_PRESENT_ADDRESS = "presentAddress";
    private static final String KEY_CONTACT_NO = "contactNo";
    private static final String KEY_BLOOD_GROUP = "bloodGroup";
    private static final String KEY_JOINING_DATE = "joiningDate";
    private static final String KEY_PASSWORD = "password";
    private static final String VAL_EMPLOYEE = "Employee";
    private static final String KEY_IDENTIFIER = "identifier";
    private static final String KEY_ERROR = "error";
    private static final String KEY_SUCCESS_COUNT = "successCount";
    private static final String KEY_FAILURE_COUNT = "failureCount";

    private static final String KEY_FAILURES = "failures";

    @Value("${spring.mail.username:noreply@scaloz.com}")
    private String fromEmail;

    private final TenantUserRepository tenantUserRepository;
    private final TenantRepository tenantRepository;
    private final ProductRepository productRepository;
    private final com.scaloz.superadmin.security.JwtUtils jwtUtils;
    private final JavaMailSender mailSender;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public TenantUserController(
            TenantUserRepository tenantUserRepository,
            TenantRepository tenantRepository,
            ProductRepository productRepository,
            com.scaloz.superadmin.security.JwtUtils jwtUtils,
            @Autowired(required = false) JavaMailSender mailSender) {
        this.tenantUserRepository = tenantUserRepository;
        this.tenantRepository = tenantRepository;
        this.productRepository = productRepository;
        this.jwtUtils = jwtUtils;
        this.mailSender = mailSender;
    }

    private static final String CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789@#$!";
    private static final int PASSWORD_LENGTH = 10;

    private String generateTempPassword() {
        StringBuilder sb = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            sb.append(CHARACTERS.charAt(secureRandom.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }

    private static java.time.LocalDate parseLocalDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        String cleanStr = dateStr.trim();
        // Try YYYY-MM-DD
        try {
            return java.time.LocalDate.parse(cleanStr);
        } catch (Exception ignored) {
            // Ignore exception and try parsing with next format
        }
        // Try DD/MM/YYYY
        try {
            return java.time.LocalDate.parse(cleanStr, java.time.format.DateTimeFormatter.ofPattern("d/M/yyyy"));
        } catch (Exception ignored) {
            // Ignore exception and try parsing with next format
        }
        // Try MM/DD/YYYY
        try {
            return java.time.LocalDate.parse(cleanStr, java.time.format.DateTimeFormatter.ofPattern("M/d/yyyy"));
        } catch (Exception ignored) {
            // Ignore exception and try parsing with next format
        }
        // Try YYYY/MM/DD
        try {
            return java.time.LocalDate.parse(cleanStr, java.time.format.DateTimeFormatter.ofPattern("yyyy/M/d"));
        } catch (Exception ignored) {
            // Ignore exception and try parsing with next format
        }
        // Try DD-MM-YYYY
        try {
            return java.time.LocalDate.parse(cleanStr, java.time.format.DateTimeFormatter.ofPattern("d-M-yyyy"));
        } catch (Exception ignored) {
            // Ignore exception and try parsing with next format
        }
        return null;
    }

    private void sendWelcomeEmail(String toEmail, String name, String employeeId, String tempPassword) {
        logger.info("[Scaloz] sendWelcomeEmail called for: {}", toEmail);
        if (mailSender == null) {
            logger.error("[Scaloz] ERROR: mailSender is null! Check Spring Boot Mail properties.");
            return;
        }
        if (toEmail == null || toEmail.isBlank()) {
            logger.error("[Scaloz] ERROR: toEmail is blank!");
            return;
        }
        try {
            String cleanEmpId = employeeId;
            if (employeeId != null && employeeId.contains("_")) {
                cleanEmpId = employeeId.substring(employeeId.lastIndexOf("_") + 1);
            }

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Welcome - Your Account Credentials");
            message.setText(
                    "Dear " + name + ",\n\n" +
                            "Your account has been successfully created. Below are your credentials to access the Portal:\n\n"
                            +
                            "Employee ID: " + cleanEmpId + "\n" +
                            "Portal Link: https://scaloz.com\n" +
                            "Temporary Password: " + tempPassword + "\n\n" +
                            "Please log in and change your password at the earliest.\n\n" +
                            "Best regards,\nScaloz Team");
            logger.info("[Scaloz] Sending welcome email via SMTP from {} to {}...", fromEmail, toEmail);
            mailSender.send(message);
            logger.info("[Scaloz] SUCCESS: Welcome email sent to: {}", toEmail);
        } catch (Exception e) {
            logger.error("[Scaloz] ERROR: Could not send welcome email to {}: {}", toEmail, e.getMessage(), e);
        }
    }

    private String getCallingUserTenantCode() {
        org.springframework.security.core.Authentication authentication = 
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getDetails() instanceof java.util.Map<?, ?> details) {
            return (String) details.get("tenant");
        }
        return null;
    }

    private String getCallingUserRole() {
        org.springframework.security.core.Authentication authentication = 
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getDetails() instanceof java.util.Map<?, ?> details) {
            return (String) details.get("role");
        }
        return null;
    }

    private boolean isCallingUserAdmin() {
        String role = getCallingUserRole();
        return role != null && (role.equalsIgnoreCase("Admin") || role.equalsIgnoreCase("Sub Admin") || role.equalsIgnoreCase("Sub_Admin"));
    }

    private boolean isCallingUserSuperAdmin() {
        org.springframework.security.core.Authentication authentication = 
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));
        }
        return false;
    }

    private boolean isCallingUserAuthorizedForTenant(Tenant tenant) {
        if (isCallingUserSuperAdmin()) {
            return true;
        }
        String adminTenantCode = getCallingUserTenantCode();
        return adminTenantCode != null && tenant != null && adminTenantCode.equalsIgnoreCase(tenant.getCode()) && isCallingUserAdmin();
    }

    @GetMapping("/tenant/{tenantId}")
    public ResponseEntity<Object> getUsersByTenant(@PathVariable Long tenantId) {
        Optional<Tenant> tenantOpt = tenantRepository.findById(tenantId);
        if (!tenantOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        if (!isCallingUserAuthorizedForTenant(tenantOpt.get())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(KEY_MESSAGE, "Access denied."));
        }
        List<TenantUser> allUsers = tenantUserRepository.findByTenantId(tenantId);
        List<TenantUser> filtered = new ArrayList<>();
        for (TenantUser u : allUsers) {
            if (u.getRole() != null && u.getRole().equalsIgnoreCase(ROLE_ADMIN)) {
                continue;
            }
            filtered.add(u);
        }
        return ResponseEntity.ok(filtered);
    }

    private Optional<ResponseEntity<Object>> validateTenant(TenantUser user) {
        if (user.getTenant() == null || user.getTenant().getId() == null) {
            return Optional.of(ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, "Tenant ID is missing.")));
        }
        Optional<Tenant> tenantOpt = tenantRepository.findById(user.getTenant().getId());
        if (!tenantOpt.isPresent()) {
            return Optional.of(ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, "Invalid Tenant ID.")));
        }
        user.setTenant(tenantOpt.get());
        return Optional.empty();
    }

    @PostMapping("/onboard")
    public ResponseEntity<Object> onboardUser(@RequestBody TenantUserDto userDto) {
        TenantUser user = convertToEntity(userDto);
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, "Invalid request body."));
        }
        Optional<ResponseEntity<Object>> tenantValidation = validateTenant(user);
        if (tenantValidation.isPresent()) {
            return tenantValidation.get();
        }

        if (!isCallingUserAuthorizedForTenant(user.getTenant())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(KEY_MESSAGE, "Access denied."));
        }

        // 1. Mandatory Fields Validation
        Optional<ResponseEntity<Object>> validationResult = validateTenantUser(user, false);
        if (validationResult.isPresent()) {
            return validationResult.get();
        }

        // 2. Duplicate Checks
        String cleanEmpId = getCleanEmployeeId(user.getEmployeeId());
        String prefixedEmpId = user.getTenant().getCode() + "_" + cleanEmpId;

        Optional<ResponseEntity<Object>> duplicateResult = checkDuplicateUser(user, cleanEmpId, prefixedEmpId);
        if (duplicateResult.isPresent()) {
            return duplicateResult.get();
        }

        user.setEmployeeId(prefixedEmpId);

        // Generate and hash a temporary password (ignore any password from frontend)
        String rawPassword = generateTempPassword();
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setMustChangePassword(true);
        user.setPasswordChangedAt(java.time.LocalDateTime.now(java.time.ZoneId.systemDefault()));
        if (user.getAccountLocked() == null) {
            user.setAccountLocked(false);
        }
        if (user.getFailedAttemptCount() == null) {
            user.setFailedAttemptCount(0);
        }

        TenantUser savedUser = tenantUserRepository.save(user);

        // Sync to HRMS employee_portal table IMMEDIATELY (synchronous)
        try {
            syncToProducts(savedUser, rawPassword);
        } catch (Exception e) {
            logger.error("[Scaloz] Error syncing to HRMS employee_portal table: {}", e.getMessage(), e);
        }

        // Send welcome email in background thread (non-blocking)
        new Thread(() -> {
            try {
                sendWelcomeEmail(savedUser.getEmail(), savedUser.getFirstName() + " " + savedUser.getLastName(),
                        savedUser.getEmployeeId(), rawPassword);
                if (savedUser.getPersonalEmail() != null && !savedUser.getPersonalEmail().isBlank()
                        && !savedUser.getPersonalEmail().equals(savedUser.getEmail())) {
                    sendWelcomeEmail(savedUser.getPersonalEmail(),
                            savedUser.getFirstName() + " " + savedUser.getLastName(), savedUser.getEmployeeId(),
                            rawPassword);
                }
            } catch (Exception e) {
                logger.error("[Scaloz] Background welcome email delivery error: {}", e.getMessage(), e);
            }
        }).start();

        return ResponseEntity.ok(Map.of(KEY_MESSAGE,
                "User onboarded successfully. Employee record created in HRMS. Credentials sent via email."));
    }

    private boolean isBlankStr(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String getDomainFromEmail(String email) {
        if (email == null || !email.contains("@")) {
            return null;
        }
        return email.substring(email.indexOf("@") + 1).trim().toLowerCase();
    }

    private String getCleanEmployeeId(String employeeId) {
        if (employeeId == null) return "";
        int lastUnderscore = employeeId.lastIndexOf('_');
        return lastUnderscore != -1 ? employeeId.substring(lastUnderscore + 1) : employeeId;
    }

    private Optional<ResponseEntity<Object>> validateBasicInfo(TenantUser user) {
        if (isBlankStr(user.getFirstName())) {
            return Optional.of(ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, "First Name is missing.")));
        }
        if (user.getFirstName().length() > 50 || !user.getFirstName().trim().matches(NAME_REGEX)) {
            return Optional.of(ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, "First Name must be under 50 characters and contain only letters, numbers, spaces, hyphens, or periods.")));
        }

        if (isBlankStr(user.getLastName())) {
            return Optional.of(ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, "Last Name is missing.")));
        }
        if (user.getLastName().length() > 50 || !user.getLastName().trim().matches(NAME_REGEX)) {
            return Optional.of(ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, "Last Name must be under 50 characters and contain only letters, numbers, spaces, hyphens, or periods.")));
        }

        if (isBlankStr(user.getEmployeeId())) {
            return Optional.of(ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, "Employee ID is missing.")));
        }
        String cleanEmpId = getCleanEmployeeId(user.getEmployeeId());
        if (cleanEmpId.length() > 50 || !cleanEmpId.matches(EMP_ID_REGEX)) {
            return Optional.of(ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, "Employee ID must be under 50 characters and contain only alphanumeric characters, hyphens, or underscores.")));
        }
        return Optional.empty();
    }

    private Optional<ResponseEntity<Object>> validateDomainMatch(TenantUser user) {
        if (user.getTenant() == null || user.getTenant().getId() == null) {
            return Optional.empty();
        }
        Tenant tenant = tenantRepository.findById(user.getTenant().getId()).orElse(null);
        if (tenant == null || isBlankStr(tenant.getEmail())) {
            return Optional.empty();
        }
        String tenantDomain = getDomainFromEmail(tenant.getEmail());
        if (tenantDomain == null) {
            return Optional.empty();
        }
        String employeeDomain = getDomainFromEmail(user.getEmail());
        if (employeeDomain == null || !employeeDomain.equalsIgnoreCase(tenantDomain)) {
            return Optional.of(ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, "Work Email must belong to the tenant domain: " + tenantDomain)));
        }
        return Optional.empty();
    }

    private Optional<ResponseEntity<Object>> validateEmailsAndDomain(TenantUser user) {
        if (isBlankStr(user.getEmail())) {
            return Optional.of(ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, "Work Email is missing.")));
        }
        if (user.getEmail().length() > 100 || !user.getEmail().matches(EMAIL_REGEX)) {
            return Optional.of(ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, "Work Email format is invalid or exceeds 100 characters.")));
        }

        Optional<ResponseEntity<Object>> domainValidation = validateDomainMatch(user);
        if (domainValidation.isPresent()) {
            return domainValidation;
        }

        if (!isBlankStr(user.getPersonalEmail()) && (user.getPersonalEmail().length() > 100 || !user.getPersonalEmail().matches(EMAIL_REGEX))) {
            return Optional.of(ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, "Personal Email format is invalid or exceeds 100 characters.")));
        }
        return Optional.empty();
    }

    private Optional<ResponseEntity<Object>> validateContactsAndRole(TenantUser user) {
        if (!isBlankStr(user.getContactNo()) && !user.getContactNo().trim().matches(PHONE_REGEX)) {
            return Optional.of(ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, "Contact Number must be between 7 and 15 digits.")));
        }

        if (!isBlankStr(user.getEmergencyContactNo()) && !user.getEmergencyContactNo().trim().matches(PHONE_REGEX)) {
            return Optional.of(ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, "Emergency Contact Number must be between 7 and 15 digits.")));
        }

        if (isBlankStr(user.getRole())) {
            return Optional.of(ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, "Role is missing.")));
        }
        if (user.getRole().length() > 50) {
            return Optional.of(ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, "Role cannot exceed 50 characters.")));
        }

        if (isBlankStr(user.getWorkLocation())) {
            return Optional.of(ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, "Work Location is missing.")));
        }
        if (user.getWorkLocation().length() > 100) {
            return Optional.of(ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, "Work Location cannot exceed 100 characters.")));
        }
        return Optional.empty();
    }

    private Optional<ResponseEntity<Object>> validateAddressesAndMetadata(TenantUser user) {
        if (user.getPresentAddress() != null && user.getPresentAddress().length() > 1000) {
            return Optional.of(ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, "Present Address cannot exceed 1000 characters.")));
        }

        if (user.getPermanentAddress() != null && user.getPermanentAddress().length() > 1000) {
            return Optional.of(ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, "Permanent Address cannot exceed 1000 characters.")));
        }

        if (isBlankStr(user.getBloodGroup())) {
            return Optional.of(ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, "Blood Group is missing.")));
        }
        List<String> validBloodGroups = List.of("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-");
        if (!validBloodGroups.contains(user.getBloodGroup().trim().toUpperCase())) {
            return Optional.of(ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, "Blood Group must be one of: A+, A-, B+, B-, AB+, AB-, O+, O-.")));
        }

        if (isBlankStr(user.getAadharNo())) {
            return Optional.of(ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, "Aadhaar Number is missing.")));
        }
        String aadhar = user.getAadharNo().trim();
        if (!aadhar.matches(AADHAR_REGEX)) {
            return Optional.of(ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, VAL_AADHAR_INVALID)));
        }
        user.setAadharNo(aadhar);

        if (isBlankStr(user.getPanNo())) {
            return Optional.of(ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, "PAN Number is missing.")));
        }
        String pan = user.getPanNo().trim().toUpperCase();
        if (!pan.matches(PAN_REGEX)) {
            return Optional.of(ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, VAL_PAN_INVALID)));
        }
        user.setPanNo(pan);
        return Optional.empty();
    }

    private Optional<ResponseEntity<Object>> validateDatesAndStatus(TenantUser user, boolean isUpdate) {
        if (isBlankStr(user.getDateOfBirth())) {
            return Optional.of(ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, VAL_DOB_MISSING)));
        }
        java.time.LocalDate dobVal = parseLocalDate(user.getDateOfBirth());
        if (dobVal != null) {
            if (dobVal.isAfter(java.time.LocalDate.now(java.time.ZoneId.systemDefault()))) {
                return Optional.of(ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, VAL_DOB_FUTURE)));
            }
        } else {
            return Optional.of(ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, VAL_DOB_INVALID)));
        }

        if (isBlankStr(user.getJoiningDate())) {
            return Optional.of(ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, "Joining Date is missing.")));
        }
        java.time.LocalDate joiningVal = parseLocalDate(user.getJoiningDate());
        if (joiningVal == null) {
            return Optional.of(ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, "Joining Date format is invalid. Please use YYYY-MM-DD or DD/MM/YYYY.")));
        }

        if (!isUpdate) {
            if (isBlankStr(user.getAssignedProducts())) {
                return Optional.of(ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, "Products selection is missing.")));
            }
            if (isBlankStr(user.getStatus())) {
                return Optional.of(ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, "Status is missing.")));
            }
            if (isBlankStr(user.getGender())) {
                return Optional.of(ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, "Gender is missing.")));
            }
        }
        return Optional.empty();
    }

    private Optional<ResponseEntity<Object>> validateTenantUser(TenantUser user, boolean isUpdate) {
        Optional<ResponseEntity<Object>> basic = validateBasicInfo(user);
        if (basic.isPresent()) return basic;

        Optional<ResponseEntity<Object>> emails = validateEmailsAndDomain(user);
        if (emails.isPresent()) return emails;

        Optional<ResponseEntity<Object>> contacts = validateContactsAndRole(user);
        if (contacts.isPresent()) return contacts;

        Optional<ResponseEntity<Object>> docs = validateAddressesAndMetadata(user);
        if (docs.isPresent()) return docs;

        return validateDatesAndStatus(user, isUpdate);
    }

    private Optional<ResponseEntity<Object>> checkDuplicateUser(TenantUser user, String cleanEmpId, String prefixedEmpId) {
        if (isEmployeeIdExists(user.getTenant().getId(), cleanEmpId, prefixedEmpId)) {
            return Optional.of(ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(KEY_MESSAGE, VAL_EMPLOYEE_EXISTS)));
        }
        if (tenantUserRepository.existsByEmailIgnoreCase(user.getEmail())) {
            return Optional.of(ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(KEY_MESSAGE, "Work Email is already existing.")));
        }
        if (!isBlankStr(user.getPersonalEmail()) && tenantUserRepository.existsByPersonalEmail(user.getPersonalEmail().trim())) {
            return Optional.of(ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(KEY_MESSAGE, "Personal Email is already existing.")));
        }
        if (tenantUserRepository.existsByAadharNo(user.getAadharNo())) {
            return Optional.of(ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(KEY_MESSAGE, "Aadhaar Number is already existing.")));
        }

        String uppercasePan = user.getPanNo().toUpperCase();
        user.setPanNo(uppercasePan);
        if (tenantUserRepository.existsByPanNo(uppercasePan)) {
            return Optional.of(ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(KEY_MESSAGE, "PAN Number is already existing.")));
        }
        if (!isBlankStr(user.getContactNo()) && tenantUserRepository.existsByContactNo(user.getContactNo().trim())) {
            return Optional.of(ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(KEY_MESSAGE, "Contact Number is already existing.")));
        }
        return Optional.empty();
    }

    private boolean isEmployeeIdExists(Long tenantId, String cleanEmpId, String prefixedEmpId) {
        for (TenantUser tu : tenantUserRepository.findByTenantId(tenantId)) {
            String tuClean = getCleanEmployeeId(tu.getEmployeeId());
            if (Objects.equals(tuClean, cleanEmpId) || Objects.equals(tu.getEmployeeId(), prefixedEmpId)) {
                return true;
            }
        }
        return false;
    }

    @PutMapping("/{id}")
    public ResponseEntity<Object> updateUser(@PathVariable Long id, @RequestBody TenantUserDto userDetailsDto) {
        Optional<TenantUser> userOpt = tenantUserRepository.findById(id);
        if (!userOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        TenantUser user = userOpt.get();
        if (!isCallingUserAuthorizedForTenant(user.getTenant())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(KEY_MESSAGE, "Access denied."));
        }

        TenantUser userDetails = convertToEntity(userDetailsDto);
        if (userDetails == null) {
            return ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, "Invalid request body."));
        }

        // 1. Validate Fields formatting
        Optional<ResponseEntity<Object>> valResult = validateTenantUser(userDetails, true);
        if (valResult.isPresent()) {
            return valResult.get();
        }

        String cleanEmpId = getCleanEmployeeId(userDetails.getEmployeeId());
        String prefixedEmpId = user.getTenant().getCode() + "_" + cleanEmpId;

        // 2. Duplicate Checks
        Optional<ResponseEntity<Object>> dupResult = checkDuplicateUpdateUser(user, userDetails, cleanEmpId, prefixedEmpId);
        if (dupResult.isPresent()) {
            return dupResult.get();
        }

        user.setEmail(userDetails.getEmail());
        user.setRole(userDetails.getRole());
        user.setEmployeeId(prefixedEmpId);
        user.setAssignedProducts(userDetails.getAssignedProducts());
        user.setAssignedModules(userDetails.getAssignedModules());
        user.setIsSubAdmin(userDetails.getIsSubAdmin());
        user.setStatus(userDetails.getStatus());

        // Copy new onboarding fields
        user.setFirstName(userDetails.getFirstName());
        user.setLastName(userDetails.getLastName());
        user.setWorkLocation(userDetails.getWorkLocation());
        user.setPersonalEmail(userDetails.getPersonalEmail());
        user.setGender(userDetails.getGender());
        user.setDateOfBirth(userDetails.getDateOfBirth());
        user.setAadharNo(userDetails.getAadharNo());
        user.setPanNo(userDetails.getPanNo());
        user.setPresentAddress(userDetails.getPresentAddress());
        user.setPermanentAddress(userDetails.getPermanentAddress());
        user.setContactNo(userDetails.getContactNo());
        user.setEmergencyContactNo(userDetails.getEmergencyContactNo());
        user.setBloodGroup(userDetails.getBloodGroup());
        user.setJoiningDate(userDetails.getJoiningDate());

        TenantUser savedUser = tenantUserRepository.save(user);

        // Sync to HRMS employee_portal table IMMEDIATELY (synchronous)
        try {
            syncToProducts(savedUser, null);
        } catch (Exception e) {
            logger.error("[Scaloz] Error syncing to HRMS employee_portal table during update: {}", e.getMessage(), e);
        }
        return ResponseEntity.ok(savedUser);
    }


    private Optional<ResponseEntity<Object>> checkDuplicateUpdateUser(TenantUser user, TenantUser userDetails, String cleanEmpId, String prefixedEmpId) {
        boolean employeeExists = false;
        for (TenantUser tu : tenantUserRepository.findByTenantId(user.getTenant().getId())) {
            if (!Objects.equals(tu.getId(), user.getId())) {
                String tuClean = getCleanEmployeeId(tu.getEmployeeId());
                if (Objects.equals(tuClean, cleanEmpId) || Objects.equals(tu.getEmployeeId(), prefixedEmpId)) {
                    employeeExists = true;
                    break;
                }
            }
        }
        if (employeeExists) {
            return Optional.of(ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(KEY_MESSAGE, VAL_EMPLOYEE_EXISTS)));
        }
        if (tenantUserRepository.existsByEmailIgnoreCaseAndIdNot(userDetails.getEmail(), user.getId())) {
            return Optional.of(ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(KEY_MESSAGE, "Work Email is already existing.")));
        }
        if (!isBlankStr(userDetails.getPersonalEmail()) && tenantUserRepository.existsByPersonalEmailAndIdNot(userDetails.getPersonalEmail().trim(), user.getId())) {
            return Optional.of(ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(KEY_MESSAGE, "Personal Email is already existing.")));
        }
        if (tenantUserRepository.existsByAadharNoAndIdNot(userDetails.getAadharNo(), user.getId())) {
            return Optional.of(ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(KEY_MESSAGE, "Aadhaar Number is already existing.")));
        }

        String uppercasePan = userDetails.getPanNo().toUpperCase();
        userDetails.setPanNo(uppercasePan);
        if (tenantUserRepository.existsByPanNoAndIdNot(uppercasePan, user.getId())) {
            return Optional.of(ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(KEY_MESSAGE, "PAN Number is already existing.")));
        }
        if (!isBlankStr(userDetails.getContactNo()) && tenantUserRepository.existsByContactNoAndIdNot(userDetails.getContactNo().trim(), user.getId())) {
            return Optional.of(ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(KEY_MESSAGE, "Contact Number is already existing.")));
        }
        return Optional.empty();
    }

    public void syncToProducts(TenantUser user, String rawPassword) {
        if (user.getRole() != null && user.getRole().equalsIgnoreCase(ROLE_ADMIN)) {
            logger.info("[Scaloz] Skipping sync to products for tenant admin: {}", user.getEmployeeId());
            return;
        }
        Tenant tenant = user.getTenant();
        if (tenant == null) {
            logger.info("[Scaloz] Skipping sync for user {} because tenant is null.", user.getEmployeeId());
            return;
        }

        java.util.Set<String> activeTenantProducts = getActiveTenantProducts(tenant);
        java.util.List<String> productsToSync = getProductsToSync(user, activeTenantProducts);

        if (productsToSync.isEmpty()) {
            logger.info("[Scaloz] No new active products to sync for user {}", user.getEmployeeId());
            return;
        }

        java.util.Map<String, Object> payload = buildSyncPayload(user, rawPassword);

        new Thread(() -> {
            for (String productCode : productsToSync) {
                syncToProductWithPayload(user.getEmployeeId(), productCode, payload);
            }
        }).start();
    }

    private java.util.Set<String> getActiveTenantProducts(Tenant tenant) {
        java.util.Set<String> activeTenantProducts = new java.util.HashSet<>();
        if (tenant.getSelectedProducts() != null) {
            for (String p : tenant.getSelectedProducts().split(",")) {
                String trimmed = p.trim();
                if (trimmed.isEmpty())
                    continue;
                String productCode = trimmed;
                String status = VAL_ACTIVE;
                if (trimmed.contains(":")) {
                    String[] parts = trimmed.split(":", 2);
                    productCode = parts[0].trim();
                    status = parts[1].trim();
                }
                if (status.equalsIgnoreCase(VAL_ACTIVE)) {
                    activeTenantProducts.add(productCode);
                }
            }
        }
        return activeTenantProducts;
    }

    private java.util.List<String> getProductsToSync(TenantUser user, java.util.Set<String> activeTenantProducts) {
        java.util.List<String> productsToSync = new java.util.ArrayList<>();
        if (user.getAssignedProducts() == null || user.getAssignedProducts().trim().isEmpty()) {
            return productsToSync;
        }
        java.util.List<String> newProductCodes = java.util.Arrays.stream(user.getAssignedProducts().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        for (String code : newProductCodes) {
            if (activeTenantProducts.contains(code)) {
                productsToSync.add(code);
            } else {
                logger.info("[Scaloz] Skipping user sync for product {} because it is not active or selected for tenant {}",
                        code, user.getTenant().getCode());
            }
        }
        return productsToSync;
    }

    private java.util.Map<String, Object> buildSyncPayload(TenantUser user, String rawPassword) {
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put(KEY_EMPLOYEE_ID, user.getEmployeeId());
        payload.put(KEY_FIRST_NAME, user.getFirstName());
        payload.put(KEY_LAST_NAME, user.getLastName());
        payload.put(KEY_EMAIL, user.getEmail());
        String roleToSend = user.getRole();
        if (Boolean.TRUE.equals(user.getIsSubAdmin())) {
            roleToSend = ROLE_SUB_ADMIN;
        }
        payload.put("role", roleToSend);
        if (user.getTenant() != null) {
            payload.put("tenantId", user.getTenant().getCode());
            payload.put("tenantName", user.getTenant().getName());
            payload.put("tenantCode", user.getTenant().getCode());
            payload.put("adminEmail", user.getTenant().getAdminEmail());
        }
        payload.put(KEY_WORK_LOCATION, user.getWorkLocation());
        payload.put("personalEmail", user.getPersonalEmail());
        payload.put(KEY_GENDER, user.getGender());
        payload.put(KEY_DATE_OF_BIRTH, user.getDateOfBirth());
        payload.put(KEY_AADHAR_NO, user.getAadharNo());
        payload.put(KEY_PAN_NO, user.getPanNo());
        payload.put(KEY_PRESENT_ADDRESS, user.getPresentAddress());
        payload.put("permanentAddress", user.getPermanentAddress());
        payload.put(KEY_CONTACT_NO, user.getContactNo());
        payload.put(KEY_BLOOD_GROUP, user.getBloodGroup());
        payload.put(KEY_JOINING_DATE, user.getJoiningDate());
        if (rawPassword != null) {
            payload.put(KEY_PASSWORD, rawPassword);
        }
        return payload;
    }

    private void syncToProductWithPayload(String employeeId, String productCode, java.util.Map<String, Object> payload) {
        Optional<Product> prodOpt = productRepository.findByCode(productCode);
        if (prodOpt.isEmpty()) {
            logger.info("[Scaloz] Product with code {} not found in database. Skipping user sync.", productCode);
            return;
        }
        Product prod = prodOpt.get();
        String syncUrl = prod.getSyncUserUrl();
        if (syncUrl == null || syncUrl.trim().isEmpty()) {
            String baseUrl = prod.getUrl();
            if (baseUrl != null && !baseUrl.trim().isEmpty()) {
                baseUrl = baseUrl.trim();
                if (baseUrl.endsWith("/")) {
                    baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
                }
                syncUrl = baseUrl + "/api/external/employees";
            }
        }
        if (syncUrl == null || syncUrl.trim().isEmpty()) {
            logger.info("[Scaloz] No syncUserUrl could be resolved for product {}. Skipping sync.", productCode);
            return;
        }
        try {
            org.springframework.http.client.SimpleClientHttpRequestFactory requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(10000);
            requestFactory.setReadTimeout(10000);
            org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate(
                    requestFactory);

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            String jwtToken = jwtUtils.generateToken("system_sync", java.util.Map.of("role", "SYSTEM"));
            headers.set("Authorization", VAL_BEARER + jwtToken);

            org.springframework.http.HttpEntity<java.util.Map<String, Object>> requestEntity = new org.springframework.http.HttpEntity<>(
                    payload, headers);

            logger.info("[Scaloz] Syncing user {} to product {}: {}", employeeId, productCode, syncUrl);
            org.springframework.http.ResponseEntity<String> response = restTemplate.postForEntity(syncUrl,
                    requestEntity, String.class);
            logger.info("[Scaloz] Product {} user sync response status: {}", productCode, response.getStatusCode());
        } catch (Exception e) {
            logger.error("[Scaloz] Error syncing user to product {}: {}", productCode, e.getMessage(), e);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deleteUser(@PathVariable Long id) {
        Optional<TenantUser> userOpt = tenantUserRepository.findById(id);
        if (!userOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        TenantUser user = userOpt.get();
        if (!isCallingUserAuthorizedForTenant(user.getTenant())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(KEY_MESSAGE, "Access denied."));
        }
        tenantUserRepository.deleteById(id);
        return ResponseEntity.ok(Map.of(KEY_MESSAGE, "User deleted successfully"));
    }

    @GetMapping("/search")
    public ResponseEntity<Object> searchUsers(@RequestParam Long tenantId, @RequestParam String query) {
        Optional<Tenant> tenantOpt = tenantRepository.findById(tenantId);
        if (!tenantOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        if (!isCallingUserAuthorizedForTenant(tenantOpt.get())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(KEY_MESSAGE, "Access denied."));
        }
        List<TenantUser> allUsers = tenantUserRepository.searchTenantUsers(tenantId, query);
        List<TenantUser> filtered = new ArrayList<>();
        for (TenantUser u : allUsers) {
            if (u.getRole() != null && u.getRole().equalsIgnoreCase(ROLE_ADMIN)) {
                continue;
            }
            filtered.add(u);
        }
        return ResponseEntity.ok(filtered);
    }

    /**
     * Called by the HRMS backend when a new employee is onboarded there.
     * Creates a matching TenantUser record WITHOUT triggering the reverse HRMS sync
     * (to avoid infinite loops). Secured by X-API-Key header.
     */
    @PostMapping("/sync-from-hrms")
    public ResponseEntity<Object> syncFromHrms(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> payload) {

        boolean isAuthenticated = false;

        if (authHeader != null && authHeader.startsWith(VAL_BEARER)) {
            String jwt = authHeader.substring(7);
            if (jwtUtils.validateSystemToken(jwt)) {
                isAuthenticated = true;
            }
        }

        if (!isAuthenticated) {
            return ResponseEntity.status(403).body(Map.of(KEY_MESSAGE, "Unauthorized"));
        }

        try {
            String jwt = (authHeader != null && authHeader.startsWith("Bearer ")) ? authHeader.substring(7) : null;
            Optional<Tenant> tenantOpt = resolveMspTenant(payload, jwt);
            if (!tenantOpt.isPresent()) {
                logger.error("[Scaloz] sync-from-hrms: MSP Tenant not found for payload identifiers: {}", payload);
                return ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, "MSP Tenant not found for payload identifiers. Ensure mspTenantCode, tenantCode, or tenantId refers to an existing MSP Tenant."));
            }
            Tenant tenant = tenantOpt.get();

            String empId = (String) payload.get(KEY_EMPLOYEE_ID);
            String email = (String) payload.get(KEY_EMAIL);

            // Extract ITSM customer code (stored as metadata on TenantUser).
            // Ownership validation (customer belongs to MSP) is enforced on the ITSM side.
            // Workspace stores it for display/filtering only.
            String incomingCustomerCode = null;
            for (String ck : new String[]{"customerCode", "customer_code", "customerId", "customerRef"}) {
                Object cv = payload.get(ck);
                if (cv != null && !cv.toString().isBlank()) {
                    incomingCustomerCode = cv.toString().trim();
                    break;
                }
            }
            final String customerCode = incomingCustomerCode;

            // Validate cross-MSP customer isolation:
            // If this customerCode is already associated with users under another MSP tenant, reject to prevent cross-tenant leakage.
            if (customerCode != null && !customerCode.isBlank()) {
                Optional<TenantUser> foreignCustomerUser = tenantUserRepository.findFirstByCustomerCodeIgnoreCaseAndTenantIdNot(customerCode.trim(), tenant.getId());
                if (foreignCustomerUser.isPresent()) {
                    String existingTenantCode = foreignCustomerUser.get().getTenant() != null ? foreignCustomerUser.get().getTenant().getCode() : "unknown";
                    logger.warn("[Scaloz] sync-from-hrms: Customer code '{}' already belongs to MSP tenant '{}', cannot attach to MSP tenant '{}'",
                            customerCode, existingTenantCode, tenant.getCode());
                    return ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, "Customer '" + customerCode + "' already belongs to another MSP tenant (" + existingTenantCode + "). Cross-MSP customer association is not allowed."));
                }
            }

            if (empId != null) {
                String cleanEmpId = empId.contains("_") ? empId.substring(empId.lastIndexOf("_") + 1) : empId;
                empId = tenant.getCode() + "_" + cleanEmpId;
            }

            // Upsert: find existing user by employeeId+tenant or by email
            Optional<TenantUser> existing = tenantUserRepository.findByEmployeeIdAndTenantId(empId, tenant.getId());
            Optional<TenantUser> existingByEmail = (email != null && !email.isBlank()) ? tenantUserRepository.findFirstByEmailIgnoreCaseOrderByIdDesc(email.trim()) : Optional.empty();

            TenantUser user;
            if (existing.isPresent()) {
                user = existing.get();
            } else if (existingByEmail.isPresent()) {
                user = existingByEmail.get();
                if (user.getTenant() != null && !user.getTenant().getId().equals(tenant.getId())) {
                    logger.warn("[Scaloz] sync-from-hrms: User {} already exists under MSP tenant '{}', rejected sync attempt to MSP tenant '{}'",
                            email, user.getTenant().getCode(), tenant.getCode());
                    return ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, "User already belongs to another MSP tenant (" + user.getTenant().getCode() + "). Cross-tenant user reassignment is rejected."));
                }
            } else {
                user = new TenantUser();
                user.setTenant(tenant);
                user.setEmployeeId(empId);
                user.setEmail(email);
            }

            // Extract password from payload keys
            String rawPassword = null;
            if (payload.containsKey(KEY_PASSWORD) && payload.get(KEY_PASSWORD) != null) {
                rawPassword = (String) payload.get(KEY_PASSWORD);
            } else if (payload.containsKey("rawPassword") && payload.get("rawPassword") != null) {
                rawPassword = (String) payload.get("rawPassword");
            } else if (payload.containsKey("tempPassword") && payload.get("tempPassword") != null) {
                rawPassword = (String) payload.get("tempPassword");
            } else if (payload.containsKey("temporaryPassword") && payload.get("temporaryPassword") != null) {
                rawPassword = (String) payload.get("temporaryPassword");
            }

            if (rawPassword != null && !rawPassword.isBlank()) {
                user.setPassword(passwordEncoder.encode(rawPassword));
                user.setMustChangePassword(true);
                user.setPasswordChangedAt(java.time.LocalDateTime.now(java.time.ZoneId.systemDefault()));
            } else if (user.getId() == null || user.getPassword() == null || user.getPassword().isBlank()) {
                String defaultTemp = "Welcome123!";
                user.setPassword(passwordEncoder.encode(defaultTemp));
                user.setMustChangePassword(true);
                user.setPasswordChangedAt(java.time.LocalDateTime.now(java.time.ZoneId.systemDefault()));
            }

            String firstName = (String) payload.getOrDefault(KEY_FIRST_NAME, user.getFirstName());
            String lastName = (String) payload.getOrDefault(KEY_LAST_NAME, user.getLastName());
            user.setFirstName(firstName != null ? firstName : "");
            user.setLastName(lastName != null ? lastName : "");
            if (email != null && !email.isBlank()) {
                user.setEmail(email.trim());
            }

            String syncRole = (String) payload.getOrDefault("role", user.getRole() != null ? user.getRole() : VAL_EMPLOYEE);
            if (syncRole != null && (syncRole.equalsIgnoreCase("Admin") || syncRole.equalsIgnoreCase("Sub Admin") || syncRole.equalsIgnoreCase("Sub_Admin") || syncRole.equalsIgnoreCase("ROLE_SUPER_ADMIN"))) {
                syncRole = VAL_EMPLOYEE;
            }
            user.setRole(syncRole);
            user.setStatus(VAL_ACTIVE);
            user.setAccountLocked(false);
            user.setFailedAttemptCount(0);
            user.setLastFailedLogin(null);
            user.setWorkLocation((String) payload.get(KEY_WORK_LOCATION));
            user.setPersonalEmail((String) payload.get("personalEmail"));
            user.setGender((String) payload.get(KEY_GENDER));
            user.setDateOfBirth((String) payload.get(KEY_DATE_OF_BIRTH));
            user.setAadharNo((String) payload.get(KEY_AADHAR_NO));
            user.setPanNo((String) payload.get(KEY_PAN_NO));
            user.setPresentAddress((String) payload.get(KEY_PRESENT_ADDRESS));
            user.setPermanentAddress((String) payload.get("permanentAddress"));
            user.setContactNo((String) payload.get(KEY_CONTACT_NO));
            user.setEmergencyContactNo((String) payload.get("emergencyContactNo"));
            user.setBloodGroup((String) payload.get(KEY_BLOOD_GROUP));
            user.setJoiningDate((String) payload.get(KEY_JOINING_DATE));

            // Store ITSM customer code as metadata (update if changed; clear if absent)
            if (customerCode != null) {
                user.setCustomerCode(customerCode);
            }

            // Resolve assigned products for this user
            String incomingAssignedProducts = null;
            for (String pk : new String[]{"assignedProducts", "assigned_products", "products", "productCode", "productCodes", "appCodes"}) {
                Object pv = payload.get(pk);
                if (pv != null && !pv.toString().isBlank()) {
                    incomingAssignedProducts = pv.toString().trim();
                    break;
                }
            }

            String tenantSelectedProds = tenant.getSelectedProducts();
            String resolvedAssignedProducts = null;

            // If specific products are provided in payload, validate each against MSP tenant's active selected products
            if (incomingAssignedProducts != null && !incomingAssignedProducts.isBlank()) {
                List<String> validProducts = new ArrayList<>();
                for (String p : incomingAssignedProducts.split(",")) {
                    String cleanP = extractCleanProductCode(p);
                    if (cleanP != null && TenantController.isProductActive(tenantSelectedProds, cleanP)) {
                        if (!validProducts.contains(cleanP)) {
                            validProducts.add(cleanP);
                        }
                    } else {
                        logger.warn("[Scaloz] sync-from-hrms: Product '{}' requested for user '{}' is NOT active in MSP tenant '{}' selectedProducts ('{}')",
                                cleanP, email, tenant.getCode(), tenantSelectedProds);
                    }
                }
                if (!validProducts.isEmpty()) {
                    resolvedAssignedProducts = String.join(",", validProducts);
                }
            }

            // If no valid products specified in payload, preserve existing valid products or default dynamically:
            if (resolvedAssignedProducts == null && user.getAssignedProducts() != null && !user.getAssignedProducts().isBlank()) {
                List<String> validProducts = new ArrayList<>();
                for (String p : user.getAssignedProducts().split(",")) {
                    String cleanP = extractCleanProductCode(p);
                    if (cleanP != null && TenantController.isProductActive(tenantSelectedProds, cleanP)) {
                        if (!validProducts.contains(cleanP)) {
                            validProducts.add(cleanP);
                        }
                    }
                }
                if (!validProducts.isEmpty()) {
                    resolvedAssignedProducts = String.join(",", validProducts);
                }
            }

            // Default dynamically if still unset:
            if (resolvedAssignedProducts == null) {
                if (customerCode != null && !customerCode.isBlank()) {
                    // For customer users, assign the primary customer-facing active product (e.g. itsm or first active product)
                    if (TenantController.isProductActive(tenantSelectedProds, "itsm")) {
                        resolvedAssignedProducts = "itsm";
                    } else if (tenantSelectedProds != null && !tenantSelectedProds.isBlank()) {
                        for (String p : tenantSelectedProds.split(",")) {
                            String cleanP = extractCleanProductCode(p);
                            if (cleanP != null && TenantController.isProductActive(tenantSelectedProds, cleanP)) {
                                resolvedAssignedProducts = cleanP;
                                break;
                            }
                        }
                    }
                } else if (tenantSelectedProds != null && !tenantSelectedProds.isBlank()) {
                    // For MSP staff users, default to all active products of the tenant
                    List<String> activeTenantProds = new ArrayList<>();
                    for (String p : tenantSelectedProds.split(",")) {
                        String cleanP = extractCleanProductCode(p);
                        if (cleanP != null && TenantController.isProductActive(tenantSelectedProds, cleanP)) {
                            if (!activeTenantProds.contains(cleanP)) {
                                activeTenantProds.add(cleanP);
                            }
                        }
                    }
                    if (!activeTenantProds.isEmpty()) {
                        resolvedAssignedProducts = String.join(",", activeTenantProds);
                    }
                }
            }

            user.setAssignedProducts(resolvedAssignedProducts);

            String incomingModules = (String) payload.get("assignedModules");
            if (incomingModules != null) {
                user.setAssignedModules(incomingModules.trim());
            }

            tenantUserRepository.save(user);

            // Structured diagnostic logs
            logger.info("[Scaloz Provisioning] Resolved MSP tenant: {}", tenant.getCode());
            logger.info("[Scaloz Provisioning] Resolved customer: {}", customerCode != null ? customerCode : "(MSP staff)");
            logger.info("[Scaloz Provisioning] Provisioned user: {} ({})", empId, email);
            logger.info("[Scaloz Provisioning] Requested products: {}", incomingAssignedProducts != null ? incomingAssignedProducts : "(None)");
            logger.info("[Scaloz Provisioning] Validated products: {}", resolvedAssignedProducts != null ? resolvedAssignedProducts : "(None)");
            logger.info("[Scaloz Provisioning] Persisted products: {}", user.getAssignedProducts());

            return ResponseEntity.ok(Map.of(KEY_MESSAGE, "Synced successfully from HRMS."));
        } catch (Exception e) {
            logger.error("[Scaloz] sync-from-hrms error: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(KEY_MESSAGE, "Sync failed: " + e.getMessage()));
        }
    }

    /**
     * Updates a TenantUser's status to Inactive based on their employeeId and tenantCode.
     * Secured by System jwt token verification.
     */
    @PostMapping("/sync-status-from-hrms")
    public ResponseEntity<Object> syncStatusFromHrms(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> payload) {

        boolean isAuthenticated = false;

        if (authHeader != null && authHeader.startsWith(VAL_BEARER)) {
            String jwt = authHeader.substring(7);
            if (jwtUtils.validateSystemToken(jwt)) {
                isAuthenticated = true;
            }
        }

        if (!isAuthenticated) {
            return ResponseEntity.status(403).body(Map.of(KEY_MESSAGE, "Unauthorized"));
        }

        try {
            String jwt = (authHeader != null && authHeader.startsWith(VAL_BEARER)) ? authHeader.substring(7) : null;
            String empId = (String) payload.get(KEY_EMPLOYEE_ID);
            String status = (String) payload.get("status"); // e.g. "Inactive"

            if (empId == null || status == null) {
                return ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, "employeeId and status are required"));
            }

            Optional<Tenant> tenantOpt = resolveMspTenant(payload, jwt);
            if (!tenantOpt.isPresent()) {
                return ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, "MSP Tenant not found for payload identifiers."));
            }
            Tenant tenant = tenantOpt.get();

            String prefixedEmpId = empId;
            if (!empId.contains("_")) {
                prefixedEmpId = tenant.getCode() + "_" + empId;
            }

            Optional<TenantUser> userOpt = tenantUserRepository.findByEmployeeIdAndTenantId(prefixedEmpId, tenant.getId());
            if (!userOpt.isPresent()) {
                return ResponseEntity.status(404).body(Map.of(KEY_MESSAGE, "User not found"));
            }

            TenantUser user = userOpt.get();
            user.setStatus(status);
            tenantUserRepository.save(user);

            logger.info("[Scaloz] sync-status-from-hrms: Updated status of {} to {} in tenant {}", prefixedEmpId, status, tenant.getCode());
            return ResponseEntity.ok(Map.of(KEY_MESSAGE, "Status updated successfully."));
        } catch (Exception e) {
            logger.error("[Scaloz] sync-status-from-hrms error: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(KEY_MESSAGE, "Update failed: " + e.getMessage()));
        }
    }

    // ── Helper method to resolve existing MSP Tenant cleanly ──────────────────
    private Optional<Tenant> resolveMspTenant(Map<String, Object> payload, String jwt) {
        // 1. Check explicit MSP Tenant Code parameters in request payload
        String[] mspCodeKeys = {"mspTenantCode", "mspCode", "parentTenantCode", "mspId", "orgCode", "organizationCode"};
        for (String key : mspCodeKeys) {
            Object val = payload.get(key);
            if (val != null && !val.toString().isBlank()) {
                Optional<Tenant> t = findTenantByCodeOrName(val.toString().trim());
                if (t.isPresent()) {
                    logger.info("[Scaloz] Resolved MSP Tenant by payload key '{}': {}", key, t.get().getCode());
                    return t;
                }
            }
        }

        // 2. Check explicit MSP Tenant ID parameters in request payload
        String[] mspIdKeys = {"mspTenantId", "tenantId", "parentTenantId"};
        for (String key : mspIdKeys) {
            Object val = payload.get(key);
            if (val != null) {
                try {
                    Long tid = Long.parseLong(val.toString().trim());
                    Optional<Tenant> t = tenantRepository.findById(tid);
                    if (t.isPresent()) {
                        logger.info("[Scaloz] Resolved MSP Tenant by payload ID key '{}': {}", key, t.get().getCode());
                        return t;
                    }
                } catch (Exception ignored) {}
            }
        }

        // 3. Check 'tenant' claim in the SYSTEM JWT (if embedded in service token)
        if (jwt != null) {
            String jwtTenant = jwtUtils.extractStringClaim(jwt, "tenant");
            if (jwtTenant != null && !jwtTenant.isBlank()) {
                Optional<Tenant> t = findTenantByCodeOrName(jwtTenant.trim());
                if (t.isPresent()) {
                    logger.info("[Scaloz] Resolved MSP Tenant from JWT 'tenant' claim: {}", t.get().getCode());
                    return t;
                }
            }
        }

        // 4. Fallback: check if 'tenantCode' in payload matches an existing MSP Tenant
        String tenantCode = (String) payload.get("tenantCode");
        if (tenantCode != null && !tenantCode.isBlank()) {
            Optional<Tenant> t = findTenantByCodeOrName(tenantCode.trim());
            if (t.isPresent()) {
                logger.info("[Scaloz] Resolved MSP Tenant by payload 'tenantCode': {}", t.get().getCode());
                return t;
            }
        }

        return Optional.empty();
    }

    private Optional<Tenant> findTenantByCodeOrName(String codeOrName) {
        Optional<Tenant> tenantOpt = tenantRepository.findByCode(codeOrName);
        if (!tenantOpt.isPresent()) {
            tenantOpt = tenantRepository.findByCode(codeOrName.toUpperCase());
        }
        if (!tenantOpt.isPresent()) {
            tenantOpt = tenantRepository.findByCode(codeOrName.toLowerCase());
        }
        if (!tenantOpt.isPresent()) {
            tenantOpt = tenantRepository.findByNameIgnoreCase(codeOrName);
        }
        return tenantOpt;
    }


    @GetMapping("/template-fields")
    public ResponseEntity<List<String>> getTemplateFields() {
        List<String> fields = new ArrayList<>();
        for (Field field : TenantUser.class.getDeclaredFields()) {
            String name = field.getName();
            // Filter out helper/internal fields
            if (!name.equals("id") && !name.equals(KEY_PASSWORD) && !name.equals("tenant")
                    && !name.equals("resetToken") && !name.equals("resetTokenExpiry")
                    && !name.equals("mustChangePassword")
                    && !name.equals("assignedModules") && !name.equals("isSubAdmin")
                    && !name.equals("failedAttemptCount") && !name.equals("accountLocked")
                    && !name.equals("lastFailedLogin")) {
                fields.add(name);
            }
        }
        return ResponseEntity.ok(fields);
    }

    @PostMapping("/bulk-onboard")
    public ResponseEntity<Object> bulkOnboardUsers(@RequestBody List<TenantUserDto> usersDto) {
        int successCount = 0;
        List<Map<String, String>> failures = new ArrayList<>();

        for (int i = 0; i < usersDto.size(); i++) {
            TenantUser user = convertToEntity(usersDto.get(i));
            if (onboardSingleBulkUser(user, i, failures)) {
                successCount++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put(KEY_SUCCESS_COUNT, successCount);
        result.put(KEY_FAILURE_COUNT, failures.size());
        result.put(KEY_FAILURES, failures);

        return ResponseEntity.ok(result);
    }

    @SuppressWarnings("unchecked")
    private boolean onboardSingleBulkUser(TenantUser user, int index, List<Map<String, String>> failures) {
        String rowIdentifier;
        if (!isBlankStr(user.getEmail())) {
            rowIdentifier = user.getEmail();
        } else if (!isBlankStr(user.getEmployeeId())) {
            rowIdentifier = user.getEmployeeId();
        } else {
            rowIdentifier = "Row " + (index + 1);
        }

        try {
            Optional<ResponseEntity<Object>> tenantValidation = validateTenant(user);
            if (tenantValidation.isPresent()) {
                Map<String, String> body = (Map<String, String>) tenantValidation.get().getBody();
                failures.add(Map.of(KEY_IDENTIFIER, rowIdentifier, KEY_ERROR, body != null ? body.get(KEY_MESSAGE) : "Tenant validation error"));
                return false;
            }

            // 1. Mandatory Fields Validation
            Optional<ResponseEntity<Object>> valOpt = validateTenantUser(user, false);
            if (valOpt.isPresent()) {
                Map<String, String> body = (Map<String, String>) valOpt.get().getBody();
                failures.add(Map.of(KEY_IDENTIFIER, rowIdentifier, KEY_ERROR, body != null ? body.get(KEY_MESSAGE) : "Validation error"));
                return false;
            }

            // 2. Duplicate Checks
            String cleanEmpId = getCleanEmployeeId(user.getEmployeeId());
            String prefixedEmpId = user.getTenant().getCode() + "_" + cleanEmpId;

            Optional<ResponseEntity<Object>> dupOpt = checkDuplicateUser(user, cleanEmpId, prefixedEmpId);
            if (dupOpt.isPresent()) {
                Map<String, String> body = (Map<String, String>) dupOpt.get().getBody();
                failures.add(Map.of(KEY_IDENTIFIER, rowIdentifier, KEY_ERROR, body != null ? body.get(KEY_MESSAGE) : "Duplicate error"));
                return false;
            }

            user.setEmployeeId(prefixedEmpId);

            setTenantUserDetailsDefaults(user);

            // Generate and hash password
            String rawPassword = generateTempPassword();
            user.setPassword(passwordEncoder.encode(rawPassword));
            user.setMustChangePassword(true);

            TenantUser savedUser = tenantUserRepository.save(user);

            // Sync to HRMS employee_portal table IMMEDIATELY (synchronous)
            syncBulkUserToProducts(savedUser, rawPassword, rowIdentifier);

            // Send welcome email in background thread (non-blocking)
            sendBulkWelcomeEmail(savedUser, rawPassword, rowIdentifier);

            return true;

        } catch (Exception e) {
            failures.add(Map.of(KEY_IDENTIFIER, rowIdentifier, KEY_ERROR, "Unexpected error: " + e.getMessage()));
            return false;
        }
    }

    private void setTenantUserDetailsDefaults(TenantUser user) {
        if (user.getRole() == null || user.getRole().isEmpty()) {
            user.setRole(VAL_EMPLOYEE);
        }
        if (user.getStatus() == null || user.getStatus().isEmpty()) {
            user.setStatus(VAL_ACTIVE);
        }
        if (user.getIsSubAdmin() == null) {
            user.setIsSubAdmin(false);
        }
        if (user.getAssignedModules() == null) {
            user.setAssignedModules("");
        }
        if (user.getAccountLocked() == null) {
            user.setAccountLocked(false);
        }
        if (user.getFailedAttemptCount() == null) {
            user.setFailedAttemptCount(0);
        }
    }

    private void syncBulkUserToProducts(TenantUser savedUser, String rawPassword, String rowIdentifier) {
        try {
            syncToProducts(savedUser, rawPassword);
        } catch (Exception e) {
            logger.warn("[Scaloz] Warning: Sync to HRMS failed for bulk user {}: {}", rowIdentifier, e.getMessage(), e);
        }
    }

    private void sendBulkWelcomeEmail(TenantUser savedUser, String rawPassword, String rowIdentifier) {
        new Thread(() -> {
            try {
                sendWelcomeEmail(savedUser.getEmail(), savedUser.getFirstName() + " " + savedUser.getLastName(),
                        savedUser.getEmployeeId(), rawPassword);
                if (savedUser.getPersonalEmail() != null && !savedUser.getPersonalEmail().isBlank()
                        && !savedUser.getPersonalEmail().equals(savedUser.getEmail())) {
                    sendWelcomeEmail(savedUser.getPersonalEmail(),
                            savedUser.getFirstName() + " " + savedUser.getLastName(),
                            savedUser.getEmployeeId(), rawPassword);
                }
            } catch (Exception e) {
                logger.warn("[Scaloz] Warning: Welcome email failed for bulk user {}: {}", rowIdentifier, e.getMessage(), e);
            }
        }).start();
    }


    private TenantUser convertToEntity(TenantUserDto dto) {
        if (dto == null) {
            return null;
        }
        TenantUser entity = new TenantUser();
        entity.setId(dto.getId());
        entity.setEmployeeId(dto.getEmployeeId());
        entity.setEmail(dto.getEmail());
        entity.setPassword(dto.getPassword());
        entity.setRole(dto.getRole());
        entity.setAssignedProducts(dto.getAssignedProducts());
        entity.setAssignedModules(dto.getAssignedModules());
        entity.setIsSubAdmin(dto.getIsSubAdmin());
        entity.setStatus(dto.getStatus());
        entity.setFirstName(dto.getFirstName());
        entity.setLastName(dto.getLastName());
        entity.setWorkLocation(dto.getWorkLocation());
        entity.setPersonalEmail(dto.getPersonalEmail());
        entity.setGender(dto.getGender());
        entity.setDateOfBirth(dto.getDateOfBirth());
        entity.setAadharNo(dto.getAadharNo());
        entity.setPanNo(dto.getPanNo());
        entity.setPresentAddress(dto.getPresentAddress());
        entity.setPermanentAddress(dto.getPermanentAddress());
        entity.setContactNo(dto.getContactNo());
        entity.setEmergencyContactNo(dto.getEmergencyContactNo());
        entity.setBloodGroup(dto.getBloodGroup());
        entity.setJoiningDate(dto.getJoiningDate());
        if (dto.getCustomerCode() != null) {
            entity.setCustomerCode(dto.getCustomerCode());
        }
        if (dto.getTenant() != null) {
            Tenant t = new Tenant();
            t.setId(dto.getTenant().getId());
            t.setCode(dto.getTenant().getCode());
            t.setName(dto.getTenant().getName());
            entity.setTenant(t);
        }
        return entity;
    }

    private String extractCleanProductCode(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        String clean = raw.trim();
        if (clean.contains(":")) {
            clean = clean.split(":", 2)[0].trim();
        }
        return clean;
    }

    public static class TenantDto {
        private Long id;
        private String code;
        private String name;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class TenantUserDto {
        private Long id;
        private String employeeId;
        private String email;
        private String password;
        private String role;
        private String assignedProducts;
        private String assignedModules;
        private Boolean isSubAdmin;
        private String status;
        private String firstName;
        private String lastName;
        private String workLocation;
        private String personalEmail;
        private String gender;
        private String dateOfBirth;
        private String aadharNo;
        private String panNo;
        private String presentAddress;
        private String permanentAddress;
        private String contactNo;
        private String emergencyContactNo;
        private String bloodGroup;
        private String joiningDate;
        private TenantDto tenant;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getEmployeeId() {
            return employeeId;
        }

        public void setEmployeeId(String employeeId) {
            this.employeeId = employeeId;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getAssignedProducts() {
            return assignedProducts;
        }

        public void setAssignedProducts(String assignedProducts) {
            this.assignedProducts = assignedProducts;
        }

        public String getAssignedModules() {
            return assignedModules;
        }

        public void setAssignedModules(String assignedModules) {
            this.assignedModules = assignedModules;
        }

        public Boolean getIsSubAdmin() {
            return isSubAdmin;
        }

        public void setIsSubAdmin(Boolean isSubAdmin) {
            this.isSubAdmin = isSubAdmin;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }

        public String getWorkLocation() {
            return workLocation;
        }

        public void setWorkLocation(String workLocation) {
            this.workLocation = workLocation;
        }

        public String getPersonalEmail() {
            return personalEmail;
        }

        public void setPersonalEmail(String personalEmail) {
            this.personalEmail = personalEmail;
        }

        public String getGender() {
            return gender;
        }

        public void setGender(String gender) {
            this.gender = gender;
        }

        public String getDateOfBirth() {
            return dateOfBirth;
        }

        public void setDateOfBirth(String dateOfBirth) {
            this.dateOfBirth = dateOfBirth;
        }

        public String getAadharNo() {
            return aadharNo;
        }

        public void setAadharNo(String aadharNo) {
            this.aadharNo = aadharNo;
        }

        public String getPanNo() {
            return panNo;
        }

        public void setPanNo(String panNo) {
            this.panNo = panNo;
        }

        public String getPresentAddress() {
            return presentAddress;
        }

        public void setPresentAddress(String presentAddress) {
            this.presentAddress = presentAddress;
        }

        public String getPermanentAddress() {
            return permanentAddress;
        }

        public void setPermanentAddress(String permanentAddress) {
            this.permanentAddress = permanentAddress;
        }

        public String getContactNo() {
            return contactNo;
        }

        public void setContactNo(String contactNo) {
            this.contactNo = contactNo;
        }

        public String getEmergencyContactNo() {
            return emergencyContactNo;
        }

        public void setEmergencyContactNo(String emergencyContactNo) {
            this.emergencyContactNo = emergencyContactNo;
        }

        public String getBloodGroup() {
            return bloodGroup;
        }

        public void setBloodGroup(String bloodGroup) {
            this.bloodGroup = bloodGroup;
        }

        public String getJoiningDate() {
            return joiningDate;
        }

        public void setJoiningDate(String joiningDate) {
            this.joiningDate = joiningDate;
        }

        public TenantDto getTenant() {
            return tenant;
        }

        public void setTenant(TenantDto tenant) {
            this.tenant = tenant;
        }

        private String customerCode;

        public String getCustomerCode() {
            return customerCode;
        }

        public void setCustomerCode(String customerCode) {
            this.customerCode = customerCode;
        }
    }
}
