package com.nhnacademy.book_server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.book_server.dto.BookInfoDto;
import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.request.BookUpdateRequest;
import com.nhnacademy.book_server.dto.response.GetBookResponse;
import com.nhnacademy.book_server.entity.*;
import com.nhnacademy.book_server.exception.BusinessException;
import com.nhnacademy.book_server.exception.ErrorCode;
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

    public Book createBook(BookInfoDto dto) {
        if (bookRepository.existsByIsbn13(dto.getIsbn())) {
            throw new BusinessException(ErrorCode.BOOK_ALREADY_EXISTS);
        }

        Publisher publisher = null;
        String publisherName = dto.getPublisher().trim();
        publisher = publisherRepository.findByName(publisherName)
                .orElseGet(() -> publisherRepository.save(
                        Publisher.builder().name(publisherName).build()
        ));

        Integer targetCategoryId = dto.getCategoryId();
        Category category = null;

        if (targetCategoryId == null) {
            targetCategoryId = CategoryMapper.findCategoryId(dto.getTitle());
        }

        if (targetCategoryId != null) {
            category = categoryRepository.findByCategoryId(targetCategoryId).orElse(null);
        }

        String publishedDateStr = (dto.getPublishedDate() != null)
                ? dto.getPublishedDate().toString() // "2023-12-25" 형식으로 변환됨
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
            log.info("저장 완료 : {}",bookCategory);
        }

        if (dto.getAuthors() != null && !dto.getAuthors().isEmpty()) {
            for (String name : dto.getAuthors()) {
                String trimmedName = name.trim();
                if (trimmedName.isEmpty()) continue;

                // 작가 조회 없으면 생성
                Author author = authorRepository.findByName(trimmedName)
                        .orElseGet(() -> authorRepository.save(
                                Author.builder().name(trimmedName).build()
                ));

                // BookAuthor 연결 관계 저장
                BookAuthor bookAuthor = BookAuthor.builder()
                        .book(savedBook)
                        .author(author)
                        .build();

                bookAuthorRepository.save(bookAuthor);
            }
        }

        try {
//            em.flush();
            em.refresh(savedBook);
            elasticService.saveAll(List.of(BookResponse.from(savedBook)));
            log.info("Elasticsearch 인덱싱 완료 (작가/카테고리 포함): {}", savedBook.getTitle());
        } catch (Exception e) {
            log.error("Elasticsearch 인덱싱 실패 (DB는 저장됨): {}", e.getMessage());
        }

        return savedBook;
    }

    // 모든 책 조회
    // list -> Pageable로 변환
    @Transactional(readOnly = true)
    public Page<BookResponse> findAllBooks(Pageable pageable) {
        return bookRepository.findAll(pageable)
                .map(BookResponse::from);
    }

    // 책 한권 조회
   // ----------------------------------------------------------------
    // 1. 책 상세 조회 (리팩토링)
    // --------------------------------------------------------------

    @Transactional(readOnly = true)
    public BookResponse findBookById(Long id) {
        // [1] 조회수 증가는 캐싱과 상관없이 무조건 실행 (기존 RedisTemplate 사용)
        incrementViewCount(id);

        // [2] 데이터 조회는 캐시 적용된 메서드 호출
        // 'this.getCache...'가 아니라 'self.getCache...'로 호출해야 프록시(캐시)가 작동함!
        return self.getCachedBookDetail(id);
    }

    // [★핵심] 실제 DB 조회 로직 + 캐싱 적용
    // value = 캐시이름, key = 저장할 키값
    @Cacheable(value = "bookDetail", key = "#id")
    @Transactional(readOnly = true)
    public BookResponse getCachedBookDetail(Long id) {
        log.info("캐시 없음! DB에서 조회합니다. bookId={}", id); // 로그 확인용

        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("책을 찾을 수 없습니다."));

        // 아까 구현하신 AI 요약 + 리뷰 로직
        String aiSummary = bookReviewAiRepository.findByBook_Id(id)
                .map(BookReviewAi::getSummary)
                .orElse(null);

        List<Review> reviews = reviewRepository.findByBookId(id, Pageable.unpaged()).getContent();

        // 어노테이션이 리턴값을 자동으로 JSON 변환해서 Redis에 넣어줍니다.
        return BookResponse.fromWithReviewSummary(book, aiSummary, reviews);
    }

    // ----------------------------------------------------------------
    // 2. 신간 추천 (리팩토링)
    // ----------------------------------------------------------------
    // key를 단순 문자열 'default'로 고정하여 하나의 리스트만 캐싱
    @Cacheable(value = "newBooks", key = "'default'")
    @Transactional(readOnly = true)
    public List<BookResponse> getNewBooks() {
        log.info("캐시 없음! 신간 목록 DB 조회");

        LocalDate start = LocalDate.of(2020, 1, 1);
        LocalDate end = LocalDate.of(2025, 12, 31);

        List<Book> books = bookRepository.findTop5ByOrderByIdDesc();

        return books.stream()
                .map(BookResponse::from)
                .collect(Collectors.toList());
    }

    // 책 업데이트
    @Transactional
    public BookResponse updateBook(Long id, BookUpdateRequest request) {
        // 1. 엔티티 조회 (Entity 상태로 가져와야 Dirty Checking 가능)
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("아이디가 존재하지 않습니다. ID: " + id));

        // 2. 필드 업데이트 (ISBN은 변경하지 않음)
        // Book 엔티티에 Setter나 update 메서드가 있어야 합니다.
        // 예시: Setter 사용 시
        if (StringUtils.hasText(request.getTitle())) book.setTitle(request.getTitle());
        if (StringUtils.hasText(request.getDescription())) book.setContent(request.getDescription()); // description -> content 매핑 주의
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
            log.info("도서 삭제 전 연관 리뷰 {}건 삭제 완료", reviews.size());
        }

        bookRepository.deleteById(id);
        log.info("도서 삭제 완료 - ID: {}", id);
    }

    // bulk api 조회
    // 장바구니에서 책을 조회할때 책을 1번만 호출하도록 하는 API
    // Service Layer
    public List<GetBookResponse> getBooksBulk(List<Long> bookIds) {
        List<Book> books = bookRepository.findAllById(bookIds);

        // List를 Map<BookId, Dto> 형태로 변환
        return books.stream()
                .map(book -> new GetBookResponse(
                        book.getId(),
                        book.getTitle(),
                        book.getPrice(),
                        book.getImage()                // 이미지
                ))
                .collect(Collectors.toList());
    }

    public void incrementViewCount(Long bookId) {

        String logKey = "view_log:" + bookId;

        // B. 일간 랭킹 키: "daily_ranking:20241208" (날짜별로 점수 저장)
        String todayDate = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String dailyRankingKey = "daily_ranking:" + todayDate;

        // 사용자가 현재 조회한 순간부터 00:00 까지
        long secondsUntilMidnight = getSecondsDay();

        Boolean isFirstView = redisTemplate.opsForValue()
                .setIfAbsent(logKey, "1", Duration.ofSeconds(secondsUntilMidnight));

        // E. 오늘 처음 조회한 경우에만 점수 증가
        if (Boolean.TRUE.equals(isFirstView)) {
            redisTemplate.opsForZSet().incrementScore(dailyRankingKey, String.valueOf(bookId), 1.0);

            // 8일뒤 랭킹 키 자동 삭제
            redisTemplate.expire(dailyRankingKey, Duration.ofDays(8));
        }
    }

    private long getSecondsDay() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay();
        return ChronoUnit.SECONDS.between(now, midnight);
    }

    // 주간 인기 급상승 로직
//    // @Scheduled(cron = "0 0 0 * * *")    // 조회수를 카운트 하는 로직이 매시간 반영
    @Transactional(readOnly = true)
    public List<BookResponse> getWeeklyPopularBooks(int limit) {
        String weeklyKey = "weekly_ranking";

        List<String> recentKeys = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            String date = LocalDate.now().minusDays(i).format(DateTimeFormatter.BASIC_ISO_DATE);
            recentKeys.add("daily_ranking:" + date);
            log.info("추가됨 : {}",date);
        }

        if (!recentKeys.isEmpty()) {

            // 1. 레디스 키 생성 -> 과거 7일치의 '일간 랭킹' 키를 생성하기 위해
            // 첫 번째 키를 기준으로 나머지 키들과 합산
            String firstKey = recentKeys.get(0);
            List<String> otherKeys = recentKeys.subList(1, recentKeys.size());

            // 2. 일간 랭킹 ZSet을 연산하여 새로운 키에 점수를 합산
            if (otherKeys.isEmpty()) {
                // 키가 하나뿐이면 그냥 복사하거나 그대로 사용 (여기선 생략 가능하지만 안전하게 복사)
                redisTemplate.opsForZSet().unionAndStore(firstKey, Collections.emptyList(), weeklyKey);
            }

            else {
                redisTemplate.opsForZSet().unionAndStore(firstKey, otherKeys, weeklyKey);
            }
            // 계산된 키는 10분 정도만 유지 (잦은 연산 방지)
            redisTemplate.expire(weeklyKey, Duration.ofMinutes(10));
        }

        // 점수가 높은순으로 10개 가져옴
        Set<String> topBookIds = redisTemplate.opsForZSet().reverseRange(weeklyKey, 0, limit - 1);

        if (topBookIds == null || topBookIds.isEmpty()) {
            return List.of();
        }

        List<Long> bookIds = topBookIds.stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());

        // DB 조회 -> 가져온 아이디들을 이용해 데이터베이스에서 도서 정보 조회함

        // 2. [수정됨] Redis가 알려준 ID로 DB 조회 (findAllById 사용)
        List<Book> books = bookRepository.findAllById(bookIds);


        // 3. Map 변환
        Map<Long, Book> bookMap = books.stream()
                .collect(Collectors.toMap(Book::getId, book -> book));

        // 4. Redis 랭킹 순서대로 정렬해서 반환
        return bookIds.stream()
                .map(bookMap::get)
                .filter(Objects::nonNull)
                .map(BookResponse::from)
                .collect(Collectors.toList());
    }


    @Transactional(readOnly = true)
    public List<BookResponse> getBestSeller(int limit) {
        String cacheKey = "best_seller";

        // Redis의 ZSet은 기본적으로 점수가 낮은 순서(오름차순)로 정렬되어 저장되는데
        // zset의 순서를 바꿈
        Set<String> BestBookIds = redisTemplate.opsForZSet().reverseRange("best_seller", 0, limit - 1);

        if (BestBookIds == null || BestBookIds.isEmpty()) {
            return List.of();
        }

        // 책 아이디를 가져와서
        List<Long> bookIds = BestBookIds.stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());

        // 2. DB에서 책 정보 조회 (순서 보장 안됨)
        List<Book> books = bookRepository.findAllById(bookIds);

        // 3. Redis 랭킹 순서대로 정렬하기 위해 Map 변환
        // Redis 랭킹 순서를 그대로 유지
        Map<Long, Book> bookMap = books.stream()
                .collect(Collectors.toMap(Book::getId, book -> book));

        // 4. 순서대로 매핑하여 반환
        return bookIds.stream()
                .map(bookMap::get)
                .filter(Objects::nonNull) // DB에 삭제된 책이 있을 경우 대비
                .map(BookResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public void incrementBestSellerScore(Long bookId, Integer quantity) {
        String key = "best_seller";

        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOOK_NOT_FOUND));
        book.setSalesVolume(book.getSalesVolume() + quantity);

        try {
            redisTemplate.opsForZSet().incrementScore(key, String.valueOf(bookId), quantity.doubleValue());
            log.info("베스트셀러 점수 갱신 완료: bookId={}, quantity={}", bookId, quantity);
        } catch (Exception e) {
            log.error("Redis 점수 갱신 실패 (주문은 계속 진행됨): bookId={}", bookId, e);
        }
    }

    @Transactional(readOnly = true)
    public Page<BookResponse> getBooksByCategory(int categoryId, Pageable pageable) {
        Page<BookCategory> books = bookRepository.findBooksByCategory(categoryId, pageable);
        return books.map(bc -> BookResponse.from(bc.getBook()));
    }

    // BookService나 도서 등록 로직 내부
    @Transactional
    public void saveBookWithCategory(Long bookId,Integer targetCategoryId) {

        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new RuntimeException("도서를 찾을 수 없습니다. ID: " + bookId));

        // 1. DB에서 카테고리 조회 (API로 미리 넣어둔 데이터)
        Category category = categoryRepository.findByCategoryId(targetCategoryId)
                .orElseThrow(() -> new RuntimeException("데이터를 생성해주세요!"));

        BookCategory.Pk pk = new BookCategory.Pk(bookId, targetCategoryId);

        BookCategory bookCategory = new BookCategory(pk, book, category);
        bookCategoryRepository.save(bookCategory);

    }

    public void unlike(Long bookId, Long memberId) {
        if (memberId == null) throw new RuntimeException("회원 정보가 없습니다.");

        // 존재 여부 확인 후 삭제
        if (bookLikeRepository.existsByBook_IdAndMemberId(bookId, memberId)) {
            bookLikeRepository.deleteByBook_IdAndMemberId(bookId, memberId);
        }

        else {
            throw new RuntimeException("삭제할 좋아요 기록이 없습니다.");
        }
    }

    // 책과 카테고리 아이디로 매핑
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public synchronized void migrateCategories() {
        log.info("============== [마이그레이션 시작] ==============");

        // 1. 카테고리 맵 로딩
        Map<Integer, Integer> categoryMap = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(Category::getCategoryId, Category::getCategoryId));
        log.info("▶ 카테고리 로딩 완료 (총 {}개)", categoryMap.size());

        int totalProcessed = 0;
        int batchSize = 10;
        Long lastId = 0L; // 커서 역할 (마지막으로 조회한 책 ID)

        while (true) {
            // [핵심] pageNumber 대신 lastId를 사용하여 다음 데이터를 가져옵니다.
            // Repository에 findNextBatch 메서드가 필요합니다. (아래 참고)
            PageRequest pageRequest = PageRequest.of(0, batchSize);
            List<Book> targetBooks = bookRepository.findNextBatch(lastId, pageRequest);

            if (targetBooks.isEmpty()) {
                log.info("✅ 더 이상 처리할 도서가 없습니다. (총 {}권 매핑 완료)", totalProcessed);
                break;
            }

            List<Object[]> batchArgs = new ArrayList<>();
            List<BookCategory> bookCategories=new ArrayList<>();

            for (Book book : targetBooks) {
                Integer matchedId = CategoryMapper.findCategoryId(book.getTitle());


                if (matchedId != null && categoryMap.containsKey(matchedId)) {
                    batchArgs.add(new Object[]{book.getId(), matchedId});
                }

                int parentId = CategoryMapper.getParentId(matchedId);

                if (parentId != 0 && categoryMap.containsKey(parentId)) {
                    batchArgs.add(new Object[]{book.getId(), parentId});
                }

//                Integer childId=categoryRepository.findByChildId(matchedId);

                // [핵심] 다음 조회를 위해 마지막 ID를 기억합니다.
                lastId = book.getId();
            }

            // DB 저장 (트랜잭션 없이 JDBC 바로 실행 -> 자동 커밋됨)
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
            } else {
                // 매핑된 게 없어도 lastId가 갱신되었으므로 무한 루프에 빠지지 않습니다.
                log.info("⚠️ 이번 배치({}권)에서는 매칭된 카테고리가 없습니다. (진행 중...)", targetBooks.size());
            }
        }

        log.info("============== [마이그레이션 정상 종료] ==============");
    }
}