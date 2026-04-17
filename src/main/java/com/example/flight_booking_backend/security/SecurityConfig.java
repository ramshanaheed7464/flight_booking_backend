package com.example.flight_booking_backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

        @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
        private String jwkSetUri;

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                http
                                .csrf(csrf -> csrf.disable())
                                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers(HttpMethod.GET, "/api/flights/**").permitAll()
                                                .requestMatchers(HttpMethod.GET, "/api/duffel/flights").permitAll()
                                                .requestMatchers(HttpMethod.POST, "/api/duffel/validate/passport").permitAll()
                                                .requestMatchers(HttpMethod.POST, "/api/duffel/bookings").authenticated()
                                                .requestMatchers(HttpMethod.POST, "/api/bookings/duffel").authenticated()
                                                .requestMatchers(HttpMethod.GET, "/api/locations/cities").permitAll()
                                                .requestMatchers(HttpMethod.POST, "/api/auth/forgot-password")
                                                .permitAll()
                                                .requestMatchers(HttpMethod.POST, "/api/auth/reset-password")
                                                .permitAll()
                                                .requestMatchers(HttpMethod.GET, "/api/booking-options/meals")
                                                .permitAll()
                                                .requestMatchers(HttpMethod.POST, "/api/user/sync").authenticated()
                                                .anyRequest().authenticated())
                                .oauth2ResourceServer(oauth2 -> oauth2
                                                .jwt(jwt -> jwt.jwkSetUri(jwkSetUri)));

                return http.build();
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration config = new CorsConfiguration();
                config.setAllowedOrigins(List.of(
                                "http://localhost:3000",
                                "http://localhost:5173",
                                "https://flight-booking-frontend-hgml.vercel.app"));
                config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                config.setAllowedHeaders(List.of("*"));
                config.setAllowCredentials(true);
                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", config);
                return source;
        }
}