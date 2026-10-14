package com.campusfind.controller;

import com.campusfind.service.ImageStorageService;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/images")
public class ImageUploadController {

    private final ImageStorageService imageStorageService;

    public ImageUploadController(
            ImageStorageService imageStorageService) {
        this.imageStorageService = imageStorageService;
    }

    @PostMapping(
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<Map<String, String>> uploadImage(
            @RequestParam("file") MultipartFile file) {

        try {
            String imagePath =
                    imageStorageService.storeImage(file);

            return ResponseEntity.ok(
                    Map.of(
                            "message", "Image uploaded successfully",
                            "imagePath", imagePath
                    )
            );

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest().body(
                    Map.of("error", e.getMessage())
            );

        } catch (IOException e) {

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(
                            Map.of(
                                    "error",
                                    "Unable to save the image."
                            )
                    );
        }
    }
}