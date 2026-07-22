package com.campkin.config;
import org.springframework.beans.factory.annotation.Value; import org.springframework.context.annotation.*; import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder; import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.config.annotation.CorsRegistry; import java.util.Arrays;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
@Configuration public class WebConfig implements WebMvcConfigurer {
  private final AuthInterceptor auth; public WebConfig(AuthInterceptor auth){this.auth=auth;}
  @Value("${app.cors-origins}") String origins;
  @Override public void addCorsMappings(CorsRegistry r){ r.addMapping("/api/**").allowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).toArray(String[]::new)).allowedMethods("GET","POST","PUT","PATCH","DELETE").allowedHeaders("*"); }
  @Override public void addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry registry){registry.addInterceptor(auth).addPathPatterns("/api/**").excludePathPatterns("/api/health","/api/auth/register","/api/auth/login");}
  @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
}
