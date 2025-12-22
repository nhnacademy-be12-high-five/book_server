package com.nhnacademy.book_server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.request.BookCreateRequest;
import com.nhnacademy.book_server.dto.request.BookUpdateRequest;
import com.nhnacademy.book_server.dto.response.GetBookResponse;
import com.nhnacademy.book_server.entity.*;
import com.nhnacademy.book_server.mapper.CategoryMapper;
import com.nhnacademy.book_server.parser.ParsingDto;
import com.nhnacademy.book_server.repository.*;
import com.nhnacademy.book_server.repository.review.BookReviewAiRepository;
import com.nhnacademy.book_server.repository.review.ReviewRepository;
import com.nhnacademy.book_server.service.search.BookSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class BookService {

    private final BookRepository bookRepository;
    private final PublisherRepository publisherRepository;
    private final AuthorRepository authorRepository;
    private final BookAuthorRepository bookAuthorRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ReviewRepository reviewRepository;
    private final BookReviewAiRepository bookReviewAiRepository;
    private final BookSearchService bookSearchService;
    private final MinioImageService minioImageService;
    private final CategoryRepository categoryRepository;

    @Lazy
    @Autowired
    private BookService self;

//    @Transactional
//    public Book createBook(ParsingDto dto) {
//        if (bookRepository.existsByIsbn13(dto.getIsbn())) {
//            log.warn("이미 존재하는 ISBN입니다: {}", dto.getIsbn());
//        }
//
//        Publisher publisher = null;
//        if (StringUtils.hasText(dto.getPublisher())) {
//            String publisherName = dto.getPublisher().trim();
//            publisher = publisherRepository.findByName(publisherName)
//                    .orElseGet(() -> publisherRepository.save(
//                            Publisher.builder().name(publisherName).build()
//                    ));
//        }
//
//        Book newBook = Book.builder()
//                .isbn13(dto.getIsbn())
//                .title(dto.getTitle())
//                .publisher(publisher)
//                .publishedDate(dto.getPubDate() != null ? dto.getPubDate().toString() : null)
//                .price(parsePrice(dto.getPrice()))
//                .image(dto.getImageUrl())
//                .content(dto.getDescription())
//                .build();
//
//        Book savedBook = bookRepository.save(newBook);
//
//        if (StringUtils.hasText(dto.getAuthor())) {
//            String[] authorNames = dto.getAuthor().split(",");
//            for (String name : authorNames) {
//                String trimmedName = name.trim();
//                if (trimmedName.isEmpty()) continue;
//
//                // 작가 조회 없으면 생성
//                Author author = authorRepository.findByName(trimmedName)
//                        .orElseGet(() -> authorRepository.save(
//                                Author.builder().name(trimmedName).build()
//                        ));
//
//                // BookAuthor 연결 관계 저장
//                BookAuthor bookAuthor = BookAuthor.builder()
//                        .book(savedBook)
//                        .author(author)
//                        .build();
//
//                bookAuthorRepository.save(bookAuthor);
//            }
//        }
//
//        return savedBook;
//    }
    public BookResponse createBook(BookCreateRequest request) {
        String imageUrl = null;
        if (StringUtils.hasText(request.getImage())) {
            imageUrl = minioImageService.uploadImageFromUrl(request.getImage(), request.getIsbn());
        }
        Book savedBook = self.createBookInTx(request, imageUrl);
        try {
            bookSearchService.indexBook(savedBook);
        } catch (Exception e) {
            log.error("Elasticsearch 인덱싱 실패 (도서 등록은 성공): {}", savedBook.getId(), e);
        }

        return BookResponse.from(savedBook);
    }

    @Transactional
    public Book createBookInTx(BookCreateRequest request, String uploadedImageUrl) {
        if (bookRepository.existsByIsbn13(request.getIsbn())) {
            throw new IllegalArgumentException("이미 존재하는 ISBN입니다: " + request.getIsbn());
        }

        Publisher publisher = null;
        if (StringUtils.hasText(request.getPublisher())) {
            String publisherName = request.getPublisher().trim();
            publisher = publisherRepository.findByName(publisherName)
                    .orElseGet(() -> publisherRepository.save(Publisher.builder().name(publisherName).build()));
        }

        Integer matchedId = CategoryMapper.findCategoryId(request.getTitle());
        Category category = null;
        if (matchedId != null) {
            category = categoryRepository.findByCategoryId(matchedId).orElse(null);
        }

        Book newBook = Book.builder()
                .isbn13((request.getIsbn()))
                .title(request.getTitle())
                .price(request.getPrice())
                .publisher(publisher)
                .publishedDate(request.getPublishedDate())
                .image(uploadedImageUrl)
                .content(request.getDescription())
                .averageRating(0.0)
                .reviewCount(0)
                .salesVolume(0L)
                .build();

        Book savedBook = bookRepository.save(newBook);

        if (request.getAuthors() != null && !request.getAuthors().isEmpty()) {
            Set<String> requestAuthorNames = request.getAuthors().stream()
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toSet());

            if (!requestAuthorNames.isEmpty()) {
                List<Author> existingAuthors = new ArrayList<>(authorRepository.findByNameIn(requestAuthorNames));
                Set<String> existingAuthorNames = existingAuthors.stream()
                        .map(Author::getName)
                        .collect(Collectors.toSet());

                List<Author> newAuthors = requestAuthorNames.stream()
                        .filter(name -> !existingAuthorNames.contains(name))
                        .map(name -> Author.builder().name(name).build())
                        .toList();

                if (!newAuthors.isEmpty()) {
                    authorRepository.saveAll(newAuthors);
                    existingAuthors.addAll(newAuthors);
                }

                List<BookAuthor> bookAuthors = existingAuthors.stream()
                        .map(author -> BookAuthor.builder()
                                .book(savedBook)
                                .author(author)
                                .build())
                        .toList();

                bookAuthorRepository.saveAll(bookAuthors);
                savedBook.getBookAuthors().addAll(bookAuthors);
            }
        }

        try {
            bookSearchService.indexBook(savedBook);
        }catch (Exception e){
            log.error("Elasticsearch 인덱싱 실패 (도서 등록 성공)", e);
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
    public BookResponse updateBook(Long id, BookUpdateRequest request) {
        Book savedBook = self.updateBookInTx(id, request);
        try {
            bookSearchService.indexBook(savedBook);
        } catch (Exception e) {
            log.error("Elasticsearch 갱신 실패", e);
        }
        String cachedKey = "bookDetail::" + id;
        try {
            redisTemplate.delete(cachedKey);
        } catch (Exception e) {
            log.error("Redis 캐시 삭제 실패", e);
        }

        return BookResponse.from(savedBook);
    }
    @Transactional
    public Book updateBookInTx(Long id, BookUpdateRequest request) {
        log.debug("도서 수정 요청 시작 - ID:{}", id);
        Book existingBook = bookRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("아이디가 존재하지 않습니다."));

        if (request.getPrice() != null) existingBook.setPrice(request.getPrice());
        if (StringUtils.hasText(request.getTitle())) existingBook.setTitle(request.getTitle());
        if (StringUtils.hasText(request.getIsbn())) existingBook.setIsbn13(request.getIsbn());
        if (StringUtils.hasText(request.getDescription())) existingBook.setContent(request.getDescription());
        if (StringUtils.hasText(request.getPublishedDate())) existingBook.setPublishedDate(request.getPublishedDate());

        if (StringUtils.hasText(request.getImage())){
            existingBook.setImage(request.getImage());
        }

        if (request.getAuthors() != null) {
            // 4-1. 기존 저자 연결 끊기 (orphanRemoval=true 설정 시 DB에서도 삭제됨)
            existingBook.getBookAuthors().clear();

            Set<String> requestAuthorNames = request.getAuthors().stream()
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toSet());

            if (!requestAuthorNames.isEmpty()) {
                List<Author> existingAuthors = new ArrayList<>(authorRepository.findByNameIn(requestAuthorNames));
                Set<String> foundAuthorNames = existingAuthors.stream()
                        .map(Author::getName)
                        .collect(Collectors.toSet());

                List<Author> newAuthors = requestAuthorNames.stream()
                        .filter(name -> !foundAuthorNames.contains(name))
                        .map(name -> Author.builder().name(name).build())
                        .toList();

                if (!newAuthors.isEmpty()) {
                    authorRepository.saveAll(newAuthors);
                    existingAuthors.addAll(newAuthors);
                }

                for (Author author : existingAuthors) {
                    BookAuthor bookAuthor = BookAuthor.builder()
                            .book(existingBook)
                            .author(author)
                            .build();

                    existingBook.getBookAuthors().add(bookAuthor);
                }
            }
        }

        return bookRepository.save(existingBook);
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


        Set<String> topBookIds = redisTemplate.opsForZSet().reverseRange(weeklyKey, 0, limit - 1);

        if (topBookIds == null || topBookIds.isEmpty()) {
            return List.of();
        }

        List<Long> bookIds = topBookIds.stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());

        System.out.println("1. Redis 요청 ID 목록: " + bookIds);

        // 2. [수정됨] Redis가 알려준 ID로 DB 조회 (findAllById 사용)
        List<Book> books = bookRepository.findAllById(bookIds);

        System.out.println("2. DB에서 찾은 책 개수: " + books.size());

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
        List<Book> books = bookRepository.findBooksByCategoryWithAuthors(categoryId);
        return books.stream()
                .map(BookResponse::from)
                .toList();
    }
}