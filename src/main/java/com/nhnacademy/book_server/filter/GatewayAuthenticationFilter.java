package com.nhnacademy.book_server.filter;

import com.nhnacademy.book_server.entity.MemberPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Slf4j
@Component
public class GatewayAuthenticationFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String memberIdStr = request.getHeader("X-User-ID");
        String loginId = request.getHeader("X-Login-ID");
        String role = request.getHeader("X-Role");

        if (memberIdStr != null && !memberIdStr.isEmpty()) {
            String roleName = (role != null) ? role : "USER";
            if (!roleName.startsWith("ROLE_")) {
                roleName = "ROLE_" + roleName;
            }
            SimpleGrantedAuthority authority = new SimpleGrantedAuthority(roleName);
            try {
                Long memberId = Long.parseLong(memberIdStr);
                MemberPrincipal principal = new MemberPrincipal(memberId, loginId, roleName);

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(principal, null, Collections.singleton(authority));

                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (NumberFormatException e) {
                log.error("잘못된 X-User-ID 헤더 형식입니다: {}", memberIdStr);
            }
        }

        filterChain.doFilter(request, response);
    }
}