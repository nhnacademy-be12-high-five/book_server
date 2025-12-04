package com.nhnacademy.book_server.service.search;

import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.BookSortType;
import com.nhnacademy.book_server.dto.SearchResult;
import com.nhnacademy.book_server.repository.ElasticRepository;
import com.nhnacademy.book_server.service.read.BookReadService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookSearchServiceImplTest {

    @Mock
    ElasticRepository elasticRepository;

    @Mock
    BookReadService bookReadService;

    @Mock
    SearchLogService searchLogService;

    @InjectMocks
    BookSearchServiceImpl bookSearchService;

    private BookResponse createBook(long id, String title) {
        return new BookResponse(
                id,
                title,
                "저자",
                "ISBN",
                10000,
                "image-url",
                1,
                "내용",
                "출판사",
                "2024-01-01",
                4.5,
                10L
        );
    }

    @Test
    @DisplayName("검색어가 비어 있으면 빈 Page 반환 & ES/로그 호출 안 함")
    void searchBooks_whenKeywordBlank_returnsEmptyPage() {
        // given
        String keyword = "   "; // 공백
        int page = 0;
        int size = 10;

        // when
        Page<BookResponse> result = bookSearchService.searchBooks(keyword, BookSortType.POPULAR, page, size);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getPageable()).isEqualTo(PageRequest.of(page, size));

        verifyNoInteractions(elasticRepository);
        verifyNoInteractions(searchLogService);
    }

    @Test
    @DisplayName("검색어가 있으면 로그 기록 후 ES 검색 결과를 Page로 반환")
    void searchBooks_whenKeywordProvided_callsLogAndElastic() {
        // given
        String keyword = "자바";
        int page = 0;
        int size = 10;

        List<BookResponse> books = List.of(
                createBook(1L, "자바 입문"),
                createBook(2L, "스프링과 자바")
        );
        long totalHits = 25L; // ES에서 온 전체 검색 건수

        when(elasticRepository.search(keyword, BookSortType.POPULAR, page, size))
                .thenReturn(new SearchResult<>(books, totalHits));

        // when
        Page<BookResponse> result = bookSearchService.searchBooks(keyword, BookSortType.POPULAR, page, size);

        // then
        // 1) SearchLogService 호출 확인
        verify(searchLogService, times(1)).setSearchLog(keyword);

        // 2) ElasticRepository 호출 파라미터 확인
        verify(elasticRepository, times(1)).search(keyword, BookSortType.POPULAR, page, size);

        // 3) Page 변환 검증
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).title()).isEqualTo("자바 입문");
        assertThat(result.getTotalElements()).isEqualTo(totalHits);
        assertThat(result.getNumber()).isEqualTo(page);
        assertThat(result.getSize()).isEqualTo(size);
    }

    @Test
    @DisplayName("getAllBooks - BookReadService에서 받아온 전체 목록을 페이지로 잘라 반환")
    void getAllBooks_returnsPagedResult() {
        // given
        List<BookResponse> allBooks = List.of(
                createBook(1L, "책1"),
                createBook(2L, "책2"),
                createBook(3L, "책3")
        );
        when(bookReadService.findAllBooks()).thenReturn(allBooks);

        int page = 0;
        int size = 2;

        // when
        Page<BookResponse> result = bookSearchService.getAllBooks(page, size);

        // then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getContent().get(0).id()).isEqualTo(1L);
        assertThat(result.getContent().get(1).id()).isEqualTo(2L);
    }

    @Test
    @DisplayName("getAllBooks - 페이지가 범위를 벗어나면 빈 페이지 반환")
    void getAllBooks_whenPageOutOfRange_returnsEmpty() {
        // given
        List<BookResponse> allBooks = List.of(
                createBook(1L, "책1"),
                createBook(2L, "책2")
        );
        when(bookReadService.findAllBooks()).thenReturn(allBooks);

        int page = 5; // 존재하지 않는 페이지
        int size = 10;

        // when
        Page<BookResponse> result = bookSearchService.getAllBooks(page, size);

        // then
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("getBookById - 도서가 존재하면 반환")
    void getBookById_whenFound_returnsBook() {
        // given
        long id = 1L;
        BookResponse book = createBook(id, "테스트 책");
        when(bookReadService.findBookById(id)).thenReturn(Optional.of(book));

        // when
        BookResponse result = bookSearchService.getBookById(id);

        // then
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(id);
        assertThat(result.title()).isEqualTo("테스트 책");
    }

    @Test
    @DisplayName("getBookById - 도서가 없으면 IllegalArgumentException")
    void getBookById_whenNotFound_throwsException() {
        // given
        long id = 999L;
        when(bookReadService.findBookById(id)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> bookSearchService.getBookById(id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("해당 ID의 도서를 찾을 수 없습니다");
    }
}
