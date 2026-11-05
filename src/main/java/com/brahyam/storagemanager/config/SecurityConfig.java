package com.brahyam.storagemanager.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import jakarta.servlet.DispatcherType;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] WEB_PATHS = {"/", "/index.html", "/acceso", "/registro", "/inventario", "/perfil", "/objetos/{id:[0-9]+}",
            "/*.js", "/*.css", "/favicon.svg", "/manifest.webmanifest", "/icons/**", "/media/**", "/3rdpartylicenses.txt"};

    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsServiceImpl userDetailsServiceImpl;

    @Value("${security.browser-headers.enabled:false}")
    private boolean browserHeadersEnabled;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsServiceImpl);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, WEB_PATHS).permitAll()
                        .requestMatchers(HttpMethod.HEAD, WEB_PATHS).permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/v3/api-docs", "/swagger-resources/**", "/webjars/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/users", "/api/users/").denyAll()
                        .requestMatchers(HttpMethod.DELETE, "/api/users", "/api/users/**").denyAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    response.getWriter().write("{\"error\":\"Autenticación requerida\"}");
                }))
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        if (browserHeadersEnabled) {
            http.headers(headers -> headers
                    .addHeaderWriter((request, response) -> {
                        String path = request.getServletPath();
                        String type = response.getContentType();
                        if (response.getStatus() == 200 && type != null
                                && (type.startsWith("text/javascript") || type.startsWith("application/javascript") || type.startsWith("text/css"))
                                && ("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()))
                                && path.matches("/(?:main|polyfills|chunk|styles)-[A-Za-z0-9_-]{6,}\\.(?:js|css)")) {
                            response.setHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable");
                        }
                    })
                    .contentSecurityPolicy(csp -> csp.policyDirectives(
                            "default-src 'none'; script-src 'self'; style-src 'self'; " +
                            "img-src 'self' data:; font-src 'self'; connect-src 'self'; " +
                            "manifest-src 'self'; worker-src 'none'; object-src 'none'; " +
                            "base-uri 'none'; frame-ancestors 'none'; form-action 'self'"))
                    .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                    .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy", "camera=(), microphone=(), geolocation=()"))
                    // The HTTPS tunnel terminates TLS; do not trust client-supplied forwarded headers.
                    // Browsers ignore HSTS over plain HTTP on the loopback preview.
                    .httpStrictTransportSecurity(hsts -> hsts.requestMatcher(request -> true)
                            .maxAgeInSeconds(31536000).includeSubDomains(false)));
        }
        return http.build();
    }

}