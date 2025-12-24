//
//package com.nhnacademy.book_server.service;
//
//import com.nhnacademy.book_server.entity.Book;
//import com.nhnacademy.book_server.entity.BookLike;
//import com.nhnacademy.book_server.repository.BookLikeRepository;
//import com.nhnacademy.book_server.repository.BookRepository;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.springframework.data.domain.Page;
//import org.springframework.data.domain.PageImpl;
//import org.springframework.data.domain.PageRequest;
//import org.springframework.data.domain.Pageable;
//
//import java.util.List;
//import java.util.Optional;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.assertj.core.api.Assertions.assertThatThrownBy;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.BDDMockito.given;
//import static org.mockito.Mockito.times;
//import static org.mockito.Mockito.verify;
//
//@ExtendWith(MockitoExtension.class)
//class BookLikeServiceTest {
//
//    @InjectMocks
//    private BookLikeService bookLikeService;
//
//    @Mock
//    private BookRepository bookRepository;
//
//    @Mock
//    private BookLikeRepository bookLikeRepository;
//
//    @Test
//    @DisplayName("좋아요 토글 - 추가 (좋아요가 없을 때)")
//    void toggleLike_Add() {
//        // given
//        Long bookId = 1L;
//        Long memberId = 100L;
//        Book book = Book.builder().id(bookId).build();
//
//        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
//        given(bookLikeRepository.existsByBook_IdAndMemberId(bookId, memberId)).willReturn(false);
//
//        // when
//        bookLikeService.toggleLike(bookId, memberId);
//
//        // then
//        verify(bookLikeRepository, times(1)).save(any(BookLike.class));
//    }
//
//    @Test
//    @DisplayName("좋아요 토글 - 삭제 (좋아요가 이미 있을 때)")
//    void toggleLike_Remove() {
//        // given
//        Long bookId = 1L;
//        Long memberId = 100L;
//        Book book = Book.builder().id(bookId).build();
//
//        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
//        given(bookLikeRepository.existsByBook_IdAndMemberId(bookId, memberId)).willReturn(true);
//
//        // when
//        bookLikeService.toggleLike(bookId, memberId);
//
//        // then
//        verify(bookLikeRepository, times(1)).deleteByBook_IdAndMemberId(bookId, memberId);
//    }
//
//    @Test
//    @DisplayName("좋아요 토글 실패 - 회원이 없을 때 (ID Null)")
//    void toggleLike_Fail_NoMember() {
//        assertThatThrownBy(() -> bookLikeService.toggleLike(1L, null))
//                .isInstanceOf(RuntimeException.class)
//                .hasMessage("회원이 존재하지 않습니다.");
//    }
//
//    @Test
//    @DisplayName("좋아요 토글 실패 - 책이 없을 때")
//    void toggleLike_Fail_NoBook() {
//        Long bookId = 999L;
//        Long memberId = 100L;
//
//        given(bookRepository.findById(bookId)).willReturn(Optional.empty());
//
//        assertThatThrownBy(() -> bookLikeService.toggleLike(bookId, memberId))
//                .isInstanceOf(RuntimeException.class)
//                .hasMessage("책의 아이디가 존재하지 않습니다.");
//    }
//
//    @Test
//    @DisplayName("내 좋아요 목록 조회")
//    void getMyLikedBooksTest() {
//        // given
//        Long memberId = 1L;
//        Pageable pageable = PageRequest.of(0, 10);
//        Book book = Book.builder().id(1L).title("Test Book").price(1000).build();
//        BookLike bookLike = BookLike.builder().book(book).memberId(memberId).build();
//        Page<BookLike> page = new PageImpl<>(List.of(bookLike));
//
//        given(bookLikeRepository.findAllByMemberId(memberId, pageable)).willReturn(page);
//
//        // when
//        List<BookResponse> result = bookLikeService.getMyLikedBooks(memberId, pageable);
//
//        // then
//        assertThat(result).hasSize(1);
//        assertThat(result.get(0).bookId());
//    }
//}