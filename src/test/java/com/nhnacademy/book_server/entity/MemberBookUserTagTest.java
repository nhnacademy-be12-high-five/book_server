package com.nhnacademy.book_server.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MemberBookUserTagTest {

    @Test
    @DisplayName("MemberBookUserTag: 빌더 패턴을 이용한 객체 생성 및 Getter 확인")
    void createMemberBookUserTag() {
        // Given
        Long memberId = 1L;
        Long bookId = 100L;
        String tagCode = "CART_CANDIDATE";

        // When
        MemberBookUserTag tag = MemberBookUserTag.builder()
                .memberId(memberId)
                .bookId(bookId)
                .tagCode(tagCode)
                .build();

        // Then
        // 1. 값들이 정상적으로 들어갔는지 확인
        assertThat(tag.getMemberId()).isEqualTo(memberId);
        assertThat(tag.getBookId()).isEqualTo(bookId);
        assertThat(tag.getTagCode()).isEqualTo(tagCode);
        
        // 2. prePersist 호출 전이므로 createdAt은 null이어야 함 (Builder에서 설정 안 했으므로)
        assertThat(tag.getCreatedAt()).isNull();
    }

    @Test
    @DisplayName("prePersist: createdAt이 null일 때 현재 시간으로 자동 설정")
    void prePersist_SetsCreatedAt() {
        // Given
        MemberBookUserTag tag = MemberBookUserTag.builder()
                .memberId(1L)
                .build();

        // When
        // prePersist 메서드는 package-private이므로 동일 패키지 내 테스트에서 직접 호출 가능합니다.
        tag.prePersist();

        // Then
        assertThat(tag.getCreatedAt()).isNotNull();
        // 현재 시간(또는 그 직전)보다 이후인지 확인하여 값이 설정되었음을 검증
        assertThat(tag.getCreatedAt()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    @DisplayName("prePersist: createdAt이 이미 존재하면 덮어쓰지 않음")
    void prePersist_KeepsExistingCreatedAt() {
        // Given
        LocalDateTime oldTime = LocalDateTime.of(2020, 1, 1, 10, 0);
        MemberBookUserTag tag = MemberBookUserTag.builder()
                .createdAt(oldTime) // 이미 시간이 설정된 상태
                .build();

        // When
        tag.prePersist();

        // Then
        // 값이 변경되지 않고 기존 시간 유지 확인
        assertThat(tag.getCreatedAt()).isEqualTo(oldTime);
    }

    @Test
    @DisplayName("기본 생성자 및 Setter 없는 필드 동작 확인")
    void constructorTest() {
        // Given & When
        MemberBookUserTag tag = new MemberBookUserTag();

        // Then
        // 기본 생성자로 생성 시 필드는 null/0 (primitive wrapper는 null)
        assertThat(tag.getMemberId()).isNull();
        assertThat(tag.getId()).isNull();
    }
}