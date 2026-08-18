package com.scaloz.superadmin.controller;

import com.scaloz.superadmin.model.Product;
import com.scaloz.superadmin.model.ProductModule;
import com.scaloz.superadmin.repository.ProductRepository;
import com.scaloz.superadmin.repository.ProductModuleRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/modules")
public class ModuleController {

    private final ProductModuleRepository productModuleRepository;
    private final ProductRepository productRepository;

    public ModuleController(ProductModuleRepository productModuleRepository,
                             ProductRepository productRepository) {
        this.productModuleRepository = productModuleRepository;
        this.productRepository = productRepository;
    }

    @GetMapping
    public List<ProductModule> getAllModules() {
        return productModuleRepository.findAll();
    }

    @GetMapping("/product/{productId}")
    public List<ProductModule> getModulesByProductId(@PathVariable Long productId) {
        return productModuleRepository.findByProductId(productId);
    }

    @PostMapping
    public ResponseEntity<Object> createModule(@RequestBody ModuleRequest request) {
        if (request.getName() == null || request.getName().trim().isEmpty() || request.getName().length() > 100) {
            return ResponseEntity.badRequest().body("Module name is required and cannot exceed 100 characters.");
        }
        if (request.getCode() == null || request.getCode().trim().isEmpty() || request.getCode().length() > 50) {
            return ResponseEntity.badRequest().body("Module code is required and cannot exceed 50 characters.");
        }
        if (!request.getCode().trim().matches("^[a-zA-Z0-9_\\-]+$")) {
            return ResponseEntity.badRequest().body("Module code must contain only alphanumeric characters, underscores, or hyphens.");
        }

        Optional<Product> optionalProduct = productRepository.findById(request.getProductId());
        if (optionalProduct.isEmpty()) {
            return ResponseEntity.badRequest().body("Product with ID " + request.getProductId() + " not found.");
        }

        if (productModuleRepository.findByCode(request.getCode().trim()).isPresent()) {
            return ResponseEntity.badRequest().body("Module Code is already existing.");
        }

        ProductModule module = new ProductModule();
        module.setName(request.getName().trim());
        module.setCode(request.getCode().trim());
        module.setProduct(optionalProduct.get());
        module.setProductCode(optionalProduct.get().getCode());

        ProductModule saved = productModuleRepository.save(module);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteModule(@PathVariable Long id) {
        if (productModuleRepository.existsById(id)) {
            productModuleRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    public static class ModuleRequest {
        private String name;
        private String code;
        private Long productId;

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

        public Long getProductId() {
            return productId;
        }

        public void setProductId(Long productId) {
            this.productId = productId;
        }
    }
}
