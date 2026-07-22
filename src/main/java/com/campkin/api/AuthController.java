package com.campkin.api;
import com.campkin.api.ApiModels.*; import com.campkin.domain.Camp; import com.campkin.service.AuthService; import jakarta.validation.Valid; import lombok.RequiredArgsConstructor; import org.springframework.http.*; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/auth") @RequiredArgsConstructor public class AuthController {
 private final AuthService auth;
 @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED) AuthResponse register(@Valid @RequestBody RegisterRequest request){return auth.register(request);}
 @PostMapping("/login") AuthResponse login(@Valid @RequestBody LoginRequest request){return auth.login(request);}
 @GetMapping("/me") UserView me(){return auth.me();}
 @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT) void logout(@RequestHeader(value="Authorization",required=false)String authorization){auth.logout(authorization!=null&&authorization.startsWith("Bearer ")?authorization.substring(7):null);}
 @PostMapping("/join-camp") Camp join(@Valid @RequestBody JoinCampRequest request){return auth.join(request);}
}
