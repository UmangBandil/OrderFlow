package com.orderflow.common.config;

import com.orderflow.auth.jwt.JwtAuthenticationFilter;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitProperties rateLimitProperties;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, RateLimitProperties rateLimitProperties) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitProperties = resolveProperties(rateLimitProperties);
    }

    private static RateLimitProperties resolveProperties(RateLimitProperties injected) {
        return injected != null ? injected : new RateLimitProperties();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/actuator/health",
                                "/actuator/info")
                        .permitAll()
                        .requestMatchers(
                                HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/refresh")
                        .permitAll()
                        .requestMatchers("/api/v1/admin/**")
                        .hasRole("ADMIN")
                        .requestMatchers("/api/v1/inventory/**")
                        .hasAnyRole("ADMIN", "WAREHOUSE_MANAGER")
                        .requestMatchers("/api/v1/shipments/**")
                        .hasAnyRole("ADMIN", "WAREHOUSE_MANAGER")
                        .requestMatchers("/api/v1/payments/**")
                        .hasAnyRole("ADMIN", "SUPPORT_AGENT", "CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/**")
                        .permitAll()
                        .requestMatchers("/api/v1/cart/**", "/api/v1/orders/**")
                        .authenticated()
                        .requestMatchers("/api/v1/audit-logs/**")
                        .hasRole("ADMIN")
                        .anyRequest()
                        .authenticated())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin())
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true))
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'")))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:3000"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
