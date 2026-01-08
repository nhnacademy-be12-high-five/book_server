package com.nhnacademy.book_server.service.Tag;

import com.nhnacademy.book_server.dto.request.TagRequest;
import com.nhnacademy.book_server.dto.response.TagResponse;
import com.nhnacademy.book_server.entity.Tag;
import com.nhnacademy.book_server.exception.BusinessException;
import com.nhnacademy.book_server.exception.ErrorCode;
import com.nhnacademy.book_server.repository.TagRepository;
import com.nhnacademy.book_server.service.TagService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @InjectMocks
    private TagService tagService;

    @Mock
    private TagRepository tagRepository;

    @Test
    @DisplayName("태그 생성 성공")
    void createTag_Success() {
        // given
        String tagName = "소설";
        TagRequest request = new TagRequest(tagName); // Record 생성자 가정

        // DB에 저장된 후 반환될 Mock 객체 (ID가 있음)
        Tag savedTag = Tag.builder()
                .tagId(1L)
                .name(tagName)
                .build();

        // 1. 중복 검사: 중복 아님(false) 설정
        given(tagRepository.existsByName(tagName)).willReturn(false);
        // 2. 저장: savedTag 반환 설정
        given(tagRepository.save(any(Tag.class))).willReturn(savedTag);

        // when
        TagResponse response = tagService.createTag(request);

        // then
        assertThat(response.tagId()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo(tagName);

        // 메서드 호출 검증
        verify(tagRepository).existsByName(tagName);
        verify(tagRepository).save(any(Tag.class));
    }

    @Test
    @DisplayName("태그 생성 실패 - 이미 존재하는 태그")
    void createTag_Fail_Duplicate() {
        // given
        String tagName = "이미있는태그";
        TagRequest request = new TagRequest(tagName);

        // 이미 존재함(true) 설정
        given(tagRepository.existsByName(tagName)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> tagService.createTag(request))
                .isInstanceOf(BusinessException.class) // 1. 예외 타입 검증
                .extracting("errorCode")               // 2. BusinessException 내부의 errorCode 필드 추출
                .isEqualTo(ErrorCode.TAG_ALREADY_EXISTS); // 3. 예상되는 에러 코드(Tag 이미 존재)와 비교

        // save 메서드는 호출되지 않아야 함
        verify(tagRepository, never()).save(any(Tag.class));
    }

    @Test
    @DisplayName("태그 단건 조회 성공")
    void getTag_Success() {
        // given
        Long tagId = 1L;
        Tag tag = Tag.builder()
                .tagId(tagId)
                .name("과학")
                .build();

        given(tagRepository.findById(tagId)).willReturn(Optional.of(tag));

        // when
        TagResponse response = tagService.getTag(tagId);

        // then
        assertThat(response.tagId()).isEqualTo(tagId);
        assertThat(response.name()).isEqualTo("과학");
    }

    @Test
    @DisplayName("태그 단건 조회 실패 - 존재하지 않는 ID")
    void getTag_Fail_NotFound() {
        // given
        Long tagId = 999L;
        given(tagRepository.findById(tagId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> tagService.getTag(tagId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("태그가 존재하지 않습니다.");
    }

    @Test
    @DisplayName("모든 태그 목록 조회 성공")
    void getAllTags_Success() {
        // given
        Tag tag1 = Tag.builder().tagId(1L).name("소설").build();
        Tag tag2 = Tag.builder().tagId(2L).name("에세이").build();

        given(tagRepository.findAll()).willReturn(List.of(tag1, tag2));

        // when
        List<TagResponse> responses = tagService.getAllTags();

        // then
        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).name()).isEqualTo("소설");
        assertThat(responses.get(1).name()).isEqualTo("에세이");
    }
}