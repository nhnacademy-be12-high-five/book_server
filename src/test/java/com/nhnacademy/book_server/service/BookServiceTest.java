import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.entity.BookReviewAi;
import com.nhnacademy.book_server.entity.Category;
import com.nhnacademy.book_server.feign.OrderFeignClient;
import com.nhnacademy.book_server.repository.*;
import com.nhnacademy.book_server.repository.review.ReviewRepository;
import com.nhnacademy.book_server.service.BookService;
import com.nhnacademy.book_server.service.search.ElasticService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    // 1. 모든 의존성 Mock 선언 (하나라도 빠지면 NPE 발생)
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

    @InjectMocks
    private BookService bookService;

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
        // DB에 IT(10)와 IT대분류(3) 카테고리가 있다고 가정
        Category itSub = mock(Category.class);
        when(itSub.getCategoryId()).thenReturn(10);
        Category itMain = mock(Category.class);
        when(itMain.getCategoryId()).thenReturn(3);

        when(categoryRepository.findAll()).thenReturn(List.of(itSub, itMain));

        // 제목에 '자바'가 포함된 도서 (CategoryMapper에 의해 10번으로 매칭됨)
        Book book = Book.builder().id(1L).title("맛있는 자바 프로그래밍").build();

        // 반복문 탈출을 위해 첫 번째는 도서 반환, 두 번째는 빈 리스트 반환
        when(bookRepository.findNextBatch(eq(0L), any())).thenReturn(List.of(book));
        when(bookRepository.findNextBatch(eq(1L), any())).thenReturn(Collections.emptyList());

        // when
        bookService.migrateCategories();

        // then
        // 1. SQL이 실행되었는지 확인
        verify(jdbcTemplate).batchUpdate(contains("INSERT INTO book_category"), any(BatchPreparedStatementSetter.class));
        // 2. 루프가 정상적으로 돌았는지 확인
        verify(bookRepository, times(2)).findNextBatch(anyLong(), any());
    }

    @Test
    @DisplayName("도서 상세 정보 조회 - DB 조회 시 AI 요약이 포함되는지 확인")
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
                .thenReturn(Optional.of(new BookReviewAi(book, "AI 요약입니다.",1L,1.5)));
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
}
