package com.campkin.config;
import com.campkin.domain.UserSession; import com.campkin.repo.UserSessionRepository; import jakarta.servlet.http.*; import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Component; import org.springframework.web.servlet.HandlerInterceptor; import java.io.*; import java.time.*;
@Component @RequiredArgsConstructor public class AuthInterceptor implements HandlerInterceptor {
 public static final String USER_ATTRIBUTE="vaultUser"; private final UserSessionRepository sessions;
 @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler)throws IOException{
  if("OPTIONS".equalsIgnoreCase(request.getMethod()))return true;
  String authorization=request.getHeader("Authorization");
  if(authorization==null||!authorization.startsWith("Bearer "))return unauthorized(response);
  String hash=AuthTokens.hash(authorization.substring(7).trim());
  UserSession session=sessions.findByTokenHash(hash).orElse(null);
  if(session==null||session.getExpiresAt().isBefore(Instant.now())){if(session!=null)sessions.delete(session);return unauthorized(response);}
  request.setAttribute(USER_ATTRIBUTE,session.getUser());return true;
 }
 private boolean unauthorized(HttpServletResponse response)throws IOException{response.setStatus(401);response.setContentType("application/json");response.getWriter().write("{\"message\":\"Sign in is required\"}");return false;}
}
