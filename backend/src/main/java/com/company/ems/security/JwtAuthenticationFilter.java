package com.company.ems.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final UserAuthorityService authorityService;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, UserAuthorityService authorityService) {
        this.tokenProvider = tokenProvider;
        this.authorityService = authorityService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = tokenProvider.parse(token);
                if (!tokenProvider.isRefreshToken(claims)) {
                    Long userId = Long.valueOf(claims.getSubject());
                    LoginUser user = authorityService.loadByUserId(userId);
                    if (user != null) {
                        List<GrantedAuthority> authorities = new ArrayList<>();
                        user.permissions().forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
                        user.roles().forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(user, null, authorities);
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            } catch (Exception ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
