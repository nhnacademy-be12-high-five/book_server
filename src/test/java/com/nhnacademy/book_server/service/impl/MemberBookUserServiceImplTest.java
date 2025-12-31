//package com.nhnacademy.book_server.service.impl;
//
//import com.nhnacademy.book_server.entity.MemberBookUserTag;
//import com.nhnacademy.book_server.repository.MemberBookUserTagRepository;
//// UserTagCode import 필요 (실제 패키지 경로에 맞게 수정해주세요)
//// import com.nhnacademy.book_server.resolver.UserTagCode;
//import com.nhnacademy.book_server.resolver.UserTagCode;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//
//import java.util.Collections;
//import java.util.List;
//
//import static com.nhnacademy.book_server.resolver.UserTagCode.WANT_RECOMMEND;
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.assertj.core.api.Assertions.assertThatThrownBy;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.BDDMockito.given;
//import static org.mockito.Mockito.*;
//
//@ExtendWith(MockitoExtension.class)
//class MemberBookUserTagServiceImplTest {
//
//    @InjectMocks
//    private MemberBookUserTagServiceImpl service;
//
//    @Mock
//    private MemberBookUserTagRepository repository;
//
//    /**
//     * 주의: 테스트에 사용되는 tagCode 문자열("READING")은
//     * 실제 UserTagCode Enum에 정의되어 있어야 테스트가 통과합니다.
//     * 만약 "READING"이 없다면, 실제 존재하는 값(예: "WISH", "DONE" 등)으로 변경해주세요.
//     */
//    private final String VALID_TAG_CODE = "READING";
//    private final Long MEMBER_ID = 1L;
//    private final Long BOOK_ID = 100L;
//
//    @Test
//    @DisplayName("태그 목록 조회 - 성공")
//    void getUserTags_Success() {
//        // given
//        MemberBookUserTag tagEntity = MemberBookUserTag.builder()
//                .memberId(MEMBER_ID)
//                .bookId(BOOK_ID)
//                .tagCode(VALID_TAG_CODE)
//                .build();
//
//        given(repository.findAllByMemberIdAndBookId(MEMBER_ID, BOOK_ID))
//                .willReturn(List.of(tagEntity));
//
//        // when
//        List<String> result = service.getUserTags(MEMBER_ID, BOOK_ID);
//
//        // then
//        assertThat(result).hasSize(1);
//        assertThat(result.get(0)).isEqualTo(VALID_TAG_CODE);
//        verify(repository).findAllByMemberIdAndBookId(MEMBER_ID, BOOK_ID);
//    }
//
//    @Test
//    @DisplayName("태그 목록 조회 - 결과 없음")
//    void getUserTags_Empty() {
//        // given
//        given(repository.findAllByMemberIdAndBookId(MEMBER_ID, BOOK_ID))
//                .willReturn(Collections.emptyList());
//
//        // when
//        List<String> result = service.getUserTags(MEMBER_ID, BOOK_ID);
//
//        // then
//        assertThat(result).isEmpty();
//    }
//
//    @Test
//    @DisplayName("태그 추가 - 성공 (중복 아님)")
//    void addUserTag_Success() {
//        // given
//        // 중복 여부 체크: 중복 아님(false)
//        given(repository.existsByMemberIdAndBookIdAndTagCode(MEMBER_ID, BOOK_ID, VALID_TAG_CODE))
//                .willReturn(false);
//
//        // when
////        service.addUserTag(MEMBER_ID, BOOK_ID, VALID_TAG_CODE);
//
//        // then
//        verify(repository).existsByMemberIdAndBookIdAndTagCode(MEMBER_ID, BOOK_ID, VALID_TAG_CODE);
//        verify(repository).save(any(MemberBookUserTag.class)); // 저장이 호출되었는지 검증
//    }
//
////    @Test
////    @DisplayName("태그 추가 - 이미 존재하는 태그일 경우 무시(저장 안 함)")
////    void addUserTag_Duplicate_Skip() {
////        // given
////        // 중복 여부 체크: 이미 존재함(true)
////        given(repository.existsByMemberIdAndBookIdAndTagCode(MEMBER_ID, BOOK_ID, VALID_TAG_CODE))
////                .willReturn(true);
////
////        // when
////        service.addUserTag(MEMBER_ID, BOOK_ID, VALID_TAG_CODE);
////
////        // then
////        verify(repository).existsByMemberIdAndBookIdAndTagCode(MEMBER_ID, BOOK_ID, VALID_TAG_CODE);
////        verify(repository, never()).save(any(MemberBookUserTag.class)); // 저장이 호출되지 않아야 함
////    }
//
//    @Test
//    @DisplayName("태그 추가 - 유효하지 않은 태그 코드 (Enum에 없음)")
//    void addUserTag_InvalidCode() {
//        // given
//        String invalidCode = "INVALID_RANDOM_CODE_123";
//
//        // UserTagCode.valueOf(invalidCode)에서 예외가 발생할 것이므로
//        // when & then
//        assertThatThrownBy(() -> service.addUserTag(MEMBER_ID, BOOK_ID, invalidCode))
//                .isInstanceOf(IllegalArgumentException.class)
//                .hasMessageContaining("허용되지 않은 tagCode");
//
//        // 리포지토리는 호출되지 않아야 함
//        verify(repository, never()).save(any());
//    }
//
//    @Test
//    @DisplayName("태그 추가 - 태그 코드가 null이거나 비어있음")
//    void addUserTag_NullOrBlank() {
//        // when & then
//        assertThatThrownBy(() -> service.addUserTag(MEMBER_ID, BOOK_ID, ""))
//                .isInstanceOf(IllegalArgumentException.class)
//                .hasMessageContaining("tagCode가 비어 있습니다");
//
//        assertThatThrownBy(() -> service.addUserTag(MEMBER_ID, BOOK_ID, null))
//                .isInstanceOf(IllegalArgumentException.class)
//                .hasMessageContaining("tagCode가 비어 있습니다");
//    }
//
////    @Test
////    @DisplayName("태그 삭제 - 성공")
////    void removeUserTag_Success() {
////        // when
////        service.removeUserTag(MEMBER_ID, BOOK_ID, VALID_TAG_CODE);
////
////        // then
////        verify(repository).deleteByMemberIdAndBookIdAndTagCode(MEMBER_ID, BOOK_ID, VALID_TAG_CODE);
////    }
//
//    @Test
//    @DisplayName("태그 삭제 - 유효하지 않은 태그 코드일 경우 예외")
//    void removeUserTag_InvalidCode() {
//        // given
//        String invalidCode = "WRONG_CODE";
//
//        // when & then
//        assertThatThrownBy(() -> service.removeUserTag(MEMBER_ID, BOOK_ID, invalidCode))
//                .isInstanceOf(IllegalArgumentException.class);
//
//        verify(repository, never()).deleteByMemberIdAndBookIdAndTagCode(any(), any(), any());
//    }
//}