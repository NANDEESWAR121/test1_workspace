package com.scaloz.superadmin.controller;

import com.scaloz.superadmin.model.Tenant;
import com.scaloz.superadmin.model.Subscription;
import com.scaloz.superadmin.repository.TenantRepository;
import com.scaloz.superadmin.repository.SubscriptionRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    private final SubscriptionRepository subscriptionRepository;
    private final TenantRepository tenantRepository;

    public SubscriptionController(SubscriptionRepository subscriptionRepository,
                                   TenantRepository tenantRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.tenantRepository = tenantRepository;
    }

    @GetMapping
    public List<Subscription> getAllSubscriptions() {
        return subscriptionRepository.findAll();
    }

    @PostMapping
    public ResponseEntity<Object> createOrUpdateSubscription(@RequestBody SubscriptionRequest request) {
        if (request.getPlanName() == null || request.getPlanName().trim().isEmpty() || request.getPlanName().length() > 100) {
            return ResponseEntity.badRequest().body("Plan name is required and cannot exceed 100 characters.");
        }
        if (request.getUserLimit() == null || request.getUserLimit() <= 0 || request.getUserLimit() > 100000) {
            return ResponseEntity.badRequest().body("User limit must be a positive integer and cannot exceed 100,000.");
        }
        if (request.getStatus() != null && !request.getStatus().equalsIgnoreCase("Active") && !request.getStatus().equalsIgnoreCase("Inactive")) {
            return ResponseEntity.badRequest().body("Status must be 'Active' or 'Inactive'.");
        }

        Optional<Tenant> optionalTenant = tenantRepository.findById(request.getTenantId());
        if (optionalTenant.isEmpty()) {
            return ResponseEntity.badRequest().body("Tenant with ID " + request.getTenantId() + " not found.");
        }

        // Check if subscription already exists for this tenant
        Optional<Subscription> existing = subscriptionRepository.findByTenantId(request.getTenantId());
        Subscription subscription = existing.orElse(new Subscription());
        
        subscription.setPlanName(request.getPlanName().trim());
        subscription.setUserLimit(request.getUserLimit());
        subscription.setStatus(request.getStatus() != null ? request.getStatus().trim() : "Active");
        subscription.setTenant(optionalTenant.get());

        Subscription saved = subscriptionRepository.save(subscription);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSubscription(@PathVariable Long id) {
        if (subscriptionRepository.existsById(id)) {
            subscriptionRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    public static class SubscriptionRequest {
        private String planName;
        private Integer userLimit;
        private String status;
        private Long tenantId;

        public String getPlanName() {
            return planName;
        }

        public void setPlanName(String planName) {
            this.planName = planName;
        }

        public Integer getUserLimit() {
            return userLimit;
        }

        public void setUserLimit(Integer userLimit) {
            this.userLimit = userLimit;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Long getTenantId() {
            return tenantId;
        }

        public void setTenantId(Long tenantId) {
            this.tenantId = tenantId;
        }
    }
}
