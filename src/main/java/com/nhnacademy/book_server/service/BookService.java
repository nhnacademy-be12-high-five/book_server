package com.nhnacademy.book_server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.request.BookCreateRequest;
import com.nhnacademy.book_server.dto.request.BookUpdateRequest;
import com.nhnacademy.book_server.dto.response.GetBookResponse;
import com.nhnacademy.book_server.entity.*;
import com.nhnacademy.book_server.feign.OrderFeignClient;
import com.nhnacademy.book_server.mapper.CategoryMapper;
import com.nhnacademy.book_server.parser.ParsingDto;
import com.nhnacademy.book_server.repository.*;
import com.nhnacademy.book_server.repository.review.ReviewRepository;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.Cookie;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.iterators.CartesianProductIterator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cglib.core.Local;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.web.PageableDefault;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.*;
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

    private final OrderFeignClient orderFeignClient;
    private final CategoryRepository categoryRepository;
    private final BookCategoryRepository bookCategoryRepository;

    private final JdbcTemplate jdbcTemplate;


    @Lazy
    @Autowired
    private BookService self;

    public Book createBook(ParsingDto dto) {
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

        Integer matchedId = CategoryMapper.findCategoryId(dto.getTitle());
        Category category = null;
        if (matchedId != null) {
            category = categoryRepository.findByCategoryId(matchedId).orElse(null);
        }


        Book newBook = Book.builder()
                .isbn13(dto.getIsbn())
                .title(dto.getTitle())
                .publisher(publisher)
                .publishedDate(dto.getPubDate())
                .price(parsePrice(dto.getPrice()))
                .image(dto.getImageUrl())
                .content(dto.getDescription())
                .build();

        Book savedBook = bookRepository.save(newBook);

        if (category != null) {
            BookCategory.Pk pk = new BookCategory.Pk(savedBook.getId(), category.getCategoryId());
            BookCategory bookCategory = new BookCategory(pk, savedBook, category);
            bookCategoryRepository.save(bookCategory);
            log.info("저장 완료 : {}",bookCategory);
        }

        if (StringUtils.hasText(dto.getAuthor())) {
            String[] authorNames = dto.getAuthor().split(",");
            for (String name : authorNames) {
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
    // ----------------------------------------------------------------
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
    @Transactional // 💡 트랜잭션 적용
    public BookResponse updateBook(Long id, BookUpdateRequest request) {
        BookResponse existingBook = BookResponse.from(bookRepository.findById(id).orElseThrow(() -> new RuntimeException("아이디가 존재하지 않습니다.")));

        existingBook.price();

        return  existingBook;
    }

    // 책 삭제
    public void deleteBook(Long id, Long memberId) {
        if (!bookRepository.existsById(id)) {
            throw new RuntimeException("삭제할 아이디가 없습니다.");
        }

        bookRepository.deleteById(id);
    }

    private Integer parsePrice(String priceStr) {
        if (!StringUtils.hasText(priceStr)) return 0;
        try {
            return Integer.parseInt(priceStr.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
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

    // 재고 확인 (단순 조회이므로 readOnly)
    @Transactional(readOnly = true)
    public int getBookStock(Long bookId) {
        // 1. 전체 엔티티를 다 가져오는 건 낭비일 수 있음.
        // 단순히 재고만 확인할 거라면 Repository에서 재고 컬럼만 가져오는 쿼리를 짜는 게 성능상 베스트.
        // 하지만 일단 기존 로직을 유지하면서 Service로 옮긴다면:

        return bookRepository.findById(bookId)
                .map(book -> {
                    // 만약 getStockCheckedAt이 Boolean이 아니라 날짜라거나 로직이 있다면 여기서 처리
                    // 예시: 재고 필드가 따로 있다면 book.getStock() 반환
                    boolean inStock = Boolean.TRUE.equals(book.getStockCheckedAt());
                    return inStock ? 1 : 0;
                })
                .orElse(0); // 책이 없으면 재고 0 처리
    }

    public void incrementViewCount(Long bookId) {

////        // Todo 비회원은 쿠키로 저장하는 로직으로 수정
//
//        if (memberId == null) {
//            return;
//        }

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

//    // @Scheduled(cron = "0 0 0 * * *")    // 조회수를 카운트 하는 로직이 매시간 반영

    @Transactional(readOnly = true)
    public List<BookResponse> getWeeklyPopularBooks(int limit) {
        String weeklyKey = "weekly_ranking";

        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);

        List<String> recentKeys = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            String date = LocalDate.now().minusDays(i).format(DateTimeFormatter.BASIC_ISO_DATE);
            recentKeys.add("daily_ranking:" + date);
            log.info("추가됨 : {}",date);
        }

        if (!recentKeys.isEmpty()) {
            // 첫 번째 키를 기준으로 나머지 키들과 합산
            String firstKey = recentKeys.get(0);
            List<String> otherKeys = recentKeys.subList(1, recentKeys.size());

            if (otherKeys.isEmpty()) {
                // 키가 하나뿐이면 그냥 복사하거나 그대로 사용 (여기선 생략 가능하지만 안전하게 복사)
                redisTemplate.opsForZSet().unionAndStore(firstKey, Collections.emptyList(), weeklyKey);
            } else {
                redisTemplate.opsForZSet().unionAndStore(firstKey, otherKeys, weeklyKey);
            }
            // 계산된 키는 10분 정도만 유지 (잦은 연산 방지)
            redisTemplate.expire(weeklyKey, Duration.ofMinutes(10));
        }

        Set<String> topBookIds = redisTemplate.opsForZSet().reverseRange(weeklyKey, 0, limit - 1);

        if (topBookIds == null || topBookIds.isEmpty()) {
            return List.of();
        }

        List<Long> bookIds = topBookIds.stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());

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

        //Redis의 ZSet은 기본적으로 점수가 낮은 순서(오름차순)로 정렬되어 저장되는데
        // zset의 순서를 바꿈

        Set<String> BestBookIds = redisTemplate.opsForZSet().reverseRange("best_seller", 0, limit - 1);

        log.info("Redis에서 가져온 베스트 셀러 ID들: {}", BestBookIds);

        if (BestBookIds == null || BestBookIds.isEmpty()) {
            return List.of();
        }

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
        try {
            redisTemplate.opsForZSet().incrementScore(key, String.valueOf(bookId), quantity.doubleValue());
            log.info("베스트셀러 점수 갱신 완료: bookId={}, quantity={}", bookId, quantity);
        } catch (Exception e) {
            log.error("Redis 점수 갱신 실패 (주문은 계속 진행됨): bookId={}", bookId, e);
        }
    }

    @Transactional(readOnly = true)
    public List<BookResponse> getBooksByCategory(int categoryId) {
        List<BookCategory> books = bookRepository.findBooksByCategoryWithAuthors(categoryId);
        return books.stream()
                .map(bc -> BookResponse.from(bc.getBook()))
                .toList();
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

            for (Book book : targetBooks) {
                Integer matchedId = CategoryMapper.findCategoryId(book.getTitle());

                if (matchedId != null && categoryMap.containsKey(matchedId)) {
                    batchArgs.add(new Object[]{book.getId(), matchedId});
                }

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