package com.scaloz.superadmin.controller;

import com.scaloz.superadmin.dto.ProductDTO;
import com.scaloz.superadmin.service.ProductService;
import com.scaloz.superadmin.repository.ProductRepository;
import jakarta.validation.Valid;

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
@RequestMapping("/api/products")
public class ProductController {

    private static final String KEY_MESSAGE = "message";

    private final ProductService productService;
    private final ProductRepository productRepository;

    public ProductController(ProductService productService,
                              ProductRepository productRepository) {
        this.productService = productService;
        this.productRepository = productRepository;
    }

    @GetMapping
    public List<ProductDTO> getAllProducts() {
        return productService.getAllProducts();
    }

    @PostMapping
    public ResponseEntity<Object> createProduct(@Valid @RequestBody ProductDTO productDTO) {
        if (productDTO.getName() != null && !productDTO.getName().trim().isEmpty()
                && productRepository.findByNameIgnoreCase(productDTO.getName().trim()).isPresent()) {
            return ResponseEntity.badRequest().body("Product Name is already existing.");
        }
        // Duplicate product code check (requires DB lookup, cannot be done via annotation)
        if (productRepository.findByCode(productDTO.getCode().trim()).isPresent()) {
            return ResponseEntity.badRequest().body("Product Code is already existing.");
        }
        if (productDTO.getUrl() != null && !productDTO.getUrl().trim().isEmpty()
                && productRepository.findByUrl(productDTO.getUrl().trim()).isPresent()) {
            return ResponseEntity.badRequest().body("Product URL is already existing.");
        }
        try {
            ProductDTO savedProduct = productService.createProduct(productDTO);
            return ResponseEntity.ok(savedProduct);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error creating product: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Object> updateProduct(@PathVariable Long id, @Valid @RequestBody ProductDTO productDetailsDTO) {
        if (productDetailsDTO.getName() != null && !productDetailsDTO.getName().trim().isEmpty()) {
            Optional<com.scaloz.superadmin.model.Product> duplicateNameOpt = productRepository.findByNameIgnoreCase(productDetailsDTO.getName().trim());
            if (duplicateNameOpt.isPresent() && !duplicateNameOpt.get().getId().equals(id)) {
                return ResponseEntity.badRequest().body("Product Name is already existing.");
            }
        }
        // Duplicate product code check for a different product (requires DB lookup)
        Optional<com.scaloz.superadmin.model.Product> duplicateOpt = productRepository.findByCode(productDetailsDTO.getCode().trim());
        if (duplicateOpt.isPresent() && !duplicateOpt.get().getId().equals(id)) {
            return ResponseEntity.badRequest().body("Product Code is already existing.");
        }
        if (productDetailsDTO.getUrl() != null && !productDetailsDTO.getUrl().trim().isEmpty()) {
            Optional<com.scaloz.superadmin.model.Product> duplicateUrlOpt = productRepository.findByUrl(productDetailsDTO.getUrl().trim());
            if (duplicateUrlOpt.isPresent() && !duplicateUrlOpt.get().getId().equals(id)) {
                return ResponseEntity.badRequest().body("Product URL is already existing.");
            }
        }

        try {
            ProductDTO updatedProduct = productService.updateProduct(id, productDetailsDTO);
            return ResponseEntity.ok(updatedProduct);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error updating product: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        try {
            productService.deleteProduct(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * POST /api/products/upload-icon
     * Accepts a multipart image file, converts it to base64, and returns the data URL.
     */
    @PostMapping(value = "/upload-icon", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Object> uploadIcon(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, "No file provided."));
        }
        if (file.getSize() > 2 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of(KEY_MESSAGE, "Icon file must be smaller than 2MB."));
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
            String iconUrl = "data:" + contentType + ";base64," + base64;
            return ResponseEntity.ok(Map.of("iconUrl", iconUrl));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(KEY_MESSAGE, "Failed to process icon: " + e.getMessage()));
        }
    }
}
