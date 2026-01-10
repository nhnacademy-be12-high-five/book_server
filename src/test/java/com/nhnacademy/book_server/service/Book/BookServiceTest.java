package com.nhnacademy.book_server.service.Book;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.book_server.dto.BookInfoDto;
import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.request.BookUpdateRequest;
import com.nhnacademy.book_server.dto.response.GetBookResponse;
import com.nhnacademy.book_server.entity.*;
import com.nhnacademy.book_server.feign.OrderFeignClient;
import com.nhnacademy.book_server.mapper.CategoryMapper;
import com.nhnacademy.book_server.repository.*;
import com.nhnacademy.book_server.repository.review.ReviewRepository;
import com.nhnacademy.book_server.service.BookService;
import com.nhnacademy.book_server.service.search.ElasticService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @InjectMocks
    private BookService bookService;

    @Mock private BookRepository bookRepository;
    @Mock private PublisherRepository publisherRepository;
    @Mock private AuthorRepository authorRepository;
    @Mock private BookAuthorRepository bookAuthorRepository;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ObjectMapper objectMapper;
    @Mock private ReviewRepository reviewRepository;
    @Mock private BookReviewAiRepository bookReviewAiRepository;
    @Mock private ElasticService elasticService;
    @Mock private BookLikeRepository bookLikeRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private BookCategoryRepository bookCategoryRepository;
    @Mock private OrderFeignClient orderFeignClient;
    @Mock private EntityManager em;
    @Mock private JdbcTemplate jdbcTemplate;

    // Redis Operations Mocking
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private ZSetOperations<String, String> zSetOperations;

    @BeforeEach
    void setUp() {
        // 1. self 참조 주입 (기존 코드)
        ReflectionTestUtils.setField(bookService, "self", bookService);

        // 2. [추가] EntityManager Mock 강제 주입
        // BookService의 em 필드는 @PersistenceContext 필드 주입 방식이라
        // @InjectMocks가 놓칠 수 있으므로 수동으로 넣어줍니다.
        ReflectionTestUtils.setField(bookService, "em", em);
    }

    // ============================================================
    // 1. 도서 생성 (Create)
    // ============================================================

    @Test
    @DisplayName("도서 생성 성공 - 작가/출판사가 DB에 없어 새로 생성하는 경우")
    void createBook_Success_NewEntities() {
        // given
        BookInfoDto dto = new BookInfoDto();
        dto.setIsbn("9791112223334");
        dto.setTitle("테스트 도서");
        dto.setPublisher("새 출판사");
        dto.setAuthors(List.of("새 작가"));
        dto.setCategoryId(1);
        dto.setPrice(10000);
        dto.setPublishedDate(LocalDate.now());

        Category mockCategory = new Category(1, "IT", 1, 0);
        Publisher mockPublisher =new Publisher(1L,"name");
        Author mockAuthor = Author.builder().id(1L).name("새 작가").build();
        Book savedBook = Book.builder().id(100L).title("테스트 도서").isbn13("9791112223334").publisher(mockPublisher).build();

        given(bookRepository.existsByIsbn13(anyString())).willReturn(false);
        given(publisherRepository.findByName("새 출판사")).willReturn(Optional.empty());
        given(publisherRepository.save(any(Publisher.class))).willReturn(mockPublisher);
        given(categoryRepository.findByCategoryId(1)).willReturn(Optional.of(mockCategory));
        given(authorRepository.findByName("새 작가")).willReturn(Optional.empty());
        given(authorRepository.save(any(Author.class))).willReturn(mockAuthor);
        given(bookRepository.save(any(Book.class))).willReturn(savedBook);

        // when
        Book result = bookService.createBook(dto);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getIsbn13()).isEqualTo("9791112223334");

        verify(bookCategoryRepository).save(any(BookCategory.class));
        verify(bookAuthorRepository).save(any(BookAuthor.class));
        verify(elasticService).saveAll(anyList());
    }

    @Test
    @DisplayName("도서 생성 성공 - 기존 작가/출판사 사용 & 카테고리 자동 매핑")
    void createBook_Success_ExistingEntities_AutoCategory() {
        // given
        BookInfoDto dto = new BookInfoDto();
        dto.setIsbn("9790000100001");
        dto.setTitle("자바의 정석");
        dto.setPublisher("기존 출판사");
        dto.setAuthors(List.of("   ")); // 공백 이름 무시 확인
        dto.setCategoryId(null); // 카테고리 ID 누락 -> 자동 매핑 시도

        Publisher existingPublisher = new Publisher(1L,"test");
        Book savedBook = Book.builder().id(101L).title("자바의 정석").build();
        Category mappedCategory = new Category(10, "Java", 1, 0);

        given(bookRepository.existsByIsbn13(anyString())).willReturn(false);
        given(publisherRepository.findByName("기존 출판사")).willReturn(Optional.of(existingPublisher));
        given(bookRepository.save(any(Book.class))).willReturn(savedBook);
        given(categoryRepository.findByCategoryId(anyInt())).willReturn(Optional.of(mappedCategory));

        // Static Method Mocking (CategoryMapper)
        try (MockedStatic<CategoryMapper> mockedMapper = mockStatic(CategoryMapper.class)) {
            mockedMapper.when(() -> CategoryMapper.findCategoryId("자바의 정석")).thenReturn(10);

            // when
            bookService.createBook(dto);

            // then
            verify(publisherRepository, never()).save(any(Publisher.class)); // 기존 출판사 사용
            verify(authorRepository, never()).findByName(anyString()); // 공백 작가라 로직 수행 안함
            mockedMapper.verify(() -> CategoryMapper.findCategoryId("자바의 정석")); // 매퍼 호출 확인
            verify(bookCategoryRepository).save(any(BookCategory.class));
        }
    }

    // ============================================================
    // 2. 도서 조회 (Read)
    // ============================================================

    @Test
    @DisplayName("도서 상세 조회 - Redis 조회수 증가 및 상세 정보 반환")
    void findBookById_Success() {
        // given
        Long bookId = 1L;
        Book book = Book.builder().id(bookId).title("상세 도서").build();

        // Redis Mock
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).willReturn(true);
        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);

        // DB Mock
        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        given(bookReviewAiRepository.findByBook_Id(bookId)).willReturn(Optional.empty());
        given(reviewRepository.findByBookId(eq(bookId), any())).willReturn(Page.empty());

        // when
        BookResponse response = bookService.findBookById(bookId);

        // then
        assertThat(response.bookId()).isEqualTo(bookId);
        verify(zSetOperations).incrementScore(anyString(), eq(String.valueOf(bookId)), eq(1.0));
    }

    @Test
    @DisplayName("신간 도서 조회 (getNewBooks)")
    void getNewBooks_Success() {
        List<Book> books = List.of(Book.builder().id(1L).title("신간").build());
        given(bookRepository.findTop5ByOrderByIdDesc()).willReturn(books);

        List<BookResponse> result = bookService.getNewBooks();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().title()).isEqualTo("신간");
    }

    @Test
    @DisplayName("주간 인기 도서 조회 (getWeeklyPopularBooks) - Redis 연산 확인")
    void getWeeklyPopularBooks_Success() {
        int limit = 5;
        Set<String> topIds = new LinkedHashSet<>(List.of("10", "20"));

        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(zSetOperations.unionAndStore(anyString(), any(List.class), anyString())).willReturn(2L);
        given(zSetOperations.reverseRange("weekly_ranking", 0, limit - 1)).willReturn(topIds);

        Book b1 = Book.builder().id(10L).title("인기1").build();
        Book b2 = Book.builder().id(20L).title("인기2").build();
        given(bookRepository.findAllById(anyList())).willReturn(List.of(b1, b2));

        List<BookResponse> result = bookService.getWeeklyPopularBooks(limit);

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().bookId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("베스트셀러 조회 - Redis 순서 보장 확인")
    void getBestSeller_Success() {
        // Redis는 ID 순서대로 [3, 1]을 반환한다고 가정
        Set<String> redisIds = new LinkedHashSet<>(List.of("3", "1"));
        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(zSetOperations.reverseRange("best_seller", 0, 2)).willReturn(redisIds);

        Book b1 = Book.builder().id(1L).title("책1").build();
        Book b3 = Book.builder().id(3L).title("책3").build();
        // DB는 순서 보장 없이 리스트 반환
        given(bookRepository.findAllById(anyList())).willReturn(List.of(b1, b3));

        // when
        List<BookResponse> result = bookService.getBestSeller(3);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).bookId()).isEqualTo(3L); // Redis 순서(3) 유지
        assertThat(result.get(1).bookId()).isEqualTo(1L); // Redis 순서(1) 유지
    }

    @Test
    @DisplayName("카테고리별 도서 조회")
    void getBooksByCategory_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Book book = Book.builder().id(1L).title("카테고리 도서").build();
        BookCategory bc = new BookCategory(new BookCategory.Pk(1L, 10), book, new Category());
        Page<BookCategory> page = new PageImpl<>(List.of(bc));

        given(bookRepository.findBooksByCategory(eq(10), any(Pageable.class))).willReturn(page);

        Page<BookResponse> result = bookService.getBooksByCategory(10, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().title()).isEqualTo("카테고리 도서");
    }

    @Test
    @DisplayName("도서 Bulk 조회 - 리스트 변환 확인")
    void getBooksBulk_Success() {
        List<Long> ids = List.of(1L, 2L);
        Book b1 = Book.builder().id(1L).title("A").price(100).image("img1").build();
        Book b2 = Book.builder().id(2L).title("B").price(200).image("img2").build();

        given(bookRepository.findAllById(ids)).willReturn(List.of(b1, b2));

        List<GetBookResponse> result = bookService.getBooksBulk(ids);

        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting("title")
                .containsExactlyInAnyOrder("A", "B");
    }

    // ============================================================
    // 3. 도서 수정 (Update)
    // ============================================================

    @Test
    @DisplayName("도서 수정 - Dirty Checking 및 캐시 삭제 확인")
    void updateBook_Success() {
        Long bookId = 1L;
        Book book = Book.builder().id(bookId).title("구제목").build();

        BookUpdateRequest req = new BookUpdateRequest();
        req.setTitle("신제목");
        req.setPrice(50000);
        req.setDescription("새 내용");
        req.setPublisher("신규 출판사");

        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        // 출판사 처리
        given(publisherRepository.findByName("신규 출판사")).willReturn(Optional.empty());
        given(publisherRepository.save(any(Publisher.class))).willReturn(Publisher.builder().name("신규 출판사").build());

        // when
        BookResponse res = bookService.updateBook(bookId, req);

        // then
        assertThat(res.title()).isEqualTo("신제목");
        assertThat(book.getPrice()).isEqualTo(50000);
        verify(redisTemplate).delete("bookDetail::" + bookId);
        verify(elasticService).saveAll(anyList());
    }

    // ============================================================
    // 4. 도서 삭제 (Delete)
    // ============================================================

    @Test
    @DisplayName("도서 삭제 - 연관 리뷰/AI 요약 삭제 확인")
    void deleteBook_Success() {
        Long bookId = 1L;
        given(bookRepository.existsById(bookId)).willReturn(true);
        given(bookReviewAiRepository.findByBook_Id(bookId)).willReturn(Optional.empty());
        given(reviewRepository.findByBookId(eq(bookId), any())).willReturn(new PageImpl<>(List.of()));

        bookService.deleteBook(bookId);

        verify(bookRepository).deleteById(bookId);
    }

    @Test
    @DisplayName("도서 삭제 실패 - 존재하지 않음")
    void deleteBook_Fail() {
        given(bookRepository.existsById(1L)).willReturn(false);
        assertThrows(RuntimeException.class, () -> bookService.deleteBook(1L));
    }

    // ============================================================
    // 5. 기타 기능 (좋아요, 점수, 마이그레이션)
    // ============================================================

    @Test
    @DisplayName("좋아요 취소 성공")
    void unlike_Success() {
        given(bookLikeRepository.existsByBook_IdAndMemberId(1L, 100L)).willReturn(true);
        bookService.unlike(1L, 100L);
        verify(bookLikeRepository).deleteByBook_IdAndMemberId(1L, 100L);
    }

    @Test
    @DisplayName("좋아요 취소 실패 - 기록 없음")
    void unlike_Fail() {
        given(bookLikeRepository.existsByBook_IdAndMemberId(1L, 100L)).willReturn(false);
        assertThrows(RuntimeException.class, () -> bookService.unlike(1L, 100L));
    }

    @Test
    @DisplayName("도서-카테고리 수동 연결")
    void saveBookWithCategory_Success() {
        Long bookId = 1L;
        Integer catId = 10;
        Book book = Book.builder().id(bookId).build();
        Category cat = new Category(catId, "Test", 1, 0);

        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        given(categoryRepository.findByCategoryId(catId)).willReturn(Optional.of(cat));

        bookService.saveBookWithCategory(bookId, catId);

        verify(bookCategoryRepository).save(any(BookCategory.class));
    }

    @Test
    @DisplayName("카테고리 마이그레이션 - 배치 업데이트 및 루프 확인")
    void migrateCategories_Success() {
        // given
        Category catIT = new Category(10, "IT", 1, 0);
        given(categoryRepository.findAll()).willReturn(List.of(catIT));

        Book book1 = Book.builder().id(100L).title("Java Book").build();
        // 첫 루프: 책 1권 반환
        given(bookRepository.findNextBatch(eq(0L), any(Pageable.class))).willReturn(List.of(book1));
        // 둘째 루프: 빈 리스트 반환 (종료)
        given(bookRepository.findNextBatch(eq(100L), any(Pageable.class))).willReturn(Collections.emptyList());

        try (MockedStatic<CategoryMapper> mockedMapper = mockStatic(CategoryMapper.class)) {
            mockedMapper.when(() -> CategoryMapper.findCategoryId("Java Book")).thenReturn(10);
            mockedMapper.when(() -> CategoryMapper.getParentId(10)).thenReturn(0);

            // when
            bookService.migrateCategories();

            // then
            // JdbcTemplate이 실행되었는지 확인 (DB Insert 발생)
            verify(jdbcTemplate, times(1)).batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));
            // 루프가 정확히 2번 돌았는지 확인
            verify(bookRepository, times(2)).findNextBatch(anyLong(), any(Pageable.class));
        }
    }

    @Test
    @DisplayName("모든 책 조회 - 페이징")
    void findAllBooks_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Book> page = new PageImpl<>(List.of(Book.builder().id(1L).title("A").build()));
        given(bookRepository.findAll(pageable)).willReturn(page);

        Page<BookResponse> result = bookService.findAllBooks(pageable);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }
}