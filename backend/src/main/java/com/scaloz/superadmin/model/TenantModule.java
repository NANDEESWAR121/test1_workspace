package com.scaloz.superadmin.model;

import jakarta.persistence.*;

@Entity
@Table(name = "tenant_modules")
public class TenantModule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "module_id", nullable = false)
    private ProductModule productModule;

    public TenantModule() {}

    public TenantModule(Tenant tenant, ProductModule productModule) {
        this.tenant = tenant;
        this.productModule = productModule;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public void setTenant(Tenant tenant) {
        this.tenant = tenant;
    }

    public ProductModule getProductModule() {
        return productModule;
    }

    public void setProductModule(ProductModule productModule) {
        this.productModule = productModule;
    }
}
