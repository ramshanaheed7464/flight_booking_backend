package com.example.flight_booking_backend.security;

import com.example.flight_booking_backend.service.CustomUserDetailsService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JwtRequestFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtRequestFilter.class);

    private final CustomUserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;

    public JwtRequestFilter(CustomUserDetailsService userDetailsService, JwtUtil jwtUtil) {
        this.userDetailsService = userDetailsService;
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        String method = request.getMethod();
        return path.startsWith("/api/auth/")
                || path.startsWith("/api/auth")
                || method.equals("OPTIONS");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws java.io.IOException, jakarta.servlet.ServletException {

        final String authHeader = request.getHeader("Authorization");
        log.debug("JWT Filter — path: {}, authHeader present: {}", request.getServletPath(), authHeader != null);

        String email = null;
        String jwt = null;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwt = authHeader.substring(7);
            try {
                email = jwtUtil.extractClaims(jwt).getSubject();
                log.debug("JWT Filter — extracted email: {}", email);
            } catch (Exception e) {
                log.warn("JWT Filter — failed to extract claims: {}", e.getMessage());
                chain.doFilter(request, response);
                return;
            }
        }

        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                var userDetails = userDetailsService.loadUserByUsername(email);
                boolean valid = jwtUtil.validateToken(jwt, userDetails.getUsername());
                log.debug("JWT Filter — token valid: {}, authorities: {}", valid, userDetails.getAuthorities());
                if (valid) {
                    UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    token.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(token);
                    log.debug("JWT Filter — authentication set for {}", email);
                }
            } catch (Exception e) {
                log.error("JWT Filter — authentication failed: {}", e.getMessage(), e);
            }
        }

        chain.doFilter(request, response);
    }
}