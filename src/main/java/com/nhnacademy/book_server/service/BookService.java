package com.nhnacademy.book_server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.request.BookUpdateRequest;
import com.nhnacademy.book_server.dto.response.GetBookResponse;
import com.nhnacademy.book_server.entity.*;
import com.nhnacademy.book_server.parser.ParsingDto;
import com.nhnacademy.book_server.repository.AuthorRepository;
import com.nhnacademy.book_server.repository.BookAuthorRepository;
import com.nhnacademy.book_server.repository.BookRepository;
import com.nhnacademy.book_server.repository.PublisherRepository;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.Cookie;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cglib.core.Local;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.web.PageableDefault;
import org.springframework.scheduling.annotation.Scheduled;
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
@Transactional
public class BookService {

    private final BookRepository bookRepository;
    private final PublisherRepository publisherRepository;
    private final AuthorRepository authorRepository;
    private final BookAuthorRepository bookAuthorRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

//    @PostConstruct
//    public void initObjectMapper() {
//        objectMapper.registerModule(new JavaTimeModule());
//        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
//    }

    public Book createBook(ParsingDto dto){
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
    public Page<BookResponse> findAllBooks(Pageable pageable){
        return bookRepository.findAll(pageable)
                .map(BookResponse::from);
    }

    // 책 한권 조회
    @Transactional(readOnly = true)
    public BookResponse findBookById(Long id,Long memberId) {

        // 1. [Redis Cache 확인]

        incrementViewCount(id,memberId);

        // 조회 카운트를 위함
        String cacheKey = "book:detail:" + id;
        // 레디스에서 먼저 책의 아이디가 있는지 찾아봄
        String cachedData = redisTemplate.opsForValue().get(cacheKey);

        // 레디스에 있으면 데이터베이스까지 가지 않음
        if (cachedData != null) {
            try {
                // Cache Hit: DB 접근 없이 즉시 반환
                return objectMapper.readValue(cachedData, BookResponse.class);  // json -> java
            } catch (JsonProcessingException e) {
                // 파싱 실패 시 로그만 남기고 DB 조회로 진행 (서비스 장애 방지)
                log.error("Redis Data Parsing Error", e);
            }
        }

        // 레디스에 없으면 데이터베이스에서 책을 찾음
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("책을 찾을 수 없습니다."));
        BookResponse response = BookResponse.from(book);

        // 3. [Redis Cache 저장] (TTL: 30분)
        try {
            String jsonString = objectMapper.writeValueAsString(response);
            // 데이터베이스에서 찾은 데이터를 레디스에 저장 (TTL : 30)
            redisTemplate.opsForValue().set(cacheKey, jsonString, Duration.ofMinutes(30));
        } catch (JsonProcessingException e) {
            log.error("Redis Data Saving Error", e);
        }

        return response;
    }

    // 책 업데이트
    @Transactional // 💡 트랜잭션 적용
    public Book updateBook(Long id, BookUpdateRequest request){
        Book existingBook = bookRepository.findById(id).orElseThrow(()->new RuntimeException("아이디가 존재하지 않습니다."));

        existingBook.setIsbn13(request.getIsbn());
        existingBook.setTitle(request.getTitle());
        existingBook.setContent(request.getDescription());
        existingBook.setPrice(request.getPrice());
        existingBook.setImage(request.getImage());
        existingBook.setPublishedDate(request.getPublishedDate());

        if (StringUtils.hasText(request.getPublisher())) {
            String publisherName = request.getPublisher().trim();
            Publisher publisher = publisherRepository.findByName(publisherName)
                    .orElseGet(() -> publisherRepository.save(
                            Publisher.builder().name(publisherName).build()
                    ));

            existingBook.setPublisher(publisher);
        }

        if (request.getAuthors() != null){
            existingBook.getBookAuthors().clear();

            for (String authorName: request.getAuthors()){
                String trimmedName = authorName.trim();

                if(!StringUtils.hasText(trimmedName)) continue;
                Author author=authorRepository.findByName(authorName).orElseGet(()->authorRepository.save(Author.builder().name(authorName).build()));

                BookAuthor bookAuthor = BookAuthor.builder()
                        .book(existingBook)  // 중요: 현재 책 정보 주입
                        .author(author)      // 중요: 찾은 작가 정보 주입
                        .build();

                existingBook.getBookAuthors().add(bookAuthor);
                bookRepository.save(existingBook);
            }
        }

        return existingBook;
    }

    // 책 삭제
    public void deleteBook(Long id,Long memberId){
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

    public void incrementViewCount(Long bookId, Long memberId) {

//        // Todo 비회원은 쿠키로 저장하는 로직으로 수정
//        Cookie cookie=new Cookie();

        if (memberId == null) {
            return;
        }

        String logKey = "view_log:" + memberId + ":" + bookId;

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

    @Scheduled(cron = "0 0 0 * * *")    // 조회수를 카운트 하는 로직이 매시간 반영
    public void updateWeeklyRanking() {
        String weeklyKey = "weekly_ranking";
        // 1단계: "합쳐야 할 날짜 리스트 뽑기" (Key Collection)
        List<String> keysToUnion = new ArrayList<>();
        LocalDate today = LocalDate.now();

        // 오늘 포함 최근 7일간의 날짜 키 수집
        for (int i = 0; i < 7; i++) {
            String dateStr = today.minusDays(i).format(DateTimeFormatter.BASIC_ISO_DATE);
            keysToUnion.add("daily_ranking:" + dateStr);
        }

        // Redis UNION: 여러 키의 점수를 합산하여 weeklyKey에 저장
        if (!keysToUnion.isEmpty()) {
            redisTemplate.opsForZSet().unionAndStore(
                    keysToUnion.get(0),
                    keysToUnion.subList(1, keysToUnion.size()),
                    weeklyKey
            );
            // 랭킹 키 유효기간 설정 (1일)
            redisTemplate.expire(weeklyKey, Duration.ofDays(1));
        }
        log.info("Weekly popular books updated.");
    }

    @Transactional(readOnly = true)
    public List<BookResponse> getWeeklyPopularBooks() {
        String weeklyKey = "weekly_ranking";

        // 1. 점수가 높은 순(Reverse)으로 상위 5개(0~4) ID 추출
        Set<String> topBookIds = redisTemplate.opsForZSet().reverseRange(weeklyKey, 0, 4);

        if (topBookIds == null || topBookIds.isEmpty()) {
            return List.of();
        }

        List<Long> bookIds = topBookIds.stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());

        // 2. DB에서 책 정보 조회 (순서 보장 안됨)
        List<Book> books = bookRepository.findAllById(bookIds);

        // 3. Redis 랭킹 순서대로 정렬하기 위해 Map 변환
        Map<Long, Book> bookMap = books.stream()
                .collect(Collectors.toMap(Book::getId, book -> book));

        // 4. 순서대로 매핑하여 반환
        return bookIds.stream()
                .map(bookMap::get)
                .filter(Objects::nonNull) // DB에 삭제된 책이 있을 경우 대비
                .map(BookResponse::from)
                .collect(Collectors.toList());
    }


    //신간 추천 로직
    // 매 1일 자정에 신간이 바뀜
    // ex) 오늘이 12월 1일이면 11/1 - 11/30일까지 나온 책중 좋아요 수가 많은 책 추천
    @Transactional(readOnly = true)
//    @Scheduled(cron = "0 0 0 1 * *")
    public List<BookResponse> getNewBooks() {
        String cacheKey = "recommendation:new_books_ids_1_5";

        // 1. Redis에서 먼저 조회
        String cachedData = redisTemplate.opsForValue().get(cacheKey);
        if (StringUtils.hasText(cachedData)) {

            try {
                // 캐시가 있으면 JSON -> List 객체로 변환하여 즉시 반환
                return objectMapper.readValue(cachedData, new TypeReference<List<BookResponse>>() {});
            } catch (JsonProcessingException e) {
                log.error("Redis 파싱 오류, DB에서 다시 조회합니다.", e);
            }
        }

        // 레디스에 없으면 db로 조회
//        LocalDate start=LocalDate.now().withDayOfMonth(1).minusMonths(1);  // 지난 달
//        LocalDate end=start.withDayOfMonth(start.lengthOfMonth());  // 지난달의 마지막 날짜 구하기

        LocalDate start=LocalDate.of(2020,1,1);
        LocalDate end=LocalDate.of(2025,12,31);

        // 시작날짜부터 마지막날짜까지의 책을 찾음
//        List<Book> books = bookRepository.findTop5ByPublishedDateBetweenOrderByPublishedDateDesc(
//                start.toString(),end.toString()
//        );

//        List<Book> books=bookRepository.findTop5ByPublishedDateBetweenOrderByIdAsc(start.toString(),end.toString());

        List<Book> books=bookRepository.findTop5ByOrderByIdAsc();

        List<BookResponse> responses = books.stream()
                .map(BookResponse::from)
                .collect(Collectors.toList());

        // 3. Redis에 저장 (하루 동안 캐시 유지)
        try {
            // 객체 -> json
            String jsonString = objectMapper.writeValueAsString(responses);
            redisTemplate.opsForValue().set(cacheKey, jsonString, Duration.ofDays(1));
        } catch (JsonProcessingException e) {
            log.error("Redis 저장 오류", e);
        }

        return responses; // 데이터 반환
    }
}