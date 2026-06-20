package com.mytadika.config;

import com.mytadika.security.SupabaseJwtAuthConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Spring Boot never issues tokens here — Supabase Auth does that client-side.
 * This config only verifies the Supabase-issued JWT (as an OAuth2 Resource
 * Server, via JWKS) and maps it to a local Account/role through
 * {@link SupabaseJwtAuthConverter}.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final SupabaseJwtAuthConverter supabaseJwtAuthConverter;
    private final CorsConfigurationSource corsConfigurationSource;

    public SecurityConfig(SupabaseJwtAuthConverter supabaseJwtAuthConverter,
                           CorsConfigurationSource corsConfigurationSource) {
        this.supabaseJwtAuthConverter = supabaseJwtAuthConverter;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/accounts/complete-profile", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(supabaseJwtAuthConverter)));
        return http.build();
    }
}
