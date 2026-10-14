
package com.campusfind.config;

import java.nio.file.Paths;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {

        String uploadLocation = Paths
                .get("uploads", "item-images")
                .toAbsolutePath()
                .normalize()
                .toUri()
                .toString();

        System.out.println("IMAGE RESOURCE LOCATION: " + uploadLocation);

        registry.addResourceHandler("/uploads/item-images/**")
                .addResourceLocations(uploadLocation);
    }
}
