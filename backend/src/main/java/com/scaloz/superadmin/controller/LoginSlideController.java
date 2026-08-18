package com.scaloz.superadmin.controller;

import com.scaloz.superadmin.model.LoginSlide;
import com.scaloz.superadmin.repository.LoginSlideRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class LoginSlideController {

    private final LoginSlideRepository loginSlideRepository;

    public LoginSlideController(LoginSlideRepository loginSlideRepository) {
        this.loginSlideRepository = loginSlideRepository;
    }

    // Public endpoint for both login screens (no authentication required)
    @GetMapping("/api/public/slides")
    public List<LoginSlide> getAllPublicSlides() {
        return loginSlideRepository.findAll();
    }

    // Authenticated endpoint to get all slides (for Settings UI)
    @GetMapping("/api/settings/slides")
    public List<LoginSlide> getAllSettingsSlides() {
        return loginSlideRepository.findAll();
    }

    // Authenticated endpoint to save a new slide
    @PostMapping("/api/settings/slides")
    public ResponseEntity<Object> createSlide(@RequestBody LoginSlideDto slideDto) {
        if (slideDto.getTitle() != null && slideDto.getTitle().length() > 100) {
            return ResponseEntity.badRequest().body("Slide title cannot exceed 100 characters.");
        }
        if (slideDto.getDescription() != null && slideDto.getDescription().length() > 500) {
            return ResponseEntity.badRequest().body("Slide description cannot exceed 500 characters.");
        }
        if (slideDto.getImageUrl() != null && slideDto.getImageUrl().length() > 15000000) {
            return ResponseEntity.badRequest().body("Slide image URL/data cannot exceed 15,000,000 characters.");
        }

        LoginSlide slide = new LoginSlide();
        slide.setId(slideDto.getId());
        slide.setTitle(slideDto.getTitle());
        slide.setDescription(slideDto.getDescription());
        slide.setImageUrl(slideDto.getImageUrl());

        LoginSlide savedSlide = loginSlideRepository.save(slide);
        return ResponseEntity.ok(savedSlide);
    }

    // Authenticated endpoint to delete a slide by ID
    @DeleteMapping("/api/settings/slides/{id}")
    public ResponseEntity<Void> deleteSlide(@PathVariable Long id) {
        if (loginSlideRepository.existsById(id)) {
            loginSlideRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    public static class LoginSlideDto {
        private Long id;
        private String imageUrl;
        private String title;
        private String description;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getImageUrl() {
            return imageUrl;
        }

        public void setImageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}
