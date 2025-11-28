package com.nhnacademy.book_server.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class GatewayAuthenticationFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String userId = request.getHeader("X-User-Id");
        String role = request.getHeader("X-Role");

        // 헤더에 사용자 ID가 있다면 권한과 가짜 유저 객체 생성해서 인증 토큰을 만들어 그리고 저장소에 '로그인 된 사람'이라고 등록시킴
        // 서비스 코드에서 유저 정보 쉽게 꺼내 쓰게 하기 위해서 설정함
        if(userId != null && !userId.isEmpty()){
            SimpleGrantedAuthority authority = new SimpleGrantedAuthority(role != null ? role : "ROLE_USER");
            User principal = new User(userId, "", Collections.singleton(authority));
            Authentication auth =
                    new UsernamePasswordAuthenticationToken(principal, null, Collections.singleton(authority));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        filterChain.doFilter(request, response);
    }
}
