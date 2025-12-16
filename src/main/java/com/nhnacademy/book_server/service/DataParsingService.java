package com.nhnacademy.book_server.service;

import com.nhnacademy.book_server.entity.Author;
import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.entity.Publisher;
import com.nhnacademy.book_server.parser.ParsingDto;
import com.nhnacademy.book_server.repository.AuthorRepository;
import com.nhnacademy.book_server.repository.BookAuthorRepository;
import com.nhnacademy.book_server.repository.BookRepository;
import com.nhnacademy.book_server.repository.PublisherRepository;
import com.nhnacademy.book_server.service.category.CategoryMappingService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataParsingService {

    private final BookRepository bookRepository;
    private final AuthorRepository authorRepository;
    private final PublisherRepository publisherRepository;
    private final BookAuthorRepository bookAuthorRepository;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;
    private final MinioImageService minioImageService;
    private final CategoryMappingService categoryMappingService;

    private final TransactionalService transactionalService;

    private static final int BATCH_SIZE = 1000;

    @Value("${minio.default-image-url}")
    private String defaultImageUrl;

    /**
     * 파싱된 데이터를 DB에 저장 (Insert + Update)
     */
    public void saveAll(List<ParsingDto> records) {
        if (records == null || records.isEmpty()) return;

        log.info("총 {}건의 데이터 파싱 완료. 데이터 처리(Upsert) 시작...", records.size());

        Set<String> allPublisherNames = new HashSet<>();
        Set<String> allAuthorNames = new HashSet<>();

        // 1. 데이터 수집 (출판사, 작가 이름 모으기)
        for (ParsingDto dto : records) {
            if (StringUtils.hasText(dto.getPublisher())) {
                allPublisherNames.add(dto.getPublisher().trim());
            }
            if (StringUtils.hasText(dto.getAuthor())) {
                for (String authorName : dto.getAuthor().split("[,;]")) {
                    if (StringUtils.hasText(authorName)) {
                        allAuthorNames.add(authorName.trim());
                    }
                }
            }
        }

        // 2. 출판사/작가 처리 (별도 트랜잭션으로 미리 확보하여 영속화)
        //    -> 이후 로직에서 조회 시 확실하게 DB에 존재하도록 보장
        Map<String, Publisher> publisherMap = transactionalService.executeInNewTransaction(
                () -> resolvePublishers(allPublisherNames)
        );
        Map<String, Author> authorMap = transactionalService.executeInNewTransaction(
                () -> resolveAuthors(allAuthorNames)
        );

        // 3. 책 데이터 배치 처리 (Insert + Update)
        saveBooksInBatch_JDBC(records, publisherMap, authorMap);
    }

    /**
     * 🚀 [최종 최적화] JDBC Upsert (Insert + Update) + 예외 방어 로직
     */
    private void saveBooksInBatch_JDBC(List<ParsingDto> dtos, Map<String, Publisher> publisherMap, Map<String, Author> authorMap) {
        int total = dtos.size();
        log.info("🔥 JDBC 배치 저장(Upsert) 시작. 총 {}권", total);

        for (int i = 0; i < total; i += BATCH_SIZE) {
            int end = Math.min(total, i + BATCH_SIZE);
            List<ParsingDto> batchDtos = dtos.subList(i, end);

            // =========================================================
            // ⚡️ [STEP 1] 이미지 먼저 업로드 (DB 트랜잭션 걸기 전)
            // 병렬 스트림(parallelStream)을 써서 속도를 획기적으로 높입니다.
            // =========================================================
            log.info("🖼️ 이미지 업로드 시작 (배치 {} ~ {})...", i, end);
            batchDtos.parallelStream().forEach(dto -> {
                try {
                    if (StringUtils.hasText(dto.getImageUrl())) {
                        String newUrl = minioImageService.uploadImageFromUrl(dto.getImageUrl(), dto.getIsbn());
                        dto.setImageUrl(newUrl);
                    } else {
                        dto.setImageUrl(defaultImageUrl);
                    }
                } catch (Exception e) {
                    log.warn("이미지 처리 실패 (ISBN: {}): {}", dto.getIsbn(), e.getMessage());
                    dto.setImageUrl(defaultImageUrl);
                }
            });

            log.info("🖼️ 이미지 업로드 완료!");


            // =========================================================
            // ⚡️ [STEP 2] DB 저장 (기존 로직)
            // 이미지는 이미 URL이 바뀌어 있으므로 여기선 DB 작업만 빠르게 수행
            // =========================================================
            try {
                transactionalService.executeInNewTransaction(() -> {
                    // 1. 책 정보 저장 (INSERT OR UPDATE) - 한 방 쿼리
                    bulkUpsertBooks(batchDtos, publisherMap);

                    // 2. ID 조회를 위한 ISBN 목록 추출
                    Set<String> batchIsbns = batchDtos.stream()
                            .map(d -> d.getIsbn().trim())
                            .collect(Collectors.toSet());

                    // 3. 방금 저장된 책들의 ID 조회
                    List<Book> savedBooks = bookRepository.findAllByIsbn13In(batchIsbns);
                    Map<String, Book> bookEntityMap = savedBooks.stream()
                            .collect(Collectors.toMap(Book::getIsbn13, b -> b, (a, b) -> a));
                    Map<String, Long> bookIdMap = savedBooks.stream()
                            .collect(Collectors.toMap(Book::getIsbn13, Book::getId, (oldValue, newValue) -> oldValue));
                    for (ParsingDto dto : batchDtos) {
                        String isbn = dto.getIsbn() != null ? dto.getIsbn().trim() : null;
                        if (!StringUtils.hasText(isbn)) continue;

                        Book bookEntity = bookEntityMap.get(isbn);
                        if (bookEntity == null) continue;

                        // dto에 categoryId/categoryName이 들어있는 경우에만 매핑
                        if (dto.getCategoryId() != null && StringUtils.hasText(dto.getCategoryName())) {
                            categoryMappingService.upsertCategoryAndMap(bookEntity, dto.getCategoryId(), dto.getCategoryName());
                        }
                    }

                    // 4. 작가 연결 (BookAuthor) 준비
                    List<Object[]> bookAuthorArgs = new ArrayList<>();

                    for (ParsingDto dto : batchDtos) {
                        Long bookId = bookIdMap.get(dto.getIsbn().trim());
                        // 책이 정상적으로 저장되지 않았거나 작가가 없으면 패스
                        if (bookId == null || !StringUtils.hasText(dto.getAuthor())) continue;

                        String[] authorNames = dto.getAuthor().split("[,;]");
                        Set<String> uniqueAuthors = new HashSet<>(Arrays.asList(authorNames));

                        for (String name : uniqueAuthors) {
                            Author author = authorMap.get(name.trim());
                            if (author != null) {
                                bookAuthorArgs.add(new Object[]{bookId, author.getId()});
                            }
                        }
                    }

                    // 5. 작가 관계 저장 (중복 무시 INSERT IGNORE)
                    if (!bookAuthorArgs.isEmpty()) {
                        String sql = "INSERT IGNORE INTO book_author (book_id, author_id) VALUES (?, ?)";
                        jdbcTemplate.batchUpdate(sql, bookAuthorArgs);
                    }
                    return null;
                });

                log.info("✅ 배치 성공: {} ~ {} (누적 {}건)", i, end, end);

            } catch (Exception e) {
                // 🛡️ 여기가 핵심: 배치가 실패해도 멈추지 않고 로그만 찍고 다음으로 넘어갑니다.
                log.error("⚠️ 배치 저장 실패 (Index: {} ~ {}). 원인: {}", i, end, e.getMessage());
                // 필요하다면 여기서 실패한 batchDtos만 별도 로그 파일로 저장 가능
            }
        }
    }

    /**
     * 핵심 기술: ON DUPLICATE KEY UPDATE (있으면 수정, 없으면 입력)
     */
    private void bulkUpsertBooks(List<ParsingDto> dtos, Map<String, Publisher> publisherMap) {
        String sql = "INSERT INTO book (isbn13, title, publisher_id, price, content, image_url, published_date) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "title = VALUES(title), " +
                "publisher_id = VALUES(publisher_id), " +
                "price = VALUES(price), " +
                "content = VALUES(content), " +
                "image_url = VALUES(image_url), " +
                "published_date = VALUES(published_date)";

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                ParsingDto dto = dtos.get(i);
                String pubName = dto.getPublisher() != null ? dto.getPublisher().trim() : "";
                Publisher pub = publisherMap.get(pubName);

                String finalUrl = convertToFrontendImageUrl(dto.getImageUrl());

                ps.setString(1, dto.getIsbn().trim());
                ps.setString(2, dto.getTitle());

                // 🔥 [수정됨] 출판사 ID 안전하게 넣기
                if (pub != null && pub.getPublisherId() != null) {
                    ps.setLong(3, pub.getPublisherId());
                } else {
                    // 출판사가 없거나 ID가 없으면 NULL (DB 컬럼이 Not Null이면 에러남 -> 이 경우 기본값 넣어야 함)
                    ps.setNull(3, Types.BIGINT);
                }

                ps.setInt(4, parsePrice(dto.getPrice()));
                ps.setString(5, dto.getDescription());
                ps.setString(6, convertToFrontendImageUrl(dto.getImageUrl()));
                ps.setString(7, parseDate(dto.getPubDate()).toString());
            }

            @Override
            public int getBatchSize() {
                return dtos.size();
            }
        });
    }

    /**
     * 출판사 이름 목록을 받아 DB 확인 후 없으면 생성하여 Map으로 반환
     */
    /**
     * JDBC INSERT IGNORE를 사용하여 중복 에러 없이 출판사 확보
     */
    private Map<String, Publisher> resolvePublishers(Set<String> names) {
        if (names.isEmpty()) return new HashMap<>();

        log.info("🏢 출판사 데이터 확보 중... (총 {}건)", names.size());

        // 1. JDBC로 '무조건 입력 시도' (중복이면 DB가 알아서 무시함 - 에러 안남)
        String sql = "INSERT IGNORE INTO publishers (publisher_name) VALUES (?)";

        List<String> nameList = new ArrayList<>(names);
        final int BATCH = 1000;

        try {
            for (int i = 0; i < nameList.size(); i += BATCH) {
                List<String> batch = nameList.subList(i, Math.min(nameList.size(), i + BATCH));

                jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        ps.setString(1, batch.get(i));
                    }
                    @Override
                    public int getBatchSize() {
                        return batch.size();
                    }
                });
            }
        } catch (Exception e) {
            log.error("출판사 INSERT IGNORE 중 에러 (무시 가능): {}", e.getMessage());
        }

        // 2. 입력이 끝났으니, 안전하게 조회해서 Map 만들기
        // (데이터가 너무 많을 수 있으니 배치로 나눠서 조회)
        Map<String, Publisher> publisherMap = new HashMap<>();

        for (int i = 0; i < nameList.size(); i += BATCH) {
            List<String> batch = nameList.subList(i, Math.min(nameList.size(), i + BATCH));

            // JPA로 조회해서 영속성 컨텍스트에 올림 (이후 book 저장 시 필요)
            List<Publisher> found = publisherRepository.findAllByNameIn(new HashSet<>(batch));
            for (Publisher p : found) {
                publisherMap.put(p.getName(), p);
            }
        }

        return publisherMap;
    }

    /**
     * 작가 이름 목록을 받아 DB 확인 후 없으면 생성하여 Map으로 반환
     */
    /**
     * JDBC INSERT IGNORE를 사용하여 중복 에러 없이 작가 확보
     */
    private Map<String, Author> resolveAuthors(Set<String> names) {
        if (names.isEmpty()) return new HashMap<>();

        log.info("✍️ 작가 데이터 확보 중... (총 {}건)", names.size());

        // 1. JDBC로 '무조건 입력 시도'
        String sql = "INSERT IGNORE INTO authors (author_name) VALUES (?)";

        List<String> nameList = new ArrayList<>(names);
        final int BATCH = 1000;

        try {
            for (int i = 0; i < nameList.size(); i += BATCH) {
                List<String> batch = nameList.subList(i, Math.min(nameList.size(), i + BATCH));

                jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        ps.setString(1, batch.get(i));
                    }
                    @Override
                    public int getBatchSize() {
                        return batch.size();
                    }
                });
            }
        } catch (Exception e) {
            log.error("작가 INSERT IGNORE 중 에러 (무시 가능): {}", e.getMessage());
        }

        // 2. 안전하게 조회
        Map<String, Author> authorMap = new HashMap<>();

        for (int i = 0; i < nameList.size(); i += BATCH) {
            List<String> batch = nameList.subList(i, Math.min(nameList.size(), i + BATCH));

            List<Author> found = authorRepository.findAllByNameIn(new HashSet<>(batch));
            for (Author a : found) {
                authorMap.put(a.getName(), a);
            }
        }

        return authorMap;
    }

    private ParsingDto findDtoByIsbn(List<ParsingDto> dtos, String isbn) {
        for (ParsingDto dto : dtos) {
            String dtoIsbn = dto.getIsbn() != null ? dto.getIsbn().trim() : "";
            if (dtoIsbn.equals(isbn)) {
                return dto;
            }
        }
        return null;
    }

    private int parsePrice(String priceStr) {
        if (!StringUtils.hasText(priceStr)) return 0;
        try {
            return Integer.parseInt(priceStr.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private LocalDate parseDate(String dateStr) {
        if (!StringUtils.hasText(dateStr)) return LocalDate.now();
        try {
            return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (Exception e) {
            return LocalDate.now();
        }
    }

    // 날짜 수정 전용 유틸리티 메서드 (필요시 사용)
    public void fixDatesOnly(List<ParsingDto> records) {
        if (records == null || records.isEmpty()) return;

        log.info("📅 날짜 복구 작업 시작! 총 {}건", records.size());

        for (int i = 0; i < records.size(); i += BATCH_SIZE) {
            int end = Math.min(records.size(), i + BATCH_SIZE);
            List<ParsingDto> batchDtos = records.subList(i, end);

            transactionalService.executeInNewTransaction(() -> {
                Set<String> isbns = batchDtos.stream()
                        .map(dto -> dto.getIsbn().trim())
                        .filter(StringUtils::hasText)
                        .collect(Collectors.toSet());

                List<Book> books = bookRepository.findAllByIsbn13In(isbns);
                Map<String, Book> bookMap = books.stream()
                        .collect(Collectors.toMap(Book::getIsbn13, book -> book));

                List<Book> dirtyBooks = new ArrayList<>();

                for (ParsingDto dto : batchDtos) {
                    Book book = bookMap.get(dto.getIsbn().trim());
                    if (book != null) {
                        String newDateStr = parseDate(dto.getPubDate()).toString();
                        if (!newDateStr.equals(book.getPublishedDate())) {
                            book.setPublishedDate(newDateStr);
                            dirtyBooks.add(book);
                        }
                    }
                }

                if (!dirtyBooks.isEmpty()) {
                    bookRepository.saveAll(dirtyBooks);
                }
                return null;
            });
            entityManager.clear();
        }
        log.info("🎉 모든 날짜 복구 작업 완료!");
    }

    private String convertToFrontendImageUrl(String imageUrl) {
        // 기본값이면서 null/blank이면 기본 이미지
        if (!StringUtils.hasText(imageUrl)) {
            return defaultImageUrl;
        }

        String trimmed = imageUrl.trim();

        // 1. 외부 HTTPS 이미지는 그대로
        if (trimmed.startsWith("https://")) {
            return trimmed;
        }

        // 2. MinIO HTTP 내부 주소를 프록시 HTTPS로 변환
        // proxy base path: https://nhnbook.shop/hi-five-bucket/
        // 예: http://storage.java21.net:8000/hi-five-bucket/xxxx.jpg
        String minioHttpPrefix = "http://storage.java21.net:8000/hi-five-bucket/";
        if (trimmed.startsWith(minioHttpPrefix)) {
            String fileName = trimmed.substring(minioHttpPrefix.length());
            return "https://nhnbook.shop/hi-five-bucket/" + fileName;
        }

        // 3. MinIO URL이 다른 형태라도 bucket 부분이 포함돼 있다면 대응
        if (trimmed.contains("/hi-five-bucket/")) {
            String fileName = trimmed.substring(trimmed.lastIndexOf("/hi-five-bucket/") + "/hi-five-bucket/".length());
            return "https://nhnbook.shop/hi-five-bucket/" + fileName;
        }

        // 그 외는 그대로
        return trimmed;
    }

}

