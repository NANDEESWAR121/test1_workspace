package com.scaloz.superadmin.service;

import com.scaloz.superadmin.dto.ProductDTO;
import com.scaloz.superadmin.model.Product;
import com.scaloz.superadmin.repository.ProductRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }


    public List<ProductDTO> getAllProducts() {
        return productRepository.findAll().stream()
                .map(this::convertToDTO)
                .toList();
    }



    private void populateDefaults(Product product) {
        String baseUrl = product.getUrl();
        if (baseUrl != null && !baseUrl.trim().isEmpty()) {
            baseUrl = baseUrl.trim();
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            product.setSyncTenantUrl(baseUrl + "/api/external/tenants");
            product.setSyncUserUrl(baseUrl + "/api/external/employees");
        }
    }

    @Transactional
    public ProductDTO createProduct(ProductDTO productDTO) {
        Product product = convertToEntity(productDTO);
        populateDefaults(product);
        Product savedProduct = productRepository.save(product);
        return convertToDTO(savedProduct);
    }

    @Transactional
    public ProductDTO updateProduct(Long id, ProductDTO productDetailsDTO) {
        Optional<Product> optionalProduct = productRepository.findById(id);
        if (optionalProduct.isPresent()) {
            Product product = optionalProduct.get();
            product.setName(productDetailsDTO.getName());
            product.setCode(productDetailsDTO.getCode());
            product.setUrl(productDetailsDTO.getUrl());
            product.setIcon(productDetailsDTO.getIcon());
            product.setContent(productDetailsDTO.getContent());
            product.setStatus(productDetailsDTO.getStatus());
            
            // Set values from DTO (they will be null if not passed, but we populate defaults right after)
            product.setSyncTenantUrl(productDetailsDTO.getSyncTenantUrl());
            product.setSyncUserUrl(productDetailsDTO.getSyncUserUrl());
            
            populateDefaults(product);
            
            Product updatedProduct = productRepository.save(product);
            return convertToDTO(updatedProduct);
        }
        throw new java.util.NoSuchElementException("Product not found");
    }

    @Transactional
    public void deleteProduct(Long id) {
        if (productRepository.existsById(id)) {
            productRepository.deleteById(id);
        } else {
            throw new java.util.NoSuchElementException("Product not found");
        }
    }

    public Optional<ProductDTO> findProductByCode(String code) {
        return productRepository.findByCode(code).map(this::convertToDTO);
    }

    private ProductDTO convertToDTO(Product product) {
        ProductDTO dto = new ProductDTO();
        dto.setId(product.getId());
        dto.setName(product.getName());
        dto.setCode(product.getCode());
        dto.setUrl(product.getUrl());
        dto.setIcon(product.getIcon());
        dto.setContent(product.getContent());
        dto.setStatus(product.getStatus());
        dto.setSyncTenantUrl(product.getSyncTenantUrl());
        dto.setSyncUserUrl(product.getSyncUserUrl());
        return dto;
    }

    private Product convertToEntity(ProductDTO dto) {
        Product product = new Product();
        product.setId(dto.getId());
        product.setName(dto.getName());
        product.setCode(dto.getCode());
        product.setUrl(dto.getUrl());
        product.setIcon(dto.getIcon());
        product.setContent(dto.getContent());
        product.setSyncTenantUrl(dto.getSyncTenantUrl());
        product.setSyncUserUrl(dto.getSyncUserUrl());
        if (dto.getStatus() != null) {
            product.setStatus(dto.getStatus());
        }
        return product;
    }
}
