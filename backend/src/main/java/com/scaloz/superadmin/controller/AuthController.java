package com.scaloz.superadmin.controller;

import com.scaloz.superadmin.model.Tenant;
import com.scaloz.superadmin.model.TenantModule;
import com.scaloz.superadmin.model.TenantUser;
import com.scaloz.superadmin.model.PasswordResetToken;
import com.scaloz.superadmin.model.Product;
import com.scaloz.superadmin.model.ProductModule;
import com.scaloz.superadmin.model.SuperAdmin;
import com.scaloz.superadmin.repository.TenantRepository;
import com.scaloz.superadmin.repository.TenantModuleRepository;
import com.scaloz.superadmin.repository.SuperAdminRepository;
import com.scaloz.superadmin.security.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.scaloz.superadmin.service.DeviceSecurityService;
import com.scaloz.superadmin.service.CryptoMetadataService;
import com.scaloz.superadmin.repository.UserDeviceRepository;
import com.scaloz.superadmin.model.UserDevice;
import java.util.*;
import java.time.LocalDateTime;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import jakarta.mail.internet.MimeMessage;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    // ── String constants ───────────────────────────
    private static final String KEY_MESSAGE = "message";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_TENANT_CODE = "tenantCode";
    private static final String KEY_EMPLOYEE_ID = "employeeId";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_TENANT = "tenant";
    private static final String KEY_TENANT_ID = "tenantId";
    private static final String KEY_IS_SUB_ADMIN = "isSubAdmin";
    private static final String KEY_PRODUCT_ID = "productId";
    private static final String KEY_PRODUCT_NAME = "productName";
    private static final String KEY_PRODUCT_CODE = "productCode";
    private static final String KEY_CONTENT = "content";
    private static final String KEY_INACTIVE = "Inactive";
    private static final String ROLE_SUB_ADMIN = "Sub Admin";
    private static final String ROLE_SUPER_ADMIN = "ROLE_SUPER_ADMIN";
    private static final String PASS_PATTERN = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{8,}$";
    private static final String MSG_NO_ACCESS = "You don't have access for this. Please contact your administrator.";
    private static final String MSG_INVALID_CREDENTIALS = "Invalid username or password";
    private static final String MSG_ACCOUNT_LOCKED = "Your account has been locked due to 3 unsuccessful login attempts. You can reset your password to unlock it immediately.";
    private static final String MSG_PASSWORD_REUSED = "Password has been used recently. Please choose a password that does not match your current password or any of your last 5 passwords.";
    private static final String UTF_8 = "UTF-8";
    private static final String PREFIX_SUPER_ADMIN = "SUPER_ADMIN_";

    private final JwtUtils jwtUtils;
    private final com.scaloz.superadmin.security.TokenBlacklistService tokenBlacklistService;
    private final TenantRepository tenantRepository;
    private final TenantModuleRepository tenantModuleRepository;
    private final com.scaloz.superadmin.repository.ProductRepository productRepository;
    private final com.scaloz.superadmin.repository.TenantUserRepository tenantUserRepository;
    private final TenantUserController tenantUserController;
    private final JavaMailSender mailSender;
    private final com.scaloz.superadmin.repository.PasswordResetTokenRepository passwordResetTokenRepository;
    private final SuperAdminRepository superAdminRepository;
    private final DeviceSecurityService deviceSecurityService;
    private final UserDeviceRepository userDeviceRepository;
    private final com.scaloz.superadmin.repository.UserSessionRepository userSessionRepository;
    private final CryptoMetadataService cryptoService;

    @Autowired
    public AuthController(
            JwtUtils jwtUtils,
            com.scaloz.superadmin.security.TokenBlacklistService tokenBlacklistService,
            TenantRepository tenantRepository,
            TenantModuleRepository tenantModuleRepository,
            com.scaloz.superadmin.repository.ProductRepository productRepository,
            com.scaloz.superadmin.repository.TenantUserRepository tenantUserRepository,
            TenantUserController tenantUserController,
            @Autowired(required = false) JavaMailSender mailSender,
            com.scaloz.superadmin.repository.PasswordResetTokenRepository passwordResetTokenRepository,
            SuperAdminRepository superAdminRepository,
            DeviceSecurityService deviceSecurityService,
            UserDeviceRepository userDeviceRepository,
            com.scaloz.superadmin.repository.UserSessionRepository userSessionRepository,
            CryptoMetadataService cryptoService) {
        this.jwtUtils = jwtUtils;
        this.tokenBlacklistService = tokenBlacklistService;
        this.tenantRepository = tenantRepository;
        this.tenantModuleRepository = tenantModuleRepository;
        this.productRepository = productRepository;
        this.tenantUserRepository = tenantUserRepository;
        this.tenantUserController = tenantUserController;
        this.mailSender = mailSender;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.superAdminRepository = superAdminRepository;
        this.deviceSecurityService = deviceSecurityService;
        this.userDeviceRepository = userDeviceRepository;
        this.userSessionRepository = userSessionRepository;
        this.cryptoService = cryptoService;
    }

    @Value("${spring.mail.username:noreply@scaloz.com}")
    private String fromEmail;

    @Value("${scaloz.app.encryptionKey}")
    private String encryptionKey;

    private final org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder passwordEncoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AuthController.class);

    // ─────────────────────────────────────────────────────────────────
    // LOOKUP — safe public endpoint (no auth required, no passwords)
    // GET /api/auth/lookup?email=user@company.com
    // Returns: tenant name, domain, logo, products list
    // ─────────────────────────────────────────────────────────────────
    @GetMapping("/lookup")
    public ResponseEntity<Map<String, Object>> lookupTenant(
            @RequestParam(value = KEY_EMAIL, required = false) String email,
            @RequestParam(value = "code", required = false) String code) {

        if (code != null && !code.trim().isEmpty()) {
            return handleLookupByCode(code);
        }
        if (email != null && email.contains("@")) {
            return handleLookupByEmail(email);
        }
        return badRequest(Map.of(KEY_MESSAGE, "A valid email address or workspace code is required."));
    }

    private ResponseEntity<Map<String, Object>> handleLookupByCode(String code) {
        String searchCode = code.trim().toLowerCase();
        Optional<Tenant> tenantOpt = tenantRepository.findByCode(searchCode);

        if (!tenantOpt.isPresent()) {
            tenantOpt = findTenantBySlug(searchCode);
        }

        if (!tenantOpt.isPresent()) {
            return notFound(Map.of(KEY_MESSAGE, "No workspace found."));
        }

        Tenant tenant = tenantOpt.get();
        if (isTenantInactive(tenant)) {
            return ok(Map.of("inactive", true, KEY_MESSAGE, MSG_NO_ACCESS));
        }

        String domain = resolveDomainFromTenant(tenant);
        return ok(buildLookupResult(tenant, domain));
    }

    private ResponseEntity<Map<String, Object>> handleLookupByEmail(String email) {
        String domain = email.substring(email.indexOf("@") + 1).toLowerCase().trim();
        Optional<Tenant> tenantOpt = resolveTenantByEmailOrDomain(email);

        if (!tenantOpt.isPresent()) {
            return notFound(Map.of(KEY_MESSAGE, "No workspace found."));
        }

        Tenant tenant = tenantOpt.get();
        if (isTenantInactive(tenant)) {
            return ok(Map.of("inactive", true, KEY_MESSAGE, MSG_NO_ACCESS));
        }

        return ok(buildLookupResult(tenant, domain));
    }

    private Optional<Tenant> findTenantBySlug(String searchCode) {
        List<Tenant> allTenants = tenantRepository.findAll();
        for (Tenant t : allTenants) {
            if (t.getName() == null)
                continue;
            String slug = t.getName().toLowerCase()
                    .replaceAll("[^a-z0-9]", "-")
                    .replaceAll("-+", "-")
                    .replaceAll("^-", "")
                    .replaceAll("-$", "");
            if (slug.equals(searchCode))
                return Optional.of(t);
        }
        return Optional.empty();
    }

    private boolean isTenantInactive(Tenant tenant) {
        return tenant.getStatus() != null && tenant.getStatus().equalsIgnoreCase(KEY_INACTIVE);
    }

    private String resolveDomainFromTenant(Tenant tenant) {
        return tenant.getAdminEmail() != null && tenant.getAdminEmail().contains("@")
                ? tenant.getAdminEmail().substring(tenant.getAdminEmail().indexOf("@") + 1)
                : tenant.getCode() + ".com";
    }

    private Map<String, Object> buildLookupResult(Tenant tenant, String domain) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tenantName", tenant.getName());
        result.put("domain", domain);
        result.put("code", tenant.getCode());
        result.put("logo", tenant.getLogo());
        result.put("adminEmail", tenant.getAdminEmail());
        result.put("adminEmployeeId", tenant.getCode() + "_admin");
        result.put("website", tenant.getWebsite());
        result.put("products", new ArrayList<>(buildProductMap(tenant).values()));
        result.put("found", true);
        return result;
    }

    private Map<Long, Map<String, Object>> buildProductMap(Tenant tenant) {
        Map<Long, Map<String, Object>> productMap = new LinkedHashMap<>();
        String selProds = tenant.getSelectedProducts();
        if (selProds != null && !selProds.trim().isEmpty()) {
            populateProductMapFromSelected(productMap, selProds);
        } else {
            populateProductMapFromModules(productMap, tenant);
        }
        return productMap;
    }

    private void populateProductMapFromSelected(Map<Long, Map<String, Object>> productMap, String selProds) {
        for (String c : selProds.split(",")) {
            String trimmed = c.trim();
            String pCode = trimmed;
            String status = "Active";
            if (trimmed.contains(":")) {
                String[] parts = trimmed.split(":", 2);
                pCode = parts[0].trim();
                status = parts[1].trim();
            }
            if (!status.equalsIgnoreCase("Active"))
                continue;
            productRepository.findByCode(pCode).ifPresent(p -> {
                if (!productMap.containsKey(p.getId())) {
                    productMap.put(p.getId(), buildProductInfo(p));
                }
            });
        }
    }

    private void populateProductMapFromModules(Map<Long, Map<String, Object>> productMap, Tenant tenant) {
        List<TenantModule> tenantModules = tenantModuleRepository.findByTenantId(tenant.getId());
        for (TenantModule tm : tenantModules) {
            ProductModule pm = tm.getProductModule();
            if (pm == null)
                continue;
            Product p = pm.getProduct();
            if (p != null && !productMap.containsKey(p.getId())) {
                productMap.put(p.getId(), buildProductInfo(p));
            }
        }
    }

    private Map<String, Object> buildProductInfo(Product p) {
        Map<String, Object> pInfo = new LinkedHashMap<>();
        pInfo.put(KEY_PRODUCT_ID, p.getId());
        pInfo.put(KEY_PRODUCT_NAME, p.getName());
        pInfo.put(KEY_PRODUCT_CODE, p.getCode());
        pInfo.put("icon", p.getIcon());
        pInfo.put(KEY_CONTENT, p.getContent());
        return pInfo;
    }

    // ─────────────────────────────────────────────────────────────────
    // LOGIN — email + password (tenant auto-resolved from email)
    // POST /api/auth/login
    // ─────────────────────────────────────────────────────────────────
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> loginRequest,
            HttpServletRequest request, HttpServletResponse response) {
        // String clientIp = getClientIp(request);
        // if (checkIpRateLimit(clientIp)) {
        // return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        // .body(Map.of(KEY_MESSAGE, "Too many login attempts. Please try again
        // later."));
        // }

        if (loginRequest.containsKey(KEY_EMAIL)) {
            return handleTenantLogin(loginRequest, request, response);
        }
        return handleSuperAdminLogin(loginRequest, request, response);
    }

    private ResponseEntity<Map<String, Object>> handleTenantLogin(Map<String, String> req, 
            HttpServletRequest request, HttpServletResponse response) {
        String email = trimOrNull(req.get(KEY_EMAIL));
        String password = trimOrNull(req.get(KEY_PASSWORD));
        String tenantCode = req.get(KEY_TENANT_CODE);

        logger.info("[Scaloz Auth] Tenant login attempt for identifier='{}', tenantCode='{}'", email, tenantCode);

        Optional<TenantUser> userOpt = resolveUserByEmailOrEmpId(email, tenantCode);

        if (!userOpt.isPresent()) {
            logger.warn("[Scaloz Auth] User not found for identifier='{}', tenantCode='{}'", email, tenantCode);
            recordGlobalFailedAttempt();
            return unauthorized(Map.of(KEY_MESSAGE, "Invalid email/employee ID or password"));
        }

        TenantUser tu = userOpt.get();
        logger.info("[Scaloz Auth] Resolved user: id={}, email='{}', employeeId='{}', tenant='{}', customerCode='{}', status='{}', locked={}, mustChangePassword={}",
                tu.getId(), tu.getEmail(), tu.getEmployeeId(), tu.getTenant() != null ? tu.getTenant().getCode() : "null",
                tu.getCustomerCode(), tu.getStatus(), tu.getAccountLocked(), tu.getMustChangePassword());

        ResponseEntity<Map<String, Object>> blockResponse = checkUserAccessBlocked(tu);
        if (blockResponse != null)
            return blockResponse;

        return validatePasswordAndRespond(tu, password, request, response);
    }

    private Optional<TenantUser> resolveUserByEmailOrEmpId(String email, String tenantCode) {
        if (email == null || email.trim().isEmpty()) {
            return Optional.empty();
        }

        // First try finding user directly by email (auto-resolving tenant)
        Optional<TenantUser> userOpt = tenantUserRepository.findFirstByEmailIgnoreCaseOrderByIdDesc(email.trim());

        // If not found by email, search by employeeId and tenantCode
        if (!userOpt.isPresent()) {
            userOpt = findUserByEmpId(email, tenantCode);
        }
        return userOpt;
    }

    private Optional<TenantUser> findUserByEmpId(String email, String tenantCode) {
        String searchEmailOrEmpId = email;
        String searchTenantCode = tenantCode;

        if (email != null && email.contains("_")) {
            int idx = email.indexOf('_');
            if (tenantCode == null || tenantCode.trim().isEmpty()) {
                searchTenantCode = email.substring(0, idx);
            }
            searchEmailOrEmpId = email.substring(idx + 1);
        }

        final String finalEmpId = searchEmailOrEmpId;
        final String finalTenantCode = searchTenantCode;

        List<TenantUser> allUsers = tenantUserRepository.findAll();
        for (TenantUser tu : allUsers) {
            if (matchesEmpId(tu, email, finalEmpId) &&
                    matchesTenantCode(tu, finalTenantCode)) {
                return Optional.of(tu);
            }
        }
        return Optional.empty();
    }

    private boolean matchesEmpId(TenantUser tu, String email, String searchEmpId) {
        String checkId = tu.getEmployeeId();
        String cleanCheckId = checkId != null && checkId.contains("_")
                ? checkId.substring(checkId.indexOf("_") + 1)
                : checkId;
        return (checkId != null && (checkId.equalsIgnoreCase(email) || checkId.equalsIgnoreCase(searchEmpId)))
                || (cleanCheckId != null
                        && (cleanCheckId.equalsIgnoreCase(email) || cleanCheckId.equalsIgnoreCase(searchEmpId)));
    }

    private boolean matchesTenantCode(TenantUser tu, String tenantCode) {
        return tenantCode == null
                || Objects.equals(tu.getTenant().getCode(), tenantCode.toLowerCase().trim());
    }

    private ResponseEntity<Map<String, Object>> checkUserAccessBlocked(TenantUser tu) {
        if (isTenantInactive(tu.getTenant())) {
            logger.warn("[Scaloz Auth] Login blocked: Tenant '{}' is inactive for user '{}'", tu.getTenant() != null ? tu.getTenant().getCode() : "null", tu.getEmail());
            return badRequest(Map.of(KEY_MESSAGE, MSG_NO_ACCESS));
        }
        if (tu.getStatus() != null && tu.getStatus().equalsIgnoreCase(KEY_INACTIVE)) {
            logger.warn("[Scaloz Auth] Login blocked: User '{}' status is Inactive", tu.getEmail());
            return badRequest(Map.of(KEY_MESSAGE,
                    "Your account is inactive. Please contact your administrator."));
        }
        if (Boolean.TRUE.equals(tu.getAccountLocked())) {
            logger.warn("[Scaloz Auth] Login blocked: User '{}' account is locked (failed attempts: {})", tu.getEmail(), tu.getFailedAttemptCount());
            return unauthorized(Map.of(KEY_MESSAGE,
                    MSG_ACCOUNT_LOCKED));
        }
        return null;
    }

    private ResponseEntity<Map<String, Object>> validatePasswordAndRespond(TenantUser tu, String password,
            HttpServletRequest request, HttpServletResponse response) {
        if (passwordEncoder.matches(password, tu.getPassword()) || Objects.equals(tu.getPassword(), password)) {
            logger.info("[Scaloz Auth] Password verification SUCCESS for user='{}' in tenant='{}'",
                    tu.getEmail(), tu.getTenant() != null ? tu.getTenant().getCode() : "null");
            tu.setFailedAttemptCount(0);
            tu.setLastFailedLogin(null);
            tenantUserRepository.save(tu);

            boolean passwordExpired = tu.getPasswordChangedAt() != null
                    && tu.getPasswordChangedAt()
                            .isBefore(LocalDateTime.now(java.time.ZoneId.systemDefault()).minusDays(90));

            if (Boolean.TRUE.equals(tu.getMustChangePassword()) || passwordExpired) {
                if (passwordExpired && !Boolean.TRUE.equals(tu.getMustChangePassword())) {
                    tu.setMustChangePassword(true);
                    tenantUserRepository.save(tu);
                }
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("mustChangePassword", true);
                resp.put(KEY_EMPLOYEE_ID, tu.getEmployeeId());
                resp.put(KEY_EMAIL, tu.getEmail());
                resp.put(KEY_TENANT_CODE, tu.getTenant().getCode());
                return ok(resp);
            }

            ResponseEntity<Map<String, Object>> successResponse = buildLoginSuccessResponse(tu);
            Map<String, Object> successBody = successResponse.getBody();
            if (successBody != null && successBody.containsKey(KEY_TOKEN)) {
                String token = (String) successBody.get(KEY_TOKEN);
                String jwtId = UUID.randomUUID().toString();
                deviceSecurityService.evaluateDeviceAndHandleSession(tu.getId(), "TENANT_USER", tu.getEmail(), jwtId, request, response);
            }

            return successResponse;
        }
        recordGlobalFailedAttempt();
        return handleFailedLogin(tu);
    }

    private ResponseEntity<Map<String, Object>> handleFailedLogin(TenantUser tu) {
        int count = tu.getFailedAttemptCount() != null ? tu.getFailedAttemptCount() : 0;
        count++;
        tu.setFailedAttemptCount(count);
        tu.setLastFailedLogin(LocalDateTime.now(java.time.ZoneId.systemDefault()));

        logger.warn("[Scaloz Auth] Password verification FAILED for user='{}' in tenant='{}' (failed attempt count: {}/3)",
                tu.getEmail(), tu.getTenant() != null ? tu.getTenant().getCode() : "null", count);

        if (count >= 3) {
            tu.setAccountLocked(true);
            tenantUserRepository.save(tu);
            sendLockoutEmail(tu);
            logger.warn("[Scaloz Auth] User '{}' has been LOCKED due to 3 failed attempts", tu.getEmail());
            return unauthorized(Map.of(KEY_MESSAGE,
                    MSG_ACCOUNT_LOCKED));
        }
        tenantUserRepository.save(tu);
        return unauthorized(Map.of(KEY_MESSAGE, "Invalid email/employee ID or password"));
    }

    private ResponseEntity<Map<String, Object>> handleSuperAdminLogin(Map<String, String> req,
            HttpServletRequest request, HttpServletResponse response) {
        String username = trimOrNull(req.get(KEY_USERNAME));
        String password = trimOrNull(req.get(KEY_PASSWORD));
        logger.debug("Super Admin Login Attempt - Username: {}, Password: {}", username, password);

        if (username == null || password == null) {
            recordGlobalFailedAttempt();
            return unauthorized(Map.of(KEY_MESSAGE, MSG_INVALID_CREDENTIALS));
        }

        Optional<SuperAdmin> adminOpt = superAdminRepository.findByUsername(username);
        if (!adminOpt.isPresent()) {
            adminOpt = superAdminRepository.findByEmailIgnoreCase(username);
        }

        if (!adminOpt.isPresent()) {
            recordGlobalFailedAttempt();
            return unauthorized(Map.of(KEY_MESSAGE, MSG_INVALID_CREDENTIALS));
        }

        SuperAdmin admin = adminOpt.get();
        if (Boolean.TRUE.equals(admin.getAccountLocked())) {
            return unauthorized(Map.of(KEY_MESSAGE, MSG_ACCOUNT_LOCKED));
        }

        if (passwordEncoder.matches(password, admin.getPassword())) {
            return handleSuccessfulSuperAdminLogin(admin, request, response);
        }

        return handleFailedSuperAdminLogin(admin);
    }

    private ResponseEntity<Map<String, Object>> handleSuccessfulSuperAdminLogin(SuperAdmin admin,
            HttpServletRequest request, HttpServletResponse response) {
        admin.setFailedAttemptCount(0);
        admin.setLastFailedLogin(null);
        superAdminRepository.save(admin);

        boolean passwordExpired = admin.getPasswordChangedAt() != null
                && admin.getPasswordChangedAt()
                        .isBefore(LocalDateTime.now(java.time.ZoneId.systemDefault()).minusDays(90));

        if (passwordExpired) {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("mustChangePassword", true);
            resp.put(KEY_EMPLOYEE_ID, admin.getUsername());
            resp.put(KEY_EMAIL, admin.getEmail());
            resp.put("role", ROLE_SUPER_ADMIN);
            return ok(resp);
        }

        String token = jwtUtils.generateToken(admin.getUsername(), Map.of("role", ROLE_SUPER_ADMIN));
        String jwtId = UUID.randomUUID().toString();
        deviceSecurityService.evaluateDeviceAndHandleSession(admin.getId(), "SUPER_ADMIN", admin.getEmail(), jwtId, request, response);

        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put(KEY_TOKEN, token);
        responseMap.put(KEY_USERNAME, admin.getUsername());
        responseMap.put(KEY_EMAIL, admin.getEmail());
        responseMap.put("role", ROLE_SUPER_ADMIN);
        return ok(responseMap);
    }

    private ResponseEntity<Map<String, Object>> handleFailedSuperAdminLogin(SuperAdmin admin) {
        int count = admin.getFailedAttemptCount() != null ? admin.getFailedAttemptCount() : 0;
        count++;
        admin.setFailedAttemptCount(count);
        admin.setLastFailedLogin(LocalDateTime.now(java.time.ZoneId.systemDefault()));

        if (count >= 3) {
            admin.setAccountLocked(true);
            superAdminRepository.save(admin);
            sendSuperAdminLockoutEmail(admin);
            recordGlobalFailedAttempt();
            return unauthorized(Map.of(KEY_MESSAGE, MSG_ACCOUNT_LOCKED));
        }

        superAdminRepository.save(admin);
        recordGlobalFailedAttempt();
        return unauthorized(Map.of(KEY_MESSAGE, MSG_INVALID_CREDENTIALS));
    }

    // ─────────────────────────────────────────────────────────────────
    // /me — validate token and return session context (used by HRMS)
    // GET /api/auth/me
    // Header: Authorization: Bearer <token>
    // ─────────────────────────────────────────────────────────────────
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return unauthorized(Map.of(KEY_MESSAGE, "No token provided"));
        }

        String token = header.substring(7);
        if (!jwtUtils.validateToken(token)) {
            return unauthorized(Map.of(KEY_MESSAGE, "Token is expired or Invalid"));
        }

        return ok(buildMeResponse(token));
    }

    // ─────────────────────────────────────────────────────────────────
    // /logout — invalidate token immediately
    // POST /api/auth/logout
    // Header: Authorization: Bearer <token>
    // ─────────────────────────────────────────────────────────────────
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtUtils.validateToken(token)) {
                java.time.Instant expiryInstant = jwtUtils.getExpirationDateFromToken(token);
                if (expiryInstant != null) {
                    tokenBlacklistService.blacklistToken(token, expiryInstant.toEpochMilli());
                }
                // Revoke session from user_sessions
                String jwtId = sha256(token);
                userSessionRepository.findByJwtId(jwtId).ifPresent(session -> {
                    session.setRevoked(true);
                    userSessionRepository.save(session);
                });
            }
        }
        return ok(Map.of(KEY_MESSAGE, "Logged out successfully"));
    }

    private Map<String, Object> buildMeResponse(String token) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sub", jwtUtils.getUsernameFromToken(token));
        result.put(KEY_TENANT, jwtUtils.extractStringClaim(token, KEY_TENANT));
        result.put(KEY_TENANT_ID, jwtUtils.extractStringClaim(token, KEY_TENANT_ID));
        result.put("role", jwtUtils.extractStringClaim(token, "role"));
        result.put(KEY_EMPLOYEE_ID, jwtUtils.extractStringClaim(token, KEY_EMPLOYEE_ID));
        result.put("name", jwtUtils.extractStringClaim(token, "name"));
        result.put("apps", jwtUtils.extractListClaim(token, "apps"));
        Object isSubAdminObj = jwtUtils.extractClaim(token, KEY_IS_SUB_ADMIN);
        result.put(KEY_IS_SUB_ADMIN, isSubAdminObj instanceof Boolean b && b);
        String customerCode = jwtUtils.extractStringClaim(token, "customerCode");
        if (customerCode != null) {
            result.put("customerCode", customerCode);
        }
        result.put("authenticated", true);
        return result;
    }

    // ─────────────────────────────────────────────────────────────────
    // SHARED HELPER — resolve Tenant by email exact match then domain
    // ─────────────────────────────────────────────────────────────────
    private Optional<Tenant> resolveTenantByEmailOrDomain(String email) {
        Optional<TenantUser> tuOpt = tenantUserRepository.findByEmailIgnoreCase(email);
        if (tuOpt.isPresent())
            return Optional.of(tuOpt.get().getTenant());

        List<Tenant> byAdminEmail = tenantRepository.findByAdminEmail(email);
        if (!byAdminEmail.isEmpty())
            return Optional.of(byAdminEmail.get(0));

        return Optional.empty();
    }

    private ResponseEntity<Map<String, Object>> buildLoginSuccessResponse(TenantUser tu) {
        List<String> appCodes = resolveAppCodes(tu);
        Map<String, Object> claims = buildJwtClaims(tu, appCodes);
        String token = jwtUtils.generateToken(tu.getEmail(), claims);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put(KEY_TOKEN, token);
        response.put("user", buildUserMap(tu));
        response.put(KEY_TENANT, buildTenantMap(tu));
        response.put("products", buildUserProducts(tu));
        return ok(response);
    }

    private List<String> resolveAppCodes(TenantUser tu) {
        List<String> appCodes = new ArrayList<>();
        if (tu == null || tu.getTenant() == null) {
            return appCodes;
        }
        String tenantSelectedProds = tu.getTenant().getSelectedProducts();
        if (tenantSelectedProds == null || tenantSelectedProds.trim().isEmpty()) {
            return appCodes;
        }

        String assignedProds = tu.getAssignedProducts();
        if ((assignedProds == null || assignedProds.trim().isEmpty()) && isAdminRole(tu)) {
            assignedProds = tenantSelectedProds;
        }
        // Fallback for customer users: if customerCode is set and assignedProds is empty
        if ((assignedProds == null || assignedProds.trim().isEmpty()) && tu.getCustomerCode() != null) {
            List<String> activeTenantCodes = getActiveProductCodesForTenant(tenantSelectedProds);
            if (activeTenantCodes.contains("itsm")) {
                assignedProds = "itsm";
            } else if (!activeTenantCodes.isEmpty()) {
                assignedProds = activeTenantCodes.get(0);
            }
        }

        if (assignedProds == null || assignedProds.trim().isEmpty()) {
            return appCodes;
        }

        for (String pIdStr : assignedProds.split(",")) {
            try {
                String code = extractCode(pIdStr);
                if (code == null || code.trim().isEmpty()) continue;

                Optional<Product> pOpt = findProductCaseInsensitive(code);
                if (pOpt.isPresent()) {
                    Product p = pOpt.get();
                    if (p.getCode() != null && TenantController.isProductActive(tenantSelectedProds, p.getCode())) {
                        if (!appCodes.contains(p.getCode())) {
                            appCodes.add(p.getCode());
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return appCodes;
    }

    private Map<String, Object> buildJwtClaims(TenantUser tu, List<String> appCodes) {
        String ssoRole = Boolean.TRUE.equals(tu.getIsSubAdmin()) ? ROLE_SUB_ADMIN : tu.getRole();
        String empId = qualifyEmployeeId(tu);

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put(KEY_TENANT, tu.getTenant().getCode());
        claims.put(KEY_TENANT_ID, tu.getTenant().getCode());
        claims.put("tenantName", tu.getTenant().getName());
        claims.put("role", ssoRole != null ? ssoRole : "USER");
        claims.put(KEY_IS_SUB_ADMIN, tu.getIsSubAdmin());
        claims.put("apps", appCodes);
        claims.put(KEY_EMPLOYEE_ID, empId);
        claims.put("name", tu.getFirstName() + " " + tu.getLastName());
        claims.put("firstName", tu.getFirstName());
        claims.put("lastName", tu.getLastName());
        claims.put("workLocation", tu.getWorkLocation());
        claims.put("personalEmail", tu.getPersonalEmail());
        claims.put("gender", tu.getGender());
        claims.put("dateOfBirth", tu.getDateOfBirth());
        claims.put("aadharNo", tu.getAadharNo());
        claims.put("panNo", tu.getPanNo());
        claims.put("presentAddress", tu.getPresentAddress());
        claims.put("permanentAddress", tu.getPermanentAddress());
        claims.put("contactNo", tu.getContactNo());
        claims.put("bloodGroup", tu.getBloodGroup());
        claims.put("joiningDate", tu.getJoiningDate());
        if (tu.getCustomerCode() != null) {
            claims.put("customerCode", tu.getCustomerCode());
        }
        return claims;
    }

    private Map<String, Object> buildUserMap(TenantUser tu) {
        Map<String, Object> userMap = new LinkedHashMap<>();
        userMap.put("id", tu.getId());
        userMap.put("name", tu.getFirstName() + " " + tu.getLastName());
        userMap.put("role", tu.getRole());
        userMap.put(KEY_IS_SUB_ADMIN, tu.getIsSubAdmin());
        userMap.put(KEY_EMPLOYEE_ID, tu.getEmployeeId());
        if (tu.getCustomerCode() != null) {
            userMap.put("customerCode", tu.getCustomerCode());
        }
        return userMap;
    }

    private Map<String, Object> buildTenantMap(TenantUser tu) {
        Map<String, Object> tenantMap = new LinkedHashMap<>();
        tenantMap.put("id", tu.getTenant().getId());
        tenantMap.put("name", tu.getTenant().getName());
        tenantMap.put("code", tu.getTenant().getCode());
        return tenantMap;
    }

    private List<Map<String, Object>> buildUserProducts(TenantUser tu) {
        List<Map<String, Object>> userProducts = new ArrayList<>();
        if (tu == null || tu.getTenant() == null) {
            logger.warn("[Scaloz Auth] Cannot build user products: user or tenant is null");
            return userProducts;
        }

        String tenantSelectedProds = tu.getTenant().getSelectedProducts();
        logger.info("[Scaloz Auth] Resolving applications for user='{}', role='{}', MSP tenant='{}', customerCode='{}', tenantSelectedProds='{}', userAssignedProds='{}'",
                tu.getEmail(), tu.getRole(), tu.getTenant().getCode(), tu.getCustomerCode(), tenantSelectedProds, tu.getAssignedProducts());

        if (tenantSelectedProds == null || tenantSelectedProds.trim().isEmpty()) {
            logger.warn("[Scaloz Auth] MSP tenant '{}' has no active selected products", tu.getTenant().getCode());
            return userProducts;
        }

        String assignedProds = tu.getAssignedProducts();
        if ((assignedProds == null || assignedProds.trim().isEmpty()) && isAdminRole(tu)) {
            assignedProds = tenantSelectedProds;
        }
        // Fallback for customer users: if customerCode is set and assignedProds is empty
        if ((assignedProds == null || assignedProds.trim().isEmpty()) && tu.getCustomerCode() != null) {
            List<String> activeTenantCodes = getActiveProductCodesForTenant(tenantSelectedProds);
            if (activeTenantCodes.contains("itsm")) {
                assignedProds = "itsm";
            } else if (!activeTenantCodes.isEmpty()) {
                assignedProds = activeTenantCodes.get(0);
            }
        }

        if (assignedProds == null || assignedProds.trim().isEmpty()) {
            logger.warn("[Scaloz Auth] User '{}' has no assigned products and no fallback matched for tenant '{}'", tu.getEmail(), tu.getTenant().getCode());
            return userProducts;
        }

        Set<String> seenProductCodes = new HashSet<>();
        for (String pIdStr : assignedProds.split(",")) {
            try {
                String code = extractCode(pIdStr);
                if (code == null || code.trim().isEmpty()) continue;

                Optional<Product> pOpt = findProductCaseInsensitive(code);
                if (pOpt.isPresent()) {
                    Product p = pOpt.get();
                    if (p.getCode() != null && !seenProductCodes.contains(p.getCode().toLowerCase())) {
                        if (TenantController.isProductActive(tenantSelectedProds, p.getCode())
                                && (p.getStatus() == null || "Active".equalsIgnoreCase(p.getStatus()))) {
                            seenProductCodes.add(p.getCode().toLowerCase());
                            userProducts.add(buildUserProductInfo(p, tu));
                        } else {
                            logger.info("[Scaloz Auth] Product '{}' is not active for MSP tenant '{}' or product status is not Active",
                                    p.getCode(), tu.getTenant().getCode());
                        }
                    }
                } else {
                    logger.warn("[Scaloz Auth] Product with code/identifier '{}' not found in product repository", code);
                }
            } catch (Exception e) {
                logger.warn("[Scaloz Auth] Error resolving product '{}' for user '{}': {}", pIdStr, tu.getEmail(), e.getMessage());
            }
        }

        logger.info("[Scaloz Auth] Final applications returned for user='{}' [MSP tenant='{}', customer='{}']: count={}, products={}",
                tu.getEmail(), tu.getTenant().getCode(), tu.getCustomerCode(), userProducts.size(),
                userProducts.stream().map(m -> m.get(KEY_PRODUCT_CODE)).toList());

        return userProducts;
    }

    private Optional<Product> findProductCaseInsensitive(String code) {
        if (code == null || code.trim().isEmpty()) {
            return Optional.empty();
        }
        String clean = code.trim();
        Optional<Product> pOpt = productRepository.findByCode(clean);
        if (pOpt.isPresent()) return pOpt;
        pOpt = productRepository.findByCode(clean.toUpperCase());
        if (pOpt.isPresent()) return pOpt;
        pOpt = productRepository.findByCode(clean.toLowerCase());
        if (pOpt.isPresent()) return pOpt;
        return productRepository.findByNameIgnoreCase(clean);
    }

    private List<String> getActiveProductCodesForTenant(String selectedProducts) {
        List<String> activeCodes = new ArrayList<>();
        if (selectedProducts == null || selectedProducts.trim().isEmpty()) {
            return activeCodes;
        }
        for (String p : selectedProducts.split(",")) {
            String clean = extractCode(p);
            if (clean != null && !clean.isBlank() && TenantController.isProductActive(selectedProducts, clean)) {
                if (!activeCodes.contains(clean)) {
                    activeCodes.add(clean);
                }
            }
        }
        return activeCodes;
    }

    private Map<String, Object> buildUserProductInfo(Product p, TenantUser tu) {
        Map<String, Object> pInfo = new LinkedHashMap<>();
        pInfo.put(KEY_PRODUCT_ID, p.getId());
        pInfo.put(KEY_PRODUCT_NAME, p.getName());
        pInfo.put(KEY_PRODUCT_CODE, p.getCode());
        pInfo.put("url", p.getUrl());
        pInfo.put("icon", p.getIcon());
        pInfo.put(KEY_CONTENT, p.getContent());
        pInfo.put("modules", resolveUserModules(tu));
        return pInfo;
    }

    private List<String> resolveUserModules(TenantUser tu) {
        List<String> modules = new ArrayList<>();
        String assignedMods = tu.getAssignedModules();
        if (assignedMods != null && !assignedMods.isEmpty()) {
            for (String m : assignedMods.split(",")) {
                modules.add(m.trim());
            }
        }
        return modules;
    }

    private boolean isAdminRole(TenantUser tu) {
        if (Boolean.TRUE.equals(tu.getIsSubAdmin()))
            return true;
        String role = tu.getRole();
        return role != null && (role.equalsIgnoreCase("Admin")
                || role.equalsIgnoreCase(ROLE_SUB_ADMIN)
                || role.equalsIgnoreCase("Sub_Admin"));
    }

    private String qualifyEmployeeId(TenantUser tu) {
        String empId = tu.getEmployeeId();
        String prefix = tu.getTenant().getCode() + "_";
        if (empId != null && !empId.startsWith(prefix)) {
            empId = prefix + empId;
        }
        return empId;
    }

    private String extractCode(String raw) {
        String clean = raw.trim();
        return clean.contains(":") ? clean.split(":", 2)[0].trim() : clean;
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(@RequestBody Map<String, String> request) {
        String emailOrEmpId = request.get(KEY_EMPLOYEE_ID);
        String tempPassword = request.get("tempPassword");
        String newPassword = request.get("newPassword");
        String tenantCode = request.get(KEY_TENANT_CODE);

        if (emailOrEmpId == null || tempPassword == null || newPassword == null) {
            return badRequest(Map.of(KEY_MESSAGE, "employeeId, tempPassword, and newPassword are required."));
        }

        Optional<TenantUser> userOpt = findUserForPasswordChange(emailOrEmpId, tenantCode);
        if (!userOpt.isPresent()) {
            return notFound(Map.of(KEY_MESSAGE, "User not found."));
        }

        TenantUser user = userOpt.get();
        if (!passwordEncoder.matches(tempPassword, user.getPassword())
                && !Objects.equals(user.getPassword(), tempPassword)) {
            return unauthorized(Map.of(KEY_MESSAGE, "Temporary password does not match current password."));
        }

        if (!newPassword.matches(PASS_PATTERN)) {
            return badRequest(Map.of(KEY_MESSAGE,
                    "Password does not meet security requirements. Password must be at least 8 characters and include uppercase, lowercase, numbers, and special characters."));
        }

        if (isCommonPassword(newPassword)) {
            return badRequest(
                    Map.of(KEY_MESSAGE, "Password is too weak or commonly used. Please choose a stronger password."));
        }

        if (isPasswordReused(newPassword, user.getPassword(), user.getPasswordHistory())) {
            return badRequest(Map.of(KEY_MESSAGE,
                    MSG_PASSWORD_REUSED));
        }

        String currentHash = user.getPassword();
        String updatedHistory = getUpdatedPasswordHistory(currentHash, user.getPasswordHistory());
        user.setPasswordHistory(updatedHistory);
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(LocalDateTime.now(java.time.ZoneId.systemDefault()));
        user.setMustChangePassword(false);
        tenantUserRepository.save(user);

        try {
            tenantUserController.syncToProducts(user, newPassword);
        } catch (Exception e) {
            logger.warn("[Scaloz] Could not sync updated password to products: {}", e.getMessage());
        }

        sendPasswordChangedEmail(user.getEmail(), user.getFirstName() + " " + user.getLastName());

        return buildLoginSuccessResponse(user);
    }

    private Optional<TenantUser> findUserForPasswordChange(String emailOrEmpId, String tenantCode) {
        String searchEmailOrEmpId = emailOrEmpId;
        String searchTenantCode = tenantCode;
        if (emailOrEmpId.contains("_")) {
            int idx = emailOrEmpId.indexOf('_');
            searchTenantCode = emailOrEmpId.substring(0, idx);
            searchEmailOrEmpId = emailOrEmpId.substring(idx + 1);
        }

        Optional<TenantUser> byEmail = tenantUserRepository.findByEmailIgnoreCase(emailOrEmpId.trim());
        if (byEmail.isPresent())
            return byEmail;

        Optional<TenantUser> byTenant = findByTenantCode(emailOrEmpId, searchEmailOrEmpId, searchTenantCode);
        if (byTenant.isPresent())
            return byTenant;

        return findInAllUsersByEmpId(emailOrEmpId, searchEmailOrEmpId);
    }

    private Optional<TenantUser> findByTenantCode(String empId, String searchEmpId, String tenantCode) {
        if (tenantCode == null || tenantCode.trim().isEmpty())
            return Optional.empty();
        Optional<Tenant> tenantOpt = tenantRepository.findByCode(tenantCode.trim().toLowerCase());
        if (!tenantOpt.isPresent())
            return Optional.empty();

        Long tenantId = tenantOpt.get().getId();
        Optional<TenantUser> result = tenantUserRepository.findByEmployeeIdAndTenantId(empId.trim(), tenantId);
        if (!result.isPresent()) {
            result = tenantUserRepository.findByEmployeeIdAndTenantId(searchEmpId.trim(), tenantId);
        }
        return result;
    }

    private Optional<TenantUser> findInAllUsersByEmpId(String empId, String searchEmpId) {
        List<TenantUser> allUsers = tenantUserRepository.findAll();
        for (TenantUser tu : allUsers) {
            if (Objects.equals(tu.getEmployeeId(), empId.trim())
                    || Objects.equals(tu.getEmployeeId(), searchEmpId.trim())) {
                return Optional.of(tu);
            }
        }
        return Optional.empty();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, Object>> forgotPassword(
            @RequestBody Map<String, String> request, HttpServletRequest httpRequest) {

        String input = request.get(KEY_EMPLOYEE_ID);
        String tenantCode = request.get(KEY_TENANT_CODE);

        Map<String, Object> genericResponse = Map.of(KEY_MESSAGE,
                "If an account exists for this Employee ID, a password reset link has been sent.");

        if (input == null || input.trim().isEmpty())
            return ok(genericResponse);

        if (checkAndRecordRateLimit(input)) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of(KEY_MESSAGE, "Too many password reset requests. Please try again after an hour."));
        }

        String portal = request.get("portal");

        if ("superadmin".equalsIgnoreCase(portal)) {
            // Check if Super Admin only
            Optional<SuperAdmin> adminOpt = superAdminRepository.findByUsername(input.trim());
            if (!adminOpt.isPresent()) {
                adminOpt = superAdminRepository.findByEmailIgnoreCase(input.trim());
            }

            if (adminOpt.isPresent()) {
                SuperAdmin admin = adminOpt.get();
                String token = saveResetTokenForSuperAdmin(admin);
                String resetLink = buildResetLink(httpRequest, token);
                sendPasswordResetEmailForSuperAdmin(admin, resetLink);
            }
            return ok(genericResponse);
        }

        if (KEY_TENANT.equalsIgnoreCase(portal)) {
            // Check if Tenant User only
            Optional<TenantUser> userOpt = findUserForForgotPassword(input, tenantCode);
            if (userOpt.isPresent()) {
                TenantUser user = userOpt.get();
                if (user.getStatus() != null && user.getStatus().equalsIgnoreCase("Inactive")) {
                    return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_REQUEST)
                            .body(Map.of(KEY_MESSAGE, "Your account is inactive. Please contact your administrator."));
                }
                String token = saveResetToken(user);
                String resetLink = buildResetLink(httpRequest, token);
                sendPasswordResetEmail(user, resetLink);
            }
            return ok(genericResponse);
        }

        // Fallback/Legacy logic (checks super admin first, then tenant user)
        Optional<SuperAdmin> adminOpt = superAdminRepository.findByUsername(input.trim());
        if (!adminOpt.isPresent()) {
            adminOpt = superAdminRepository.findByEmailIgnoreCase(input.trim());
        }

        if (adminOpt.isPresent()) {
            SuperAdmin admin = adminOpt.get();
            String token = saveResetTokenForSuperAdmin(admin);
            String resetLink = buildResetLink(httpRequest, token);
            sendPasswordResetEmailForSuperAdmin(admin, resetLink);
        } else {
            Optional<TenantUser> userOpt = findUserForForgotPassword(input, tenantCode);
            if (userOpt.isPresent()) {
                TenantUser user = userOpt.get();
                if (user.getStatus() != null && user.getStatus().equalsIgnoreCase("Inactive")) {
                    return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_REQUEST)
                            .body(Map.of(KEY_MESSAGE, "Your account is inactive. Please contact your administrator."));
                }
                String token = saveResetToken(user);
                String resetLink = buildResetLink(httpRequest, token);
                sendPasswordResetEmail(user, resetLink);
            }
        }
        return ok(genericResponse);
    }

    private Optional<TenantUser> findUserForForgotPassword(String input, String tenantCode) {
        String searchEmailOrEmpId = input.trim();
        String searchTenantCode = tenantCode;
        if (input.contains("_")) {
            int idx = input.indexOf('_');
            searchTenantCode = input.substring(0, idx);
            searchEmailOrEmpId = input.substring(idx + 1);
        }

        Optional<TenantUser> byEmail = tenantUserRepository.findByEmailIgnoreCase(input.trim());
        if (byEmail.isPresent())
            return byEmail;

        Optional<TenantUser> byEmpId = tenantUserRepository.findByEmployeeId(input.trim());
        if (byEmpId.isPresent())
            return byEmpId;

        return scanAllUsersForForgotPassword(input, searchEmailOrEmpId, searchTenantCode);
    }

    private Optional<TenantUser> scanAllUsersForForgotPassword(
            String input, String searchEmpId, String searchTenantCode) {
        List<TenantUser> allUsers = tenantUserRepository.findAll();
        for (TenantUser tu : allUsers) {
            String checkId = tu.getEmployeeId();
            String cleanCheckId = checkId != null && checkId.contains("_")
                    ? checkId.substring(checkId.indexOf("_") + 1)
                    : checkId;
            boolean empIdMatch = Objects.equals(checkId, input.trim())
                    || Objects.equals(cleanCheckId, input.trim())
                    || Objects.equals(cleanCheckId, searchEmpId);
            boolean tenantMatch = searchTenantCode == null
                    || Objects.equals(tu.getTenant().getCode(), searchTenantCode.toLowerCase());
            if (empIdMatch && tenantMatch)
                return Optional.of(tu);
        }
        return Optional.empty();
    }

    private String saveResetToken(TenantUser user) {
        String token = java.util.UUID.randomUUID().toString();
        PasswordResetToken resetToken = new PasswordResetToken(
                user.getEmployeeId(), token, LocalDateTime.now(java.time.ZoneId.systemDefault()).plusMinutes(15));
        passwordResetTokenRepository.save(resetToken);
        return token;
    }

    private String buildResetLink(HttpServletRequest httpRequest, String token) {
        String origin = httpRequest.getHeader("Origin");
        if (origin == null || origin.isEmpty()) {
            origin = httpRequest.getHeader("Referer");
        }
        if (origin == null || origin.isEmpty()) {
            String scheme = httpRequest.getScheme();
            String serverName = httpRequest.getServerName();
            int serverPort = httpRequest.getServerPort();
            StringBuilder sb = new StringBuilder();
            sb.append(scheme).append("://").append(serverName);
            if (("http".equals(scheme) && serverPort != 80) || ("https".equals(scheme) && serverPort != 443)) {
                sb.append(":").append(serverPort);
            }
            origin = sb.toString();
        } else {
            origin = extractOrigin(origin);
        }
        return origin + "/reset-password?token=" + token;
    }

    private String extractOrigin(String origin) {
        try {
            java.net.URI uri = java.net.URI.create(origin);
            return uri.getScheme() + "://" + uri.getHost()
                    + (uri.getPort() != -1 ? ":" + uri.getPort() : "");
        } catch (Exception ignored) {
            return origin;
        }
    }

    private void sendPasswordResetEmail(TenantUser user, String resetLink) {
        new Thread(() -> {
            try {
                if (mailSender != null) {
                    MimeMessage message = mailSender.createMimeMessage();
                    MimeMessageHelper helper = new MimeMessageHelper(message, true, UTF_8);
                    helper.setFrom(fromEmail);
                    helper.setTo(user.getEmail());
                    helper.setSubject("Password Reset Request");
                    helper.setText(
                            buildResetEmailHtml(
                                    user.getFirstName() + " " + user.getLastName(),
                                    resetLink),
                            true);
                    mailSender.send(message);
                    logger.info("Sent HTML password reset link successfully to: {}", user.getEmail());
                } else {
                    logger.warn("mailSender is null. Reset link: {}", resetLink);
                }
            } catch (Exception e) {
                logger.error("Failed to send reset email to {}: {}", user.getEmail(), e.getMessage());
            }
        }).start();
    }

    private String saveResetTokenForSuperAdmin(SuperAdmin admin) {
        String token = java.util.UUID.randomUUID().toString();
        PasswordResetToken resetToken = new PasswordResetToken(
                PREFIX_SUPER_ADMIN + admin.getUsername(), token,
                LocalDateTime.now(java.time.ZoneId.systemDefault()).plusMinutes(15));
        passwordResetTokenRepository.save(resetToken);
        return token;
    }

    private void sendPasswordResetEmailForSuperAdmin(SuperAdmin admin, String resetLink) {
        new Thread(() -> {
            try {
                if (mailSender != null) {
                    MimeMessage message = mailSender.createMimeMessage();
                    MimeMessageHelper helper = new MimeMessageHelper(message, true, UTF_8);
                    helper.setFrom(fromEmail);
                    helper.setTo(admin.getEmail());
                    helper.setSubject("Super Admin Password Reset Request");
                    helper.setText(
                            buildResetEmailHtml(
                                    "Super Admin",
                                    resetLink),
                            true);
                    mailSender.send(message);
                    logger.info("Sent HTML password reset link successfully to Super Admin: {}", admin.getEmail());
                } else {
                    logger.warn("mailSender is null. Reset link: {}", resetLink);
                }
            } catch (Exception e) {
                logger.error("Failed to send reset email to Super Admin {}: {}", admin.getEmail(), e.getMessage());
            }
        }).start();
    }

    private String buildResetEmailHtml(String candidateName, String resetLink) {
        return "<div style='font-family:Arial,sans-serif;max-width:600px;margin:auto;padding:30px;border:1px solid #e5e7eb;border-radius:10px;background:#ffffff;'>"
                + "<div style='text-align:center;margin-bottom:20px;'>"
                + "<h1 style='color:#0f172a;margin:0;'>Scaloz</h1>"
                + "</div>"
                + "<hr style='border:none;border-top:1px solid #e5e7eb;'>"
                + "<h2 style='color:#0f172a;'>Password Reset Request</h2>"
                + "<p style='font-size:16px;color:#374151;'>"
                + "Hello <strong>" + candidateName + "</strong>,"
                + "</p>"
                + "<p style='font-size:15px;color:#4b5563;line-height:1.6;'>"
                + "We received a request to reset the password for your Scaloz account."
                + "</p>"
                + "<p style='font-size:15px;color:#4b5563;line-height:1.6;'>"
                + "To create a new password, click the button below:"
                + "</p>"
                + "<div style='text-align:center;margin:30px 0;'>"
                + "<a href='" + resetLink + "' "
                + "style='background:#0284c7;color:white;padding:14px 32px;"
                + "text-decoration:none;border-radius:6px;font-weight:600;'>"
                + "Reset Password"
                + "</a>"
                + "</div>"
                + "<p style='color:#dc2626;font-size:14px;'>"
                + "This link will expire in 15 minutes."
                + "</p>"
                + "<p style='font-size:14px;color:#6b7280;'>"
                + "If you did not request a password reset, please ignore this email. "
                + "Your account will remain secure."
                + "</p>"
                + "<hr style='border:none;border-top:1px solid #e5e7eb;margin-top:25px;'>"
                + "<p style='font-size:13px;color:#9ca3af;text-align:center;'>"
                + "Regards,<br>"
                + "<strong>Scaloz Security Team</strong>"
                + "</p>"
                + "</div>";
    }

    @PostMapping("/reset-password")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Map<String, Object>> resetPassword(@RequestBody Map<String, String> request) {
        String token = request.get(KEY_TOKEN);
        String newPassword = request.get("newPassword");

        if (token == null || token.isEmpty()) {
            return badRequest(Map.of(KEY_MESSAGE, "Reset token is required."));
        }

        Optional<PasswordResetToken> tokenOpt = passwordResetTokenRepository.findByToken(token);
        if (!tokenOpt.isPresent()) {
            return badRequest(Map.of(KEY_MESSAGE,
                    "Reset token is missing or invalid. Please check your email link again."));
        }

        PasswordResetToken resetToken = tokenOpt.get();
        ResponseEntity<Map<String, Object>> tokenError = validateResetToken(resetToken);
        if (tokenError != null)
            return tokenError;

        if (newPassword == null || !newPassword.matches(PASS_PATTERN)) {
            return badRequest(Map.of(KEY_MESSAGE,
                    "Password must be at least 8 characters, include uppercase, lowercase, number, and special character."));
        }

        if (isCommonPassword(newPassword)) {
            return badRequest(Map.of(KEY_MESSAGE,
                    "Password is too weak or commonly used. Please choose a stronger password."));
        }

        if (resetToken.getEmployeeId() != null && resetToken.getEmployeeId().startsWith(PREFIX_SUPER_ADMIN)) {
            String adminUsername = resetToken.getEmployeeId().substring(PREFIX_SUPER_ADMIN.length());
            Optional<SuperAdmin> adminOpt = superAdminRepository.findByUsername(adminUsername);
            if (!adminOpt.isPresent()) {
                return notFound(Map.of(KEY_MESSAGE, "Super Admin account not found."));
            }

            SuperAdmin admin = adminOpt.get();

            if (isPasswordReused(newPassword, admin.getPassword(), admin.getPasswordHistory())) {
                return badRequest(Map.of(KEY_MESSAGE,
                        MSG_PASSWORD_REUSED));
            }

            applySuperAdminPasswordReset(admin, newPassword);
            return ok(Map.of(KEY_MESSAGE, "Password reset successfully. You can now log in."));
        }

        Optional<TenantUser> userOpt = tenantUserRepository.findByEmployeeId(resetToken.getEmployeeId());
        if (!userOpt.isPresent()) {
            return notFound(Map.of(KEY_MESSAGE, "Employee account not found."));
        }

        TenantUser user = userOpt.get();

        if (isPasswordReused(newPassword, user.getPassword(), user.getPasswordHistory())) {
            return badRequest(Map.of(KEY_MESSAGE,
                    MSG_PASSWORD_REUSED));
        }

        applyPasswordReset(user, newPassword);
        return ok(Map.of(KEY_MESSAGE, "Password reset successfully. You can now log in."));
    }

    private void applySuperAdminPasswordReset(SuperAdmin admin, String newPassword) {
        String currentHash = admin.getPassword();
        String updatedHistory = getUpdatedPasswordHistory(currentHash, admin.getPasswordHistory());
        admin.setPasswordHistory(updatedHistory);
        admin.setPassword(passwordEncoder.encode(newPassword));
        admin.setPasswordChangedAt(LocalDateTime.now(java.time.ZoneId.systemDefault()));
        admin.setFailedAttemptCount(0);
        admin.setAccountLocked(false);
        admin.setLastFailedLogin(null);
        superAdminRepository.save(admin);

        List<PasswordResetToken> activeTokens = passwordResetTokenRepository
                .findByEmployeeIdAndUsedFalse(PREFIX_SUPER_ADMIN + admin.getUsername());
        for (PasswordResetToken t : activeTokens) {
            t.setUsed(true);
        }
        passwordResetTokenRepository.saveAll(activeTokens);

        logger.info("Password reset successful for Super Admin: {} (Email: {})",
                admin.getUsername(), admin.getEmail());

        sendPasswordChangedEmail(admin.getEmail(), admin.getUsername());
    }

    private ResponseEntity<Map<String, Object>> validateResetToken(PasswordResetToken resetToken) {
        if (Boolean.TRUE.equals(resetToken.getUsed())) {
            return badRequest(Map.of(KEY_MESSAGE,
                    "This reset token has already been used. Please request a new link."));
        }
        if (resetToken.getExpiryTime() == null
                || resetToken.getExpiryTime().isBefore(LocalDateTime.now(java.time.ZoneId.systemDefault()))) {
            return badRequest(Map.of(KEY_MESSAGE,
                    "This reset token has expired. Reset links are only valid for 15 minutes."));
        }
        return null;
    }

    private void applyPasswordReset(TenantUser user, String newPassword) {
        String currentHash = user.getPassword();
        String updatedHistory = getUpdatedPasswordHistory(currentHash, user.getPasswordHistory());
        user.setPasswordHistory(updatedHistory);
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(LocalDateTime.now(java.time.ZoneId.systemDefault()));
        user.setFailedAttemptCount(0);
        user.setAccountLocked(false);
        user.setLastFailedLogin(null);
        tenantUserRepository.save(user);

        List<PasswordResetToken> activeTokens = passwordResetTokenRepository
                .findByEmployeeIdAndUsedFalse(user.getEmployeeId());
        for (PasswordResetToken t : activeTokens) {
            t.setUsed(true);
        }
        passwordResetTokenRepository.saveAll(activeTokens);

        logger.info("Password reset successful for employee ID: {} (Email: {})",
                user.getEmployeeId(), user.getEmail());

        try {
            tenantUserController.syncToProducts(user, newPassword);
        } catch (Exception e) {
            logger.warn("Could not sync updated password to products: {}", e.getMessage());
        }

        sendPasswordChangedEmail(user.getEmail(), user.getFirstName() + " " + user.getLastName());
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, java.util.List<java.time.LocalDateTime>> rateLimitMap = new java.util.concurrent.ConcurrentHashMap<>();

    private boolean checkAndRecordRateLimit(String email) {
        String key = email.toLowerCase().trim();
        java.time.LocalDateTime now = java.time.LocalDateTime.now(java.time.ZoneId.systemDefault());

        java.util.List<java.time.LocalDateTime> timestamps = rateLimitMap.compute(key, (k, list) -> {
            if (list == null) {
                list = new java.util.ArrayList<>();
            } else {
                list.removeIf(t -> t.isBefore(now.minusHours(1)));
            }
            return list;
        });

        if (timestamps.size() >= 3) {
            return true; // rate limited
        }

        timestamps.add(now);
        return false; // not rate limited
    }

    // ── Security Helpers ──
    private static final java.util.concurrent.ConcurrentHashMap<String, java.util.List<java.time.LocalDateTime>> ipLoginAttempts = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String, java.time.LocalDateTime> blockedIps = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.List<java.time.LocalDateTime> globalFailedAttempts = java.util.Collections
            .synchronizedList(new java.util.ArrayList<>());

    private static final java.util.Set<String> COMMON_PASSWORDS = java.util.Set.of(
            KEY_PASSWORD, "password123", "12345678", "123456", "qwerty", "admin123", "welcome123", "scaloz123",
            "password@123", "welcome@123", "admin@123", "scaloz@123", "xevyte123", "xevyte@123");

    private boolean isCommonPassword(String password) {
        if (password == null)
            return true;
        String normalized = password.toLowerCase().trim();
        return COMMON_PASSWORDS.contains(normalized);
    }

    private boolean isPasswordReused(String newPassword, String currentPassword, String history) {
        if (passwordEncoder.matches(newPassword, currentPassword)) {
            return true;
        }
        if (history != null && !history.trim().isEmpty()) {
            for (String hash : history.split(";")) {
                if (!hash.trim().isEmpty() && passwordEncoder.matches(newPassword, hash.trim())) {
                    return true;
                }
            }
        }
        return false;
    }

    private String getUpdatedPasswordHistory(String currentPassword, String history) {
        java.util.List<String> list = new java.util.ArrayList<>();
        if (history != null && !history.trim().isEmpty()) {
            for (String h : history.split(";")) {
                if (!h.trim().isEmpty()) {
                    list.add(h.trim());
                }
            }
        }
        if (currentPassword != null && !currentPassword.trim().isEmpty()) {
            list.add(0, currentPassword);
        }
        if (list.size() > 5) {
            list = list.subList(0, 5);
        }
        return String.join(";", list);
    }

    private boolean checkIpRateLimit(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        java.time.LocalDateTime now = java.time.LocalDateTime.now(java.time.ZoneId.systemDefault());

        java.time.LocalDateTime blockExpiry = blockedIps.get(ip);
        if (blockExpiry != null) {
            if (blockExpiry.isAfter(now)) {
                return true;
            } else {
                blockedIps.remove(ip);
            }
        }

        java.util.List<java.time.LocalDateTime> attempts = ipLoginAttempts.compute(ip, (key, list) -> {
            if (list == null) {
                list = new java.util.ArrayList<>();
            } else {
                list.removeIf(t -> t.isBefore(now.minusMinutes(5)));
            }
            list.add(now);
            return list;
        });

        if (attempts.size() >= 20) {
            blockedIps.put(ip, now.plusMinutes(15));
            logger.warn("[SECURITY] IP {} blocked for 15 minutes due to too many login attempts.", ip);
            return true;
        }

        return false;
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }

    private void recordGlobalFailedAttempt() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now(java.time.ZoneId.systemDefault());
        globalFailedAttempts.add(now);
        globalFailedAttempts.removeIf(t -> t.isBefore(now.minusMinutes(5)));
        if (globalFailedAttempts.size() >= 10) {
            logger.error("[SECURITY ALERT] 10+ failed login attempts detected across accounts within 5 minutes!");
            // TEMPORARILY DISABLED: sendSecurityTeamAlert();
        }
    }

    private void sendSecurityTeamAlert() {
        new Thread(() -> {
            try {
                if (mailSender != null) {
                    MimeMessage message = mailSender.createMimeMessage();
                    MimeMessageHelper helper = new MimeMessageHelper(message, true, UTF_8);
                    helper.setFrom(fromEmail);
                    helper.setTo("security@xevyte.com");
                    helper.setSubject("CRITICAL SECURITY ALERT: Brute Force Attempt");
                    helper.setText(
                            "<h2>Brute Force Attempt Detected</h2>" +
                                    "<p>The security system has detected 10+ failed login attempts within 5 minutes across the application.</p>"
                                    +
                                    "<p>Timestamp: <strong>"
                                    + java.time.LocalDateTime.now(java.time.ZoneId.systemDefault()) + "</strong></p>" +
                                    "<p>Please check the application logs immediately.</p>",
                            true);
                    mailSender.send(message);
                    logger.info("Sent brute-force alert email to security team.");
                }
            } catch (Exception e) {
                logger.error("Failed to send brute force alert to security team: {}", e.getMessage());
            }
        }).start();
    }

    private void sendLockoutEmail(TenantUser user) {
        new Thread(() -> {
            try {
                if (mailSender != null) {
                    MimeMessage message = mailSender.createMimeMessage();
                    MimeMessageHelper helper = new MimeMessageHelper(message, true, UTF_8);
                    helper.setFrom(fromEmail);
                    helper.setTo(user.getEmail());
                    helper.setSubject("Security Alert: Your Scaloz Account is Locked");
                    helper.setText(
                            "<div style='font-family:Arial,sans-serif;max-width:600px;margin:auto;padding:30px;border:1px solid #e5e7eb;border-radius:10px;background:#ffffff;'>"
                                    + "<h2>Account Locked</h2>"
                                    + "<p>Dear " + user.getFirstName() + " " + user.getLastName() + ",</p>"
                                    + "<p>Your Scaloz account has been locked due to 3 consecutive failed login attempts.</p>"
                                    + "<p>You can reset your password to unlock it immediately.</p>"
                                    + "<p>Regards,<br><strong>Scaloz Security Team</strong></p>"
                                    + "</div>",
                            true);
                    mailSender.send(message);
                    logger.info("Sent lockout email successfully to: {}", user.getEmail());
                }
            } catch (Exception e) {
                logger.error("Failed to send lockout email to {}: {}", user.getEmail(), e.getMessage());
            }
        }).start();
    }

    private void sendSuperAdminLockoutEmail(SuperAdmin admin) {
        new Thread(() -> {
            try {
                if (mailSender != null) {
                    MimeMessage message = mailSender.createMimeMessage();
                    MimeMessageHelper helper = new MimeMessageHelper(message, true, UTF_8);
                    helper.setFrom(fromEmail);
                    helper.setTo(admin.getEmail());
                    helper.setSubject("Security Alert: Super Admin Account Locked");
                    helper.setText(
                            "<div style='font-family:Arial,sans-serif;max-width:600px;margin:auto;padding:30px;border:1px solid #e5e7eb;border-radius:10px;background:#ffffff;'>"
                                    + "<h2>Super Admin Account Locked</h2>"
                                    + "<p>Dear " + admin.getUsername() + ",</p>"
                                    + "<p>Your Super Admin account has been locked due to 3 consecutive failed login attempts.</p>"
                                    + "<p>You can reset your password to unlock it immediately.</p>"
                                    + "<p>Regards,<br><strong>Scaloz Security Team</strong></p>"
                                    + "</div>",
                            true);
                    mailSender.send(message);
                    logger.info("Sent Super Admin lockout email successfully to: {}", admin.getEmail());
                }
            } catch (Exception e) {
                logger.error("Failed to send Super Admin lockout email to {}: {}", admin.getEmail(), e.getMessage());
            }
        }).start();
    }

    private boolean isLocalIp(String ip) {
        if (ip == null)
            return false;
        String cleanIp = ip.trim();
        return "127.0.0.1".equals(cleanIp) || "0:0:0:0:0:0:0:1".equals(cleanIp) || "::1".equals(cleanIp)
                || "localhost".equalsIgnoreCase(cleanIp);
    }

    private boolean isIpSimilar(String ip1, String ip2) {
        if (ip1 == null || ip2 == null)
            return false;
        String ip1Clean = ip1.trim();
        String ip2Clean = ip2.trim();
        if (ip1Clean.isEmpty() || ip2Clean.isEmpty())
            return false;
        if (ip1Clean.equals(ip2Clean))
            return true;

        if (isLocalIp(ip1Clean) && isLocalIp(ip2Clean)) {
            return true;
        }

        // Check if both are IPv4 in the same /24 subnet
        if (ip1Clean.contains(".") && ip2Clean.contains(".")) {
            String[] parts1 = ip1Clean.split("\\.");
            String[] parts2 = ip2Clean.split("\\.");
            if (parts1.length >= 3 && parts2.length >= 3) {
                return parts1[0].equals(parts2[0]) && parts1[1].equals(parts2[1]) && parts1[2].equals(parts2[2]);
            }
        }
        return false;
    }

    private boolean isDeviceKnown(java.util.List<String> list, String ip, String userAgent) {
        for (String entry : list) {
            String[] parts = entry.split("\\|", 2);
            if (parts.length == 2) {
                String entryIp = parts[0].trim();
                String entryAgent = parts[1].trim();
                String cleanUserAgent = userAgent != null ? userAgent.trim() : "";
                if (entryAgent.equals(cleanUserAgent) && isIpSimilar(entryIp, ip)) {
                    return true;
                }
            }
        }
        return false;
    }

    private java.util.List<String> parseKnownLogins(String known) {
        java.util.List<String> list = new java.util.ArrayList<>();
        if (known != null && !known.trim().isEmpty()) {
            String[] entries = known.contains("|||") ? known.split("\\|\\|\\|") : known.split("\r?\n");
            for (String entry : entries) {
                if (entry != null && !entry.trim().isEmpty()) {
                    list.add(entry.trim());
                }
            }
        }
        return list;
    }

    private String updateKnownLogins(java.util.List<String> list, String loginId) {
        list.add(0, loginId);
        if (list.size() > 10) {
            list = list.subList(0, 10);
        }
        return String.join("|||", list);
    }

    private void checkAndAlertNewDevice(TenantUser user, String ip, String userAgent) {
        String cleanUserAgent = userAgent == null ? "Unknown" : userAgent.replace("\r", "").replace("\n", "");
        String loginId = ip + "|" + cleanUserAgent;
        java.util.List<String> list = parseKnownLogins(user.getKnownLogins());

        boolean known = isDeviceKnown(list, ip, cleanUserAgent);

        if (!list.isEmpty() && !known) {
            sendNewDeviceAlertEmail(user.getEmail(), user.getFirstName() + " " + user.getLastName(), ip,
                    cleanUserAgent);
        }

        if (!known) {
            user.setKnownLogins(updateKnownLogins(list, loginId));
            tenantUserRepository.save(user);
        }
    }

    private void checkAndAlertNewDeviceSuperAdmin(SuperAdmin admin, String ip, String userAgent) {
        String cleanUserAgent = userAgent == null ? "Unknown" : userAgent.replace("\r", "").replace("\n", "");
        String loginId = ip + "|" + cleanUserAgent;
        java.util.List<String> list = parseKnownLogins(admin.getKnownLogins());

        boolean known = isDeviceKnown(list, ip, cleanUserAgent);

        if (!list.isEmpty() && !known) {
            sendNewDeviceAlertEmail(admin.getEmail(), admin.getUsername(), ip, cleanUserAgent);
        }

        if (!known) {
            admin.setKnownLogins(updateKnownLogins(list, loginId));
            superAdminRepository.save(admin);
        }
    }

    private void sendNewDeviceAlertEmail(String email, String name, String ip, String userAgent) {
        new Thread(() -> {
            try {
                if (mailSender != null) {
                    MimeMessage message = mailSender.createMimeMessage();
                    MimeMessageHelper helper = new MimeMessageHelper(message, true, UTF_8);
                    helper.setFrom(fromEmail);
                    helper.setTo(email);
                    helper.setSubject("Security Alert: Login from New Device/Location");

                    String formattedDate = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                            .format(java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata"))) + " IST";

                    String htmlMsg = "<div style='font-family:\"Inter\",\"Helvetica Neue\",Helvetica,Arial,sans-serif;max-width:550px;margin:30px auto;padding:40px;border:1px solid #e2e8f0;border-radius:12px;background:#ffffff;box-shadow:0 4px 6px -1px rgba(0,0,0,0.05);'>"
                            + "<div style='text-align:center;margin-bottom:30px;'>"
                            + "  <span style='background:#fef2f2;color:#dc2626;padding:12px;border-radius:50%;display:inline-block;font-size:24px;width:32px;height:32px;line-height:32px;font-weight:bold;'>⚠️</span>"
                            + "</div>"
                            + "<h2 style='color:#1e293b;font-size:20px;font-weight:700;margin-top:0;margin-bottom:12px;text-align:center;'>New Device Login Detected</h2>"
                            + "<p style='color:#475569;font-size:14px;line-height:1.6;'>Dear <strong>" + name
                            + "</strong>,</p>"
                            + "<p style='color:#475569;font-size:14px;line-height:1.6;'>A new login to your Scaloz account was detected. Below are the login details:</p>"
                            + "<div style='background:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;padding:20px;margin:24px 0;'>"
                            + "  <table style='width:100%;font-size:13px;border-collapse:collapse;color:#334155;'>"
                            + "    <tr><td style='padding:6px 0;font-weight:600;width:120px;'>IP Address:</td><td style='padding:6px 0;color:#0f172a;'>"
                            + ip + "</td></tr>"
                            + "    <tr><td style='padding:6px 0;font-weight:600;'>Browser/Device:</td><td style='padding:6px 0;color:#0f172a;'>"
                            + userAgent + "</td></tr>"
                            + "    <tr><td style='padding:6px 0;font-weight:600;'>Date & Time:</td><td style='padding:6px 0;color:#0f172a;'>"
                            + formattedDate + "</td></tr>"
                            + "  </table>"
                            + "</div>"
                            + "<div style='background:#fffbeb;border-left:4px solid #f59e0b;padding:16px;border-radius:4px;margin-bottom:30px;'>"
                            + "  <p style='margin:0;font-size:13px;color:#b45309;line-height:1.6;'>"
                            + "    <strong>Important:</strong> If this was you, you can safely ignore this email. "
                            + "    If you do not recognize this activity, please reset your password immediately to secure your account."
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
                    logger.info("Sent new device alert email successfully to: {}", email);
                }
            } catch (Exception e) {
                logger.error("Failed to send new device alert email to {}: {}", email, e.getMessage());
            }
        }).start();
    }

    private void sendPasswordChangedEmail(String email, String name) {
        new Thread(() -> {
            try {
                if (mailSender != null) {
                    MimeMessage message = mailSender.createMimeMessage();
                    MimeMessageHelper helper = new MimeMessageHelper(message, true, UTF_8);
                    helper.setFrom(fromEmail);
                    helper.setTo(email);
                    helper.setSubject("Security Notification: Password Changed Successfully");
                    helper.setText(
                            "<div style='font-family:Arial,sans-serif;max-width:600px;margin:auto;padding:30px;border:1px solid #e5e7eb;border-radius:10px;background:#ffffff;'>"
                                    + "<h2>Password Changed Successfully</h2>"
                                    + "<p>Dear " + name + ",</p>"
                                    + "<p>Your password for your Scaloz account has been changed successfully.</p>"
                                    + "<p>If you did not make this change, please contact your administrator or reset your password immediately.</p>"
                                    + "<p>Regards,<br><strong>Scaloz Security Team</strong></p>"
                                    + "</div>",
                            true);
                    mailSender.send(message);
                    logger.info("Sent password changed confirmation email successfully to: {}", email);
                }
            } catch (Exception e) {
                logger.error("Failed to send password changed email to {}: {}", email, e.getMessage());
            }
        }).start();
    }
    @GetMapping(value = "/pwa/manifest.json", produces = "application/json")
    public ResponseEntity<Map<String, Object>> getDynamicManifest() {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("short_name", "Scaloz");
        manifest.put("name", "Scaloz");
        
        manifest.put("icons", Arrays.asList(
            Map.of("src", "logo192.png", "sizes", "192x192", "type", "image/png", "purpose", "any maskable"),
            Map.of("src", "logo512.png", "sizes", "512x512", "type", "image/png", "purpose", "any maskable")
        ));
        
        manifest.put("start_url", "/");
        manifest.put("scope", "/");
        manifest.put("display", "standalone");
        manifest.put("theme_color", "#1F2937");
        manifest.put("background_color", "#F5F7FA");
        
        List<Map<String, String>> extensions = new ArrayList<>();
        extensions.add(Map.of("origin", "https://*.apps.scaloz.com"));
        extensions.add(Map.of("origin", "https://apps.scaloz.com"));
        
        try {
            List<Product> products = productRepository.findAll();
            for (Product p : products) {
                String productUrl = p.getUrl();
                if (productUrl != null && !productUrl.trim().isEmpty()) {
                    try {
                        java.net.URI uri = new java.net.URI(productUrl.trim());
                        String host = uri.getHost();
                        if (host != null && !host.isEmpty()) {
                            if (host.equals("localhost") || host.equals("127.0.0.1")) {
                                extensions.add(Map.of("origin", "http://localhost"));
                                extensions.add(Map.of("origin", "http://*.localhost"));
                            } else {
                                extensions.add(Map.of("origin", "https://" + host));
                                extensions.add(Map.of("origin", "https://*." + host));
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("[Scaloz] Failed to parse product URL domain for manifest: {}", productUrl);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("[Scaloz] Failed to load products for dynamic manifest: {}", e.getMessage());
        }
        
        List<Map<String, String>> uniqueExtensions = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Map<String, String> ext : extensions) {
            String origin = ext.get("origin");
            if (seen.add(origin)) {
                uniqueExtensions.add(ext);
            }
        }
        
        manifest.put("scope_extensions", uniqueExtensions);
        return ok(manifest);
    }

    // ── Response helpers ──────────────────────────────────────────────
    private <T> ResponseEntity<T> ok(T body) {
        return ResponseEntity.ok(body);
    }

    private <T> ResponseEntity<T> badRequest(T body) {
        return ResponseEntity.badRequest().body(body);
    }

    private <T> ResponseEntity<T> unauthorized(T body) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    private <T> ResponseEntity<T> notFound(T body) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    private String trimOrNull(String s) {
        return s != null ? s.trim() : null;
    }

    private String sha256(String data) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return data;
        }
    }

    private static class AuthenticatedUser {
        public final Long id;
        public final String email;
        public final String type;

        public AuthenticatedUser(Long id, String email, String type) {
            this.id = id;
            this.email = email;
            this.type = type;
        }
    }

    private AuthenticatedUser getAuthenticatedUser(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        String token = header.substring(7);
        if (!jwtUtils.validateToken(token)) {
            return null;
        }
        String username = jwtUtils.getUsernameFromToken(token);
        String role = jwtUtils.extractStringClaim(token, "role");
        
        if ("ROLE_SUPER_ADMIN".equalsIgnoreCase(role)) {
            Optional<SuperAdmin> adminOpt = superAdminRepository.findByUsername(username);
            if (!adminOpt.isPresent()) {
                adminOpt = superAdminRepository.findByEmailIgnoreCase(username);
            }
            if (adminOpt.isPresent()) {
                return new AuthenticatedUser(adminOpt.get().getId(), adminOpt.get().getEmail(), "SUPER_ADMIN");
            }
        } else {
            Optional<TenantUser> userOpt = tenantUserRepository.findByEmailIgnoreCase(username);
            if (userOpt.isPresent()) {
                return new AuthenticatedUser(userOpt.get().getId(), userOpt.get().getEmail(), "TENANT_USER");
            }
        }
        return null;
    }

    // GET /api/auth/devices
    @GetMapping("/devices")
    public ResponseEntity<Object> getActiveDevices(HttpServletRequest request) {
        AuthenticatedUser user = getAuthenticatedUser(request);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(KEY_MESSAGE, "Unauthorized"));
        }

        List<UserDevice> activeDevices = userDeviceRepository
                .findTop20ByUserIdAndUserTypeAndStatusOrderByLastSeenDesc(user.id, user.type, "ACTIVE");

        List<Map<String, Object>> deviceList = new ArrayList<>();
        for (UserDevice dev : activeDevices) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", dev.getId());
            map.put("deviceId", dev.getDeviceId());
            map.put("deviceName", dev.getDeviceName());
            map.put("browser", dev.getBrowser());
            map.put("os", dev.getOs());
            map.put("lastSeen", dev.getLastSeen());
            
            String decryptedIp = dev.getLastIpEncrypted() != null ? 
                    cryptoService.decrypt(dev.getLastIpEncrypted()) : "Unknown";
            String decryptedCountry = dev.getLastCountryEncrypted() != null ? 
                    cryptoService.decrypt(dev.getLastCountryEncrypted()) : "Unknown";
            
            map.put("lastIp", decryptedIp);
            map.put("lastCountry", decryptedCountry);
            deviceList.add(map);
        }

        return ResponseEntity.ok(deviceList);
    }

    // DELETE /api/auth/devices/{id}
    @DeleteMapping("/devices/{id}")
    public ResponseEntity<Object> revokeDevice(@PathVariable("id") Long id, HttpServletRequest request) {
        AuthenticatedUser user = getAuthenticatedUser(request);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(KEY_MESSAGE, "Unauthorized"));
        }

        Optional<UserDevice> devOpt = userDeviceRepository.findById(id);
        if (!devOpt.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(KEY_MESSAGE, "Device not found"));
        }

        UserDevice dev = devOpt.get();
        if (!dev.getUserId().equals(user.id) || !dev.getUserType().equalsIgnoreCase(user.type)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(KEY_MESSAGE, "Access denied"));
        }

        dev.setStatus("REVOKED");
        dev.setRevocationReason("USER_REMOVED");
        userDeviceRepository.save(dev);

        // Also revoke all active sessions associated with this device
        userSessionRepository.findByDeviceIdAndRevokedFalse(dev.getDeviceId()).forEach(session -> {
            session.setRevoked(true);
            userSessionRepository.save(session);
        });

        return ResponseEntity.ok(Map.of(KEY_MESSAGE, "Device successfully revoked"));
    }
}
