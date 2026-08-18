package com.scaloz.superadmin.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import java.util.Arrays;
import java.util.Collections;

@Configuration
@EnableWebSecurity
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
public class SecurityConfig {

    private static final String REGEX_HTTP_PREFIX = "^https?://";
    private static final String REGEX_PORT_SUFFIX = ":\\d+$";

    private final com.scaloz.superadmin.repository.ProductRepository productRepository;
    private final com.scaloz.superadmin.repository.TenantRepository tenantRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public SecurityConfig(
            com.scaloz.superadmin.repository.ProductRepository productRepository,
            com.scaloz.superadmin.repository.TenantRepository tenantRepository) {
        this.productRepository = productRepository;
        this.tenantRepository = tenantRepository;
    }

    @org.springframework.beans.factory.annotation.Value("${scaloz.app.domainName:scaloz.com}")
    private String domainName;

    @Bean
    @SuppressWarnings("java:S4502")
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers
                .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                .contentTypeOptions(contentType -> {})
                .frameOptions(frame -> frame.deny())
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"))
                .referrerPolicy(referrer -> referrer.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                .permissionsPolicyHeader(permissions -> permissions.policy("geolocation=(), microphone=()"))
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/api/public/**", "/error").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/products", "/api/products/**").authenticated()
                .requestMatchers("/api/tenants/**", "/api/products/**", "/api/subscriptions/**", "/api/modules/**").hasAuthority("ROLE_SUPER_ADMIN")
                .requestMatchers("/api/tenant-users/sync-from-hrms", "/api/tenant-users/sync-status-from-hrms").hasAuthority("ROLE_SYSTEM")
                .anyRequest().authenticated()
            )
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"message\": \"Token is expired or Invalid\"}");
                })
            );

        http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        return request -> {
            String origin = request.getHeader("Origin");
            if (origin == null) {
                return null;
            }
            if (isOriginAllowed(origin)) {
                CorsConfiguration configuration = new CorsConfiguration();
                configuration.setAllowedOrigins(Collections.singletonList(origin));
                configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With"));
                configuration.setExposedHeaders(Collections.singletonList("Authorization"));
                configuration.setAllowCredentials(true);
                return configuration;
            }
            return null;
        };
    }

    private String cleanOriginUrl(String url) {
        if (url == null) {
            return "";
        }
        String cleaned = url.toLowerCase()
                .replaceAll(REGEX_HTTP_PREFIX, "");
        while (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned.replaceAll(REGEX_PORT_SUFFIX, "");
    }

    private boolean isProductOriginAllowed(String cleanOrigin) {
        try {
            java.util.List<com.scaloz.superadmin.model.Product> products = productRepository.findAll();
            for (com.scaloz.superadmin.model.Product p : products) {
                String url = p.getUrl();
                if (url != null && !url.trim().isEmpty()) {
                    String cleanUrl = cleanOriginUrl(url);
                    if (cleanOrigin.equals(cleanUrl) || cleanOrigin.endsWith("." + cleanUrl)) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
            // Ignored: fallback if database lookup fails during origin validation
        }
        return false;
    }

    private boolean isTenantOriginAllowed(String cleanOrigin) {
        try {
            java.util.List<com.scaloz.superadmin.model.Tenant> tenants = tenantRepository.findAll();
            for (com.scaloz.superadmin.model.Tenant t : tenants) {
                String website = t.getWebsite();
                if (website != null && !website.trim().isEmpty()) {
                    String cleanUrl = cleanOriginUrl(website);
                    if (cleanOrigin.equals(cleanUrl) || cleanOrigin.endsWith("." + cleanUrl)) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
            // Ignored: fallback if database lookup fails during origin validation
        }
        return false;
    }

    private boolean isOriginAllowed(String origin) {
        if (origin == null) {
            return false;
        }
        if (origin.startsWith("http://localhost:") || origin.equals("http://localhost")) {
            return true;
        }
        if (origin.endsWith(".localhost:3000") || origin.endsWith(".localhost:3001") || origin.endsWith(".localhost:3005")) {
            return true;
        }

        String cleanOrigin = cleanOriginUrl(origin);

        if (isProductOriginAllowed(cleanOrigin)) {
            return true;
        }

        if (isTenantOriginAllowed(cleanOrigin)) {
            return true;
        }

        if (domainName != null && !domainName.trim().isEmpty()) {
            String cleanDomain = cleanOriginUrl(domainName);
            if (cleanOrigin.equals(cleanDomain) || cleanOrigin.endsWith("." + cleanDomain)) {
                return true;
            }
        }

        return false;
    }
}
