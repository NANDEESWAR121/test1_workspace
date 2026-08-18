package com.scaloz.superadmin.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtUtils jwtUtils;
    private final TokenBlacklistService tokenBlacklistService;

    public JwtAuthFilter(JwtUtils jwtUtils, TokenBlacklistService tokenBlacklistService) {
        this.jwtUtils = jwtUtils;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String path = request.getRequestURI();
            String jwt = parseJwt(request);
            boolean isSyncEndpoint = path != null && path.contains("/api/tenant-users/sync");

            if (isSyncEndpoint) {
                log.info("[Scaloz Auth Debug Filter] Path: {}, Token present: {}", path, jwt != null);
            }

            if (jwt != null && !tokenBlacklistService.isBlacklisted(jwt) && jwtUtils.validateToken(jwt)) {
                String username = jwtUtils.getUsernameFromToken(jwt);
                String role = jwtUtils.extractStringClaim(jwt, "role");
                if (role == null) {
                    role = "USER";
                }
                String authorityName = role.startsWith("ROLE_") ? role : "ROLE_" + role;
                org.springframework.security.core.authority.SimpleGrantedAuthority authority =
                        new org.springframework.security.core.authority.SimpleGrantedAuthority(authorityName);

                if (isSyncEndpoint) {
                    log.info("[Scaloz Auth Debug Filter] Sub: {}, Role: {}, Authority: {}", username, role, authorityName);
                }

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        username, null, java.util.Collections.singletonList(authority));

                java.util.Map<String, Object> details = new java.util.HashMap<>();
                details.put("tenant", jwtUtils.extractStringClaim(jwt, "tenant"));
                details.put("role", role);
                details.put("employeeId", jwtUtils.extractStringClaim(jwt, "employeeId"));
                authentication.setDetails(details);

                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else if (isSyncEndpoint && jwt != null) {
                log.warn("[Scaloz Auth Debug Filter] JWT validation failed for sync endpoint!");
            }
        } catch (Exception e) {
            log.error("Cannot set user authentication: {}", e.getMessage(), e);
        }

        filterChain.doFilter(request, response);
    }

    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }
        return null;
    }
}
