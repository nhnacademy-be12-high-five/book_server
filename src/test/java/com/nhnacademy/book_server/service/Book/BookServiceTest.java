package com.nhnacademy.book_server.service.Book;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.util.*;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    // 1. 모든 의존성 Mock 선언
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
    @Mock
    private EntityManager em;

    // Redis Operations Mock
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private ZSetOperations<String, String> zSetOperations;

    @InjectMocks
    private BookService bookService;

    @BeforeEach
    void setUp() {
        // RedisTemplate Mock 설정 (Lenient: 사용하지 않는 경우에도 에러 방지)
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        // [핵심] BookService 내부의 'self' 필드에 자기 자신 주입 (프록시 호출 시뮬레이션)
        ReflectionTestUtils.setField(bookService, "self", bookService);
    }

    // ================= 기존 테스트 =================

    @Test
    @DisplayName("도서 삭제 시 연관 데이터(AI 요약, 리뷰)가 함께 삭제되는지 확인")
    void deleteBook_Success() {
        // given
        Long bookId = 1L;
        when(bookRepository.existsById(bookId)).thenReturn(true);
        when(bookReviewAiRepository.findByBook_Id(bookId)).thenReturn(Optional.empty());
        when(reviewRepository.findByBookId(eq(bookId), any())).thenReturn(Page.empty());

        // when
        bookService.deleteBook(bookId);

        // then
        verify(bookReviewAiRepository).findByBook_Id(bookId);
        verify(reviewRepository).findByBookId(eq(bookId), any());
        verify(bookRepository, times(1)).deleteById(bookId);
    }

    @Test
    @DisplayName("카테고리 마이그레이션 - 제목 기반 매칭 및 JDBC 배치 저장 검증")
    void migrateCategories_Success() {
        // given
        Category itSub = mock(Category.class);
        when(itSub.getCategoryId()).thenReturn(10);
        Category itMain = mock(Category.class);
        when(itMain.getCategoryId()).thenReturn(3);

        when(categoryRepository.findAll()).thenReturn(List.of(itSub, itMain));

        Book book = Book.builder().id(1L).title("맛있는 자바 프로그래밍").build();

        // 반복문 제어: 첫 호출엔 도서 반환, 두 번째 호출엔 빈 리스트 반환
        when(bookRepository.findNextBatch(eq(0L), any())).thenReturn(List.of(book));
        when(bookRepository.findNextBatch(eq(1L), any())).thenReturn(Collections.emptyList());

        // when
        bookService.migrateCategories();

        // then
        verify(jdbcTemplate).batchUpdate(contains("INSERT INTO book_category"), any(BatchPreparedStatementSetter.class));
        verify(bookRepository, times(2)).findNextBatch(anyLong(), any());
    }

    @Test
    @DisplayName("도서 상세 정보 조회 - DB 조회 시 AI 요약 포함 확인 (캐시 메서드 직접 호출)")
    void getCachedBookDetail_Success() {
        // given
        Long bookId = 1L;
        Book book = Book.builder()
                .id(bookId)
                .title("테스트 도서")
                .price(15000)
                .build();

        when(bookRepository.findById(bookId)).thenReturn(Optional.of(book));
        when(bookReviewAiRepository.findByBook_Id(bookId))
                .thenReturn(Optional.of(new BookReviewAi(book, "AI 요약입니다.", 1L, 1.5)));
        when(reviewRepository.findByBookId(eq(bookId), any())).thenReturn(Page.empty());

        // when
        BookResponse response = bookService.getCachedBookDetail(bookId);

        // then
        assertNotNull(response);
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
//
//    // ================= 추가된 테스트 (주요 로직 커버) =================
//
//    @Test
//    @DisplayName("도서 생성 - 출판사 및 작가가 없는 경우 새로 생성 후 저장 및 ElasticSearch 인덱싱 확인")
//    void createBook_Success_WithNewPublisherAndAuthor() {
//        // given
//        BookInfoDto dto = new BookInfoDto();
//        dto.setIsbn("9781234567890");
//        dto.setTitle("테스트 신간");
//        dto.setPublisher("새로운 출판사");
//        dto.setAuthors(List.of("새로운 작가"));
//        dto.setPublishedDate(LocalDate.now());
//        dto.setPrice(20000);
//        dto.setDescription("책 설명");
//        // categoryId는 null로 설정 (Mapper 로직 테스트)
//
//        // 1. ISBN 중복 체크 (중복 아님)
//        when(bookRepository.existsByIsbn13(anyString())).thenReturn(false);
//
//        // 2. 출판사 처리 (존재하지 않으므로 저장 로직 실행)
//        when(publisherRepository.findByName(anyString())).thenReturn(Optional.empty());
//        when(publisherRepository.save(any(Publisher.class))).thenAnswer(i -> {
//            Publisher p = i.getArgument(0);
//            // 필요하다면 ID 세팅: ReflectionTestUtils.setField(p, "publisherId", 1L);
//            return p;
//        });
//
//        // 3. 작가 처리 (존재하지 않으므로 저장 로직 실행)
//        when(authorRepository.findByName(anyString())).thenReturn(Optional.empty());
//        when(authorRepository.save(any(Author.class))).thenAnswer(i -> {
//            Author a = i.getArgument(0);
//            return a;
//        });
//
//        // 4. 도서 저장 (ID 생성 시뮬레이션)
//        when(bookRepository.save(any(Book.class))).thenAnswer(i -> {
//            Book book = i.getArgument(0);
//            ReflectionTestUtils.setField(book, "id", 100L);
//            return book;
//        });
//
//        // 5. 카테고리 레포지토리 Stub (Service 로직상 호출될 수 있음)
//        // 혹시 CategoryMapper가 ID를 반환하더라도 DB에 없으면 null 처리되도록 설정
//        lenient().when(categoryRepository.findByCategoryId(anyInt())).thenReturn(Optional.empty());
//
//        // [중요 6] EntityManager Stubbing
//        // em.refresh()는 void 메서드이므로 doNothing()을 사용합니다.
//        doNothing().when(em).refresh(any(Book.class));
//
//        // [중요 7] ElasticService Stubbing
//        // elasticService.saveAll()도 void라면 doNothing, 리턴이 있다면 적절히 처리
//        doNothing().when(elasticService).saveAll(anyList());
//
//        // when
//        Book createdBook = bookService.createBook(dto);
//
//        // then
//        assertNotNull(createdBook);
//        assertEquals(100L, createdBook.getId());
//        assertEquals("테스트 신간", createdBook.getTitle());
//
//        // 검증
//        verify(publisherRepository).save(any(Publisher.class));       // 출판사 저장 호출됨
//        verify(authorRepository).save(any(Author.class));             // 작가 저장 호출됨
//        verify(bookAuthorRepository).save(any(BookAuthor.class));     // 도서-작가 연결 저장 호출됨
//
//        // [중요] 마지막 단계 검증
//        verify(em).refresh(any(Book.class));                          // refresh 호출 확인
//        verify(elasticService).saveAll(anyList());                    // 엘라스틱 인덱싱 호출 확인
//    }

    @Test
    @DisplayName("도서 단건 조회 - 조회수 증가 로직 호출 후 상세 정보 반환 확인")
    void findBookById_Success() {
        // given
        Long bookId = 1L;
        Book book = Book.builder().id(bookId).title("조회된 책").build();

        // 조회수 증가 관련 Mock (Redis)
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        // 상세 조회 관련 Mock (getCachedBookDetail 내부)
        when(bookRepository.findById(bookId)).thenReturn(Optional.of(book));
        when(bookReviewAiRepository.findByBook_Id(bookId)).thenReturn(Optional.empty());
        when(reviewRepository.findByBookId(eq(bookId), any())).thenReturn(Page.empty());

        // when
        BookResponse response = bookService.findBookById(bookId);

        // then
        assertEquals("조회된 책", response.title());
        // 조회수 증가 로직(Redis)이 실행되었는지 검증
        verify(zSetOperations).incrementScore(matches("daily_ranking:.*"), eq(String.valueOf(bookId)), eq(1.0));
    }

    @Test
    @DisplayName("도서 수정 - 변경된 필드 업데이트, Redis 캐시 삭제, ES 업데이트 확인")
    void updateBook_Success() {
        // given
        Long bookId = 1L;
        Book book = Book.builder().id(bookId).title("구버전 제목").price(1000).build();

        BookUpdateRequest request = new BookUpdateRequest();
        ReflectionTestUtils.setField(request, "title", "신버전 제목");
        ReflectionTestUtils.setField(request, "price", 2000);
        ReflectionTestUtils.setField(request, "publisher", "변경 출판사");

        when(bookRepository.findById(bookId)).thenReturn(Optional.of(book));
        when(publisherRepository.findByName("변경 출판사")).thenReturn(Optional.empty());
        when(publisherRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // when
        BookResponse response = bookService.updateBook(bookId, request);

        // then
        assertEquals("신버전 제목", response.title());
        assertEquals(2000, book.getPrice());

        verify(redisTemplate).delete("bookDetail::" + bookId); // 캐시 무효화
        verify(elasticService).saveAll(anyList()); // 검색 엔진 갱신
    }

    @Test
    @DisplayName("조회수 증가 - 오늘 처음 조회 시 Redis 점수 증가")
    void incrementViewCount_FirstView() {
        // given
        Long bookId = 1L;
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        // when
        bookService.incrementViewCount(bookId);

        // then
        verify(zSetOperations).incrementScore(matches("daily_ranking:.*"), eq(String.valueOf(bookId)), eq(1.0));
    }

    @Test
    @DisplayName("조회수 증가 - 이미 조회한 경우 Redis 점수 증가 안함")
    void incrementViewCount_AlreadyViewed() {
        // given
        Long bookId = 1L;
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        // when
        bookService.incrementViewCount(bookId);

        // then
        verify(zSetOperations, never()).incrementScore(anyString(), anyString(), anyDouble());
    }

    @Test
    @DisplayName("주간 인기 도서 - 일간 랭킹 합산(Union) 및 상위 목록 조회 확인")
    void getWeeklyPopularBooks_Success() {
        // given
        // Redis에서 랭킹 ID 반환 (ID: 5, 3)
        Set<String> topIds = new LinkedHashSet<>(List.of("5", "3"));
        when(zSetOperations.reverseRange("weekly_ranking", 0, 9)).thenReturn(topIds);

        Book b5 = Book.builder().id(5L).title("인기1위").build();
        Book b3 = Book.builder().id(3L).title("인기2위").build();

        // DB는 순서 보장 없이 리스트 반환
        when(bookRepository.findAllById(anyList())).thenReturn(List.of(b3, b5));

        // when
        List<BookResponse> result = bookService.getWeeklyPopularBooks(10);

        // then
        assertEquals(2, result.size());
        assertEquals("인기1위", result.get(0).title()); // Redis 순서(5번)가 먼저 와야 함
        assertEquals("인기2위", result.get(1).title());

        verify(zSetOperations).unionAndStore(anyString(), any(List.class), eq("weekly_ranking"));
    }

    @Test
    @DisplayName("베스트셀러 조회 - Redis 순서대로 정렬되어 반환되는지 확인")
    void getBestSeller_Success() {
        // given
        // Redis: 2 -> 1 순서
        Set<String> redisIds = new LinkedHashSet<>(List.of("2", "1"));
        when(zSetOperations.reverseRange("best_seller", 0, 9)).thenReturn(redisIds);

        Book b1 = Book.builder().id(1L).title("책1").build();
        Book b2 = Book.builder().id(2L).title("책2").build();

        when(bookRepository.findAllById(anyList())).thenReturn(List.of(b1, b2));

        // when
        List<BookResponse> result = bookService.getBestSeller(10);

        // then
        assertEquals(2, result.size());
    }
}