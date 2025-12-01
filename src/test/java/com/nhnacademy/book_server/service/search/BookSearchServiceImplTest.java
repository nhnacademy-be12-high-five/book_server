//package com.nhnacademy.book_server.service.search;
//
//import com.nhnacademy.book_server.dto.BookResponse;
//import com.nhnacademy.book_server.dto.BookSortType;
//import com.nhnacademy.book_server.service.read.BookReadService;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.springframework.data.domain.Page;
//import org.springframework.data.domain.PageRequest;
//
//import java.util.List;
//import java.util.Optional;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.Mockito.when;
//
//@ExtendWith(MockitoExtension.class)
//class BookSearchServiceImplTest {
//
//    @Mock
//    BookReadService bookReadService;
//
//    @Mock
//    SearchLogService searchLogService;
//
//    @InjectMocks
//    BookSearchServiceImpl bookSearchService;
//
//    // ==== 공통으로 사용할 테스트용 BookResponse 생성 메서드 ====
//    private BookResponse createBook(
//            Long id,
//            String title,
//            String author,
//            String isbn,
//            Integer price,
//            String content,
//            String publisher,
//            String publishedDate,
//            Double avgRating,
//            Long reviewCount
//    ) {
//        return new BookResponse(
//                id,
//                title,
//                author,
//                isbn,
//                price,
//                "image-url",
//                1,
//                content,
//                publisher,
//                publishedDate,
//                avgRating,
//                reviewCount
//        );
//    }
//
//    @Test
//    @DisplayName("searchBooks – 기본(정렬 null)일 때 검색 키워드가 포함된 도서만 점수 계산 후 리턴")
//    void searchBooks_filtersAndSortsByScore() {
//        // given
//        // keyword: "자바" (Dictionary.expand 에서 원 키워드는 반드시 포함된다고 가정)
//        String keyword = "자바";
//
//        BookResponse book1 = createBook(
//                1L,
//                "자바 입문",             // 제목에 자바 포함
//                "홍길동",
//                "111-1",
//                10000,
//                "자바 완전 정복",        // 내용에도 자바 포함 (=> 최소 2필드 매칭)
//                "한빛",
//                "2024-01-01",
//                4.5,
//                10L
//        );
//
//        BookResponse book2 = createBook(
//                2L,
//                "스프링 부트",           // 제목엔 자바 없음
//                "이몽룡",
//                "222-2",
//                12000,
//                "자바 웹 개발 입문",     // 내용에만 자바 포함 (=> 1필드 매칭)
//                "길벗",
//                "2024-01-02",
//                4.0,
//                5L
//        );
//
//        BookResponse book3 = createBook(
//                3L,
//                "파이썬 자료구조",        // 어디에도 자바 없음
//                "성춘향",
//                "333-3",
//                15000,
//                "자료구조 설명",
//                "위키북스",
//                "2024-01-03",
//                3.0,
//                2L
//        );
//
//        when(bookReadService.findAllBooks())
//                .thenReturn(List.of(book1, book2, book3));
//
//        // when
//        Page<BookResponse> result =
//                bookSearchService.searchBooks(keyword, null, 0, 10);
//
//        // then
//        List<BookResponse> content = result.getContent();
//
//        // 자바가 포함된 책 2권만 나와야 함
//        assertEquals(2, content.size());
//        assertEquals(2, result.getTotalElements());
//
//        // book1 은 제목+내용 모두 매칭, book2는 내용만 매칭 → book1 점수가 더 높다고 가정
//        assertEquals(1L, content.get(0).id());
//        assertEquals(2L, content.get(1).id());
//    }
//
//    @Test
//    @DisplayName("searchBooks – LOW_PRICE 정렬 시 가격 오름차순 정렬")
//    void searchBooks_sortByLowPrice() {
//        // given
//        String keyword = "테스트";
//
//        BookResponse cheap = createBook(
//                1L,
//                "테스트 책 A",
//                "저자A",
//                "111-1",
//                5000,                  // 더 저렴
//                "테스트 내용",
//                "출판사A",
//                "2024-01-01",
//                4.0,
//                3L
//        );
//
//        BookResponse expensive = createBook(
//                2L,
//                "테스트 책 B",
//                "저자B",
//                "222-2",
//                15000,                 // 더 비쌈
//                "테스트 상세 내용",
//                "출판사B",
//                "2024-01-02",
//                4.5,
//                5L
//        );
//
//        when(bookReadService.findAllBooks())
//                .thenReturn(List.of(cheap, expensive));
//
//        // when
//        Page<BookResponse> result =
//                bookSearchService.searchBooks(keyword, BookSortType.LOW_PRICE, 0, 10);
//
//        // then
//        List<BookResponse> content = result.getContent();
//        assertEquals(2, content.size());
//
//        // 가격 오름차순: 5,000 → 15,000
//        assertEquals(5000, content.get(0).price());
//        assertEquals(15000, content.get(1).price());
//    }
//
//    @Test
//    @DisplayName("searchBooks – POPULAR 정렬 시 searchLogService 검색수 기준 정렬")
//    void searchBooks_sortByPopular_usesSearchCount() {
//        // given
//        String keyword = "테스트";
//
//        BookResponse book1 = createBook(
//                1L,
//                "테스트 책 A",
//                "저자A",
//                "111-1",
//                10000,
//                "테스트 내용",
//                "출판사A",
//                "2024-01-01",
//                4.0,
//                3L
//        );
//
//        BookResponse book2 = createBook(
//                2L,
//                "테스트 책 B",
//                "저자B",
//                "222-2",
//                10000,
//                "테스트 내용",
//                "출판사B",
//                "2024-01-01",
//                4.0,
//                3L
//        );
//
//        when(bookReadService.findAllBooks())
//                .thenReturn(List.of(book1, book2));
//
//        // SearchLogService 에서 제목 기준 검색 횟수 리턴하도록 설정
//        when(searchLogService.getSearchCount("테스트 책 A")).thenReturn(5L);
//        when(searchLogService.getSearchCount("테스트 책 B")).thenReturn(10L);
//
//        // when
//        Page<BookResponse> result =
//                bookSearchService.searchBooks(keyword, BookSortType.POPULAR, 0, 10);
//
//        // then
//        List<BookResponse> content = result.getContent();
//        assertEquals(2, content.size());
//
//        // 검색 횟수 10이 더 크므로 book2 가 먼저 와야 함
//        assertEquals(2L, content.get(0).id());
//        assertEquals(1L, content.get(1).id());
//    }
//
//    @Test
//    @DisplayName("getAllBooks – 페이지 범위 내부일 때 subList로 페이징")
//    void getAllBooks_returnsPagedResult() {
//        // given
//        BookResponse b1 = createBook(1L, "책1", "저자1", "111-1",
//                10000, "내용1", "출판사1", "2024-01-01", 4.0, 1L);
//        BookResponse b2 = createBook(2L, "책2", "저자2", "222-2",
//                20000, "내용2", "출판사2", "2024-01-02", 4.0, 2L);
//        BookResponse b3 = createBook(3L, "책3", "저자3", "333-3",
//                30000, "내용3", "출판사3", "2024-01-03", 4.0, 3L);
//
//        when(bookReadService.findAllBooks())
//                .thenReturn(List.of(b1, b2, b3));
//
//        // page=0, size=2 → [b1, b2]
//        PageRequest pageRequest = PageRequest.of(0, 2);
//
//        // when
//        Page<BookResponse> result =
//                bookSearchService.getAllBooks(pageRequest.getPageNumber(), pageRequest.getPageSize());
//
//        // then
//        assertEquals(3, result.getTotalElements());
//        assertEquals(2, result.getContent().size());
//        assertEquals(1L, result.getContent().get(0).id());
//        assertEquals(2L, result.getContent().get(1).id());
//    }
//
//    @Test
//    @DisplayName("getAllBooks – 페이지가 범위를 벗어나면 빈 페이지 반환")
//    void getAllBooks_outOfRangeReturnsEmptyPage() {
//        // given
//        BookResponse b1 = createBook(1L, "책1", "저자1", "111-1",
//                10000, "내용1", "출판사1", "2024-01-01", 4.0, 1L);
//
//        when(bookReadService.findAllBooks())
//                .thenReturn(List.of(b1));
//
//        // page=1, size=10 → from = 10, 전체 1개이므로 out of range
//        Page<BookResponse> result =
//                bookSearchService.getAllBooks(1, 10);
//
//        // then
//        assertTrue(result.getContent().isEmpty());
//        assertEquals(1, result.getTotalElements());
//    }
//
//    @Test
//    @DisplayName("getBookById – 존재하는 도서 ID는 그대로 반환")
//    void getBookById_found() {
//        // given
//        Long id = 1L;
//        BookResponse b1 = createBook(id, "책1", "저자1", "111-1",
//                10000, "내용1", "출판사1", "2024-01-01", 4.0, 1L);
//
//        when(bookReadService.findBookById(id))
//                .thenReturn(Optional.of(b1));
//
//        // when
//        BookResponse result = bookSearchService.getBookById(id);
//
//        // then
//        assertNotNull(result);
//        assertEquals(id, result.id());
//        assertEquals("책1", result.title());
//    }
//
//    @Test
//    @DisplayName("getBookById – 존재하지 않는 도서 ID는 IllegalArgumentException 발생")
//    void getBookById_notFoundThrowsException() {
//        // given
//        Long id = 99L;
//        when(bookReadService.findBookById(id))
//                .thenReturn(Optional.empty());
//
//        // when & then
//        IllegalArgumentException ex = assertThrows(
//                IllegalArgumentException.class,
//                () -> bookSearchService.getBookById(id)
//        );
//
//        assertTrue(ex.getMessage().contains("해당 ID의 도서를 찾을 수 없습니다"));
//    }
//}
