package com.brahyam.storagemanager.config;

import com.brahyam.storagemanager.controller.AuthController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
@RequiredArgsConstructor
public class AuthRateLimitConfig implements WebMvcConfigurer {
    private final AuthRateLimiter limiter;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                    throws IOException {
                if (!"POST".equals(request.getMethod()) || !(handler instanceof HandlerMethod method)
                        || !AuthController.class.isAssignableFrom(method.getBeanType())) return true;
                String action = method.getMethod().getName();
                if (!action.equals("login") && !action.equals("register")) return true;
                long retryAfter = limiter.tryAcquire(request.getRemoteAddr(), action.equals("register"));
                if (retryAfter == 0) return true;
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
                response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.getWriter().write("{\"error\":\"Demasiados intentos. Inténtalo de nuevo más tarde\"}");
                return false;
            }
        });
    }
}
