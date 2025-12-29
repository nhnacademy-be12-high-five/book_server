package com.nhnacademy.book_server.service.Book;

import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.entity.BookLike;
import com.nhnacademy.book_server.repository.BookLikeRepository;
import com.nhnacademy.book_server.repository.BookRepository;
import com.nhnacademy.book_server.service.BookLikeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookLikeServiceTest {

    @InjectMocks
    private BookLikeService bookLikeService;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private BookLikeRepository bookLikeRepository;

    // 테스트용 더미 Book 객체 생성 헬퍼
    private Book createBook(Long id) {
        return Book.builder()
                .id(id)
                .title("테스트 책")
                .price(10000)
                .isbn13("1234567890123")
                .reviewCount(1)
                .build();
    }

    @Test
    @DisplayName("좋아요 토글 - 실패: 회원 ID가 null일 경우")
    void toggleLike_Fail_MemberIdNull() {
        // given
        Long bookId = 1L;
        Long memberId = null;

        // when & then
        assertThatThrownBy(() -> bookLikeService.toggleLike(bookId, memberId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("회원이 존재하지 않습니다.");
    }

    @Test
    @DisplayName("좋아요 토글 - 실패: 책이 존재하지 않을 경우")
    void toggleLike_Fail_BookNotFound() {
        // given
        Long bookId = 999L;
        Long memberId = 1L;

        given(bookRepository.findById(bookId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> bookLikeService.toggleLike(bookId, memberId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("책의 아이디가 존재하지 않습니다.");
    }

    @Test
    @DisplayName("좋아요 토글 - 성공: 이미 좋아요 상태일 때 (좋아요 취소)")
    void toggleLike_Success_Unlike() {
        // given
        Long bookId = 1L;
        Long memberId = 1L;
        Book book = createBook(bookId);

        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        // 이미 좋아요가 눌러져 있는 상태
        given(bookLikeRepository.existsByBook_IdAndMemberId(bookId, memberId)).willReturn(true);

        // when
        bookLikeService.toggleLike(bookId, memberId);

        // then
        // delete 메서드가 호출되었는지 검증
        verify(bookLikeRepository, times(1)).deleteByBook_IdAndMemberId(bookId, memberId);
        // save 메서드는 호출되지 않아야 함
        verify(bookLikeRepository, never()).save(any(BookLike.class));
    }

    @Test
    @DisplayName("좋아요 토글 - 성공: 좋아요 상태가 아닐 때 (좋아요 추가)")
    void toggleLike_Success_Like() {
        // given
        Long bookId = 1L;
        Long memberId = 1L;
        Book book = createBook(bookId);

        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        // 좋아요가 없는 상태
        given(bookLikeRepository.existsByBook_IdAndMemberId(bookId, memberId)).willReturn(false);

        // when
        bookLikeService.toggleLike(bookId, memberId);

        // then
        // save 메서드가 호출되었는지 검증
        verify(bookLikeRepository, times(1)).save(any(BookLike.class));
        // delete 메서드는 호출되지 않아야 함
        verify(bookLikeRepository, never()).deleteByBook_IdAndMemberId(anyLong(), anyLong());
    }

    @Test
    @DisplayName("마이페이지 - 좋아요 누른 도서 목록 조회 성공")
    void getMyLikedBooks() {
        // given
        Long memberId = 1L;
        Pageable pageable = PageRequest.of(0, 10);
        Book book = createBook(1L);

        // BookLike 객체 생성 (Book과 연관관계 설정)
        BookLike bookLike = BookLike.builder()
                .book(book)
                .memberId(memberId)
                .build();

        Page<BookLike> likePage = new PageImpl<>(List.of(bookLike));

        given(bookLikeRepository.findAllByMemberId(memberId, pageable)).willReturn(likePage);

        // when
        List<BookResponse> result = bookLikeService.getMyLikedBooks(memberId, pageable);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo(book.getTitle());
        assertThat(result.get(0).bookId()).isEqualTo(book.getId());

        verify(bookLikeRepository, times(1)).findAllByMemberId(memberId, pageable);
    }

    @Test
    @DisplayName("마이페이지 - 좋아요 목록이 비어있을 경우")
    void getMyLikedBooks_Empty() {
        // given
        Long memberId = 1L;
        Pageable pageable = PageRequest.of(0, 10);

        Page<BookLike> emptyPage = Page.empty();

        given(bookLikeRepository.findAllByMemberId(memberId, pageable)).willReturn(emptyPage);

        // when
        List<BookResponse> result = bookLikeService.getMyLikedBooks(memberId, pageable);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("좋아요 여부 확인 (isLiked)")
    void isLiked() {
        // given
        Long bookId = 1L;
        Long memberId = 1L;

        given(bookLikeRepository.existsByBook_IdAndMemberId(bookId, memberId)).willReturn(true);

        // when
        boolean result = bookLikeService.isLiked(bookId, memberId);

        // then
        assertThat(result).isTrue();
        verify(bookLikeRepository, times(1)).existsByBook_IdAndMemberId(bookId, memberId);
    }
}