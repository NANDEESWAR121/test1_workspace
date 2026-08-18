package com.scaloz.superadmin.service;

import com.scaloz.superadmin.dto.TenantDTO;
import com.scaloz.superadmin.model.Tenant;
import com.scaloz.superadmin.model.Subscription;
import com.scaloz.superadmin.model.TenantModule;
import com.scaloz.superadmin.model.ProductModule;
import com.scaloz.superadmin.model.Product;
import com.scaloz.superadmin.repository.TenantRepository;
import com.scaloz.superadmin.repository.SubscriptionRepository;
import com.scaloz.superadmin.repository.TenantModuleRepository;
import com.scaloz.superadmin.repository.ProductModuleRepository;
import com.scaloz.superadmin.repository.ProductRepository;
import com.scaloz.superadmin.repository.TenantUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TenantModuleRepository tenantModuleRepository;
    private final ProductModuleRepository productModuleRepository;
    private final TenantUserRepository tenantUserRepository;
    private final ProductRepository productRepository;
    private final com.scaloz.superadmin.security.JwtUtils jwtUtils;
    private final org.springframework.mail.javamail.JavaMailSender mailSender;

    @Autowired
    public TenantService(
            TenantRepository tenantRepository,
            SubscriptionRepository subscriptionRepository,
            TenantModuleRepository tenantModuleRepository,
            ProductModuleRepository productModuleRepository,
            TenantUserRepository tenantUserRepository,
            ProductRepository productRepository,
            com.scaloz.superadmin.security.JwtUtils jwtUtils,
            @Autowired(required = false) org.springframework.mail.javamail.JavaMailSender mailSender) {
        this.tenantRepository = tenantRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.tenantModuleRepository = tenantModuleRepository;
        this.productModuleRepository = productModuleRepository;
        this.tenantUserRepository = tenantUserRepository;
        this.productRepository = productRepository;
        this.jwtUtils = jwtUtils;
        this.mailSender = mailSender;
    }

    @org.springframework.beans.factory.annotation.Value("${spring.mail.username:noreply@scaloz.com}")
    private String fromEmail;

    @org.springframework.beans.factory.annotation.Value("${scaloz.security.trust-all-ssl:false}")
    private boolean trustAllSsl;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(TenantService.class);

    private static final String STATUS_ACTIVE = "Active";
    private static final String ROLE_ADMIN = "Admin";

    private final org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder passwordEncoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    private final java.security.SecureRandom secureRandom = new java.security.SecureRandom();

    private static final String CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789@#$!";
    private static final int PASSWORD_LENGTH = 10;

    private String generateTempPassword() {
        StringBuilder sb = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            sb.append(CHARACTERS.charAt(secureRandom.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }

    private void sendAdminWelcomeEmail(String toEmail, String tenantName, String tempPassword, String productNames) {
        if (mailSender == null) {
            logger.error("[Scaloz] ERROR: mailSender is null! Check Spring Boot Mail properties.");
            return;
        }
        if (toEmail == null || toEmail.isBlank()) {
            logger.error("[Scaloz] ERROR: toEmail is blank!");
            return;
        }
        try {
            org.springframework.mail.SimpleMailMessage message = new org.springframework.mail.SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Welcome to Scaloz - Your Tenant Workspace Credentials");
            message.setText(
                    "Dear Administrator,\n\n" +
                            "Your tenant workspace '" + tenantName + "' has been successfully created.\n\n" +
                            "Here are your credentials to access the portal:\n" +
                            "Portal Link: https://scaloz.com\n" +
                            "Admin Email: " + toEmail + "\n" +
                            "Temporary Password: " + tempPassword + "\n\n" +
                            "Assigned Products: " + productNames + "\n\n" +
                            "Please log in and update your password when prompted.\n\n" +
                            "Best regards,\nScaloz Team");
            mailSender.send(message);
            logger.info("[Scaloz] SUCCESS: Admin welcome email sent to: {}", toEmail);
        } catch (Exception e) {
            logger.error("[Scaloz] ERROR: Could not send welcome email to admin {}: {}", toEmail, e.getMessage());
        }
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
                    return status.equalsIgnoreCase(STATUS_ACTIVE);
                }
            } else {
                if (trimmed.equalsIgnoreCase(productCode)) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<String> getActiveProductCodes(String selectedProducts) {
        if (selectedProducts == null || selectedProducts.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> activeCodes = new ArrayList<>();
        String[] products = selectedProducts.split(",");
        for (String p : products) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) {
                String productCode = trimmed;
                boolean active = true;
                if (trimmed.contains(":")) {
                    String[] parts = trimmed.split(":", 2);
                    productCode = parts[0].trim();
                    String status = parts[1].trim();
                    active = status.equalsIgnoreCase(STATUS_ACTIVE);
                }
                if (active) {
                    activeCodes.add(productCode);
                }
            }
        }
        return activeCodes;
    }

    private void syncTenantToProduct(Tenant tenant, String productCode) {
        Optional<Product> prodOpt = productRepository.findByCode(productCode);
        if (prodOpt.isEmpty()) {
            logger.warn("[Scaloz] Product with code {} not found in database. Skipping sync.", productCode);
            return;
        }
        Product prod = prodOpt.get();
        String syncUrl = resolveSyncUrl(prod);
        if (syncUrl == null || syncUrl.trim().isEmpty()) {
            logger.warn("[Scaloz] No syncTenantUrl could be resolved for product {}. Skipping sync.", productCode);
            return;
        }

        sendSyncRequest(tenant, productCode, syncUrl);
    }

    private String resolveSyncUrl(Product prod) {
        String syncUrl = prod.getSyncTenantUrl();
        if (syncUrl != null && !syncUrl.trim().isEmpty()) {
            return syncUrl;
        }
        String baseUrl = prod.getUrl();
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            return null;
        }
        baseUrl = baseUrl.trim();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + "/api/external/tenants";
    }

    @SuppressWarnings("java:S4830")
    private org.springframework.web.client.RestTemplate buildTrustAllRestTemplate() {
        if (!this.trustAllSsl) {
            org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(10000);
            factory.setReadTimeout(10000);
            return new org.springframework.web.client.RestTemplate(factory);
        }
        try {
            javax.net.ssl.TrustManager[] trustAllCerts = new javax.net.ssl.TrustManager[] {
                    new javax.net.ssl.X509TrustManager() {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[0];
                        }

                        public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                            /* trust all */ }

                        public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                            /* trust all */ }
                    }
            };
            javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            org.apache.hc.client5.http.ssl.TlsSocketStrategy tlsSocketStrategy = new org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy(
                    sslContext,
                    org.apache.hc.client5.http.ssl.NoopHostnameVerifier.INSTANCE);

            org.apache.hc.client5.http.impl.classic.CloseableHttpClient httpClient = org.apache.hc.client5.http.impl.classic.HttpClients
                    .custom()
                    .setConnectionManager(
                            org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder.create()
                                    .setTlsSocketStrategy(tlsSocketStrategy)
                                    .build())
                    .build();

            org.springframework.http.client.HttpComponentsClientHttpRequestFactory factory = new org.springframework.http.client.HttpComponentsClientHttpRequestFactory(
                    httpClient);
            factory.setConnectTimeout(10000);
            return new org.springframework.web.client.RestTemplate(factory);
        } catch (Exception ex) {
            logger.warn("[Scaloz] Could not build trust-all RestTemplate, falling back to default: {}",
                    ex.getMessage());
            org.springframework.http.client.SimpleClientHttpRequestFactory fallback = new org.springframework.http.client.SimpleClientHttpRequestFactory();
            fallback.setConnectTimeout(10000);
            fallback.setReadTimeout(10000);
            return new org.springframework.web.client.RestTemplate(fallback);
        }
    }

    private void sendSyncRequest(Tenant tenant, String productCode, String syncUrl) {
        try {
            org.springframework.web.client.RestTemplate restTemplate = buildTrustAllRestTemplate();

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            String jwtToken = jwtUtils.generateToken("system_sync", java.util.Map.of("role", "SYSTEM"));
            headers.set("Authorization", "Bearer " + jwtToken);

            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("tenantId", tenant.getCode());
            payload.put("tenantName", tenant.getName());
            payload.put("adminEmail", tenant.getAdminEmail());
            payload.put("adminFirstName", tenant.getName());
            payload.put("adminLastName", "Administrooes");
            payload.put("admin_first_name", tenant.getName());
            payload.put("admin_last_name", "Administrooes");

            org.springframework.http.HttpEntity<java.util.Map<String, Object>> requestEntity = new org.springframework.http.HttpEntity<>(
                    payload, headers);

            logger.info("[Scaloz] Syncing tenant {} to product {}: {}", tenant.getCode(), productCode, syncUrl);
            org.springframework.http.ResponseEntity<String> response = restTemplate.postForEntity(syncUrl,
                    requestEntity, String.class);
            logger.info("[Scaloz] Product {} tenant sync response: {}", productCode, response.getStatusCode());
        } catch (Exception e) {
            logger.error("[Scaloz] Error syncing tenant to product {}: {}", productCode, e.getMessage(), e);
        }
    }

    private void syncTenantToProducts(Tenant tenant) {
        String selectedProducts = tenant.getSelectedProducts();
        List<String> activeCodes = getActiveProductCodes(selectedProducts);
        if (activeCodes.isEmpty()) {
            logger.info("[Scaloz] No active products selected for tenant {}. Skipping sync.", tenant.getCode());
            return;
        }
        for (String productCode : activeCodes) {
            syncTenantToProduct(tenant, productCode);
        }
    }

    private void syncTenantToProductsAsync(Tenant tenant) {
        new Thread(() -> {
            try {
                syncTenantToProducts(tenant);
            } catch (Exception e) {
                logger.error("[Scaloz] Background tenant sync error: {}", e.getMessage(), e);
            }
        }).start();
    }

    /**
     * Syncs the tenant's admin employee record to all active product databases.
     * Called on tenant update so admin email/name changes propagate to HRMS.
     */
    private void syncAdminUserToProductsAsync(Tenant tenant) {
        // Capture admin user details before the async thread
        Optional<com.scaloz.superadmin.model.TenantUser> adminOpt = tenantUserRepository
                .findByTenantId(tenant.getId())
                .stream()
                .filter(u -> ROLE_ADMIN.equalsIgnoreCase(u.getRole()))
                .findFirst();
        if (!adminOpt.isPresent()) {
            logger.info("[Scaloz] No admin user found for tenant {}, skipping admin employee sync.", tenant.getCode());
            return;
        }
        com.scaloz.superadmin.model.TenantUser adminUser = adminOpt.get();

        new Thread(() -> {
            try {
                String selectedProducts = tenant.getSelectedProducts();
                List<String> activeCodes = getActiveProductCodes(selectedProducts);
                for (String productCode : activeCodes) {
                    syncAdminUserToProduct(tenant, adminUser, productCode);
                }
            } catch (Exception e) {
                logger.error("[Scaloz] Background admin user sync error: {}", e.getMessage(), e);
            }
        }).start();
    }

    private void syncAdminUserToProduct(Tenant tenant, com.scaloz.superadmin.model.TenantUser adminUser, String productCode) {
        Optional<Product> prodOpt = productRepository.findByCode(productCode);
        if (prodOpt.isEmpty()) {
            logger.warn("[Scaloz] Product {} not found, skipping admin user sync.", productCode);
            return;
        }
        Product prod = prodOpt.get();

        // Resolve employee sync URL (same logic as TenantUserController)
        String syncUrl = prod.getSyncUserUrl();
        if (syncUrl == null || syncUrl.trim().isEmpty()) {
            String baseUrl = prod.getUrl();
            if (baseUrl == null || baseUrl.trim().isEmpty()) {
                logger.warn("[Scaloz] No syncUserUrl or baseUrl for product {}, skipping admin user sync.", productCode);
                return;
            }
            baseUrl = baseUrl.trim();
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            syncUrl = baseUrl + "/api/external/employees";
        }

        try {
            org.springframework.web.client.RestTemplate restTemplate = buildTrustAllRestTemplate();
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            String jwtToken = jwtUtils.generateToken("system_sync", java.util.Map.of("role", "SYSTEM"));
            headers.set("Authorization", "Bearer " + jwtToken);

            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("employeeId", adminUser.getEmployeeId()); // e.g. "apex0001_admin"
            payload.put("email", adminUser.getEmail());
            payload.put("firstName", adminUser.getFirstName());
            payload.put("lastName", adminUser.getLastName());
            payload.put("role", "Admin");
            payload.put("tenantId", tenant.getCode());
            payload.put("tenantName", tenant.getName());
            payload.put("assignedProducts", adminUser.getAssignedProducts());

            org.springframework.http.HttpEntity<java.util.Map<String, Object>> requestEntity =
                    new org.springframework.http.HttpEntity<>(payload, headers);

            logger.info("[Scaloz] Syncing admin user {} email update to product {}: {}", adminUser.getEmployeeId(), productCode, syncUrl);
            org.springframework.http.ResponseEntity<String> response = restTemplate.postForEntity(syncUrl, requestEntity, String.class);
            logger.info("[Scaloz] Admin user sync to product {} response: {}", productCode, response.getStatusCode());
        } catch (Exception e) {
            logger.error("[Scaloz] Error syncing admin user to product {}: {}", productCode, e.getMessage(), e);
        }
    }

    public List<TenantDTO> getAllTenants() {
        List<Tenant> tenants = tenantRepository.findAll();
        if (tenants.isEmpty()) {
            return new ArrayList<>();
        }

        // Fetch all subscriptions in one query and group by tenant ID
        List<Subscription> allSubs = subscriptionRepository.findAll();
        Map<Long, String> tenantToSubPlan = new HashMap<>();
        for (Subscription sub : allSubs) {
            if (sub.getTenant() != null) {
                tenantToSubPlan.put(sub.getTenant().getId(), sub.getPlanName());
            }
        }

        // Fetch all tenant modules in one query and group by tenant ID
        List<TenantModule> allMods = tenantModuleRepository.findAll();
        Map<Long, List<TenantModule>> tenantToModules = new HashMap<>();
        for (TenantModule tm : allMods) {
            if (tm.getTenant() != null) {
                tenantToModules.computeIfAbsent(tm.getTenant().getId(), k -> new ArrayList<>()).add(tm);
            }
        }

        List<TenantDTO> dtos = new ArrayList<>();
        for (Tenant t : tenants) {
            TenantDTO dto = convertToDTO(t);
            
            // Set subscription plan from pre-fetched map
            String plan = tenantToSubPlan.get(t.getId());
            if (plan != null) {
                dto.setSubscriptionPlan(plan);
            }

            // Set selected modules and products from pre-fetched map
            List<TenantModule> tMods = tenantToModules.getOrDefault(t.getId(), Collections.emptyList());
            List<String> modNames = new ArrayList<>();
            List<String> prodCodes = new ArrayList<>();
            for (TenantModule tm : tMods) {
                ProductModule pm = tm.getProductModule();
                if (pm != null) {
                    modNames.add(pm.getName());
                    Product p = pm.getProduct();
                    if (p != null && !prodCodes.contains(p.getCode())) {
                        prodCodes.add(p.getCode());
                    }
                }
            }
            dto.setSelectedModules(String.join(", ", modNames));
            if (dto.getSelectedProducts() == null || dto.getSelectedProducts().trim().isEmpty()) {
                dto.setSelectedProducts(String.join(", ", prodCodes));
            }
            dtos.add(dto);
        }
        return dtos;
    }

    private void createTenantSubscription(Tenant savedTenant, String subscriptionPlan) {
        String planName = subscriptionPlan != null ? subscriptionPlan : "Standard Plan";
        Integer userLimit = 100; // default user limit
        if (planName.toLowerCase().contains("users")) {
            try {
                String clean = planName.replaceAll("\\D", "");
                if (!clean.isEmpty()) {
                    userLimit = Integer.parseInt(clean);
                }
            } catch (Exception ignored) {
                // Ignore parsing errors and fallback to default userLimit
            }
        }
        Subscription subscription = new Subscription(planName, userLimit, STATUS_ACTIVE, savedTenant);
        subscriptionRepository.save(subscription);
    }

    private void saveTenantModules(Tenant savedTenant, String selectedModules) {
        if (selectedModules == null || selectedModules.trim().isEmpty()) {
            return;
        }
        String[] modules = selectedModules.split(",");
        for (String modName : modules) {
            String trimmed = modName.trim();
            if (trimmed.isEmpty())
                continue;
            Optional<ProductModule> modOpt = productModuleRepository.findByName(trimmed);
            if (modOpt.isPresent()) {
                tenantModuleRepository.save(new TenantModule(savedTenant, modOpt.get()));
            } else {
                Optional<ProductModule> modCodeOpt = productModuleRepository.findByCode(trimmed);
                if (modCodeOpt.isPresent()) {
                    tenantModuleRepository.save(new TenantModule(savedTenant, modCodeOpt.get()));
                }
            }
        }
    }

    private String createTenantAdminUser(Tenant savedTenant) {
        String tempPassword = generateTempPassword();
        com.scaloz.superadmin.model.TenantUser adminUser = new com.scaloz.superadmin.model.TenantUser();
        adminUser.setTenant(savedTenant);
        adminUser.setEmployeeId(savedTenant.getCode() + "_admin");
        adminUser.setFirstName(ROLE_ADMIN);
        adminUser.setLastName("User");
        adminUser.setEmail(savedTenant.getAdminEmail());
        adminUser.setRole(ROLE_ADMIN);
        adminUser.setPassword(passwordEncoder.encode(tempPassword));
        adminUser.setMustChangePassword(true);
        adminUser.setStatus(STATUS_ACTIVE);
        adminUser.setAssignedProducts(savedTenant.getSelectedProducts());
        tenantUserRepository.save(adminUser);
        logger.debug("[Scaloz DEBUG] Created tenant admin user. Email: {}, Temp Password: {}",
                savedTenant.getAdminEmail(), tempPassword);
        return tempPassword;
    }

    private void sendWelcomeEmailAsync(Tenant savedTenant, String tempPassword) {
        String selectedProducts = savedTenant.getSelectedProducts();
        new Thread(() -> {
            try {
                List<String> prodNames = new ArrayList<>();
                if (selectedProducts != null && !selectedProducts.trim().isEmpty()) {
                    String[] codes = selectedProducts.split(",");
                    for (String code : codes) {
                        String cleanCode = code.trim();
                        if (cleanCode.contains(":")) {
                            cleanCode = cleanCode.split(":", 2)[0].trim();
                        }
                        productRepository.findByCode(cleanCode).ifPresent(p -> prodNames.add(p.getName()));
                    }
                }
                String productNames = String.join(", ", prodNames);
                sendAdminWelcomeEmail(savedTenant.getAdminEmail(), savedTenant.getName(), tempPassword, productNames);
            } catch (Exception e) {
                logger.error("[Scaloz] Background welcome email delivery error: {}", e.getMessage(), e);
            }
        }).start();
    }

    private void updateTenantFields(Tenant existingTenant, TenantDTO updatedTenantDTO) {
        existingTenant.setName(updatedTenantDTO.getName());
        if (updatedTenantDTO.getCode() != null) {
            existingTenant.setCode(updatedTenantDTO.getCode());
        }
        existingTenant.setEmail(updatedTenantDTO.getEmail());
        existingTenant.setCountryCode(updatedTenantDTO.getCountryCode());
        existingTenant.setPhone(updatedTenantDTO.getPhone());
        existingTenant.setLandline(updatedTenantDTO.getLandline());
        existingTenant.setWebsite(updatedTenantDTO.getWebsite());
        existingTenant.setCompanySize(updatedTenantDTO.getCompanySize());
        existingTenant.setAddress(updatedTenantDTO.getAddress());
        if (updatedTenantDTO.getLogo() != null) {
            existingTenant.setLogo(updatedTenantDTO.getLogo());
        }
        existingTenant.setSelectedProducts(updatedTenantDTO.getSelectedProducts());
        if (updatedTenantDTO.getAdminEmail() != null) {
            existingTenant.setAdminEmail(updatedTenantDTO.getAdminEmail());
        }
        if (updatedTenantDTO.getStatus() != null) {
            existingTenant.setStatus(updatedTenantDTO.getStatus());
        }
    }

    private void updateAdminUser(Long tenantId, Tenant existingTenant) {
        Optional<com.scaloz.superadmin.model.TenantUser> adminUserOpt = tenantUserRepository.findByTenantId(tenantId)
                .stream()
                .filter(u -> ROLE_ADMIN.equalsIgnoreCase(u.getRole()))
                .findFirst();
        if (adminUserOpt.isPresent()) {
            com.scaloz.superadmin.model.TenantUser adminUser = adminUserOpt.get();
            adminUser.setAssignedProducts(existingTenant.getSelectedProducts());
            if (existingTenant.getAdminEmail() != null) {
                adminUser.setEmail(existingTenant.getAdminEmail());
            }
            if (existingTenant.getCode() != null) {
                adminUser.setEmployeeId(existingTenant.getCode() + "_admin");
            }
            tenantUserRepository.save(adminUser);
        }
    }

    private void updateTenantSubscription(Long tenantId, Tenant existingTenant, String subscriptionPlan) {
        Optional<Subscription> subOpt = subscriptionRepository.findByTenantId(tenantId);
        String planName = subscriptionPlan != null ? subscriptionPlan : "Standard Plan";
        if (subOpt.isPresent()) {
            Subscription subscription = subOpt.get();
            subscription.setPlanName(planName);
            subscriptionRepository.save(subscription);
        } else {
            Subscription subscription = new Subscription(planName, 100, STATUS_ACTIVE, existingTenant);
            subscriptionRepository.save(subscription);
        }
    }

    private void updateTenantModules(Long tenantId, Tenant existingTenant, String selectedModules) {
        tenantModuleRepository.deleteByTenantId(tenantId);
        tenantModuleRepository.flush();
        saveTenantModules(existingTenant, selectedModules);
    }

    @Transactional
    public TenantDTO createTenant(TenantDTO tenantDTO) {
        Tenant tenant = convertToEntity(tenantDTO);
        Tenant savedTenant = tenantRepository.save(tenant);

        createTenantSubscription(savedTenant, tenantDTO.getSubscriptionPlan());
        saveTenantModules(savedTenant, tenantDTO.getSelectedModules());
        String tempPassword = createTenantAdminUser(savedTenant);
        sendWelcomeEmailAsync(savedTenant, tempPassword);

        // Sync Tenant to employee portal database
        syncTenantToProductsAsync(savedTenant);

        return convertToDTO(savedTenant);
    }

    @Transactional
    public TenantDTO updateTenant(Long id, TenantDTO updatedTenantDTO) {
        Optional<Tenant> tenantOpt = tenantRepository.findById(id);
        if (!tenantOpt.isPresent()) {
            throw new java.util.NoSuchElementException("Tenant not found");
        }

        Tenant existingTenant = tenantOpt.get();
        updateTenantFields(existingTenant, updatedTenantDTO);
        tenantRepository.save(existingTenant);

        updateAdminUser(id, existingTenant);
        updateTenantSubscription(id, existingTenant, updatedTenantDTO.getSubscriptionPlan());
        updateTenantModules(id, existingTenant, updatedTenantDTO.getSelectedModules());

        // Sync updated Tenant details to employee portal database
        syncTenantToProductsAsync(existingTenant);

        // Also sync admin user record so email/name changes propagate to HRMS employee table
        syncAdminUserToProductsAsync(existingTenant);

        // Force flush
        tenantRepository.flush();
        tenantUserRepository.flush();
        subscriptionRepository.flush();
        tenantModuleRepository.flush();

        return convertToDTO(existingTenant);
    }

    @Transactional
    public void deleteTenant(Long id) {
        if (tenantRepository.existsById(id)) {
            subscriptionRepository.deleteByTenantId(id);
            tenantModuleRepository.deleteByTenantId(id);
            tenantRepository.deleteById(id);
        } else {
            throw new java.util.NoSuchElementException("Tenant not found");
        }
    }

    private TenantDTO convertToDTO(Tenant tenant) {
        TenantDTO dto = new TenantDTO();
        dto.setId(tenant.getId());
        dto.setName(tenant.getName());
        dto.setCode(tenant.getCode());
        dto.setEmail(tenant.getEmail());
        dto.setCountryCode(tenant.getCountryCode());
        dto.setPhone(tenant.getPhone());
        dto.setLandline(tenant.getLandline());
        dto.setWebsite(tenant.getWebsite());
        dto.setCompanySize(tenant.getCompanySize());
        dto.setAddress(tenant.getAddress());
        dto.setLogo(tenant.getLogo());
        dto.setAdminEmail(tenant.getAdminEmail());
        dto.setSelectedProducts(tenant.getSelectedProducts());
        dto.setStatus(tenant.getStatus());
        return dto;
    }

    private Tenant convertToEntity(TenantDTO dto) {
        Tenant tenant = new Tenant();
        tenant.setId(dto.getId());
        tenant.setName(dto.getName());
        tenant.setCode(dto.getCode());
        tenant.setEmail(dto.getEmail());
        tenant.setCountryCode(dto.getCountryCode());
        tenant.setPhone(dto.getPhone());
        tenant.setLandline(dto.getLandline());
        tenant.setWebsite(dto.getWebsite());
        tenant.setCompanySize(dto.getCompanySize());
        tenant.setAddress(dto.getAddress());
        tenant.setLogo(dto.getLogo());
        tenant.setAdminEmail(dto.getAdminEmail());
        tenant.setSelectedProducts(dto.getSelectedProducts());
        if (dto.getStatus() != null) {
            tenant.setStatus(dto.getStatus());
        }
        return tenant;
    }
}
