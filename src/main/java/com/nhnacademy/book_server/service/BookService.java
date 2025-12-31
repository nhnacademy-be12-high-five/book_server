package com.nhnacademy.book_server.service;

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
import com.nhnacademy.book_server.service.search.ElasticService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class BookService {

    private final BookRepository bookRepository;
    private final PublisherRepository publisherRepository;
    private final AuthorRepository authorRepository;
    private final BookAuthorRepository bookAuthorRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ReviewRepository reviewRepository;
    private final BookReviewAiRepository bookReviewAiRepository;
    private final ElasticService elasticService;
    private final BookLikeRepository bookLikeRepository;

    private final OrderFeignClient orderFeignClient;
    private final CategoryRepository categoryRepository;
    private final BookCategoryRepository bookCategoryRepository;

    @PersistenceContext
    private EntityManager em;

    private final JdbcTemplate jdbcTemplate;

    @Lazy
    @Autowired
    private BookService self;

    // 도서 생성
    public Book createBook(BookInfoDto dto) {
        if (bookRepository.existsByIsbn13(dto.getIsbn())) {
            log.warn("이미 존재하는 ISBN입니다: {}", dto.getIsbn());
        }

        Publisher publisher = null;
        if (StringUtils.hasText(dto.getPublisher())) {
            String publisherName = dto.getPublisher().trim();
            publisher = publisherRepository.findByName(publisherName)
                    .orElseGet(() -> publisherRepository.save(
                            Publisher.builder().name(publisherName).build()
                    ));
        }

        Integer targetCategoryId = dto.getCategoryId();
        Category category = null;

        if (targetCategoryId == null) {
            targetCategoryId = CategoryMapper.findCategoryId(dto.getTitle());
        }
        if (targetCategoryId != null) {
            category = categoryRepository.findByCategoryId(targetCategoryId).orElse(null);
        }

        String publishedDateStr = (dto.getPublishedDate() != null)
                ? dto.getPublishedDate().toString()
                : LocalDate.now().toString();

        Book newBook = Book.builder()
                .isbn13(dto.getIsbn())
                .title(dto.getTitle())
                .publisher(publisher)
                .publishedDate(publishedDateStr)
                .price(dto.getPrice() != null ? dto.getPrice() : 0)
                .image(dto.getImage())
                .content(dto.getDescription())
                .build();

        Book savedBook = bookRepository.save(newBook);

        if (category != null) {
            BookCategory.Pk pk = new BookCategory.Pk(savedBook.getId(), category.getCategoryId());
            BookCategory bookCategory = new BookCategory(pk, savedBook, category);
            bookCategoryRepository.save(bookCategory);
            log.info("저장 완료 : {}", bookCategory);
        }

        if (dto.getAuthors() != null && !dto.getAuthors().isEmpty()) {
            for (String name : dto.getAuthors()) {
                String trimmedName = name.trim();
                if (trimmedName.isEmpty()) continue;

                Author author = authorRepository.findByName(trimmedName)
                        .orElseGet(() -> authorRepository.save(
                                Author.builder().name(trimmedName).build()
                        ));

                BookAuthor bookAuthor = BookAuthor.builder()
                        .book(savedBook)
                        .author(author)
                        .build();

                bookAuthorRepository.save(bookAuthor);
            }
        }

        try {
            em.refresh(savedBook);
            elasticService.saveAll(List.of(BookResponse.from(savedBook)));
            log.info("Elasticsearch 인덱싱 완료: {}", savedBook.getTitle());
        } catch (Exception e) {
            log.error("Elasticsearch 인덱싱 실패: {}", e.getMessage());
        }

        return savedBook;
    }

    // 모든 책 조회
    @Transactional(readOnly = true)
    public Page<BookResponse> findAllBooks(Pageable pageable) {
        return bookRepository.findAll(pageable)
                .map(BookResponse::from);
    }

    // ----------------------------------------------------------------
    // 책 상세 조회
    // ----------------------------------------------------------------
    @Transactional(readOnly = true)
    public BookResponse findBookById(Long id) {
        // [1] 조회수 증가는 캐싱과 무관하게 실행
        incrementViewCount(id);

        // [2] 데이터 조회는 캐시 적용된 메서드 호출 (self proxy 사용)
        return self.getCachedBookDetail(id);
    }

    @Cacheable(value = "bookDetail", key = "#id")
    @Transactional(readOnly = true)
    public BookResponse getCachedBookDetail(Long id) {
        log.info("캐시 없음! DB에서 조회합니다. bookId={}", id);

        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("책을 찾을 수 없습니다."));

        String aiSummary = bookReviewAiRepository.findByBook_Id(id)
                .map(BookReviewAi::getSummary)
                .orElse(null);

        List<Review> reviews = reviewRepository.findByBookId(id, Pageable.unpaged()).getContent();

        return BookResponse.fromWithReviewSummary(book, aiSummary, reviews);
    }

    // ----------------------------------------------------------------
    // 신간 추천
    // ----------------------------------------------------------------
    @Cacheable(value = "newBooks", key = "'default'")
    @Transactional(readOnly = true)
    public List<BookResponse> getNewBooks() {
        log.info("캐시 없음! 신간 목록 DB 조회");
        List<Book> books = bookRepository.findTop5ByOrderByIdDesc();
        return books.stream()
                .map(BookResponse::from)
                .collect(Collectors.toList());
    }

    // 책 업데이트
    @Transactional
    public BookResponse updateBook(Long id, BookUpdateRequest request) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("아이디가 존재하지 않습니다. ID: " + id));

        if (StringUtils.hasText(request.getTitle())) book.setTitle(request.getTitle());
        if (StringUtils.hasText(request.getDescription())) book.setContent(request.getDescription());
        if (request.getPrice() != null) book.setPrice(request.getPrice());
        if (StringUtils.hasText(request.getImage())) book.setImage(request.getImage());
        if (request.getPublishedDate() != null) book.setPublishedDate(request.getPublishedDate().toString());

        if (StringUtils.hasText(request.getPublisher())) {
            Publisher publisher = publisherRepository.findByName(request.getPublisher())
                    .orElseGet(() -> publisherRepository.save(Publisher.builder().name(request.getPublisher()).build()));
            book.setPublisher(publisher);
        }

        redisTemplate.delete("bookDetail::" + id);

        try {
            elasticService.saveAll(List.of(BookResponse.from(book)));
        } catch (Exception e) {
            log.error("Elasticsearch 업데이트 실패: {}", e.getMessage());
        }

        return BookResponse.from(book);
    }

    // 책 삭제
    public void deleteBook(Long id) {
        if (!bookRepository.existsById(id)) {
            throw new RuntimeException("삭제할 아이디가 없습니다.");
        }
        bookReviewAiRepository.findByBook_Id(id)
                .ifPresent(bookReviewAiRepository::delete);

        List<Review> reviews = reviewRepository.findByBookId(id, Pageable.unpaged()).getContent();
        if (!reviews.isEmpty()) {
            reviewRepository.deleteAll(reviews);
        }

        bookRepository.deleteById(id);
        log.info("도서 삭제 완료 - ID: {}", id);
    }

    // Bulk 조회 (장바구니 등)
    public List<GetBookResponse> getBooksBulk(List<Long> bookIds) {
        List<Book> books = bookRepository.findAllById(bookIds);
        return books.stream()
                .map(book -> new GetBookResponse(
                        book.getId(),
                        book.getTitle(),
                        book.getPrice(),
                        book.getImage()
                ))
                .collect(Collectors.toList());
    }

    // ----------------------------------------------------------------
    // 조회수 카운트 & 일간 랭킹
    // ----------------------------------------------------------------
    public void incrementViewCount(Long bookId) {
        // 주의: 이 로직은 bookId 만을 키로 사용하여 사용자 구분 없이 '책 기준' 1회/일 제한처럼 동작합니다.
        // 사용자별(IP/MemberId) 제한이 필요하다면 키에 식별자를 추가해야 합니다.
        String logKey = "view_log:" + bookId;

        String todayDate = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String dailyRankingKey = "daily_ranking:" + todayDate;

        long secondsUntilMidnight = getSecondsDay();

        // 오늘 하루 동안 해당 키에 대한 set이 없었다면(=첫 조회라면) true 반환
        Boolean isFirstView = redisTemplate.opsForValue()
                .setIfAbsent(logKey, "1", Duration.ofSeconds(secondsUntilMidnight));

        if (Boolean.TRUE.equals(isFirstView)) {
            // 일간 랭킹 점수 증가
            redisTemplate.opsForZSet().incrementScore(dailyRankingKey, String.valueOf(bookId), 1.0);
            // 랭킹 키는 8일간 유지 (주간 랭킹 계산용)
            redisTemplate.expire(dailyRankingKey, Duration.ofDays(8));
        }
    }

    private long getSecondsDay() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay();
        return ChronoUnit.SECONDS.between(now, midnight);
    }

    // ----------------------------------------------------------------
    // [수정됨] 주간 인기 도서 (스케줄러와 조회 분리)
    // ----------------------------------------------------------------

    // 1. [스케줄러] 매일 자정, 주간 랭킹 '집계' 및 캐시 초기화
    @Scheduled(cron = "0 0 0 * * *")
    public void updateWeeklyRankingZSet() {
        String weeklyKey = "weekly_ranking";

        // 최근 7일간의 키 생성
        List<String> recentKeys = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            String date = LocalDate.now().minusDays(i).format(DateTimeFormatter.BASIC_ISO_DATE);
            recentKeys.add("daily_ranking:" + date);
        }

        if (!recentKeys.isEmpty()) {
            String firstKey = recentKeys.get(0);
            List<String> otherKeys = recentKeys.subList(1, recentKeys.size());

            // Redis ZSet 합치기 (Union)
            if (otherKeys.isEmpty()) {
                redisTemplate.opsForZSet().unionAndStore(firstKey, Collections.emptyList(), weeklyKey);
            } else {
                redisTemplate.opsForZSet().unionAndStore(firstKey, otherKeys, weeklyKey);
            }

            // 집계된 ZSet 유효기간 설정
            redisTemplate.expire(weeklyKey, Duration.ofHours(26));

            // [중요] 기존 캐시 삭제 -> 다음 조회 시 갱신된 데이터 로드
            redisTemplate.delete("weekly_popular_books::default");
            log.info("주간 랭킹 집계 완료 & 캐시 초기화 실행");
        }
    }

    // 2. [조회 API] 집계된 ZSet을 기반으로 '조회' (@Cacheable 적용)
    @Cacheable(value = "weekly_popular_books", key = "'default'")
    @Transactional(readOnly = true)
    public List<BookResponse> getWeeklyPopularBooks(int limit) {
        String weeklyKey = "weekly_ranking";

        // Redis ZSet에서 상위 ID 조회 (점수 높은 순)
        Set<String> topBookIds = redisTemplate.opsForZSet().reverseRange(weeklyKey, 0, limit - 1);

        if (topBookIds == null || topBookIds.isEmpty()) {
            return List.of();
        }

        List<Long> bookIds = topBookIds.stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());

        // 순서 보장을 위해 공통 메서드 사용
        return getSortedBookResponses(bookIds);
    }


    // ----------------------------------------------------------------
    // 베스트 셀러 (주문 수 기반)
    // ----------------------------------------------------------------
    @Cacheable(value = "best_seller", key = "'default'")
    @Transactional(readOnly = true)
    public List<BookResponse> getBestSeller(int limit) {
        String key = "best_seller";

        Set<String> bestBookIds = redisTemplate.opsForZSet().reverseRange(key, 0, limit - 1);
        log.info("Redis에서 가져온 베스트 셀러 ID들: {}", bestBookIds);

        if (bestBookIds == null || bestBookIds.isEmpty()) {
            return List.of();
        }

        List<Long> bookIds = bestBookIds.stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());

        return getSortedBookResponses(bookIds);
    }

    @Transactional
    public void incrementBestSellerScore(Long bookId, Integer quantity) {
        String key = "best_seller";
        try {
            redisTemplate.opsForZSet().incrementScore(key, String.valueOf(bookId), quantity.doubleValue());
            log.info("베스트셀러 점수 갱신 완료: bookId={}, quantity={}", bookId, quantity);
        } catch (Exception e) {
            log.error("Redis 점수 갱신 실패 (주문은 계속 진행됨): bookId={}", bookId, e);
        }
    }

    // ----------------------------------------------------------------
    // [공통 유틸] ID 리스트 순서대로 BookResponse 반환
    // ----------------------------------------------------------------
    private List<BookResponse> getSortedBookResponses(List<Long> bookIds) {
        // 1. DB 조회 (순서 보장 안됨)
        List<Book> books = bookRepository.findAllById(bookIds);

        // 2. ID를 키로 하는 맵 생성
        Map<Long, Book> bookMap = books.stream()
                .collect(Collectors.toMap(Book::getId, book -> book));

        // 3. bookIds의 순서(랭킹 순서)대로 리스트 재구성
        return bookIds.stream()
                .map(bookMap::get)
                .filter(Objects::nonNull) // DB 삭제된 책 방어
                .map(BookResponse::from)
                .collect(Collectors.toList());
    }

    // ----------------------------------------------------------------
    // 기타 기능 (카테고리, 좋아요, 마이그레이션)
    // ----------------------------------------------------------------
    @Transactional(readOnly = true)
    public Page<BookResponse> getBooksByCategory(int categoryId, Pageable pageable) {
        Page<BookCategory> books = bookRepository.findBooksByCategory(categoryId, pageable);
        return books.map(bc -> BookResponse.from(bc.getBook()));
    }

    @Transactional
    public void saveBookWithCategory(Long bookId, Integer targetCategoryId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new RuntimeException("도서를 찾을 수 없습니다. ID: " + bookId));

        Category category = categoryRepository.findByCategoryId(targetCategoryId)
                .orElseThrow(() -> new RuntimeException("데이터를 생성해주세요!"));

        BookCategory.Pk pk = new BookCategory.Pk(bookId, targetCategoryId);
        BookCategory bookCategory = new BookCategory(pk, book, category);
        bookCategoryRepository.save(bookCategory);
    }

    public void unlike(Long bookId, Long memberId) {
        if (memberId == null) throw new RuntimeException("회원 정보가 없습니다.");
        if (bookLikeRepository.existsByBook_IdAndMemberId(bookId, memberId)) {
            bookLikeRepository.deleteByBook_IdAndMemberId(bookId, memberId);
        } else {
            throw new RuntimeException("삭제할 좋아요 기록이 없습니다.");
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public synchronized void migrateCategories() {
        log.info("============== [마이그레이션 시작] ==============");
        Map<Integer, Integer> categoryMap = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(Category::getCategoryId, Category::getCategoryId));
        log.info("▶ 카테고리 로딩 완료 (총 {}개)", categoryMap.size());

        int totalProcessed = 0;
        int batchSize = 10;
        Long lastId = 0L;

        while (true) {
            PageRequest pageRequest = PageRequest.of(0, batchSize);
            List<Book> targetBooks = bookRepository.findNextBatch(lastId, pageRequest);

            if (targetBooks.isEmpty()) {
                log.info("✅ 더 이상 처리할 도서가 없습니다. (총 {}권 매핑 완료)", totalProcessed);
                break;
            }

            List<Object[]> batchArgs = new ArrayList<>();

            for (Book book : targetBooks) {
                Integer matchedId = CategoryMapper.findCategoryId(book.getTitle());

                if (matchedId != null && categoryMap.containsKey(matchedId)) {
                    batchArgs.add(new Object[]{book.getId(), matchedId});
                }

                int parentId = CategoryMapper.getParentId(matchedId);
                if (parentId != 0 && categoryMap.containsKey(parentId)) {
                    batchArgs.add(new Object[]{book.getId(), parentId});
                }
                lastId = book.getId();
            }

            if (!batchArgs.isEmpty()) {
                try {
                    String sql = "INSERT INTO book_category (book_id, category_id) VALUES (?, ?)";
                    jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                        @Override
                        public void setValues(PreparedStatement ps, int i) throws SQLException {
                            Object[] args = batchArgs.get(i);
                            ps.setLong(1, (Long) args[0]);
                            ps.setInt(2, (Integer) args[1]);
                        }
                        @Override
                        public int getBatchSize() {
                            return batchArgs.size();
                        }
                    });
                    totalProcessed += batchArgs.size();
                    log.info("▷ {}권 저장 성공! (마지막 ID: {}, 누적: {}권)", batchArgs.size(), lastId, totalProcessed);
                } catch (Exception e) {
                    log.error("❌ 저장 중 에러 발생 (계속 진행함): {}", e.getMessage());
                }
            }
        }
        log.info("============== [마이그레이션 정상 종료] ==============");
    }
}