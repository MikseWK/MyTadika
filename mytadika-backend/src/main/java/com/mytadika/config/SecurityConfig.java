package com.mytadika.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/**", "/error").permitAll()
                .requestMatchers(
                    "/",
                    "/login.html",
                    "/login",
                    "/forgotpassword.html",
                    "/createparentaccount.html",
                    "/resetpassword.html",
                    "/home.html",
                    "/parentprofile.html",
                    "/editparentprofile.html",
                    "/parentchatwithtecher.html",
                    "/teacherclassroom.html",
                    "/parent/**",
                    "/teacher/**",
                    "/admin/**",
                    "/css/**",
                    "/js/**",
                    "/images/**",
                    "/components/**",
                    "/uploads/**",
                    "/favicon.svg",
                    "/test.html")
                .permitAll()
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login.html")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/home.html", true)
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login.html"));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
