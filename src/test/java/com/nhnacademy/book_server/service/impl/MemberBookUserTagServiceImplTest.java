package com.nhnacademy.book_server.service.impl;

import com.nhnacademy.book_server.entity.MemberBookUserTag;
import com.nhnacademy.book_server.repository.MemberBookUserTagRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MemberBookUserTagServiceImplTest {

    @InjectMocks
    private MemberBookUserTagServiceImpl service;

    @Mock
    private MemberBookUserTagRepository repo;

    private final Long memberId = 1L;
    private final Long bookId = 100L;
    private final String validTag = "CART_CANDIDATE"; // UserTagCode에 존재하는 값

    @Test
    @DisplayName("getUserTags: 회원의 책 태그 목록 조회 성공")
    void getUserTags() {
        // Given
        MemberBookUserTag entity = MemberBookUserTag.builder()
                .memberId(memberId)
                .bookId(bookId)
                .tagCode(validTag)
                .build();

        given(repo.findAllByMemberIdAndBookId(memberId, bookId))
                .willReturn(List.of(entity));

        // When
        List<String> tags = service.getUserTags(memberId, bookId);

        // Then
        assertThat(tags).hasSize(1);
        assertThat(tags.get(0)).isEqualTo(validTag);
    }

    @Test
    @DisplayName("getUserTags: 태그가 없을 경우 빈 리스트 반환")
    void getUserTags_Empty() {
        // Given
        given(repo.findAllByMemberIdAndBookId(memberId, bookId))
                .willReturn(Collections.emptyList());

        // When
        List<String> tags = service.getUserTags(memberId, bookId);

        // Then
        assertThat(tags).isEmpty();
    }

    @Test
    @DisplayName("addUserTag: 새로운 태그 추가 성공")
    void addUserTag_Success() {
        // Given
        // 이미 존재하는지 확인 -> false (존재하지 않음)
        given(repo.existsByMemberIdAndBookIdAndTagCode(memberId, bookId, validTag))
                .willReturn(false);

        // When
        service.addUserTag(memberId, bookId, validTag);

        // Then
        // save가 호출되어야 함
        verify(repo).save(any(MemberBookUserTag.class));
    }

    @Test
    @DisplayName("addUserTag: 이미 존재하는 태그라면 저장하지 않음 (Idempotency)")
    void addUserTag_AlreadyExists() {
        // Given
        // 이미 존재함 -> true
        given(repo.existsByMemberIdAndBookIdAndTagCode(memberId, bookId, validTag))
                .willReturn(true);

        // When
        service.addUserTag(memberId, bookId, validTag);

        // Then
        // save가 호출되지 않아야 함
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("addUserTag: 유효하지 않은 태그 코드(Enum 아님)일 경우 예외 발생")
    void addUserTag_InvalidTagCode() {
        // Given
        String invalidTag = "INVALID_TAG_CODE";

        // When & Then
        assertThatThrownBy(() -> service.addUserTag(memberId, bookId, invalidTag))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("허용되지 않은 tagCode");

        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("addUserTag: 태그 코드가 null이거나 비어있을 경우 예외 발생")
    void addUserTag_NullOrEmptyTag() {
        // When & Then
        assertThatThrownBy(() -> service.addUserTag(memberId, bookId, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("비어 있습니다");
        
        assertThatThrownBy(() -> service.addUserTag(memberId, bookId, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("removeUserTag: 태그 삭제 성공")
    void removeUserTag_Success() {
        // When
        service.removeUserTag(memberId, bookId, validTag);

        // Then
        // delete 메서드 호출 확인
        verify(repo).deleteByMemberIdAndBookIdAndTagCode(memberId, bookId, validTag);
    }

    @Test
    @DisplayName("removeUserTag: 유효하지 않은 태그 코드로 삭제 시도 시 예외 발생")
    void removeUserTag_InvalidTagCode() {
        // Given
        String invalidTag = "UNKNOWN";

        // When & Then
        assertThatThrownBy(() -> service.removeUserTag(memberId, bookId, invalidTag))
                .isInstanceOf(IllegalArgumentException.class);

        // 레포지토리 삭제 메서드는 호출되지 않아야 함
        verify(repo, never()).deleteByMemberIdAndBookIdAndTagCode(any(), any(), any());
    }
}