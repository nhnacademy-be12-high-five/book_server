package com.nhnacademy.book_server.entity.member;

import com.nhnacademy.book_server.entity.MemberPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

class MemberPrincipalTest {

    @Test
    @DisplayName("MemberPrincipal 생성 및 UserDetails 인터페이스 동작 검증")
    void memberPrincipalTests() {
        // Given
        Long memberId = 1L;
        String loginId = "testUser";
        String role = "ROLE_USER";

        // When
        MemberPrincipal principal = new MemberPrincipal(memberId, loginId, role);

        // Then 1. 기본 필드 검증 (Lombok Getter)
        assertThat(principal.getMemberId()).isEqualTo(memberId);
        assertThat(principal.getLoginId()).isEqualTo(loginId);
        assertThat(principal.getRole()).isEqualTo(role);

        // Then 2. UserDetails 인터페이스 메서드 검증
        // getUsername() -> loginId 반환
        assertThat(principal.getUsername()).isEqualTo(loginId);
        
        // getPassword() -> null 반환 (패스워드 미사용 정책)
        assertThat(principal.getPassword()).isNull();

        // Authorities 검증: role을 기반으로 SimpleGrantedAuthority 생성 여부 확인
        Collection<? extends GrantedAuthority> authorities = principal.getAuthorities();
        assertThat(authorities).hasSize(1);
        assertThat(authorities.iterator().next())
                .isInstanceOf(SimpleGrantedAuthority.class)
                .extracting("authority") // getAuthority() 값 확인
                .isEqualTo(role);

        // 계정 상태 플래그 검증 (모두 true여야 함)
        assertThat(principal.isAccountNonExpired()).isTrue();
        assertThat(principal.isAccountNonLocked()).isTrue();
        assertThat(principal.isCredentialsNonExpired()).isTrue();
        assertThat(principal.isEnabled()).isTrue();
    }
}