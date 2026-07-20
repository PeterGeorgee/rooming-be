package com.campkin.config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
@Configuration public class WebConfig implements WebMvcConfigurer {
  @Value("${app.cors-origins}") String origins;
  @Override public void addCorsMappings(CorsRegistry r){ r.addMapping("/api/**").allowedOrigins(origins.split(",")).allowedMethods("GET","POST","PUT","PATCH","DELETE").allowedHeaders("*"); }
}
