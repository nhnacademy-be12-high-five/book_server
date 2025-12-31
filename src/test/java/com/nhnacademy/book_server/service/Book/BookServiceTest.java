package com.nhnacademy.book_server.service.Book;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.book_server.dto.BookInfoDto;
import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.request.BookUpdateRequest;
import com.nhnacademy.book_server.entity.*;
import com.nhnacademy.book_server.feign.OrderFeignClient;
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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
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
    @Mock private OrderFeignClient orderFeignClient;
    @Mock private CategoryRepository categoryRepository;
    @Mock private BookCategoryRepository bookCategoryRepository;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private EntityManager em;

    // Redis Operations Mock
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private ZSetOperations<String, String> zSetOperations;

    @BeforeEach
    void setUp() {
        // RedisTemplate이 NullPointerException을 뱉지 않도록 lenient 설정
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        // [핵심 1] 프록시 호출 시뮬레이션을 위한 self 주입
        ReflectionTestUtils.setField(bookService, "self", bookService);

        // [핵심 2] EntityManager 주입 (이게 없으면 createBook에서 에러 발생)
        ReflectionTestUtils.setField(bookService, "em", em);
    }

    @Test
    @DisplayName("도서 삭제 시 연관 데이터(AI 요약, 리뷰)가 함께 삭제되는지 확인")
    void deleteBook_Success() {
        // given
        Long bookId = 1L;
        when(bookRepository.existsById(bookId)).thenReturn(true);

        // AI 요약이 존재하는 경우 시뮬레이션
        BookReviewAi mockAi = mock(BookReviewAi.class);
        when(bookReviewAiRepository.findByBook_Id(bookId)).thenReturn(Optional.of(mockAi));

        // 리뷰는 없는 경우로 가정
        when(reviewRepository.findByBookId(eq(bookId), any())).thenReturn(Page.empty());

        // when
        bookService.deleteBook(bookId);

        // then
        verify(bookReviewAiRepository).delete(mockAi); // 삭제 메서드 호출 검증
        verify(bookRepository).deleteById(bookId);
    }

    @Test
    @DisplayName("카테고리 마이그레이션 - JDBC 배치 저장 검증")
    void migrateCategories_Success() {
        // given
        // 실제 객체를 사용하여 Getter 동작 보장
        Category itMain = new Category(3, "IT", 0, 1);
        Category itSub = new Category(10, "Java", 3, 2);

        when(categoryRepository.findAll()).thenReturn(List.of(itSub, itMain));

        Book book = Book.builder().id(1L).title("맛있는 자바 프로그래밍").build();

        // 첫 번째 배치 호출 시 책 반환, 두 번째 호출 시 빈 리스트 반환 (loop 종료)
        when(bookRepository.findNextBatch(eq(0L), any())).thenReturn(List.of(book));
        when(bookRepository.findNextBatch(eq(1L), any())).thenReturn(Collections.emptyList());

        // when
        bookService.migrateCategories();

        // then
        // JDBC Batch Update가 호출되었는지 확인
        verify(jdbcTemplate).batchUpdate(contains("INSERT INTO book_category"), any(BatchPreparedStatementSetter.class));
        verify(bookRepository, times(2)).findNextBatch(anyLong(), any());
    }

    @Test
    @DisplayName("도서 상세 정보 조회 - DB 조회 및 캐싱 메서드 호출 확인")
    void getCachedBookDetail_Success() {
        // given
        Long bookId = 1L;
        Book book = Book.builder()
                .id(bookId)
                .title("테스트 도서")
                .price(15000)
                .publishedDate(LocalDate.now().toString())
                .publisher(new Publisher(1L, "출판사"))
                .build();

        when(bookRepository.findById(bookId)).thenReturn(Optional.of(book));

        // AI 요약 존재 설정
        BookReviewAi aiReview = new BookReviewAi(book, "AI 요약입니다.", 1L, 1.5);
        when(bookReviewAiRepository.findByBook_Id(bookId)).thenReturn(Optional.of(aiReview));

        // 리뷰 페이징 빈 결과
        when(reviewRepository.findByBookId(eq(bookId), any())).thenReturn(Page.empty());

        // when
        BookResponse response = bookService.getCachedBookDetail(bookId);

        // then
        assertNotNull(response);
        assertEquals("테스트 도서", response.title());
        assertEquals("AI 요약입니다.", response.aiReviewSummary());

        verify(bookRepository).findById(bookId);
    }

    @Test
    @DisplayName("좋아요 취소 - 존재할 경우 정상 삭제 확인")
    void unlike_Success() {
        // given
        Long bookId = 1L;
        Long memberId = 100L;
        when(bookLikeRepository.existsByBook_IdAndMemberId(bookId, memberId)).thenReturn(true);

        // when
        bookService.unlike(bookId, memberId);

        // then
        verify(bookLikeRepository).deleteByBook_IdAndMemberId(bookId, memberId);
    }

    @Test
    @DisplayName("도서 생성 - 출판사 및 작가가 없는 경우 새로 생성 후 저장 및 ElasticSearch 인덱싱 확인")
    void createBook_Success_WithNewPublisherAndAuthor() {
        // given
        BookInfoDto dto = new BookInfoDto();
        dto.setIsbn("9781234567890");
        dto.setTitle("테스트 신간");
        dto.setPublisher("새로운 출판사");
        dto.setAuthors(List.of("새로운 작가"));
        dto.setPublishedDate(LocalDate.now());
        dto.setPrice(20000);
        dto.setDescription("책 설명");

        // [중요] 카테고리 매퍼(static method) 호출을 피하기 위해 ID를 직접 설정
        dto.setCategoryId(1);

        // 1. ISBN 중복 체크 (중복 아님)
        when(bookRepository.existsByIsbn13(anyString())).thenReturn(false);

        // 2. 출판사 검색 실패 -> 저장 로직 실행
        when(publisherRepository.findByName(anyString())).thenReturn(Optional.empty());
        when(publisherRepository.save(any(Publisher.class))).thenAnswer(i -> i.getArgument(0));

        // 3. 작가 검색 실패 -> 저장 로직 실행
        when(authorRepository.findByName(anyString())).thenReturn(Optional.empty());
        when(authorRepository.save(any(Author.class))).thenAnswer(i -> i.getArgument(0));

        // 4. Book 저장 (ID 주입 시뮬레이션)
        when(bookRepository.save(any(Book.class))).thenAnswer(i -> {
            Book book = i.getArgument(0);
            ReflectionTestUtils.setField(book, "id", 100L);
            return book;
        });

        // 5. 카테고리 조회 (DTO에 ID를 넣었으므로 Repository가 호출됨)
        Category mockCategory = new Category(1, "IT", 0, 1);
        when(categoryRepository.findByCategoryId(1)).thenReturn(Optional.of(mockCategory));

        // 6. EntityManager & ElasticService Void 메서드 처리
        doNothing().when(em).refresh(any(Book.class));
        doNothing().when(elasticService).saveAll(anyList());

        // when
        Book createdBook = bookService.createBook(dto);

        // then
        assertNotNull(createdBook);
        assertEquals(100L, createdBook.getId());
        assertEquals("테스트 신간", createdBook.getTitle());

        verify(publisherRepository).save(any(Publisher.class));
        verify(authorRepository).save(any(Author.class));
        verify(bookRepository).save(any(Book.class));

        // 카테고리 연관관계 저장 확인
        verify(bookCategoryRepository).save(any(BookCategory.class));

        verify(em).refresh(any(Book.class));
        verify(elasticService).saveAll(anyList());
    }

    @Test
    @DisplayName("도서 단건 조회 - 조회수 증가 로직 호출 후 상세 정보 반환 확인")
    void findBookById_Success() {
        // given
        Long bookId = 1L;
        Book book = Book.builder()
                .id(bookId)
                .title("조회된 책")
                .publishedDate("2023-01-01")
                .publisher(new Publisher(1L, "출판사"))
                .build();

        // 1. Redis 조회수 증가 로직 Mock
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        // 2. 내부 캐시 메서드(getCachedBookDetail) 동작 Mock
        // 실제로는 self.getCachedBookDetail을 호출하므로, self(this) 내부의 repository 호출을 스텁해야 함
        when(bookRepository.findById(bookId)).thenReturn(Optional.of(book));
        when(bookReviewAiRepository.findByBook_Id(bookId)).thenReturn(Optional.empty());
        when(reviewRepository.findByBookId(eq(bookId), any())).thenReturn(Page.empty());

        // when
        BookResponse response = bookService.findBookById(bookId);

        // then
        assertEquals("조회된 책", response.title());
        // Redis ZSet Increment가 호출되었는지 확인 (키 패턴 매칭)
        verify(zSetOperations).incrementScore(matches("daily_ranking:.*"), eq(String.valueOf(bookId)), eq(1.0));
    }

    @Test
    @DisplayName("도서 수정 - 변경된 필드 업데이트, Redis 캐시 삭제, ES 업데이트 확인")
    void updateBook_Success() {
        // given
        Long bookId = 1L;
        Book book = new Book(); // 실제 객체 생성
        ReflectionTestUtils.setField(book, "id", bookId);
        book.setTitle("구버전 제목");
        book.setPrice(1000);
        book.setPublisher(new Publisher(1L, "구 출판사"));

        // 업데이트 요청 객체
        BookUpdateRequest request = new BookUpdateRequest();
        ReflectionTestUtils.setField(request, "title", "신버전 제목");
        ReflectionTestUtils.setField(request, "price", 2000);
        ReflectionTestUtils.setField(request, "publisher", "변경 출판사");

        when(bookRepository.findById(bookId)).thenReturn(Optional.of(book));
        // 변경된 출판사 조회 -> 없으면 저장
        when(publisherRepository.findByName("변경 출판사")).thenReturn(Optional.empty());
        when(publisherRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // ElasticService 업데이트 무시
        doNothing().when(elasticService).saveAll(anyList());

        // when
        BookResponse response = bookService.updateBook(bookId, request);

        // then
        assertEquals("신버전 제목", response.title());
        assertEquals(2000, book.getPrice()); // Dirty Checking을 위한 엔티티 상태 변경 확인

        verify(redisTemplate).delete("bookDetail::" + bookId); // 캐시 삭제 확인
        verify(elasticService).saveAll(anyList());
    }

    @Test
    @DisplayName("조회수 증가 - 오늘 처음 조회 시 Redis 점수 증가")
    void incrementViewCount_FirstView() {
        // given
        Long bookId = 1L;
        // setIfAbsent가 true를 반환하면 처음 조회한 것
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        // when
        bookService.incrementViewCount(bookId);

        // then
        verify(zSetOperations).incrementScore(matches("daily_ranking:.*"), eq(String.valueOf(bookId)), eq(1.0));
        verify(redisTemplate).expire(matches("daily_ranking:.*"), any(Duration.class));
    }

    @Test
    @DisplayName("조회수 증가 - 이미 조회한 경우 Redis 점수 증가 안함")
    void incrementViewCount_AlreadyViewed() {
        // given
        Long bookId = 1L;
        // setIfAbsent가 false를 반환하면 이미 조회한 것
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        // when
        bookService.incrementViewCount(bookId);

        // then
        verify(zSetOperations, never()).incrementScore(anyString(), anyString(), anyDouble());
    }

    @Test
    @DisplayName("주간 랭킹 스케줄러 동작 테스트 - ZSet Union 및 캐시 삭제 확인")
    void updateWeeklyRankingZSet_Success() {
        // given
        // opsForZSet, opsForValue 등이 Mocking 되어 있다고 가정

        // when
        bookService.updateWeeklyRankingZSet();

        // then
        // 1. ZSet 합치기(unionAndStore)가 호출되었는지 검증
        verify(zSetOperations, times(1)).unionAndStore(
                anyString(),    // firstKey
                anyList(),      // otherKeys
                eq("weekly_ranking") // destKey
        );

        // 2. 만료 시간 설정(expire)이 호출되었는지 검증
        verify(redisTemplate, times(1)).expire(eq("weekly_ranking"), any(Duration.class));

        // 3. 기존 조회 캐시 삭제(delete)가 호출되었는지 검증
        verify(redisTemplate, times(1)).delete("weekly_popular_books::default");
    }

    // 2. [수정] 조회 API 테스트: 집계 로직은 검증에서 제외하고 조회만 확인
    @Test
    @DisplayName("주간 인기 도서 조회 성공 테스트")
    void getWeeklyPopularBooks_Success() {
        // given
        int limit = 10;
        String weeklyKey = "weekly_ranking";

        // Redis에서 반환할 도서 ID Set 설정
        Set<String> topBookIds = new LinkedHashSet<>(List.of("1", "2"));
        given(zSetOperations.reverseRange(weeklyKey, 0, limit - 1)).willReturn(topBookIds);

        // DB에서 반환할 Book 객체 설정
        Book book1 = Book.builder().id(1L).title("Book 1").price(1000).build();
        Book book2 = Book.builder().id(2L).title("Book 2").price(2000).build();
        given(bookRepository.findAllById(anyList())).willReturn(List.of(book1, book2));

        // when
        List<BookResponse> result = bookService.getWeeklyPopularBooks(limit);

        // then
//        assertThat(result).
        assertThat(result.get(0).title()).isEqualTo("Book 1");

        // [핵심] 이제는 reverseRange(조회)만 호출되었는지 확인해야 합니다.
        verify(zSetOperations, times(1)).reverseRange(weeklyKey, 0, limit - 1);

        // [중요] 더 이상 unionAndStore(집계)는 호출되지 않아야 합니다. (never() 사용)
        verify(zSetOperations, never()).unionAndStore(anyString(), anyList(), anyString());
    }

    @Test
    @DisplayName("베스트셀러 조회 - Redis 순서대로 정렬되어 반환되는지 확인")
    void getBestSeller_Success() {
        // given
        Set<String> redisIds = new LinkedHashSet<>(List.of("2", "1"));
        when(zSetOperations.reverseRange("best_seller", 0, 9)).thenReturn(redisIds);

        Book b1 = Book.builder().id(1L).title("책1").publisher(new Publisher(1L, "A")).build();
        Book b2 = Book.builder().id(2L).title("책2").publisher(new Publisher(2L, "B")).build();

        // DB 조회는 순서 상관없이 반환됨
        when(bookRepository.findAllById(anyList())).thenReturn(List.of(b1, b2));

        // when
        List<BookResponse> result = bookService.getBestSeller(10);

        // then
        assertEquals(2, result.size());
        assertEquals("책2", result.get(0).title()); // Redis 순서(2 -> 1) 유지 확인
        assertEquals("책1", result.get(1).title());
    }

    @Test
    @DisplayName("베스트셀러 점수 증가 - 성공")
    void incrementBestSellerScore_Success() {
        // given
        Long bookId = 1L;
        Integer quantity = 5;

        // when
        bookService.incrementBestSellerScore(bookId, quantity);

        // then
        // 키값 "best_seller", member "1", score 5.0
        verify(zSetOperations).incrementScore(eq("best_seller"), eq(String.valueOf(bookId)), eq(5.0));
    }

    @Test
    @DisplayName("베스트셀러 점수 증가 - Redis 예외 발생 시 로그만 찍고 정상 종료")
    void incrementBestSellerScore_Exception() {
        // given
        Long bookId = 1L;
        Integer quantity = 5;

        when(zSetOperations.incrementScore(anyString(), anyString(), anyDouble()))
                .thenThrow(new RuntimeException("Redis connection failed"));

        // when
        // 예외가 던져지지 않아야 함 (assertDoesNotThrow)
        assertDoesNotThrow(() -> bookService.incrementBestSellerScore(bookId, quantity));

        // then
        verify(zSetOperations).incrementScore(anyString(), anyString(), anyDouble());
    }

    @Test
    @DisplayName("카테고리별 도서 조회 - 페이지 반환 확인")
    void getBooksByCategory_Success() {
        // given
        int categoryId = 10;
        Pageable pageable = PageRequest.of(0, 10);

        Book book = Book.builder()
                .id(1L)
                .title("카테고리 도서")
                .publisher(new Publisher(1L, "출판사"))
                .build();
        Category category = new Category(10, "소설", 0, 1);

        BookCategory.Pk pk = new BookCategory.Pk(1L, 10);
        BookCategory bookCategory = new BookCategory(pk, book, category);

        Page<BookCategory> pageResult = new PageImpl<>(List.of(bookCategory));

        when(bookRepository.findBooksByCategory(categoryId, pageable)).thenReturn(pageResult);

        // when
        Page<BookResponse> result = bookService.getBooksByCategory(categoryId, pageable);

        // then
        assertEquals(1, result.getTotalElements());
        assertEquals("카테고리 도서", result.getContent().get(0).title());
        verify(bookRepository).findBooksByCategory(categoryId, pageable);
    }

    @Test
    @DisplayName("도서에 카테고리 매핑 저장 - 성공")
    void saveBookWithCategory_Success() {
        // given
        Long bookId = 1L;
        Integer categoryId = 10;

        Book book = Book.builder().id(bookId).title("책").build();
        Category category = new Category(categoryId, "IT", 0, 1);

        when(bookRepository.findById(bookId)).thenReturn(Optional.of(book));
        when(categoryRepository.findByCategoryId(categoryId)).thenReturn(Optional.of(category));

        // when
        bookService.saveBookWithCategory(bookId, categoryId);

        // then
        verify(bookCategoryRepository).save(any(BookCategory.class));
    }

    @Test
    @DisplayName("도서에 카테고리 매핑 저장 - 책이 없을 경우 예외 발생")
    void saveBookWithCategory_BookNotFound() {
        // given
        Long bookId = 999L;
        Integer categoryId = 10;

        when(bookRepository.findById(bookId)).thenReturn(Optional.empty());

        // when & then
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                bookService.saveBookWithCategory(bookId, categoryId));

        assertTrue(ex.getMessage().contains("도서를 찾을 수 없습니다"));
        verify(bookCategoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("도서에 카테고리 매핑 저장 - 카테고리가 없을 경우 예외 발생")
    void saveBookWithCategory_CategoryNotFound() {
        // given
        Long bookId = 1L;
        Integer categoryId = 999;
        Book book = Book.builder().id(bookId).title("책").build();

        when(bookRepository.findById(bookId)).thenReturn(Optional.of(book));
        when(categoryRepository.findByCategoryId(categoryId)).thenReturn(Optional.empty());

        // when & then
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                bookService.saveBookWithCategory(bookId, categoryId));

        assertTrue(ex.getMessage().contains("데이터를 생성해주세요"));
        verify(bookCategoryRepository, never()).save(any());
    }




}